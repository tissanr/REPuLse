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

## Phase 5 — Active Code Highlighting ✅ *delivered*

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

## Phase 6a — Plugin System + Visual Plugins ✅ *delivered*

A lightweight plugin API and the first plugin type: visual plugins that receive the audio
stream and draw to a canvas — like Strudel's oscilloscope and spectrum views.

**Key additions:**
- Plugin registry (`plugins.cljs`) — register, list, unregister plugins by name
- Host API object passed to every plugin on `init` (audioCtx, analyser, registerLisp)
- Permanent `AnalyserNode` tap on the master bus (no audible change)
- Plugin panel DOM (collapsible, below the editor)
- Built-in **oscilloscope** plugin (`app/public/plugins/oscilloscope.js`)
- `(load-plugin url)` Lisp built-in — dynamically imports a third-party plugin

See full spec: [PROMPTS/phase-6a-plugins-visual.md](PROMPTS/phase-6a-plugins-visual.md)

---

## Phase 6b — Effect Plugins ✅ *delivered*

The second plugin type: Web Audio nodes inserted into the master signal chain, addressable
from the Lisp REPL via `(fx :name ...)`.

**Key additions:**
- Effect plugin interface — `createNodes`, `setParam`, `bypass`, `destroy`
- Graph manager (`fx.cljs`) — inserts/rewires effect nodes cleanly, with dry/wet bypass
- Five built-in effects: **reverb**, **delay**, **filter**, **compressor**, **dattorro-reverb**
- `(fx :reverb 0.4)` / `(fx :delay :wet 0.4 :time 0.25)` / `(fx :off :reverb)` Lisp built-ins
- Compressor reimplemented in ClojureScript (`app/src/repulse/plugins/compressor.cljs`)
- Dattorro plate reverb runs in its own `AudioWorkletProcessor` (high-quality, audio-thread)

See full spec: [PROMPTS/phase-6b-plugins-effects.md](PROMPTS/phase-6b-plugins-effects.md)

---

## Phase 8 — Song Arrangement Language ✅ *delivered*

A three-layer abstraction for composing full songs: motifs → sections → arrangement.
Keeps live coding readable as pieces grow beyond a handful of patterns.

**The three layers:**
```lisp
; Layer 1: motifs
(def kick  (seq :bd :_ :bd :_))
(def snare (seq :_ :sd :_ :sd))

; Layer 2: sections
(def verse  (stack kick snare))
(def chorus (stack kick snare (fast 2 (seq :hh :oh))))

; Layer 3: arrangement
(arrange [[intro 4] [verse 8] [chorus 8] [verse 8] [chorus 8]])
```

**Key additions:**
- `arrange* ` in `core.cljs` — time-shifting combinator; sections play in order, arrangement loops
- `(arrange [[pattern cycles] ...])` Lisp built-in — full arrangement with per-section cycle counts
- `(play-scenes [pat pat pat ...])` — each section plays for 1 cycle (Ableton scene-chain style)
- Map literals `{:key val}` in the reader + `get`, `assoc`, `merge` built-ins
- Parametric section factories: `(def make-verse (fn [opts] (if (get opts :dense false) ...)))`

See full spec: [PROMPTS/phase-8-song-arrangement.md](PROMPTS/phase-8-song-arrangement.md)

---

## Phase 10 — Syntax Highlighting ✅ *delivered*

A CodeMirror 6 language extension for REPuLse-Lisp — bracket matching and colour-coded
syntax using a hand-written Lezer grammar.

**Key additions:**
- Lezer grammar (`repulse-lisp.grammar`) — compiled once, parser committed to repo
- Highlight spec mapping Lezer node types to `@lezer/highlight` tags that integrate
  with the existing oneDark theme
- Keywords (`:bd`, `:sd`, …) in orange; built-ins (`seq`, `stack`, …) in purple;
  numbers gold; strings green; comments grey
- `(bracketMatching)` — clicking a delimiter highlights its pair
- `gen:grammar` npm script to regenerate the parser after grammar edits

See full spec: [PROMPTS/phase-10-syntax-highlighting.md](PROMPTS/phase-10-syntax-highlighting.md)

---

## Phase 9 — External Sample Repository Import 📋 *planned*

Load sample banks at runtime from any public GitHub repository or a manifest file,
directly from the REPL. Two manifest formats are supported.

**Key additions:**
- `(samples! "https://…/samples.edn")` — REPuLse Lisp manifest (native format, parsed by the existing reader)
- `(samples! "https://…/samples.json")` — Strudel-compatible JSON manifest (ecosystem compat)
- `(samples! "github:owner/repo")` — auto-discovers audio files via the GitHub public tree API, groups by folder name, registers as banks
- `(samples! "github:owner/repo/branch")` — target a specific branch
- `(sample-banks)` — list all currently registered bank names
- After loading, new banks usable immediately: `(seq :my-kick :my-snare)`

**REPuLse Lisp manifest format (`.edn`):**
```clojure
{:_base "https://raw.githubusercontent.com/user/repo/main/samples/"
 :kick  ["kick1.wav" "kick2.wav"]
 :snare ["snare1.wav" "snare2.wav"]}
```
Parsed by the existing `repulse.lisp.reader` — no new dependencies. Keywords become bank names.

**How GitHub discovery works:**
Queries `api.github.com/repos/{owner}/{repo}/git/trees/{branch}?recursive=1`,
filters for audio extensions (`.wav`, `.mp3`, `.ogg`, `.flac`, `.aiff`),
groups files by immediate parent folder name, and fetches samples from
`raw.githubusercontent.com`. Both endpoints support CORS from the browser.

See full spec: [PROMPTS/phase-9-external-sample-repos.md](PROMPTS/phase-9-external-sample-repos.md)

---

## Phase A — More Effects 📋 *planned*

Additional effect plugins: chorus, phaser, tremolo, overdrive, and bitcrusher — all
following the same dry/wet plugin interface, addressable via `(fx :name ...)`.

**Key additions:**
- **Chorus** — multi-voice LFO-modulated delay for width and depth
- **Phaser** — all-pass filter chain with LFO sweep
- **Tremolo** — amplitude LFO for rhythmic volume modulation
- **Overdrive** — waveshaping soft-clip distortion
- **Bitcrusher** — sample-rate and bit-depth reduction

See full spec: [PROMPTS/phase-a-more-effects.md](PROMPTS/phase-a-more-effects.md)

---

## Phase 4 — Live Performance Features 📋 *planned*

Named pattern slots, tap BPM, MIDI clock sync, and shareable session URLs — all
aimed at live performance on stage.

**Key additions:**
- Named pattern slots (`slots.cljs`) — multiple independent patterns playing simultaneously
- `(slot :a (seq :bd :sd))` / `(mute :a)` / `(solo :a)` Lisp built-ins
- Tap BPM button — click to set tempo from live tapping
- MIDI clock input — sync the scheduler to external hardware
- Session URL — encode the current editor buffer in the URL hash for instant sharing

See full spec: [PROMPTS/phase-4-live-features.md](PROMPTS/phase-4-live-features.md)

---

## Phase B — Richer Visuals 📋 *planned*

Two new visual plugin types: a high-quality spectrum analyser (audiomotion-analyzer)
and a p5.js canvas plugin adapter for generative graphics driven by audio data.

**Key additions:**
- **Spectrum plugin** — audiomotion-analyzer frequency display with gradient colouring
- **p5.js adapter** — `makeP5Plugin(sketchFn)` helper; sketch receives `{ analyser, p }` each frame
- Both plugins loadable via `(load-plugin url)`

See full spec: [PROMPTS/phase-b-richer-visuals.md](PROMPTS/phase-b-richer-visuals.md)

---

## Phase 7 — Advanced Plugins 📋 *planned*

Per-pattern effect routing, MIDI output, and audio recording.

**Key additions:**
- Named pattern slots with independent gain nodes (`slots.cljs`)
- `(with-fx :slot-name ...)` — per-slot effects chain before the master bus
- **MIDI output plugin** — route pattern events to hardware via Web MIDI API
- **Recorder plugin** — ⏺ record button captures master output to a downloadable file
- `(record)` / `(record-stop)` Lisp built-ins

See full spec: [PROMPTS/phase-7-plugins-advanced.md](PROMPTS/phase-7-plugins-advanced.md)

---

## Future ideas (unscheduled)

See [docs/FUTURE-FEATURES.md](docs/FUTURE-FEATURES.md) for the full list, including
additional visual plugins, effect ideas, MIDI/OSC/CV integration, export options,
language features, and collaboration tools.
