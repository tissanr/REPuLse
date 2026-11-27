# Phase TEST4 — E2E Coverage Expansion: Generated, Measured, Fuzzed

> **Automation:** 🤖 auto. Every deliverable is a Playwright test, a test helper or a
> report script that runs headless. The phase adds no sound and does not edit the CI
> workflows (that is QA7), so the unattended pipeline can build it and verify it end to end.

**Milestone:** none. Development tooling, in the *Tooling* block ahead of M1–M4, so later
phases land on a broader safety net.
**Depends on:** TEST3 and QA4 (both delivered). QA4 makes `builtin_meta.edn` the single
source for env names, completion, hover and highlighting. That single source is what Part A
generates tests from. Independent of TEST2, QA5 and QA7.

## Goal

The e2e suite has grown by hand, one spec per feature: 31 specs, about 300 tests, Chromium
only. Each test checks what its phase author thought of. TEST4 scales e2e coverage in four
ways:

- **A. Generated tests** from data the project already keeps: every example in
  `builtin_meta.edn` (431 names, 753 examples), every demo, every tutorial chapter, every
  documented name in hover and highlighting, and every "did you mean" path.
- **B. Measured e2e coverage.** V8 coverage of the app namespaces during the e2e run,
  reported per namespace with the list of functions never called.
- **C. Targeted specs** for the namespaces that report B shows as weakest.
- **D. Robustness and breadth:** a seeded fuzz test, opt-in Firefox/WebKit runs,
  platform-independent ARIA snapshots of the main layout states, and parallel test files so
  CI time stays bounded.

Before and after, for one documentation example:

```lisp
;; builtin_meta.edn, :chop
:examples ["(chop 8 (sound :breaks152 0))" …]
```

Before: nothing evaluates this example in the app env. If `sound` were renamed, the hover
text and the AI docs would keep teaching a broken example until a human noticed.

After: `e2e/gen-examples.spec.ts › transform › chop #1` evaluates it in the app env. It
fails with the eval error, and the error message names the example.

And for a crash class nobody wrote a test for:

```lisp
(every (seq 0 2) rev (fast :x (seq :bd)))
```

Before: this is covered only if someone thought of it. After: `e2e/fuzz.spec.ts` produces
hundreds of programs of this shape from a printed seed. Each must end in either a result or
a typed `Error: …` line, with no uncaught page error, and the app must still play
`(seq :bd :sd)` afterwards.

## Background

### Test infrastructure today (on `dev`, 2026-11-27)

- `playwright.config.ts`: one `chromium` project, `retries: 0`, no `fullyParallel`, so
  files run in parallel and tests inside a file run serially. `webServer` compiles
  `test-harness app embed-e2e` and serves `app/public` through `scripts/serve-static.mjs`
  with `--dev-eval`, which also sends the `vercel.json` headers.
- CI (`.github/workflows/ci.yml`, job `e2e`): `npm run build:wasm`,
  `npx playwright install --with-deps chromium`, `npm run test:e2e`. **Do not edit this
  file in TEST4** (see *What NOT to do*).
- Harness page `/test-harness.html`, `window.__REPULSE_TEST__`
  (`app/src/repulse/test_api.cljs`): `evalToEvents(code, cycles)`, `renderOffline`,
  `songEvents`, `captureSong`, `registerTestSample`, `setBankPrefix`, `reset`, `backend`,
  `audioReady`. Its env is **pattern-only** (`leval/make-env` plus `defsynth`/`bus`). App
  built-ins such as `track`, `fx`, `master`, `bpm`, `bank`, `export!` and `share!` are not
  bound there.
- Full app `/`, `window.__REPULSE_APP__` (`app/src/repulse/app.cljs` ≈ line 360):
  `evaluate(code)`, `tracks()`, `playing()`, `onsetCount(track)`, `cycle()`,
  `builtinNames()`, `trackParam`, `fxParam`, `selection`, `openSubmitModal`,
  `reloadSnippets`, `pendingDisconnects`, `timelineQueries`, … Also
  `window.__REPULSE_EDITOR__` (CodeMirror `EditorView`), `__REPULSE_BUFFERS__` and
  `__REPULSE_HISTORY__`. Eval results show in `#output`, and errors start with `Error: `
  (see `e2e/robust-playback.spec.ts`).
- Helpers in `e2e/helpers/`: `audio-analysis.ts` (`rms`, `countOnsets`, `goertzel`,
  `spectralCentroid`, `estimatePitch`, `assertFinitePcm`, …), `csp.ts`
  (`trackCsp`, `cspViolations`), `app-export.ts`, `zip.ts`.
- `docs/TESTPLAN.md`: *Coverage Map*, *Release-Protocol Coverage*, *What Is Not Fully
  Covered Yet*.

### Data the generated tests read

| Source | Shape | Today's tests |
|---|---|---|
| `docs/ai/builtins.json` (generated from `app/src/repulse/content/builtin_meta.edn` by `npm run gen:ai-docs`) | 431 names → `{category, signature, returns, side-effects, examples[], see-also}`; 753 examples. Categories by example count: transform 245, util 100, source 79, param 65, synth 54, signal 39, special 34, effect 34, audio 23, ugen 19, io 18, midi 12, session 11, sample 10, content 6, ai 4. Side effects: audio 38, dom 23, storage 7, network 4, midi 4, AI 2 | `e2e/builtins-meta.spec.ts › B1` checks only that env and meta list the same names. The `tl*-documented-examples-evaluate` tests in `packages/lisp/src/repulse/lisp/integration_test.cljs` evaluate a hand-picked subset in the lisp env |
| `app/src/repulse/content/demos.cljs` `demo-templates` | 9 demos `{:bpm n :code "…"}` | `demos_test.cljs`: parse, `(bpm n)` matches, a `;; Shows:` line. **No demo is ever evaluated or played** |
| `app/src/repulse/content/tutorial.cljs` `tutorial-chapters` | 8 chapter strings | none |
| `app/src/repulse/lisp-lang/builtins.generated.js` | read by `hover.js` (`DOCS`) and `completions.js` | `editor.spec.ts` E1/E2/E2b/E3/E3b sample 10 names |

### "Did you mean" and reader errors (exact texts live in the code)

- `packages/lisp/src/repulse/lisp/eval.cljs` ≈ line 200: `Undefined symbol: <n>` + ` — did you mean <h>?`
- `app/src/repulse/history.cljs` ≈ line 188: `unknown snapshot "<nm>" — did you mean "<m>"?`
- `app/src/repulse/tracks_model.cljs` ≈ line 25: `unknown track option <s> — did you mean <hint>?`
- `app/src/repulse/sound_check.cljs` ≈ lines 125/133: ` — did you mean :<suggestion>?`
- `packages/lisp/src/repulse/lisp/reader.cljs`: `Unterminated string — missing closing "`,
  `Unterminated list — missing closing )`, `… vector — missing closing ]`,
  `… map — missing closing }`

### Build output that coverage reads

`npx shadow-cljs compile app` (dev mode) writes one JS file per namespace under
`app/public/js/cljs-runtime/` (e.g. `repulse.ui.inspector.js`). With `--dev-eval`, the dev
loader evaluates each file with a `//# sourceURL=` comment, so V8 coverage reports one
script per namespace and CLJS function names survive in munged form
(`repulse$ui$inspector$render_row`). Verify this in step B0 before building on it.

## Implementation

### Step 0 — Baseline

On the unmodified branch, run `npm run test:e2e` twice. Note the wall time, the test count
and any flaky test in the PR. All later budgets are relative to this baseline.

### Part A — Generated tests

**A1. Every builtin example: `e2e/gen-examples.spec.ts`.**

- Read `docs/ai/builtins.json` in the spec (Node side, like `builtins-meta.spec.ts`).
  Generate one `test()` per **name**, titled `<category> › <name>`. Inside, loop over that
  name's examples and attach the failing example's index and source to the assertion
  message.
- `test.describe.configure({ mode: 'parallel' })`. Open one page per worker in a
  `beforeAll` and reuse it: the harness page for the harness route, a full-app page for the
  app route. Call `reset()` (or `(stop)` plus clearing tracks) between names.
- **Placeholder prelude.** Many examples use conventional free names on purpose:
  `(not= x 0)`, `(+ 100 (* 12 n))`, `(not= val :bd)`. Evaluate each example after a fixed
  prelude that binds them, e.g. `(def x 1) (def n 2) (def val :bd) (def pat (seq :bd :sd))
  (def p pat)`. Build the list from the free symbols the first run reports, and keep it
  ≤ 15 names. An example whose free name is not a sensible placeholder is a doc bug: fix
  it (below).
- **Route per example:**
  1. Categories `synth`, `effect`, `ugen` are keywords or defsynth-only. Wrap the example
     as the meta entry demands: run synth examples as written if they are full forms,
     effects inside `(fx …)` only if the example is not already a full form, ugens inside
     a minimal `(defsynth t [] …)`. If an example is a fragment that cannot run alone,
     that is a doc bug. Fix the example (below).
  2. Otherwise try the harness `evalToEvents(code, 1)`. If it succeeds with a pattern,
     assert: no throw, every event has finite numeric `whole` bounds, and (for
     `source`/`transform` with a non-empty-looking body) at least one event in 4 cycles.
     Some examples are silent by design (`(degrade-by 1 …)`, rests); list them in the
     allow-list below.
  3. If the harness throws `Undefined symbol` for an app built-in, run it in the full
     app: `__REPULSE_APP__.evaluate(code)`. Assert that `#output` does not start with
     `Error: ` and that no `pageerror` fired. Then `(stop)`.
- **Side effects** (`side-effects` field): `network` / `ai`: `page.route` mock the
  endpoint (reuse the patterns in `snippets-lib.spec.ts` and `ai-rail.spec.ts`).
  `midi`: headless Chromium has no MIDI devices, so assert the documented
  "no MIDI access/device" message instead of success. `storage` / `dom`: fine in a fresh
  context. `audio`: for the categories `audio`, `synth`, `param`, `effect` also run
  `renderOffline` for 1 cycle at 22 050 Hz and assert finite PCM (`assertFinitePcm`). Do
  **not** assert loudness for every example.
- **Failing examples are doc bugs: fix them** in `builtin_meta.edn`, then run
  `npm run gen:ai-docs` and commit `builtins.json` and the generated files together
  (CLAUDE.md Rule 3). If an example fails because of an **app bug**, fix the bug when the
  fix is local (≤ ~30 lines, one namespace) and add a unit test. Otherwise put it in
  `KNOWN_BROKEN` with a one-line reason and list it in the PR under *Known issues*.
- One allow-list object at the top of the spec:
  `SILENT_BY_DESIGN`, `KNOWN_BROKEN`, `SKIP` (each `name#index → reason`). The spec fails
  if an allow-listed example **passes** (stale entry), so the list can only shrink. At most
  25 entries in total.

**A2. Every demo and tutorial chapter: `e2e/gen-content.spec.ts`.**

- Read the demo and chapter code through a test hook rather than parsing CLJS in Node.
  Add `:demos (fn [] (clj->js demo-templates))` and
  `:tutorialChapters (fn [] (clj->js tutorial-chapters))` to `__REPULSE_APP__` in
  `app.cljs`. These are test hooks with no behaviour change.
- For each demo (9 tests) and chapter (8 tests), on the full app with sample-bank URLs
  routed to the local test-sample fixture (see `qa3-robustness.spec.ts › A6`):
  1. load it the way the user does (`(demo :techno)` / `(tutorial 3)` in the command bar),
  2. Alt+Enter,
  3. assert no `Error: ` output and no `pageerror`, `playing()` true, `tracks()` non-empty
     (for demos), and the sum of `onsetCount` over all tracks > 0 within 4 s
     (`bpm` raised to 240 first, as `robust-playback.spec.ts` does),
  4. `(stop)`.
- Bundle chapter 5's `def`s check: after evaluating chapter 5, `kick` is defined
  (evaluate `kick` → no error).

**A3. Hover, highlight and completion sweep: extend `e2e/editor.spec.ts`.**

- E7 *highlight*: put every env name from `builtinNames()` (minus `ENV_EXCLUDED` from
  `builtins-meta.spec.ts`) into the editor, one `(<name>)` per line, with the cursor at
  the end. Read every `.cm-line span` once and assert each name's class equals the class
  of `amp`. One DOM read, so this stays fast.
- E8 *hover*: for every documented name, hover it and assert
  `.repulse-hover-sig` equals `meta[name].signature`. Same layout as E7. Reuse the
  `hover()` helper from E2b. Run it in parallel chunks (`test.describe.configure({ mode:
  'parallel' })` with 4 chunk tests) so it stays under 60 s on CI. Names with operator
  characters (`+`, `->>`, `not=`, `samples!`, `?`-suffixed) are the point of this test.
  Do not skip them.
- E9 *completion*: for a fixed sample of 40 names (every 11th name in sorted order), type
  the first 3 characters and assert the name is in `.cm-tooltip-autocomplete`. A full
  typing sweep would cost minutes and adds little once E7/E8 cover the data path.

**A4. Error-message catalogue: `e2e/error-messages.spec.ts`.**

One test per message, full app, Alt+Enter, exact `#output` text copied from the code (and
from the harness for the evaluator texts). At least:

| Input | Expected output starts with |
|---|---|
| `(sek :bd :sd)` | `Error: Undefined symbol: sek — did you mean seq?` |
| `(seq :bd :sd` | `Error: Unterminated list — missing closing )` |
| `(seq "0 1)` | `Error: Unterminated string — missing closing "` |
| `[1 2` / `{:a 1` | the vector / map texts |
| `(track :a (seq :bd) :mutee true)` | the `unknown track option … — did you mean :mute?` text |
| `(seq :bdd)` played | the `sound_check` hint text with `— did you mean :bd?` |
| `(restore! "drumz")` after `(snapshot! "drums")` | `unknown snapshot "drumz" — did you mean "drums"?` |

Also assert the underline (`.cm-lintRange-error` or whatever `robust-playback.spec.ts`
asserts) sits on the offending token for the `sek` case. Verify each input against the code
before committing. If the real text differs, the code wins and the table above is updated in
the spec.

### Part B — Measured e2e coverage

**B0.** Confirm the per-namespace script URLs: in a scratch spec, `page.coverage.
startJSCoverage()` → load `/` → `stopJSCoverage()` and print the URLs. If the dev build
reports one script per namespace, continue. If it does not, map through the source maps
in `app/public/js/cljs-runtime/*.map` with a small inline decoder (VLQ, no dependency) and
note the choice in the PR.

**B1. Collection.** `e2e/helpers/coverage.ts` exports a `test` that extends
`@playwright/test` with an auto fixture:

- active only when `process.env.REPULSE_E2E_COVERAGE === '1'` and the browser is
  Chromium,
- starts JS coverage (`resetOnNavigation: false`) before each test and writes the raw
  result to `coverage/e2e/raw/<worker>-<testId>.json` after it.

Migrating every spec to import this `test` is mechanical. Do it with one sed pass over
`e2e/*.spec.ts` (`from '@playwright/test'` → `from './helpers/coverage'`, re-exporting
`expect` and `type Page`). The fixture does nothing when the env var is unset, so normal
runs cost nothing.

**B2. Report.** `scripts/e2e-coverage-report.mjs` (Node, no dependency):

- merges the raw files and keeps scripts whose URL matches
  `/js/cljs-runtime/repulse\.` (app, core and lisp namespaces). Drops `*_test`,
  `repulse.test-api` and `repulse.test-runner`.
- per namespace: **function coverage** (V8 functions with `count > 0` / all functions) and
  **byte coverage** (covered byte ranges / script length, block-level ranges included),
- writes `coverage/e2e/summary.md`: a table sorted by function coverage ascending, and per
  namespace the list of never-called functions, demunged (`repulse$ui$inspector$render_row`
  → `repulse.ui.inspector/render-row`),
- writes `coverage/e2e/summary.json` with the same data, for Part C and later phases.

`package.json`: `"test:e2e:coverage": "REPULSE_E2E_COVERAGE=1 playwright test --project=chromium && node scripts/e2e-coverage-report.mjs"`.
Add `coverage/` to `.gitignore`.

**B3. Baseline.** Run B2 before Part C starts and record the numbers in the PR and in
`docs/TESTPLAN.md` → new section *Measured E2E Coverage*: overall app-namespace function
coverage, plus the 15 lowest namespaces.

### Part C — Targeted specs from the report

- Work through the namespaces under 50 % function coverage in the B3 baseline, lowest
  first. Skip the ones that are only reachable with hardware, real login or a real AI key
  (`midi`, the Supabase auth flow, live `ai/client` streaming) and give a reason in
  TESTPLAN.
- Likely candidates on today's code (confirm from the report, don't assume):
  `ui/inspector`, `ui/timeline`, `ui/goto`, `ui/status_head`, `ui/history_panel`,
  `ui/snippet_panel`, `ui/snippet_submit_modal`, `ui/assistant_panel` (mock
  `/api/ai-stream` with a canned SSE body through `page.route`, the way `ai-rail.spec.ts`
  mocks it), `ui/auth_button` (signed-out state and the menu only; no real OAuth),
  `midi_export`, `plugin_loading`, `content/first_visit`, `snippets/preview`.
- One spec per area, named after the namespace (`e2e/inspector.spec.ts`, …). Every test
  drives the UI the way a user does: clicks, keys and evaluations. It reads state through
  the existing hooks and asserts concrete DOM text or values. Don't call internal
  functions just to raise the number. A test that only touches a function without
  asserting its effect does not count.
- **Target:** overall app-namespace function coverage rises by **≥ 15 percentage points**
  over the B3 baseline. Every namespace still under 40 % afterwards has a row in
  TESTPLAN with the reason.

### Part D — Robustness and breadth

**D1. Seeded fuzz: `e2e/fuzz.spec.ts`.**

- Generator in `e2e/helpers/fuzz-gen.ts`: a seeded PRNG (mulberry32, inline). Forms:
  `form := atom | (name arg{0..4})` with depth ≤ 3. `name` comes from `builtins.json`
  restricted to categories `source`, `transform`, `param`, `signal`, `util` with no
  `side-effects`. Atoms come from a pool: `:bd :sd :hh :c4 :e4 :_ :x`, ints `-2..16`,
  `0.5`, `1/3`, `"0 1 2"`, `"<0 3>"`, `nil`, `true`, `[]`.
- `REPULSE_FUZZ_SEED` (default: a fixed seed in CI, so failures reproduce) and
  `REPULSE_FUZZ_N` (default 200). Print the seed in the test title.
- Run on the full app in batches of 50 programs per test (4 tests, parallel). For each
  program: `__REPULSE_APP__.evaluate(...)` inside `(track :fz …)`. Assert:
  - no `pageerror`,
  - `#output` is a result or starts with `Error: ` (no `undefined`, no `[object Object]`,
    no JS stack text),
  - the eval returns within 2 s (QA1 count limits).
- After each batch, evaluate `(track :ok (seq :bd :sd))` and assert `onsetCount('ok')`
  grows within 3 s. Then `(stop)`.
- A failing program is written to `test-results/fuzz-<seed>-<i>.lisp`. Fix crashes the
  same way as in A1 (local fix plus a regression test in `error-messages.spec.ts` or the
  unit tests). Otherwise add the program to `e2e/fixtures/fuzz-known.txt` with a reason,
  where `test.fail` keeps it visible.

**D2. Firefox and WebKit, opt in.**

- `playwright.config.ts`: add `firefox` and `webkit` projects only when
  `REPULSE_E2E_BROWSERS=all`. They run tests tagged `@cross` (`grep: /@cross/`).
- Tag `@cross`: smoke S1–S2, editor E1–E5, the harness render tests in
  `audio-render.spec.ts`, A1 (harness route only) and A4.
- `npm run test:e2e:browsers` (`REPULSE_E2E_BROWSERS=all playwright test`). Run it
  locally after `npx playwright install firefox webkit`. Record the results in the PR:
  pass, or fail with the reason. Browser-specific failures that are real app bugs go to
  *Known issues*. Mark tests that cannot work in a browser (for example WebKit without
  `OfflineAudioContext` features) with `test.skip(browserName === 'webkit', '<reason>')`.
- Wiring these projects into CI is a QA7 item. Add one line to QA7's *Scope* in
  `PROMPTS/PHASE-QA7.md` that points at the opt-in projects.

**D3. ARIA snapshots of layout states: `e2e/layout.spec.ts`.**

Pixel screenshots differ between macOS (where the pipeline runs) and Linux CI, so TEST4
uses Playwright's `toMatchAriaSnapshot` instead. The accessible structure is
platform-independent and catches missing buttons, labels and regions.

- States: first visit (empty), two tracks playing (`(track :a (seq :bd))`
  `(track :b (seq :hh :hh))`), eval error, inspector open on a track, history panel open,
  snippet panel open (mocked library), assistant rail open, two buffers.
- Snapshot the `#app` landmark tree with `depth` limited to keep the snapshots short
  (≤ 40 lines each). Mask dynamic text (cycle counter, time) with regex matchers.
- Plus geometric invariants at 1280×800 and 1024×700: the editor is ≥ 500 px wide in
  every state, nothing overflows horizontally
  (`document.documentElement.scrollWidth <= innerWidth`), and the status head stays
  visible.

**D4. Parallelism and time budget.**

- Generated and fuzz specs use `mode: 'parallel'`. Specs that capture real-time audio or
  measure timing (`qa3-robustness`, `robust-playback`, `audio-properties`) stay serial;
  add `test.describe.configure({ mode: 'serial' })` where it is missing so that is
  explicit.
- Do not change `workers` for CI. Measure the e2e wall time on the CI runner from the PR
  checks: it may grow by **at most 4 minutes** over the step-0 baseline. If it grows more,
  shrink A3/D1 sizes (N, chunks) before cutting coverage elsewhere.

### Docs

- `docs/TESTPLAN.md`: Coverage Map rows for `gen-examples`, `gen-content`,
  `error-messages`, `fuzz`, `layout`, the Part C specs, and the cross-browser opt-in. Add
  the new *Measured E2E Coverage* section (baseline → final). In *Standard Commands* add
  `test:e2e:coverage` and `test:e2e:browsers`. In *Adding Coverage For New Phases* add:
  "New built-ins get tested by `gen-examples` automatically through their
  `builtin_meta.edn` examples. Make each example runnable on its own."
- `docs/CONTRIBUTING.md`: one paragraph on the fuzz seed (how to reproduce a failure) and
  on the coverage report.
- `CLAUDE.md` *Build & test*: add the two new npm scripts (one line each).

## Files to change

| File | Change |
|---|---|
| `e2e/gen-examples.spec.ts` | New: A1, one test per builtin name, allow-lists |
| `e2e/gen-content.spec.ts` | New: A2, demos and tutorial chapters |
| `e2e/editor.spec.ts` | Add E7–E9 |
| `e2e/error-messages.spec.ts` | New: A4 |
| `e2e/helpers/coverage.ts` | New: coverage fixture (B1) |
| `e2e/*.spec.ts` | Import `test`/`expect` from `./helpers/coverage` (mechanical) |
| `scripts/e2e-coverage-report.mjs` | New: B2 report |
| `e2e/<namespace>.spec.ts` | New: Part C specs, one per targeted area |
| `e2e/fuzz.spec.ts`, `e2e/helpers/fuzz-gen.ts`, `e2e/fixtures/fuzz-known.txt` | New: D1 |
| `e2e/layout.spec.ts` (+ `e2e/layout.spec.ts-snapshots/` if Playwright writes YAML files) | New: D3 |
| `playwright.config.ts` | Opt-in `firefox`/`webkit` projects with `grep: /@cross/` (D2) |
| `app/src/repulse/app.cljs` | Test hooks `demos`, `tutorialChapters` on `__REPULSE_APP__` |
| `app/src/repulse/test_api.cljs` | ? only if A1 needs a hook that doesn't exist; keep it test-only |
| `app/src/repulse/content/builtin_meta.edn` | Fix examples that cannot run on their own |
| `docs/ai/builtins.json`, `app/src/repulse/lisp-lang/builtins.generated.js`, grammar/parser | Regenerated by `npm run gen:ai-docs` if examples changed |
| `package.json` | `test:e2e:coverage`, `test:e2e:browsers` |
| `.gitignore` | `coverage/` |
| `docs/TESTPLAN.md`, `docs/CONTRIBUTING.md`, `CLAUDE.md` | Docs as above |
| `PROMPTS/PHASE-QA7.md` | One scope line: wire the opt-in browsers and the e2e coverage summary into CI |
| app namespaces | ? small local fixes for bugs the generated or fuzz tests find, each with a test |

## Definition of done

- [ ] `e2e/gen-examples.spec.ts` has one test per documented builtin name (≥ 430 tests).
      Every example in `builtins.json` is evaluated or allow-listed, and the allow-lists
      hold ≤ 25 entries, each with a reason.
- [ ] A1 evaluates `(seq :bd :sd :bd :sd)`, `(euclidean 3 8 :sd 2)` and
      `(cat (seq :bd :sd) (seq :hh :oh))` (examples of `seq`, `euclidean`, `cat`) without
      error and with ≥ 1 event in 4 cycles. A stale allow-list entry (an example that now
      passes) fails the spec.
- [ ] Breaking one example by hand (locally, not committed), e.g. changing `:chop`'s
      first example to `(chopp 8 (sound :breaks152 0))`, makes exactly that test fail, and the message
      names the example.
- [ ] `e2e/gen-content.spec.ts` plays all 9 demos and all 8 tutorial chapters: no
      `Error: ` output, no page error, onsets counted > 0.
- [ ] Editor E7/E8 cover every documented name. E8 asserts the hover signature for `+`,
      `->>`, `not=` and `samples!` among all others.
- [ ] `e2e/error-messages.spec.ts` asserts the exact output for `(sek :bd :sd)` (did you
      mean `seq`), for `(seq :bd :sd` (unterminated list), and for the track-option,
      sound and snapshot hints.
- [ ] `npm run test:e2e:coverage` writes `coverage/e2e/summary.md` with per-namespace
      function and byte coverage and demunged never-called function names.
- [ ] `docs/TESTPLAN.md` → *Measured E2E Coverage* shows baseline and final numbers.
      Overall app-namespace function coverage rose by ≥ 15 percentage points, and every
      namespace still under 40 % has a reason.
- [ ] `e2e/fuzz.spec.ts` runs 200 seeded programs in CI. Re-running with the printed
      `REPULSE_FUZZ_SEED` reproduces the same programs. After each batch,
      `(track :ok (seq :bd :sd))` still produces onsets.
- [ ] `REPULSE_E2E_BROWSERS=all npx playwright test --grep @cross` was run locally on
      Firefox and WebKit, and each result is recorded in the PR.
- [ ] `e2e/layout.spec.ts` has ARIA snapshots for the 8 listed states and the width and
      overflow invariants at 1280×800 and 1024×700.
- [ ] `.github/workflows/` is unchanged. The CI e2e wall time grew by ≤ 4 minutes over
      the step-0 baseline (both numbers in the PR).
- [ ] The new parallel specs pass `--repeat-each 5` locally without a flake (noted in
      the PR).
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass. If examples changed,
      `npm run check:ai-docs` shows no drift.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5)

## What NOT to do

- Don't touch `.github/workflows/`, CI sharding, coverage thresholds that fail CI, or
  pixel screenshot baselines. Those change the pipeline's own gate or need Linux
  baselines, so they belong to QA7 (🧑).
- Don't write tests for `api/` serverless functions. QA5 adds the `api/` test runner and
  its cases. TEST4 only mocks `/api/*` from the browser side with `page.route`.
- Don't capture PCM from the production AudioWorklet + WASM path. That is TEST2. A2 counts
  scheduler onsets and does not measure live audio.
- Don't re-assert event values that `core_test` / `integration_test` already check. A1
  asserts that examples run, not what they produce.
- Don't raise the coverage number with tests that call internals without asserting a
  user-visible effect, and don't loosen a flaky assertion until it passes. Drop or
  allow-list it with a reason instead.
- Don't add npm dependencies for coverage, fuzzing or snapshots. Playwright's built-ins
  plus small inline helpers are enough.
