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
    ;; TL4: sometimes-by is a stack of two branches, so a multi-cycle query lists
    ;; branch by branch; compare the events in time order instead.
    (let [by-start (fn [evs] (mapv :value (sort-by #(c/rat->float (:start (:whole %))) evs)))]
      (is (= (into [] (mapcat #(by-start (c/query pat (c/cycle-span %)))) (range 6))
             (by-start (c/query pat (c/span (c/int->rat 0) (c/int->rat 6)))))))))

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
  (testing "choose-cycles (the pre-TL4 choose) returns exactly one event per cycle"
    (let [pat (c/choose-cycles [:a :b :c :d])]
      (doseq [cy (range 10)]
        (let [evs (c/query pat (c/cycle-span cy))]
          (is (= 1 (count evs)))
          (is (contains? #{:a :b :c :d} (:value (first evs)))))))))

(deftest choose-deterministic
  (testing "choose-cycles is deterministic for the same cycle"
    (let [pat (c/choose-cycles [:a :b :c :d])]
      (doseq [cy (range 10)]
        (is (= (mapv :value (c/query pat (c/cycle-span cy)))
               (mapv :value (c/query pat (c/cycle-span cy)))))))))

(deftest wchoose-picks-one-per-cycle
  (testing "wchoose-cycles (the pre-TL4 wchoose) returns exactly one event per cycle"
    (let [pat (c/wchoose-cycles [[0.5 :bd] [0.3 :sd] [0.2 :hh]])]
      (doseq [cy (range 10)]
        (let [evs (c/query pat (c/cycle-span cy))]
          (is (= 1 (count evs)))
          (is (contains? #{:bd :sd :hh} (:value (first evs)))))))))

(deftest choose-attaches-source
  (testing "choose-cycles attaches :source of the chosen value (TL4 vector, cycles 0-7)"
    (let [srcs [{:from 0 :to 3} {:from 4 :to 7} {:from 8 :to 11}]
          pat  (c/choose-cycles [:a :b :c] srcs)
          idxs [0 1 0 2 0 0 1 0]]
      (doseq [cy (range 8)]
        (let [ev (first (c/query pat (c/cycle-span cy)))]
          (is (= (nth [:a :b :c] (nth idxs cy)) (:value ev)))
          (is (= (nth srcs (nth idxs cy)) (:source ev))))))))

(deftest wchoose-attaches-source
  (testing "wchoose attaches :source from the sources vector to events"
    (let [srcs [{:from 0 :to 3} {:from 4 :to 7} {:from 8 :to 11}]
          pat  (c/wchoose-cycles [[0.5 :bd] [0.3 :sd] [0.2 :hh]] srcs)]
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

;;; ── Phase TL3b ───────────────────────────────────────────────────────

(defn- tb-rs [[n d]] (if (= d 1) (str n) (str n "/" d)))

(defn- tb-wop [e] (or (:whole e) (:part e)))

(defn- tb-evs
  "Events of cycles from..to as [value ws we] (part = whole) or
   [value ws we ps pe]; rationals as strings; sorted by whole/part start."
  ([pat c] (tb-evs pat c c))
  ([pat from to]
   (->> (mapcat #(c/query pat (c/cycle-span %)) (range from (inc to)))
        (sort-by (juxt #(c/rat->float (:start (tb-wop %)))
                       #(c/rat->float (:start (:part %)))
                       #(str (:value %))))
        (mapv (fn [e]
                (let [w (:whole e) p (:part e)]
                  (if (= w p)
                    [(:value e) (tb-rs (:start w)) (tb-rs (:end w))]
                    [(:value e) (tb-rs (:start w)) (tb-rs (:end w))
                     (tb-rs (:start p)) (tb-rs (:end p))])))))))

(defn- tb-vals [pat c] (mapv first (tb-evs pat c)))

(defn- tb-onsets [pat c]
  (->> (c/query-onsets pat (c/cycle-span c))
       (sort-by #(c/rat->float (:start (:whole %))))
       (mapv #(tb-rs (:start (:whole %))))))

(defn- tb-alt
  "<a b …>: one value per cycle."
  [& xs] (c/cat* (mapv c/pure xs)))

(defn- tb-pc [& xs] (c/seq* (vec xs)))
(defn- tb-cat [& xs] (c/cat* (vec xs)))
(defn- tb-q [n d] [n d])

(deftest tl3b-compress
  (let [p (c/seq* [:bd :sd])]
    (is (= [[:bd "1/4" "1/2"] [:sd "1/2" "3/4"]] (tb-evs (c/compress [1 4] [3 4] p) 0)))
    (is (= [[:bd "5/4" "3/2"] [:sd "3/2" "7/4"]] (tb-evs (c/compress [1 4] [3 4] p) 1)))
    (is (= [] (tb-evs (c/compress [3 4] [1 4] p) 0)))
    (is (= [] (tb-evs (c/compress [1 2] [3 2] p) 0)))
    (is (= [[:a "0" "1/2"] [:b "1/2" "1"]]
           (tb-evs (c/compress (tb-alt 0 [1 2]) 1 (c/seq* [:a :b])) 0)))
    (is (= [[:a "3/2" "7/4"] [:b "7/4" "2"]]
           (tb-evs (c/compress (tb-alt 0 [1 2]) 1 (c/seq* [:a :b])) 1)))))

(deftest tl3b-zoom
  (is (= [[:b "0" "1/2"] [:c "1/2" "1"]]
         (tb-evs (c/zoom [1 4] [3 4] (c/seq* [:a :b :c :d])) 0)))
  (is (= [[:b "0" "1/2"] [:c "1/2" "1"]]
         (tb-evs (c/zoom [1 2] [3 2] (c/cat* [(c/seq* [:a :b]) (c/seq* [:c :d])])) 0)))
  (let [evs (c/query (c/zoom [1 4] [3 4] (c/pure :x)) (c/cycle-span 0))]
    (is (= [[:x "-1/2" "3/2" "0" "1"]] (tb-evs (c/zoom [1 4] [3 4] (c/pure :x)) 0)))
    (is (= 1 (count evs)))
    (is (not (c/onset? (first evs)))))
  (is (= [[:a "1" "3/2"] [:b "3/2" "2"]]
         (tb-evs (c/zoom 0 (tb-alt 1 [1 2]) (c/seq* [:a :b :c :d])) 1)))
  (is (= [] (tb-evs (c/zoom [3 4] [1 4] (c/seq* [:a :b])) 0))))

(deftest tl3b-focus
  (let [p (c/focus [1 4] [3 4] (c/seq* [:a :b :c :d]))
        evs (tb-evs p 0)]
    (is (= [:c :d :a :b :c :d :a :b] (mapv first evs)))
    (is (= [:c "0" "1/8"] (first evs)))
    (is (= [:a "1/4" "3/8"] (nth evs 2))))
  (is (= [[:b "-1/4" "1/4" "0" "1/4"] [:a "1/4" "3/4"] [:b "3/4" "5/4" "3/4" "1"]]
         (tb-evs (c/focus [1 4] [3 4] (c/cat* [(c/pure :a) (c/pure :b)])) 0)))
  (is (= [[:a "0" "1/2"] [:b "1/2" "1"]]
         (tb-evs (c/focus 0 (tb-alt 1 [1 2]) (c/seq* [:a :b])) 0)))
  (is (= [:a :b :a :b] (tb-vals (c/focus 0 (tb-alt 1 [1 2]) (c/seq* [:a :b])) 1)))
  (is (= [] (tb-evs (c/focus [3 4] [1 4] (c/seq* [:a :b])) 0))))

(deftest tl3b-fast-gap
  (let [p (c/seq* [:bd :sd])]
    (is (= [[:bd "0" "1/4"] [:sd "1/4" "1/2"]] (tb-evs (c/fast-gap 2 p) 0)))
    (is (= [[:bd "1" "5/4"] [:sd "5/4" "3/2"]] (tb-evs (c/fast-gap 2 p) 1)))
    (is (= (tb-evs p 0) (tb-evs (c/fast-gap [1 2] p) 0)))
    (is (= [] (tb-evs (c/fast-gap 0 p) 0)))
    (is (= [[:a "1" "5/4"] [:b "5/4" "3/2"]]
           (tb-evs (c/fast-gap (tb-alt 1 2) (c/seq* [:a :b])) 1)))))

(deftest tl3b-linger
  (let [p (c/seq* [0 1 2 3 4 5 6 7])
        eighths (fn [n vs] (mapv (fn [i v] [v (tb-rs (c/rat i 8)) (tb-rs (c/rat (inc i) 8))])
                                 (range n) vs))]
    (is (= (eighths 8 [0 1 0 1 0 1 0 1]) (tb-evs (c/linger [1 4] p) 0)))
    (is (= [0 1 2 0 1 2 0 1] (tb-vals (c/linger [3 8] p) 0)))
    (is (= [6 7 6 7 6 7 6 7] (tb-vals (c/linger [-1 4] p) 0)))
    (is (= [[:c "1" "3/2"] [:c "3/2" "2"]]
           (tb-evs (c/linger [1 2] (c/cat* [(c/seq* [:a :b]) (c/seq* [:c :d])])) 1)))
    (is (= [0 1 2 3 0 1 2 3] (tb-vals (c/linger (tb-alt [1 4] [1 2]) p) 1)))
    (is (= [] (tb-evs (c/linger 0 p) 0)))
    (is (= (tb-evs p 0) (tb-evs (c/linger 1 p) 0)))))

(deftest tl3b-ribbon
  (let [p (c/cat* (mapv c/pure [:a :b :c :d]))]
    (is (= [[:b "0" "1"] [:c "1" "2"] [:b "2" "3"] [:c "3" "4"]]
           (tb-evs (c/ribbon 1 2 p) 0 3))))
  (is (= [[:b "0" "1/2"] [:a "1/2" "1"]] (tb-evs (c/ribbon [1 2] 1 (c/seq* [:a :b])) 0)))
  (let [p (c/ribbon (tb-alt 0 1) 1 (c/cat* (mapv c/pure [:a :b :c])))]
    (is (= [[:a "0" "1"]] (tb-evs p 0)))
    (is (= [[:b "1" "2"]] (tb-evs p 1)))))

(deftest tl3b-trunc
  (is (= [[:a "0" "1/4"] [:b "1/4" "1/2"] [:c "1/2" "3/4"]]
         (tb-evs (c/trunc [3 4] (c/seq* [:a :b :c :d])) 0)))
  (let [p (c/trunc [1 2] (c/seq* [:a :b :c]))]
    (is (= [[:a "0" "1/3"] [:b "1/3" "2/3" "1/3" "1/2"]] (tb-evs p 0)))
    (is (= ["0" "1/3"] (tb-onsets p 0))))
  (is (= [[:a "1" "5/4"] [:b "5/4" "3/2"]]
         (tb-evs (c/trunc (tb-alt 1 [1 2]) (c/seq* [:a :b :c :d])) 1)))
  (is (= [] (tb-evs (c/trunc 0 (c/seq* [:a])) 0))))

(deftest tl3b-revv
  (is (= [[:c "0" "1/3"] [:b "1/3" "2/3"] [:a "2/3" "1"]] (tb-evs (c/revv (c/seq* [:a :b :c])) 0)))
  (is (= [[:c "0" "1"] [:b "1" "2"] [:a "2" "3"]]
         (tb-evs (c/revv (c/cat* (mapv c/pure [:a :b :c]))) 0 2))))

(deftest tl3b-repeat-cycles
  (let [cyc (fn [p] (mapv #(first (first (tb-evs p % %))) (range 4)))]
    (is (= [:a :a :b :b] (cyc (c/repeat-cycles 2 (c/cat* [(c/pure :a) (c/pure :b)])))))
    (is (= [:a :a :c :b]
           (cyc (c/repeat-cycles (tb-alt 1 2) (c/cat* (mapv c/pure [:a :b :c]))))))))

(deftest tl3b-press
  (is (= [[:a "1/4" "1/2"] [:b "3/4" "1"]] (tb-evs (c/press (c/seq* [:a :b])) 0)))
  (is (= [[:a "1/8" "1/2"] [:b "5/8" "1"]] (tb-evs (c/press-by [1 4] (c/seq* [:a :b])) 0)))
  (is (= [] (tb-evs (c/press-by 1 (c/seq* [:a :b])) 0)))
  (let [p (c/press-by (tb-alt 0 [1 2]) (c/seq* [:a :b]))]
    (is (= [[:a "0" "1/2"] [:b "1/2" "1"]] (tb-evs p 0)))
    (is (= [[:a "5/4" "3/2"] [:b "7/4" "2"]] (tb-evs p 1)))))

(deftest tl3b-bite
  (let [p (c/seq* [:a :b :c :d :e :f :g :h])
        q (fn [& xs] (mapv (fn [i v] [v (tb-rs (c/rat i 4)) (tb-rs (c/rat (inc i) 4))]) (range) xs))]
    (is (= (q :a :b :e :f) (tb-evs (c/bite 4 (c/seq* [0 2]) p) 0)))
    (is (= (q :a :b :c :d) (tb-evs (c/bite 4 (c/seq* [0 5]) p) 0)))
    (is (= (mapv (fn [i v] [v (tb-rs (c/rat (+ 8 i) 8)) (tb-rs (c/rat (+ 9 i) 8))])
                 (range) [:a :b :c :d :e :f :g :h])
           (tb-evs (c/bite (tb-alt 4 2) (c/seq* [0 1]) p) 1)))
    (is (= [] (tb-evs (c/bite 0 (c/seq* [0]) p) 0)))))

(deftest tl3b-chunk-into
  (let [p (c/seq* [0 1 2 3])]
    (is (= [[1 "0" "1/4"] [0 "1/4" "1/2"] [2 "1/2" "3/4"] [3 "3/4" "1"]]
           (tb-evs (c/chunk-into 2 c/rev p) 0)))
    (is (= [0 1 3 2] (tb-vals (c/chunk-into 2 c/rev p) 1)))
    (is (= [0 1 3 2] (tb-vals (c/chunk-into (tb-alt 4 2) c/rev p) 1))))
  (let [evs (tb-evs (c/chunk-into 4 #(c/fast 2 %) (c/seq* [0 1 2 3 4 5 6 7])) 0)]
    (is (= [[0 "0" "1/16"] [1 "1/16" "1/8"] [0 "1/8" "3/16"] [1 "3/16" "1/4"]] (subvec evs 0 4)))
    (is (= [[2 "1/4" "3/8"] [3 "3/8" "1/2"] [4 "1/2" "5/8"] [5 "5/8" "3/4"]
            [6 "3/4" "7/8"] [7 "7/8" "1"]]
           (subvec evs 4)))))

(deftest tl3b-chunk-back-into
  (let [p (c/seq* [0 1 2 3])]
    (is (= [0 1 3 2] (tb-vals (c/chunk-back-into 2 c/rev p) 0)))
    (is (= [1 0 2 3] (tb-vals (c/chunk-back-into 2 c/rev p) 1)))
    (is (= [1 0 2 3] (tb-vals (c/chunk-back-into (tb-alt 4 2) c/rev p) 1)))))

(deftest tl3b-euclidish
  (let [x (c/pure :x)]
    (is (= [[:x "0" "1/8"] [:x "3/8" "1/2"] [:x "3/4" "7/8"]] (tb-evs (c/euclidish 3 8 0 x) 0)))
    (is (= (tb-evs (c/euclidean 3 8 x) 0) (tb-evs (c/euclidish 3 8 0 x) 0)))
    (is (= [[:x "0" "1/8"] [:x "1/3" "11/24"] [:x "2/3" "19/24"]] (tb-evs (c/euclidish 3 8 1 x) 0)))
    (is (= [[:x "0" "1/8"] [:x "17/48" "23/48"] [:x "17/24" "5/6"]]
           (tb-evs (c/euclidish 3 8 [1 2] x) 0)))
    (is (= ["1" "4/3" "5/3"] (tb-onsets (c/euclidish 3 8 (tb-alt 0 1) x) 1)))
    (is (= ["1" "5/4" "11/8" "13/8" "7/4"] (tb-onsets (c/euclidish (tb-alt 3 5) 8 0 x) 1)))
    (is (= [] (tb-evs (c/euclidish 0 8 0 x) 0)))))

(deftest tl3b-ply-for-each
  (let [f (fn [p i] (c/fmap #(+ % (* i 12)) p))]
    (is (= [[0 "0" "1/6"] [12 "1/6" "1/3"] [24 "1/3" "1/2"]
            [7 "1/2" "2/3"] [19 "2/3" "5/6"] [31 "5/6" "1"]]
           (tb-evs (c/ply-for-each 3 f (c/seq* [0 7])) 0)))
    (let [g (fn [p i] (c/fmap #(+ % i) p))
          p (c/ply-for-each (tb-alt 1 2) g (c/seq* [0 7]))]
      (is (= [[0 "0" "1/2"] [7 "1/2" "1"]] (tb-evs p 0)))
      (is (= [[0 "1" "5/4"] [1 "5/4" "3/2"] [7 "3/2" "7/4"] [8 "7/4" "2"]] (tb-evs p 1))))))

(deftest tl3b-filter-events
  (is (= [[3 "1/2" "3/4"] [4 "3/4" "1"]]
         (tb-evs (c/filter-events #(> % 2) (c/seq* [1 2 3 4])) 0)))
  (is (= [[:bd "0" "1/3"] [:bd "2/3" "1"]]
         (tb-evs (c/filter-events #(= % :bd) (c/seq* [:bd :sd :bd])) 0))))

(deftest tl3b-filter-when
  (let [p (c/filter-when (fn [t] (< (if (vector? t) (/ (first t) (second t)) t) 0.5))
                         (c/seq* [:a :b :c :d]))]
    (is (= [[:a "0" "1/4"] [:b "1/4" "1/2"]] (tb-evs p 0)))
    (is (= [] (tb-evs p 1))))
  (is (= [[:a "0" "1/2"]] (tb-evs (c/filter-when #(= % 0) (c/seq* [:a :b])) 0)))
  (is (= [0 [1 2]] (let [seen (atom [])]
                     (tb-evs (c/filter-when (fn [t] (swap! seen conj t) true) (c/seq* [:a :b])) 0)
                     @seen))))

(deftest tl3b-bypass
  (let [p (c/seq* [:a :b])]
    (is (= [] (tb-evs (c/bypass 1 p) 0)))
    (is (= (tb-evs p 0) (tb-evs (c/bypass 0 p) 0)))
    (is (= [:a :b] (tb-vals (c/bypass (tb-alt 0 1) p) 0)))
    (is (= [] (tb-evs (c/bypass (tb-alt 0 1) p) 1)))
    (is (= [[:a "0" "1/4"] [:b "1/4" "1/2"]]
           (tb-evs (c/bypass (c/seq* [0 1]) (c/seq* [:a :b :c :d])) 0)))))

(deftest tl3b-beat
  (let [bd (c/pure :bd)
        stack3 (c/stack* [(c/pure 0) (c/pure 7) (c/pure 10)])]
    (is (= [[:bd "0" "1/16"] [:bd "7/16" "1/2"] [:bd "5/8" "11/16"]]
           (tb-evs (c/beat stack3 16 bd) 0)))
    (is (= [[:b "1/2" "3/4"]] (tb-evs (c/beat 2 4 (c/seq* [:a :b])) 0)))
    (is (= [[:x "1/16" "1/8"]] (tb-evs (c/beat 17 16 (c/pure :x)) 0)))
    (is (= [[:bd "5/4" "3/2"]] (tb-evs (c/beat (tb-alt 0 1) 4 bd) 1)))
    (is (= [] (tb-evs (c/beat 0 0 bd) 0)))))

(deftest tl3b-sew
  (let [tf (c/seq* [:t :f])]
    (is (= [[:a "0" "1/4"] [:a "1/4" "1/2"] [:b "1/2" "3/4"] [:b "3/4" "1"]]
           (tb-evs (c/sew tf (c/seq* [:a :a :a :a]) (c/seq* [:b :b :b :b])) 0)))
    (let [p (c/sew tf (c/seq* [:a :b :c]) (c/pure :x))]
      (is (= [[:a "0" "1/3"] [:b "1/3" "2/3" "1/3" "1/2"] [:x "0" "1" "1/2" "1"]]
             (vec (sort-by (fn [e] (.indexOf [:a :b :x] (first e))) (tb-evs p 0)))))
      (is (= ["0" "1/3"] (tb-onsets p 0))))
    (let [p (c/sew (tb-alt :t :f) (c/pure :a) (c/pure :b))]
      (is (= [[:a "0" "1"]] (tb-evs p 0)))
      (is (= [[:b "1" "2"]] (tb-evs p 1))))))

(deftest tl3b-stitch
  (is (= [[:bd "0" "1/4"] [:sn "1/4" "1/2"] [:bd "1/2" "3/4"] [:bd "3/4" "1"]]
         (tb-evs (c/stitch (c/seq* [:t :f :t :t]) (c/pure :bd) (c/pure :sn)) 0)))
  (is (= [[:a "0" "1/3"] [:b "2/3" "1"]]
         (tb-evs (c/stitch (c/seq* [:t :_ :f]) (c/pure :a) (c/pure :b)) 0))))

(deftest tl3b-binary
  (is (= [[true "0" "1/3"] [false "1/3" "2/3"] [true "2/3" "1"]] (tb-evs (c/binary 5) 0)))
  (is (= [[:x "0" "1/3"] [:x "2/3" "1"]] (tb-evs (c/struct (c/binary 5) (c/pure :x)) 0)))
  (is (= [[true "0" "1"]] (tb-evs (c/binary 1) 0)))
  (is (= [[false "0" "1"]] (tb-evs (c/binary 0) 0)))
  (is (= [[true "1" "3/2"] [true "3/2" "2"]] (tb-evs (c/binary (tb-alt 5 3)) 1))))

(deftest tl3b-binary-n
  (is (= [false true false true] (tb-vals (c/binary-n 5 4) 0)))
  (let [evs (tb-evs (c/binary-n 5) 0)]
    (is (= 16 (count evs)))
    (is (= [[true "13/16" "7/8"] [false "7/8" "15/16"] [true "15/16" "1"]] (subvec evs 13)))
    (is (every? #(false? (first %)) (subvec evs 0 13))))
  (is (= [true true true true] (tb-vals (c/binary-n -1 4) 0)))
  (is (= [] (tb-evs (c/binary-n 5 0) 0)))
  (is (= [[false "0" "1/2"] [true "1/2" "1"]] (tb-evs (c/binary-n 1 (tb-alt 2 4)) 0)))
  (is (= [false false false true] (tb-vals (c/binary-n 1 (tb-alt 2 4)) 1)))
  (is (= [false true false true] (tb-vals (c/binary-n 5 4) 1))))

;;; ── Phase TL4: randomness & conditionals ────────────────────────────

(defn- vc
  "values c: onset values of cycle n in whole-start order."
  [pat n]
  (mapv :value (sort-by #(c/rat->float (:start (:whole %))) (c/query-onsets pat (c/cycle-span n)))))

(defn- vcs [pat from to] (mapv #(vc pat %) (range from to)))

(defn- evs
  "[value whole-start whole-end] triples of cycle n sorted by whole start."
  [pat n]
  (mapv (fn [e] [(:value e) (:start (:whole e)) (:end (:whole e))])
        (sort-by (juxt #(c/rat->float (:start (:whole %))) #(str (:value %)))
                 (c/query-onsets pat (c/cycle-span n)))))

(def ^:private s8 (c/seq* [0 1 2 3 4 5 6 7]))
(def ^:private s4 (c/seq* [:a :b :c :d]))
(defn- plus100 [p] (c/fmap #(+ % 100) p))
(defn- close= [a b] (every? true? (map #(< (Math/abs (- %1 %2)) 1e-12) a b)))
(defn- sample [pat t]
  (:value (first (c/query pat (c/span t (c/rat+ t [1 1000000]))))))

(deftest tl4-time->rands
  (is (close= [0.2591378428041935 0.712890088558197 0.9065967965871096 0.6629501506686211]
              (c/time->rands [1 2] 4)))
  (is (close= [0.4010523743927479 0.35760145634412766 0.1534953359514475 0.363248186185956]
              (c/time->rands [3 2] 4)))
  (is (= [0 0 0] (c/time->rands [0 1] 3)))
  (doseq [t [[1 1] [1 4] [7 3]]]
    (is (= (c/time->rand t) (first (c/time->rands t 5))))))

(deftest tl4-with-seed
  (let [r4 (c/segment 4 c/rand)]
    (is (= (vc r4 1) (vc (c/with-seed 1 r4) 0)))
    (is (close= [0.5177721455693245 0.2788401562720537 0.4010523743927479 0.7870833929628134]
                (vc (c/with-seed 1 r4) 0)))
    (is (= (vc r4 2) (vc (c/with-seed 1 (c/with-seed 2 r4)) 0)))
    (is (close= [0.03601731359958649 0.35768954269587994 0.5576803125441074 0.6513602118939161]
                (vc (c/with-seed 1 (c/with-seed 2 r4)) 0)))
    (is (= [0 1 4 6 7] (vc (c/with-seed 3 (c/degrade-by 0.5 s8)) 0)))
    (is (= (vc (c/degrade-by 0.5 s8) 3) (vc (c/with-seed 3 (c/degrade-by 0.5 s8)) 0)))
    (is (= [:a :a :b :a :b :a :a :a]
           (mapv first (vcs (c/with-seed 7 (c/randcat [(c/pure :a) (c/pure :b) (c/pure :c)])) 0 8))))
    (is (= [0 0.03601731359958649]
           [(first (vc (c/with-seed (c/cat* [(c/pure 0) (c/pure 1)]) (c/segment 1 c/rand)) 0))
            (first (vc (c/with-seed (c/cat* [(c/pure 0) (c/pure 1)]) (c/segment 1 c/rand)) 1))]))))

(deftest tl4-seed-differs
  (let [pats [(c/pure :a) (c/pure :b) (c/pure :c)]
        base (c/randcat pats)
        seeded (c/with-seed 1 base)]
    (is (>= (count (filter #(not= (vc base %) (vc seeded %)) (range 100))) 50))
    (is (= (vcs (c/shuffle 4 s4) 0 20) (vcs (c/shuffle 4 s4) 0 20)))))

(deftest tl4-degrade-by
  (is (= [2 3] (vc (c/degrade-by 0.5 s8) 0)))
  (is (= [0 1 3 5 6] (vc (c/degrade-by 0.5 s8) 1)))
  (is (= 8 (count (vc (c/degrade-by 0 s8) 0))))
  (is (empty? (vc (c/degrade-by 1 s8) 0)))
  (is (= (vc (c/degrade-by 0.5 s8) 0) (vc (c/degrade s8) 0)))
  (is (= [1 2 3 4 6 7] (vc (c/degrade-by (c/cat* [(c/pure 0.2) (c/pure 0.6)]) s8) 0)))
  (is (= [1 3 6] (vc (c/degrade-by (c/cat* [(c/pure 0.2) (c/pure 0.6)]) s8) 1)))
  (is (= [13 7 12 8]
         (mapv #(count (vc (c/degrade-by (c/cat* [(c/pure 0.2) (c/pure 0.6)]) (c/fast 16 (c/seq* [:hh]))) %)) (range 4))))
  (is (= [{:from 0 :to 1} {:from 2 :to 3}]
         (let [pat (c/seq* [:a :b] [{:from 0 :to 1} {:from 2 :to 3}])]
           (mapv :source (c/query (c/degrade-by 0 pat) (cyc 0)))))))

(deftest tl4-undegrade-by
  (is (= [0 1 4 5 6 7] (vc (c/undegrade-by 0.5 s8) 0)))
  (is (= [2 4 7] (vc (c/undegrade-by 0.5 s8) 1)))
  (doseq [x [0.1 0.5 0.9] n (range 10)]
    (is (= (evs s8 n) (evs (c/stack* [(c/degrade-by x s8) (c/undegrade-by x s8)]) n))))
  (is (= (vc (c/undegrade-by 0.5 s8) 0) (vc (c/undegrade s8) 0))))

(deftest tl4-degrade-by-with
  (is (= [4 5 6 7] (vc (c/degrade-by-with c/saw [1 2] s8) 0)))
  (is (empty? (vc (c/degrade-by-with (c/segment 1 c/rand) 0.5 s8) 0)))
  (is (= 8 (count (vc (c/degrade-by-with (c/segment 1 c/rand) 0.5 s8) 1)))))

(deftest tl4-sometimes-by
  (is (= [[100 [0 1] [1 4]] [1 [1 4] [1 2]] [102 [1 2] [3 4]] [103 [3 4] [1 1]]]
         (evs (c/sometimes-by 0.5 plus100 (c/seq* [0 1 2 3])) 0)))
  (is (= [[0 [1 1] [5 4]] [101 [5 4] [3 2]] [102 [3 2] [7 4]] [3 [7 4] [2 1]]]
         (evs (c/sometimes-by 0.5 plus100 (c/seq* [0 1 2 3])) 1)))
  (is (= (set [[:a [0 1] [1 8]] [:c [1 4] [3 8]] [:d [3 8] [1 2]] [:b [1 4] [1 2]] [:b [5 8] [3 4]] [:c [3 4] [7 8]]])
         (set (evs (c/sometimes-by 0.5 #(c/fast 2 %) s4) 0))))
  (is (= [:a :b] (vc (c/sometimes-by (c/cat* [(c/pure 0) (c/pure 1)]) c/rev (c/seq* [:a :b])) 0)))
  (is (= [:b :a] (vc (c/sometimes-by (c/cat* [(c/pure 0) (c/pure 1)]) c/rev (c/seq* [:a :b])) 1))))

(deftest tl4-named-probabilities
  (is (= (evs (c/sometimes-by 0.5 plus100 s8) 0) (evs (c/sometimes plus100 s8) 0)))
  (is (= (range 100 108) (vc (c/often plus100 s8) 0)))
  (is (= [100 1 2 3 4 105 106 7] (vc (c/rarely plus100 s8) 0)))
  (is (= [100 1 2 3 4 5 6 7] (vc (c/almost-never plus100 s8) 0)))
  (is (= (range 8) (vc (c/almost-never plus100 s8) 1)))
  (is (= (range 100 108) (vc (c/almost-always plus100 s8) 0)))
  (is (= (range 100 108) (vc (c/almost-always plus100 s8) 1)))
  (is (= (vc (plus100 s8) 0) (vc (c/always plus100 s8) 0)))
  (is (= (vc s8 0) (vc (c/never plus100 s8) 0))))

(deftest tl4-some-cycles-by
  (is (= ["dcba" "abcd" "dcba" "abcd" "dcba" "dcba" "abcd" "dcba"]
         (mapv #(apply str (map name %)) (vcs (c/some-cycles-by 0.5 c/rev s4) 0 8))))
  (is (= [[100 101 102 103] [0 1 2 3] [100 101 102 103] [0 1 2 3]]
         (vcs (c/some-cycles plus100 (c/seq* [0 1 2 3])) 0 4)))
  (let [pat (c/some-cycles-by 0.5 c/rev s4)]
    (is (= (into [] (mapcat #(vals-in pat % (inc %))) (range 6)) (vals-in pat 0 6))))
  (is (= [:a :b :c :d] (vc (c/some-cycles-by (c/cat* [(c/pure 0) (c/pure 1)]) c/rev s4) 0)))
  (is (= [:d :c :b :a] (vc (c/some-cycles-by (c/cat* [(c/pure 0) (c/pure 1)]) c/rev s4) 1))))

(deftest tl4-choose
  (let [ch (c/choose [:a :b :c])]
    (is (= [:a :b :a :a] (vc (c/segment 4 ch) 0)))
    (is (= [:b :a :b :c] (vc (c/segment 4 ch) 1)))
    (let [es (c/query ch (c/span [1 4] [1 2]))]
      (is (= 1 (count es)))
      (is (nil? (:whole (first es))))
      (is (= :b (:value (first es)))))
    (is (empty? (c/query-onsets (c/choose [:a :b]) (cyc 0))))
    (is (empty? (c/query (c/choose []) (cyc 0))))
    (let [pat (c/choose [:a :b :c] [{:from 0 :to 1} {:from 2 :to 3} {:from 4 :to 5}])
          es  (mapv #(first (c/query pat (c/span [%1 4] [(inc %1) 4]))) (range 4))]
      ;; sampled directly (segment keeps the source of its own structure)
      (is (= [:a :b :a :a] (mapv :value (map #(first (c/query pat (c/span [%1 4] [(inc %1) 4]))) (range 4)))))
      (is (= {:from 2 :to 3} (:source (first (c/query pat (c/span [1 4] [1 2]))))))
      (is (= 4 (count es))))))

(deftest tl4-wchoose
  (let [w (c/wchoose [[1 :a] [3 :b]])]
    (is (= [:a :b :b :a] (vc (c/segment 4 w) 0)))
    (is (= [:b :b :b :b] (vc (c/segment 4 w) 1))))
  (is (empty? (c/query (c/wchoose [[0 :a] [0 :b]]) (cyc 0)))))

(deftest tl4-choose-with-in
  (is (= [:b :c :b :a] (vc (c/segment 4 (c/choose-with c/sine [:a :b :c])) 0)))
  (let [ci (c/choose-in [(c/seq* [:a :b]) (c/seq* [:c :d])])]
    (is (= [[:c [1 1] [3 2]] [:d [3 2] [2 1]]]
           (mapv (fn [e] [(:value e) (:start (:whole e)) (:end (:whole e))]) (c/query ci (c/span [1 1] [2 1])))))
    (is (= [[:b [3 2] [2 1]]]
           (mapv (fn [e] [(:value e) (:start (:whole e)) (:end (:whole e))]) (c/query ci (c/span [3 2] [2 1]))))))
  (is (= [:c] (vc (c/choose-in-with (c/segment 1 c/rand) [(c/seq* [:a :b]) (c/pure :c)]) 1))))

(deftest tl4-randcat
  (let [pat (c/randcat [(c/pure :a) (c/seq* [:b :b]) (c/pure :c)])]
    (is (= [[:a] [:b :b] [:a] [:c] [:a] [:a] [:b :b] [:a]] (vcs pat 0 8)))
    (is (= [[:b [1 1] [3 2]] [:b [3 2] [2 1]]] (evs pat 1))))
  (let [pat (c/choose-cycles [:a :b :c])]
    (doseq [n (range 10)]
      (let [es (c/query pat (cyc n))]
        (is (= 1 (count es)))
        (is (= (cyc n) (:whole (first es)))))))
  (let [es (c/query (c/choose-cycles [:a :b] [{:from 0 :to 2} {:from 3 :to 5}]) (cyc 1))]
    (is (= [:b] (mapv :value es)))
    (is (= {:from 3 :to 5} (:source (first es))))))

(deftest tl4-wrandcat
  (is (= [[:a] [:a] [:a] [:b] [:a] [:a] [:a] [:a]]
         (vcs (c/wrandcat [[3 (c/pure :a)] [1 (c/pure :b)]]) 0 8)))
  (is (= [[:a] [:c] [:a] [:c] [:a] [:a] [:c] [:a]]
         (vcs (c/wchoose-cycles [[1 :a] [1 :b] [2 :c]]) 0 8))))

(deftest tl4-brand
  (is (= [true false true true] (vc (c/segment 4 c/brand) 0)))
  (is (= [false true true false] (vc (c/segment 4 c/brand) 1)))
  (is (= [true false false true] (vc (c/segment 4 (c/brand-by 0.25)) 0)))
  (is (= [false false false false] (vc (c/segment 4 (c/brand-by 0.25)) 1)))
  (is (= [false] (vc (c/segment 1 (c/brand-by (c/cat* [(c/pure 0) (c/pure 1)]))) 0)))
  (is (= [true] (vc (c/segment 1 (c/brand-by (c/cat* [(c/pure 0) (c/pure 1)]))) 1))))

(deftest tl4-berlin
  (let [at (fn [t] (sample c/berlin t))]
    (is (close= [0 0.12944303639233112 0.25888607278466225 0.26789040118455887 0.2186607443727553]
                (mapv at [[0 1] [1 2] [1 1] [3 2] [5 2]])))
    (is (close= [(at [3 2])] [(sample (c/with-seed 1 c/berlin) [1 2])]))))

(deftest tl4-rand-l
  (is (close= [0.2591378428041935 0.712890088558197 0.9065967965871096]
              (sample (c/rand-l 3) [1 2])))
  (is (= [0 0 0] (sample (c/rand-l 3) [0 1])))
  (is (empty? (c/query (c/rand-l 0) (cyc 0)))))

(deftest tl4-randrun
  (is (= [[0 3 1 2] [2 1 3 0] [1 3 2 0] [1 3 0 2]] (vcs (c/randrun 4) 0 4)))
  (is (= [[0 [0 1] [1 4]] [3 [1 4] [1 2]] [1 [1 2] [3 4]] [2 [3 4] [1 1]]] (evs (c/randrun 4) 0)))
  (is (= [[0 [0 1] [1 1]]] (evs (c/randrun 1) 0)))
  (is (empty? (c/query (c/randrun 0) (cyc 0))))
  (is (every? #(= [0 1 2 3 4] (sort %)) (vcs (c/randrun 5) 0 1000))))

(deftest tl4-shuffle-scramble
  (is (= [[:a :d :b :c] [:c :b :d :a] [:b :d :c :a] [:b :d :a :c]] (vcs (c/shuffle 4 s4) 0 4)))
  (is (= [[:a [0 1] [1 4]] [:d [1 4] [1 2]] [:b [1 2] [3 4]] [:c [3 4] [1 1]]] (evs (c/shuffle 4 s4) 0)))
  (is (= [[:a :b :c :d] [:c :d :a :b] [:c :d :a :b] [:c :d :a :b]] (vcs (c/shuffle 2 s4) 0 4)))
  (is (= [[:b :d :c :a] [:c :a :d :b] [:b :a :d :c] [:c :a :d :b]] (vcs (c/with-seed 7 (c/shuffle 4 s4)) 0 4)))
  (is (empty? (c/query (c/shuffle 0 s4) (cyc 0))))
  (is (= [:a :b :c :d] (vc (c/shuffle (c/cat* [(c/pure 1) (c/pure 4)]) s4) 0)))
  (is (= (vc (c/shuffle 4 s4) 1) (vc (c/shuffle (c/cat* [(c/pure 1) (c/pure 4)]) s4) 1)))
  (is (= [[:a :c :b :a] [:c :b :b :d] [:a :b :c :c] [:d :a :c :d]] (vcs (c/scramble 4 s4) 0 4))))

(deftest tl4-when-pat
  (is (= [:a :b] (vc (c/when-pat (c/cat* [(c/pure 0) (c/pure 1)]) c/rev (c/seq* [:a :b])) 0)))
  (is (= [:b :a] (vc (c/when-pat (c/cat* [(c/pure 0) (c/pure 1)]) c/rev (c/seq* [:a :b])) 1)))
  (is (= [[:a [0 1] [1 8]] [:b [1 8] [1 4]] [:c [1 4] [3 8]] [:d [3 8] [1 2]] [:c [1 2] [3 4]] [:d [3 4] [1 1]]]
         (evs (c/when-pat (c/seq* [1 0]) #(c/fast 2 %) s4) 0)))
  (let [p (c/when-pat (fn [cy] (> cy 1)) c/rev (c/seq* [:a :b]))]
    (is (= [[:a :b] [:a :b] [:b :a]] (vcs p 0 3)))))

(deftest tl4-whenmod
  (let [p (c/whenmod 8 6 c/rev (c/seq* [0 2 4 7]))]
    (doseq [n (range 16)]
      (is (= (if (#{6 7 14 15} n) [7 4 2 0] [0 2 4 7]) (vc p n)))))
  (is (= [[:a :b] [:a :b] [:b :a] [:b :a]] (vcs (c/whenmod 4 2 c/rev (c/seq* [:a :b])) 0 4)))
  (is (= (vc s4 0) (vc (c/whenmod 0 0 c/rev s4) 0)))
  (is (= [:b :a] (vc (c/whenmod (c/cat* [(c/pure 4) (c/pure 2)]) 1 c/rev (c/seq* [:a :b])) 1))))

(deftest tl4-apply-n
  (is (= [[:a [0 1] [1 8]] [:b [1 8] [1 4]] [:a [1 4] [3 8]] [:b [3 8] [1 2]]
          [:a [1 2] [5 8]] [:b [5 8] [3 4]] [:a [3 4] [7 8]] [:b [7 8] [1 1]]]
         (evs (c/apply-n 2 #(c/fast 2 %) (c/seq* [:a :b])) 0)))
  (is (= [:a :b] (vc (c/apply-n 0 c/rev (c/seq* [:a :b])) 0)))
  (let [p (c/apply-n (c/cat* [(c/pure 1) (c/pure 2)]) #(c/fast 2 %) (c/seq* [:a :b]))]
    (is (= 4 (count (vc p 0))))
    (is (= 8 (count (vc p 1))))))

(defn- frac [pred xs] (/ (count (filter pred xs)) (count xs)))
(defn- near [target x] (< (Math/abs (- x target)) 0.05))

(deftest tl4-statistics
  (let [n 1000
        s4' (c/seq* [:a :b :c :d])
        kept (fn [pat] (/ (reduce + (map #(count (vc pat %)) (range n))) (* 4.0 n)))]
    (is (near 0.7 (kept (c/degrade-by 0.3 s4'))))
    (is (near 0.5 (kept (c/degrade s4'))))
    (let [f (c/sometimes-by 0.25 (fn [p] (c/fmap (constantly :x) p)) s4')]
      (is (near 0.25 (/ (reduce + (map #(count (filter #{:x} (vc f %))) (range n))) (* 4.0 n)))))
    (let [f (c/some-cycles-by 0.3 (fn [p] (c/fmap (constantly :x) p)) s4')]
      (is (near 0.3 (frac #(= [:x :x :x :x] %) (map #(vc f %) (range n))))))
    (let [counts (frequencies (map #(first (vc (c/randcat [(c/pure :a) (c/pure :b) (c/pure :c)]) %)) (range 900)))]
      (is (every? #(< (Math/abs (- % 300)) 45) (vals counts))))
    (is (near 0.75 (frac #{:a} (map #(first (vc (c/wrandcat [[3 (c/pure :a)] [1 (c/pure :b)]]) %)) (range n)))))
    (let [vs (mapcat #(vc (c/segment 4 (c/choose [:a :b :c])) %) (range n))]
      (doseq [k [:a :b :c]] (is (near (/ 1 3.0) (frac #{k} vs)))))
    (is (near 0.5 (frac true? (mapcat #(vc (c/segment 4 c/brand) %) (range n)))))
    (is (near 0.25 (frac true? (mapcat #(vc (c/segment 4 (c/brand-by 0.25)) %) (range n)))))
    (let [cs (map #(vc (c/shuffle 4 s4') %) (range n))]
      (is (every? #(= [:a :b :c :d] (sort %)) cs))
      (doseq [k [:a :b :c :d]] (is (near 0.25 (frac #{k} (map first cs))))))
    (let [vs (mapcat #(vc (c/scramble 4 s4') %) (range n))]
      (doseq [k [:a :b :c :d]] (is (near 0.25 (frac #{k} vs)))))))
