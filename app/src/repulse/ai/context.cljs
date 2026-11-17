(ns repulse.ai.context
  "Pure AI context for the assistant rail (Phase WS4): what the user is pointing
   at in the outline — the WS1 selection, else the entry under the cursor.
   No DOM, no editor; tested in Node.
   Exports: resolve-context, chip-text, prompt-block."
  (:require [clojure.string :as str]
            [repulse.outline :as outline]
            [repulse.ui.selection :as sel]
            [repulse.ai.injection-guard :as guard]))

(def ^:private kind-label
  {:def "def" :defn "defn" :defsynth "synth" :track "track" :section "section"
   :song "song" :fx "fx" :fx-group "fx" :master "master" :master-fx "master fx"})

(defn- item
  "Context item for an outline entry."
  [id e]
  (cond-> (select-keys e [:kind :name :from :to])
    (= id "master") (assoc :name nil)))

(defn- label-of [items]
  (if (> (count items) 1)
    (str (count items) " tracks")
    (let [it (first items)]
      (case (:kind it)
        :song "song"
        :master "master"
        :fx-group "fx"
        (:name it)))))

(defn resolve-context
  "sel: selection ids; idx: {id entry} (outline-panel/selection-index);
   entries: outline entries; cursor: editor head.
   → nil | {:source :selection|:cursor :label str :items [{:kind :name :from :to}]}"
  [sel idx entries cursor]
  (let [ids (sel/prune sel idx)]
    (if (seq ids)
      (let [items (mapv #(item % (get idx %)) ids)]
        {:source :selection :label (label-of items) :items items})
      (when-let [e (outline/entry-at entries cursor)]
        (let [items [(item nil e)]]
          {:source :cursor :label (label-of items) :items items})))))

(defn chip-text
  "Header chip text for a context, or nil."
  [ctx]
  (when ctx (str "ctx " (:label ctx))))

(defn- describe [{:keys [kind name]}]
  (if (contains? #{:fx-group :master} kind)
    (get kind-label kind)
    (str (get kind-label kind (clojure.core/name kind))
         (when name (str " " name)))))

(defn- slice [code {:keys [from to]}]
  (let [n (count code)]
    (subs code (min from n) (min to n))))

(defn prompt-block
  "System-prompt focus block for ctx, or nil. Source text of the focused forms
   is included only when include-code? is true."
  [ctx code include-code?]
  (when ctx
    (str "\n\nUser focus ("
         (if (= :selection (:source ctx)) "selected in the outline" "under the cursor")
         ") — assume the request refers to this unless the user says otherwise:\n"
         (guard/wrap
           (cond-> (str/join ", " (map describe (:items ctx)))
             include-code? (str "\n\n" (str/join "\n\n" (map #(slice code %) (:items ctx)))))))))
