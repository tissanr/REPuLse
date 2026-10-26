(ns repulse.ai.settings
  (:require [repulse.ai.models :as models]))

(def ^:private key-prefix "repulse:ai:")

;; localStorage can be missing (Node test runner) or throw (private mode,
;; blocked storage); settings then live in memory only.
(defn- ls-get [k]    (try (.getItem    js/localStorage (str key-prefix k)) (catch :default _ nil)))
(defn- ls-set! [k v] (try (.setItem    js/localStorage (str key-prefix k) v) (catch :default _ nil)))
(defn- ls-del! [k]   (try (.removeItem js/localStorage (str key-prefix k)) (catch :default _ nil)))

(defonce enabled?          (atom (= "true" (ls-get "enabled"))))
(defonce provider          (atom (or (ls-get "provider") "anthropic")))
(defonce custom-base-url   (atom (or (ls-get "custom-url") "")))
(defonce custom-tools?     (atom (not= "false" (ls-get "custom-tools"))))
(defonce model-override    (atom (or (ls-get "model") "")))
(defonce include-code?     (atom (= "true" (ls-get "include-code"))))
(defonce auto-apply?       (atom (= "true" (ls-get "auto-apply"))))
(defonce freesound-api-key (atom (ls-get "freesound-key")))
(defonce search-api-key    (atom (ls-get "search-key")))

(add-watch enabled?          :persist (fn [_ _ _ v] (if v (ls-set! "enabled" "true") (ls-del! "enabled"))))
(add-watch provider          :persist (fn [_ _ _ v] (ls-set! "provider" v)))
(add-watch custom-base-url   :persist (fn [_ _ _ v] (if (seq v) (ls-set! "custom-url" v) (ls-del! "custom-url"))))
(add-watch custom-tools?     :persist (fn [_ _ _ v] (if v (ls-del! "custom-tools") (ls-set! "custom-tools" "false"))))
(add-watch model-override    :persist (fn [_ _ _ v] (if (seq v) (ls-set! "model" v) (ls-del! "model"))))
(add-watch include-code?     :persist (fn [_ _ _ v] (if v (ls-set! "include-code" "true") (ls-del! "include-code"))))
(add-watch auto-apply?       :persist (fn [_ _ _ v] (if v (ls-set! "auto-apply" "true") (ls-del! "auto-apply"))))
(add-watch freesound-api-key :persist (fn [_ _ _ v] (if (seq v) (ls-set! "freesound-key" v) (ls-del! "freesound-key"))))
(add-watch search-api-key    :persist (fn [_ _ _ v] (if (seq v) (ls-set! "search-key" v) (ls-del! "search-key"))))

;; ── API keys, one per provider ────────────────────────────────────────────────
;; Stored as repulse:ai:key:<provider>, so switching between `custom` and a
;; hosted provider neither wipes nor leaks a key. `api-key` always mirrors the
;; key of the current provider.

(defn key-for [p] (or (ls-get (str "key:" p)) ""))

(defn- migrate-legacy-key!
  "Move the pre-AI2c single repulse:ai:key value to the current provider."
  []
  (when-let [legacy (ls-get "key")]
    (when (and (seq legacy) (not (seq (key-for @provider))))
      (ls-set! (str "key:" @provider) legacy))
    (ls-del! "key")))

(defonce api-key (atom (do (migrate-legacy-key!) (key-for @provider))))

(add-watch api-key  :persist (fn [_ _ _ v]
                               (let [k (str "key:" @provider)]
                                 (if (seq v) (ls-set! k v) (ls-del! k)))))
(add-watch provider :key     (fn [_ _ old v]
                               (when (not= old v) (reset! api-key (key-for v)))))

(def default-models
  "Offline fallbacks, used until a live catalogue has been fetched."
  {"anthropic"  "claude-sonnet-5-5"
   "openai"     "gpt-4o"
   "google"     "gemini-2.0-flash"
   "groq"       "llama-3.3-70b-versatile"
   "xai"        "grok-4.7"
   "openrouter" "openrouter/auto"
   "mistral"    "mistral-medium-latest"})

(defn resolved-default
  "Provider default: newest matching model in the live catalogue, else the
   static fallback."
  []
  (models/resolved-default @provider (get default-models @provider)))

(defn effective-model []
  (let [override @model-override]
    (if (seq override) override (resolved-default))))
