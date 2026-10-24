(ns repulse.ai.edit-test
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [repulse.ai.edit :as edit]))

(def doc "(bpm 120)\n\n(track :kick\n  (seq :bd :sd))\n")

(defn- apply-change [doc {:keys [from to insert]}]
  (str (subs doc 0 from) insert (subs doc to)))

(deftest count-occurrences-test
  (is (= 0 (edit/count-occurrences doc "(bpm 140)")))
  (is (= 1 (edit/count-occurrences doc "(bpm 120)")))
  (is (= 3 (edit/count-occurrences doc ":")) "counts every occurrence")
  (is (= 2 (edit/count-occurrences "aaaa" "aa")) "non-overlapping"))

(deftest line-at-test
  (is (= 1 (edit/line-at doc 0)))
  (is (= 3 (edit/line-at doc (str/index-of doc "(track"))))
  (is (= 4 (edit/line-at doc (str/index-of doc "(seq")))))

(deftest locate-unique-match-test
  (testing "replaces exactly the matched text"
    (let [c (edit/locate doc "(bpm 120)" "(bpm 140)")]
      (is (= {:from 0 :to 9 :insert "(bpm 140)"} c))
      (is (= "(bpm 140)\n\n(track :kick\n  (seq :bd :sd))\n" (apply-change doc c)))))
  (testing "multi-line matches keep whitespace exact"
    (let [c (edit/locate doc "(track :kick\n  (seq :bd :sd))" "(track :kick\n  (seq :bd :bd))")]
      (is (str/includes? (apply-change doc c) "(seq :bd :bd)"))
      (is (not (str/includes? (apply-change doc c) ":sd"))))))

(deftest locate-errors-test
  (testing "missing match tells the model to re-read the buffer"
    (is (str/includes? (:error (edit/locate doc "(bpm 999)" "x")) "not found")))
  (testing "whitespace differences count as missing"
    (is (:error (edit/locate doc "(track :kick (seq :bd :sd))" "x"))))
  (testing "ambiguous match reports the count"
    (is (str/includes? (:error (edit/locate doc ":" "x")) "found 3 occurrences"))))

(deftest append-test
  (testing "blank match appends after a blank line"
    (doseq [m [nil "" "  \n"]]
      (let [c (edit/locate doc m "(track :hat (seq :hh))")]
        (is (= (count doc) (:from c) (:to c)))
        (is (str/ends-with? (apply-change doc c) "\n\n(track :hat (seq :hh))")))))
  (testing "appending to an empty buffer adds no leading blank line"
    (is (= {:from 0 :to 0 :insert "(seq :bd)"} (edit/append "" "(seq :bd)")))))

(deftest push-snapshot-test
  (testing "first edit of a turn records the pre-turn text"
    (is (= [{:turn-id "t1" :text "a"}] (edit/push-snapshot [] "t1" "a"))))
  (testing "later edits in the same turn keep the pre-turn snapshot"
    (is (= [{:turn-id "t1" :text "a"}]
           (-> [] (edit/push-snapshot "t1" "a") (edit/push-snapshot "t1" "b")))))
  (testing "a new turn gets its own entry"
    (is (= ["t1" "t2"]
           (mapv :turn-id (-> [] (edit/push-snapshot "t1" "a") (edit/push-snapshot "t2" "b"))))))
  (testing "keeps only the newest max-undo-turns turns"
    (let [s (reduce #(edit/push-snapshot %1 %2 (str "text" %2)) [] (range 25))]
      (is (= edit/max-undo-turns (count s)))
      (is (= 5 (:turn-id (first s))))
      (is (= 24 (:turn-id (peek s)))))))
