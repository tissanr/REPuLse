(ns repulse.ai.settings)

(def ^:private key-prefix "repulse:ai:")

(defn- ls-get [k]    (.getItem    js/localStorage (str key-prefix k)))
(defn- ls-set! [k v] (.setItem    js/localStorage (str key-prefix k) v))
(defn- ls-del! [k]   (.removeItem js/localStorage (str key-prefix k)))

(defonce enabled?         (atom (= "true" (ls-get "enabled"))))
(defonce provider         (atom (or (ls-get "provider") "anthropic")))
(defonce api-key          (atom (or (ls-get "key") "")))
(defonce model-override   (atom (or (ls-get "model") "")))
(defonce include-code?    (atom (= "true" (ls-get "include-code"))))
(defonce freesound-api-key (atom (ls-get "freesound-key")))
(defonce search-api-key   (atom (ls-get "search-key")))

(add-watch enabled?         :persist (fn [_ _ _ v] (if v (ls-set! "enabled" "true") (ls-del! "enabled"))))
(add-watch provider         :persist (fn [_ _ _ v] (ls-set! "provider" v)))
(add-watch api-key          :persist (fn [_ _ _ v] (ls-set! "key" v)))
(add-watch model-override   :persist (fn [_ _ _ v] (if (seq v) (ls-set! "model" v) (ls-del! "model"))))
(add-watch include-code?    :persist (fn [_ _ _ v] (if v (ls-set! "include-code" "true") (ls-del! "include-code"))))
(add-watch freesound-api-key :persist (fn [_ _ _ v] (if (seq v) (ls-set! "freesound-key" v) (ls-del! "freesound-key"))))
(add-watch search-api-key   :persist (fn [_ _ _ v] (if (seq v) (ls-set! "search-key" v) (ls-del! "search-key"))))

(def default-models
  {"anthropic" "claude-sonnet-4-6"
   "openai"    "gpt-4o"
   "google"    "gemini-2.0-flash"
   "groq"      "llama-3.3-70b-versatile"
   "xai"       "grok-3"})

(defn effective-model []
  (let [override @model-override]
    (if (seq override) override (get default-models @provider))))
