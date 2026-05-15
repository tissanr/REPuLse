(ns repulse.ui.assistant-panel
  "AI assistant chat panel — BYO API key, streaming responses, code insert.
   Exports: init!, show-panel!, hide-panel!, toggle-panel!, send!, visible?,
            add-message!, set-pending!, get-builtins-summary, log-tool-call!"
  (:require [repulse.ai.settings :as settings]
            [repulse.ai.agent-loop :as agent-loop]
            [repulse.ai.budget :as budget]
            [repulse.ai.undo :as undo]
            [repulse.ui.editor :as editor]
            [clojure.string :as str]))

;;; Forward declarations
(declare render-panel!)

;; ── History persistence ───────────────────────────────────────────────────────

(defn- load-history []
  (try
    (when-let [raw (.getItem js/localStorage "repulse:ai:history")]
      (js->clj (js/JSON.parse raw) :keywordize-keys true))
    (catch :default _ nil)))

(defn- save-history! [msgs]
  (try
    (.setItem js/localStorage "repulse:ai:history"
              (js/JSON.stringify (clj->js (take-last 40 msgs))))
    (catch :default _ nil)))

(defn- clear-history! []
  (try
    (.removeItem js/localStorage "repulse:ai:history")
    (catch :default _ nil)))

;; ── State ─────────────────────────────────────────────────────────────────────

(defonce visible?                   (atom false))
(defonce messages                   (atom (or (load-history) [])))
(defonce ^:private pending          (atom false))
(defonce ^:private builtins-summary (atom ""))
(defonce ^:private activity-log     (atom []))
(defonce ^:private log-expanded?    (atom false))

;; ── DOM helper ────────────────────────────────────────────────────────────────

(defn- el [id] (.getElementById js/document id))

;; ── Escape helper ─────────────────────────────────────────────────────────────

(defn- escape-html [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

;; ── Insert code into editor ───────────────────────────────────────────────────

(defn- insert-code! [code]
  (when-let [view @editor/editor-view]
    (let [doc-len (.. view -state -doc -length)
          nl      (if (> doc-len 0) "\n\n" "")]
      (.dispatch view #js {:changes #js {:from doc-len :to doc-len
                                         :insert (str nl code)}}))))

;; ── Public message / state API (used by agent-loop) ─────────────────────────

(defn add-message!
  "Append a message to the chat and re-render. role is \"user\", \"assistant\", or \"tool\"."
  [role content]
  (let [new-msgs (conj @messages {:role role :content (str content)})]
    (reset! messages new-msgs)
    ;; Tool-status lines are ephemeral — don't persist them to localStorage
    (when (not= role "tool")
      (save-history! new-msgs))
    (render-panel!)))

(defn set-pending!
  "Set the pending (typing indicator) state and re-render."
  [v]
  (reset! pending v)
  (render-panel!))

(defn get-builtins-summary
  "Return the current built-ins vocabulary summary string."
  []
  @builtins-summary)

(defn log-tool-call!
  "Append a tool call record to the activity log (max 50 entries)."
  [tool-name args result]
  (swap! activity-log
         (fn [log]
           (let [entry {:ts (.now js/Date) :tool tool-name :args args :result result}
                 log   (conj log entry)]
             (if (> (count log) 50) (subvec log 1) log)))))

(defn- export-activity-log! []
  (let [json (js/JSON.stringify (clj->js @activity-log) nil 2)
        blob (js/Blob. #js [json] #js {:type "application/json"})
        url  (.createObjectURL js/URL blob)
        a    (.createElement js/document "a")]
    (set! (.-href a) url)
    (set! (.-download a) "repulse-ai-activity-log.json")
    (.click a)
    (js/setTimeout #(.revokeObjectURL js/URL url) 1000)))

;; ── Markdown renderer ────────────────────────────────────────────────────────
;;
;; Converts a plain-text Markdown string to an HTML fragment.
;; Security model: escape-html runs first on every text node; inline-md only
;; ever injects hard-coded tag names — never raw content — so XSS is impossible.

(defn- inline-md
  "Apply inline Markdown transforms to an already HTML-escaped string.
   Inline-code runs first so backtick content is immune to bold/italic."
  [s]
  (-> s
      (str/replace #"`([^`\n]+)`"           "<code>$1</code>")
      (str/replace #"\*\*\*([^*\n]+)\*\*\*" "<strong><em>$1</em></strong>")
      (str/replace #"\*\*([^*\n]+)\*\*"     "<strong>$1</strong>")
      (str/replace #"\*([^*\n]+)\*"         "<em>$1</em>")))

(defn- render-md-block
  "Render a non-code Markdown segment to HTML.
   Handles: ### headings, - / * unordered lists, 1. ordered lists,
   inline-code, bold, italic, paragraphs.

   Two list-specific behaviours:
   - Blank lines between list items are ignored (lookahead: only close the
     list when the next real line is NOT another list item).
   - Non-list lines immediately after a list item are treated as continuations
     of that item (appended to the last <li>) rather than starting a new <p>.
     This handles models that put inline-code snippets on their own line."
  [raw]
  (let [lines      (vec (str/split-lines raw))
        n          (count lines)
        out        (atom [])
        list-open  (atom nil)
        next-real  (fn [i]
                     (loop [j (inc i)]
                       (cond (>= j n)                        nil
                             (seq (str/trim (nth lines j)))  (str/trim (nth lines j))
                             :else                           (recur (inc j)))))
        list-line? (fn [s]
                     (and s (or (re-matches #"[-*] .+" s)
                                (re-matches #"\d+\. .+" s))))]
    (loop [i 0]
      (when (< i n)
        (let [t (str/trim (nth lines i))]
          (cond
            ;; blank — only close the list if the next real line is not a list item
            (empty? t)
            (when (and @list-open (not (list-line? (next-real i))))
              (swap! out conj (if (= @list-open :ul) "</ul>" "</ol>"))
              (reset! list-open nil))

            ;; heading (# / ## / ###)
            (re-matches #"#{1,3} .+" t)
            (let [[_ hashes text] (re-matches #"(#{1,3}) (.*)" t)
                  tag (get ["h3" "h4" "h5"] (dec (count hashes)) "h5")]
              (when @list-open
                (swap! out conj (if (= @list-open :ul) "</ul>" "</ol>"))
                (reset! list-open nil))
              (swap! out conj (str "<" tag ">" (inline-md (escape-html text)) "</" tag ">")))

            ;; unordered list item (- or *)
            (re-matches #"[-*] .+" t)
            (let [text (str/replace-first t #"^[-*] " "")]
              (when (not= @list-open :ul)
                (when @list-open (swap! out conj "</ol>"))
                (swap! out conj "<ul>")
                (reset! list-open :ul))
              (swap! out conj (str "<li>" (inline-md (escape-html text)) "</li>")))

            ;; ordered list item (N.)
            (re-matches #"\d+\. .+" t)
            (let [text (str/replace-first t #"^\d+\. " "")]
              (when (not= @list-open :ol)
                (when @list-open (swap! out conj "</ul>"))
                (swap! out conj "<ol>")
                (reset! list-open :ol))
              (swap! out conj (str "<li>" (inline-md (escape-html text)) "</li>")))

            ;; continuation line inside an open list — append to the last <li>
            @list-open
            (swap! out update (dec (count @out))
                   #(str/replace % #"</li>$" (str " " (inline-md (escape-html t)) "</li>")))

            ;; plain paragraph
            :else
            (swap! out conj (str "<p>" (inline-md (escape-html t)) "</p>"))))
        (recur (inc i))))
    (when @list-open
      (swap! out conj (if (= @list-open :ul) "</ul>" "</ol>")))
    (str/join "" @out)))

;; ── Message rendering ─────────────────────────────────────────────────────────

(defn- render-message [{:keys [role content]}]
  (let [parts (str/split (str content) #"```")]
    (str "<div class=\"ai-msg ai-msg--" (name role) "\">"
         (str/join ""
           (map-indexed
             (fn [i part]
               (if (odd? i)
                 ;; Code block — strip optional language tag on first line
                 (let [lines (str/split-lines part)
                       lang? (and (seq lines)
                                  (re-matches #"[a-zA-Z-]+" (str/trim (first lines))))
                       code  (str/join "\n" (if lang? (rest lines) lines))]
                   (str "<pre><code>" (escape-html code) "</code>"
                        "<button class=\"ai-insert-btn\""
                        " data-code=\"" (escape-html code)
                        "\">&#8595; insert</button></pre>"))
                 ;; Non-code segment — render as Markdown
                 (render-md-block part)))
             parts))
         "</div>")))

;; ── Settings drawer ───────────────────────────────────────────────────────────

(defn- render-settings! []
  (when-let [panel (el "ai-panel")]
    (set! (.-innerHTML panel)
          (str "<form class=\"ai-settings\" onsubmit=\"return false\" autocomplete=\"off\">"
               "<h3 class=\"ai-settings-title\">AI Settings</h3>"
               ;; Each field uses explicit for= on <label> + matching id on control.
               ;; Hint text is a <span> OUTSIDE the <label> to avoid association issues.
               "<div class=\"ai-field\">"
               "  <label for=\"ai-provider-sel\" class=\"ai-label\">Provider</label>"
               "  <select id=\"ai-provider-sel\" class=\"ai-select\">"
               (str/join ""
                 (map (fn [p]
                        (str "<option value=\"" p "\""
                             (when (= p @settings/provider) " selected")
                             ">" p "</option>"))
                      ["anthropic" "openai" "google" "groq" "xai"]))
               "  </select>"
               "</div>"
               "<div class=\"ai-field\">"
               "  <label for=\"ai-key-input\" class=\"ai-label\">API key</label>"
               "  <span class=\"ai-key-hint\">Stored in localStorage only — never sent to REPuLse servers</span>"
               "  <input id=\"ai-key-input\" class=\"ai-input-text\" type=\"password\""
               "   autocomplete=\"new-password\""
               "   value=\"" (escape-html @settings/api-key) "\">"
               "</div>"
               "<div class=\"ai-field\">"
               "  <label for=\"ai-model-input\" class=\"ai-label\">Model override</label>"
               "  <span class=\"ai-key-hint\">Leave blank for provider default</span>"
               "  <input id=\"ai-model-input\" class=\"ai-input-text\" type=\"text\""
               "   autocomplete=\"off\""
               "   value=\"" (escape-html @settings/model-override) "\">"
               "</div>"
               "<div class=\"ai-field\">"
               "  <label class=\"ai-checkbox-label\">"
               "    <input id=\"ai-include-code-cb\" type=\"checkbox\""
               (when @settings/include-code? " checked")
               "> Share editor code with AI"
               "  </label>"
               "</div>"
               "<div class=\"ai-field\">"
               "  <label class=\"ai-checkbox-label\">"
               "    <input id=\"ai-auto-apply-cb\" type=\"checkbox\""
               (when @settings/auto-apply? " checked")
               "> Auto-apply edits (adds Revert button)"
               "  </label>"
               "</div>"
               "<h4 class=\"ai-settings-section\">Budget (this session)</h4>"
               (let [{:keys [tokens-used calls-used]} (budget/get-state)
                     lims (budget/limits)]
                 (str
                   "<div class=\"ai-field\">"
                   "  <label for=\"ai-token-budget\" class=\"ai-label\">Token limit</label>"
                   "  <span class=\"ai-key-hint\">Remaining: " (max 0 (- (:tokens lims) tokens-used)) "</span>"
                   "  <input id=\"ai-token-budget\" class=\"ai-input-text\" type=\"number\""
                   "   min=\"1000\" step=\"1000\" value=\"" (:tokens lims) "\">"
                   "</div>"
                   "<div class=\"ai-field\">"
                   "  <label for=\"ai-call-budget\" class=\"ai-label\">Tool-call limit</label>"
                   "  <span class=\"ai-key-hint\">Remaining: " (max 0 (- (:calls lims) calls-used)) "</span>"
                   "  <input id=\"ai-call-budget\" class=\"ai-input-text\" type=\"number\""
                   "   min=\"1\" step=\"1\" value=\"" (:calls lims) "\">"
                   "</div>"))
               "<h3 class=\"ai-settings-title ai-settings-subtitle\">Sample &amp; Search</h3>"
               "<div class=\"ai-field\">"
               "  <label for=\"ai-freesound-key-input\" class=\"ai-label\">Freesound API key</label>"
               "  <span class=\"ai-key-hint\">Get a key at freesound.org/apiv2/ &mdash; stored in localStorage only</span>"
               "  <div class=\"ai-key-row\">"
               "  <input id=\"ai-freesound-key-input\" class=\"ai-input-text\" type=\"password\""
               "   autocomplete=\"new-password\""
               "   value=\"" (escape-html (or @settings/freesound-api-key "")) "\">"
               "  <button id=\"ai-freesound-key-toggle\" class=\"ai-btn ai-btn--secondary ai-btn--sm\" type=\"button\">show</button>"
               "  <button id=\"ai-freesound-key-clear\" class=\"ai-btn ai-btn--secondary ai-btn--sm\" type=\"button\">clear</button>"
               "  </div>"
               "</div>"
               "<div class=\"ai-field\">"
               "  <label for=\"ai-search-key-input\" class=\"ai-label\">Web Search API key <em class=\"ai-opt\">(optional)</em></label>"
               "  <span class=\"ai-key-hint\">Brave Search &mdash; brave.com/search/api/ &mdash; omit to disable web_search tool</span>"
               "  <div class=\"ai-key-row\">"
               "  <input id=\"ai-search-key-input\" class=\"ai-input-text\" type=\"password\""
               "   autocomplete=\"new-password\""
               "   value=\"" (escape-html (or @settings/search-api-key "")) "\">"
               "  <button id=\"ai-search-key-toggle\" class=\"ai-btn ai-btn--secondary ai-btn--sm\" type=\"button\">show</button>"
               "  <button id=\"ai-search-key-clear\" class=\"ai-btn ai-btn--secondary ai-btn--sm\" type=\"button\">clear</button>"
               "  </div>"
               "</div>"
               "<div class=\"ai-settings-btns\">"
               "<button id=\"ai-settings-save-btn\" class=\"ai-btn\" type=\"button\">Save</button>"
               "<button id=\"ai-settings-back-btn\" class=\"ai-btn ai-btn--secondary\" type=\"button\">Back</button>"
               "</div>"
               "</form>"))))

;; ── Main panel render ─────────────────────────────────────────────────────────

(defn render-panel! []
  (when-let [panel (el "ai-panel")]
    (if-not @settings/enabled?
      (set! (.-innerHTML panel)
            (str "<div class=\"ai-disabled\">"
                 "<p>AI assistant is disabled.</p>"
                 "<p class=\"ai-key-hint\">Enable it to chat with an AI about your patterns.<br>"
                 "You will need an API key from Anthropic, OpenAI, Google, or Groq.</p>"
                 "<button id=\"ai-enable-btn\" class=\"ai-btn\">Enable AI assistant</button>"
                 "</div>"))
      (do
        (let [{:keys [tokens-used calls-used]} (budget/get-state)
              {:keys [tokens calls]} (budget/limits)
              log-count (count @activity-log)]
          (set! (.-innerHTML panel)
                (str "<div class=\"ai-header\">"
                     "<span class=\"ai-provider-badge\">"
                     (escape-html @settings/provider) " &middot; " (escape-html (settings/effective-model))
                     "</span>"
                     "<span class=\"ai-budget-badge\" title=\"Session token / call usage\">"
                     tokens-used "/" tokens " tok &middot; " calls-used "/" calls " calls"
                     "</span>"
                     "<button id=\"ai-settings-btn\" class=\"ai-icon-btn\" title=\"AI settings\" aria-label=\"AI settings\">&#9881;</button>"
                     "<button id=\"ai-clear-btn\" class=\"ai-clear-btn\" type=\"button\" title=\"Clear chat\" aria-label=\"Clear chat\">clear</button>"
                     "</div>"
                     ;; Undo button — only shown when auto-apply is on and stack has entries
                     (when (and @settings/auto-apply? (pos? (undo/stack-size)))
                       "<div class=\"ai-undo-bar\">"
                       "<button id=\"ai-revert-btn\" class=\"ai-btn ai-btn--secondary\">&#8617; Revert last turn</button>"
                       "</div>")
                     "<div id=\"ai-messages\" class=\"ai-messages\">"
                     (str/join "" (map render-message @messages))
                     (when @pending
                       "<div class=\"ai-msg ai-msg--assistant ai-thinking\">&#8230;</div>")
                     "</div>"
                     "<div class=\"ai-input-row\">"
                     "<textarea id=\"ai-input\" class=\"ai-textarea\""
                     " placeholder=\"Describe a pattern, ask for help…\" rows=\"2\"></textarea>"
                     (if @pending
                       "<button id=\"ai-cancel-btn\" class=\"ai-btn ai-btn--secondary\">cancel</button>"
                       "<button id=\"ai-send-btn\" class=\"ai-btn\">send</button>")
                     "</div>"
                     ;; Activity log section — only shown when there are entries
                     (when (pos? log-count)
                       (str "<div class=\"ai-log-section\">"
                            "<button id=\"ai-log-toggle-btn\" class=\"ai-log-toggle\">"
                            "&#128269; tool log (" log-count ")"
                            (if @log-expanded? " &#9650;" " &#9660;")
                            "</button>"
                            (when @log-expanded?
                              (str "<div class=\"ai-log-entries\">"
                                   (str/join ""
                                     (map (fn [{:keys [tool ts]}]
                                            (str "<div class=\"ai-log-entry\">"
                                                 (escape-html (str tool)) "</div>"))
                                          (take-last 10 @activity-log)))
                                   "</div>"
                                   "<div class=\"ai-log-btns\">"
                                   "<button id=\"ai-log-export-btn\" class=\"ai-btn ai-btn--secondary\">Export log</button>"
                                   "<button id=\"ai-log-reset-btn\" class=\"ai-btn ai-btn--secondary\">Reset session</button>"
                                   "</div>"))
                            "</div>")))))
        ;; Scroll messages to bottom
        (when-let [msgs-el (el "ai-messages")]
          (set! (.-scrollTop msgs-el) (.-scrollHeight msgs-el)))))))

;; ── Panel visibility ──────────────────────────────────────────────────────────

(defn show-panel! []
  (reset! visible? true)
  (when-let [panel (el "ai-panel")]
    (.remove (.-classList panel) "hidden"))
  (render-panel!))

(defn hide-panel! []
  (reset! visible? false)
  (when-let [panel (el "ai-panel")]
    (.add (.-classList panel) "hidden")))

(defn toggle-panel! []
  (if @visible? (hide-panel!) (show-panel!)))

;; ── Send a message ────────────────────────────────────────────────────────────

(defn send!
  "Append user-text to the conversation and run the agent loop."
  [user-text]
  (when (and (seq (str/trim user-text)) (not @pending))
    (agent-loop/run-agent-turn! (str/trim user-text))))

;; ── Init ──────────────────────────────────────────────────────────────────────

(defn init!
  "Wire panel events and preload the builtins summary. Must be called after DOM exists."
  []
  ;; Preload builtins vocabulary summary async (silent fail — panel still works without it)
  (-> (js/fetch "/docs/ai/builtins.json")
      (.then #(.json %))
      (.then (fn [data]
               (let [clj-data (js->clj data)
                     by-cat   (group-by #(get % "category")
                                        (map (fn [[k v]]
                                               (assoc (js->clj v) "name" k))
                                             clj-data))]
                 (reset! builtins-summary
                   (str/join "\n"
                     (for [[cat entries] by-cat]
                       (str cat ": "
                            (str/join ", " (map #(get % "name") entries)))))))))
      (.catch (fn [_] nil)))

  ;; Wire agent-loop callbacks (avoids circular require — agent-loop holds atoms)
  (agent-loop/set-panel-fns!
    {:add-message!         add-message!
     :set-pending!         set-pending!
     :get-builtins-summary get-builtins-summary
     :log-tool-call!       log-tool-call!})

  (when-let [panel (el "ai-panel")]
    ;; Click delegation
    (.addEventListener panel "click"
      (fn [^js e]
        (let [t  (.-target e)
              id (.-id t)]
          (cond
            (= id "ai-enable-btn")
            (do (reset! settings/enabled? true) (render-panel!))

            (= id "ai-settings-btn")
            (render-settings!)

            (= id "ai-clear-btn")
            (do (reset! messages []) (clear-history!) (agent-loop/reset-history!) (render-panel!))

            (= id "ai-send-btn")
            (when-let [inp (el "ai-input")]
              (let [txt (.-value inp)]
                (set! (.-value inp) "")
                (send! txt)))

            (= id "ai-cancel-btn")
            (do (agent-loop/cancel!) (set-pending! false))

            (= id "ai-settings-save-btn")
            (do
              (when-let [sel (el "ai-provider-sel")]
                (reset! settings/provider (.-value sel)))
              (when-let [ki (el "ai-key-input")]
                (reset! settings/api-key (.-value ki)))
              (when-let [mi (el "ai-model-input")]
                (reset! settings/model-override (.-value mi)))
              (when-let [cb (el "ai-include-code-cb")]
                (reset! settings/include-code? (.-checked cb)))
              (when-let [cb (el "ai-auto-apply-cb")]
                (reset! settings/auto-apply? (.-checked cb)))
              (when-let [ti (el "ai-token-budget")]
                (when-let [ci (el "ai-call-budget")]
                  (let [t (js/parseInt (.-value ti) 10)
                        c (js/parseInt (.-value ci) 10)]
                    (when (and (pos? t) (pos? c))
                      (budget/save-limits! {:tokens t :calls c})))))
              (when-let [fk (el "ai-freesound-key-input")]
                (reset! settings/freesound-api-key (let [v (.-value fk)] (when (seq v) v))))
              (when-let [sk (el "ai-search-key-input")]
                (reset! settings/search-api-key (let [v (.-value sk)] (when (seq v) v))))
              (render-panel!))

            (= id "ai-freesound-key-toggle")
            (when-let [inp (el "ai-freesound-key-input")]
              (let [showing? (= "text" (.-type inp))]
                (set! (.-type inp) (if showing? "password" "text"))
                (set! (.-textContent t) (if showing? "show" "hide"))))

            (= id "ai-freesound-key-clear")
            (do
              (reset! settings/freesound-api-key nil)
              (when-let [inp (el "ai-freesound-key-input")] (set! (.-value inp) "")))

            (= id "ai-search-key-toggle")
            (when-let [inp (el "ai-search-key-input")]
              (let [showing? (= "text" (.-type inp))]
                (set! (.-type inp) (if showing? "password" "text"))
                (set! (.-textContent t) (if showing? "show" "hide"))))

            (= id "ai-search-key-clear")
            (do
              (reset! settings/search-api-key nil)
              (when-let [inp (el "ai-search-key-input")] (set! (.-value inp) "")))

            (= id "ai-settings-back-btn")
            (render-panel!)

            (= id "ai-revert-btn")
            (do (undo/revert-last-turn!) (render-panel!))

            (= id "ai-log-toggle-btn")
            (do (swap! log-expanded? not) (render-panel!))

            (= id "ai-log-export-btn")
            (export-activity-log!)

            (= id "ai-log-reset-btn")
            (do
              (reset! activity-log [])
              (budget/reset-budget!)
              (render-panel!))

            ;; ↓ insert button on code blocks — matched by class, any other id
            (.contains (.-classList t) "ai-insert-btn")
            (insert-code! (.getAttribute t "data-code"))

            ;; All other clicks (ai-messages, ai-input, etc.) — ignore
            :else nil)))
      false)

    ;; Cmd/Ctrl+Enter in the textarea sends the message
    (.addEventListener panel "keydown"
      (fn [^js e]
        (when (and (= "Enter" (.-key e)) (or (.-metaKey e) (.-ctrlKey e)))
          (when-let [inp (el "ai-input")]
            (let [txt (.-value inp)]
              (set! (.-value inp) "")
              (send! txt)))))
      false)))
