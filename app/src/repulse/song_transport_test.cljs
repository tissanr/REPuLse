(ns repulse.song-transport-test
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [repulse.core :as core]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.song :as song]
            [repulse.song-transport :as st]
            [repulse.audio :as audio]
            [repulse.ui.status-model :as status-model]
            [repulse.env.builtins.song :as b-song]
            [repulse.env.builtins.export :as b-export]))

(defn- make-env []
  (merge (leval/make-env (fn [] nil) (fn [_] nil))
         {"track" (fn [n p]
                    (st/collect-track! (leval/unwrap n) (leval/unwrap p) (leval/source-of n))
                    nil)}
         (b-song/make-builtins {})))

(defn- run [code]
  (st/reset-pending!)
  (lisp/eval-string code (make-env)))

(use-fixtures :each
  {:before (fn [] (st/reset-pending!) (reset! st/song-buffer nil))
   :after  (fn [] (st/reset-pending!) (reset! st/song-buffer nil))})

(def tracks-s
  "(track :kick  (seq :bd :_ :bd :_))
   (track :snare (seq :_ :sd :_ :sd))
   (track :hats  (fast 2 (seq :hh :_)))
   (track :bass  (synth :saw (seq :c2 :_ :g2 :_)))\n")

(def song-s
  "(song (section :intro  4 [:kick :hats])
         (section :verse  8 [:kick :snare :bass])
         (section :chorus 8 :all)
         (section :outro  4 [:hats]))")

(deftest song-registers-and-resolves
  (let [r (run (str tracks-s song-s))
        s (:result r)]
    (is (not (lisp/eval-error? r)) (:message r))
    (is (song/song? s))
    (is (= s @st/pending-song))
    (is (= 24 (song/song-length s)))
    (is (= [:kick :snare :hats :bass] (mapv first @st/pending-tracks)))
    (is (= [:kick :snare :hats :bass] (:tracks (nth (:sections s) 2))))
    (is (every? core/pattern? (map second @st/pending-tracks))))
  (testing ":end option"
    (is (= :loop (:end (:result (run (str tracks-s "(song (section :a 1 [:kick]) :end :loop)"))))))))

(deftest song-errors
  (testing "unknown track — fuzzy suggestion and squiggle on the track keyword"
    (let [code (str tracks-s "(song (section :verse 8 [:kick :snar]))")
          r    (run code)]
      (is (lisp/eval-error? r))
      (is (= "Unknown track :snar in section :verse — did you mean :snare?" (:message r)))
      (is (= ":snar" (subs code (:from (:source r)) (:to (:source r)))))))
  (testing "unknown track without a close match lists the known tracks"
    (is (= "Unknown track :zzzzzzzz in section :a — tracks defined above the song: :kick :snare :hats :bass"
           (:message (run (str tracks-s "(song (section :a 1 [:zzzzzzzz]))"))))))
  (testing "duplicate section name"
    (is (= "Duplicate section name :a — section names must be unique in a song"
           (:message (run (str tracks-s "(song (section :a 1 [:kick]) (section :a 1 [:hats]))"))))))
  (testing "second song"
    (is (= "Only one (song …) per buffer"
           (:message (run (str tracks-s "(song (section :a 1 [:kick])) (song (section :b 1 [:kick]))"))))))
  (testing "track after the song"
    (let [code (str tracks-s "(song (section :a 1 [:kick])) (track :x (seq :bd))")
          r    (run code)]
      (is (= "track :x is defined after (song …) — define tracks above the song" (:message r)))
      (is (= ":x" (subs code (:from (:source r)) (:to (:source r)))))))
  (testing "duplicate track name"
    (is (= "Duplicate track name :kick — each track must have a unique name in the buffer"
           (:message (run "(track :kick (seq :bd)) (track :kick (seq :sd))")))))
  (testing "option errors"
    (is (= "song needs at least one (section …)" (:message (run "(song)"))))
    (is (= "song needs at least one (section …)" (:message (run "(song :end :stop)"))))
    (is (= "song: expected (section …) or an option, got 5"
           (:message (run (str tracks-s "(song (section :a 1 [:kick]) 5)")))))
    (is (= "song: unknown option :foo — expected :end"
           (:message (run (str tracks-s "(song (section :a 1 [:kick]) :foo 1)")))))
    (is (= "song: :end must be :stop or :loop, got :fade"
           (:message (run (str tracks-s "(song (section :a 1 [:kick]) :end :fade)")))))))

(deftest play-from-test
  (testing "named section in the same buffer"
    (let [r (run (str tracks-s song-s " (play-from :chorus)"))]
      (is (= "=> playing from :chorus (bar 12)" (:result r)))
      (is (= 12 @st/pending-play-from))))
  (testing "bar number"
    (let [r (run (str tracks-s song-s " (play-from 17)"))]
      (is (= "=> playing from bar 17" (:result r)))
      (is (= 17 @st/pending-play-from))))
  (testing "from a later evaluation (command bar) the last activated song is used"
    (let [s (:result (run (str tracks-s song-s)))]
      (reset! st/song-buffer {:tracks @st/pending-tracks :song s})
      (is (= "=> playing from :outro (bar 20)" (:result (run "(play-from :outro)"))))))
  (testing "errors"
    (reset! st/song-buffer nil)
    (is (= "play-from: no song — evaluate a buffer that ends with (song …) first"
           (:message (run "(play-from :chorus)"))))
    (let [code (str tracks-s song-s " (play-from :chrous)")
          r    (run code)]
      (is (= "Unknown section :chrous — did you mean :chorus?" (:message r)))
      (is (= ":chrous" (subs code (:from (:source r)) (:to (:source r))))))
    (is (= "Unknown section :zzzzzzzz — sections: :intro :verse :chorus :outro"
           (:message (run (str tracks-s song-s " (play-from :zzzzzzzz)")))))
    (is (= "play-from: bar 30 is outside the song (0–23)"
           (:message (run (str tracks-s song-s " (play-from 30)")))))
    (is (= "play-from: bar -1 is outside the song (0–23)"
           (:message (run (str tracks-s song-s " (play-from -1)")))))
    (is (= "play-from: expected a section name or a bar number"
           (:message (run (str tracks-s song-s " (play-from 1.5)")))))))

(deftest reset-pending-test
  (run (str tracks-s song-s " (play-from 3)"))
  (st/reset-pending!)
  (is (= [] @st/pending-tracks))
  (is (nil? @st/pending-song))
  (is (nil? @st/pending-play-from))
  (is (nil? @st/pending-export)))

;;; Phase WS1 — cue-bar! (status-head segment click / inspector ▶ cue)

(deftest cue-bar-test
  (let [saved @audio/scheduler-state]
    (try
      (testing "playing with a song: the cued bar becomes the next scheduled bar"
        (let [s (:result (run (str tracks-s song-s)))]
          (swap! audio/scheduler-state assoc
                 :playing? true :cycle 40
                 :song {:song s :origin 20 :start-bar 0})
          (st/cue-bar! (status-model/segment-cue-bar s 2))
          (let [st' @audio/scheduler-state]
            (is (= {:song s :origin 28 :start-bar 12} (:song st')))
            (is (= 12 (- (:cycle st') (get-in st' [:song :origin]))))
            (is (= 40 (:cycle st')) "the already scheduled bar is not touched"))))
      (testing "stopped without a song buffer: nothing happens"
        (swap! audio/scheduler-state assoc :playing? false :song nil)
        (reset! st/song-buffer nil)
        (is (nil? (st/cue-bar! 12))))
      (finally
        (reset! audio/scheduler-state saved)))))


;;; Phase EXP1 — export argument grammar (E13) → pending-export

(defn- export-env [out]
  (merge (make-env) (b-export/make-builtins {:set-output! out})))

(defn- run-export [code]
  (st/reset-pending!)
  (let [out (fn [_ _] nil)
        r   (lisp/eval-string code (export-env out))]
    {:result (if (lisp/eval-error? r) (:message r) (:result r))
     :pending (some-> @st/pending-export (dissoc :set-output!))}))

(deftest export-grammar-test
  (let [saved @audio/scheduler-state]
    (try
      (swap! audio/scheduler-state assoc :tracks {} :capture nil :cycle-dur 2.0)
      (reset! st/export-running? false)
      (testing "(export n) / (export :track) render offline after evaluation"
        (is (= {:result "rendering all tracks — 4 cycles offline…"
                :pending {:kind :cycles :n 4 :track nil}}
               (run-export "(track :a (seq :bd)) (export 4)")))
        (is (= {:result "rendering all tracks — 4 cycles offline…"
                :pending {:kind :cycles :n 4 :track nil}}
               (run-export "(track :a (seq :bd)) (export)")))
        (is (= {:result "rendering :a — 4 cycles offline…"
                :pending {:kind :cycles :n 4 :track :a}}
               (run-export "(track :a (seq :bd)) (track :b (seq :sd)) (export :a)")))
        (is (= {:result "rendering all tracks — 2 cycles offline…"
                :pending {:kind :cycles :n 2 :track nil}}
               (run-export "(track :a (seq :bd)) (export 2.5)"))
            "fractional cycle counts are truncated, as before")
        (is (= "rendering :a — 4 cycles offline…"
               (:result (run-export "(track :a (seq :bd)) (export :a)")))))
      (testing "(export n) errors"
        (is (= {:result "Error: no track :zz" :pending nil}
               (run-export "(track :a (seq :bd)) (export :zz)")))
        (is (= {:result "Error: no active tracks to export" :pending nil}
               (run-export "(export 4)")))
        (is (= {:result "Error: export needs at least 1 cycle" :pending nil}
               (run-export "(track :a (seq :bd)) (export 0)")))
        (is (= {:result "Error: :realtime only applies to (export :song)" :pending nil}
               (run-export "(track :a (seq :bd)) (export 4 :realtime)")))
        (is (= {:result "Error: :realtime only applies to (export :song)" :pending nil}
               (run-export "(track :a (seq :bd)) (export :realtime)"))))
      (testing "(export :song …) renders offline by default"
        (let [song-src "(track :a (seq :bd)) (song (section :a 1 [:a])) "]
          (is (= {:result "rendering song — 1 bars, 4.0 s offline…"
                  :pending {:kind :song :tail 2 :realtime? false}}
                 (run-export (str song-src "(export :song)"))))
          (is (= {:result "rendering song — 1 bars, 2.5 s offline…"
                  :pending {:kind :song :tail 0.5 :realtime? false}}
                 (run-export (str song-src "(export :song :tail 0.5)"))))
          (is (= {:result "Error: :tail must be a number of seconds between 0 and 30" :pending nil}
                 (run-export (str song-src "(export :song :tail 40)"))))
          (is (= {:result "Error: :tail must be a number of seconds between 0 and 30" :pending nil}
                 (run-export (str song-src "(export :song :fade)"))))
          (testing ":realtime needs AudioWorklet (absent under node)"
            (is (= {:result "Error: (export :song) needs AudioWorklet support for real-time capture"
                    :pending nil}
                   (run-export (str song-src "(export :song :realtime)")))))
          (testing "one export at a time (E14)"
            (reset! st/export-running? true)
            (is (= {:result "Error: an export is already running — (stop) cancels it" :pending nil}
                   (run-export (str song-src "(export :song)"))))
            (is (= {:result "Error: an export is already running — (stop) cancels it" :pending nil}
                   (run-export (str song-src "(export 2)"))))
            (reset! st/export-running? false))))
      (testing "(export :song) without a song"
        (is (= {:result "Error: no song — evaluate a buffer that ends with (song …) first" :pending nil}
               (run-export "(export :song)"))))
      (finally
        (reset! st/export-running? false)
        (reset! audio/scheduler-state saved)))))

(deftest parse-song-opts-test
  (testing "every valid E13 form"
    (is (= {:tail 2 :realtime? false} (b-export/parse-song-opts [])))
    (is (= {:tail 1 :realtime? false} (b-export/parse-song-opts [:tail 1])))
    (is (= {:tail 2 :realtime? true} (b-export/parse-song-opts [:realtime])))
    (is (= {:tail 1 :realtime? true} (b-export/parse-song-opts [:tail 1 :realtime])))
    (is (= {:tail 1 :realtime? true} (b-export/parse-song-opts [:realtime :tail 1]))))
  (testing "invalid forms"
    (doseq [opts [[:tail] [:tail -1] [:tail 31] [:tail "x"] [:realtime :realtime]
                  [:tail 1 :tail 2] [:foo] [3]]]
      (is (= {:error "Error: :tail must be a number of seconds between 0 and 30"}
             (b-export/parse-song-opts opts))
          (pr-str opts)))))

(deftest cycles-labels-test
  (is (= "all tracks" (st/cycles-label nil)))
  (is (= ":kick" (st/cycles-label :kick)))
  (is (= "repulse-all-4cycles.wav" (st/cycles-filename nil 4)))
  (is (= "repulse-kick-2cycles.wav" (st/cycles-filename :kick 2))))
