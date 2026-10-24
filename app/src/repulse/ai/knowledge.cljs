(ns repulse.ai.knowledge
  "The AI knowledge base (docs/ai/*.json, Phase AI1) compiled into the app.
   Inlined at build time so the assistant can never lose it to a failed fetch —
   docs/ai/ sits outside app/public and is not served by the dev server or Vercel."
  (:require [shadow.resource :as rc]
            [clojure.string :as str]))

(def ^:private builtins-json (rc/inline "ai/builtins.json"))
(def ^:private cookbook-json (rc/inline "ai/cookbook.json"))

(defn- parse [label s]
  (try
    (js->clj (js/JSON.parse s))
    (catch :default e
      (js/console.warn (str "[REPuLse/ai] could not parse " label
                            " — the assistant runs without it:") e)
      nil)))

(def builtins
  "{name {\"category\" … \"signature\" …}} from docs/ai/builtins.json."
  (parse "builtins.json" builtins-json))

(def cookbook
  "Recipe maps {\"id\" \"title\" \"tags\" \"code\" …} from docs/ai/cookbook.json."
  (parse "cookbook.json" cookbook-json))

(defn builtins-summary
  "Compact vocabulary for the system prompt: one line per category listing its
   built-in names, both sorted. \"\" when the knowledge base is unavailable."
  ([] (builtins-summary builtins))
  ([bs]
   (->> bs
        (group-by (fn [[_ v]] (get v "category" "other")))
        (sort-by key)
        (map (fn [[cat entries]]
               (str cat ": " (str/join ", " (sort (map key entries))))))
        (str/join "\n"))))

(def example-ids
  "Cookbook recipes shown to the model: drums, bass, melody, pad and an effect,
   so it sees the real sound names and the ->> / fx idioms."
  ["boom-bap" "minimal-techno" "acid-303" "minor-arpeggio" "dreamy-pad" "reverb-tail"])

(defn examples
  "Selected cookbook recipes as ;; title + code blocks."
  ([] (examples cookbook))
  ([recipes]
   (let [by-id (into {} (map (juxt #(get % "id") identity)) recipes)]
     (->> example-ids
          (keep by-id)
          (map (fn [r] (str ";; " (get r "title") "\n" (get r "code"))))
          (str/join "\n\n")))))

(when (str/blank? (builtins-summary))
  (js/console.warn "[REPuLse/ai] built-in vocabulary is empty — check docs/ai/builtins.json"))
