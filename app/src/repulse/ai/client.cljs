(ns repulse.ai.client
  (:require [repulse.ai.settings :as settings]
            [clojure.string :as str]))

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

;; ── SSE delta parser ──────────────────────────────────────────────────────────

(defn- parse-delta [provider line]
  (try
    (when (str/starts-with? line "data: ")
      (let [json-str (str/trim (subs line 6))]
        (when-not (= "[DONE]" json-str)
          (let [obj (js/JSON.parse json-str)]
            (case provider
              "anthropic"        (when (= "content_block_delta" (.-type obj))
                                   (some-> (.-delta obj) (.-text)))
              ("openai" "groq" "xai") (some-> (.. obj -choices (aget 0) -delta -content))
              "google"           (some-> (.. obj -candidates (aget 0) -content -parts
                                            (aget 0) -text))
              nil)))))
    (catch :default _ nil)))

;; ── Streaming fetch ───────────────────────────────────────────────────────────

(defn stream!
  "Start a streaming request.
   system   — system prompt string
   messages — vector of {:role :content} chat turns (no system role)
   callbacks — {:on-chunk f, :on-done f, :on-error f}
   Returns an AbortController."
  [system messages {:keys [on-chunk on-done on-error]}]
  (let [abort-ctrl (js/AbortController.)
        {:keys [url headers body]} (make-request system messages)
        provider   @settings/provider
        line-buf   (atom "")]
    (->
      (js/fetch url #js {:method  "POST"
                         :headers (clj->js headers)
                         :body    body
                         :signal  (.-signal abort-ctrl)})
      (.then
        (fn [resp]
          (if (.-ok resp)
            ;; Stream the response body
            (let [reader  (.getReader (.-body resp))
                  decoder (js/TextDecoder.)]
              (letfn [(process-text [text]
                        ;; Split on newlines, keeping a buffer for partial lines
                        (let [combined (str @line-buf text)
                              lines    (str/split combined #"\n")]
                          ;; All lines except the last are complete
                          (doseq [line (butlast lines)]
                            (when-let [delta (parse-delta provider line)]
                              (on-chunk delta)))
                          ;; Last fragment may be incomplete
                          (reset! line-buf (last lines))))
                      (read-loop []
                        (-> (.read reader)
                            (.then (fn [result]
                                     (if (.-done result)
                                       (do
                                         ;; Flush remaining buffer
                                         (when-let [delta (parse-delta provider @line-buf)]
                                           (on-chunk delta))
                                         (on-done nil))
                                       (do
                                         (process-text (.decode decoder (.-value result)))
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
