(ns repulse.ui.status-head
  "Status head at the top of the outline column (Phase WS1): BPM (click to
   edit, ↑↓ ±1, ⇧↑↓ ±10, 40–300), bank prefix and wasm/js badges, a song
   progress bar with one segment per section (click = cue that section at the
   next bar) and the audio latency (or the REC status while (export :song)
   captures).  Renders into #status-head.
   Exports: init!, render!, schedule-render!."
  (:require [repulse.audio :as audio]
            [repulse.samples :as samples]
            [repulse.song-transport :as song-transport]
            [repulse.ui.status-model :as sm]
            [repulse.ui.html :refer [escape-html]]))

(defonce ^:private editing? (atom false))
(defonce ^:private last-html (atom nil))
(defonce ^:private render-scheduled? (atom false))

(defn- el [] (.getElementById js/document "status-head"))

(defn- current-song
  "The song being played, else the last evaluated song buffer's song."
  []
  (or (get-in @audio/scheduler-state [:song :song])
      (:song @song-transport/song-buffer)))

(defn- current-bpm [] (sm/bpm-of (:cycle-dur @audio/scheduler-state)))

;;; HTML

(defn- bpm-html [bpm]
  (if @editing?
    (str "<input class=\"sh-bpm-input\" data-key=\"bpm-input\" inputmode=\"numeric\""
         " value=\"" bpm "\" aria-label=\"BPM\">")
    (str "<span class=\"sh-bpm\" tabindex=\"0\" data-key=\"bpm\" role=\"spinbutton\""
         " aria-valuemin=\"" sm/BPM-MIN "\" aria-valuemax=\"" sm/BPM-MAX "\""
         " aria-valuenow=\"" bpm "\""
         " title=\"click to edit · ↑↓ ±1 · ⇧↑↓ ±10\">" bpm "</span>")))

(defn- progress-html [state song]
  (when song
    (let [bar  (when (and (audio/playing?) (get-in state [:song :song]))
                 (sm/song-bar state))
          segs (sm/progress-segments song bar)
          ttl  (or (sm/song-status-text state)
                   (str "song · " (:total song) " bars — click a section to cue it"))]
      (str "<div class=\"sh-progress\" title=\"" (escape-html ttl) "\">"
           (apply str
             (map (fn [{:keys [index name bars fill playing?]}]
                    (str "<div class=\"sh-seg" (when playing? " playing") "\""
                         " style=\"flex:" bars "\" data-index=\"" index "\""
                         " title=\":" (escape-html (cljs.core/name name)) " · " bars " bars — click to cue\">"
                         "<div class=\"sh-fill\" style=\"width:" (* 100 fill) "%\"></div>"
                         "</div>"))
                  segs))
           "</div>"))))

(defn- meta-html [state]
  (let [ac  @audio/ctx
        rec (sm/rec-status-text state (if ac (.-currentTime ac) 0))]
    (str "<div class=\"sh-meta\">"
         (if rec
           (str "<span class=\"sh-rec\">" (escape-html rec) "</span>")
           (str "<span>latency <span class=\"sh-val\">"
                (escape-html (if ac
                               (sm/latency-text (.-baseLatency ac) (.-outputLatency ac))
                               "—"))
                "</span></span>"))
         "</div>")))

(defn- render-html []
  (let [state @audio/scheduler-state
        bpm   (sm/bpm-of (:cycle-dur state))
        pfx   @samples/active-bank-prefix]
    (str "<div class=\"sh-row\">"
         (bpm-html bpm)
         "<span class=\"sh-label\">BPM</span>"
         "<span class=\"sh-spacer\"></span>"
         (when pfx (str "<span class=\"sh-badge sh-bank\" title=\"bank prefix\">"
                        (escape-html (str pfx)) "</span>"))
         "<span class=\"sh-badge sh-backend\" title=\"audio backend\">"
         (if @audio/worklet-ready? "wasm" "js") "</span>"
         "</div>"
         (progress-html state (current-song))
         (meta-html state))))

;;; Render

(defn render!
  "Repaint unless the HTML is unchanged; keeps focus on the element whose
   data-key had it."
  []
  (when-let [root (el)]
    (let [html (render-html)]
      (when-not (= html @last-html)
        (let [active  (.-activeElement js/document)
              fkey    (when (and active (.contains root active))
                        (.getAttribute active "data-key"))]
          (reset! last-html html)
          (set! (.-innerHTML root) html)
          (when fkey
            (when-let [n (.querySelector root (str "[data-key=\"" fkey "\"]"))]
              (.focus n))))))))

(defn schedule-render!
  "RAF-coalesced repaint; skipped while the BPM field is being edited."
  []
  (when (and (not @render-scheduled?) (not @editing?))
    (reset! render-scheduled? true)
    (js/requestAnimationFrame
      (fn []
        (reset! render-scheduled? false)
        (when-not @editing? (render!))))))

;;; BPM editing

(defn- open-editor! []
  (reset! editing? true)
  (render!)
  (when-let [^js input (.querySelector (el) ".sh-bpm-input")]
    (.focus input)
    (.select input)))

(defn- close-editor! [refocus?]
  (when @editing?
    (reset! editing? false)
    (render!)
    (when refocus?
      (when-let [n (.querySelector (el) ".sh-bpm")] (.focus n)))))

(defn- commit! [^js input refocus?]
  (if-let [n (sm/parse-bpm (.-value input))]
    (do (audio/set-bpm! n)
        (close-editor! refocus?))
    (.add (.-classList input) "sh-invalid")))

;;; Events

(defn- on-click [^js e]
  (let [t (.-target e)]
    (cond
      (.closest t ".sh-bpm")
      (open-editor!)

      (.closest t ".sh-seg")
      (let [i (js/parseInt (.getAttribute (.closest t ".sh-seg") "data-index") 10)]
        (when-let [song (current-song)]
          (when-let [bar (sm/segment-cue-bar song i)]
            (song-transport/cue-bar! bar)))))))

(defn- on-keydown [^js e]
  (let [t   (.-target e)
        key (.-key e)]
    (cond
      (.contains (.-classList t) "sh-bpm-input")
      (case key
        "Enter"  (do (.preventDefault e) (commit! t true))
        "Escape" (do (.preventDefault e) (.stopPropagation e) (close-editor! true))
        (.remove (.-classList t) "sh-invalid"))

      (.contains (.-classList t) "sh-bpm")
      (case key
        ("ArrowUp" "ArrowDown")
        (do (.preventDefault e)
            (audio/set-bpm! (sm/step-bpm (current-bpm) (if (= key "ArrowUp") 1 -1)
                                         (.-shiftKey e))))
        ("Enter" " ")
        (do (.preventDefault e) (open-editor!))
        nil)

      :else nil)))

(defn- on-focusout [^js e]
  (let [t (.-target e)]
    (when (and @editing? (.contains (.-classList t) "sh-bpm-input"))
      ;; blur commits like Enter; an invalid value is dropped (the field would
      ;; otherwise stay open without focus)
      (if (sm/parse-bpm (.-value t))
        (commit! t false)
        (close-editor! false)))))

(defn init!
  "Attach delegated listeners once and paint."
  []
  (when-let [root (el)]
    (.addEventListener root "click" on-click)
    (.addEventListener root "keydown" on-keydown)
    (.addEventListener root "focusout" on-focusout))
  (render!))
