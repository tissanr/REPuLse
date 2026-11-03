(ns repulse.ui.outline-panel
  "Outline panel (SONG3): a left column listing top-level def/defn/defsynth/
   track forms and the song with its sections.  Entries come from the Lezer
   syntax tree (repulse.outline), so the list survives syntax errors.
   Renders HTML strings; no framework.
   Phase WS1: rows are selectable (click = select/inspect, ⇧-click adds
   tracks, Esc clears, F12 reveals the selection); top-level (fx …) forms are
   listed in one fx group after all entries; MIDI/buses/sources live in a
   collapsible session block above the footer.  The list renders into
   #outline-main (the status head sits above it in #outline-panel).
   Exports: extension, init!, refresh!, schedule-render!, toggle-collapsed!,
            entries, selection, set-selection!, clear-selection!,
            on-selection-change, selection-index, view-ref."
  (:require [repulse.audio :as audio]
            [repulse.outline :as outline]
            [repulse.midi :as midi]
            [repulse.bus :as bus]
            [repulse.samples :as samples]
            [repulse.ui.status-model :as sm]
            [repulse.ui.selection :as sel]
            [repulse.ui.selection-highlight :as sel-hl]
            [repulse.ui.goto :as goto]
            [repulse.ui.html :refer [escape-html]]
            ["@codemirror/view" :refer [EditorView keymap]]
            ["@codemirror/state" :refer [Prec]]
            ["@codemirror/language" :refer [syntaxTree ensureSyntaxTree]]))

(def ^:private STORAGE-KEY "repulse:outline-collapsed")
(def ^:private SESSION-KEY "repulse:outline-session-open")
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
(defonce view-ref (atom nil))

;; Phase WS1 selection: vector of selection ids (repulse.ui.selection), newest
;; last.  on-selection-change is a 0-arg hook set by the inspector.
(defonce selection (atom []))
(defonce on-selection-change (atom nil))

(defn- load-session-open []
  (try (= "1" (.getItem js/localStorage SESSION-KEY))
       (catch :default _ false)))

(defonce session-open? (atom (load-session-open)))
(defonce ^:private render-scheduled? (atom false))
(defonce ^:private extract-timer (atom nil))

(defn- mac? []
  (boolean (re-find #"Mac" (str (.-platform js/navigator)))))

;;; Extraction

(declare schedule-render!)

(defn selection-index
  "{id entry} of the current outline entries."
  []
  (sel/index-entries @entries))

(defn- notify-selection! []
  (when-let [f @on-selection-change] (f)))

(defn- sync-highlight! []
  (sel-hl/set-ranges! @view-ref (sel/ranges @selection (selection-index))))

(defn set-selection!
  "Replace the selection with `ids`, refresh the editor highlight, repaint the
   outline and notify the inspector."
  [ids]
  (reset! selection (vec ids))
  (sync-highlight!)
  (schedule-render!)
  (notify-selection!))

(defn clear-selection! [] (set-selection! []))

(defn- re-extract! [view]
  (let [state (.-state view)
        len   (.. state -doc -length)
        tree  (or (ensureSyntaxTree state len 200) (syntaxTree state))
        res   (outline/extract tree (.toString (.-doc state)))]
    (reset! entries (:entries res))
    (reset! partial? (:partial? res))
    ;; ids that no longer resolve are pruned; positions moved with the edit
    (reset! selection (sel/prune @selection (selection-index)))
    (sync-highlight!)
    (notify-selection!)))

;;; Rendering

(def ^:private kind-label
  {:def "def" :defn "defn" :defsynth "synth" :track "track" :song "song" :fx "fx"})

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
   :dots (vector of colours) :meta :fold? :id (selection id) :sel? :row-act"
  [{:keys [kind name from to line indent cursor? playing? progress muted? off?
           glyph glyph-class dots meta fold? id sel? row-act]}]
  (str "<div class=\"ol-row"
       (when cursor? " ol-cursor")
       (when playing? " ol-playing")
       (when muted? " ol-muted")
       (when off? " ol-off")
       (when sel? " ol-sel")
       "\" data-from=\"" from "\" data-to=\"" to "\""
       (when id (str " data-id=\"" (escape-html id) "\""))
       (when row-act (str " data-act=\"" row-act "\""))
       ">"
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
       (when sel? "<span class=\"ol-sel-mark\">›</span>")
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
  (str "<div class=\"ol-footer\">"
       "<div class=\"ol-hint\"><span>click</span><span>inspect</span>"
       "<span class=\"ol-spacer\"></span>" (kbd "⇧ click") "<span>add</span></div>"
       "<div class=\"ol-hint\">" (kbd "F12") "<span>go to def</span>"
       "<span class=\"ol-spacer\"></span>" (kbd "esc") "<span>close</span></div>"
       "<div class=\"ol-hint\"><span class=\"ol-spacer\"></span>"
       (kbd (if (mac?) "⌘⇧O" "Ctrl+⇧+O")) "<span>hide</span></div>"
       "</div>"))

;;; Session block (MIDI / buses / sources, Phase WS1)

(defn- session-html []
  (let [mappings @midi/cc-mappings
        buses    (bus/active-buses)
        sources  @samples/loaded-sources
        parts    (keep (fn [[label n]] (when (pos? n) (str label " " n)))
                       [["midi" (count mappings)] ["buses" (count buses)]
                        ["sources" (count sources)]])]
    (when (seq parts)
      (str "<div class=\"ol-session" (when @session-open? " open") "\">"
           "<div class=\"ol-session-head\" data-act=\"session\">"
           "<span class=\"ol-session-glyph\">" (if @session-open? "▾" "▸") "</span>"
           "<span class=\"ol-session-title\">session</span>"
           "<span class=\"ol-session-sum\">" (escape-html (apply str (interpose " · " parts))) "</span>"
           "</div>"
           (when @session-open?
             (str "<div class=\"ol-session-body\">"
                  (apply str
                    (map (fn [[cc-num {:keys [target]}]]
                           (str "<div class=\"ol-session-row\">"
                                "<span class=\"ol-session-k\">CC #" cc-num "</span>"
                                "<span class=\"ol-session-v\">"
                                (when target (str "→ " (escape-html (name target))))
                                "</span></div>"))
                         (sort-by key mappings)))
                  (apply str
                    (map (fn [[bus-name {:keys [type]}]]
                           (str "<div class=\"ol-session-row\">"
                                "<span class=\"ol-session-k\">" (escape-html bus-name) "</span>"
                                "<span class=\"ol-session-v\">" (escape-html (name type)) "</span>"
                                "</div>"))
                         (sort-by (comp name key) buses)))
                  (apply str
                    (map (fn [{:keys [type id banks query count]}]
                           (str "<div class=\"ol-session-row ol-session-src\">♫ "
                                (case type
                                  :github    (str "github:" (escape-html id)
                                                  (when (pos? (or banks 0))
                                                    (str " (" banks " banks)")))
                                  :freesound (str "freesound: " (escape-html query)
                                                  (when count (str " (" count ")")))
                                  (escape-html (str id)))
                                "</div>"))
                         sources))
                  "</div>"))
           "</div>"))))

(defn- toggle-session! []
  (swap! session-open? not)
  (try (.setItem js/localStorage SESSION-KEY (if @session-open? "1" "0"))
       (catch :default _ nil))
  (schedule-render!))

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
                   (outline/playing-section (:song (:song state)) (sm/song-bar state) secs))
        play-idx (:index playing)
        cur      (outline/entry-at es @cursor-pos)
        cur-from (:from cur)
        muted    (or (:muted state) #{})
        tnames   (track-names es)
        sec-trk  (when playing (:tracks (nth secs play-idx)))
        line     #(if doc (line-of doc (min % (.-length doc))) 0)
        sel-set  (set @selection)
        fxs      (filterv #(= :fx (:kind %)) es)]
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
                                          :line (line (:from e)) :row-act "fold"
                                          :glyph (if @song-open? "▾" "▸") :glyph-class "ol-fold" :fold? true
                                          :cursor? (= cur-from (:from e))
                                          :meta (when-let [b (outline/song-bars e)] (str b " bars"))})
                               (when @song-open?
                                 (apply str
                                   (map-indexed
                                     (fn [i s]
                                       (let [pl? (= i play-idx)
                                             id  (sel/entry-id s i)]
                                         (row-html {:name (:name s) :from (:from s) :to (:to s)
                                                    :id id :sel? (contains? sel-set id)
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
                                       :id (sel/entry-id e nil)
                                       :sel? (contains? sel-set (sel/entry-id e nil))
                                       :cursor? (= cur-from (:from e))
                                       :muted? m? :off? (and playing (not in?))
                                       :glyph (cond m? "■" aud? "•") :glyph-class (when (or m? aud?) (if m? "ol-g-mute" "ol-g-aud"))}))
                          :fx ""   ; listed in the fx group below
                          (let [id (sel/entry-id e nil)]
                            (row-html {:kind (:kind e) :name (:name e) :from (:from e) :to (:to e)
                                       :line (line (:from e))
                                       :id id :sel? (contains? sel-set id)
                                       :cursor? (= cur-from (:from e))}))))
                      es))
                  (when (seq fxs)
                    (str "<div class=\"ol-group-divider\"></div>"
                         (row-html {:kind :fx :name "fx" :meta (str (count fxs))
                                    :from (:from (first fxs)) :to (:to (last fxs))
                                    :line (line (:from (first fxs)))
                                    :id "fx" :sel? (contains? sel-set "fx")})
                         (apply str
                           (map-indexed
                             (fn [i f]
                               (let [id (sel/entry-id f i)]
                                 (row-html {:name (:name f) :from (:from f) :to (:to f)
                                            :line (line (:from f)) :indent 12
                                            :id id :sel? (contains? sel-set id)
                                            :cursor? (= cur-from (:from f))})))
                             fxs))))
                  "</div>"))
           (session-html)
           (footer-html)))))

(defn render!
  []
  (when-let [panel (.getElementById js/document "outline-panel")]
    (when-let [main (.getElementById js/document "outline-main")]
      (let [list-el (.querySelector main ".ol-list")
            scroll  (when list-el (.-scrollTop list-el))]
        (if @collapsed?
          (.add (.-classList panel) "collapsed")
          (.remove (.-classList panel) "collapsed"))
        (set! (.-innerHTML main) (render-html))
        (when scroll
          (when-let [l (.querySelector main ".ol-list")]
            (set! (.-scrollTop l) scroll)))))))

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
  (when @collapsed? (clear-selection!))
  (try (.setItem js/localStorage STORAGE-KEY (if @collapsed? "1" "0"))
       (catch :default _ nil))
  (render!))

;;; Clicks

(defn- scroll-to-entry!
  "Scroll the entry's start to ~1/3 from the top without moving the cursor,
   then focus the editor (WS1 decision 9)."
  [^js view e]
  (let [len    (.. view -state -doc -length)
        pos    (max 0 (min (:from e) len))
        margin (/ (.. view -dom -clientHeight) 3)]
    (.dispatch view #js {:effects #js [(.scrollIntoView EditorView pos
                                                        #js {:y "start" :yMargin margin})]})
    (.focus view)))

(defn- select-row! [id shift?]
  (set-selection! (sel/pick @selection id shift?))
  (when-let [view @view-ref]
    (if-let [e (sel/primary @selection (selection-index))]
      (scroll-to-entry! view (if (= :fx-group (:kind e)) (first (:children e)) e))
      (.focus view))))

(defn- on-click [^js e]
  (let [target (.-target e)
        act-el (.closest target "[data-act]")
        act    (when act-el (.getAttribute act-el "data-act"))]
    (when-not (.closest target "#status-head")
      (cond
        (contains? #{"collapse" "expand"} act)
        (toggle-collapsed!)

        (= act "fold")
        (do (swap! song-open? not) (render!))

        (= act "session")
        (toggle-session!)

        :else
        (when-let [row (.closest target ".ol-row[data-id]")]
          (select-row! (.getAttribute row "data-id") (.-shiftKey e)))))))

(defn reveal-selection!
  "F12 / ↗ with a selection: move the cursor to the most recently picked
   entry's paren (pushing the jump stack, so Shift+F12 returns).  Returns
   true when something was revealed."
  [^js view]
  (when-let [e (sel/primary @selection (selection-index))]
    (let [e (if (= :fx-group (:kind e)) (first (:children e)) e)]
      (goto/push-jump! (.-state view))
      (goto/reveal! view (:from e) (:from e) (:to e))
      true)))

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
                        :run (fn [_] (toggle-collapsed!) true)}]))
       ;; WS1: Escape / F12 act on the selection first; with an empty selection
       ;; they return false and the existing bindings (command bar, go to
       ;; definition) run.
       (.highest Prec
         (.of keymap
              #js [#js {:key "Escape"
                        :run (fn [_]
                               (if (seq @selection)
                                 (do (clear-selection!) true)
                                 false))}
                   #js {:key "F12"
                        :run (fn [view]
                               (if (seq @selection)
                                 (boolean (reveal-selection! view))
                                 false))}]))])

(defn refresh!
  "Re-extract and repaint after the view's state was swapped (a buffer switch
   via view.setState does not run update listeners) — Phase SONG4."
  [view]
  (reset! view-ref view)
  (reset! cursor-pos (.. view -state -selection -main -head))
  (reset! selection [])
  (re-extract! view)
  (render!))

(defn- on-doc-keydown
  "Escape while focus is inside the outline column or the inspector clears the
   selection and returns focus to the editor."
  [^js e]
  (when (and (= "Escape" (.-key e)) (seq @selection))
    (let [t (.-target e)]
      (when (and (instance? js/Element t)
                 (not (contains? #{"INPUT" "SELECT" "TEXTAREA"} (.-tagName t)))
                 (.closest t "#outline-panel, #inspector"))
        (.preventDefault e)
        (clear-selection!)
        (when-let [v @view-ref] (.focus v))))))

(defn init!
  "Wire the panel to the main editor view: initial extraction, listeners, paint."
  [view]
  (reset! view-ref view)
  (reset! cursor-pos (.. view -state -selection -main -head))
  (re-extract! view)
  (when-let [panel (.getElementById js/document "outline-panel")]
    (.addEventListener panel "click" on-click))
  (.addEventListener js/document "keydown" on-doc-keydown)
  (render!))
