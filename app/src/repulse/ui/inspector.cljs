(ns repulse.ui.inspector
  "Inspector column (Phase WS1), right of the outline: open only while
   something is selected in the outline, and shows only that thing —
   a track (M/S, param sliders, fx chain), several tracks, a section (bar
   strip, ▶ cue, its tracks with M/S + amp), a def/defn/defsynth (highlighted
   source preview + used by) or the top-level fx (their sliders).
   Sliders write back into the code through repulse.eval-orchestrator (one
   undo step per drag; ⇧-drag is 10× finer).  Renders into #inspector.
   Exports: init!, render!, schedule-render!, track-param."
  (:require [clojure.string :as str]
            [repulse.core :as core]
            [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.outline :as outline]
            [repulse.song-transport :as song-transport]
            [repulse.eval-orchestrator :as eo]
            [repulse.ui.editor :as editor]
            [repulse.ui.goto :as goto]
            [repulse.ui.outline-panel :as outline-panel]
            [repulse.ui.selection :as sel]
            [repulse.ui.slider-model :as slm]
            [repulse.ui.status-model :as stm]
            [repulse.ui.inspector-model :as im]
            [repulse.ui.html :refer [escape-html]]
            ["@lezer/highlight" :refer [highlightCode]]
            ["@codemirror/language" :refer [syntaxTree]]
            ["../lisp-lang/highlight.js" :refer [repulseLispSyntaxTheme]]))

(def ^:private WIDTH-KEY "repulse:inspector-width")
(def ^:private W-MIN 180)
(def ^:private W-MAX 380)
(def ^:private W-DEFAULT 220)
(def ^:private PREVIEW-LINES 12)
(def ^:private MAX-BAR-CELLS 64)
(def ^:private TRACK-COLORS
  ["#e94560" "#56b6c2" "#e5c07b" "#c678dd" "#98c379" "#d19a66"])

(defn- clamp-width [w] (max W-MIN (min W-MAX w)))

(defn- load-width []
  (try (let [v (js/parseFloat (.getItem js/localStorage WIDTH-KEY))]
         (if (js/isNaN v) W-DEFAULT (clamp-width v)))
       (catch :default _ W-DEFAULT)))

(defonce ^:private width (atom (load-width)))
(defonce ^:private dragging? (atom false))
(defonce ^:private drag (atom nil))        ; active slider drag state
(defonce ^:private last-html (atom nil))
(defonce ^:private render-scheduled? (atom false))

(defn- root [] (.getElementById js/document "inspector"))

;;; Live data

(defn- track-kw [track-name] (keyword (str/replace (str track-name) #"^:" "")))

(defn track-param
  "Live value of `param` (keyword) for `track` (keyword) from the playing
   pattern, or nil."
  [track param]
  (when-let [pat (get (:tracks @audio/scheduler-state) track)]
    (try
      (get (im/track-params (map :value (core/query pat {:start [0 1] :end [1 1]})))
           param)
      (catch :default _ nil))))

(defn- params-of [^js plugin]
  (try (js->clj (.getParams plugin)) (catch :default _ {})))

(defn- live-track-params [kw]
  (when-let [pat (get (:tracks @audio/scheduler-state) kw)]
    (try (im/track-params (map :value (core/query pat {:start [0 1] :end [1 1]})))
         (catch :default _ {}))))

(defn- track-fx-chain [kw]
  (mapv (fn [{:keys [name plugin bypassed?]}]
          {:name name :bypassed? bypassed? :params (params-of plugin)})
        (:fx-chain (get @audio/track-nodes kw))))

(defn- current-song []
  (or (get-in @audio/scheduler-state [:song :song])
      (:song @song-transport/song-buffer)))

(defn- line-of [from]
  (when-let [view @editor/editor-view]
    (let [doc (.. view -state -doc)]
      (.-number (.lineAt doc (max 0 (min from (.-length doc))))))))

(defn- track-color [track-names n]
  (if-let [i (first (keep-indexed #(when (= %2 n) %1) track-names))]
    (nth TRACK-COLORS (mod i (count TRACK-COLORS)))
    "#5a6a8a"))

;;; Controls

(defn- slider-html
  "label + custom slider + value. ids: {:key :track :fx :param :at}."
  [label v cfg {:keys [key track fx param at]}]
  (let [t   (slm/value->t cfg v)
        pct (* 100 t)
        [lo hi] (if-let [vs (seq (:values cfg))] [(first vs) (last vs)] [(:min cfg) (:max cfg)])]
    (str "<div class=\"ins-srow\">"
         "<span class=\"ins-plabel\">" (escape-html label) "</span>"
         "<div class=\"ins-slider\" tabindex=\"0\" role=\"slider\""
         " aria-label=\"" (escape-html label) "\""
         " aria-valuemin=\"" lo "\" aria-valuemax=\"" hi "\" aria-valuenow=\"" v "\""
         " title=\"" lo "–" hi " · drag, ⇧ fine · ←→ step, ⇧ ×10\""
         " data-key=\"" (escape-html key) "\""
         " data-track=\"" (escape-html (or track "")) "\""
         " data-fx=\"" (escape-html (or fx "")) "\""
         " data-param=\"" (escape-html param) "\""
         " data-at=\"" (if (some? at) at "") "\""
         " data-value=\"" v "\">"
         "<div class=\"ins-rail\"></div>"
         "<div class=\"ins-fill\" style=\"width:" pct "%\"></div>"
         "<div class=\"ins-knob\" style=\"left:calc(" pct "% - 4px)\"></div>"
         "</div>"
         "<span class=\"ins-pval\">" (escape-html (slm/fmt-pv v)) "</span>"
         "</div>")))

(defn- select-html [label v opts {:keys [key track fx param at]}]
  (let [cur (str/replace (str v) #"^:" "")]
    (str "<div class=\"ins-srow ins-selrow\">"
         "<span class=\"ins-plabel\">" (escape-html label) "</span>"
         "<select class=\"ins-select\""
         " data-key=\"" (escape-html key) "\""
         " data-track=\"" (escape-html (or track "")) "\""
         " data-fx=\"" (escape-html (or fx "")) "\""
         " data-param=\"" (escape-html param) "\""
         " data-at=\"" (if (some? at) at "") "\">"
         (apply str (map (fn [o]
                           (str "<option value=\"" (escape-html o) "\""
                                (when (= o cur) " selected") ">" (escape-html o) "</option>"))
                         opts))
         "</select><span></span></div>")))

(defn- jump-html [from]
  (when from
    (str "<span class=\"ins-jump\" data-act=\"jump\" data-from=\"" from "\""
         " title=\"Go to code  F12\">↗ L" (line-of from) "</span>")))

(defn- ms-html [tname {:keys [muted? solo?]}]
  (str "<button class=\"ins-ms ins-m" (when muted? " on") "\" data-act=\"mute\""
       " data-track=\"" (escape-html tname) "\" title=\"mute\">M</button>"
       "<button class=\"ins-ms ins-s" (when solo? " on") "\" data-act=\"solo\""
       " data-track=\"" (escape-html tname) "\" title=\"solo\">S</button>"))

(defn- label [s] (str "<div class=\"ins-lbl\">" s "</div>"))

(defn- fx-chain-html
  "Controls for a list of fx-rows. owner: track name string, or nil for the
   top-level (master) effects."
  [rows owner show-head? jumps]
  (apply str
    (map-indexed
      (fn [i {:keys [name pos bypassed? sliders selects]}]
        (str "<div class=\"ins-fx\">"
             (when show-head?
               (str "<div class=\"ins-fx-head\"><span class=\"ins-dim\">" pos "</span>"
                    "<span class=\"ins-fx-name\">" (escape-html name) "</span>"
                    (when bypassed? "<span class=\"ins-off\">off</span>")
                    "<span class=\"ins-spacer\"></span>"
                    (jump-html (nth jumps i nil))
                    "</div>"))
             (apply str (map (fn [[p v cfg]]
                               (slider-html p v cfg {:key (str (or owner "fx") "|" name "|" p)
                                                     :track owner :fx name :param p
                                                     :at (when-not owner (nth jumps i nil))}))
                             sliders))
             (apply str (map (fn [[p v opts]]
                               (select-html p v opts {:key (str (or owner "fx") "|" name "|" p)
                                                      :track owner :fx name :param p
                                                      :at (when-not owner (nth jumps i nil))}))
                             selects))
             "</div>"))
      rows)))

;;; Blocks

(defn- track-block [entry head?]
  (let [state  @audio/scheduler-state
        tname  (subs (:name entry) 1)
        kw     (keyword tname)
        tracks (:tracks state)
        pat    (get tracks kw)
        flags  (im/track-flags (keys tracks) (:muted state) kw)
        resting (when-not (or (:muted? flags) (:solo? flags))
                  (stm/inactive-section state kw))
        status (cond (:muted? flags) "muted"
                     (:solo? flags)  "solo"
                     resting         (str "rests in :" (name resting))
                     :else           "live")
        tnames (mapv :name (filter #(= :track (:kind %)) @outline-panel/entries))]
    (str "<div class=\"ins-blk\">"
         (when head?
           (str "<div class=\"ins-thead\">"
                "<span class=\"ins-dot\" style=\"background:" (track-color tnames (:name entry)) "\"></span>"
                "<span class=\"ins-tname\">" (escape-html (:name entry)) "</span>"
                "<span class=\"ins-spacer\"></span>"
                (jump-html (:from entry))
                "</div>"))
         (if-not pat
           "<div class=\"ins-note\">not evaluated — Alt+Enter</div>"
           (let [{:keys [sliders text]} (im/param-rows (live-track-params kw))
                 fxrows (im/fx-rows (track-fx-chain kw))]
             (str "<div class=\"ins-msrow\">" (ms-html tname flags)
                  "<span class=\"ins-status\">" (escape-html status) "</span></div>"
                  (when (or (seq sliders) (seq text))
                    (str (label "params")
                         (apply str (map (fn [[k v cfg]]
                                           (slider-html (name k) v cfg
                                                        {:key (str tname "|" (name k))
                                                         :track tname :param (name k)}))
                                         sliders))
                         (apply str (map (fn [[k s]]
                                           (str "<div class=\"ins-trow\"><span class=\"ins-plabel\">"
                                                (escape-html (name k)) "</span>"
                                                "<span class=\"ins-tval\">" (escape-html s) "</span></div>"))
                                         text))))
                  (when (seq fxrows)
                    (str (label "fx chain") (fx-chain-html fxrows tname true []))))))
         "</div>")))

(defn- section-block [entry index]
  (let [state   @audio/scheduler-state
        es      @outline-panel/entries
        song-e  (first (filter #(= :song (:kind %)) es))
        secs    (:children song-e)
        song    (current-song)
        playing (when (audio/playing?)
                  (outline/playing-section (get-in state [:song :song]) (stm/song-bar state) secs))
        mine?   (= index (:index playing))
        bars    (:bars entry)
        bar     (if mine? (:section-bar playing) -1)
        cue-bar (when song (stm/section-cue-bar song secs index))
        tentries (filterv #(= :track (:kind %)) es)
        tnames  (mapv :name tentries)
        tracks  (im/section-tracks entry tentries)
        muted   (:muted state)
        tkeys   (keys (:tracks state))]
    (str "<div class=\"ins-blk\">"
         "<div class=\"ins-secrow\"><span>" (if bars (str (slm/fmt-pv bars) " bars") "? bars") "</span>"
         "<span class=\"ins-spacer\"></span>"
         (if mine?
           (str "<span class=\"ins-barpos\">▶ bar " (inc bar) "/" bars "</span>")
           (str "<button class=\"ins-cue\" data-act=\"cue\""
                (if cue-bar
                  (str " data-bar=\"" cue-bar "\" title=\"cue this section at the next bar\"")
                  " disabled title=\"evaluate first\"")
                ">▶ cue</button>"))
         "</div>"
         (when (and bars (pos? bars))
           (let [n (min MAX-BAR-CELLS (int bars))]
             (str "<div class=\"ins-bars\">"
                  (apply str
                    (map (fn [i]
                           (str "<div class=\"ins-bar"
                                (cond (= i bar) " cur" (< i bar) " done")
                                "\">" (when (or (zero? i) (= i (dec n)) (= i bar)) (inc i)) "</div>"))
                         (range n)))
                  "</div>")))
         (label (str "tracks · " (count tracks)))
         (apply str
           (map (fn [tn]
                  (let [kw    (track-kw tn)
                        flags (im/track-flags tkeys muted kw)
                        amp   (:amp (live-track-params kw))
                        tname (name kw)]
                    (str "<div class=\"ins-sectrack" (when (:muted? flags) " muted") "\">"
                         "<div class=\"ins-thead\">"
                         "<span class=\"ins-dot\" style=\"background:" (track-color tnames tn) "\"></span>"
                         "<span class=\"ins-tname2\">" (escape-html tn) "</span>"
                         (ms-html tname flags)
                         "</div>"
                         (when (number? amp)
                           (slider-html "amp" amp (:amp slm/SLIDER-PARAMS)
                                        {:key (str "sec|" tname "|amp") :track tname :param "amp"}))
                         "</div>")))
                tracks))
         "</div>")))

(defn- preview-html [entry]
  (when-let [view @editor/editor-view]
    (let [state (.-state view)
          code  (.toString (.-doc state))
          from  (max 0 (min (:from entry) (count code)))
          to    (max from (min (:to entry) (count code)))
          out   (array)
          lines (atom 1)
          cut?  (atom false)]
      (highlightCode code (syntaxTree state) repulseLispSyntaxTheme
                     (fn [text classes]
                       (when-not @cut?
                         (.push out (if (seq classes)
                                      (str "<span class=\"" classes "\">" (escape-html text) "</span>")
                                      (escape-html text)))))
                     (fn []
                       (when-not @cut?
                         (if (>= @lines PREVIEW-LINES)
                           (do (reset! cut? true) (.push out "\n…"))
                           (do (swap! lines inc) (.push out "\n")))))
                     from to)
      (str "<pre class=\"ins-pre\">" (.join out "") "</pre>"))))

(defn- def-block [entry]
  (let [users (outline/used-by @outline-panel/entries (:name entry))]
    (str "<div class=\"ins-blk\">"
         (preview-html entry)
         "<div class=\"ins-note\">"
         (if (seq users)
           (str "Used by " (escape-html (str/join ", " users)) ".")
           "Not used by any track or def.")
         "</div></div>")))

(defn- fx-block [fx-entries]
  (let [names  (mapv #(str/replace (:name %) #"^:" "") fx-entries)
        active (filter :active? @fx/chain)
        found  (mapv (fn [n] (some #(when (= n (:name %)) %) active)) names)
        rows   (im/fx-rows (mapv (fn [n e]
                                   {:name n
                                    :bypassed? (:bypassed? e)
                                    :params (if e (params-of (:plugin e)) {})})
                                 names found))]
    (str "<div class=\"ins-blk\">"
         (fx-chain-html rows nil (> (count rows) 1) (mapv :from fx-entries))
         (when (some nil? found)
           "<div class=\"ins-note\">not active yet — Alt+Enter</div>")
         "<div class=\"ins-foot\">applies after all tracks</div>"
         "</div>")))

;;; Render

(defn- body-html [ids idx]
  (if (> (count ids) 1)
    (str (apply str (map #(track-block (idx %) true) ids))
         "<div class=\"ins-hint\">⇧ click a track to add / remove</div>")
    (let [id (first ids)
          e  (idx id)]
      (case (:kind e)
        :track    (track-block e false)
        :section  (section-block e (js/parseInt (subs id (count "section:")) 10))
        (:def :defn :defsynth) (def-block e)
        :fx       (fx-block [e])
        :fx-group (fx-block (:children e))
        :master   (fx-block (:children e))
        :master-fx (fx-block [e])
        ""))))

(defn- render-html []
  (let [idx (outline-panel/selection-index)
        ids (sel/prune @outline-panel/selection idx)]
    (when (seq ids)
      (let [{:keys [kind title from]} (sel/header ids idx)]
        (str "<div class=\"ins-header\">"
             "<span class=\"ins-kind ins-kind-" (str/replace kind " " "-") "\">" (escape-html kind) "</span>"
             "<span class=\"ins-title\">" (escape-html title) "</span>"
             (jump-html from)
             "<kbd class=\"ol-kbd ins-esc\" data-act=\"close\" title=\"close  Esc\">esc</kbd>"
             "</div>"
             "<div class=\"ins-body\">" (body-html ids idx) "</div>"
             "<div class=\"ins-resize\" title=\"drag to resize\"></div>")))))

(defn render!
  "Repaint (or hide) the inspector; keeps the focused control focused."
  []
  (when-let [r (root)]
    (set! (.. r -style -width) (str @width "px"))
    (let [html (render-html)]
      (if (nil? html)
        (do (.add (.-classList r) "hidden")
            (when @last-html
              (set! (.-innerHTML r) "")
              (reset! last-html nil)))
        (do
          (.remove (.-classList r) "hidden")
          (when-not (= html @last-html)
            (let [active (.-activeElement js/document)
                  fkey   (when (and active (.contains r active))
                           (.getAttribute active "data-key"))
                  body   (.querySelector r ".ins-body")
                  scroll (when body (.-scrollTop body))]
              (reset! last-html html)
              (set! (.-innerHTML r) html)
              (when scroll
                (when-let [b (.querySelector r ".ins-body")] (set! (.-scrollTop b) scroll)))
              (when fkey
                (when-let [n (.querySelector r (str "[data-key=\"" fkey "\"]"))]
                  (.focus n))))))))))

(defn schedule-render!
  "RAF-coalesced repaint; skipped while a slider is dragged."
  []
  (when (and (not @render-scheduled?) (not @dragging?))
    (reset! render-scheduled? true)
    (js/requestAnimationFrame
      (fn []
        (reset! render-scheduled? false)
        (when-not @dragging? (render!))))))

;;; Slider interaction

(defn- slider-info [^js s]
  (let [^js ds (.-dataset s)
        fx (.-fx ds)
        tr (.-track ds)
        p  (.-param ds)
        at (js/parseInt (.-at ds) 10)]
    {:fx    (when (seq fx) fx)
     :at    (when-not (js/isNaN at) at)
     :track (when (seq tr) tr)
     :param p
     :cfg   (if (seq fx)
              (get-in slm/FX-SLIDER-PARAMS [fx p])
              (get slm/SLIDER-PARAMS (keyword p)))
     :value (js/parseFloat (.-value ds))}))

(defn- apply-value!
  "Show v on the slider element and write it into the code."
  [^js s {:keys [fx track param cfg at]} v]
  (let [pct (* 100 (slm/value->t cfg v))]
    (set! (.. s -dataset -value) (str v))
    (.setAttribute s "aria-valuenow" (str v))
    (when-let [^js f (.querySelector s ".ins-fill")] (set! (.. f -style -width) (str pct "%")))
    (when-let [^js k (.querySelector s ".ins-knob")] (set! (.. k -style -left) (str "calc(" pct "% - 4px)")))
    (when-let [^js lbl (some-> s .-parentNode (.querySelector ".ins-pval"))]
      (set! (.-textContent lbl) (slm/fmt-pv v)))
    (cond
      (and fx track) (eo/per-track-fx-slider-patch-and-eval! track fx param v)
      fx             (eo/fx-slider-patch-and-eval! fx param v at)
      :else          (eo/slider-patch-and-eval! track param v))))

(defn- on-pointerdown [^js e]
  (let [t (.-target e)]
    (cond
      (.closest t ".ins-slider")
      (let [^js s (.closest t ".ins-slider")
            info  (slider-info s)]
        (when (:cfg info)
          (.preventDefault e)
          (.focus s)
          (let [rect   (.getBoundingClientRect s)
                w      (max 1 (.-width rect))
                v0     (if (.-shiftKey e)
                         (:value info)
                         (slm/t->value (:cfg info) (/ (- (.-clientX e) (.-left rect)) w)))]
            (eo/begin-slider-drag!)
            (reset! dragging? true)
            (reset! drag {:el s :info info :v0 v0 :acc 0 :x (.-clientX e) :w w :last v0})
            (try (.setPointerCapture s (.-pointerId e)) (catch :default _ nil))
            (when-not (== v0 (:value info))
              (apply-value! s info v0)))))

      (.closest t ".ins-resize")
      (let [r  (root)
            x0 (.-clientX e)
            w0 @width]
        (.preventDefault e)
        (try (.setPointerCapture (.closest t ".ins-resize") (.-pointerId e)) (catch :default _ nil))
        (reset! drag {:resize true :x0 x0 :w0 w0 :root r})))))

(defn- on-pointermove [^js e]
  (when-let [d @drag]
    (if (:resize d)
      (let [w (clamp-width (+ (:w0 d) (- (.-clientX e) (:x0 d))))]
        (reset! width w)
        (set! (.. (:root d) -style -width) (str w "px")))
      (let [dx  (- (.-clientX e) (:x d))
            acc (+ (:acc d) (* dx (if (.-shiftKey e) 0.1 1)))
            v   (slm/drag-value (:cfg (:info d)) (:v0 d) acc (:w d) false)]
        (swap! drag assoc :acc acc :x (.-clientX e) :last v)
        (when-not (== v (:last d))
          (apply-value! (:el d) (:info d) v))))))

(defn- on-pointerup [_]
  (when-let [d @drag]
    (reset! drag nil)
    (if (:resize d)
      (try (.setItem js/localStorage WIDTH-KEY (str @width)) (catch :default _ nil))
      (do (eo/end-slider-drag!)
          (reset! dragging? false)
          (schedule-render!)))))

(defn- on-keydown [^js e]
  (let [t (.-target e)]
    (when (.contains (.-classList t) "ins-slider")
      (let [dir (case (.-key e)
                  ("ArrowRight" "ArrowUp")  1
                  ("ArrowLeft" "ArrowDown") -1
                  nil)]
        (when dir
          (.preventDefault e)
          (let [info (slider-info t)]
            (when (:cfg info)
              (let [v (slm/key-step (:cfg info) (:value info) dir (.-shiftKey e))]
                (when-not (== v (:value info))
                  (eo/begin-slider-drag!)
                  (apply-value! t info v)
                  (eo/end-slider-drag!))))))))))

(defn- on-change [^js e]
  (let [t (.-target e)]
    (when (.contains (.-classList t) "ins-select")
      (let [^js ds (.-dataset t)
            fx (.-fx ds)
            tr (.-track ds)
            p  (.-param ds)
            at (js/parseInt (.-at ds) 10)
            at (when-not (js/isNaN at) at)
            v  (.-value t)]
        (eo/begin-slider-drag!)
        (if (seq tr)
          (eo/per-track-fx-select-patch-and-eval! tr fx p v)
          (eo/fx-select-patch-and-eval! fx p v at))
        (eo/end-slider-drag!)))))

;;; Buttons

(defn- run-action! [[op kw]]
  (case op
    :mute       (audio/mute-track! kw)
    :unmute     (audio/unmute-track! kw)
    :solo       (audio/solo-track! kw)
    :unmute-all (doseq [m (:muted @audio/scheduler-state)] (audio/unmute-track! m))))

(defn- on-click [^js e]
  (let [t      (.-target e)
        act-el (.closest t "[data-act]")
        act    (when act-el (.getAttribute act-el "data-act"))
        state  @audio/scheduler-state]
    (case act
      "mute"  (let [kw (keyword (.getAttribute act-el "data-track"))]
                (run-action! (im/mute-action (:muted state) kw)))
      "solo"  (let [kw (keyword (.getAttribute act-el "data-track"))]
                (run-action! (im/solo-action (keys (:tracks state)) (:muted state) kw)))
      "cue"   (when-let [b (.getAttribute act-el "data-bar")]
                (song-transport/cue-bar! (js/parseInt b 10)))
      "jump"  (when-let [view @editor/editor-view]
                (let [from (js/parseInt (.getAttribute act-el "data-from") 10)
                      e    (some #(when (= from (:from %)) %)
                                 (vals (outline-panel/selection-index)))]
                  (goto/push-jump! (.-state view))
                  (goto/reveal! view from from (or (:to e) from))))
      "close" (do (outline-panel/clear-selection!)
                  (when-let [v @editor/editor-view] (.focus v)))
      nil)))

(defn init!
  "Wire listeners once and register as the outline's selection hook."
  []
  (when-let [r (root)]
    (set! (.. r -style -width) (str @width "px"))
    (.addEventListener r "pointerdown" on-pointerdown)
    (.addEventListener r "pointermove" on-pointermove)
    (.addEventListener r "pointerup" on-pointerup)
    (.addEventListener r "pointercancel" on-pointerup)
    (.addEventListener r "keydown" on-keydown)
    (.addEventListener r "change" on-change)
    (.addEventListener r "click" on-click))
  (reset! outline-panel/on-selection-change schedule-render!)
  (render!))
