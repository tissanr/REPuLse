(ns repulse.ui.snippet-panel
  "Snippet library browser panel — renders, filters, previews, and inserts
   snippets from the community library.
   Exports: init!, show-panel!, hide-panel!, toggle-panel!, visible?"
  (:require [repulse.snippets :as snippets]
            [repulse.audio :as audio]
            [repulse.snippets.preview :as preview]
            [repulse.ui.editor :as editor]
            [repulse.ui.snippet-submit-modal :as submit-modal]
            [repulse.eval-orchestrator :as eo]
            [repulse.auth :as auth]
            [repulse.api :as api]
            [repulse.ai.tools :as ai-tools]
            [repulse.ui.dialog :as dialog]
            [repulse.ui.modal-stack :as modal-stack]
            [repulse.ui.html :refer [escape-html]]
            [clojure.string :as cstr]
            [clojure.set :as cset]))

;;; Panel state

(defonce visible?       (atom false))
(defonce search-query   (atom ""))
(defonce tag-filter     (atom nil))

;;; Forward declarations
(declare hide-panel! render-cards!)

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
  (preview/stop!)
  (preview/render-waveforms!))

(defn- conflict-tracks
  "Returns set of track names in the snippet that already exist in the session."
  [snippet]
  (let [snippet-tracks (extract-track-names (:code snippet))
        active-tracks  (set (keys (:tracks @audio/scheduler-state)))]
    (cset/intersection snippet-tracks active-tracks)))

(defn preview-solo!
  "Stop current session, play snippet in isolation as temporary track(s)."
  [snippet]
  (if (preview/previewing-mode? (:id snippet) :solo)
    (preview/stop!)
    (preview/start! :solo snippet))
  (render-cards!))

(defn preview-mix!
  "Add snippet track(s) alongside the current running session."
  [snippet]
  (if (preview/previewing-mode? (:id snippet) :mix)
    (preview/stop!)
    (preview/start! :mix snippet))
  (render-cards!))

(defn insert-snippet!
  "Append snippet code to editor, trigger (upd), and track usage."
  [snippet]
  (when-let [view @editor/editor-view]
    (let [conflicts (conflict-tracks snippet)
          insert!   (fn []
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
                          (api/track-usage! id))))]
      (if (empty? conflicts)
        (insert!)
        (-> (dialog/confirm!
             (str "Track name(s) already in use: "
                  (cstr/join ", " (map #(str ":" (name %)) conflicts))
                  "\n\nInsert anyway?")
             {:ok-label "Insert"})
            (.then (fn [ok?] (when ok? (insert!)))))))))

;;; Rendering

(defn- tag-pill [t]
  (str "<span class=\"snippet-tag\">" (escape-html t) "</span>"))

(defn- logged-in? []
  (some? (auth/session)))

(def ^:private uuid-re
  (js/RegExp. "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$" "i"))

(defn- uuid-id? [id]
  (boolean (and id (.test uuid-re id))))

(defn- render-stars
  "Render a row of 5 clickable star buttons plus avg-rating display."
  [id my-rating can-star avg-rating star-count]
  (let [safe-id (escape-html (str id))]
    (str "<div class=\"snippet-stars\""
       (when-not can-star " data-disabled=\"true\"")
       ">"
       (apply str
         (for [n (range 1 6)]
           (str "<button class=\"snippet-star"
                (when (<= n my-rating) " snippet-star--filled")
                "\""
                " data-id=\"" safe-id "\""
                " data-rating=\"" n "\""
                (when-not can-star " disabled title=\"Log in to rate\"")
                ">&#9733;</button>")))
       (if (pos? star-count)
         (str "<span class=\"snippet-avg-rating\">"
              (.toFixed avg-rating 1) " avg (" star-count ")</span>")
         "<span class=\"snippet-avg-rating\">no ratings yet</span>")
       "</div>")))

(defn- render-card [snippet]
  (let [id         (:id snippet)
        safe-id    (escape-html (str id))
        playing?   (preview/previewing? id)
        soloing?    (preview/previewing-mode? id :solo)
        mixing?     (preview/previewing-mode? id :mix)
        err        (preview/error-for id)
        title      (:title snippet)
        auth-info  (get-in snippet [:profiles :display_name])
        auth-str   (or auth-info (:author snippet) "repulse")
        tags       (or (:tags snippet) [])
        desc       (or (:description snippet) "")
        code       (or (:code snippet) "")
        star-count (or (:star_count snippet) 0)
        avg-rating (or (:avg_rating snippet) 0)
        my-rating  (snippets/get-rating id)
        ;; Star/report only available for community snippets (UUID ids from the API)
        can-star   (and (logged-in?) (uuid-id? id))
        can-delete (and can-star (= (:author_id snippet) (auth/user-id)))]
    (str "<div class=\"snippet-card"
         (when playing? " snippet-card--playing")
         (when err " snippet-card--error")
         "\""
         (when err (str " title=\"" (escape-html err) "\""))
         ">"
         "<div class=\"snippet-card-top\">"
         "<span class=\"snippet-title-wrap\">"
         "<span class=\"snippet-playing-dot\" aria-hidden=\"true\"></span>"
         "<span class=\"snippet-title\">" (escape-html title) "</span>"
         "</span>"
         "<span class=\"snippet-author\">" (escape-html auth-str) "</span>"
         "</div>"
         "<canvas class=\"snippet-waveform\" data-id=\"" safe-id "\" aria-hidden=\"true\"></canvas>"
         (when err
           (str "<div class=\"snippet-preview-error\">" (escape-html err) "</div>"))
         "<div class=\"snippet-tags\">"
         (cstr/join "" (map tag-pill tags))
         "</div>"
         "<div class=\"snippet-desc\">" (escape-html desc) "</div>"
         "<div class=\"snippet-actions\">"
         "<button class=\"snippet-btn snippet-preview-btn"
         (when soloing? " snippet-btn--active")
         "\" data-id=\"" safe-id "\">" (if soloing? "&#9632; stop" "&#9654; solo") "</button>"
         "<button class=\"snippet-btn snippet-mix-btn"
         (when mixing? " snippet-btn--active")
         "\" data-id=\"" safe-id "\">" (if mixing? "&#9632; stop" "&oplus; mix") "</button>"
         "<button class=\"snippet-btn snippet-insert-btn\" data-id=\"" safe-id "\">&#8595; insert</button>"
         "</div>"
         "<div class=\"snippet-meta-row\">"
         (render-stars id my-rating can-star avg-rating star-count)
         "<button class=\"snippet-report-btn\" data-id=\"" safe-id "\""
         (when-not can-star " disabled title=\"Log in or use a community snippet to report\"")
         ">&#9872; report</button>"
         (when can-delete
           (str "<button class=\"snippet-delete-btn\" data-id=\"" safe-id "\""
                " title=\"Delete your snippet\">delete</button>"))
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
    (if (and @snippets/load-error (empty? (snippets/all-snippets)))
      ;; QA3 E: a failed load says so (instead of "loading…" forever)
      (set! (.-innerHTML container)
            (str "<div class=\"snippet-empty snippet-load-error\" role=\"alert\">"
                 "could not load the snippet library "
                 "<button type=\"button\" class=\"snippet-retry-btn\">retry</button>"
                 "</div>"))
      (let [snips (snippets/filter-snippets @search-query @tag-filter)]
        (set! (.-innerHTML container)
              (if (empty? snips)
                "<div class=\"snippet-empty\">no snippets match</div>"
                (cstr/join "" (map render-card snips))))
        (preview/render-waveforms!)))))

(defn- toolbar-html
  "The toolbar, built once by init! (QA3 E: never re-rendered afterwards, so
   typing in its inputs never loses focus)."
  []
  (str "<span class=\"snippet-panel-title\">Snippets</span>"
       "<input id=\"snippet-search\" class=\"snippet-search\" type=\"text\""
       " placeholder=\"search\u2026\" aria-label=\"search snippets\" />"
       "<select id=\"snippet-tag-filter\" class=\"snippet-tag-filter\" aria-label=\"filter by tag\">"
       "<option value=\"\">all tags</option>"
       "</select>"
       "<select id=\"snippet-sort\" class=\"snippet-sort-filter\" aria-label=\"sort order\">"
       "<option value=\"top-rated\">&#9733; top rated</option>"
       "<option value=\"newest\">&#128337; newest</option>"
       "<option value=\"most-used\">&#128200; uses</option>"
       "<option value=\"trending\">&#128293; trending</option>"
       "</select>"
       "<input id=\"snippet-author-filter\" class=\"snippet-author-filter\" type=\"text\""
       " placeholder=\"author\u2026\" aria-label=\"filter by author\" />"
       "<button type=\"button\" id=\"snippet-share-btn\" class=\"snippet-share-btn\""
       " title=\"Share current code as a snippet\" hidden>+ share</button>"
       "<button type=\"button\" class=\"snippet-close-btn\" id=\"snippet-close-btn\""
       " aria-label=\"close snippet library\">&times;</button>"))

(defonce ^:private shown-tags (atom nil))

(defn- sync-toolbar!
  "Bring the toolbar's controls in line with the state without replacing it:
   tag options only when the tag list changed, select values, input values
   only when the input is not focused, the share button's visibility."
  []
  (when (el "snippet-toolbar")
    (let [all-tags (snippets/all-tags)
          active   (.-activeElement js/document)]
      (when-let [tag-sel (el "snippet-tag-filter")]
        (when (not= all-tags @shown-tags)
          (reset! shown-tags all-tags)
          (set! (.-innerHTML tag-sel)
                (str "<option value=\"\">all tags</option>"
                     (cstr/join ""
                                (map (fn [t]
                                       (str "<option value=\"" (escape-html t) "\">"
                                            (escape-html t) "</option>"))
                                     all-tags)))))
        (set! (.-value tag-sel) (or @tag-filter "")))
      (when-let [sort-sel (el "snippet-sort")]
        (set! (.-value sort-sel) @snippets/sort-order))
      (when-let [search (el "snippet-search")]
        (when-not (identical? search active)
          (set! (.-value search) (or @search-query ""))))
      (when-let [author (el "snippet-author-filter")]
        (when-not (identical? author active)
          (set! (.-value author) (or @snippets/author-filter ""))))
      (when-let [share (el "snippet-share-btn")]
        (set! (.-hidden share) (not (logged-in?)))))))

(defn- show-loading! []
  (when-let [cards (el "snippet-cards")]
    (set! (.-innerHTML cards) "<div class=\"snippet-empty\">loading\u2026</div>")))

(defn- wire-toolbar! []
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
        (when (snippets/server-filtered?) (show-loading!))
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
                (when (snippets/server-filtered?) (show-loading!))
                (snippets/reload!))
              400))))))
  (when-let [share-btn (el "snippet-share-btn")]
    (.addEventListener share-btn "click" submit-modal/open!)))

;;; Rating handler

(defn- handle-rate!
  "Click on star N: if already rated N → remove (0), else set to N."
  [snippet-id n]
  (when (and (logged-in?) (uuid-id? snippet-id))
    (let [prev     (snippets/get-rating snippet-id)
          new-r    (if (= prev n) 0 n)]
      (snippets/set-rating! snippet-id new-r)
      (render-cards!)
      (-> (api/set-rating! snippet-id new-r)
          (.then (fn [result]
                   (when (:error result)
                     ;; Revert optimistic update on error
                     (snippets/set-rating! snippet-id prev)
                     (render-cards!))))))))

;;; Report handler

(defn- handle-report! [snippet-id]
  (when (logged-in?)
    (-> (dialog/prompt! "Why are you reporting this snippet? (optional)" "")
        (.then (fn [reason]
                 (when reason
                   (-> (api/report-snippet! snippet-id reason)
                       (.then (fn [result]
                                (if (:error result)
                                  (dialog/alert! (str "Report failed: " (:error result)))
                                  (dialog/alert! "Thank you — the snippet has been flagged for review.")))))))))))

(defn- handle-delete! [snippet-id]
  (when (and (logged-in?) (uuid-id? snippet-id))
    (-> (dialog/confirm! "Delete this snippet? This cannot be undone."
                         {:ok-label "Delete" :danger? true})
        (.then (fn [ok?]
                 (when ok?
                   (clear-preview!)
                   (-> (api/delete-snippet! snippet-id)
                       (.then (fn [result]
                                (if (:error result)
                                  (dialog/alert! (str "Delete failed: " (:error result)))
                                  (do
                                    (when-let [cards (el "snippet-cards")]
                                      (set! (.-innerHTML cards)
                                            "<div class=\"snippet-empty\">loading\u2026</div>"))
                                    (snippets/reload!))))))))))))

;;; Show / hide

(defn show-panel! []
  (when-let [panel (el "snippet-panel")]
    (reset! visible? true)
    (.remove (.-classList panel) "hidden")
    (.add    (.-classList (el "snippet-toggle-btn")) "active")
    (when (empty? (snippets/all-snippets))
      (show-loading!))
    (sync-toolbar!)
    (if (and @snippets/load-error (not @snippets/loaded?))
      ;; a failed load is retried by the retry button, not by reopening
      (render-cards!)
      (do (snippets/load!)
          (when @snippets/loaded?
            (render-cards!))))))

(defn hide-panel! []
  (when-let [panel (el "snippet-panel")]
    (clear-preview!)
    (reset! visible? false)
    (.add    (.-classList panel) "hidden")
    (when-let [btn (el "snippet-toggle-btn")]
      (.remove (.-classList btn) "active"))))

(defn toggle-panel! []
  (if @visible? (hide-panel!) (show-panel!)))

(defn open-with-search!
  "Open the snippet panel and pre-fill the search query."
  [q]
  (reset! search-query q)
  (show-panel!)
  (when @snippets/loaded?
    (render-cards!)
    (when-let [input (el "snippet-search")]
      (set! (.-value input) q))))

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

          (and id (.contains cl "snippet-star"))
          (let [n (js/parseInt (.. target -dataset -rating))]
            (when-not (js/isNaN n) (handle-rate! id n)))

          (and id (.contains cl "snippet-report-btn"))
          (handle-report! id)

          (and id (.contains cl "snippet-delete-btn"))
          (handle-delete! id)

          (.contains cl "snippet-retry-btn")
          (do (show-loading!)
              (snippets/reload!))))))

  ;; Keyboard: Escape closes panel — not while a modal (dialog, submit modal)
  ;; is open: that Escape belongs to the modal (QA3 C).
  (.addEventListener js/document "keydown"
    (fn [^js e]
      (when (and @visible? (= "Escape" (.-key e)) (not (modal-stack/any-open?)))
        (hide-panel!)))))

;;; Initialization

(defn init!
  "Build the panel DOM skeleton and wire events.
   Must be called after the panel element exists in the DOM."
  []
  (reset! ai-tools/open-snippet-search-fn open-with-search!)
  (preview/init!)
  (when-let [panel (el "snippet-panel")]
    (set! (.-innerHTML panel)
          (str "<div id=\"snippet-toolbar\" class=\"snippet-panel-bar\">"
               (toolbar-html)
               "</div>"
               "<div id=\"snippet-cards\" class=\"snippet-cards\"></div>"))
    (reset! shown-tags nil)
    (wire-toolbar!)
    (wire-panel! panel)

    ;; Watch library-atom: re-render when fetch completes
    (add-watch snippets/library-atom ::panel-update
      (fn [_ _ _ _]
        (when @visible?
          (sync-toolbar!)
          (render-cards!))))

    ;; QA3 E: client-side sort / author filter (anonymous) and load errors
    (add-watch snippets/filter-rev ::filter-update
      (fn [_ _ _ _]
        (when @visible? (render-cards!))))
    (add-watch snippets/load-error ::load-error
      (fn [_ _ _ _]
        (when @visible? (render-cards!))))

    ;; Watch ratings-atom: re-render cards when load-ratings! completes
    ;; (ratings load async, often after the first render)
    (add-watch snippets/ratings ::ratings-update
      (fn [_ _ _ _]
        (when @visible?
          (render-cards!))))))
