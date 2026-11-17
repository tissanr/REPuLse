(ns repulse.inserts-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [repulse.inserts :as ins]))

(defn- near? [a b] (< (Math/abs (- a b)) 1e-6))

(deftest insert-params-nil-without-inserts
  (is (nil? (ins/insert-params {:note :c4 :amp 0.5})))
  (is (nil? (ins/insert-params :bd)))
  (is (nil? (ins/insert-params {:note :c4 :resonance 4})) "Q alone has no effect")
  (is (nil? (ins/insert-params {:note :c4 :shape 0 :crush 16})) "off values count as absent")
  (is (nil? (ins/insert-params {:note :c4 :gain 0.5 :velocity 0.3})) "gain is not an insert"))

(deftest insert-params-defaults
  (is (= {:lpf 800.0 :resonance 0.707 :hpf nil :hpq 0.707 :bpf nil :bpq 1.0
          :shape 0.0 :crush 16.0}
         (ins/insert-params {:note :c4 :lpf 800}))))

(deftest insert-params-clamps
  (let [p (ins/insert-params {:lpf 5 :resonance 100 :hpf 99999 :hpq 0
                              :bpf 1000 :bpq -3 :shape 2 :crush 0})]
    (is (= 20.0 (:lpf p)))
    (is (= 30.0 (:resonance p)))
    (is (= 20000.0 (:hpf p)))
    (is (= 0.1 (:hpq p)))
    (is (= 0.1 (:bpq p)))
    (is (= 0.99 (:shape p)))
    (is (= 1.0 (:crush p)))))

(deftest insert-params-rationals-and-junk
  (is (= 500.0 (:lpf (ins/insert-params {:lpf [1000 2]}))))
  (is (nil? (ins/insert-params {:lpf :high})) "non-numbers are ignored")
  (is (= 4.0 (:crush (ins/insert-params {:crush 4})))))

(deftest amp-factor-model
  (is (= 1.0 (ins/amp-factor {:note :c4})))
  (is (= 1.0 (ins/amp-factor :bd)))
  (is (= 0.5 (ins/amp-factor {:gain 0.5})))
  (is (= 1.0 (ins/amp-factor {:gain 2 :velocity 0.5})))
  (is (= 2.0 (ins/amp-factor {:gain 9})) "gain clamps to 2")
  (is (= 1.0 (ins/amp-factor {:velocity 3})) "velocity clamps to 1")
  (is (= 0.0 (ins/amp-factor {:gain -1}))))

(deftest shape-curve-formula
  (let [c (ins/shape-curve 0.5 5)            ; k = 2
        k 2.0]
    (is (= 5 (.-length c)))
    (is (near? -1.0 (aget c 0)) "ends map to ±1")
    (is (near? 0.0 (aget c 2)))
    (is (near? 1.0 (aget c 4)))
    (is (near? (/ (* (+ 1 k) 0.5) (+ 1 (* k 0.5))) (aget c 3)))))

(deftest shape-curve-zero-is-identity
  (let [c (ins/shape-curve 0 9)]
    (doseq [i (range 9)]
      (is (near? (- (/ (* 2.0 i) 8) 1) (aget c i))))))

(deftest crush-curve-staircase
  (let [c (ins/crush-curve 4 65536)
        vals (set (array-seq c))]
    (is (<= (count vals) 17) "4 bits → levels 8 → values −1, −7/8 … 1")
    (is (every? #(near? (* 8 %) (Math/round (* 8 %))) vals)))
  (is (near? 0.2 (aget (ins/crush-curve 16 11) 6)) "bits 16 bypass — identity curve"))

(deftest crush-levels-bypass
  (is (nil? (ins/crush-levels 16)))
  (is (= 8 (ins/crush-levels 4))))

(deftest q-conversion
  (is (near? 0 (ins/q->webaudio "lowpass" 1)))
  (is (near? 12.0412 (ins/q->webaudio "highpass" 4)) "20·log10 4")
  (is (= 5 (ins/q->webaudio "bandpass" 5))))

(deftest wasm-slots-order
  (is (= [800.0 4.0 0 0.707 0 1.0 0.0 16.0]
         (ins/wasm-slots (ins/insert-params {:lpf 800 :resonance 4})))))
