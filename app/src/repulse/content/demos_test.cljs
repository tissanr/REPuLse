(ns repulse.content.demos-test
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [repulse.ai.knowledge :as knowledge]
            [repulse.content.demos :as demos]
            [repulse.lisp.reader :as reader]))

(def ^:private defining-forms #{'def 'defn 'defsynth 'defmacro})

(defn- all-forms [code]
  (tree-seq coll? seq (reader/read-all code)))

(defn- called-names
  "Every symbol in head position of a list form."
  [code]
  (->> (all-forms code)
       (filter #(and (seq? %) (symbol? (first %))))
       (map (comp str first))
       set))

(defn- defined-names [code]
  (->> (all-forms code)
       (filter #(and (seq? %) (contains? defining-forms (first %))))
       (map (comp str second))
       set))

(deftest every-demo-parses
  (doseq [[k {:keys [code]}] demos/demo-templates]
    (testing (name k)
      (is (seq (reader/read-all code))))))

(deftest demo-bpm-matches-code
  (doseq [[k {:keys [bpm code]}] demos/demo-templates]
    (testing (name k)
      (is (str/includes? code (str "(bpm " bpm ")"))))))

(deftest demos-name-their-features
  (doseq [[k {:keys [code]}] demos/demo-templates]
    (testing (name k)
      (is (re-find #"(?m)^;; Shows: " code)))))

(deftest demos-only-call-known-builtins
  (let [known (set (keys knowledge/builtins))]
    (is (seq known) "builtins.json is available")
    (doseq [[k {:keys [code]}] demos/demo-templates]
      (testing (name k)
        (is (empty? (remove (into known (defined-names code))
                            (called-names code))))))))
