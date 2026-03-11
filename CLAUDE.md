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
├── app/               # Svelte 5 browser app — wires everything together
├── prompts/           # Phase prompts (one per Claude Code session)
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
| Browser app          | Svelte 5                          |
| Build tool (CLJS)    | shadow-cljs                       |
| Build tool (Rust)    | wasm-pack (`--target web`)        |
| Package management   | npm workspaces                    |

---

## Coding conventions

- **Pure functions by default.** Side effects only at the edges: audio output, DOM.
- **Rational time everywhere.** Use `clojure.core` rationals (`1/4`, `3N`) in `core`.
  Never `(/ 1.0 4.0)` for time values.
- **No external CLJS libraries** in `core` or `lisp`. Only `cljs.core` and `cljs.test`.
- **No external Rust audio libraries** in `audio`. Only `web-sys` Web Audio API bindings.
- **Errors are data.** Return `{:error "message"}` maps, not thrown exceptions, from
  the Lisp evaluator.
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
npm run dev              # build:wasm + shadow-cljs watch app

# Tests
npm run test:core        # cljs.test for packages/core
```

---

## Current phase

**Phase 2 — Rust/WASM Synthesis**
See `prompts/phase-2-rust-wasm.md`

### Phases

| Phase | Description                              | Status      |
|-------|------------------------------------------|-------------|
| 1     | Monorepo scaffold + first sound (JS)     | ✓ complete  |
| 2     | Rust/WASM synthesis, JS scheduler stays  | in progress |
| 3     | Rust/WASM moves into AudioWorklet        | not started |
| 4     | Mini-notation (`~` reader macro)         | not started |
| 5     | Sample loading                           | not started |
| 6     | Editor polish (CodeMirror extensions)    | not started |

---

## What REPuLse is not

- Not a general-purpose Lisp. The language exists to describe music patterns.
- Not a DAW. No timeline, no arrangement, no export.
- Not a sample player (yet). Phase 1–3 are synthesis only.
- Not mobile-first. Desktop browser is the target.