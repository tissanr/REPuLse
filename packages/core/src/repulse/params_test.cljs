(ns repulse.params-test
  (:require [cljs.test :refer-macros [deftest is]]
            [repulse.core :as core]
            [repulse.params :as params]))

(def ^:private one-cycle
  (core/span (core/int->rat 0) (core/int->rat 1)))

;;; ── combine ──────────────────────────────────────────────────────────

(deftest combine-pairs-overlapping-events
  (let [evs (core/query
              (core/combine vector
                            (core/seq* [1 2])
                            (core/seq* [:a :b]))
              one-cycle)
        vals (mapv :value evs)]
    (is (= 2 (count vals)))
    (is (= [1 :a] (first vals)))
    (is (= [2 :b] (second vals)))))

(deftest combine-pure-pairs-with-all-events
  ;; pure 99 covers the full cycle — every note event pairs with it
  (let [evs (core/query
              (core/combine vector
                            (core/pure 99)
                            (core/seq* [:x :y :z]))
              one-cycle)
        vals (mapv :value evs)]
    (is (= 3 (count vals)))
    (is (every? #(= 99 (first %)) vals))
    (is (= [:x :y :z] (mapv second vals)))))

;;; ── amp ──────────────────────────────────────────────────────────────

(deftest amp-scalar-keyword
  (let [evs (core/query (params/amp 0.8 (core/pure :c4)) one-cycle)]
    (is (= 1 (count evs)))
    (is (= {:note :c4 :amp 0.8} (:value (first evs))))))

(deftest amp-scalar-number
  (let [evs (core/query (params/amp 0.5 (core/pure 440.0)) one-cycle)]
    (is (= {:note 440.0 :amp 0.5} (:value (first evs))))))

(deftest amp-scalar-map-extends-existing
  ;; If value is already a map, amp is assoc-ed in
  (let [evs (core/query (params/amp 0.7 (core/pure {:note :bd :decay 0.1})) one-cycle)]
    (is (= {:note :bd :decay 0.1 :amp 0.7} (:value (first evs))))))

(deftest amp-patterned
  (let [evs (core/query
              (params/amp (core/seq* [0.9 0.5])
                          (core/seq* [:c4 :e4]))
              one-cycle)
        vals (mapv :value evs)]
    (is (= 2 (count vals)))
    (is (= {:note :c4 :amp 0.9} (first vals)))
    (is (= {:note :e4 :amp 0.5} (second vals)))))

(deftest amp-one-arg-returns-fn
  (let [soften (params/amp 0.3)
        evs    (core/query (soften (core/pure :bd)) one-cycle)]
    (is (= {:note :bd :amp 0.3} (:value (first evs))))))

;;; ── attack / decay / release / pan ───────────────────────────────────

(deftest attack-one-arg-returns-fn
  (let [t (params/attack 0.05)
        evs (core/query (t (core/pure :c4)) one-cycle)]
    (is (= {:note :c4 :attack 0.05} (:value (first evs))))))

(deftest decay-applies
  (let [evs (core/query (params/decay 2.0 (core/pure :c4)) one-cycle)]
    (is (= {:note :c4 :decay 2.0} (:value (first evs))))))

(deftest release-applies
  (let [evs (core/query (params/release 0.5 (core/pure :c4)) one-cycle)]
    (is (= {:note :c4 :release 0.5} (:value (first evs))))))

(deftest pan-applies
  (let [evs (core/query (params/pan -0.8 (core/pure :c4)) one-cycle)]
    (is (= {:note :c4 :pan -0.8} (:value (first evs))))))

;;; ── chaining ─────────────────────────────────────────────────────────

(deftest params-chain-builds-map
  ;; Chaining amp then attack produces a single map with both keys
  (let [evs (core/query
              (params/attack 0.02
                (params/amp 0.8
                  (core/pure :c4)))
              one-cycle)]
    (is (= {:note :c4 :amp 0.8 :attack 0.02} (:value (first evs))))))

(deftest all-five-params-chain
  (let [pat (->> (core/pure :c4)
                 (params/amp 0.7)
                 (params/attack 0.01)
                 (params/decay 0.5)
                 (params/release 0.3)
                 (params/pan 0.2))
        evs (core/query pat one-cycle)]
    (is (= {:note :c4 :amp 0.7 :attack 0.01 :decay 0.5 :release 0.3 :pan 0.2}
           (:value (first evs))))))

(deftest comp-preset
  ;; (comp f g) applies g first, then f — like (f (g pat))
  (let [pluck (comp (params/amp 0.9) (params/attack 0.003) (params/decay 0.15))
        evs   (core/query (pluck (core/pure :e4)) one-cycle)]
    (is (= {:note :e4 :amp 0.9 :attack 0.003 :decay 0.15}
           (:value (first evs))))))
