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
