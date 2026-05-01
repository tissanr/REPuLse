# Phase AI2 — Assistant Panel & Providers

## Goal

Add an opt-in AI chat panel to REPuLse — a collapsible sidebar where users can
describe a musical idea and receive REPuLse-Lisp code in response. The panel
supports bring-your-own API key for OpenAI, Anthropic, Google, and Groq with
streaming responses. The entire AI feature is **disabled by default**; users
enable it once in settings and their key stays in localStorage — never sent to
the REPuLse backend. The session context given to the assistant includes track
names, BPM, and active effect names — no editor code by default.

This phase ships the conversational UI and provider integration. Tool calling
(buffer reads, edit proposals) arrives in AI3. The only code action in AI2 is
a manual "Insert" button on code blocks that appends to the editor.

```
;; Before — no AI capability:
;; User must know the Lisp API; a blank editor is the starting point.

;; After — typing in the AI panel:
;;   "Give me a euclidean kick pattern at 130 bpm"
;;
;; → assistant streams back:
;;   "Here's a euclidean kick:
;;    ```
;;    (track :kick (euclidean 5 8 :bd))
;;    ```
;;   Click Insert to add it to your session."

;; New Lisp built-ins:
(ai)            ; → opens the assistant panel (or shows "enable AI in settings")
(ai "question") ; → opens panel and pre-fills the input with the question
```

---

## Background

### Existing panel pattern

REPuLse has two established collapsible panels:

- `app/src/repulse/ui/snippet_panel.cljs` — a `<div id="snippet-panel">` toggled
  by a header button ("lib"); exports `init!`, `show-panel!`, `hide-panel!`,
  `toggle-panel!`, `visible?`
- `app/src/repulse/ui/context_panel.cljs` — right-sidebar status; rendered by
  `render-context-panel!` into `<div id="context-panel">`

`app/src/repulse/app.cljs` mounts both: the HTML scaffold at `(defn- render-app [])`
(around line 176) includes all panel `<div>`s with `hidden` class; header buttons
toggle them. AI2 adds an `<div id="ai-panel">` and an "ai" header button following
exactly this pattern.

### localStorage key conventions (session.cljs)

Existing keys: `"repulse-session"` (v2 JSON), legacy `"repulse-editor"`,
`"repulse-bpm"`. New AI keys must use the `"repulse:ai:"` namespace prefix to
avoid collision:

- `"repulse:ai:enabled"` — `"true"` / absent; the feature gate
- `"repulse:ai:provider"` — `"anthropic"` / `"openai"` / `"google"` / `"groq"`
- `"repulse:ai:key"` — the API key string (stored in plain text; the user is
  informed that this stays local)
- `"repulse:ai:model"` — model ID override (optional; defaults per provider)
- `"repulse:ai:history"` — JSON array of `{role, content}` turn objects (last N)
- `"repulse:ai:include-code"` — `"true"` / absent; opt-in to share editor code

### `help-export` built-in (AI1)

`(help-export)` in `app/src/repulse/env/builtins.cljs` returns a JS object
with `bpm`, `tracks`, `muted`, `fx`, `bank`, `sources`. The system prompt in AI2
will call this function, JSON-serialize the result, and embed it as the session
snapshot. When `include-code` is enabled, the full editor text from
`session/editor-text-fn` is appended as well.

### `docs/ai/builtins.json` (AI1)

The AI docs generated in AI1. AI2 uses a **summary** of this file in the system
prompt — not the full ~200-entry JSON, which would fill most of a context window.
The summary is assembled client-side: top-level categories with a few representative
examples per category, plus the full detail for any name mentioned in the
user's current message.

### Streaming APIs

All four target providers support streaming via `fetch` + `ReadableStream`:

| Provider | Base URL | Auth header | Stream format |
|---|---|---|---|
| Anthropic | `https://api.anthropic.com/v1/messages` | `x-api-key` | SSE `data:` lines, `delta.text` |
| OpenAI | `https://api.openai.com/v1/chat/completions` | `Authorization: Bearer` | SSE `data:` lines, `choices[0].delta.content` |
| Google | `https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent` | `?key=` query param | Newline-delimited JSON |
| Groq | `https://api.groq.com/openai/v1/chat/completions` | `Authorization: Bearer` | Same as OpenAI |

`fetch` with `{signal: AbortController.signal}` handles cancellation.

### Existing auth module

`app/src/repulse/auth.cljs` owns `auth-atom`, Supabase login/logout. The AI module
is **independent of auth** — BYO keys are local only and work for anonymous users.

---

## Implementation

### 1. New file: `app/src/repulse/ai/settings.cljs`

Owns all AI-related localStorage state atoms. Keeps the AI feature strictly isolated
from `session.cljs` (which must not grow AI concerns):

```clojure
(ns repulse.ai.settings)

(def ^:private key-prefix "repulse:ai:")

(defn- ls-get [k]     (.getItem js/localStorage (str key-prefix k)))
(defn- ls-set! [k v]  (.setItem js/localStorage (str key-prefix k) v))
(defn- ls-del! [k]    (.removeItem js/localStorage (str key-prefix k)))

(defonce enabled?       (atom (= "true" (ls-get "enabled"))))
(defonce provider       (atom (or (ls-get "provider") "anthropic")))
(defonce api-key        (atom (or (ls-get "key") "")))
(defonce model-override (atom (or (ls-get "model") "")))
(defonce include-code?  (atom (= "true" (ls-get "include-code"))))

;; Persist on change
(add-watch enabled?       :persist #(if %4 (ls-set! "enabled" "true") (ls-del! "enabled")))
(add-watch provider       :persist #(ls-set! "provider" %4))
(add-watch api-key        :persist #(ls-set! "key" %4))
(add-watch model-override :persist #(if (seq %4) (ls-set! "model" %4) (ls-del! "model")))
(add-watch include-code?  :persist #(if %4 (ls-set! "include-code" "true") (ls-del! "include-code")))

(def default-models
  {"anthropic" "claude-sonnet-4-6"
   "openai"    "gpt-4o"
   "google"    "gemini-2.0-flash"
   "groq"      "llama-3.3-70b-versatile"})

(defn effective-model []
  (let [override @model-override]
    (if (seq override) override (get default-models @provider))))
```

### 2. New file: `app/src/repulse/ai/client.cljs`

Provider abstraction with streaming fetch. Returns a channel/callback-based
streaming reader:

```clojure
(ns repulse.ai.client
  (:require [repulse.ai.settings :as settings]))

(defn- anthropic-request [messages model key]
  {:url     "https://api.anthropic.com/v1/messages"
   :headers {"x-api-key"         key
             "anthropic-version" "2023-06-01"
             "content-type"      "application/json"}
   :body    (js/JSON.stringify
              #js {:model     model
                   :max_tokens 2048
                   :stream    true
                   :messages  (clj->js messages)})})

(defn- openai-request [messages model key]
  {:url     "https://api.openai.com/v1/chat/completions"
   :headers {"Authorization" (str "Bearer " key)
             "content-type"  "application/json"}
   :body    (js/JSON.stringify
              #js {:model    model
                   :stream   true
                   :messages (clj->js messages)})})

(defn- groq-request [messages model key]
  (-> (openai-request messages model key)
      (assoc :url "https://api.groq.com/openai/v1/chat/completions")))

(defn- google-request [messages model key]
  {:url     (str "https://generativelanguage.googleapis.com/v1beta/models/"
                 model ":streamGenerateContent?key=" key "&alt=sse")
   :headers {"content-type" "application/json"}
   :body    (js/JSON.stringify
              #js {:contents (clj->js
                               (mapv (fn [{:keys [role content]}]
                                       {:role (if (= role "assistant") "model" role)
                                        :parts [{:text content}]})
                                     messages))})})

(defn make-request [messages]
  (let [provider @settings/provider
        key      @settings/api-key
        model    (settings/effective-model)]
    (case provider
      "anthropic" (anthropic-request messages model key)
      "openai"    (openai-request    messages model key)
      "groq"      (groq-request      messages model key)
      "google"    (google-request    messages model key)
      (throw (ex-info "Unknown provider" {:provider provider})))))

(defn- parse-delta [provider line]
  (try
    (when (str/starts-with? line "data: ")
      (let [json-str (subs line 6)]
        (when-not (= "[DONE]" json-str)
          (let [obj (js/JSON.parse json-str)]
            (case provider
              "anthropic" (some-> (.-delta obj) (.-text))
              ("openai" "groq") (some-> (.. obj -choices (aget 0) -delta -content))
              "google"    (some-> (.. obj -candidates (aget 0) -content -parts (aget 0) -text)
                            str)
              nil)))))
    (catch :default _ nil)))

(defn stream!
  "Start a streaming request. Calls on-chunk with each text delta,
   on-done with total token usage, on-error with error message.
   Returns an AbortController."
  [messages {:keys [on-chunk on-done on-error]}]
  (let [abort-ctrl (js/AbortController.)
        {:keys [url headers body]} (make-request messages)
        provider @settings/provider]
    (->
      (js/fetch url #js {:method  "POST"
                         :headers (clj->js headers)
                         :body    body
                         :signal  (.-signal abort-ctrl)})
      (.then (fn [resp]
               (if (.-ok resp)
                 (let [reader (.getReader (.-body resp))
                       decoder (js/TextDecoder.)]
                   (letfn [(read-loop []
                             (->
                               (.read reader)
                               (.then (fn [chunk]
                                        (if (.-done chunk)
                                          (on-done nil)
                                          (do
                                            (doseq [line (str/split-lines
                                                           (.decode decoder (.-value chunk)))]
                                              (when-let [delta (parse-delta provider line)]
                                                (on-chunk delta)))
                                            (read-loop)))))))]
                     (read-loop)))
                 (on-error (str "HTTP " (.-status resp) " " (.-statusText resp))))))
      (.catch (fn [err]
                (when-not (= "AbortError" (.-name err))
                  (on-error (.-message err))))))
    abort-ctrl))
```

### 3. New file: `app/src/repulse/ai/system_prompt.cljs`

Assembles the system prompt from AI docs + session snapshot:

```clojure
(ns repulse.ai.system-prompt
  (:require [repulse.ai.settings :as settings]
            [repulse.session :as session]
            [repulse.ui.editor :as editor]))

(defn- fetch-builtins-summary []
  ;; Load /docs/ai/builtins.json at runtime and return a compact summary
  ;; Group by category; list up to 5 names per category with their signature
  (-> (js/fetch "/docs/ai/builtins.json")
      (.then #(.json %))
      (.then (fn [data]
               (let [by-cat (group-by #(get % "category")
                                      (map #(assoc (js->clj (val %)) :name (key %))
                                           (js->clj data)))]
                 (str/join "\n"
                   (for [[cat entries] by-cat]
                     (str "## " cat "\n"
                          (str/join "\n"
                            (map #(str "  " (:name %) " — " (get % "signature" ""))
                                 (take 8 entries)))))))))))

(defn build []
  (let [help     (js/JSON.stringify (js/Object.assign #js {} ((-> @session/editor-text-fn deref))))
        code-ctx (when @settings/include-code?
                   (str "\n\nEditor code:\n```lisp\n"
                        (if-let [f @session/editor-text-fn] (f) "")
                        "\n```"))]
    (str
      "You are an expert REPuLse-Lisp live coding assistant. REPuLse is a browser-based "
      "live coding instrument where music is written in a minimal Lisp. Patterns are pure "
      "functions of time. Always return working REPuLse-Lisp code in fenced code blocks.\n\n"
      "Key rules:\n"
      "- Use (track :name pattern) for named tracks, not bare patterns\n"
      "- Thread parameters with ->>: (->> pat (amp 0.8) (attack 0.02))\n"
      "- Use (bpm N) to set tempo, (fx :name val) for effects\n"
      "- Rests are :_ — not nil or 0\n"
      "- (scale :minor :c4 (seq 0 2 4 7)) maps degrees to Hz\n\n"
      "Current session:\n"
      help
      (or code-ctx "")
      "\n\nBuilt-in vocabulary (partial):\n"
      ;; This is filled lazily from /docs/ai/builtins.json — the panel loads it once at init
      ;; and caches it; see assistant_panel.cljs
      "{BUILTINS_SUMMARY}")))
```

The `{BUILTINS_SUMMARY}` placeholder is substituted by the panel after the async
`fetch-builtins-summary` resolves. The panel caches the summary string in a
`(defonce builtins-summary (atom nil))` atom and refetches only on first open.

### 4. New file: `app/src/repulse/ui/assistant_panel.cljs`

Main panel module. Follows `snippet_panel.cljs` structure exactly:

```clojure
(ns repulse.ui.assistant-panel
  (:require [repulse.ai.settings :as settings]
            [repulse.ai.client :as ai-client]
            [repulse.ai.system-prompt :as sys-prompt]
            [repulse.ui.editor :as editor]
            [clojure.string :as str]))

;;; State

(defonce visible?          (atom false))
(defonce messages          (atom (load-history)))  ; [{:role "user"/:assistant :content "..."}]
(defonce ^:private pending (atom false))            ; streaming in progress
(defonce ^:private abort   (atom nil))              ; AbortController for current request
(defonce ^:private builtins-summary (atom nil))

;;; History persistence

(defn- load-history []
  (try
    (when-let [raw (.getItem js/localStorage "repulse:ai:history")]
      (js->clj (js/JSON.parse raw) :keywordize-keys true))
    (catch :default _ nil)))

(defn- save-history! []
  (try
    (.setItem js/localStorage "repulse:ai:history"
              (js/JSON.stringify (clj->js (take-last 40 @messages))))
    (catch :default _ nil)))

;;; Panel show/hide

(defn- el [id] (.getElementById js/document id))

(defn show-panel!  [] (reset! visible? true)  (.remove (.-classList (el "ai-panel")) "hidden"))
(defn hide-panel!  [] (reset! visible? false) (.add    (.-classList (el "ai-panel")) "hidden"))
(defn toggle-panel! [] (if @visible? (hide-panel!) (show-panel!)))

;;; Insert helper — appends a code block to the editor

(defn- insert-code! [code]
  (when-let [view @editor/editor-view]
    (let [doc   (.-doc (.-state view))
          end   (.-length doc)
          nl    (if (> end 0) "\n" "")]
      (.dispatch view
        (.update (.-state view)
          #js {:changes #js {:from end :insert (str nl code)}})))))

;;; Render

(defn- render-message [{:keys [role content]}]
  (let [parts (str/split content #"```")]
    (str "<div class=\"ai-msg ai-msg--" role "\">"
         (str/join ""
           (map-indexed
             (fn [i part]
               (if (odd? i)
                 ;; Code block — strip optional language tag from first line
                 (let [lines (str/split-lines part)
                       lang  (when (re-matches #"[a-zA-Z]+" (first lines)) (first lines))
                       code  (str/join "\n" (if lang (rest lines) lines))]
                   (str "<pre><code>" (escape-html code) "</code>"
                        "<button class=\"ai-insert-btn\" data-code=\""
                        (escape-html code) "\">↓ insert</button></pre>"))
                 ;; Plain text
                 (str "<p>" (escape-html part) "</p>")))
             parts))
         "</div>")))

(defn- render-panel! []
  (when-let [panel (el "ai-panel")]
    (if-not @settings/enabled?
      (set! (.-innerHTML panel)
            (str "<div class=\"ai-disabled\">"
                 "<p>AI assistant is disabled.</p>"
                 "<button id=\"ai-enable-btn\">Enable AI assistant</button>"
                 "</div>"))
      (set! (.-innerHTML panel)
            (str "<div class=\"ai-header\">"
                 "  <span class=\"ai-provider-badge\">" @settings/provider " · " (settings/effective-model) "</span>"
                 "  <button id=\"ai-settings-btn\" title=\"AI settings\">⚙</button>"
                 "  <button id=\"ai-clear-btn\" title=\"Clear chat\">✕</button>"
                 "</div>"
                 "<div id=\"ai-messages\" class=\"ai-messages\">"
                 (str/join "" (map render-message @messages))
                 "</div>"
                 "<div class=\"ai-input-row\">"
                 "  <textarea id=\"ai-input\" placeholder=\"Describe a pattern, ask for help…\" rows=\"2\"></textarea>"
                 "  <button id=\"ai-send-btn\"" (when @pending " disabled") ">send</button>"
                 "</div>"
                 (when @pending
                   "<div class=\"ai-thinking\">…</div>"))))))

;;; Settings drawer (simple inline form)

(defn- render-settings! []
  (when-let [panel (el "ai-panel")]
    (set! (.-innerHTML panel)
          (str "<div class=\"ai-settings\">"
               "<h3>AI Settings</h3>"
               "<label>Provider"
               "  <select id=\"ai-provider-sel\">"
               (str/join "" (map #(str "<option value=\"" % "\"" (when (= % @settings/provider) " selected") ">" % "</option>")
                                 ["anthropic" "openai" "google" "groq"]))
               "  </select>"
               "</label>"
               "<label>API key (stored locally only)"
               "  <input id=\"ai-key-input\" type=\"password\" value=\"" (escape-html @settings/api-key) "\">"
               "</label>"
               "<label>Model (leave blank for default)"
               "  <input id=\"ai-model-input\" type=\"text\" value=\"" (escape-html @settings/model-override) "\">"
               "</label>"
               "<label><input id=\"ai-include-code-cb\" type=\"checkbox\""
               (when @settings/include-code? " checked") "> Share editor code with AI</label>"
               "<button id=\"ai-settings-save-btn\">Save</button>"
               "<button id=\"ai-settings-back-btn\">Back</button>"
               "</div>"))))

;;; Send a message

(defn send! [user-text]
  (when (and (seq (str/trim user-text)) (not @pending))
    (let [user-msg {:role "user" :content (str/trim user-text)}
          full-sys (-> @sys-prompt/cached-prompt
                       (str/replace "{BUILTINS_SUMMARY}" (or @builtins-summary "")))]
      (swap! messages conj user-msg)
      (reset! pending true)
      (render-panel!)
      (let [history (conj (mapv #(select-keys % [:role :content]) @messages)
                          {:role "system" :content full-sys})
            result-atom (atom "")]
        (reset! abort
          (ai-client/stream!
            history
            {:on-chunk (fn [delta]
                         (swap! result-atom str delta)
                         ;; Live-update the last message in the DOM
                         (when-let [msgs-el (el "ai-messages")]
                           (let [last-el (.-lastElementChild msgs-el)]
                             (when last-el
                               (set! (.-innerHTML last-el)
                                     (render-message {:role "assistant" :content @result-atom}))))))
             :on-done  (fn [_usage]
                         (swap! messages conj {:role "assistant" :content @result-atom})
                         (save-history!)
                         (reset! pending false)
                         (reset! abort nil)
                         (render-panel!))
             :on-error (fn [err]
                         (swap! messages conj {:role "assistant"
                                               :content (str "Error: " err)})
                         (reset! pending false)
                         (reset! abort nil)
                         (render-panel!))}))))))

;;; Init — wire event delegation

(defn init! []
  ;; Load builtins summary async
  (-> (js/fetch "/docs/ai/builtins.json")
      (.then #(.json %))
      (.then (fn [data]
               (let [by-cat (group-by #(get % "category")
                                       (map (fn [[k v]] (assoc (js->clj v) :name k))
                                            (js->clj data)))]
                 (reset! builtins-summary
                   (str/join "\n"
                     (for [[cat entries] by-cat]
                       (str cat ": " (str/join ", " (map :name entries)))))))))
      (.catch (fn [_] nil)))

  ;; Event delegation on the panel div
  (when-let [panel (el "ai-panel")]
    (.addEventListener panel "click"
      (fn [e]
        (let [t (.-target e)]
          (condp = (.-id t)
            "ai-enable-btn"       (do (reset! settings/enabled? true) (render-panel!))
            "ai-settings-btn"     (render-settings!)
            "ai-clear-btn"        (do (reset! messages []) (save-history!) (render-panel!))
            "ai-send-btn"         (when-let [inp (el "ai-input")]
                                    (send! (.-value inp))
                                    (set! (.-value inp) ""))
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
            ;; Insert button (class-based, not id)
            nil (when (.contains (.-classList t) "ai-insert-btn")
                  (insert-code! (.getAttribute t "data-code")))))
      true))

    (.addEventListener panel "keydown"
      (fn [e]
        (when (and (= "Enter" (.-key e)) (.-metaKey e))
          (when-let [inp (el "ai-input")]
            (send! (.-value inp))
            (set! (.-value inp) "")))))))
```

### 5. `app/src/repulse/app.cljs` — wire panel into scaffold

In `render-app` HTML string, add the AI panel div and header button:

```clojure
;; In header-controls div, add after snippet-toggle-btn:
"    <button id=\"ai-toggle-btn\" class=\"ai-toggle-btn\">ai</button>"

;; After the snippet-panel div:
"<div id=\"ai-panel\" class=\"ai-panel hidden\"></div>"
```

In the bootstrap function (after other panel `init!` calls):

```clojure
(assistant-panel/init!)

;; Wire the toggle button:
(when-let [btn (el "ai-toggle-btn")]
  (.addEventListener btn "click" assistant-panel/toggle-panel!))
```

### 6. `app/src/repulse/env/builtins.cljs` — `(ai)` and `(ai "text")` built-ins

```clojure
"ai"
(fn
  ([]
   (if @ai-settings/enabled?
     (do (assistant-panel/show-panel!) nil)
     "AI assistant is disabled — enable it in the AI panel settings"))
  ([prompt-text]
   (if @ai-settings/enabled?
     (do (assistant-panel/show-panel!)
         (assistant-panel/send! (str prompt-text))
         nil)
     "AI assistant is disabled — enable it in the AI panel settings")))
```

Requires adding `[repulse.ai.settings :as ai-settings]` and
`[repulse.ui.assistant-panel :as assistant-panel]` to the `builtins.cljs` ns requires.

### 7. Grammar, completions, hover docs

Add `ai` and `help-export` to the grammar, completions, and hover docs using the
standard checklist (CLAUDE.md Rule 4 + existing Rule 1 steps).

### 8. CSS — `app/public/css/main.css`

New classes needed: `.ai-panel`, `.ai-toggle-btn`, `.ai-header`, `.ai-messages`,
`.ai-msg`, `.ai-msg--user`, `.ai-msg--assistant`, `.ai-input-row`, `.ai-insert-btn`,
`.ai-settings`, `.ai-disabled`, `.ai-thinking`. Follow existing panel styles from
`.snippet-panel` for sizing, scrolling, z-index, and dark theme colors.

---

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/ai/settings.cljs` | **New** — localStorage-backed AI settings atoms |
| `app/src/repulse/ai/client.cljs` | **New** — streaming fetch abstraction for 4 providers |
| `app/src/repulse/ai/system_prompt.cljs` | **New** — system prompt builder using help-export + builtins summary |
| `app/src/repulse/ui/assistant_panel.cljs` | **New** — chat panel UI, history, insert button |
| `app/src/repulse/app.cljs` | Add AI panel div, header button, init! call, toggle wiring |
| `app/src/repulse/env/builtins.cljs` | Add `(ai)` and `(ai "prompt")` built-ins |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `ai` to `BuiltinName` |
| `app/src/repulse/lisp-lang/completions.js` | Add `ai` completion entry |
| `app/src/repulse/lisp-lang/hover.js` | Add `ai` hover doc |
| `app/src/repulse/content/builtin_meta.edn` | Add `ai` metadata entry |
| `app/public/css/main.css` | New `.ai-*` classes for panel layout and dark theme |
| `docs/ai/builtins.json` | Regenerate after grammar + completions update (`npm run gen:ai-docs`) |
| `app/src/repulse/lisp-lang/parser.js` | Regenerate (`npm run gen:grammar`) |
| `ROADMAP.md` + `CLAUDE.md` | Mark AI2 ✓ delivered when complete |

---

## Definition of done

- [ ] The "ai" button appears in the header; clicking it opens/closes the AI panel
- [ ] When AI is disabled (default), the panel shows an "Enable AI assistant" button
- [ ] After clicking enable, saving a provider + API key, and sending a message,
      a streaming response appears in the panel
- [ ] `(ai)` opens the assistant panel; `(ai "help me write a bass line")` opens
      the panel and immediately sends the prompt
- [ ] `(ai)` when AI is disabled returns the string
      `"AI assistant is disabled — enable it in the AI panel settings"`
- [ ] All four providers produce streaming responses:
      Anthropic (`claude-sonnet-4-6`), OpenAI (`gpt-4o`), Groq (`llama-3.3-70b-versatile`),
      Google (`gemini-2.0-flash`)
- [ ] Code blocks in assistant responses show an "↓ insert" button that appends
      the code to the editor without replacing existing content
- [ ] Conversation history persists across page reloads (localStorage `repulse:ai:history`)
- [ ] "Clear chat" button wipes history from state and localStorage
- [ ] The "Share editor code with AI" checkbox, when enabled, includes the editor buffer
      in the system prompt; when disabled (default), only track names + BPM + FX are shared
- [ ] API key is stored in `repulse:ai:key` localStorage entry; the key is never
      included in any fetch to the REPuLse backend (Vercel/Supabase)
- [ ] An invalid or expired API key shows an inline error in the panel, not an unhandled rejection
- [ ] `(reset!)` wipes `repulse:ai:*` localStorage entries alongside the session wipe
- [ ] `npm run gen:grammar` and `npm run gen:ai-docs` are run; committed `parser.js`
      and `builtins.json` include the `ai` entry
- [ ] `npm run test` passes — no regressions

---

## What NOT to do

- **Do not add tool calling or buffer-reading capabilities.** The assistant may suggest
  code but cannot read, patch, or evaluate the editor buffer. That is Phase AI3.
- **Do not implement server-relay key storage.** Keys stay in localStorage only.
  Supabase-backed encrypted key storage arrives in Phase AI4.
- **Do not add an auto-apply mode.** Every code insertion requires a manual "↓ insert"
  click. The auto-apply toggle is an AI4 safety feature.
- **Do not add per-snippet AI integration.** The snippet panel is out of scope here.
  AI3 will wire snippet search as a tool call.
- **Do not add token/cost tracking beyond a simple indicator.** Precise cost accounting
  across providers is complex; a rough tokens-used counter in the panel header is
  sufficient for AI2. Accurate cost limits arrive in AI4.
- **Do not change the (help-export) output format.** Its schema is defined in AI1
  and must not be extended here — add a new field only via an AI1 patch if needed.
