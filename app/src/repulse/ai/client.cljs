(ns repulse.ai.client
  (:require [repulse.ai.settings :as settings]
            [repulse.ai.models :as models]
            [clojure.string :as str]
            [goog.object :as gobj]))

;;; ── Normalised message format used by the agent loop ────────────────────────
;;
;;  {:role "user"      :content "text"}
;;  {:role "assistant" :content "text"}
;;  {:role "assistant" :tool-calls [{:id "..." :name "..." :args {...}}]}
;;  {:role "tool"      :results   [{:id "..." :name "..." :result "..."}]}

;; ── Request builders ──────────────────────────────────────────────────────────

(defn- anthropic-messages
  "Convert normalised messages to Anthropic format (handles tool-calls / tool results)."
  [messages]
  (reduce
    (fn [acc {:keys [role content tool-calls results]}]
      (cond
        (= role "tool")
        (conj acc {:role    "user"
                   :content (mapv (fn [{:keys [id result]}]
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
        (into acc (mapv (fn [{:keys [id result]}]
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
                                :system     system
                                :messages   (anthropic-messages messages)}
                          (seq tools) (assoc :tools tools))))})

(defn- openai-like-request [url system messages model key tools]
  {:url     url
   :headers {"Authorization" (str "Bearer " key)
             "content-type"  "application/json"}
   :body    (js/JSON.stringify
              (clj->js (cond-> {:model    model
                                :messages (into [{:role "system" :content system}]
                                               (openai-messages messages))}
                          (seq tools) (assoc :tools tools :tool_choice "auto"))))})

(defn- google-request [system messages model key tools]
  {:url     (str "https://generativelanguage.googleapis.com/v1beta/models/"
                 model ":generateContent?key=" key)
   :headers {"content-type" "application/json"}
   :body    (js/JSON.stringify
              (clj->js (cond-> {:systemInstruction {:parts [{:text system}]}
                                :contents (google-messages messages)}
                          (seq tools) (assoc :tools tools))))})

(def openai-like
  "Chat and model-list endpoints of the OpenAI-compatible providers."
  {"openai"     {:chat   "https://api.openai.com/v1/chat/completions"
                 :models "https://api.openai.com/v1/models"}
   "groq"       {:chat   "https://api.groq.com/openai/v1/chat/completions"
                 :models "https://api.groq.com/openai/v1/models"}
   "xai"        {:chat   "https://api.x.ai/v1/chat/completions"
                 :models "https://api.x.ai/v1/models"}
   "openrouter" {:chat   "https://openrouter.ai/api/v1/chat/completions"
                 :models "https://openrouter.ai/api/v1/models"}
   "mistral"    {:chat   "https://api.mistral.ai/v1/chat/completions"
                 :models "https://api.mistral.ai/v1/models"}})

(def ^:private openrouter-attribution
  {"HTTP-Referer" "https://repulse.app"
   "X-Title"      "REPuLse"})

(defn build-request
  "Provider request envelope {:url :headers :body} for one completion.
   Pure — the caller supplies provider, key and model."
  [{:keys [provider key model]} system messages tools]
  (case provider
    "anthropic" (anthropic-request system messages model key tools)
    "google"    (google-request    system messages model key tools)
    (if-let [{:keys [chat]} (openai-like provider)]
      (cond-> (openai-like-request chat system messages model key tools)
        (= provider "openrouter") (update :headers merge openrouter-attribution))
      (throw (ex-info "Unknown provider" {:provider provider})))))

(defn models-request
  "Model-list request envelope {:url :headers} for a provider. Pure.
   OpenRouter's catalogue is public, so it carries no Authorization header."
  [{:keys [provider key]}]
  (case provider
    "anthropic" {:url     "https://api.anthropic.com/v1/models?limit=1000"
                 :headers {"x-api-key" key "anthropic-version" "2023-06-01"}}
    "google"    {:url     "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000"
                 :headers {"x-goog-api-key" key}}
    "openrouter" {:url     (get-in openai-like ["openrouter" :models])
                  :headers {}}
    (if-let [{:keys [models]} (openai-like provider)]
      {:url models :headers {"Authorization" (str "Bearer " key)}}
      (throw (ex-info "Unknown provider" {:provider provider})))))

(defn- make-request [system messages tools]
  (build-request {:provider @settings/provider
                  :key      @settings/api-key
                  :model    (settings/effective-model)}
                 system messages tools))

;; ── Response parsers ─────────────────────────────────────────────────────────

(defn- first-item [xs]
  (when (and xs (pos? (.-length xs)))
    (aget xs 0)))

(defn- jget [obj k]
  (when obj
    (gobj/get obj k)))

(defn- http-error-text
  "Format a non-2xx response as a user-facing error string, appending an
   actionable hint for known rejections."
  [status body-text]
  (str "HTTP " status " — " body-text
       (when (and (= 403 status)
                  (str/includes? (str body-text) "Origin not allowed"))
         (str "\nHint: the AI proxy rejected this site's origin. "
              "If the app runs on a new domain, add it to the ALLOWED_ORIGINS "
              "environment variable of the Vercel deployment."))))

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

(defn- extract-usage
  "Total tokens (input + output) billed for one response, from the provider's
   usage block. nil when the provider did not report usage."
  [provider obj]
  (case provider
    "anthropic" (when-let [u (jget obj "usage")]
                  (+ (or (jget u "input_tokens") 0)
                     (or (jget u "output_tokens") 0)
                     (or (jget u "cache_creation_input_tokens") 0)
                     (or (jget u "cache_read_input_tokens") 0)))
    "google"    (some-> (jget obj "usageMetadata") (jget "totalTokenCount"))
    (some-> (jget obj "usage") (jget "total_tokens"))))

(defn parse-complete-response
  "Normalise a provider's JSON response (a JS object) to {:content text} or
   {:tool-calls [...]}, plus :usage (billed tokens) when reported."
  [provider obj]
  (let [tool-calls (case provider
                     "anthropic" (extract-tool-calls-anthropic obj)
                     "google"    (extract-tool-calls-google obj)
                     (extract-tool-calls-openai obj))
        text       (when-not tool-calls
                     (case provider
                       "anthropic" (extract-text-anthropic obj)
                       "google"    (extract-text-google obj)
                       (extract-text-openai obj)))
        usage      (extract-usage provider obj)]
    (cond-> (or tool-calls
                (when text {:content text})
                {:content "(no response)"})
      usage (assoc :usage usage))))

(defn complete!
  "Non-streaming request that supports tool calls.
   system   — system prompt string
   messages — vector of normalised chat turns
   tools    — provider-specific tool descriptors (nil for no tools)
   Returns a Promise resolving to {:content text} or {:tool-calls [...]}, with
   :usage (billed tokens) when the provider reports it; {:rate-limited true}
   on HTTP 429; or {:error msg} on any other failure.

   Requests are routed through /api/ai-stream (a thin server-side proxy) to
   avoid CORS rejections from AI provider APIs when called from the browser."
  [system messages tools]
  (let [{:keys [url headers body]} (make-request system messages tools)
        provider   @settings/provider
        proxy-body (js/JSON.stringify #js {:url     url
                                           :headers (clj->js headers)
                                           :body    body})]
    (-> (js/fetch "/api/ai-stream"
                  #js {:method  "POST"
                       :headers #js {"content-type" "application/json"}
                       :body    proxy-body})
        (.then (fn [resp]
                 (cond
                   (.-ok resp)
                   (-> (.json resp) (.then #(parse-complete-response provider %)))
                   (= 429 (.-status resp))
                   {:rate-limited true}
                   :else
                   (-> (.text resp)
                       (.then (fn [t] {:error (http-error-text (.-status resp) t)}))))))
        (.catch (fn [err]
                  {:error (if (string? err) err (or (.-message err) "unknown error"))})))))

(def max-attempts 3)

(defn complete-with-retry!
  "Like complete! but retries on HTTP 429 with exponential back-off (1 s, 2 s).
   At most max-attempts requests in total; resolves to {:error …} once they are
   all rate-limited."
  [system messages tools]
  (letfn [(attempt [n]
            (-> (complete! system messages tools)
                (.then (fn [result]
                         (cond
                           (not (:rate-limited result)) result
                           (< (inc n) max-attempts)
                           (js/Promise.
                             (fn [resolve _reject]
                               (js/setTimeout #(resolve (attempt (inc n)))
                                              (* 1000 (js/Math.pow 2 n)))))
                           :else
                           {:error (str "Rate limited — " max-attempts
                                        " attempts failed. Try again shortly.")})))))]
    (attempt 0)))

;; ── Model catalogue fetch ─────────────────────────────────────────────────────

(defn fetch-models!
  "Fetch, normalise and cache a provider's model list via /api/ai-models.
   Non-fatal: on failure keeps the previous cache and marks the provider :error.
   Returns a Promise resolving to the normalised models or nil."
  [provider key]
  (swap! models/status assoc provider :loading)
  (let [{:keys [url headers]} (models-request {:provider provider :key key})]
    (-> (js/fetch "/api/ai-models"
                  #js {:method  "POST"
                       :headers #js {"content-type" "application/json"}
                       :body    (js/JSON.stringify #js {:url url :headers (clj->js headers)})})
        (.then (fn [resp]
                 (if (.-ok resp)
                   (.json resp)
                   (throw (js/Error. (str "HTTP " (.-status resp)))))))
        (.then (fn [j]
                 (let [ms (models/normalise provider (js->clj j :keywordize-keys true))]
                   (models/store! provider ms)
                   (swap! models/status dissoc provider)
                   ms)))
        (.catch (fn [_]
                  (swap! models/status assoc provider :error)
                  nil)))))

(defn refresh-models!
  "Fetch the current provider's catalogue when forced or stale. Providers other
   than OpenRouter need an API key first."
  [{:keys [force?]}]
  (let [provider @settings/provider
        key      @settings/api-key]
    (when (and (or force? (models/stale? (models/cached provider)))
               (or (= provider "openrouter") (seq key)))
      (fetch-models! provider key))))
