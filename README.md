# REPuLse

A browser-based live coding instrument where music is written in a small, purpose-built Lisp.
Think [Strudel](https://strudel.cc), but the user-facing language is a minimal Lisp and the
pattern engine is implemented in ClojureScript.

The name: **REPL** + **pulse** (rhythm, heartbeat).

---

## Quick start

**Requirements:** Node.js 18+, Java 11+ (for shadow-cljs)

```bash
git clone <repo>
cd repulse
npm install
npx shadow-cljs watch app
```

Open [http://localhost:3000](http://localhost:3000).

You should see a dark editor. Type this and press **Ctrl+Enter** (or **Cmd+Enter** on macOS):

```lisp
(seq :bd :sd :bd :sd)
```

You'll hear a kick and snare alternating in a loop. To stop:

```lisp
(stop)
```

---

## The language

REPuLse-Lisp is a minimal Lisp DSL wrapped around a pattern algebra. A **pattern** is a pure
function from a time span to a list of events — the same model used by TidalCycles and Strudel.

### Built-in pattern functions

| Expression | Description |
|---|---|
| `(seq :bd :sd :hh)` | Sequence values evenly across one cycle |
| `(stack p1 p2)` | Layer two patterns simultaneously |
| `(pure :bd)` | One value repeated every cycle |
| `(fast 2 pat)` | Double the speed |
| `(slow 2 pat)` | Halve the speed |
| `(rev pat)` | Reverse a pattern within each cycle |
| `(every 4 (fast 2) pat)` | Apply transform every 4th cycle |
| `(fmap f pat)` | Apply a function to every event value |
| `(stop)` | Stop playback |

### Sound values

| Value | Sound |
|---|---|
| `:bd` | Kick drum (low sine burst) |
| `:sd` | Snare (noise burst) |
| `:hh` | Hi-hat (high noise burst) |
| `440` | Sine tone at that frequency in Hz |
| any other keyword | Sine tone at 440 Hz |

### Examples

```lisp
; Four-on-the-floor kick with snare on 2 and 4
(stack (seq :bd :bd :bd :bd)
       (seq :_ :sd :_ :sd))

; Fast hi-hat pattern
(fast 2 (seq :hh :hh :hh :hh))

; Every 4th cycle, double the speed
(every 4 (fast 2) (seq :bd :sd :bd :sd))

; Define and reuse patterns
(def kick (seq :bd :_ :bd :_))
(def snare (seq :_ :sd :_ :sd))
(stack kick snare)

; Tone sequence
(seq 220 330 440 550)
```

### Language features

```lisp
; Local bindings
(let [n 4
      pat (seq :bd :sd)]
  (fast n pat))

; Anonymous functions
(fmap (fn [x] (if (= x :bd) 80 440)) (seq :bd :sd))

; Arithmetic
(fast (+ 1 1) (seq :bd :sd))
```

---

## Development setup

### Repository structure

```
repulse/
├── packages/
│   ├── core/        # Pattern algebra — pure CLJS, no DOM, no audio
│   │   └── src/repulse/core.cljs
│   └── lisp/        # REPuLse-Lisp reader + evaluator
│       └── src/repulse/lisp/
│           ├── reader.cljs   # Tokeniser + parser
│           ├── eval.cljs     # Environment-based evaluator
│           └── core.cljs     # Public eval-string entry point
├── app/             # Browser app
│   ├── src/repulse/
│   │   ├── app.cljs    # UI bootstrap + CodeMirror 6 editor
│   │   └── audio.cljs  # Web Audio scheduler
│   └── public/         # Static assets + compiled JS output
├── package.json     # npm workspaces root
└── shadow-cljs.edn  # Build config
```

### Build targets

```bash
# Start dev server with hot reload at http://localhost:3000
npx shadow-cljs watch app

# Run core unit tests (Node.js)
npx shadow-cljs compile test && node out/test.js

# Production build
npx shadow-cljs release app
```

### How it fits together

1. **`packages/core`** — pure pattern algebra. A `Pattern` is a map `{:query fn}` where the
   function takes a `{:start r :end r}` span (rational numbers as `[n d]` vectors) and returns
   a sequence of events. Zero dependencies beyond `cljs.core`.

2. **`packages/lisp`** — a hand-written recursive-descent reader and an environment-based
   evaluator. The initial environment is populated with all `core` functions. Undefined symbol
   errors include Levenshtein-based typo hints.

3. **`app/audio.cljs`** — implements the [Web Audio lookahead clock](https://web.dev/articles/audio-scheduling)
   pattern (Chris Wilson). A `setInterval` fires every 25 ms and schedules any events falling
   within the next 100 ms lookahead window using `AudioContext.currentTime`.

4. **`app/app.cljs`** — mounts a CodeMirror 6 editor, wires **Ctrl+Enter** to `eval-string`,
   and routes Pattern results to the audio scheduler vs. plain values to the output line.

### Editor keybindings

| Key | Action |
|---|---|
| Ctrl+Enter / Cmd+Enter | Evaluate buffer |
| Ctrl+Z / Cmd+Z | Undo |
| Ctrl+Shift+Z / Cmd+Shift+Z | Redo |

### Adding a new built-in

1. Implement the function in [packages/core/src/repulse/core.cljs](packages/core/src/repulse/core.cljs)
2. Add it to the env map in [packages/lisp/src/repulse/lisp/eval.cljs](packages/lisp/src/repulse/lisp/eval.cljs) — `make-env`
3. Add a test in [packages/core/src/repulse/core_test.cljs](packages/core/src/repulse/core_test.cljs)

### Adding a new voice

Open [app/src/repulse/audio.cljs](app/src/repulse/audio.cljs) and add a clause to the `play-event` multimethod at the bottom of the file.
