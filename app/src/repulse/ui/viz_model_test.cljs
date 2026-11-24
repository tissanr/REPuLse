(ns repulse.ui.viz-model-test
  "Phase VIZ1 — the reference test vectors of the spec plus option validation,
   rows, the scope trigger and song-mode resting tracks."
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.core :as core]
            [repulse.params :as params]
            [repulse.song :as song]
            [repulse.ui.viz-model :as vm]))

(defn- laid [pat]
  (vm/layout-events (vm/query-window pat 0 1)))

(deftest layout-pitched
  (let [evs (laid (core/seq* [:c3 :eb3 :g3 :bb3]))]
    (is (= [48 51 55 58] (mapv :midi evs)))
    (is (= [0 0.25 0.5 0.75] (mapv :start evs)))
    (is (= [0.25 0.5 0.75 1] (mapv :end evs)))
    (is (every? nil? (map :lane evs)))
    (is (= ["c3" "eb3" "g3" "bb3"] (mapv :label evs)))))

(deftest layout-drums
  (let [evs (laid (core/seq* [:bd :sd :bd :sd]))]
    (is (every? nil? (map :midi evs)))
    (is (= ["bd" "sd"] (:drum-lanes (vm/rows evs {:fold true}))))
    (is (= [] (:pitch-rows (vm/rows evs {:fold true}))))))

(deftest layout-bare-numbers-are-hz
  (is (= [69 57] (mapv :midi (laid (core/seq* [440 220]))))))

(deftest layout-velocity-alpha
  (let [evs (laid (params/amp 0.2 (core/seq* [:c4 :e4])))]
    (is (= 2 (count evs)))
    (is (every? #(< (Math/abs (- 0.4 (:alpha %))) 1e-9) evs))))

(deftest layout-drops-rests
  (is (= 2 (count (laid (core/seq* [:bd :_ :sd :_]))))))

(deftest query-window-merges-fragments
  ;; one 2-cycle note queried over [0.5, 1.5) is one event, not two fragments
  (let [evs (vm/layout-events (vm/query-window (core/slow 2 (core/pure :c3)) 0.5 1.5))]
    (is (= 1 (count evs)))
    (is (= [0 2] [(:start (first evs)) (:end (first evs))]))))

(deftest heard-cycle-and-window
  (is (= 5.0 (vm/heard-cycle #(/ % 2) 10.05 0.05)))
  (is (= [3.0 7.0] (vm/window 5.0 {:cycles 4 :playhead 0.5})))
  (is (= [5.0 6.0] (vm/window 5.0 {:cycles 1 :playhead 0})))
  (is (= [4 6] (vm/grid-window 5.5 2)))
  (is (= [5 6] (vm/grid-window 5.5 1))))

(deftest rows-fold
  (let [evs (laid (core/seq* [:c3 :e3 :g3]))]
    (is (= [48 52 55] (:pitch-rows (vm/rows evs {:fold true}))))
    (is (= (vec (range 48 56)) (:pitch-rows (vm/rows evs {:fold false}))))))

(deftest validate-opts-texts
  (testing "defaults"
    (is (= {:ok {:cycles 4 :playhead 0.5 :labels false :fold true :height 80}}
           (vm/validate-opts :pianoroll nil)))
    (is (= 2 (get-in (vm/validate-opts :pianoroll {:cycles 2}) [:ok :cycles]))))
  (testing "unknown option with and without a close match"
    (is (= {:error "pianoroll: unknown option :cycle — did you mean :cycles?"}
           (vm/validate-opts :pianoroll {:cycle 2})))
    (is (= {:error "pianoroll: unknown option :zzzzzz — known options: :cycles :playhead :labels :fold :height"}
           (vm/validate-opts :pianoroll {:zzzzzz 2}))))
  (testing "range and type"
    (is (= {:error "pianoroll: :cycles must be between 1 and 16, got 40"}
           (vm/validate-opts :pianoroll {:cycles 40})))
    (is (= {:error "scope: :scale must be between 0.1 and 10, got 20"}
           (vm/validate-opts :scope {:scale 20})))
    (is (= {:error "punchcard: :labels must be true or false, got 3"}
           (vm/validate-opts :punchcard {:labels 3})))
    (is (= {:error "spiral: :cycles must be a number, got :x"}
           (vm/validate-opts :spiral {:cycles :x}))))
  (testing "rationals become floats"
    (is (= 0.25 (get-in (vm/validate-opts :pianoroll {:playhead [1 4]}) [:ok :playhead])))))

(deftest scope-trigger
  (is (= 3 (vm/scope-trigger-index (js/Float32Array.from #js [0.5 0.2 -0.1 0.3 0.6 0.1 -0.2 0.4]))))
  (is (= 0 (vm/scope-trigger-index (js/Float32Array.from #js [0.5 0.4 0.3 0.2]))))
  (is (= 0 (vm/scope-trigger-index (js/Float32Array. 8)))))

(deftest song-resting
  (let [s (song/make-song [(song/make-section :a 2 [:kick])
                           (song/make-section :b 2 [:kick :bass])]
                          {} [:kick :bass] (constantly nil))
        state {:tracks {:kick 1 :bass 2} :song {:song s :origin 10}}]
    (is (= #{:bass} (vm/song-resting-tracks state 11)))
    (is (= #{} (vm/song-resting-tracks state 12)))
    (is (nil? (vm/song-resting-tracks {:tracks {:kick 1}} 3)))))
