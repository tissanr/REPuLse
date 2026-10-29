(ns repulse.env.builtins.export
  "WAV export builtin: (export n), (export :track) render offline;
   (export :song [:tail secs]) captures the song in real time (SONG1)."
  (:require [repulse.core :as core]
            [repulse.audio :as audio]
            [repulse.song-transport :as song-transport]
            [repulse.wav :as wav]
            [repulse.lisp.eval :as leval]))

(def ^:private default-tail-secs 2)

(defn- export-song
  "(export :song) / (export :song :tail secs). Returns a status or \"Error: …\" string;
   the capture itself starts after the buffer has evaluated (song-transport)."
  [opts set-output!]
  (let [tail-v (if (= :tail (first opts)) (second opts) default-tail-secs)]
    (cond
      (or (and (seq opts) (not= :tail (first opts)))
          (not (number? tail-v)) (js/isNaN tail-v) (< tail-v 0) (> tail-v 30))
      "Error: :tail must be a number of seconds between 0 and 30"

      (nil? (song-transport/current-song))
      "Error: no song — evaluate a buffer that ends with (song …) first"

      (or (not (.-AudioWorkletNode js/globalThis))
          (and @audio/ctx (nil? (.-audioWorklet ^js @audio/ctx))))
      "Error: (export :song) needs AudioWorklet support for real-time capture"

      (:capture @audio/scheduler-state)
      "Error: an export is already running — (stop) cancels it"

      :else
      (let [s (song-transport/current-song)]
        (reset! song-transport/pending-export {:tail tail-v :set-output! set-output!})
        (str "exporting song — " (song-transport/song-summary s tail-v)
             " in real time… (stop) cancels")))))

(defn make-builtins
  "ctx — {:set-output! f}"
  [{:keys [set-output!]}]
  {"export"
   (fn [& args]
     (if (= :song (some-> (first args) leval/unwrap))
       (export-song (mapv leval/unwrap (rest args)) set-output!)
     (let [arg        (when (seq args) (leval/unwrap (first args)))
           n-cycles   (if (number? arg) (int arg) 4)
           track-kw   (when (keyword? arg) arg)
           state      @audio/scheduler-state
           ;; tracks defined earlier in this same buffer are activated only
           ;; after evaluation (SONG1) — include them so (export n) sees them
           all-tracks (merge (:tracks state) (into {} @song-transport/pending-tracks))
           tracks     (cond
                        (nil? track-kw) all-tracks
                        (contains? all-tracks track-kw) {track-kw (get all-tracks track-kw)}
                        :else {})
           cycle-dur  (:cycle-dur state)
           duration   (* n-cycles cycle-dur)
           sr         44100
           n-frames   (int (* sr duration))
           offline    (js/OfflineAudioContext. 2 n-frames sr)]
       (if (empty? tracks)
         (if track-kw
           (str "Error: no track :" (name track-kw))
           "Error: no active tracks to export")
         (do
           ;; Schedule all events for N cycles into the offline context
           (doseq [c (range n-cycles)]
             (let [sp {:start [(int c) 1] :end [(int (inc c)) 1]}]
               (doseq [[_track-name pattern] tracks]
                 (when pattern
                   (let [evs (core/query-onsets pattern sp)]
                     (doseq [ev evs]
                       (let [part-start (core/rat->float (:start (:whole ev)))
                             t          (* (- part-start c) cycle-dur)
                             abs-t      (+ (* c cycle-dur) t)]
                         (when (>= abs-t 0)
                           (audio/play-event offline abs-t (:value ev))))))))))
           ;; Render and encode as WAV
           (-> (.startRendering offline)
               (.then (fn [buffer]
                        (wav/download-wav!
                         (wav/encode-wav-16 (.getChannelData buffer 0)
                                            (.getChannelData buffer 1)
                                            sr)
                         (str "repulse-"
                              (if track-kw (name track-kw) "all")
                              "-" n-cycles "cycles.wav"))))
               (.catch (fn [e]
                         (js/console.error "[REPuLse] export failed:" e))))
           (str "exporting "
                (if track-kw (str ":" (name track-kw)) "all tracks")
                " — " n-cycles " cycles…"))))))})
