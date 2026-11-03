(ns repulse.theory
  (:require [repulse.core :as core]))

;;; ── Note name parsing ────────────────────────────────────────────────

;; Note letter → semitone offset within octave (chromatic, used only for
;; parsing note names like :c4, :eb3 — not a scale numbering system).
(def ^:private note-semitones
  {"c" 0 "d" 2 "e" 4 "f" 5 "g" 7 "a" 9 "b" 11})

(defn note-keyword?
  "True if kw looks like a note name: a letter a–g, optional accidental (s=sharp, b=flat),
   and an optional octave number (defaults to 4 if omitted).
   Examples: :c4, :eb3, :fs5, :bb4, :cs-1, :a, :g, :bb."
  [kw]
  (and (keyword? kw)
       (boolean (re-matches #"[a-g][sb]?(-?\d+)?" (name kw)))))

(defn note->midi
  "Convert a note keyword to a MIDI note number.
   Convention: C4 = 60, A4 = 69.
   Accidentals: s = sharp (+1), b = flat (−1).
   Octave defaults to 4 if omitted.
   Examples: :c4 → 60, :a4 → 69, :a → 69, :eb3 → 51, :fs5 → 78, :bb4 → 70."
  [kw]
  (let [[_ letter acc oct-str] (re-matches #"([a-g])([sb])?(-?\d+)?" (name kw))
        semitone   (get note-semitones letter 0)
        accidental (case acc "s" 1 "b" -1 0)
        octave     (if oct-str (js/parseInt oct-str 10) 4)]
    (+ semitone accidental (* (+ octave 1) 12))))

(declare midi->hz)

(def ^:private midi-note-names
  ["c" "cs" "d" "ds" "e" "f" "fs" "g" "gs" "a" "as" "b"])

(defn midi->note
  "Convert an integer MIDI note number back to a note keyword (sharps spelling).
   Inverse of note->midi up to enharmonics: 60 → :c4, 61 → :cs4, 51 → :ds3.
   Non-integer input has no note name and returns its frequency in Hz."
  [midi]
  (if (integer? midi)
    (let [pc  (mod midi 12)
          oct (dec (quot (- midi pc) 12))]
      (keyword (str (nth midi-note-names pc) oct)))
    (midi->hz midi)))

(defn midi->hz
  "Convert a MIDI note number to a frequency in Hz.
   Uses equal temperament with A4 (MIDI 69) = 440 Hz."
  [midi]
  (* 440.0 (js/Math.pow 2 (/ (- midi 69) 12))))

(defn note->hz
  "Convert a note keyword directly to Hz. (:c4 → 261.63, :a4 → 440.0)"
  [kw]
  (midi->hz (note->midi kw)))

;;; ── Interval arithmetic ──────────────────────────────────────────────

;; Scales and chords are defined using standard music-theory interval names
;; rather than raw semitone offsets, so that definitions read as musicians
;; write them. A plain integer n means the nth diatonic degree (no alteration).
;; Keyword :b3 means "flat 3", :s4 means "sharp 4", :bb7 means "double flat 7".
;;
;; Interval name → semitone offset from root:
(def ^:private interval->semitones
  {1    0    ; unison / root
   :b2  1    ; minor second
   2    2    ; major second
   :b3  3    ; minor third
   3    4    ; major third
   4    5    ; perfect fourth
   :s4  6    ; augmented fourth (tritone)
   :b5  6    ; diminished fifth  (enharmonic with :s4)
   5    7    ; perfect fifth
   :s5  8    ; augmented fifth
   :b6  8    ; minor sixth       (enharmonic with :s5)
   6    9    ; major sixth
   :bb7 9    ; diminished seventh (double flat — enharmonic with 6)
   :b7  10   ; minor seventh
   7    11   ; major seventh
   8    12}) ; octave

(defn- resolve-intervals
  "Convert a vector of interval names to semitone offsets."
  [ivs]
  (mapv #(get interval->semitones % 0) ivs))

;;; ── Scale tables ─────────────────────────────────────────────────────

;; Defined in interval notation — each entry reads exactly as a musician
;; would describe the scale formula.
(def ^:private scale-intervals
  {:major            [1 2  3   4   5  6   7  ]
   :ionian           [1 2  3   4   5  6   7  ]   ; alias
   :minor            [1 2  :b3 4   5  :b6 :b7]
   :aeolian          [1 2  :b3 4   5  :b6 :b7]   ; alias
   :dorian           [1 2  :b3 4   5  6   :b7]
   :phrygian         [1 :b2 :b3 4  5  :b6 :b7]
   :lydian           [1 2  3   :s4 5  6   7  ]
   :mixolydian       [1 2  3   4   5  6   :b7]
   :locrian          [1 :b2 :b3 4  :b5 :b6 :b7]
   :pentatonic       [1 2  3   5   6          ]
   :minor-pentatonic [1 :b3 4  5   :b7        ]
   :blues            [1 :b3 4  :s4 5   :b7    ]
   ;; TL5
   :harmonic-minor    [1 2 :b3 4 5 :b6 7]
   :melodic-minor     [1 2 :b3 4 5 6 7]
   :whole-tone        [1 2 3 :s4 :s5 :b7]
   :chromatic         [1 :b2 2 :b3 3 4 :s4 5 :b6 6 :b7 7]
   :major-pentatonic  [1 2 3 5 6]
   :hirajoshi         [1 2 :b3 5 :b6]
   :phrygian-dominant [1 :b2 3 4 5 :b6 :b7]
   :lydian-dominant   [1 2 3 :s4 5 6 :b7]
   :altered           [1 :b2 :b3 3 :b5 :b6 :b7]
   :diminished        [1 2 :b3 4 :b5 :b6 6 7]
   :bebop-dominant    [1 2 3 4 5 6 :b7 7]})

(defn scale-names
  "Sorted list of known scale names."
  []
  (sort (keys scale-intervals)))

;;; ── Chord tables ─────────────────────────────────────────────────────

;; Interval notation makes chord formulas self-documenting. :m7b5 reads as
;; "minor 7, flat 5" — no lookup required to understand the voicing.
(def ^:private chord-intervals
  {:major    [1 3   5           ]   ; major triad
   :minor    [1 :b3 5           ]   ; minor triad
   :major7   [1 3   5   7       ]   ; major 7th
   :minor7   [1 :b3 5   :b7     ]   ; minor 7th
   :dom7     [1 3   5   :b7     ]   ; dominant 7th
   :m7b5     [1 :b3 :b5 :b7     ]   ; half-diminished (minor 7 flat 5)
   :dim      [1 :b3 :b5         ]   ; diminished triad
   :dim7     [1 :b3 :b5 :bb7    ]   ; fully diminished 7th
   :aug      [1 3   :s5         ]   ; augmented triad
   :aug7     [1 3   :s5 :b7     ]   ; augmented dominant 7th
   :maj7s11  [1 3   :s4 5  7    ]   ; major 7 sharp 11 (Lydian chord)
   :sus2     [1 2   5           ]   ; suspended 2nd
   :sus4     [1 4   5           ]}) ; suspended 4th

;;; ── Helpers ──────────────────────────────────────────────────────────

(defn- deg-val
  "Numeric value of a number or [n d] rational; nil otherwise."
  [x]
  (cond (number? x) x
        (and (vector? x) (= 2 (count x)) (number? (first x)) (number? (second x)))
        (/ (first x) (second x))
        :else nil))

(defn- ->midi [x]
  (cond (number? x) x
        (note-keyword? x) (note->midi x)
        :else (throw (ex-info (str "expected a note or MIDI number, got " (pr-str x))
                              {:type :eval-error}))))

(defn- fan-out
  "Replace each event's value by every value of (f v) (a vector, or nil to drop
   the event). Whole, part and :source are kept; rests pass through."
  [f pat]
  (core/pattern
   (fn [sp]
     (mapcat (fn [ev]
               (let [v (:value ev)]
                 (if (= v :_)
                   [ev]
                   (map #(assoc ev :value %) (f v)))))
             (core/query pat sp)))))

;;; ── Scales ───────────────────────────────────────────────────────────

(defn- scale-semis [name]
  (if (vector? name)
    name
    (resolve-intervals (get scale-intervals name [1 2 3 4 5 6 7]))))

(defn- degree->midi [semis root-midi d]
  (let [n   (count semis)
        d   (int (js/Math.floor d))
        oct (int (js/Math.floor (/ d n)))]
    (+ root-midi (* 12 oct) (nth semis (- d (* oct n))))))

(defn- scale* [name root pat]
  (let [semis (scale-semis name)
        r     (note->midi root)
        f     (fn [v] (if-let [d (deg-val v)] (midi->note (degree->midi semis r d)) v))]
    (core/fmap #(if (and (map? %) (deg-val (:note %))) (update % :note f) (f %)) pat)))

(defn scale
  "Map 0-based scale degrees in pat to note keywords. Degree 0 is the root;
   negative degrees go below it, degrees beyond the scale wrap into octaves.
   name is a scale keyword, a vector of semitone offsets, or a pattern of
   names; root is a note keyword or pattern of them.

   (scale :minor :c4 (seq 0 2 4 7))   ; :c4 :ds4 :g4 :c5"
  [name root pat]
  ((core/patternify2 scale*) name root pat))

(defn- scale-transpose* [n sc pat]
  (let [[name root] (cond (and (vector? sc) (keyword? (first sc))) [(first sc) (or (second sc) :c)]
                          :else [sc :c])
        semis (scale-semis name)
        n     (int (js/Math.floor (deg-val n)))
        rpc   (mod (note->midi root) 12)
        step  (fn [m]
                (let [rel (- m rpc)
                      oct (int (js/Math.floor (/ rel 12)))
                      pc  (- rel (* 12 oct))
                      idx (last (keep-indexed (fn [i s] (when (<= s pc) i)) semis))]
                  (degree->midi semis (+ rpc (* 12 oct)) (+ idx n))))
        f     (fn [v] (cond (deg-val v)        (+ (deg-val v) n)
                            (note-keyword? v)  (midi->note (step (note->midi v)))
                            :else              v))]
    (core/fmap #(if (and (map? %) (:note %)) (update % :note f) (f %)) pat)))

(defn scale-transpose
  "Move degrees (numbers) or notes (keywords) n steps inside a scale.
   sc: scale keyword, [scale root], or a vector of semitone offsets."
  [n sc pat]
  ((core/patternify (fn [n' p] (scale-transpose* n' sc p))) n pat))

;;; ── Transpose ────────────────────────────────────────────────────────

(defn interval->semitones-str
  "Parse an interval like \"3M\", \"5P\", \"-2M\", \"4A\", \"9m\" into semitones.
   Returns nil for anything invalid (e.g. \"3P\", \"5M\")."
  [s]
  (when-let [[_ sign num q] (re-matches #"(-?)(\d+)([PMmAd])" s)]
    (let [n (js/parseInt num 10)]
      (when (>= n 1)
        (let [idx     (mod (dec n) 7)
              base    (+ (nth [0 2 4 5 7 9 11] idx) (* 12 (quot (dec n) 7)))
              perfect (contains? #{0 3 4} idx)
              adj     (if perfect
                        (case q "P" 0 "A" 1 "d" -1 nil)
                        (case q "M" 0 "m" -1 "A" 1 "d" -2 nil))]
          (when adj
            (let [semis (+ base adj)]
              (if (= sign "-") (- semis) semis))))))))

(defn- transpose* [n pat]
  (let [n     (deg-val n)
        ratio (js/Math.pow 2 (/ n 12))
        shift (fn [v] (cond (number? v)       (* v ratio)
                            (note-keyword? v) (midi->note (+ (note->midi v) n))
                            :else             v))]
    (core/fmap #(if (and (map? %) (:note %)) (update % :note shift) (shift %)) pat)))

(defn transpose
  "Shift values in pat by n semitones (n may be a pattern). Note keywords stay
   note keywords; plain numbers are frequencies and scale by 2^(n/12); maps
   with a :note key are updated; other keywords pass through.

   (transpose 12 (seq :c4 :e4 :g4))   ; up one octave"
  [n pat]
  ((core/patternify transpose*) n pat))

;;; ── Chords ───────────────────────────────────────────────────────────

(defn chord
  "Stacked pattern of chord tones as note keywords (one whole-cycle event per tone).
   Optional source map is attached to every event for editor highlighting.

   (chord :major :c4)    ; C E G stacked"
  ([chord-kw root] (chord chord-kw root nil))
  ([chord-kw root source]
   (let [root-midi (note->midi root)
         semitones (resolve-intervals (get chord-intervals chord-kw [1 3 5]))]
     (core/stack* (mapv #(core/pure (midi->note (+ root-midi %)) source) semitones)))))

(def chord-qualities
  "Chord-symbol qualities: [aliases semitones-from-root]. Order matters for
   error suggestions."
  [[["" "M" "maj"] [0 4 7]]
   [["m" "min" "-"] [0 3 7]]
   [["dim" "o"] [0 3 6]]
   [["aug" "+"] [0 4 8]]
   [["sus2"] [0 2 7]]
   [["sus4" "sus"] [0 5 7]]
   [["5"] [0 7]]
   [["6"] [0 4 7 9]]
   [["m6" "-6"] [0 3 7 9]]
   [["69"] [0 4 7 9 14]]
   [["add9" "add2"] [0 4 7 14]]
   [["madd9"] [0 3 7 14]]
   [["7"] [0 4 7 10]]
   [["^7" "^" "maj7" "M7" "Δ" "Δ7"] [0 4 7 11]]
   [["m7" "-7" "min7"] [0 3 7 10]]
   [["m^7" "mM7" "mmaj7" "-^7"] [0 3 7 11]]
   [["m7b5" "h7" "h" "ø" "ø7" "-7b5"] [0 3 6 10]]
   [["o7" "dim7"] [0 3 6 9]]
   [["7sus4" "7sus"] [0 5 7 10]]
   [["+7" "7#5" "aug7"] [0 4 8 10]]
   [["7b5"] [0 4 6 10]]
   [["9"] [0 4 7 10 14]]
   [["^9" "maj9" "M9"] [0 4 7 11 14]]
   [["m9" "-9"] [0 3 7 10 14]]
   [["7b9"] [0 4 7 10 13]]
   [["7#9"] [0 4 7 10 15]]
   [["9sus4" "9sus"] [0 5 7 10 14]]
   [["11"] [0 7 10 14 17]]
   [["m11" "-11"] [0 3 7 10 14 17]]
   [["7#11"] [0 4 7 10 18]]
   [["^7#11" "maj7#11"] [0 4 7 11 18]]
   [["13"] [0 4 7 10 14 21]]
   [["m13" "-13"] [0 3 7 10 14 21]]
   [["7b13"] [0 4 7 10 20]]])

(def ^:private quality-by-alias
  (into {} (for [[aliases semis] chord-qualities, a aliases] [a semis])))

(def ^:private letter-semitones {"C" 0 "D" 2 "E" 4 "F" 5 "G" 7 "A" 9 "B" 11})

(defn- acc-offset [acc] (case acc "#" 1 "b" -1 0))

(defn- symbol-text [v]
  (cond (string? v)  v
        (keyword? v) (subs (str v) 1)
        :else        nil))

(defn parse-chord-symbol
  "Parse a chord symbol (string or keyword) into {:root midi :intervals [...] :bass midi-or-nil}.
   Returns nil when the symbol is not understood. Root is placed in octave 4."
  [v]
  (when-let [text (symbol-text v)]
    (let [[_ main bass-txt] (or (re-matches #"(.*)/([A-G][#b]?)" text) [nil text nil])]
      (when-let [[_ letter acc suffix] (re-matches #"([A-G])([#b]?)(.*)" main)]
        (when-let [semis (get quality-by-alias suffix)]
          (let [root (+ 60 (get letter-semitones letter) (acc-offset acc))
                bass (when bass-txt
                       (let [pc (mod (+ (get letter-semitones (subs bass-txt 0 1))
                                        (acc-offset (subs bass-txt 1))) 12)]
                         (loop [m (dec root)]
                           (if (= (mod m 12) pc) m (recur (dec m))))))]
            {:root root :intervals semis :bass bass}))))))

(defn chord-symbol->notes
  "Ascending MIDI vector for a chord symbol, or nil."
  [v]
  (when-let [{:keys [root intervals bass]} (parse-chord-symbol v)]
    (vec (concat (when bass [bass]) (map #(+ root %) intervals)))))

(defn- edit-distance [a b]
  (let [a (vec a) b (vec b)]
    (peek
     (reduce (fn [prev i]
               (reduce (fn [row j]
                         (conj row (min (inc (peek row))
                                        (inc (nth prev (inc j)))
                                        (+ (nth prev j) (if (= (nth a i) (nth b j)) 0 1)))))
                       [(inc i)]
                       (range (count b))))
             (vec (range (inc (count b))))
             (range (count a))))))

(defn chord-symbol-error
  "Error message for an unparseable chord symbol, with a did-you-mean hint."
  [v]
  (let [text (or (symbol-text v) (str v))
        [_ root suffix] (re-matches #"([A-G][#b]?)(.*)" text)
        best (when suffix
               (->> (for [[aliases _] chord-qualities, a aliases :when (seq a)]
                      [(edit-distance suffix a) a])
                    (reduce (fn [b c] (if (and (<= (first c) 2) (or (nil? b) (< (first c) (first b)))) c b))
                            nil)))]
    (str "unknown chord symbol \"" text "\""
         (when best (str " — did you mean \"" root (second best) "\"?")))))

(defn chord-symbols
  "Pattern of chord symbols → one note-keyword event per chord tone (root position)."
  [pat]
  (fan-out #(some->> (chord-symbol->notes %) (mapv midi->note)) pat))

;;; ── Voicing ──────────────────────────────────────────────────────────

(defn voiced-set
  "Reduce intervals to ≤ 4 pitch classes: drop the fifth, then the root."
  [semis]
  (loop [pcs (vec (distinct (map #(mod % 12) semis)))]
    (if (<= (count pcs) 4)
      (vec (sort pcs))
      (recur (if (some #{7} pcs)
               (vec (remove #{7} pcs))
               (vec (remove #{0} pcs)))))))

(defn- inversions [vset]
  (vec (for [k (range (count vset))]
         (vec (concat (drop k vset) (map #(+ % 12) (take k vset)))))))

(defn- voice-symbol
  "Voice a chord symbol as ascending MIDI numbers, or nil if unknown."
  [v anchor mode offset]
  (when-let [{:keys [root intervals bass]} (parse-chord-symbol v)]
    (let [r     (mod root 12)
          vset  (voiced-set intervals)
          voic  (if (= mode :root)
                  [(if (some #{0} vset) (first (inversions vset)) (into [0] (first (inversions vset))))]
                  (inversions vset))
          cands (vec (for [vc voic, k (range -1 11)
                           :let [notes (mapv #(+ r (* 12 k) %) vc)]
                           :when (every? #(<= 0 % 127) notes)]
                       notes))
          top   peek
          bot   first
          pick  (fn [pred key-fn better?]
                  (reduce (fn [best c]
                            (if (pred c)
                              (if (or (nil? best) (better? (key-fn c) (key-fn best))) c best)
                              best))
                          nil cands))
          sel   (case mode
                  :below (pick #(<= (top %) anchor) top >)
                  :duck  (pick #(< (top %) anchor) top >)
                  :above (pick #(>= (bot %) anchor) bot <)
                  :root  (pick #(<= (bot %) anchor) bot >))]
      (when sel
        (let [sorted (vec (sort-by (juxt top bot) cands))
              idx    (or (first (keep-indexed (fn [i c] (when (= c sel) i)) sorted)) -1)
              sel    (if (and (not (zero? offset)) (>= idx 0))
                       (nth sorted (max 0 (min (dec (count sorted)) (+ idx offset))))
                       sel)
              low    (first sel)]
          (if bass
            (let [bpc (mod bass 12)
                  b   (loop [m (dec low)] (if (= (mod m 12) bpc) m (recur (dec m))))]
              (into [b] sel))
            sel))))))

(def voicing-modes #{:below :above :duck :root})

(defn voicing
  "Voice chord symbols with anchor-based voice leading.
   Options: :anchor (note, default :c5), :mode (:below :above :duck :root), :offset."
  ([pat] (voicing {} pat))
  ([{:keys [anchor mode offset] :or {anchor :c5 mode :below offset 0}} pat]
   (let [a (->midi anchor)]
     (fan-out #(some->> (voice-symbol % a mode offset) (mapv midi->note)) pat))))

(defn- root-notes* [oct pat]
  (let [o (int (js/Math.floor (deg-val oct)))]
    (fan-out #(when-let [{:keys [root]} (parse-chord-symbol %)]
                [(midi->note (+ (mod root 12) (* 12 (inc o))))])
             pat)))

(defn root-notes
  "Chord symbol → its root as a note keyword in octave oct (default 2)."
  ([pat] (root-notes 2 pat))
  ([oct pat] ((core/patternify root-notes*) oct pat)))
