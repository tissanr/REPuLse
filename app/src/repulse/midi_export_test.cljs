(ns repulse.midi-export-test
  "Phase SK1 — Standard MIDI File export: velocity, tempo, VLQ, GM drum map,
   exact ticks, format-1 byte vectors for song S and the regression cases."
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [repulse.audio :as audio]
            [repulse.core :as core]
            [repulse.download :as download]
            [repulse.env.builtins.midi :as b-midi]
            [repulse.env.builtins.song :as b-song]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.midi :as midi]
            [repulse.midi-export :as mx]
            [repulse.song-transport :as st]))

;;; ── helpers ────────────────────────────────────────────────────────────

(defn- hex [bytes]
  (str/join " " (map #(.toUpperCase (.padStart (.toString % 16) 2 "0"))
                     (array-seq bytes))))

(defn- hx
  "Normalise a spec vector (whitespace-separated hex) for comparison."
  [& parts]
  (str/join " " (remove str/blank? (str/split (str/join " " parts) #"\s+"))))

(defn- bytes-of [^js u8 from to] (.slice u8 from to))

(def ^:private captured (atom nil))

(defn- make-env [out]
  (merge (leval/make-env (fn [] nil)
                         (fn [b] (swap! audio/scheduler-state assoc :cycle-dur (/ 240.0 b)) nil))
         {"track" (fn [n p]
                    (st/collect-track! (leval/unwrap n) (leval/unwrap p) (leval/source-of n))
                    nil)}
         (b-song/make-builtins {})
         (b-midi/make-builtins {:set-output! out})))

(defn- run-midi
  "Evaluate code (cycle-dur 2.0 = 120 BPM unless the code sets (bpm …)); the
   download is captured instead of clicked."
  [code]
  (st/reset-pending!)
  (reset! st/song-buffer nil)
  (reset! captured nil)
  (swap! audio/scheduler-state assoc :tracks {} :cycle-dur 2.0)
  (with-redefs [download/download-parts!
                (fn [parts mime filename]
                  (reset! captured {:bytes (aget parts 0) :mime mime :name filename}))]
    (let [r (lisp/eval-string code (make-env (fn [_ _] nil)))]
      {:result (if (lisp/eval-error? r) (:message r) (:result r))
       :bytes  (:bytes @captured)
       :name   (:name @captured)
       :mime   (:mime @captured)})))

(use-fixtures :each
  {:before (fn [] (st/reset-pending!) (reset! st/song-buffer nil))
   :after  (fn [] (st/reset-pending!) (reset! st/song-buffer nil))})

(defn- note-ons
  "Ticks of every note-on (status 9n, velocity > 0) in the note track `idx`
   (1-based, after the conductor), read back from the file bytes."
  [^js bytes idx]
  (let [;; walk the chunks: header (14) then MTrk chunks
        chunks (loop [pos 14 acc []]
                 (if (< pos (.-length bytes))
                   (let [len (+ (bit-shift-left (aget bytes (+ pos 4)) 24)
                                (bit-shift-left (aget bytes (+ pos 5)) 16)
                                (bit-shift-left (aget bytes (+ pos 6)) 8)
                                (aget bytes (+ pos 7)))]
                     (recur (+ pos 8 len) (conj acc [(+ pos 8) (+ pos 8 len)])))
                   acc))
        [from to] (nth chunks idx)]
    (loop [pos from tick 0 acc []]
      (if (< pos to)
        (let [[delta pos'] (loop [p pos v 0]
                             (let [b (aget bytes p)
                                   v' (+ (* v 128) (bit-and b 0x7F))]
                               (if (>= b 0x80) (recur (inc p) v') [v' (inc p)])))
              tick' (+ tick delta)
              status (aget bytes pos')]
          (cond
            (= status 0xFF)
            (let [len (aget bytes (+ pos' 2))]
              (recur (+ pos' 3 len) tick' acc))
            (= 0x90 (bit-and status 0xF0))
            (recur (+ pos' 3) tick' (conj acc tick'))
            :else
            (recur (+ pos' 3) tick' acc)))
        acc))))

;;; ── velocity / tempo / VLQ ─────────────────────────────────────────────

(deftest amp->velocity-test
  (doseq [[amp v] [[1.0 127] [0.5 64] [0.25 32] [0.1 13] [0.0 1] [-1 1] [2.0 127]
                   [nil 127] [:x 127] [js/NaN 127]]]
    (is (= v (midi/amp->velocity amp)) (str "amp " amp))))

(deftest tempo-us-test
  (is (= 500000 (midi/tempo-us 2.0)))
  (is (= 428571 (midi/tempo-us (/ 240.0 140))))
  (is (= 666667 (midi/tempo-us (/ 240.0 90)) ) "rounded, not truncated (old code: 666 666)")
  (is (= 1000000 (midi/tempo-us 4.0)))
  (testing "as the tempo meta event of the conductor track"
    (doseq [[cd tempo] [[2.0 "07 A1 20"] [(/ 240.0 140) "06 8A 1B"]
                        [(/ 240.0 90) "0A 2C 2B"] [4.0 "0F 42 40"]]]
      (let [f (midi/midi-file {:cycle-dur cd :end-tick 0 :markers [] :tracks []})]
        (is (= (hx "FF 51 03" tempo) (hex (bytes-of f 31 37)))
            (str "cycle-dur " cd))))))

(deftest vlq-test
  (doseq [[ticks enc] [[0 "00"] [127 "7F"] [128 "81 00"] [480 "83 60"] [1920 "8F 00"]
                       [3840 "9E 00"] [7680 "BC 00"] [0x0FFFFFFF "FF FF FF 7F"]]]
    (let [f   (midi/midi-file {:cycle-dur 2.0 :end-tick ticks :markers [] :tracks []})
          ;; conductor ends  <vlq> FF 2F 00
          n   (.-length f)
          end (hex (bytes-of f (- n (+ 3 (count (str/split enc #" ")))) n))]
      (is (= (hx enc "FF 2F 00") end) (str ticks)))))

;;; ── event → note ───────────────────────────────────────────────────────

(deftest event-note-test
  (testing "GM drum map (K13) — channel 10 drums"
    (doseq [[kw n] [[:bd 36] [:rim 37] [:sd 38] [:cp 39] [:hh 42] [:lt 45] [:oh 46]
                    [:mt 47] [:cr 49] [:ht 50] [:rd 51] [:tb 54] [:cb 56] [:sh 70]]]
      (is (= {:kind :drum :note n} (mx/event-note kw {})) (str kw))
      (is (= {:kind :drum :note n} (mx/event-note kw nil)) (str kw)))
    (is (= {:kind :drum :note 38} (mx/event-note {:note :sd :amp 0.5} {})))
    (is (= {:kind :drum :note 42} (mx/event-note {:bank :hh :n 2} {})))
    (is (= {:kind :drum :note 46} (mx/event-note :RolandTR808_oh {})))
    (is (= {:kind :unmapped :name :tabla} (mx/event-note :tabla {})))
    (is (= {:kind :drum :note 60} (mx/event-note :tabla {:tabla 60})))
    (is (= {:kind :drum :note 35} (mx/event-note :bd {:bd 35}))))
  (testing "pitch"
    (is (= {:kind :pitch :note 60} (mx/event-note :c4 {})))
    (is (= {:kind :pitch :note 69} (mx/event-note 440 {})))
    (is (= {:kind :pitch :note 69} (mx/event-note {:note :a4} {})))
    (is (= {:kind :pitch :note 60} (mx/event-note {:freq 261.63} {})))
    (is (= {:kind :pitch :note 69} (mx/event-note {:note 440} {})))
    (is (= {:kind :pitch :note 127} (mx/event-note 20000 {})) "clamped to 127"))
  (testing "rests and noise are skipped silently"
    (is (= {:kind :skip} (mx/event-note :_ {})))
    (is (= {:kind :skip} (mx/event-note {:note :_} {})))
    (is (= {:kind :skip} (mx/event-note {:synth :noise} {})))))

(deftest drum-note-test
  (is (= 46 (mx/drum-note :RolandTR808_oh nil)))
  (is (= 36 (mx/drum-note :bd nil)))
  (is (nil? (mx/drum-note :tabla nil)))
  (is (nil? (mx/drum-note :foo_ nil))))

(deftest rat->tick-test
  (is (= 0 (mx/rat->tick [0 1])))
  (is (= 1920 (mx/rat->tick [1 1])))
  (is (= 3920 (mx/rat->tick [49 24])) "float seconds gave 3919 in the old exporter")
  (is (= 640 (mx/rat->tick [1 3]))))

(deftest melodic-channel-test
  (is (= [1 2 3 4 5 6 7 8 9 11 12 13 14 15 16 1] (mapv mx/melodic-channel (range 16)))))

;;; ── Song S ─────────────────────────────────────────────────────────────

(def ^:private song-s
  "(bpm 120)
   (track :kick (seq :bd :sd :hh :oh))
   (track :bass (amp 0.5 (seq :c2 :_ :g2 :_)))
   (song (section :intro 2 [:kick]) (section :verse 4 :all) (section :outro 2 [:bass]))")

(def ^:private conductor-s
  (hx "4D 54 72 6B 00 00 00 31 00 FF 58 04 04 02 18 08 00 FF 51 03 07 A1 20"
      "00 FF 06 05 69 6E 74 72 6F 9E 00 FF 06 05 76 65 72 73 65"
      "BC 00 FF 06 05 6F 75 74 72 6F 9E 00 FF 2F 00"))

(def ^:private bass-bar
  "83 60 91 24 40 83 60 81 24 00 83 60 91 2B 40 83 60 81 2B 00")

(deftest song-s-midi-test
  (let [{:keys [result bytes name mime]} (run-midi (str song-s " (midi-export :song)"))]
    (is (= "exported song as MIDI — 2 tracks, 8 bars" result))
    (is (= "repulse-song-8bars.mid" name))
    (is (= "audio/midi" mime))
    (is (= 449 (.-length bytes)))
    (testing "header and conductor"
      (is (= (hx "4D 54 68 64 00 00 00 06 00 01 00 03 01 E0") (hex (bytes-of bytes 0 14))))
      (is (= conductor-s (hex (bytes-of bytes 14 71)))))
    (testing "track 1 — kick, drums on channel 10"
      (is (= (hx "4D 54 72 6B 00 00 00 E5") (hex (bytes-of bytes 71 79))))
      (is (= (hx "00 FF 03 04 6B 69 63 6B"
                 "00 99 24 7F 83 60 89 24 00 00 99 26 7F 83 60 89 26 00"
                 "00 99 2A 7F 83 60 89 2A 00 00 99 2E 7F 83 60 89 2E 00")
             (hex (bytes-of bytes 79 123))))
      (is (= (* 4 6) (count (note-ons bytes 1))) "24 note-ons (bars 0-5)")
      (is (= (hx "89 2E 00 9E 00 FF 2F 00") (hex (bytes-of bytes 300 308)))
          "last off at 11520, end of track at 15360"))
    (testing "track 2 — bass, channel 2, velocity 64, first note at tick 3840"
      (is (= (hx "4D 54 72 6B 00 00 00 85 00 FF 03 04 62 61 73 73 9E 00 91 24 40 83 60 81 24 00"
                 "83 60 91 2B 40 83 60 81 2B 00"
                 (str/join " " (repeat 5 bass-bar))
                 "83 60 FF 2F 00")
             (hex (bytes-of bytes 308 449))))
      (is (= 3840 (first (note-ons bytes 2)))))))

;;; ── regressions ────────────────────────────────────────────────────────

(deftest velocity-in-file-test
  (let [{:keys [bytes]} (run-midi "(track :t (amp 0.5 (seq :c4))) (midi-export :t 1)")]
    (is (re-find #"90 3C 40" (hex bytes)))))

(deftest time-bug-regression-test
  (let [{:keys [result bytes name]} (run-midi "(track :lead (seq :c4 :e4)) (midi-export :lead 2)")]
    (testing "same-buffer track lookup works"
      (is (= "exported 2 cycles of :lead as MIDI" result))
      (is (= "repulse-lead.mid" name)))
    (is (= (hx "4D 54 68 64 00 00 00 06 00 01 00 02 01 E0") (hex (bytes-of bytes 0 14))))
    (is (= (hx "4D 54 72 6B 00 00 00 14 00 FF 58 04 04 02 18 08 00 FF 51 03 07 A1 20 9E 00 FF 2F 00")
           (hex (bytes-of bytes 14 42))))
    (is (= (hx "4D 54 72 6B 00 00 00 30 00 FF 03 04 6C 65 61 64"
               "00 90 3C 7F 87 40 80 3C 00 00 90 40 7F 87 40 80 40 00"
               "00 90 3C 7F 87 40 80 3C 00 00 90 40 7F 87 40 80 40 00 00 FF 2F 00")
           (hex (bytes-of bytes 42 (.-length bytes)))))
    (is (= [0 960 1920 2880] (note-ons bytes 1)))))

(deftest chord-overlap-test
  (let [{:keys [bytes result]} (run-midi "(track :ch (chord :major :c4)) (midi-export :ch 1)")]
    (is (= "exported 1 cycles of :ch as MIDI" result))
    (let [h (hex bytes)]
      (is (str/includes? h (hx "00 90 3C 7F 00 90 40 7F 00 90 43 7F 8F 00 80 3C 00 00 80 40 00 00 80 43 00"))
          h))))

(deftest chord-with-amp-test
  (let [{:keys [bytes]} (run-midi "(track :ch (amp 0.5 (chord :major :c4))) (midi-export :ch 1)")]
    (is (str/includes? (hex bytes) (hx "00 90 3C 40 00 90 40 40 00 90 43 40 8F 00 80 3C 00")))))

(deftest exact-ticks-test
  (let [{:keys [bytes]} (run-midi "(track :t (fast 24 (seq :c4))) (midi-export :t 3)")
        ons (note-ons bytes 1)]
    (is (= 72 (count ons)))
    (is (= (mapv #(* 80 %) (range 72)) ons) "k = 49 lands on 3920, not 3919")))

(deftest unmapped-warning-test
  (testing "unmapped drum names are counted and reported, sorted"
    (let [{:keys [result bytes]} (run-midi "(track :p (seq :bd :tabla :tabla :perc)) (midi-export :p 2)")]
      (is (= "exported 2 cycles of :p as MIDI — skipped 6 events with no GM drum note (:perc :tabla); map them with :drum-map"
             result))
      (is (= 2 (count (note-ons bytes 1))))))
  (testing ":drum-map maps them and removes the warning"
    (let [{:keys [result bytes]}
          (run-midi "(track :p (seq :bd :tabla :tabla :perc)) (midi-export :p 2 :drum-map {:tabla 60 :perc 61})")]
      (is (= "exported 2 cycles of :p as MIDI" result))
      (is (= 8 (count (note-ons bytes 1))))
      (is (re-find #"99 3C 7F" (hex bytes)) "tabla → 60 on channel 10")
      (is (re-find #"99 3D 7F" (hex bytes))))))

(deftest song-skips-unused-tracks-test
  (testing "K2: a track used in no section isn't a MIDI track"
    (let [{:keys [result bytes]}
          (run-midi (str "(track :a (seq :c4)) (track :unused (seq :e4)) "
                         "(song (section :x 1 [:a])) (midi-export :song)"))]
      (is (= "exported song as MIDI — 1 tracks, 1 bars" result))
      (is (= (hx "00 01 00 02") (hex (bytes-of bytes 8 12)))))))

(deftest cycles->midi-test
  (testing "tweened amp becomes a velocity ramp (K15)"
    (let [pat (:result (lisp/eval-string "(->> (seq :c4 :c4 :c4 :c4) (amp (tween :linear 0 1 4)))"
                                         (leval/make-env (fn [] nil) (fn [_] nil))))
          {:keys [spec]} (mx/cycles->midi {:tracks [[:t pat]] :n 1 :cycle-dur 1.0 :drum-map {}})
          vels (mapv :velocity (:notes (first (:tracks spec))))]
      (is (= 4 (count vels)))
      (is (apply <= vels) "non-decreasing")
      (is (< (first vels) (last vels)))))
  (testing "two tracks: channels 1 and 2; a drum on a melodic track uses channel 10"
    (let [env (leval/make-env (fn [] nil) (fn [_] nil))
          p   (fn [c] (:result (lisp/eval-string c env)))
          {:keys [spec]} (mx/cycles->midi {:tracks [[:a (p "(seq :c4 :bd)")] [:b (p "(seq :e4)")]]
                                           :n 1 :cycle-dur 2.0 :drum-map {}})]
      (is (= [[1 10] [2]] (mapv #(vec (distinct (map :channel (:notes %)))) (:tracks spec)))))))
