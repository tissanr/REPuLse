(ns repulse.wav
  "16-bit stereo PCM WAV encoding and browser download.
   Shared by (export n) / (export :track) and (export :song).")

(defn encode-wav-16
  "Encode two Float32Array channels (equal length) as a 16-bit stereo RIFF/WAVE
   ArrayBuffer at sample-rate. Samples are clamped to [-1, 1]."
  [ch-l ch-r sample-rate]
  (let [sr         sample-rate
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
    buf))

(defn download-wav!
  "Offer an encoded WAV ArrayBuffer as a file download."
  [buf filename]
  (let [blob (js/Blob. #js [buf] #js {:type "audio/wav"})
        url  (.createObjectURL js/URL blob)
        a    (.createElement js/document "a")]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (.appendChild (.-body js/document) a)
    (.click a)
    (.removeChild (.-body js/document) a)
    (js/setTimeout #(.revokeObjectURL js/URL url) 1000)))
