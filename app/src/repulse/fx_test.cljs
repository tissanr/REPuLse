(ns repulse.fx-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.core :as core]
            [repulse.env.builtins.fx :as b-fx]
            [repulse.fx :as fx]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]))

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

(deftest per-track-waveshape-accepts-parenthesized-curve
  (testing "DST5 waveshape curve data can use a parenthesized numeric list"
    (let [played (atom nil)
          env    (merge (leval/make-env (fn [] nil) (fn [_] nil))
                        (b-fx/make-builtins nil)
                        {"track" (fn [_name pat]
                                   (let [pat' (leval/unwrap pat)]
                                     (reset! played pat')
                                     pat'))})
          code   "(track :melody
                    (->> (scale :minor :a4 (seq 0 2 4 7 4 2))
                         (slow 2)
                         (amp 0.4)
                         (attack 0.1)
                         (fx :waveshape :curve (-1.0 -0.8 -0.3 0 0.3 0.9 1.0) :drive 3)))"
          result (lisp/eval-string code env)
          pat    @played
          fx-meta (first (:track-fx pat))]
      (is (not (lisp/eval-error? result)) (:message result))
      (is (core/pattern? pat))
      (is (= "waveshape" (:name fx-meta)))
      (is (= [-1.0 -0.8 -0.3 0 0.3 0.9 1.0]
             (vec (get-in fx-meta [:params "curve"]))))
      (is (= 3 (get-in fx-meta [:params "drive"]))))))

(deftest reset-global-effects-resets-plugin-state-and-flags
  (testing "global plugin params do not leak between evaluations"
    (let [calls  (atom [])
          plugin #js {:resetParams (fn [] (swap! calls conj :reset))}]
      (reset! fx/chain [{:name "distort"
                         :plugin plugin
                         :active? true
                         :bypassed? true}])
      (fx/reset-global-effects!)
      (is (= [:reset] @calls))
      (is (= [{:name "distort"
               :plugin plugin
               :active? false
               :bypassed? false}]
             @fx/chain)))))
