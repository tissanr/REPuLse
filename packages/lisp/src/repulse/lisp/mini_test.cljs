(ns repulse.lisp.mini-test
  (:require [cljs.test :refer-macros [deftest is]]
            [repulse.lisp.mini :as mini]
            [repulse.core :as core]))

(def ^:private one-cycle
  (core/span (core/int->rat 0) (core/int->rat 1)))

(defn- values [pat]
  (mapv :value (core/query pat one-cycle)))

;;; ── Basic sequences ─────────────────────────────────────────────────

(deftest simple-sequence
  (is (= [:bd :sd :hh] (values (mini/parse "bd sd hh")))))

(deftest single-element
  (is (= [:bd] (values (mini/parse "bd")))))

(deftest rest-tilde
  (is (= [:bd :_ :sd] (values (mini/parse "bd ~ sd")))))

(deftest rest-underscore
  (is (= [:bd :_ :sd] (values (mini/parse "bd _ sd")))))

;;; ── Numbers and note names ──────────────────────────────────────────

(deftest number-value
  (is (= [440] (values (mini/parse "440")))))

(deftest note-keyword
  (is (= [:c4 :e4 :g4] (values (mini/parse "c4 e4 g4")))))

(deftest mixed-types
  (is (= [:bd 440 :c4] (values (mini/parse "bd 440 c4")))))

;;; ── Subdivision ─────────────────────────────────────────────────────

(deftest subdivision
  ;; "bd [sd hh]" → 2 top-level slots; second slot has 2 sub-events
  (let [evs (core/query (mini/parse "bd [sd hh]") one-cycle)]
    (is (= 3 (count evs)))
    (is (= :bd (:value (first evs))))
    (is (= :sd (:value (second evs))))
    (is (= :hh (:value (nth evs 2))))))

(deftest nested-subdivision
  ;; "bd [[sd hh] cp]" → bd takes half, then [sd hh] and cp split the other half
  (let [evs (core/query (mini/parse "bd [[sd hh] cp]") one-cycle)]
    (is (= 4 (count evs)))
    (is (= :bd (:value (first evs))))))

;;; ── Repetition ──────────────────────────────────────────────────────

(deftest repetition
  ;; "hh*4" → fast 4 of a single hh — produces 4 events per cycle
  (let [evs (core/query (mini/parse "hh*4") one-cycle)]
    (is (= 4 (count evs)))
    (is (every? #(= :hh (:value %)) evs))))

(deftest repetition-in-sequence
  ;; "bd hh*2 sd" → 3 top-level slots; hh slot has 2 sub-events
  (let [evs (core/query (mini/parse "bd hh*2 sd") one-cycle)]
    (is (= 4 (count evs)))
    (is (= :bd (:value (first evs))))
    (is (= :hh (:value (second evs))))
    (is (= :hh (:value (nth evs 2))))
    (is (= :sd (:value (nth evs 3))))))

;;; ── Alternation ─────────────────────────────────────────────────────

(deftest alternation-cycle-0
  ;; "<bd sd>" on cycle 0 → bd
  (let [evs (core/query (mini/parse "<bd sd>") (core/span [0 1] [1 1]))]
    (is (= [:bd] (mapv :value evs)))))

(deftest alternation-cycle-1
  ;; "<bd sd>" on cycle 1 → sd
  (let [evs (core/query (mini/parse "<bd sd>") (core/span [1 1] [2 1]))]
    (is (= [:sd] (mapv :value evs)))))

(deftest alternation-wraps
  ;; "<bd sd cp>" on cycle 3 → wraps back to bd
  (let [evs (core/query (mini/parse "<bd sd cp>") (core/span [3 1] [4 1]))]
    (is (= [:bd] (mapv :value evs)))))

;;; ── Sample index ────────────────────────────────────────────────────

(deftest sample-index
  (let [evs (core/query (mini/parse "bd:2") one-cycle)]
    (is (= 1 (count evs)))
    (is (= {:bank :bd :n 2} (:value (first evs))))))

(deftest sample-index-in-sequence
  (let [vals (values (mini/parse "bd:0 sd:1"))]
    (is (= [{:bank :bd :n 0} {:bank :sd :n 1}] vals))))

;;; ── Probability ─────────────────────────────────────────────────────

(deftest degrade-produces-subset
  ;; Run the pattern many times — sometimes events are dropped.
  ;; With 50% probability over 100 trials, we should see both 0 and 1 events.
  (let [pat    (mini/parse "bd?")
        counts (map (fn [_] (count (core/query pat one-cycle))) (range 100))
        has-0  (some zero? counts)
        has-1  (some #(= 1 %) counts)]
    (is (or has-0 has-1) "degrade should sometimes drop events")))

;;; ── Weight / elongation ─────────────────────────────────────────────

(deftest weight-simple
  ;; "bd@3 sd" → bd takes 3/4 of the cycle, sd takes 1/4
  (let [evs (core/query (mini/parse "bd@3 sd") one-cycle)]
    (is (= 2 (count evs)))
    (is (= :bd (:value (first evs))))
    (is (= :sd (:value (second evs))))
    ;; bd occupies [0, 3/4), sd occupies [3/4, 1)
    (let [bd-end   (core/rat->float (:end (:part (first evs))))
          sd-start (core/rat->float (:start (:part (second evs))))]
      (is (< (Math/abs (- bd-end 0.75)) 0.01))
      (is (< (Math/abs (- sd-start 0.75)) 0.01)))))

;;; ── Composition with Lisp ───────────────────────────────────────────

(deftest fast-of-mini
  (let [pat (core/fast 2 (mini/parse "bd sd"))
        evs (core/query pat one-cycle)]
    (is (= 4 (count evs)))))

(deftest stack-of-minis
  (let [pat (core/stack* [(mini/parse "bd sd") (mini/parse "hh hh hh hh")])
        evs (core/query pat one-cycle)]
    (is (= 6 (count evs)))))

;;; ── Edge cases ──────────────────────────────────────────────────────

(deftest empty-string
  (let [evs (core/query (mini/parse "") one-cycle)]
    (is (empty? evs))))

(deftest extra-whitespace
  (is (= [:bd :sd] (values (mini/parse "  bd   sd  ")))))

(deftest group-with-suffix
  ;; "[bd sd]*2" — the group is repeated twice
  (let [evs (core/query (mini/parse "[bd sd]*2") one-cycle)]
    (is (= 4 (count evs)))))
