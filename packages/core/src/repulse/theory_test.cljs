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

;;; ── scale ────────────────────────────────────────────────────────────

(deftest scale-major-degrees
  ;; C major: C4=60 E4=64 G4=67
  (let [events (core/query
                 (theory/scale :major :c4 (core/seq* [0 2 4]))
                 one-cycle)
        freqs  (mapv :value events)]
    (is (= 3 (count freqs)))
    (is (< (js/Math.abs (- (theory/midi->hz 60) (nth freqs 0))) 0.01))
    (is (< (js/Math.abs (- (theory/midi->hz 64) (nth freqs 1))) 0.01))
    (is (< (js/Math.abs (- (theory/midi->hz 67) (nth freqs 2))) 0.01))))

(deftest scale-minor-degrees
  ;; A minor: A3=57 C4=60 E4=64
  (let [events (core/query
                 (theory/scale :minor :a3 (core/seq* [0 2 4]))
                 one-cycle)
        freqs  (mapv :value events)]
    (is (= 3 (count freqs)))
    (is (< (js/Math.abs (- (theory/midi->hz 57) (nth freqs 0))) 0.01))
    (is (< (js/Math.abs (- (theory/midi->hz 60) (nth freqs 1))) 0.01))
    (is (< (js/Math.abs (- (theory/midi->hz 64) (nth freqs 2))) 0.01))))

(deftest scale-degree-octave-wrapping
  ;; Degree 7 in a 7-note scale = root one octave up
  (let [events (core/query
                 (theory/scale :major :c4 (core/seq* [0 7]))
                 one-cycle)
        freqs  (mapv :value events)]
    (is (< (js/Math.abs (- (* 2.0 (theory/midi->hz 60)) (nth freqs 1))) 0.01))))

;;; ── chord ────────────────────────────────────────────────────────────

(deftest chord-major-triad
  ;; C major = C4 E4 G4
  (let [events (core/query (theory/chord :major :c4) one-cycle)
        freqs  (set (map :value events))]
    (is (= 3 (count freqs)))
    (is (some #(< (js/Math.abs (- (theory/midi->hz 60) %)) 0.01) freqs))
    (is (some #(< (js/Math.abs (- (theory/midi->hz 64) %)) 0.01) freqs))
    (is (some #(< (js/Math.abs (- (theory/midi->hz 67) %)) 0.01) freqs))))

(deftest chord-minor7
  ;; A minor 7 = A3 C4 E4 G4 (MIDI 57 60 64 67)
  (let [events (core/query (theory/chord :minor7 :a3) one-cycle)
        freqs  (set (map :value events))]
    (is (= 4 (count freqs)))
    (is (some #(< (js/Math.abs (- (theory/midi->hz 57) %)) 0.01) freqs))
    (is (some #(< (js/Math.abs (- (theory/midi->hz 60) %)) 0.01) freqs))
    (is (some #(< (js/Math.abs (- (theory/midi->hz 64) %)) 0.01) freqs))
    (is (some #(< (js/Math.abs (- (theory/midi->hz 67) %)) 0.01) freqs))))

(deftest chord-dim7
  ;; C dim7 = C4 Eb4 Gb4 A4 (MIDI 60 63 66 69)
  (let [events (core/query (theory/chord :dim7 :c4) one-cycle)
        freqs  (set (map :value events))]
    (is (= 4 (count freqs)))
    (is (some #(< (js/Math.abs (- (theory/midi->hz 60) %)) 0.01) freqs))
    (is (some #(< (js/Math.abs (- (theory/midi->hz 63) %)) 0.01) freqs))
    (is (some #(< (js/Math.abs (- (theory/midi->hz 66) %)) 0.01) freqs))
    (is (some #(< (js/Math.abs (- (theory/midi->hz 69) %)) 0.01) freqs))))

;;; ── transpose ────────────────────────────────────────────────────────

(deftest transpose-up-octave
  (let [hz     (theory/note->hz :c4)
        result (core/query (theory/transpose 12 (core/pure hz)) one-cycle)
        freq   (:value (first result))]
    (is (< (js/Math.abs (- (* 2.0 hz) freq)) 0.01))))

(deftest transpose-passes-keywords
  (let [result (core/query (theory/transpose 5 (core/pure :bd)) one-cycle)]
    (is (= :bd (:value (first result))))))

(deftest transpose-passes-rests
  (let [result (core/query (theory/transpose 7 (core/pure :_)) one-cycle)]
    (is (= :_ (:value (first result))))))
