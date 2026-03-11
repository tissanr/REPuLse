# REPuLse — Rust/WASM Audio Layer (Phase 2) (delivered)

## Context

REPuLse is a browser-based live coding instrument with a Lisp user language and a
ClojureScript pattern engine. Phase 1 is complete:

- Monorepo at `repulse/` with `packages/core`, `packages/lisp`, and `app/`
- A browser REPL evaluates REPuLse-Lisp expressions into Patterns
- Patterns are queried each cycle and scheduled via Web Audio API + `setInterval`
- Synthesis is handled by simple `OscillatorNode` / noise constructs in ClojureScript

**This phase replaces the ClojureScript synthesis layer with a Rust/WASM module.**
The JS scheduler (`setInterval` lookahead clock) stays in place — only the sound
generation moves to Rust. This is a deliberate stepping stone toward a full
AudioWorklet architecture in Phase 3.

---

## Goal for this session

By the end of this session:

1. A Rust crate (`packages/audio`) compiles to WASM via `wasm-pack`
2. The WASM module is loaded in the browser and replaces the ClojureScript synthesis code
3. ClojureScript calls into WASM to trigger individual sound events
4. The following sounds work, noticeably better than the JS oscillator versions:
   - `:bd` — kick drum (sine sweep + click)
   - `:sd` — snare (noise burst + tone)
   - `:hh` — closed hi-hat (filtered noise, short)
   - `:oh` — open hi-hat (filtered noise, longer decay)
   - Numbers → sine tone at that frequency in hz
5. All existing REPL functionality continues to work

---

## Repository Structure

Add one new package:

```
repulse/
├── packages/
│   ├── core/          # unchanged
│   ├── lisp/          # unchanged
│   └── audio/         # NEW — Rust crate
│       ├── Cargo.toml
│       ├── src/
│       │   └── lib.rs
│       └── pkg/       # wasm-pack output (gitignored)
├── app/               # Svelte app — minimal changes to wire WASM
├── package.json
└── shadow-cljs.edn
```

---

## Package: `audio` (Rust)

### Cargo.toml

```toml
[package]
name = "repulse-audio"
version = "0.1.0"
edition = "2021"

[lib]
crate-type = ["cdylib"]

[dependencies]
wasm-bindgen = "0.2"
web-sys = { version = "0.3", features = [
  "AudioContext",
  "AudioNode",
  "AudioParam",
  "AudioDestinationNode",
  "OscillatorNode",
  "OscillatorType",
  "GainNode",
  "BiquadFilterNode",
  "BiquadFilterType",
  "AudioBuffer",
  "AudioBufferSourceNode",
] }
js-sys = "0.3"

[profile.release]
opt-level = 3
lto = true
```

### Public API (wasm-bindgen exports)

```rust
/// Initialize with a JS AudioContext passed from ClojureScript
#[wasm_bindgen]
pub fn init(ctx: AudioContext) -> AudioEngine;

#[wasm_bindgen]
impl AudioEngine {
    /// Trigger a sound event at a specific AudioContext time.
    /// value: keyword string (":bd", ":sd", ":hh", ":oh") or frequency as string ("440")
    /// time: AudioContext.currentTime value (f64) — schedule ahead
    /// duration: in seconds
    pub fn trigger(&self, value: &str, time: f64, duration: f64);

    /// Stop all currently playing sounds immediately
    pub fn stop_all(&self);
}
```

### Synthesis implementations

Implement each drum sound using only the Web Audio API nodes available via `web-sys`.
No sample loading — all synthesis.

**`:bd` — Kick drum**
- `OscillatorNode` (sine), frequency envelope: 150hz → 40hz over ~50ms
- `GainNode` envelope: fast attack (1ms), exponential decay (~300ms)
- Pitch sweep via `frequency.exponentialRampToValueAtTime`

**`:sd` — Snare**
- White noise via `AudioBuffer` filled with `Math.random()` values (generate in Rust)
- `BiquadFilterNode` (bandpass, ~200hz)
- `GainNode` envelope: ~200ms decay
- Optional: mix in a short sine tone at ~180hz for body

**`:hh` — Closed hi-hat**
- White noise, `BiquadFilterNode` (highpass, ~8000hz)
- Very short decay: ~40ms

**`:oh` — Open hi-hat**
- Same as `:hh` but longer decay: ~300ms

**Numbers / frequencies**
- `OscillatorNode` (sine), fixed frequency, short gain envelope (~200ms)

### Noise generation

Generate white noise buffers in Rust using a simple LCG random number generator —
do not use `Math.random()` from JS for this. Keep a small pre-generated noise buffer
(e.g. 2 seconds at 44100hz) and reuse it via `AudioBufferSourceNode` with random offsets.

```rust
fn generate_noise_buffer(ctx: &AudioContext, duration_secs: f32) -> AudioBuffer {
    let sample_rate = ctx.sample_rate();
    let length = (sample_rate * duration_secs) as u32;
    // fill with LCG noise
    // return AudioBuffer
}
```

---

## Integration: ClojureScript → WASM

### Loading the WASM module

In `app/src/repulse/app/audio.cljs`, replace the current synthesis functions with WASM calls:

```clojure
(ns repulse.app.audio
  (:require ["../../../packages/audio/pkg/repulse_audio.js" :as wasm]))

(defonce engine (atom nil))

(defn init! [audio-ctx]
  (-> (wasm/default)  ; wasm-pack init function
      (.then (fn [_]
               (reset! engine (wasm/init audio-ctx))))))

(defn trigger! [value time duration]
  (when-let [eng @engine]
    (.trigger eng (name value) time duration)))

(defn stop! []
  (when-let [eng @engine]
    (.stop_all eng)))
```

The existing scheduler loop in ClojureScript remains unchanged — it still queries patterns
and calls `trigger!` for each event. Only the synthesis backend changes.

### shadow-cljs config

Add the WASM package as an npm dependency. In `package.json` workspaces, add:

```json
"repulse-audio": "file:packages/audio/pkg"
```

Run `wasm-pack build packages/audio --target web` before `shadow-cljs watch app`.

Add a build script to `package.json`:

```json
"scripts": {
  "build:wasm": "wasm-pack build packages/audio --target web --out-dir pkg",
  "dev": "npm run build:wasm && npx shadow-cljs watch app"
}
```

---

## Error handling

- If WASM fails to load, fall back to the existing ClojureScript synthesis silently
- Log WASM errors to the browser console with prefix `[REPuLse audio]`
- `trigger` should never throw — wrap all Web Audio calls in Rust in a check for
  `AudioContext.state` and skip if suspended or closed

---

## Constraints

- **No external Rust audio libraries** — use only `web-sys` Web Audio API bindings
- **No sample files** — all synthesis, no loading `.wav` or `.mp3`
- **No AudioWorklet yet** — that is Phase 3. WASM runs on the main thread for now
- The ClojureScript scheduler interface must not change — `trigger!` and `stop!` are
  the only surface area
- `wasm-pack` version: use latest stable
- Target: `--target web` (ES module output, not `bundler`)

---

## Definition of Done

- [ ] `npm run build:wasm` succeeds without warnings
- [ ] `npm run dev` starts the dev server with WASM loaded
- [ ] Browser console shows no errors on load
- [ ] `(seq :bd :sd :bd :sd)` produces a clearly better kick/snare than Phase 1
- [ ] `(stack (seq :bd :sd) (fast 2 (seq :hh)))` plays kick, snare, and hi-hats together
- [ ] `(stop)` stops all sound immediately
- [ ] Evaluating a new pattern while one is playing switches without glitch
- [ ] Fallback to JS synthesis if WASM load fails (verify by temporarily breaking the import)

---

## Migration path to Phase 3 (do not implement now, just keep in mind)

Phase 3 will move the WASM module into an `AudioWorklet`. The key architectural change:

```
Phase 2 (now)               Phase 3
────────────────────         ────────────────────────────────
Main Thread                  Main Thread
  ClojureScript scheduler      ClojureScript scheduler
  → calls WASM directly        → posts events to Worklet

                               AudioWorklet Thread
                                 WASM module (same Rust code)
                                 processes events, synthesizes
```

To make this migration easy:
- Keep `AudioEngine` stateless between `trigger` calls — no shared mutable audio graph state
- Do not store `AudioContext` in Rust structs in a way that prevents moving to Worklet context
- The `trigger(value, time, duration)` API should remain identical in Phase 3