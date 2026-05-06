(ns repulse.ui.timeline-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.core :as core]
            [repulse.ui.timeline :as timeline]))

(deftest value-pitch-test
  (testing "maps arbitrary event values to stable chromatic rows"
    (is (= 2 (timeline/value-pitch -14 0)))
    (is (= 6 (timeline/value-pitch :bd 0)))
    (is (= 9 (timeline/value-pitch {:note 9} 0)))
    (is (= 4 (timeline/value-pitch {:sample :hh} 0)))
    (is (= 7 (timeline/value-pitch nil 7)))))

(deftest track-events-test
  (testing "converts pattern events into normalized cycle-local timeline events"
    (let [data (timeline/track-events :drums
                                      (core/seq* [:bd :sd])
                                      0
                                      "#e94560"
                                      false)]
      (is (= :drums (:name data)))
      (is (= 0 (:cycle data)))
      (is (= "#e94560" (:color data)))
      (is (false? (:muted? data)))
      (is (= [{:pos 0 :dur 0.5 :value :bd}
              {:pos 0.5 :dur 0.5 :value :sd}]
             (:events data))))))

(deftest energy-curve-test
  (testing "samples a precomputed energy curve with wraparound interpolation"
    (is (= 1 (timeline/sample-energy [1 3] 0)))
    (is (= 2 (timeline/sample-energy [1 3] 0.25)))
    (is (= 3 (timeline/sample-energy [1 3] 0.5)))
    (is (= 2 (timeline/sample-energy [1 3] 0.75)))
    (is (= 1 (timeline/sample-energy [1 3] 1))))
  (testing "precomputes event energy once at a fixed curve resolution"
    (let [curve (timeline/energy-curve [{:pos 0 :dur 0.25}])]
      (is (= 512 (count curve)))
      (is (> (first curve) 0.9))
      (is (= 0.06 (last curve))))))
