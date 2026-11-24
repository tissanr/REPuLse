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

;;; ── Phase SONG2: automation and fills (R1–R14) ────────────────────

(def tracks-a
  {:kick  (core/seq* [:bd :bd :bd :bd])
   :hats  (core/fast 4 (core/pure :hh))
   :snare (core/seq* [:_ :sd :_ :sd])
   :bass  (core/pure :c2)})

(def known-a [:kick :hats :snare :bass])

(defn- build-song
  ([] (build-song [(song/make-automation :hats :amp (song/make-ramp :linear 0 1) {})
                   (song/make-fill :snare 1 (core/fast 2 (core/pure :sd)))
                   (song/make-fill :kick 1 :_)]))
  ([items]
   (song/make-song
    [(song/make-section :intro 2 [:kick])
     (song/make-section :build 4 [:kick :hats :bass] items)
     (song/make-section :drop 2 :all)]
    {} known-a hint)))

(def A (build-song))

(defn- approx= [a b] (< (Math/abs (- a b)) 0.01))

(defn- evs-of [song bar track]
  (filterv #(= track (:track %)) (song/song-onsets song tracks-a #{} bar)))

(defn- starts-of [evs] (mapv #(:start (:whole (:event %))) evs))
(defn- vals-of [evs] (mapv #(:value (:event %)) evs))

(deftest r1-r3-ramp-values
  (let [lin (song/make-ramp :linear 0 1)]
    (is (= [0 0.25 0.5] (mapv #(song/ramp-value lin %) [0 0.25 0.5]))))
  (let [e (song/make-ramp :exp 300 4000)]
    (is (approx= 573.27 (song/ramp-value e 0.25)))
    (is (approx= 1095.45 (song/ramp-value e 0.5)))
    (is (approx= 2093.27 (song/ramp-value e 0.75))))
  (let [s (song/make-ramp :sine 0 1)]
    (is (approx= 0.1464 (* 1 (song/ramp-value s 0.25))))
    (is (< (Math/abs (- 0.1464 (song/ramp-value s 0.25))) 0.0001))
    (is (< (Math/abs (- 0.5 (song/ramp-value s 0.5))) 1e-9))
    (is (< (Math/abs (- 0.8536 (song/ramp-value s 0.75))) 0.0001))))

(deftest r4-hats-fade-in
  (let [h (evs-of A 2 :hats)]
    (is (= [[2 1] [9 4] [5 2] [11 4]] (starts-of h)))
    (is (= [{:note :hh :amp 0} {:note :hh :amp 0.0625} {:note :hh :amp 0.125} {:note :hh :amp 0.1875}]
           (vals-of h)))))

(deftest r5-r6-automation-value
  (is (= [0.25 0.625 0.9375]
         (mapv #(song/automation-value A :hats :amp %) [[3 1] [9 2] [23 4]])))
  (is (nil? (song/automation-value A :hats :amp [6 1])))
  (is (nil? (song/automation-value A :bass :amp [3 1]))))

(deftest r7-seek-is-pure
  (let [alone  (song/song-onsets A tracks-a #{} 4)
        _      (doseq [b (range 4)] (song/song-onsets A tracks-a #{} b))
        after  (song/song-onsets A tracks-a #{} 4)]
    (is (= (mapv summary alone) (mapv summary after)))
    (is (seq alone))))

(deftest r8-last-build-bar-fills
  (let [on (fn [t] (evs-of A 5 t))]
    (is (empty? (on :kick)))
    (is (= [[5 1] [11 2]] (starts-of (on :snare))))
    (is (= [:sd :sd] (vals-of (on :snare))))
    (is (= [0.75 0.8125 0.875 0.9375] (mapv :amp (vals-of (on :hats)))))
    (is (= [[5 1]] (starts-of (on :bass))))
    (is (= [:c2] (vals-of (on :bass))))))

(deftest r9-fill-only-covers-last-bar
  (is (= 4 (count (evs-of A 4 :kick))))
  (is (empty? (evs-of A 4 :snare))))

(deftest r10-drop-is-plain
  (is (= [:hh :hh :hh :hh] (vals-of (evs-of A 6 :hats))))
  (is (= 4 (count (evs-of A 6 :kick))))
  (is (= [[25 4] [27 4]]
         (starts-of (remove #(= :_ (:value (:event %))) (evs-of A 6 :snare))))))

(deftest r11-op-mul
  (let [tr  {:kick (core/pure :bd) :hats (core/fast 4 (core/pure {:note :hh :amp 0.8})) :snare (core/pure :sd) :bass (core/pure :c2)}
        S1  (build-song [(song/make-automation :hats :amp (song/make-ramp :linear 0 1) {:op :mul})])
        evs (filterv #(= :hats (:track %)) (song/song-onsets S1 tr #{} 4))]
    (is (= {:note :hh :amp 0.4} (:value (:event (first evs)))))
    (is (= {:note :hh :amp 0.4} (song/automate-value S1 :hats {:note :hh :amp 0.8} [4 1])))))

(deftest r12-signal-is-section-local
  (let [curve (core/range-pat -1 1 (core/slow 4 core/saw))
        A'    (build-song [(song/make-automation :bass :pan curve {})])]
    (is (= [-1 -0.5 0]
           (mapv #(song/automation-value A' :bass :pan %) [[2 1] [3 1] [4 1]])))))

(deftest r13-bars-range
  (let [A' (build-song [(song/make-automation :hats :amp (song/make-ramp :linear 0 1) {:bars [2 4]})])]
    (is (= [nil 0 0.5]
           (mapv #(song/automation-value A' :hats :amp %) [[3 1] [4 1] [5 1]])))))

(deftest r14-loop
  (let [L  (assoc A :end :loop)
        a  (song/song-onsets L tracks-a #{} 2)
        b  (song/song-onsets L tracks-a #{} 10)
        sh (fn [r] (mapv (fn [x] [(:track x) (:value (:event x))]) r))]
    (is (= (sh a) (sh b)))
    (is (= (mapv #(core/rat+ % [8 1]) (starts-of a)) (starts-of b)))))

(deftest song2-mute-and-rests
  (testing "mute wins over a fill"
    (is (empty? (filter #(= :snare (:track %)) (song/song-onsets A tracks-a #{:snare} 5)))))
  (testing "rests are never automated (A12)"
    (let [tr  (assoc tracks-a :hats (core/seq* [:hh :_ :hh :_]))
          evs (filterv #(= :hats (:track %)) (song/song-onsets A tr #{} 3))]
      (is (some #(= :_ %) (vals-of evs)))
      (is (every? #(or (= :_ %) (map? %)) (vals-of evs)))
      (is (every? #(not (and (map? %) (= :_ (:note %)))) (vals-of evs))))))

(deftest song2-validation
  (let [err (fn [f] (try (f) nil (catch :default e (ex-message e))))
        r   (song/make-ramp :linear 0 1)]
    (is (= "ramp: unknown curve :log — use :linear, :exp or :sine" (err #(song/make-ramp :log 0 1))))
    (is (= "ramp :exp needs both values > 0, got 0 and 1 — use :linear or :sine"
           (err #(song/make-ramp :exp 0 1))))
    (is (= "automate: :op must be :set, :mul or :add, got :foo"
           (err #(song/make-automation :hats :amp 1 {:op :foo}))))
    (is (= "automate :hats :amp: expected a number, (ramp …) or a pattern, got \"x\""
           (err #(song/make-automation :hats :amp "x" {}))))
    (is (= "automate: :bars must be [from to] with whole numbers, got [1 \"a\"]"
           (err #(song/make-automation :hats :amp 1 {:bars [1 "a"]}))))
    (is (= "fill: needs one track name, got :all" (err #(song/make-fill :all 1 :_))))
    (is (= "fill :snare: bars must be a whole number ≥ 1, got 0" (err #(song/make-fill :snare 0 :_))))
    (is (= "automate :hats :amp: :bars [3 6] is outside section :build (4 bars) — use 0 ≤ from < to ≤ 4"
           (err #(song/make-section :build 4 [:hats]
                                    [(song/make-automation :hats :amp r {:bars [3 6]})]))))
    (is (= "fill :snare: 5 bars is longer than section :build (4 bars)"
           (err #(song/make-section :build 4 [:hats] [(song/make-fill :snare 5 :_)]))))
    (is (= "Two fills for :snare in section :build — use one (fill …) per track"
           (err #(song/make-section :build 4 [:hats]
                                    [(song/make-fill :snare 1 :_) (song/make-fill :snare 2 :_)]))))
    (is (= "section :build: unexpected argument 7 — after the tracks, sections take (automate …) and (fill …)"
           (err #(song/make-section :build 4 [:hats] [7]))))
    (is (= "Unknown track :hat in (automate …) in section :build — did you mean :hats?"
           (err #(build-song [(assoc (song/make-automation :hat :amp 1 {}) :source {:from 1 :to 2})]))))
    (is (= "Unknown track :snar in (fill …) in section :build — did you mean :snare?"
           (err #(build-song [(song/make-fill :snar 1 :_)]))))
    (is (= {:from 1 :to 2}
           (try (build-song [(assoc (song/make-automation :hat :amp 1 {}) :source {:from 1 :to 2})])
                (catch :default e (:item-source (ex-data e))))))
    (testing ":all is fine for automate"
      (is (some? (build-song [(song/make-automation :all :amp 0.5 {})]))))))

(deftest qa1-song-onsets-on-error
  (let [boom   (core/pattern (fn [_] (throw (ex-info "boom" {}))))
        tracks (assoc tracks-m :kick boom)
        errors (atom [])]
    (testing "with on-error, a throwing track adds no events and the others play"
      (is (= [:hats :hats :hats :hats]
             (mapv :track (song/song-onsets M tracks #{} 0
                                            (fn [t e] (swap! errors conj [t (ex-message e)]))))))
      (is (= [[:kick "boom"]] @errors)))
    (testing "without on-error the error propagates"
      (is (thrown-with-msg? js/Error #"boom" (song/song-onsets M tracks #{} 0))))))
