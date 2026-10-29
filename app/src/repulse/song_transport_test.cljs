(ns repulse.song-transport-test
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [repulse.core :as core]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.song :as song]
            [repulse.song-transport :as st]
            [repulse.env.builtins.song :as b-song]))

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
