# REPuLse — Usage Reference

REPuLse is a browser-based live coding instrument. You write patterns in a minimal Lisp,
evaluate them with **Ctrl+Enter** (or the **▶ play** button), and hear them loop in real time.

---

## Table of Contents

1. [Getting started](#getting-started)
2. [The editor](#the-editor)
3. [Language basics](#language-basics)
   - [Comments](#comments)
   - [Literals](#literals)
   - [Arithmetic](#arithmetic)
   - [Comparison and logic](#comparison-and-logic)
   - [Map literals](#map-literals-and-operations)
   - [Local bindings — `let`](#local-bindings--let)
   - [Anonymous functions — `fn`](#anonymous-functions--fn)
   - [Conditionals — `if`](#conditionals--if)
   - [Sequential evaluation — `do`](#sequential-evaluation--do)
   - [Top-level definitions — `def`](#top-level-definitions--def)
4. [Pattern functions](#pattern-functions)
5. [Sound and samples](#sound-and-samples)
6. [Tempo control](#tempo-control)
7. [Combining patterns](#combining-patterns)
8. [Defining names](#defining-names)
9. [Song arrangement](#song-arrangement)
10. [Effect plugins](#effect-plugins)
11. [Visual plugins](#visual-plugins)
12. [Available sample banks](#available-sample-banks)
13. [Error messages](#error-messages)
14. [Examples](#examples)

---

## Getting started

```bash
npm install

# Build the Rust/WASM synthesis engine (first time only)
npm run build:wasm

# Start the dev server
npx shadow-cljs watch app
# open http://localhost:3000
```

> If you skip `npm run build:wasm`, the app still works — it falls back to a JavaScript
> synthesis engine automatically. You'll see `audio backend: clojurescript synthesis`
> in the browser console instead of `audio backend: audioworklet+wasm`.

Type an expression in the editor and press **Ctrl+Enter** (macOS: **Cmd+Enter**) or click **▶ play**.

To stop: type `(stop)` and evaluate it, or click **■ stop**.

---

## The editor

| Key | Action |
|---|---|
| **Ctrl+Enter** / **Cmd+Enter** | Evaluate the entire buffer and start playback |
| **Ctrl+Z** / **Cmd+Z** | Undo |
| **Ctrl+Shift+Z** / **Cmd+Shift+Z** | Redo |

The **footer line** shows the last result or error. The **dot** in the header pulses on each beat
when a pattern is playing.

The **▶ play** / **■ stop** button in the header evaluates the current editor content and
toggles playback.

**Active code highlighting:** while a pattern plays, the tokens in the editor that produced
each event flash amber at the moment they fire. This lets you see exactly which part of your
code is being heard — useful for understanding how `every`, `fast`, and nested patterns behave
in real time.

---

## Language basics

REPuLse-Lisp is a minimal Lisp. Every expression is either a literal or a function call.

### Comments

```lisp
; this is a line comment — everything after ; is ignored
(seq :bd :sd)  ; inline comment after an expression
```

Comments run to the end of the line. There are no block comments.

### Literals

```lisp
42          ; integer
3.14        ; float
"hello"     ; string
:bd         ; keyword  — the main way to name sounds
true false  ; booleans
nil         ; null
```

Strings support the standard escape sequences: `\n` (newline), `\t` (tab), `\\` (backslash),
`\"` (double quote).

Commas are treated as whitespace — `{:a 1, :b 2}` is the same as `{:a 1 :b 2}`.

### Function calls

```lisp
(function arg1 arg2 ...)
```

### Arithmetic

```lisp
(+ 1 2)        ; => 3
(- 10 3)       ; => 7
(* 2 (+ 3 4))  ; => 14
(/ 1 3)        ; => 0.333...
```

### Comparison and logic

```lisp
(= 1 1)        ; => true
(not= 1 2)     ; => true
(< 1 2)        ; => true
(>= 4 4)       ; => true
(not true)     ; => false
```

### Map literals and operations

```lisp
{:key "value" :n 42}         ; map literal

(get {:a 1} :a)              ; => 1
(get {:a 1} :b "default")    ; => "default"
(assoc {:a 1} :b 2)          ; => {:a 1 :b 2}
(merge {:a 1} {:b 2})        ; => {:a 1 :b 2}
(keys {:a 1 :b 2})           ; => (:a :b)
(vals {:a 1 :b 2})           ; => (1 2)
```

Maps are mainly used for parametric section factories:

```lisp
(def make-section
  (fn [opts]
    (let [dense? (get opts :dense false)]
      (if dense? (stack kick snare hats) (stack kick snare)))))

(make-section {:dense true})
```

### Local bindings — `let`

```lisp
(let [x 4
      y 2]
  (fast x (seq :bd :sd)))
```

The body of a `let` may contain multiple expressions; the last is returned:

```lisp
(let [n 3
      base (seq :bd :sd)]
  (fast n base))    ; this is the return value
```

Later bindings may reference earlier ones in the same `let`:

```lisp
(let [base (seq :bd :sd)
      fast-base (fast 2 base)]
  fast-base)
```

### Anonymous functions — `fn`

```lisp
(fn [x] (fast x (seq :bd :sd)))
```

A function body may contain multiple expressions; the last one is the return value:

```lisp
(fn [n]
  (def base (seq :bd :sd))   ; side effect: binds 'base'
  (fast n base))             ; return value
```

`lambda` is an alias for `fn`:

```lisp
(lambda [x] (* x 2))        ; same as (fn [x] (* x 2))
```

### Conditionals — `if`

```lisp
(if condition then-expr else-expr)
(if condition then-expr)     ; else is optional — returns nil when false
```

```lisp
(if true  :yes :no)          ; => :yes
(if false :yes :no)          ; => :no
(if false :yes)              ; => nil

; Common pattern inside fmap:
(fmap (fn [x] (if (= x :bd) 80 440)) (seq :bd :sd))
```

### Sequential evaluation — `do`

`do` evaluates a sequence of expressions and returns the value of the last one:

```lisp
(do (bpm 140) (seq :bd :sd)) ; set BPM then return the pattern
```

This is useful when you want to perform a side effect (like `bpm`) before returning a pattern.
`def` inside `do` works as expected:

```lisp
(do
  (def kick (seq :bd :_ :bd :_))
  (def snare (seq :_ :sd :_ :sd))
  (stack kick snare))
```

### Top-level definitions — `def`

```lisp
(def kick (seq :bd :_ :bd :_))
(def snare (seq :_ :sd :_ :sd))
(stack kick snare)
```

`def` bindings persist for the session. Re-evaluate to update them.

---

## Pattern functions

A **Pattern** is a pure function from a time span to a list of events. Evaluating a pattern
expression starts playback; evaluating a non-pattern expression (a number, string, etc.)
just prints the result.

### `seq` — sequence

Spread values evenly across one cycle (one bar):

```lisp
(seq :bd :sd :bd :sd)        ; kick snare kick snare, 4 per bar
(seq :bd :sd :hh :sd :hh)    ; 5 notes per bar (odd metres work fine)
(seq 220 440 330 550)         ; tone sequence in Hz
```

### `stack` — layer

Play multiple patterns simultaneously:

```lisp
(stack (seq :bd :bd :bd :bd)
       (seq :_ :sd :_ :sd)
       (seq :hh :hh :hh :hh))
```

### `pure` — single value

One value repeated every cycle:

```lisp
(pure :bd)    ; one kick per bar
```

### `fast` — speed up

```lisp
(fast 2 (seq :hh :hh))    ; double speed — 4 hats per bar
(fast 0.5 (seq :bd :sd))  ; half speed — one hit every 2 bars
```

### `slow` — slow down

```lisp
(slow 2 (seq :bd :sd :bd :sd))    ; half speed
(slow 3 (pure :bd))               ; one kick every 3 bars
```

### `rev` — reverse

Reverse event order within each cycle:

```lisp
(rev (seq :bd :sd :hh :cp))    ; cp hh sd bd
```

### `every` — conditional transform

Apply a transformation every nth cycle, leave pattern unchanged otherwise:

```lisp
(every 4 (fast 2) (seq :bd :sd))   ; double speed on every 4th bar
(every 2 rev (seq :bd :hh :sd :hh)) ; reverse every other bar
```

### `fmap` — transform values

Apply a function to every event value:

```lisp
(fmap (fn [x] (if (= x :bd) 80 440)) (seq :bd :sd))
; plays 80 Hz on bd events and 440 Hz on sd events
```

---

## Sound and samples

REPuLse loads the **TidalCycles Dirt-Samples** and the **Tidal Drum Machines** sample library
automatically from Strudel's CDN when the app starts. Samples are fetched and cached on first use.

### Keywords as sounds

Any keyword is looked up as a sample bank name:

```lisp
(seq :bd :sd :hh :cp)     ; kick, snare, hi-hat, clap
(seq :arpy :arpy :arpy)   ; arpeggio synth
(seq :bass)               ; bass
```

If the sample bank doesn't exist, REPuLse falls back to the synthesis engine (a sine tone at 440 Hz).

### Silence / rest

Use `:_` for a silent step:

```lisp
(seq :bd :_ :sd :_)    ; kick on 1, snare on 3
```

### Numbers as frequencies

Numbers are treated as Hz and played as a sine tone:

```lisp
(seq 110 220 330 440)     ; ascending tones
(pure 60)                  ; 60 Hz drone
```

### `sound` — pick a specific sample from a bank

Most sample banks contain multiple variations (different kit sounds). Use `sound` to pick one:

```lisp
(sound :bd 0)     ; first bd sample (same as :bd alone)
(sound :bd 3)     ; fourth bd sample
(sound :arpy 5)   ; sixth arpy sample

; Use in sequences:
(seq (sound :bd 0) (sound :bd 2) (sound :sd 1) :hh)
```

### Built-in synthesis fallback

These keywords always work even if samples haven't loaded yet — they are synthesized
by the Rust/WASM engine (or the JS fallback if WASM is unavailable):

| Keyword | Synthesis |
|---|---|
| `:bd` | Sine sweep kick (150 → 40 Hz) |
| `:sd` | Bandpass-filtered noise + sine crack |
| `:hh` | Highpass-filtered noise, 45 ms (closed) |
| `:oh` | Highpass-filtered noise, 350 ms (open) |

---

## Tempo control

### `bpm` — beats per minute

Default tempo is **120 BPM** (one cycle = one bar = 4 beats = 2 seconds).

```lisp
(bpm 140)                       ; set tempo to 140 BPM
(bpm 90)                        ; slow it down
(stack (bpm 160) (seq :bd :sd)) ; set tempo and play together
```

`bpm` takes effect immediately, even mid-playback. One cycle = one bar regardless of BPM.

---

## Combining patterns

Patterns can be composed freely:

```lisp
; Layered groove
(stack
  (seq :bd :_ :bd :_)
  (seq :_ :sd :_ :sd)
  (fast 2 (seq :hh :_)))

; Polyrhythm — 3 against 4
(stack
  (seq :bd :bd :bd)
  (seq :hh :hh :hh :hh))

; Conditional variation
(every 4 (fast 2)
  (stack (seq :bd :sd) (seq :hh :hh :hh :hh)))

; Reverse every other bar
(every 2 rev (seq :bd :hh :sd :oh))
```

---

## Defining names

`def` binds a name in the current session:

```lisp
(def kick  (seq :bd :_ :bd :_))
(def snare (seq :_ :sd :_ :sd))
(def hats  (fast 2 (seq :hh :_)))

(stack kick snare hats)
```

After re-evaluating a `def`, any pattern that references it will use the new value on the
next cycle.

`let` is for local, non-persistent bindings:

```lisp
(let [n 3
      base (seq :bd :sd :hh)]
  (every n (fast 2) base))
```

---

## Song arrangement

`arrange` and `play-scenes` let you compose multi-section pieces that loop automatically.

### `arrange` — section sequence with cycle counts

```lisp
(arrange [[intro  4]    ; intro plays for 4 cycles
          [verse  8]    ; verse plays for 8 cycles
          [chorus 8]])  ; chorus plays for 8 cycles, then loops
```

Each entry is `[pattern cycles]`. After the total duration the arrangement loops.

### `play-scenes` — one cycle per section

```lisp
(play-scenes [verse verse chorus bridge chorus])
```

Shorthand where every section plays for exactly 1 cycle. Useful for short, bar-length patterns.

### Parametric sections with maps

```lisp
(def motif-a (seq :bd :_ :bd :_))
(def motif-b (seq :bd :bd :sd :_))

(def make-verse
  (fn [opts]
    (let [dense? (get opts :dense false)]
      (if dense? (stack motif-b hats) (stack motif-a hats)))))

(def verse-1 (make-verse {}))
(def verse-2 (make-verse {:dense true}))

(arrange
  [[intro   4]
   [verse-1 8]
   [chorus  8]
   [verse-2 8]
   [chorus  8]])
```

---

## Effect plugins

REPuLse has a built-in effect chain between the synthesis engine and the audio output.
Five effects are loaded automatically at startup, all silent by default (wet = 0).
Control them with the `fx` built-in.

### `fx` — effect control

```lisp
; Set wet mix (positional — shorthand)
(fx :reverb 0.4)           ; reverb wet = 0.4
(fx :delay 0.5)            ; delay wet = 0.5

; Set named parameters
(fx :reverb :wet 0.4 :room 0.8)
(fx :delay  :wet 0.5 :time 0.25 :feedback 0.4)
(fx :filter :freq 800 :q 2)

; Bypass (mute) an effect
(fx :off :reverb)          ; bypass reverb — dry signal passes unchanged
(fx :on  :reverb)          ; un-bypass reverb

; Remove an effect from the chain
(fx :remove :delay)
```

### Built-in effects

#### `reverb` — convolution reverb

Stereo convolution reverb using a procedurally generated impulse response.

| Parameter | Key | Default | Range |
|-----------|-----|---------|-------|
| Wet mix   | `wet` / positional | `0.3` | 0–1 |

```lisp
(fx :reverb 0.4)
```

#### `delay` — tape delay

Stereo feedback delay with a tempo-synced default time.

| Parameter | Key | Default | Description |
|-----------|-----|---------|-------------|
| Wet mix   | `wet` / positional | `0.0` | Overall delay level |
| Delay time | `time` | `0.375` s | Delay in seconds |
| Feedback  | `feedback` | `0.35` | Repeat amount (max 0.95) |

```lisp
(fx :delay 0.4)
(fx :delay :wet 0.4 :time 0.25 :feedback 0.5)
```

#### `filter` — biquad filter

A `BiquadFilterNode` with configurable type, cutoff, and resonance.

| Parameter | Key | Default | Description |
|-----------|-----|---------|-------------|
| Cutoff freq | `freq` / positional | `20000` Hz | Filter cutoff (20000 = fully open) |
| Resonance   | `q`   | `1.0` | Q / resonance |
| Filter type | `type` | `"lowpass"` | `"lowpass"` `"highpass"` `"bandpass"` `"notch"` |

```lisp
(fx :filter 800)                          ; lowpass at 800 Hz
(fx :filter :freq 1200 :q 4)             ; resonant lowpass
(fx :filter :freq 500 :type "highpass")  ; highpass
```

#### `compressor` — dynamics compressor

A `DynamicsCompressorNode` with full dry/wet control.

| Parameter | Key | Default | Description |
|-----------|-----|---------|-------------|
| Wet mix   | `wet` / positional | `1.0` | Compressed signal level |
| Threshold | `threshold` | `-24` dB | Level above which compression starts |
| Ratio     | `ratio` | `4` | Compression ratio |
| Attack    | `attack` | `0.003` s | |
| Release   | `release` | `0.25` s | |
| Knee      | `knee` | `10` dB | Soft-knee width |

```lisp
(fx :compressor 0.8)
(fx :compressor :threshold -18 :ratio 6)
```

#### `dattorro-reverb` — Dattorro plate reverb

A high-quality plate reverb implemented as an AudioWorkletProcessor, based on the
algorithm published by Jon Dattorro (AES 1997). Uses a network of all-pass filters,
delay lines, and LFO modulation for a lush, shimmer-free tail.

| Parameter | Key | Default | Description |
|-----------|-----|---------|-------------|
| Wet mix   | `wet` / positional | `0.0` | Reverb return level |
| Decay     | `decay` | `0.5` | Tail length (0 = dead, 0.99 = very long) |
| Damping   | `damping` | `0.0005` | High-frequency absorption (0 = bright, 0.9 = dark) |
| Bandwidth | `bandwidth` | `0.9999` | Input high-frequency rolloff |
| Pre-delay | `predelay` | `0.0` s | Delay before the reverb begins (0–0.5 s) |

```lisp
(fx :dattorro-reverb 0.5)
(fx :dattorro-reverb :wet 0.5 :decay 0.8 :damping 0.2)
(fx :dattorro-reverb :predelay 0.02)   ; 20 ms pre-delay
```

Note: the worklet loads asynchronously. Reverb becomes active a few milliseconds
after startup — this is inaudible in practice.

### Effect chain order

The fixed signal chain is:
```
synthesis → reverb → delay → filter → compressor → dattorro-reverb → output
```

Use `(fx :remove :name)` to take an effect out of the chain entirely.
Use `(fx :off :name)` / `(fx :on :name)` for transparent bypass without removing.

### Loading a custom effect

Custom effect plugins follow the same plugin interface as the built-ins and can be
loaded at runtime:

```lisp
(load-plugin "/plugins/my-effect.js")
(load-plugin "https://example.com/chorus.js")
```

See `docs/ARCHITECTURE.md` for the full effect plugin interface specification.

---

## Visual plugins

A permanent `AnalyserNode` sits on the master audio bus. Visual plugins read from it and
draw to a canvas in the **plugin panel** that appears below the editor.

The oscilloscope is loaded automatically at startup. You can reload it or load other plugins:

```lisp
(load-plugin "/plugins/oscilloscope.js")
(load-plugin "https://example.com/my-spectrum.js")
```

### Plugin interface

Plugins are ES module default exports with this shape:

```javascript
export default {
  type:    "visual",        // required: "visual"
  name:    "my-plugin",    // required: unique name
  version: "1.0.0",

  init(host) {
    // host.analyser    — AnalyserNode (fftSize 2048)
    // host.audioCtx    — AudioContext
    // host.masterGain  — GainNode
    // host.registerLisp(name, fn) — add a Lisp built-in
  },

  mount(container) { /* append canvas, start animation */ },
  unmount()        { /* cancel animation, remove canvas */ },
  destroy()        { /* unmount + release refs */ }
};
```

Loading a plugin with the same name replaces the existing registration.

---

## Available sample banks

Sample banks are loaded from the **Dirt-Samples** and **Tidal Drum Machines** collections.
They are available after a short initial network fetch.

### Dirt-Samples (classic TidalCycles sounds)

| Bank | Description |
|---|---|
| `:bd` | Bass drum / kick |
| `:sd` | Snare drum |
| `:hh` | Closed hi-hat |
| `:oh` | Open hi-hat |
| `:cp` | Clap |
| `:cb` | Cowbell |
| `:cr` | Crash cymbal |
| `:ride` | Ride cymbal |
| `:lt` `:mt` `:ht` | Low / mid / high tom |
| `:arpy` | Arpeggio synth |
| `:bass` `:bass0`–`:bass3` | Various bass sounds |
| `:moog` | Moog synthesizer |
| `:jvbass` | Bass guitar |
| `:feel` | Texture / feel samples |
| `:house` | House music hits |
| `:tink` | Tink / high percussive |
| `:peri` | Peridactyl synth |
| `:glitch` | Glitch/noise textures |
| `:birds` | Bird sounds |
| `:alphabet` | Spoken letters |
| `:numbers` | Spoken numbers |
| `:diphone` | Speech diphones |
| `:gabba` | Gabba kick |
| `:tabla` | Tabla |
| `:tabla2` | Tabla variation |
| `:casio` | Casio keyboard |
| `:flick` | Flick/snap |
| `:psr` | PSR keyboard |
| `:electro1` | Electro hits |
| `:rave` `:rave2` | Rave stabs |
| `:space` | Space textures |
| `:metal` | Metal hits |
| `:can` | Metal can percussion |
| `:drum` | Drum machine hits |
| `:v` | Vocal hits |

### Tidal Drum Machines (full kit names)

These use the format `:<MachineModel>_<part>`:

```lisp
(seq :RolandTR808_bd :RolandTR808_sd :RolandTR808_hh :RolandTR808_cp)
(seq :RolandTR909_bd :RolandTR909_sd)
(seq :AkaiRhythm99_bd :AkaiRhythm99_sd)
```

Common drum machines in the library include: `RolandTR808`, `RolandTR909`, `RolandTR606`,
`RolandTR707`, `LinnDrum`, `AkaiMPC60`, `EmuDrumulator`, `KorgKR55`, `YamahaRX5`, and many more.

Use `(sound :RolandTR808_bd 2)` to pick from multiple kit variations.

---

## Error messages

REPuLse shows structured errors in the footer:

```
Error: Undefined symbol: fsat — did you mean fast?
Error: Unterminated list
Error: seq is not a function  (when called wrong)
```

Typo detection uses Levenshtein distance — if your symbol is within 3 edits of a known
name, a suggestion is shown.

---

## Examples

### Basic beat

```lisp
(seq :bd :sd :bd :sd)
```

### Four-on-the-floor

```lisp
(stack
  (seq :bd :bd :bd :bd)
  (seq :_ :sd :_ :sd)
  (fast 2 (seq :hh :_)))
```

### Triplet feel

```lisp
(seq :bd :hh :hh :sd :hh :hh)
```

### Polyrhythm

```lisp
(stack
  (seq :bd :_ :bd :_ :bd)   ; 5-beat kick
  (seq :hh :hh :hh :hh))    ; 4-beat hats
```

### Evolving pattern

```lisp
(every 4 (fast 2)
  (every 2 rev
    (stack (seq :bd :sd :bd :sd)
           (fast 2 (seq :hh :oh)))))
```

### Drum machine sounds

```lisp
(bpm 128)
(stack
  (seq :RolandTR808_bd :_ :RolandTR808_bd :_)
  (seq :_ :RolandTR808_sd :_ :RolandTR808_sd)
  (fast 2 (seq :RolandTR808_hh :RolandTR808_oh)))
```

### Tone melody

```lisp
(bpm 100)
(seq 220 277 330 415 330 277 220 165)
```

### Combining samples and synthesis

```lisp
(stack
  (seq :bd :_ :bd :_)
  (seq 440 330 220 165))
```

### Using `def` for structure

```lisp
(def kick  (seq :bd :_ :bd :bd))
(def snare (seq :_ :sd :_ :sd))
(def hats  (fast 4 (seq :hh :_ :hh :oh)))
(def fill  (every 4 (fast 2) (seq :bd :bd :sd :sd :hh :hh :cp :cp)))

(stack kick snare hats fill)
```

### Stopping

```lisp
(stop)
```
