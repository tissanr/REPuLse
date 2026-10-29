(ns repulse.song-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [repulse.core :as core]
            [repulse.song :as song]))

;;; Fixtures — reference song S, S' and mini song M from PROMPTS/PHASE-SONG1.md

(defn- hint
  "Tiny stand-in for repulse.lisp.eval/typo-hint (core may not depend on lisp)."
  [n known]
  (some #(when (and (= (count %) (inc (count n)))
                    (.startsWith % n)) %)
        known))

(def tracks-s
  {:kick  (core/seq* [:bd :_ :bd :_])
   :snare (core/seq* [:_ :sd :_ :sd])
   :hats  (core/fast 2 (core/seq* [:hh :_] [{:from 1 :to 3} {:from 4 :to 6}]))
   :bass  (core/seq* [{:note :c2 :synth :saw} {:note :_ :synth :saw}
                      {:note :g2 :synth :saw} {:note :_ :synth :saw}])})

(def known-s [:kick :snare :hats :bass])

(defn- sec [n bars tracks] (song/make-section n bars tracks))

(def S
  (song/make-song [(sec :intro 4 [:kick :hats])
                   (sec :verse 8 [:kick :snare :bass])
                   (sec :chorus 8 :all)
                   (sec :outro 4 [:hats])]
                  {} known-s hint))

(def S-loop (assoc S :end :loop))

(def S'
  (song/make-song [(sec :intro 4 [:kick :hats])
                   (sec :verse 8 [:kick :snare :bass])]
                  {} known-s hint))

(def tracks-m
  {:kick (core/seq* [:bd :_ :bd :_])
   :hats (core/fast 2 (core/seq* [:hh :_]))})

(def M
  (song/make-song [(sec :a 1 [:kick :hats]) (sec :b 1 [:hats])]
                  {} [:kick :hats] hint))

(def M-loop
  (song/make-song [(sec :a 1 [:kick :hats]) (sec :b 1 [:hats])]
                  {:end :loop} [:kick :hats] hint))

(defn- summary
  "[track value [ws we]] with rational whole bounds."
  [{:keys [track event]}]
  [track (:value event) [(:start (:whole event)) (:end (:whole event))]])

;;; Tests

(deftest song-length-and-timing
  (is (= 24 (song/song-length S)))
  (is (= 0  (song/section-start-bar S :intro)))
  (is (= 4  (song/section-start-bar S :verse)))
  (is (= 12 (song/section-start-bar S :chorus)))
  (is (= 20 (song/section-start-bar S :outro)))
  (is (nil? (song/section-start-bar S :bridge)))
  (is (= 48 (song/song-duration-secs S 120)))
  (is (= 64 (song/song-duration-secs S 90)))
  (is (= 1  (song/song-duration-secs M 480))))

(deftest section-and-track-activation
  (is (= [:intro :intro :verse :chorus :outro :outro]
         (mapv #(:name (song/section-at-bar S %)) [0 3 4 12 20 23])))
  (is (nil? (song/section-at-bar S 24)))
  (is (nil? (song/section-at-bar S -1)))
  (is (= #{:kick :hats} (song/active-tracks-at-bar S 3)))
  (is (= #{:kick :snare :hats :bass} (song/active-tracks-at-bar S 13)))
  (is (= #{:hats} (song/active-tracks-at-bar S 21)))
  (is (= #{} (song/active-tracks-at-bar S 24)))
  (is (= [:kick :snare :hats :bass] (:tracks (nth (:sections S) 2))))
  (is (true? (:all? (nth (:sections S) 2))))
  (testing "song value shape"
    (is (song/song? S))
    (is (= :stop (:end S)))
    (is (= known-s (:tracks S)))
    (is (= [[0 4] [4 12] [12 20] [20 24]]
           (mapv (juxt :start :end) (:sections S))))
    (is (song/section? (sec :x 1 [:kick])))
    (is (not (song/song? (sec :x 1 [:kick]))))))

(deftest wrap-bar-test
  (is (= 23 (song/wrap-bar S 23)))
  (is (nil? (song/wrap-bar S 24)))
  (is (nil? (song/wrap-bar S -1)))
  (is (= 0 (song/wrap-bar S-loop 24)))
  (is (= 2 (song/wrap-bar S-loop 50)))
  (is (nil? (song/wrap-bar S-loop -1))))

(deftest position-after-reeval-test
  (is (= 13 (song/position-after-reeval S 13)))
  (is (= 4  (song/position-after-reeval S' 13)))
  (is (= 4  (song/position-after-reeval S' 12)))
  (is (= 11 (song/position-after-reeval S' 11)))
  (is (= 20 (song/position-after-reeval S 30)))
  (is (= -1 (song/position-after-reeval S -1))))

(deftest song-status-test
  (is (= {:bar 13 :total 24 :section :chorus :section-bar 1 :section-bars 8}
         (song/song-status S 13)))
  (is (nil? (song/song-status S 24)))
  (is (= :intro (:section (song/song-status S-loop 24)))))

(deftest validation-test
  (testing "unknown track with fuzzy hint"
    (let [e (try (song/make-song [(sec :verse 8 [:kick :snar])] {} known-s hint)
                 nil (catch :default e e))]
      (is (= "Unknown track :snar in section :verse — did you mean :snare?" (ex-message e)))
      (is (= :verse (:section (ex-data e))))
      (is (= :snar (:track (ex-data e))))
      (is (= :song-error (:type (ex-data e))))))
  (testing "duplicate section name"
    (is (thrown-with-msg? js/Error
                          #"^Duplicate section name :verse — section names must be unique in a song$"
                          (song/make-song [(sec :verse 8 [:kick]) (sec :verse 4 [:kick])]
                                          {} known-s hint))))
  (testing "section length must be whole bars ≥ 1"
    (is (thrown-with-msg? js/Error
                          #"^section :intro: length must be a whole number of bars ≥ 1, got 1/2$"
                          (song/make-section :intro [1 2] [:kick])))
    (is (thrown-with-msg? js/Error
                          #"^section :intro: length must be a whole number of bars ≥ 1, got 0$"
                          (song/make-section :intro 0 [:kick])))
    (is (thrown-with-msg? js/Error
                          #"^section :intro: length must be a whole number of bars ≥ 1, got 1.5$"
                          (song/make-section :intro 1.5 [:kick]))))
  (testing "tracks must be :all or a keyword vector; duplicates removed"
    (is (thrown? js/Error (song/make-section :intro 4 :kick)))
    (is (= [:kick :hats] (:tracks (song/make-section :intro 4 [:kick :hats :kick])))))
  (testing "bad :end option"
    (is (thrown-with-msg? js/Error #"^song: :end must be :stop or :loop, got :fade$"
                          (song/make-song [(sec :a 1 [:kick])] {:end :fade} known-s hint)))))

(deftest song-onsets-mini-song
  (testing "bar 1 (:b) has only hats, rests kept"
    (is (= [[:hats :hh [[1 1] [5 4]]]
            [:hats :_  [[5 4] [3 2]]]
            [:hats :hh [[3 2] [7 4]]]
            [:hats :_  [[7 4] [2 1]]]]
           (mapv summary (song/song-onsets M tracks-m #{} 1)))))
  (testing ":end :stop — nothing after the last bar, nothing before bar 0"
    (is (= [] (song/song-onsets M tracks-m #{} 2)))
    (is (= [] (song/song-onsets M tracks-m #{} -1))))
  (testing ":end :loop — bar 2 equals bar 0 shifted by 2"
    (let [evs (mapv summary (song/song-onsets M-loop tracks-m #{} 2))]
      (is (= [[:kick :bd [[2 1] [9 4]]]
              [:kick :_  [[9 4] [5 2]]]
              [:kick :bd [[5 2] [11 4]]]
              [:kick :_  [[11 4] [3 1]]]
              [:hats :hh [[2 1] [9 4]]]
              [:hats :_  [[9 4] [5 2]]]
              [:hats :hh [[5 2] [11 4]]]
              [:hats :_  [[11 4] [3 1]]]]
             evs))
      (is (= (mapv (fn [[t v [s e]]] [t v [(core/rat+ s [2 1]) (core/rat+ e [2 1])]])
                   (mapv summary (song/song-onsets M-loop tracks-m #{} 0)))
             evs))))
  (testing "mute always wins"
    (let [evs (song/song-onsets M tracks-m #{:kick} 0)]
      (is (= 4 (count evs)))
      (is (every? #(= :hats (:track %)) evs))))
  (testing "parts are shifted too"
    (let [ev (:event (first (song/song-onsets M-loop tracks-m #{} 2)))]
      (is (= (:whole ev) (:part ev))))))

(deftest song-onsets-outro
  (doseq [b (range 20 24)]
    (let [evs (song/song-onsets S tracks-s #{} b)]
      (is (seq evs))
      (is (every? #(= :hats (:track %)) evs) (str "bar " b))))
  (testing ":source survives the shift"
    (let [evs (song/song-onsets S-loop tracks-s #{} 45)]   ; second pass, song bar 21
      (is (every? #(= :hats (:track %)) evs))
      (is (= {:from 1 :to 3} (:source (:event (first evs)))))
      (is (= [[45 1] [181 4]] ((juxt :start :end) (:whole (:event (first evs))))))))
  (testing "chorus has every track, in song order"
    (is (= [:kick :snare :hats :bass]
           (distinct (map :track (song/song-onsets S tracks-s #{} 13)))))))
