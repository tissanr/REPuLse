(ns repulse.audio-clock-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.audio-clock :as clock]))

(def base {:current-time 10.0 :perf-now 5000.0})

(deftest latency-s-test
  (testing "prefers outputLatency, falls back to baseLatency, then 0"
    (is (= 0.05 (clock/latency-s {:output-latency 0.05 :base-latency 0.01})))
    (is (= 0.01 (clock/latency-s {:output-latency 0 :base-latency 0.01})))
    (is (= 0.01 (clock/latency-s {:output-latency nil :base-latency 0.01})))
    (is (= 0.01 (clock/latency-s {:output-latency js/NaN :base-latency 0.01})))
    (is (= 0 (clock/latency-s {})))))

(deftest sane-output-ts-test
  (testing "accepts a recent timestamp behind both clocks"
    (is (clock/sane-output-ts?
         (assoc base :output-ts {:context-time 9.96 :performance-time 4990.0}))))
  (testing "rejects missing, zero, future or stale timestamps"
    (is (not (clock/sane-output-ts? base)))
    (is (not (clock/sane-output-ts?
              (assoc base :output-ts {:context-time 0 :performance-time 0}))))
    (is (not (clock/sane-output-ts?
              (assoc base :output-ts {:context-time 10.5 :performance-time 4990.0}))))
    (is (not (clock/sane-output-ts?
              (assoc base :output-ts {:context-time 9.96 :performance-time 5100.0}))))
    (is (not (clock/sane-output-ts?
              (assoc base :output-ts {:context-time 9.0 :performance-time 4000.0}))))))

(deftest audible-perf-ms-test
  (testing "uses getOutputTimestamp when sane, without adding latency again"
    (let [snap (assoc base
                      :output-ts {:context-time 9.75 :performance-time 4995.0}
                      :output-latency 0.04)]
      ;; t = 10.5 is 0.75 s after the output sample at 9.75
      (is (= 5745.0 (clock/audible-perf-ms snap 10.5)))))
  (testing "falls back to currentTime + outputLatency"
    (is (= 5540.0 (clock/audible-perf-ms (assoc base :output-latency 0.04) 10.5))))
  (testing "falls back to baseLatency when outputLatency is missing (Safari)"
    (is (= 5510.0 (clock/audible-perf-ms (assoc base :base-latency 0.01) 10.5))))
  (testing "falls back to the raw clock offset when no latency is known"
    (is (= 5500.0 (clock/audible-perf-ms base 10.5))))
  (testing "rejected output timestamp uses the fallback path"
    (let [snap (assoc base
                      :output-ts {:context-time 0 :performance-time 0}
                      :output-latency 0.04)]
      (is (= 5540.0 (clock/audible-perf-ms snap 10.5))))))

(deftest frame-queue-test
  (let [q (-> []
              (clock/enqueue {:at 300 :id :c})
              (clock/enqueue {:at 100 :id :a})
              (clock/enqueue {:at 200 :id :b}))]
    (testing "enqueue keeps entries sorted by :at"
      (is (= [:a :b :c] (mapv :id q))))
    (testing "entries within half a frame fire on this frame"
      (let [{:keys [due pending]} (clock/split-due q 192 8 250)]
        (is (= [:a :b] (mapv :id due)))
        (is (= [:c] (mapv :id pending)))))
    (testing "nothing is due before its time"
      (is (= [] (:due (clock/split-due q 50 8 250))))
      (is (= 3 (count (:pending (clock/split-due q 50 8 250))))))
    (testing "stale entries are dropped, not fired"
      (let [{:keys [due pending]} (clock/split-due q 420 8 250)]
        (is (= [:b :c] (mapv :id due)))
        (is (= [] pending))))))
