(ns repulse.lisp-patcher-test
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [repulse.lisp-patcher :as p]))

(def S
  (str/join "\n"
    ["(track :drums (->> (seq :bd :_ :bd :_) (fx :reverb 0.5)))"
     "(master"
     "  (fx :delay :time 0.375 :feedback 0.4)"
     "  (fx :reverb 0.3))"]))

(defn- apply-edit [doc {:keys [from to insert]}]
  (str (subs doc 0 from) insert (subs doc to)))

(deftest fx-form-range-test
  (is (= 125 (count S)))
  (testing "P1"
    (is (= {:from 108 :to 124} (p/fx-form-range S 108 "reverb"))))
  (testing "P2"
    (is (= {:from 68 :to 105} (p/fx-form-range S 68 "delay")))
    (is (nil? (p/fx-form-range S 108 "delay")))
    (is (nil? (p/fx-form-range S 100 "reverb")))
    (is (nil? (p/fx-form-range S nil "reverb")))
    (is (nil? (p/fx-form-range S -1 "reverb")))
    (is (nil? (p/fx-form-range S 500 "reverb")))))

(deftest fx-param-edit-test
  (testing "P3"
    (let [e (p/fx-param-edit S "reverb" "wet" "0.45" true 108)]
      (is (= {:from 120 :to 123 :insert "0.45"} e))
      (let [out (apply-edit S e)]
        (is (str/includes? out "  (fx :reverb 0.45))"))
        (is (str/includes? out "(fx :reverb 0.5)")))))
  (testing "P4: unscoped hits the first form"
    (is (= {:from 51 :to 54 :insert "0.45"}
           (p/fx-param-edit S "reverb" "wet" "0.45" true nil))))
  (testing "P5"
    (is (= {:from 101 :to 104 :insert "0.50"}
           (p/fx-param-edit S "delay" "feedback" "0.50" false 68))))
  (testing "P6"
    (let [e (p/fx-param-edit S "reverb" "room" "0.80" false 108)]
      (is (= {:from 123 :to 123 :insert " :room 0.80"} e))
      (is (str/includes? (apply-edit S e) "  (fx :reverb 0.3 :room 0.80))"))))
  (testing "P7: stale at"
    (is (nil? (p/fx-param-edit S "reverb" "wet" "0.45" true 100)))))

(deftest fx-keyword-param-edit-test
  (testing "P8"
    (is (= {:from 27 :to 32 :insert ":atan"}
           (p/fx-keyword-param-edit "(master (fx :distort :mode :tanh))" "distort" "mode" ":atan" 8)))))
