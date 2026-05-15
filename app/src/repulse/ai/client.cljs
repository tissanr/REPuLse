(ns repulse.ai.client
  (:require [repulse.ai.settings :as settings]
            [clojure.string :as str]
            [goog.object :as gobj]))

;;; ── Normalised message format used by the agent loop ────────────────────────
;;
;;  {:role "user"      :content "text"}
;;  {:role "assistant" :content "text"}
;;  {:role "assistant" :tool-calls [{:id "..." :name "..." :args {...}}]}
;;  {:role "tool"      :results   [{:id "..." :name "..." :result "..."}]}

;; ── Request builders ──────────────────────────────────────────────────────────

(defn- user-messages
  "Filter out any system messages — the system prompt is sent separately."
  [messages]
  (filterv #(not= "system" (:role %)) messages))

(defn- anthropic-messages
  "Convert normalised messages to Anthropic format (handles tool-calls / tool results)."
  [messages]
  (reduce
    (fn [acc {:keys [role content tool-calls results]}]
      (cond
        (= role "tool")
        (conj acc {:role    "user"
                   :content (mapv (fn [{:keys [id name result]}]
                                    {:type        "tool_result"
                                     :tool_use_id id
                                     :content     (if (string? result) result (js/JSON.stringify (clj->js result)))})
                                  results)})

        (seq tool-calls)
        (conj acc {:role    "assistant"
                   :content (mapv (fn [{:keys [id name args]}]
                                    {:type  "tool_use"
                                     :id    id
                                     :name  name
                                     :input (or args {})})
                                  tool-calls)})

        (not= role "system")
        (conj acc {:role role :content (str content)})

        :else acc))
    []
    messages))

(defn- openai-messages
  "Convert normalised messages to OpenAI format."
  [messages]
  (reduce
    (fn [acc {:keys [role content tool-calls results]}]
      (cond
        (= role "tool")
        (into acc (mapv (fn [{:keys [id name result]}]
                          {:role         "tool"
                           :tool_call_id id
                           :content      (if (string? result) result (js/JSON.stringify (clj->js result)))})
                        results))

        (seq tool-calls)
        (conj acc {:role       "assistant"
                   :content    nil
                   :tool_calls (mapv (fn [{:keys [id name args]}]
                                       {:id       id
                                        :type     "function"
                                        :function {:name      name
                                                   :arguments (js/JSON.stringify (clj->js (or args {})))}})
                                     tool-calls)})

        (not= role "system")
        (conj acc {:role role :content (str content)})

        :else acc))
    []
    messages))

(defn- google-messages
  "Convert normalised messages to Google format."
  [messages]
  (reduce
    (fn [acc {:keys [role content tool-calls results]}]
      (cond
        (= role "tool")
        (conj acc {:role  "user"
                   :parts (mapv (fn [{:keys [name result]}]
                                  {:functionResponse {:name     name
                                                      :response (if (map? result) result {:result result})}})
                                results)})

        (seq tool-calls)
        (conj acc {:role  "model"
                   :parts (mapv (fn [{:keys [name args]}]
                                  {:functionCall {:name name :args (or args {})}})
                                tool-calls)})

        (= role "assistant")
        (conj acc {:role "model" :parts [{:text (str content)}]})

        (not= role "system")
        (conj acc {:role "user" :parts [{:text (str content)}]})

        :else acc))
    []
    messages))

(defn- anthropic-request [system messages model key tools]
  {:url     "https://api.anthropic.com/v1/messages"
   :headers {"x-api-key"         key
             "anthropic-version" "2023-06-01"
             "content-type"      "application/json"}
   :body    (js/JSON.stringify
              (clj->js (cond-> {:model      model
                                :max_tokens 2048
                                :stream     true
                                :system     system
                                :messages   (anthropic-messages messages)}
                          (seq tools) (assoc :tools tools))))})

(defn- openai-like-request [url system messages model key tools]
  {:url     url
   :headers {"Authorization" (str "Bearer " key)
             "content-type"  "application/json"}
   :body    (js/JSON.stringify
              (clj->js (cond-> {:model    model
                                :stream   true
                                :messages (into [{:role "system" :content system}]
                                               (openai-messages messages))}
                          (seq tools) (assoc :tools tools :tool_choice "auto"))))})

(defn- google-request [system messages model key tools]
  {:url     (str "https://generativelanguage.googleapis.com/v1beta/models/"
                 model ":streamGenerateContent?key=" key "&alt=sse")
   :headers {"content-type" "application/json"}
   :body    (js/JSON.stringify
              (clj->js (cond-> {:systemInstruction {:parts [{:text system}]}
                                :contents (google-messages messages)}
                          (seq tools) (assoc :tools tools))))})

(defn- make-request
  ([system messages] (make-request system messages nil))
  ([system messages tools]
   (let [provider @settings/provider
         key      @settings/api-key
         model    (settings/effective-model)]
     (case provider
       "anthropic" (anthropic-request  system messages model key tools)
       "openai"    (openai-like-request "https://api.openai.com/v1/chat/completions"
                                        system messages model key tools)
       "groq"      (openai-like-request "https://api.groq.com/openai/v1/chat/completions"
                                        system messages model key tools)
       "xai"       (openai-like-request "https://api.x.ai/v1/chat/completions"
                                        system messages model key tools)
       "google"    (google-request      system messages model key tools)
       (throw (ex-info "Unknown provider" {:provider provider}))))))

;; ── Response parsers ─────────────────────────────────────────────────────────

(defn- first-item [xs]
  (when (and xs (pos? (.-length xs)))
    (aget xs 0)))

(defn- jget [obj k]
  (when obj
    (gobj/get obj k)))

(defn- error-message [obj]
  (some-> (jget obj "error") (jget "message")))

(defn- text-from-json [provider obj]
  (case provider
    "anthropic" (or (when (= "content_block_delta" (jget obj "type"))
                      (some-> (jget obj "delta") (jget "text")))
                    (some-> (jget obj "content_block") (jget "text"))
                    (some-> (jget obj "content") first-item (jget "text")))
    ("openai" "groq" "xai") (let [choice (some-> (jget obj "choices") first-item)]
                               (or (some-> choice (jget "delta") (jget "content"))
                                   (some-> choice (jget "message") (jget "content"))
                                   (some-> choice (jget "text"))))
    "google" (some-> (jget obj "candidates") first-item (jget "content") (jget "parts")
                     first-item (jget "text"))
    nil))

(defn- parse-json [s]
  (try
    (js/JSON.parse s)
    (catch :default _ nil)))

(defn- parse-sse-event [provider line]
  (try
    ;; SSE permits both "data:<payload>" and "data: <payload>".
    (when (str/starts-with? line "data:")
      (let [json-str (str/trim (subs line 5))]
        (cond
          (= "[DONE]" json-str)
          {:type :done}

          :else
          (when-let [obj (parse-json json-str)]
            (cond
              (error-message obj)
              {:type :error :message (error-message obj)}

              (= "error" (jget obj "type"))
              {:type :error
               :message (or (some-> (jget obj "error") (jget "message"))
                            (jget obj "message")
                            "Provider returned a streaming error.")}

              :else
              (when-let [text (text-from-json provider obj)]
                {:type :delta :text text}))))))
    (catch :default _ nil)))

(defn- parse-full-response [provider text]
  (when-let [obj (parse-json (str/trim text))]
    (cond
      (error-message obj) {:type :error :message (error-message obj)}
      :else (when-let [content (text-from-json provider obj)]
              {:type :delta :text content}))))

;; ── Streaming fetch ───────────────────────────────────────────────────────────

(defn stream!
  "Start a streaming request.
   system   — system prompt string
   messages — vector of {:role :content} chat turns (no system role)
   callbacks — {:on-chunk f, :on-done f, :on-error f}
   Returns an AbortController.

   Requests are routed through /api/ai-stream (a thin server-side proxy) to
   avoid CORS rejections from AI provider APIs when called from the browser."
  [system messages {:keys [on-chunk on-done on-error]}]
  (let [abort-ctrl (js/AbortController.)
        {:keys [url headers body]} (make-request system messages)
        provider   @settings/provider
        line-buf   (atom "")
        raw-buf    (atom "")
        got-text?  (atom false)
        failed?    (atom false)
        proxy-body (js/JSON.stringify #js {:url     url
                                           :headers (clj->js headers)
                                           :body    body})]
    (->
      (js/fetch "/api/ai-stream"
                #js {:method  "POST"
                     :headers #js {"content-type" "application/json"}
                     :body    proxy-body
                     :signal  (.-signal abort-ctrl)})
      (.then
        (fn [resp]
          (if (.-ok resp)
            ;; Stream the response body
            (let [reader  (.getReader (.-body resp))
                  decoder (js/TextDecoder.)]
              (letfn [(handle-event [event]
                        (case (:type event)
                          :delta (do
                                   (reset! got-text? true)
                                   (on-chunk (:text event)))
                          :error (do
                                   (reset! failed? true)
                                   (on-error (:message event)))
                          :done nil
                          nil))
                      (process-text [text]
                        ;; Split on newlines, keeping a buffer for partial lines
                        (swap! raw-buf str text)
                        (let [combined (str @line-buf text)
                              lines    (str/split combined #"\n")]
                          ;; All lines except the last are complete
                          (doseq [line (butlast lines)]
                            (when-let [event (parse-sse-event provider line)]
                              (handle-event event)))
                          ;; Last fragment may be incomplete
                          (reset! line-buf (last lines))))
                      (read-loop []
                        (-> (.read reader)
                            (.then (fn [result]
                                     (if (.-done result)
                                       (do
                                         (let [tail (.decode decoder)]
                                           (when (seq tail)
                                             (process-text tail)))
                                         ;; Flush remaining buffer
                                         (when-let [event (parse-sse-event provider @line-buf)]
                                           (handle-event event))
                                         (when-not @got-text?
                                           (when-let [event (parse-full-response provider @raw-buf)]
                                             (handle-event event)))
                                         (when-not @failed?
                                           (on-done nil)))
                                       (do
                                         (process-text (.decode decoder (.-value result)
                                                               #js {:stream true}))
                                         (read-loop)))))))]
                (read-loop)))
            ;; Non-2xx: read body text for a useful error message
            (-> (.text resp)
                (.then (fn [body-text]
                         (on-error (str "HTTP " (.-status resp) " — " body-text))))))))
      (.catch
        (fn [err]
          (when-not (= "AbortError" (.-name err))
            (on-error (.-message err))))))
    abort-ctrl))

;; ── Non-streaming complete! with tool call support ────────────────────────────

(defn- extract-tool-calls-openai [obj]
  (when-let [msg (some-> (jget obj "choices") first-item (jget "message"))]
    (when-let [tcs (jget msg "tool_calls")]
      (when (pos? (.-length tcs))
        {:tool-calls
         (mapv (fn [tc]
                 {:id   (jget tc "id")
                  :name (some-> (jget tc "function") (jget "name"))
                  :args (try (js->clj (js/JSON.parse (some-> (jget tc "function") (jget "arguments")))
                                      :keywordize-keys true)
                             (catch :default _ {}))})
               (array-seq tcs))}))))

(defn- extract-tool-calls-anthropic [obj]
  (when-let [content (jget obj "content")]
    (let [tool-blocks (filter #(= "tool_use" (jget % "type")) (array-seq content))]
      (when (seq tool-blocks)
        {:tool-calls
         (mapv (fn [b]
                 {:id   (jget b "id")
                  :name (jget b "name")
                  :args (js->clj (jget b "input") :keywordize-keys true)})
               tool-blocks)}))))

(defn- extract-tool-calls-google [obj]
  (when-let [parts (some-> (jget obj "candidates") first-item
                           (jget "content") (jget "parts"))]
    (let [fn-calls (filter #(jget % "functionCall") (array-seq parts))]
      (when (seq fn-calls)
        {:tool-calls
         (mapv (fn [p]
                 (let [fc (jget p "functionCall")]
                   {:id   (str (gensym "gcall_"))
                    :name (jget fc "name")
                    :args (js->clj (jget fc "args") :keywordize-keys true)}))
               fn-calls)}))))

(defn- extract-text-openai [obj]
  (some-> (jget obj "choices") first-item (jget "message") (jget "content")))

(defn- extract-text-anthropic [obj]
  (when-let [content (jget obj "content")]
    (some (fn [b] (when (= "text" (jget b "type")) (jget b "text")))
          (array-seq content))))

(defn- extract-text-google [obj]
  (some-> (jget obj "candidates") first-item
          (jget "content") (jget "parts") first-item (jget "text")))

(defn- parse-complete-response [provider obj]
  (let [tool-calls (case provider
                     "anthropic" (extract-tool-calls-anthropic obj)
                     "google"    (extract-tool-calls-google obj)
                     (extract-tool-calls-openai obj))
        text       (when-not tool-calls
                     (case provider
                       "anthropic" (extract-text-anthropic obj)
                       "google"    (extract-text-google obj)
                       (extract-text-openai obj)))]
    (or tool-calls
        (when text {:content text})
        {:content "(no response)"})))

(defn complete!
  "Non-streaming request that supports tool calls.
   system   — system prompt string
   messages — vector of normalised chat turns
   tools    — provider-specific tool descriptors (nil for no tools)
   Returns a Promise resolving to {:content text} or {:tool-calls [...]}"
  [system messages tools]
  (let [{:keys [url headers body]} (make-request system messages tools)
        provider   @settings/provider
        ;; Override stream:true → stream:false so provider returns full JSON
        body-obj   (js/JSON.parse body)
        _          (gobj/set body-obj "stream" false)
        body-nostr (js/JSON.stringify body-obj)
        ;; Google uses a different endpoint path for non-streaming responses
        final-url  (if (= provider "google")
                     (-> url
                         (str/replace ":streamGenerateContent?" ":generateContent?")
                         (str/replace "&alt=sse" ""))
                     url)
        proxy-body (js/JSON.stringify #js {:url     final-url
                                           :headers (clj->js headers)
                                           :body    body-nostr})]
    (-> (js/fetch "/api/ai-stream"
                  #js {:method  "POST"
                       :headers #js {"content-type" "application/json"}
                       :body    proxy-body})
        (.then (fn [resp]
                 (if (.-ok resp)
                   (.json resp)
                   (-> (.text resp)
                       (.then (fn [t] (js/Promise.reject (str "HTTP " (.-status resp) " — " t))))))))
        (.then (fn [obj]
                 (parse-complete-response provider obj)))
        (.catch (fn [err]
                  {:content (str "Error: " (if (string? err) err (or (.-message err) "unknown error")))})))))
