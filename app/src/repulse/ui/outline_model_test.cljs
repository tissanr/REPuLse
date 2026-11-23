(ns repulse.ui.outline-model-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.ui.outline-model :as om]))

(deftest track-state-test
  (is (= :playing (om/track-state :kick #{:kick} #{})))
  (is (= :muted   (om/track-state :kick #{:kick} #{:kick})))
  (is (= :stopped (om/track-state :kick #{} #{})))
  (testing "a muted name that is not scheduled is stopped"
    (is (= :stopped (om/track-state :kick #{:hh} #{:kick})))))

(deftest running-rows-test
  (testing "empty"
    (is (= [] (om/running-rows #{} #{} #{} {}))))
  (testing "drops previews and active-buffer tracks"
    (is (= [{:kw :extra :state :playing :origin :none}]
           (om/running-rows #{:kick :__preview__ :extra} #{} #{:kick} {}))))
  (testing "origins, states and order (:_ last)"
    (is (= [{:kw :extra :state :playing :origin :none}
            {:kw :hats  :state :muted   :origin :buffer :buffer "drums"}
            {:kw :_     :state :playing :origin :anonymous}]
           (om/running-rows #{:_ :hats :extra} #{:hats} #{} {:hats "drums"})))))
