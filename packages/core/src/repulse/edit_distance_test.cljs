(ns repulse.edit-distance-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.edit-distance :as ed]))

(deftest levenshtein-test
  (is (= 3 (ed/levenshtein "" "abc")))
  (is (= 3 (ed/levenshtein "abc" "")))
  (is (= 0 (ed/levenshtein "" "")))
  (is (= 0 (ed/levenshtein "stack" "stack")))
  (is (= 3 (ed/levenshtein "kitten" "sitting")))
  (testing "symmetric"
    (is (= (ed/levenshtein "kitten" "sitting") (ed/levenshtein "sitting" "kitten")))
    (is (= (ed/levenshtein "palindrom" "palindrome") (ed/levenshtein "palindrome" "palindrom")))))

(deftest max-typo-distance-test
  (is (= 1 (ed/max-typo-distance 4)))
  (is (= 2 (ed/max-typo-distance 5)))
  (is (= 2 (ed/max-typo-distance 8)))
  (is (= 3 (ed/max-typo-distance 9))))

(deftest typo-hint-test
  (let [known ["seq" "stack" "rev"]]
    (is (= "seq" (ed/typo-hint "sek" known)))
    (is (nil? (ed/typo-hint "sqe" known)) "2 edits is too far for a 3-letter name")
    (is (= "stack" (ed/typo-hint "stakc" known))))
  (testing "on equal distances the last candidate wins"
    (is (= "seq" (ed/typo-hint "sek" ["sew" "seq"]))))
  (testing "nothing known, nothing suggested"
    (is (nil? (ed/typo-hint "sek" [])))))
