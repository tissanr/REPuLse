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
  (boolean (re-matches #"[a-g][sb]?(-?\d+)?" (name kw))))

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
   :blues            [1 :b3 4  :s4 5   :b7    ]})

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

;;; ── Pattern combinators ──────────────────────────────────────────────

(defn scale
  "Map scale degree integers in pat to Hz frequencies.
   Degrees are zero-indexed from the root; values outside [0, n) wrap
   into higher/lower octaves (e.g. degree 7 in a 7-note scale = root + 1 octave).

   (scale :minor :c4 (seq 0 2 4 7))
   (scale :pentatonic :g3 (fast 2 (seq 0 1 2 3 4)))"
  [scale-kw root pat]
  (let [root-midi  (note->midi root)
        semitones  (resolve-intervals (get scale-intervals scale-kw [1 2 3 4 5 6 7]))
        n          (count semitones)
        degree->hz (fn [degree]
                     (let [degree (int degree)
                           oct    (int (js/Math.floor (/ degree n)))
                           idx    (- degree (* oct n))]
                       (midi->hz (+ root-midi (* oct 12) (nth semitones idx)))))]
    (core/fmap degree->hz pat)))

(defn chord
  "Return a stacked pattern of the chord tones as Hz values.
   Each tone is a (pure hz) pattern lasting one full cycle.

   (chord :major :c4)    ; C E G stacked
   (chord :m7b5 :b3)     ; half-diminished on B3"
  [chord-kw root]
  (let [root-midi (note->midi root)
        semitones (resolve-intervals (get chord-intervals chord-kw [1 3 5]))
        freqs     (map #(midi->hz (+ root-midi %)) semitones)]
    (core/stack* (mapv core/pure freqs))))

(defn transpose
  "Shift note values in pat up or down by n semitones.
   Works on Hz numbers, note keywords (:c4, :eb3, …), and parameter maps
   with a :note key. Non-note keywords (drums, rests) are passed through.

   (transpose 12 (seq :c4 :e4 :g4))   ; up one octave
   (transpose -7 (scale :major :c5 (seq 0 1 2 3)))"
  [semitones pat]
  (let [ratio  (js/Math.pow 2 (/ semitones 12))
        shift  (fn [v]
                 (cond
                   (number? v)       (* v ratio)
                   (note-keyword? v) (* (note->hz v) ratio)
                   :else             v))]
    (core/fmap (fn [v]
                 (if (and (map? v) (:note v))
                   (update v :note shift)
                   (shift v)))
               pat)))
