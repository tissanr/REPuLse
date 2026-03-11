# REPuLse — Claude Code Prompt (delivered)

## Vision

Build **REPuLse** — a browser-based live coding instrument where music is written in a small,
purpose-built Lisp. Think Strudel.cc, but the user-facing language is a minimal Lisp (not
JavaScript), and the pattern engine is implemented in ClojureScript.

The name: **REPL** + **pulse** (rhythm, heartbeat).

---

## Goal for this session

By the end of this session, the following must work:

1. A browser opens
2. The user sees a minimal REPL / code editor
3. They type a REPuLse-Lisp expression like `(seq :bd :sd :bd :sd)` and evaluate it
4. A sound plays — a kick and snare alternating in a loop
5. They can type `(stop)` to stop playback

That's it. First sound. Everything else is secondary.

---

## Repository Structure

Set up a **monorepo** with this shape:

```
repulse/
├── packages/
│   ├── core/        # Pattern algebra — pure CLJS, no DOM, no audio
│   └── lisp/        # REPuLse-Lisp reader + evaluator (CLJS)
├── app/             # Browser app — Svelte, wires everything together
├── package.json     # Workspace root (npm workspaces)
└── shadow-cljs.edn  # Shared shadow-cljs config
```

Do **not** add the Rust/WASM audio layer yet — use the Web Audio API directly from ClojureScript
for now. Keep it simple enough that a first sound works.

---

## Package: `core`

The pattern algebra. A **Pattern** is a pure function from a time span to a list of events.

### Key types

```clojure
;; A time span: rational start and end
{:start 0 :end 1}

;; An event
{:value   :bd          ; the payload — keyword, number, map, anything
 :whole   {:start 0 :end 1}   ; the "logical" time of the event
 :part    {:start 0 :end 1}}  ; the "active" slice (may be smaller than whole)
```

### Core functions to implement

```clojure
;; Create a pattern that repeats a single value every cycle
(pure value) => Pattern

;; Sequence: spread values evenly across one cycle
(seq & values) => Pattern

;; Stack: layer multiple patterns simultaneously
(stack & patterns) => Pattern

;; Query a pattern for events in a time span
(query pattern timespan) => [Event]

;; Tempo transformations
(fast factor pattern) => Pattern
(slow factor pattern) => Pattern

;; Reverse a pattern within each cycle
(rev pattern) => Pattern

;; Apply a function to every event value
(fmap f pattern) => Pattern

;; Every nth cycle, apply a transformation
(every n transform pattern) => Pattern
```

Use **rational arithmetic** for time. Represent rationals as `[numerator denominator]` or use a
small rational library — do not use floats for time calculations.

Write **unit tests** for all core functions using `cljs.test`.

---

## Package: `lisp`

A small Lisp interpreter. The goal is not a general-purpose language — it's a DSL shell around
the `core` pattern functions.

### Reader

Parse REPuLse-Lisp source text into ClojureScript data structures:

| Lisp syntax     | CLJS representation       |
|-----------------|---------------------------|
| `42`            | `42` (number)             |
| `3.14`          | `3.14` (number)           |
| `"hello"`       | `"hello"` (string)        |
| `:bd`           | `:bd` (keyword)           |
| `foo`           | `'foo` (symbol)           |
| `(a b c)`       | `'(a b c)` (list)         |
| `[a b c]`       | `[a b c]` (vector)        |
| `; comment`     | ignored                   |

### Evaluator

A simple environment-based evaluator. The initial environment contains:

- All functions from `core` (`seq`, `stack`, `fast`, `slow`, `rev`, `every`, `pure`)
- Basic arithmetic: `+`, `-`, `*`, `/`
- `fn` / `lambda` for anonymous functions
- `def` for top-level bindings
- `let` for local bindings
- `stop` — stops current playback

No macros required yet. No tail-call optimization required yet.

### Error handling

Return structured errors with a message and, if possible, the source position:

```clojure
{:error "Undefined symbol: fsat — did you mean fast?"}
```

Fuzzy-match undefined symbols against known names for typo hints.

---

## App

A minimal browser application. Stack: **Svelte 5** + **shadow-cljs**.

### Layout

```
┌─────────────────────────────────┐
│  REPuLse                    ●   │  ← dot = playing indicator
├─────────────────────────────────┤
│                                 │
│  (seq :bd :sd :bd :sd)          │  ← CodeMirror 6 editor
│                                 │
├─────────────────────────────────┤
│  > evaluated: Pattern           │  ← output / error line
└─────────────────────────────────┘
```

- **Ctrl+Enter** or **Cmd+Enter** evaluates the current expression
- Output line shows either the result type or a red error message
- Playing indicator pulses on each beat

### Audio

Use the **Web Audio API** directly — no external audio library for now.

When a Pattern is evaluated:
1. Query the pattern for events in the current cycle
2. Schedule `OscillatorNode` or `AudioBufferSourceNode` hits using `audioContext.currentTime`
3. Loop: at the end of each cycle, query the next cycle and schedule ahead

For the first sound, it's fine to use a simple sine/square oscillator per event value:
- `:bd` → low sine burst (~60hz, 0.1s)
- `:sd` → noise burst (0.1s)
- `:hh` → high noise burst (0.05s)
- Any keyword not recognized → default sine at 440hz
- Numbers → treated as frequency in hz

Implement a simple **clock** using `setInterval` that looks ahead by ~100ms and schedules
upcoming events. This is the standard WebAudio scheduling pattern (see Chris Wilson's
"A Tale of Two Clocks").

---

## Constraints & style

- **ClojureScript throughout** (except Svelte templates)
- **shadow-cljs** as the build tool — one `shadow-cljs.edn` at the root
- **No external ClojureScript libraries** beyond `cljs.core` for `core` and `lisp` packages
- **npm workspaces** to link packages together
- Prefer **pure functions** — keep side effects at the edges (audio, DOM)
- The `core` package must have **zero** knowledge of audio, DOM, or the Lisp layer
- The `lisp` package may depend on `core` but not on `app`

---

## Definition of Done

- [ ] `npm install` at the root works
- [ ] `npx shadow-cljs watch app` starts a dev server
- [ ] Browser opens, editor is visible
- [ ] Typing `(seq :bd :sd :bd :sd)` and pressing Ctrl+Enter produces a looping kick/snare pattern
- [ ] Typing `(stop)` stops playback
- [ ] Typing a syntax error shows a red error message
- [ ] All `core` unit tests pass (`npx shadow-cljs compile test && node out/test.js`)

---

## What NOT to build yet

- Rust/WASM audio layer
- MIDI or OSC output
- Sample loading
- Multiple simultaneous patterns
- Persistence / sharing
- Mobile layout
- Anything not needed for first sound