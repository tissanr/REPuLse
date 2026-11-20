# Phase QA4 — One Source for Builtins, Strict Lint, Shared Helpers

> **Spec status:** full
> **Automation:** 🤖 auto — generated files are checked by a drift check, the env ↔ meta
> link by a unit test and an e2e test, lint by clj-kondo's exit code, and the helper
> refactor by the existing test suites. Nothing here changes sound, so no listen check.

**Milestone:** none — quality track
**Depends on:** none (best after QA1, which touches eval.cljs)

> **Note on QA1:** QA1 moves `levenshtein`/`typo-hint` from `eval.cljs` into
> `packages/lisp/src/repulse/lisp/util.cljs` (re-exported from `eval.cljs`) and adds typo
> hints to theory names in `music.cljs`. If QA1 is delivered, start Part C step 4 from that
> location: the target is still the single `repulse.edit-distance` namespace below, and the
> `util.cljs` copy becomes a re-export or is removed. Line numbers in this spec predate QA1.

## Goal

A built-in name is maintained by hand in five places today. They have drifted: hover docs
miss about a hundred names, the grammar misses the operators, and hover still documents a
`play` that was renamed to `track`. After this phase `builtin_meta.edn` is the only source.
Adding a built-in means editing `builtin_meta.edn` and the implementation, then running
`npm run gen:ai-docs`. Completions, hover docs, grammar highlighting and
`docs/ai/builtins.json` are generated, and CI fails when they drift.

The phase also makes clj-kondo a hard gate (0 warnings), folds duplicated helpers into one
implementation each, and brings the library rule in CLAUDE.md in line with the code.

## Background

Measured on `dev` at 5cbda21. Recount before starting; the numbers move.

### Where builtin names live

| Place | Count | Content |
|---|---|---|
| `app/src/repulse/lisp-lang/repulse-lisp.grammar`, `BuiltinName` token | 375 | names only |
| `app/src/repulse/lisp-lang/completions.js`, `BUILTINS` | 389 | `{label, type, detail}`; `type` is `function` (326), `keyword` (41) or `variable` (22) |
| `app/src/repulse/content/builtin_meta.edn` | 389 | `:category :returns :side-effects :examples :see-also`; keys are keywords, or strings for names like `"~"` |
| `app/src/repulse/lisp-lang/hover.js`, `DOCS` | 284 | `{signature, description, example}` |
| env: `repulse.lisp.eval/make-env`, `repulse.env.builtins/ensure-env!`, special forms in the `case` of `repulse.lisp.eval/eval-form` | — | the real bindings |

- Grammar lacks: `samples! sample-banks + - * / = not= < > <= >= keys vals`.
- Hover lacks 106 completions names, among them `defn defmacro defsynth synth map filter
  reduce legato sustain slice splice chop fill ramp automate alt` and every instrument
  voice (`sax trumpet epiano bell marimba guitar harp koto …`). Hover has one name that is
  in no other list: `play` ("Renamed to track").
- Completions and meta hold the same 389 names.
- `scripts/gen_ai_docs.mjs` reads all four files, fails only when a completions name is
  missing from meta, and writes `docs/ai/builtins.json`
  (`category signature returns side-effects examples see-also detail description`). Its
  hand-rolled EDN reader turns `\n` inside strings into `n` (it drops the backslash).
- `insert-categories.js` imports `BUILTINS` from `completions.js` for detail strings.
- `repulse.ai.knowledge` inlines `docs/ai/builtins.json`. Nothing reads the `.edn` at runtime.
- CI: `check:ai-docs` (`.github/workflows/ci.yml`, job `ai-docs`) diffs only
  `builtins.json`. The `grammar` job in `.github/workflows/lint.yml` diffs `parser.js` and
  `parser.terms.js` after `npm run gen:grammar`.
- The `:test` build cannot load `repulse.env.builtins` (it pulls in `ui.editor` and
  CodeMirror). The full env exists only in the browser.

### Lint

`npx -y clj-kondo@2025.10.23 --parallel --lint packages/ app/src/` reports 8 warnings,
0 errors:

- `app/src/repulse/history_store.cljs:6` — `clojure.string` required, never used
- `app/src/repulse/midi_export.cljs:61` — unused binding `synth`; `:87` — unused binding `pat`
- `app/src/repulse/midi_export_test.cljs:7` — `repulse.core` required, never used
- `packages/core/src/repulse/core.cljs:801` — `mask` shadows `cljs.core/mask`
- `packages/core/src/repulse/core_test.cljs:1254` — `clojure.string/split` without require
- `packages/lisp/src/repulse/lisp/integration_test.cljs:1329` — `repulse.theory/note->midi`
  without require; `:1520` — unused binding `e`

`.github/workflows/lint.yml` downloads the installer from `master`, installs clj-kondo
2024.11.14 and treats exit code 2 (warnings) as success.

### Duplicated helpers

- `xf` (wrap a Lisp fn so SourcedVals never reach core): identical in
  `packages/lisp/src/repulse/lisp/builtins/{pattern,structure,random,pick}.cljs`.
- `step->pat`, `n+p`, `n+f+p`: identical in `builtins/structure.cljs` and `builtins/random.cljs`.
- `numeric-token?`: identical in `builtins/pattern.cljs` and `builtins/music.cljs`.
- Number / `[n d]` rational → JS number, seven times: `core/num-val` and `core/pick-num`
  (`core.cljs`), `theory/deg-val`, `params/num-of` and `params/->num`, `arith/num-val`
  (lisp builtins), `util/->num` (lisp, also re-exported as `leval/->num`). Strict ones
  return nil for non-numbers, lenient ones return the input.
- `shift-event`: private in `core.cljs` (via `map-spans`) and in `song.cljs` (nil-safe
  `shift-span` on `:whole`/`:part`).
- Levenshtein twice: `leval/levenshtein` (atoms, `last` on a vector → O(m·n²)) and private
  `theory/edit-distance`. `leval/typo-hint` accepts any distance ≤ 3, which for short names
  suggests almost anything. Callers: `eval.cljs`, `song_transport.cljs`, `project_eval.cljs`,
  `env/builtins/song.cljs`, `sound_check.cljs` (adds its own `< (count n)` filter);
  `leval/levenshtein` directly in `history.cljs`, `tracks_model.cljs`, `sound_check.cljs`.
- `mini/alt*` (`packages/lisp/src/repulse/lisp/mini.cljs`) does what `core/cat*` does.

### Dead code

- `core/combine` — a wrapper for `app-right`, used only in `core_test.cljs` and `params_test.cljs`.
- `:rest` branch of `mini/compile-ast` — the mini parser never emits a `:rest` node.
- `params/pat?` — a private wrapper for `core/pattern?`.
- `"defmacro"` in `eval-form` builds `macro-fn` with a body identical to `make-closure`.

### Convention

CLAUDE.md says core and lisp may use only `cljs.core` and `cljs.test`, but
`repulse.core.specs` uses `cljs.spec.alpha` and two namespaces use `clojure.string`.

## Decisions

1. **`builtin_meta.edn` is the single source.** Every entry gets four new keys:
   `:type` (`"function"`, `"keyword"` or `"variable"`, from completions), `:detail` (the
   completions one-liner, unchanged), `:signature` and `:doc` (from hover `signature` and
   `description`). The hover example is the first item of `:examples`. All eight keys plus
   `:side-effects` are required, `:examples` has at least one item.
2. **Missing hover content** (106 names) is written from the implementation's docstring,
   the error messages and `docs/USAGE.md`. Describe only behaviour that exists. When a
   source says nothing about a detail, leave the detail out.
3. **`play` is dropped.** It is not bound, so it gets no meta entry and no hover.
4. **Generated outputs** (all written by one script, all committed):
   - `app/src/repulse/lisp-lang/builtins.generated.js` — `export const BUILTINS = [{label,
     type, detail}, …]` and `export const DOCS = {name: {signature, description, example}}`,
     in meta file order, with a `// GENERATED by scripts/gen_builtins.mjs — do not edit` header.
   - The `BuiltinName` token body in the grammar, between `// BEGIN GENERATED BuiltinName`
     and `// END GENERATED BuiltinName` lines. Every meta name goes in, operators included.
   - `docs/ai/builtins.json`, same keys and shape as today.
   - `parser.js` / `parser.terms.js` through `gen:grammar`.
5. **Hand-written logic stays hand-written.** `completions.js` keeps `builtinCompletions`
   and re-exports `BUILTINS`; `hover.js` keeps `wordAt` and `lispHoverTooltip` and imports
   `DOCS`. Neither file contains a name list afterwards.
6. **Scripts.** `scripts/gen_ai_docs.mjs` is renamed to `scripts/gen_builtins.mjs` and
   reads only the `.edn`. `package.json`: `"gen:ai-docs": "node scripts/gen_builtins.mjs &&
   npm run gen:grammar"` (the name stays; CLAUDE.md and CI use it). `check:ai-docs` runs
   `gen:ai-docs`, then `git diff --exit-code` on all six generated files.
7. **Env ↔ meta check in two layers.** A cljs.test checks the lisp layer (`make-env` plus
   special forms) in the `:test` build. A Playwright spec checks the full app env in both
   directions. Both have an explicit, commented exclusion list that starts empty; a name may
   only go on it if it is internal and not meant for users.
8. **Typo-hint threshold scales with the typed name's length** `n`: at most 1 edit for
   `n ≤ 4`, 2 for `n ≤ 8`, 3 otherwise. On ties the last candidate wins, as with today's
   `apply min-key`. `theory`'s chord-suffix hint keeps its fixed limit of 2, and
   `sound_check`, `history` and `tracks_model` keep their own extra filters.
9. **Shared helpers by layer.** Core-level helpers go into `packages/core` (the lisp layer
   may use them; core never requires lisp). Lisp-builtin helpers go into a new
   `repulse.lisp.builtins.common`.
10. **One number coercion:** `core/as-number` — a number as-is, `[n d]` with numeric
    `n`, `d` and `d ≠ 0` → `(/ n d)`, else nil. Lenient callers use `(or (core/as-number x) x)`.
    The only change is for zero-denominator pairs (`1/0`): `util/->num` and `deg-val`
    used to return ±Infinity and now return the pair / nil. Covered by a test, noted in the PR.
11. **Library rule:** core and lisp may use `cljs.core`, `cljs.test`, and the
    ClojureScript-bundled `clojure.string`, `clojure.set`, `cljs.reader` and
    `cljs.spec.alpha`. Still no third-party libraries.

## Implementation

### Part A — Builtin single source

1. **Migrate the data.** Write a throwaway Node script (scratch directory, not committed)
   that inserts `:type :detail :signature :doc` into each `.edn` entry textually, keeping
   the existing layout and comments. Source: `completions.js` (`type`, `detail`) and
   `hover.js` (`signature`, `description`). For the 106 names without hover, write
   `:signature` and `:doc` by hand (Decision 2). Commit the result, not the script.
2. **`scripts/gen_builtins.mjs`.** Start from `gen_ai_docs.mjs`; delete the grammar,
   completions and hover readers. Fix the EDN string reader: `\n`, `\t`, `\"` and `\\`
   become newline, tab, quote and backslash. Validate before writing anything, and exit 1
   with one line per problem:
   - every entry has the required keys from Decision 1, `:examples` is non-empty, `:type` is
     one of the three values;
   - every `:see-also` name is itself a meta key;
   - no name occurs twice (keyword and string form count as the same name).
   Then write the outputs of Decision 4. The grammar region holds one `"name" |` per
   name, joined like the current block; escape `\` and `"` in names. If the markers are
   missing, exit 1 with `grammar: BuiltinName markers not found`.
3. **Grammar.** Put the two marker comments (`//` comments, which Lezer grammars accept)
   around the current `BuiltinName` body, then let the generator fill it.
4. **`completions.js` / `hover.js`.** Replace the literals with imports from
   `./builtins.generated.js` (Decision 5). `insert-categories.js` keeps importing
   `BUILTINS` from `./completions.js`.
5. **`package.json` and CI.** Scripts as in Decision 6. `.github/workflows/ci.yml` job
   `ai-docs` keeps `npm run check:ai-docs`; it now also catches grammar and parser drift.
   The diff list: `docs/ai/builtins.json`,
   `app/src/repulse/lisp-lang/builtins.generated.js`,
   `app/src/repulse/lisp-lang/repulse-lisp.grammar`,
   `app/src/repulse/lisp-lang/parser.js`, `app/src/repulse/lisp-lang/parser.terms.js`.
6. **Special forms list.** In `repulse.lisp.eval` add a public
   `(def special-forms #{…})` with exactly the strings of the `case` in `eval-form`
   (today: `def defn let fn lambda if and or quote do ->> -> quasiquote defmacro master
   defsynth loop recur`). Add a comment on the `case`: a new branch needs an entry here
   and in `builtin_meta.edn`.
7. **cljs.test `app/src/repulse/content/builtin_meta_test.cljs`**
   (`repulse.content.builtin-meta-test`). Load the file with
   `(shadow.resource/inline "repulse/content/builtin_meta.edn")` and `cljs.reader/read-string`,
   normalise keys with `name`. Assert that every string key of
   `(leval/make-env (fn []) (fn [_]))` and every `leval/special-forms` entry has a meta
   entry, minus `lisp-excluded` (a commented `#{}`). Register the namespace in
   `packages/core/src/repulse/test_runner.cljs` (require and `run-tests`).
8. **Full env, both directions — e2e.** In `app.cljs` add `:builtinNames` to
   `window.__REPULSE_APP__`: sorted JS array of the string keys of
   `@repulse.env.builtins/builtin-names` united with `leval/special-forms`. New
   `e2e/builtins-meta.spec.ts`: load the app, call it, read the keys of
   `docs/ai/builtins.json` with `fs`, and assert env − meta = ∅ and meta − env = ∅,
   each minus a commented exclusion list that starts empty.
9. **Highlighting side effects.** Putting `-`, `+`, `/` etc. into `BuiltinName` changes only
   highlighting (the evaluator uses its own reader). `outline.cljs`, `ui/goto.cljs`,
   `defs-completion.js` and `hover.js` already accept both `Symbol` and `BuiltinName`. Add
   a Lezer parse test to `e2e/editor.spec.ts` or the generator: `(samples! x)`,
   `(+ 1 2)`, `(not= a b)`, `(keys m)` → the head is `BuiltinName`. A name that begins with
   a builtin (for example `-1` → `-` + `1`) splits the same way `sinewave` already does:
   known, not a blocker. Record it under *Known issues* in the PR protocol.
10. **Docs.** Replace CLAUDE.md Rule 3 with: (1) add the entry to `builtin_meta.edn` with
    all required keys, (2) run `npm run gen:ai-docs`, (3) commit the `.edn` with the
    generated files; never edit `builtins.generated.js`, the marked grammar region or
    `builtins.json` by hand. Update the comment at the top of `e2e/editor.spec.ts`
    (hover now reads generated data from `builtin_meta.edn`).

### Part B — Lint gate

1. Fix the 8 warnings without changing behaviour: drop the unused requires; drop `synth`
   from the destructuring at `midi_export.cljs:61`; rename the unused `pat` parameter at
   `:87` to `_pat` (keep the arity); add `mask` to `(:refer-clojure :exclude [...])` in
   `core.cljs`; require `[clojure.string :as str]` in `core_test.cljs` and use it; require
   `[repulse.theory :as theory]` in `integration_test.cljs` and use the alias; rename the
   unused `e` at `:1520` to `_`. Rerun clj-kondo; fix any warning that is new since this spec.
2. `.github/workflows/lint.yml`: download the installer from
   `https://raw.githubusercontent.com/clj-kondo/clj-kondo/v2025.10.23/script/install-clj-kondo`,
   install `--version 2025.10.23`, and run
   `clj-kondo --parallel --fail-level warning --lint packages/ app/src/` with no exit-code
   handler. Rename the step to `Run clj-kondo (fail on warnings)`.
3. Do not relax `.clj-kondo/config.edn` to get to 0.

### Part C — Shared helpers (no behaviour change except Decisions 8 and 10)

1. **`repulse.lisp.builtins.common`** (new, `packages/lisp/src/repulse/lisp/builtins/common.cljs`,
   requires `repulse.core` and `repulse.lisp.util` only): public `xf`, `step->pat`, `n+p`,
   `n+f+p`, `numeric-token?`, moved verbatim with their docstrings. Delete the copies in
   `pattern`, `structure`, `random`, `pick` and `music`, and refer to `common`.
2. **`core/as-number`** (public, `core.cljs`, Decision 10). Replace `core/num-val`,
   `core/pick-num`, `theory/deg-val`, `params/num-of`, `params/->num`, `arith/num-val`, and
   the body of `util/->num` (unwrap, then `(or (core/as-number v) v)`). `leval/->num` stays
   a re-export.
3. **`core/shift-event`** (public, nil-safe: an event with nil `:whole` keeps nil). Delete
   `shift-event`/`shift-span` in `song.cljs` if nothing else uses them.
4. **Levenshtein** — new `packages/core/src/repulse/edit_distance.cljs`
   (`repulse.edit-distance`): `levenshtein` with two reused rows of numbers
   (`js/Int32Array` or transients), O(m·n) time, O(n) memory; `max-typo-distance` (Decision 8);
   `typo-hint [name known]`. `leval/levenshtein` and `leval/typo-hint` become `def`
   re-exports, so the app callers stay unchanged. `theory/chord-symbol-error` uses
   `edit-distance/levenshtein`; delete `theory/edit-distance`.
5. **`mini/alt*`** — delete it; the `:alt` branch calls `core/cat*`. Check that
   `mini_test.cljs` and the `<…>` cases in `integration_test.cljs` pass unchanged.
6. **Dead code** — delete `core/combine` and move its tests to `core/app-right`; delete the
   `:rest` branch in `compile-ast`; replace `params/pat?` with `core/pattern?`;
   `"defmacro"` stores `(make-closure params body env)`.
7. **Tests** (core rule: every core function has a test): `as-number` (number, `[1 4]`,
   `[1 0]` → nil, keyword → nil), `shift-event` (with and without `:whole`),
   `levenshtein` (`"" "abc"` → 3, `"kitten" "sitting"` → 3, symmetric), `max-typo-distance`
   (4 → 1, 5 → 2, 8 → 2, 9 → 3), `typo-hint` against `["seq" "stack" "rev"]`: `"sek"` →
   `"seq"`; `"sqe"` → nil (2 edits, 3 letters); `"stakc"` → `"stack"`; and against
   `["sew" "seq"]`, `"sek"` → `"seq"` (last of equal candidates wins). New file
   `packages/core/src/repulse/edit_distance_test.cljs`, registered in `test_runner.cljs`.
   If an existing test expects a hint the new threshold rejects, update the expectation and
   list it in the PR.

### Part D — Convention

CLAUDE.md, *Coding conventions*: replace the library line with Decision 11.

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/content/builtin_meta.edn` | `:type :detail :signature :doc` on every entry; 106 new docs |
| `scripts/gen_ai_docs.mjs` → `scripts/gen_builtins.mjs` | edn-only source, validation, all generated outputs, escape fix |
| `app/src/repulse/lisp-lang/builtins.generated.js` | new, generated |
| `app/src/repulse/lisp-lang/completions.js`, `hover.js` | import generated data; no name lists |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar`, `parser.js`, `parser.terms.js` | marked generated region; regenerated |
| `docs/ai/builtins.json` | regenerated |
| `package.json` | `gen:ai-docs`, `check:ai-docs` |
| `.github/workflows/lint.yml` | pinned installer, 2025.10.23, fail on warnings |
| `packages/lisp/src/repulse/lisp/eval.cljs` | `special-forms`; Levenshtein/typo-hint re-exports; `defmacro` via `make-closure` |
| `app/src/repulse/content/builtin_meta_test.cljs` | new: lisp env ↔ meta |
| `app/src/repulse/app.cljs` | `:builtinNames` on `__REPULSE_APP__` |
| `e2e/builtins-meta.spec.ts` | new: full env ↔ meta |
| `e2e/editor.spec.ts` | hover cases below; header comment |
| `packages/lisp/src/repulse/lisp/builtins/common.cljs` | new shared helpers |
| `packages/lisp/src/repulse/lisp/builtins/{pattern,structure,random,pick,music,arith}.cljs` | use `common` / `core/as-number` |
| `packages/lisp/src/repulse/lisp/{util,mini}.cljs` | `->num` via `as-number`; `alt*` and `:rest` removed |
| `packages/core/src/repulse/{core,theory,params,song}.cljs` | `as-number`, `shift-event`, `mask` exclude, dead code |
| `packages/core/src/repulse/edit_distance.cljs` + `_test.cljs` | new |
| `packages/core/src/repulse/{core_test,params_test}.cljs` | `combine` → `app-right`, new tests, require fix |
| lint sites in `history_store`, `midi_export`, `midi_export_test`, `integration_test` | Part B.1 |
| `packages/core/src/repulse/test_runner.cljs` | register the two new test namespaces |
| `CLAUDE.md` | Rule 3; library rule |
| `ROADMAP.md` | mark delivered |

## Definition of done

- [ ] `builtin_meta.edn` is the only file with a hand-written builtin name list:
      `completions.js`, `hover.js` and the grammar outside the marked region have none.
- [ ] Adding a throwaway entry `:zz-test` to the `.edn` only, then
      `npm run gen:ai-docs`, updates completions, hover, grammar, parser and
      `builtins.json` (try it, then revert).
- [ ] `npm run gen:ai-docs` exits 1 with a clear line for a missing `:doc`, an empty
      `:examples` and an unknown `:see-also` name (try each, then revert).
- [ ] `npm run check:ai-docs` passes on a clean tree and fails after a hand edit of
      `builtins.generated.js` or of the grammar region.
- [ ] Playwright: hovering `defn`, `legato` and `slice` shows a `.repulse-hover-sig`
      tooltip with the `:signature` from the `.edn`; `master` and `slider` still pass E2.
- [ ] Playwright: `play` has no hover tooltip; `samples!`, `+` and `not=` parse as
      `BuiltinName`.
- [ ] `e2e/builtins-meta.spec.ts` passes in both directions; `builtin_meta_test` passes.
- [ ] `npx -y clj-kondo@2025.10.23 --parallel --fail-level warning --lint packages/ app/src/`
      exits 0 with 0 warnings and 0 errors.
- [ ] `xf`, `step->pat`, `n+p`, `n+f+p`, `numeric-token?`, number coercion, `shift-event`,
      Levenshtein each have one definition (`grep -rn "defn-\? xf\b"` etc. finds one).
      `core/combine`, `mini/alt*`, `params/pat?`, the `:rest` branch are gone.
- [ ] `(palindrom (seq :bd))` reports `Undefined symbol: palindrom — did you mean
      palindrome?`; `(stakc :bd)` suggests `stack`; `(sqe :bd)` (2 edits, 3 letters)
      reports `Undefined symbol: sqe` with no hint, unless some name is 1 edit away
      (then that name; take the expected text from the run).
- [ ] `npm run test:all` passes; `npx shadow-cljs compile app` reports no warnings.
- [ ] CLAUDE.md Rule 3 and the library rule are updated; ROADMAP marks QA4 delivered. The
      PR ends with a `## Test protocol` section (hover, completion, highlighting, typo
      hints, regressions in `seq`/`chord`/`arp`/mini `<…>`).

## What NOT to do

- Don't change evaluator or mini-parser behaviour beyond Decisions 8 and 10 (that is QA1).
- Don't split large files or move namespaces beyond the helpers named here (that is QA7).
- Don't change CI beyond the lint gate and the drift check.
- Don't edit generated files by hand, and don't commit the migration script.
- Don't invent behaviour in new `:doc` texts; describe what the code does.
- Don't make `packages/core` require anything from `packages/lisp` or `app`.
- Don't silence clj-kondo through config or `#_:clj-kondo/ignore` to reach 0.
