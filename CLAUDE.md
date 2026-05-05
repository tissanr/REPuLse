# REPuLse

> A Lisp that beats.

Browser-based live coding instrument for algorithmic music. The user writes patterns in
**REPuLse-Lisp** — a small, purpose-built Lisp — and hears them as sound in real time.

Inspired by Strudel.cc and Tidal Cycles, but with a Lisp as the user-facing language
instead of JavaScript, and a ClojureScript pattern engine at the core.

---

## What it is

```
(stack
  (seq :bd :sd :bd :sd)
  (fast 2 (seq :hh :oh))
  (every 4 rev (seq :c3 :eb3 :g3)))
```

That's REPuLse. A REPL in the browser. You type, you hear. No compile step, no build,
no file system. Just patterns and sound.

---

## Architecture

Three layers, strictly separated:

```
┌─────────────────────────────────────────────────┐
│  REPuLse-Lisp  (user-facing language)           │
│  packages/lisp — Reader + Evaluator (CLJS)      │
├─────────────────────────────────────────────────┤
│  Pattern Engine  (pure functions of time)       │
│  packages/core — Pattern algebra (CLJS)         │
├─────────────────────────────────────────────────┤
│  Audio Layer  (sound synthesis + scheduling)    │
│  packages/audio — Rust/WASM                     │
│  app/audio.cljs — JS scheduler + WASM bridge    │
└─────────────────────────────────────────────────┘
```

**Dependency rule:** each layer may only depend on layers below it.
`core` has zero knowledge of `lisp`, audio, or DOM. Ever.

---

## Repository structure

```
repulse/
├── packages/
│   ├── core/          # Pattern algebra — pure CLJS, no DOM, no audio
│   ├── lisp/          # REPuLse-Lisp reader + evaluator (CLJS)
│   └── audio/         # Rust crate → wasm-pack → WASM module
├── app/               # Browser app (CLJS + vanilla DOM) — wires everything together
├── PROMPTS/           # Phase prompts (one per Claude Code session)
├── docs/              # Architecture, usage, and future-features docs
├── README.md          # Quick start and language reference
├── ROADMAP.md         # Detailed phase history and delivery status
├── CLAUDE.md          # This file
├── package.json       # npm workspaces root
└── shadow-cljs.edn    # Shared CLJS build config
```

---

## Core concepts

### Pattern

A **Pattern** is a pure function from a `TimeSpan` to a list of `Event`s.

```clojure
;; TimeSpan
{:start 0N :end 1N}   ; rational numbers — never floats

;; Event
{:value :bd                      ; payload — keyword, number, map
 :whole {:start 0N :end 1N}      ; logical time of the full event
 :part  {:start 0N :end 1N}}     ; active slice (may differ from whole)
```

Time is always **rational arithmetic**. Never floats for time values.

### REPuLse-Lisp

A minimal Lisp. Not general-purpose — a DSL shell around the pattern functions.

| Concept        | Example                          |
|----------------|----------------------------------|
| Sequence       | `(seq :bd :sd :bd :sd)`          |
| Stack          | `(stack pat-a pat-b)`            |
| Speed          | `(fast 2 pattern)`               |
| Transformation | `(every 4 rev pattern)`          |
| Anonymous fn   | `(fn [p] (fast 2 p))`            |
| Binding        | `(def kick (seq :bd :bd :sd))`   |
| Mini-notation  | `(~ "bd sd [bd bd] sd")`         |

### Audio bridge

ClojureScript scheduler (setInterval lookahead, ~100ms) queries patterns each cycle
and calls into Rust/WASM for synthesis:

```clojure
(trigger! :bd time duration)   ; schedules one sound event
(stop!)                        ; stops all playback
```

---

## Technology

| Concern              | Technology                        |
|----------------------|-----------------------------------|
| Pattern engine       | ClojureScript                     |
| Lisp interpreter     | ClojureScript                     |
| Audio synthesis      | Rust → WASM (via wasm-pack)       |
| Audio scheduling     | Web Audio API + setInterval (JS)  |
| Browser app          | ClojureScript + CodeMirror 6 + vanilla DOM |
| Build tool (CLJS)    | shadow-cljs                       |
| Build tool (Rust)    | wasm-pack (`--target web`)        |
| Package management   | npm workspaces                    |

---

## Pre-push verification (MANDATORY)

Before committing and pushing any ClojureScript changes, **always** run both checks:

```bash
npm run test                        # unit tests (core + lisp + session)
npx shadow-cljs compile app         # full app compile — catches bracket errors,
                                    # missing requires, and type errors in app/ files
                                    # that the :test target does not compile
```

The `:test` shadow-cljs target compiles only `packages/core`, `packages/lisp`, and the
session-test namespace. It does **not** compile `app/src/repulse/env/builtins/` or any
other app-layer namespace. Bracket mismatches, unresolved namespaces, and unused
requires in those files are only caught by compiling the `:app` target.

**Never push without running both commands.** CI runs `shadow-cljs release app` and
`clj-kondo` on the full source tree and will catch errors that the test runner misses.

---

## Coding conventions

- **Pure functions by default.** Side effects only at the edges: audio output, DOM.
- **Rational time everywhere.** Use `clojure.core` rationals (`1/4`, `3N`) in `core`.
  Never `(/ 1.0 4.0)` for time values.
- **No external CLJS libraries** in `core` or `lisp`. Only `cljs.core` and `cljs.test`.
- **No external Rust audio libraries** in `audio`. Only `web-sys` Web Audio API bindings.
- **Errors surface as typed values at the boundary.** Reader/evaluator internals may throw
  `ex-info`, but `repulse.lisp.core/eval-string` converts failures into a typed
  eval-error result for the app layer.
- **Fuzzy-match typos** in the evaluator. If a symbol is undefined, suggest the closest
  known name.
- **Tests for core.** Every function in `packages/core` has a unit test in `cljs.test`.

---

## Build

```bash
# First time
npm install
npm run build:wasm       # compiles Rust → WASM

# Development
npm run dev              # shadow-cljs watch app only (no WASM build)
npm run dev:full         # build:wasm + shadow-cljs watch app

# Tests
npm run test             # shared cljs.test runner for core + lisp + app session tests
npm run test:rust        # Rust AudioEngine unit tests
npm run test:e2e         # Playwright browser offline audio tests
npm run test:all         # CLJS + Rust + browser offline tests
./scripts/test-all.sh    # same full TEST1 verification suite

# Lezer grammar (syntax highlighting) — run after editing repulse-lisp.grammar
npm run gen:grammar      # regenerates parser.js + parser.terms.js
```

TEST1 browser audio tests use a dedicated `:test-harness` build with
`OfflineAudioContext` and the `"offline-js"` backend. They verify the offline
render/export-style path, not production browser AudioWorklet + WASM capture.
That live production path is tracked separately in TEST2.

### Syntax highlighting + completions checklist

When adding a new built-in name that should be highlighted and autocompleted in the editor:

1. Add the name to `BuiltinName` in `app/src/repulse/lisp-lang/repulse-lisp.grammar`
2. **Run `npm run gen:grammar`** — this overwrites the committed `parser.js`
3. Add a `{ label, type, detail }` entry in `app/src/repulse/lisp-lang/completions.js`
4. Commit both `repulse-lisp.grammar` **and** the regenerated `parser.js`

Skipping step 2 means the grammar change has no effect at runtime.

---

## Dev server

Use `preview_start` to start the dev server before verifying UI changes. The server
runs on port 3000 via `npm run dev` (shadow-cljs watch only — does **not** build WASM).
Use `npm run dev:full` on the first run of a session to build WASM first, then start
the watcher. After code edits, follow the standard `<verification_workflow>` using the
preview tools.

### Worktrees and Vercel

This repo uses git worktrees. Each worktree needs its own one-time setup:

```bash
npm install                  # node_modules are not shared across worktrees
vercel link                  # writes .vercel/project.json (gitignored)
vercel env pull .env.local   # pulls Vercel env vars (gitignored)
```

`vercel link` can be run in as many worktrees as needed — they all point at the
same Vercel project. `.vercel/` and `.env.local` are gitignored so each worktree
holds its own copy independently.

**Are the env vars required for local dev?**
Only if you're working on auth or community features (Phase S2/S3). Core audio,
pattern engine, and editor work entirely client-side with no env vars needed.

---

## Phase status

| Phase | Description                                                    | Status       |
|-------|----------------------------------------------------------------|--------------|
| 1     | Monorepo scaffold + first sound (JS)                           | ✓ delivered  |
| 2     | Rust/WASM synthesis, JS scheduler stays                        | ✓ delivered  |
| 3     | Rust/WASM moves into AudioWorklet                              | ✓ delivered  |
| 5     | Active code highlighting — editor flashes playing tokens       | ✓ delivered  |
| 6a    | Plugin system + visual plugins (oscilloscope, AnalyserNode)    | ✓ delivered  |
| 6b    | Effect plugins — reverb, delay, filter, compressor, dattorro  | ✓ delivered  |
| 8     | Song arrangement — `arrange`, `play-scenes`, map literals      | ✓ delivered  |
| 10    | Syntax highlighting — Lezer grammar, bracket matching, oneDark | ✓ delivered  |
| 9     | External sample repos — `(samples! "github:owner/repo")`      | ✓ delivered  |
| C     | Code completion — built-in docstrings + live `def` tracking    | ✓ delivered  |
| A     | More effects — chorus, phaser, tremolo, overdrive, bitcrusher  | ✓ delivered  |
| D     | Editor persistence — localStorage save/restore across reloads  | ✓ delivered  |
| E     | Session context panel — live bindings, effects, BPM sidebar    | ✓ delivered  |
| F     | Drum machine bank prefix — `(bank :AkaiLinn)` scope shorthand  | ✓ delivered  |
| G     | Music theory — note keywords, `scale`, `chord`, `transpose`    | ✓ delivered  |
| H     | Per-event parameters — `amp`, `attack`, `decay`, `pan`, `->>`  | ✓ delivered  |
| 4     | Named tracks, command bar, tap BPM, MIDI clock, session URLs   | ✓ delivered  |
| I     | Pattern combinators — euclidean, cat, late, sometimes, jux, off | ✓ delivered  |
| J     | Onboarding — demo templates, hover docs, interactive tutorial  | ✓ delivered  |
| K     | Mini-notation & sharing — `(~ "bd sd")`, gist import, WAV export | ✓ delivered |
| L     | Per-track audio — track FX chains, sample params, sidechain   | ✓ delivered  |
| M     | Lisp superpowers — defsynth, defmacro, loop/recur, rationals  | ✓ delivered  |
| N     | MIDI & I/O — controller input, note out, clock out, Freesound | ✓ delivered  |
| B     | Richer visuals — audiomotion-analyzer spectrum, p5.js support  | ✓ delivered  |
| E2    | Live session dashboard — per-track params, conditional FX, sources | ✓ delivered  |
| E2b   | Parameter sliders — dashboard sliders that update code live    | ✓ delivered  |
| 10a   | Editor diagnostics — red squiggle underlines on eval errors    | ✓ delivered  |
| D2    | Full session persistence — all state in localStorage + reset!  | ✓ delivered  |
| N1    | MIDI CC mapping — bind controller knobs to any parameter      | planned      |
| O     | Platform — PWA, embeddable component, collaboration, mobile   | planned      |
| O1    | Embeddable component — `<repulse-editor>` custom element, snippet attr | ✓ delivered  |
| T1    | Parameter transitions — `tween` built-in, WASM per-sample ramp | ✓ delivered  |
| P     | Modular routing — busses, control rate, general envelopes      | ✓ delivered  |
| P2    | Modular effect routing — reorderable chains, parallel FX, sends | planned      |
| J2    | Contextual insertion buttons — hover `+` on parens for wrap/chain | ✓ delivered      |
| R0    | Correctness & safety — and/or short-circuit, BPM clamp, plugin consent | ✓ delivered  |
| S1    | Local snippet library — curated JSON, browse/preview/insert    | ✓ delivered  |
| R1    | Refactor — split app.cljs into focused modules                 | ✓ delivered  |
| S2    | Backend & auth — Vercel + Supabase, GitHub OAuth, REST API     | ✓ delivered  |
| S3    | Community snippets — submit, star, rank, usage tracking        | ✓ delivered  |
| S4    | Snippet audio preview — sandboxed eval, waveforms, indicators  | ✓ delivered  |
| R2    | Refactor — decompose Lisp and app builtin environments          | ✓ delivered  |
| DST1  | Distortion — soft clipping (:distort, tanh/sigmoid/atan)       | ✓ delivered  |
| DST2  | Distortion — asymmetric clipping (:asym) + DC blocker          | ✓ delivered  |
| DST3  | Distortion — multi-stage amp simulation (:amp-sim)             | ✓ delivered  |
| DST4  | Distortion — oversampling wrapper (:oversample 1/2/4)          | planned      |
| DST5  | Distortion — waveshaper LUT (:waveshape, chebyshev/fold/bitcrush) | planned   |
| DST6  | Distortion — cabinet simulation (:cab, ConvolverNode + IRs)    | planned      |
| CI1   | CI pipeline — GitHub Actions: tests, lint, Rust, grammar drift | ✓ delivered  |
| TEST1 | Automated audio verification — CLJS, Rust DSP, offline PCM tests | ✓ delivered  |
| TEST2 | Production browser audio capture — AudioWorklet/WASM PCM verification | planned      |
| HRD1  | Hardening — AST editor patching, fetch validation, reproducible Rust builds | ✓ delivered  |
| HRD2  | Security hardening — XSS, RLS, CORS, input validation, CSP    | ✓ delivered  |
| HRD3  | Interface specs — plugins, pattern data, sessions, FX, MIDI   | planned      |
| DOC1  | User docs — split manual, tutorials, cookbook, reference       | planned      |
| R3    | Refactor — purify hand-written JS into CLJS where appropriate  | planned      |
| PLUG1 | Drop-in plugins — drag local JS/package plugins into the app   | planned      |
| AI1   | AI knowledge base — /docs/ai/*.json, gen:ai-docs, help-export  | planned      |
| AI2   | AI assistant panel — BYO key, streaming chat, opt-in feature   | planned      |
| AI3   | Tool-using agent — read_buffer, propose_edit, eval_preview     | planned      |
| AI4   | AI safety & limits — budgets, injection guards, auto-apply     | planned      |

See `PROMPTS/` for detailed phase specifications and `ROADMAP.md` for full delivery notes.

---

## Phase lifecycle rules (IMPORTANT — follow these every time)

These rules apply in every Claude Code session. They are not optional.

**Branch protection:** `main` is protected. All changes go via Pull Request — never commit directly to `main`.

### Rule 1 — Creating a new phase prompt

When writing a new `PROMPTS/PHASE-*.md` file, **always also**:

1. Add a row to the phase status table above:
   ```
   | XYZ   | Short description                                              | planned      |
   ```
2. Add a `## Phase XYZ — Title 📋 *planned*` section to `ROADMAP.md` with:
   - 2–4 bullet points summarising key additions
   - A `See full spec: [PROMPTS/PHASE-XYZ.md](PROMPTS/PHASE-XYZ.md)` link
3. Commit all three files together.

### Rule 2 — Implementing a phase

When the code for a phase is complete and working, **always also**:

1. Change the phase row in the table above from `planned` to `✓ delivered`.
2. In `ROADMAP.md`, change the heading from `📋 *planned*` to `✅ *delivered*` and
   add a **Delivered:** section listing what was actually built (match the style of
   existing delivered phases in ROADMAP.md).
3. Update `docs/USAGE.md`: add every new built-in, effect, or parameter to the
   relevant reference tables and add at least one usage example.
4. Update `README.md` if the phase adds user-facing syntax or changes the quick-start
   example.
5. Commit all documentation changes in the same commit as (or immediately after) the
   implementation commit.
6. Run `npm run test:all` before marking the phase delivered. For docs-only phases,
   run at least `npm test` and explain why the full suite was not needed.

### Rule 3 — Verification is part of done

A phase implementation is **not complete** until the relevant verification command
has passed:

- Run `npm run test:all` for code phases. This covers CLJS tests, Rust AudioEngine
  tests, and browser offline audio tests.
- Run `npm test` at minimum for docs-only phases or prompt-only updates.
- If `npm run test:all` cannot run because of environment limits, state the exact
  blocker and run the strongest available subset.

Do not mark a phase delivered in this file or `ROADMAP.md` until verification is
complete or the exception is documented.

### Rule 4 — Documentation is part of done

A phase is **not complete** until Rules 2.1–2.5 are satisfied. "The code works" is
necessary but not sufficient. If you finish implementing a phase and realise the docs
haven't been updated, do not mark the task done — update the docs first.

### Rule 5 — AI docs are part of done

When adding a new built-in name that should be highlighted and autocompleted in the editor:

1. Add the name to `BuiltinName` in `app/src/repulse/lisp-lang/repulse-lisp.grammar`
2. **Run `npm run gen:grammar`** — overwrites the committed `parser.js`
3. Add a `{ label, type, detail }` entry in `app/src/repulse/lisp-lang/completions.js`
4. **Add an entry to `app/src/repulse/content/builtin_meta.edn`** with `category`,
   `returns`, `side-effects`, `examples` (≥1), and `see-also`
5. **Run `npm run gen:ai-docs`** — overwrites `docs/ai/builtins.json`
6. Commit grammar, `parser.js`, `completions.js`, `builtin_meta.edn`, and
   `builtins.json` together

Skipping steps 4–5 means the AI knowledge base drifts from the real surface and CI
will fail on the `ai-docs` drift check.

---

## What REPuLse is not

- Not a general-purpose Lisp. The language exists to describe music patterns.
- Not a DAW. No timeline, no arrangement, no export.
- Not a sample player (yet). Phase 1–3 are synthesis only.
- Not mobile-first. Desktop browser is the target.
