# Phase WS4 — AI Assistant Rail

> **Spec status:** full
> **Automation:** 🤖 auto — layout, toggle and working-state are DOM behaviour, verifiable with
> Playwright using a mocked provider.

**Milestone:** M2 — Full songs in code
**Depends on:** AI2/AI3 (assistant panel, delivered). WS1 recommended (selection as context).

## Goal

Move the AI assistant out of its floating overlay into a right-hand rail, as in
*Outline Panel v2* (`design/outline-panel-v2/outline-v2.jsx`: `AIRail`, `AIPanel`;
frame 4 in `Outline Panel v2.html`):

- A **28 px rail** on the right edge of the app shell (`‹` arrow, vertical "AI" label).
  Clicking it (or pressing **⌘I** / **Ctrl+I**) opens a **300 px panel** to its left. The
  rail then shows `›`. Clicking again closes it.
- The `ai` button in the top nav goes away (the rail replaces it).
- **Working state** while a request runs: three 5 px cyan dots pulsing (1.1 s, staggered
  .18 s) with "thinking", a 1 px cyan sweep along the top of the input (1.4 s), and a
  pulsing dot on the rail while the panel is closed.
- The WS1 outline selection (or, with nothing selected, the outline entry under the
  cursor) is sent to the model as context and shown as a `ctx :chorus` chip in the panel
  header.

No language change: the user-facing code stays the same. What changes is what happens
around it:

```lisp
;; before — click "ai" in the top nav; a 380 px overlay covers the right of the editor
;; after  — click the rail (or ⌘I); a 300 px column opens beside the editor.
;; Select the :bass row in the outline, then ask "give it more movement":
;; the header shows "ctx :bass" and the model is told the user means (track :bass …).
(track :bass (->> (seq :c2 :eb2) (amp 0.6)))
(ai "give the bass more movement")   ; still works, opens the rail panel
```

## Scope

- Re-host the existing `repulse.ui.assistant-panel` UI (settings, messages, revert, log,
  input) in the rail panel. No change to providers, tools, budgets or the injection guard.
- Persist open/closed in localStorage.
- The Apply / Reject diff card (`#ai-proposal-overlay`, `repulse.ai.tools/show-diff-overlay!`)
  keeps its current behaviour and position (below `#editor-container` in the workbench).
- One new pure namespace for the context (`repulse.ai.context`). One additive hook in the
  system prompt (`repulse.ai.system-prompt/focus-fn`).

## Decisions (decided)

The two open questions in the outline came with recommendations. Both were adopted (1, 2).
Decisions 3–16 were taken during spec expansion (2026-11-04, auto-phase pipeline) to fit
the existing code.

1. **⌘I = `Mod-i`, in the editor keymap and a global handler.** — **Decided.**
   - Editor: a new `assistant-panel/extension` is a `Prec.high` keymap with
     `{:key "Mod-i" :run toggle-from-ui! :preventDefault true}`. `Prec.high` is required:
     CodeMirror's `defaultKeymap` (added in `repulse.ui.editor/editor-extensions`) already
     binds `Mod-i` to `selectParentSyntax` with `preventDefault: true`. Our binding replaces
     it in the main editor. `selectParentSyntax` isn't documented anywhere in REPuLse, so
     nothing documented is lost.
   - Everywhere else (command bar, AI textarea, outline, inspector, body): a `keydown`
     listener on `js/document`, the same pattern as `outline-panel/on-doc-keydown`. It
     ignores events with `defaultPrevented` set. The editor binding has already handled
     those, so a single key press never toggles twice.
   - Match: `(.-key e)` lower-cased is `"i"`, the platform modifier is down (`metaKey` on
     Mac, `ctrlKey` elsewhere, using the `navigator.platform` `#"Mac"` test that
     `outline-panel/mac?` uses), and Shift and Alt are up. Then `preventDefault` and toggle.
   - **Fallback:** if the Playwright shortcut test (T3) shows that Chromium consumes
     ⌘I/Ctrl+I before the page sees it, change both bindings to `Mod-Shift-a`. Then update
     the rail tooltip, USAGE.md and the test, and note it in the PR. Chromium has no
     default action on ⌘I/Ctrl+I, so the fallback isn't expected. Firefox's Page Info
     (Ctrl+I on Windows/Linux) is out of the test matrix. If it wins there, that's a known
     issue for the protocol, not a blocker.
2. **Context = the WS1 selection, else the outline entry under the cursor.** — **Decided.**
   WS1 is delivered and provides a selection: `repulse.ui.outline-panel/selection`, a vector
   of ids resolved through `outline-panel/selection-index` and pruned with
   `repulse.ui.selection/prune`. The context is that selection. If it's empty, the context
   is `(repulse.outline/entry-at @outline-panel/entries @outline-panel/cursor-pos)`, the
   outline's own recommendation. An explicit selection is what the user pointed at, and the
   cursor is what they're working on, so the selection wins. Without the fallback, the chip
   would be blank most of the time.
3. **DOM placement.** `#ai-panel` moves out of `.workbench` and becomes a direct child of
   `.app-shell`, after `.workbench`. A new `<button id="ai-rail">` follows it as the last
   child. Order: `#outline-panel`, `#inspector`, `.workbench`, `#ai-panel`, `#ai-rail`.
   The panel is a flex column (`width: 300px; flex-shrink: 0`), no longer
   `position: fixed`. The editor shrinks when the panel opens and is never covered.
4. **Persistence key.** `localStorage["repulse:ai:panel-open"]` = `"1"` when open, `"0"`
   when closed, written on every show/hide (including `(ai)`). A missing or unreadable key
   means closed. The `repulse:ai:` prefix means `session/wipe!` (the `reset!` built-in)
   clears it along with the other AI keys. The `"1"`/`"0"` values follow the WS1 layout
   keys (`repulse:outline-collapsed`, `repulse:outline-session-open`).
5. **Default closed.** First visit, no key → closed, so the editor gets the width (DoD:
   ≥ 1000 px at 1280×800).
6. **The rail is always present**, even with AI disabled. Opening it shows the existing
   "Enable AI assistant" view (`render-panel!` already handles `enabled?` false).
7. **Focus.** Opening from the rail or the shortcut focuses `#ai-input` when it exists.
   Closing from the shortcut while focus is inside `#ai-panel` focuses the main editor
   (`@editor/editor-view`). `(ai)` / `(ai "…")` open the panel without moving focus, as
   today. This is the private `toggle-from-ui!`. The public `show-panel!`, `hide-panel!` and
   `toggle-panel!` keep their signatures.
8. **Working state replaces the `…` bubble.** While `@pending` is true, `render-panel!`
   draws `#ai-working` (three `.ai-dot`s + "thinking") instead of
   `<div class="ai-msg ai-msg--assistant ai-thinking">&#8230;</div>`. It also gives
   `.ai-input-row` the class `ai-input-row--busy` with an `.ai-sweep` child, and sets the
   textarea placeholder to `working…`. The idle placeholder is unchanged
   (`Describe a pattern, ask for help…`). The send/cancel buttons are unchanged.
9. **Rail dot only while closed.** `#ai-rail` gets the class `busy` when
   `@pending` is true and the panel is not visible. `.ai-rail-dot` is only displayed under
   `.busy`. With the panel open, the panel's own working state is enough (per the design
   sheet: "rail shows a pulsing dot when collapsed").
10. **Animation names and timings** (Playwright checks computed styles): keyframes
    `ai-dot-pulse` (0 %/100 % opacity .25 scale .8, 50 % opacity 1 scale 1), 1.1 s
    ease-in-out infinite. Dot delays are 0 s / 0.18 s / 0.36 s (`:nth-child(2)` / `(3)`).
    The rail dot uses the same keyframes with no delay. Keyframes `ai-sweep`
    (`background-position` −120 px → 320 px), 1.4 s linear infinite, on a
    `linear-gradient(90deg, transparent, var(--cyan), transparent)` sized 120 px × 1 px.
    Under `@media (prefers-reduced-motion: reduce)`, all three have `animation: none`
    (dots at opacity 1, sweep static). Playwright runs without reduced motion.
11. **Multi-selection.** WS1 allows multi-selecting tracks only. The context then lists all
    of them in selection order, and the chip reads `ctx <n> tracks` (e.g. `ctx 2 tracks`).
    The design draws the chip only for single selections, but the user should see what is
    being sent, so the chip shows here too.
12. **Chip labels.** Single item → `ctx ` + its outline name: `ctx :bass` (track),
    `ctx :chorus` (section), `ctx kick` (def/defn/defsynth), `ctx :reverb` (fx or master
    fx child). Unnamed kinds use fixed words: `:song` → `ctx song`, `:master` →
    `ctx master`, the loose `fx` group (`:fx-group`) → `ctx fx`. No context → the chip is
    in the DOM with class `hidden` and empty text.
13. **What is sent.** `system-prompt/build` appends a focus block after the editor-code
    block. The block is the item description(s), wrapped in `<untrusted>` (track and def
    names are user-controlled, like the snapshot). The selected form's **source text is
    added only when "Share editor code with AI" (`settings/include-code?`) is on**. That
    keeps the existing privacy switch meaningful. The exact text is in the
    `prompt-block` vectors below.
14. **Live chip updates without re-rendering.** `render-panel!` rebuilds `innerHTML`, which
    would wipe the textarea while typing. So the chip is updated in place by
    `update-ctx-chip!`. It runs from `add-watch` hooks on `outline-panel/selection`,
    `outline-panel/cursor-pos` and `outline-panel/entries`. It's a no-op when `#ai-ctx-chip`
    doesn't exist (settings drawer open, panel never rendered). The existing single-slot
    `outline-panel/on-selection-change` hook belongs to the inspector and is not touched.
15. **Context is read per request.** `focus-fn` is called inside `sys-prompt/build`, i.e.
    at every model request of a turn. If the user changes the selection mid-turn, later tool
    rounds see the new focus. That's consistent with the snapshot and editor code, which
    are also rebuilt per request.
16. **No mobile special case.** Below 760 px the outline auto-collapses as today. The rail
    and panel keep their widths (O4 owns mobile layout). The `.ai-toggle-btn` entry is
    removed from the `@media (max-width: 760px)` rule.

## Background

### App shell — `app/src/repulse/app.cljs`

- `build-dom!` writes the whole page as one HTML string into `#app`. The top nav
  (`header.top-nav > .header-controls.nav-actions`) holds `#tap-btn`,
  `#snippet-toggle-btn`, **`<button id="ai-toggle-btn" class="ai-toggle-btn">ai</button>`**
  and `#share-btn`. Below it is `<div class="main-area app-shell">` with `#outline-panel`
  (containing `#status-head` + `#outline-main`), `#inspector` (WS1, `hidden` until
  something is selected) and `.workbench`. `.workbench` contains `#buffer-tabs`,
  `#editor-container`, `#snippet-panel`,
  **`<div id="ai-panel" class="ai-panel hidden"></div>`**, `#cmd-bar`, `#track-panel`,
  `#plugin-panel` and `footer.status-bar`.
- `init` calls `(assistant-panel/init!)` and then wires
  `(.addEventListener (el "ai-toggle-btn") "click" assistant-panel/toggle-panel!)`.
- The main editor's extensions are built in `init` as
  `(.concat (editor/editor-extensions …) #js [outline-panel/extension goto/extension selection-highlight/extension inline-slider/extension])`.
  The same `exts` are passed to `buffers/init!`, so every buffer's state gets them.
- Test handles: `window.__REPULSE_EDITOR__` (the main `EditorView`) and
  `window.__REPULSE_APP__` (`cycleDur`, `muted`, `selection`, `trackParam`, `fxParam`).

### Assistant panel — `app/src/repulse/ui/assistant_panel.cljs` (`repulse.ui.assistant-panel`)

- State: `visible?` (public atom), `messages` (persisted in `repulse:ai:history`), private
  `pending`, `activity-log`, `log-expanded?` and `draft-provider`.
- `render-panel!` sets `#ai-panel` `innerHTML`. If AI is disabled, that's `.ai-disabled`
  with `#ai-enable-btn`. Otherwise:
  - `.ai-header`: `.ai-provider-badge`, `.ai-budget-badge`, `#ai-settings-btn`, `#ai-clear-btn`
  - `.ai-undo-bar` with `#ai-revert-btn` (auto-apply on and `undo/stack-size` > 0)
  - `#ai-messages` with the `.ai-msg--<role>` divs and, while `@pending`, the
    `.ai-thinking` "…" bubble
  - `.ai-log-section`
  - `.ai-input-row` with `#ai-input` (textarea) and `#ai-send-btn`, or `#ai-cancel-btn`
    while pending

  `render-settings!` replaces the same `innerHTML` with the settings form
  (`#ai-provider-sel`, `#ai-key-input`, `#ai-include-code-cb` "Share editor code with AI",
  `#ai-auto-apply-cb`, `#ai-settings-save-btn`, `#ai-settings-back-btn`, …).
- `set-pending!` (called by the agent loop through `agent-loop/set-panel-fns!`) resets
  `pending` and calls `render-panel!`. `add-message!` appends and re-renders.
- `show-panel!` / `hide-panel!` / `toggle-panel!` flip `visible?` and the `hidden` class.
  `show-panel!` also renders and calls `refresh-catalogue!`.
- `init!` wires `agent-loop/set-panel-fns!`, plus `click`, `change` and `keydown`
  (Cmd/Ctrl+Enter sends) delegation on `#ai-panel`. It must run after `build-dom!`.
- Callers outside the ns: `app.cljs` (above) and `repulse.env.builtins.ai`. There,
  `(ai)` calls `show-panel!`, and `(ai "…")` calls `show-panel!` + `send!`.

### Agent loop, client, system prompt — `app/src/repulse/ai/`

- `agent_loop.cljs` `run-loop!` calls `(sys-prompt/build)` before **every**
  `client/complete-with-retry!` request. `cancel!` sets a flag. The pending `.then` then
  only clears pending, and no message is added.
- `client.cljs` `complete!` is non-streaming. Hosted providers POST
  `{url, headers, body}` (body = the provider JSON as a string) to **`/api/ai-stream`**. The
  model catalogue goes to **`/api/ai-models`**, called from `refresh-models!` when a key is
  set. For `anthropic`, `anthropic-request` puts the system prompt in `body.system`.
  `parse-complete-response` reads `content[].type == "text"` / `"tool_use"` (`id`, `name`,
  `input`) and `usage.input_tokens + output_tokens`.
- `system_prompt.cljs` `build` assembles rules, the `guard/wrap`ped session snapshot,
  `code-ctx` ("Editor code:", only when `settings/include-code?`), vocabulary and examples.
- `injection_guard.cljs` `wrap` → `"<untrusted>\n" + neutralised + "\n</untrusted>"`.
- `settings.cljs`: keys are `repulse:ai:<k>` (`enabled` = `"true"`, `provider`,
  `key:<provider>`, `include-code` = `"true"`, `auto-apply` = `"true"`, …), read once at
  load into `defonce` atoms.
- `tools.cljs` `show-diff-overlay!` inserts `#ai-proposal-overlay` right after
  `#editor-container` (`insertBefore parent … nextSibling`), with `#ai-apply-btn` /
  `#ai-reject-btn`. With `auto-apply?`, `apply-or-overlay!` dispatches directly and records
  an undo snapshot (`undo/record-pre-edit!`). `propose_edit` args: `match`, `replacement`
  (a blank `match` appends via `edit/append`).

### WS1 selection and outline — `app/src/repulse/ui/outline_panel.cljs`, `selection.cljs`, `app/src/repulse/outline.cljs`

- `outline-panel/entries` (atom, outline entries of the active buffer, re-extracted 150 ms
  after edits), `outline-panel/cursor-pos` (atom, main-selection head, updated in the
  editor update listener) and `outline-panel/selection` (atom, vector of ids, newest
  last). `selection-index` → `{id entry}` via `sel/index-entries`. `refresh!` (buffer
  switch) clears the selection.
- Selection ids: `def:kick`, `defn:…`, `defsynth:…`, `track::bass`, `section:<i>`,
  `fx`, `fx:<i>`, `master`, `master:<i>`. Index entries carry `:kind :name :from :to`. The
  `fx` group is `{:kind :fx-group :name "fx"}`, and `master` is the master entry with
  `:name "fx"` (see decision 12 for its label). `sel/prune` drops ids that no longer resolve.
- `repulse.outline/entry-at entries pos` → the innermost entry with `from <= pos <= to`
  (a section before its song), or nil. Entry names are the source tokens: `":bass"`,
  `":chorus"`, `"kick"`. Songs and masters have `:name nil`.
- Precedents: `outline-panel/extension` (a `Prec.high` keymap with `Mod-Shift-o`),
  `on-doc-keydown` (a `js/document` keydown listener installed in `init!`), and the private
  `mac?`.

### Styles — `app/public/css/main.css`

- `.app-shell, .main-area` are a flex row. `.workbench` is `flex: 1; min-width: 0`.
  `.outline-panel` is 192 px (28 px collapsed). `.inspector` is 220 px. Colours:
  `--cyan: #56b6c2`, `--mid: #5a6a8a`, column bg `#10172a`, borders `#1a2440`.
- `/* ── AI assistant panel ── */` (≈ line 2206): `.ai-toggle-btn` (+ `:hover`, `.active`).
  `.ai-panel` is `position: fixed; top: 41px; right: 0; bottom: 0; width: 380px;
  z-index: 50; box-shadow …`. `.ai-panel.hidden { display: none; }`. `.ai-thinking`,
  `.ai-input-row` (flex, `border-top`), `.ai-textarea`, `.ai-btn` …. The
  `@media (max-width: 760px)` block lists `.ai-toggle-btn`.

### Tests

- CLJS: `packages/core/src/repulse/test_runner.cljs` registers every test ns in both the
  `:require` list and `run-tests`. AI tests: `repulse.ai.safety-test`, `edit-test`,
  `client-test`, `models-test`, `tool-calls-test`, `knowledge-test`. `repulse.outline-test`
  shows how to parse a buffer in Node:
  `(outline/extract (.parse parser text) text)` with `["./lisp-lang/parser.js" :refer [parser]]`.
- Playwright (`playwright.config.ts`): chromium, `baseURL` `http://127.0.0.1:3100`, and
  the web server is `shadow-cljs compile test-harness app` + `scripts/serve-static.mjs
  app/public`. It's a static server, so there's no `/api/*`: every AI request must be
  answered by `page.route`. No AI e2e test exists yet. `e2e/workspace.spec.ts` shows the
  conventions: viewport 1280×800, `addInitScript` clearing localStorage, `load()` waiting
  for `__REPULSE_EDITOR__` / `__REPULSE_APP__`, `setText` via `dispatch`, and outline rows
  `#outline-panel .ol-row[data-id="…"]`. Its layout test asserts `#editor-container .cm-editor`
  width ≥ 1000 and must stay green with the rail (192 + 28 → 1060 px).

## Implementation

### 1. Pure context — `app/src/repulse/ai/context.cljs` (new, `repulse.ai.context`)

Requires only `clojure.string`, `repulse.outline`, `repulse.ui.selection` and
`repulse.ai.injection-guard`. Nothing that touches DOM or the editor (it's tested in Node).

```clojure
(defn resolve-context
  "sel: selection ids; idx: {id entry} (outline-panel/selection-index);
   entries: outline entries; cursor: editor head.
   → nil | {:source :selection|:cursor :label str :items [{:kind :name :from :to}]}"
  [sel idx entries cursor] …)

(defn chip-text [ctx] …)              ; "ctx :bass" | "ctx 2 tracks" | nil
(defn prompt-block [ctx code include-code?] …)   ; string | nil
```

- `resolve-context`: `ids` = `(sel/prune sel idx)`. If `ids` is non-empty, items =
  `(mapv #(item (idx %)) ids)` with `:source :selection`. Otherwise
  `(outline/entry-at entries cursor)` → one item with `:source :cursor`, or nil. `item` =
  `(select-keys e [:kind :name :from :to])`. For the `"master"` id, set `:name nil` (the
  index aliases it to `"fx"`).
- `label`: more than one item → `(str n " tracks")`. Otherwise by kind: `:song` →
  `"song"`, `:master` → `"master"`, `:fx-group` → `"fx"`, else `(:name item)`.
- `describe` (private): `KIND` label + `" "` + name when the name is non-nil.
  `KIND` = `{:def "def" :defn "defn" :defsynth "synth" :track "track" :section "section"
  :song "song" :fx "fx" :fx-group "fx" :master "master" :master-fx "master fx"}`.
  `:fx-group` and `:master` describe as just `"fx"` / `"master"` (their name is not
  appended).
- `prompt-block`: nil when ctx is nil. Otherwise:
  ```clojure
  (str "\n\nUser focus (" (if (= :selection (:source ctx)) "selected in the outline" "under the cursor")
       ") — assume the request refers to this unless the user says otherwise:\n"
       (guard/wrap (cond-> (str/join ", " (map describe (:items ctx)))
                     include-code? (str "\n\n" (str/join "\n\n" (map #(slice code %) (:items ctx)))))))
  ```
  `slice` = `(subs code (min from n) (min to n))` with `n` = `(count code)`. A stale
  range never throws.

### 2. System prompt hook — `app/src/repulse/ai/system_prompt.cljs`

Add `(defonce focus-fn (atom nil))` with a docstring: a 0-arg fn returning the focus block
or nil, set by the assistant panel. In `build`, append `(or (when-let [f @focus-fn] (f)) "")`
right after `(or code-ctx "")`. Nothing else changes.

### 3. Panel — `app/src/repulse/ui/assistant_panel.cljs`

New requires: `[repulse.ai.context :as ai-ctx]`, `[repulse.ai.system-prompt :as sys-prompt]`,
`[repulse.ui.outline-panel :as outline-panel]`, `["@codemirror/view" :refer [keymap]]`,
`["@codemirror/state" :refer [Prec]]`. (`outline-panel` doesn't require the assistant
panel or any `repulse.ai.*` ns, so there's no cycle. Check with `npx shadow-cljs compile app`.)

- **Context:**
  ```clojure
  (defn- current-context []
    (ai-ctx/resolve-context @outline-panel/selection (outline-panel/selection-index)
                            @outline-panel/entries @outline-panel/cursor-pos))
  (defn- editor-text [] (if-let [v @editor/editor-view] (.. v -state -doc (toString)) ""))
  (defn- update-ctx-chip! []
    (when-let [chip (el "ai-ctx-chip")]
      (let [t (ai-ctx/chip-text (current-context))]
        (set! (.-textContent chip) (or t ""))
        (.toggle (.-classList chip) "hidden" (nil? t)))))
  ```
- **Header:** in `render-panel!` (enabled branch), insert the chip after
  `.ai-budget-badge`:
  `<span id="ai-ctx-chip" class="ai-ctx-chip[ hidden]" title="Sent to the AI as context">ctx …</span>`.
  Escape the text with `escape-html` (names come from code).
- **Working state:** replace the `.ai-thinking` line with
  ```html
  <div id="ai-working" class="ai-working"><span class="ai-dots"><span class="ai-dot"></span><span class="ai-dot"></span><span class="ai-dot"></span></span>thinking</div>
  ```
  When `@pending`: `.ai-input-row` becomes `class="ai-input-row ai-input-row--busy"` with
  `<div class="ai-sweep"></div>` as its first child, and the textarea placeholder is
  `working…`.
- **Rail:**
  ```clojure
  (def ^:private STORAGE-KEY "repulse:ai:panel-open")
  (defn- render-rail! []
    (when-let [r (el "ai-rail")]
      (let [open? @visible?]
        (.toggle (.-classList r) "open" open?)
        (.toggle (.-classList r) "busy" (and @pending (not open?)))
        (.setAttribute r "aria-expanded" (str open?))
        (when-let [a (.querySelector r ".ai-rail-arrow")]
          (set! (.-textContent a) (if open? "›" "‹"))))))
  ```
  Call `render-rail!` from `set-pending!`, `show-panel!` and `hide-panel!`.
- **Visibility + persistence:** `show-panel!` / `hide-panel!` additionally write
  `STORAGE-KEY` (`"1"` / `"0"`, in `try`) and call `render-rail!`. `show-panel!` calls
  `update-ctx-chip!` after `render-panel!` (the render already includes the chip, so this
  only matters if the call order changes; it's cheap).
- **`toggle-from-ui!`** (private, decision 7):
  ```clojure
  (defn- toggle-from-ui! []
    (if @visible?
      (let [inside? (some-> (el "ai-panel") (.contains (.-activeElement js/document)))]
        (hide-panel!)
        (when inside? (some-> @editor/editor-view .focus)))
      (do (show-panel!) (some-> (el "ai-input") .focus)))
    true)
  ```
- **Shortcut:**
  ```clojure
  (def extension
    (.high Prec (.of keymap #js [#js {:key "Mod-i" :preventDefault true
                                      :run (fn [_] (toggle-from-ui!))}])))
  (defn- on-doc-keydown [^js e]
    (when (and (not (.-defaultPrevented e))
               (= "i" (str/lower-case (str (.-key e))))
               (if (mac?) (.-metaKey e) (.-ctrlKey e))
               (not (.-shiftKey e)) (not (.-altKey e)))
      (.preventDefault e)
      (toggle-from-ui!)))
  ```
  `mac?` is a private copy of `outline-panel/mac?`.
- **`init!` additions** (after the existing wiring):
  - `(reset! sys-prompt/focus-fn #(ai-ctx/prompt-block (current-context) (editor-text) @settings/include-code?))`
  - Rail click: `(.addEventListener (el "ai-rail") "click" (fn [_] (toggle-from-ui!)))`. Also set the
    rail `title` to `"AI  ⌘I"` on Mac, `"AI  Ctrl+I"` otherwise.
  - The document keydown listener, installed once (guard with
    `(defonce ^:private doc-listener? (atom false))` so hot reload doesn't double-toggle).
  - `(doseq [a [outline-panel/selection outline-panel/cursor-pos outline-panel/entries]]
     (add-watch a ::ctx-chip (fn [_ _ o n] (when (not= o n) (update-ctx-chip!)))))`
  - Restore: `(when (= "1" (try (.getItem js/localStorage STORAGE-KEY) (catch :default _ nil))) (show-panel!))`,
    then `(render-rail!)`.
  - Update the ns docstring's export list (`extension`).

### 4. App wiring — `app/src/repulse/app.cljs`

- `build-dom!`: delete the `#ai-toggle-btn` line. Move the `#ai-panel` line out of
  `.workbench` to after the workbench's closing `</div>`, inside `.app-shell`, followed by:
  ```html
  <button id="ai-rail" class="ai-rail" type="button" aria-label="Toggle AI assistant" aria-expanded="false" aria-controls="ai-panel"><span class="ai-rail-arrow">‹</span><span class="ai-rail-label">ai</span><span class="ai-rail-dot"></span></button>
  ```
- `init`: delete the `(when-let [btn (el "ai-toggle-btn")] …)` block. Add
  `assistant-panel/extension` to the extension array after `inline-slider/extension`.
  `assistant-panel/init!` stays where it is. It runs before the editor exists, which is
  fine: `current-context` reads atoms, `editor-text` handles a nil view, and the watches fire
  once the outline extracts.

### 5. Styles — `app/public/css/main.css`

- Delete `.ai-toggle-btn`, `.ai-toggle-btn:hover`, `.ai-toggle-btn.active` and the
  `.ai-toggle-btn,` selector in the 760 px media block.
- Replace the `.ai-panel` rule:
  ```css
  .ai-panel {
    width: 300px; flex-shrink: 0; box-sizing: border-box; min-height: 0;
    background: #10172a; border-left: 1px solid #1a2440;
    display: flex; flex-direction: column; overflow: hidden;
  }
  ```
  Keep `.ai-panel.hidden { display: none; }`. Let the header wrap at 300 px:
  `.ai-header { flex-wrap: wrap; }`, and badges get `min-width: 0; overflow: hidden;
  text-overflow: ellipsis; white-space: nowrap`.
- Rail:
  ```css
  .ai-rail {
    width: 28px; flex-shrink: 0; box-sizing: border-box; padding: 0; margin: 0;
    background: #10172a; border: 0; border-left: 1px solid #1a2440; border-radius: 0;
    display: flex; flex-direction: column; align-items: center; cursor: pointer;
    font-family: var(--font-mono); color: var(--text);
  }
  .ai-rail-arrow { height: 28px; width: 100%; display: flex; align-items: center;
    justify-content: center; font-size: 13px; border-bottom: 1px solid #1a2440; }
  .ai-rail-label { writing-mode: vertical-rl; font-size: 9px; letter-spacing: .2em;
    text-transform: uppercase; color: var(--mid); margin-top: 12px; }
  .ai-rail.open .ai-rail-label, .ai-rail:hover .ai-rail-label { color: var(--cyan); }
  .ai-rail-dot { display: none; width: 5px; height: 5px; border-radius: 50%;
    background: var(--cyan); margin-top: 10px; animation: ai-dot-pulse 1.1s ease-in-out infinite; }
  .ai-rail.busy .ai-rail-dot { display: block; }
  ```
- Chip: `.ai-ctx-chip { font-size: 9px; color: var(--mid); border: 1px solid var(--bd);
  padding: 0 4px; line-height: 14px; white-space: nowrap; }`, and
  `.ai-ctx-chip.hidden { display: none; }`.
- Working state (replaces `.ai-thinking`):
  ```css
  @keyframes ai-dot-pulse { 0%,100% { opacity: .25; transform: scale(.8); } 50% { opacity: 1; transform: scale(1); } }
  @keyframes ai-sweep { 0% { background-position: -120px 0; } 100% { background-position: 320px 0; } }
  .ai-working { display: flex; align-items: center; gap: 8px; color: var(--mid); font-size: 11px; padding: 4px 2px; }
  .ai-dots { display: flex; gap: 4px; }
  .ai-dot { width: 5px; height: 5px; border-radius: 50%; background: var(--cyan);
    animation: ai-dot-pulse 1.1s ease-in-out infinite; }
  .ai-dot:nth-child(2) { animation-delay: .18s; }
  .ai-dot:nth-child(3) { animation-delay: .36s; }
  .ai-input-row { position: relative; }
  .ai-sweep { position: absolute; left: 0; right: 0; top: -1px; height: 1px;
    background: linear-gradient(90deg, transparent, var(--cyan), transparent) no-repeat;
    background-size: 120px 1px; animation: ai-sweep 1.4s linear infinite; }
  @media (prefers-reduced-motion: reduce) {
    .ai-dot, .ai-rail-dot, .ai-sweep { animation: none; }
    .ai-dot, .ai-rail-dot { opacity: 1; }
  }
  ```

### 6. Unit tests — `app/src/repulse/ai/context_test.cljs` (new, `repulse.ai.context-test`)

Register it in `packages/core/src/repulse/test_runner.cljs` in **both** the `:require`
vector and the `run-tests` call. Vectors K1–K12 below.

### 7. Playwright — `e2e/ai-rail.spec.ts` (new)

Tests T1–T10 below, using the mock in "Mock provider".

### 8. Docs

- `docs/USAGE.md`, "AI assistant": "Getting started" step 1 becomes "Click the **AI rail**
  on the right edge, press **⌘I** (Ctrl+I), or run `(ai)` in the REPL". Add a subsection
  `### The AI rail` covering: the rail and the 300 px panel, the shortcut, that open/closed
  survives a reload, the working state (dots + sweep, the rail dot while closed), and the
  `ctx` chip (selection first, else the entry under the cursor; code is sent only with
  "Share editor code with AI" on). Include the Goal example.
- `README.md`: only if it mentions the `ai` nav button (currently it doesn't; check again).
- No grammar, completion, `builtin_meta.edn` or `docs/ai/builtins.json` change: no new
  built-in. The `(ai)` meta text "opens AI assistant panel" stays correct.
- `ROADMAP.md` / `CLAUDE.md`: mark WS4 delivered with a **Delivered:** section (Rule 2),
  after verification.

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/ai/context.cljs` | **new** — `resolve-context`, `chip-text`, `prompt-block` |
| `app/src/repulse/ai/context_test.cljs` | **new** — K1–K12 |
| `packages/core/src/repulse/test_runner.cljs` | register `repulse.ai.context-test` (require + run-tests) |
| `app/src/repulse/ai/system_prompt.cljs` | `focus-fn` atom, appended in `build` |
| `app/src/repulse/ui/assistant_panel.cljs` | chip, working state, rail render, persistence, `toggle-from-ui!`, `extension`, document keydown, watches, restore |
| `app/src/repulse/app.cljs` | remove `#ai-toggle-btn` + its listener; move `#ai-panel` into `.app-shell`; add `#ai-rail`; add `assistant-panel/extension` |
| `app/public/css/main.css` | remove `.ai-toggle-btn*`; new `.ai-panel` column; `.ai-rail*`, `.ai-ctx-chip`, `.ai-working`, `.ai-dot`, `.ai-sweep`, keyframes, reduced motion |
| `e2e/ai-rail.spec.ts` | **new** — T1–T10 |
| `docs/USAGE.md` | AI rail section, getting-started step |
| `ROADMAP.md`, `CLAUDE.md` | mark WS4 delivered (Rule 2) |

Unchanged on purpose: `client.cljs`, `agent_loop.cljs`, `tools.cljs`, `budget.cljs`,
`injection_guard.cljs`, `settings.cljs`, `env/builtins/ai.cljs`, `test_api.cljs`.

## Definition of done

### Reference test vectors

**Context** (`context_test.cljs`). `C` (175 chars) is:

```lisp
(def kick (seq :bd :_ :bd :_))
(track :drums (->> kick (amp 0.8)))
(track :bass (->> (seq :c2 :eb2) (amp 0.6)))
(song
  (section :intro 4 [:drums])
  (section :chorus 8 :all))
```

`E` = `(:entries (outline/extract (.parse parser C) C))`, `I` = `(sel/index-entries E)`.
Entry ranges: kick [0, 30], `:drums` [31, 66], `:bass` [67, 111], song [112, 175],
`:intro` [120, 147], `:chorus` [150, 174]. `rc` = `(fn [sel cur] (ai-ctx/resolve-context sel I E cur))`.

| # | Call | Expected |
|---|---|---|
| K1 | `(rc [] 160)` | `{:source :cursor :label ":chorus" :items [{:kind :section :name ":chorus" :from 150 :to 174}]}` |
| K2 | `(rc [] 5)`, `(rc [] 30)`, `(rc [] 67)` | labels `"kick"`, `"kick"`, `":bass"` |
| K3 | `(rc [] 115)` | `{:source :cursor :label "song" :items [{:kind :song :name nil :from 112 :to 175}]}` |
| K4 | `(rc ["track::bass"] 5)` | `{:source :selection :label ":bass" :items [{:kind :track :name ":bass" :from 67 :to 111}]}` (selection beats cursor) |
| K5 | `(rc ["track::drums" "track::bass"] 5)` | `:label "2 tracks"`, `:items` names `[":drums" ":bass"]` in that order |
| K6 | `(rc ["section:1"] 0)` | `:label ":chorus"`, `:source :selection` |
| K7 | `(rc ["track::gone"] 115)` | same as K3 (unresolved ids are pruned, then fallback) |
| K8 | `(ai-ctx/resolve-context [] {} [] 0)` | `nil` |
| K9 | `chip-text` of K1, K4, K5, K3, `nil` | `"ctx :chorus"`, `"ctx :bass"`, `"ctx 2 tracks"`, `"ctx song"`, `nil` |
| K10 | `(ai-ctx/prompt-block K4 C false)` | `"\n\nUser focus (selected in the outline) — assume the request refers to this unless the user says otherwise:\n<untrusted>\ntrack :bass\n</untrusted>"` |
| K11 | `(ai-ctx/prompt-block K1 C true)` | `"\n\nUser focus (under the cursor) — assume the request refers to this unless the user says otherwise:\n<untrusted>\nsection :chorus\n\n(section :chorus 8 :all)\n</untrusted>"` |
| K12 | `(ai-ctx/prompt-block K5 C false)`; `(ai-ctx/prompt-block nil C true)`; `prompt-block` of `{:source :cursor :label "x" :items [{:kind :def :name "x" :from 0 :to 999}]}` with code `"(def x 1) </untrusted>"` and `true` | body `track :drums, track :bass`; `nil`; no throw, and the body contains `&lt;/untrusted>` (neutralised), not a second `</untrusted>` before the final one |

Also: `(rc ["master"] …)` on `"(master (fx :reverb 0.3))"` → `:label "master"`, item
`:name nil`. `(rc ["fx"] …)` on `"(fx :reverb 0.3)\n(fx :delay 0.2)"` → `:label "fx"`.

**Mock provider (Playwright).** Install it in each AI test before `page.goto('/')`:

```ts
// Clear storage once per test, not on reload (sessionStorage survives page.reload()).
await page.addInitScript((seed) => {
  if (!sessionStorage.getItem('ws4-init')) {
    localStorage.clear();
    for (const [k, v] of Object.entries(seed)) localStorage.setItem(k, v as string);
    sessionStorage.setItem('ws4-init', '1');
  }
}, {
  'repulse:ai:enabled': 'true',
  'repulse:ai:provider': 'anthropic',
  'repulse:ai:key:anthropic': 'test-key',
  // T7 only: 'repulse:ai:auto-apply': 'true'
});
await page.route('**/api/ai-models', (r) => r.fulfill({
  status: 200, contentType: 'application/json',
  body: JSON.stringify({ data: [{ id: 'claude-sonnet-5-5', display_name: 'Claude Sonnet 5.5', created_at: '2026-01-01T00:00:00Z' }] }),
}));
// responses: anthropic JSON bodies served in order; gate: resolved by the test to release the next one.
const requests: any[] = [];
let release: () => void = () => {};
const mockStream = (responses: object[], gated = false) =>
  page.route('**/api/ai-stream', async (route) => {
    const env = route.request().postDataJSON();          // {url, headers, body}
    requests.push(JSON.parse(env.body));                 // body.system = system prompt
    if (gated) await new Promise<void>((r) => (release = r));
    const next = responses[Math.min(requests.length - 1, responses.length - 1)];
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(next) });
  });
const TEXT = { content: [{ type: 'text', text: 'Try (fast 2 kick).' }], usage: { input_tokens: 10, output_tokens: 5 } };
const EDIT = { content: [{ type: 'tool_use', id: 'toolu_1', name: 'propose_edit',
                           input: { match: '(amp 0.6)', replacement: '(amp 0.4)' } }],
               usage: { input_tokens: 10, output_tokens: 5 } };
```

Use the viewport 1280×800 and buffer `C` (above) via `setText`, as in `workspace.spec.ts`.
Send = fill `#ai-input`, click `#ai-send-btn`. Non-AI tests (T1–T3) use the same init
script with an empty seed.

**Playwright** (`e2e/ai-rail.spec.ts`):

| # | Steps | Expected |
|---|---|---|
| T1 | Load (no seed). | `#ai-toggle-btn` count 0. No `.top-nav button` with exact text `ai`. `.workbench #ai-panel` count 0. `.app-shell > #ai-panel + #ai-rail` count 1, and `#ai-rail` is the last child of `.app-shell`. `#ai-panel` hidden. `#ai-rail` box: width 28 (±0.5), `x + width` = 1280 (±0.5), height = `.app-shell` height (±1). `#ai-rail .ai-rail-arrow` text `‹`, `aria-expanded="false"`. `#outline-panel` lacks `.collapsed`. `#editor-container .cm-editor` width ≥ 1000. `#ai-rail .ai-rail-dot` hidden. |
| T2 | Click `#ai-rail`. Reload. Click `#ai-rail`. Reload. | After the 1st click: `#ai-panel` visible, box width 300 (±0.5), x = 952 (±1). Arrow `›`, `aria-expanded="true"`, `#ai-rail` has class `open`. `localStorage['repulse:ai:panel-open']` = `"1"`. Editor width = T1 width − 300 (±2). After reload: still open, arrow `›`. After the 2nd click: hidden, key `"0"`, arrow `‹`. After the 2nd reload: hidden. |
| T3 | Focus the editor (`__REPULSE_EDITOR__.focus()`), selection at 75, press `ControlOrMeta+i`. Press it again (focus is now in `#ai-input`). Run `document.activeElement.blur()` and press it once more. | Opens, and `#ai-input` is focused. Doc equals `C` and the main selection is still empty at 75 (no `selectParentSyntax`). 2nd press closes it and the editor has focus (`.cm-editor.cm-focused` in `#editor-container`). 3rd press (focus on body) opens it. Each press toggles exactly once. |
| T4 | Seed enabled. `mockStream([TEXT], true)`. Open the rail, send `make it swing`. Click the rail (close), click it again (open), then `release()`. | While gated: `#ai-panel #ai-working` visible and contains `thinking`. `#ai-working .ai-dot` count 3. Computed `animationName` of the dots = `ai-dot-pulse`, `animationDuration` = `1.1s`, `animationDelay` = `0s`, `0.18s`, `0.36s`. `.ai-input-row.ai-input-row--busy > .ai-sweep` count 1, `animationName` `ai-sweep`, `animationDuration` `1.4s`. `#ai-input` placeholder `working…`. `#ai-cancel-btn` visible. Closed: `#ai-rail` has class `busy` and `.ai-rail-dot` is visible. Reopened: `.ai-rail-dot` hidden. After release: `#ai-working` count 0, `.ai-sweep` count 0, `#ai-send-btn` visible, and the last `.ai-msg--assistant` contains `Try (fast 2 kick).`. `#ai-rail` lacks `busy`. `requests.length` = 1. |
| T5 | Seed enabled. `mockStream([TEXT], true)`. Send, click `#ai-cancel-btn`, then `release()`, wait 500 ms. | After the cancel: `#ai-working` count 0 and `#ai-send-btn` visible. No `.ai-msg--assistant` contains `Try (fast 2 kick).`. |
| T6 | Seed enabled, buffer `C`, wait until the `#outline-panel .ol-row[data-id="track::bass"]` row exists. Open the rail, click the row. Send `more movement` (`mockStream([TEXT])`). Then focus the editor, press `Escape`, dispatch selection 160. Then selection 115. Then `setText('')`. | After the row click: `#ai-ctx-chip` text `ctx :bass`, visible, `title` `Sent to the AI as context`. `requests[0].system` contains `User focus (selected in the outline)` and `<untrusted>\ntrack :bass\n</untrusted>`, and does **not** contain `(track :bass (->> (seq :c2 :eb2) (amp 0.6)))` (include-code off). After Esc + 160: chip polls to `ctx :chorus`. At 115: `ctx song`. Empty buffer: chip hidden, text `""`. The textarea keeps typed text across chip updates (fill `#ai-input` with `abc` before the Esc step, and its value is still `abc` at the end). |
| T7 | Seed enabled + `'repulse:ai:auto-apply': 'true'`, buffer `C`. `mockStream([EDIT, TEXT])`. Send `quieter bass`. Then click `#ai-revert-btn`. | Doc contains `(amp 0.4)` and not `(amp 0.6)`. `#ai-revert-btn` visible. `requests.length` = 2. After the revert: doc equals `C`. |
| T8 | Seed enabled (auto-apply off), buffer `C`. `mockStream([EDIT, TEXT])`. Send `quieter bass`. Then click `#ai-apply-btn`. | `.workbench > #editor-container + #ai-proposal-overlay` count 1 (unchanged position). After Apply: overlay count 0, doc contains `(amp 0.4)`, and the last `.ai-msg--assistant` contains `Try (fast 2 kick).`. |
| T9 | Seed enabled. Open the rail, click `#ai-settings-btn`, then `#ai-settings-back-btn`. | `#ai-panel #ai-provider-sel` visible with value `anthropic`. After Back: `#ai-input` visible and `#ai-ctx-chip` present. |
| T10 | Seed enabled. Focus `#cmd-container .cm-content`, type `(ai)`, press Enter. | `#ai-panel` visible, `localStorage['repulse:ai:panel-open']` = `"1"`, arrow `›`. |

### Checklist

- [ ] No `#ai-panel` in `.workbench` and no `ai` nav button (`#ai-toggle-btn` gone from
      DOM and CSS). `#ai-panel` + `#ai-rail` are the last two children of `.app-shell`
      (T1).
- [ ] The rail is 28 px at the right edge. The panel is 300 px directly to its left. The
      arrow switches `‹`/`›` and `aria-expanded` follows (T1, T2).
- [ ] Open/closed persists in `localStorage["repulse:ai:panel-open"]` as `"1"`/`"0"` and
      survives a reload. The first visit is closed (T2). `(reset!)` clears it with the other
      `repulse:ai:` keys.
- [ ] ⌘I / Ctrl+I toggles from the editor (without running `selectParentSyntax`), from the
      AI textarea and with nothing focused, exactly once per press (T3). If the fallback
      `Mod-Shift-a` was needed, the spec's decision 1 fallback is followed and noted in the
      PR.
- [ ] With the mocked provider, the working state (3 dots `ai-dot-pulse` 1.1 s with delays
      0 / .18 / .36 s, "thinking", `.ai-sweep` 1.4 s, `working…` placeholder) appears during
      the request and disappears after. The rail dot shows only while the panel is closed
      (T4).
- [ ] Send, cancel (T5), revert (T7), Apply on the diff overlay (T8) and settings (T9) work
      from the rail panel. `(ai)` opens it (T10).
- [ ] Context: the chip shows the selection (`ctx :bass`), else the entry under the cursor
      (`ctx :chorus`, `ctx song`), and hides with no context. The system prompt carries the
      focus block, with code only when "Share editor code with AI" is on (T6, K1–K12).
- [ ] At 1280×800 with the outline open and the AI panel closed, the editor is ≥ 1000 px
      wide (T1). `e2e/workspace.spec.ts` "layout: …wide editor" still passes.
- [ ] All existing tests stay green, unmodified: `npm test` (incl. `repulse.ai.safety-test`,
      `edit-test`, `client-test`, `models-test`, `tool-calls-test`, `knowledge-test`,
      `repulse.outline-test`, `repulse.ui.selection-test`), and every existing
      `e2e/*.spec.ts` (`workspace`, `outline`, `inline-slider`, `master`, `song`,
      `buffers`, `audio-render`).
- [ ] `repulse.ai.context-test` is registered in `test_runner.cljs` (require + run-tests),
      and K1–K12 pass.
- [ ] USAGE.md documents the rail, the shortcut, persistence, the working state and the
      `ctx` chip. The "Getting started" step no longer mentions an **ai** header button.
- [ ] `npm run test:all` + `npx shadow-cljs compile app` pass. clj-kondo reports no new
      warnings in the touched files.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5). It covers: opening
      and closing via the rail and ⌘I (from the editor and from the command bar),
      persistence across reload, the working state with a real provider (or the cancel
      button mid-request) including the rail dot while closed, the `ctx` chip for a
      selected track, a selected section, the cursor fallback and an empty buffer, the
      "Share editor code" effect (ask "what does the focused code do?" with it on and off),
      `(ai "…")`, and ≥ 2 regressions: the WS1 inspector opening beside the outline with the
      AI panel open (editor still usable), and Apply/Reject on a proposed edit.

## What NOT to do

- No new providers (AI2b), no streaming client, and no changes to tool calling, budgets,
  auto-apply, undo or the injection guard. The only AI-core edit is the additive
  `focus-fn` hook.
- Don't move or restyle the Apply / Reject diff card (`#ai-proposal-overlay`). It stays
  below the editor.
- No resizable AI panel (fixed 300 px), no rail in the `<repulse-editor>` embed, and no
  mobile/overlay variant (O4).
- No redesign of the message list, the settings drawer or the input (the design's `⏎` kbd
  input and diff styling are out of scope). Only the header chip and the working state are
  new.
- Don't overwrite `outline-panel/on-selection-change` (it belongs to the inspector). Use
  `add-watch`.
- No AI5 variation workflows or multi-variant audition.
