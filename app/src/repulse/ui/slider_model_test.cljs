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

;;; Inline sliders (Phase WS3)

(defn- fm [v lo hi & [mode]]
  {:value {:text v} :min {:text lo} :max {:text hi} :mode (when mode {:text mode})})

(defn- near? [a b eps] (< (Math/abs (- a b)) eps))

(def ^:private c01 (sm/inline-cfg (fm "0.7" "0" "1")))
(def ^:private clog (sm/inline-cfg (fm "632" "20" "20000")))
(def ^:private cpan (sm/inline-cfg (fm "0" "-1" "1")))

(deftest parse-num-literal-test
  (testing "M1"
    (is (= [0.7 -1 0.25 20000 -0.5]
           (mapv sm/parse-num-literal ["0.7" "-1" "1/4" "20000" "-0.5"])))
    (is (= [nil nil nil nil nil]
           (mapv sm/parse-num-literal ["120bpm" ":a" "1e3" ".5" "1/0"])))))

(deftest inline-cfg-test
  (testing "M2"
    (is (= {:v 0.7 :min 0 :max 1 :log? false :min-text "0" :max-text "1"} c01)))
  (testing "M3"
    (is (= [true true false false true false]
           (mapv (comp :log? sm/inline-cfg)
                 [(fm "632" "20" "20000") (fm "5" "1" "100") (fm "5" "1" "99")
                  (fm "800" "20" "20000" ":lin") (fm "5" "1" "10" ":log") (fm "5" "0" "20000")]))))
  (testing "M4"
    (is (= [nil nil nil nil nil]
           (mapv sm/inline-cfg
                 [(fm "0.5" "0" "1" ":log") (fm "0.5" "1" "0") (fm "0.5" "1" "1")
                  (fm "0.5" "0" "1" ":exp") (fm "x" "0" "1")])))))

(deftest inline-t-value-test
  (testing "M5"
    (is (near? 0.7 (sm/inline-t c01 0.7) 1e-9))
    (is (near? 1 (sm/inline-t c01 2) 1e-9))
    (is (near? 0 (sm/inline-t clog 20) 1e-9))
    (is (near? 1 (sm/inline-t clog 20000) 1e-9))
    (is (near? 0.5 (sm/inline-t clog 632.4555320336759) 1e-9)))
  (testing "M6"
    (is (near? 632.4555320336759 (sm/inline-value clog 0.5) 1e-6))
    (is (near? 0.25 (sm/inline-value c01 0.25) 1e-12))
    (is (near? 0 (sm/inline-value cpan 0.5) 1e-12))))

(deftest inline-fmt-test
  (testing "M7"
    (is (= "0.70" (sm/inline-fmt c01 0.7)))
    (is (= "1.00" (sm/inline-fmt c01 1)))
    (is (= "-0.50" (sm/inline-fmt cpan -0.5)))
    (is (= "120" (sm/inline-fmt (sm/inline-cfg (fm "120" "40" "300")) 120.4)))
    (is (= "100" (sm/inline-fmt (sm/inline-cfg (fm "50" "0" "100")) 99.6)))
    (is (= "1261" (sm/inline-fmt clog 1261.0057830603323)))
    (is (= "0.001" (sm/inline-fmt (sm/inline-cfg (fm "0.5" "0.001" "1")) 0.002)))))

(deftest inline-drag-test
  (testing "M8"
    (is (= "0.80" (sm/inline-fmt c01 (sm/inline-value c01 (sm/inline-drag-t (sm/inline-t c01 0.7) 14)))))
    (is (= "0.71" (sm/inline-fmt c01 (sm/inline-value c01 (sm/inline-drag-t (sm/inline-t c01 0.7) 1.4)))))
    (is (= 1 (sm/inline-drag-t 0.95 14)))
    (is (= 0 (sm/inline-drag-t 0.05 -14)))
    (is (= "1261" (sm/inline-fmt clog (sm/inline-value clog (sm/inline-drag-t (sm/inline-t clog 632) 14)))))
    (is (= 140 sm/INLINE-DRAG-PX))))

(deftest inline-range-text-and-wrap-range-test
  (testing "M9 inline-range-text"
    (is (= "0–1" (sm/inline-range-text c01)))
    (is (= "-1–1" (sm/inline-range-text cpan)))
    (is (= "20–20k · log" (sm/inline-range-text clog)))
    (is (= "0–1k" (sm/inline-range-text (sm/inline-cfg (fm "5" "0" "1000")))))
    (is (= "40–300" (sm/inline-range-text (sm/inline-cfg (fm "120" "40" "300")))))
    (is (= "1–10 · log" (sm/inline-range-text (sm/inline-cfg (fm "5" "1" "10" ":log"))))))
  (testing "M9 wrap-range"
    (is (= [0 1] (sm/wrap-range "amp" 0.7)))
    (is (= [0 2] (sm/wrap-range "decay" 0.25)))
    (is (= [20 20000] (sm/wrap-range "freq" 800)))
    (is (= [40 300] (sm/wrap-range "bpm" 120)))
    (is (= [-1 1] (sm/wrap-range "pan" -0.3)))
    (is (= [0 1.5] (sm/wrap-range "amp" 1.5)))
    (is (= [0 2] (sm/wrap-range "fast" 2)))
    (is (= [0 1] (sm/wrap-range nil 0.5)))))
