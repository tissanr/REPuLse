(ns repulse.ui.timeline
  "Track panel and visualizer runtime (Phase VIZ1).
   Responsibility: the track panel under the editor (mode toolbar, track mute
   buttons, one row per track) and the single animation loop that draws it and
   every registered inline visualizer canvas (repulse.ui.inline-viz).

   Every view is honest: event views draw the exact pattern events around the
   heard position (audio/heard-cycle-pos — the output latency included), audio
   views draw the track's own measured audio (audio/track-audio). Drawing is
   done by repulse.ui.viz-draw, the arithmetic by repulse.ui.viz-model.
   Exports: render-track-panel!, start-viz-raf!, register-canvas!,
            unregister-canvas!, draw-canvas!, summaries, query-count, last-cycle."
  (:require [repulse.audio :as audio]
            [repulse.song :as song]
            [repulse.ui.viz-model :as vm]
            [repulse.ui.viz-draw :as vd]
            [repulse.ui.html :refer [escape-html]]))

(defn- el [id] (.getElementById js/document id))

;;; ── Panel modes ──────────────────────────────────────────────────────────

(def modes
  [{:id "punch" :label "▦ punch" :kind :punchcard :opts {:cycles 1 :labels true}}
   {:id "roll"  :label "⊟ roll"  :kind :pianoroll :opts {:cycles 4 :playhead 0.5 :fold true}}
   {:id "scope" :label "∿ scope" :kind :scope     :opts {:scale 1 :trigger true}}
   {:id "spec"  :label "⊞ spec"  :kind :spectrum  :opts {:log true}}
   {:id "level" :label "◐ level" :kind :level     :opts {}}])

(defonce ^:private viz-mode (atom "punch"))
(defonce ^:private raf-started? (atom false))

(def track-colors ["#e94560" "#56b6c2" "#e5c07b" "#c678dd" "#98c379" "#d19a66"])

;;; ── Event query cache ────────────────────────────────────────────────────
;; {[track c0 c1] {:pattern p :events laid-out}} — a track is queried again
;; only when the integer cycles of a view's window or its pattern change.
(defonce ^:private events-cache (atom {}))
(defonce ^:private used-keys (atom #{}))
;; Test handles: pattern queries so far, and the cycle of the last draw.
(defonce query-count (atom 0))
(defonce last-cycle (atom nil))
;; Test handle: {[track-name kind source] summary} of the last drawn views
;; (source "panel" or "inline").
(defonce summaries (atom {}))

(defn- window-events [track-name pattern from to]
  (let [c0  (Math/floor from)
        c1  (max (inc c0) (Math/ceil to))
        k   [track-name c0 c1]
        hit (get @events-cache k)]
    (swap! used-keys conj k)
    (if (and hit (identical? pattern (:pattern hit)))
      (:events hit)
      (let [evs (try (vm/layout-events (vm/query-window pattern c0 c1))
                     (catch :default _ []))]
        (swap! query-count inc)
        (swap! events-cache assoc k {:pattern pattern :events evs})
        evs))))

;;; ── Level history ────────────────────────────────────────────────────────
;; {track [[ms rms] …]} — the last 4 s of per-frame RMS values.
(defonce ^:private level-history (atom {}))

(defn- push-level! [track-name rms]
  (let [now (.now js/performance)]
    (swap! level-history update track-name
           (fn [h] (let [h (conj (or h []) [now rms])]
                     (if (> (- now (ffirst h)) 4000)
                       (vec (drop-while #(> (- now (first %)) 4000) h))
                       h))))))

;;; ── Track data ───────────────────────────────────────────────────────────

(defn- pattern-heard
  "Heard position in a track's pattern time: in song mode the song bar
   (wrapped like the scheduler does), else the cycle itself."
  [state heard]
  (if-let [{s :song origin :origin} (:song state)]
    (if origin
      (let [rel (- heard origin)
            bar (Math/floor rel)
            q   (song/wrap-bar s bar)]
        (if q (+ q (- rel bar)) rel))
      heard)
    heard))

(defn- track-entries
  "Sorted running tracks: [{:name :pattern :color :muted?} …]."
  [state heard]
  (let [{:keys [tracks muted]} state
        resting (or (when heard (vm/song-resting-tracks state (Math/floor heard))) #{})]
    (vec (map-indexed
          (fn [idx [track-name pattern]]
            {:name    track-name
             :pattern pattern
             :color   (nth track-colors (mod idx (count track-colors)))
             :muted?  (or (contains? muted track-name) (contains? resting track-name))})
          (sort-by (comp name first) tracks)))))

(defn- track-data
  "The track-data map viz-draw expects for one view of one track."
  [{:keys [name pattern] :as entry} kind opts state heard]
  (let [ph (pattern-heard state heard)]
    (cond
      (contains? vm/event-kinds kind)
      (let [[from to] (if (= kind :pianoroll)
                        (vm/window ph opts)
                        (vm/grid-window ph (:cycles opts 1)))]
        (assoc entry :heard ph :from from :to to
               :events (window-events name pattern from to)))

      :else
      (let [a (audio/track-audio name)]
        (when (= kind :level) (push-level! name (:rms a)))
        (assoc entry :heard ph :audio a
               :history (mapv second (get @level-history name)))))))

;;; ── Panel ────────────────────────────────────────────────────────────────

(defn- current-mode []
  (or (some #(when (= (:id %) @viz-mode) %) modes) (first modes)))

(def ^:private panel-min-h 126)
(def ^:private panel-min-row-h 32)

(defn- fit-canvas!
  "Size canvas's backing store to its CSS box × devicePixelRatio and set the
   transform, so drawing happens in CSS pixels and text is neither stretched
   nor blurred. → [css-width css-height]."
  [^js canvas ^js ctx]
  (let [dpr (or (.-devicePixelRatio js/window) 1)
        cw  (max 1 (.-clientWidth canvas))
        ch  (max 1 (.-clientHeight canvas))]
    (when (or (not= (.-width canvas) (Math/round (* cw dpr)))
              (not= (.-height canvas) (Math/round (* ch dpr))))
      (set! (.-width canvas) (Math/round (* cw dpr)))
      (set! (.-height canvas) (Math/round (* ch dpr))))
    (.setTransform ctx dpr 0 0 dpr 0 0)
    [cw ch]))

(defn- draw-panel! [state heard]
  (when-let [^js canvas (el "track-viz-canvas")]
    (let [ctx     (.getContext canvas "2d")
          entries (track-entries state heard)
          ;; grow the panel with the track count instead of clipping rows
          css-h   (max panel-min-h (* (count entries) panel-min-row-h))
          _       (when (not= (str css-h "px") (.. canvas -style -height))
                    (set! (.. canvas -style -height) (str css-h "px")))
          [w h]   (fit-canvas! canvas ctx)
          {:keys [kind opts]} (current-mode)
          opts    (merge (vm/default-opts kind) opts)]
      (if (or (empty? entries) (nil? heard))
        (vd/draw-placeholder! ctx 0 0 w h "viz waits for active tracks")
        (let [row-h (/ h (count entries))]
          (doseq [[idx entry] (map-indexed vector entries)]
            (let [y  (* idx row-h)
                  td (track-data entry kind opts state heard)
                  sm (vd/draw! kind ctx 0 y w row-h td opts)]
              (swap! summaries assoc [(:name entry) kind "panel"]
                     (assoc sm :heard (:heard td) :muted? (:muted? entry)))
              (when (< idx (dec (count entries)))
                (set! (.-fillStyle ctx) vd/grid-color)
                (.fillRect ctx 0 (Math/round (+ y row-h)) w 1))))))
      (contains? vm/audio-kinds kind))))

;;; ── Inline canvases (registered by repulse.ui.inline-viz) ────────────────
;; id → {:canvas el :track kw :visible? bool}
(defonce ^:private canvases (atom {}))

(defn register-canvas! [id canvas track-name]
  (swap! canvases assoc id {:canvas canvas :track track-name :visible? true}))

(defn set-canvas-visible! [id visible?]
  (swap! canvases (fn [m] (if (contains? m id) (assoc-in m [id :visible?] visible?) m))))

(defn unregister-canvas! [id]
  (swap! canvases dissoc id))

(def not-playing-text "not playing — evaluate to start")

(defn track-vizzes
  "The :viz entries of a running track's pattern ([{:kind :opts}] ≤ 3), or nil."
  [track-name]
  (seq (:viz (get-in @audio/scheduler-state [:tracks track-name]))))

(defn draw-canvas!
  "Draw one inline canvas: its track's visualizers stacked, or the
   not-playing placeholder. → true when it drew an audio view."
  [{:keys [^js canvas track]} state heard]
  (let [ctx    (.getContext canvas "2d")
        vizzes (track-vizzes track)
        total  (if vizzes (reduce + (map #(get-in % [:opts :height] 64) vizzes)) 0)]
    (when (and vizzes (not= (str total "px") (.. canvas -style -height)))
      (set! (.. canvas -style -height) (str total "px")))
    (let [[cw ch] (fit-canvas! canvas ctx)]
      (let [entry (some #(when (= track (:name %)) %) (track-entries state heard))]
        (if (or (nil? vizzes) (nil? entry) (nil? heard))
          (do (vd/draw-placeholder! ctx 0 0 cw ch not-playing-text)
              false)
          (loop [[{:keys [kind opts]} & more] vizzes y 0 audio? false]
            (if-not kind
              audio?
              (let [hh (get opts :height 64)
                    td (track-data entry kind opts state heard)
                    sm (vd/draw! kind ctx 0 y cw hh td opts)]
                (swap! summaries assoc [track kind "inline"]
                       (assoc sm :heard (:heard td) :muted? (:muted? entry)))
                (recur more (+ y hh) (or audio? (contains? vm/audio-kinds kind)))))))))))

;;; ── Frame ────────────────────────────────────────────────────────────────

(defn- draw-all! []
  (let [state  @audio/scheduler-state
        heard  (audio/heard-cycle-pos)]
    (reset! used-keys #{})
    (when heard (reset! last-cycle (max 0 (Math/floor heard))))
    (let [panel-audio? (draw-panel! state heard)
          inline-audio? (reduce (fn [acc [_ c]]
                                  (if (and (:visible? c) (.-isConnected ^js (:canvas c)))
                                    (or (draw-canvas! c state heard) acc)
                                    acc))
                                false @canvases)]
      (swap! events-cache select-keys @used-keys)
      (swap! level-history select-keys (keys (:tracks state)))
      (when (and (:playing? state) (or panel-audio? inline-audio?))
        (audio/request-meters!)))))

(defn render-track-panel! []
  (when-let [panel (el "track-panel")]
    (let [entries (track-entries @audio/scheduler-state nil)]
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
                 (when (seq entries)
                   (str "<div class=\"viz-track-mutes\">"
                        (apply str
                          (map (fn [{:keys [name color muted?]}]
                                 (str "<button class=\"viz-track-btn"
                                      (when muted? " muted")
                                      "\" style=\"--track-color:" color "\""
                                      ;; WS5: muting writes :mute into the code;
                                      ;; the anonymous :_ track has no form
                                      (if (= :_ name)
                                        " disabled title=\"name the track to mute it: (track :name …)\""
                                        " title=\"mute — writes :mute into the code\"")
                                      " data-name=\"" (escape-html (cljs.core/name name))
                                      "\">:" (escape-html (cljs.core/name name)) "</button>"))
                               entries))
                        "</div>"))
                 "</div>"
                 "<canvas id=\"track-viz-canvas\" class=\"track-viz-canvas\" width=\"1600\" height=\"126\"></canvas>"))
      (doseq [btn (array-seq (.querySelectorAll panel ".viz-mode-btn"))]
        (.addEventListener btn "click"
          (fn []
            (reset! viz-mode (.. btn -dataset -mode))
            (render-track-panel!))))
      ;; Inline onclick handlers are blocked by the CSP (no unsafe-inline),
      ;; so mute buttons are wired here like the viz-mode buttons above.
      (doseq [btn (array-seq (.querySelectorAll panel ".viz-track-btn"))]
        (.addEventListener btn "click"
          (fn []
            (js/window._repulseMuteToggle (.. btn -dataset -name)))))
      (draw-all!))))

(defn start-viz-raf!
  "The one animation loop for the panel and every inline visualizer. Draws
   only while playing; render-track-panel! (and inline-viz on mount) draw the
   stopped state."
  []
  (when-not @raf-started?
    (reset! raf-started? true)
    (letfn [(frame [_ts]
              (when (audio/playing?)
                (draw-all!))
              (js/requestAnimationFrame frame))]
      (js/requestAnimationFrame frame))))

(defn redraw!
  "Draw everything once (used by inline-viz after mounting a canvas)."
  []
  (draw-all!))
