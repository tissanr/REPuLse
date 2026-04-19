(ns repulse.ui.snippet-panel
  "Snippet library browser panel — renders, filters, previews, and inserts
   snippets from the curated library.
   Exports: init!, show-panel!, hide-panel!, toggle-panel!, visible?"
  (:require [repulse.snippets :as snippets]
            [repulse.audio :as audio]
            [repulse.ui.editor :as editor]
            [repulse.eval-orchestrator :as eo]
            [clojure.string :as cstr]
            [clojure.set :as cset]))

;;; Panel state

(defonce visible?       (atom false))
(defonce search-query   (atom ""))
(defonce tag-filter     (atom nil))
(defonce preview-tracks (atom #{}))   ; keyword set of tracks added by preview

;;; DOM helpers

(defn- el [id] (.getElementById js/document id))

;;; Track name extraction

(defn- extract-track-names [code]
  (when code
    (->> (re-seq #"\(track\s+:([^\s)]+)" code)
         (map #(keyword (second %)))
         set)))

;;; Preview / insert

(defn- clear-preview! []
  (doseq [k @preview-tracks]
    (audio/clear-track! k))
  (reset! preview-tracks #{}))

(defn- conflict-tracks
  "Returns set of track names in the snippet that already exist in the session,
   excluding any currently previewing tracks."
  [snippet]
  (let [snippet-tracks (extract-track-names (:code snippet))
        active-tracks  (set (keys (:tracks @audio/scheduler-state)))]
    (cset/difference (cset/intersection snippet-tracks active-tracks)
                     @preview-tracks)))

(defn preview-solo!
  "Stop current session, play snippet in isolation as temporary track(s)."
  [snippet]
  (clear-preview!)
  (audio/stop!)
  (let [names (extract-track-names (:code snippet))]
    (reset! preview-tracks names)
    (eo/evaluate! (:code snippet))))

(defn preview-mix!
  "Add snippet track(s) alongside the current running session."
  [snippet]
  (clear-preview!)
  (let [names (extract-track-names (:code snippet))]
    (reset! preview-tracks names)
    (eo/evaluate! (:code snippet))))

(defn insert-snippet!
  "Append snippet code to editor and trigger (upd)."
  [snippet]
  (when-let [view @editor/editor-view]
    (let [conflicts (conflict-tracks snippet)]
      (when (or (empty? conflicts)
                (js/confirm
                  (str "Track name(s) already in use: "
                       (cstr/join ", " (map #(str ":" (name %)) conflicts))
                       "\n\nInsert anyway?")))
        (let [code    (:code snippet)
              doc-len (.. view -state -doc -length)]
          (clear-preview!)
          (.dispatch view
                     #js {:changes #js {:from doc-len
                                        :to   doc-len
                                        :insert (str "\n\n" code)}})
          (js/setTimeout #(eo/evaluate! "(upd)") 50))))))

;;; Forward declaration (hide-panel! used by wire-panel! and show-panel!)
(declare hide-panel!)

;;; Rendering

(defn- tag-pill [t]
  (str "<span class=\"snippet-tag\">" t "</span>"))

(defn- render-card [snippet]
  (let [id    (:id snippet)
        title (:title snippet)
        auth  (or (:author snippet) "repulse")
        tags  (or (:tags snippet) [])
        desc  (or (:description snippet) "")]
    (str "<div class=\"snippet-card\">"
         "<div class=\"snippet-card-top\">"
         "<span class=\"snippet-title\">" title "</span>"
         "<span class=\"snippet-author\">" auth "</span>"
         "</div>"
         "<div class=\"snippet-tags\">"
         (cstr/join "" (map tag-pill tags))
         "</div>"
         "<div class=\"snippet-desc\">" desc "</div>"
         "<div class=\"snippet-actions\">"
         "<button class=\"snippet-btn snippet-preview-btn\" data-id=\"" id "\">&#9654; solo</button>"
         "<button class=\"snippet-btn snippet-mix-btn\" data-id=\"" id "\">&oplus; mix</button>"
         "<button class=\"snippet-btn snippet-insert-btn\" data-id=\"" id "\">&#8595; insert</button>"
         "</div>"
         "</div>")))

(defn- render-cards!
  "Re-render only the cards area, preserving the toolbar."
  []
  (when-let [container (el "snippet-cards")]
    (let [snips (snippets/filter-snippets @search-query @tag-filter)]
      (set! (.-innerHTML container)
            (if (empty? snips)
              "<div class=\"snippet-empty\">no snippets match</div>"
              (cstr/join "" (map render-card snips)))))))

(defn- render-toolbar!
  "Re-render the toolbar (tag dropdown must reflect loaded tags)."
  []
  (when-let [toolbar (el "snippet-toolbar")]
    (let [all-tags (snippets/all-tags)
          cur-tag  @tag-filter]
      (set! (.-innerHTML toolbar)
            (str "<span class=\"snippet-panel-title\">Snippets</span>"
                 "<input id=\"snippet-search\" class=\"snippet-search\" type=\"text\""
                 " placeholder=\"search\u2026\" value=\"" (or @search-query "") "\" />"
                 "<select id=\"snippet-tag-filter\" class=\"snippet-tag-filter\">"
                 "<option value=\"\">all tags</option>"
                 (cstr/join ""
                            (map (fn [t]
                                   (str "<option value=\"" t "\""
                                        (when (= t cur-tag) " selected")
                                        ">" t "</option>"))
                                 all-tags))
                 "</select>"
                 "<button class=\"snippet-close-btn\" id=\"snippet-close-btn\">&times;</button>"))
      ;; Re-wire toolbar input events after re-render
      (when-let [search-input (el "snippet-search")]
        (.addEventListener search-input "input"
          (fn [^js e]
            (reset! search-query (.-value (.-target e)))
            (render-cards!))))
      (when-let [tag-sel (el "snippet-tag-filter")]
        (.addEventListener tag-sel "change"
          (fn [^js e]
            (let [v (.-value (.-target e))]
              (reset! tag-filter (when (seq v) v)))
            (render-cards!))))
      (when-let [close-btn (el "snippet-close-btn")]
        (.addEventListener close-btn "click" hide-panel!)))))

;;; Show / hide

(defn show-panel! []
  (when-let [panel (el "snippet-panel")]
    (reset! visible? true)
    (.remove (.-classList panel) "hidden")
    (.add    (.-classList (el "snippet-toggle-btn")) "active")
    (when (empty? (snippets/all-snippets))
      ;; Show loading state immediately, then re-render when data arrives
      (when-let [container (el "snippet-cards")]
        (set! (.-innerHTML container)
              "<div class=\"snippet-empty\">loading\u2026</div>")))
    (snippets/load!)
    ;; If already loaded, render immediately
    (when @snippets/loaded?
      (render-toolbar!)
      (render-cards!))))

(defn hide-panel! []
  (when-let [panel (el "snippet-panel")]
    (clear-preview!)
    (reset! visible? false)
    (.add    (.-classList panel) "hidden")
    (when-let [btn (el "snippet-toggle-btn")]
      (.remove (.-classList btn) "active"))))

(defn toggle-panel! []
  (if @visible? (hide-panel!) (show-panel!)))

;;; One-time event wiring

(defn- wire-panel! [panel]
  ;; Click delegation for all card buttons + close
  (.addEventListener panel "click"
    (fn [^js e]
      (let [target (.-target e)
            cl     (.-classList target)
            id     (.. target -dataset -id)]
        (cond
          (.contains cl "snippet-close-btn")
          (hide-panel!)

          (and id (.contains cl "snippet-preview-btn"))
          (when-let [s (snippets/by-id id)] (preview-solo! s))

          (and id (.contains cl "snippet-mix-btn"))
          (when-let [s (snippets/by-id id)] (preview-mix! s))

          (and id (.contains cl "snippet-insert-btn"))
          (when-let [s (snippets/by-id id)] (insert-snippet! s))))))

  ;; Keyboard: Escape closes panel
  (.addEventListener js/document "keydown"
    (fn [^js e]
      (when (and @visible? (= "Escape" (.-key e)))
        (hide-panel!)))))

;;; Initialization

(defn init!
  "Build the panel DOM skeleton and wire events.
   Must be called after the panel element exists in the DOM."
  []
  (when-let [panel (el "snippet-panel")]
    ;; Build static structure (toolbar + cards container)
    (set! (.-innerHTML panel)
          (str "<div id=\"snippet-toolbar\" class=\"snippet-panel-bar\">"
               "<span class=\"snippet-panel-title\">Snippets</span>"
               "<input id=\"snippet-search\" class=\"snippet-search\" type=\"text\""
               " placeholder=\"search\u2026\" />"
               "<select id=\"snippet-tag-filter\" class=\"snippet-tag-filter\">"
               "<option value=\"\">all tags</option>"
               "</select>"
               "<button id=\"snippet-close-btn\" class=\"snippet-close-btn\">&times;</button>"
               "</div>"
               "<div id=\"snippet-cards\" class=\"snippet-cards\"></div>"))

    ;; Wire toolbar inputs
    (when-let [search-input (el "snippet-search")]
      (.addEventListener search-input "input"
        (fn [^js e]
          (reset! search-query (.-value (.-target e)))
          (render-cards!))))
    (when-let [tag-sel (el "snippet-tag-filter")]
      (.addEventListener tag-sel "change"
        (fn [^js e]
          (let [v (.-value (.-target e))]
            (reset! tag-filter (when (seq v) v)))
          (render-cards!))))

    (wire-panel! panel)

    ;; Watch library-atom: re-render when fetch completes
    (add-watch snippets/library-atom ::panel-update
      (fn [_ _ _ _]
        (when @visible?
          (render-toolbar!)
          (render-cards!))))))
