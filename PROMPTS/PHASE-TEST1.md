# Phase TEST1 - Automated Audio Verification

## Goal

Add a reliable automated test suite for REPuLse's audio behavior, without relying
on manual listening after each development session.

TEST1 verifies three layers:

```bash
# Before
npm test
# Pure CLJS tests pass, but "does sound still work?" is manual.

# After
./scripts/test-all.sh
npm run test:all
#   cljs-unit       - existing tests plus Lisp eval -> event integration tests
#   rust-audio      - Rust AudioEngine DSP/scheduling tests
#   browser-offline - Playwright + OfflineAudioContext PCM analysis
```

This phase is an implementation phase. Build the tests, scripts, harness, CI jobs,
and docs needed so future agents can run one command and catch common audio
regressions automatically.

---

## Architecture Decision

REPuLse currently has two relevant audio paths:

1. **Live playback path:** browser `AudioContext` -> `AudioWorkletNode` ->
   Rust/WASM `AudioEngine`.
2. **Offline/export path:** browser `OfflineAudioContext` -> ClojureScript/JS
   fallback synths.

In `app/src/repulse/audio.cljs`, `OfflineAudioContext` deliberately bypasses the
worklet:

```clojure
;; OfflineAudioContext has no worklet - skip to JS fallbacks for rendering.
```

So TEST1 must not claim that browser offline tests verify the production
AudioWorklet + WASM path. Instead:

- CLJS integration tests verify Lisp evaluation and event timing.
- Rust tests verify the real `AudioEngine` DSP loop used by WASM.
- Browser offline tests verify app-level evaluation, scheduling, and PCM rendering
  through the current offline/export path.

Production browser AudioWorklet + WASM capture is deferred to TEST2.

---

## Implementation Order

Do the work in this order so partial progress is useful:

1. Add/extend CLJS eval-to-event integration tests.
2. Add Rust `AudioEngine` unit tests.
3. Add unified local scripts.
4. Add dedicated browser test harness build.
5. Add Playwright browser offline audio tests.
6. Wire CI.
7. Update docs.

Do not assume any of these files already exist. If a file or helper already exists
on the implementation branch, extend it instead of recreating or duplicating it.

---

## 1. CLJS Eval-To-Events Tests

Create or extend:

```text
packages/lisp/src/repulse/lisp/integration_test.cljs
```

Test through `repulse.lisp.core/eval-string`, then query the resulting pattern
with `repulse.core/query`. These tests should exercise the language integration
surface, not just direct core helper calls.

Required helper shape:

```clojure
(defn- make-test-env [] ...)

(defn- eval-pattern
  "Evaluate code and return events for cycle 0."
  [code] ...)

(defn- event-values [events] ...)
```

Required test cases:

- `(seq :bd :sd :hh :sd)` produces four ordered events.
- `(fast 2 (seq :bd :sd))` produces four events in cycle 0.
- `(slow 2 (seq :bd :sd))` produces one event in cycle 0.
- `(stack (pure :bd) (pure :sd))` produces both overlapping values.
- `(->> (seq :c4 :e4) (amp 0.5) (pan -1.0))` preserves event params.
- `(->> (pure :c4) (attack 0.1) (decay 0.5))` preserves envelope params.
- `(euclidean 3 8 :bd)` produces 3 hits and 5 rests.
- `(cat (seq :bd :sd) (seq :hh :oh))` plays the first pattern in cycle 0.
- `(chord :major :c4)` produces three notes.
- `(transpose 12 (seq :c4 :e4))` produces numeric Hz values.
- `(def kick (seq :bd :bd)) kick` reuses a binding across forms.
- `(~ "bd [sd sd] hh")` produces subdivided events.
- Undefined symbols return typed eval errors through `eval-string`.
- Calling a non-function returns a typed eval error.
- Event timing for `(seq :a :b :c :d)` divides the cycle into rational quarters.
- Event timing for `(fast 2 (seq :a :b))` starts at `0`, `1/4`, `1/2`, `3/4`.

Wire the namespace into:

```text
packages/core/src/repulse/test_runner.cljs
```

The normal command must include these tests:

```bash
npm test
```

---

## 2. Rust AudioEngine Tests

Create or extend `packages/audio/src/lib.rs` so the audio engine has a testable
raw render function. The production WASM method should call the same raw function
that tests call.

Expected shape:

```rust
pub fn process_block(&mut self, n_samples: u32, current_time: f64) -> Float32Array {
    let buf = self.process_block_raw(n_samples, current_time);
    Float32Array::from(buf.as_slice())
}

pub fn process_block_raw(&mut self, n_samples: u32, current_time: f64) -> Vec<f32> {
    // Existing production DSP loop moved here.
}
```

Add test-only helpers if they do not already exist:

```rust
#[cfg(test)]
impl AudioEngine {
    pub fn new_for_test(sample_rate: f32) -> AudioEngine { ... }
    pub fn trigger_raw(&mut self, value: &str, time: f64) { ... }
    pub fn trigger_raw_v2(
        &mut self,
        value: &str,
        time: f64,
        amp: f32,
        attack: f32,
        decay: f32,
        pan: f32,
    ) { ... }
}
```

Required Rust tests:

- `bd` produces non-silent output.
- `sd` produces non-silent output.
- `hh` produces non-silent output.
- Numeric tone, e.g. `440`, produces non-silent output.
- `saw:440` produces non-silent output.
- `square:440:0.5` produces non-silent output.
- `noise` produces non-silent output.
- `fm:440:2.0:3.0` produces non-silent output.
- `amp=0.5` has lower RMS than `amp=1.0`.
- `pan=-1.0` is left-heavy.
- `pan=1.0` is right-heavy.
- `pan=0.0` is balanced.
- A short-decay voice reaches near silence in the rendered tail.
- Finished voices are pruned.
- `stop_all` clears active voices and pending events.
- Output is stereo interleaved: `n_samples * 2`.
- Pending events inside a block activate.
- Pending events outside the block stay pending.
- All rendered samples are finite: no `NaN`, no `inf`.

Use local helpers for RMS and stereo RMS inside the Rust test module. Avoid
browser dependencies.

The command must pass:

```bash
cargo test --manifest-path packages/audio/Cargo.toml
```

Do not add broad PCM snapshots in TEST1. If a tiny Rust golden vector is useful,
it must live under `packages/audio/testdata/`, be short, and include an explicit
update command in comments or docs. Browser PCM snapshots are TEST2 scope.

---

## 3. Unified Test Commands

Add `scripts/test-all.sh`.

Required behavior:

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "== cljs-unit =="
npm test

echo "== rust-audio =="
cargo test --manifest-path packages/audio/Cargo.toml

echo "== browser-offline =="
npm run test:e2e
```

The script must:

- run from the repository root,
- fail fast,
- exit 0 only when all layers pass,
- avoid destructive cleanup,
- avoid network access after dependencies are installed.

Add package scripts:

```json
"test:cljs": "npx shadow-cljs compile test && node out/test.js",
"test:rust": "cargo test --manifest-path packages/audio/Cargo.toml",
"test:e2e": "playwright test",
"test:all": "./scripts/test-all.sh"
```

Keep the existing `npm test` behavior as CLJS tests.

---

## 4. Dedicated Browser Harness Build

Add a dedicated shadow-cljs build for browser audio tests. Do not expose the test
API from the release app bundle.

Create:

```text
app/src/repulse/test_api.cljs
app/public/test-harness.html
```

Add a build to `shadow-cljs.edn`:

```clojure
:test-harness
{:target :browser
 :output-dir "app/public/test-js"
 :asset-path "/test-js"
 :modules {:main {:init-fn repulse.test-api/init!}}
 :compiler-options {:language-out :ecmascript-2020
                    :externs ["app/externs/lezer-lr.js"
                              "app/externs/supabase.js"]}}
```

Adjust the externs list only if the test harness does not load modules that need
those externs. Prefer consistency with the app build over clever minimization.

### `test_api.cljs`

Mount exactly one global:

```javascript
window.__REPULSE_TEST__
```

Required methods:

```javascript
window.__REPULSE_TEST__.evalToEvents(code, cycles = 1)
window.__REPULSE_TEST__.renderOffline(code, cycles = 1, options = {})
window.__REPULSE_TEST__.reset()
window.__REPULSE_TEST__.backend()
```

Expected behavior:

- `evalToEvents` returns JSON-safe event summaries:
  `{ value, start, end, wholeStart, wholeEnd }`.
- Rational times should be returned as numbers for Playwright assertions.
- Values should be JSON-safe strings/numbers/maps, not CLJS objects.
- `renderOffline` evaluates code, schedules events into `OfflineAudioContext`,
  starts rendering, and resolves:
  `{ sampleRate, channels, length, duration, backend, left, right }`.
- `left` and `right` should be arrays or serializable typed-array data.
- `backend` must return `"offline-js"` in TEST1.
- `reset` clears any test-local state.
- Errors reject with readable messages.
- No download link creation.
- No localStorage write.
- No network request.
- No user gesture requirement.

The test API may reuse existing evaluator, scheduler, and `audio/play-event`
helpers, but keep it independent from the full editor UI.

### `test-harness.html`

Load the compiled test harness module and set:

```javascript
window.__REPULSE_TEST_READY__ = true
```

The page should render minimal text only. It should be stable in headless
Chromium.

---

## 5. Playwright Browser Offline Tests

Add:

```text
playwright.config.ts
e2e/audio-render.spec.ts
e2e/helpers/audio-analysis.ts
```

Add `http-server` as a dev dependency unless the repo already has an equivalent
static server dependency.

Use compile-once plus static serving in CI. The Playwright web server should be
close to:

```ts
webServer: {
  command:
    "npx shadow-cljs compile test-harness && npx http-server app/public -a 127.0.0.1 -p 3000",
  url: "http://127.0.0.1:3000/test-harness.html",
  reuseExistingServer: !process.env.CI,
  timeout: 120_000,
}
```

Required browser tests:

- Harness loads and reports `backend() === "offline-js"`.
- `(pure :bd)` renders non-silent audio.
- `(seq :bd :sd :hh :sd)` has four onsets in one cycle.
- `(fast 2 (seq :bd :sd))` has four onsets in one cycle.
- `(amp 0.5 (pure :c4))` has lower RMS than `(pure :c4)`.
- `(pan 1.0 (pure :c4))` is right-heavy.
- `(pan -1.0 (pure :c4))` is left-heavy.
- `(stack (pure :bd) (pure :c4))` renders non-silent mixed audio.
- A short-decay tone returns to near silence in the tail.
- `(seq :_ :_ :_ :_)` renders silence below the noise threshold.
- All returned PCM samples are finite.

### Audio Analysis Helpers

Implement in `e2e/helpers/audio-analysis.ts`:

```ts
export function rms(buffer: number[]): number
export function rmsStereo(left: number[], right: number[]): { left: number; right: number }
export function countOnsets(buffer: number[], sampleRate: number, options?: OnsetOptions): number
export function tailRms(buffer: number[], sampleRate: number, tailSeconds: number): number
export function assertFinitePcm(buffer: number[]): void
```

Add simple frequency/energy helpers only if needed for the initial assertions.
Do not add an FFT dependency unless the implementation becomes painful. If a
frequency assertion is needed, prefer a narrow helper such as a simple DFT or
Goertzel check for known tones.

Keep thresholds named and documented near the tests. Use broad property checks,
not exact PCM equality.

### FX Scope

TEST1 does not need to prove every effect plugin. Add FX tests only if the
offline harness naturally applies the relevant FX chain.

Do not force reverb/delay/filter tests into TEST1 if the harness does not route
through the plugin chain. That belongs in a later browser audio phase or TEST2.

---

## 6. CI

Update `.github/workflows/ci.yml` with these jobs:

- `test` - existing CLJS `npm test`
- `cargo-test` - Rust audio tests
- `browser-audio` - Playwright offline audio tests
- `release-build` - existing release build

The `browser-audio` job should:

1. Check out the repo.
2. Set up Node 20 with npm cache.
3. Cache Maven/shadow-cljs deps.
4. Run `npm ci`.
5. Run `npx playwright install --with-deps chromium`.
6. Run `npm run test:e2e`.

Run the browser audio job on every PR and on pushes to `main`.

Keep jobs separate so failures are easy to locate.

---

## 7. Documentation

Update both:

```text
CLAUDE.md
README.md
```

Document:

- `npm test` runs CLJS unit/integration tests.
- `npm run test:rust` runs Rust audio tests.
- `npm run test:e2e` runs browser offline audio tests.
- `./scripts/test-all.sh` and `npm run test:all` run the full TEST1 suite.
- TEST1 browser tests use `OfflineAudioContext` and the `"offline-js"` backend.
- Production browser AudioWorklet + WASM capture is not covered until TEST2.

Update the phase status table in `CLAUDE.md` only after implementation is
complete.

---

## Files To Change

| File | Change |
|---|---|
| `packages/lisp/src/repulse/lisp/integration_test.cljs` | New or extended eval-to-events tests |
| `packages/core/src/repulse/test_runner.cljs` | Include integration test namespace |
| `packages/audio/src/lib.rs` | Add raw render path and AudioEngine tests |
| `app/src/repulse/test_api.cljs` | New test harness API |
| `app/public/test-harness.html` | New minimal browser harness page |
| `shadow-cljs.edn` | Add `:test-harness` build |
| `playwright.config.ts` | New Playwright config |
| `e2e/audio-render.spec.ts` | New browser offline audio tests |
| `e2e/helpers/audio-analysis.ts` | New PCM analysis helpers |
| `scripts/test-all.sh` | New full-suite runner |
| `package.json` | Add scripts and any required dev dependency for static serving |
| `.github/workflows/ci.yml` | Add Rust and browser audio jobs |
| `CLAUDE.md` | Document new verification commands and limitation |
| `README.md` | Document new verification commands and limitation |
| `PROMPTS/PHASE-TEST1.md` | This implementation prompt |

---

## Definition Of Done

- [ ] `npm test` passes and includes at least 10 eval-to-events integration tests.
- [ ] `cargo test --manifest-path packages/audio/Cargo.toml` passes with at least
      8 `AudioEngine` tests.
- [ ] `npm run test:e2e` runs Playwright headlessly and passes.
- [ ] Browser audio tests cover non-silence, expected silence, onset count, RMS
      change, stereo balance, and finite PCM.
- [ ] Browser tests assert or report `backend === "offline-js"`.
- [ ] No TEST1 test claims production AudioWorklet + WASM browser coverage.
- [ ] `./scripts/test-all.sh` runs CLJS, Rust, and browser offline tests.
- [ ] `npm run test:all` runs the same full suite.
- [ ] CI runs CLJS, Rust, browser audio, and release-build jobs on every PR.
- [ ] CI browser job compiles once, serves static files, and runs Chromium
      headlessly.
- [ ] No external CLJS dependencies are added to `packages/core` or
      `packages/lisp`.
- [ ] No FFT/audio-analysis dependency is added unless the implementation notes
      why it became necessary.
- [ ] No browser PCM snapshots are added in TEST1.
- [ ] Browser tests require no network after dependencies are installed, no
      microphone permission, no speakers, and no user gesture.
- [ ] `CLAUDE.md` and `README.md` document the new commands and TEST1 limitation.

---

## Out Of Scope

- True browser production AudioWorklet + WASM PCM capture.
- Browser PCM snapshots.
- Full FX plugin verification.
- Randomized pattern determinism for `choose`, `sometimes`, `degrade`, etc.
- Web Audio mocks in Node.js.
- New user-facing UI.
- Performance benchmark gates.

---

## Risks And Mitigations

1. **False confidence from offline browser tests.**
   Mitigation: every browser result reports `"offline-js"`; TEST2 tracks the
   production Worklet/WASM gap.

2. **Flaky PCM thresholds.**
   Mitigation: assert broad audio properties, not exact samples.

3. **Slow CI.**
   Mitigation: use separate jobs, compile once for Playwright, and cache npm/Maven.

4. **Test harness leaking into production.**
   Mitigation: dedicated `:test-harness` shadow build.

5. **Browser harness does not naturally cover FX.**
   Mitigation: keep FX out of TEST1 unless routing is already correct; defer
   production-path FX verification to TEST2 or a later phase.
