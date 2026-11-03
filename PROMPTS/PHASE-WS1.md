# Phase WS1 — Inspector Column & Status Head (retire the context panel)

> **Spec status:** full
> **Automation:** 🤖 auto — selection, inspector content and slider write-back are DOM +
> buffer behaviour, verifiable with unit tests and Playwright.

**Milestone:** M2 — Full songs in code
**Depends on:** SONG3 (outline column, entry model). Recommended before WS2 and WS3.

## Goal

Finish the workspace layout from Claude Design's *Outline Panel v2*
(`design/outline-panel-v2/`, `outline-v2.jsx`: `StatusHead`, `Inspector`,
`TrackBlock`, `SectionBlock`, `DefBlock`). The right **context panel goes away**, and its
parts move to where they belong:

- **Status head** at the top of the outline column: BPM (click to edit; ↑↓ ±1, ⇧↑↓ ±10,
  40–300), a `wasm`/`js` badge, a **song progress bar** split into one segment per section
  (click a segment = cue that section at the next bar), and audio latency.
- **Inspector column** (220 px, resizable 180–380 px, right of the outline). It's only
  open while something is selected and shows only that thing:
  - track: M/S buttons, param sliders, fx chain in signal order
  - section: bar strip, `▶ cue`, the tracks in it with M/S + amp
  - def/defn/synth: a syntax-coloured source preview + "used by"
  - fx (top-level `(fx …)` forms, until WS2 brings `(master …)`): their sliders
- **Selection model:** clicking an outline row selects it and highlights its range in
  the editor (bg `#1f2d4e70`, gutter bar `#4a5a7e`), **without moving the cursor**.
  Clicking the same row again deselects. ⇧-click adds/removes tracks (multi-select).
  Esc clears. `↗ L20` in the inspector header or F12 moves the cursor. This replaces
  SONG3's "click moves the cursor".
- Inspector sliders write back into the code exactly like today's context-panel sliders
  (`eval-orchestrator/slider-patch-and-eval!` and its FX variants), one undo step per
  drag; ⇧-drag = 10× finer.

There is no new Lisp syntax. Before/after for the user, with this buffer:

```lisp
(track :bass (->> (seq :c2 :eb2) (synth :saw) (amp 0.6) (decay 0.25)))
```

```text
before (SONG3 + context panel)            after (WS1)
┌ OUTLINE ───────┐ editor … ┌ CONTEXT ┐   ┌ 124 BPM   wasm ┐┌ track :bass  ↗ L3 esc ┐ editor …
│ track :bass L3 │          │ :bass   │   │ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬ ││ [M][S] live           │ (line 3 tinted,
│ …              │          │ amp ─●─ │   │ latency 5.3 ms ││ PARAMS                │  gutter bar,
└────────────────┘          │ BINDINGS│   │ OUTLINE 8      ││ amp   ───●──── 0.60   │  cursor stays)
 click = cursor moves       └─────────┘   │ track :bass L3 ││ decay ─●────── 0.25   │
                                           └────────────────┘└───────────────────────┘
                                            click = select, ⇧click = add, esc = close
```

Dragging `amp` to 0.75 turns the buffer into `… (amp 0.75) …`; one Cmd/Ctrl-Z restores
`(amp 0.6)`.

## Design reference

`design/outline-panel-v2/outline-v2.jsx` holds the exact sizes and colours:

- `StatusHead` (L156–172): padding `9px 10px 8px`, BPM 15 px bold yellow `#e5c07b` with a
  dashed underline, `BPM` label 9 px, badge `wasm` 9 px bordered, progress bar 5 px high,
  segments `flex: bars`, gap 2 px, fill cyan (opacity 1 for the playing segment, .4 for
  others), third row `latency 5.3 ms` at 9 px. **Drop the `cpu` figure** (decision 2).
- `Row` (L137–154): selected row background `#1f2d4e` (`V.sel`), a 2 px left bar in the
  highlight colour, and a `›` after the line number.
- `OutlineV2` footer (L195–199): the v2 hint set. WS1 uses: `click inspect · ⇧ click add`,
  `F12 go to def · esc close`, `⌘⇧O hide`. **Not** `⌘K S slider` (WS3).
- `Inspector` (L256–276), `TrackBlock` (L221–232), `SectionBlock` (L234–245), `DefBlock`
  (L252–254), `ISlider` (L201–214), `MS` (L217), `Jump` (L218): inspector background
  `#0e1526` (`V.insp`), 28 px header with kind (10 px, kind colour), name (12 px), `↗ L<n>`
  and an `esc` kbd; blocks padded `8px 10px 10px`; slider row grid
  `36px minmax(0,1fr) 38px` (drop the 12 px `≋` column, which is WS3); M/S buttons
  18×16 px, M on = orange `#d19a66`, S on = yellow `#e5c07b`; 5 px resize handle on the
  right edge.
- `MasterBlock` (L247–250) is the template for the top-level fx block ("applies after all
  tracks"). The `(master …)` form itself is WS2.
- `Editor2` (L127–135): selected lines get background `#1f2d4e70` and a 2 px gutter
  border in `#4a5a7e`.

**Ignore** `InlineSlider`, `CodeLine`'s slider rendering, `AIRail`, `AIPanel` and the
`master` outline group (WS2–WS4).

## Background

### App shell — `app/src/repulse/app.cljs`

- `build-dom!` (~L159–209) writes the shell as one HTML string: `header.top-nav`, then
  `div.main-area.app-shell` containing `#outline-panel`, `div.workbench` (buffer tabs,
  `#editor-container`, snippet/AI panels, `#cmd-bar`, `#track-panel`, `#plugin-panel`,
  status bar) and **`<div id="context-panel" class="context-panel">`** (L205).
- `attach-slider-listener!` (~L213–276) wires the context panel: `pointerdown` on a
  `.ctx-slider` sets `ctx-panel/slider-active?`; `document` `pointerup` clears it and
  re-renders; `input` on a range dispatches by `data-track` / `data-fx` / `data-param` /
  `data-values` to `eo/per-track-fx-slider-patch-and-eval!`,
  `eo/fx-slider-patch-and-eval!` or `eo/slider-patch-and-eval!`; `change` on a
  `.ctx-select` goes to `eo/per-track-fx-select-patch-and-eval!` /
  `eo/fx-select-patch-and-eval!`.
- `init` (~L278–480): after `(build-dom!)` it calls `(attach-slider-listener!)`; creates
  the single `EditorView` with `editor/editor-extensions` + `outline-panel/extension` +
  `goto/extension`; calls `outline-panel/init!`, `buffers/add-switch-hook!` →
  `outline-panel/refresh!`; sets the debug handle `window.__REPULSE_EDITOR__` (used by
  `e2e/outline.spec.ts`). At the end (~L462–475) it adds watches:
  `builtins/env-atom`, `fx/chain`, `samples/active-bank-prefix`, `samples/loaded-sources`,
  `midi/cc-mappings` → `ctx-panel/schedule-render!`; `audio/scheduler-state` →
  `ctx-panel/schedule-render!` + `outline-panel/schedule-render!` +
  `timeline/render-track-panel!`; then `(ctx-panel/render-context-panel!)`.
- A global capture-phase `keydown` handler handles Alt+Enter, Ctrl+. / F9 and Ctrl/Cmd+A.
- `window._repulseMuteToggle` (used by the timeline buttons) calls
  `audio/mute-track!` / `audio/unmute-track!`.

### The panel being retired — `app/src/repulse/ui/context_panel.cljs` (`repulse.ui.context-panel`)

Sections rendered by `render-context-panel!`:

| Section | Renderer | Data source |
|---|---|---|
| status | `render-status-section` | BPM `(Math/round (/ 240.0 (:cycle-dur state)))`, `@audio/worklet-ready?` → `[wasm]`/`[js]`, `@samples/active-bank-prefix`, `audio/playing?`, `song-status-text`, `rec-status-text` |
| TRACKS | `render-tracks-section` | `(:tracks state)`, `(:muted state)`, solo derived (exactly one unmuted of >1 tracks), `inactive-section` (`(not in :outro)`), `extract-track-params` (cycle-0 query, keys `TRACK-PARAM-KEYS`), sliders from `SLIDER-PARAMS`, per-track fx from `@audio/track-nodes` `:fx-chain` (`{:name :plugin :bypassed?}`, `.getParams`) |
| FX | `render-fx-section` | `(filter :active? @fx/chain)`, `.getParams`, `FX-SLIDER-PARAMS`, `FX-SELECT-PARAMS` |
| MIDI | `render-midi-section` | `@midi/cc-mappings` (`{cc {:target kw}}`) |
| BUSES | `render-buses-section` | `(bus/active-buses)` (`{name {:type :audio\|:control}}`) |
| SOURCES | `render-sources-section` | `@samples/loaded-sources` (`[{:type :github\|:freesound :id :banks :query :count}]`) |
| BINDINGS | `render-bindings-section` | `@builtins/env-atom` minus `@builtins/builtin-names` |

Public pure helpers that other code uses and that must survive:

- `song-bar` — used by `repulse.ui.outline-panel` (`render-html`, `playing-section` call).
- `song-status-text`, `rec-status-text`, `inactive-section`.
- `SLIDER-PARAMS`, `FX-SLIDER-PARAMS`, `FX-SELECT-PARAMS`, `FX-PRIMARY-PARAM`
  (`FX-PRIMARY-PARAM` is used by `eval_orchestrator.cljs` ~L370 and ~L425), plus the
  private `TRACK-PARAM-KEYS`, `fmt-pv`, `slider-attrs`, `extract-track-params`.
- `schedule-render!`, `slider-active?`, `render-context-panel!` (only `app.cljs`).

CSS: `app/public/css/main.css` has two `.context-panel` blocks (~L200 and ~L1824), the
`.ctx-*` rules (~L213–410 and after ~L1824) and a responsive override
`.context-panel { width: 168px; }` (~L2609).

### Outline column (SONG3/SONG4)

- `app/src/repulse/outline.cljs` (`repulse.outline`, pure, tested by
  `app/src/repulse/outline_test.cljs`, which parses with
  `["./lisp-lang/parser.js" :refer [parser]]` under the node `:test` build — so Lezer
  parsing **works in unit tests**). `extract` returns `{:entries [...] :partial?}`; entry
  = `{:kind :def|:defn|:defsynth|:track|:song|:section :name :from :to :name-from :name-to
  :children [...]}`, sections add `:bars` and `:tracks` (`[":drums" …]` or `[":all"]`).
  `HEADS` maps head text → kind. Also `entry-at`, `definition-for`, `project-definition`,
  `song-bars`, `playing-section` (song value + bar + outline sections →
  `{:index :section-bar :section-bars}`; the outline section at the same index must carry
  the same name).
- `app/src/repulse/ui/outline_panel.cljs` (`repulse.ui.outline-panel`): atoms `entries`,
  `partial?`, `cursor-pos`, `collapsed?` (localStorage `repulse:outline-collapsed`),
  `song-open?`, private `view-ref`. `row-html` renders `.ol-row` with `data-from` /
  `data-to`, classes `ol-cursor`, `ol-playing`, `ol-muted`, `ol-off`. `render-html` builds
  header + `.ol-list` + `footer-html` (or `rail-html` when collapsed) and **requires
  `repulse.ui.context-panel` for `song-bar`**. `render!` writes `#outline-panel`
  innerHTML. `on-click`: `data-act` `collapse`/`expand`/`fold`, otherwise
  **`goto/reveal!`** (moves the cursor — this is what WS1 replaces). `extension` =
  update listener (re-extract 150 ms after doc changes) + `Mod-Shift-o`. `refresh!`
  (buffer switch), `init!`.
- `app/src/repulse/ui/goto.cljs` (`repulse.ui.goto`): `flash-field`, `reveal!`
  (move cursor + scroll ⅓ + 600 ms flash + focus), `name-at`, `goto-definition!`,
  `go-back!`, `jump-stack`, `cross-buffer`, and `extension` with an `.high` keymap
  `F12` → `goto-definition!`, `Shift-F12` → `go-back!`, plus Cmd/Ctrl-mousedown.
- `app/src/repulse/ui/editor.cljs`: `editor-extensions` binds **`Escape` → focus the
  command bar** (`escape-binding`), `Alt-Enter`, `Ctrl-.`, `F9`, `history`,
  `defaultKeymap`, `historyKeymap`. `editor-view` atom holds the main view. One
  `EditorState` per buffer (SONG4), so each buffer has its own undo history.
- `app/src/repulse/ui/buffers.cljs`: `add-switch-hook!`, `active-name`,
  `current-project`.
- `app/src/repulse/lisp-lang/highlight.js` exports `repulseLispSyntaxTheme` (a
  `HighlightStyle`); `@lezer/highlight` exports `highlightCode(code, tree, highlighter,
  putText, putBreak, from, to)`.

### Slider write-back — `app/src/repulse/eval_orchestrator.cljs` (~L332–472)

- `fmt-num`: integer → `"7"`, else `(.toFixed v 2)` (so `0.7` → `"0.70"`, `0.75` → `"0.75"`).
- `dispatch-replace! [view from to text]` dispatches one change, no annotations.
- `patch-param-in-editor!` scopes to the first `":<track>"` in the doc and uses
  `patcher/find-param-num`; `slider-patch-and-eval!` patches and debounces
  `evaluate-project!` by 150 ms via `slider-timeout`. **The track-param path has no
  direct audio update; the sound changes on the debounced project re-eval.**
- `fx-slider-patch-and-eval!` / `fx-select-patch-and-eval!` call `fx/set-param!` first
  (instant sound), then patch + debounce. `per-track-fx-slider-patch-and-eval!` /
  `per-track-fx-select-patch-and-eval!` call `fx/set-track-param!`, scope to
  `"(track :<name>"` … next `"(track :"`, then patch + debounce.
- `app/src/repulse/lisp_patcher.cljs` (`repulse.lisp-patcher`): `find-param-num`,
  `find-fx-named-param-num`, `find-fx-named-param-keyword`, `find-fx-pos-param-num`,
  `find-fx-form-close`. It is and stays the only code that locates numbers in source.
- CodeMirror history: consecutive slider changes are only merged into one undo event if
  they are adjacent and < 500 ms apart, so a slow drag today produces several undo steps.

### Transport — song, BPM, mute/solo

- `packages/core/src/repulse/song.cljs`: `section-start-bar`, `section-at-bar`,
  `wrap-bar` (`:loop` wraps, else nil past the end), `song-status`
  (`{:bar :total :section :section-bar :section-bars}`), `active-tracks-at-bar`. A song
  value has `:sections [{:name :bars :tracks :start :end}]`, `:total`, `:end`.
- `app/src/repulse/env/builtins/song.cljs` `"play-from"` resolves a keyword (first
  section with that name) or a bar and `reset!`s `song-transport/pending-play-from`.
- `app/src/repulse/song_transport.cljs`: `activate-buffer!` → private
  `apply-play-from! [bar song-activated?]`: song already activated → nothing; playing
  with a song → `audio/relocate-song!` (the bar becomes **the next scheduled bar**);
  otherwise `replay-song-buffer!` (restart the last activated song buffer from that bar).
  This is the "jump at the next bar" path WS1 reuses.
- `app/src/repulse/audio.cljs`: `scheduler-state` (`:cycle-dur` default `2.0` = 120 BPM,
  `:cycle`, `:playing?`, `:tracks`, `:muted`, `:song {:song :origin :start-bar}`,
  `:capture`); `set-bpm!` → `(assoc :cycle-dur (/ 240.0 (coerce-bpm bpm)))`
  (`coerce-bpm` clamps to 20–640); `get-bpm`; `relocate-song!`; `playing?`;
  `mute-track!`, `unmute-track!`, `solo-track!` (mutes all others) — the functions behind
  the `mute!` / `unmute!` / `solo!` builtins (`env/builtins/tracks.cljs`); `ctx` atom
  (the `AudioContext`, `.-baseLatency`, `.-outputLatency`); `worklet-ready?` atom;
  `track-nodes` atom. The `(bpm n)` builtin (`packages/lisp/src/repulse/lisp/eval.cljs`
  ~L377) calls the `bpm-fn` that `env/builtins.cljs` wires to `audio/set-bpm!`.

### Tests

- `npm test` = `npx shadow-cljs compile test && node out/test.js`; the runner
  `packages/core/src/repulse/test_runner.cljs` lists every test namespace in its `:require`
  **and** in `run-tests` (both places). App-layer pure tests live next to their namespace
  (e.g. `app/src/repulse/ui/timeline_test.cljs`, `app/src/repulse/song_transport_test.cljs`,
  which already requires `repulse.audio` and builds song values with `run`).
- Playwright: `playwright.config.ts` compiles `test-harness` **and** `app`, serves
  `app/public` with `scripts/serve-static.mjs` on port 3100, tests in `e2e/*.spec.ts`.
  `e2e/outline.spec.ts` drives the real app page `/` through `window.__REPULSE_EDITOR__`
  (helpers `load`, `setText`, `head`, `lineOfHead`, `setCursor`); its test *"clicking a row
  moves the cursor to its paren…"* encodes SONG3 behaviour that WS1 replaces.
  `window.__REPULSE_TEST__` (`app/src/repulse/test_api.cljs`) belongs to the separate
  offline harness page (`test-harness.html`), which has no outline or inspector — WS1
  doesn't touch it.
- `npm run test:all` = `./scripts/test-all.sh` (cljs unit, Rust, Playwright).

## Design decisions

Questions 1–4 of the outline came with recommendations; they were adopted as written.
Decisions 5–12 were taken during spec expansion (2026-11-03, auto-phase pipeline).

1. **Cue = next bar, via the `play-from` path.** Clicking a progress segment or `▶ cue`
   calls a new public `song-transport/cue-bar!`, which is the existing
   `apply-play-from!` with `song-activated? = false`: while a song plays,
   `audio/relocate-song!` makes the section's start bar the next scheduled bar; while
   stopped, `replay-song-buffer!` starts the last evaluated song there. Cueing works by
   **section index** (start bar = `(:start (nth (:sections song) i))`), so duplicate
   section names (`:verse` twice) cue the clicked one.
2. **Latency only, no cpu.** The status head shows
   `latency <(baseLatency + outputLatency) × 1000, 1 decimal> ms`; a missing
   `outputLatency` counts as 0; no `AudioContext` yet → `latency —`. No worklet
   render-time reporting in WS1.
3. **MIDI / BUSES / SOURCES → a "session" block** at the bottom of the outline column,
   above the footer, collapsed by default, with a one-line summary
   (`▸ session  midi 2 · buses 1 · sources 1`, zero counts omitted). It is not rendered
   at all when all three are empty. Its open state is kept in localStorage
   `repulse:outline-session-open` (`"1"`/`"0"`, default closed).
4. **Inspector width in localStorage** `repulse:inspector-width`, default 220, clamped
   to 180–380 on load and while dragging the handle.
5. **Where the other status items go.** BPM, backend badge (`wasm`/`js`), progress bar and
   latency are the status head. The bank prefix (`@samples/active-bank-prefix`) becomes a
   second badge next to `wasm`. `song-status-text` becomes the progress bar's `title`.
   `rec-status-text` replaces the latency text while an `(export :song)` capture runs
   (red). "● playing / ○ stopped" is dropped: the top-nav play button and
   `#playing-dot` already show it. BINDINGS is dropped (the outline lists defs).
6. **Top-level FX: an "fx" outline group.** `repulse.outline/extract` learns a new
   top-level kind `:fx` (head `fx`, name = the effect keyword, e.g. `":reverb"`). The
   panel lists all `:fx` entries in **one group after all other entries** (1 px divider,
   group row `fx  fx  <n>`, children `:reverb  L4` indented 12 px), not at their source
   position. Selecting the group shows every top-level effect; selecting a child shows
   one. Values come from `@fx/chain` (`:active?` entries, `.getParams`), sliders write via
   `fx-slider-patch-and-eval!` / `fx-select-patch-and-eval!`. WS2 replaces the group with
   `(master …)`.
7. **Selection ids** are strings, stable across edits: `"def:kick"`, `"defn:humanize"`,
   `"defsynth:pad"`, `"track::drums"` (`"track:"` + the name incl. its colon),
   `"section:<index>"`, `"fx"` (the group), `"fx:<index>"` (index among `:fx` entries).
   The song row is **not selectable**: clicking it folds/unfolds its sections. After every
   re-extraction, ids that no longer resolve are pruned; when the selection becomes empty
   the inspector closes. Switching buffers and collapsing the outline to the rail clear
   the selection.
8. **Pick rules** (from `useStudio`'s `pick`, L97–99 of `outline-v2.jsx`): plain click on the only selected
   row deselects; plain click otherwise replaces the selection with that row; ⇧-click on a
   track while every selected item is a track toggles that track in the selection;
   ⇧-click in any other situation behaves like a plain click.
9. **Row click scrolls, never moves the cursor.** Selecting scrolls the most recently
   picked entry's `from` to ~⅓ from the top (`EditorView.scrollIntoView`, `y: "start"`,
   `yMargin` = ⅓ of the view height) and focuses the editor; the editor selection is not
   dispatched. `goto/reveal!` stays for go-to-definition and is used by `↗ L<n>` / F12.
10. **Keys.** `Escape`: when the selection is non-empty it clears it and keeps focus in the
    editor (consumed); when empty, the existing `Escape` → command bar binding runs. `F12`:
    when the selection is non-empty it reveals the most recently picked entry
    (`goto/reveal!` with the cursor at its `:from`, the form's `(`, like SONG3's row click;
    pushes `goto/jump-stack` so Shift+F12 returns); when empty, SONG3 go-to-definition runs unchanged. Both live in a
    `Prec.highest` keymap that returns `false` when the selection is empty, so they fall
    through to the existing bindings. A document-level `keydown` handles `Escape` while
    focus is inside `#outline-panel` or `#inspector`.
11. **One undo step per drag, without touching `lisp_patcher`.** `eval_orchestrator` gets
    a drag session: `begin-slider-drag!` stores the doc string; while it is set,
    `dispatch-replace!` adds `Transaction.addToHistory.of(false)`; `end-slider-drag!`
    computes two minimal diffs with the pure `slider-model/text-diff` and dispatches
    *current → original* without history, then *original → current* with history. The
    buffer text is unchanged and the history holds exactly one event. All positions inside
    the code still come from `lisp_patcher`. A keyboard step (arrow key on a focused
    slider) is a one-step drag (begin, patch, end).
12. **"Without a full re-eval" in the outline's DoD is not achievable for track params
    without changing how sliders work**, because `slider-patch-and-eval!` relies on the
    150 ms debounced `evaluate-project!` (FX sliders do update instantly through
    `fx/set-param!`). WS1 keeps the existing behaviour; the DoD checks that the live track
    param changes within 1 s **without a manual Alt+Enter**.
13. **Slider widget.** Custom pointer slider like `ISlider` (not `<input type=range>`), so
    ⇧-drag can be 10× finer: pointer-down on the track jumps to that position unless ⇧ is
    held; each move adds `dx / trackWidth` (× 0.1 with ⇧) to `t`; values snap to the
    config `:step` (or to the nearest `:values` entry). Each slider is focusable
    (`tabindex="0"`, `role="slider"`, `aria-valuemin/max/now`); `←/→` and `↓/↑` move one
    step, with ⇧ ten steps (same convention as the BPM field). FX selects stay native
    `<select>`s.
14. **BPM commit** calls `audio/set-bpm!` directly — the same function the `(bpm n)`
    builtin uses. It does not patch the buffer; a `(bpm …)` in the code wins on the next
    Alt+Enter (documented). Input accepts 2–3 digit integers 40–300 only; anything else is
    rejected (the field gets `.sh-invalid` and stays open). Esc cancels, blur commits like
    Enter. On the closed display (focusable): ↑/↓ ±1, ⇧↑/⇧↓ ±10, clamped to 40–300.
15. **Repaint without losing focus or drag.** Inspector and status head repaint through
    their own RAF-coalesced `schedule-render!`. They skip the repaint while a slider drag
    or the BPM edit is active, skip it when the derived view model is `=` to the last
    painted one, and after a repaint re-focus the element whose `data-key` had focus.

## Implementation

### 0. Session start

Run `npm run gen:grammar`, `npm run gen:ai-docs`, `npm test` on the unmodified branch
(CLAUDE.md Rule 6). WS1 adds no built-in, so the grammar, `completions.js`,
`builtin_meta.edn` and `docs/ai/builtins.json` must show **no drift** at the end.

### 1. Pure: slider model — `app/src/repulse/ui/slider_model.cljs` (new, ns `repulse.ui.slider-model`)

No DOM, no atoms, only `cljs.core` + `clojure.string`.

- Move verbatim from `context_panel.cljs`: `TRACK-PARAM-KEYS` (now public),
  `SLIDER-PARAMS`, `FX-SLIDER-PARAMS`, `FX-SELECT-PARAMS`, `FX-PRIMARY-PARAM`, `fmt-pv`.
- `(decimals step)` → digits after the point of `step` (`0.01` → 2, `0.001` → 3, `1` → 0,
  `0.5` → 1).
- `(snap cfg v)`: `:values` cfg → the nearest entry; else clamp to `[min max]`, then
  `min + round((v − min) / step) · step`, then `(js/parseFloat (.toFixed x (decimals step)))`.
- `(value->t cfg v)` → 0..1 (`:values`: index / (n − 1)); `(t->value cfg t)` → snapped
  value (`:values`: entry at `round(t · (n − 1))`).
- `(drag-value cfg v0 dx width fine?)` → `(t->value cfg (clamp01 (+ (value->t cfg v0)
  (* (/ dx width) (if fine? 0.1 1)))))`.
- `(key-step cfg v dir coarse?)` → `v + dir · step · (if coarse? 10 1)`, snapped (for
  `:values`, ± one / ten indices, clamped).
- `(text-diff old new)` → `{:from :to :insert}` replacing `old[from,to)` with `insert`
  turns `old` into `new` (longest common prefix, then longest common suffix not
  overlapping it), or `nil` when equal.

### 2. Pure: status model — `app/src/repulse/ui/status_model.cljs` (new, ns `repulse.ui.status-model`)

Requires only `repulse.song` and `clojure.string`.

- Move verbatim: `song-bar`, `song-status-text`, `rec-status-text`, `inactive-section`.
- `(bpm-of cycle-dur)` → `(Math/round (/ 240 cycle-dur))`.
- `BPM-MIN` 40, `BPM-MAX` 300. `(parse-bpm s)` → integer when `(str/trim s)` matches
  `#"^\d{2,3}$"` and is within 40–300, else `nil`. `(step-bpm bpm dir coarse?)` →
  `bpm + dir · (if coarse? 10 1)`, clamped to 40–300.
- `(latency-text base output)` → `"5.3 ms"` (`(.toFixed (* 1000 (+ base (or output 0))) 1)`),
  `"—"` when `base` is nil.
- `(progress-segments song bar)` → one map per song section in order:
  `{:index i :name kw :bars n :start s :fill f :playing? b}`. With `q = (song/wrap-bar song
  bar)` (nil when `bar` is nil or outside): `:fill` = `clamp01((q + 1 − start) / bars)`,
  0 for all when `q` is nil; `:playing?` = the section containing `q`. Returns `[]` for a
  nil song.
- `(segment-cue-bar song index)` → `(:start (nth (:sections song) index nil))`.
- `(section-cue-bar song outline-sections index)` → the start bar of song section `index`
  when outline section `index` has the same name (`":verse"` vs `:verse`, same rule as
  `outline/playing-section`), else nil (the code changed since the last eval → `▶ cue`
  is disabled with title "evaluate first").

### 3. Outline extraction — `app/src/repulse/outline.cljs`

- Add `"fx" :fx` to `HEADS`. A top-level `fx` entry needs a `Keyword` name token (like
  `track`); `(fx)` without a keyword is skipped. Nested `fx` (inside `->>` or `track`)
  stays invisible because only `Program` children are walked.
- Every top-level entry (not sections) gets `:refs` — the set of `Symbol` and
  `BuiltinName` texts anywhere inside its `List`, excluding the head and the name token
  (walk the subtree; strings and comments are single tokens so they never contribute).
- `(used-by entries name)` → vector of the `:name`s of entries (any kind except `:song`)
  whose `:refs` contain `name`, in source order, excluding the entry itself.
- Update the ns docstring's export list.

### 4. Pure: selection — `app/src/repulse/ui/selection.cljs` (new, ns `repulse.ui.selection`)

Requires `repulse.outline` only.

- `(entry-id e index)` per decision 7 (`index` is the section index within the song or
  the fx index among `:fx` entries).
- `(index-entries entries)` → `{id entry}` for every selectable item: top-level
  def/defn/defsynth/track, each section, `"fx"` → `{:kind :fx-group :from <first fx> :to
  <last fx> :children [fx entries]}`, each `"fx:<i>"`.
- `(pick sel id shift?)` per decision 8. `sel` is a vector, newest last.
- `(prune sel idx)` → `sel` without ids missing from `idx`.
- `(ranges sel idx)` → sorted, merged `[{:from :to}]` (the `"fx"` group contributes each
  child's range, not one span).
- `(primary sel idx)` → the entry of the last id that resolves (for F12, `↗ L`, scroll).
- `(header sel idx)` → `{:kind "track"|"tracks"|"section"|"def"|"defn"|"synth"|"fx"
  :title s :from n|nil}`. Multi-track: `{:kind "tracks" :title "2" :from nil}`.

### 5. Pure: inspector model — `app/src/repulse/ui/inspector_model.cljs` (new, ns `repulse.ui.inspector-model`)

Requires `repulse.ui.slider-model` only. Input is plain data that the DOM layer collects.

- `(track-params values)` → first value per key in `slider-model/TRACK-PARAM-KEYS` over
  a seq of event values (maps only; non-maps ignored) — the logic of
  `extract-track-params` without the query.
- `(param-rows params)` → `{:sliders [[k v cfg] …] :text [[k "str"] …]}` in
  `TRACK-PARAM-KEYS` order: numeric values with a `SLIDER-PARAMS` entry are sliders,
  everything else is text (`:saw` → `"saw"`).
- `(fx-rows chain)` — `chain` = `[{:name "filter" :bypassed? false :params {"freq" 2400 …}}]`
  in signal order → `[{:name :pos (1-based) :bypassed? :sliders [[pname v cfg] …]
  :selects [[pname v options] …]}]`; slider/select order = key order of the effect's
  `FX-SLIDER-PARAMS` / `FX-SELECT-PARAMS` entry; params missing from `:params` are
  omitted; bypassed effects get no controls.
- `(track-flags track-names muted name)` → `{:muted? :solo?}`; solo = more than one track
  and `name` is the only unmuted one (the context panel's rule).
- `(mute-action muted name)` → `[:unmute name]` or `[:mute name]`.
  `(solo-action track-names muted name)` → `[:unmute-all]` when `name` is currently solo,
  else `[:solo name]`.
- `(section-tracks section track-entries)` → the section's track names; `[":all"]` → all
  track entry names in source order.

### 6. Song transport — `app/src/repulse/song_transport.cljs`

```clojure
(defn cue-bar!
  "Cue `bar` like (play-from bar) issued while a song is active: playing → it becomes
   the next scheduled bar; stopped → the last song buffer restarts there. WS1."
  [bar]
  (apply-play-from! bar false))
```

No other change.

### 7. Orchestrator — `app/src/repulse/eval_orchestrator.cljs`

- Require `repulse.ui.slider-model :as sm` instead of `repulse.ui.context-panel`;
  `ctx-panel/FX-PRIMARY-PARAM` → `sm/FX-PRIMARY-PARAM` (two places).
- Drag session (decision 11):

```clojure
(defonce ^:private drag-origin (atom nil))   ; doc string at drag start, or nil

(defn begin-slider-drag! []
  (when-let [view @editor/editor-view]
    (reset! drag-origin (.. view -state -doc (toString)))))

(defn- dispatch-replace! [view from to text]
  (.dispatch view
    (clj->js (cond-> {:changes {:from from :to to :insert text}}
               @drag-origin (assoc :annotations (.of (.-addToHistory Transaction) false))))))

(defn end-slider-drag! []
  (when-let [orig @drag-origin]
    (reset! drag-origin nil)
    (when-let [view @editor/editor-view]
      (let [cur (.. view -state -doc (toString))]
        (when-let [back (sm/text-diff cur orig)]
          (.dispatch view #js {:changes (clj->js back)
                               :annotations (.of (.-addToHistory Transaction) false)})
          (let [fwd (sm/text-diff orig cur)]
            (.dispatch view #js {:changes (clj->js fwd)})))))))
```

  (`clj->js` on the `:annotations` value must not convert the annotation object — build
  the spec with `#js` literals in the real code.) `Transaction` comes from
  `"@codemirror/state"`.
- The five `*-patch-and-eval!` functions keep their signatures and behaviour.

### 8. Selection highlight — `app/src/repulse/ui/selection_highlight.cljs` (new, ns `repulse.ui.selection-highlight`)

- `set-ranges` `StateEffect`; a `StateField` holding a `DecorationSet` of
  `Decoration.line({class: "ws-sel-line"})` for every line touched by the ranges, mapped
  through changes; plus `gutterLineClass.compute` (or a `StateField` provided to
  `gutterLineClass`) adding a `GutterMarker` with class `ws-sel-gutter` on the same
  lines.
- `(set-ranges! view ranges)` dispatches the effect. `extension` = the field(s). Installed
  only in the main app (added to `exts` in `app.cljs` next to `outline-panel/extension`),
  never in the embed.

### 9. Outline panel — `app/src/repulse/ui/outline_panel.cljs`

- Require `repulse.ui.status-model :as sm` (for `song-bar`) instead of
  `repulse.ui.context-panel`; require `repulse.ui.selection :as sel`, `repulse.midi`,
  `repulse.bus`, `repulse.samples`, `repulse.ui.html`.
- New atom `selection` (vector of ids) and `on-selection-change` hook atom (set by
  `inspector/init!`). `(set-selection! ids)` resets it, recomputes
  `sel/ranges` → `selection-highlight/set-ranges!`, schedules both repaints and calls the
  hook. `(clear-selection!)`.
- `re-extract!` additionally prunes the selection (`sel/prune`) and refreshes the
  highlight ranges (positions move with edits).
- `row-html`: new opt `:sel?` → class `ol-sel`, `data-id` attribute, and a trailing
  `<span class="ol-sel-mark">›</span>`. Every selectable row gets `data-id`.
- Render the fx group (decision 6) after the last entry when any `:fx` entry exists.
- `on-click`: rows with `data-id` → `(set-selection! (sel/pick @selection id
  (.-shiftKey e)))`, then scroll the primary entry into view (decision 9) and
  `(.focus view)`. The song row (`data-act="fold"` on the whole row) folds. Remove the
  `goto/reveal!` call from row clicks.
- `toggle-collapsed!` clears the selection when collapsing.
- Session block (decision 3): `session-html` with summary + (when open) the MIDI, buses
  and sources rows ported from `render-midi-section`, `render-buses-section`,
  `render-sources-section` (same text, `ol-*` classes). `data-act="session"` toggles.
- `footer-html` → three rows: `click inspect · ⇧ click add`, `F12 go to def · esc close`,
  `⌘⇧O hide` (Ctrl variants off macOS).
- `render!` targets `#outline-main` (see step 12) instead of `#outline-panel`; the
  `collapsed` class still goes on `#outline-panel`.
- `refresh!` (buffer switch) clears the selection.
- `extension` adds a `Prec.highest` keymap: `Escape` → when `@selection` non-empty,
  `(clear-selection!)` and return `true`, else `false`; `F12` → when non-empty, push
  `goto/jump-stack` (make `goto/push-jump!` public) and `goto/reveal!` the primary entry,
  return `true`; else `false`.

### 10. Status head — `app/src/repulse/ui/status_head.cljs` (new, ns `repulse.ui.status-head`)

- Renders into `#status-head`:
  `.sh-row` (`.sh-bpm` focusable span with `tabindex="0"` and `data-key="bpm"`, `BPM`
  label, spacer, `.sh-badge` bank prefix when set, `.sh-badge.sh-backend` `wasm`/`js`),
  `.sh-progress` (one `.sh-seg` per `sm/progress-segments` entry with
  `style="flex:<bars>"`, `data-index`, inner `.sh-fill` width `fill·100%`, `.playing`
  class; `title` = `sm/song-status-text`; hidden when there is no song),
  `.sh-meta` (`latency …` or the REC text in `.sh-rec`).
- BPM edit: click on `.sh-bpm` swaps in `<input class="sh-bpm-input">` (sets `editing?`);
  Enter/blur → `sm/parse-bpm` → `audio/set-bpm!` + close, or `.sh-invalid`; Escape →
  close unchanged. Keydown on the closed `.sh-bpm`: ArrowUp/ArrowDown with/without Shift
  → `sm/step-bpm` → `audio/set-bpm!`.
- Segment click → `(song-transport/cue-bar! (sm/segment-cue-bar song i))` with
  `song = (:song (:song @audio/scheduler-state))`, falling back to
  `(:song @song-transport/song-buffer)` when stopped.
- `schedule-render!` per decision 15; `init!` attaches delegated listeners once.

### 11. Inspector — `app/src/repulse/ui/inspector.cljs` (new, ns `repulse.ui.inspector`)

- `#inspector` is hidden (`.hidden`) when the selection is empty. Width from
  localStorage (decision 4) as inline `style="width:<w>px"`.
- Header: kind (coloured like the outline kind), title, `.ins-jump` `↗ L<n>` (line of
  `:from`, absent for multi-track), `<kbd>esc</kbd>` (click = clear).
- Body by `sel/header` kind, using the pure models:
  - **track** (`TrackBlock`): M/S buttons (`data-act="mute"`/`"solo"`, `data-track`),
    status text `live` / `muted` / `solo` / `rests in :outro` (`sm/inactive-section`);
    `PARAMS` slider rows from `im/param-rows` over
    `(im/track-params (map :value (core/query pat {:start [0 1] :end [1 1]})))` for the
    pattern in `(:tracks @audio/scheduler-state)`; text params as `key value`;
    `FX CHAIN` from `im/fx-rows` over `(:fx-chain (get @audio/track-nodes kw))` with
    `.getParams`. A track not in `:tracks` shows `not evaluated — Alt+Enter`.
  - **tracks** (multi): one `TrackBlock` per id with a head row (dot, `:name`, `↗ L<n>`),
    then the hint `⇧ click a track to add / remove`.
  - **section** (`SectionBlock`): `<n> bars`, `▶ bar k/n` when it is the playing section
    (`outline/playing-section`), otherwise `▶ cue` (`data-act="cue"`, disabled when
    `sm/section-cue-bar` is nil); a bar strip of `n` cells (current bar cyan, earlier
    bars dim cyan); `TRACKS · m` with, per `im/section-tracks` entry, dot + name + M/S +
    an `amp` slider when the track has a numeric `amp` param.
  - **def / defn / synth** (`DefBlock`): source preview of `[from, to)` rendered with
    `highlightCode` + `repulseLispSyntaxTheme` over the editor's syntax tree (escape every
    text chunk with `escape-html`; max 12 lines, then `…`), then
    `Used by :drums, :bass.` from `outline/used-by`, or `Not used by any track or def.`
  - **fx / fx:i**: one block per top-level effect from `@fx/chain` (`:active?`, matched
    by name; the first chain entry with that name), `im/fx-rows`, footnote
    `applies after all tracks`.
- Slider DOM: `.ins-slider` with `data-key` (`"<track>|<param>"`, `"<track>|<fx>|<param>"`,
  `"fx|<fx>|<param>"`), `data-track`/`data-fx`/`data-param`, value label `sm/fmt-pv`.
  Pointer: `pointerdown` → `eo/begin-slider-drag!`, set `dragging?`, capture the pointer;
  `pointermove` → `slider-model/drag-value` (⇧ = fine) → update the knob/label in place
  and call the matching `eo/*-patch-and-eval!` (same dispatch rules as
  `attach-slider-listener!`, with `:values` already resolved); `pointerup` /
  `pointercancel` → `eo/end-slider-drag!`, clear `dragging?`, repaint. Keyboard per
  decision 13, each key = begin/patch/end. Selects call the `*-select-patch-and-eval!`
  functions on `change`.
- M/S buttons: `im/mute-action` / `im/solo-action` → `audio/mute-track!`,
  `audio/unmute-track!`, `audio/solo-track!`, or `unmute-track!` for every muted track.
- `↗ L<n>`: push the jump stack and `goto/reveal!` at the entry's `:from` (cursor
  moves to the `(`, editor focused).
- Resize handle `.ins-resize` (5 px, right edge): pointer drag → width clamped 180–380,
  saved on `pointerup`.
- `schedule-render!` per decision 15. `init!` wires listeners once and registers itself
  as `outline-panel`'s selection hook.

### 12. App wiring — `app/src/repulse/app.cljs`

- `build-dom!`: `#outline-panel` now contains `<div id="status-head" class="status-head">`
  and `<div id="outline-main" class="outline-main">`; insert
  `<div id="inspector" class="inspector hidden"></div>` between `#outline-panel` and
  `.workbench`; **delete** the `#context-panel` div.
- Delete `attach-slider-listener!` and its call; drop the
  `repulse.ui.context-panel` require; require `status-head`, `inspector`,
  `selection-highlight`.
- Add `selection-highlight/extension` to `exts`.
- After `outline-panel/init!`: `(status-head/init!)`, `(inspector/init!)`. The buffer
  switch hook also clears the selection (via `outline-panel/refresh!`).
- Watches: replace the `::ctx` watches with —
  `audio/scheduler-state` → `outline-panel/schedule-render!`,
  `status-head/schedule-render!`, `inspector/schedule-render!`,
  `timeline/render-track-panel!`; `fx/chain` → `inspector/schedule-render!`;
  `samples/active-bank-prefix`, `audio/worklet-ready?` → `status-head/schedule-render!`;
  `samples/loaded-sources`, `midi/cc-mappings` → `outline-panel/schedule-render!`.
  Remove the `builtins/env-atom` watch (BINDINGS is gone). Remove
  `(ctx-panel/render-context-panel!)`.
- Debug handle next to `__REPULSE_EDITOR__` (main app only, used by
  `e2e/workspace.spec.ts`):

```clojure
(set! (.-__REPULSE_APP__ js/window)
      #js {:cycleDur  (fn [] (:cycle-dur @audio/scheduler-state))
           :muted     (fn [] (clj->js (map name (:muted @audio/scheduler-state))))
           :selection (fn [] (clj->js @outline-panel/selection))
           :trackParam (fn [track param] …)})   ; im/track-params of the live pattern, or nil
```

### 13. Delete the context panel

- Delete `app/src/repulse/ui/context_panel.cljs` once nothing requires it (`grep -rn
  "context-panel\|ctx-panel" app/src` must be empty).
- `app/public/css/main.css`: delete both `.context-panel` blocks, all `.ctx-*` rules, the
  responsive `.context-panel` override. Add `.status-head`/`.sh-*`, `.outline-main`,
  `.ol-sel`, `.ol-sel-mark`, `.ol-group-divider`, `.ol-session*`, `.inspector`/`.ins-*`,
  `.cm-line.ws-sel-line { background: #1f2d4e70 }`,
  `.ws-sel-gutter { border-right: 2px solid #4a5a7e }` with the design's sizes/colours;
  `.outline-panel.collapsed .status-head { display: none }`; `.inspector.hidden {
  display: none }`. Comment-only references in `samples.cljs`, `bus.cljs`, `audio.cljs`
  ("for the context panel") are updated to name the new home.

### 14. Tests

Unit (register each new namespace in **both** the `:require` and `run-tests` of
`packages/core/src/repulse/test_runner.cljs`):

- `app/src/repulse/ui/slider_model_test.cljs` — `snap`, `drag-value`, `key-step`,
  `text-diff`, `fmt-pv` vectors below.
- `app/src/repulse/ui/status_model_test.cljs` — `bpm-of`, `parse-bpm`, `step-bpm`,
  `latency-text`, `progress-segments`, `segment-cue-bar`, `section-cue-bar`,
  `song-status-text`.
- `app/src/repulse/ui/selection_test.cljs` — parses `W` with the Lezer `parser` (like
  `outline_test.cljs`), then `pick`, `prune`, `ranges`, `header`.
- `app/src/repulse/ui/inspector_model_test.cljs` — `track-params`, `param-rows`,
  `fx-rows`, `track-flags`, `mute-action`, `solo-action`, `section-tracks`.
- `app/src/repulse/outline_test.cljs` — add `:fx` entries, `:refs`, `used-by` on `W`; the
  existing `R` assertions must still pass (R has no top-level `fx`).
- `app/src/repulse/song_transport_test.cljs` — `cue-bar!` (vector below); save and
  restore `@audio/scheduler-state` around the test.

Playwright:

- New `e2e/workspace.spec.ts` (same `beforeEach` as `outline.spec.ts`: clear
  localStorage, 1280×800; same `load`/`setText` helpers, waiting for `kick` in
  `#outline-panel`).
- `e2e/outline.spec.ts`: replace *"clicking a row moves the cursor…"* with *"clicking a
  row selects it without moving the cursor"* (cursor stays where `setText` put it, row
  has `.ol-sel`, focus is in the editor). All other SONG3 tests stay unchanged.

### 15. Docs

- `docs/USAGE.md`: "The editor" intro (~L308: no context panel; outline on the left with
  status head, inspector); outline section (~L360–385: click selects/inspects, ⇧-click,
  Esc, F12 with a selection, fx group, session block, footer hints); replace
  "### Context panel" (~L387) with "### Status head and inspector" (BPM edit + keys,
  wasm/js, progress bar + cue, latency, inspector kinds, sliders write code, ⇧-drag fine,
  one undo per drag, resize); song chapter (~L2309 "### Context panel" → the status head's
  progress bar / title and the inspector's `rests in :outro`; ~L2324 REC shown in the
  status head); ~L1663 (bank shown in the status head); ~L2408 ("the context-panel
  sliders" → "the inspector sliders"). Add the key table rows (Esc, F12 with selection,
  ⇧-click).
- `docs/ARCHITECTURE.md`, `docs/CONTRIBUTING.md`, `docs/FUTURE-FEATURES.md`: replace
  context-panel references (namespace map: `repulse.ui.status-head`,
  `repulse.ui.inspector`, the pure `*-model` namespaces).
- `README.md` has no context-panel mention today; check again and only change it if one
  appeared.
- `ROADMAP.md` / `CLAUDE.md`: mark WS1 delivered per Rule 2 when done.

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/ui/slider_model.cljs` | **new** — slider constants (moved), `fmt-pv`, `snap`, `drag-value`, `key-step`, `text-diff` |
| `app/src/repulse/ui/slider_model_test.cljs` | **new** — unit tests |
| `app/src/repulse/ui/status_model.cljs` | **new** — `song-bar`, `song-status-text`, `rec-status-text`, `inactive-section` (moved), BPM, latency, progress segments, cue bars |
| `app/src/repulse/ui/status_model_test.cljs` | **new** — unit tests |
| `app/src/repulse/ui/selection.cljs` | **new** — ids, `pick`, `prune`, `ranges`, `primary`, `header` |
| `app/src/repulse/ui/selection_test.cljs` | **new** — unit tests over `W` |
| `app/src/repulse/ui/inspector_model.cljs` | **new** — track params, fx rows, M/S flags/actions, section tracks |
| `app/src/repulse/ui/inspector_model_test.cljs` | **new** — unit tests |
| `app/src/repulse/ui/selection_highlight.cljs` | **new** — CM line + gutter decoration for selected ranges |
| `app/src/repulse/ui/status_head.cljs` | **new** — status head DOM, BPM edit, cue on segment click |
| `app/src/repulse/ui/inspector.cljs` | **new** — inspector DOM, sliders, M/S, cue, jump, resize |
| `app/src/repulse/outline.cljs` | `:fx` kind, `:refs`, `used-by` |
| `app/src/repulse/outline_test.cljs` | `:fx`, `:refs`, `used-by` tests |
| `app/src/repulse/ui/outline_panel.cljs` | selection state + rows, fx group, session block, v2 footer, Esc/F12 keymap, render into `#outline-main`, no `ctx-panel` require |
| `app/src/repulse/ui/goto.cljs` | make `push-jump!` public (no behaviour change) |
| `app/src/repulse/song_transport.cljs` | public `cue-bar!` |
| `app/src/repulse/song_transport_test.cljs` | `cue-bar!` test |
| `app/src/repulse/eval_orchestrator.cljs` | require `slider-model`; drag session (`begin-slider-drag!`, `end-slider-drag!`, history annotation in `dispatch-replace!`) |
| `app/src/repulse/app.cljs` | DOM (`#status-head`, `#outline-main`, `#inspector`, no `#context-panel`), remove `attach-slider-listener!`, new watches, init calls, `__REPULSE_APP__` |
| `app/src/repulse/ui/context_panel.cljs` | **delete** |
| `app/public/css/main.css` | remove `.context-panel`/`.ctx-*`; add status head, inspector, selection, session styles |
| `app/src/repulse/samples.cljs`, `bus.cljs`, `audio.cljs` | comment wording only |
| `packages/core/src/repulse/test_runner.cljs` | register the four new test namespaces |
| `e2e/workspace.spec.ts` | **new** — Playwright tests below |
| `e2e/outline.spec.ts` | row-click test → selection behaviour |
| `docs/USAGE.md`, `docs/ARCHITECTURE.md`, `docs/CONTRIBUTING.md`, `docs/FUTURE-FEATURES.md` | context panel → status head / inspector / session block |
| `ROADMAP.md`, `CLAUDE.md` | WS1 delivered (Rule 2) |

No grammar, completion, `builtin_meta.edn` or WASM change.

## Definition of done

### Reference test vectors

Workspace buffer `W` (used by the selection, outline and Playwright tests; 278 chars):

```lisp
(def kick (seq :bd :_ :bd :_))
(track :drums (->> kick (amp 0.8) (fx :filter :freq 2400)))
(track :bass (->> (seq :c2 :eb2) (synth :saw) (amp 0.6) (decay 0.25)))
(fx :reverb 0.3)
(song
  (section :intro 4 [:drums])
  (section :verse 8 [:drums :bass])
  (section :chorus 8 :all))
```

Offsets in `W`: `def kick` [0, 30) L1 · `track :drums` [31, 90) L2 · `track :bass`
[91, 161) L3 · `fx :reverb` [162, 178) L4 · `song` L5 · `:intro` L6 · `:verse`
[217, 250) L7 · `:chorus` L8. The `0.6` of `(amp 0.6)` is [142, 145).

Song value `S` (literal map, enough for `status-model`):
`{:total 20 :end :stop :sections [{:name :intro :bars 4 :start 0 :end 4}
{:name :verse :bars 8 :start 4 :end 12} {:name :chorus :bars 8 :start 12 :end 20}]}`;
`S-loop` = `S` with `:end :loop`.

**slider-model**

| Call | Expected |
|---|---|
| `(snap {:min 0 :max 1 :step 0.01} 0.80000000001)` | `0.8` |
| `(drag-value {:min 0 :max 1 :step 0.01} 0.6 30 150 false)` | `0.8` |
| `(drag-value {:min 0 :max 1 :step 0.01} 0.6 30 150 true)` | `0.62` |
| `(drag-value {:min 0 :max 1 :step 0.01} 0.6 300 150 false)` | `1` |
| `(drag-value {:min 0 :max 1 :step 0.01} 0.6 -300 150 false)` | `0` |
| `(drag-value {:min 20 :max 8000 :step 1} 1000 15 150 false)` | `1798` |
| `(drag-value {:values [1 2 4]} 1 75 150 false)` | `2` |
| `(key-step {:min 0 :max 1 :step 0.01} 0.6 1 false)` | `0.61` |
| `(key-step {:min 0 :max 1 :step 0.01} 0.6 1 true)` | `0.7` |
| `(key-step {:min 0 :max 1 :step 0.01} 0.6 -1 true)` | `0.5` |
| `(key-step {:min 0 :max 1 :step 0.01} 0.95 1 true)` | `1` |
| `(key-step {:values [1 2 4]} 4 1 false)` | `4` |
| `(text-diff "(amp 0.6)" "(amp 0.75)")` | `{:from 7 :to 8 :insert "75"}` |
| `(text-diff "(amp 0.75)" "(amp 0.6)")` | `{:from 7 :to 9 :insert "6"}` |
| `(text-diff "aaa" "aaaa")` | `{:from 3 :to 3 :insert "a"}` |
| `(text-diff "x" "x")` | `nil` |
| `(fmt-pv 0.7)` / `(fmt-pv 2400)` | `"0.70"` / `"2400"` |

**status-model**

| Call | Expected |
|---|---|
| `(bpm-of 2.0)` / `(bpm-of (/ 240 90))` | `120` / `90` |
| `(/ 240.0 90)` — `:cycle-dur` after committing 90 | `2.6666666666666665` |
| `(parse-bpm "90")`, `(parse-bpm " 120 ")`, `(parse-bpm "300")`, `(parse-bpm "40")` | `90`, `120`, `300`, `40` |
| `(parse-bpm "39")`, `(parse-bpm "301")`, `(parse-bpm "120.5")`, `(parse-bpm "12a")`, `(parse-bpm "")` | all `nil` |
| `(step-bpm 120 1 false)`, `(step-bpm 120 -1 true)`, `(step-bpm 295 1 true)`, `(step-bpm 45 -1 true)` | `121`, `110`, `300`, `40` |
| `(latency-text 0.0029 0.0024)` | `"5.3 ms"` |
| `(latency-text 0.01 nil)` | `"10.0 ms"` |
| `(latency-text nil nil)` | `"—"` |
| `(map (juxt :fill :playing?) (progress-segments S 13))` | `([1 false] [1 false] [0.25 true])` |
| `(map (juxt :fill :playing?) (progress-segments S 4))` | `([1 false] [0.125 true] [0 false])` |
| `(map :fill (progress-segments S nil))` | `(0 0 0)` |
| `(map (juxt :fill :playing?) (progress-segments S-loop 23))` | `([1 true] [0 false] [0 false])` |
| `(map :fill (progress-segments S 25))` (`:stop`, past the end) | `(0 0 0)` |
| `(progress-segments nil 3)` | `[]` |
| `(segment-cue-bar S 2)` / `(segment-cue-bar S 1)` | `12` / `4` |
| `(section-cue-bar S [{:name ":intro"} {:name ":verse"} {:name ":chorus"}] 2)` | `12` |
| `(section-cue-bar S [{:name ":intro"} {:name ":bridge"}] 1)` | `nil` |
| `(song-status-text {:cycle 34 :song {:song S :origin 20}})` | `"▶ chorus · bar 13/20"` |
| `(song-status-text {:cycle 20 :song {:song S :origin nil}})` | `"▶ song · starting"` |

**selection** (entries = `(:entries (outline/extract (.parse parser W) W))`, `idx` = `(index-entries entries)`)

| Call | Expected |
|---|---|
| `(sort (keys idx))` | `("def:kick" "fx" "fx:0" "section:0" "section:1" "section:2" "track::bass" "track::drums")` |
| `(pick [] "track::drums" false)` | `["track::drums"]` |
| `(pick ["track::drums"] "track::drums" false)` | `[]` |
| `(pick ["track::drums"] "track::bass" true)` | `["track::drums" "track::bass"]` |
| `(pick ["track::drums" "track::bass"] "track::drums" true)` | `["track::bass"]` |
| `(pick ["track::drums" "track::bass"] "track::bass" false)` | `["track::bass"]` |
| `(pick ["def:kick"] "track::bass" true)` | `["track::bass"]` |
| `(pick ["track::drums"] "section:1" true)` | `["section:1"]` |
| `(ranges ["track::bass"] idx)` | `[{:from 91 :to 161}]` |
| `(ranges ["track::bass" "track::drums"] idx)` | `[{:from 31 :to 90} {:from 91 :to 161}]` |
| `(ranges ["section:1"] idx)` | `[{:from 217 :to 250}]` |
| `(ranges ["fx"] idx)` | `[{:from 162 :to 178}]` |
| `(ranges ["def:kick"] idx)` | `[{:from 0 :to 30}]` |
| `(prune ["track::bass" "def:gone"] idx)` | `["track::bass"]` |
| `(header ["track::bass"] idx)` | `{:kind "track" :title ":bass" :from 91}` |
| `(header ["track::drums" "track::bass"] idx)` | `{:kind "tracks" :title "2" :from nil}` |
| `(header ["section:1"] idx)` | `{:kind "section" :title ":verse" :from 217}` |

**outline** (on `W`)

- Entry kinds/names in order: `[:def "kick"] [:track ":drums"] [:track ":bass"]
  [:fx ":reverb"] [:song nil]`; `:fx` entry `{:from 162 :to 178}`.
- `(contains? (:refs drums-entry) "kick")` → true; `(used-by entries "kick")` →
  `[":drums"]`; `(used-by entries "nope")` → `[]`.
- `(fx)` alone at top level and `(->> x (fx :delay 0.2))` inside a track produce no `:fx`
  entry.

**inspector-model**

| Call | Expected |
|---|---|
| `(track-params [{:note :c2 :amp 0.6 :decay 0.25 :synth :saw} {:amp 0.9} :bd])` | `{:amp 0.6 :decay 0.25 :synth :saw}` |
| `(map first (:sliders (param-rows {:amp 0.6 :decay 0.25 :synth :saw})))` | `(:amp :decay)` |
| `(:text (param-rows {:amp 0.6 :decay 0.25 :synth :saw}))` | `[[:synth "saw"]]` |
| `(map (juxt :name :pos) (fx-rows [{:name "filter" :params {"freq" 2400 "q" 1}} {:name "reverb" :bypassed? true :params {"wet" 0.3}}]))` | `(["filter" 1] ["reverb" 2])` |
| `(map first (:sliders (first (fx-rows [{:name "filter" :params {"q" 1 "freq" 2400}}]))))` | `("freq" "q")` |
| `(:sliders (second (fx-rows [{:name "filter" :params {}} {:name "reverb" :bypassed? true :params {"wet" 0.3}}])))` | `[]` |
| `(track-flags [:drums :bass] #{:bass} :drums)` | `{:muted? false :solo? true}` |
| `(track-flags [:drums :bass] #{:bass} :bass)` | `{:muted? true :solo? false}` |
| `(track-flags [:drums] #{} :drums)` | `{:muted? false :solo? false}` |
| `(mute-action #{:bass} :bass)` / `(mute-action #{} :bass)` | `[:unmute :bass]` / `[:mute :bass]` |
| `(solo-action [:drums :bass :keys] #{} :drums)` | `[:solo :drums]` |
| `(solo-action [:drums :bass :keys] #{:bass :keys} :drums)` | `[:unmute-all]` |
| `(section-tracks {:tracks [":all"]} [{:name ":drums"} {:name ":bass"}])` | `[":drums" ":bass"]` |

**song-transport** (`song_transport_test.cljs`, song from the existing `tracks-s` +
`song-s`: intro 4, verse 8, chorus 8, outro 4)

- With `@audio/scheduler-state` set to `{… :playing? true :cycle 40 :song {:song s :origin
  20 :start-bar 0}}`, `(st/cue-bar! (segment-cue-bar s 2))` leaves `:song` as
  `{:song s :origin 28 :start-bar 12}`, so the next scheduled song bar
  (`:cycle − :origin`) is `12` = the first bar of `:chorus`. The bar that was already
  scheduled is not touched (this is the "next bar" semantics).
- Not playing and `@st/song-buffer` nil: `(st/cue-bar! 12)` returns nil and doesn't throw.

**Playwright** (`e2e/workspace.spec.ts`, buffer `W`)

| Step | Expected |
|---|---|
| Load the app at 1280×800 | `#context-panel` count 0; `#status-head` visible; `#inspector` hidden; `.cm-editor` bounding width ≥ 1000 |
| Click the `track :bass` row (cursor at 0 before) | `#inspector` visible; header contains `track`, `:bass`, `↗ L3`; slider rows `amp 0.60` and `decay 0.25`; text row `synth saw`; editor head still `0`; exactly line 3 has `.ws-sel-line`; row has `.ol-sel`; `__REPULSE_APP__.selection()` = `["track::bass"]` |
| Click the same row again | `#inspector` hidden; no `.ws-sel-line` |
| Click `:drums`, ⇧-click `:bass` | two track blocks; lines 2 and 3 highlighted |
| Press Escape (editor focused) | `#inspector` hidden; `document.activeElement` inside `.cm-editor`; a second Escape focuses the command bar (unchanged behaviour) |
| Select `:bass`, press F12 in the editor | head = `91` (line 3); Shift+F12 returns to `0` |
| Alt+Enter, select `:bass`, focus the `amp` slider, press Shift+ArrowRight | buffer contains `(amp 0.70)` and not `(amp 0.6)`; within 1 s `__REPULSE_APP__.trackParam("bass","amp")` = `0.7`, with no Alt+Enter pressed |
| Focus the editor, press Mod+Z once | buffer text `=== W` |
| Pointer-drag the `:bass` amp knob +40 px in 4 moves (≥ 600 ms total), release, focus editor, Mod+Z once | after the drag the buffer differs from `W` only inside `(amp …)` of line 3; after one undo it `=== W` |
| Click `.sh-bpm`, type `90`, Enter | `__REPULSE_APP__.cycleDur()` = `2.6666666666666665`; `.sh-bpm` text `90` |
| Click `.sh-bpm`, type `200`, Escape | cycleDur unchanged; text `90` |
| Click `.sh-bpm`, type `301`, Enter | input still open with `.sh-invalid`; cycleDur unchanged |
| Focus `.sh-bpm`, ArrowUp; then Shift+ArrowDown | text `91`, then `81` |
| Click the `:verse` row | header `section :verse ↗ L7`; `8 bars`; `▶ cue`; track rows `:drums`, `:bass` |
| Click the `def kick` row | preview contains `(def kick (seq :bd :_ :bd :_))`; text `Used by :drums.` |
| Outline rows | an `fx` group row after the song rows, with child `:reverb` `L4`; clicking `:reverb` shows a `reverb` block with a `wet` slider (once the reverb plugin loaded) and `applies after all tracks` |
| Click M on `:bass` in the inspector | `__REPULSE_APP__.muted()` = `["bass"]`; the outline row gets `.ol-muted`; click again → `[]` |
| Drag the inspector handle +500 px, reload | inspector width 380 after re-selecting a row |

### Checklist

- [ ] No `#context-panel` in the DOM; `app/src/repulse/ui/context_panel.cljs` is deleted
      and `grep -rn "context-panel\|ctx-panel\|ctx-" app/src app/public/css` is empty.
      The PR lists where each former section went (status → status head, TRACKS → track
      inspector, FX → fx group, MIDI/BUSES/SOURCES → session block, BINDINGS dropped).
- [ ] All slider-model, status-model, selection, inspector-model, outline and
      song-transport vectors above pass under `npm test`.
- [ ] Selecting `:bass` in `W` shows its sliders without moving the cursor; editing `amp`
      rewrites the number in the buffer, the live param follows within 1 s without
      Alt+Enter, and **one** undo restores `W` exactly (keyboard step and pointer drag).
- [ ] ⇧-click on two tracks shows both. Esc closes the inspector and keeps editor focus;
      Esc with nothing selected still focuses the command bar.
- [ ] Clicking a progress segment or `▶ cue` while `(song …)` plays switches at the next
      bar (`cue-bar!` unit test; Playwright: with `(bpm 480)` + `W`, clicking the
      `:chorus` segment while the outline marks `:intro` playing makes it mark `:chorus`
      within 2 s — the natural arrival is ≥ 4.5 s away).
- [ ] BPM edit: click, `90`, Enter → `:cycle-dur` = `2.6666666666666665`; Esc cancels;
      `39`, `301`, `120.5` are rejected; ↑ / ⇧↓ step by 1 / 10 within 40–300.
- [ ] Latency shows `latency <x.x> ms` after audio started; no `cpu` text anywhere.
- [ ] At 1280×800 with the inspector closed, the editor is ≥ 1000 px wide.
- [ ] REPuLse-Lisp checks in the app: `(fx :reverb 0.3)` appears as `fx › :reverb` in the
      outline; `(track :bass (->> (seq :c2 :eb2) (amp 0.6)))` gives an inspector `amp`
      slider; `(def kick (seq :bd :_ :bd :_))` used in `(track :drums (stack kick))`
      shows `Used by :drums.`
- [ ] SONG3 tests in `e2e/outline.spec.ts` pass with the row-click test rewritten to the
      selection behaviour; Cmd/Ctrl-click, F12 (no selection), Shift+F12, collapse and
      playing-section tests unchanged.
- [ ] The `<repulse-editor>` embed is unchanged (no status head, inspector or selection
      highlight installed).
- [ ] Docs: USAGE.md updated (status head, inspector, selection, keys, session block,
      every former context-panel mention); ARCHITECTURE/CONTRIBUTING/FUTURE-FEATURES
      updated; README checked. ROADMAP + CLAUDE.md marked delivered.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass; `npm run gen:grammar`
      and `npm run gen:ai-docs` show no drift.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5) covering the
      inspector kinds, slider write-back + undo, multi-select, Esc/F12, BPM edit, cue,
      session block, resize persistence, and ≥ 2 regressions (Alt+Enter eval, timeline
      mute buttons).

## What NOT to do

- No `(master …)` form (WS2), no inline sliders in the code and no `≋` / ⌘K S (WS3), no
  AI rail (WS4). The fx outline group is a stand-in that WS2 replaces.
- Don't change how sliders patch code: `lisp_patcher.cljs` stays the single writer that
  locates numbers; the `*-patch-and-eval!` functions keep their signatures, scoping and
  150 ms debounce. Only the history annotation of the drag session is new.
- Don't change the bottom console / track visualisation (`#track-panel`,
  `repulse.ui.timeline`) or the top nav.
- Don't add a CPU meter or worklet timing messages.
- Don't make BPM edits write `(bpm …)` into the buffer, and don't add new Lisp built-ins.
- Don't touch `window.__REPULSE_TEST__` / `test_api.cljs` or the embed.
