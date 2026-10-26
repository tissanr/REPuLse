(ns repulse.ai.client-test
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [repulse.ai.client :as client]))

(def messages
  [{:role "user" :content "make it faster"}
   {:role "assistant" :tool-calls [{:id "c1" :name "read_buffer" :args {}}]}
   {:role "tool" :results [{:id "c1" :name "read_buffer" :result {:ok true :text "(bpm 120)"}}]}])

(def tools [{:name "read_buffer"}])

(defn- request [provider & [tls]]
  (let [r (client/build-request {:provider provider :key "test-key" :model "m-1"}
                                "SYSTEM" messages tls)]
    (assoc r :body (js->clj (js/JSON.parse (:body r)) :keywordize-keys true))))

(deftest anthropic-request-test
  (let [{:keys [url headers body]} (request "anthropic" tools)]
    (is (= "https://api.anthropic.com/v1/messages" url))
    (is (= "test-key" (get headers "x-api-key")))
    (is (= "m-1" (:model body)))
    (is (= "SYSTEM" (:system body)) "system prompt goes in the top-level field")
    (is (not (contains? body :stream)) "non-streaming request")
    (is (= tools (:tools body)))
    (testing "tool calls and results map to tool_use / tool_result blocks"
      (let [[u a t] (:messages body)]
        (is (= {:role "user" :content "make it faster"} u))
        (is (= {:type "tool_use" :id "c1" :name "read_buffer" :input {}} (first (:content a))))
        (is (= "user" (:role t)))
        (is (= "c1" (:tool_use_id (first (:content t)))))
        (is (= {:ok true :text "(bpm 120)"}
               (js->clj (js/JSON.parse (:content (first (:content t)))) :keywordize-keys true)))))))

(deftest openai-like-request-test
  (doseq [[provider url] [["openai" "https://api.openai.com/v1/chat/completions"]
                          ["groq"   "https://api.groq.com/openai/v1/chat/completions"]
                          ["xai"    "https://api.x.ai/v1/chat/completions"]
                          ["openrouter" "https://openrouter.ai/api/v1/chat/completions"]
                          ["mistral" "https://api.mistral.ai/v1/chat/completions"]]]
    (testing provider
      (let [{u :url headers :headers body :body} (request provider tools)
            [sys usr asst tool] (:messages body)]
        (is (= url u))
        (is (= "Bearer test-key" (get headers "Authorization")))
        (is (= {:role "system" :content "SYSTEM"} sys) "system prompt is the first message")
        (is (= "user" (:role usr)))
        (is (= "{}" (get-in asst [:tool_calls 0 :function :arguments])) "arguments are a JSON string")
        (is (= {:role "tool" :tool_call_id "c1"} (select-keys tool [:role :tool_call_id])))
        (is (= "auto" (:tool_choice body))))))
  (testing "no tools → no tools or tool_choice fields"
    (let [{:keys [body]} (request "openai" nil)]
      (is (not (contains? body :tools)))
      (is (not (contains? body :tool_choice))))))

(deftest openrouter-attribution-test
  (is (= {"HTTP-Referer" "https://repulse.app" "X-Title" "REPuLse"}
         (select-keys (:headers (request "openrouter" nil)) ["HTTP-Referer" "X-Title"])))
  (is (not (contains? (:headers (request "mistral" nil)) "X-Title"))))

(deftest models-request-test
  (doseq [[provider url] [["anthropic" "https://api.anthropic.com/v1/models?limit=1000"]
                          ["openai"    "https://api.openai.com/v1/models"]
                          ["google"    "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000"]
                          ["groq"      "https://api.groq.com/openai/v1/models"]
                          ["xai"       "https://api.x.ai/v1/models"]
                          ["openrouter" "https://openrouter.ai/api/v1/models"]
                          ["mistral"   "https://api.mistral.ai/v1/models"]]]
    (is (= url (:url (client/models-request {:provider provider :key "k"}))) provider))
  (is (= "k" (get-in (client/models-request {:provider "anthropic" :key "k"}) [:headers "x-api-key"])))
  (is (= "Bearer k" (get-in (client/models-request {:provider "mistral" :key "k"}) [:headers "Authorization"])))
  (is (= {} (:headers (client/models-request {:provider "openrouter" :key ""}))) "openrouter list is public"))

(deftest google-request-test
  (let [{:keys [url body]} (request "google" tools)
        [usr model fn-resp] (:contents body)]
    (is (str/includes? url "/models/m-1:generateContent?key=test-key"))
    (is (not (str/includes? url "alt=sse")))
    (is (= "SYSTEM" (get-in body [:systemInstruction :parts 0 :text])))
    (is (= "user" (:role usr)))
    (is (= "model" (:role model)))
    (is (= "read_buffer" (get-in model [:parts 0 :functionCall :name])))
    (is (= "read_buffer" (get-in fn-resp [:parts 0 :functionResponse :name])))))

(deftest unknown-provider-test
  (is (thrown? js/Error (client/build-request {:provider "nope"} "s" [] nil))))

(defn- parse [provider data]
  (client/parse-complete-response provider (clj->js data)))

(deftest parse-anthropic-test
  (testing "text reply with usage summed across input, output and cache"
    (is (= {:content "hi" :usage 140}
           (parse "anthropic" {:content [{:type "text" :text "hi"}]
                               :usage {:input_tokens 100 :output_tokens 10
                                       :cache_creation_input_tokens 20
                                       :cache_read_input_tokens 10}}))))
  (testing "tool_use blocks become tool calls; text alongside them is dropped"
    (let [r (parse "anthropic" {:content [{:type "text" :text "let me look"}
                                          {:type "tool_use" :id "t1" :name "propose_edit"
                                           :input {:match "a" :replacement "b"}}]
                                :usage {:input_tokens 5 :output_tokens 5}})]
      (is (= [{:id "t1" :name "propose_edit" :args {:match "a" :replacement "b"}}] (:tool-calls r)))
      (is (= 10 (:usage r)))
      (is (not (contains? r :content))))))

(deftest parse-openai-test
  (is (= {:content "ok" :usage 42}
         (parse "openai" {:choices [{:message {:content "ok"}}] :usage {:total_tokens 42}})))
  (testing "tool call arguments are parsed from JSON; bad JSON becomes {} plus :args-error"
    (let [r (parse "groq" {:choices [{:message {:tool_calls
                                                [{:id "a" :function {:name "query_track" :arguments "{\"name\":\"kick\"}"}}
                                                 {:id "b" :function {:name "read_buffer" :arguments "{oops"}}]}}]})]
      (is (= [{:id "a" :name "query_track" :args {:name "kick"}}
              {:id "b" :name "read_buffer" :args {}}]
             (map #(dissoc % :args-error) (:tool-calls r))))
      (is (some? (:args-error (second (:tool-calls r)))))
      (is (not (contains? r :usage)) "no usage block → no :usage"))))

(deftest parse-google-test
  (is (= {:content "yo" :usage 77}
         (parse "google" {:candidates [{:content {:parts [{:text "yo"}]}}]
                          :usageMetadata {:totalTokenCount 77}})))
  (let [r (parse "google" {:candidates [{:content {:parts [{:functionCall {:name "read_buffer" :args {}}}]}}]})]
    (is (= "read_buffer" (get-in r [:tool-calls 0 :name])))
    (is (string? (get-in r [:tool-calls 0 :id])) "Google omits ids; one is generated")))

(deftest parse-empty-test
  (is (= {:content "(no response)"} (parse "anthropic" {:content []})))
  (is (= {:content "(no response)"} (parse "openai" {:choices []}))))

;; ── AI2c: custom endpoints ───────────────────────────────────────────────────

(deftest normalise-base-url-test
  (testing "https: any host"
    (is (= {:ok "https://api.together.xyz/v1"} (client/normalise-base-url "https://api.together.xyz/v1")))
    (is (= {:ok "https://10.0.0.5/v1"} (client/normalise-base-url "https://10.0.0.5/v1"))))
  (testing "http: loopback only, any port"
    (is (= {:ok "http://localhost:1234/v1"} (client/normalise-base-url "http://localhost:1234/v1")))
    (is (= {:ok "http://127.0.0.1:11434/v1"} (client/normalise-base-url " http://127.0.0.1:11434/v1 ")))
    (is (= {:ok "http://[::1]:8080/v1"} (client/normalise-base-url "http://[::1]:8080/v1"))))
  (testing "http LAN / remote hosts are rejected with an explanation"
    (doseq [u ["http://192.168.1.20:1234/v1" "http://my-box.local:8080/v1" "http://example.com/v1"]]
      (let [{:keys [ok error]} (client/normalise-base-url u)]
        (is (nil? ok) u)
        (is (str/includes? error "mixed content") u))))
  (testing "trailing slash and pasted /chat/completions are stripped"
    (is (= {:ok "http://localhost:1234/v1"} (client/normalise-base-url "http://localhost:1234/v1/")))
    (is (= {:ok "https://api.deepseek.com/v1"}
           (client/normalise-base-url "https://api.deepseek.com/v1/chat/completions")))
    (is (= {:ok "https://h/v1"} (client/normalise-base-url "https://h/v1/chat/completions/"))))
  (testing "garbage and other schemes"
    (is (:error (client/normalise-base-url "")))
    (is (:error (client/normalise-base-url "localhost:1234")))
    (is (:error (client/normalise-base-url "ftp://localhost/v1")))))

(defn- custom-request [cfg tls]
  (let [r (client/build-request (merge {:provider "custom" :model "qwen3"
                                        :base-url "http://localhost:1234/v1/"} cfg)
                                "SYSTEM" messages tls)]
    (assoc r :body (js->clj (js/JSON.parse (:body r)) :keywordize-keys true))))

(deftest custom-build-request-test
  (testing "chat URL from the normalised base, OpenAI body"
    (let [{:keys [url headers body]} (custom-request {:key ""} tools)]
      (is (= "http://localhost:1234/v1/chat/completions" url))
      (is (not (contains? headers "Authorization")) "no key → no Authorization header")
      (is (= "application/json" (get headers "content-type")))
      (is (= "qwen3" (:model body)))
      (is (= tools (:tools body)))
      (is (= "auto" (:tool_choice body)))))
  (testing "Bearer only with a key"
    (is (= "Bearer sk-x" (get (:headers (custom-request {:key "sk-x"} tools)) "Authorization"))))
  (testing "Send tools off → no tools / tool_choice"
    (let [{:keys [body]} (custom-request {:tools? false} tools)]
      (is (not (contains? body :tools)))
      (is (not (contains? body :tool_choice)))))
  (testing "Send tools only affects custom"
    (is (= tools (:tools (:body (request "openai" tools))))))
  (testing "invalid base URL throws its validation message"
    (is (thrown-with-msg? js/Error #"mixed content"
          (client/build-request {:provider "custom" :base-url "http://192.168.0.2/v1"}
                                "S" messages nil)))))

(deftest custom-models-request-test
  (is (= {:url "http://localhost:11434/v1/models" :headers {}}
         (client/models-request {:provider "custom" :base-url "http://localhost:11434/v1"})))
  (is (= {:url "https://api.fireworks.ai/inference/v1/models"
          :headers {"Authorization" "Bearer k"}}
         (client/models-request {:provider "custom" :key "k"
                                 :base-url "https://api.fireworks.ai/inference/v1/"}))))

(deftest transport-selection-test
  (is (client/direct-provider? "custom"))
  (doseq [p ["anthropic" "openai" "google" "groq" "xai" "openrouter" "mistral"]]
    (is (not (client/direct-provider? p)) (str p " must go through the proxy"))))

(deftest direct-error-text-test
  (let [base "http://localhost:1234/v1"]
    (is (str/includes? (client/direct-error-text base nil nil) "Couldn't reach http://localhost:1234/v1"))
    (is (str/includes? (client/direct-error-text base nil nil) "CORS"))
    (is (str/includes? (client/direct-error-text base 401 "") "API key"))
    (is (str/includes? (client/direct-error-text base 403 "") "API key"))
    (is (str/includes? (client/direct-error-text base 404 "") "/v1"))
    (is (= "HTTP 500 — boom" (client/direct-error-text base 500 "boom")))))

(defn- openai-fixture [message & [usage]]
  (clj->js (cond-> {:choices [{:message message}]} usage (assoc :usage usage))))

(deftest parse-custom-response-test
  (testing "text-only reply although tools were offered"
    (is (= {:content "Here is a pattern"}
           (client/parse-complete-response "custom" (openai-fixture {:content "Here is a pattern"})))))
  (testing "malformed JSON arguments are flagged, not silently {}"
    (let [{[tc] :tool-calls} (client/parse-complete-response
                               "custom"
                               (openai-fixture {:tool_calls [{:id "a" :function {:name "read_buffer"
                                                                                 :arguments "{oops"}}]}))]
      (is (= "read_buffer" (:name tc)))
      (is (= {} (:args tc)))
      (is (str/includes? (:args-error tc) "{oops"))))
  (testing "empty arguments are an empty map, not an error; missing id is generated"
    (let [{[tc] :tool-calls} (client/parse-complete-response
                               "custom"
                               (openai-fixture {:tool_calls [{:function {:name "read_buffer" :arguments ""}}]}))]
      (is (nil? (:args-error tc)))
      (is (seq (:id tc)))))
  (testing "text alongside tool calls is kept"
    (is (= "Let me look"
           (:content (client/parse-complete-response
                       "custom"
                       (openai-fixture {:content "Let me look"
                                        :tool_calls [{:id "a" :function {:name "read_buffer" :arguments "{}"}}]}))))))
  (testing "no usage block → no :usage"
    (is (not (contains? (client/parse-complete-response "custom" (openai-fixture {:content "x"})) :usage)))
    (is (= 12 (:usage (client/parse-complete-response "custom" (openai-fixture {:content "x"} {:total_tokens 12})))))))
