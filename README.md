# REPuLse

A browser-based live coding instrument where music is written in a small, purpose-built Lisp.
Think [Strudel](https://strudel.cc), but the user-facing language is a minimal Lisp and the
pattern engine is implemented in ClojureScript.

The name: **REPL** + **pulse** (rhythm, heartbeat).

I love Clojure — its REPL-driven workflow, immutable data model, and the way it makes you think
about time and transformation. REPuLse is what happens when that mindset meets live music: a
sequencer you talk to like a Clojure REPL, where patterns are pure functions and the output is
sound.

---

## Quick start

**Requirements:** Node.js 18+, Java 11+ (for shadow-cljs), Rust + wasm-pack (for WASM synthesis)

```bash
git clone <repo>
cd repulse
npm install

# Build the Rust/WASM synthesis engine (first time only)
npm run build:wasm

# Start the dev server
npx shadow-cljs watch app
```

Open [http://localhost:3000](http://localhost:3000).

You should see a dark editor. Type this and press **Alt+Enter** (or **Option+Enter** on macOS):

```lisp
(seq :bd :sd :bd :sd)
```

You'll hear a kick and snare alternating in a loop. To stop:

```lisp
(stop)
```

> If you skip `npm run build:wasm`, the app still works — it falls back to a JS synthesis
> engine automatically. You'll see `[REPuLse] audio backend: clojurescript synthesis` in the browser console.

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
| `(scale :minor :c4 pat)` | Map degree integers in pat to Hz using a named scale |
| `(chord :major7 :c4)` | Stack chord tones as a pattern of Hz values |
| `(transpose 7 pat)` | Shift all Hz values by n semitones (keywords pass through) |
| `(->> pat (amp 0.8) (attack 0.02))` | Thread pattern through parameter transformers |
| `(amp 0.8 pat)` | Set amplitude 0.0–1.0; `(amp 0.8)` returns a transformer |
| `(attack 0.05 pat)` | Envelope attack time in seconds |
| `(decay 0.4 pat)` | Envelope decay time in seconds |
| `(release 0.5 pat)` | Envelope release time in seconds |
| `(pan -0.5 pat)` | Stereo panning -1.0 (left) to 1.0 (right) |
| `(comp f g …)` | Compose transformers right-to-left: `(def pluck (comp (amp 0.8) (decay 0.15)))` |
| `(~ "bd sd [hh hh] bd")` | Mini-notation: compact string syntax for patterns (see below) |
| `(alt p1 p2 p3)` | Alternation: cycle 0 plays p1, cycle 1 plays p2, etc. |
| `(load-gist url)` | Load a GitHub Gist into the editor and auto-evaluate |
| `(export 4)` | Render 4 cycles to a downloadable WAV file |
| `(arrange [[p 4] [q 8]])` | Play sections in order for N cycles each, then loop |
| `(play-scenes [p q r])` | Play each pattern for 1 cycle in sequence, then loop |
| `(bpm 140)` | Set the tempo in BPM (default: 120) |
| `(fx :reverb 0.4)` | Set reverb wet mix (built-in convolution reverb) |
| `(fx :dattorro-reverb 0.5)` | Dattorro plate reverb (AudioWorklet, high quality) |
| `(fx :delay :wet 0.4 :time 0.25)` | Tape delay with feedback |
| `(fx :filter 800)` | Lowpass filter cutoff in Hz |
| `(fx :compressor :threshold -18)` | Dynamics compressor |
| `(fx :off :reverb)` | Bypass an effect (transparent) |
| `(fx :on :reverb)` | Re-enable a bypassed effect |
| `(play :name pattern)` | Start or replace a named track — use in the editor buffer |
| `(mute! :name)` | Silence a track without removing it — use in the command bar |
| `(unmute! :name)` | Re-enable a muted track — use in the command bar |
| `(solo! :name)` | Play only this track, mute all others — use in the command bar |
| `(clear! :name)` | Remove a named track — use in the command bar |
| `(clear!)` | Remove all tracks and stop — use in the command bar |
| `(tracks)` | Return list of active track names |
| `(upd)` | Hot-swap: re-evaluate the editor buffer and update running tracks without stopping — use in the command bar |
| `(tap!)` | Register a BPM tap; 4 taps sets tempo (or click the tap button) |
| `(midi-sync! true)` | Enable MIDI clock sync from external hardware |
| `(samples! "github:owner/repo")` | Load sample banks from a public GitHub repo (auto-discovers audio files) |
| `(samples! "https://…/samples.edn")` | Load banks from a REPuLse Lisp manifest |
| `(samples! "https://…/samples.json")` | Load banks from a Strudel-compatible JSON manifest |
| `(sample-banks)` | List all currently registered sample bank names |
| `(load-plugin url)` | Load a visual or effect plugin from a URL |
| `(stop)` | Stop playback |
| `(demo :techno)` | Load and play a starter template (`:techno`, `:ambient`, `:dnb`, `:minimal`, `:house`, `:dub`, `:experimental`) |
| `(demo)` | List all available demo templates |
| `(tutorial)` | Load tutorial chapter 1 into the editor (does not auto-play) |
| `(tutorial 3)` | Load tutorial chapter 3 (chapters 1–8) |

### Sound values

| Value | Sound |
|---|---|
| `:bd` | Kick drum |
| `:sd` | Snare |
| `:hh` | Closed hi-hat |
| `:oh` | Open hi-hat |
| `:_` | Rest (silence) |
| `440` | Sine tone at that frequency in Hz |
| `:c4`, `:eb3`, `:fs5` | Note keyword — sine tone at that pitch (s=sharp, b=flat) |
| `(sound :bank n)` | Sample from the Strudel CDN library by name and index |
| any loaded keyword | Plays the matching sample bank (e.g. `:cp`, `:bass`, `:tabla`) |

Over 100 sample banks from the [Strudel CDN](https://strudel.cc) (TidalCycles Dirt-Samples +
Tidal Drum Machines) are loaded at startup. Use `(sound :bd 2)` for indexed access.

### Examples

```lisp
; Four-on-the-floor kick with snare on 2 and 4
(stack (seq :bd :bd :bd :bd)
       (seq :_ :sd :_ :sd))

; Hi-hats twice as fast as the kick
(stack (seq :bd :_ :bd :_)
       (fast 2 (seq :hh :_)))

; Open hi-hat on the offbeat
(stack (seq :bd :_ :bd :_)
       (seq :_ :oh :_ :oh))

; Every 4th cycle, double the speed
(every 4 (fast 2) (seq :bd :sd :bd :sd))

; Define and reuse patterns
(def kick (seq :bd :_ :bd :_))
(def snare (seq :_ :sd :_ :sd))
(stack kick snare)

; Tone sequence
(seq 220 330 440 550)

; Samples from the library
(seq (sound :tabla 0) (sound :tabla 1) :_ :_)

; Effect chain — reverb and filtered delay
(do
  (fx :reverb 0.3)
  (fx :delay :wet 0.4 :time 0.25 :feedback 0.4)
  (fx :filter 3000)
  (seq :bd :sd :bd :sd))
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

### Mini-notation

`~` parses a compact string into a pattern, composing with all other functions:

| Syntax | Example | Meaning |
|---|---|---|
| space-separated | `"bd sd hh"` | sequence of 3 equal slots |
| `[…]` | `"bd [sd hh]"` | subdivision — contents share one parent slot |
| `*N` | `"hh*4"` | repeat N times |
| `<…>` | `"<bd sd cp>"` | alternation — cycle N picks element mod N |
| `~` or `_` | `"bd ~ sd"` | rest / silence (`:_`) |
| `?` | `"bd?"` | 50% probability of playing |
| `:N` | `"bd:2"` | sample index (`{:bank :bd :n 2}`) |
| `@N` | `"bd@3 sd"` | weight — bd takes 3 units, sd takes 1 |
| bare number | `"440"` | numeric value |
| note name | `"c4 eb3"` | keyword (`:c4`, `:eb3`) |

```lisp
(~ "bd [sd hh] bd sd")                        ; subdivision
(->> (~ "c4 e4 g4") (amp 0.6) (attack 0.02))  ; with params
(fast 2 (~ "bd sd"))                           ; with transformers
(stack (~ "bd _ bd _") (~ "_ sd _ sd"))        ; stacked
```

---

## Development setup

### Repository structure

```
repulse/
├── packages/
│   ├── core/        # Pattern algebra — pure CLJS, no DOM, no audio
│   │   └── src/repulse/core.cljs
│   ├── lisp/        # REPuLse-Lisp reader + evaluator
│   │   └── src/repulse/lisp/
│   │       ├── reader.cljs   # Recursive-descent parser
│   │       ├── eval.cljs     # Environment-based evaluator
│   │       └── core.cljs     # Public eval-string entry point
│   └── audio/       # Rust/WASM synthesis engine
│       ├── Cargo.toml
│       └── src/lib.rs        # Kick, snare, hi-hat, tone via web-sys
├── app/             # Browser app
│   ├── src/repulse/
│   │   ├── app.cljs          # UI bootstrap + CodeMirror 6 editor
│   │   ├── audio.cljs        # Web Audio scheduler + WASM integration
│   │   ├── samples.cljs      # Sample loader (CDN + external manifests + GitHub)
│   │   ├── plugins.cljs      # Plugin registry
│   │   ├── fx.cljs           # Effect chain manager
│   │   ├── plugins/
│   │   │   └── compressor.cljs  # Dynamics compressor (CLJS implementation)
│   │   └── lisp-lang/        # CodeMirror 6 language extension
│   │       ├── repulse-lisp.grammar  # Lezer grammar source
│   │       ├── parser.js             # Generated parser (committed)
│   │       ├── highlight.js          # Syntax highlight tag mapping
│   │       ├── rainbow.js            # Rainbow delimiter ViewPlugin
│   │       └── index.js              # LanguageSupport export
│   └── public/
│       ├── plugins/
│       │   ├── oscilloscope.js      # Built-in oscilloscope visual plugin
│       │   ├── reverb.js            # Convolution reverb effect
│       │   ├── delay.js             # Tape delay effect
│       │   ├── filter.js            # Biquad filter effect
│       │   └── dattorro-reverb.js   # Dattorro plate reverb (AudioWorklet)
│       ├── worklets/
│       │   └── dattorro-reverb-processor.js  # AudioWorkletProcessor
│       └── …            # Static assets + compiled JS output
├── package.json     # npm workspaces root
└── shadow-cljs.edn  # Build config
```

### Build targets

```bash
# Build Rust/WASM engine (required once, or after changing packages/audio/src/lib.rs)
npm run build:wasm

# Start dev server with hot reload at http://localhost:3000
npx shadow-cljs watch app

# Both in one step
npm run dev:full

# Run core unit tests (Node.js)
npx shadow-cljs compile test && node out/test.js
```

### How it fits together

1. **`packages/core`** — pure pattern algebra. A `Pattern` is a map `{:query fn}` where the
   function takes a `{:start r :end r}` span (rational numbers as `[n d]` vectors) and returns
   a sequence of events. Zero dependencies beyond `cljs.core`.

2. **`packages/lisp`** — a hand-written recursive-descent reader and an environment-based
   evaluator. The initial environment is populated with all `core` functions. Undefined symbol
   errors include Levenshtein-based typo hints.

3. **`packages/audio`** — a Rust crate compiled to WASM via `wasm-pack`. Provides an
   `AudioEngine` class with a `trigger(value, time)` method. All synthesis (kick, snare,
   hi-hats, tones) uses Web Audio API nodes via `web-sys`. Noise buffers are generated with
   a deterministic LCG — no `Math.random()`. Loaded as a `<script type="module">` in
   `index.html` to bypass Closure Compiler's lack of `import.meta` support.

4. **`app/audio.cljs`** — implements the [Web Audio lookahead clock](https://web.dev/articles/audio-scheduling)
   pattern (Chris Wilson). A `setInterval` fires every 25 ms and schedules any events falling
   within the next **200 ms** lookahead window using `AudioContext.currentTime`. Sound dispatch:
   sample bank → WASM synth → JS synth fallback.

5. **`app/samples.cljs`** — fetches two JSON manifests from the Strudel CDN at startup and
   builds a lazy buffer cache. Buffers are decoded on first use and reused thereafter.

6. **`app/plugins.cljs`** — a plugin registry with protocol validation. Plugins are ES
   module default exports — either a plain object or a class extending `VisualPlugin` /
   `EffectPlugin` from `app/public/plugin-base.js`. The built-in oscilloscope auto-loads
   at startup. Load third-party plugins at runtime: `(load-plugin "https://…/plugin.js")`.
   The `AnalyserNode` on the master bus feeds visual plugins.
   See [docs/PLUGINS.md](docs/PLUGINS.md) for the full plugin development guide.

7. **`app/app.cljs`** — mounts a CodeMirror 6 main editor and a single-line command bar
   editor; wires **Alt+Enter** to `eval-string` and routes Pattern results to the audio
   scheduler vs. plain values to the output line. A live context panel shows BPM, user `def`
   bindings, and the active effect chain; a track timeline panel below the editor shows all
   active tracks with SVG event bars and a `requestAnimationFrame` playhead sweep. All panels
   update reactively via `add-watch` on the relevant atoms.

### Editor keybindings

| Key | Action |
|---|---|
| Alt+Enter / Option+Enter | Evaluate buffer (global — works even when editor is not focused) |
| Ctrl+. or F9 | Run `(upd)` — apply BPM/param changes without restarting patterns (global) |
| Escape | Focus command bar |
| Ctrl+A / Cmd+A | Select all code (global — works even when editor is not focused) |
| Ctrl+Z / Cmd+Z | Undo |
| Ctrl+Shift+Z / Cmd+Shift+Z | Redo |

### Command bar

Below the editor is a single-line **command bar** (bash-style REPL) for imperative
commands that shouldn't live in the buffer — `mute!`, `unmute!`, `solo!`, `clear!`,
`tap!`, `tracks`, `upd`, etc.

| Key | Action |
|---|---|
| Enter | Evaluate and clear |
| Escape | Clear and return focus to editor |

The command bar has full syntax highlighting and autocompletion (same as the main editor),
but its content is not saved to `localStorage`.

### Adding a new built-in

**Pattern built-ins** (pure functions, no DOM or audio):
1. Implement in [packages/core/src/repulse/core.cljs](packages/core/src/repulse/core.cljs)
2. Add to `make-env` in [packages/lisp/src/repulse/lisp/eval.cljs](packages/lisp/src/repulse/lisp/eval.cljs)
3. Add a test in [packages/core/src/repulse/core_test.cljs](packages/core/src/repulse/core_test.cljs)

**Audio/UI built-ins** (touch audio, DOM, or network — e.g. `fx`, `load-plugin`, `samples!`):
Add directly to the `assoc` in `ensure-env!` inside [app/src/repulse/app.cljs](app/src/repulse/app.cljs)

**Syntax highlighting + autocompletion for any new built-in:**

The Lezer grammar controls which names are coloured as built-ins in the editor.
The compiled parser is committed to the repo — edit the source, then regenerate:

1. Add the new name to `BuiltinName` in
   [app/src/repulse/lisp-lang/repulse-lisp.grammar](app/src/repulse/lisp-lang/repulse-lisp.grammar)
2. Run `npm run gen:grammar` — this overwrites `parser.js` and `parser.terms.js`
3. Add a completion entry (with `detail` docstring) to
   [app/src/repulse/lisp-lang/completions.js](app/src/repulse/lisp-lang/completions.js)

The generated `parser.js` must be committed alongside the grammar source.

### Adding a new synthesized voice

Open [packages/audio/src/lib.rs](packages/audio/src/lib.rs) and add a match arm to `trigger()`,
then implement a `play_*` method on `AudioEngine`. Run `npm run build:wasm` to rebuild.

---

## Browser support

Works on Chrome, Firefox, and Safari. Safari requires the `webkitAudioContext` fallback and
an unconditional `.resume()` call — both handled automatically.
