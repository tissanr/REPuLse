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
                          ["xai"    "https://api.x.ai/v1/chat/completions"]]]
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
  (testing "tool call arguments are parsed from JSON; bad JSON becomes {}"
    (let [r (parse "groq" {:choices [{:message {:tool_calls
                                                [{:id "a" :function {:name "query_track" :arguments "{\"name\":\"kick\"}"}}
                                                 {:id "b" :function {:name "read_buffer" :arguments "{oops"}}]}}]})]
      (is (= [{:id "a" :name "query_track" :args {:name "kick"}}
              {:id "b" :name "read_buffer" :args {}}]
             (:tool-calls r)))
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
