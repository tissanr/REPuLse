# REPuLse — Architecture

REPuLse is structured as a monorepo of three packages wired together by a browser app.
The design principle is strict layering: each layer knows only about the layer below it.

---

## Package dependency graph

```
app  ──────────────────►  packages/lisp  ──►  packages/core
 │                                              (pure, no deps)
 └──────────────────────────────────────────►  packages/core
 │
 └── Web Audio API (browser)
 └── Strudel CDN (sample manifests)
```

- `core` has zero dependencies beyond `cljs.core`
- `lisp` depends on `core`, knows nothing about audio or DOM
- `app` depends on both; owns all side effects

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
each evaluation. `def` writes into a mutable `defs` atom attached to the environment;
those names are merged back into the env on subsequent evaluations.

### Public API (`core.cljs`)

```clojure
(eval-string source env)
;; => {:result value} | {:error message}
```

---

## Layer 3 — `app`: Browser application

**Source:** `app/src/repulse/`

### `audio.cljs` — Web Audio scheduler

Implements the [lookahead clock pattern](https://web.dev/articles/audio-scheduling):
- `setInterval` fires every **25ms**
- Looks ahead **200ms** and schedules any events in that window
- Events are scheduled using `AudioContext.currentTime` (sample-accurate)
- Cycle duration is configurable (`set-bpm!`); default 120 BPM = 2.0s/cycle

Sound dispatch in `play-event`:
1. `:_` → silence
2. Map `{:bank k :n i}` → sample lookup
3. Keyword → sample registry, then synthesized fallback
4. Number → sine oscillator at that Hz

### `samples.cljs` — Sample loader

Fetches two manifests from Strudel's CDN at startup:
- `https://strudel.b-cdn.net/dirt-samples.json` — TidalCycles Dirt-Samples
- `https://strudel.b-cdn.net/tidal-drum-machines.json` — Drum machine library

Manifest format:
```json
{ "_base": "https://raw.githubusercontent.com/...",
  "bd": ["bd/BT0AADA.wav", "bd/BT0AAD0.wav", ...] }
```

`get-buffer!` returns a `Promise<AudioBuffer>`, deduplicating concurrent requests
and caching decoded buffers. `play!` uses `max(scheduledTime, currentTime)` to
handle first-load latency gracefully.

### `app.cljs` — UI and wiring

- Builds the DOM (header, CodeMirror editor, footer)
- Creates the Lisp environment via `leval/make-env`
- Routes evaluated results: Pattern → audio scheduler, other → output line
- **▶ play / ■ stop** button evaluates the editor buffer or stops playback

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
  audio.cljs               setInterval tick
        │  every 25ms
        ▼
  core/query               pattern.query({start: [n,1], end: [n+1,1]}) → [event]
        │
        ▼
  audio/play-event         for each event: schedule AudioNode at t
        │
        ▼
  samples/play!            fetch buffer → AudioBufferSourceNode.start(t)
  (or synth voices)        OscillatorNode.start(t)
```

---

## Build system

**shadow-cljs** compiles all ClojureScript. Two build targets:

| Target | Command | Output |
|---|---|---|
| `:app` | `npx shadow-cljs watch app` | `app/public/js/main.js` |
| `:test` | `npx shadow-cljs compile test` | `out/test.js` (Node) |

Source paths span all three packages:
```edn
:source-paths ["app/src" "packages/core/src" "packages/lisp/src"]
```

npm workspaces link the three packages together. The dev HTTP server serves
`app/public/` at port 3000.

---

## Future phases

| Phase | Focus |
|---|---|
| **2** | Rust/WASM synthesis — better quality drum sounds via `wasm-pack` |
| **3** | AudioWorklet — move WASM to the audio thread, eliminate main-thread jank |
| **4** | Live features — named pattern slots, tap BPM, MIDI clock, session URLs |

See `PROMPTS/` for detailed specifications of each phase.
