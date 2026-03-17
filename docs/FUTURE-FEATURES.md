# REPuLse — Future Features

Unscheduled ideas beyond the current roadmap. Organised by architectural concept so each
section maps cleanly to a potential implementation phase. Features are grouped into
**must-have** (competitive parity with Strudel / TidalCycles) and **differentiators**
(capabilities neither competitor offers).

Priority markers: **[must-have]** or **[differentiator]**.

---

## Pattern combinators & stochastic transforms

REPuLse currently has 10 combinators: `seq`, `stack`, `pure`, `fast`, `slow`, `rev`,
`every`, `fmap`, `combine`, `arrange`. Tidal has 100+, Strudel ~80. This is the single
most impactful area for enriching the language — every function here is pure, testable,
and lives in `packages/core`. No audio, no DOM, no external dependencies.

| Idea | Priority | Description |
|---|---|---|
| Euclidean rhythms | **must-have** | `(euclidean 5 8 :bd)` — distribute k onsets across n steps using the Björklund algorithm. Returns a `seq` of values and `:_` rests. Table stakes for algorithmic music; both Tidal and Strudel have it. Pure function in `core.cljs`, ~20 lines. |
| `cat` (concatenate) | **must-have** | `(cat p1 p2 p3)` — play patterns one after another, each lasting one cycle, then loop. Different from `seq` (which divides one cycle). `(cat verse chorus)` = 2-cycle loop. In Tidal: `cat`. In Strudel: `cat`. |
| `sometimes` / probability | **must-have** | `(sometimes (fast 2) pat)` — apply a transform ~50% of cycles. Family: `(often f pat)` ~75%, `(rarely f pat)` ~25%, `(almostNever f pat)` ~10%, `(almostAlways f pat)` ~90%. Use cycle number as PRNG seed → deterministic per cycle, reproducible when shared. Tidal's most-used expressive tool. |
| `degrade` | **must-have** | `(degrade pat)` — randomly drop ~50% of events. `(degrade-by 0.3 pat)` — drop 30%. Seed from cycle position for reproducibility. Essential for humanisation. |
| `choose` / `wchoose` | **must-have** | `(choose [:bd :sd :hh])` — random element per cycle. `(wchoose [[:bd 0.5] [:sd 0.3] [:hh 0.2]])` — weighted. Again, cycle-seeded PRNG. |
| `off` (offset layer) | **must-have** | `(off 0.25 (fast 2) pat)` — layer a transformed copy shifted by a fraction of a cycle. Creates canons, echoes, phasing. Requires `late`/`early` (time shift). In Tidal: `off`. |
| `late` / `early` | **must-have** | `(late 0.25 pat)` — shift events forward in time by a fraction of a cycle. `(early 0.25 pat)` — shift back. Building block for `off`, swing, and humanisation. In Tidal: `late`, `early`. |
| `jux` (juxtapose) | **must-have** | `(jux rev pat)` — play original panned left, transformed copy panned right. Built on `pan` (Phase H) + `stack`. In Tidal: `jux`. One of the most-used stereo tools. |
| `struct` (structural rhythm) | nice-to-have | `(struct (seq true true false true) (pure :bd))` — use a boolean pattern as a rhythmic mask. Events only fire where the structure is `true`. In Tidal: `struct`. |
| `ply` (replicate) | nice-to-have | `(ply 3 pat)` — subdivide each event into n copies. `(ply (seq 1 2 3 1) pat)` — variable subdivision. In Tidal: `ply`. |
| `chunk` | nice-to-have | `(chunk 4 rev pat)` — apply transform to one quarter of the pattern per cycle, rotating which quarter. In Tidal: `chunk`. Creates evolving variations. |
| `range` / `segment` | nice-to-have | `(range 200 800 (slow 4 (pure 0)))` — map a 0–1 pattern to a numeric range. `(segment 16 pat)` — sample a continuous pattern at n evenly-spaced points. Useful for LFO-style modulation. |
| Polymeter helpers | nice-to-have | `(polymeter 4 (seq :a :b :c))` — fit 3 events into 4 beats, creating polymetric tension. Currently achievable with `(fast 3/4 ...)` but a dedicated function is more readable. |

---

## Language & notation

The Lisp is REPuLse's identity. These features lean into that — making the language more
expressive without abandoning S-expressions.

| Idea | Priority | Description |
|---|---|---|
| Mini-notation | **must-have** | The single biggest usability gap. A reader macro `(~ "bd sd [bd bd] sd")` that parses a compact string into a `seq`/`stack` tree — brackets for subdivision, `*` for repetition, `<>` for alternation, `?` for random, `~` for rest. Implemented as a function in `packages/lisp` that calls existing `seq*`/`stack*` combinators. The Lisp stays the host language; mini-notation is sugar inside it, not a replacement. |
| Pattern macros | **differentiator** | `(defmacro name [args] body)` — compile-time transforms in the evaluator. Lisp's homoiconicity makes this natural — Strudel and Tidal cannot offer user-defined syntax transforms. |
| Tail-call optimisation | **differentiator** | `loop`/`recur` or trampoline-based TCO in the evaluator. Enables infinite recursive generative patterns that are impossible in Haskell/JS without stack overflows. A genuine Lisp advantage. |
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
| MIDI file export | nice-to-have | Export a pattern as a `.mid` file: query N cycles, map events to Note On/Off messages, write the SMF binary format. Enables getting REPuLse patterns into a DAW offline. MIDI files are a simple binary spec; no external library needed (~100 lines). |
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

## Onboarding & discoverability

REPuLse has code completion with docstrings, but no guided entry point for new users.
These features reduce the "blank page" problem.

| Idea | Priority | Description |
|---|---|---|
| Starter templates | **must-have** | A `(demo :techno)` / `(demo :ambient)` / `(demo :dnb)` command that loads a curated multi-track pattern into the editor. 5–10 genre presets that showcase different combinators. Alternatively, a dropdown/palette in the UI. Almost zero code — just curated Lisp strings. |
| Interactive tutorial | nice-to-have | A guided walkthrough that progressively introduces `seq` → `stack` → `fast` → `every` → `def` → `play`, with each step playable. Could be a special `(tutorial)` command that loads chapters into the editor, or a sidebar panel. |
| Hover documentation | nice-to-have | CM6 tooltip on hover over a built-in name showing its signature and docstring. Extends the existing `completions.js` data with a `hoverTooltip` extension. |

---

## Editor features

| Idea | Description |
|---|---|
| Env-aware completions | Drive the completion list from the live `env-atom` — plugin-registered built-ins and dynamically added names appear automatically. Requires a JS/CLJS bridge to expose `env-atom` keys to CM6's `CompletionSource`. Natural upgrade from Phase C's static approach. |
| Signature / parameter hints | Tooltip showing argument names inside a call: `(fast [factor] [pattern])`. Requires a signature table per built-in and a CM6 `ViewPlugin` or tooltip extension. |

---

## Timing & performance diagnostics

Live coding demands tight timing. These features expose scheduling health so performers
can catch problems before the audience does.

| Idea | Description |
|---|---|
| Latency monitor | `(latency)` — display current scheduling stats: lookahead window, actual vs expected event times, drift. Could also be a small always-visible indicator in the context panel (green/yellow/red). |
| Jitter histogram | Visual plugin showing per-event timing deviation over the last N cycles. Reads `AudioContext.currentTime` at trigger vs scheduled time. |

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
