(ns repulse.ui.timeline
  "Canvas track visualization rendering and RAF animation.
   Responsibility: render a compact viz toolbar, draw scheduler tracks in multiple
   modes, preserve track muting, and animate while playback is active.
   Exports: render-track-panel!, start-playhead-raf!"
  (:require [repulse.core :as core]
            [repulse.audio :as audio]
            [clojure.string :as str]))

(defn- el [id] (.getElementById js/document id))

(def ^:private modes
  [{:id "wave" :label "∿ wave"}
   {:id "spec" :label "⊞ spec"}
   {:id "loud" :label "◐ loud"}
   {:id "beat" :label "║ beat"}
   {:id "roll" :label "⊟ roll"}
   {:id "seg"  :label "▬ seg"}])

(defonce ^:private viz-mode (atom "wave"))
(defonce ^:private phase (atom 0))
(defonce ^:private last-ts (atom nil))
(defonce ^:private raf-started? (atom false))
(defonce ^:private energy-cache (atom {}))

(def ^:private track-colors ["#e94560" "#56b6c2" "#e5c07b" "#c678dd" "#98c379" "#d19a66"])
(def ^:private energy-resolution 512)

(defn- escape-html [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn track-events [track-name pattern cycle color muted?]
  (let [sp {:start [cycle 1] :end [(inc cycle) 1]}
        evs (try (core/query pattern sp) (catch :default _ []))]
    {:name track-name
     :cycle cycle
     :color color
     :muted? muted?
     :events (mapv (fn [ev]
                     (let [part (:part ev)
                           value (:value ev)
                           pos (- (core/rat->float (:start part)) cycle)
                           dur (- (core/rat->float (:end part))
                                  (core/rat->float (:start part)))]
                       {:pos (mod pos 1)
                        :dur (max 0.01 dur)
                        :value value}))
                   evs)}))

(defn- active-track-data []
  (let [{:keys [tracks muted cycle]} @audio/scheduler-state]
    (mapv (fn [[idx [track-name pattern]]]
            (track-events track-name pattern cycle
                          (nth track-colors (mod idx (count track-colors)))
                          (contains? muted track-name)))
          (map-indexed vector (sort-by (comp name first) tracks)))))

(defn- draw-label! [ctx x y {:keys [name color muted?]}]
  (set! (.-fillStyle ctx) (if muted? "#3a4868" color))
  (set! (.-font ctx) "10px JetBrains Mono, monospace")
  (set! (.-textBaseline ctx) "top")
  (.fillText ctx (str ":" (cljs.core/name name)) (+ x 7) (+ y 5)))

(defn- clear-row! [ctx x y w h]
  (set! (.-fillStyle ctx) "#060b16")
  (.fillRect ctx x y w h)
  (set! (.-strokeStyle ctx) "#0e1628")
  (set! (.-lineWidth ctx) 1)
  (.beginPath ctx)
  (.moveTo ctx x (+ y (/ h 2)))
  (.lineTo ctx (+ x w) (+ y (/ h 2)))
  (.stroke ctx))

(defn- event-energy [events t]
  (reduce (fn [acc {:keys [pos dur]}]
            (let [d (mod (- t pos) 1)
                  inside? (< d dur)
                  pulse (if inside?
                          (* 0.92 (js/Math.exp (* -10 (/ d dur))))
                          (if (< d 0.08) (* 0.35 (js/Math.exp (* -50 d))) 0))]
              (max acc pulse)))
          0.06
          events))

(defn energy-curve [events]
  (mapv (fn [i]
          (event-energy events (/ i energy-resolution)))
        (range energy-resolution)))

(defn sample-energy [curve t]
  (let [n (count curve)
        x (* (mod t 1) n)
        i (int (js/Math.floor x))
        frac (- x i)
        a (nth curve i)
        b (nth curve (mod (inc i) n))]
    (+ a (* (- b a) frac))))

(defn- energy-key [{:keys [name cycle events]}]
  [name cycle events])

(defn- track-energy-curve [tr]
  (let [key (energy-key tr)]
    (or (get @energy-cache key)
        (let [curve (energy-curve (:events tr))]
          (swap! energy-cache assoc key curve)
          curve))))

(defn- prune-energy-cache! [tracks]
  (let [live-keys (set (map energy-key tracks))]
    (swap! energy-cache select-keys live-keys)))

(defn- draw-wave! [ctx x y w h tr live? ph]
  (clear-row! ctx x y w h)
  (let [cy (+ y (/ h 2))
        curve (track-energy-curve tr)
        color (if (:muted? tr) "#3a4868" (:color tr))]
    (.beginPath ctx)
    (doseq [px (range 0 (inc w))]
      (let [t (mod (+ (/ px w) (if live? (* ph 0.12) 0)) 1)
            amp (sample-energy curve t)
            yy (+ cy (* (js/Math.sin (+ (* t 56) (* ph 2))) amp h 0.38))]
        (if (zero? px)
          (.moveTo ctx (+ x px) yy)
          (.lineTo ctx (+ x px) yy))))
    (set! (.-strokeStyle ctx) color)
    (set! (.-lineWidth ctx) 1.5)
    (set! (.-shadowColor ctx) color)
    (set! (.-shadowBlur ctx) (if (:muted? tr) 0 6))
    (.stroke ctx)
    (set! (.-shadowBlur ctx) 0)
    (draw-label! ctx x y tr)))

(defn- draw-spec! [ctx x y w h tr live? ph]
  (clear-row! ctx x y w h)
  (let [curve (track-energy-curve tr)
        color (:color tr)
        bars 56
        bw (/ w bars)]
    (doseq [i (range bars)]
      (let [t (mod (+ (/ i bars) (if live? (* ph 0.16) 0)) 1)
            e (sample-energy curve t)
            fall (js/Math.exp (* -3 (/ i bars)))
            bh (max 1 (* h (+ (* e 0.72) (* fall 0.18))))
            alpha (if (:muted? tr) "44" "cc")]
        (set! (.-fillStyle ctx)
              (if (:muted? tr) "#26314c" (str color alpha)))
        (.fillRect ctx (+ x (* i bw)) (- (+ y h) bh 3) (max 1 (- bw 1)) bh)))
    (draw-label! ctx x y tr)))

(defn- draw-loud! [ctx x y w h tr live? ph]
  (clear-row! ctx x y w h)
  (let [curve (track-energy-curve tr)
        color (if (:muted? tr) "#3a4868" (:color tr))
        bottom (+ y h -1)]
    (.beginPath ctx)
    (.moveTo ctx x bottom)
    (doseq [px (range 0 (inc w))]
      (let [t (mod (+ (/ px w) (if live? (* ph 0.1) 0)) 1)
            e (sample-energy curve t)]
        (.lineTo ctx (+ x px) (- bottom (* e h 0.88)))))
    (.lineTo ctx (+ x w) bottom)
    (.closePath ctx)
    (let [g (.createLinearGradient ctx x y x (+ y h))]
      (.addColorStop g 0 (str color "aa"))
      (.addColorStop g 1 (str color "08"))
      (set! (.-fillStyle ctx) g)
      (.fill ctx))
    (draw-label! ctx x y tr)))

(defn- draw-beat! [ctx x y w h tr live? ph]
  (clear-row! ctx x y w h)
  (let [color (if (:muted? tr) "#3a4868" (:color tr))
        cy (+ y (* h 0.72))
        offset (if live? (mod (* ph 0.18 w) w) 0)]
    (doseq [i (range 32)]
      (let [x0 (+ x (mod (- (* i (/ w 16)) offset) w))
            strong? (zero? (mod i 4))
            mid? (zero? (mod i 2))
            hh (* h (cond strong? 0.62 mid? 0.34 :else 0.18))]
        (set! (.-strokeStyle ctx) (str color (if strong? "ee" (if mid? "88" "44"))))
        (set! (.-lineWidth ctx) (if strong? 1.5 1))
        (.beginPath ctx)
        (.moveTo ctx x0 cy)
        (.lineTo ctx x0 (- cy hh))
        (.stroke ctx)))
    (doseq [{:keys [pos dur]} (:events tr)]
      (set! (.-fillStyle ctx) (str color "dd"))
      (.fillRect ctx (+ x (* pos w)) (+ y 4) (max 2 (* dur w)) 5))
    (draw-label! ctx x y tr)))

(defn value-pitch [v idx]
  (cond
    (map? v) (value-pitch (or (:note v) (:freq v) (:sample v) (:synth v) (:value v)) idx)
    (number? v) (mod (js/Math.abs v) 12)
    (keyword? v) (let [n (name v)]
                   (mod (reduce + (map #(.charCodeAt n %) (range (count n)))) 12))
    :else (mod idx 12)))

(defn- draw-roll! [ctx x y w h tr _live? _ph]
  (clear-row! ctx x y w h)
  (let [color (if (:muted? tr) "#3a4868" (:color tr))
        row-h (/ h 12)]
    (doseq [r (range 12)]
      (when (contains? #{1 3 6 8 10} r)
        (set! (.-fillStyle ctx) "#09111e")
        (.fillRect ctx x (+ y (* r row-h)) w row-h))
      (set! (.-strokeStyle ctx) "#111a2e")
      (.beginPath ctx)
      (.moveTo ctx x (+ y (* r row-h)))
      (.lineTo ctx (+ x w) (+ y (* r row-h)))
      (.stroke ctx))
    (doseq [[idx {:keys [pos dur value]}] (map-indexed vector (:events tr))]
      (let [pitch (value-pitch value idx)
            yy (+ y (* (- 11 pitch) row-h) 1)
            xx (+ x (* pos w))
            ww (max 2 (- (* dur w) 1))]
        (set! (.-fillStyle ctx) (str color "cc"))
        (.fillRect ctx xx yy ww (max 2 (- row-h 1)))))
    (draw-label! ctx x y tr)))

(defn- draw-seg! [ctx x y w h tr _live? _ph]
  (clear-row! ctx x y w h)
  (let [color (if (:muted? tr) "#3a4868" (:color tr))]
    (doseq [[idx {:keys [pos dur value]}] (map-indexed vector (:events tr))]
      (let [xx (+ x (* pos w))
            ww (max 10 (* dur w))
            label (cond
                    (keyword? value) (str value)
                    (map? value) (or (some-> (:sample value) str)
                                     (some-> (:note value) str)
                                     (str "event " (inc idx)))
                    :else (str value))]
        (set! (.-fillStyle ctx) (str color "1f"))
        (.fillRect ctx (+ xx 1) (+ y 8) (- ww 2) (- h 16))
        (set! (.-strokeStyle ctx) (str color "66"))
        (.strokeRect ctx (+ xx 1) (+ y 8) (- ww 2) (- h 16))
        (set! (.-fillStyle ctx) (str color "cc"))
        (set! (.-font ctx) "9px JetBrains Mono, monospace")
        (set! (.-textBaseline ctx) "middle")
        (.fillText ctx label (+ xx 6) (+ y (/ h 2)) (max 0 (- ww 10)))))
    (draw-label! ctx x y tr)))

(defn- draw-empty! [ctx w h]
  (set! (.-fillStyle ctx) "#060b16")
  (.fillRect ctx 0 0 w h)
  (set! (.-fillStyle ctx) "#2a3a55")
  (set! (.-font ctx) "11px JetBrains Mono, monospace")
  (set! (.-textBaseline ctx) "middle")
  (.fillText ctx "viz waits for active tracks" 12 (/ h 2)))

(defn- draw-viz! []
  (when-let [canvas (el "track-viz-canvas")]
    (let [ctx (.getContext canvas "2d")
          data (active-track-data)
          w (.-width canvas)
          h (.-height canvas)
          live? (audio/playing?)
          ph @phase]
      (if (empty? data)
        (draw-empty! ctx w h)
        (let [row-h (max 28 (js/Math.floor (/ h (count data))))]
          (prune-energy-cache! data)
          (doseq [[idx tr] (map-indexed vector data)]
            (let [y (* idx row-h)]
              (case @viz-mode
                "spec" (draw-spec! ctx 0 y w row-h tr live? ph)
                "loud" (draw-loud! ctx 0 y w row-h tr live? ph)
                "beat" (draw-beat! ctx 0 y w row-h tr live? ph)
                "roll" (draw-roll! ctx 0 y w row-h tr live? ph)
                "seg"  (draw-seg! ctx 0 y w row-h tr live? ph)
                (draw-wave! ctx 0 y w row-h tr live? ph))
              (when (< idx (dec (count data)))
                (set! (.-strokeStyle ctx) "#0e1628")
                (.beginPath ctx)
                (.moveTo ctx 0 (+ y row-h))
                (.lineTo ctx w (+ y row-h))
                (.stroke ctx)))))))))

(defn render-track-panel! []
  (when-let [panel (el "track-panel")]
    (let [data (active-track-data)]
      (set! (.-innerHTML panel)
            (str "<div class=\"viz-toolbar\">"
                 "<span class=\"viz-label\">viz</span>"
                 (apply str
                   (map (fn [{:keys [id label]}]
                          (str "<button class=\"viz-mode-btn"
                               (when (= id @viz-mode) " active")
                               "\" data-mode=\"" id "\" title=\"" id "\">"
                               (escape-html label)
                               "</button>"))
                        modes))
                 (when (seq data)
                   (str "<div class=\"viz-track-mutes\">"
                        (apply str
                          (map (fn [{:keys [name color muted?]}]
                                 (str "<button class=\"viz-track-btn"
                                      (when muted? " muted")
                                      "\" style=\"--track-color:" color "\""
                                      " onclick=\"window._repulseMuteToggle('"
                                      (escape-html (cljs.core/name name))
                                      "')\">:" (escape-html (cljs.core/name name)) "</button>"))
                               data))
                        "</div>"))
                 "</div>"
                 "<canvas id=\"track-viz-canvas\" class=\"track-viz-canvas\" width=\"1600\" height=\"126\"></canvas>"))
      (doseq [btn (array-seq (.querySelectorAll panel ".viz-mode-btn"))]
        (.addEventListener btn "click"
          (fn []
            (reset! viz-mode (.. btn -dataset -mode))
            (render-track-panel!))))
      (draw-viz!))))

(defn start-playhead-raf! []
  (when-not @raf-started?
    (reset! raf-started? true)
    (letfn [(frame [ts]
              (when @last-ts
                (when (audio/playing?)
                  (swap! phase + (* (- ts @last-ts) 0.001))))
              (reset! last-ts ts)
              (draw-viz!)
              (js/requestAnimationFrame frame))]
      (js/requestAnimationFrame frame))))
