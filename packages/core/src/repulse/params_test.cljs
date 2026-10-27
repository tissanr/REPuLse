(ns repulse.params-test
  (:require [cljs.test :refer-macros [deftest is testing]]
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

(deftest amp-preserves-source
  ;; :source must survive amp/attack/decay chains — used for editor highlighting
  (let [src   {:from 1 :to 3}
        pat   (core/pure :c4 src)
        evs   (core/query (params/amp 0.7 pat) one-cycle)]
    (is (= src (:source (first evs))))))

(deftest params-chain-preserves-source
  ;; :source must survive a full amp→attack→decay chain
  (let [src {:from 1 :to 3}
        pat (core/pure :c4 src)
        evs (core/query (->> pat
                             (params/amp 0.7)
                             (params/attack 0.02)
                             (params/decay 0.5))
                        one-cycle)]
    (is (= src (:source (first evs))))))

(deftest seq-source-preserved-through-amp
  ;; :source on individual events from seq* must survive amp transform
  (let [src-a {:from 1 :to 3}
        src-b {:from 4 :to 6}
        pat   (core/seq* [:c4 :e4] [src-a src-b])
        evs   (core/query (params/amp 0.7 pat) one-cycle)]
    (is (= src-a (:source (first evs))))
    (is (= src-b (:source (second evs))))))

;;; ── rate / begin / end* / loop-sample ───────────────────────────────

(deftest rate-scalar
  (let [evs (core/query (params/rate 1.5 (core/pure :bd)) one-cycle)]
    (is (= {:note :bd :rate 1.5} (:value (first evs))))))

(deftest rate-one-arg
  (let [speed-up (params/rate 2.0)
        evs      (core/query (speed-up (core/pure :bd)) one-cycle)]
    (is (= {:note :bd :rate 2.0} (:value (first evs))))))

(deftest begin-end-chain
  (let [evs (core/query
              (params/end* 0.8 (params/begin 0.2 (core/pure :tabla)))
              one-cycle)]
    (is (= {:note :tabla :begin 0.2 :end 0.8} (:value (first evs))))))

(deftest loop-sample-test
  (let [evs (core/query (params/loop-sample true (core/pure :bd)) one-cycle)]
    (is (= {:note :bd :loop true} (:value (first evs))))))

(deftest rate-chains-with-amp
  (let [evs (core/query (->> (core/pure :tabla)
                              (params/rate 1.5)
                              (params/amp 0.7))
                        one-cycle)]
    (is (= {:note :tabla :rate 1.5 :amp 0.7} (:value (first evs))))))

;;; ── jux ─────────────────────────────────────────────────────────────

(deftest jux-stacks-panned
  ;; jux should produce events panned left and right
  (let [evs  (core/query (params/jux core/rev (core/seq* [:a :b])) one-cycle)
        pans (set (map #(get (:value %) :pan) evs))]
    (is (contains? pans -1))
    (is (contains? pans 1))))

(deftest jux-by-half-width
  (let [evs  (core/query (params/jux-by 0.5 core/rev (core/seq* [:a :b])) one-cycle)
        pans (set (map #(get (:value %) :pan) evs))]
    (is (contains? pans -0.5))
    (is (contains? pans 0.5))))

;;; ── Phase TL3b: hurry ────────────────────────────────────────────────

(defn- hurry-evs [pat c]
  (->> (core/query pat (core/cycle-span c))
       (sort-by #(core/rat->float (:start (:whole %))))
       (mapv (fn [e] [(:value e) (:whole e) (:part e)]))))

(deftest tl3b-hurry
  (testing "hurry 2 doubles speed and rate"
    (is (= [[{:note :bd :rate 2} (core/span [0 1] [1 4]) (core/span [0 1] [1 4])]
            [{:note :sd :rate 2} (core/span [1 4] [1 2]) (core/span [1 4] [1 2])]
            [{:note :bd :rate 2} (core/span [1 2] [3 4]) (core/span [1 2] [3 4])]
            [{:note :sd :rate 2} (core/span [3 4] [1 1]) (core/span [3 4] [1 1])]]
           (hurry-evs (params/hurry 2 (core/seq* [:bd :sd])) 0))))
  (testing "multiplies an existing rate"
    (is (= [[{:note :bd :rate 3.0} (core/span [0 1] [1 2])]
            [{:note :bd :rate 3.0} (core/span [1 2] [1 1])]]
           (mapv (fn [[v w _]] [v w])
                 (hurry-evs (params/hurry 2 (params/rate 1.5 (core/seq* [:bd]))) 0)))))
  (testing "slowing: (hurry 1/2 …) plays :a in cycle 0 and :b in cycle 1"
    (let [evs (hurry-evs (params/hurry [1 2] (core/seq* [:a :b])) 0)]
      (is (= 1 (count evs)))
      (is (= [{:note :a :rate 0.5} (core/span [0 1] [1 1]) (core/span [0 1] [1 1])]
             (first evs)))))
  (testing "rests pass through"
    (is (= [:bd :_ :bd :_]
           (mapv (fn [[v _ _]] (if (map? v) (:note v) v))
                 (hurry-evs (params/hurry 2 (core/seq* [:bd :_])) 0)))))
  (testing "patterned rate"
    (let [p (params/hurry (core/cat* [(core/pure 1) (core/pure 2)]) (core/seq* [:bd :sd]))]
      (is (= [1 1] (mapv (comp :rate first) (hurry-evs p 0))))
      (is (= [2 2 2 2] (mapv (comp :rate first) (hurry-evs p 1))))))
  (testing "curried"
    (is (= 4 (count (hurry-evs ((params/hurry 2) (core/seq* [:bd :sd])) 0))))))

;;; ── Phase TL5: echo, stut, jux-flip, xfade ──────────────────────────

(defn- tl5-onsets [pat n]
  (->> (core/query pat (core/cycle-span n))
       (filter core/onset?)
       (sort-by (comp core/rat->float :start :whole))))

(deftest tl5-echo-decays-amp
  (let [evs (tl5-onsets (params/echo 3 [1 8] 0.6 (core/pure :cp)) 0)
        amps (mapv (comp :amp :value) evs)]
    (is (= [[0 1] [1 8] [1 4]] (mapv (comp :start :whole) evs)))
    (is (= [1.0 0.6] (subvec amps 0 2)))
    (is (< (js/Math.abs (- 0.36 (nth amps 2))) 1e-9)))
  (is (empty? (tl5-onsets (params/echo 0 [1 8] 0.5 (core/pure :cp)) 0)))
  (testing "existing amp is multiplied"
    (is (= [0.8 0.4 0.2]
           (mapv (comp :amp :value)
                 (tl5-onsets (params/echo 3 [1 8] 0.5 (params/amp 0.8 (core/pure :cp))) 0))))))

(deftest tl5-stut-is-echo-with-tidal-order
  (is (= (mapv (juxt :value :whole) (tl5-onsets (params/echo 3 [1 8] 0.5 (core/pure :cp)) 0))
         (mapv (juxt :value :whole) (tl5-onsets (params/stut 3 0.5 [1 8] (core/pure :cp)) 0)))))

(deftest tl5-jux-flip-swaps-sides
  (let [first-note-pan (fn [n]
                         (:pan (:value (first (tl5-onsets (params/jux-flip core/rev (core/seq* [:a :b])) n)))))
        pan-of-a (fn [n] (set (map (comp :pan :value)
                                   (filter #(= :a (:note (:value %)))
                                           (tl5-onsets (params/jux-flip core/rev (core/seq* [:a :b])) n)))))]
    ;; cycle 0: original :a on the left (-1), reversed :a on the right (1)
    (is (= #{-1 1} (pan-of-a 0)))
    (is (= [-1 1] [(first-note-pan 0) (first-note-pan 1)]))))

(deftest tl5-xfade-gains
  (let [amps (fn [pos]
               (sort (map (fn [e] [(:note (:value e)) (:amp (:value e))])
                          (tl5-onsets (params/xfade (core/pure :bd) pos (core/pure :hh)) 0))))]
    (is (= [[:bd 1] [:hh 0.5]] (amps 0.25)))
    (is (= [[:bd 0.5] [:hh 1]] (amps 0.75)))
    (is (= [[:bd 1] [:hh 1]] (amps 0.5)))))
