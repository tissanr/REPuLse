# Phase SONG3 — Editor Navigation: Outline Panel & Go-to-Definition

> **Automation:** 🤖 auto — the outline extraction is a pure function over the syntax tree
> and the UI behaviour (click → cursor moves, Cmd-click → jump) is verifiable with
> Playwright.

**Milestone:** M2 — Full songs in code
**Depends on:** SONG1 (song/section forms to list; `repulse.song` queries). Extended by
SONG4 (cross-buffer jumps) and by the workspace phases WS1–WS4 (inspector, master group,
inline sliders, AI rail).

## Goal

Songs are written top to bottom with the arrangement at the bottom, so the file gets
long. Make it navigable:

- an **outline panel** on the left of the editor, listing every top-level `def`, `defn`,
  `defsynth`, `track` and the `song` block with its `section`s. Clicking an entry moves
  the cursor there;
- **go-to-definition**: Cmd/Ctrl-click (or F12) on `kick` or `:drums` jumps to where it is
  defined, and Shift+F12 jumps back.

There's no new Lisp syntax: this phase is editor behaviour only.

```lisp
(def kick (seq :bd :_ :bd :_))       ; ← outline: def   kick          L1
(track :drums (stack kick snare))    ; ← outline: track :drums        L4
...
(song                                ; ← outline: ▾ song  24 bars     L40
  (section :verse 8 [:drums :bass])  ;      :verse 8b ••          L42
  ...)                               ; Cmd-click :drums → jumps to (track :drums …)
```

```text
OUTLINE 12                       ‹
   def    kick                  L1
   def    snare                 L2
   defn   humanize              L5
   synth  pad                   L9
 • track  :drums               L14     • = audible in the playing section
 ■ track  :keys                L27     ■ = muted (dimmed)
▾  song   24 bars              L40
      :intro   4b  ••          L41
 ▶    :chorus  8b  ••••        L43     ▶ = playing, cyan, 1 px progress line
───────────────────────────────────
 ⌘ click  F12  go to def
 ⇧F12 back          ⌘⇧O hide
```

## Design reference

The visual design is the **outline column** of Claude Design's *Outline Panel v2*, checked
in at [`design/outline-panel-v2/`](../design/outline-panel-v2/). Open
`Outline Panel v2.html` in a browser to see it live. `outline-app.jsx` (`ORow`, `Outline`,
`OutlineRail`, `OutlineHeader`, `OutlineFooter`) and `outline-v2.jsx` (`Row`, `OutlineV2`) hold
the exact sizes and colours. Only the outline column belongs to SONG3 (see decision 6):
**ignore** the status head, inspector, master group, inline sliders and AI rail in those
files.

## Background

- **App layout:** `app/src/repulse/app.cljs` `build-dom!` writes the shell as an HTML string:
  `header.top-nav`, then `div.main-area.app-shell` containing `div.workbench` (editor,
  snippet/ai panels, console, `#track-panel`, status bar) and `#context-panel` on the
  right. `.app-shell` is `display:flex` (`app/public/css/main.css` ~L1592). There's nothing
  left of the workbench today.
- **Editor:** `app/src/repulse/ui/editor.cljs`. `make-editor` builds the main
  CodeMirror 6 view (extensions: `history`, `lineNumbers`, `oneDarkTheme`, `lispLanguage`,
  `highlights-field`, `lintGutter`, two `EditorView.updateListener`s, keymap = custom
  bindings + `defaultKeymap` + `historyKeymap`). `editor/editor-view` holds the view.
  `make-editor` is also used by `app/src/repulse/embed.cljs` (the `<repulse-editor>`
  element), which must **not** get the outline.
- **Existing keys:** `Alt-Enter` eval, `Ctrl-.` and `F9` `(upd)`, `Escape` focuses the
  command bar, and `F9` is also handled globally in `app.cljs` ~L410. `defaultKeymap` binds
  `Alt-ArrowLeft` (word-left on macOS), so it can't be the "back" key.
- **Grammar:** `app/src/repulse/lisp-lang/repulse-lisp.grammar`. Node names: `Program`,
  `List`, `Vector`, `Map`, `OpenParen`, `CloseParen`, `LineComment`, `Number`, `String`,
  `Keyword`, `BuiltinName`, `Symbol`. `def defn defsynth track song section` are
  **`BuiltinName`** tokens. A user's name is a `Symbol`, or a `BuiltinName` if it
  collides with a built-in. `:drums` is a `Keyword`. Comments and strings are single
  tokens, so parens inside them never affect the tree. Lezer recovers from unbalanced
  input and marks error nodes (`node.type.isError`).
- **Tree-walk precedent:** `app/src/repulse/lisp-lang/defs-completion.js` iterates
  `syntaxTree(state)` to find `(def <Symbol> …)`. `insert-helper.js` uses `resolveInner`.
  The generated parser is `app/src/repulse/lisp-lang/parser.js` (exports `parser`).
  `index.js` exports `lispLanguage`.
- **Song state (SONG1):** `packages/core/src/repulse/song.cljs`: `section-at-bar`,
  `wrap-bar`, `song-status` (`{:bar :total :section :section-bar :section-bars}`), and
  `:sections` (vector of `{:name :bars :tracks :start :end}` in source order).
  `repulse.ui.context-panel/song-bar` gives the current scheduler bar relative to the
  song origin. `@audio/scheduler-state` holds `:song`, `:tracks` and `:muted`.
  `app.cljs` ~L468 re-renders the context panel and track panel on every
  `scheduler-state` change. The outline hooks into the same watch.
- **Panel precedent:** `app/src/repulse/ui/context_panel.cljs` renders HTML strings with
  `escape-html` (`repulse.ui.html`) and repaints through `requestAnimationFrame`
  (`schedule-render!`). Follow the same style: no framework.
- **Tests:** `npm test` runs `packages/core/src/repulse/test_runner.cljs` (node target
  `:test` in `shadow-cljs.edn`). App-layer pure tests live next to their namespace
  (e.g. `app/src/repulse/ui/timeline_test.cljs`) and are registered in the runner.
  Playwright (`playwright.config.ts`) serves `app/public` via
  `scripts/serve-static.mjs` after compiling only `test-harness`. The harness API
  (`window.__REPULSE_TEST__`) lives in `app/src/repulse/test_api.cljs`.

## Design decisions

All questions were decided by the project owner: 1, 3 and 4 on 2026-10-29, and 2, 5 and 6
on 2026-10-30 after the Claude Design review. They are fixed for this phase.

1. **Source of truth: the Lezer syntax tree.** The outline is extracted from the
   CodeMirror/Lezer tree already in the editor, not from the Lisp reader. Lezer keeps a
   partial tree on invalid code, which the "works while code is invalid" requirement
   needs. The extraction is a pure function over the tree and the text that returns
   `{:kind :name :from :to :children}`, so it can be unit-tested.
   **Decided:** Lezer syntax tree.

2. **Panel placement: a new left column**, 192 px wide, between the window edge and the
   workbench, per *Outline Panel v2*. It collapses to a 28 px rail (`OutlineRail`): a `›`
   button, a vertical "OUTLINE" label, and one 4 px bar per song section (height ∝ bars,
   cyan when playing). The right context panel stays unchanged in SONG3.
   **Decided:** left column, collapsible to a rail; context panel untouched.

3. **Shortcuts.** Go-to-definition: **Cmd/Ctrl-click** on a name and **F12** with the
   cursor on a name. Back: **Shift+F12** returns to the position before the last jump (a
   position stack, max 50). Alt-click isn't used because Alt is the eval modifier.
   Alt+← isn't used because `defaultKeymap` binds it. Hide/show the outline:
   **Mod-Shift-o** plus the `‹`/`›` buttons.
   **Decided:** Cmd/Ctrl-click + F12 to go, Shift+F12 back, Mod-Shift-o to toggle.

4. **Current playing section.** While a song plays, the outline marks the `section` entry
   that is sounding (▶, cyan, bold, 1 px progress line). It uses SONG1's
   `section-at-bar` + `song-bar` and refreshes on the same tick as the context panel. It is
   visually distinct from the "cursor inside" highlight, and both can apply at once.
   **Decided:** highlight the playing section.

5. **Clicking an entry moves the cursor and reveals the form.** The cursor goes to the
   form's `(`, the line scrolls to about ⅓ from the top, the form's range flashes with
   background `#1f2d4e70` for ~600 ms, and focus returns to the editor. (v2's
   select-without-moving behaviour belongs to the inspector phase WS1.)
   **Decided:** move cursor + reveal.

6. **Scope: outline column only.** The rest of *Outline Panel v2* is split into planned
   phases: WS1 (inspector and retiring the context panel, status head in the outline),
   WS2 (`(master …)` form and its outline group), WS3 (inline `(slider …)` + ⌘K S), and
   WS4 (AI rail). SONG3 must not build any of them, but it should keep the outline's row
   renderer and entry model easy to extend with new kinds and a selection state.
   **Decided:** outline only.

## Implementation

### 1. Pure extraction — `app/src/repulse/outline.cljs` (new, ns `repulse.outline`)

No DOM and no CodeMirror view: only a Lezer `Tree` (or `SyntaxNode`) and the document
text.

```clojure
(extract tree text) ;; → {:entries [entry …] :partial? bool}
;; entry = {:kind     :def | :defn | :defsynth | :track | :song | :section
;;          :name     "kick" | ":drums" | nil (song)
;;          :from N :to N          ; the whole List form, absolute doc offsets
;;          :name-from N :name-to N ; the name token (nil for song)
;;          :bars     8             ; section only, when the 2nd arg is a Number literal
;;          :tracks   [":drums" …]  ; section only: Keyword children of its Vector, or [:all]
;;          :children [entry …]}    ; song only: its sections, in source order
```

Rules:
- Walk only the **direct children of `Program`** (top level). For each `List`, take the
  first non-paren child. If its text is `def`/`defn`/`defsynth`/`track`/`song`, it's an
  entry. Name = the next sibling's text when it's a `Symbol`/`BuiltinName` (def, defn,
  defsynth) or a `Keyword` (track). A form without a name token is skipped, except `song`.
- `song` children: direct `List` children whose head is `section`. Name = the Keyword
  after it; `:bars` = the following `Number`, parsed; `:tracks` = Keyword texts in the next
  `Vector`, or `[":all"]` if that arg is the keyword `:all`.
- `:partial?` = the tree contains any error node. Forms that still parse are listed. A form
  whose `List` has no `CloseParen` is still listed (Lezer recovers), with `:to` = end of
  the recovered node.
- Helpers (pure, tested):
  - `(entry-at entries pos)`: innermost entry (section before song) with `from ≤ pos ≤ to`, or nil.
  - `(definition-for entries kind name)`: `kind` is `:symbol` or `:keyword`. Symbols match
    `def`/`defn`/`defsynth` names, keywords match `track` names. If several match, take the
    **last one** (the binding that wins on eval).
  - `(song-bars entry)`: sum of `:bars`, or nil if any is missing.

**Spike first:** check that a node unit test can `(:require ["./lisp-lang/parser.js" :refer [parser]])`
and call `(.parse parser text)`. If it works, `app/src/repulse/outline_test.cljs` runs under
`npm test` (register it in `test_runner.cljs`). If shadow's node target can't load the ESM
parser, keep `repulse.outline` free of that require, put the tests into the Playwright
harness instead (expose `outline(code)` in `test_api.cljs` and test it in
`e2e/outline.spec.ts`), and record which way you went in the PR.

### 2. Go-to-definition — `app/src/repulse/ui/goto.cljs` (new)

- `(name-at state pos)`: `syntaxTree(state).resolveInner(pos, ±1)`. Returns
  `{:kind :symbol|:keyword :text …}` for a `Symbol`, `BuiltinName` or `Keyword` node, or nil.
- `(goto-definition! view pos)`: name → `outline/definition-for` over the current
  entries. If found and different from the current form, push the current cursor pos on
  `jump-stack` (atom, max 50), then set the selection to the target's `:name-from` (fallback
  `:from`), `scrollIntoView` (y "start", margin = ⅓ view height), and flash (see 3).
  **Unknown names do nothing** and log nothing.
- `(go-back! view)`: pop `jump-stack` and move there (clamped to the doc length).
- `extension`: `Prec.high` keymap `[{key "F12" run …} {key "Shift-F12" run …}]` +
  `EditorView.domEventHandlers` `mousedown`: when `metaKey` (mac) or `ctrlKey` (other)
  is down, use `posAtCoords`, `goto-definition!`, `preventDefault`. While Mod is held, hovering a
  resolvable name shows an underline + pointer cursor (a `ViewPlugin` with a mark
  decoration). This is optional; skip it if it complicates things and note that in the PR.

### 3. Outline panel — `app/src/repulse/ui/outline_panel.cljs` (new)

- **DOM:** `build-dom!` in `app.cljs` adds `<div id="outline-panel" class="outline-panel"></div>`
  as the **first child** of `.app-shell` (before `.workbench`).
- **State atoms:** `entries` (from extract), `partial?`, `cursor-pos`, `collapsed?`
  (persisted in `localStorage` key `repulse:outline-collapsed`, every access in
  try/catch; default collapsed when `innerWidth ≤ 760`), `song-open?` (fold ▾/▸).
- **`extension`**: an `EditorView.updateListener`. On `docChanged`, debounce 150 ms and then
  re-extract using `ensureSyntaxTree(state, docLen, 200)` (fall back to `syntaxTree`).
  On `selectionSet`, update `cursor-pos` and repaint (cheap, no re-extract). The outline must
  update ≤ 300 ms after typing stops.
- **Install** only in the main app. In `app.cljs`, right after `make-editor`:
  `(.dispatch view #js {:effects (.of StateEffect.appendConfig #js [outline-panel/extension goto/extension])})`.
  Embed stays untouched.
- **Render** (HTML string, `escape-html` on every name, RAF-coalesced like
  `context-panel/schedule-render!`):
  - Header row, 28 px: `OUTLINE` (9 px, .14em, `#4a5a7e`) + count (`#3a4868`) + a
    `~ partial` tag (orange `#d19a66`, title "Syntax error — showing what still parses")
    when `partial?` + a `‹` collapse button (22×22).
  - List (scrolls): one row per entry; the song's sections are indented 12 px under `▾ song`.
  - Footer: key hints with `<kbd>`-styled boxes. `⌘ click` `F12` go to def, `⇧F12` back,
    `⌘⇧O` hide. Show `Ctrl` instead of `⌘` on non-Mac (`navigator.platform`).
  - Empty state (no entries): "No definitions yet. `(def name …)` and `(track :name …)`
    appear here." with syntax colours as in `Outline` `mode='empty'`.
  - Collapsed: the 28 px rail (decision 2). Clicking the rail expands it.
- **Row** (`Row` in `outline-v2.jsx`): height 22, font 11 px.
  - 18 px glyph column: `▶` playing / `■` muted / `•` audible / `▾▸` fold.
  - Kind label, 44 px wide, 10 px. Colours: def `#c678dd`, defn `#98c379`, synth `#d19a66`,
    track `#e5c07b`, song `#e94560`. Display `defsynth` as `synth`.
  - Name, ellipsis, plus meta in `#5a6a8a` (song: `N bars`; section: `8b`).
  - Section only: track dots, 5 px each. Colour per track by its index among the
    outline's tracks, cycling `#e94560 #56b6c2 #e5c07b #c678dd #98c379 #d19a66`.
  - Line number `L42`, 30 px wide, right-aligned, 10 px.
  - States: hover bg `#151f37`; cursor-inside bg `#1b2744` + 2 px left bar `#5a6a8a`;
    playing bg `#122634`, name cyan bold, 1 px cyan progress line at the bottom
    (`width = section-bar / section-bars`); cursor-inside + playing bg `#1a3040` and a cyan
    left bar.
  - Tracks while a song plays: muted → `■`, opacity .55; in the current section and not
    muted → cyan `•`; not in the section → name `#5a6a8a`.
  - "Cursor inside" = `entry-at` for the cursor (a section wins over the song).
- **Playing section:** in `app.cljs`, the existing `scheduler-state` watch also calls
  `outline-panel/schedule-render!`. The renderer reads `(:song state)`, computes `q` via
  `song-bar` + `wrap-bar`, and finds the section index in `(:sections song)` with
  `start ≤ q < end`. It highlights the outline's section **at the same index**, but only
  if its name matches (the text may have changed since eval).
- **Clicks** (one delegated listener on `#outline-panel`; rows carry `data-from`): on a row,
  move the cursor and reveal it (decision 5); flash = a `StateField` with a single mark
  decoration (`class "outline-flash"`), cleared by a `setTimeout`. On `▾/▸`, toggle
  `song-open?`. On `‹`/rail, toggle `collapsed?`.
- **Toggle shortcut:** `Mod-Shift-o` in the goto/outline `Prec.high` keymap toggles
  `collapsed?`. If Playwright shows that Chromium consumes it, keep only the buttons and
  record that in the PR.

### 4. CSS — `app/public/css/main.css`

Add an `/* Outline panel (SONG3) */` block next to the context-panel rules:
`.outline-panel { width:192px; flex-shrink:0; background:#10172a; border-right:1px solid #1a2440; display:flex; flex-direction:column; font-family:var(--font-mono); overflow:hidden }`
plus `.outline-panel.collapsed { width:28px }` and classes for the header, rows, states,
footer, rail and `.cm-editor .outline-flash { background:#1f2d4e70 }`. Use the hex values
above. Don't touch `.context-panel` rules.

### 5. Playwright — `e2e/outline.spec.ts` (new)

The UI tests need the real app page. Extend the `webServer` command in
`playwright.config.ts` to `npx shadow-cljs compile test-harness app && node scripts/serve-static.mjs …`.
Load `/`, set the editor text through `window` (expose nothing new if
`editor/editor-view` is reachable; otherwise add a tiny `window.__REPULSE_EDITOR__`
getter in `app.cljs`, dev-only is fine), and assert the DoD items below. If the app page
fails to boot in the test server (e.g. missing WASM), fix the boot path or explain the
blocker. Don't skip the tests silently.

### 6. Docs

- `docs/USAGE.md`: new "Outline & go-to-definition" section (panel, states, shortcuts,
  collapse).
- `ROADMAP.md`: SONG3 → `✅ *delivered*` + **Delivered:** list. In the order table, Spec
  `outline` → `full`.
- `CLAUDE.md` phase table: if a SONG3 row exists, mark it delivered. No new built-ins, so
  no grammar/completions/`builtin_meta.edn` changes.

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/outline.cljs` | new: pure extraction + helpers |
| `app/src/repulse/outline_test.cljs` | new: unit tests (or harness tests, see spike) |
| `packages/core/src/repulse/test_runner.cljs` | register `repulse.outline-test` |
| `app/src/repulse/ui/goto.cljs` | new: name-at, goto/back, keymap + Mod-click |
| `app/src/repulse/ui/outline_panel.cljs` | new: state, render, listeners, extension |
| `app/src/repulse/app.cljs` | outline div in `build-dom!`, appendConfig extensions, watch hook |
| `app/public/css/main.css` | outline panel styles |
| `app/src/repulse/test_api.cljs` | only if the spike forces harness tests |
| `playwright.config.ts` | also compile `app` |
| `e2e/outline.spec.ts` | new |
| `docs/USAGE.md`, `ROADMAP.md`, `CLAUDE.md` | docs |

## Definition of done

Reference buffer `R` (used by tests):

```lisp
(def kick (seq :bd :_ :bd :_))
(def snare (seq :_ :sd))
; (def ghost (seq :x)) — commented out, must not appear
(defn humanize [p] (amp 0.9 p))
(defsynth pad [freq] (saw freq))
(track :drums (stack kick snare (seq "(not a paren)")))
(track :bass (seq :c2 :eb2))
(song
  (section :intro 4 [:drums])
  (section :verse 8 [:drums :bass])
  (section :outro 2 :all))
```

- [ ] `extract` on `R` returns, in order: `def kick` L1, `def snare` L2, `defn humanize` L4,
      `defsynth pad` L5, `track :drums` L6, `track :bass` L7, `song` L8 with children
      `:intro` (bars 4, tracks `[":drums"]`), `:verse` (8, `[":drums" ":bass"]`),
      `:outro` (2, `[":all"]`). `song-bars` = 14. `:partial?` false. `ghost` absent.
- [ ] Unit tests also cover: a string containing parens (`"(not a paren)"` doesn't
      break the forms after it); nested `(def x (def y 1))` lists only `x`; `R` with the
      closing `)` of `(track :drums …` deleted → `:partial?` true and `def kick`,
      `def snare`, `defn humanize`, `defsynth pad` still listed; empty text → no entries;
      `definition-for` with a duplicate `def kick` → the last one; `entry-at` inside
      `:verse` → the `:verse` section.
- [ ] Playwright: with `R` loaded, the outline shows 10 rows (7 entries + 3 sections) and
      the header count.
- [ ] Playwright: clicking the `track :bass` row puts the cursor at the `(` of line 7,
      line 7 is visible, and focus is in the editor.
- [ ] Playwright: Cmd/Ctrl-click on `:bass` inside the `:verse` section jumps to line 7.
      Ctrl/Cmd-click on `kick` in line 6 jumps to line 1. Cmd/Ctrl-click on `:bd` does
      nothing (cursor stays, no console error).
- [ ] Playwright: F12 with the cursor on `snare` in line 6 jumps to line 2; Shift+F12
      returns to the previous position in line 6.
- [ ] Playwright: typing `(def hats (seq :hh))` on a new first line makes `def hats`
      appear within 300 ms; deleting a `)` in the middle shows `~ partial` and keeps the
      rest of the outline.
- [ ] Playwright: the cursor inside `:verse` gives that row the cursor-inside class;
      moving the cursor updates it.
- [ ] Playwright: `‹` collapses to the 28 px rail, `›` restores it, and the state
      survives a reload.
- [ ] Playing section: evaluating `R` with a short song (e.g. `(bpm 480)`) and playing it
      marks `:intro`, then `:verse`, with the playing class (Playwright with the real
      audio backend if the harness allows; otherwise a unit test of the pure
      "which index is playing" helper with SONG1 song values for bars 0, 3, 4, 12, 13).
- [ ] The `<repulse-editor>` embed has no outline and no F12 handler (it's not installed there).
- [ ] `docs/USAGE.md` documents the panel and the shortcuts. ROADMAP marked delivered.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass. `gen:grammar` /
      `gen:ai-docs` show no drift.
- [ ] PR description ends with a `## Test protocol` (CLAUDE.md Rule 5) covering the
      outline, go-to-def, back, partial state, collapse, playing section, and ≥2
      regressions (Alt+Enter eval, context-panel sliders still patch code).

## What NOT to do

- No inspector, no status head, no removal or change of the right context panel (WS1).
- No `(master …)` form or master-fx group (WS2), no inline `(slider …)` (WS3), no AI
  rail (WS4).
- No multi-buffer navigation (SONG4), no rename/refactor, no timeline/arranger editing.
- Don't change Lisp evaluation semantics or add built-ins.
- Don't install the outline or goto keymap in the embed editor.
- No new npm dependencies; use what `@codemirror/*` already provides.
