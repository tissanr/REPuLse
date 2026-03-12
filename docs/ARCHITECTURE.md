# REPuLse — Architecture

REPuLse is structured as a monorepo of four packages wired together by a browser app.
The design principle is strict layering: each layer knows only about the layer below it.

---

## Package dependency graph

```
app  ──────────────────►  packages/lisp  ──►  packages/core
 │                                              (pure, no deps)
 └──────────────────────────────────────────►  packages/core
 │
 └── packages/audio  (Rust/WASM, loaded by worklet.js on the audio thread)
 └── Web Audio API (browser)
 └── Strudel CDN (sample manifests)
```

- `core` has zero dependencies beyond `cljs.core`
- `lisp` depends on `core`, knows nothing about audio or DOM
- `audio` (Rust) is a standalone WASM crate; it has no knowledge of ClojureScript or the DOM
- `app` depends on `core` and `lisp`; owns all side effects (audio, DOM, network)

---

## Layer 1 — `packages/core`: Pattern algebra

**Source:** `packages/core/src/repulse/core.cljs`

A **Pattern** is a pure value:
```clojure
{:query (fn [timespan] [event ...])}
```

A **timespan** is `{:start rational :end rational}` where rationals are `[numerator denominator]`
vectors. All time arithmetic uses exact rational numbers — no floats until rendering.

An **event** is:
```clojure
{:value anything          ; the payload
 :whole {:start r :end r} ; logical cycle slot
 :part  {:start r :end r} ; active slice (trimmed to query span)
}
```

### Core combinators

| Function | Signature | Description |
|---|---|---|
| `pure` | `value → Pattern` | One event per cycle |
| `seq*` | `[value] → Pattern` | N values spread evenly |
| `stack*` | `[Pattern] → Pattern` | Merge event streams |
| `fast` | `rational, Pattern → Pattern` | Speed up |
| `slow` | `rational, Pattern → Pattern` | Slow down |
| `rev` | `Pattern → Pattern` | Reverse within cycle |
| `every` | `n, fn, Pattern → Pattern` | Conditional transform |
| `fmap` | `fn, Pattern → Pattern` | Value transform |

---

## Layer 2 — `packages/lisp`: Reader + evaluator

**Source:** `packages/lisp/src/repulse/lisp/`

### Reader (`reader.cljs`)

A hand-written recursive-descent parser. Input: source string. Output: ClojureScript
data structures (numbers, strings, keywords, symbols, lists, vectors).

No tokeniser phase — the reader directly produces a parse tree in a single pass.

### Evaluator (`eval.cljs`)

A classic environment-based interpreter:
- **Environment**: a plain CLJS map `{name → value}`
- **`eval-form`**: walks the AST, dispatches on type
- Special forms: `def`, `let`, `fn`, `if`, `do`
- Everything else is a function call
- Levenshtein distance on unknown symbols for typo hints

The environment is created once by `make-env` (in `app.cljs`) and passed through
each evaluation. `def` writes into a mutable `defs` atom attached to the environment
as `:*defs*`; symbol lookup checks both the env map and `@(:*defs* env)` so that
`def`-bound names are visible in subsequent evaluations.

### Public API (`core.cljs`)

```clojure
(eval-string source env)
;; => {:result value} | {:error message}
```

---

## Layer 3 — `packages/audio`: Rust/WASM PCM synthesis engine

**Source:** `packages/audio/src/lib.rs`
**Build:** `wasm-pack build packages/audio --target web --out-dir pkg`
**Output:** `app/public/repulse_audio.js` + `app/public/repulse_audio_bg.wasm`

A pure PCM synthesis engine with no web-sys dependency. It has no knowledge of the
Web Audio API node graph — it only generates raw `Float32Array` samples.

### Public WASM API

```rust
AudioEngine::new(sample_rate: f32) -> AudioEngine
AudioEngine::trigger(&mut self, value: &str, time: f64)
AudioEngine::process_block(&mut self, n_samples: u32, current_time: f64) -> Float32Array
AudioEngine::stop_all(&mut self)
```

- `trigger(value, time)` — enqueues a pending event at the given `AudioContext.currentTime`
- `process_block(n, t)` — called once per audio render quantum (128 samples by default);
  activates pending events whose scheduled time has arrived, sums all active voices,
  prunes silent voices, and returns the mixed samples
- `stop_all()` — clears all voices and pending events immediately

### Synthesized voices

| Value | Voice | Description |
|---|---|---|
| `"bd"` | Kick | Sine sweep 150 → 40 Hz, amplitude envelope |
| `"sd"` | Snare | LCG noise through biquad bandpass + 180 Hz sine crack |
| `"hh"` | Hi-hat (closed) | LCG noise through highpass, 45 ms decay |
| `"oh"` | Hi-hat (open) | LCG noise through highpass, 350 ms decay |
| `"<N>"` | Tone | Phase accumulator sine at frequency N Hz |

All noise uses a deterministic LCG (Linear Congruential Generator) — no `Math.random()`.
Biquad filters use Direct Form I with per-sample coefficient update.

---

## Layer 4 — `app`: Browser application

**Source:** `app/src/repulse/`

### `audio.cljs` — Web Audio scheduler + AudioWorklet bridge

Implements the [lookahead clock pattern](https://web.dev/articles/audio-scheduling):
- `setInterval` fires every **25ms**
- Looks ahead **200ms** and schedules any events in that window
- Events are scheduled using `AudioContext.currentTime` (sample-accurate)
- Cycle duration is configurable (`set-bpm!`); default 120 BPM = 2.0s/cycle

At startup, `get-ctx` first calls `build-master-chain!` to create a permanent master
signal chain:

```
WorkletNode ──► masterGain ──► analyser ──► destination
```

- `masterGain` (GainNode, value 1.0) — master volume control point
- `analyser` (AnalyserNode, fftSize 2048, smoothing 0.8) — exposed to visual plugins

Then `init-worklet!` is called:
1. Calls `audioWorklet.addModule("/worklet.js")` to register the processor
2. Creates an `AudioWorkletNode` connected to `masterGain` (not `destination` directly)
3. Sends an `init` message with the WASM file URLs; the worklet loads WASM on the audio thread
4. On `ready` reply, sets `worklet-ready?` to `true` and logs the active backend

The JS fallback synth voices also connect to `masterGain` via the `output-node` helper.

Sound dispatch in `play-event`:
1. `:_` → silence (no-op)
2. Map `{:bank k :n i}` → sample lookup → `samples/play!`
3. Keyword → sample registry → `worklet-trigger!` → JS synth fallback
4. Number → `worklet-trigger!` (frequency string) → JS sine fallback

The two-tier fallback chain:
- **AudioWorklet + WASM** (preferred) — console: `[REPuLse] audio backend: audioworklet+wasm`
- **JS synthesis** (fallback) — console: `[REPuLse] audio backend: clojurescript synthesis`

### `worklet.js` — AudioWorkletProcessor

**Source:** `app/public/worklet.js`

A plain JavaScript file (not processed by shadow-cljs/Closure Compiler) that runs on the
dedicated audio render thread inside an `AudioWorkletGlobalScope`.

- On `init` message: calls `init(wasmModule)` with the pre-compiled `WebAssembly.Module`
  received from the main thread, constructs `AudioEngine(sampleRate)`, posts `ready`
- On `trigger` message: calls `engine.trigger(value, time)`
- On `stop` message: calls `engine.stop_all()`
- `process(_inputs, outputs)`: calls `engine.process_block(ch.length, currentTime)` and writes
  the returned `Float32Array` directly into the output channel buffer

The WASM module is compiled on the main thread via `WebAssembly.compileStreaming` and sent
via `postMessage` (structured-clone serialises `WebAssembly.Module`). This avoids the
`AudioWorkletGlobalScope` ban on dynamic `import()`. The worklet imports
`worklet-polyfills.js` first to provide `TextDecoder`/`TextEncoder`, which the wasm-pack
glue calls at module top-level before the worklet scope has them available.

### `samples.cljs` — Sample loader

Fetches two manifests from Strudel's CDN at startup:
- `https://strudel.b-cdn.net/dirt-samples.json` — TidalCycles Dirt-Samples
- `https://strudel.b-cdn.net/tidal-drum-machines.json` — Drum machine library

`get-buffer!` returns a `Promise<AudioBuffer>`, deduplicating concurrent requests
and caching decoded buffers. `play!` uses `max(scheduledTime, currentTime)` to
handle first-load latency gracefully.

**External sample loading (Phase 9):**

`load-external!` is the router called by the `(samples! url)` Lisp built-in:

| URL form | Handler |
|---|---|
| `"github:owner/repo"` | `load-github!` — queries GitHub tree API, groups audio by folder |
| `"github:owner/repo/branch"` | `load-github!` on the named branch |
| `"https://…/samples.edn"` | `load-lisp-manifest!` — parses via `repulse.lisp.reader` |
| any other URL | `load-manifest!` — existing Strudel JSON handler |

REPuLse Lisp manifest format (`.edn`):
```clojure
{:_base "https://raw.githubusercontent.com/user/repo/main/samples/"
 :kick  ["kick1.wav" "kick2.wav"]
 :snare ["snare1.wav"]}
```
Keywords become bank names; `:_base` is prepended to relative filenames.
Parsed by the existing reader — no new dependencies required.

### `plugins.cljs` — Plugin registry

Maintains a map of `plugin-name → {:plugin js-obj :type keyword}`.

```clojure
(register! plugin host)   ; validate!, call .init, replace existing registration
(unregister! name)        ; calls .destroy, removes from registry
(visual-plugins)          ; returns all registered :visual plugins
```

`register!` validates that the plugin object exposes all required methods before
calling `init`. Missing methods produce a descriptive error at load time rather
than a silent failure later.

Plugins are ES module default exports. Two authoring styles are supported:

- **Class style** — extend `VisualPlugin` or `EffectPlugin` from `app/public/plugin-base.js`.
  The base classes set `type` automatically and provide default no-op implementations for
  optional methods.
- **Plain object style** — export a literal object with the full method set.

See [`docs/PLUGINS.md`](PLUGINS.md) for the complete protocol, Host API, worked examples,
and registration rules.

The built-in oscilloscope lives at `app/public/plugins/oscilloscope.js` and is
auto-loaded at startup. Third-party plugins can be loaded at runtime:

```lisp
(load-plugin "/plugins/oscilloscope.js")
(load-plugin "https://example.com/my-spectrum.js")
```

### `fx.cljs` — Effect chain manager

**Source:** `app/src/repulse/fx.cljs`

Maintains an ordered vector of effect plugin entries and manages the audio graph wiring
between them.

```clojure
(defonce chain (atom []))   ; [{:name "reverb" :plugin js-obj :input node :output node} ...]

(add-effect! plugin)              ; call createNodes, append to chain, rewire
(remove-effect! effect-name)      ; destroy plugin, remove from chain, rewire
(set-param! effect-name param value) ; forward to plugin.setParam
(bypass! effect-name enabled)     ; forward to plugin.bypass
```

`rewire!` rebuilds the audio graph after any change:

```
masterGain → effect1.input → effect1.output
           → effect2.input → effect2.output
           → …
           → analyser → destination
```

All disconnections happen before reconnection to avoid duplicate signal paths.

### `app.cljs` — UI and wiring

- Builds the DOM (header, CodeMirror editor, plugin panel, footer)
- Creates the Lisp environment via `leval/make-env`; injects `samples!`, `sample-banks`, `load-plugin`, and `fx` built-ins
- On startup, auto-loads the oscilloscope (visual) and five effect plugins via dynamic `import()`
- Effect plugins auto-loaded: `reverb`, `delay`, `filter`, `compressor`, `dattorro-reverb`
- Routes evaluated results: Pattern → audio scheduler, other → output line
- **▶ play / ■ stop** button evaluates the editor buffer or stops playback

### `lisp-lang/` — CodeMirror 6 language extension

**Source:** `app/src/repulse/lisp-lang/`

Provides syntax highlighting, rainbow delimiters, bracket matching, and indentation
for the CodeMirror 6 editor. Built with [Lezer](https://lezer.codemirror.net/).

| File | Purpose |
|---|---|
| `repulse-lisp.grammar` | Lezer grammar source — tokens, precedence, node types |
| `parser.js` + `parser.terms.js` | Pre-compiled parser (committed; no build step at dev time) |
| `highlight.js` | `styleTags` mapping Lezer nodes → `@lezer/highlight` tags (oneDark colours) |
| `rainbow.js` | `ViewPlugin` that walks the syntax tree and applies `rainbow-N` CSS classes by nesting depth |
| `index.js` | `LRLanguage` + `LanguageSupport` export; includes indent/fold props and `rainbowBrackets` |

Colour mapping (oneDark palette):

| Token | Highlight tag | Colour |
|---|---|---|
| `:bd`, `:sd`, … | `tags.atom` | orange |
| `seq`, `stack`, … | `tags.keyword` | purple |
| numbers | `tags.number` | gold |
| strings | `tags.string` | green |
| `; comments` | `tags.lineComment` | grey |
| `( )` depth 0–5 | `.rainbow-1` … `.rainbow-6` | red / gold / green / cyan / purple / blue |

Regenerate the parser after grammar edits:
```bash
npm run gen:grammar
```

---

## Audio graph topology

At runtime the master signal chain is:

```
AudioWorkletNode (synthesis)
        │
        ▼
   masterGain
        │
        ▼  (rewired by fx.cljs whenever effects are added/removed)
  reverb.input → reverb.output
        │
        ▼
  delay.input  → delay.output
        │
        ▼
  filter.input → filter.output
        │
        ▼
  compressor.input → compressor.output
        │
        ▼
  dattorro-reverb.input → dattorro-reverb.output
        │
        ▼
   analyser (AnalyserNode — tapped by visual plugins)
        │
        ▼
   destination
```

Each effect plugin internally routes through a dry/wet topology so bypassing any
single effect is click-free and transparent:

```
inputNode ─┬─── dry GainNode ───────────────────────┬── outputNode
           └─── processing (conv/delay/etc) ─ wet GainNode ─┘
```

When `wet = 0` (default for delay, dattorro-reverb) the effect is inaudible but
in the chain; the dry signal passes unchanged.

---

## Data flow: evaluation to sound

```
User types expression
        │
        ▼
  CodeMirror editor
        │  Ctrl+Enter
        ▼
  lisp/reader.cljs         parse source → AST
        │
        ▼
  lisp/eval.cljs           eval AST in env → Pattern (or error)
        │
        ▼
  app.cljs                 pattern? → audio/start!
        │
        ▼
  audio.cljs               setInterval tick (every 25ms)
        │
        ▼
  core/query               pattern.query({start: [n,1], end: [n+1,1]}) → [event]
        │
        ▼
  audio/play-event         for each event: schedule at AudioContext time t
        │
        ├── samples/play!  fetch buffer → AudioBufferSourceNode.start(t)
        │
        └── worklet-trigger!
                │  MessagePort postMessage({type:"trigger", value, time})
                ▼
          worklet.js       AudioWorkletProcessor.process() → engine.process_block()
                │
                ▼
          packages/audio   Rust PCM engine sums voices → Float32Array → output buffer
```

---

## Build system

**shadow-cljs** compiles all ClojureScript. Two build targets:

| Target | Command | Output |
|---|---|---|
| `:app` | `npx shadow-cljs watch app` | `app/public/js/main.js` |
| `:test` | `npx shadow-cljs compile test` | `out/test.js` (Node) |

Source paths span all three CLJS packages:
```edn
:source-paths ["app/src" "packages/core/src" "packages/lisp/src"]
```

**wasm-pack** builds the Rust synthesis engine:
```bash
npm run build:wasm
# → packages/audio/pkg/repulse_audio{.js,_bg.wasm}
# → copied to app/public/ for serving
```

`worklet.js` is a static file in `app/public/` — it is served directly and never
processed by the ClojureScript build pipeline.

npm workspaces link the three packages together. The dev HTTP server serves
`app/public/` at port 3000.

---

## Phase status

| Phase | Focus | Status |
|---|---|---|
| **1** | First sound — CLJS pattern engine, Web Audio synthesis, CodeMirror editor | ✅ delivered |
| **2** | Rust/WASM synthesis — better drum sounds via `wasm-pack` | ✅ delivered |
| **3** | AudioWorklet — WASM on the audio thread, zero main-thread jank | ✅ delivered |
| **5** | Active code highlighting — editor flashes source tokens as they play | ✅ delivered |
| **6a** | Plugin system + visual plugins — AnalyserNode tap, oscilloscope | ✅ delivered |
| **6b** | Effect plugins — reverb, delay, filter, compressor, dattorro-reverb | ✅ delivered |
| **8** | Song arrangement — `arrange`, `play-scenes`, map literals | ✅ delivered |
| **10** | Syntax highlighting — Lezer grammar, bracket matching, oneDark | ✅ delivered |
| **9** | External sample repos — `(samples! "github:owner/repo")` | ✅ delivered |
| **C** | Code completion — built-in docstrings + live `def` tracking | ✅ delivered |
| **A** | More effects — chorus, phaser, tremolo, overdrive, bitcrusher | ✅ delivered |
| **D** | Editor persistence — localStorage save/restore across reloads | ✅ delivered |
| **E** | Session context panel — live bindings, effects, BPM sidebar | ✅ delivered |
| **F** | Drum machine bank prefix — `(bank :AkaiLinn)` scope shorthand | ✅ delivered |
| **4** | Live features — named pattern slots, tap BPM, MIDI clock, session URLs | 📋 planned |
| **B** | Richer visuals — audiomotion-analyzer spectrum, p5.js plugin support | 📋 planned |
| **7** | Advanced plugins — per-pattern routing, MIDI out, recorder | 📋 planned |

See `PROMPTS/` for detailed specifications of each phase.
