(ns repulse.theory-test
  (:require [cljs.test :refer-macros [deftest is]]
            [repulse.theory :as theory]
            [repulse.core :as core]))

(def ^:private one-cycle
  (core/span (core/int->rat 0) (core/int->rat 1)))

;;; ── Note keyword predicate ───────────────────────────────────────────

(deftest note-keyword-predicate
  (is (theory/note-keyword? :c4))
  (is (theory/note-keyword? :eb3))
  (is (theory/note-keyword? :fs5))
  (is (theory/note-keyword? :bb4))
  (is (theory/note-keyword? :cs-1))
  ;; bare note names (no octave) — default to octave 4
  (is (theory/note-keyword? :a))
  (is (theory/note-keyword? :c))
  (is (theory/note-keyword? :g))
  (is (theory/note-keyword? :bb))
  (is (not (theory/note-keyword? :bd)))
  (is (not (theory/note-keyword? :sd)))
  (is (not (theory/note-keyword? :_)))
  (is (not (theory/note-keyword? :hh))))

;;; ── note->midi ───────────────────────────────────────────────────────

(deftest note->midi-naturals
  (is (= 60 (theory/note->midi :c4)))   ; middle C
  (is (= 69 (theory/note->midi :a4)))   ; concert A
  (is (= 48 (theory/note->midi :c3)))
  (is (= 72 (theory/note->midi :c5))))

(deftest note->midi-bare-letter
  ;; bare note names default to octave 4
  (is (= 69 (theory/note->midi :a)))    ; :a = :a4 = 69
  (is (= 60 (theory/note->midi :c)))    ; :c = :c4 = 60
  (is (= 62 (theory/note->midi :d)))    ; :d = :d4 = 62
  (is (= 70 (theory/note->midi :bb)))   ; :bb = :bb4 = 70
  ;; bare :a and :a4 are identical
  (is (= (theory/note->midi :a) (theory/note->midi :a4))))

(deftest note->midi-accidentals
  (is (= 61 (theory/note->midi :cs4)))  ; C-sharp 4
  (is (= 63 (theory/note->midi :eb4)))  ; E-flat 4
  (is (= 70 (theory/note->midi :bb4)))  ; B-flat 4
  (is (= 66 (theory/note->midi :fs4)))  ; F-sharp 4
  (is (= 58 (theory/note->midi :bb3)))) ; B-flat 3

;;; ── midi->hz ─────────────────────────────────────────────────────────

(deftest midi->hz-concert-a
  (is (= 440.0 (theory/midi->hz 69))))

(deftest midi->hz-middle-c
  ;; C4 ≈ 261.626 Hz
  (is (< (js/Math.abs (- 261.626 (theory/midi->hz 60))) 0.01)))

(deftest midi->hz-octave-doubles
  ;; Each octave up doubles the frequency
  (let [c4 (theory/midi->hz 60)
        c5 (theory/midi->hz 72)]
    (is (< (js/Math.abs (- (* 2 c4) c5)) 0.01))))

;;; ── note->hz ─────────────────────────────────────────────────────────

(deftest note->hz-concert-a
  (is (= 440.0 (theory/note->hz :a4))))

(deftest note->hz-middle-c
  (is (< (js/Math.abs (- 261.626 (theory/note->hz :c4))) 0.01)))

;;; ── helpers ──────────────────────────────────────────────────────────

(defn- cyc [pat c] (core/query pat (core/cycle-span c)))
(defn- onsets [pat c] (filter core/onset? (cyc pat c)))
(defn- midis [pat c] (mapv #(theory/note->midi (:value %)) (onsets pat c)))
(defn- by-start [evs]
  (sort-by (fn [e] (core/rat->float (:start (:whole e)))) evs))
(defn- tones [pat] (sort (map #(theory/note->midi (:value %)) (cyc pat 0))))

;;; ── scale (0-based, note keywords) ───────────────────────────────────

(deftest scale-minor-degrees
  (is (= [:c4 :ds4 :g4 :c5]
         (mapv :value (by-start (cyc (theory/scale :minor :c4 (core/seq* [0 2 4 7])) 0)))))
  (is (= [60 63 67 72] (midis (theory/scale :minor :c4 (core/seq* [0 2 4 7])) 0))))

(deftest scale-zero-based
  (is (= [62] (midis (theory/scale :major :c4 (core/seq* [1])) 0)))
  (is (= [58] (midis (theory/scale :minor :c4 (core/seq* [-1])) 0)))
  (is (= [60 72 84] (midis (theory/scale :major :c4 (core/seq* [0 7 14])) 0)))
  (is (= [62] (midis (theory/scale :minor :c4 (core/seq* [1.7])) 0))))

(deftest scale-passes-rests
  (let [evs (by-start (onsets (theory/scale :minor :c4 (core/seq* [:_ 0])) 0))]
    (is (= [:_ :c4] (mapv :value evs)))))

(deftest scale-patterned-and-vector
  (is (= [64] (midis (theory/scale (core/cat* [(core/pure :major) (core/pure :minor)]) :c4 (core/seq* [2])) 0)))
  (is (= [63] (midis (theory/scale (core/cat* [(core/pure :major) (core/pure :minor)]) :c4 (core/seq* [2])) 1)))
  (is (= [60 62 64] (midis (theory/scale [0 2 4 5 7 9 11] :c4 (core/seq* [0 1 2])) 0)))
  (is (= [50 53] (midis (theory/scale :dorian :d3 (core/seq* [0 2])) 0))))

(deftest scale-new-scales
  (is (= [71] (midis (theory/scale :harmonic-minor :c4 (core/seq* [6])) 0)))
  (is (= [70] (midis (theory/scale :whole-tone :c4 (core/seq* [5])) 0)))
  (is (= [73] (midis (theory/scale :chromatic :c4 (core/seq* [13])) 0)))
  (is (= [64] (midis (theory/scale :altered :c4 (core/seq* [3])) 0)))
  (is (= [72] (midis (theory/scale :bebop-dominant :c4 (core/seq* [8])) 0)))
  (doseq [k [:melodic-minor :major-pentatonic :hirajoshi :phrygian-dominant
             :lydian-dominant :diminished]]
    (is (some #{k} (theory/scale-names)))))

;;; ── scale-transpose ──────────────────────────────────────────────────

(defn- evals [pat c] (mapv :value (by-start (onsets pat c))))

(deftest scale-transpose-degrees
  (is (= [2 4 6] (evals (theory/scale-transpose 2 :minor (core/seq* [0 2 4])) 0)))
  (is (= [60 63 67] (let [p (theory/scale :minor :c4 (theory/scale-transpose 0 :minor (core/seq* [0 2 4])))] (midis p 0))))
  (is (= [63 67 70]
         (midis (theory/scale :minor :c4 (theory/scale-transpose 2 :minor (core/seq* [0 2 4]))) 0)))
  (let [p (theory/scale-transpose (core/cat* [(core/pure 0) (core/pure 2)]) :minor (core/seq* [0 2 4]))]
    (is (= [0 2 4] (evals p 0)))
    (is (= [2 4 6] (evals p 1)))))

(deftest scale-transpose-notes
  (is (= [62 65 68] (midis (theory/scale-transpose 1 :minor (core/seq* [:c4 :eb4 :g4])) 0)))
  (is (= [59] (midis (theory/scale-transpose -1 :major (core/seq* [:c4])) 0)))
  (is (= [:e4 :f4] (evals (theory/scale-transpose 1 [:dorian :d] (core/seq* [:d4 :e4])) 0)))
  (is (= [:c4] (evals (theory/scale-transpose 0 :major (core/seq* [:cs4])) 0)))
  (is (= [:bd] (evals (theory/scale-transpose 1 :major (core/seq* [:bd])) 0))))

;;; ── chord (keywords) ─────────────────────────────────────────────────

(deftest chord-major-triad
  (is (= [60 64 67] (tones (theory/chord :major :c4)))))

(deftest chord-minor7
  (is (= [57 60 64 67] (tones (theory/chord :minor7 :a3))))
  (is (= [:c4 :ds4 :g4 :as4] (mapv :value (cyc (theory/chord :minor7 :c4) 0)))))

(deftest chord-dim7
  (is (= [60 63 66 69] (tones (theory/chord :dim7 :c4)))))

;;; ── chord symbols ────────────────────────────────────────────────────

(defn- sym [s] (theory/chord-symbol->notes s))

(deftest chord-symbol-qualities-table
  (let [expect (fn [alias semis] (is (= (mapv #(+ 60 %) semis) (sym (str "C" alias))) alias))]
    (doseq [[aliases semis] theory/chord-qualities, a aliases]
      (expect a semis))
    (is (= 34 (count theory/chord-qualities)))))

(deftest chord-symbol-examples
  (is (= [60 64 67] (sym "C")))
  (is (= [60 63 67 70] (sym "Cm7")))
  (is (= (sym "C^7") (sym "Cmaj7") (sym "CM7") [60 64 67 71]))
  (is (= [60 63 66 69] (sym "Co7")))
  (is (= (sym "Ch7") (sym "Cm7b5") [60 63 66 70]))
  (is (= [60 64 67 69 74] (sym "C69")))
  (is (= [66 70 73 76 79] (sym "F#7b9")))
  (is (= [70 74 77 80] (sym "Bb7")))
  (is (= [69 73 76 79 89] (sym "A7b13")))
  (is (= [67 71 74 77 81 88] (sym "G13")))
  (is (= [52 60 64 67] (sym "C/E")))
  (is (= [67 69 72 76] (sym "Am/G")))
  (is (= [59 63 66] (sym "Cb")))
  (is (= [60 64 67] (sym :C)))
  (is (= [60 63 67 70] (sym :Cm7)))
  (is (nil? (sym "Cxyz")))
  (is (nil? (sym "c4")))
  (is (nil? (sym 5))))

(deftest chord-symbol-error-messages
  (is (= "unknown chord symbol \"Cmj7\" — did you mean \"Cmaj7\"?" (theory/chord-symbol-error "Cmj7")))
  (is (= "unknown chord symbol \"Cxyz\"" (theory/chord-symbol-error "Cxyz"))))

(deftest chord-symbols-pattern
  (let [p (theory/chord-symbols (core/pure "Cm7"))]
    (is (= [:c4 :ds4 :g4 :as4] (mapv :value (cyc p 0))))
    (is (every? #(= {:start [0 1] :end [1 1]} (:whole %)) (cyc p 0))))
  (is (= [:_] (mapv :value (cyc (theory/chord-symbols (core/seq* [:_ "Cxyz"])) 0))) "unknown dropped, rest kept"))

;;; ── voicing ──────────────────────────────────────────────────────────

(deftest voiced-set-column
  (let [expected [[0 4 7] [0 3 7] [0 3 6] [0 4 8] [0 2 7] [0 5 7] [0 7] [0 4 7 9] [0 3 7 9]
                  [0 2 4 9] [0 2 4 7] [0 2 3 7] [0 4 7 10] [0 4 7 11] [0 3 7 10] [0 3 7 11]
                  [0 3 6 10] [0 3 6 9] [0 5 7 10] [0 4 8 10] [0 4 6 10] [0 2 4 10] [0 2 4 11]
                  [0 2 3 10] [0 1 4 10] [0 3 4 10] [0 2 5 10] [0 2 5 10] [2 3 5 10] [0 4 6 10]
                  [0 4 6 11] [2 4 9 10] [2 3 9 10] [0 4 8 10]]]
    (is (= expected (mapv (fn [[_ semis]] (theory/voiced-set semis)) theory/chord-qualities)))))

(defn- voiced [opts s] (midis (theory/voicing opts (core/pure s)) 0))

(deftest voicing-progression
  (let [p (theory/voicing (core/seq* ["Dm7" "G7" "C^7"]))
        vs (mapv (fn [i] (sort (map #(theory/note->midi (:value %))
                                    (filter #(= (core/span (core/rat i 3) (core/rat (inc i) 3)) (:whole %)) (cyc p 0)))))
                 (range 3))]
    (is (= [[62 65 69 72] [62 65 67 71] [64 67 71 72]] vs))
    (doseq [[a b] (partition 2 1 vs)]
      (is (<= (apply max (map #(js/Math.abs (- %1 %2)) a b)) 4)))))

(deftest voicing-cycle-progression
  (let [p (theory/voicing (core/cat* (mapv #(core/pure %) ["C^7" "A7b13" "Dm7" "G7"])))]
    (is (= [[64 67 71 72] [61 65 67 69] [62 65 69 72] [62 65 67 71]]
           (mapv #(vec (sort (midis p %))) (range 4))))))

(deftest voicing-options
  (is (= [64 67 72] (voiced {} "C")))
  (is (= [60 64 67] (voiced {:mode :above :anchor :c4} "C")))
  (is (= [60 62 65 69] (voiced {:mode :duck} "Dm7")))
  (is (= [65 69 72 74] (voiced {:offset 1} "Dm7")))
  (is (= [60 62 65 69] (voiced {:offset -1} "Dm7")))
  (is (= [55 59 62 65] (voiced {:mode :root :anchor :c4} "G7")))
  (is (= [52 64 67 72] (voiced {} "C/E")))
  (is (<= (apply max (voiced {:anchor :c4} "Dm7")) 60)))

(deftest root-notes-test
  (is (= [:c2] (mapv :value (cyc (theory/root-notes (core/pure "Cm7")) 0))))
  (is (= [:as3 :ds3] (mapv :value (by-start (cyc (theory/root-notes 3 (core/seq* ["Bb7" "Eb^7"])) 0)))))
  (is (= [:c2] (mapv :value (cyc (theory/root-notes 2 (core/pure "C/E")) 0))))
  (is (= [:c4] (mapv :value (cyc (theory/root-notes (core/cat* [(core/pure 2) (core/pure 4)]) (core/pure "C")) 1)))))

;;; ── transpose ────────────────────────────────────────────────────────

(deftest transpose-up-octave
  (let [hz     (theory/note->hz :c4)
        result (core/query (theory/transpose 12 (core/pure hz)) one-cycle)
        freq   (:value (first result))]
    (is (< (js/Math.abs (- (* 2.0 hz) freq)) 0.01)))
  (is (= 880 (:value (first (cyc (theory/transpose 12 (core/pure 440)) 0))))))

(deftest transpose-note-keywords-stay-keywords
  (is (= [:c5 :e5] (evals (theory/transpose 12 (core/seq* [:c4 :e4])) 0)))
  (is (= [:a3] (evals (theory/transpose -12 (core/seq* [:a4])) 0)))
  (is (= [:g4] (evals (theory/transpose (core/cat* [(core/pure 0) (core/pure 7)]) (core/seq* [:c4])) 1))))

(deftest transpose-passes-keywords
  (let [result (core/query (theory/transpose 5 (core/pure :bd)) one-cycle)]
    (is (= :bd (:value (first result))))))

(deftest transpose-passes-rests
  (let [result (core/query (theory/transpose 7 (core/pure :_)) one-cycle)]
    (is (= :_ (:value (first result))))))

(deftest transpose-param-map
  (let [m      {:note :c4 :amp 0.8}
        result (core/query (theory/transpose 7 (core/pure m)) one-cycle)
        v      (:value (first result))]
    (is (map? v))
    (is (= 0.8 (:amp v)))
    (is (= :g4 (:note v)))))

(deftest interval-strings
  (doseq [[s n] [["3M" 4] ["5P" 7] ["-2M" -2] ["4A" 6] ["5d" 6] ["8P" 12] ["9m" 13]
                 ["1P" 0] ["3m" 3] ["2m" 1] ["7M" 11] ["-3m" -3]]]
    (is (= n (theory/interval->semitones-str s)) s))
  (doseq [s ["3P" "5M" "x" "3" "0P" "4m"]]
    (is (nil? (theory/interval->semitones-str s)) s)))

(deftest note-hz-midi-math
  (is (= 63 (js/Math.round (+ 69 (* 12 (/ (js/Math.log (/ (theory/note->hz :ds4) 440)) (js/Math.log 2))))))))

(deftest midi->note-round-trips
  (is (= :c4 (theory/midi->note 60)))
  (is (= :cs4 (theory/midi->note 61)))
  (is (= :ds3 (theory/midi->note 51)))
  (is (= :c-1 (theory/midi->note 0)))
  (doseq [kw [:c3 :e3 :g5 :a4 :b0]]
    (is (= kw (theory/midi->note (theory/note->midi kw)))))
  (is (number? (theory/midi->note 60.5)) "non-integer MIDI has no name → Hz"))
