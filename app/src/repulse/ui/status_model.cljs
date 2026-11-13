(ns repulse.ui.status-model
  "Pure status-head model (Phase WS1): song position and status text, REC
   text, BPM parsing/stepping, audio latency text, song progress segments and
   cue bars.  No DOM, no atoms.
   Exports: song-bar, song-status-text, rec-status-text, inactive-section,
            bpm-of, BPM-MIN, BPM-MAX, parse-bpm, step-bpm, latency-text,
            progress-segments, segment-cue-bar, section-cue-bar."
  (:require [clojure.string :as str]
            [repulse.song :as song]))

;;; Song position

(defn song-bar
  "Song bar most recently scheduled (≈ the one sounding), or nil when no song
   is active or its origin is not resolved yet."
  [state]
  (when-let [{:keys [origin]} (:song state)]
    (when origin (- (:cycle state) 1 origin))))

(defn song-status-text
  "\"▶ chorus · bar 13/24\", \"▶ song · starting\" during pre-roll, or nil."
  [state]
  (when-let [{s :song origin :origin} (:song state)]
    (if-let [{:keys [bar total section]} (when origin (song/song-status s (song-bar state)))]
      (str "▶ " (name section) " · bar " bar "/" total)
      (when (or (nil? origin) (neg? (song-bar state)))
        "▶ song · starting"))))

(defn rec-status-text
  "\"● REC 12.0 / 50.0 s\" while an (export :song) capture runs, or nil."
  [state now]
  (when-let [{:keys [status start-time end-time]} (:capture state)]
    (if (= :recording status)
      (str "● REC " (.toFixed (max 0 (- now start-time)) 1)
           " / " (.toFixed (- end-time start-time) 1) " s")
      "● REC waiting")))

(defn inactive-section
  "In song mode: the current section's name when track-name rests in it, else nil."
  [state track-name]
  (when-let [{s :song} (:song state)]
    (when-let [bar (song-bar state)]
      (when-let [q (song/wrap-bar s bar)]
        (when-not (contains? (song/active-tracks-at-bar s q) track-name)
          (:name (song/section-at-bar s q)))))))

;;; BPM

(def BPM-MIN 40)
(def BPM-MAX 300)

(defn bpm-of
  "Integer BPM for a scheduler :cycle-dur (one cycle = 4 beats)."
  [cycle-dur]
  (Math/round (/ 240 cycle-dur)))

(defn parse-bpm
  "Integer BPM from user input: 2–3 digits within 40–300, else nil."
  [s]
  (let [t (str/trim (str s))]
    (when (re-matches #"\d{2,3}" t)
      (let [n (js/parseInt t 10)]
        (when (<= BPM-MIN n BPM-MAX) n)))))

(defn step-bpm
  "bpm ± 1 (or ± 10 when coarse?), clamped to 40–300."
  [bpm dir coarse?]
  (max BPM-MIN (min BPM-MAX (+ bpm (* dir (if coarse? 10 1))))))

;;; Latency

(defn latency-text
  "\"5.3 ms\" from AudioContext baseLatency + outputLatency (seconds), or \"—\"."
  [base output]
  (if (nil? base)
    "—"
    (str (.toFixed (* 1000 (+ base (or output 0))) 1) " ms")))

;;; Progress bar

(defn- clamp01 [x] (max 0 (min 1 x)))

(defn progress-segments
  "One map per song section in order:
   {:index :name :bars :start :fill 0..1 :playing?}. bar is the scheduler bar
   relative to the song origin (nil when unknown)."
  [song bar]
  (if (nil? song)
    []
    (let [q (when (some? bar) (song/wrap-bar song bar))]
      (vec (map-indexed
             (fn [i {:keys [name bars start end]}]
               (let [end (or end (+ start bars))]
                 {:index    i
                  :name     name
                  :bars     bars
                  :start    start
                  :fill     (if (some? q) (clamp01 (/ (- (inc q) start) bars)) 0)
                  :playing? (boolean (and (some? q) (<= start q) (< q end)))}))
             (:sections song))))))

(defn segment-cue-bar
  "Start bar of song section `index`, or nil."
  [song index]
  (:start (nth (:sections song) index nil)))

(defn section-cue-bar
  "Start bar of song section `index` when outline section `index` still has the
   same name (the code may have changed since the last eval), else nil."
  [song outline-sections index]
  (let [sec (nth (:sections song) index nil)
        o   (nth outline-sections index nil)]
    (when (and sec o (= (:name o) (str ":" (name (:name sec)))))
      (:start sec))))
