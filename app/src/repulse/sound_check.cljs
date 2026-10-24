(ns repulse.sound-check
  "Unknown sound-name detection for evaluated patterns.
   Responsibility: find keyword event values that are neither note keywords,
   built-in drums, nor registered sample banks, and suggest a likely intended
   name. Unknown names otherwise fall through to a quiet 440 Hz sine, which is
   easy to mistake for silence.
   Pure — sample-bank lookups are injected by the caller.
   Exports: builtin-drums, sound-keywords, suggest, unknown-sounds, warning-message,
            keyword-ranges"
  (:require [repulse.core :as core]
            [repulse.theory :as theory]
            [repulse.lisp.eval :as leval]
            [clojure.string :as str]))

(def builtin-drums
  "Drum sounds synthesised by the audio engine without any sample bank."
  #{:bd :sd :hh :oh})

(def ^:private aliases
  "Common drum names from other tools, mapped to the REPuLse built-in."
  {"kick"        :bd
   "bassdrum"    :bd
   "bass-drum"   :bd
   "snare"       :sd
   "snaredrum"   :sd
   "snare-drum"  :sd
   "hat"         :hh
   "hihat"       :hh
   "hi-hat"      :hh
   "closedhat"   :hh
   "closed-hat"  :hh
   "openhat"     :oh
   "open-hat"    :oh
   "openhihat"   :oh
   "open-hihat"  :oh})

(defn sound-keywords
  "Distinct keywords a pattern plays as sounds over the first n cycles (default 4).
   Includes bare keyword values, :note keywords of synth-less parameter maps, and
   :bank keywords of (sound …) maps. Rests and note keywords are excluded."
  ([pat] (sound-keywords pat 4))
  ([pat n]
   ;; Query cycle by cycle, several of them so (every 4 …) branches show up.
   (->> (range n)
        (mapcat #(core/query pat (core/cycle-span %)))
        (keep (fn [{:keys [value]}]
                (cond
                  (keyword? value)                         value
                  (and (map? value) (:bank value))         (:bank value)
                  (and (map? value) (not (:synth value))
                       (keyword? (:note value)))           (:note value))))
        (remove #(or (= % :_) (theory/note-keyword? %)))
        distinct)))

(defn suggest
  "Most likely intended sound for an unknown keyword, or nil.
   Common aliases (kick → :bd) win; otherwise the closest name among the built-in
   drums and bank-names by edit distance, when close enough to be plausible."
  [kw bank-names]
  (let [n (name kw)]
    (or (get aliases (str/lower-case n))
        (let [candidates (concat (map name builtin-drums) bank-names)]
          (when-let [best (leval/typo-hint n candidates)]
            ;; Reject matches that need as many edits as the name is long — for
            ;; short names that is any two-letter bank.
            (when (< (leval/levenshtein n best) (count n))
              (keyword best)))))))

(defn unknown-sounds
  "Unknown sound keywords across patterns, each as {:name kw :suggestion kw-or-nil}.
   known-bank? — (fn [kw]) true when kw resolves to a registered sample bank.
   bank-names  — registered bank names (strings), used for suggestions."
  [patterns known-bank? bank-names]
  (->> patterns
       (mapcat #(try (sound-keywords %) (catch :default _ nil)))
       distinct
       (remove #(or (contains? builtin-drums %) (known-bank? %)))
       (mapv (fn [kw] {:name kw :suggestion (suggest kw bank-names)}))))

(defn warning-message
  "Human-readable warning for one {:name :suggestion} entry."
  [{:keys [name suggestion]}]
  (str "unknown sound :" (cljs.core/name name)
       (if suggestion
         (str " — did you mean :" (cljs.core/name suggestion) "?")
         " — plays a 440 Hz sine; see (sample-banks)")))

(defn keyword-ranges
  "Source ranges [{:from :to}] of every :kw token in code, for editor diagnostics.
   Matches whole tokens only — :kick does not match inside :kicker."
  [code kw]
  (let [token (str ":" (name kw))
        re    (js/RegExp. (str (str/replace token #"[.*+?^${}()|\[\]\\]" "\\$&")
                               "(?![A-Za-z0-9_\\-!?*+<>=./])")
                          "g")]
    (loop [acc []]
      (if-let [m (.exec re code)]
        (recur (conj acc {:from (.-index m) :to (+ (.-index m) (count token))}))
        acc))))
