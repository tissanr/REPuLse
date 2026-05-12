(ns repulse.ai.client
  (:require [repulse.ai.settings :as settings]
            [clojure.string :as str]
            [goog.object :as gobj]))

;; ── Request builders ──────────────────────────────────────────────────────────

(defn- user-messages
  "Filter out any system messages — the system prompt is sent separately."
  [messages]
  (filterv #(not= "system" (:role %)) messages))

(defn- anthropic-request [system messages model key]
  {:url     "https://api.anthropic.com/v1/messages"
   :headers {"x-api-key"         key
             "anthropic-version" "2023-06-01"
             "content-type"      "application/json"}
   :body    (js/JSON.stringify
              (clj->js {:model      model
                        :max_tokens 2048
                        :stream     true
                        :system     system
                        :messages   (user-messages messages)}))})

(defn- openai-like-request [url system messages model key]
  {:url     url
   :headers {"Authorization" (str "Bearer " key)
             "content-type"  "application/json"}
   :body    (js/JSON.stringify
              (clj->js {:model    model
                        :stream   true
                        :messages (into [{:role "system" :content system}]
                                        (user-messages messages))}))})

(defn- google-request [system messages model key]
  {:url     (str "https://generativelanguage.googleapis.com/v1beta/models/"
                 model ":streamGenerateContent?key=" key "&alt=sse")
   :headers {"content-type" "application/json"}
   :body    (js/JSON.stringify
              (clj->js {:systemInstruction {:parts [{:text system}]}
                        :contents (mapv (fn [{:keys [role content]}]
                                          {:role  (if (= role "assistant") "model" role)
                                           :parts [{:text content}]})
                                        (user-messages messages))}))})

(defn- make-request [system messages]
  (let [provider @settings/provider
        key      @settings/api-key
        model    (settings/effective-model)]
    (case provider
      "anthropic" (anthropic-request  system messages model key)
      "openai"    (openai-like-request "https://api.openai.com/v1/chat/completions"
                                       system messages model key)
      "groq"      (openai-like-request "https://api.groq.com/openai/v1/chat/completions"
                                       system messages model key)
      "xai"       (openai-like-request "https://api.x.ai/v1/chat/completions"
                                       system messages model key)
      "google"    (google-request      system messages model key)
      (throw (ex-info "Unknown provider" {:provider provider})))))

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
