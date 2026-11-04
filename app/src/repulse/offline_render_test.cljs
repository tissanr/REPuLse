(ns repulse.offline-render-test
  "Phase EXP1 — pure parts of the offline export: frame counts, tween re-arming,
   FX planning and the fallback notice."
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.audio :as audio]
            [repulse.core :as core]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.offline-render :as offline-render]
            [repulse.song :as song]
            [repulse.song-transport :as st]))

(defn- eval-pat [code]
  (let [r (lisp/eval-string code (leval/make-env (fn [] nil) (fn [_] nil)))]
    (is (not (lisp/eval-error? r)) (:message r))
    (:result r)))

(deftest export-frames-test
  (testing "frames = round(secs × sr), shared by offline render and capture (E3)"
    (is (= 352800 (audio/export-frames 8.0 44100)))
    (is (= 329143 (audio/export-frames (* 4 (/ 240 140)) 48000))
        "(export 4) at 140 BPM / 48 kHz — the old (int …) truncation gave 329142")
    (is (= 176400 (audio/export-frames (* 4 1.0) 44100)))
    (is (= 192000 (audio/export-frames (* 4 1.0) 48000)))))

(deftest tween-helpers-test
  (let [pat (eval-pat "(->> (seq :c4) (amp (tween :linear 0 1 4)))")
        ts  (audio/tween-state-for pat 0.25)
        raw (:value (first (core/query-onsets pat {:start [0 1] :end [1 1]})))
        amp (fn [t] (:amp (audio/resolve-tweens raw ts t 1.0)))]
    (testing "tween-state-for finds the tweened param and arms it at start-time"
      (is (= #{:amp} (set (keys ts))))
      (is (= 0.25 (get-in ts [:amp :start-time]))))
    (testing "resolve-tweens interpolates from the armed start (cycle-dur 1.0)"
      (is (= 0.0 (amp 0.25)))
      (is (= 0.5 (amp 2.25)))
      (is (= 1.0 (amp 4.25)))
      (is (= 1.0 (amp 9.0)))
      (is (= 0.0 (amp 0.0)) "before the start the value is clamped to the start"))
    (testing "values without tweens pass through"
      (is (= :bd (audio/resolve-tweens :bd ts 1.0 1.0)))
      (is (= {:note :c4} (audio/resolve-tweens {:note :c4} {} 1.0 1.0)))))
  (testing "rearm-tweens sets every start time to t0 (decision 3)"
    (let [tw {:type :tween :curve :linear :start 0 :end 1 :duration-bars 4}
          st {:a {:amp {:tween tw :start-time 3.5} :pan {:tween tw :start-time 9.0}}
              :b {:amp {:tween tw :start-time 1.0}}}]
      (is (= {:a {:amp {:tween tw :start-time 12.0} :pan {:tween tw :start-time 12.0}}
              :b {:amp {:tween tw :start-time 12.0}}}
             (audio/rearm-tweens st 12.0)))
      (is (= {} (audio/rearm-tweens {} 12.0))))))

(defn- entry [nm ^js plugin & {:as more}]
  (merge {:name nm :plugin plugin :active? false :bypassed? false} more))

(deftest fx-plan-test
  (let [effect  #js {:type "effect" :name "reverb" :getParams (fn [] #js {})}
        visual  #js {:type "visual" :name "scope"}
        no-get  (doto #js {:type "effect" :name "third-party"}
                  (aset "getParams" (fn [] #js {}))
                  (aset "__repulseDefaultGetParams" true))
        entries [(entry "reverb" effect :active? true)
                 (entry "scope" visual)
                 (entry "third-party" no-get)]]
    (testing "a visual entry is skipped; an unused plugin without getParams is rebuilt"
      (let [plan (offline-render/fx-plan entries #{"reverb"})]
        (is (= ["scope"] (:skip plan)))
        (is (= ["reverb" "third-party"] (mapv :name (:build plan))))
        (is (nil? (:fail plan)))
        (is (nil? (offline-render/fx-plan-reason plan)))))
    (testing "a used plugin whose getParams is the installed default fails, naming it"
      (let [plan (offline-render/fx-plan entries #{"reverb" "third-party"})]
        (is (= {:plugin "third-party" :reason "no getParams"} (:fail plan)))
        (is (= "plugin \"third-party\" can't be rebuilt offline (no getParams)"
               (offline-render/fx-plan-reason plan)))
        (is (= "offline render unavailable (plugin \"third-party\" can't be rebuilt offline (no getParams)) — capturing in real time instead…"
               (st/fallback-notice (offline-render/fx-plan-reason plan))))))))

(deftest used-effects-test
  (let [pat (assoc (eval-pat "(seq :bd)")
                   :track-fx [{:name "delay" :params {"value" 0.25}}
                              {:name "oscilloscope" :params {}}])]
    (is (= #{"reverb" "delay" "oscilloscope"}
           (offline-render/used-effects [{:name "reverb" :active? true}
                                         {:name "filter" :active? false}]
                                        {:a pat})))))

(deftest job-frames-and-events-test
  (let [pat (eval-pat "(seq :bd :_ :hh :_)")]
    (testing ":cycles — n cycles, t = whole start × cycle-dur (rests pass through, as in the scheduler)"
      (let [job {:kind :cycles :tracks {:a pat} :n 2 :cycle-dur 1.0}]
        (is (= 96000 (offline-render/job-frames job 48000)))
        (is (= [[:a :bd 0] [:a :_ 0.25] [:a :hh 0.5] [:a :_ 0.75]
                [:a :bd 1] [:a :_ 1.25] [:a :hh 1.5] [:a :_ 1.75]]
               (mapv (fn [{:keys [track event t]}] [track (:value event) t])
                     (offline-render/job-events job))))))
    (testing ":song — one pass plus tail, song bar b queries cycle b"
      (let [s   (song/make-song [(song/make-section :a 2 [:a])] {:end :stop} [:a] nil)
            job {:kind :song :tracks {:a pat} :song s :tail 0.5 :cycle-dur 0.5}]
        (is (= 72000 (offline-render/job-frames job 48000)))
        (is (= [[:a :bd 0] [:a :_ 0.125] [:a :hh 0.25] [:a :_ 0.375]
                [:a :bd 0.5] [:a :_ 0.625] [:a :hh 0.75] [:a :_ 0.875]]
               (mapv (fn [{:keys [track event t]}] [track (:value event) t])
                     (offline-render/job-events job))))))))

(deftest synthetic-song-buffer-test
  (testing "E9: (export n) falls back to a one-section song of n bars"
    (let [pat (eval-pat "(seq :bd)")
          {:keys [tracks] s :song} (st/synthetic-song-buffer {:a pat :b pat} 3)]
      (is (= [:a :b] (mapv first tracks)))
      (is (= 3 (song/song-length s)))
      (is (= :stop (:end s)))
      (is (= [:a :b] (:tracks (first (:sections s))))))))
