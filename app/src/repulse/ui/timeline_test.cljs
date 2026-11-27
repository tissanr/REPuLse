(ns repulse.ui.timeline-test
  "Phase VIZ1: the panel's modes. The fake-energy code (energy-curve,
   sample-energy, value-pitch) is gone; the event and audio arithmetic is
   tested in repulse.ui.viz-model-test."
  (:require [cljs.test :refer [deftest is]]
            [repulse.ui.timeline :as timeline]
            [repulse.ui.viz-model :as vm]))

(deftest panel-modes
  (is (= ["punch" "roll" "scope" "spec" "level"] (mapv :id timeline/modes)))
  (is (= :punchcard (:kind (first timeline/modes))))
  (is (every? #(contains? vm/opt-specs (:kind %)) timeline/modes))
  (is (every? #(:ok (vm/validate-opts (:kind %) (:opts %))) timeline/modes)))
