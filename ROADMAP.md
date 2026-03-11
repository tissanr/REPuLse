# REPuLse — Roadmap

## Phase 1 — First Sound ✅ *delivered*

Browser REPL with ClojureScript pattern engine and Web Audio synthesis.

**Delivered:**
- Monorepo: `packages/core`, `packages/lisp`, `app/`
- REPuLse-Lisp: reader + evaluator with typo hints
- Pattern algebra: `seq`, `stack`, `pure`, `fast`, `slow`, `rev`, `every`, `fmap`
- Web Audio lookahead scheduler (Chris Wilson clock)
- Synthesized voices: kick, snare, hi-hat via oscillators/noise
- CodeMirror 6 editor, Ctrl+Enter evaluation
- ▶ play / ■ stop button
- Strudel CDN sample library (Dirt-Samples + Tidal Drum Machines)
- `(sound :bank n)` for indexed sample access, `(bpm N)` for tempo, `:_` rest
- Core unit tests (6 tests, 18 assertions)
- Safari compatibility: `webkitAudioContext` fallback, unconditional `.resume()`, improved first-play timing

---

## Phase 2 — Rust/WASM Synthesis ✅ *delivered*

Replace JS oscillator synthesis with a Rust/WASM module for better sound quality.
Sample loading from the Strudel CDN is unchanged.

**What changes:**
- `packages/audio/` — Rust crate compiles to WASM via `wasm-pack`
- `AudioEngine.trigger(value, time)` — WASM API called from ClojureScript
- Fallback chain: sample bank → WASM synth → JS synth
- Console shows `[REPuLse] audio backend: wasm` when active

**Synthesis improvements over Phase 1:**
- Kick: sine sweep 150 → 40 Hz, LCG noise-free envelope
- Snare: bandpass noise body + 180 Hz sine crack, tuned separately
- Hi-hat: closed (45 ms) and open (350 ms) as distinct voices
- All noise: LCG pre-generated 2s buffer in Rust (deterministic, no Math.random)

### Build requirements

```bash
# Install Rust (if not already)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source ~/.cargo/env
rustup target add wasm32-unknown-unknown

# Install wasm-pack
cargo install wasm-pack

# Build WASM and start dev server
npm run build:wasm
npx shadow-cljs watch app
```

Or in one step: `npm run dev:full`

**Definition of done:**
- [x] `npm run build:wasm` completes without errors
- [x] Browser console shows `[REPuLse] audio backend: wasm`
- [x] `:bd :sd :hh :oh` sound noticeably better than Phase 1
- [x] `(stop)` stops sound immediately
- [x] Fallback verified (JS synthesis when WASM unavailable — confirmed Firefox, Chrome, Safari)

See full spec: [PROMPTS/phase-2-rust-wasm.md](PROMPTS/phase-2-rust-wasm.md)

---

## Phase 3 — AudioWorklet ✅ *delivered*

Move the WASM module into an `AudioWorkletProcessor` so synthesis runs on the
dedicated audio thread — eliminating main-thread jank and GC pauses.

**What changed:**
- `packages/audio/` — Rust crate rewritten for PCM synthesis; `web-sys` removed entirely
- `AudioEngine` now accepts `sample_rate: f32` (not an `AudioContext`), generates raw `Float32Array` samples
- `app/public/worklet.js` — `AudioWorkletProcessor` that loads WASM via dynamic `import()` on the audio thread
- `MessagePort` channel: main thread → Worklet for `trigger`, `stop`, and `init` messages
- `app/src/repulse/audio.cljs` — `init-worklet!` replaces `init-wasm!`; `wasm-engine` atom replaced by `worklet-node` + `worklet-ready?`
- `app/public/index.html` — removed `<script type="module">` WASM bootstrap block
- Two-tier fallback: Worklet+WASM → JS synthesis (main-thread WASM tier removed)
- Console shows `[REPuLse] audio backend: audioworklet+wasm` when active

**Synthesis improvements over Phase 2:**
- All DSP runs on the dedicated audio render thread — zero main-thread audio work
- `process_block(n_samples, current_time)` called directly from `AudioWorkletProcessor.process()`
- Pending events are time-stamped and activated sample-accurately within each block
- Voice lifecycle managed in Rust; `voices.retain(|v| !v.is_silent())` keeps the voice list lean

**Definition of done:**
- [x] `npm run build:wasm` completes without errors (no `web-sys` dependency)
- [x] `app/public/worklet.js` registered as `repulse-processor`
- [x] Browser console shows `[REPuLse] audio backend: audioworklet+wasm`
- [x] `:bd :sd :hh :oh` play via PCM synthesis on the audio thread
- [x] `(stop)` clears all voices and pending events in the Worklet
- [x] JS synthesis fallback active when AudioWorklet is unavailable

See full spec: [PROMPTS/phase-3-audioworklet.md](PROMPTS/phase-3-audioworklet.md)

---

## Phase 4 — Live Performance Features 📋 *planned*

Named pattern slots, tap BPM, MIDI clock sync, session persistence, visual timeline.

**Key additions:**
- `(play :name pattern)` — run multiple named patterns simultaneously
- `(mute :name)` / `(solo :name)` / `(clear)` — slot control
- Tap tempo button + MIDI clock sync
- URL-based session save/restore (Base64 encoded)
- Mini piano-roll timeline per slot

See full spec: [PROMPTS/phase-4-live-features.md](PROMPTS/phase-4-live-features.md)

---

## Phase 5 — Active Code Highlighting 📋 *planned*

As a pattern plays, the editor highlights the exact tokens in the source code that are
generating the current sound — like Strudel.cc.

**How it works:**
- Reader extended to attach `{:from N :to N}` source ranges to every parsed form
- Evaluator propagates source ranges from literals into pattern events as `:source`
- Scheduler fires a `setTimeout` for each event, timed to the event's audio time
- CodeMirror applies a 120 ms CSS flash (`active-event` class) on the source range

**Example:** for `(seq :bd :sd :hh :sd)`, `:bd` flashes on beat 1, `:sd` on beats 2 and 4,
`:hh` on beat 3. Works with `stack`, `fast`, `every`, `def`, and numeric frequencies.

See full spec: [PROMPTS/phase-5-active-highlighting.md](PROMPTS/phase-5-active-highlighting.md)

---

## Future ideas (unscheduled)

- Export to audio file (OfflineAudioContext)
- MIDI / OSC output
- Sample recording and upload
- Collaborative session (WebRTC)
- Mobile layout
- Syntax highlighting for REPuLse-Lisp in CodeMirror
- Community pattern library (share/import)
