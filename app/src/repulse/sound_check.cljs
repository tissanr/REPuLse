(ns repulse.sound-check
  "Unknown sound-name detection for evaluated patterns.
   Responsibility: find keyword event values that are neither note keywords,
   built-in drums, nor registered sample banks, and suggest a likely intended
   name. Unknown names otherwise fall through to a quiet 440 Hz sine, which is
   easy to mistake for silence.
   Pure — sample-bank lookups are injected by the caller.
   Also detects continuous signals with no rhythm, which never sound.
   Also detects unknown (synth …) voice names (Phase SYN6), which play a sine.
   Exports: builtin-drums, sound-keywords, suggest, unknown-sounds, warning-message,
            synth-voice-names, unknown-voices, voice-warning-message,
            keyword-ranges, has-analog-events?, structure-warning-message"
  (:require [repulse.core :as core]
            [repulse.theory :as theory]
            [repulse.voices :as voices]
            [repulse.lisp.eval :as leval]
            [clojure.string :as str]))

(def builtin-drums
  "Drum sounds synthesised by the audio engine without any sample bank: the
   SYN6 kit (repulse.voices/drum-voices)."
  (set (map keyword voices/drum-voices)))

(defn- synth-drum-kw?
  "True for :synth_<drum> — a kit voice addressed inside any bank (SYN6)."
  [kw]
  (let [n (name kw)]
    (and (str/starts-with? n "synth_") (voices/drum-name? (subs n 6)))))

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
   known-bank? — (fn [kw]) true when kw resolves to something playable: a
                 registered sample bank or a defsynth name.
   bank-names  — registered bank names (strings), used for suggestions."
  [patterns known-bank? bank-names]
  (->> patterns
       (mapcat #(try (vec (sound-keywords %)) (catch :default _ nil)))
       distinct
       (remove #(or (contains? builtin-drums %) (synth-drum-kw? %) (known-bank? %)))
       (mapv (fn [kw] {:name kw :suggestion (suggest kw bank-names)}))))

;;; Unknown synth voices (Phase SYN6, D2)

(defn synth-voice-names
  "Distinct (:synth value) keywords a pattern plays over the first n cycles."
  ([pat] (synth-voice-names pat 4))
  ([pat n]
   (->> (range n)
        (mapcat #(core/query pat (core/cycle-span %)))
        (keep (fn [{:keys [value]}]
                (when (and (map? value) (keyword? (:synth value)))
                  (:synth value))))
        distinct)))

(defn unknown-voices
  "Unknown (synth …) voices across patterns, each as {:name kw :suggestion kw-or-nil}.
   known-voice? — (fn [kw]) true for a built-in voice or a defsynth name."
  [patterns known-voice?]
  (->> patterns
       (mapcat #(try (vec (synth-voice-names %)) (catch :default _ nil)))
       distinct
       (remove known-voice?)
       (mapv (fn [kw]
               (let [n    (name kw)
                     best (leval/typo-hint n (map name voices/synth-voices))]
                 {:name       kw
                  :suggestion (when (and best (< (leval/levenshtein n best) (count n)))
                                (keyword best))})))))

(defn voice-warning-message
  "Human-readable warning for one unknown voice {:name :suggestion}."
  [{:keys [name suggestion]}]
  (str "unknown synth voice :" (cljs.core/name name)
       (if suggestion
         (str " — did you mean :" (cljs.core/name suggestion) "?")
         " — plays a sine")))

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

(defn probe-error
  "Pure (QA1 decision 3). Query every track [[kw pat] …] for cycle 0 and
   realise the events. nil when all succeed, else the first failure as
   {:track kw :message msg} plus :from/:to when the error carries a position."
  [tracks]
  (some (fn [[kw pat]]
          (try (dorun (core/query pat (core/cycle-span 0)))
               (catch :default e
                 (let [{:keys [from to]} (ex-data e)]
                   (cond-> {:track kw :message (or (ex-message e) (str e))}
                     (and from to) (assoc :from from :to to))))))
        tracks))

(defn has-analog-events?
  "True when pat yields any continuous (whole-less) event in cycle 0.
   Such events never trigger sound: a signal needs structure."
  [pat]
  (boolean (some core/analog? (core/query pat (core/cycle-span 0)))))

(defn structure-warning-message [track-name]
  (str "track :" (name track-name) " contains a continuous signal with no rhythm, "
       "which plays nothing. A signal needs structure, e.g. (segment 8 …) "
       "or (pan sine pat)"))
