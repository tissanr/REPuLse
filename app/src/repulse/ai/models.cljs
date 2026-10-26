(ns repulse.ai.models
  "Live model catalogue: normalise each provider's model list, cache it in
   localStorage (24 h TTL) and pick a default from what the provider serves
   today. Pure functions plus a small cache; the network fetch lives in
   repulse.ai.client (fetch-models!) so this ns has no dependencies."
  (:require [clojure.string :as str]))

(def ttl-ms (* 24 60 60 1000))

;; ── Normalisers (pure) ────────────────────────────────────────────────────────
;; Each takes the provider's parsed JSON (keywordized map) and returns
;;   [{:id :label :tools? true/false/nil :created epoch-seconds-or-nil}]

(def ^:private non-chat-openai
  ["embedding" "whisper" "tts" "dall-e" "moderation" "image" "audio" "realtime" "transcribe"])

(defn- parse-iso [s]
  (when (string? s)
    (let [t (js/Date.parse s)]
      (when-not (js/isNaN t) (quot t 1000)))))

(defmulti normalise (fn [provider _json] provider))

(defmethod normalise "openrouter" [_ {:keys [data]}]
  (vec (for [m data]
         {:id      (:id m)
          :label   (or (:name m) (:id m))
          :tools?  (boolean (some #{"tools"} (:supported_parameters m)))
          :created (:created m)})))

(defmethod normalise "mistral" [_ {:keys [data]}]
  (vec (for [m data]
         {:id      (:id m)
          :label   (or (:name m) (:id m))
          :tools?  (some-> m :capabilities :function_calling boolean)
          :created (:created m)})))

(defmethod normalise "google" [_ {:keys [models]}]
  (vec (for [m models
             :when (some #{"generateContent"} (:supportedGenerationMethods m))
             :let [id (str/replace (str (:name m)) #"^models/" "")]]
         {:id id :label (or (:displayName m) id) :tools? nil :created nil})))

(defmethod normalise "groq" [_ {:keys [data]}]
  (vec (for [m data :when (not (false? (:active m)))]
         {:id (:id m) :label (:id m) :tools? nil :created (:created m)})))

(defmethod normalise "openai" [_ {:keys [data]}]
  (vec (for [m data
             :let [id (str (:id m))]
             :when (not-any? #(str/includes? id %) non-chat-openai)]
         {:id id :label id :tools? nil :created (:created m)})))

(defmethod normalise "anthropic" [_ {:keys [data]}]
  (vec (for [m data]
         {:id      (:id m)
          :label   (or (:display_name m) (:id m))
          :tools?  nil
          :created (parse-iso (:created_at m))})))

(defmethod normalise :default [_ {:keys [data]}]   ; xai, custom and any other openai-like
  (vec (for [m data]
         {:id (:id m) :label (:id m) :tools? nil :created (:created m)})))

;; ── Default selection (pure) ──────────────────────────────────────────────────

(def preferred-models
  "Ordered id-prefix patterns per provider; the first pattern that matches any
   catalogue entry wins."
  {"anthropic"  ["claude-sonnet-" "claude-opus-" "claude-haiku-"]
   "openai"     ["gpt-5" "gpt-4.1" "gpt-4o"]
   "google"     ["gemini-2.5-pro" "gemini-2.5-flash" "gemini-2.0-flash"]
   "groq"       ["llama-3.3-70b"]
   "xai"        ["grok-4" "grok-3"]
   "openrouter" ["openrouter/auto"]
   "mistral"    ["mistral-medium-latest" "mistral-large-latest" "mistral-small-latest"]})

(defn- tools-rank [{:keys [tools?]}]
  (case tools? true 0 nil 1 2))

(defn pick-default
  "Choose a default model id from a normalised catalogue, or `fallback` when
   nothing matches. An exact id match beats prefix matches; among prefix matches
   prefer tools-capable, then newest. Providers without preferred patterns
   (`custom`) default to the first model the server lists."
  [provider models fallback]
  (or (some (fn [pat]
              (let [hits (filter #(str/starts-with? (str (:id %)) pat) models)]
                (when (seq hits)
                  (or (:id (first (filter #(= pat (:id %)) hits)))
                      (:id (first (sort-by (juxt tools-rank #(- (or (:created %) 0))) hits)))))))
            (get preferred-models provider))
      (when-not (contains? preferred-models provider)
        (:id (first models)))
      fallback))

;; ── Cache ─────────────────────────────────────────────────────────────────────

(defn- ck [provider] (str "repulse:ai:models:" provider))

(defn- ls-read [provider]
  (try (some-> (.getItem js/localStorage (ck provider)) js/JSON.parse
               (js->clj :keywordize-keys true))
       (catch :default _ nil)))

(defn- ls-write! [provider v]
  (try (.setItem js/localStorage (ck provider) (js/JSON.stringify (clj->js v)))
       (catch :default _ nil)))

(defonce ^:private cache (atom {}))   ; provider -> {:fetched-at ms :models [...]}
(defonce status (atom {}))            ; provider -> :loading | :error

(defn cached
  "{:fetched-at :models} for a provider, or nil. Reads localStorage once."
  [provider]
  (or (get @cache provider)
      (when-let [c (ls-read provider)]
        (swap! cache assoc provider c)
        c)))

(defn store! [provider models]
  (let [c {:fetched-at (js/Date.now) :models (vec models)}]
    (swap! cache assoc provider c)
    (ls-write! provider c)
    c))

(defn stale?
  ([c] (stale? c (js/Date.now)))
  ([c now] (or (nil? c) (nil? (:fetched-at c)) (> (- now (:fetched-at c)) ttl-ms))))

(defn age-label
  "\"just now\" / \"3 h ago\" / \"2 d ago\" for a fetched-at timestamp."
  [fetched-at now]
  (let [mins (quot (max 0 (- now fetched-at)) 60000)]
    (cond (< mins 1)  "just now"
          (< mins 60) (str mins " min ago")
          (< mins 1440) (str (quot mins 60) " h ago")
          :else (str (quot mins 1440) " d ago"))))

(defn resolved-default
  "Default model for a provider from the cached catalogue, else `fallback`."
  [provider fallback]
  (pick-default provider (:models (cached provider)) fallback))

(defn known?
  "True when id appears in the provider's cached catalogue (or no catalogue)."
  [provider id]
  (let [ms (:models (cached provider))]
    (or (empty? ms) (boolean (some #(= id (:id %)) ms)))))
