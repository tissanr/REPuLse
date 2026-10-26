# REPuLse

> A Lisp that beats.

Browser-based live coding instrument for algorithmic music. The user writes patterns in
**REPuLse-Lisp** — a small, purpose-built Lisp — and hears them in real time. Inspired by
Strudel.cc and Tidal Cycles, with a Lisp instead of JavaScript and a ClojureScript
pattern engine at the core.

```
(stack
  (seq :bd :sd :bd :sd)
  (fast 2 (seq :hh :oh))
  (every 4 rev (seq :c3 :eb3 :g3)))
```

Not a general-purpose Lisp — the language exists to describe music patterns.
Desktop browser is the target.

---

## Architecture

Three layers, strictly separated:

```
┌──────────────────────────────────────────────────────────┐
│  REPuLse-Lisp  (user-facing language)                    │
│  packages/lisp — Reader + Evaluator (CLJS)               │
├──────────────────────────────────────────────────────────┤
│  Pattern Engine  (pure functions of time)                │
│  packages/core — Pattern algebra (CLJS)                  │
├──────────────────────────────────────────────────────────┤
│  Audio Layer  (synthesis + scheduling)                   │
│  packages/audio — Rust → WASM, runs in an AudioWorklet   │
│  app/src/repulse/audio.cljs — lookahead scheduler        │
└──────────────────────────────────────────────────────────┘
```

**Dependency rule:** each layer may only depend on layers below it.
`core` has zero knowledge of `lisp`, audio, or DOM. Ever.

```
packages/core/     Pattern algebra — pure CLJS, no DOM, no audio
packages/lisp/     REPuLse-Lisp reader + evaluator
packages/audio/    Rust crate → wasm-pack → WASM (AudioEngine)
app/               Browser app (CLJS + CodeMirror 6 + vanilla DOM) — wires it all together
api/               Vercel serverless functions (auth, snippets, AI proxy)
e2e/               Playwright offline-audio tests
PROMPTS/           Phase specs (one per phase)
ROADMAP.md         Phase status and delivery notes — the single source of truth
docs/              USAGE (user manual), ARCHITECTURE, PLUGINS, …
```

### Pattern

A **Pattern** is a pure function from a `TimeSpan` to a list of `Event`s.

```clojure
{:start 0N :end 1N}                ; TimeSpan — rational numbers, never floats

{:value :bd                        ; Event payload — keyword, number, map
 :whole {:start 0N :end 1N}        ; logical time of the full event
 :part  {:start 0N :end 1N}}       ; active slice (may differ from whole)
```

---

## Coding conventions

- **Pure functions by default.** Side effects only at the edges: audio output, DOM.
- **Rational time everywhere.** Use rationals (`1/4`, `3N`) in `core`. Never
  `(/ 1.0 4.0)` for time values.
- **No external CLJS libraries** in `core` or `lisp`. Only `cljs.core` and `cljs.test`.
- **No external Rust audio libraries** in `audio`. Only `web-sys` Web Audio bindings.
- **Errors surface as typed values at the boundary.** Reader/evaluator internals may
  throw `ex-info`; `repulse.lisp.core/eval-string` converts failures into a typed
  eval-error result for the app layer.
- **Fuzzy-match typos** in the evaluator: suggest the closest known name.
- **Tests for core.** Every function in `packages/core` has a `cljs.test` unit test.

---

## Build & test

```bash
npm install && npm run build:wasm   # first time (and per worktree)
npm run dev                         # shadow-cljs watch, port 3000 (no WASM build)
npm run dev:full                    # build:wasm + watch — first run of a session

npm run test                        # CLJS: core + lisp + app session tests
npm run test:rust                   # Rust AudioEngine tests
npm run test:e2e                    # Playwright offline-audio tests
npm run test:all                    # all three

npm run gen:grammar                 # after editing repulse-lisp.grammar
npm run gen:ai-docs                 # after editing builtin_meta.edn
```

TEST1 browser tests use a `:test-harness` build with `OfflineAudioContext` and the
`"offline-js"` backend — not the production AudioWorklet + WASM path (that is TEST2).

Use `preview_start` (port 3000) to verify UI changes.

### Pre-push verification (MANDATORY)

Before pushing any ClojureScript change, run **both**:

```bash
npm run test
npx shadow-cljs compile app
```

The `:test` target does **not** compile app-layer namespaces
(`app/src/repulse/env/builtins/`, `ui/`, …). Bracket mismatches, unresolved
namespaces and unused requires there are only caught by the `:app` compile. CI runs
`shadow-cljs release app` and `clj-kondo` on the full tree.

### Worktrees and Vercel

Each worktree needs its own `npm install`. Env vars are only needed for auth/community
features (S2/S3): `vercel link` then `vercel env pull .env.local` (both gitignored).

---

## WASM synthesis — architectural decisions

Read before touching `packages/audio/src/lib.rs`.

**Karplus-Strong delay buffer: pre-allocate max per voice.**
Every `Voice::KarplusStrong` holds a fixed buffer sized for ~20 Hz (2205 samples at
44.1 kHz, ~9 KB/voice). Wasteful on high notes, but keeps the AudioWorklet render
callback allocation-free. Do not move allocation into the render path without
profiling first.

**SYN1 plucked strings: body resonance EQ chain per instrument.**
`Voice::KarplusStrong` carries `body_filters: Vec<Biquad>`, built by
`ks_body_filters(preset, sr)` from `Biquad::peaking_eq` / `Biquad::highshelf` (Audio EQ
Cookbook) and allocated at trigger time, never in the render callback. It models the
body resonances that keep an instrument recognisable through the sustain. SYN3 reuses
the same `Biquad` primitives: shared filter infrastructure, separate excitation and
waveguide models.

**FM preset tests: "produces non-silent output" is the complete scope.**
Each `Voice::FM` preset test triggers 440 Hz, renders 0.1 s and asserts RMS > 0.001.
Tests cannot verify that a sound *sounds like* a saxophone — that needs a human listen
loop. Don't add tests claiming spectral/tonal checks unless they make a concrete FFT
assertion (e.g. peak frequency within ±5%). A test that passes when the sound is wrong
is worse than no test.

**SYN3 bowed string: full bi-directional waveguide, not a Karplus approximation.**
Two delay lines (nut→bridge, bridge→nut), a piecewise-linear bow friction table, and
per-instrument body IIR filters. A Karplus bow (slow attack + high feedback) was
rejected: it sounds plucked and re-triggers instead of sustaining.

---

## Phase workflow (IMPORTANT — follow every time)

`main` is protected — all changes go via Pull Request.

**Source of truth:** `ROADMAP.md` holds every phase and its status;
`PROMPTS/PHASE-*.md` holds each spec. There is no other status table to update.

### Rule 1 — Creating a phase

Write `PROMPTS/PHASE-XYZ.md` and add a `## Phase XYZ — Title 📋 *planned*` section to
`ROADMAP.md` (2–4 bullets + `See full spec: [PROMPTS/PHASE-XYZ.md](PROMPTS/PHASE-XYZ.md)`).
Commit both together.

### Rule 2 — Definition of done for a phase

A phase is delivered only when **all** of these hold:

1. **Verified.** `npm run test:all` passes for code phases; `npm test` at minimum for
   docs-only phases (say why the full suite wasn't needed). If the full suite can't run,
   state the exact blocker and run the strongest available subset.
2. **Documented.** `docs/USAGE.md` lists every new built-in, effect or parameter in its
   reference tables with at least one example; `README.md` is updated if user-facing
   syntax or the quick-start changed.
3. **Marked delivered.** In `ROADMAP.md`, `📋 *planned*` → `✅ *delivered*` plus a
   **Delivered:** section matching the existing style.
4. Docs are committed with (or immediately after) the implementation.

"The code works" is necessary but not sufficient.

### Rule 3 — New built-in names

For any built-in that should be highlighted, autocompleted and known to the AI:

1. Add it to `BuiltinName` in `app/src/repulse/lisp-lang/repulse-lisp.grammar`
2. **Run `npm run gen:grammar`** — regenerates the committed `parser.js`
   (skip this and the grammar change has no effect)
3. Add a `{ label, type, detail }` entry to `app/src/repulse/lisp-lang/completions.js`
4. Add an entry to `app/src/repulse/content/builtin_meta.edn` with `category`,
   `returns`, `side-effects`, `examples` (≥1) and `see-also`
5. **Run `npm run gen:ai-docs`** — regenerates `docs/ai/builtins.json`
6. Commit grammar, `parser.js`, `completions.js`, `builtin_meta.edn` and
   `builtins.json` together — CI fails on grammar or AI-docs drift.

### Rule 4 — Verify build tooling at session start

Before writing phase code, run on the unmodified branch:

```bash
npm run gen:grammar && npm run gen:ai-docs && npm run test
```

A broken script masquerades as a data problem. If a script hangs or exits 1 with no
output, **the script is broken — don't fix the input data first.** Isolate each step
with explicit prints and a subprocess wrapper capturing stderr and exit code.
`setTimeout` can't detect synchronous infinite loops — use an iteration-counter guard.
