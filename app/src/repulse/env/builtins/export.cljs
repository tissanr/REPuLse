(ns repulse.env.builtins.export
  "WAV export builtin."
  (:require [repulse.core :as core]
            [repulse.audio :as audio]
            [repulse.lisp.eval :as leval]))

(defn make-builtins
  "No ctx dependencies."
  [_ctx]
  {"export"
   (fn [& args]
     (let [arg        (when (seq args) (leval/unwrap (first args)))
           n-cycles   (if (number? arg) (int arg) 4)
           track-kw   (when (keyword? arg) arg)
           state      @audio/scheduler-state
           all-tracks (:tracks state)
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
                   (let [evs (core/query pattern sp)]
                     (doseq [ev evs]
                       (let [part-start (core/rat->float (:start (:part ev)))
                             t          (* (- part-start c) cycle-dur)
                             abs-t      (+ (* c cycle-dur) t)]
                         (when (>= abs-t 0)
                           (audio/play-event offline abs-t (:value ev))))))))))
           ;; Render and encode as WAV
           (-> (.startRendering offline)
               (.then (fn [buffer]
                        (let [ch-l       (.getChannelData buffer 0)
                              ch-r       (.getChannelData buffer 1)
                              n          (.-length ch-l)
                              bps        16
                              n-ch       2
                              data-bytes (* n n-ch (/ bps 8))
                              buf        (js/ArrayBuffer. (+ 44 data-bytes))
                              dv         (js/DataView. buf)]
                          ;; RIFF header
                          (doto dv
                            (.setUint8   0 82) (.setUint8   1 73)  ; R I
                            (.setUint8   2 70) (.setUint8   3 70)  ; F F
                            (.setUint32  4 (+ 36 data-bytes) true)
                            (.setUint8   8 87) (.setUint8   9 65)  ; W A
                            (.setUint8  10 86) (.setUint8  11 69)  ; V E
                            ;; fmt chunk
                            (.setUint8  12 102) (.setUint8 13 109) ; f m
                            (.setUint8  14 116) (.setUint8 15 32)  ; t _
                            (.setUint32 16 16 true)                ; chunk size
                            (.setUint16 20 1 true)                 ; PCM
                            (.setUint16 22 n-ch true)
                            (.setUint32 24 sr true)
                            (.setUint32 28 (* sr n-ch (/ bps 8)) true)
                            (.setUint16 32 (* n-ch (/ bps 8)) true)
                            (.setUint16 34 bps true)
                            ;; data chunk
                            (.setUint8  36 100) (.setUint8 37 97)  ; d a
                            (.setUint8  38 116) (.setUint8 39 97)  ; t a
                            (.setUint32 40 data-bytes true))
                          ;; Interleaved 16-bit PCM samples
                          (dotimes [i n]
                            (let [l      (Math/max -1 (Math/min 1 (aget ch-l i)))
                                  r      (Math/max -1 (Math/min 1 (aget ch-r i)))
                                  offset (+ 44 (* i 4))]
                              (.setInt16 dv offset       (int (* l 32767)) true)
                              (.setInt16 dv (+ offset 2) (int (* r 32767)) true)))
                          ;; Trigger download
                          (let [blob (js/Blob. #js [buf] #js {:type "audio/wav"})
                                url  (.createObjectURL js/URL blob)
                                a    (.createElement js/document "a")]
                            (set! (.-href a) url)
                            (set! (.-download a) (str "repulse-"
                                                       (if track-kw (name track-kw) "all")
                                                       "-" n-cycles "cycles.wav"))
                            (.appendChild (.-body js/document) a)
                            (.click a)
                            (.removeChild (.-body js/document) a)
                            (js/setTimeout #(.revokeObjectURL js/URL url) 1000)))))
               (.catch (fn [e]
                         (js/console.error "[REPuLse] export failed:" e))))
           (str "exporting "
                (if track-kw (str ":" (name track-kw)) "all tracks")
                " — " n-cycles " cycles…"))))})
