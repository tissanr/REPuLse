(ns repulse.ai.injection-guard
  "Wrap externally-sourced content in <untrusted> XML tags before it enters
   the model context. The system prompt instructs the model to treat the
   contents as opaque data, not instructions."
  (:require [clojure.string :as str]))

(defn- neutralise
  "Defang any <untrusted> / </untrusted> tags inside content so it cannot close
   the wrapper early and smuggle text outside it."
  [s]
  (str/replace (str s) #"(?i)<(/?)untrusted" "&lt;$1untrusted"))

(defn wrap
  "Wrap a string in <untrusted> tags."
  [s]
  (str "<untrusted>\n" (neutralise s) "\n</untrusted>"))

(defn- wrap-json
  "Serialise a data structure and wrap it — for results whose every string may
   be user- or third-party-controlled (track names, def names, sample tags)."
  [x]
  (wrap (js/JSON.stringify (clj->js x))))

(defn- wrap-fields [rs ks]
  (mapv (fn [r]
          (reduce (fn [r k] (if (some? (get r k)) (update r k wrap) r)) r ks))
        rs))

(defn guard-tool-result
  "Return a copy of result with untrusted fields wrapped.
   tool-kw is the keyword tool name (e.g. :read_buffer)."
  [tool-kw result]
  (case tool-kw
    :read_buffer
    (cond-> result (and (:ok result) (:text result)) (update :text wrap))

    :find_snippet
    (cond-> result (:results result) (update :results wrap-fields [:title :description :tags]))

    :web_search
    (cond-> result (:results result) (update :results wrap-fields [:title :url :description]))

    :freesound_search
    (cond-> result (:results result) (update :results wrap-fields [:name :tags]))

    :query_session
    (cond-> result (:session result) (update :session wrap-json))

    :query_track
    (cond-> result (:track result) (update :track wrap-json))

    ;; Bank names come from external sample manifests (samples! "github:…").
    :list_banks
    (cond-> result (:banks result) (update :banks wrap))

    ;; Remaining tools return app-generated data (status flags, ids).
    result))
