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
