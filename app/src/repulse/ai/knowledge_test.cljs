(ns repulse.ai.knowledge-test
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [repulse.ai.knowledge :as knowledge]
            [repulse.ai.system-prompt :as sys-prompt]))

(deftest knowledge-base-compiled-in-test
  (is (> (count knowledge/builtins) 100) "builtins.json is inlined and parsed")
  (is (seq knowledge/cookbook) "cookbook.json is inlined and parsed"))

(deftest builtins-summary-test
  (let [summary (knowledge/builtins-summary)]
    (is (not (str/blank? summary)))
    (testing "core names appear under their categories"
      (is (re-find #"(?m)^source: .*\bseq\b" summary))
      (is (re-find #"(?m)^transform: .*\bfast\b" summary)))
    (testing "every built-in is listed"
      (doseq [n ["stack" "track" "scale" "chord" "euclidean" "amp" "fx"]]
        (is (str/includes? summary n) n))))
  (is (= "" (knowledge/builtins-summary {})) "degrades to empty"))

(deftest examples-test
  (let [ex (knowledge/examples)]
    (testing "all selected recipes exist in the cookbook"
      (is (= (count knowledge/example-ids)
             (count (re-seq #"(?m)^;; " ex)))))
    (testing "examples use real sound names, never invented ones"
      (is (str/includes? ex ":bd"))
      (is (not (re-find #"\(seq[^)]*:(kick|snare|hat|hihat|clap)\b" ex))))))

(deftest system-prompt-includes-knowledge-test
  (let [p (sys-prompt/build)]
    (is (str/includes? p "Built-in vocabulary"))
    (is (str/includes? p (knowledge/builtins-summary)))
    (is (str/includes? p "There is NO :kick, :snare, :hat"))
    (is (str/includes? p "Working examples"))
    (is (str/includes? p (knowledge/examples)))))
