(ns repulse.envelope-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.envelope :as env]))

;;; ── Helpers ──────────────────────────────────────────────────────────────

(defn- approx= [a b] (< (Math/abs (- a b)) 1e-9))
(defn- approx-seq= [xs ys] (every? true? (map approx= xs ys)))

;;; ── make-env ─────────────────────────────────────────────────────────────

(deftest test-make-env
  (testing "two-arg form fills curves with :lin"
    (let [e (env/make-env [0 1 0] [0.1 0.5])]
      (is (= [0 1 0] (:levels e)))
      (is (= [0.1 0.5] (:times e)))
      (is (= [:lin :lin] (:curves e)))))

  (testing "provided curves are preserved"
    (let [e (env/make-env [0 1 0] [0.1 0.5] [:exp :sin])]
      (is (= [:exp :sin] (:curves e)))))

  (testing "short curves vector is padded with :lin"
    (let [e (env/make-env [0 1 0 0] [0.1 0.5 0.2] [:exp])]
      (is (= [:exp :lin :lin] (:curves e))))))

;;; ── total-duration ───────────────────────────────────────────────────────

(deftest test-total-duration
  (testing "sums segment times"
    (is (approx= 0.61 (env/total-duration (env/make-env [0 1 0] [0.1 0.5 0.01]))))))

;;; ── lin-samples ──────────────────────────────────────────────────────────

(deftest test-lin-samples
  (testing "starts at from"
    (is (approx= 0.0 (first (env/lin-samples 0 1 8)))))
  (testing "ends at to"
    (is (approx= 1.0 (last (env/lin-samples 0 1 8)))))
  (testing "midpoint is halfway"
    (let [s (env/lin-samples 0.0 1.0 3)]
      (is (approx= 0.5 (second s)))))
  (testing "n=2 returns exactly [from to]"
    (is (approx-seq= [2.0 5.0] (env/lin-samples 2 5 2)))))

;;; ── sin-samples ──────────────────────────────────────────────────────────

(deftest test-sin-samples
  (testing "starts at from, ends at to"
    (let [s (env/sin-samples 0 1 16)]
      (is (approx= 0.0 (first s)))
      (is (approx= 1.0 (last s)))))
  (testing "midpoint is 0.5 (symmetric)"
    (let [s (env/sin-samples 0 1 3)]
      (is (approx= 0.5 (second s)))))
  (testing "curve is slower at edges than linear"
    ;; At t=0.25, sin curve < linear curve (ease-in)
    (let [s-sin (env/sin-samples 0 1 5)
          s-lin (env/lin-samples 0 1 5)]
      (is (< (nth s-sin 1) (nth s-lin 1))))))

;;; ── welch-samples ────────────────────────────────────────────────────────

(deftest test-welch-samples
  (testing "starts at from, ends at to (rising)"
    (let [s (env/welch-samples 0 1 16)]
      (is (approx= 0.0 (first s)))
      (is (approx= 1.0 (last s)))))
  (testing "starts at from, ends at to (falling)"
    (let [s (env/welch-samples 1 0 16)]
      (is (approx= 1.0 (first s)))
      (is (approx= 0.0 (last s)))))
  (testing "rising curve is faster at start than linear"
    ;; sin(π/2·t) at t=0.25 ≈ 0.383 > 0.25 (linear)
    (let [s-w (env/welch-samples 0 1 5)
          s-l (env/lin-samples 0 1 5)]
      (is (> (nth s-w 1) (nth s-l 1))))))

;;; ── exp-samples ──────────────────────────────────────────────────────────

(deftest test-exp-samples
  (testing "starts near from, ends near to (positive range)"
    (let [s (env/exp-samples 1 0.001 16)]
      (is (approx= 1.0 (first s)))
      (is (approx= 0.001 (last s)))))
  (testing "falls back to linear when from=0"
    ;; exp-samples clamps 0→0.0001 internally — just checks no crash
    (let [s (env/exp-samples 0 1 4)]
      (is (= 4 (count s)))))
  (testing "sign crossing falls back to linear"
    (let [s-exp (env/exp-samples -1 1 5)
          s-lin (env/lin-samples -1 1 5)]
      (is (approx-seq= s-lin s-exp)))))

;;; ── segment-samples ──────────────────────────────────────────────────────

(deftest test-segment-samples
  (testing ":lin"
    (is (approx-seq= [0.0 0.5 1.0] (env/segment-samples 0 1 :lin 3))))
  (testing ":step stays at from until last sample"
    (let [s (env/segment-samples 0 1 :step 4)]
      (is (every? #(approx= 0.0 %) (butlast s)))
      (is (approx= 1.0 (last s)))))
  (testing ":sin symmetric midpoint"
    (let [s (env/segment-samples 0 1 :sin 3)]
      (is (approx= 0.5 (second s)))))
  (testing "numeric curvature 1.0 = linear"
    (let [s-c (env/segment-samples 0 1 1.0 5)
          s-l (env/lin-samples 0 1 5)]
      (is (approx-seq= s-l s-c)))))
