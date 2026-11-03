(ns repulse.ui.slider-model-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.ui.slider-model :as sm]))

(def ^:private amp {:min 0 :max 1 :step 0.01})

(deftest decimals-test
  (is (= 2 (sm/decimals 0.01)))
  (is (= 3 (sm/decimals 0.001)))
  (is (= 0 (sm/decimals 1)))
  (is (= 1 (sm/decimals 0.5))))

(deftest snap-test
  (is (= 0.8 (sm/snap amp 0.80000000001)))
  (is (= 1 (sm/snap amp 1.7)))
  (is (= 0 (sm/snap amp -3)))
  (is (= 2 (sm/snap {:values [1 2 4]} 2.4)))
  (is (= 4 (sm/snap {:values [1 2 4]} 3.5))))

(deftest drag-value-test
  (is (= 0.8 (sm/drag-value amp 0.6 30 150 false)))
  (is (= 0.62 (sm/drag-value amp 0.6 30 150 true)))
  (is (= 1 (sm/drag-value amp 0.6 300 150 false)))
  (is (= 0 (sm/drag-value amp 0.6 -300 150 false)))
  (is (= 1798 (sm/drag-value {:min 20 :max 8000 :step 1} 1000 15 150 false)))
  (is (= 2 (sm/drag-value {:values [1 2 4]} 1 75 150 false))))

(deftest key-step-test
  (is (= 0.61 (sm/key-step amp 0.6 1 false)))
  (is (= 0.7 (sm/key-step amp 0.6 1 true)))
  (is (= 0.5 (sm/key-step amp 0.6 -1 true)))
  (is (= 1 (sm/key-step amp 0.95 1 true)))
  (is (= 4 (sm/key-step {:values [1 2 4]} 4 1 false)))
  (is (= 1 (sm/key-step {:values [1 2 4]} 4 -1 true))))

(deftest text-diff-test
  (is (= {:from 7 :to 8 :insert "75"} (sm/text-diff "(amp 0.6)" "(amp 0.75)")))
  (is (= {:from 7 :to 9 :insert "6"} (sm/text-diff "(amp 0.75)" "(amp 0.6)")))
  (is (= {:from 3 :to 3 :insert "a"} (sm/text-diff "aaa" "aaaa")))
  (is (nil? (sm/text-diff "x" "x")))
  (testing "applying the diff reproduces the target"
    (doseq [[a b] [["(amp 0.6)" "(amp 0.75)"] ["abc" ""] ["" "abc"] ["abcabc" "abc"]]]
      (let [{:keys [from to insert]} (sm/text-diff a b)]
        (is (= b (str (subs a 0 from) insert (subs a to))) (pr-str [a b]))))))

(deftest fmt-pv-test
  (is (= "0.70" (sm/fmt-pv 0.7)))
  (is (= "2400" (sm/fmt-pv 2400)))
  (is (= "saw" (sm/fmt-pv "saw"))))

(deftest value-t-roundtrip
  (is (= 0.6 (sm/t->value amp (sm/value->t amp 0.6))))
  (is (= 0.5 (sm/value->t {:values [1 2 4]} 2)))
  (is (= 4 (sm/t->value {:values [1 2 4]} 1))))
