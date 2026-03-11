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

## Phase 2 — Rust/WASM Synthesis 🔧 *in progress* (source complete, build pending)

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
- [ ] `npm run build:wasm` completes without errors
- [ ] Browser console shows `[REPuLse] audio backend: wasm`
- [ ] `:bd :sd :hh :oh` sound noticeably better than Phase 1
- [x] `(stop)` stops sound immediately
- [x] Fallback verified (JS synthesis when WASM unavailable — confirmed Firefox, Chrome, Safari)

See full spec: [PROMPTS/phase-2-rust-wasm.md](PROMPTS/phase-2-rust-wasm.md)

---

## Phase 3 — AudioWorklet 📋 *planned*

Move the WASM module into an `AudioWorkletProcessor` so synthesis runs on the
dedicated audio thread — eliminating main-thread jank and GC pauses.

**Key changes:**
- `app/public/worklet.js` — AudioWorkletProcessor that loads WASM
- `MessagePort` channel: scheduler → Worklet for each event
- Three-tier fallback: Worklet+WASM → main-thread WASM → JS synthesis
- Dev tools Performance tab shows work on Audio thread, not Main

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

## Future ideas (unscheduled)

- Export to audio file (OfflineAudioContext)
- MIDI / OSC output
- Sample recording and upload
- Collaborative session (WebRTC)
- Mobile layout
- Syntax highlighting for REPuLse-Lisp in CodeMirror
- Community pattern library (share/import)
