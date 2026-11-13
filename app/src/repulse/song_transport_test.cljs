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
            [repulse.env.builtins.export :as b-export]
            [repulse.env.builtins.midi :as b-midi]))

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

(deftest first-song-bar-honours-the-start-margin
  (testing "live start: next boundary unless it is closer than the margin"
    (is (= 4 (audio/first-song-bar 3 1.0 3.50 0.05)))
    (is (= 5 (audio/first-song-bar 3 1.0 3.97 0.05)))
    (is (= 3 (audio/first-song-bar 3 1.0 2.90 0.05)) "never before the current cycle"))
  (testing "capture start: a bar boundary 0.16 s ahead is skipped with a 0.5 s margin"
    (is (= 4 (audio/first-song-bar 3 1.0 3.84 0.05)))
    (is (= 5 (audio/first-song-bar 3 1.0 3.84 0.5))))
  (testing "short bars (640 BPM): the margin can skip more than one boundary"
    ;; now 4.40 s is in cycle 11; bars 12 (4.5 s) and 13 (4.875 s) are too close
    (is (= 14 (audio/first-song-bar 11 0.375 4.40 0.5)))))


;;; Phase SK1 — (export :track n), (export :stems …), stems helpers, midi-export grammar

(def ^:private usage-error
  "Error: usage — (export [cycles]), (export :track [cycles]), (export :stems …) or (export :song …)")

(deftest export-track-cycles-test
  (let [saved @audio/scheduler-state]
    (try
      (swap! audio/scheduler-state assoc :tracks {} :capture nil :cycle-dur 2.0)
      (reset! st/export-running? false)
      (is (= {:result "rendering :a — 8 cycles offline…"
              :pending {:kind :cycles :n 8 :track :a}}
             (run-export "(track :a (seq :bd)) (export :a 8)")))
      (is (= {:result "Error: export needs at least 1 cycle" :pending nil}
             (run-export "(track :a (seq :bd)) (export :a 0)")))
      (testing "the old silently-ignored shapes are usage errors now"
        (is (= {:result usage-error :pending nil}
               (run-export "(track :a (seq :bd)) (export 8 :a)")))
        (is (= {:result usage-error :pending nil}
               (run-export "(track :a (seq :bd)) (export :a 8 2)")))
        (is (= {:result usage-error :pending nil}
               (run-export "(track :a (seq :bd)) (export \"x\")"))))
      (testing ":realtime is still checked first"
        (is (= {:result "Error: :realtime only applies to (export :song)" :pending nil}
               (run-export "(track :a (seq :bd)) (export :a 8 :realtime)"))))
      (finally
        (reset! st/export-running? false)
        (reset! audio/scheduler-state saved)))))

(def ^:private two-tracks-and-song
  "(track :kick (seq :bd :sd :hh :oh))
   (track :bass (amp 0.5 (seq :c2 :_ :g2 :_)))
   (song (section :intro 2 [:kick]) (section :verse 4 :all) (section :outro 2 [:bass])) ")

(deftest export-stems-grammar-test
  (let [saved @audio/scheduler-state]
    (try
      (swap! audio/scheduler-state assoc :tracks {} :capture nil :cycle-dur 2.0)
      (reset! st/export-running? false)
      (testing "with a song: the song, default tail 2 s"
        (is (= {:result "rendering stems — 2 tracks, 8 bars, 18.0 s offline…"
                :pending {:kind :stems :n nil :tail 2}}
               (run-export (str two-tracks-and-song "(export :stems)"))))
        (is (= {:result "rendering stems — 2 tracks, 8 bars, 17.0 s offline…"
                :pending {:kind :stems :n nil :tail 1}}
               (run-export (str two-tracks-and-song "(export :stems :tail 1)")))))
      (testing "(export :stems n) is cycles, with or without a song"
        (is (= {:result "rendering stems — 2 tracks, 8 cycles offline…"
                :pending {:kind :stems :n 8 :tail 2}}
               (run-export (str two-tracks-and-song "(export :stems 8)"))))
        (is (= {:result "rendering stems — 2 tracks, 8 cycles offline…"
                :pending {:kind :stems :n 8 :tail 2}}
               (run-export "(track :a (seq :bd)) (track :b (seq :sd)) (export :stems 8)"))))
      (testing "without a song: 4 cycles of every track"
        (is (= {:result "rendering stems — 2 tracks, 4 cycles offline…"
                :pending {:kind :stems :n nil :tail 2}}
               (run-export "(track :a (seq :bd)) (track :b (seq :sd)) (export :stems)"))))
      (testing "errors"
        (is (= {:result "Error: no song — evaluate a buffer that ends with (song …) first" :pending nil}
               (run-export "(track :a (seq :bd)) (export :stems :tail 1)")))
        (is (= {:result "Error: :realtime only applies to (export :song)" :pending nil}
               (run-export (str two-tracks-and-song "(export :stems :realtime)"))))
        (is (= {:result "Error: (export :stems) takes a cycle count or :tail secs" :pending nil}
               (run-export (str two-tracks-and-song "(export :stems :foo)"))))
        (is (= {:result "Error: :tail must be a number of seconds between 0 and 30" :pending nil}
               (run-export (str two-tracks-and-song "(export :stems :tail 40)"))))
        (is (= {:result "Error: export needs at least 1 cycle" :pending nil}
               (run-export (str two-tracks-and-song "(export :stems 0)"))))
        (is (= {:result "Error: no active tracks to export" :pending nil}
               (run-export "(export :stems)")))
        (reset! st/export-running? true)
        (is (= {:result "Error: an export is already running — (stop) cancels it" :pending nil}
               (run-export (str two-tracks-and-song "(export :stems)"))))
        (reset! st/export-running? false))
      (finally
        (reset! st/export-running? false)
        (reset! audio/scheduler-state saved)))))

(deftest parse-stems-opts-test
  (is (= {} (b-export/parse-stems-opts [])))
  (is (= {:n 8} (b-export/parse-stems-opts [8])))
  (is (= {:n 2} (b-export/parse-stems-opts [2.5])))
  (is (= {:tail 0} (b-export/parse-stems-opts [:tail 0])))
  (is (= {:tail 30} (b-export/parse-stems-opts [:tail 30])))
  (doseq [opts [[:tail] [:tail 31] [:tail -1] [:tail "x"] [:tail 1 :x]]]
    (is (= {:error "Error: :tail must be a number of seconds between 0 and 30"}
           (b-export/parse-stems-opts opts)) (pr-str opts)))
  (doseq [opts [[:foo] [1 2] ["x"]]]
    (is (= {:error "Error: (export :stems) takes a cycle count or :tail secs"}
           (b-export/parse-stems-opts opts)) (pr-str opts)))
  (is (= {:error "Error: :realtime only applies to (export :song)"}
         (b-export/parse-stems-opts [:realtime])))
  (is (= {:error "Error: export needs at least 1 cycle"} (b-export/parse-stems-opts [0]))))

(deftest stem-tracks-test
  (let [r (run "(track :k (seq :bd)) (track :bass (seq :c2)) (track :unused (seq :sd))
                (song (section :a 2 [:k]) (section :b 2 [:k :bass]))")
        s (:result r)]
    (is (not (lisp/eval-error? r)) (:message r))
    (is (= [:k :bass] (st/stem-tracks s)))))

(deftest stem-filenames-test
  (is (= ["repulse-kick.wav" "repulse-bass.wav" "repulse-kick-2.wav" "repulse-a_b.wav"]
         (st/stem-filenames [:kick :bass :drums/kick :a?b])))
  (is (= ["repulse-x.wav" "repulse-x-2.wav" "repulse-x-3.wav"]
         (st/stem-filenames [:x :x :x]))))

(deftest current-song-buffer-test
  (is (nil? (st/current-song-buffer)))
  (let [s (:result (run (str two-tracks-and-song)))]
    (is (= s (:song (st/current-song-buffer))) "this evaluation's song")
    (is (= [:kick :bass] (mapv first (:tracks (st/current-song-buffer)))))))

(defn- run-midi-args [code]
  (st/reset-pending!)
  (reset! st/song-buffer nil)
  (let [env (merge (make-env) (b-midi/make-builtins {:set-output! (fn [_ _])}))
        r   (lisp/eval-string code env)]
    (if (lisp/eval-error? r) (:message r) (:result r))))

(deftest midi-export-grammar-test
  (let [saved @audio/scheduler-state]
    (try
      (swap! audio/scheduler-state assoc :tracks {} :cycle-dur 2.0)
      (testing "parse-midi-args"
        (is (= {:target :song :n nil :drum-map {}} (b-midi/parse-midi-args [:song])))
        (is (= {:target :song :n nil :drum-map {:x 60}}
               (b-midi/parse-midi-args [:song :drum-map {:x 60}])))
        (is (= {:target :bass :n 4 :drum-map {}} (b-midi/parse-midi-args [:bass])))
        (is (= {:target :bass :n 8 :drum-map {}} (b-midi/parse-midi-args [:bass 8])))
        (is (= {:target :bass :n 4 :drum-map {:x 1}} (b-midi/parse-midi-args [:bass :drum-map {:x 1}])))
        (is (= {:target :bass :n 8 :drum-map {:x 1}} (b-midi/parse-midi-args [:bass 8 :drum-map {:x 1}]))))
      (testing "messages"
        (let [usage "Error: usage — (midi-export :track [cycles]) or (midi-export :song), optionally followed by :drum-map {…}"]
          (is (= usage (run-midi-args "(midi-export)")))
          (is (= usage (run-midi-args "(midi-export 4)")))
          (is (= usage (run-midi-args "(midi-export :song 4)")))
          (is (= usage (run-midi-args "(midi-export :a 4 5)")))
          (is (= usage (run-midi-args "(midi-export :a :drum-map)"))))
        (is (= "Error: midi-export needs at least 1 cycle" (run-midi-args "(midi-export :a 0)")))
        (is (= "Error: :drum-map must map keywords to MIDI notes 0–127, e.g. {:perc 60}"
               (run-midi-args "(midi-export :a :drum-map {:x 200})")))
        (is (= "Error: :drum-map must map keywords to MIDI notes 0–127, e.g. {:perc 60}"
               (run-midi-args "(midi-export :a :drum-map 5)")))
        (is (= "Error: :drum-map must map keywords to MIDI notes 0–127, e.g. {:perc 60}"
               (run-midi-args "(midi-export :a :drum-map {:x 60.5})")))
        (is (= "Error: no song — evaluate a buffer that ends with (song …) first"
               (run-midi-args "(midi-export :song)")))
        (is (= "Error: no track :zz" (run-midi-args "(midi-export :zz)"))))
      (finally
        (reset! audio/scheduler-state saved)))))
