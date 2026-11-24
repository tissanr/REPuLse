(ns repulse.ui.viz-draw
  "Phase VIZ1 — one renderer for the track panel and the inline visualizers.
   draw! paints one view of one track into a rectangle of a 2D canvas context
   and returns a small summary of what it drew (note rectangles, lanes, the
   playhead) that the runtime keeps as a test handle.

   Event views (punchcard, pianoroll, spiral) draw the exact laid-out events of
   repulse.ui.viz-model; audio views (scope, spectrum, level) draw the track's
   measured audio. Nothing is synthesized.

   track-data — {:name kw :color css :muted? bool
                 :heard float          ; heard position in pattern time
                 :from :to             ; event window (event views)
                 :events [laid-out…]   ; viz-model/layout-events
                 :audio {:samples Float32Array :rms :peak :slot} ; audio views
                 :history [rms…]}      ; level view, oldest first, ≤ 4 s"
  (:require [repulse.ui.viz-model :as vm]))

(def dim-color "#3a4868")
(def bg-color "#060b16")
(def grid-color "#0e1628")

(defn- color-of [{:keys [muted? color]}]
  (if muted? dim-color color))

(defn- clear! [^js ctx x y w h]
  (set! (.-fillStyle ctx) bg-color)
  (.fillRect ctx x y w h))

(defn- label! [^js ctx x y td extra]
  (set! (.-fillStyle ctx) (color-of td))
  (set! (.-font ctx) "10px JetBrains Mono, monospace")
  (set! (.-textBaseline ctx) "top")
  (.fillText ctx (str ":" (name (:name td)) (when extra (str "  " extra))) (+ x 6) (+ y 4)))

(defn- hex-alpha [a]
  (let [n (Math/round (* 255 (max 0 (min 1 a))))
        s (.toString n 16)]
    (if (< n 16) (str "0" s) s)))

(defn- playhead! [^js ctx px y h]
  (set! (.-fillStyle ctx) "#e8edf7")
  (.fillRect ctx (Math/round px) y 1 h))

;;; ── Lane layout shared by pianoroll and punchcard ─────────────────────

(defn- lane-layout
  "Row index per event: drum lanes on top (first appearance order), then the
   pitch rows, highest pitch first. → {:n rows :row-of (fn [ev] idx) :names […]}"
  [events opts]
  (let [{:keys [pitch-rows drum-lanes]} (vm/rows events opts)
        pitch-desc (vec (reverse pitch-rows))
        nd   (count drum-lanes)
        lidx (zipmap drum-lanes (range))
        pidx (zipmap pitch-desc (range nd (+ nd (count pitch-desc))))]
    {:n      (max 1 (+ nd (count pitch-desc)))
     :row-of (fn [{:keys [midi lane]}] (if midi (get pidx midi) (get lidx lane)))
     :names  (into (vec drum-lanes) (map str pitch-desc))
     :drums  nd}))

(defn- draw-lanes!
  "Shared body of pianoroll / punchcard: events of [from to) on lanes."
  [^js ctx x y w h {:keys [from to heard events] :as td} opts]
  (let [{:keys [n row-of drums]} (lane-layout events opts)
        top    (+ y 16)
        lh     (/ (- h 18) n)
        span   (- to from)
        x-of   (fn [t] (+ x (* w (/ (- t from) span))))
        col    (color-of td)
        labels (:labels opts)]
    ;; lane separators; a faint band under the drum lanes
    (when (pos? drums)
      (set! (.-fillStyle ctx) "#0a1222")
      (.fillRect ctx x top w (* drums lh)))
    (set! (.-fillStyle ctx) grid-color)
    (doseq [c (range (Math/ceil from) (inc (Math/floor to)))]
      (.fillRect ctx (Math/round (x-of c)) top 1 (- h 18)))
    (let [notes
          (vec
           (for [{:keys [start end alpha label continuous?] :as ev} events
                 :let [r (row-of ev)]
                 :when (and r (< start to) (> end from))]
             (let [x0 (max x (x-of start))
                   x1 (min (+ x w) (x-of end))
                   ww (max 2 (- x1 x0 1))
                   yy (+ top (* r lh) 0.5)
                   hh (max 2 (- lh 1))]
               (set! (.-fillStyle ctx) (str col (hex-alpha (* (if continuous? 0.45 1) alpha))))
               (.fillRect ctx x0 yy ww hh)
               (when continuous?
                 (set! (.-strokeStyle ctx) (str col "55"))
                 (.strokeRect ctx x0 yy ww hh))
               (when (and labels (> ww 18) (>= hh 9))
                 (set! (.-fillStyle ctx) "#060b16")
                 (set! (.-font ctx) (str (min 10 (Math/floor (- hh 1))) "px JetBrains Mono, monospace"))
                 (set! (.-textBaseline ctx) "middle")
                 (.fillText ctx label (+ x0 3) (+ yy (/ hh 2)) (- ww 5)))
               {:midi (:midi ev) :lane (:lane ev) :label label :row r
                :x (- x0 x) :w ww :y (- yy y) :h hh})))
          px (x-of heard)]
      (playhead! ctx px top (- h 18))
      {:notes notes :rows n :playhead-x (- px x)})))

(defn- draw-pianoroll! [ctx x y w h td opts]
  (clear! ctx x y w h)
  (let [r (draw-lanes! ctx x y w h td opts)]
    (label! ctx x y td nil)
    r))

(defn- draw-punchcard! [ctx x y w h td opts]
  (clear! ctx x y w h)
  (let [r (draw-lanes! ctx x y w h td (assoc opts :fold true))]
    (label! ctx x y td nil)
    r))

(defn- draw-spiral! [^js ctx x y w h {:keys [from to heard events] :as td} _opts]
  (clear! ctx x y w h)
  (let [cx    (+ x (/ w 2))
        cy    (+ y (/ h 2) 6)
        turns (max 1 (Math/round (- to from)))
        rmax  (max 8 (- (/ (min w h) 2) 10))
        rmin  (* rmax 0.25)
        dr    (/ (- rmax rmin) turns)
        col   (color-of td)
        tau   (* 2 Math/PI)
        ang   (fn [t] (- (* tau (- t (Math/floor t))) (/ Math/PI 2)))
        rad   (fn [t] (+ rmin (* dr (+ 0.5 (Math/floor (- t from))))))]
    (set! (.-strokeStyle ctx) grid-color)
    (set! (.-lineWidth ctx) 1)
    (dotimes [k turns]
      (.beginPath ctx)
      (.arc ctx cx cy (+ rmin (* dr (+ 0.5 k))) 0 tau)
      (.stroke ctx))
    (set! (.-lineWidth ctx) (max 2 (* dr 0.6)))
    (set! (.-lineCap ctx) "butt")
    (doseq [{:keys [start end alpha]} events
            :when (and (>= start from) (< start to))]
      (let [e (min end (inc (Math/floor start)))]
        (set! (.-strokeStyle ctx) (str col (hex-alpha alpha)))
        (.beginPath ctx)
        (.arc ctx cx cy (rad start) (ang start) (max (+ (ang start) 0.04)
                                                    (if (>= (- e (Math/floor start)) 1)
                                                      (+ (ang start) (* tau (- e start)))
                                                      (ang e))))
        (.stroke ctx)))
    (set! (.-lineWidth ctx) 1)
    (let [a (ang heard)]
      (set! (.-strokeStyle ctx) "#e8edf7")
      (.beginPath ctx)
      (.moveTo ctx cx cy)
      (.lineTo ctx (+ cx (* (+ rmax 4) (Math/cos a))) (+ cy (* (+ rmax 4) (Math/sin a))))
      (.stroke ctx))
    (label! ctx x y td nil)
    {:notes (count events) :playhead-angle (ang heard)}))

;;; ── Audio views ───────────────────────────────────────────────────────

(defn- no-audio-note [{:keys [audio]}]
  (when (and audio (= -1 (:slot audio)))
    "no meter: more than 16 tracks"))

(defn- draw-scope! [^js ctx x y w h {:keys [audio] :as td} opts]
  (clear! ctx x y w h)
  (let [^js s  (:samples audio)
        n      (if s (.-length s) 0)
        start  (if (and s (:trigger opts)) (vm/scope-trigger-index s) 0)
        cnt    (max 2 (- n start (quot n 4)))
        scale  (or (:scale opts) 1)
        cy     (+ y (/ h 2))
        amp    (* 0.45 h scale)
        col    (color-of td)]
    (set! (.-fillStyle ctx) grid-color)
    (.fillRect ctx x (Math/round cy) w 1)
    (when (pos? n)
      (.beginPath ctx)
      (dotimes [px (inc (Math/floor w))]
        (let [i  (+ start (Math/floor (* (/ px w) (dec cnt))))
              v  (max -1 (min 1 (* (aget s (min (dec n) i)) scale)))
              yy (- cy (* v amp (/ 1 (max scale 1e-6))))]
          (if (zero? px) (.moveTo ctx (+ x px) yy) (.lineTo ctx (+ x px) yy))))
      (set! (.-strokeStyle ctx) col)
      (set! (.-lineWidth ctx) 1.5)
      (.stroke ctx)
      (set! (.-lineWidth ctx) 1))
    (label! ctx x y td (no-audio-note td))
    {:rms (or (:rms audio) 0) :peak (or (:peak audio) 0)}))

;; FFT scratch (2048 points): reused typed arrays, precomputed window and
;; twiddles — no allocation per frame.
(def ^:private fft-n 2048)
(defonce ^:private fft-state
  (let [n fft-n
        hann (js/Float32Array. n)
        cos  (js/Float32Array. (/ n 2))
        sin  (js/Float32Array. (/ n 2))
        rev  (js/Uint32Array. n)
        bits (Math/round (/ (Math/log n) (Math/log 2)))]
    (dotimes [i n]
      (aset hann i (* 0.5 (- 1 (Math/cos (/ (* 2 Math/PI i) (dec n))))))
      (aset rev i (loop [b 0 v i r 0]
                    (if (= b bits) r (recur (inc b) (bit-shift-right v 1)
                                            (bit-or (bit-shift-left r 1) (bit-and v 1)))))))
    (dotimes [i (/ n 2)]
      (aset cos i (Math/cos (/ (* -2 Math/PI i) n)))
      (aset sin i (Math/sin (/ (* -2 Math/PI i) n))))
    {:hann hann :cos cos :sin sin :rev rev
     :re (js/Float32Array. n) :im (js/Float32Array. n) :db (js/Float32Array. (/ n 2))}))

(defn spectrum-db
  "Radix-2 FFT (Hann window) of the first 2048 samples → the reused
   Float32Array of 1024 magnitudes in dB (full-scale sine ≈ 0 dB)."
  [^js samples]
  (let [{:keys [hann cos sin rev re im db]} fft-state
        n fft-n]
    (dotimes [i n]
      (let [j (aget rev i)]
        (aset re j (if (< i (.-length samples)) (* (aget samples i) (aget hann i)) 0))
        (aset im j 0)))
    (loop [size 2]
      (when (<= size n)
        (let [half (/ size 2) step (/ n size)]
          (loop [i 0]
            (when (< i n)
              (dotimes [k half]
                (let [tr (aget cos (* k step)) ti (aget sin (* k step))
                      a (+ i k) b (+ a half)
                      xr (- (* (aget re b) tr) (* (aget im b) ti))
                      xi (+ (* (aget re b) ti) (* (aget im b) tr))]
                  (aset re b (- (aget re a) xr)) (aset im b (- (aget im a) xi))
                  (aset re a (+ (aget re a) xr)) (aset im a (+ (aget im a) xi))))
              (recur (+ i size)))))
        (recur (* size 2))))
    (dotimes [k (/ n 2)]
      (let [m (/ (* 4 (Math/hypot (aget re k) (aget im k))) n)]
        (aset db k (* 20 (/ (Math/log (max m 1e-9)) Math/LN10)))))
    db))

(defn- draw-spectrum! [^js ctx x y w h {:keys [audio] :as td} opts]
  (clear! ctx x y w h)
  (let [^js s (:samples audio)
        col   (color-of td)
        top   (+ y 16)
        hh    (- h 18)
        nyq   22050
        log?  (:log opts true)
        fx    (fn [px] (let [t (/ px w)]
                         (if log? (* 20 (Math/pow 1000 t)) (+ 20 (* t (- 20000 20))))))]
    (when s
      (let [db (spectrum-db s)]
        (.beginPath ctx)
        (.moveTo ctx x (+ top hh))
        (dotimes [px (inc (Math/floor w))]
          (let [k  (min 1023 (Math/round (* 1024 (/ (fx px) nyq))))
                v  (aget db k)
                t  (max 0 (min 1 (/ (- v -90) 80)))]
            (.lineTo ctx (+ x px) (- (+ top hh) (* t hh)))))
        (.lineTo ctx (+ x w) (+ top hh))
        (.closePath ctx)
        (set! (.-fillStyle ctx) (str col "99"))
        (.fill ctx)))
    (label! ctx x y td (no-audio-note td))
    {:rms (or (:rms audio) 0)}))

(defn- to-db [v] (* 20 (/ (Math/log (max v 1e-6)) Math/LN10)))

(defn- draw-level! [^js ctx x y w h {:keys [audio history] :as td} _opts]
  (clear! ctx x y w h)
  (let [col  (color-of td)
        top  (+ y 16)
        hh   (- h 20)
        db01 (fn [v] (max 0 (min 1 (/ (- (to-db v) -60) 60))))
        bar  (* (db01 (or (:peak audio) 0)) w 0.25)
        hx   (+ x (* w 0.28))
        hw   (* w 0.7)
        n    (count history)]
    (set! (.-fillStyle ctx) grid-color)
    (.fillRect ctx x (+ top (/ hh 2) -3) (* w 0.25) 6)
    (set! (.-fillStyle ctx) col)
    (.fillRect ctx x (+ top (/ hh 2) -3) bar 6)
    (when (> n 1)
      (.beginPath ctx)
      (doseq [[i v] (map-indexed vector history)]
        (let [px (+ hx (* hw (/ i (dec n))))
              py (- (+ top hh) (* hh (db01 v)))]
          (if (zero? i) (.moveTo ctx px py) (.lineTo ctx px py))))
      (set! (.-strokeStyle ctx) col)
      (.stroke ctx))
    (label! ctx x y td (no-audio-note td))
    {:rms (or (:rms audio) 0) :peak (or (:peak audio) 0)}))

(defn draw!
  "Draw view `kind` of track-data into [x y w h] of ctx. → summary map."
  [kind ctx x y w h td opts]
  (case kind
    :pianoroll (draw-pianoroll! ctx x y w h td opts)
    :punchcard (draw-punchcard! ctx x y w h td opts)
    :spiral    (draw-spiral! ctx x y w h td opts)
    :scope     (draw-scope! ctx x y w h td opts)
    :spectrum  (draw-spectrum! ctx x y w h td opts)
    :level     (draw-level! ctx x y w h td opts)
    (do (clear! ctx x y w h) nil)))

(defn draw-placeholder!
  "A dark rectangle with a centred hint (track not playing, …)."
  [^js ctx x y w h text]
  (clear! ctx x y w h)
  (set! (.-fillStyle ctx) "#2a3a55")
  (set! (.-font ctx) "11px JetBrains Mono, monospace")
  (set! (.-textBaseline ctx) "middle")
  (.fillText ctx text (+ x 12) (+ y (/ h 2))))
