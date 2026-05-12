(ns repulse.ui.assistant-panel
  "AI assistant chat panel — BYO API key, streaming responses, code insert.
   Exports: init!, show-panel!, hide-panel!, toggle-panel!, send!, visible?,
            add-message!, set-pending!, get-builtins-summary"
  (:require [repulse.ai.settings :as settings]
            [repulse.ai.agent-loop :as agent-loop]
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
   inline-code, bold, italic, paragraphs."
  [raw]
  (let [out       (atom [])
        list-open (atom nil)]   ; :ul | :ol | nil
    (doseq [line (str/split-lines raw)]
      (let [t (str/trim line)]
        (cond
          ;; blank — close any open list, emit nothing
          (empty? t)
          (when-let [lt @list-open]
            (swap! out conj (if (= lt :ul) "</ul>" "</ol>"))
            (reset! list-open nil))

          ;; heading (# / ## / ###)
          (re-matches #"#{1,3} .+" t)
          (let [[_ hashes text] (re-matches #"(#{1,3}) (.*)" t)
                tag (get ["h3" "h4" "h5"] (dec (count hashes)) "h5")]
            (when-let [lt @list-open]
              (swap! out conj (if (= lt :ul) "</ul>" "</ol>"))
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

          ;; plain paragraph line
          :else
          (do
            (when-let [lt @list-open]
              (swap! out conj (if (= lt :ul) "</ul>" "</ol>"))
              (reset! list-open nil))
            (swap! out conj (str "<p>" (inline-md (escape-html t)) "</p>"))))))
    ;; close any trailing open list
    (when-let [lt @list-open]
      (swap! out conj (if (= lt :ul) "</ul>" "</ol>")))
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
        (set! (.-innerHTML panel)
              (str "<div class=\"ai-header\">"
                   "<span class=\"ai-provider-badge\">"
                   (escape-html @settings/provider) " &middot; " (escape-html (settings/effective-model))
                   "</span>"
                   "<button id=\"ai-settings-btn\" class=\"ai-icon-btn\" title=\"AI settings\" aria-label=\"AI settings\">&#9881;</button>"
                   "<button id=\"ai-clear-btn\" class=\"ai-clear-btn\" type=\"button\" title=\"Clear chat\" aria-label=\"Clear chat\">clear</button>"
                   "</div>"
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
                   "</div>"))
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
    {:add-message!        add-message!
     :set-pending!        set-pending!
     :get-builtins-summary get-builtins-summary})

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
              (render-panel!))

            (= id "ai-settings-back-btn")
            (render-panel!)

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
