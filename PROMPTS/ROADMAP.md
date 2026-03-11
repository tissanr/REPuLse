# REPuLse — Implementation Roadmap

> Last updated: March 2026

---

## Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Implemented & committed |
| 🔄 | In progress / next step |
| 📋 | Planned, prompt written |
| 💡 | Discussed, no prompt yet |

---

## Phase 1 — Monorepo Scaffold + First Sound ✅

**Commit:** `edf77bd` — *Initial implementation of REPuLse*

### Architecture

- ✅ Monorepo with npm workspaces (`packages/core`, `packages/lisp`, `app/`)
- ✅ `shadow-cljs.edn` as the single build entry point
- ✅ `npx shadow-cljs watch app` starts dev server on port 3000

### `packages/core` — Pattern algebra

- ✅ Rational arithmetic (`[numerator denominator]` vectors — no floats for time)
- ✅ `TimeSpan` and `Event` data structures
- ✅ `pure` — one value per cycle
- ✅ `seq*` — N values spread evenly across one cycle
- ✅ `stack*` — layer multiple patterns simultaneously
- ✅ `fast` / `slow` — tempo transformations
- ✅ `rev` — reverse a pattern within each cycle
- ✅ `every` — apply a transformation every N cycles
- ✅ `fmap` — apply a function to all event values
- ✅ Unit tests with `cljs.test` (6 tests, 18 assertions — all green)

### `packages/lisp` — REPuLse-Lisp interpreter

- ✅ Recursive descent reader (keywords, numbers, strings, symbols, lists, vectors)
- ✅ Environment-based evaluator
- ✅ Special forms: `def`, `let`, `fn` / `lambda`, `if`, `do`
- ✅ Levenshtein typo hints on unknown symbols
- ✅ Structured error values `{:error "..."}`
- ✅ Line comments with `;`

### `app/` — Browser app

- ✅ CodeMirror 6 editor with One Dark theme
- ✅ Ctrl+Enter / Cmd+Enter evaluates the buffer
- ✅ Web Audio API lookahead clock (Chris Wilson pattern, 25ms interval, 200ms lookahead)
- ✅ Synthesised base voices: `:bd` (kick), `:sd` (snare), `:hh` (hi-hat)
- ✅ Numbers treated as Hz frequency (sine tone)
- ✅ Playing dot pulses on the beat
- ✅ Error output in the footer line (red / green)

---

## Phase 1.5 — Sample Loader & UI Polish ✅

**Commit:** `801d1cb` — *Add sample loader, play/stop button, and usage docs*

### Sample loader

- ✅ Lightweight sample loader (`samples.cljs`) — no external framework
- ✅ Fetches manifests from Strudel's CDN on startup:
  - `https://strudel.b-cdn.net/dirt-samples.json` (TidalCycles Dirt-Samples)
  - `https://strudel.b-cdn.net/tidal-drum-machines.json` (drum machine library)
- ✅ Lazy loading: buffers cached, in-flight requests deduplicated
- ✅ Fire-and-forget playback with `max(scheduledTime, currentTime)` timing safeguard
- ✅ Falls back to synthesis while samples are still loading

### New Lisp functions

- ✅ `(sound :bank n)` — pick a specific sample index from a bank
- ✅ `(bpm N)` — set tempo (default: 120 BPM = 2.0s/cycle)
- ✅ `:_` — rest / silence in sequences

### Bug fix

- ✅ Cycle duration corrected: 0.5s → 2.0s (was 480 BPM, now correct 120 BPM)

### UI

- ✅ Play / stop button in the header
- ✅ Button style (green / red, pulses while playing)

### Documentation

- ✅ `docs/USAGE.md` — full language reference with examples
- ✅ `docs/ARCHITECTURE.md` — system overview, data flow diagram, build system
- ✅ `CLAUDE.md` — persistent project knowledge for Claude Code sessions
- ✅ `.gitignore` — node_modules, WASM output, caches
- ✅ `README.md` — quickstart + developer setup guide

---

## Phase 2 — Rust/WASM Synthesis 🔄

**Prompt:** `PROMPTS/phase-2-rust-wasm.md`
**Status:** Prompt written, not yet implemented

### Goal

First sound, but better — synthesis moves to Rust, JS scheduler stays.

### Planned

- 📋 `packages/audio/` — Rust crate compiled via `wasm-pack`
- 📋 `AudioEngine.trigger(value, time, duration)` — public WASM API
- 📋 `:bd` — kick drum (sine sweep 150→40 Hz + gain envelope)
- 📋 `:sd` — snare (bandpass-filtered noise + sine body)
- 📋 `:hh` — closed hi-hat (highpass noise, 40ms decay)
- 📋 `:oh` — open hi-hat (highpass noise, 300ms decay)
- 📋 Numbers → sine tone at given frequency
- 📋 Noise generation in Rust (LCG, no `Math.random()`)
- 📋 WASM runs on the main thread (AudioWorklet is Phase 3)
- 📋 Graceful fallback to CLJS synthesis if WASM fails to load
- 📋 `npm run build:wasm` + `npm run dev` as top-level build commands

**Migration path to Phase 3:** the `trigger(value, time, duration)` API stays identical.

---

## Phase 3 — AudioWorklet 📋

**Prompt:** `PROMPTS/phase-3-audioworklet.md`

### Goal

WASM runs inside an AudioWorklet — no GC jank on the main thread.

### Planned

- 📋 `app/public/worklet.js` — `AudioWorkletProcessor` that hosts the WASM module
- 📋 WASM initialised inside the Worklet context
- 📋 Communication via `MessagePort` (main thread → worklet)
- 📋 Three-tier fallback: AudioWorklet+WASM → main-thread WASM → CLJS synthesis
- 📋 Startup log: `[REPuLse] audio backend: audioworklet+wasm`
- 📋 Rust API: `AudioEngine::from_worklet()` constructor for worklet context

---

## Phase 4 — Live Performance Features 📋

**Prompt:** `PROMPTS/phase-4-live-features.md`

### Goal

A real live coding session: multiple patterns, tap BPM, MIDI, shareable URLs.

### Planned

#### Multiple simultaneous patterns

- 📋 `(play :name pattern)` — named pattern slot
- 📋 `(mute :name)` / `(unmute :name)` — silence / restore a slot
- 📋 `(solo :name)` — play only this slot
- 📋 `(clear :name)` / `(clear)` — remove slot(s)
- 📋 `(slots)` — return list of active slot names

#### Tempo

- 📋 Tap tempo button in the header (4 clicks → average BPM)
- 📋 MIDI clock sync (24 pulses per quarter note via Web MIDI API)
- 📋 `(midi-sync true/false)` in the Lisp environment

#### Session persistence

- 📋 State encoded as Base64 in the URL fragment (`#v1:...`)
- 📋 Share button copies the URL to clipboard
- 📋 Auto-save to `localStorage` every 5 seconds
- 📋 Restore on load (URL fragment takes precedence)

#### Visual timeline

- 📋 Mini piano-roll below the editor
- 📋 Shows events in the current cycle as coloured blocks
- 📋 Scrolling playhead with beat numbers (1–4)
- 📋 Click a slot name to mute / unmute

---

## Discussed — No Phase Yet 💡

### Lisp language

- 💡 Mini-notation reader macro: `(~ "bd sd [bd bd] sd")`
- 💡 Tail-call optimisation for recursive patterns
- 💡 Syntax highlighting as a CodeMirror language extension for REPuLse-Lisp

### Audio

- 💡 Local sample upload from the file system
- 💡 MIDI output (Web MIDI API)
- 💡 OSC output (WebSocket bridge to SuperCollider)

### UI / UX

- 💡 Mobile layout (deliberately desktop-first for now)
- 💡 Collaborative live coding (Y.js multi-user REPL)
- 💡 Export to audio file
- 💡 Dark / light theme toggle

---

## Architecture Decision Log

| Decision | Rationale |
|---|---|
| ClojureScript for pattern engine, not Rust | Better fit for pattern algebra; persistent data structures are a natural match |
| Rust/WASM only in the AudioWorklet long-term | GC pauses on the audio thread are the real problem, not pattern evaluation |
| Hand-written mini-Lisp, not JS eval | Better error messages, clean security model, no Babel hacks |
| shadow-cljs as sole build tool | Single entry point, npm integration out of the box |
| Strudel CDN for samples | Established source, maintained manifest, no self-hosting required |
| Monorepo over multi-repo | Early stage: cross-package refactoring without Git complexity |
| Phase 2 before Phase 3 for WASM | Learn the WASM↔CLJS boundary before adding AudioWorklet complexity on top |

---

## Repository

```
https://github.com/tissanr/REPuLse
```

### Commit history

```
main
├── edf77bd  Initial implementation of REPuLse          [Phase 1]
├── 801d1cb  Add sample loader, play/stop button, docs   [Phase 1.5]
└── 5aa53fc  Add phase 3/4 prompts and architecture doc
```