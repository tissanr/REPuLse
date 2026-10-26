(ns repulse.core-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.core :as c]
            [repulse.core.specs :as specs]))

(deftest test-rat
  (testing "rat reduces"
    (is (= [1 2] (c/rat 2 4)))
    (is (= [1 1] (c/rat 3 3)))
    (is (= [-1 2] (c/rat -2 4))))
  (testing "rat arithmetic"
    (is (= [3 4] (c/rat+ [1 4] [1 2])))
    (is (= [1 4] (c/rat- [1 2] [1 4])))
    (is (= [1 6] (c/rat* [1 2] [1 3])))
    (is (= [2 1] (c/rat-div [1 2] [1 4])))))

(deftest core-data-specs
  (testing "rational vectors are normalized with positive denominators"
    (is (specs/valid-rat? [1 2]))
    (is (not (specs/valid-rat? [2 4])))
    (is (not (specs/valid-rat? [1 0])))
    (is (not (specs/valid-rat? [1 -2]))))
  (testing "spans require valid rational bounds and start before end"
    (is (specs/valid-span? (c/span [0 1] [1 1])))
    (is (not (specs/valid-span? (c/span [1 1] [1 1]))))
    (is (not (specs/valid-span? {:start [0 2] :end [1 1]}))))
  (testing "events and patterns match the core constructors"
    (let [sp (c/span [0 1] [1 1])
          ev (c/event :bd sp sp)]
      (is (specs/valid-event? ev))
      (is (specs/valid-pattern? (c/pure :bd)))
      (is (not (specs/valid-pattern? ev))))))

(deftest test-pure
  (testing "pure yields one event per cycle"
    (let [evs (c/query (c/pure :bd) (c/span (c/int->rat 0) (c/int->rat 1)))]
      (is (= 1 (count evs)))
      (is (= :bd (:value (first evs)))))))

(deftest test-seq
  (testing "seq yields n events per cycle"
    (let [evs (c/query (c/seq* [:bd :sd]) (c/span (c/int->rat 0) (c/int->rat 1)))]
      (is (= 2 (count evs)))
      (is (= :bd (:value (first evs))))
      (is (= :sd (:value (second evs))))))
  (testing "seq event timing"
    (let [evs (c/query (c/seq* [:a :b :c :d]) (c/span (c/int->rat 0) (c/int->rat 1)))]
      (is (= 4 (count evs)))
      (is (= [0 1] (:start (:whole (nth evs 0)))))   ;; 0/4 reduces to 0/1
      (is (= [1 4] (:start (:whole (nth evs 1))))))))

(deftest test-stack
  (testing "stack combines events"
    (let [p1 (c/pure :bd)
          p2 (c/pure :sd)
          evs (c/query (c/stack* [p1 p2]) (c/span (c/int->rat 0) (c/int->rat 1)))]
      (is (= 2 (count evs))))))

(deftest test-fast
  (testing "fast doubles events"
    (let [evs (c/query (c/fast 2 (c/seq* [:bd :sd]))
                       (c/span (c/int->rat 0) (c/int->rat 1)))]
      (is (= 4 (count evs))))))

(deftest test-fmap
  (testing "fmap transforms values"
    (let [evs (c/query (c/fmap name (c/seq* [:bd :sd]))
                       (c/span (c/int->rat 0) (c/int->rat 1)))]
      (is (= "bd" (:value (first evs)))))))

(deftest arrange-test
  (testing "arrange* plays sections in order"
    (let [a   (c/pure :a)
          b   (c/pure :b)
          arr (c/arrange* [[a 2] [b 2]])]
      ;; Cycle 0: section a
      (is (= [:a] (map :value (c/query arr (c/cycle-span 0)))))
      ;; Cycle 1: still section a (it runs for 2 cycles)
      (is (= [:a] (map :value (c/query arr (c/cycle-span 1)))))
      ;; Cycle 2: section b
      (is (= [:b] (map :value (c/query arr (c/cycle-span 2)))))
      ;; Cycle 3: still section b
      (is (= [:b] (map :value (c/query arr (c/cycle-span 3)))))
      ;; Cycle 4: loops back to a
      (is (= [:a] (map :value (c/query arr (c/cycle-span 4)))))
      ;; Cycle 6: loops back to b
      (is (= [:b] (map :value (c/query arr (c/cycle-span 6))))))))

;;; ── Phase I: Pattern Combinators ───────────────────────────────────

(deftest euclidean-5-8
  (testing "euclidean 5 8 distributes 5 onsets across 8 steps"
    (let [evs  (c/query (c/euclidean 5 8 :bd) (c/cycle-span 0))
          vals (mapv :value evs)]
      (is (= 8 (count vals)))
      (is (= 5 (count (filter #(= :bd %) vals))))
      (is (= 3 (count (filter #(= :_ %) vals)))))))

(deftest euclidean-3-8-rotation
  (testing "euclidean with rotation shifts the pattern"
    (let [evs-no-rot (mapv :value (c/query (c/euclidean 3 8 :x) (c/cycle-span 0)))
          evs-rot    (mapv :value (c/query (c/euclidean 3 8 :x 2) (c/cycle-span 0)))]
      (is (= 3 (count (filter #(= :x %) evs-rot))))
      (is (= 5 (count (filter #(= :_ %) evs-rot))))
      (is (not= evs-no-rot evs-rot)))))

(deftest euclidean-4-4
  (testing "euclidean k=n produces all onsets"
    (let [vals (mapv :value (c/query (c/euclidean 4 4 :bd) (c/cycle-span 0)))]
      (is (= [:bd :bd :bd :bd] vals)))))

(deftest cat-basic
  (testing "cat* plays each pattern for one cycle in sequence"
    (let [a   (c/pure :a)
          b   (c/pure :b)
          c-p (c/pure :c)
          pat (c/cat* [a b c-p])]
      (is (= [:a] (mapv :value (c/query pat (c/cycle-span 0)))))
      (is (= [:b] (mapv :value (c/query pat (c/cycle-span 1)))))
      (is (= [:c] (mapv :value (c/query pat (c/cycle-span 2)))))
      ;; Loops back
      (is (= [:a] (mapv :value (c/query pat (c/cycle-span 3))))))))

(deftest cat-with-seq
  (testing "cat* with seq patterns preserves internal structure"
    (let [pat (c/cat* [(c/seq* [:bd :sd]) (c/pure :hh)])]
      (is (= [:bd :sd] (mapv :value (c/query pat (c/cycle-span 0)))))
      (is (= [:hh]     (mapv :value (c/query pat (c/cycle-span 1))))))))

;; ── Queries spanning several cycles ─────────────────────────────────
;; The scheduler asks for one cycle at a time, but (fast n …) asks its input
;; for n cycles at once — cycle-dependent combinators must answer per cycle.

(defn- vals-in [pat start end]
  (mapv :value (c/query pat (c/span (c/int->rat start) (c/int->rat end)))))

(deftest split-cycles-test
  (is (= [(c/span [0 1] [1 1]) (c/span [1 1] [2 1]) (c/span [2 1] [5 2])]
         (c/split-cycles (c/span [0 1] [5 2]))))
  (is (= [(c/span [1 4] [3 4])] (c/split-cycles (c/span [1 4] [3 4]))))
  (is (empty? (c/split-cycles (c/span [1 1] [1 1])))))

(deftest cat-multi-cycle-span
  (let [pat (c/cat* [(c/pure :a) (c/pure :b)])]
    (is (= [:a :b :a] (vals-in pat 0 3)))
    (testing "(fast 2 (cat a b)) plays both patterns every cycle"
      (is (= [:a :b] (vals-in (c/fast 2 pat) 0 1))))))

(deftest every-multi-cycle-span
  (let [pat (c/every 2 (fn [p] (c/fmap (constantly :x) p)) (c/pure :a))]
    (is (= [:x :a :x :a] (vals-in pat 0 4)))
    (is (= [:x :a] (vals-in (c/fast 2 pat) 0 1)))))

(deftest sometimes-by-multi-cycle-span
  (let [pat (c/sometimes-by 0.5 (fn [p] (c/fmap (constantly :x) p)) (c/pure :a))]
    (is (= (into [] (mapcat #(vals-in pat % (inc %))) (range 6))
           (vals-in pat 0 6)))))

(deftest arrange-multi-cycle-span
  (let [arr (c/arrange* [[(c/pure :a) 1] [(c/pure :b) 2]])]
    (is (= [:a :b :b :a] (vals-in arr 0 4)))))

(deftest rev-multi-cycle-and-partial-spans
  (let [pat (c/rev (c/cat* [(c/seq* [:a :b]) (c/seq* [:c :d])]))]
    (testing "each cycle is reversed on its own"
      (is (= [:b :a :d :c]
             (->> (c/query pat (c/span [0 1] [2 1]))
                  (sort-by #(c/rat->float (:start (:whole %))))
                  (mapv :value)))))
    (testing "a partial span returns the events that land in it after reversal"
      (let [evs (c/query (c/rev (c/seq* [:a :b :c :d])) (c/span [0 1] [1 4]))]
        (is (= [:d] (mapv :value evs)))
        (is (= (c/span [0 1] [1 4]) (:whole (first evs))))))))

(deftest late-shifts-forward
  (testing "late shifts events forward in time"
    (let [pat    (c/late 0.5 (c/pure :bd))
          evs    (c/query pat (c/span [0 1] [2 1]))
          starts (set (map #(:start (:whole %)) evs))]
      (is (pos? (count evs)))
      ;; cycle-0 event shifted from [0,1] to [1/2]=[1,2]; [0,1] should not appear
      (is (contains? starts [1 2]))
      (is (not (contains? starts [0 1]))))))

(deftest early-shifts-backward
  (testing "early is the inverse of late"
    (let [pat    (c/early 0.25 (c/pure :bd))
          evs    (c/query pat (c/span [0 1] [2 1]))
          starts (set (map #(:start (:whole %)) evs))]
      (is (pos? (count evs)))
      ;; cycle-0 event shifted from [0,1] to [-1/4]=[-1,4]; [0,1] should not appear
      (is (contains? starts [-1 4]))
      (is (not (contains? starts [0 1]))))))

(deftest sometimes-deterministic
  (testing "sometimes produces consistent results for the same cycle"
    (let [pat  (c/sometimes c/rev (c/pure :bd))
          evs1 (c/query pat (c/cycle-span 5))
          evs2 (c/query pat (c/cycle-span 5))]
      (is (= (mapv :value evs1) (mapv :value evs2))))))

(deftest sometimes-by-zero-never-applies
  (testing "sometimes-by 0.0 never applies the transform"
    (let [pat (c/sometimes-by 0.0 (fn [p] (c/fmap (constantly :WRONG) p))
                              (c/pure :bd))]
      (doseq [cy (range 20)]
        (is (= [:bd] (mapv :value (c/query pat (c/cycle-span cy)))))))))

(deftest sometimes-by-one-always-applies
  (testing "sometimes-by 1.0 always applies the transform"
    (let [pat (c/sometimes-by 1.0 (fn [p] (c/fmap (constantly :YES) p))
                              (c/pure :bd))]
      (doseq [cy (range 20)]
        (is (= [:YES] (mapv :value (c/query pat (c/cycle-span cy)))))))))

(deftest degrade-drops-some-events
  (testing "degrade drops approximately half the events"
    (let [pat (c/degrade (c/seq* [:a :b :c :d :e :f :g :h]))
          evs (c/query pat (c/cycle-span 0))]
      (is (< (count evs) 8))
      (is (pos? (count evs))))))

(deftest degrade-by-zero-keeps-all
  (testing "degrade-by 0.0 keeps all events"
    (let [pat (c/degrade-by 0.0 (c/seq* [:a :b :c :d]))
          evs (c/query pat (c/cycle-span 0))]
      (is (= 4 (count evs))))))

(deftest degrade-deterministic
  (testing "degrade produces same results for same query"
    (let [pat  (c/degrade (c/seq* [:a :b :c :d :e :f :g :h]))
          evs1 (c/query pat (c/cycle-span 0))
          evs2 (c/query pat (c/cycle-span 0))]
      (is (= (mapv :value evs1) (mapv :value evs2))))))

(deftest choose-picks-one-per-cycle
  (testing "choose returns exactly one event per cycle"
    (let [pat (c/choose [:a :b :c :d])]
      (doseq [cy (range 10)]
        (let [evs (c/query pat (c/cycle-span cy))]
          (is (= 1 (count evs)))
          (is (contains? #{:a :b :c :d} (:value (first evs)))))))))

(deftest choose-deterministic
  (testing "choose is deterministic for the same cycle"
    (let [pat (c/choose [:a :b :c :d])]
      (doseq [cy (range 10)]
        (is (= (mapv :value (c/query pat (c/cycle-span cy)))
               (mapv :value (c/query pat (c/cycle-span cy)))))))))

(deftest wchoose-picks-one-per-cycle
  (testing "wchoose returns exactly one event per cycle"
    (let [pat (c/wchoose [[0.5 :bd] [0.3 :sd] [0.2 :hh]])]
      (doseq [cy (range 10)]
        (let [evs (c/query pat (c/cycle-span cy))]
          (is (= 1 (count evs)))
          (is (contains? #{:bd :sd :hh} (:value (first evs)))))))))

(deftest choose-attaches-source
  (testing "choose attaches :source from the sources vector to events"
    (let [srcs [{:from 0 :to 3} {:from 4 :to 7} {:from 8 :to 11} {:from 12 :to 15}]
          pat  (c/choose [:a :b :c :d] srcs)]
      (doseq [cy (range 20)]
        (let [ev  (first (c/query pat (c/cycle-span cy)))
              idx (mod (c/cycle-hash cy) 4)]
          (is (= (nth srcs idx) (:source ev))))))))

(deftest wchoose-attaches-source
  (testing "wchoose attaches :source from the sources vector to events"
    (let [srcs [{:from 0 :to 3} {:from 4 :to 7} {:from 8 :to 11}]
          pat  (c/wchoose [[0.5 :bd] [0.3 :sd] [0.2 :hh]] srcs)]
      (doseq [cy (range 20)]
        (let [ev (first (c/query pat (c/cycle-span cy)))]
          (is (contains? (set srcs) (:source ev))))))))

(deftest off-layers-original-and-shifted
  (testing "off produces events from both original and transformed copy"
    (let [pat (c/off 0.5 c/rev (c/seq* [:a :b]))
          evs (c/query pat (c/span [0 1] [2 1]))]
      (is (> (count evs) 2)))))

;;; ── Phase TL1: onsets, rationals, app-*, joins, patterned args ───────

(defn- cyc [n] (c/cycle-span n))

(defn- ev-summary
  "[value whole-start whole-end part-start part-end] for compact assertions."
  [e]
  [(:value e)
   (:start (:whole e)) (:end (:whole e))
   (:start (:part e)) (:end (:part e))])

(defn- by-time
  "Event values ordered by part start (rev returns events in query order)."
  [evs]
  (map :value (sort-by #(c/rat->float (:start (:part %))) evs)))

(defn- onset-values [pat sp]
  (map :value (c/query-onsets pat sp)))

(deftest onset-predicate
  (let [w (c/span [0 1] [2 1])]
    (is (true? (c/onset? (c/event :bd w (c/span [0 1] [1 1])))))
    (is (false? (c/onset? (c/event :bd w (c/span [1 1] [2 1])))))
    (is (false? (c/onset? {:value :bd :part w})) "events without a whole are never onsets")))

(deftest slow-whole-crossing-cycles-is-one-onset
  (testing "plain query returns a fragment in every cycle (the pre-TL1 double trigger)"
    (let [p (c/slow 4 (c/seq* [:bd :sd]))]
      (is (= [1 1 1 1] (map #(count (c/query p (cyc %))) (range 4))))
      (testing "but only the onsets trigger: :bd once per 4 cycles"
        (is (= [[:bd] [] [:sd] []] (map #(vec (onset-values p (cyc %))) (range 4)))))))
  (testing "(late 1/2 (pure :bd)) triggers once per cycle"
    (let [p (c/late [1 2] (c/pure :bd))]
      (is (= [2 2] (map #(count (c/query p (cyc %))) (range 2))))
      (is (= [1 1] (map #(count (c/query-onsets p (cyc %))) (range 2))))
      (is (= [1 2] (:start (:whole (first (c/query-onsets p (cyc 0))))))))))

(deftest to-rat-coercion
  (is (= [1 4] (c/->rat 0.25)))
  (is (= [3 2] (c/->rat 1.5)))
  (is (= [1 3] (c/->rat 0.3333333)))
  (is (= [1 2] (c/->rat [2 4])))
  (is (= [3 1] (c/->rat 3)))
  (is (= [-1 8] (c/->rat -0.125)))
  (is (thrown? js/Error (c/->rat :bd))))

(deftest rat-overflow-guard
  (testing "rationals beyond Number.MAX_SAFE_INTEGER throw in dev builds"
    (let [big (* 2 9007199254740991)]
      (is (= :rational-overflow
             (try (c/rat big 3) nil
                  (catch :default e (:type (ex-data e)))))))))

(deftest fast-one-third-stays-exact
  (testing "(fast 1/3 …) over cycles 0–3000: exact rationals, small denominators"
    (let [p   (c/fast [1 3] (c/seq* [:a :b]))
          evs (mapcat #(c/query p (cyc %)) (range 0 3001 7))]
      (is (seq evs))
      (is (every? (fn [e]
                    (every? (fn [[n d]] (and (integer? n) (integer? d) (<= 1 d 6)))
                            [(:start (:whole e)) (:end (:whole e))
                             (:start (:part e)) (:end (:part e))]))
                  evs))
      (testing "first event of cycle 3000 is :a with whole [3000, 6003/2)"
        (let [e (first (c/query p (cyc 3000)))]
          (is (= :a (:value e)))
          (is (= [[3000 1] [6003 2]] [(:start (:whole e)) (:end (:whole e))])))))))

(deftest float-late-is-exact
  (is (= [1 3] (:start (:whole (first (c/query-onsets (c/late 0.3333333 (c/pure :x)) (cyc 0))))))))

;; Tidal reference: "10 20" |+ "1 2 3", "10 20" +| "1 2 3", "10 20" |+| "1 2 3"
(def ^:private tens  (c/seq* [10 20]))
(def ^:private ones  (c/seq* [1 2 3]))

(deftest app-left-matches-tidal
  (is (= [[11 [0 1] [1 2] [0 1] [1 3]]
          [12 [0 1] [1 2] [1 3] [1 2]]
          [22 [1 2] [1 1] [1 2] [2 3]]
          [23 [1 2] [1 1] [2 3] [1 1]]]
         (map ev-summary (c/query (c/app-left + tens ones) (cyc 0))))))

(deftest app-right-matches-tidal
  (is (= [[11 [0 1] [1 3] [0 1] [1 3]]
          [12 [1 3] [2 3] [1 3] [1 2]]
          [22 [1 3] [2 3] [1 2] [2 3]]
          [23 [2 3] [1 1] [2 3] [1 1]]]
         (map ev-summary (c/query (c/app-right + tens ones) (cyc 0))))))

(deftest app-both-matches-tidal
  (is (= [[11 [0 1] [1 3] [0 1] [1 3]]
          [12 [1 3] [1 2] [1 3] [1 2]]
          [22 [1 2] [2 3] [1 2] [2 3]]
          [23 [2 3] [1 1] [2 3] [1 1]]]
         (map ev-summary (c/query (c/app-both + tens ones) (cyc 0))))))

(deftest app-keeps-structure-source
  (let [a (c/pure 1 {:from 0 :to 1})
        b (c/pure 2 {:from 5 :to 6})]
    (is (= {:from 0 :to 1} (:source (first (c/query (c/app-left + a b) (cyc 0))))))
    (is (= {:from 5 :to 6} (:source (first (c/query (c/app-right + a b) (cyc 0))))))
    (is (= {:from 0 :to 1} (:source (first (c/query (c/app-both + a b) (cyc 0))))))))

(deftest combine-is-app-right
  (is (= (map ev-summary (c/query (c/app-right vector tens ones) (cyc 0)))
         (map ev-summary (c/query (c/combine vector tens ones) (cyc 0))))))

(deftest inner-join-hand-computed
  ;; outer: two halves carrying (seq :a :b) and (pure :c); structure from inner.
  ;; first half sees (seq :a :b)'s first half → :a [0,1/2);
  ;; second half sees (pure :c) whole [0,1) cut to part [1/2,1).
  (let [pp (c/seq* [(c/seq* [:a :b]) (c/pure :c)])]
    (is (= [[:a [0 1] [1 2] [0 1] [1 2]]
            [:c [0 1] [1 1] [1 2] [1 1]]]
           (map ev-summary (c/query (c/inner-join pp) (cyc 0)))))))

(deftest outer-join-hand-computed
  ;; structure from outer halves; values sampled at each outer whole's start.
  (let [pp (c/seq* [(c/seq* [:a :b]) (c/seq* [:x :y :z])])]
    (is (= [[:a [0 1] [1 2] [0 1] [1 2]]
            [:y [1 2] [1 1] [1 2] [1 1]]]
           (map ev-summary (c/query (c/outer-join pp) (cyc 0)))))))

(deftest squeeze-join-hand-computed
  ;; each outer half squeezes a full cycle of its inner pattern.
  (let [pp (c/seq* [(c/seq* [:a :b]) (c/pure :c)])]
    (is (= [[:a [0 1] [1 4] [0 1] [1 4]]
            [:b [1 4] [1 2] [1 4] [1 2]]
            [:c [1 2] [1 1] [1 2] [1 1]]]
           (map ev-summary (c/query (c/squeeze-join pp) (cyc 0)))))
    (testing "cycle 1 plays inner cycle 1"
      (is (= [[:a [1 1] [5 4] [1 1] [5 4]]
              [:b [5 4] [3 2] [5 4] [3 2]]
              [:c [3 2] [2 1] [3 2] [2 1]]]
             (map ev-summary (c/query (c/squeeze-join pp) (cyc 1))))))))

(deftest patternify-scalar-fast-path
  (let [f (c/patternify (fn [n p] [n p]))]
    (is (= [2 :p] (f 2 :p)) "scalars call f directly")))

(deftest fast-with-pattern-factor
  (is (= [[:bd [0 1] [1 2] [0 1] [1 2]]
          [:bd [1 2] [3 4] [1 2] [3 4]]
          [:sd [3 4] [1 1] [3 4] [1 1]]]
         (map ev-summary (c/query (c/fast (c/seq* [1 2]) (c/seq* [:bd :sd])) (cyc 0))))))

(deftest slow-early-every-patterned
  (testing "slow with a per-cycle factor"
    (let [p (c/slow (c/cat* [(c/pure 1) (c/pure 2)]) (c/seq* [:a :b]))]
      (is (= 2 (count (c/query-onsets p (cyc 0)))))
      (is (= 1 (count (c/query-onsets p (cyc 1)))))))
  (testing "early with a patterned amount"
    (let [p (c/early (c/cat* [(c/pure 0) (c/pure [1 4])]) (c/seq* [:a :b :c :d]))]
      (is (= [:a :b :c :d] (map :value (c/query-onsets p (cyc 0)))))
      (is (= [:b :c :d :a] (map :value (c/query-onsets p (cyc 1)))))))
  (testing "every with a patterned n"
    (let [p (c/every (c/cat* [(c/pure 1) (c/pure 3)]) c/rev (c/seq* [:a :b]))]
      ;; cycle 0: n=1 → rev; cycle 1: n=3 → 1 mod 3 ≠ 0 → plain
      (is (= [:b :a] (by-time (c/query p (cyc 0)))))
      (is (= [:a :b] (by-time (c/query p (cyc 1))))))))

(deftest euclidean-patterned-k
  (let [p (c/euclidean (c/cat* [(c/pure 3) (c/pure 5)]) 8 :bd)
        hits (fn [n] (count (remove #(= :_ (:value %)) (c/query-onsets p (cyc n)))))]
    (is (= 3 (hits 0)))
    (is (= 5 (hits 1)))
    (is (= (map :value (c/query (c/euclidean 3 8 :bd) (cyc 0)))
           (map :value (c/query p (cyc 0)))))))

(deftest degrade-sometimes-off-patterned
  (testing "degrade-by with a per-cycle probability"
    (let [base (c/fast 8 (c/pure :hh))
          p    (c/degrade-by (c/cat* [(c/pure 0) (c/pure 1)]) base)]
      (is (= 8 (count (c/query p (cyc 0)))))
      (is (= 0 (count (c/query p (cyc 1)))))))
  (testing "sometimes-by with a per-cycle probability"
    (let [p (c/sometimes-by (c/cat* [(c/pure 0) (c/pure 1)]) c/rev (c/seq* [:a :b]))]
      (is (= [:a :b] (by-time (c/query p (cyc 0)))))
      (is (= [:b :a] (by-time (c/query p (cyc 1)))))))
  (testing "off with a patterned amount"
    (let [p (c/off (c/cat* [(c/pure [1 4]) (c/pure [1 2])]) identity (c/pure :x))]
      (is (= [[0 1] [1 4]] (map #(:start (:whole %)) (c/query-onsets p (cyc 0)))))
      (is (= [[1 1] [3 2]] (map #(:start (:whole %)) (c/query-onsets p (cyc 1))))))))

(deftest fast-zero-is-silence
  (is (empty? (c/query (c/fast 0 (c/pure :bd)) (cyc 0))))
  (is (empty? (c/query (c/slow 0 (c/pure :bd)) (cyc 0)))))

;;; ── Phase TL2: continuous signals ───────────────────────────────────

(defn- near? [a b tol] (< (Math/abs (- a b)) tol))

(defn- seg-vals
  "Values of (segment n sig) over cycle k."
  ([n sig] (seg-vals n sig 0))
  ([n sig k] (mapv :value (sort-by #(c/rat->float (:start (:whole %)))
                                   (c/query-onsets (c/segment n sig) (c/cycle-span k))))))

(defn- all-near? [expected actual tol]
  (and (= (count expected) (count actual))
       (every? true? (map #(near? %1 %2 tol) expected actual))))

(deftest tl2-signal-shape
  (testing "one analog event covering the query span"
    (let [evs (c/query c/sine (c/span [1 4] [1 2]))]
      (is (= 1 (count evs)))
      (is (contains? (first evs) :whole))
      (is (nil? (:whole (first evs))))
      (is (= (c/span [1 4] [1 2]) (:part (first evs))))
      (is (near? 1 (:value (first evs)) 1e-12))
      (is (specs/valid-event? (first evs)))
      (is (specs/analog-event? (first evs)))
      (is (c/analog? (first evs)))))
  (testing "no splitting at cycle boundaries"
    (let [evs (c/query c/saw (c/span [1 2] [5 2]))]
      (is (= 1 (count evs)))
      (is (= (c/span [1 2] [5 2]) (:part (first evs))))
      (is (= 0.5 (:value (first evs))))))
  (testing "empty and inverted spans"
    (is (= [] (c/query c/saw (c/span [1 2] [1 2]))))
    (is (= [] (c/query c/saw (c/span [1 1] [1 2])))))
  (testing "never an onset"
    (is (empty? (c/query-onsets c/sine (c/cycle-span 0))))))

(deftest tl2-segment
  (testing "segment 4 saw"
    (let [evs (sort-by #(c/rat->float (:start (:whole %)))
                       (c/query-onsets (c/segment 4 c/saw) (c/cycle-span 0)))]
      (is (= [0 0.25 0.5 0.75] (mapv :value evs)))
      (is (= [(c/span [0 1] [1 4]) (c/span [1 4] [1 2]) (c/span [1 2] [3 4]) (c/span [3 4] [1 1])]
             (mapv :whole evs))))
    (is (= [0 0.25 0.5 0.75] (seg-vals 4 c/saw 1)))
    (is (= [(c/span [1 1] [5 4])]
           (map :whole (take 1 (sort-by #(c/rat->float (:start (:whole %)))
                                        (c/query-onsets (c/segment 4 c/saw) (c/cycle-span 1))))))))
  (testing "patterned n"
    (let [evs (sort-by #(c/rat->float (:start (:whole %)))
                       (c/query-onsets (c/segment (c/seq* [2 4]) c/saw) (c/cycle-span 0)))]
      (is (= [[0 1] [1 2] [3 4]] (mapv #(:start (:whole %)) evs)))
      (is (= [0 0.5 0.75] (mapv :value evs))))))

(deftest tl2-waveforms
  (is (all-near? [0.5 1 0.5 0] (seg-vals 4 c/sine) 1e-12))
  (is (all-near? [0 0.5 1 0.5] (seg-vals 4 c/cosine) 1e-12))
  (is (= [0 0.5 1 0.5] (seg-vals 4 c/tri)))
  (is (= [1 0.5 0 0.5] (seg-vals 4 c/itri)))
  (is (= [0 0 1 1] (seg-vals 4 c/square)))
  (is (= [1 1 0 0] (seg-vals 4 c/isquare)))
  (is (all-near? [1 0.75 0.5 0.25] (seg-vals 4 c/isaw) 1e-12))
  (is (all-near? [0 1 0 -1] (seg-vals 4 c/sine2) 1e-12))
  (is (all-near? [-1 -0.5 0 0.5] (seg-vals 4 c/saw2) 1e-12))
  (is (= [1 1.25 1.5 1.75] (seg-vals 4 c/time 1)))
  (testing "negative time wraps with floored mod"
    (is (= 0.75 (:value (first (c/query c/saw (c/span [-1 4] [0 1]))))))))

(deftest tl2-time->rand
  (testing "Tidal timeToRand reference values (exact)"
    (is (= 0 (c/time->rand [0 1])))
    (is (= 0.6295689214020967 (c/time->rand [1 4])))
    (is (= 0.2591378428041935 (c/time->rand [1 2])))
    (is (= 0.20052618719637394 (c/time->rand [3 4])))
    (is (= 0.5177721455693245 (c/time->rand [1 1])))
    (is (= 0.4010523743927479 (c/time->rand [3 2])))
    (is (= 0.30286644771695137 (c/time->rand [73 10])))
    (is (= 0.6520281322300434 (c/time->rand [-1 4]))))
  (testing "int seeds and xorwise"
    (is (= 447392 (js/Number (c/time->int-seed [1 4]))))
    (is (= -447392 (js/Number (c/time->int-seed [-1 4]))))
    (is (= 118449597881 (js/Number (c/xorwise (c/time->int-seed [1 4]))))))
  (testing "deterministic"
    (is (= (c/time->rand [7 3]) (c/time->rand [7 3]))))
  (testing "segment rand"
    (is (= [0 0.6295689214020967 0.2591378428041935 0.20052618719637394]
           (seg-vals 4 c/rand)))))

(deftest tl2-irand-perlin-run
  (is (= [0 5 2 1] (seg-vals 4 (c/irand 8))))
  (let [at (fn [t] (:value (first (c/query c/perlin (c/span t (c/rat+ t [1 1]))))))]
    (is (near? 0 (at [0 1]) 1e-12))
    (is (near? 0.053597507256199606 (at [1 4]) 1e-12))
    (is (near? 0.25888607278466225 (at [1 2]) 1e-12))
    (is (near? 0.2768947295844555 (at [3 2]) 1e-12))
    (is (near? 0.1463428430059552 (at [73 10]) 1e-12)))
  (let [evs (sort-by #(c/rat->float (:start (:whole %)))
                     (c/query-onsets (c/run 4) (c/cycle-span 0)))]
    (is (= [0 1 2 3] (mapv :value evs)))
    (is (= [[0 1] [1 4] [1 2] [3 4]] (mapv #(:start (:whole %)) evs))))
  (is (empty? (c/query (c/run 0) (c/cycle-span 0)))))

(deftest tl2-params-sampled-at-onsets
  (testing "app-right with a signal samples at the structure event's onset"
    (let [pat (c/app-right (fn [v _] v) c/sine (c/fast 8 (c/seq* [:hh])))
          evs (sort-by #(c/rat->float (:start (:whole %))) (c/query-onsets pat (c/cycle-span 0)))]
      (is (= 8 (count evs)))
      (is (all-near? [0.5 0.8535533905932737 1 0.8535533905932737 0.5
                      0.14644660940672627 0 0.14644660940672616]
                     (mapv :value evs) 1e-12))))
  (testing "app-left with a signal on the right"
    (let [pat (c/app-left (fn [a b] [a b]) (c/seq* [:a :b]) c/saw)
          evs (c/query-onsets pat (c/cycle-span 0))]
      (is (= [[:a 0] [:b 0.5]] (mapv :value evs)))))
  (testing "slow 4 saw swell"
    (let [pat (c/range-pat 0.3 0.9 (c/slow 4 c/saw))
          v   (fn [k t] (:value (first (c/query pat (c/span (c/rat+ [k 1] t) [(+ k 2) 1])))))]
      (is (near? 0.3 (v 0 [0 1]) 1e-12))
      (is (near? 0.375 (v 0 [1 2]) 1e-12))
      (is (near? 0.440625 (v 0 [15 16]) 1e-12))
      (is (near? 0.890625 (v 3 [15 16]) 1e-12)))))

(deftest tl2-range
  (testing "range-pat"
    (let [vs (seg-vals 16 (c/range-pat 2 6 c/saw))]
      (is (= 2 (first vs)))
      (is (every? #(and (>= % 2) (< % 6)) vs)))
    (is (near? 6 (nth (seg-vals 4 (c/range-pat 2 6 c/sine)) 1) 1e-12))
    (testing "patterned bounds keep analog structure"
      (let [evs (c/query (c/range-pat (c/pure 0) (c/pure 10) c/saw) (c/span [1 2] [1 2]))]
        (is (= [] evs)))
      (let [evs (c/query (c/range-pat (c/pure 0) (c/pure 10) c/saw) (c/span [1 4] [1 2]))]
        (is (= 1 (count evs)))
        (is (nil? (:whole (first evs))))
        (is (near? 2.5 (:value (first evs)) 1e-12)))))
  (testing "rangex-pat"
    (is (all-near? [20 63.245553203367585 200] (take 3 (seg-vals 4 (c/rangex-pat 20 2000 c/saw))) 1e-9))
    (is (thrown? js/Error (c/rangex-pat 0 10 c/saw))))
  (testing "range2-pat"
    (is (all-near? (seg-vals 4 c/sine2) (seg-vals 4 (c/range2-pat -1 1 c/sine2)) 1e-12))))

(deftest tl2-bipolar
  (is (= [-1 0 1] (mapv :value (c/query-onsets (c/to-bipolar (c/seq* [0 0.5 1])) (c/cycle-span 0)))))
  (is (= [0 0.5 1] (mapv :value (c/query-onsets (c/from-bipolar (c/seq* [-1 0 1])) (c/cycle-span 0)))))
  (is (= [:bd] (mapv :value (c/query-onsets (c/to-bipolar (c/seq* [:bd])) (c/cycle-span 0))))))

(deftest tl2-nil-whole-safety
  (doseq [[nm p] [["fast" (c/fast 2 c/sine)] ["slow" (c/slow 4 c/sine)] ["rev" (c/rev c/sine)]
                  ["late" (c/late [1 4] c/sine)] ["early" (c/early [1 4] c/sine)]]]
    (let [evs (c/query p (c/cycle-span 0))]
      (is (seq evs) nm)
      (is (every? #(and (contains? % :whole) (nil? (:whole %))) evs) nm)))
  (is (= [0 0.5 0 0.5] (seg-vals 4 (c/fast 2 c/saw))))
  (is (= [0.75 0 0.25 0.5] (seg-vals 4 (c/late [1 4] c/saw)))))

(deftest tl2-steady-signal
  (is (= [3 3 3 3] (seg-vals 4 (c/steady 3))))
  (is (= [0 2 4 6] (seg-vals 4 (c/signal #(* 8 (c/rat->float %)))))))

;;; ── Phase TL3: Björklund, structure, timecat ────────────────────────

(defn- bools->str [bs] (apply str (map #(if % "x" ".") bs)))

(deftest tl3-bjorklund-table
  (is (= "x.x.." (bools->str (c/bjorklund 2 5))))
  (is (= "x..x..x." (bools->str (c/bjorklund 3 8))))
  (is (= "x.xx.xx." (bools->str (c/bjorklund 5 8))))
  (is (= "x.xx.x.xx.x." (bools->str (c/bjorklund 7 12))))
  (is (= "...." (bools->str (c/bjorklund 0 4))))
  (is (= ".xx.xx.x" (bools->str (c/bjorklund -3 8))))
  (is (= "xxxxxxxx" (bools->str (c/bjorklund 9 8))))
  (is (= [] (c/bjorklund 3 0))))

(defn- cvals [pat n] (mapv :value (sort-by #(c/rat->float (:start (:whole %)))
                                           (c/query pat (c/cycle-span n)))))

(deftest tl3-euclidean-distribution
  (is (= [:x :_ :_ :x :_ :_ :x :_] (cvals (c/euclidean 3 8 :x) 0)))
  (is (= [:_ :x :_ :_ :x :_ :x :_] (cvals (c/euclidean 3 8 :x 2) 0)))
  (is (= [:x :_ :x :x :_ :x :x :_] (cvals (c/euclidean 5 8 :x) 0))))

(defn- wholes [pat n]
  (mapv (fn [e] [(:value e) (:whole e)])
        (sort-by #(c/rat->float (:start (:whole %))) (c/query pat (c/cycle-span n)))))

(deftest tl3-step-true
  (doseq [v [false nil 0 :_ :f :false]] (is (not (c/step-true? v))))
  (doseq [v [true 1 :t :x :bd]] (is (c/step-true? v))))

(deftest tl3-struct-core
  (is (= [[:bd (c/span [0 1] [1 4])] [:bd (c/span [1 2] [3 4])] [:bd (c/span [3 4] [1 1])]]
         (wholes (c/struct (c/seq* [true false true true]) (c/pure :bd)) 0)))
  (let [evs (c/query (c/struct (c/pure true) (c/seq* [:a :b])) (c/cycle-span 0))]
    (is (= [:a :b] (mapv :value evs)))
    (is (= 1 (count (filter c/onset? evs))))))

(deftest tl3-euclidean-struct-core
  (is (= [[:bd (c/span [0 1] [1 8])] [:bd (c/span [3 8] [1 2])] [:sd (c/span [3 4] [7 8])]]
         (wholes (c/euclidean 3 8 (c/seq* [:bd :sd])) 0))))

(deftest tl3-timecat-core
  (is (= [[:e3 (c/span [0 1] [3 4])] [:g3 (c/span [3 4] [1 1])]]
         (wholes (c/timecat [[3 :e3] [1 :g3]]) 0)))
  (is (= [] (c/query (c/timecat []) (c/cycle-span 0)))))

(deftest tl3-ply-core
  (is (= [[:bd (c/span [0 1] [1 4])] [:bd (c/span [1 4] [1 2])]
          [:sd (c/span [1 2] [3 4])] [:sd (c/span [3 4] [1 1])]]
         (wholes (c/ply 2 (c/seq* [:bd :sd])) 0)))
  (is (= [] (c/query (c/ply 0 (c/seq* [:bd :sd])) (c/cycle-span 0)))))

(deftest tl3-rot-fragment-query
  (let [evs (c/query (c/rot 1 (c/seq* [:a :b :c :d])) (c/span [1 2] [1 1]))]
    (is (= [[:d (c/span [1 2] [3 4])] [:a (c/span [3 4] [1 1])]]
           (mapv (fn [e] [(:value e) (:whole e)]) (sort-by #(c/rat->float (:start (:whole %))) evs))))))

(deftest tl3-polymeter-core
  (is (= [:a :b :c :a] (cvals (c/polymeter 4 [[:a :b :c]]) 0)))
  (is (= [:b :c :a :b] (cvals (c/polymeter 4 [[:a :b :c]]) 1)))
  (is (= [] (c/query (c/polymeter 0 [[:a :b]]) (c/cycle-span 0)))))
