(ns repulse.fx-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.fx :as fx]))

(deftest apply-track-effects-replaces-chain-and-applies-params
  (testing "pattern FX metadata is applied as a fresh track chain"
    (let [calls (atom [])]
      (with-redefs [fx/clear-track-effects! (fn [track-name]
                                              (swap! calls conj [:clear track-name]))
                    fx/add-track-effect!    (fn [track-name effect-name]
                                              (swap! calls conj [:add track-name effect-name]))
                    fx/set-track-param!     (fn [track-name effect-name param-name value]
                                              (swap! calls conj [:set track-name effect-name param-name value]))]
        (fx/apply-track-effects! :_ [{:name "delay"
                                      :params (array-map "time" 0.5 "wet" 0.3)}
                                     {:name "distort"
                                      :params (array-map "drive" 100)}]))
      (is (= [[:clear :_]
              [:add :_ "delay"]
              [:set :_ "delay" "time" 0.5]
              [:set :_ "delay" "wet" 0.3]
              [:add :_ "distort"]
              [:set :_ "distort" "drive" 100]]
             @calls)))))
