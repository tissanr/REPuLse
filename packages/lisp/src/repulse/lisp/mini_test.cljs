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

(deftest alternation-multi-cycle-span
  ;; (fast 2 "<bd sd>") asks for two cycles at once → bd then sd
  (is (= [:bd :sd] (values (core/fast 2 (mini/parse "<bd sd>"))))))

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

(deftest rational-atoms-are-exact
  ;; TL1: "1/8" used to parse as the float 1 (parseFloat stops at "/")
  (is (= [[1 8] [1 4]] (values (mini/parse "1/8 2/8"))))
  (is (= [[-1 2]] (values (mini/parse "-1/2")))))

;;; ── Phase TL3: nested alternation, !, ., ",", {}%n, (k,n,r) ─────────

(defn- cyc-evs [s c]
  (sort-by (juxt #(core/rat->float (:start (:whole %))) #(str (:value %)))
           (core/query (mini/parse s) (core/cycle-span c))))

(defn- tuples
  "[value whole-start whole-end part-start part-end] with rationals as vectors"
  [evs]
  (mapv (fn [e] [(:value e) (:start (:whole e)) (:end (:whole e))
                 (:start (:part e)) (:end (:part e))])
        evs))

(defn- wh [evs] (mapv (fn [e] [(:value e) (:start (:whole e)) (:end (:whole e))]) evs))

(defn- onset-starts [s c]
  (mapv #(:start (:whole %))
        (sort-by #(core/rat->float (:start (:whole %)))
                 (core/query-onsets (mini/parse s) (core/cycle-span c)))))

(deftest tl3-exact-sequence-times
  (is (= [[:a [0 1] [1 3]] [:b [1 3] [2 3]] [:c [2 3] [1 1]]]
         (wh (cyc-evs "a b c" 0)))))

(deftest tl3-nested-alternation-alternates
  (is (= [[:b [1 1] [3 2]] [:c [3 2] [2 1]]] (wh (cyc-evs "<a b> c" 1))))
  (is (= [[:a [0 1] [1 2]] [:c [1 2] [1 1]]] (wh (cyc-evs "<a b> c" 0)))))

(deftest tl3-bang-replicate
  (is (= (wh (cyc-evs "bd bd bd sd" 0)) (wh (cyc-evs "bd!3 sd" 0))))
  (is (= (wh (cyc-evs "bd bd sd" 0)) (wh (cyc-evs "bd ! sd" 0))))
  (is (= (wh (cyc-evs "[bd sd] [bd sd]" 0)) (wh (cyc-evs "[bd sd]!2" 0))))
  (let [src (fn [s] (mapv :source (core/query (mini/parse s 10) (core/cycle-span 0))))]
    (is (= [{:from 10 :to 12} {:from 10 :to 12} {:from 10 :to 12}]
           (take 3 (src "bd!3 sd"))))))

(deftest tl3-dot-grouping
  (is (= [[:bd [0 1] [1 4]] [:sd [1 4] [1 2]] [:hh [1 2] [2 3]] [:hh [2 3] [5 6]] [:hh [5 6] [1 1]]]
         (wh (cyc-evs "bd sd . hh hh hh" 0))))
  (is (= [0.5 1] (mapv :value (cyc-evs "0.5 1" 0)))))

(deftest tl3-comma-layers
  (is (= [[:bd [0 1] [1 1]] [:hh [0 1] [1 2]] [:hh [1 2] [1 1]]]
         (wh (cyc-evs "[bd, hh hh]" 0))))
  (is (= 3 (count (cyc-evs "[c3,e3,g3]" 0))))
  (is (every? #(= [[0 1] [1 1]] [(:start (:whole %)) (:end (:whole %))]) (cyc-evs "[c3,e3,g3]" 0)))
  (is (= 5 (count (cyc-evs "bd sd, hh hh hh" 0)))))

(deftest tl3-polymeter-braces
  (is (= [:a :b :c :a] (mapv :value (cyc-evs "{a b c}%4" 0))))
  (is (= [:b :c :a :b] (mapv :value (cyc-evs "{a b c}%4" 1))))
  (is (= [[:a [0 1] [1 4]] [:b [1 4] [1 2]] [:c [1 2] [3 4]] [:a [3 4] [1 1]]]
         (wh (cyc-evs "{a b c}%4" 0))))
  (is (= (wh (cyc-evs "{a b c, d e}" 1))
         (wh (sort-by (juxt #(core/rat->float (:start (:whole %))) #(str (:value %)))
                      (core/query (core/polymeter nil [[:a :b :c] [:d :e]]) (core/cycle-span 1)))))))

(deftest tl3-euclid-suffix
  (is (= [[0 1] [3 8] [3 4]] (onset-starts "bd(3,8)" 0)))
  (is (= [[1 8] [1 2] [3 4]] (onset-starts "bd(3,8,2)" 0)))
  (is (= [[1 1] [5 4] [11 8] [13 8] [7 4]] (onset-starts "bd(<3 5>,8)" 1)))
  (is (every? #(= [1 8] (core/rat- (:end (:whole %)) (:start (:whole %))))
              (cyc-evs "bd(3,8)" 0))))

(deftest tl3-parse-errors
  (doseq [s ["{a b" "bd(3,8" "! bd" "[a b" "<a b"]]
    (is (= :mini-parse-error
           (try (mini/parse s) nil
                (catch :default e (:type (ex-data e)))))
        s)))
