(ns repulse.ui.assistant-panel
  "AI assistant chat panel — BYO API key, streaming responses, code insert.
   Exports: init!, show-panel!, hide-panel!, toggle-panel!, send!, visible?"
  (:require [repulse.ai.settings :as settings]
            [repulse.ai.client :as ai-client]
            [repulse.ai.system-prompt :as sys-prompt]
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

;; ── State ─────────────────────────────────────────────────────────────────────

(defonce visible?         (atom false))
(defonce messages         (atom (or (load-history) [])))
(defonce ^:private pending    (atom false))
(defonce ^:private abort-ctrl (atom nil))
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
                 ;; Plain text paragraph
                 (str "<p>" (escape-html part) "</p>")))
             parts))
         "</div>")))

;; ── Settings drawer ───────────────────────────────────────────────────────────

(defn- render-settings! []
  (when-let [panel (el "ai-panel")]
    (set! (.-innerHTML panel)
          (str "<div class=\"ai-settings\">"
               "<h3 class=\"ai-settings-title\">AI Settings</h3>"
               "<label class=\"ai-label\">Provider"
               "  <select id=\"ai-provider-sel\" class=\"ai-select\">"
               (str/join ""
                 (map (fn [p]
                        (str "<option value=\"" p "\""
                             (when (= p @settings/provider) " selected")
                             ">" p "</option>"))
                      ["anthropic" "openai" "google" "groq"]))
               "  </select>"
               "</label>"
               "<label class=\"ai-label\">API key"
               "  <span class=\"ai-key-hint\">(stored locally only — never sent to REPuLse servers)</span>"
               "  <input id=\"ai-key-input\" class=\"ai-input-text\" type=\"password\""
               "  value=\"" (escape-html @settings/api-key) "\">"
               "</label>"
               "<label class=\"ai-label\">Model override"
               "  <span class=\"ai-key-hint\">(leave blank for default)</span>"
               "  <input id=\"ai-model-input\" class=\"ai-input-text\" type=\"text\""
               "  value=\"" (escape-html @settings/model-override) "\">"
               "</label>"
               "<label class=\"ai-checkbox-label\">"
               "  <input id=\"ai-include-code-cb\" type=\"checkbox\""
               (when @settings/include-code? " checked")
               "> Share editor code with AI"
               "</label>"
               "<div class=\"ai-settings-btns\">"
               "<button id=\"ai-settings-save-btn\" class=\"ai-btn\">Save</button>"
               "<button id=\"ai-settings-back-btn\" class=\"ai-btn ai-btn--secondary\">Back</button>"
               "</div>"
               "</div>"))))

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
                   "<button id=\"ai-settings-btn\" class=\"ai-icon-btn\" title=\"AI settings\">&#9881;</button>"
                   "<button id=\"ai-clear-btn\" class=\"ai-icon-btn\" title=\"Clear chat\">&#10005;</button>"
                   "</div>"
                   "<div id=\"ai-messages\" class=\"ai-messages\">"
                   (str/join "" (map render-message @messages))
                   (when @pending
                     "<div class=\"ai-msg ai-msg--assistant ai-thinking\">&#8230;</div>")
                   "</div>"
                   "<div class=\"ai-input-row\">"
                   "<textarea id=\"ai-input\" class=\"ai-textarea\""
                   " placeholder=\"Describe a pattern, ask for help…\" rows=\"2\"></textarea>"
                   "<button id=\"ai-send-btn\" class=\"ai-btn\""
                   (when @pending " disabled") ">send</button>"
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
  "Append user-text to the conversation and stream an assistant response."
  [user-text]
  (when (and (seq (str/trim user-text)) (not @pending))
    (let [user-msg  {:role "user" :content (str/trim user-text)}
          new-msgs  (conj @messages user-msg)]
      (reset! messages new-msgs)
      (reset! pending true)
      (render-panel!)
      (let [system  (sys-prompt/build @builtins-summary)
            history (mapv #(select-keys % [:role :content]) new-msgs)
            result  (atom "")]
        (reset! abort-ctrl
          (ai-client/stream!
            system
            history
            {:on-chunk
             (fn [delta]
               (swap! result str delta)
               ;; Live-update the thinking placeholder with partial content
               (when-let [thinking (and (el "ai-panel")
                                        (.querySelector (el "ai-panel") ".ai-thinking"))]
                 (set! (.-innerHTML thinking)
                       (render-message {:role "assistant" :content @result}))
                 ;; Keep scrolled to bottom during streaming
                 (when-let [msgs-el (el "ai-messages")]
                   (set! (.-scrollTop msgs-el) (.-scrollHeight msgs-el)))))

             :on-done
             (fn [_]
               (let [final-msgs (conj @messages {:role "assistant" :content @result})]
                 (reset! messages final-msgs)
                 (save-history! final-msgs)
                 (reset! pending false)
                 (reset! abort-ctrl nil)
                 (render-panel!)))

             :on-error
             (fn [err]
               (let [err-msgs (conj @messages {:role "assistant"
                                               :content (str "Error: " err)})]
                 (reset! messages err-msgs)
                 (save-history! err-msgs)
                 (reset! pending false)
                 (reset! abort-ctrl nil)
                 (render-panel!)))}))))))

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

  (when-let [panel (el "ai-panel")]
    ;; Click delegation
    (.addEventListener panel "click"
      (fn [^js e]
        (let [t (.-target e)]
          (condp = (.-id t)
            "ai-enable-btn"        (do (reset! settings/enabled? true) (render-panel!))
            "ai-settings-btn"      (render-settings!)
            "ai-clear-btn"         (do (reset! messages [])
                                       (save-history! [])
                                       (render-panel!))
            "ai-send-btn"          (when-let [inp (el "ai-input")]
                                     (let [txt (.-value inp)]
                                       (set! (.-value inp) "")
                                       (send! txt)))
            "ai-settings-save-btn" (do
                                     (when-let [sel (el "ai-provider-sel")]
                                       (reset! settings/provider (.-value sel)))
                                     (when-let [ki (el "ai-key-input")]
                                       (reset! settings/api-key (.-value ki)))
                                     (when-let [mi (el "ai-model-input")]
                                       (reset! settings/model-override (.-value mi)))
                                     (when-let [cb (el "ai-include-code-cb")]
                                       (reset! settings/include-code? (.-checked cb)))
                                     (render-panel!))
            "ai-settings-back-btn" (render-panel!)
            ;; "↓ insert" button on code blocks — matched by class
            nil (when (.contains (.-classList t) "ai-insert-btn")
                  (insert-code! (.getAttribute t "data-code"))))))
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
