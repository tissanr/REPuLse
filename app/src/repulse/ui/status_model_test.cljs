(ns repulse.ui.status-model-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.ui.status-model :as sm]))

(def S {:total 20 :end :stop
        :sections [{:name :intro :bars 4 :start 0 :end 4}
                   {:name :verse :bars 8 :start 4 :end 12}
                   {:name :chorus :bars 8 :start 12 :end 20}]})

(def S-loop (assoc S :end :loop))

(deftest bpm-test
  (is (= 120 (sm/bpm-of 2.0)))
  (is (= 90 (sm/bpm-of (/ 240 90))))
  (is (= 2.6666666666666665 (/ 240.0 90)))
  (testing "parse-bpm accepts 2–3 digit integers within 40–300"
    (is (= [90 120 300 40] (mapv sm/parse-bpm ["90" " 120 " "300" "40"])))
    (is (every? nil? (map sm/parse-bpm ["39" "301" "120.5" "12a" ""]))))
  (testing "step-bpm"
    (is (= [121 110 300 40]
           [(sm/step-bpm 120 1 false) (sm/step-bpm 120 -1 true)
            (sm/step-bpm 295 1 true) (sm/step-bpm 45 -1 true)]))))

(deftest latency-text-test
  (is (= "5.3 ms" (sm/latency-text 0.0029 0.0024)))
  (is (= "10.0 ms" (sm/latency-text 0.01 nil)))
  (is (= "—" (sm/latency-text nil nil))))

(deftest progress-segments-test
  (is (= [[1 false] [1 false] [0.25 true]]
         (map (juxt :fill :playing?) (sm/progress-segments S 13))))
  (is (= [[1 false] [0.125 true] [0 false]]
         (map (juxt :fill :playing?) (sm/progress-segments S 4))))
  (is (= [0 0 0] (map :fill (sm/progress-segments S nil))))
  (is (= [[1 true] [0 false] [0 false]]
         (map (juxt :fill :playing?) (sm/progress-segments S-loop 23))))
  (is (= [0 0 0] (map :fill (sm/progress-segments S 25))))
  (is (= [] (sm/progress-segments nil 3)))
  (is (= [[0 :intro 4 0] [1 :verse 8 4] [2 :chorus 8 12]]
         (map (juxt :index :name :bars :start) (sm/progress-segments S 0)))))

(deftest cue-bars-test
  (is (= 12 (sm/segment-cue-bar S 2)))
  (is (= 4 (sm/segment-cue-bar S 1)))
  (is (nil? (sm/segment-cue-bar S 7)))
  (is (= 12 (sm/section-cue-bar S [{:name ":intro"} {:name ":verse"} {:name ":chorus"}] 2)))
  (is (nil? (sm/section-cue-bar S [{:name ":intro"} {:name ":bridge"}] 1))))

(deftest song-status-text-test
  (is (= "▶ chorus · bar 13/20" (sm/song-status-text {:cycle 34 :song {:song S :origin 20}})))
  (is (= "▶ song · starting" (sm/song-status-text {:cycle 20 :song {:song S :origin nil}})))
  (is (nil? (sm/song-status-text {:cycle 3}))))

(deftest inactive-section-test
  (let [s (assoc-in S [:sections 0 :tracks] [:drums])]
    (is (= :intro (sm/inactive-section {:cycle 22 :song {:song s :origin 20}} :bass)))
    (is (nil? (sm/inactive-section {:cycle 22 :song {:song s :origin 20}} :drums)))))
