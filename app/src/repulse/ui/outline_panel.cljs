(ns repulse.ui.outline-panel
  "Outline panel (SONG3): a left column listing top-level def/defn/defsynth/
   track forms and the song with its sections.  Entries come from the Lezer
   syntax tree (repulse.outline), so the list survives syntax errors.
   Renders HTML strings like context_panel.cljs; no framework.
   Exports: extension, init!, schedule-render!, toggle-collapsed!."
  (:require [clojure.string :as str]
            [repulse.audio :as audio]
            [repulse.outline :as outline]
            [repulse.ui.context-panel :as ctx-panel]
            [repulse.ui.goto :as goto]
            [repulse.ui.html :refer [escape-html]]
            ["@codemirror/view" :refer [EditorView keymap]]
            ["@codemirror/state" :refer [Prec]]
            ["@codemirror/language" :refer [syntaxTree ensureSyntaxTree]]))

(def ^:private STORAGE-KEY "repulse:outline-collapsed")
(def ^:private TRACK-COLORS
  ["#e94560" "#56b6c2" "#e5c07b" "#c678dd" "#98c379" "#d19a66"])

;;; State

(defn- load-collapsed []
  (try (case (.getItem js/localStorage STORAGE-KEY)
         "1" true
         "0" false
         (<= (.-innerWidth js/window) 760))
       (catch :default _ (<= (.-innerWidth js/window) 760))))

(defonce entries     (atom []))
(defonce partial?    (atom false))
(defonce cursor-pos  (atom 0))
(defonce collapsed?  (atom (load-collapsed)))
(defonce song-open?  (atom true))
(defonce ^:private view-ref (atom nil))
(defonce ^:private render-scheduled? (atom false))
(defonce ^:private extract-timer (atom nil))

(defn- mac? []
  (boolean (re-find #"Mac" (str (.-platform js/navigator)))))

;;; Extraction

(defn- re-extract! [view]
  (let [state (.-state view)
        len   (.. state -doc -length)
        tree  (or (ensureSyntaxTree state len 200) (syntaxTree state))
        res   (outline/extract tree (.toString (.-doc state)))]
    (reset! entries (:entries res))
    (reset! partial? (:partial? res))))

;;; Rendering

(def ^:private kind-label
  {:def "def" :defn "defn" :defsynth "synth" :track "track" :song "song"})

(defn- line-of [text-doc pos]
  (.-number (.lineAt text-doc pos)))

(defn- track-names [es]
  (vec (keep #(when (= :track (:kind %)) (:name %)) es)))

(defn- dot-color [tnames n]
  (if-let [i (first (keep-indexed #(when (= %2 n) %1) tnames))]
    (nth TRACK-COLORS (mod i (count TRACK-COLORS)))
    "#5a6a8a"))

(defn- row-html
  "One outline row.  opts: :glyph :indent :cursor? :playing? :progress :muted? :off?
   :dots (vector of colours) :meta :fold?"
  [{:keys [kind name from to line indent cursor? playing? progress muted? off?
           glyph glyph-class dots meta fold?]}]
  (str "<div class=\"ol-row"
       (when cursor? " ol-cursor")
       (when playing? " ol-playing")
       (when muted? " ol-muted")
       (when off? " ol-off")
       "\" data-from=\"" from "\" data-to=\"" to "\">"
       "<span class=\"ol-glyph" (when glyph-class (str " " glyph-class)) "\""
       (when fold? " data-act=\"fold\"") ">" (or glyph "") "</span>"
       (when (and indent (pos? indent))
         (str "<span style=\"width:" indent "px;flex-shrink:0\"></span>"))
       (when kind
         (str "<span class=\"ol-kind ol-kind-" (clojure.core/name kind) "\">"
              (escape-html (kind-label kind)) "</span>"))
       "<span class=\"ol-name\">" (escape-html (or name ""))
       (when meta (str "<span class=\"ol-meta\">" (escape-html meta) "</span>"))
       "</span>"
       (when (seq dots)
         (str "<span class=\"ol-dots\">"
              (apply str (map #(str "<span class=\"ol-dot\" style=\"background:" % "\"></span>") dots))
              "</span>"))
       "<span class=\"ol-line\">L" line "</span>"
       (when playing?
         (str "<div class=\"ol-progress\" style=\"width:calc((100% - 18px) * "
              (or progress 0) ")\"></div>"))
       "</div>"))

(defn- header-html [n]
  (str "<div class=\"ol-header\">"
       "<span class=\"ol-title\">outline</span>"
       "<span class=\"ol-count\">" n "</span>"
       (when @partial?
         "<span class=\"ol-partial\" title=\"Syntax error — showing what still parses\">~ partial</span>")
       "<span class=\"ol-spacer\"></span>"
       "<button class=\"ol-collapse\" data-act=\"collapse\" title=\"Hide outline  "
       (if (mac?) "⌘⇧O" "Ctrl+Shift+O") "\">‹</button>"
       "</div>"))

(defn- kbd [s] (str "<kbd class=\"ol-kbd\">" s "</kbd>"))

(defn- footer-html []
  (let [mod (if (mac?) "⌘" "Ctrl")]
    (str "<div class=\"ol-footer\">"
         "<div class=\"ol-hint\">" (kbd (str mod " click")) (kbd "F12") "<span>go to def</span></div>"
         "<div class=\"ol-hint\">" (kbd "⇧F12") "<span>back</span>"
         "<span class=\"ol-spacer\"></span>"
         (kbd (str mod (if (mac?) "⇧O" "+⇧+O"))) "<span>hide</span></div>"
         "</div>")))

(defn- empty-html []
  (str "<div class=\"ol-empty\">No definitions yet.<br>"
       "<span class=\"ol-p\">(</span><span class=\"ol-k\">def</span> "
       "<span class=\"ol-n\">name</span> …<span class=\"ol-p\">)</span> and<br>"
       "<span class=\"ol-p\">(</span><span class=\"ol-k\">track</span> "
       "<span class=\"ol-c\">:name</span> …<span class=\"ol-p\">)</span><br>"
       "appear here.</div>"))

(defn- rail-html [song-entry playing-idx]
  (let [secs  (:children song-entry)
        total (reduce + 0 (map #(or (:bars %) 1) secs))]
    (str "<div class=\"ol-rail\" data-act=\"expand\" title=\"Show outline  "
         (if (mac?) "⌘⇧O" "Ctrl+Shift+O") "\">"
         "<div class=\"ol-rail-btn\">›</div>"
         "<div class=\"ol-rail-label\">outline</div>"
         "<div class=\"ol-rail-bars\">"
         (apply str
                (map-indexed
                  (fn [i s]
                    (str "<div class=\"ol-rail-bar" (when (= i playing-idx) " playing")
                         "\" title=\"" (escape-html (:name s)) "\" style=\"height:"
                         (max 3 (* 120 (/ (or (:bars s) 1) (max 1 total)))) "px\"></div>"))
                  secs))
         "</div></div>")))

(defn- render-html []
  (let [state    @audio/scheduler-state
        es       @entries
        view     @view-ref
        doc      (when view (.. view -state -doc))
        n        (reduce + 0 (map #(inc (count (:children %))) es))
        song-e   (first (filter #(= :song (:kind %)) es))
        secs     (:children song-e)
        playing  (when (and song-e (audio/playing?))
                   (outline/playing-section (:song (:song state)) (ctx-panel/song-bar state) secs))
        play-idx (:index playing)
        cur      (outline/entry-at es @cursor-pos)
        cur-from (:from cur)
        muted    (or (:muted state) #{})
        tnames   (track-names es)
        sec-trk  (when playing (:tracks (nth secs play-idx)))
        line     #(if doc (line-of doc (min % (.-length doc))) 0)]
    (if @collapsed?
      (rail-html song-e play-idx)
      (str (header-html n)
           (if (empty? es)
             (empty-html)
             (str "<div class=\"ol-list\">"
                  (apply str
                    (map
                      (fn [e]
                        (case (:kind e)
                          :song
                          (str (row-html {:kind :song :from (:from e) :to (:to e)
                                          :line (line (:from e))
                                          :glyph (if @song-open? "▾" "▸") :glyph-class "ol-fold" :fold? true
                                          :cursor? (= cur-from (:from e))
                                          :meta (when-let [b (outline/song-bars e)] (str b " bars"))})
                               (when @song-open?
                                 (apply str
                                   (map-indexed
                                     (fn [i s]
                                       (let [pl? (= i play-idx)]
                                         (row-html {:name (:name s) :from (:from s) :to (:to s)
                                                    :line (line (:from s)) :indent 12
                                                    :meta (when (:bars s) (str (:bars s) "b"))
                                                    :cursor? (= cur-from (:from s))
                                                    :playing? pl?
                                                    :progress (when pl?
                                                                (/ (:section-bar playing) (max 1 (:section-bars playing))))
                                                    :glyph (when pl? "▶") :glyph-class (when pl? "ol-g-play")
                                                    :dots (map #(dot-color tnames %)
                                                               (if (= [":all"] (:tracks s)) tnames (:tracks s)))})))
                                     (:children e)))))
                          :track
                          (let [m?   (contains? muted (keyword (subs (:name e) 1)))
                                in?  (and sec-trk (or (= [":all"] sec-trk) (some #{(:name e)} sec-trk)))
                                aud? (and playing (not m?) in?)]
                            (row-html {:kind :track :name (:name e) :from (:from e) :to (:to e)
                                       :line (line (:from e))
                                       :cursor? (= cur-from (:from e))
                                       :muted? m? :off? (and playing (not in?))
                                       :glyph (cond m? "■" aud? "•") :glyph-class (when (or m? aud?) (if m? "ol-g-mute" "ol-g-aud"))}))
                          (row-html {:kind (:kind e) :name (:name e) :from (:from e) :to (:to e)
                                     :line (line (:from e))
                                     :cursor? (= cur-from (:from e))})))
                      es))
                  "</div>"))
           (footer-html)))))

(defn render!
  []
  (when-let [panel (.getElementById js/document "outline-panel")]
    (let [list-el (.querySelector panel ".ol-list")
          scroll  (when list-el (.-scrollTop list-el))]
      (if @collapsed?
        (.add (.-classList panel) "collapsed")
        (.remove (.-classList panel) "collapsed"))
      (set! (.-innerHTML panel) (render-html))
      (when scroll
        (when-let [l (.querySelector panel ".ol-list")]
          (set! (.-scrollTop l) scroll))))))

(defn schedule-render!
  "Request an outline repaint via RAF (coalesced)."
  []
  (when-not @render-scheduled?
    (reset! render-scheduled? true)
    (js/requestAnimationFrame
      (fn []
        (reset! render-scheduled? false)
        (render!)))))

;;; Collapse

(defn toggle-collapsed! []
  (swap! collapsed? not)
  (try (.setItem js/localStorage STORAGE-KEY (if @collapsed? "1" "0"))
       (catch :default _ nil))
  (render!))

;;; Clicks

(defn- on-click [^js e]
  (let [target (.-target e)
        act-el (.closest target "[data-act]")
        act    (when act-el (.getAttribute act-el "data-act"))]
    (cond
      (contains? #{"collapse" "expand"} act)
      (toggle-collapsed!)

      (= act "fold")
      (do (swap! song-open? not) (render!))

      :else
      (when-let [row (.closest target ".ol-row")]
        (when-let [view @view-ref]
          (let [from (js/parseInt (.getAttribute row "data-from") 10)
                to   (js/parseInt (.getAttribute row "data-to") 10)]
            (goto/reveal! view from from to)))))))

;;; Extension + init

(defn- schedule-extract! [view]
  (when-let [t @extract-timer] (js/clearTimeout t))
  (reset! extract-timer
          (js/setTimeout
            (fn []
              (reset! extract-timer nil)
              (re-extract! view)
              (schedule-render!))
            150)))

(def extension
  #js [(.of EditorView.updateListener
            (fn [^js u]
              (let [view (.-view u)]
                (reset! view-ref view)
                (when (.-docChanged u) (schedule-extract! view))
                (when (or (.-docChanged u) (.-selectionSet u))
                  (reset! cursor-pos (.. u -state -selection -main -head))
                  (schedule-render!)))))
       (.high Prec
         (.of keymap
              #js [#js {:key "Mod-Shift-o"
                        :run (fn [_] (toggle-collapsed!) true)}]))])

(defn init!
  "Wire the panel to the main editor view: initial extraction, listeners, paint."
  [view]
  (reset! view-ref view)
  (reset! cursor-pos (.. view -state -selection -main -head))
  (re-extract! view)
  (when-let [panel (.getElementById js/document "outline-panel")]
    (.addEventListener panel "click" on-click))
  (render!))
