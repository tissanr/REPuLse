# Phase WS2 — `(master …)` Form & Master FX Outline Group

> **Spec status:** full
> **Automation:** 🤖 auto — the form is evaluator + fx-chain behaviour (unit tests), and the
> outline group and inspector block are Playwright-testable.

**Milestone:** M2 — Full songs in code
**Depends on:** WS1 (inspector), SONG3 (outline). Both delivered.

## Goal

Give the global effect chain a home in the code, as in *Outline Panel v2*
(`design/outline-panel-v2/outline-v2.jsx`, `MasterBlock`, the `master` outline group).
A new special form `master` groups the global `(fx …)` forms of a buffer. It behaves
exactly like the same `(fx …)` forms written at top level. Anything else inside it is an
eval error. The outline lists the block as a `master fx N` group after everything else;
selecting it opens all its effects in the inspector, and the inspector sliders rewrite
the literal inside that `(fx …)` form, not a same-named effect elsewhere in the buffer.

```lisp
;; before — global effects are loose top-level forms
(fx :delay :time 0.375 :feedback 0.4)
(fx :reverb 0.3)

;; after — one block, listed in the outline as "master fx 2"
(master
  (fx :delay  :time 0.375 :feedback 0.4)
  (fx :reverb 0.3))
```

The outline shows a `master` group (kind colour `#6f9bd8`, 1 px divider above, children
indented 12 px). Selecting it opens all global fx stacked in the inspector, each with `↗`
to its line. Selecting a child shows that one effect.

## Scope

- New built-in `master` (a special form): evaluates its `(fx …)` children in order as the
  global chain (same result as the same `(fx …)` forms at top level). Anything else inside
  it is an eval error with a clear message.
- Rule 3 / Rule 5 checklist for the new name: grammar `BuiltinName`, `gen:grammar`,
  `completions.js`, `builtin_meta.edn`, `gen:ai-docs`, hover docs.
- `repulse.outline/extract` learns the `:master` kind with `:fx` children.
- Inspector master block (WS1 slider components); slider write-back uses the existing
  `fx-slider-patch-and-eval!` path, which must find params inside a `(master …)` form.
- Top-level `(fx …)` keeps working unchanged.

## Decisions (decided)

The three open questions of the outline came with recommendations. They were adopted as
written. Decisions 4–12 were taken during spec expansion (2026-11-03, auto-phase
pipeline) to fit the existing code.

1. **One `master` per evaluation; top-level `(fx …)` still allowed.** — *decided.*
   A second `(master …)` in the same buffer is an eval error. Every global `(fx …)` call,
   inside or outside `master`, runs in **source order** in one pass, with no deferral.
   So a top-level `(fx …)` after the `master` block overrides the same parameter, and one
   before it is overridden by the block. When an evaluation contains a `master` **and**
   at least one top-level global `(fx …)`, the console gets a one-time hint (once per page
   load) suggesting moving them into `master`. In project mode (SONG4) the evaluation spans
   the main buffer and all its `use`d buffers in one shared environment. A second `master`
   anywhere in that evaluation is also an error, with a message that names the buffer
   holding the first one. This mirrors `(song …)` (`song-transport/register-song!`). The
   global chain is one per evaluation, so two blocks could not both be "the" master.
2. **No master gain in WS2.** — *decided.* `master` holds effects only. It takes no
   options and no keyword arguments.
3. **Outline: master group always last.** — *decided.* After all other rows, including
   the song group and WS1's loose-`fx` group, behind a 1 px divider. It is not shown at
   its source position.
4. **`master` is a special form in the evaluator, with an app hook.** A normal function
   gets its arguments already evaluated. A global `(fx …)` returns `nil`, and so do
   `(bpm …)` and many other forms, so a function could neither tell an `(fx …)` child from
   other children nor name the offending form. `master` therefore goes into the `case` of
   `repulse.lisp.eval/eval-form`, next to `defsynth`. Like `defsynth` with
   `:*register-synth-fn*`, it calls an optional app hook `:*master-fn*` from the env. The
   lisp package only checks syntax: the head symbol `fx`, and that a child does not return
   a pattern. It has no fx or audio knowledge. Without the hook (pure lisp tests, the TEST1
   harness env, the snippet sandbox env) the children are just evaluated in order.
5. **"Second master" detection = per-evaluation app state.** This works like
   `song-transport/pending-song` and `builtins/seen-tracks`. A `defonce` atom `master-run`
   in `repulse.env.builtins.fx` records the source of the first `master` of the current
   evaluation. `eval-orchestrator/evaluate*` resets it next to `seen-tracks`, and so does
   `evaluate-in-context!` (AI preview). The duplicate check runs **before** the second
   block's children are evaluated, so none of its effects are applied.
6. **Children are checked before anything runs.** A non-`(fx …)` child fails the whole
   block before any of its effects are applied. This check comes before the duplicate
   check. A child `(fx …)` whose value is a pattern is a per-track `(fx … pat)`. It is
   rejected after evaluation, which has no side effects for the per-track branch of the
   `fx` builtin.
7. **Return value.** With the hook, `master` returns the string `"=> master: <n> fx"`
   (`n` = number of children). The output line shows it when `master` is the last form.
   The design puts `master` **after** `(song …)`, which used to make the song summary and
   the diagnostics clear (`(when (some? val) …)`) depend on the last form. So `evaluate*`
   remembers the song registered in this run, and when the last value is the master
   result, it reports that song: `song: N sections · …`, as if `(song …)` were last.
   Without the hook, `master` returns `nil`. An empty `(master)` is allowed: no effects,
   `"=> master: 0 fx"`.
8. **`master` does not change the signal order.** The global chain order is the plugin
   registration order in `repulse.fx/chain` (USAGE "Effect chain order"), whatever the
   order of the `(fx …)` forms. Reordering is P2.
9. **Selection ids:** `"master"` (the group) and `"master:<i>"` (index among the
   master's `:fx` children). WS1's `"fx"` / `"fx:<i>"` stay for loose top-level `(fx …)`
   forms. Only the **first** top-level `master` entry is listed and selectable. A second
   one is an eval error anyway.
10. **Inspector header:** group → kind `master`, title `fx` (as in the design,
    `outline-v2.jsx` L260–261), `↗ L<line of (master>`. Child → kind `master fx`, title
    the effect keyword (`:reverb`), `↗ L<line>`. Kind colour `#6f9bd8` for both. The CSS
    class is the kind with spaces replaced by `-` (`ins-kind-master-fx`).
11. **Slider write-back is scoped to the form.** Today `fx-slider-patch-and-eval!`
    patches the **first** `(fx :name …)` in the buffer. That can be a per-track
    `(fx :reverb …)` inside a `track`, or a loose top-level one. The inspector now passes
    the char offset `at` of the selected `(fx …)` form's `(` (the outline entry's
    `:from`). The patcher edits only inside that form. If the text at `at` is no longer
    `(fx :<name>` (stale offset after typing, before the 150 ms re-extract), **nothing is
    written**: the live param still changes through `fx/set-param!`, and the next
    re-extract renders fresh offsets. Calls without `at` keep today's whole-buffer
    behaviour. The same applies to WS1's loose `fx` group, which benefits from the same
    fix.
12. **No folding** of the master group in WS2. The design's `▾` glyph is not rendered,
    the same as WS1's fx group. Non-fx children are not listed in the outline: they are
    eval errors, shown as a diagnostic.

### Error and hint texts (exact)

| Case | Text | Source range |
|---|---|---|
| Non-`(fx …)` child | `master only takes (fx …) forms — got <form>` | the offending child |
| Per-track `(fx … pat)` child | `master only takes global (fx …) forms — <form> is a per-track effect; put it in the track's ->> chain` | that child |
| Second `master`, same buffer (or single-buffer eval) | `Only one (master …) per buffer — move these (fx …) forms into the first (master …)` | the second `(master …)` form |
| Second `master`, other buffer of the project | `Only one (master …) per project — already defined in buffer :<name>` | the second `(master …)` form |
| Console hint (`js/console.info`, once per page load) | `[REPuLse] Hint: this code uses (master …) and top-level (fx …) — consider moving the top-level (fx …) forms into (master …)` | — |

`<form>` is `(pr-str (leval/deep-unwrap child))`. If that is longer than 40 characters,
use its first 39 characters + `…`. `…` is U+2026 everywhere, as in
`"Only one (song …) per buffer"`. The output line shows errors as `Error: <text>`
(`eval-orchestrator/report-eval-error!`).

## Background

### The global `fx` builtin — `app/src/repulse/env/builtins/fx.cljs` (`repulse.env.builtins.fx`)

`make-builtins` returns `{"fx" (fn [& raw-args] …)}`. It deep-unwraps the args.
**Per-track mode** applies when there is more than one arg and the last is a pattern
(`core/pattern?`): it returns the pattern with `{:name :params}` conj'ed onto
`:track-fx`, with no side effects. **Global mode** otherwise: `:off`/`:on` →
`fx/bypass!`, `:remove` → `fx/remove-effect!`, else `fx/set-param!` per named pair, or
`"value"` for a positional first value. It returns `nil`. The private `effect-name` maps
`:dattorro` → `"dattorro-reverb"`.

### The global chain — `app/src/repulse/fx.cljs` (`repulse.fx`)

`chain` is an atom holding a vector of `{:name :plugin :input :output :bypassed? :active?}`
in registration order. `rewire!` connects `masterGain → effects… → master-tap`.
`set-param!` sets `:active? true` on the named entry and calls `.setParam plugin`.
`reset-global-effects!` calls each plugin's `resetParams` and sets all `:active?` and
`:bypassed?` to false. `eval-orchestrator/evaluate*` calls it before every evaluation.
Signal order never depends on the source.

### Evaluator — `packages/lisp/src/repulse/lisp/eval.cljs` (`repulse.lisp.eval`)

`eval-form` dispatches lists on `(str head)` in a `case`: `def`, `defn`, `let`, `fn`,
`if`, `and`, `or`, `quote`, `do`, `->>`, `->`, `quasiquote`, `defmacro`, `defsynth`,
`loop`, `recur`. Everything else is a macro or a function call. `defsynth` reads the
app hook `(:*register-synth-fn* env)`, which `repulse.env.builtins/ensure-env!` assocs into
the env. The reader (`reader.cljs` `read-form`) puts `{:source {:from :to}}` metadata on
lists, vectors, maps and symbols, and wraps primitives in `SourcedVal`. `util/source-of`
reads both. `util/deep-unwrap` strips `SourcedVal`s recursively.
`repulse.lisp.core/eval-string` turns an `ex-info` with `:from`/`:to` into an
`EvalError {:message :source {:from :to}}`. `make-env` creates `:*defs*`, `:*macros*` and
`:*synths*`. `eval_test.cljs` builds envs with `(leval/make-env (fn [] nil) (fn [_] nil))`.

### Per-evaluation state and orchestration — `app/src/repulse/eval_orchestrator.cljs`

`evaluate*` (≈L197) does the following, in order:

1. `(swap! eval-generation inc)`, `builtins/ensure-env!`, `fx/reset-global-effects!`,
   `(reset! builtins/seen-tracks #{})`, `song-transport/reset-pending!`.
2. Evaluates: `project-eval/run-project` (project mode, Alt+Enter) or
   `lisp/eval-string`.
3. On error → `report-eval-error!`. On success it binds `val`, clears diagnostics
   (single-buffer only when `(some? val)`), and calls
   `song-transport/activate-buffer!`. **`activate-buffer!` reads and then resets
   `pending-song`.**
4. Runs a `cond` on `val`: `song/song?` → `"song: N sections · … bars · … s at … BPM ·
   :end :…"`; pattern → legacy `:_`; event map; `nil` → nothing; string → shown as-is;
   else `=> …`.

`evaluate-in-context!` (≈L50, AI preview) calls `song-transport/reset-pending!` around a
silent `eval-string`. `repulse.song-transport` holds `pending-song`, `buffer-of` (set
only during `project-eval/run-project`: `(fn [src] → buffer name)`) and
`register-song!`, which throws `"Only one (song …) per buffer"`. Its `collect-track!`
builds the "already defined in buffer :a" wording for project-wide duplicates.

### Slider write-back — `eval_orchestrator.cljs` (≈L339–520) and `app/src/repulse/lisp_patcher.cljs`

- `fmt-num`: integers print as `"7"`, everything else as `(.toFixed v 2)`.
- `dispatch-replace! [view from to text]` handles the drag session (`drag-origin`, WS1:
  no-history while dragging).
- `patch-fx-param-in-editor! [effect-name param-name new-val]` scans the **whole doc**.
  It tries `patcher/find-fx-named-param-num`, then `find-fx-pos-param-num` if `param-name`
  is `(get sm/FX-PRIMARY-PARAM effect-name)`, else inserts `" :<param> <v>"` before
  `find-fx-form-close`. `fx-slider-patch-and-eval!` = `fx/set-param!` (instant sound) +
  patch + 150 ms debounced `evaluate-project!`. The keyword variants are
  `patch-fx-keyword-param-in-editor!` / `fx-select-patch-and-eval!`.
- `lisp_patcher.cljs` (`repulse.lisp-patcher`) is a paren-aware tokenizer that skips
  comments and strings: `find-param-num`, `find-fx-named-param-num`,
  `find-fx-named-param-keyword`, `find-fx-pos-param-num`, `find-fx-form-close`. Each
  takes `[doc scope-start scope-end …]` and matches the **first** `(fx :<name>` in scope.
  `find-fx-named-param-num` even continues into later same-named forms when the first one
  lacks the param. That is why WS2 must scope to the exact form. There is no
  `lisp_patcher_test.cljs` yet.

### Outline — `app/src/repulse/outline.cljs` (`repulse.outline`)

- `HEADS` = `{"def" :def "defn" :defn "defsynth" :defsynth "track" :track "song" :song
  "fx" :fx}`. `KEYWORD-NAMED` = `#{:track :fx}`.
- `top-level-entry [text list-node]`: `:song` → `{:kind :song :name nil :from :to
  :name-from nil :name-to nil :children [section entries]}`. Every other kind needs a name
  token and gives `{:kind :name :from :to :name-from :name-to :refs :children []}`
  (`collect-refs` = Symbol/BuiltinName texts, minus the head and the name).
- `extract [tree text]` walks only direct `Program` children →
  `{:entries [...] :partial? bool}`. `entry-at` recurses into `:children`. `used-by` skips
  `:song`.
- Tests: `app/src/repulse/outline_test.cljs` parses with
  `["./lisp-lang/parser.js" :refer [parser]]` (Lezer works under the node `:test` build).
  It has reference buffers `R` and `W`.

### Selection, outline panel, inspector (WS1)

- `app/src/repulse/ui/selection.cljs` (`repulse.ui.selection`): `entry-id` (`:fx` →
  `"fx:<i>"`), `index-entries` (adds `"fx"` → `{:kind :fx-group :name "fx" :from :to
  :children fxs}` over **top-level** `:fx` entries), `pick`, `prune`, `ranges` (the
  fx-group contributes each child's range), `primary`, `header` (`KIND-LABEL`). Tests in
  `selection_test.cljs`.
- `app/src/repulse/ui/outline_panel.cljs`: the `kind-label` map; `row-html` (opts
  `:kind :name :meta :line :indent :id :sel? :cursor? …`, class `ol-kind-<kind>`);
  `render-html` loops over the entries with `case (:kind e)`: `:song` (+ sections), `:track`,
  `:fx ""` (skipped), default row. Then, if there are top-level `:fx` entries, it adds
  `<div class="ol-group-divider">` + the `"fx"` group row (`fx  fx  <n>`) + child rows
  (indent 12). `select-row!` and `reveal-selection!` special-case `:fx-group` (scroll to
  / reveal the first child). `re-extract!` resets `entries`, prunes the selection and
  calls `notify-selection!` (→ the inspector repaints).
- `app/src/repulse/ui/inspector.cljs`: `slider-html` / `select-html` (data attrs `key`,
  `track`, `fx`, `param`, `value`); `fx-chain-html [rows owner show-head? jumps]` (owner
  `nil` = global; key `"fx|<name>|<param>"`; `jumps` = the entries' `:from`s used by
  `jump-html`); `fx-block [fx-entries]` (looks up `:active?` chain entries by name, builds
  rows via `inspector-model/fx-rows`, shows `not active yet — Alt+Enter`, foot `applies
  after all tracks`); `body-html` dispatches on `(:kind e)` (`:fx` → `(fx-block [e])`,
  `:fx-group` → `(fx-block (:children e))`); `render-html` emits
  `<span class="ins-kind ins-kind-<kind>">`. `slider-info` / `apply-value!` and
  `on-change` call `eo/fx-slider-patch-and-eval!` / `eo/fx-select-patch-and-eval!` for
  global effects. `render!` only repaints when the HTML string changed, so new
  `data-at` values cause a repaint.
- CSS `app/public/css/main.css`: `.ol-kind-fx { color: #6f9bd8; }` (≈L1652),
  `.ol-group-divider` (≈L1653), `.ins-kind-fx { color: #6f9bd8; }` (≈L1693).
- Design: `design/outline-panel-v2/outline-v2.jsx`. L76–77 parse `(master` and its
  `(fx :name` children. L189–191 render the divider, the `master` row (name `fx`, meta =
  child count) and the children (indent 12). L247–250 is `MasterBlock` (per effect: index +
  name + `Jump` when > 1 effect, its sliders, foot "applies after all tracks").
  L260–267 hold the inspector kind/name for `master` / `mfx`.

### Editor language surface

- `app/src/repulse/lisp-lang/repulse-lisp.grammar`: the `BuiltinName { … }` token list
  (`"fx"` is in the line `"bpm" | "stop" | "fx" | "load-plugin" | …`). Run
  `npm run gen:grammar` → `parser.js`, `parser.terms.js`.
- `app/src/repulse/lisp-lang/completions.js`: `{ label, type, detail }`. Special forms
  use `type: "keyword"` (e.g. `defsynth`), and `fx` is `type: "function"`.
- `app/src/repulse/lisp-lang/hover.js`: the `DOCS` map `name → {signature, description,
  example}` (`"fx"` ≈L957).
- `app/src/repulse/content/builtin_meta.edn`: `:fx` (≈L1076, category `"effect"`).
  Special forms are in the `;; ── Special forms` block (≈L1339).
- `scripts/gen_ai_docs.mjs` (`npm run gen:ai-docs`) merges grammar, completions, meta
  and hover into `docs/ai/builtins.json`. CI's `ai-docs` job runs `npm run check:ai-docs`.

### Tests and harness

- `npm test` = `npx shadow-cljs compile test && node out/test.js`. The runner
  `packages/core/src/repulse/test_runner.cljs` lists every test ns in **both** its
  `:require` and `run-tests`. `app/src/repulse/fx_test.cljs` already builds envs from
  `leval/make-env` + `(b-fx/make-builtins nil)` and installs stub plugins in
  `fx/chain`.
- Playwright (`playwright.config.ts`, port 3100, `e2e/*.spec.ts`). `e2e/workspace.spec.ts`
  drives the real app page through `window.__REPULSE_EDITOR__` (the `EditorView`) and
  `window.__REPULSE_APP__`, which `app.cljs` ≈L308 sets to `cycleDur`, `muted`,
  `selection`, `trackParam`. Helpers: `load`, `setText`, `doc`, `row(page, id)`,
  `selectedLines`, `focusEditor`, `evalW`. Errors appear in `#output` as `Error: …`
  (`e2e/buffers.spec.ts`). `window.__REPULSE_TEST__` (`app/src/repulse/test_api.cljs`)
  belongs to the offline harness page (`test-harness.html`), which has no outline or
  inspector. WS2 does not touch it.

## Implementation

### 0. Session start

Run `npm run gen:grammar`, `npm run gen:ai-docs` and `npm test` on the unmodified branch
(CLAUDE.md Rule 6). All three must exit cleanly before you write code.

### 1. Evaluator special form — `packages/lisp/src/repulse/lisp/eval.cljs`

Add a private helper and a `case` branch next to `"defsynth"`:

```clojure
(defn- form-str
  "Short printed form for error messages: deep-unwrapped pr-str, max 40 chars."
  [form]
  (let [s (pr-str (deep-unwrap form))]
    (if (> (count s) 40) (str (subs s 0 39) "…") s)))

(defn- fx-form? [child]
  (and (seq? child) (symbol? (first child)) (= "fx" (str (first child)))))

;; inside eval-form's case:
"master"
;; (master (fx …) …) — Phase WS2. Only (fx …) children; they run in order.
;; The app hook :*master-fn* (repulse.env.builtins.fx/run-master!) enforces one
;; master per evaluation and returns the result string; without it → nil.
(let [msrc (source-of form)]
  (doseq [child tail]
    (when-not (fx-form? child)
      (throw (ex-info (str "master only takes (fx …) forms — got " (form-str child))
                      (merge {:type :eval-error}
                             (select-keys (or (source-of child) msrc) [:from :to]))))))
  (let [run (fn []
              (doseq [child tail]
                (let [r (eval-form child env)]
                  (when (core/pattern? (unwrap r))
                    (throw (ex-info (str "master only takes global (fx …) forms — "
                                         (form-str child)
                                         " is a per-track effect; put it in the track's ->> chain")
                                    (merge {:type :eval-error}
                                           (select-keys (source-of child) [:from :to]))))))))]
    (if-let [mf (:*master-fn* env)]
      (mf msrc (count tail) run)
      (do (run) nil))))
```

`form` is the list being evaluated. `eval-form` destructures `[head & tail]` from it.
`source-of form` reads its metadata. Errors thrown by an `(fx …)` call inside `run`
already carry a range (function-call path) and pass through unchanged.

### 2. App hook and per-evaluation state — `app/src/repulse/env/builtins/fx.cljs`

Add `[repulse.song-transport :as st]` to the requires (`tracks.cljs` already requires
it, and `song_transport` doesn't require this ns, so there is no cycle).

```clojure
;; Per-evaluation master state (Phase WS2), reset by eval-orchestrator.
(defonce master-run (atom {:src nil :in? false :loose 0 :ret nil}))
(defonce hinted? (atom false))   ; public so tests can reset it

(def LOOSE-FX-HINT
  "[REPuLse] Hint: this code uses (master …) and top-level (fx …) — consider moving the top-level (fx …) forms into (master …)")

(defn reset-master-run! [] (reset! master-run {:src nil :in? false :loose 0 :ret nil}))

(defn run-master!
  "The :*master-fn* hook: reject a second master of this evaluation, then run
   the children with :in? set. → \"=> master: <n> fx\"."
  [src n run]
  (when-let [prev (:src @master-run)]
    (let [f (or @st/buffer-of (constantly nil))
          a (f prev)
          b (f src)]
      (throw (ex-info (if (and a b (not= a b))
                        (str "Only one (master …) per project — already defined in buffer :" a)
                        "Only one (master …) per buffer — move these (fx …) forms into the first (master …)")
                      (merge {:type :eval-error} (select-keys src [:from :to]))))))
  (swap! master-run assoc :src src :in? true)
  (try (run) (finally (swap! master-run assoc :in? false)))
  (let [ret (str "=> master: " n " fx")]
    (swap! master-run assoc :ret ret)
    ret))

(defn master-result? [v]
  (and (some? (:src @master-run)) (string? v) (= v (:ret @master-run))))

(defn loose-fx-hint!
  "Log LOOSE-FX-HINT once per page load when this evaluation had a master and a
   top-level global fx. → the message when logged, else nil."
  []
  (let [{:keys [src loose]} @master-run]
    (when (and src (pos? loose) (not @hinted?))
      (reset! hinted? true)
      (js/console.info LOOSE-FX-HINT)
      LOOSE-FX-HINT)))
```

In the **global** branch of `"fx"` (the `do` after `(if per-track? …)`), increment
`:loose` first, before the `cond`: `(when-not (:in? @master-run) (swap! master-run update
:loose inc))`. This applies to `:off`/`:on`/`:remove` too. Per-track mode is unchanged.

### 3. Env wiring — `app/src/repulse/env/builtins.cljs`

In `ensure-env!`, assoc the hook next to `:*register-synth-fn*`:
`(assoc … :*register-synth-fn* synth/register-synth! :*master-fn* b-fx/run-master!)`.
`builtin-names` will contain the keyword key, which is harmless: the synth hook already
puts a keyword key there.

### 4. Orchestrator — `app/src/repulse/eval_orchestrator.cljs`

- Require `[repulse.env.builtins.fx :as b-fx]`.
- `evaluate*`: call `(b-fx/reset-master-run!)` right after
  `(reset! builtins/seen-tracks #{})`. In the success branch, **before**
  `song-transport/activate-buffer!`, capture `run-song @song-transport/pending-song`. Then
  rebind `val` as `(if (and run-song (b-fx/master-result? val)) run-song val)`, so that
  `(song …)` followed by `(master …)` prints the song summary (Decision 7). Do this rebind
  before the diagnostics clear and before the `cond`. After the existing reports, call
  `(b-fx/loose-fx-hint!)`.
- `evaluate-in-context!`: call `(b-fx/reset-master-run!)` next to both
  `song-transport/reset-pending!` calls.
- Scoped fx patching (Decision 11). Replace the bodies of `patch-fx-param-in-editor!` and
  `patch-fx-keyword-param-in-editor!` with calls to the new pure patcher functions (step
  5). Then add an optional last argument `at` through the public API:

```clojure
(defn- patch-fx-param-in-editor! [effect-name param-name new-val at]
  (when-let [view @editor/editor-view]
    (let [doc (.. view -state -doc (toString))]
      (when-let [{:keys [from to insert]}
                 (patcher/fx-param-edit doc effect-name param-name (fmt-num new-val)
                                        (= param-name (get sm/FX-PRIMARY-PARAM effect-name)) at)]
        (dispatch-replace! view from to insert)))))

(defn fx-slider-patch-and-eval!
  ([effect-name param-name new-val] (fx-slider-patch-and-eval! effect-name param-name new-val nil))
  ([effect-name param-name new-val at]
   (fx/set-param! effect-name param-name new-val)
   (patch-fx-param-in-editor! effect-name param-name new-val at)
   … unchanged 150 ms debounce …))
```

  `fx-select-patch-and-eval!` gets the same 3/4-arity shape and uses
  `patcher/fx-keyword-param-edit` with `(str ":" new-val)`. The per-track variants are
  unchanged.

### 5. Pure patcher — `app/src/repulse/lisp_patcher.cljs`

Add three public functions. Every position still comes from the existing tokenizer.

- `(fx-form-range doc at effect-name)` → `{:from at :to <index after the form's )>}` when
  the first three significant tokens from `at` are `(`, `fx`, `:<effect-name>`, and the
  form closes (`find-fx-form-close doc at (count doc) effect-name` + 1). Otherwise `nil`,
  including when `at` is `nil`, negative or ≥ the doc length.
- `(fx-param-edit doc effect-name param-name fmtd primary? at)` → `{:from :to :insert}` or
  `nil`. Scope: with `at`, the range from `fx-form-range`, or **`nil` overall when that
  is `nil`**. Without `at`, `[0, (count doc))`. Inside the scope, the same order as today:
  named `:<param> NUMBER` → replace the number; else if `primary?`, the positional number →
  replace; else insert `" :<param> <fmtd>"` at the form's `)` (`find-fx-form-close`).
- `(fx-keyword-param-edit doc effect-name param-name kw-text at)`: same, with
  `find-fx-named-param-keyword` and no positional case.

With `at` = `nil`, both edit functions return exactly what today's
`patch-fx-*-in-editor!` dispatch, so the change is a refactor plus scoping.

### 6. Outline — `app/src/repulse/outline.cljs`

- Add `"master" :master` to `HEADS`.
- In `top-level-entry`, handle `:master` like `:song` (no name token):
  `{:kind :master :name nil :from :to :name-from nil :name-to nil :children [...]}`,
  without `:refs`. `:children` = for every `List` among the body nodes after the head,
  `(top-level-entry text n)`, keeping only results with `:kind :fx`. So they have the
  exact shape of top-level `:fx` entries (incl. `:refs`), and non-fx or unnamed children
  are left out.
- Update the ns docstring (Phase WS2: `:master` with `:fx` children). `entry-at` already
  recurses into `:children`. `used-by` doesn't look into master children (out of scope).

### 7. Selection — `app/src/repulse/ui/selection.cljs`

- `entry-id`: add `:master` → `"master"` and `:master-fx` → `(str "master:" index)`.
- `index-entries`: `m` = the first `:master` entry. When it exists, add
  `"master"` → `(assoc m :name "fx")` and, for each child `i`,
  `(str "master:" i)` → `(assoc child :kind :master-fx)`. Top-level `:fx` handling is
  unchanged.
- `ranges`: unchanged code. A `:master` entry contributes its whole `[from, to)` through
  the default branch.
- `KIND-LABEL`: add `:master "master"`, `:master-fx "master fx"`.
- Update the ns docstring with the new ids.

### 8. Outline panel — `app/src/repulse/ui/outline_panel.cljs`

- `kind-label`: add `:master "master"`.
- In `render-html`'s entry loop, add `:master ""` (rendered in its own group, like `:fx`).
- After the loose `fx` group, when `m` (first `:master` entry) exists, append
  `<div class="ol-group-divider"></div>`, then
  `(row-html {:kind :master :name "fx" :meta (str (count (:children m))) :from (:from m)
  :to (:to m) :line (line (:from m)) :id "master" :sel? … :cursor? (= cur-from (:from m))})`,
  then one row per child: `:name (:name c)`, `:indent 12`,
  `:id (str "master:" i)`, `:line (line (:from c))`, `:cursor?`.
- `select-row!` / `reveal-selection!`: `:master` needs no special case. It scrolls to and
  reveals the `(master` form itself.

### 9. Inspector — `app/src/repulse/ui/inspector.cljs`

- `slider-html` / `select-html`: accept `:at` in the ids map and emit
  `data-at="<at>"` (empty string when nil).
- `fx-chain-html`: pass `:at (nth jumps i nil)` for global rows (`owner` nil) only.
- `slider-info` reads `at` (`js/parseInt`, `nil` when empty). `apply-value!` calls
  `(eo/fx-slider-patch-and-eval! fx param v at)`. `on-change` calls
  `(eo/fx-select-patch-and-eval! fx p v at)` for global selects.
- `body-html`: `:master` → `(fx-block (:children e))`, `:master-fx` → `(fx-block [e])`.
  `fx-block` itself is reused unchanged: names, `↗` per effect when > 1, `not active yet
  — Alt+Enter`, foot `applies after all tracks`.
- `render-html`: kind class `(str "ins-kind ins-kind-" (str/replace kind " " "-"))`.

### 10. CSS — `app/public/css/main.css`

Next to the existing rules: `.ol-kind-master { color: #6f9bd8; }` and
`.ins-kind-master, .ins-kind-master-fx { color: #6f9bd8; }`.

### 11. Test handle — `app/src/repulse/app.cljs`

Add `:fxParam (fn [effect param] …)` to `window.__REPULSE_APP__`. It returns
`(get (js->clj (.getParams plugin)) param)` of the first `:active?` entry of `@fx/chain`
whose `:name` is `effect`, else `nil`.

### 12. Language surface (Rule 5)

1. Grammar: add `"master"` after `"fx"` in `BuiltinName` → `npm run gen:grammar`
   (commit `parser.js` + `parser.terms.js`).
2. `completions.js`: `{ label: "master", type: "keyword", detail: "(master (fx …) …) —
   global effect chain block; only (fx …) forms inside, at most one per buffer" }`.
3. `hover.js` `DOCS`: `"master": { signature: "(master (fx :name …) …)", description:
   "Group the global effect chain in one block. Each (fx …) inside runs exactly like a
   top-level (fx …); anything else is an error. One master per buffer. The signal order
   stays the built-in chain order.", example: "(master\n  (fx :delay :time 0.375
   :feedback 0.4)\n  (fx :reverb 0.3))" }`.
4. `builtin_meta.edn`, after `:fx`: `:master {:category "effect" :returns "nil"
   :side-effects ["audio"] :examples ["(master (fx :reverb 0.3))" "(master (fx :delay
   :time 0.375 :feedback 0.4) (fx :reverb 0.3))"] :see-also ["fx" "track" "song"]}`.
5. `npm run gen:ai-docs` → `docs/ai/builtins.json`. Commit all six files together.

### 13. Tests

Unit tests. Register a new namespace in **both** places in `test_runner.cljs`.

- `packages/lisp/src/repulse/lisp/eval_test.cljs`: the `master` special form without a
  hook (vectors E1–E4).
- `app/src/repulse/fx_test.cljs`: chain equivalence, duplicate, per-track child, hint,
  result string (vectors F1–F7). Reset `fx/chain` and `b-fx/reset-master-run!` per test.
  Build the env as `(assoc (merge (leval/make-env …) (b-fx/make-builtins nil))
  :*master-fn* b-fx/run-master!)`.
- `app/src/repulse/lisp_patcher_test.cljs` (**new**, ns `repulse.lisp-patcher-test`):
  vectors P1–P8.
- `app/src/repulse/outline_test.cljs`: vectors O1–O3. The existing `R`/`W` assertions
  stay.
- `app/src/repulse/ui/selection_test.cljs`: vectors S1–S5.

Playwright: new `e2e/master.spec.ts` with the same `beforeEach` and helpers as
`e2e/workspace.spec.ts` (copy `load`/`setText`/`doc`/`row`/`selectedLines`/`focusEditor`;
`load` waits for `kick` in the outline). Tests are listed in the Definition of done.
`e2e/workspace.spec.ts` must still pass unchanged (its loose `(fx :reverb 0.3)` →
`"fx"` group).

### 14. Docs (Rule 2/4)

- `docs/USAGE.md`: in "## Effect plugins", add `### master — the global chain in one
  block` after "### `fx` — effect control": the syntax, the "same as top-level (fx …)"
  rule, one per buffer, top-level `(fx …)` still works in source order (+ the console
  hint), the per-track `(fx … pat)` error, and the fact that it doesn't change signal order
  (link "Effect chain order"). Also update "Status head and inspector" (≈L417): the outline
  `master` group and the `master` / `master fx` inspector rows in the kinds table. Add
  `master` to any built-in reference table that lists `fx`.
- `README.md`: the Effects bullet (≈L52) gets `(master (fx :delay 0.25) (fx :reverb 0.3))`.
- On delivery: `ROADMAP.md` WS2 → ✅ *delivered* with a **Delivered:** list, and the
  CLAUDE.md row → `✓ delivered` (Rule 2).

## Files to change

| File | Change |
|---|---|
| `packages/lisp/src/repulse/lisp/eval.cljs` | `master` special form, `form-str`, `fx-form?`, `:*master-fn*` hook |
| `packages/lisp/src/repulse/lisp/eval_test.cljs` | E1–E4 |
| `app/src/repulse/env/builtins/fx.cljs` | `master-run`, `reset-master-run!`, `run-master!`, `master-result?`, `loose-fx-hint!`, `LOOSE-FX-HINT`, `:loose` counting in the global branch |
| `app/src/repulse/env/builtins.cljs` | assoc `:*master-fn* b-fx/run-master!` in `ensure-env!` |
| `app/src/repulse/eval_orchestrator.cljs` | reset master state (`evaluate*`, `evaluate-in-context!`), song-after-master output, hint call, `at` arity for `fx-slider-patch-and-eval!` / `fx-select-patch-and-eval!`, patch fns delegate to the patcher |
| `app/src/repulse/lisp_patcher.cljs` | `fx-form-range`, `fx-param-edit`, `fx-keyword-param-edit` |
| `app/src/repulse/lisp_patcher_test.cljs` | **new** — P1–P8 |
| `app/src/repulse/fx_test.cljs` | F1–F7 |
| `app/src/repulse/outline.cljs` | `:master` kind with `:fx` children |
| `app/src/repulse/outline_test.cljs` | O1–O3 |
| `app/src/repulse/ui/selection.cljs` | `"master"` / `"master:<i>"` ids, `:master-fx`, labels |
| `app/src/repulse/ui/selection_test.cljs` | S1–S5 |
| `app/src/repulse/ui/outline_panel.cljs` | master group rows, last, after a divider |
| `app/src/repulse/ui/inspector.cljs` | `:master` / `:master-fx` bodies, `data-at`, kind class without spaces |
| `app/public/css/main.css` | `.ol-kind-master`, `.ins-kind-master`, `.ins-kind-master-fx` |
| `app/src/repulse/app.cljs` | `__REPULSE_APP__.fxParam` |
| `packages/core/src/repulse/test_runner.cljs` | register `repulse.lisp-patcher-test` (both places) |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | `"master"` in `BuiltinName` |
| `app/src/repulse/lisp-lang/parser.js`, `parser.terms.js` | regenerated (`npm run gen:grammar`) |
| `app/src/repulse/lisp-lang/completions.js` | `master` entry |
| `app/src/repulse/lisp-lang/hover.js` | `master` DOCS entry |
| `app/src/repulse/content/builtin_meta.edn` | `:master` entry |
| `docs/ai/builtins.json` | regenerated (`npm run gen:ai-docs`) |
| `e2e/master.spec.ts` | **new** — Playwright tests below |
| `docs/USAGE.md`, `README.md` | `master` docs |
| `ROADMAP.md`, `CLAUDE.md` | mark WS2 delivered (Rule 2) |

## Definition of done

### Reference test vectors

**Lisp evaluator (no hook)**. `env` = `(assoc (make-test-env) "fx" (fn [& args] (swap!
calls conj (mapv leval/deep-unwrap args)) nil))`.

| # | Input | Expected |
|---|---|---|
| E1 | `(master (fx :reverb 0.3) (fx :delay 0.25))` | `{:result nil}`. `@calls` = `[[:reverb 0.3] [:delay 0.25]]` |
| E2 | `(master (seq :bd))` | EvalError, `:message` `"master only takes (fx …) forms — got (seq :bd)"`, `:source` `{:from 8 :to 17}`. `@calls` = `[]` |
| E3 | `(master (fx :reverb 0.3) :reverb)` | message `"master only takes (fx …) forms — got :reverb"`. `@calls` = `[]` (checked before any child runs) |
| E4 | `(master)` | `{:result nil}` |

**Global chain (app, with hook)**. `fx/chain` = two stub entries in this order:
`{:name "reverb" :plugin P-rev :active? false :bypassed? false}` and `{:name "delay"
:plugin P-del …}`. Each plugin is `#js {:setParam (fn [p v] (swap! log conj [<name> p v]))
:resetParams (fn [])}`. Before every input: `(fx/reset-global-effects!)`,
`(b-fx/reset-master-run!)`, `(reset! log [])`.

| # | Input | Expected |
|---|---|---|
| F1 | `(master (fx :reverb 0.3))` vs `(fx :reverb 0.3)` | **Equal** `@fx/chain` after each, `=` `[{:name "reverb" :plugin P-rev :active? true :bypassed? false} {:name "delay" :plugin P-del :active? false :bypassed? false}]`. Equal `@log` = `[["reverb" "value" 0.3]]` |
| F2 | `(master (fx :delay :time 0.375 :feedback 0.4) (fx :reverb 0.3))` vs the two forms at top level | equal chains (both active). `@log` = `[["delay" "time" 0.375] ["delay" "feedback" 0.4] ["reverb" "value" 0.3]]` for both |
| F3 | `(master (fx :reverb 0.3))` | `{:result "=> master: 1 fx"}`. `(b-fx/master-result? "=> master: 1 fx")` → `true` |
| F4 | `"(master (fx :reverb 0.3))\n(master (fx :delay 0.25))"` | message `"Only one (master …) per buffer — move these (fx …) forms into the first (master …)"`, `:source` `{:from 26 :to 51}`. `@log` = `[["reverb" "value" 0.3]]` (the second block's delay never ran) |
| F5 | F4 with `@song-transport/buffer-of` = `(fn [src] (if (< (:from src) 26) "fx" "main"))` | message `"Only one (master …) per project — already defined in buffer :fx"` (reset `buffer-of` to `nil` afterwards) |
| F6 | `(master (fx :reverb 0.3 (seq :bd)))` | message `"master only takes global (fx …) forms — (fx :reverb 0.3 (seq :bd)) is a per-track effect; put it in the track's ->> chain"`, `:source` `{:from 8 :to 34}` |
| F7 | `(fx :delay 0.25)\n(master (fx :reverb 0.3))` evaluated twice, with `(b-fx/loose-fx-hint!)` after each (`(reset! b-fx/hinted? false)` first; stub `js/console.info` with `set!` and restore it) | 1st call → `LOOSE-FX-HINT` (exact text in the table above), 2nd → `nil`. `(master (fx :reverb 0.3))` alone → `nil` (`:loose` is 0) |

**Patcher**. `S` (125 chars):

```lisp
(track :drums (->> (seq :bd :_ :bd :_) (fx :reverb 0.5)))
(master
  (fx :delay :time 0.375 :feedback 0.4)
  (fx :reverb 0.3))
```

Offsets in `S`: track [0, 57), `(master` [58, 125), `(fx :delay …)` [68, 105),
`(fx :reverb 0.3)` [108, 124). The track's `0.5` is [51, 54). Master `0.3` is [120, 123),
`0.375` is [85, 90), `0.4` is [101, 104).

| # | Call | Expected |
|---|---|---|
| P1 | `(fx-form-range S 108 "reverb")` | `{:from 108 :to 124}` |
| P2 | `(fx-form-range S 68 "delay")` / `(fx-form-range S 108 "delay")` / `(fx-form-range S 100 "reverb")` / `(fx-form-range S nil "reverb")` | `{:from 68 :to 105}` / `nil` / `nil` / `nil` |
| P3 | `(fx-param-edit S "reverb" "wet" "0.45" true 108)` | `{:from 120 :to 123 :insert "0.45"}`. Applied, the last line becomes `  (fx :reverb 0.45))` and everything else is unchanged (the track keeps `(fx :reverb 0.5)`) |
| P4 | `(fx-param-edit S "reverb" "wet" "0.45" true nil)` | `{:from 51 :to 54 :insert "0.45"}` (unscoped legacy behaviour hits the track's form, which is why the inspector passes `at`) |
| P5 | `(fx-param-edit S "delay" "feedback" "0.50" false 68)` | `{:from 101 :to 104 :insert "0.50"}` |
| P6 | `(fx-param-edit S "reverb" "room" "0.80" false 108)` | `{:from 123 :to 123 :insert " :room 0.80"}`. Applied → `  (fx :reverb 0.3 :room 0.80))` |
| P7 | `(fx-param-edit S "reverb" "wet" "0.45" true 100)` (stale `at`) | `nil` |
| P8 | `(fx-keyword-param-edit "(master (fx :distort :mode :tanh))" "distort" "mode" ":atan" 8)` | `{:from 27 :to 32 :insert ":atan"}` |

**Outline** (`ex` = `(outline/extract (.parse parser text) text)` as in `outline_test.cljs`):

| # | Input | Expected |
|---|---|---|
| O1 | `(:entries (ex S))` | `[<track :drums [0,57) …> {:kind :master :name nil :from 58 :to 125 :name-from nil :name-to nil :children [{:kind :fx :name ":delay" :from 68 :to 105 :name-from 72 :name-to 78 :refs #{} :children []} {:kind :fx :name ":reverb" :from 108 :to 124 :name-from 112 :name-to 119 :refs #{} :children []}]}]`. The track entry's `:refs` = `#{"->>" "seq" "fx"}` |
| O2 | `"(master (seq :bd) (fx :reverb 0.3))\n(master (fx :delay 0.25))"` → `(mapv (juxt :kind #(mapv :name (:children %))) entries)` | `[[:master [":reverb"]] [:master [":delay"]]]` |
| O3 | `(outline/entry-at (:entries (ex S)) 115)` | the `:reverb` child (`:from 108`) |

**Selection** (`idx` = `(sel/index-entries (:entries (ex S)))`):

| # | Call | Expected |
|---|---|---|
| S1 | `(sort (keys idx))` | `("master" "master:0" "master:1" "track::drums")` |
| S2 | `(sel/ranges ["master"] idx)` / `(sel/ranges ["master:1"] idx)` | `[{:from 58 :to 125}]` / `[{:from 108 :to 124}]` |
| S3 | `(sel/header ["master"] idx)` | `{:kind "master" :title "fx" :from 58}` |
| S4 | `(sel/header ["master:1"] idx)` | `{:kind "master fx" :title ":reverb" :from 108}` |
| S5 | `(keys (sel/index-entries (:entries (ex O2-text))))` with `O2-text` = the O2 input | contains `"master"` and `"master:0"`, not `"master:1"` (only the first master is indexed) |

**Playwright buffer `M`** (`e2e/master.spec.ts`):

```lisp
(def kick (seq :bd :_ :bd :_))
(track :drums (->> kick (amp 0.8) (fx :reverb 0.5)))
(song
  (section :a 4 [:drums]))
(master
  (fx :delay :time 0.375 :feedback 0.4)
  (fx :reverb 0.3))
```

Lines: `(master` L5, `:delay` L6, `:reverb` L7.

### Checklist

- [ ] `(master (fx :reverb 0.3))` and top-level `(fx :reverb 0.3)` produce the same
      fx-chain state (unit test on the chain data: F1, F2).
- [ ] `(master (seq :bd))` → eval error naming `master` and the offending form (E2, E3;
      exact text from the table).
- [ ] A second `master` → eval error (F4, F5), and none of its effects are applied.
- [ ] `(master (fx :reverb 0.3 (seq :bd)))` → the per-track error (F6).
- [ ] Top-level `(fx …)` keeps working unchanged. A buffer with `master` + a top-level
      `(fx …)` evaluates without error and logs `LOOSE-FX-HINT` once per page load (F7).
      All existing `fx_test` / `e2e/workspace.spec.ts` tests pass unmodified.
- [ ] `(song …)` followed by `(master …)` prints the song summary in `#output`
      (Playwright: after Alt+Enter on `M`, `#output` contains `song: 1 sections`).
      `(master (fx :reverb 0.3))` alone prints `=> master: 1 fx`.
- [ ] Outline lists `master fx N` + children (Playwright on `M`): the `data-id` order ends
      with `section:0`, `master`, `master:0`, `master:1`. The element before the `master`
      row is `.ol-group-divider`. The `master` row contains `master`, `fx`, `2`, `L5`.
      Children show `:delay L6` / `:reverb L7`. All O/S vectors pass.
- [ ] Selecting `master` opens the inspector with header `master` / `fx` / `↗ L5`,
      sliders `fx|delay|time`, `fx|delay|feedback` and `fx|reverb|wet`, foot `applies
      after all tracks`, and `selectedLines` = `[5, 6, 7]`. Selecting `master:1` shows
      header `master fx` / `:reverb` / `↗ L7` and only the reverb controls.
- [ ] Inspector sliders inside `master` rewrite the right literal (Playwright): after
      Alt+Enter on `M`, select `master:1`, focus `fx|reverb|wet`, press `ArrowRight` → the
      buffer equals `M` with `(fx :reverb 0.3))` replaced by `(fx :reverb 0.31))`. The
      track's `(fx :reverb 0.5)` is unchanged. `__REPULSE_APP__.fxParam('reverb','wet')`
      polls to `0.31` within 1 s. One Cmd/Ctrl-Z restores `M` exactly. All P vectors pass.
- [ ] Errors in the app (Playwright): `(master (seq :bd))` + Alt+Enter → `#output` =
      `Error: master only takes (fx …) forms — got (seq :bd)`.
      `(master (fx :reverb 0.3))\n(master (fx :delay 0.25))` → `#output` = `Error: Only
      one (master …) per buffer — move these (fx …) forms into the first (master …)`.
- [ ] `master` is highlighted as a built-in, autocompletes, and has a hover doc.
      Grammar, completions, builtin_meta and ai-docs regenerated with no drift
      (`npm run gen:grammar`, `npm run gen:ai-docs` leave `git status` clean after the
      commit). USAGE.md documents `master`, and README's Effects bullet shows it.
- [ ] `npm run test:all` + `npx shadow-cljs compile app` pass. Test protocol in the PR.
- [ ] The PR description ends with a `## Test protocol` section (CLAUDE.md Rule 5). It
      covers the new built-in name `master` (highlight, completion, hover, eval of the Goal
      example) and **each** new error/warning text from the table above: the non-fx child,
      the per-track child, a second master in one buffer, a second master across project
      buffers, and the console hint. It also covers the outline group, the inspector block
      with slider write-back + undo, and ≥ 2 regressions (WS1 loose `fx` group, per-track
      `(fx …)` in a `->>` chain).

## What NOT to do

- No per-track routing changes, no sends/busses, no reordering of the global chain (P2).
  `master` never changes signal order.
- Don't remove or deprecate top-level `(fx …)`, and don't change what it does. The
  console hint is the only new behaviour around it.
- No master gain, master options or keyword arguments on `master` (Decision 2).
- Don't make `master` a normal function or a user-visible macro in `:*macros*`, and don't
  give the lisp package any fx/audio knowledge beyond the head-symbol check and the
  `:*master-fn*` hook.
- No folding of the master group, no `used-by` through master children, no inline
  sliders / `≋` (WS3), no AI rail (WS4).
- Don't touch `window.__REPULSE_TEST__` / `test_api.cljs`, the embed, or the per-track
  `*-patch-and-eval!` functions.
