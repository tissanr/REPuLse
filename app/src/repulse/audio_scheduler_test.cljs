(ns repulse.audio-scheduler-test
  "Phase QA3 — pure parts of the scheduler / app-layer fixes: tempo anchor,
   pending-disconnect sweep, sample LRU, autosave trigger."
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.tempo :as tempo]
            [repulse.audio :as audio]
            [repulse.samples :as samples]
            [repulse.session :as session]))

(defn- near? [a b] (< (Math/abs (- a b)) 1e-9))

(deftest tempo-round-trip
  (let [st {:cycle-dur 2.0 :anchor-time 10.0 :anchor-cycle 3}]
    (is (near? 10.0 (tempo/time-at-cycle st 3)))
    (is (near? 14.0 (tempo/time-at-cycle st 5)))
    (is (near? 11.0 (tempo/time-at-cycle st 3.5)))
    (doseq [x [0 3 4.25 17 100.5]]
      (is (near? x (tempo/cycle-at-time st (tempo/time-at-cycle st x))) (str x))))
  (testing "without anchor keys the anchor is 0.0 / 0 (unchanged behaviour)"
    (is (near? 62.0 (tempo/time-at-cycle {:cycle-dur 2.0} 31)))
    (is (near? 15.5 (tempo/cycle-at-time {:cycle-dur 2.0} 31.0)))))

(deftest retempo-playing-120-to-60
  (let [st  {:playing? true :cycle 31 :cycle-dur 2.0 :anchor-time 0.0 :anchor-cycle 0}
        st' (tempo/retempo st 61.9 4.0)]
    (is (near? 62.0 (tempo/time-at-cycle st 31)) "cycle 31 starts at 62 s at 120 BPM")
    (is (= 31 (:anchor-cycle st')) "the next unscheduled cycle becomes the anchor")
    (is (near? 62.0 (:anchor-time st')) "… at the time it would have started")
    (is (near? 4.0 (:cycle-dur st')))
    (is (near? 62.0 (tempo/time-at-cycle st' 31)) "no gap")
    (is (near? 66.0 (tempo/time-at-cycle st' 32)) "cycle 32 starts one 60-BPM bar later")))

(deftest retempo-playing-60-to-120
  (let [st  {:playing? true :cycle 16 :cycle-dur 4.0 :anchor-time 0.0 :anchor-cycle 0}
        st' (tempo/retempo st 63.9 2.0)]
    (is (= 16 (:anchor-cycle st')))
    (is (near? 64.0 (tempo/time-at-cycle st' 16)) "the next bar is not pulled into the past")
    (is (near? 66.0 (tempo/time-at-cycle st' 17)))
    (is (> (tempo/time-at-cycle st' 16) 63.9) "no catch-up burst: the next bar is still ahead")))

(deftest retempo-twice-keeps-time-continuous
  (let [st  {:playing? true :cycle 10 :cycle-dur 2.0}
        a   (tempo/retempo st 19.9 1.0)
        b   (tempo/retempo (assoc a :cycle 14) 23.9 0.5)]
    (is (near? 20.0 (tempo/time-at-cycle a 10)))
    (is (near? 24.0 (tempo/time-at-cycle a 14)))
    (is (near? 24.0 (tempo/time-at-cycle b 14)))
    (is (near? 24.5 (tempo/time-at-cycle b 15)))))

(deftest retempo-stopped
  (testing "stopped: re-anchor at the next whole cycle after now"
    (let [st  {:playing? false :cycle 0 :cycle-dur 2.0 :anchor-time 0.0 :anchor-cycle 0}
          st' (tempo/retempo st 7.0 1.0)]
      (is (= 4 (:anchor-cycle st')) "ceil(7.0 / 2.0) = 4")
      (is (near? 8.0 (:anchor-time st')))
      (is (near? 3.5 (tempo/cycle-at-time st' 7.5)) "continuous before the anchor too")
      (is (near? 5.0 (tempo/cycle-at-time st' 9.0)))))
  (testing "exactly on a bar line the anchor is that bar"
    (let [st' (tempo/retempo {:playing? false :cycle-dur 2.0} 8.0 1.0)]
      (is (= 4 (:anchor-cycle st')))
      (is (near? 8.0 (:anchor-time st'))))))

(deftest first-song-bar-with-anchor
  (testing "4-arity = anchor 0.0 / 0"
    (is (= (audio/first-song-bar 3 2.0 10.0 0.05)
           (audio/first-song-bar 3 2.0 10.0 0.05 0.0 0))))
  (testing "after a tempo change the first bar follows the anchor"
    ;; anchor: cycle 31 at 62 s, 4 s bars → cycle 32 at 66 s
    (is (= 32 (audio/first-song-bar 31 4.0 63.0 0.05 62.0 31)))
    (is (= 31 (audio/first-song-bar 31 4.0 61.0 0.05 62.0 31)))))

(deftest split-due-test
  (let [a {:at 1.0 :track :a :nodes [1]}
        b {:at 5.0 :track :a :nodes [2]}
        c {:at nil :track :b :nodes [3]}
        d {:at 2.0 :track :b :nodes [4]}]
    (is (= [[a d] [b c]] (audio/split-due [a b c d] 2.0)) "≤ now is due, nil never")
    (is (= [[] [a b c d]] (audio/split-due [a b c d] 0.5)))
    (is (= [[] []] (audio/split-due [] 3.0)))))

(deftest lru-evict-test
  (is (= [] (samples/lru-evict [] 100)))
  (is (= [] (samples/lru-evict [["a" 40] ["b" 60]] 100)) "exactly at the cap stays")
  (is (= ["a"] (samples/lru-evict [["a" 40] ["b" 60] ["c" 10]] 100)) "oldest first")
  (is (= ["a" "b"] (samples/lru-evict [["a" 40] ["b" 60] ["c" 90]] 100)))
  (is (= ["a" "b" "c"] (samples/lru-evict [["a" 40] ["b" 60] ["c" 200]] 100))
      "a single buffer above the cap is dropped too")
  (is (= (* 200 1024 1024) samples/buffer-cache-cap)))

(deftest project-relevant-change-test
  (is (false? (session/project-relevant-change? {:cycle 3 :cycle-dur 2.0}
                                                {:cycle 4 :cycle-dur 2.0}))
      "the per-bar counter does not trigger a save")
  (is (false? (session/project-relevant-change? {:tracks {}} {:tracks {:a 1}})))
  (is (true? (session/project-relevant-change? {:cycle-dur 2.0} {:cycle-dur 1.0}))
      "a tempo change does"))
