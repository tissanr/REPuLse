(ns repulse.ui.timeline
  "SVG track timeline rendering and RAF playhead animation.
   Responsibility: render per-track event bars as SVG and animate the playhead.
   Exports: render-track-panel!, start-playhead-raf!"
  (:require [repulse.core :as core]
            [repulse.audio :as audio]))

(defn- el [id] (.getElementById js/document id))

(defn- render-track-row [track-name pattern cycle muted?]
  (let [sp   {:start [cycle 1] :end [(inc cycle) 1]}
        evs  (try (core/query pattern sp) (catch :default _ []))
        bars (mapv (fn [ev]
                     (let [part (:part ev)
                           pos  (- (core/rat->float (:start part)) cycle)
                           dur  (- (core/rat->float (:end part))
                                   (core/rat->float (:start part)))]
                       {:pos pos :dur (max 0.01 dur)}))
                   evs)
        kw-name (name track-name)]
    (str "<div class=\"track-row" (when muted? " track-muted") "\">"
         "<span class=\"track-name\" onclick=\"window._repulseMuteToggle('" kw-name "')\">"
         kw-name
         "</span>"
         "<div class=\"track-timeline-wrap\">"
         "<svg class=\"track-timeline\" viewBox=\"0 0 100 10\" preserveAspectRatio=\"none\">"
         (apply str (map (fn [{:keys [pos dur]}]
                           (str "<rect x=\"" (* pos 100) "\" y=\"0.5\" "
                                "width=\"" (* dur 100) "\" height=\"9\" "
                                "rx=\"0.5\"/>"))
                         bars))
         "</svg>"
         "<div class=\"track-playhead\" id=\"ph-" kw-name "\"></div>"
         "</div>"
         "</div>")))

(defn render-track-panel! []
  (when-let [panel (el "track-panel")]
    (let [{:keys [tracks muted playing? cycle]} @audio/scheduler-state]
      (if (or (not playing?) (empty? tracks))
        (set! (.-innerHTML panel) "")
        (set! (.-innerHTML panel)
              (apply str (map (fn [[track-name pattern]]
                                (render-track-row track-name pattern cycle
                                                  (contains? muted track-name)))
                              tracks)))))))

(defn start-playhead-raf! []
  (letfn [(frame []
    (let [ac    (when (audio/playing?) @audio/ctx)
          state @audio/scheduler-state]
      (when ac
        (let [now      (.-currentTime ac)
              cyc-dur  (:cycle-dur state)
              frac     (mod (/ now cyc-dur) 1.0)
              pct      (str (* frac 100) "%")]
          (doseq [ph (array-seq (.querySelectorAll js/document ".track-playhead"))]
            (set! (.. ph -style -left) pct)))))
    (js/requestAnimationFrame frame))]
    (js/requestAnimationFrame frame)))
