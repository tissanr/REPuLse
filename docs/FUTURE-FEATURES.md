# REPuLse — Future Features

Unscheduled ideas beyond the current roadmap. Organised by architectural concept so each
section maps cleanly to a potential implementation phase. Features are grouped into
**must-have** (competitive parity with Strudel / TidalCycles) and **differentiators**
(capabilities neither competitor offers).

Priority markers: **[must-have]** or **[differentiator]**.

---

## Language & notation

The Lisp is REPuLse's identity. These features lean into that — making the language more
expressive without abandoning S-expressions.

| Idea | Priority | Description |
|---|---|---|
| Mini-notation | **must-have** | The single biggest usability gap. A reader macro `(~ "bd sd [bd bd] sd")` that parses a compact string into a `seq`/`stack` tree — brackets for subdivision, `*` for repetition, `<>` for alternation, `?` for random, `~` for rest. Implemented as a function in `packages/lisp` that calls existing `seq*`/`stack*` combinators. The Lisp stays the host language; mini-notation is sugar inside it, not a replacement. |
| Pattern macros | **differentiator** | `(defmacro name [args] body)` — compile-time transforms in the evaluator. Enables `(euclidean 5 8 :bd)` as a macro that expands to a `seq` with rests placed by the Björklund algorithm. Lisp's homoiconicity makes this natural — Strudel and Tidal cannot offer user-defined syntax transforms. |
| Tail-call optimisation | **differentiator** | `loop`/`recur` or trampoline-based TCO in the evaluator. Enables infinite recursive generative patterns (`(defn drift [n] (seq (+ 200 n) (defer (drift (+ n 1)))))`) that are impossible in Haskell/JS without stack overflows. A genuine Lisp advantage. |
| Number notation | nice-to-have | Musical shorthand: `1/4` as a rational literal (not float division), `120bpm` as syntactic sugar for `(bpm 120)`. Reader-level feature. |
| Multi-buffer editor | nice-to-have | Multiple named code buffers, each associated with a track. Switch with tabs or `(buffer :name)`. Requires CM6 multi-editor management in `app.cljs`. |

---

## Synthesis & voice engine

Expanding what REPuLse can sound like — all within the WASM AudioWorklet + Web Audio
architecture.

| Idea | Priority | Description |
|---|---|---|
| Custom synth definitions | **must-have** | `(defsynth name [freq amp] (-> (saw freq) (lpf (* freq 2)) (env-perc 0.01 decay)))` — user-defined synthesis graphs in the Lisp layer. Each UGen keyword (`saw`, `sin`, `lpf`, `env-perc`) maps to a Web Audio node or AudioWorklet DSP block. The `defsynth` form compiles to a node-graph factory; `(name :c4)` instantiates it at play time. This is the most architecturally complex feature — requires a UGen registry and a graph builder, but it's the path to genuine sound design. |
| More oscillator voices | nice-to-have | Additional WASM voices: sawtooth, pulse/square (with PWM), band-limited variants via PolyBLEP, white/pink noise, FM pair (carrier + modulator with index parameter). Extend `trigger_v2` match arms in `lib.rs`. |
| Granular synthesis | nice-to-have | Split an audio buffer into short overlapping grains; control pitch, density, spread, and position. AudioWorklet-based — similar architecture to the Dattorro reverb processor. |

---

## Per-track audio routing

Currently all effects are global (master bus). These features give each track independent
audio control — the standard expectation in any multi-track live coding tool.

| Idea | Priority | Description |
|---|---|---|
| Per-track effect chains | **must-have** | Independent effect chain per track (before the master bus). `(play :bass (seq :c2 :e2) {:fx [:filter 400 :overdrive 0.3]})` or `(track-fx :bass :reverb 0.4)`. Each track's `GainNode` feeds its own chain → master. Extends `scheduler-state` to hold per-track `AudioNode` graphs. Already sketched in Phase 7 spec. |
| Per-event sample parameters | **must-have** | Per-event control over sample playback: `:rate` (pitch-shift via `playbackRate`), `:begin`/`:end` (start/end points as 0–1 fractions), `:loop` (boolean). Extend the event-map pattern from Phase H: `(->> (sound :tabla 0) (rate 1.5) (begin 0.2) (end 0.8))`. Dispatch in `play-event` sets `AudioBufferSourceNode` properties. |
| Sidechain compressor | **differentiator** | Pattern-aware ducking: the master bus gain ducks exactly when a `:bd` event fires, using the scheduler's event timing directly — no audio-level detection needed. `(fx :sidechain :trigger :bd :amount 0.8 :release 0.1)`. REPuLse knows event times ahead of the audio clock, so this can be sample-accurate. Impossible in traditional DAW sidechaining without routing hacks. |

---

## Connectivity & I/O

Bridging REPuLse to external hardware, software, and services. Platform constraints noted
where relevant — Web MIDI is Chrome/Edge only; WebSerial is Chrome only.

| Idea | Priority | Platform | Description |
|---|---|---|---|
| MIDI controller input | **must-have** | Chrome, Edge | Receive Note On/Off and CC messages from a controller. Map CC to parameters: `(midi-map :cc1 :filter)`. Map notes to pattern triggers. Extends the existing `midi-sync!` Web MIDI infrastructure. |
| MIDI note output | **must-have** | Chrome, Edge | Route pattern events to hardware synths via MIDI Note On/Off + velocity. `(play :ext (midi-out :channel 1 (seq :c4 :e4 :g4)))`. Enables REPuLse as a sequencer for external gear. |
| MIDI clock output | **must-have** | Chrome, Edge | Send 24ppqn clock + Start/Stop/Continue to drive external hardware at REPuLse's BPM. |
| OSC output | nice-to-have | requires bridge | Forward pattern events to SuperCollider, Ableton, or Max/MSP via a WebSocket→UDP bridge. `(osc-out "localhost:57120" "/trigger" pat)`. Bridge is a ~30-line Node.js relay. |
| OSC input | nice-to-have | requires bridge | Receive OSC to modulate parameters in real time. Same bridge, reverse direction. |
| Ableton Link | nice-to-have | requires bridge | Tempo sync with Link-enabled apps (Ableton, Traktor, etc.). Requires a local WebSocket bridge daemon wrapping the Link C++ SDK. **Trade-off:** breaks REPuLse's zero-install browser-only philosophy. Worth it for studio integration, but should be optional. |
| Freesound API | **differentiator** | all browsers | Search and load samples from freesound.org directly from Lisp: `(freesound! "kick 808")`. Fetches via the Freesound REST API (CORS-enabled), decodes to `AudioBuffer`, registers as a bank. Extends the existing `samples!` infrastructure. Neither Strudel nor Tidal have built-in Freesound integration. |
| CV/Gate via WebSerial | nice-to-have | Chrome only | Send gate/pitch voltages to modular synths via WebSerial or WebUSB. `(cv-out :channel 1 pat)`. Extremely niche audience but technically feasible and unique to browser-based tools. |

---

## Sharing & export

Getting music out of REPuLse and into other people's hands.

| Idea | Priority | Description |
|---|---|---|
| Gist import | **must-have** | Load a pattern from a GitHub Gist URL: `(load-gist "https://gist.github.com/...")`. Fetch raw content, eval as REPuLse-Lisp. Pairs with the existing `share!` button — share via URL, import via Gist. |
| WAV export via OfflineAudioContext | **must-have** | Render N cycles faster-than-real-time to a downloadable WAV blob. `(export 8)` renders 8 cycles. **Caveat:** `OfflineAudioContext` does not support `AudioWorklet` in all browsers (Safari, older Firefox). May need a JS-only rendering path as fallback — acceptable since export is not real-time. |
| FLAC export | nice-to-have | Higher quality offline export using a WASM FLAC encoder. Build on top of the WAV export infrastructure. |
| Community pattern library | nice-to-have | Browse and import shared patterns from a hosted repository. Could be as simple as a curated JSON index of Gist URLs. |

---

## Platform & deployment

Making REPuLse available in more contexts.

| Idea | Priority | Description |
|---|---|---|
| PWA / offline mode | **differentiator** | Service worker + Web App Manifest → installable desktop app that works without internet. Cache the compiled CLJS bundle, WASM module, and built-in samples at install time. The Strudel CDN samples (~100 banks) would need a "download for offline" flow. Neither Strudel nor Tidal work offline. |
| Embeddable web component | **differentiator** | `<repulse-editor code="(seq :bd :sd)">` custom element for embedding live REPuLse instances in blog posts, tutorials, or documentation. Bundle CLJS output + CM6 + WASM into a single self-contained JS file via shadow-cljs `:esm` target. |
| Collaborative sessions | **differentiator** | WebRTC peer-to-peer co-editing via Yjs (CRDT library with native CM6 binding). Both peers evaluate the same code → same deterministic pattern output → synchronised audio. The hardest part is latency-compensated audio sync, not code sync. |
| Mobile layout | nice-to-have | Touch-optimised editor, larger buttons, virtual keyboard for `:bd`/`:sd` etc. CodeMirror 6 has reasonable mobile support already; main work is CSS layout and a drum-pad input mode. |

---

## Editor features

| Idea | Description |
|---|---|
| Env-aware completions | Drive the completion list from the live `env-atom` — plugin-registered built-ins and dynamically added names appear automatically. Requires a JS/CLJS bridge to expose `env-atom` keys to CM6's `CompletionSource`. Natural upgrade from Phase C's static approach. |
| Signature / parameter hints | Tooltip showing argument names inside a call: `(fast [factor] [pattern])`. Requires a signature table per built-in and a CM6 `ViewPlugin` or tooltip extension. |

---

## Visual plugins

| Idea | Description |
|---|---|
| Spectrum analyser | FFT frequency bars from `AnalyserNode.getByteFrequencyData()` |
| Punchcard timeline | Per-track step grid: 16 cells per bar, ● = event ○ = silence. Reads scheduler state. |
| Cycle clock | Rotating circle showing position within the current cycle — a visual metronome |
| Piano roll | 2D grid: time on X, pitch on Y, events as rectangles |
| Event log | Scrolling list of events as they fire — useful for debugging |
| Lissajous scope | X/Y plot of left vs. right channel (stereo) |
| VU meters | Per-track level meters using `getFloatTimeDomainData` RMS |

---

## Effect plugins

| Idea | Description |
|---|---|
| Ring modulator | Multiply signal by a carrier sine — metallic, robotic textures |
| Parametric EQ | Multi-band `BiquadFilterNode` chain with frequency/gain/Q per band |
| Wavefolder | Soft-clip / fold wave shaping — aggressive harmonic saturation |
| Granular processor | Chop incoming audio into grains and scatter — AudioWorklet-based |
