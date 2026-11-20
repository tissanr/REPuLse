(ns repulse.orbits-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.core :as core]
            [repulse.params :as params]
            [repulse.orbits :as orbits]))

(deftest orbit-index-rounds-and-clamps
  (is (= 0 (orbits/orbit-index nil)))
  (is (= 0 (orbits/orbit-index "x")))
  (is (= 2 (orbits/orbit-index 2)))
  (is (= 1 (orbits/orbit-index 1.4)))
  (is (= 3 (orbits/orbit-index 7)))
  (is (= 0 (orbits/orbit-index -1)))
  (is (= 2 (orbits/orbit-index [3 2])) "rationals: 3/2 rounds to 2"))

(deftest out-of-range-message-is-exact
  (is (= "orbit 7 is out of range 0–3; using 3" (orbits/out-of-range-message 7)))
  (is (= "orbit -1 is out of range 0–3; using 0" (orbits/out-of-range-message -1)))
  (is (orbits/out-of-range? 7))
  (is (orbits/out-of-range? 3.6))
  (is (not (orbits/out-of-range? 3.4)))
  (is (not (orbits/out-of-range? 0)))
  (is (not (orbits/out-of-range? nil))))

(deftest send-params-nil-without-sends
  (is (nil? (orbits/send-params :bd)))
  (is (nil? (orbits/send-params {:note :c4})))
  (is (nil? (orbits/send-params {:note :c4 :room 0 :delay 0 :orbit 2})))
  (is (= {:room 0.8 :delay 0.0 :orbit 0} (orbits/send-params {:note :c4 :room 0.8})))
  (is (= {:room 1.0 :delay 0.5 :orbit 3}
         (orbits/send-params {:note :c4 :room 3 :delay 0.5 :orbit 7}))
      "levels clamp to 0–1, orbit to 0–3"))

(deftest wasm-slots-layout
  (is (= [0.4 0.2 1] (orbits/wasm-slots {:room 0.4 :delay 0.2 :orbit 1})))
  (is (= [0 0 0] (orbits/wasm-slots nil))))

(deftest orbit-settings-present-keys-clamped
  (is (nil? (orbits/orbit-settings {:note :c4 :room 0.5})))
  (is (= {:size 1.0} (orbits/orbit-settings {:size 4})))
  (is (= {:delay-time 0.01 :delay-feedback 0.95}
         (orbits/orbit-settings {:delay-time 0 :delay-feedback 2})))
  (is (= {:delay-time 4.0} (orbits/orbit-settings {:delay-time 10}))))

(deftest orbit-settings-delay-sync
  (is (= {:delay-time 0.5} (orbits/orbit-settings {:delay-sync 0.25} 2.0)))
  (is (= {:delay-time 0.375} (orbits/orbit-settings {:delay-sync [3 16]} 2.0)))
  (is (= {:delay-time 0.5} (orbits/orbit-settings {:delay-sync 0.25 :delay-time 3} 2.0))
      "delay-sync overrides delay-time")
  (is (= {:delay-time 3.0} (orbits/orbit-settings {:delay-sync 0.25 :delay-time 3}))
      "no cycle length: delay-time stays")
  (is (nil? (orbits/orbit-settings {:delay-sync 0.25})))
  (is (= {:delay-time 4.0} (orbits/orbit-settings {:delay-sync 16} 2.0)) "clamped to 4 s"))

(deftest size-steps-and-ir-length
  (is (= 0 (orbits/size-step 0)))
  (is (= 2 (orbits/size-step 0.1)))
  (is (= 10 (orbits/size-step 0.5)))
  (is (= 20 (orbits/size-step 1)))
  (is (= 20 (orbits/size-step 3)))
  (is (< (Math/abs (- 0.3 (orbits/ir-seconds 0))) 1e-9))
  (is (< (Math/abs (- 3.15 (orbits/ir-seconds 10))) 1e-9))
  (is (< (Math/abs (- 6.0 (orbits/ir-seconds 20))) 1e-9)))

(deftest fill-ir-is-deterministic-and-decays
  (let [a (orbits/fill-ir! (js/Float32Array. 1000) 4 0)
        b (orbits/fill-ir! (js/Float32Array. 1000) 4 0)
        c (orbits/fill-ir! (js/Float32Array. 1000) 4 1)
        abs-sum (fn [arr from to] (reduce + (map #(Math/abs (aget arr %)) (range from to))))]
    (is (= (vec (array-seq a)) (vec (array-seq b))) "same seed → same IR")
    (is (not= (vec (array-seq a)) (vec (array-seq c))) "channels differ")
    (is (every? #(<= -1 % 1) (array-seq a)))
    (is (> (abs-sum a 0 100) (* 10 (abs-sum a 800 900))) "envelope decays")))

(deftest orbit-warnings-distinct-per-value
  (let [q   (fn [pat c] (core/query pat (core/cycle-span c)))
        pat (params/orbit (core/seq* [7 1 7 -2]) (core/seq* [:bd :bd :bd :bd]))]
    (is (= ["orbit 7 is out of range 0–3; using 3"
            "orbit -2 is out of range 0–3; using 0"]
           (orbits/orbit-warnings [pat] q 8)))
    (is (= [] (orbits/orbit-warnings [(params/orbit 2 (core/pure :bd))] q 8)))
    (testing "patterns without :orbit"
      (is (= [] (orbits/orbit-warnings [(core/pure :bd)] q 8))))))
