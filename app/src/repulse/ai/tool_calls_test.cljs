(ns repulse.ai.tool-calls-test
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [repulse.ai.tool-calls :as tc]))

(def known #{"read_buffer" "propose_edit"})

(def good  {:id "1" :name "read_buffer" :args {}})
(def bad-args {:id "2" :name "propose_edit" :args {} :args-error "arguments are not a JSON object: {x"})
(def unknown  {:id "3" :name "make_coffee" :args {}})

(deftest call-error-test
  (is (nil? (tc/call-error known good)))
  (is (str/includes? (tc/call-error known unknown) "unknown tool: make_coffee"))
  (is (str/includes? (tc/call-error known bad-args) "{x"))
  (is (tc/call-error known {:id "4" :name nil :args {}})))

(deftest next-action-test
  (testing "well-formed calls execute"
    (is (= :execute (tc/next-action known [good] 0)))
    (is (= :execute (tc/next-action known [good] 1))))
  (testing "first malformed batch is fed back, the second gives up"
    (is (= :retry   (tc/next-action known [good unknown] 0)))
    (is (= :give-up (tc/next-action known [bad-args] 1)))))

(deftest error-results-test
  (let [[r1 r2 r3] (tc/error-results known [good bad-args unknown])]
    (testing "one result per call, ids preserved (OpenAI requires a reply to every call)"
      (is (= ["1" "2" "3"] (map :id [r1 r2 r3]))))
    (is (every? #(false? (get-in % [:result :ok])) [r1 r2 r3]))
    (is (str/includes? (get-in r1 [:result :error]) "Not executed"))
    (is (str/includes? (get-in r2 [:result :error]) "Invalid tool call"))
    (is (str/includes? (get-in r3 [:result :error]) "make_coffee"))))
