(ns repulse.core-test
  (:require [cljs.test :refer [deftest is testing run-tests]]
            [repulse.core :as c]))

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

(deftest off-layers-original-and-shifted
  (testing "off produces events from both original and transformed copy"
    (let [pat (c/off 0.5 c/rev (c/seq* [:a :b]))
          evs (c/query pat (c/span [0 1] [2 1]))]
      (is (> (count evs) 2)))))
