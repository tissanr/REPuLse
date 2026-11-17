# Phase TEST3 — Release-Protocol Automation: UI Workflows & Measured Sound

> **Automation:** 🤖 auto — every deliverable is a Playwright or CLJS test that runs
> headless in CI; no listen check is needed because the phase adds no sound.

**Milestone:** none — development tooling (runs ahead of M1 so later test rounds shrink)
**Depends on:** TEST1 (delivered). Independent of TEST2.

## Goal

The v0.1.1 release protocol (PR #181) asked for 100 tests by hand. Only about 15 of
them really need a human: ears, a MIDI device, a DAW, login, an AI key, the Supabase SQL
editor or the Vercel deployment itself. The rest are deterministic DOM, editor and
project behaviour, or sound properties that can be measured (louder/quieter, brighter/
duller, higher/lower, a reverb tail). TEST3 turns those into Playwright tests, so a
future release protocol keeps only checks that need a person in Teil 1.

Before / after, for one release-protocol item:

```text
Before (Teil 1, by hand):
- [ ] 71. Strg/Cmd+K S auf fx-Parameter. (fx :filter :freq 800), Cursor in 800.
  Erwartet: (fx :filter :freq (slider 800 20 20000)).

After (Teil 2, pre-ticked by /test-protocol):
- [x] 71. … — e2e/editor.spec.ts › E4: Ctrl+K S wraps fx parameters in slider
```

and for a sound property:

```lisp
(track :drums (->> (seq :bd :sd :bd :sd) (fx :filter :freq 800)))
```

is asserted as "spectral centroid of the render with `:freq 800` is < 0.5× the render
without the filter", instead of "sounds duller".

TEST3 does **not** touch the production AudioWorklet + WASM path; that is TEST2.

## Background

### Test infrastructure today

- `playwright.config.ts` — one Chromium project; `webServer` runs
  `npx shadow-cljs compile test-harness app && node scripts/serve-static.mjs app/public <port>`.
  Both the harness page (`/test-harness.html`) and the full app (`/`) are served.
- `scripts/serve-static.mjs` (42 lines) — plain static server, SPA fallback; it does
  **not** send the headers from `vercel.json` (CSP, `X-Frame-Options`, …).
- `e2e/` — 12 specs, 117 tests. Only `workspace`, `buffers`, `outline`,
  `inline-slider`, `history`, `ai-rail`, `master` drive the full app at `/`; the rest
  use the harness.
- `e2e/helpers/audio-analysis.ts` — `rms`, `rmsStereo`, `mixMono`, `countOnsets`,
  `tailRms`, `goertzel`, `parseWav16`, `rmsPerWindow`, `toDb`, `assertFinitePcm`.
  No spectral centroid and no pitch estimator yet.
- Test hooks on `window` (set by the app): `__REPULSE_EDITOR__` (CodeMirror
  `EditorView`), `__REPULSE_APP__`, `__REPULSE_BUFFERS__`, `__REPULSE_HISTORY__`; on the
  harness `__REPULSE_TEST__` (`evalToEvents`, `renderOffline`, `songEvents`,
  `captureSong`, `registerTestSample`, …) — `app/src/repulse/test_api.cljs`.
- Pattern-level built-ins (`pick-*`, `fix`, `chop`, `striate`, `when-pat`, …) are
  already unit-tested in `packages/core/src/repulse/core_test.cljs`,
  `params_test.cljs` and `packages/lisp/src/repulse/lisp/integration_test.cljs`. Import
  and share error texts are unit-tested in `app/src/repulse/project_test.cljs` and
  `session_test.cljs`. TEST3 does **not** duplicate these; the `/test-protocol` command
  (step 3b) now counts them as automated.

### Where the behaviour lives

| Area | Code |
|---|---|
| Editor keymap (Alt+Enter, F9, `Shift-Mod-7` comment toggle, Ctrl+K S) | `app/src/repulse/ui/editor.cljs`, `ui/inline_slider.cljs` |
| Completions / hover | `app/src/repulse/lisp-lang/completions.js`, `content/builtin_meta.edn` |
| Buffers, `use`, buffer-name errors | `ui/buffers.cljs`, `project.cljs`, `project_eval.cljs` |
| Share links (`#v2:`, `#v3:`), size limit | `session.cljs` |
| History / snapshots | `history.cljs`, `history_store.cljs`, `ui/history_panel.cljs` |
| Snippet library | `snippets.cljs` (`/snippets/library.json`, `api/fetch-snippets`), `ui/snippet_panel.cljs`, `ui/snippet_submit_modal.cljs` |
| AI settings (base-URL checks) | `ui/assistant_panel.cljs`, `ai/` |
| Master hint | `env/builtins/fx.cljs` (`[REPuLse] Hint: this code uses (master …)…`) |
| Embed | `embed.cljs`, `app/public/embed-test.html` |
| CSP | `vercel.json` `headers` |

## Implementation

### 0. Inventory first

Go through Teil 1 of the PR #181 description (`gh pr view 181 --json body`), item by
item. For each item, search `e2e/` and the `*_test.cljs` files and read the test.
Some items are already covered (e.g. item 5 "create, rename, switch, delete + reload" is
`buffers.spec.ts › W2`). Write the result to `docs/TESTPLAN.md` as a new table
**Release-protocol coverage** with the columns `Item`, `Covered by`, `Status`
(`covered` / `TEST3` / `manual — <reason>`). Implement only the `TEST3` rows.

### 1. Static server sends the production headers

Extend `scripts/serve-static.mjs` to read `vercel.json` → `headers` and send them for
matching `source` patterns (only the `"/(.*)"` catch-all is used today; support that
plus exact paths). Keep a `--no-headers` flag for debugging. This makes CSP violations
show up in e2e, so a smoke test can assert none occur.

### 2. Smoke spec — `e2e/smoke.spec.ts`

- S1: loading `/` logs no `console.error` and no `securitypolicyviolation` event
  (register `document.addEventListener('securitypolicyviolation', …)` via
  `page.addInitScript`). Ignore the sample-bank network errors the offline server
  can't serve (allow-list them by URL, not by message).
- S2: `(seq :bd :sd :bd :sd)` → Alt+Enter → output `playing pattern — Alt+Enter to
  re-evaluate, (stop) to stop`; then `(stop)` in the command bar stops it
  (`__REPULSE_APP__` reports not playing).
- S3: the response for `/` carries `Content-Security-Policy` ending in
  `base-uri 'self'; object-src 'none';`. This checks the config only; the deployment
  check stays manual.

### 3. Editor spec — `e2e/editor.spec.ts`

- E1: typing `(slid` opens the completion list with `slider` and its detail text from
  `completions.js`; same for `mas` → `master`.
- E2: hovering `master` and `slider` shows the signature line from `builtin_meta.edn`.
- E3: syntax class: `master` gets the same highlight class as `fx`; `slider` as `amp`.
- E4: Ctrl+K S (Cmd on mac, see `buffers.spec.ts` line ~69 for the platform switch) on
  `800` in `(fx :filter :freq 800)` → `(fx :filter :freq (slider 800 20 20000))`; on
  `(fx :filter 800)` and `(amp 1.5 (seq :c4))` → the forms from the WS3 spec.
- E5: comment toggle: select lines 1–3, press `Shift+Control+7` → each line starts with
  `;`; press again → removed. Also `Control+/` (default keymap). Simulate the German
  layout with `page.keyboard.down('Shift')` + `press('Control+Digit7')` so the test
  doesn't depend on the CI keyboard layout.
- E6: Alt+Enter still evaluates after the comment-toggle binding (regression for #168).

### 4. Project & share spec — `e2e/project-share.spec.ts`

- P1: build a 3-buffer project via `__REPULSE_BUFFERS__`, trigger share, read the URL
  from the clipboard (grant `clipboard-read`), open it in a new page → the URL has
  `#v3:`, all buffers + active tab + BPM restored, nothing plays.
- P2: a `#v2:` link (fixture string generated from the pre-SONG4 encoder, committed
  under `e2e/fixtures/`) loads as one `main` tab with its text.
- P3: share with a ~70 000-char buffer → output starts with `Share failed: link would
  be` and the clipboard is unchanged. Same for the snapshot **link** in the history
  panel.
- P4: `+` with `Drums!`, with `main`, and after 40 `create` calls → the three exact
  error texts from `ui/buffers.cljs`.
- P5: `(use :drums)` in the command bar → `Error: use only works when the project is
  evaluated (Alt+Enter in the editor)`.
- P6: F9 / `(upd)` with a non-main tab active takes only that tab's text (assert via
  `__REPULSE_APP__` track state).
- P7: snippet insert while `drums` is active appends to `drums`, not `main` (library
  served from `page.route('**/snippets/library.json', …)`).

### 5. Snippet library spec — `e2e/snippets-lib.spec.ts`

Mock `/api/snippets*` and `/snippets/library.json` with `page.route`. No login.

- L1: search field: type `"><b id=pwn>x`, switch sort to "newest" → the field still
  holds the text and `#pwn` does not exist.
- L2: a mocked snippet with tag `My <b>Tag</b>` and one with `<img src=x onerror=…>`
  render as text; no `<b>`/`<img>` element inside the tag list, no dialog fired.
- L3: tag filter `techno` shows only snippets tagged `techno`.
- L4: `↓ insert` on "Four on the Floor" appends its code to the active buffer.

### 6. History & embed — extend `e2e/history.spec.ts`, new `e2e/embed.spec.ts`

- H8: command-bar eval, `(upd)`/F9 and a slider drag create **no** history entry.
- H9: `(reset!)` clears the session; snapshots survive a reload.
- H10: `(share!)` still puts a link on the clipboard.
- EM1: in `/embed-test.html`, `(history)` → `snapshots and history are only available
  in the full app`; no `#status-head`, no `#inspector`, no selection tint.

### 7. AI settings — extend `e2e/ai-rail.spec.ts`

No real key needed: these are client-side checks.

- A11: provider `custom`, base URL `http://[::1]:1234/v1` → rejected with the message
  from the code; `http://localhost:1234/v1` → accepted;
  `http://192.168.1.20:1234/v1` → the mixed-content message.
- A12: opening ⚙ causes no `securitypolicyviolation` (needs step 1); Enter in the
  Freesound key field does not submit or reload.
- A13: with the rail open, clicking an outline track opens the inspector, the editor
  stays ≥ 500 px wide, and the rail stays open.

### 8. Measured sound — `e2e/audio-properties.spec.ts` + helpers

Add to `e2e/helpers/audio-analysis.ts`:

- `spectralCentroid(mono, sampleRate)` — magnitude-weighted mean frequency over a
  4096-point FFT (radix-2, written inline; no npm dependency), averaged over windows
  with RMS above −40 dBFS.
- `estimatePitch(mono, sampleRate, minHz, maxHz)` — autocorrelation peak; returns Hz.
- Unit-test both on synthetic sines/noise in the same spec (`describe('helpers')`).

Tests (harness `renderOffline`, same render settings as `audio-render.spec.ts`):

| Test | Assertion |
|---|---|
| M1 filter | centroid with `(fx :filter :freq 800)` < 0.5× centroid without |
| M2 amp / slider | RMS at `(amp 0.1 …)` < 0.25× RMS at `(amp 1 …)`; `(amp (slider 0.7 0 1) …)` RMS within ±5 % of `(amp 0.7 …)` |
| M3 rate | pitch of `(->> (sound :<test sample>) (rate 1.5))` within ±5 % of 1.5× the plain one (use `registerTestSample` with a 220 Hz sine) |
| M4 accelerate | pitch in the last quarter of the event < pitch in the first quarter for `(accelerate -0.5)` |
| M5 reverb tail | `tailRms` after the last note > 10× higher with `(master (fx :reverb 0.4))` than without; same for the exported WAV (`export-parity` path) |
| M6 track fx isolation | a global reverb change does not alter the filtered track's centroid by more than 5 % |

Pick thresholds from measured values with a ≥ 2× safety margin and write the measured
numbers in a comment next to each threshold. If a property can't be separated reliably
(flaky across 20 local runs: `npx playwright test audio-properties --repeat-each 20`),
drop the test and list it as `manual — not separable` in the TESTPLAN table rather than
loosening it until it always passes.

### 9. Docs

- `docs/TESTPLAN.md`: the coverage table from step 0 with final statuses; add rows to the
  *Coverage Map* for `smoke`, `editor`, `project-share`, `snippets-lib`, `embed`,
  `audio-properties`.
- `docs/CONTRIBUTING.md`: one paragraph: "A PR's test protocol lists only what a
  human must check. Anything deterministic gets an e2e or unit test in the same PR, and
  the protocol item points at it."

## Files to change

| File | Change |
|---|---|
| `scripts/serve-static.mjs` | Send `vercel.json` headers; `--no-headers` flag |
| `e2e/smoke.spec.ts` | New — S1–S3 |
| `e2e/editor.spec.ts` | New — E1–E6 |
| `e2e/project-share.spec.ts` | New — P1–P7 |
| `e2e/fixtures/v2-link.txt` | New — pre-SONG4 share-link fixture |
| `e2e/snippets-lib.spec.ts` | New — L1–L4 (mocked API) |
| `e2e/embed.spec.ts` | New — EM1 |
| `e2e/history.spec.ts` | Add H8–H10 |
| `e2e/ai-rail.spec.ts` | Add A11–A13 |
| `e2e/audio-properties.spec.ts` | New — M1–M6 + helper tests |
| `e2e/helpers/audio-analysis.ts` | `spectralCentroid`, `estimatePitch` |
| `docs/TESTPLAN.md` | Release-protocol coverage table, coverage map rows |
| `docs/CONTRIBUTING.md` | Test-protocol paragraph |
| `app/src/repulse/test_api.cljs` | ? — only if a test needs a hook that doesn't exist; keep additions test-only |

## Definition of done

- [ ] `docs/TESTPLAN.md` has the release-protocol coverage table for all 100 Teil 1
      items of PR #181. Every row is `covered`, `TEST3` or `manual — <reason>`, and at
      most 20 rows are `manual`.
- [ ] Every `TEST3` row is covered by a named test that passes in CI.
- [ ] `scripts/serve-static.mjs` sends the `vercel.json` CSP; `e2e/smoke.spec.ts › S1`
      fails when a deliberate inline `<script>` is added to `index.html` (checked
      locally, not committed).
- [ ] `editor.spec.ts › E4` asserts `(fx :filter :freq 800)` →
      `(fx :filter :freq (slider 800 20 20000))`.
- [ ] `editor.spec.ts › E5` toggles `;` comments with `Shift+Control+7` and `Control+/`.
- [ ] `project-share.spec.ts › P1` round-trips a 3-buffer `#v3:` project; `P2` loads the
      `#v2:` fixture as a single `main` tab.
- [ ] `snippets-lib.spec.ts › L1/L2` prove that tag and search input render as text
      (no injected element, no dialog).
- [ ] `audio-properties.spec.ts › M1` asserts the filtered render of
      `(track :drums (->> (seq :bd :sd :bd :sd) (fx :filter :freq 800)))` has under half
      the spectral centroid of the unfiltered one.
- [ ] `audio-properties.spec.ts › M2` asserts that
      `(amp (slider 0.7 0 1) (seq :c4))` and `(amp 0.7 (seq :c4))` render within ±5 % RMS.
- [ ] `audio-properties.spec.ts › M5` asserts a reverb tail for
      `(master (fx :reverb 0.4))` in both the live-offline render and the exported WAV.
- [ ] `audio-properties` passes 20 repeated runs locally (`--repeat-each 20`), with the
      result noted in the PR.
- [ ] `npm run test:all` passes; the e2e suite's CI wall time grows by ≤ 3 minutes
      (state before/after in the PR).
- [ ] No test asserts tonal quality it cannot measure (CLAUDE.md, "FM preset tests").
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5)

## What NOT to do

- Don't capture audio from the production AudioWorklet + WASM path. That is TEST2.
- Don't hit the real Supabase, Vercel API or an AI provider. Mock with `page.route`.
  Login, publishing, rating and the SQL hardening stay manual.
- Don't re-test event values that `core_test` / `integration_test` already assert.
  Point the TESTPLAN row at the existing test instead.
- Don't add npm test dependencies (FFT, pitch libraries). The helpers are small enough
  to write inline.
- Don't loosen a flaky sound threshold until it always passes. Drop the test and mark the
  row manual.
- Don't change app behaviour. The only allowed production-code change is a test-only
  hook in `test_api.cljs`, and only if a test can't be written without it.
