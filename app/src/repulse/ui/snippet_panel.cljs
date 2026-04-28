(ns repulse.ui.snippet-panel
  "Snippet library browser panel — renders, filters, previews, and inserts
   snippets from the community library.
   Exports: init!, show-panel!, hide-panel!, toggle-panel!, visible?"
  (:require [repulse.snippets :as snippets]
            [repulse.audio :as audio]
            [repulse.ui.editor :as editor]
            [repulse.ui.snippet-submit-modal :as submit-modal]
            [repulse.eval-orchestrator :as eo]
            [repulse.auth :as auth]
            [repulse.api :as api]
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
  "Append snippet code to editor, trigger (upd), and track usage."
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
          (js/setTimeout #(eo/evaluate! "(upd)") 50)
          ;; Silently track usage
          (when-let [id (:id snippet)]
            (api/track-usage! id)))))))

;;; Forward declaration (hide-panel! used by wire-panel! and show-panel!)
(declare hide-panel!)

;;; Rendering

(defn- escape-html [s]
  (-> s
      (cstr/replace "&" "&amp;")
      (cstr/replace "<" "&lt;")
      (cstr/replace ">" "&gt;")))

(defn- tag-pill [t]
  (str "<span class=\"snippet-tag\">" (escape-html t) "</span>"))

(defn- logged-in? []
  (some? (auth/session)))

(def ^:private uuid-re
  (js/RegExp. "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$" "i"))

(defn- uuid-id? [id]
  (boolean (and id (.test uuid-re id))))

(defn- render-card [snippet]
  (let [id         (:id snippet)
        title      (:title snippet)
        auth-info  (get-in snippet [:profiles :display_name])
        auth-str   (or auth-info (:author snippet) "repulse")
        tags       (or (:tags snippet) [])
        desc       (or (:description snippet) "")
        code       (or (:code snippet) "")
        star-count (or (:star_count snippet) 0)
        starred    (snippets/starred? id)
        ;; Star/report only available for community snippets (UUID ids from the API)
        can-star   (and (logged-in?) (uuid-id? id))]
    (str "<div class=\"snippet-card\">"
         "<div class=\"snippet-card-top\">"
         "<span class=\"snippet-title\">" (escape-html title) "</span>"
         "<span class=\"snippet-author\">" (escape-html auth-str) "</span>"
         "</div>"
         "<div class=\"snippet-tags\">"
         (cstr/join "" (map tag-pill tags))
         "</div>"
         "<div class=\"snippet-desc\">" (escape-html desc) "</div>"
         "<div class=\"snippet-actions\">"
         "<button class=\"snippet-btn snippet-preview-btn\" data-id=\"" id "\">&#9654; solo</button>"
         "<button class=\"snippet-btn snippet-mix-btn\" data-id=\"" id "\">&oplus; mix</button>"
         "<button class=\"snippet-btn snippet-insert-btn\" data-id=\"" id "\">&#8595; insert</button>"
         "</div>"
         "<div class=\"snippet-meta-row\">"
         "<button class=\"snippet-star-btn" (when starred " snippet-star-btn--on") "\""
         " data-id=\"" id "\""
         (when-not can-star " disabled title=\"Log in to star\"")
         ">&#9733; " star-count "</button>"
         "<button class=\"snippet-report-btn\" data-id=\"" id "\""
         (when-not can-star " disabled title=\"Log in or use a community snippet to report\"")
         ">&#9872; report</button>"
         "</div>"
         "<details class=\"snippet-code-details\">"
         "<summary class=\"snippet-code-summary\">{ } code</summary>"
         "<pre class=\"snippet-code-block\">" (escape-html code) "</pre>"
         "</details>"
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
  "Re-render the toolbar (tag dropdown + sort must reflect loaded tags)."
  []
  (when-let [toolbar (el "snippet-toolbar")]
    (let [all-tags  (snippets/all-tags)
          cur-tag   @tag-filter
          cur-sort  @snippets/sort-order
          cur-auth  (or @snippets/author-filter "")]
      (set! (.-innerHTML toolbar)
            (str "<span class=\"snippet-panel-title\">Snippets</span>"
                 "<input id=\"snippet-search\" class=\"snippet-search\" type=\"text\""
                 " placeholder=\"search\u2026\" value=\"" (or @search-query "") "\" />"
                 "<select id=\"snippet-tag-filter\" class=\"snippet-tag-filter\">"
                 "<option value=\"\">all tags</option>"
                 (cstr/join ""
                            (map (fn [t]
                                   (str "<option value=\"" (escape-html t) "\""
                                        (when (= t cur-tag) " selected")
                                        ">" (escape-html t) "</option>"))
                                 all-tags))
                 "</select>"
                 "<select id=\"snippet-sort\" class=\"snippet-sort-filter\">"
                 "<option value=\"most-starred\"" (when (= cur-sort "most-starred") " selected") ">&#9733; stars</option>"
                 "<option value=\"newest\""      (when (= cur-sort "newest")       " selected") ">&#128337; newest</option>"
                 "<option value=\"most-used\""   (when (= cur-sort "most-used")    " selected") ">&#128200; uses</option>"
                 "<option value=\"trending\""    (when (= cur-sort "trending")     " selected") ">&#128293; trending</option>"
                 "</select>"
                 "<input id=\"snippet-author-filter\" class=\"snippet-author-filter\" type=\"text\""
                 " placeholder=\"author\u2026\" value=\"" (escape-html cur-auth) "\" />"
                 (when (logged-in?)
                   "<button id=\"snippet-share-btn\" class=\"snippet-share-btn\" title=\"Share current code as a snippet\">+ share</button>")
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
      (when-let [sort-sel (el "snippet-sort")]
        (.addEventListener sort-sel "change"
          (fn [^js e]
            (reset! snippets/sort-order (.-value (.-target e)))
            (when-let [cards (el "snippet-cards")]
              (set! (.-innerHTML cards)
                    "<div class=\"snippet-empty\">loading\u2026</div>"))
            (snippets/reload!))))
      (when-let [author-input (el "snippet-author-filter")]
        ;; Debounce: reload 400ms after user stops typing
        (let [timer (atom nil)]
          (.addEventListener author-input "input"
            (fn [^js e]
              (when @timer (js/clearTimeout @timer))
              (reset! timer
                (js/setTimeout
                  (fn []
                    (reset! snippets/author-filter
                            (let [v (cstr/trim (.-value (.-target e)))]
                              (when (seq v) v)))
                    (when-let [cards (el "snippet-cards")]
                      (set! (.-innerHTML cards)
                            "<div class=\"snippet-empty\">loading\u2026</div>"))
                    (snippets/reload!))
                  400))))))
      (when-let [share-btn (el "snippet-share-btn")]
        (.addEventListener share-btn "click" submit-modal/open!))
      (when-let [close-btn (el "snippet-close-btn")]
        (.addEventListener close-btn "click" hide-panel!)))))

;;; Star toggle handler

(defn- handle-star! [snippet-id]
  (when (logged-in?)
    (snippets/toggle-starred! snippet-id)
    (render-cards!)
    (-> (api/toggle-star! snippet-id)
        (.then (fn [result]
                 (when (:error result)
                   ;; Revert optimistic update on error
                   (snippets/toggle-starred! snippet-id)
                   (render-cards!)))))))

;;; Report handler

(defn- handle-report! [snippet-id]
  (when (logged-in?)
    (when-let [reason (js/prompt "Why are you reporting this snippet? (optional)")]
      (-> (api/report-snippet! snippet-id reason)
          (.then (fn [result]
                   (if (:error result)
                     (js/alert (str "Report failed: " (:error result)))
                     (js/alert "Thank you — the snippet has been flagged for review."))))))))

;;; Show / hide

(defn show-panel! []
  (when-let [panel (el "snippet-panel")]
    (reset! visible? true)
    (.remove (.-classList panel) "hidden")
    (.add    (.-classList (el "snippet-toggle-btn")) "active")
    (when (empty? (snippets/all-snippets))
      (when-let [container (el "snippet-cards")]
        (set! (.-innerHTML container)
              "<div class=\"snippet-empty\">loading\u2026</div>")))
    (snippets/load!)
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
          (when-let [s (snippets/by-id id)] (insert-snippet! s))

          (and id (.contains cl "snippet-star-btn"))
          (handle-star! id)

          (and id (.contains cl "snippet-report-btn"))
          (handle-report! id)))))

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
    (set! (.-innerHTML panel)
          (str "<div id=\"snippet-toolbar\" class=\"snippet-panel-bar\">"
               "<span class=\"snippet-panel-title\">Snippets</span>"
               "<input id=\"snippet-search\" class=\"snippet-search\" type=\"text\""
               " placeholder=\"search\u2026\" />"
               "<select id=\"snippet-tag-filter\" class=\"snippet-tag-filter\">"
               "<option value=\"\">all tags</option>"
               "</select>"
               "<select id=\"snippet-sort\" class=\"snippet-sort-filter\">"
               "<option value=\"most-starred\">&#9733; stars</option>"
               "<option value=\"newest\">&#128337; newest</option>"
               "<option value=\"most-used\">&#128200; uses</option>"
               "<option value=\"trending\">&#128293; trending</option>"
               "</select>"
               "<input id=\"snippet-author-filter\" class=\"snippet-author-filter\" type=\"text\""
               " placeholder=\"author\u2026\" />"
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
