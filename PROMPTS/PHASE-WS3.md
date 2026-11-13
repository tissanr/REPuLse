# Phase WS3 — Inline Sliders in Code: `(slider v min max)`

> **Spec status:** full
> **Automation:** 🤖 auto — `slider` evaluation is a unit test, and the widget, drag write-back
> and ⌘K S wrapping are Playwright-testable.

**Milestone:** M2 — Full songs in code
**Depends on:** none hard. WS1 recommended (the inspector's `≋` toggle).

## Goal

Numbers you want to play with become draggable widgets right in the code, as in
*Outline Panel v2* (`design/outline-panel-v2/outline-v2.jsx`: `InlineSlider`,
`CodeLine`, `ISlider`; frame 6 "Inline slider" in `Outline Panel v2.html`):

```lisp
;; before
(track :bass (->> (seq :e1 :g1) (synth :saw) (amp 0.7)))

;; after — (slider value min max) evaluates to value; the editor draws a fill bar
(track :bass (->> (seq :e1 :g1) (synth :saw) (amp (slider 0.7 0 1))))
```

- `(slider v min max)` evaluates to `v`: a plain number, so it works wherever a number works.
- The editor replaces the form with a 70×15 widget: 1 px border `#1e2a45`, cyan fill
  20 % (32 % on hover), and the value right-aligned in cyan. The cursor is `ew-resize`, and
  the tooltip shows the range.
- Dragging rewrites the value literal in the code on every move (one undo step per drag)
  and re-evaluates with the same patch + debounce path as today's inspector sliders.
  ⇧-drag = 10× finer, with a `×0.1` tag.
- When the text cursor touches the form, the widget turns back into text, so you can type.
- **⌘K S** (Ctrl-K S) on a number literal wraps it in `(slider …)`. The range comes from
  the param name: amp 0–1, pan −1–1, decay 0–2, freq 20–20000 (log), bpm 40–300;
  unknown names get 0–1. On an existing slider, ⌘K S unwraps it.
- WS1 inspector: track params that are literal numbers in the code get a `≋` toggle
  (wrap/unwrap). It is cyan when the param is an inline slider.

## Scope

- New built-in `slider` (CLAUDE.md Rule 5 checklist: grammar, `gen:grammar`, completions,
  builtin_meta, `gen:ai-docs`, hover docs). Validation: 3 or 4 args, numbers, min < max;
  a value outside [min, max] is clamped with a warning.
- A CodeMirror `StateField` + `WidgetType` replacing `(slider …)` ranges when no selection
  touches them. A log scale when min > 0 and max/min ≥ 100, or forced by `:log` / `:lin`.
- Write-back through `lisp_patcher.cljs` (new slider helpers, and the existing param finders
  learn to look into a `(slider …)` value) and the existing patch + debounced re-eval path.
- ⌘K S keybinding, inspector `≋` toggle, eval warnings for clamped values.

## Decisions (decided)

The three open questions of the outline came with recommendations. They were adopted as
written (1–3). Decisions 4–16 were taken during spec expansion (2026-11-04, auto-phase
pipeline) to fit the existing code.

1. **Drag write-back = patch + debounced re-eval.** — **Decided.** No new hot-swap path.
   A drag calls a new `eval-orchestrator/inline-slider-patch-and-eval!` that patches the
   value literal and then runs the same 150 ms debounced `evaluate-project!` as
   `slider-patch-and-eval!`. As with the inspector's track sliders, the sound follows the
   code 150 ms after the pointer stops moving. Measure before adding anything faster.
2. **Log scale: automatic, with an optional 4th arg.** — **Decided.** Log when
   min > 0 and max/min ≥ 100. An optional 4th arg `:log` forces log, `:lin` forces linear.
   `:log` with min ≤ 0 is an eval error. The scale only affects the widget mapping; the
   evaluated value is always `v` (clamped).
3. **Number formatting: integers when max ≥ 100, else 2 decimals.** — **Decided.** As
   in the design's `fmt`: `max >= 100` → `Math.round(v)`, else `v.toFixed(2)` (`0.70`,
   `1.00`). The widget label and the text the drag writes into the buffer use the same
   string. If rounding pushes the value outside [min, max] (e.g. `0.00` on a 0.001–1 range),
   the min/max literal text is written instead.
4. **`slider` lives in the lisp package, with an app warning hook.** A new namespace
   `repulse.lisp.builtins.slider` holds one implementation, `make-slider-fn [on-warning]`.
   Its `make-builtins` registers `(make-slider-fn nil)`: it clamps but does not warn. This
   is used by the TEST1 harness, lisp unit tests and anything built on `leval/make-env`.
   `repulse.env.builtins/ensure-env!` overrides `"slider"` with
   `(make-slider-fn record-eval-warning!)`, which conj's `{:message :from :to}` onto a new
   per-evaluation atom `repulse.env.builtins/eval-warnings`. Builtins never see the env,
   so a `:*hook*` env key (like `:*master-fn*`) cannot reach them. That is why the hook is
   passed as a closure.
5. **Warnings surface like the existing structure warning.** `evaluate*` resets
   `eval-warnings` next to `seen-tracks`. After a successful evaluation it appends
   `Warning: <msg>[; <msg>…]` to the output line (status `:warning`), and sets warning
   diagnostics on each warning's value-literal range. Messages are de-duplicated
   (`distinct`): the same form evaluated twice warns once. `report-unknown-sounds!` keeps
   these diagnostics when it later sets its own (today it replaces all diagnostics).
   In project mode, global ranges are mapped with `project/locate`. Only those in the
   active buffer get a diagnostic; the output line lists all of them. A warning without a
   source range (e.g. a rational value `1/4` read as a vector) only goes to the output line.
6. **Widget visibility = "no selection touches the form".** The raw text is shown when any
   selection range `r` has `r.from <= form.to` and `r.to >= form.from` (inclusive, so a
   cursor directly before `(` or after `)` counts). CodeMirror moves the cursor over a
   replaced range in one step, so a strictly-inside rule would make the text unreachable
   by arrow keys. With the inclusive rule, the arrow keys first reach the boundary, the text
   appears, and the next arrow key moves into it. Double-clicking a widget selects its value
   literal, which also shows the text.
7. **Only well-formed, single-line forms become widgets.** Exactly 3 or 4 args. The value,
   min and max are numeric literal atoms (`-?\d+(\.\d+)?(/\d+)?`). The optional 4th is
   `:log` or `:lin`. min < max, and `:log` needs min > 0. The form contains no line break
   and is not in a comment or string. Anything else stays text, and evaluation reports
   the error if there is one. `(slider x 0 1)` with a symbol is valid code (it evaluates)
   but gets no widget.
8. **Decorations come from a `StateField`, not a `ViewPlugin`.** They depend only on the
   doc and the selection. A field is deterministic, survives buffer switches (each buffer
   `EditorState` is created with the same extension array, `buffers/init!`), and keeps the
   form scan off the selection-only path: forms are re-scanned only on `docChanged`.
9. **The widget is written in ClojureScript.** `repulse.ui.inline-slider` subclasses
   `WidgetType` with `shadow.cljs.modern/defclass` (shipped with the shadow-cljs 3.5.8 in
   `package.json`). It implements `eq`, `toDOM`, `updateDOM` (returns `true`, so the DOM node
   is kept while dragging) and `ignoreEvent` (returns `true`). `lisp-lang/insert-helper.js`
   is not a template to copy: the logic lives in CLJS (`lisp_patcher`, `slider_model`).
10. **Main app only.** The extension is added to the main editor's extension array in
    `app.cljs`, next to `outline-panel/extension`. It is not added to
    `editor/editor-extensions`, so the `<repulse-editor>` embed and the command bar get no
    widgets: the embed evaluates with `eo/evaluate!`, not `evaluate-project!`. `slider` still
    *evaluates* everywhere.
11. **Drag mapping.** The pointer moves the slider in t-space (0..1, log or linear):
    `t = clamp01(t0 + acc / 140)`, where `acc` sums `dx × (⇧ ? 0.1 : 1)` per pointermove.
    So 140 px = full range, twice the widget width, as in the design's `InlineSlider`.
    `t0` comes from the value literal at pointerdown. The drag keeps the unrounded `t`, so
    fine steps never stall on rounding. Pointerdown does not jump the value (unlike the
    inspector's click-to-position). The buffer is only written when the formatted text
    changes.
12. **⌘K S = CodeMirror multi-stroke key `"Mod-k s"`.** No ⌘K chord prefix exists in the app
    today. `Shift-Mod-k` (deleteLine) in `defaultKeymap` is different and unaffected. The
    command always returns `true`, so the `s` is never typed. With nothing to toggle it
    changes nothing. Order: (a) the innermost `(slider …)` form touching the main cursor
    (inclusive, as in 6) → **unwrap**: replace the form with the text of its first arg;
    (b) else a numeric literal atom touching the cursor → **wrap**; (c) else nothing.
    After a wrap the cursor sits at the end of the value literal inside the new form, so the
    raw text stays visible until the cursor leaves. After an unwrap it sits after the literal.
    Each toggle is one ordinary undo step. No re-evaluation (the value does not change).
13. **Wrap range.** The param name is (in order): the effect's primary param when the
    number directly follows the effect keyword of an `(fx :<effect> …)` form
    (`slm/FX-PRIMARY-PARAM`, e.g. `(fx :filter 800)` → `freq`). Else the keyword right
    before the number (`(fx :filter :freq 800)` → `freq`). Else the head atom of the
    enclosing list (`(decay 0.25)` → `decay`). Ranges: `amp` [0 1], `pan` [-1 1],
    `decay` [0 2], `freq` [20 20000], `bpm` [40 300]; anything else [0 1]. If the literal
    lies outside, the range is widened to include it (`(amp 1.5)` → `(slider 1.5 0 1.5)`),
    so a wrap never creates a clamp warning. The 4th arg is never written. `freq` is log
    automatically (Decision 2). Inserted text: `(slider <original literal text> <min> <max>)`,
    with min/max as `(str n)`.
14. **Inspector `≋`.** Only on the **track** block's param slider rows (not section amp
    rows, not fx rows). The toggle is shown when the track's form (outline entry
    `:from`..`:to`) contains `(<param> NUMBER …)` (dim `≋`, click wraps) or
    `(<param> (slider NUMBER …))` (cyan `≋`, class `on`, click unwraps). Otherwise there
    is no toggle. Clicking runs the same toggle as ⌘K S at the value literal's position.
    It does not move the editor cursor, so a wrapped param shows as a widget right away
    (unless the cursor touches it).
15. **Existing write-backs look through `(slider …)`.** `find-param-num` (track params),
    `scan-for-named-param` and `find-fx-pos-param-num` (fx params) also match
    `(<param> (slider NUMBER …))`, `:<param> (slider NUMBER …)` and
    `(fx :<name> (slider NUMBER …))`. They return the inner value literal's range plus
    `:slider {:from :to :min :max}`. Without this, the inspector would write into a later
    `(amp NUMBER)` of another track, or append a duplicate `:freq` to an fx form. Every
    `eval-orchestrator` numeric write-back that targets a slider value clamps `v` to the
    slider's [min, max] first (`clamp-to-slider`), so inspector ranges (e.g. decay 0–4)
    never write outside the slider's own range.
16. **`slider` returns a JS number.** `(u/->num v)`, clamped: `(max lo (min hi v))`.
    Rational literals are accepted (`(slider 1/4 0 1)` → `0.25`). Symbols and expressions
    are fine (`(slider x 0 1)`). Non-finite numbers count as non-numbers.

### Error and warning texts (exact)

`<x>` = the offending argument after evaluation, shown as `a pattern` when
`core/pattern?`, else `(pr-str (u/deep-unwrap arg))`. If that is longer than 40
characters, use its first 39 characters + `…` (U+2026). `<n>` = `(str n)` of the
`u/->num` value (`2`, `-0.5`, `0.25`). The output line shows errors as `Error: <text>`
(`report-eval-error!`) and warnings as `Warning: <text>`.

| Case | Kind | Text | Source range |
|---|---|---|---|
| Wrong arity (not 3 or 4 args) | error | `slider expects 3 or 4 arguments: (slider value min max) or (slider value min max :log) — got <count>` | `slider` symbol |
| value not a finite number | error | `slider value must be a number — got <x>` | that arg if it has a source, else `slider` symbol |
| min not a finite number | error | `slider min must be a number — got <x>` | same rule |
| max not a finite number | error | `slider max must be a number — got <x>` | same rule |
| min ≥ max | error | `slider min must be less than max — got min <min>, max <max>` | `slider` symbol |
| 4th arg not `:log` / `:lin` | error | `slider scale must be :log or :lin — got <x>` | the 4th arg |
| `:log` with min ≤ 0 | error | `slider :log needs min > 0 — got min <min>` | the 4th arg |
| value outside [min, max] | warning (value clamped) | `slider value <v> is outside [<min>, <max>] — clamped to <clamped>` | the value arg (diagnostic, severity `warning`) |

Check order: arity → value, min, max (each must be a number) → 4th arg → min < max →
`:log` min → clamp. Errors are thrown as `(ex-info text {:type :eval-error :from … :to …})`.
Without `:from`, `eval-form` falls back to the call head's range (`packages/lisp/src/repulse/lisp/eval.cljs`, the
`(fn? f)` branch re-throw).

## Background

### Evaluator and built-in registration — `packages/lisp/src/repulse/lisp/`

- `eval.cljs` — `eval-form` evaluates a call by `(apply f (map #(eval-form % env) tail))`.
  Number, keyword and string literals reach builtins as `reader/SourcedVal` records
  (`{:v … :source {:from :to}}`). Symbols and lists carry `:source` in metadata. A thrown
  error without `:from` in its `ex-data` is re-thrown with the call head's range.
  `make-env [stop-fn bpm-fn]` merges the `make-builtins` maps of the `repulse.lisp.builtins.*`
  namespaces (`b-pattern` … `b-arrangement`, required in the `ns` form) plus
  `"bpm"`/`"stop"`/`:*defs*`/`:*macros*`/`:*synths*`.
- `util.cljs` — `unwrap`, `deep-unwrap`, `source-of`, `->num` (unwraps, and turns an
  `[n d]` rational vector into `(/ n d)`).
- `reader.cljs` — `read-number` returns ints, floats, `[n d]` for `1/4`, and
  `(bpm n)` for `120bpm`. A leading `-` followed by a digit negates. A rational vector is a
  plain vector (not a `SourcedVal`), so it has no `:source` after evaluation.
- `builtins/params.cljs` — `amp`, `decay`, `pan`, … take `(u/unwrap v)`. A plain number
  from `slider` therefore behaves exactly like a literal.
- `core.cljs` — `eval-string` turns thrown `ex-info` into `EvalError {:message :source}`.

There is **no eval-warning mechanism** today. `grep -i warn` over `packages/lisp` and
`app/src/repulse/env` finds nothing. Runtime warnings exist only in
`eval-orchestrator`: `report-structureless-signals!` (appends `Warning: …` to the output
line) and `report-unknown-sounds!` (async; output line + `set-warning-diagnostics!`, which
**replaces** all diagnostics).

### App env — `app/src/repulse/env/builtins.cljs` (`repulse.env.builtins`)

Owns `env-atom`, `builtin-names`, `seen-tracks` (per-evaluation duplicate-track set,
reset by `evaluate*`) and `evaluate-ref`. `ensure-env!` builds the env once:
`(merge (assoc (leval/make-env …) :*register-synth-fn* … :*master-fn* b-fx/run-master!)
(b-tracks/make-builtins ctx) … (b-project/make-builtins ctx))`, then snapshots
`builtin-names`. The snippet sandbox (`app/src/repulse/snippets/sandbox.cljs`,
`preview-env`) forks this env, so it inherits whatever `"slider"` the app installs.

### Evaluation — `app/src/repulse/eval_orchestrator.cljs` (`repulse.eval-orchestrator`)

- `evaluate*` [code {:keys [project]}]: resets `fx`, `seen-tracks`, `b-fx/reset-master-run!`,
  `song-transport/reset-pending!`; evaluates (project mode: `project-eval/run-project`
  returns `{:value :bases}`); on error → `report-eval-error!`; else clears diagnostics
  (`buffers/clear-all-diagnostics!` in project mode, `set-diagnostics! view nil nil nil`
  otherwise when `val` is non-nil), activates tracks, sets the output line in a `cond`, then
  runs `b-fx/loose-fx-hint!`, the legacy `:_` hot-swap, pending mutes,
  `report-structureless-signals!` (signature `[track-names last-output set-output!]`) and
  `report-unknown-sounds!` (`[track-names code base-msg set-output!]`). `last-output` is a
  volatile holding the last output message, so later warnings append with ` — `.
- `evaluate-in-context!` (AI preview) resets `song-transport` and `b-fx` master state
  before and in `finally`.
- `set-warning-diagnostics!` [view diags] — `setDiagnostics` with `:severity "warning"`.
- **Slider write-back.** `fmt-num` (integer → `"1"`, else `toFixed 2`); `drag-origin` +
  `begin-slider-drag!` / `end-slider-drag!` (patches during a drag go out of history;
  the end collapses them into one isolated undo event via `sm/text-diff`);
  `dispatch-replace!`; `patch-param-in-editor!` (scope = from the first `:<track>` in the
  doc to the end, `patcher/find-param-num`); `slider-patch-and-eval!` [track param v]
  (patch + `slider-timeout` 150 ms debounce → `evaluate-project!`). The fx variants
  (`patch-fx-param-in-editor!` → `patcher/fx-param-edit`, `fx-slider-patch-and-eval!`,
  `patch-per-track-fx-param-in-editor!`, `per-track-fx-slider-patch-and-eval!`) work the
  same way and also set the live param first. The debounce block is duplicated in five
  functions.

### Patcher — `app/src/repulse/lisp_patcher.cljs` (`repulse.lisp-patcher`)

A minimal tokenizer: `next-tok` / `tokenize doc start end` → significant tokens
`{:t :lp|:rp|:atom :from :to}` (whitespace, `;` comments and strings skipped; `[`/`]` count
as `:lp`/`:rp`). Private helpers: `atom-text`, `num-atom?` (text starts with `-?[0-9]`),
`keyword-atom?`, `scan-for-named-param` / `scan-for-named-keyword-param` (`:param X` at
depth 1 of a form). Public: `find-param-num` (first `(param NUMBER` in scope),
`find-fx-named-param-num`, `find-fx-named-param-keyword`, `find-fx-pos-param-num`,
`find-fx-form-close`, `fx-form-range`, `fx-param-edit`, `fx-keyword-param-edit`.
**None of them sees through a `(slider …)`**: `(amp (slider 0.7 0 1))` has `(` where
`find-param-num` expects a number, so today an inspector drag would patch a *later*
`(amp N)` or nothing, and `fx-param-edit` would append a second `:freq` to
`(fx :filter :freq (slider 800 20 20000))`. Tests: `app/src/repulse/lisp_patcher_test.cljs`
(`fx-form-range-test`, `fx-param-edit-test`, `fx-keyword-param-edit-test`).

### Slider model — `app/src/repulse/ui/slider_model.cljs` (`repulse.ui.slider-model`, alias `sm`/`slm`)

Pure, no DOM. `SLIDER-PARAMS` (inspector track ranges: amp 0–1, pan −1–1, decay 0–4, …),
`FX-SLIDER-PARAMS`, `FX-PRIMARY-PARAM` (`"filter"` → `"freq"`, `"reverb"` → `"wet"`, …),
`fmt-pv`, `decimals`, `snap`, `value->t`, `t->value`, `drag-value`, `key-step`, `text-diff`.
All linear and step-based; there is no log scale. Tests: `slider_model_test.cljs`.

### Editor — `app/src/repulse/ui/editor.cljs` (`repulse.ui.editor`)

`editor-view` atom; `highlights-field` (a `StateField` of `Decoration`s via
`EditorView.decorations` — the pattern to follow); `editor-extensions [on-eval on-upd]`
(history, lineNumbers, oneDark, `lispLanguage`, bracketMatching, `lintGutter`, listeners,
one keymap: Escape, Alt-Enter, Ctrl-., F9, `defaultKeymap`, `historyKeymap`).
`clear-diag-listener` clears all diagnostics on every doc change. **No `Mod-k` binding
exists anywhere** (`grep -rni "mod-k" app/src` is empty). Other keymaps:
`outline_panel.cljs` `extension` (`Mod-Shift-o`, Escape/F12 at `Prec.highest`),
`goto.cljs` `extension` (F12, Shift-F12 at `Prec.high`). No `WidgetType` exists in CLJS.
The only one is `InsertButtonWidget` in `app/src/repulse/lisp-lang/insert-helper.js` (J2), whose
plugin listens to `mousedown` on `contentDOM`.

### App wiring — `app/src/repulse/app.cljs`

The main editor's extensions are
`(.concat (editor/editor-extensions …) #js [outline-panel/extension goto/extension
selection-highlight/extension])`, passed to `editor/make-state` and to `buffers/init!`
(every buffer state uses them). Test handles: `window.__REPULSE_EDITOR__` (the view) and
`window.__REPULSE_APP__` (`cycleDur`, `muted`, `selection`, `trackParam`, `fxParam`).

### Inspector — `app/src/repulse/ui/inspector.cljs` (`repulse.ui.inspector`)

`track-block [entry head?]` renders `(im/param-rows (live-track-params kw))` sliders via
`slider-html [label v cfg {:keys [key track fx param at]}]` (`.ins-srow` grid
`36px minmax(0,1fr) 38px` in `app/public/css/main.css` ~L1731; `data-key` `"<track>|<param>"`).
`entry` is the outline entry (`:from`/`:to` of the `(track …)` form). `apply-value!` routes
to `eo/slider-patch-and-eval!` for track params. Pointer drag / keyboard step bracket the
change with `eo/begin-slider-drag!` / `eo/end-slider-drag!`. `on-click` dispatches on
`data-act` (`mute`, `solo`, `cue`, `jump`, `close`). `schedule-render!` re-renders.

### Built-in surface files (CLAUDE.md Rule 5)

`app/src/repulse/lisp-lang/repulse-lisp.grammar` (`BuiltinName`, e.g. `"quote" | "tween" |`),
`parser.js` / `parser.terms.js` (from `npm run gen:grammar`), `completions.js` (the
"Per-event parameters" block has `tween`, `amp`, …), `hover.js` (`"tween": { signature,
description, example }`), `app/src/repulse/content/builtin_meta.edn` (`:tween {:category
"transform" :returns "number" …}`), `docs/ai/builtins.json` (from `npm run gen:ai-docs`,
which reads all four sources). Note: the Lezer `Number` token has no sign or `/`, so `-1`
and `1/4` are not `Number` nodes. The widget therefore scans with the patcher tokenizer,
not the syntax tree.

### Tests

`npm test` = shadow `:test` build with `packages/core/src/repulse/test_runner.cljs`
(namespaces listed twice: `:require` and `run-tests`; `repulse.lisp.eval-test`,
`repulse.lisp-patcher-test` and `repulse.ui.slider-model-test` are already registered).
`npm run test:e2e` = Playwright (`playwright.config.ts`, `testDir: ./e2e`, server builds
`test-harness` + `app` and serves `app/public` on :3100). `/test-harness.html` exposes
`window.__REPULSE_TEST__.evalToEvents(code, cycles)` (`app/src/repulse/test_api.cljs`,
env = `leval/make-env`, so `slider` without the warning hook). App specs
(`e2e/workspace.spec.ts`, `e2e/master.spec.ts`) drive `/` through `__REPULSE_EDITOR__` /
`__REPULSE_APP__`, use `page.keyboard.press('ControlOrMeta+z')` for undo, and Alt+Enter
to evaluate.

## Implementation

### 1. Lisp built-in — `packages/lisp/src/repulse/lisp/builtins/slider.cljs` (new)

```clojure
(ns repulse.lisp.builtins.slider
  "slider — a number that the editor draws as a draggable widget (Phase WS3).
   Evaluates to its value, clamped to [min, max]."
  (:require [repulse.core :as core]
            [repulse.lisp.util :as u]))

(defn- show [x]   ; "a pattern" | pr-str, ≤ 40 chars (39 + "…")
  …)
(defn- fail! [msg arg]  ; ex-info with the arg's :from/:to when (u/source-of arg)
  …)

(defn make-slider-fn
  "on-warning: nil or (fn [{:keys [message from to]}]) — called when v is clamped."
  [on-warning]
  (fn [& args]
    ;; arity → numbers (u/->num, number? + js/isFinite) → 4th arg → min < max
    ;; → :log min → clamp; texts exactly as in the table above
    …))

(defn make-builtins [] {"slider" (make-slider-fn nil)})
```

Register it in `eval.cljs`: require `[repulse.lisp.builtins.slider :as b-slider]` and
merge `(b-slider/make-builtins)` in `make-env`. The warning callback gets
`(u/source-of value-arg)` merged into the map (`:from`/`:to` absent when there is none).

### 2. App env + warnings — `app/src/repulse/env/builtins.cljs`

- `(defonce eval-warnings (atom []))`, and `(defn record-eval-warning! [w] (swap! eval-warnings conj w))`.
  Add both to the ns docstring's Exports.
- In `ensure-env!`, merge `{"slider" (b-slider/make-slider-fn record-eval-warning!)}`
  **after** the `leval/make-env` map, so it overrides the lisp default.

### 3. Orchestrator — `app/src/repulse/eval_orchestrator.cljs`

- `evaluate*`: `(reset! builtins/eval-warnings [])` next to `seen-tracks`.
  `evaluate-in-context!`: reset it before and in `finally`.
- New private `slider-warning-diags [warnings project bases]` → `[{:from :to :message}]`.
  Non-project: the warnings that have `:from`. Project: map with `(project/locate bases
  from to)`, keep `(= buffer (buffers/active-name))`, use local `:from`/`:to`.
- New private `report-slider-warnings! [diags last-output set-output!]`: when
  `@builtins/eval-warnings` is non-empty, append `"Warning: " (cstr/join "; " (distinct
  messages))` like `report-structureless-signals!` (status `:warning`), and
  `set-warning-diagnostics!` with `diags`. Call it right after `b-fx/loose-fx-hint!`
  (before the structure and unknown-sound reports, which append after it).
- `report-unknown-sounds!` gains a first parameter `extra-diags` and concatenates it in
  front of its own diagnostics when it calls `set-warning-diagnostics!`.
- New private `schedule-slider-eval!` (the 150 ms `slider-timeout` debounce →
  `evaluate-project!`). The five existing copies may switch to it (a pure refactor).
- New public `inline-slider-patch-and-eval! [at text]`:

```clojure
(defn inline-slider-patch-and-eval!
  "Phase WS3: set the value literal of the (slider …) form whose ( is at `at` to
   `text` and schedule the debounced re-evaluation. A stale `at` (no slider
   form starts there) writes nothing and schedules nothing."
  [at text]
  (when-let [view @editor/editor-view]
    (let [doc (.. view -state -doc (toString))]
      (when-let [{:keys [value]} (patcher/slider-form-at doc at)]
        (when (not= text (:text value))
          (dispatch-replace! view (:from value) (:to value) text)
          (schedule-slider-eval!))))))
```

- New private `clamp-to-slider [r v]`: `(if-let [{:keys [min max]} (:slider r)] (cljs.core/max min (cljs.core/min max v)) v)`.
  Apply it in `patch-param-in-editor!` (the `find-param-num` result) and
  `patch-per-track-fx-param-in-editor!` before `fmt-num`. In `patch-fx-param-in-editor!`,
  when the edit returned by `patcher/fx-param-edit` has `:slider`, re-format its `:insert`
  as `(fmt-num (clamp-to-slider edit new-val))`.
- Update the ns docstring Exports with `inline-slider-patch-and-eval!`.

### 4. Slider model — `app/src/repulse/ui/slider_model.cljs`

Add (pure, documented in the Exports line):

| Fn | Contract |
|---|---|
| `parse-num-literal [s]` | number for `^-?\d+(\.\d+)?(/\d+)?$` (`n/d` → `(/ n d)`), else `nil`. Non-finite → `nil` |
| `inline-cfg [form]` | `form` from `patcher/find-slider-forms`. → `{:v :min :max :log? :min-text :max-text}` or `nil` when a literal does not parse, min ≥ max, the mode text is not `":log"`/`":lin"`, or `:log` with min ≤ 0. `:log?` = mode `:log` → true, `:lin` → false, none → `(and (pos? min) (>= (/ max min) 100))` |
| `inline-t [cfg v]` | position 0..1 of `v` (clamped to [min max]). Log: `ln(v/min)/ln(max/min)` |
| `inline-value [cfg t]` | inverse of `inline-t`, unrounded: `min·(max/min)^t` or `min + t·(max−min)` |
| `inline-fmt [cfg v]` | Decision 3 string; falls back to `:min-text`/`:max-text` when the rounded value leaves the range |
| `INLINE-DRAG-PX` | `140` |
| `inline-drag-t [t0 acc]` | `clamp01(t0 + acc / INLINE-DRAG-PX)` |
| `inline-range-text [cfg]` | `"<min>–<max>"` (U+2013). max ≥ 1000 → `(str (/ max 1000)) "k"`. Appends `" · log"` when `:log?` |
| `WRAP-RANGES` | `{"amp" [0 1] "pan" [-1 1] "decay" [0 2] "freq" [20 20000] "bpm" [40 300]}` |
| `wrap-range [pname v]` | `(get WRAP-RANGES pname [0 1])`, widened to include `v` |

### 5. Patcher — `app/src/repulse/lisp_patcher.cljs`

Require `[repulse.ui.slider-model :as slm]` (one direction only: the model never requires
the patcher). Add:

| Fn | Contract |
|---|---|
| `find-slider-forms [doc]` | every `(slider A B C [D])` list outside comments/strings where A, B, C are numeric-literal atoms (`slm/parse-num-literal` non-nil) and D, if present, is one atom, with exactly 3 or 4 args. → `[{:from :to :value {:from :to :text} :min {…} :max {…} :mode nil\|{:from :to :text}}]` in source order (`:to` = index after `)`) |
| `slider-form-at [doc at]` | the `find-slider-forms` entry whose `:from` = `at`, else `nil` |
| `visible-sliders [doc sels]` | `sels` = `[[from to] …]`. → `[{:form f :cfg c}]` for each `f` with `c = (slm/inline-cfg f)` non-nil, no `\n` in `doc[from,to)`, and no sel touching it (Decision 6) |
| `slider-toggle-edit [doc pos]` | Decision 12/13. → `{:from :to :insert :cursor}` or `nil` |

Private helpers: the innermost `(slider …` list touching `pos` (any args, for unwrap:
`:insert` = `doc[first-arg.from, first-arg.to)`, where a list arg spans to its matching
`:rp`), the numeric atom touching `pos`, and the param-name rule of Decision 13.

Extend the existing finders (Decision 15). The new branch matches `:lp`, atom `"slider"`, numeric atom
where today a numeric atom is expected. It returns the numeric atom's `{:from :to}` plus
`:slider {:from :to :min :max}` (`:min`/`:max` = `slm/parse-num-literal` of the next two atoms,
`nil` if not literals; `:to` from the matching `:rp`):
`find-param-num`, `scan-for-named-param` (so `find-fx-named-param-num`) and
`find-fx-pos-param-num`. `fx-param-edit` passes `:slider` through on its two replace branches.
The existing return shape (`{:from :to}`) is unchanged for plain numbers.

### 6. Widget + keymap — `app/src/repulse/ui/inline_slider.cljs` (new, `repulse.ui.inline-slider`)

```clojure
(ns repulse.ui.inline-slider
  "Inline (slider v min max) widgets in the main editor (Phase WS3): a StateField
   replaces each well-formed slider form with a draggable bar unless a selection
   touches it; ⌘K S / Ctrl-K S wraps or unwraps. Main app only (not the embed).
   Exports: extension, toggle-at!."
  (:require [shadow.cljs.modern :refer [defclass]]
            [repulse.lisp-patcher :as patcher]
            [repulse.ui.slider-model :as slm]
            [repulse.eval-orchestrator :as eo]
            ["@codemirror/view" :refer [EditorView Decoration WidgetType keymap]]
            ["@codemirror/state" :refer [StateField Prec]]))
```

- **Field.** Value `#js {:forms <find-slider-forms result> :deco <DecorationSet>}`.
  `create` computes both. `update` re-scans forms only when `(.-docChanged tr)`, and rebuilds
  `:deco` when the doc or the selection changed (`(.-selection tr)`). Otherwise it returns the
  old value. `provide` → `(.from EditorView.decorations f #(.-deco %))`. Build the deco
  from `visible-sliders` logic (or the cached forms with the same filter) as
  `(.range (.replace Decoration #js {:widget (SliderWidget. spec)}) from to)`, sorted,
  `(.set Decoration arr true)`.
- **Widget** (`defclass SliderWidget (extends WidgetType)`, field `spec` =
  `{:from :value-text :cfg :form-text}`):
  - `eq` → `(= spec (.-spec other))`.
  - `toDOM [view]` →
    `<span class="cm-inline-slider" data-from="<from>" title="<form text> · <range text> · drag, ⇧ fine">`
    `<span class="cm-inline-slider-fill" style="width:<100·t>%">` +
    `<span class="cm-inline-slider-val"><inline-fmt>`.
    `t` = `(slm/inline-t cfg (:v cfg))`, and the label is `(slm/inline-fmt cfg (:v cfg))`, so an
    out-of-range literal shows clamped. Add class `dragging` when the module `drag` atom's
    `:from` equals this `from`. Attach `pointerdown` and `dblclick` listeners to the span.
  - `updateDOM [dom view]` → rewrite `data-from`, title, fill width and label in place.
    Returns `true`.
  - `ignoreEvent` → `true`.
- **Drag** (module atom `drag`):
  - `pointerdown`: `.preventDefault` (suppresses CodeMirror's `mousedown`, so the
    selection does not move) and `.stopPropagation`. `(.focus view)`. Re-read the form
    with `patcher/slider-form-at` at `data-from` and `slm/inline-cfg`; bail if nil.
    `eo/begin-slider-drag!`. Store `{:from :cfg :t0 :acc 0 :x clientX :last value-text}`.
    Add class `dragging`. Add `pointermove`/`pointerup`/`pointercancel` listeners on
    `js/window`. Window listeners survive a widget re-render, so no pointer capture is needed.
  - `pointermove`: `acc += dx × (shiftKey ? 0.1 : 1)`. Toggle class `fine` (shows the
    `<span class="cm-inline-slider-fine">×0.1</span>` tag, created once in `toDOM`). Then
    `text = (slm/inline-fmt cfg (slm/inline-value cfg (slm/inline-drag-t t0 acc)))`. When
    `text` ≠ `:last` → `(eo/inline-slider-patch-and-eval! from text)`.
  - `pointerup`/`pointercancel`: `eo/end-slider-drag!` (one undo step; nothing when nothing
    changed). Remove the window listeners, clear `drag`, and remove the `dragging`/`fine`
    classes from the current `.cm-inline-slider[data-from]` element.
  - `dblclick`: dispatch `{:selection {:anchor value.from :head value.to}}`. The selection now
    touches the form, so the raw text appears with the value selected.
- **Toggle.** `toggle-at! [view pos]`: `(patcher/slider-toggle-edit doc pos)` → dispatch
  `{:changes {:from :to :insert} :selection {:anchor cursor}}`. Returns `true` when it
  changed something. The keymap entry runs it at the main selection head and always returns
  `true`.
- **`extension`** = `#js [field (.high Prec (.of keymap #js [#js {:key "Mod-k s" :run …}]))]`.

### 7. Wiring and styles

- `app/src/repulse/app.cljs`: require `repulse.ui.inline-slider` and add
  `inline-slider/extension` to the `#js [outline-panel/extension …]` array.
- `app/public/css/main.css` (next to the WS1 inspector block):

```css
.cm-inline-slider { position: relative; display: inline-flex; align-items: center;
  justify-content: flex-end; width: 70px; height: 15px; vertical-align: -3px;
  border: 1px solid #1e2a45; background: #0a1020; cursor: ew-resize; user-select: none;
  box-sizing: border-box; padding: 0 5px; }
.cm-inline-slider-fill { position: absolute; left: 0; top: 0; bottom: 0;
  background: #56b6c2; opacity: .2; pointer-events: none; }
.cm-inline-slider-val { position: relative; font-size: 11px; line-height: 13px; color: #56b6c2; }
.cm-inline-slider-fine { display: none; position: absolute; left: 100%; margin-left: 6px;
  font-size: 9px; color: #56b6c2; white-space: nowrap; }
.cm-inline-slider:hover, .cm-inline-slider.dragging { border-color: #56b6c2; }
.cm-inline-slider:hover .cm-inline-slider-fill,
.cm-inline-slider.dragging .cm-inline-slider-fill { opacity: .32; }
.cm-inline-slider:hover .cm-inline-slider-val,
.cm-inline-slider.dragging .cm-inline-slider-val { color: #d7dce4; }
.cm-inline-slider.fine .cm-inline-slider-fine { display: inline; }
.ins-srow.has-inl { grid-template-columns: 36px minmax(0, 1fr) 38px 12px; }
.ins-inl { color: #3a4868; cursor: pointer; font-size: 11px; text-align: center; }
.ins-inl.on { color: #56b6c2; }
```

### 8. Inspector — `app/src/repulse/ui/inspector.cljs`

- `slider-html` takes optional `:inline` (`nil` | `{:on? bool :pos n}`). With it, the row gets
  class `has-inl` and a trailing
  `<span class="ins-inl[ on]" data-act="inline" data-param="<param>" data-pos="<pos>" title="…">≋</span>`.
  Titles (from the design): on → `shown as inline slider in code — click to unwrap`,
  off → `wrap in (slider …) in code  ⌘K S`.
- `track-block`: for each slider row `[k v cfg]`, with the editor doc,
  `(patcher/find-param-num doc (:from entry) (:to entry) (name k))` → `nil` (no toggle) or
  `{:from …}` → `{:on? (some? (:slider r)) :pos (:from r)}`.
- `on-click`: case `"inline"` → `(inline-slider/toggle-at! @editor/editor-view pos)`, then
  `schedule-render!`.

### 9. Built-in surface (Rule 5) and docs

1. `repulse-lisp.grammar`: `"quote" | "tween" | "slider" |`. Then `npm run gen:grammar`.
2. `completions.js` (Per-event parameters block, after `tween`):
   `{ label: "slider", type: "function", detail: "(slider value min max [:log|:lin]) — a number drawn as a draggable slider in the editor; ⌘K S wraps/unwraps" }`.
3. `hover.js`: `"slider": { signature: "(slider value min max) or (slider value min max :log)", description: "Evaluates to value, clamped to [min, max] (a warning names the clamp). In the editor the form is drawn as a draggable bar: drag to change the number in the code (⇧ = 10× finer), move the cursor onto it to edit the text, ⌘K S / Ctrl-K S to unwrap. Log scale when min > 0 and max/min ≥ 100, or with :log; :lin forces linear.", example: "(->> (seq :e1 :g1) (synth :saw) (amp (slider 0.7 0 1)))" }`.
4. `builtin_meta.edn`: `:slider {:category "util" :returns "number" :side-effects []
   :examples ["(->> (seq :e1 :g1) (synth :saw) (amp (slider 0.7 0 1)))"
   "(fx :filter :freq (slider 800 20 20000))"] :see-also ["amp" "tween" "range"]}`.
   Then `npm run gen:ai-docs`.
5. `docs/USAGE.md`, "The editor": add the key row
   `| **Cmd+K S** / **Ctrl+K S** | Wrap the number under the cursor in (slider …), or unwrap the slider under the cursor |`,
   and a new `### Inline sliders — slider` subsection after "Status head and inspector":
   the form, auto/forced log, the formatting, drag / ⇧-drag / one undo step, cursor-touch
   shows text, the wrap ranges table, the clamp warning, the inspector `≋` (also add `≋` to
   the inspector table's **track** row). `README.md`: one line with `slider` in the
   language reference.
6. At delivery: `ROADMAP.md` + `CLAUDE.md` mark WS3 delivered (Rule 2).

## Files to change

| File | Change |
|---|---|
| `packages/lisp/src/repulse/lisp/builtins/slider.cljs` | **new** — `make-slider-fn`, `make-builtins` |
| `packages/lisp/src/repulse/lisp/eval.cljs` | require `b-slider`, merge into `make-env` |
| `packages/lisp/src/repulse/lisp/eval_test.cljs` | E1–E16 |
| `app/src/repulse/env/builtins.cljs` | `eval-warnings`, `record-eval-warning!`, `"slider"` override in `ensure-env!` |
| `app/src/repulse/eval_orchestrator.cljs` | reset warnings, `slider-warning-diags`, `report-slider-warnings!`, `extra-diags` for `report-unknown-sounds!`, `schedule-slider-eval!`, `inline-slider-patch-and-eval!`, `clamp-to-slider` in the numeric patch fns |
| `app/src/repulse/lisp_patcher.cljs` | `find-slider-forms`, `slider-form-at`, `visible-sliders`, `slider-toggle-edit`; slider-aware `find-param-num`, `scan-for-named-param`, `find-fx-pos-param-num`, `fx-param-edit` |
| `app/src/repulse/lisp_patcher_test.cljs` | P1–P12 |
| `app/src/repulse/ui/slider_model.cljs` | `parse-num-literal`, `inline-cfg`, `inline-t`, `inline-value`, `inline-fmt`, `INLINE-DRAG-PX`, `inline-drag-t`, `inline-range-text`, `WRAP-RANGES`, `wrap-range` |
| `app/src/repulse/ui/slider_model_test.cljs` | M1–M9 |
| `app/src/repulse/ui/inline_slider.cljs` | **new** — field, `SliderWidget`, drag, `toggle-at!`, `extension` |
| `app/src/repulse/ui/inspector.cljs` | require `repulse.ui.inline-slider` + `repulse.lisp-patcher`; `≋` toggle (`:inline` opt, `data-act="inline"`) |
| `app/src/repulse/app.cljs` | add `inline-slider/extension` |
| `app/public/css/main.css` | `.cm-inline-slider*`, `.ins-srow.has-inl`, `.ins-inl` |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | `"slider"` in `BuiltinName` |
| `app/src/repulse/lisp-lang/parser.js`, `parser.terms.js` | regenerated (`npm run gen:grammar`) |
| `app/src/repulse/lisp-lang/completions.js` | `slider` entry |
| `app/src/repulse/lisp-lang/hover.js` | `slider` entry |
| `app/src/repulse/content/builtin_meta.edn` | `:slider` entry |
| `docs/ai/builtins.json` | regenerated (`npm run gen:ai-docs`) |
| `e2e/inline-slider.spec.ts` | **new** — Playwright tests below |
| `docs/USAGE.md`, `README.md` | `slider`, ⌘K S, `≋` |
| `ROADMAP.md`, `CLAUDE.md` | mark WS3 delivered (Rule 2) |

`packages/core/src/repulse/test_runner.cljs` needs no change (all three test namespaces are
already registered).

## Definition of done

### Reference test vectors

**Lisp evaluator** (`eval_test.cljs`). `env` = `(make-test-env)` (no hook). `envw` =
`(assoc (make-test-env) "slider" (b-slider/make-slider-fn #(swap! warns conj %)))` with
`warns` = `(atom [])`. `R` = `(:result (lisp/eval-string s env))`.

| # | Input | Expected |
|---|---|---|
| E1 | `(slider 0.7 0 1)` | `0.7` |
| E2 | `(slider 2 0 1)` in `envw` | `1`. `@warns` = `[{:message "slider value 2 is outside [0, 1] — clamped to 1" :from 8 :to 9}]` |
| E3 | `(slider -0.5 0 1)` in `envw` | `0`. message `"slider value -0.5 is outside [0, 1] — clamped to 0"` |
| E4 | `(slider 2 0 1)` in `env` | `1` (no hook, no throw) |
| E5 | `(slider 0 0 1)` / `(slider 1 0 1)` in `envw` | `0` / `1`. `@warns` = `[]` |
| E6 | `(slider 800 20 20000)` / `… :lin)` / `… :log)` | `800` each |
| E7 | `(slider 1/4 0 1)` | `0.25` |
| E8 | `(do (def v 0.5) (slider v 0 1))` | `0.5` |
| E9 | `(slider 0.7 0)` / `(slider)` / `(slider 1 0 1 :log 2)` | EvalError `"slider expects 3 or 4 arguments: (slider value min max) or (slider value min max :log) — got 2"` / `… — got 0` / `… — got 5`. `:source` of the first = `{:from 1 :to 7}` |
| E10 | `(slider :a 0 1)` | `"slider value must be a number — got :a"`, `:source` `{:from 8 :to 10}` |
| E11 | `(slider 0.5 "0" 1)` | `"slider min must be a number — got \"0\""` (the text shows `got "0"`), `:source` `{:from 12 :to 15}` |
| E12 | `(slider 0.5 0 (seq 1 2))` | `"slider max must be a number — got a pattern"` |
| E13 | `(slider 0.5 1 0)` / `(slider 0.5 1 1)` | `"slider min must be less than max — got min 1, max 0"` / `… got min 1, max 1`. `:source` `{:from 1 :to 7}` |
| E14 | `(slider 0.5 0 1 :exp)` | `"slider scale must be :log or :lin — got :exp"`, `:source` `{:from 16 :to 20}` |
| E15 | `(slider 0.5 0 1 :log)` | `"slider :log needs min > 0 — got min 0"`, `:source` `{:from 16 :to 20}` |
| E16 | `(core/query R one-cycle)` for `(amp (slider 0.7 0 1) (seq :c4 :e4))` vs for `(amp 0.7 (seq :c4 :e4))` | equal event lists (`=`), 2 events, each `(:amp (:value ev))` = `0.7` |

**Slider model** (`slider_model_test.cljs`). `c01` = `(slm/inline-cfg F01)` where `F01` =
the form map for texts `"0.7" "0" "1"`; `clog` = for `"632" "20" "20000"`. Build test
forms with a helper `(fm v lo hi & [mode])` → `{:value {:text v} :min {:text lo} :max {:text hi} :mode (when mode {:text mode})}`.

| # | Call | Expected |
|---|---|---|
| M1 | `parse-num-literal` on `"0.7"` `"-1"` `"1/4"` `"20000"` `"-0.5"` / `"120bpm"` `":a"` `"1e3"` `".5"` `"1/0"` | `0.7 -1 0.25 20000 -0.5` / `nil` ×5 |
| M2 | `(inline-cfg (fm "0.7" "0" "1"))` | `{:v 0.7 :min 0 :max 1 :log? false :min-text "0" :max-text "1"}` |
| M3 | `:log?` of `inline-cfg` for `(fm "632" "20" "20000")`, `(fm "5" "1" "100")`, `(fm "5" "1" "99")`, `(fm "800" "20" "20000" ":lin")`, `(fm "5" "1" "10" ":log")`, `(fm "5" "0" "20000")` | `true true false false true false` |
| M4 | `inline-cfg` of `(fm "0.5" "0" "1" ":log")`, `(fm "0.5" "1" "0")`, `(fm "0.5" "1" "1")`, `(fm "0.5" "0" "1" ":exp")`, `(fm "x" "0" "1")` | `nil` ×5 |
| M5 | `(inline-t c01 0.7)`, `(inline-t c01 2)`, `(inline-t clog 20)`, `(inline-t clog 20000)`, `(inline-t clog 632.4555320336759)` | `0.7`, `1`, `0`, `1`, `0.5` (±1e-9) |
| M6 | `(inline-value clog 0.5)`, `(inline-value c01 0.25)`, `(inline-value (inline-cfg (fm "0" "-1" "1")) 0.5)` | `632.4555…` (±1e-6), `0.25`, `0` |
| M7 | `inline-fmt`: `c01` 0.7 → `"0.70"`; `c01` 1 → `"1.00"`; `(-1,1)` -0.5 → `"-0.50"`; `(40,300)` 120.4 → `"120"`; `(0,100)` 99.6 → `"100"`; `clog` 1261.0057830603323 → `"1261"`; `(fm "0.5" "0.001" "1")` 0.002 → `"0.001"` | as listed |
| M8 | drag end to end: `(inline-fmt c01 (inline-value c01 (inline-drag-t (inline-t c01 0.7) 14)))` → `"0.80"`; with `acc` 1.4 (⇧) → `"0.71"`; `(inline-drag-t 0.95 14)` → `1`; `(inline-drag-t 0.05 -14)` → `0`; `clog` from 632, 14 px → `"1261"` | as listed |
| M9 | `inline-range-text`: `c01` → `"0–1"`; `(-1,1)` → `"-1–1"`; `clog` → `"20–20k · log"`; `(0,1000)` → `"0–1k"`; `(40,300)` → `"40–300"`; `(fm "5" "1" "10" ":log")` → `"1–10 · log"`. `wrap-range`: `"amp" 0.7` → `[0 1]`; `"decay" 0.25` → `[0 2]`; `"freq" 800` → `[20 20000]`; `"bpm" 120` → `[40 300]`; `"pan" -0.3` → `[-1 1]`; `"amp" 1.5` → `[0 1.5]`; `"fast" 2` → `[0 2]`; `nil 0.5` → `[0 1]` | as listed |

**Patcher** (`lisp_patcher_test.cljs`). `T` (151 chars) = the Playwright buffer:

```lisp
(track :bass (->> (seq :e1 :g1) (synth :saw) (amp (slider 0.7 0 1))))
(track :hats (->> (seq :hh :hh) (decay 0.25)))
(def cutoff (slider 632 20 20000))
```

Offsets in `T`: line 1 [0, 69), line 2 [70, 116), line 3 [117, 151). Bass slider form
[50, 66): `0.7` [58, 61), `0` [62, 63), `1` [64, 65). `(decay 0.25)` [102, 114), `0.25`
[109, 113). Cutoff slider [129, 150): `632` [137, 140), `20` [141, 143), `20000` [144, 149).

| # | Call | Expected |
|---|---|---|
| P1 | `(find-slider-forms T)` | `[{:from 50 :to 66 :value {:from 58 :to 61 :text "0.7"} :min {:from 62 :to 63 :text "0"} :max {:from 64 :to 65 :text "1"} :mode nil} {:from 129 :to 150 :value {:from 137 :to 140 :text "632"} :min {:from 141 :to 143 :text "20"} :max {:from 144 :to 149 :text "20000"} :mode nil}]` |
| P2 | `(find-slider-forms "; (slider 0.5 0 1)\n(seq \"(slider 1 0 2)\")")`, `(find-slider-forms "(slider x 0 1)")`, `(find-slider-forms "(slider 0.5 0)")`, `(find-slider-forms "(slider 0.5 0 1 :log 2)")` | `[]` ×4 |
| P3 | `(:mode (first (find-slider-forms "(slider 0.5 0 1 :log)")))` | `{:from 16 :to 20 :text ":log"}` |
| P4 | `(slider-form-at T 50)` / `(slider-form-at T 51)` / `(:from (slider-form-at T 129))` | first P1 entry / `nil` / `129` |
| P5 | `(mapv (comp :from :form) (visible-sliders T S))` for `S` = `[[0 0]]`, `[[59 59]]`, `[[50 50]]`, `[[49 49]]`, `[[66 66]]`, `[[67 67]]`, `[[0 151]]` | `[50 129]`, `[129]`, `[129]`, `[50 129]`, `[129]`, `[50 129]`, `[]` |
| P6 | `(visible-sliders "(slider 0.5\n 0 1)" [[0 0]])`, `(visible-sliders "(slider 0.5 1 0)" [[0 0]])` | `[]`, `[]` (multi-line; invalid range) |
| P7 | `(slider-toggle-edit "(decay 0.25)" 9)` | `{:from 7 :to 11 :insert "(slider 0.25 0 2)" :cursor 19}` |
| P8 | `(slider-toggle-edit "(decay (slider 0.25 0 2))" 10)`, same at `24` | `{:from 7 :to 24 :insert "0.25" :cursor 11}` both |
| P9 | `(slider-toggle-edit "(fx :filter :freq 800)" 19)`, `(slider-toggle-edit "(fx :filter 800)" 13)`, `(slider-toggle-edit "(amp 1.5 (seq :c4))" 6)`, `(slider-toggle-edit "(fast 2 (seq :bd))" 6)` | `{:from 18 :to 21 :insert "(slider 800 20 20000)" :cursor 29}`, `{:from 12 :to 15 :insert "(slider 800 20 20000)" :cursor 23}`, `{:from 5 :to 8 :insert "(slider 1.5 0 1.5)" :cursor 16}`, `{:from 6 :to 7 :insert "(slider 2 0 2)" :cursor 15}` |
| P10 | `(slider-toggle-edit "(seq :bd)" 3)`, `(slider-toggle-edit "; (decay 0.25)" 10)`, `(slider-toggle-edit "(bpm 120bpm)" 7)` | `nil` ×3 |
| P11 | `(find-param-num T 0 69 "amp")`, `(find-param-num T 70 116 "decay")` | `{:from 58 :to 61 :slider {:from 50 :to 66 :min 0 :max 1}}`, `{:from 109 :to 113}` |
| P12 | `(fx-param-edit "(fx :filter :freq (slider 800 20 20000))" "filter" "freq" "900" false nil)`, `(fx-param-edit "(fx :reverb (slider 0.3 0 1))" "reverb" "wet" "0.45" true nil)` | `{:from 26 :to 29 :insert "900" :slider {:from 18 :to 39 :min 20 :max 20000}}`, `{:from 20 :to 23 :insert "0.45" :slider {:from 12 :to 28 :min 0 :max 1}}`. All existing `fx-param-edit-test` vectors still pass unchanged |

### Checklist

- [ ] `(slider 0.7 0 1)` evaluates to 0.7. `(amp (slider 0.7 0 1) …)` gives the same
      events as `(amp 0.7 …)` (harness `evalToEvents`; Playwright on `/test-harness.html`:
      `evalToEvents('(->> (seq :c4 :e4) (amp (slider 0.7 0 1)))')` deep-equals
      `evalToEvents('(->> (seq :c4 :e4) (amp 0.7))')`, has 2 events, and every
      `value.amp` is `0.7`). E1–E8, E16 pass.
- [ ] Errors: wrong arity, non-numbers, min ≥ max, an invalid 4th arg and `:log` with
      min ≤ 0 → eval errors with the exact texts from the table (E9–E15). Playwright:
      buffer `(slider 0.5 1 0)` + Alt+Enter → `#output` text is
      `Error: slider min must be less than max — got min 1, max 0`, and there is no
      `.cm-inline-slider` in `#editor-container`.
- [ ] Clamp warning (Playwright): buffer `(track :a (->> (seq :c4) (amp (slider 2 0 1))))`
      + Alt+Enter → `#output` contains
      `Warning: slider value 2 is outside [0, 1] — clamped to 1`.
      `__REPULSE_APP__.trackParam('a','amp')` polls to `1` within 1 s. At least one
      `.cm-lint-marker-warning` is in the gutter. The widget label reads `1.00`.
- [ ] All M and P vectors pass. The existing `slider_model_test` and
      `lisp_patcher_test` tests pass unmodified.
- [ ] Playwright, buffer `T`: the widget renders. `#editor-container .cm-inline-slider` count
      = 2. The first has text `0.70`, a 70 px wide box (±0.5), fill `style.width` `70%`, and
      title `(slider 0.7 0 1) · 0–1 · drag, ⇧ fine`. The second has text `632`, a fill width
      within 49.9–50.1 %, and a title containing `20–20k · log`.
- [ ] Playwright drag: after Alt+Enter, press on the first widget's centre, move +14 px
      (one `mouse.move`), release → the buffer contains `(amp (slider 0.80 0 1))`, and
      `trackParam('bass','amp')` polls to `0.8` within 1 s. Focus the editor,
      `ControlOrMeta+z` once → the buffer equals `T` exactly.
- [ ] Playwright ⇧-drag: hold Shift, press, move +14 px → `.cm-inline-slider-fine`
      (`×0.1`) is visible during the drag. Release → the buffer contains
      `(amp (slider 0.71 0 1))`. That is a 10× smaller change (0.01 vs 0.10) for the same
      mouse distance.
- [ ] Cursor touch → raw text, editable (Playwright, buffer `T`): selection at 59 →
      widget count 1 and `.cm-content` contains `(slider 0.7 0 1)`. Selection at 61, type
      `5` → the buffer contains `(slider 0.75 0 1)`. Selection at 50 → count 1. Selection
      at 49 and at 0 → count 2 again (cursor left).
- [ ] ⌘K S (Playwright, buffer `T`): selection at 111 (inside `0.25`), `ControlOrMeta+k`
      then `s` → line 2 is `(track :hats (->> (seq :hh :hh) (decay (slider 0.25 0 2))))`,
      the cursor is at 121 (end of `0.25` inside the new form), and no `s` was typed. ⌘K S again → line 2 is back to
      `(decay 0.25)`. Wrap once more, then `ControlOrMeta+z` → the buffer equals `T`. After
      a wrap, selection at 0 → widget count 3.
- [ ] Inspector `≋` (Playwright, buffer `T`, Alt+Enter): select `track::bass`. The amp row
      has `#inspector .ins-inl[data-param="amp"]` with class `on`. Select `track::hats`. The
      decay row's `.ins-inl[data-param="decay"]` has no `on`. Click it → line 2 contains
      `(decay (slider 0.25 0 2))`, and after re-render the toggle has class `on`. Click again
      → line 2 contains `(decay 0.25)`.
- [ ] Inspector write-back into a slider (Playwright, buffer `T`, Alt+Enter): select
      `track::bass`, focus `#inspector .ins-slider[data-key="bass|amp"]`, press `ArrowRight`
      → the buffer contains `(amp (slider 0.71 0 1))` (the slider's value literal, not a new
      `(amp …)` elsewhere) and is otherwise equal to `T`.
- [ ] Grammar/completions/builtin_meta/ai-docs with no drift: `slider` is highlighted as a
      built-in, autocompletes and has a hover doc, and `npm run gen:grammar` +
      `npm run gen:ai-docs` leave `git status` clean after the commit. USAGE.md documents
      `slider`, the ⌘K S key row and `≋`. README mentions `slider`.
- [ ] `npm run test:all` + `npx shadow-cljs compile app` pass. Test protocol in the PR.
- [ ] The PR description ends with a `## Test protocol` section (CLAUDE.md Rule 5). It
      covers the new built-in `slider` (highlight, completion, hover, eval of the Goal
      example, by ear: dragging amp changes the loudness), **each** new error/warning text
      from the table (arity, value/min/max non-number, min ≥ max, invalid 4th arg, `:log`
      with min ≤ 0, clamp warning), drag + ⇧-drag + one-step undo, cursor touch → text,
      ⌘K S wrap/unwrap on `(decay 0.25)` and `(fx :filter :freq 800)`, the inspector `≋`,
      and ≥ 2 regressions (WS1 inspector track slider on a plain `(amp 0.8)`, WS2 master fx
      slider write-back).

## What NOT to do

- No MIDI-CC binding of sliders (N1 covers CC mapping).
- No sliders for non-numeric args, no widgets for `(slider …)` forms whose value/min/max
  are not numeric literals, and no multi-line widgets.
- Don't remove or change the inspector/context sliders' ranges or behaviour. The only
  inspector changes are the `≋` toggle and the slider-aware, clamped write-back.
- No new audio hot-swap path for drags (Decision 1), and no `step` argument or other
  options beyond `:log` / `:lin`.
- No widgets in the `<repulse-editor>` embed or the command bar, and no changes to
  `window.__REPULSE_TEST__` / `test_api.cljs`.
- No `≋` on section amp rows or fx rows, and no inline-slider awareness in the outline (WS4+).
