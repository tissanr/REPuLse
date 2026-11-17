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

;;; ── Phase TL4b: fix ──────────────────────────────────────────────────

(defn- tl4b-evs
  "[value whole-start whole-end] of cycle 0, sorted by whole start then value."
  [pat]
  (->> (core/query pat one-cycle)
       (map (fn [e] [(:value e) (:start (:whole e)) (:end (:whole e))]))
       (sort-by (fn [[v s _]] [(core/rat->float s) (pr-str v)]))
       vec))

(defn- t [n d] (core/rat n d))
(def ^:private a02 (params/amp 0.2))

(deftest tl4b-controls-match-rules
  (let [m params/controls-match?]
    (testing "numbers compare with =="
      (is (m {:amp 1} {:amp 1.0}))
      (is (m {:amp 0.5} {:amp [1 2]}))
      (is (not (m {:amp [1 2]} {:amp 0.5})) "vector condition = any-of 1 or 2")
      (is (m {:note 60} {:note 60.0}))
      (is (not (m {:amp 1} {:amp 2}))))
    (testing "keywords and strings compare exactly"
      (is (m {:s :bd} :bd))
      (is (not (m {:s :bd} :AkaiLinn_bd)))
      (is (not (m {:s "bd"} :bd)))
      (is (m {:s "bd"} {:s "bd"})))
    (testing "missing key is no match; AND over keys"
      (is (not (m {:pan 0} :bd)))
      (is (m {:s :bd :n 2} {:bank :bd :n 2}))
      (is (not (m {:s :bd :n 2} {:bank :bd :n 0}))))
    (testing "any-of vectors"
      (is (m {:s [:bd :sd]} :sd))
      (is (not (m {:s [:bd :sd]} :hh)))
      (is (m {:amp [1 2]} {:amp 2})))
    (testing "derived :s"
      (is (m {:s :bd} {:note :bd :amp 0.5}))
      (is (m {:s :bd} {:bank :bd :n 1}))
      (is (m {:note :c4} :c4))
      (is (m {:s :c4} :c4) "any keyword :note derives :s, no note-name guessing")
      (is (not (m {:s :c4} :bd)))
      (is (m {:s :cb} :cb) "cowbell :cb looks like C-flat but still matches")
      (is (m {:s :a} {:note :a :amp 0.5}) "single-letter sample names match")
      (is (not (m {:s :bd} 60)) "numeric :note derives no :s")
      (is (m {:s :kick} {:s :kick :note :bd}) "a real :s wins")
      (is (not (m {:s :bd} {:s :kick :note :bd}))))
    (testing "bare numbers and rationals become {:note v}"
      (is (m {:note 3} 3))
      (is (m {:note 0.5} [1 2])))
    (testing "rests and other values never match, not even {}"
      (is (not (m {} :_)))
      (is (not (m {} nil)))
      (is (not (m {} {:note :_})))
      (is (not (m {} "bd")))
      (is (not (m {} true)))
      (is (not (m {} [1 2 3])))
      (is (m {} :bd)))))

(deftest tl4b-fix-numeric
  (is (= [[{:note :bd :amp 0.2} (t 0 1) (t 1 2)] [{:note :sd :amp 0.5} (t 1 2) (t 1 1)]]
         (tl4b-evs (params/fix a02 {:amp 1}
                               (params/amp (core/seq* [1.0 0.5]) (core/seq* [:bd :sd]))))))
  (is (= [[{:note :bd :amp 0.9} (t 0 1) (t 1 1)]]
         (tl4b-evs (params/fix (params/amp 0.9) {:amp 0.5} (params/amp [1 2] (core/pure :bd))))))
  (is (= [[{:note 60 :amp 0.2} (t 0 1) (t 1 1)]]
         (tl4b-evs (params/fix a02 {:note 60} (core/seq* [60.0]))))))

(deftest tl4b-fix-any-of
  (is (= [[{:note :bd :amp 0.2} (t 0 1) (t 1 4)] [:hh (t 1 4) (t 1 2)]
          [{:note :sd :amp 0.2} (t 1 2) (t 3 4)] [:cp (t 3 4) (t 1 1)]]
         (tl4b-evs (params/fix a02 {:s [:bd :sd]} (core/seq* [:bd :hh :sd :cp]))))))

(deftest tl4b-fix-bank-prefix
  (is (= [[:AkaiLinn_bd (t 0 1) (t 1 2)] [{:note :bd :amp 0.2} (t 1 2) (t 1 1)]]
         (tl4b-evs (params/fix a02 {:s :bd} (core/seq* [:AkaiLinn_bd :bd]))))))

(deftest tl4b-fix-missing-key
  (is (= [[:bd (t 0 1) (t 1 2)] [{:note :hh :pan 0 :amp 0.2} (t 0 1) (t 1 2)]
          [:sd (t 1 2) (t 1 1)] [{:note :hh :pan 0 :amp 0.2} (t 1 2) (t 1 1)]]
         (tl4b-evs (params/fix a02 {:pan 0}
                               (core/stack* [(core/seq* [:bd :sd])
                                             (params/pan 0 (core/seq* [:hh :hh]))]))))))

(deftest tl4b-fix-and-derived-s
  (is (= [[{:bank :bd :n 2 :amp 0.2} (t 0 1) (t 1 2)] [{:bank :bd :n 0} (t 1 2) (t 1 1)]]
         (tl4b-evs (params/fix a02 {:s :bd :n 2}
                               (core/seq* [{:bank :bd :n 2} {:bank :bd :n 0}])))))
  (is (= [[{:note :bd :amp 0.2} (t 0 1) (t 1 3)] [:_ (t 1 3) (t 2 3)] [{:note 3 :amp 0.2} (t 2 3) (t 1 1)]]
         (tl4b-evs (params/fix a02 {} (core/seq* [:bd :_ 3])))))
  (is (= [[{:note :c4 :amp 0.2} (t 0 1) (t 1 2)] [:bd (t 1 2) (t 1 1)]]
         (tl4b-evs (params/fix a02 {:s :c4} (core/seq* [:c4 :bd])))))
  (is (= [[{:note :cb :amp 0.2} (t 0 1) (t 1 2)] [:bd (t 1 2) (t 1 1)]]
         (tl4b-evs (params/fix a02 {:s :cb} (core/seq* [:cb :bd]))))
      "cowbell :cb is not mistaken for C-flat")
  (is (= [[{:note :c4 :amp 0.2} (t 0 1) (t 1 2)] [:bd (t 1 2) (t 1 1)]]
         (tl4b-evs (params/fix a02 {:note :c4} (core/seq* [:c4 :bd])))))
  (is (= [[:bd (t 0 1) (t 1 1)]]
         (tl4b-evs (params/fix a02 {:s "bd"} (core/seq* [:bd]))))))

(deftest tl4b-fix-structure-changing-f
  (is (= [[:hh (t 0 1) (t 1 2)] [:bd (t 1 4) (t 1 2)] [:bd (t 3 4) (t 1 1)]]
         (tl4b-evs (params/fix #(core/fast 2 %) {:s :bd} (core/seq* [:hh :bd]))))))

;;; ── Phase TL6: sample slicing ───────────────────────────────────────

(def ^:private brk {:bank :break :n 0})

(defn- onsets [pat]
  (->> (core/query pat one-cycle)
       (filter core/onset?)
       (sort-by #(core/rat->float (:start (:whole %))))))

(defn- whole [ev] [(:start (:whole ev)) (:end (:whole ev))])

(deftest chop-four-equal-pieces
  (let [evs (onsets (params/chop 4 (core/pure brk)))]
    (is (= [0.0 0.25 0.5 0.75] (mapv #(get-in % [:value :begin]) evs)))
    (is (= [0.25 0.5 0.75 1.0] (mapv #(get-in % [:value :end]) evs)))
    (is (= [[[0 1] [1 4]] [[1 4] [1 2]] [[1 2] [3 4]] [[3 4] [1 1]]] (mapv whole evs)))
    (is (every? #(= :break (get-in % [:value :bank])) evs))))

(deftest chop-is-relative-to-existing-range
  (let [evs (onsets (params/chop 2 (params/end* 0.5 (params/begin 0.25 (core/pure brk)))))]
    (is (= [[0.25 0.375] [0.375 0.5]]
           (mapv #(vector (get-in % [:value :begin]) (get-in % [:value :end])) evs)))))

(deftest chop-subdivides-each-event-and-keeps-rests
  (let [evs (onsets (params/chop 2 (core/seq* [:bd :_])))]
    (is (= [{:note :bd :begin 0.0 :end 0.5} {:note :bd :begin 0.5 :end 1.0} :_ :_]
           (mapv :value evs)))))

(deftest chop-curried-and-patterned
  (is (= 3 (count (onsets ((params/chop 3) (core/pure brk))))))
  ;; inner join: n = 1 in the first half (one whole-cycle piece), 4 in the
  ;; second half (pieces 2 and 3 fall inside it) → 3 onsets
  (let [evs (onsets (params/chop (core/seq* [1 4]) (core/pure brk)))]
    (is (= [0.0 0.5 0.75] (mapv #(get-in % [:value :begin]) evs)))))

(deftest striate-interleaves
  (let [evs (onsets (params/striate 2 (core/seq* [:a :b])))]
    (is (= [[:a 0.0] [:b 0.0] [:a 0.5] [:b 0.5]]
           (mapv #(vector (get-in % [:value :note]) (get-in % [:value :begin])) evs)))
    (is (= [1 4] (second (whole (first evs)))))))

(deftest slice-picks-slices-by-index
  (let [evs (onsets (params/slice 8 (core/seq* [0 3]) (core/pure brk)))]
    (is (= [[0.0 0.125] [0.375 0.5]]
           (mapv #(vector (get-in % [:value :begin]) (get-in % [:value :end])) evs)))
    (is (= [[[0 1] [1 2]] [[1 2] [1 1]]] (mapv whole evs)))))

(deftest slice-wraps-and-rests
  (let [evs (onsets (params/slice 4 (core/seq* [5 :_ -1]) (core/pure brk)))]
    (is (= [0.25 nil 0.75] (mapv #(when (map? (:value %)) (get-in % [:value :begin])) evs)))
    (is (= :_ (:value (second evs))))))

(deftest splice-fits-rate-to-slot
  ;; slot 1/4 cycle, slice 1/4 of the sample → speed 1 in :c units;
  ;; slot 1/2 → speed 1/2
  (let [evs (onsets (params/splice 4 (core/seq* [0 1 2 3]) (core/pure brk)))]
    (is (every? #(= 1.0 (get-in % [:value :rate])) evs))
    (is (every? #(= :c (get-in % [:value :unit])) evs)))
  (let [evs (onsets (params/splice 4 (core/seq* [0 1]) (params/rate 2 (core/pure brk))))]
    (is (= [1.0 1.0] (mapv #(get-in % [:value :rate]) evs)))))

(deftest fit-sets-rate-from-range-and-whole
  (let [evs (onsets (params/fit (core/seq* [brk :_])))]
    (is (= {:bank :break :n 0 :rate 2.0 :unit :c} (:value (first evs))))
    (is (= :_ (:value (second evs))))))

(deftest loop-at-slows-and-sets-speed
  (let [evs (core/query (params/loop-at 2 (params/chop 4 (core/pure brk)))
                        {:start [0 1] :end [2 1]})]
    (is (= 4 (count evs)))
    (is (= [[0 1] [1 2]] (whole (first evs))))
    (is (every? #(= 0.5 (get-in % [:value :rate])) evs))
    (is (every? #(= :c (get-in % [:value :unit])) evs))))

(deftest tl6-params-set-keys
  (let [v #(:value (first (core/query % one-cycle)))]
    (is (= {:note :bd :rate -1} (v (params/speed -1 (core/pure :bd)))))
    (is (= {:note :bd :unit :c} (v (params/unit "c" (core/pure :bd)))))
    (is (= {:note :bd :accelerate -0.5} (v (params/accelerate -0.5 (core/pure :bd)))))
    (is (= {:note :bd :legato 0.5} (v (params/legato 0.5 (core/pure :bd)))))
    (is (= {:note :bd :sustain 0.1} (v (params/sustain 0.1 (core/pure :bd)))))
    (is (= {:note :bd :cut 1} (v ((params/cut 1) (core/pure :bd)))))
    (is (= {:note :bd :loop-begin 0.25 :loop-end 0.5}
           (v (params/loop-end 0.5 (params/loop-begin 0.25 (core/pure :bd))))))
    (is (thrown? js/Error (params/unit :x (core/pure :bd))))))

;;; ── Phase TL7a: per-event insert controls ────────────────────────────

(def ^:private tl7a-controls
  [[params/lpf :lpf] [params/resonance :resonance] [params/hpf :hpf]
   [params/hpq :hpq] [params/bpf :bpf] [params/bpq :bpq]
   [params/shape :shape] [params/crush :crush]
   [params/gain :gain] [params/velocity :velocity]])

(deftest tl7a-controls-set-their-key
  (doseq [[f kw] tl7a-controls]
    (testing (name kw)
      (is (= {:note :c4 kw 3} (:value (first (core/query (f 3 (core/pure :c4)) one-cycle)))))
      (is (= {:bank :bd :n 0 kw 3}
             (:value (first (core/query (f 3 (core/pure {:bank :bd :n 0})) one-cycle))))
          "keeps an existing map (sample event)"))))

(deftest tl7a-controls-transformer-arity
  (doseq [[f kw] tl7a-controls]
    (is (= {:note :hh kw 0.5}
           (:value (first (core/query ((f 0.5) (core/pure :hh)) one-cycle)))))))

(deftest tl7a-controls-no-clamping-in-core
  (is (= 99999 (:lpf (:value (first (core/query (params/lpf 99999 (core/pure :c4)) one-cycle))))))
  (is (= 5 (:gain (:value (first (core/query (params/gain 5 (core/pure :c4)) one-cycle)))))))

(deftest tl7a-lpf-patterned
  (let [evs (core/query (params/lpf (core/seq* [400 4000]) (core/seq* [:hh :hh :hh :hh]))
                        one-cycle)]
    (is (= [400 400 4000 4000] (mapv (comp :lpf :value) evs)))
    (is (= 4 (count evs)) "structure comes from the note pattern")))

(deftest tl7a-lpf-signal-sampled-at-onset
  (let [evs (core/query (params/lpf core/saw (core/seq* [:hh :hh :hh :hh])) one-cycle)]
    (is (= [0 0.25 0.5 0.75] (mapv (comp :lpf :value) evs)))))

(deftest tl7a-chain-builds-one-map
  (let [p   (->> (core/pure :c3) (params/lpf 800) (params/resonance 4)
                 (params/shape 0.4) (params/crush 8) (params/gain 0.5) (params/velocity 0.6))
        v   (:value (first (core/query p one-cycle)))]
    (is (= {:note :c3 :lpf 800 :resonance 4 :shape 0.4 :crush 8 :gain 0.5 :velocity 0.6} v))))
