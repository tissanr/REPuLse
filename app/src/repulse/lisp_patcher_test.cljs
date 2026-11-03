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

;;; Inline sliders (Phase WS3)

(def T
  (str/join "\n"
    ["(track :bass (->> (seq :e1 :g1) (synth :saw) (amp (slider 0.7 0 1))))"
     "(track :hats (->> (seq :hh :hh) (decay 0.25)))"
     "(def cutoff (slider 632 20 20000))"]))

(def ^:private bass-form
  {:from 50 :to 66
   :value {:from 58 :to 61 :text "0.7"}
   :min   {:from 62 :to 63 :text "0"}
   :max   {:from 64 :to 65 :text "1"}
   :mode  nil})

(deftest find-slider-forms-test
  (is (= 151 (count T)))
  (testing "P1"
    (is (= [bass-form
            {:from 129 :to 150
             :value {:from 137 :to 140 :text "632"}
             :min   {:from 141 :to 143 :text "20"}
             :max   {:from 144 :to 149 :text "20000"}
             :mode  nil}]
           (p/find-slider-forms T))))
  (testing "P2"
    (is (= [] (p/find-slider-forms "; (slider 0.5 0 1)\n(seq \"(slider 1 0 2)\")")))
    (is (= [] (p/find-slider-forms "(slider x 0 1)")))
    (is (= [] (p/find-slider-forms "(slider 0.5 0)")))
    (is (= [] (p/find-slider-forms "(slider 0.5 0 1 :log 2)"))))
  (testing "P3"
    (is (= {:from 16 :to 20 :text ":log"}
           (:mode (first (p/find-slider-forms "(slider 0.5 0 1 :log)")))))))

(deftest slider-form-at-test
  (testing "P4"
    (is (= bass-form (p/slider-form-at T 50)))
    (is (nil? (p/slider-form-at T 51)))
    (is (= 129 (:from (p/slider-form-at T 129))))))

(deftest visible-sliders-test
  (testing "P5"
    (doseq [[sel expected] [[[[0 0]] [50 129]]
                            [[[59 59]] [129]]
                            [[[50 50]] [129]]
                            [[[49 49]] [50 129]]
                            [[[66 66]] [129]]
                            [[[67 67]] [50 129]]
                            [[[0 151]] []]]]
      (is (= expected (mapv (comp :from :form) (p/visible-sliders T sel))) (pr-str sel))))
  (testing "P6"
    (is (= [] (p/visible-sliders "(slider 0.5\n 0 1)" [[0 0]])))
    (is (= [] (p/visible-sliders "(slider 0.5 1 0)" [[0 0]])))))

(deftest slider-toggle-edit-test
  (testing "P7"
    (is (= {:from 7 :to 11 :insert "(slider 0.25 0 2)" :cursor 19}
           (p/slider-toggle-edit "(decay 0.25)" 9))))
  (testing "P8"
    (is (= {:from 7 :to 24 :insert "0.25" :cursor 11}
           (p/slider-toggle-edit "(decay (slider 0.25 0 2))" 10)))
    (is (= {:from 7 :to 24 :insert "0.25" :cursor 11}
           (p/slider-toggle-edit "(decay (slider 0.25 0 2))" 24))))
  (testing "P9"
    (is (= {:from 18 :to 21 :insert "(slider 800 20 20000)" :cursor 29}
           (p/slider-toggle-edit "(fx :filter :freq 800)" 19)))
    (is (= {:from 12 :to 15 :insert "(slider 800 20 20000)" :cursor 23}
           (p/slider-toggle-edit "(fx :filter 800)" 13)))
    (is (= {:from 5 :to 8 :insert "(slider 1.5 0 1.5)" :cursor 16}
           (p/slider-toggle-edit "(amp 1.5 (seq :c4))" 6)))
    (is (= {:from 6 :to 7 :insert "(slider 2 0 2)" :cursor 15}
           (p/slider-toggle-edit "(fast 2 (seq :bd))" 6))))
  (testing "P10"
    (is (nil? (p/slider-toggle-edit "(seq :bd)" 3)))
    (is (nil? (p/slider-toggle-edit "; (decay 0.25)" 10)))
    (is (nil? (p/slider-toggle-edit "(bpm 120bpm)" 7)))))

(deftest slider-aware-finders-test
  (testing "P11"
    (is (= {:from 58 :to 61 :slider {:from 50 :to 66 :min 0 :max 1}}
           (p/find-param-num T 0 69 "amp")))
    (is (= {:from 109 :to 113} (p/find-param-num T 70 116 "decay"))))
  (testing "P12"
    (is (= {:from 26 :to 29 :insert "900" :slider {:from 18 :to 39 :min 20 :max 20000}}
           (p/fx-param-edit "(fx :filter :freq (slider 800 20 20000))" "filter" "freq" "900" false nil)))
    (is (= {:from 20 :to 23 :insert "0.45" :slider {:from 12 :to 28 :min 0 :max 1}}
           (p/fx-param-edit "(fx :reverb (slider 0.3 0 1))" "reverb" "wet" "0.45" true nil)))))
