# REPuLse — Future Features

Prioritised feature backlog. Each tier is ordered by **impact per development effort** —
cheap pure functions before complex audio plumbing, onboarding before niche integrations.
Tiers are designed so each one can become a phase prompt.

---

## Tier 1 — Pattern expressiveness

**Why first:** Every function here is pure, testable, lives in `packages/core`, and takes
hours not days. This is where REPuLse's Lisp nature becomes an *advantage* over
Strudel/Tidal — not by copying mini-notation, but by making S-expressions more powerful
than any string DSL. After this tier, `(jux rev (euclidean 5 8 :bd))` works and feels
magical.

**Scope:** `packages/core/src/repulse/core.cljs` + `packages/lisp/src/repulse/lisp/eval.cljs`
(env bindings) + grammar/completions. No audio changes. No DOM changes.

| Function | Effort | Description |
|---|---|---|
| `euclidean` | S | `(euclidean 5 8 :bd)` — Björklund algorithm, distribute k onsets across n steps. ~20 lines. Table stakes for algorithmic music. |
| `cat` | S | `(cat p1 p2 p3)` — play patterns one after another, each lasting one cycle. Different from `seq` (which subdivides *within* one cycle). |
| `late` / `early` | S | `(late 0.25 pat)` — shift events forward/back by a fraction of a cycle. Building block for `off`, swing, humanisation. |
| `sometimes` | S | `(sometimes f pat)` — apply transform ~50% of cycles. Family: `often` ~75%, `rarely` ~25%. Cycle-seeded PRNG for reproducibility. |
| `degrade` | S | `(degrade pat)` — randomly drop ~50% of events. `(degrade-by 0.3 pat)` — drop 30%. Cycle-seeded. |
| `choose` / `wchoose` | S | `(choose [:bd :sd :hh])` — random element per cycle. `(wchoose [[:bd 0.5] [:sd 0.3] [:hh 0.2]])` — weighted. |
| `jux` | S | `(jux rev pat)` — original panned left, transformed panned right. Built on `pan` (Phase H) + `stack`. |
| `off` | S | `(off 0.25 (fast 2) pat)` — layer a shifted, transformed copy. Canons, echoes, phasing. Depends on `late`. |

**Effort key:** S = small (~20–50 lines in core), M = medium (100–300 lines), L = large (new subsystem).

---

## Tier 2 — Onboarding & first-60-seconds

**Why second:** Tier 1 gives the language power; Tier 2 lets newcomers *discover* it
without reading docs. Zero architecture changes — just curated content and a few Lisp
strings. Lowers activation energy while preserving the Lisp identity (no mini-notation
needed to start jamming).

| Feature | Effort | Description |
|---|---|---|
| Starter templates | S | `(demo :techno)` / `(demo :ambient)` / `(demo :dnb)` — loads a curated multi-track pattern into the editor. 5–10 genre presets showcasing different combinators. A dropdown or `(demo)` that lists them. Just Lisp strings + a lookup map in `ensure-env!`. |
| Hover documentation | M | CM6 `hoverTooltip` on built-in names — shows signature + docstring on mouse-over. Reuse the existing `completions.js` data. |
| Interactive tutorial | M | `(tutorial)` / `(tutorial 3)` — loads progressive chapters into the editor: `seq` → `stack` → `fast` → `every` → `def` → `play`. Each chapter is playable. |

---

## Tier 3 — Mini-notation & sharing

**Why third:** Now that Tier 1 makes the Lisp expressive and Tier 2 gets people playing,
mini-notation becomes opt-in sugar for conciseness — not a crutch for a weak language.
Sharing completes the viral loop.

| Feature | Effort | Description |
|---|---|---|
| Mini-notation | M | `(~ "bd sd [bd bd] sd")` — reader function that parses a compact string into `seq*`/`stack*` calls. `[]` subdivision, `*` repetition, `<>` alternation, `?` random, `~` rest. Lives in `packages/lisp`. The Lisp stays the host; mini-notation is sugar inside it. |
| Gist import | S | `(load-gist "https://gist.github.com/…")` — fetch raw content, eval as REPuLse-Lisp. Pairs with the existing `share!` button for a full share/import loop. |
| WAV export | M | `(export 8)` — render N cycles via `OfflineAudioContext` to a downloadable WAV. Caveat: `OfflineAudioContext` + `AudioWorklet` has browser gaps; may need JS synthesis fallback for export path. |

---

## Tier 4 — Per-track audio & sample control

**Why fourth:** Makes multi-track patterns sound professional. Requires audio graph
changes in `app/audio.cljs` and `app/fx.cljs`, but the pattern algebra (Tier 1) and
user-facing language are already in place.

| Feature | Effort | Description |
|---|---|---|
| Per-track effect chains | L | Independent FX chain per track before the master bus. `(track-fx :bass :reverb 0.4)` or inline `(play :bass pat {:fx [...]})`. Each track's `GainNode` → per-track FX → master. Extends `scheduler-state` and `fx.cljs`. Already sketched in Phase 7 spec. |
| Per-event sample params | M | `:rate` (pitch via `playbackRate`), `:begin`/`:end` (0–1 slice), `:loop` on sample events. Extends the Phase H event-map pattern: `(->> (sound :tabla 0) (rate 1.5) (begin 0.2))`. Dispatch in `play-event`. |
| Sidechain compressor | M | Pattern-aware ducking: `(fx :sidechain :trigger :bd :amount 0.8)`. Uses scheduler event timing for sample-accurate ducking — no audio detection. REPuLse's unfair advantage: it knows event times *before* they sound. |
| More oscillator voices | M | Sawtooth, pulse/square (PWM), white/pink noise, FM pair. Extend WASM `trigger_v2` in `lib.rs`. Makes melodic patterns sound less "sine-only". |

---

## Tier 5 — Lisp superpowers

**Why fifth:** These are the long-term differentiators that no competitor can match — but
they need the foundation from Tiers 1–4 to have an audience. Each is a significant
language-level feature.

| Feature | Effort | Description |
|---|---|---|
| `defsynth` | L | `(defsynth pluck [freq] (-> (saw freq) (lpf (* freq 2)) (env-perc 0.01 0.3)))` — user-defined synthesis graphs. Each UGen keyword maps to a Web Audio node or AudioWorklet DSP block. Compiles to a node-graph factory. The most architecturally complex feature — requires a UGen registry and graph builder. The path to genuine sound design in a Lisp. |
| Pattern macros | M | `(defmacro name [args] body)` — compile-time transforms in the evaluator. Homoiconicity makes user-defined syntax transforms natural. Neither Strudel nor Tidal can offer this. |
| Tail-call optimisation | M | `loop`/`recur` or trampoline in the evaluator. Enables infinite recursive generative patterns without stack overflow. The Lisp advantage for algorithmic composition. |
| Number notation | S | `1/4` as a rational literal, `120bpm` as sugar. Reader-level. |

---

## Tier 6 — MIDI & external I/O

**Why sixth:** Important for studio integration and hardware users, but Chrome/Edge only
(Web MIDI API). Matters most to power users who are already hooked.

| Feature | Effort | Platform | Description |
|---|---|---|---|
| MIDI controller input | M | Chrome, Edge | Note On/Off + CC mapping: `(midi-map :cc1 :filter)`. Extends existing `midi-sync!` infrastructure. |
| MIDI note output | M | Chrome, Edge | Route events to hardware synths: `(midi-out :channel 1 (seq :c4 :e4 :g4))`. REPuLse as a sequencer for external gear. |
| MIDI clock output | S | Chrome, Edge | 24ppqn clock + Start/Stop/Continue at current BPM. |
| MIDI file export | M | all | Export pattern as `.mid` file for DAW import. Simple binary format, ~100 lines. |
| Freesound API | M | all | `(freesound! "kick 808")` — search + load samples from freesound.org. Extends `samples!` infrastructure. |

---

## Tier 7 — Platform & deployment

**Why seventh:** These expand *where* REPuLse runs and *who* can access it, but require
the core experience (Tiers 1–5) to be worth deploying.

| Feature | Effort | Description |
|---|---|---|
| PWA / offline | M | Service worker + manifest. Cache CLJS bundle, WASM, and built-in samples. "Download for offline" flow for Strudel CDN banks. Neither Strudel nor Tidal work offline. |
| Embeddable component | L | `<repulse-editor code="(seq :bd :sd)">` — custom element for blogs/tutorials. Bundle everything into a single JS file via shadow-cljs `:esm`. |
| Collaborative sessions | L | WebRTC co-editing via Yjs (CRDT, native CM6 binding). Deterministic patterns mean shared code = shared audio. Hard part: latency-compensated sync. |
| Mobile layout | M | Touch-optimised editor, larger buttons, drum-pad input mode. CM6 has basic mobile support; main work is CSS + input abstraction. |

---

## Backlog — nice-to-haves

Features that are valuable but not blocking. Pick from here when a tier is done and
there's energy for side quests.

### Additional combinators
| Function | Description |
|---|---|
| `struct` | `(struct (seq true true false true) (pure :bd))` — boolean pattern as rhythmic mask |
| `ply` | `(ply 3 pat)` — subdivide each event into n copies |
| `chunk` | `(chunk 4 rev pat)` — apply transform to rotating quarter of pattern |
| `range` / `segment` | Map 0–1 to numeric range; sample continuous pattern at n points |
| Polymeter | `(polymeter 4 (seq :a :b :c))` — fit n events into m beats |

### Connectivity
| Feature | Description |
|---|---|
| OSC output | `(osc-out "localhost:57120" "/trigger" pat)` via WebSocket→UDP bridge (~30-line Node relay) |
| OSC input | Receive OSC to modulate parameters. Same bridge, reverse direction. |
| Ableton Link | Tempo sync via WebSocket bridge wrapping Link C++ SDK. Breaks zero-install; optional. |
| CV/Gate | WebSerial/WebUSB for modular synths. Chrome only. Niche. |

### Synthesis
| Feature | Description |
|---|---|
| SC-style UGen aliases | Alias existing UGen names to SuperCollider conventions: `sin` → `sin-osc`, `saw` → `saw-osc`, `square` → `pulse`, `tri` → `tri-osc`, `lpf` → `lpf`, `hpf` → `hpf`, `bpf` → `bpf`, `env-perc` → `env-perc`, `env-asr` → `env-asr`, `env-gen` → `env-gen`. Both names coexist — no breaking changes. Trivial: one `assoc` per alias in `make-build-fn`. Valuable for users coming from SC/Overtone who expect the longer names. |
| Granular synthesis | AudioWorklet-based grain engine: pitch, density, spread, position |
| Multi-buffer editor | Multiple named code buffers per track |

### Editor
| Feature | Description |
|---|---|
| Env-aware completions | Drive completion list from live `env-atom` instead of static list |
| Signature hints | Tooltip showing argument names inside a call |

### Timing
| Feature | Description |
|---|---|
| Latency monitor | `(latency)` — scheduling stats in context panel (green/yellow/red) |
| Jitter histogram | Visual plugin: per-event timing deviation over last N cycles |

### Visual plugins
| Feature | Description |
|---|---|
| Spectrum analyser | FFT frequency bars from `AnalyserNode` |
| Punchcard timeline | Per-track step grid (16th-note resolution) |
| Cycle clock | Rotating circle — visual metronome |
| Piano roll | 2D grid: time × pitch, events as rectangles |
| Event log | Scrolling list of events as they fire |
| Lissajous scope | X/Y stereo plot |
| VU meters | Per-track RMS level meters |

### Effect plugins
| Feature | Description |
|---|---|
| Ring modulator | Signal × carrier sine — metallic textures |
| Parametric EQ | Multi-band BiquadFilter chain |
| Wavefolder | Soft-clip / fold wave shaping |
| Granular processor | Chop + scatter incoming audio — AudioWorklet |

### Export
| Feature | Description |
|---|---|
| FLAC export | WASM FLAC encoder on top of WAV export |
| Community library | Curated JSON index of shared pattern Gist URLs |
