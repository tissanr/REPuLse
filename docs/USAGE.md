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
5. [Pattern combinators](#pattern-combinators)
6. [Music theory](#music-theory)
7. [Per-event parameters](#per-event-parameters)
8. [Sound and samples](#sound-and-samples)
9. [Mini-notation](#mini-notation)
10. [Tempo control](#tempo-control)
11. [Named tracks](#named-tracks)
12. [Combining patterns](#combining-patterns)
13. [Defining names](#defining-names)
14. [Song arrangement](#song-arrangement)
15. [Lisp language features](#lisp-language-features)
16. [Effect plugins](#effect-plugins)
17. [Visual plugins](#visual-plugins)
18. [Available sample banks](#available-sample-banks)
19. [MIDI & External I/O](#midi--external-io)
20. [Error messages](#error-messages)
21. [Examples](#examples)

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

Type an expression in the editor and press **Alt+Enter** (macOS: **Option+Enter**) or click **▶ play**.

To stop: type `(stop)` and evaluate it, or click **■ stop**.

---

## Demos and tutorials

### Starter templates

Type in the command bar (the `>` input at the bottom):

    (demo :techno)

This loads a full multi-track techno pattern into the editor and starts playing it immediately.
Available demos:

| Demo | BPM | Description |
|---|---|---|
| `:techno` | 130 | Four-on-the-floor kick, offbeat hats, acid bassline |
| `:ambient` | 72 | Slow pad chords, gentle melodic line |
| `:dnb` | 174 | Breakbeat, sub bass, amen-style rhythm |
| `:minimal` | 120 | Sparse kick, subtle hats, one-note bass |
| `:house` | 124 | Classic four-on-the-floor, organ stabs |
| `:dub` | 140 | Heavy bass, delay-heavy snare |
| `:experimental` | 110 | Algorithmic patterns: every, rev, fmap |

Type `(demo)` with no arguments to list all available demos.

### Interactive tutorial

    (tutorial)       ;; loads chapter 1
    (tutorial 3)     ;; loads chapter 3

Eight chapters that teach REPuLse from first principles. Each chapter is a playable
program — press **Alt+Enter** to hear it, read the comments, experiment, then move on.

| Chapter | Topic | Key concept |
|---|---|---|
| 1 | First sound | `seq` |
| 2 | Layering | `stack` |
| 3 | Speed | `fast`, `slow` |
| 4 | Evolution | `every` |
| 5 | Naming | `def` |
| 6 | Multi-track | `track` |
| 7 | Melody | `scale`, `chord` |
| 8 | Expression | `amp`, `decay`, `->>` |

### Hover documentation

Hover the mouse over any built-in name in the editor to see its signature, a
description, and an example in a tooltip.

---

## The editor

| Key | Action |
|---|---|
| **Alt+Enter** / **Option+Enter** | Evaluate the entire buffer and start playback |
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

### Context panel

A sidebar to the right of the editor shows live session state:

- **BPM** — current tempo, updates immediately when `(bpm N)` is evaluated
- **Bindings** — all names you have defined with `def`, annotated with their inferred type (`pattern`, `fn`, `number`, etc.). Built-in names are not shown.
- **Effects** — the active effect chain in order, with the key parameter value for each effect and an `off` indicator when an effect is bypassed

The panel is read-only and updates reactively — no interaction required.

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

## Pattern combinators

These functions extend the pattern algebra with rhythmic algorithms, time-shifting,
stochastic variation, and spatial layering.

### `euclidean` — Euclidean rhythms

Distribute `k` onsets across `n` steps as evenly as possible (Björklund algorithm):

```lisp
(euclidean 5 8 :bd)      ; 5 onsets in 8 steps — Afro-Cuban clave feel
(euclidean 3 8 :sd)      ; tresillo
(euclidean 7 16 :hh)     ; complex subdivision

; Optional rotation — shift the pattern left by r steps:
(euclidean 5 8 :bd 2)    ; same rhythm, rotated 2 steps
```

### `cat` — sequential concatenation

Play patterns in sequence, one per cycle, then loop:

```lisp
(cat (seq :bd :sd) (seq :hh :oh :hh :oh))   ; 2-cycle loop
(cat kick fill fill fill)                     ; 1 fill per 4 cycles
```

### `late` / `early` — time shifting

Shift all events forward (`late`) or backward (`early`) within the cycle:

```lisp
(late  0.25  (seq :bd :sd :bd :sd))   ; delay by ¼ cycle
(early 0.125 hihat)                    ; advance by ⅛ cycle
```

### `sometimes` / `often` / `rarely` — probabilistic transforms

Apply a transformation with a given probability. The result is deterministic — the same
cycle always gets the same decision:

```lisp
(sometimes rev (seq :c4 :e4 :g4 :c5))   ; reverse ~50% of cycles
(often (fast 2) (seq :hh :oh))           ; speed up ~75% of cycles
(rarely rev melody)                       ; reverse ~25% of cycles
```

Use `sometimes-by` for custom probability:

```lisp
(sometimes-by 0.2 (fast 4) fill)   ; double-time 20% of cycles
```

### `degrade` / `degrade-by` — probabilistic event removal

Randomly drop events from a pattern each cycle:

```lisp
(degrade (seq :hh :hh :hh :hh))           ; drop ~50% of hi-hats
(degrade-by 0.3 (fast 4 (seq :hh :oh)))   ; drop 30% of events
```

### `choose` / `wchoose` — random selection per cycle

Pick one value from a list each cycle (deterministic per cycle):

```lisp
(choose [:bd :sd :hh :oh])                    ; uniform probability
(wchoose [[0.5 :bd] [0.3 :sd] [0.2 :hh]])    ; weighted — bd most likely
```

### `jux` / `jux-by` — stereo spatial layering

Stack the original pattern (panned left) with a transformed copy (panned right):

```lisp
(jux rev (seq :c4 :e4 :g4))    ; original left, reversed right
(jux (fast 2) (seq :hh :oh))   ; normal left, double-speed right
(jux-by 0.5 rev melody)        ; half stereo width
```

### `off` — time-offset layering

Layer the original with a time-shifted, transformed copy:

```lisp
(off 0.125 (fast 2) (seq :c4 :e4 :g4))   ; original + shifted double-speed copy
(off 0.25  rev      (seq :bd :sd :hh))    ; original + ¼-cycle offset reversed copy
```

Combines freely with all other combinators:

```lisp
(->> (euclidean 5 8 :bd)
     (sometimes rev)
     (jux (fast 2))
     (amp 0.8))
```

---

## Music theory

REPuLse has a built-in music theory layer that lets you write melodies, scales, and chords
without hand-computing frequencies. Note keywords, `scale`, `chord`, and `transpose` all
work with every existing pattern function — they are pure pattern combinators like `fast`
or `fmap`.

### Note keywords

Write pitches directly as keywords. The format is **note letter** (`a`–`g`) + optional
**accidental** (`s` = sharp, `b` = flat) + **octave number**:

```lisp
(seq :c4 :d4 :e4 :f4)          ; C D E F, one octave 4
(seq :eb3 :g3 :bb3)             ; E-flat G B-flat minor triad
(seq :fs4 :gs4 :bb4 :cs5)       ; chromatic run
```

Note keywords play as sine tones by default. Use `synth` (see [Per-event parameters](#per-event-parameters))
to apply a different voice — sawtooth, square, FM, or noise — to the whole pattern. Middle C is `:c4`,
concert A is `:a4` (440 Hz). Drum keywords like `:bd` and `:sd` are unaffected.

### `scale` — melodic patterns from scale degrees

Map one-indexed degree integers to frequencies in a named scale:

```lisp
(scale :major :c4 (seq 1 2 3 4 5 6 7))   ; C major scale
(scale :minor :a3 (seq 1 3 5))            ; A minor triad
(scale :pentatonic :g3 (fast 2 (seq 1 2 3 4 5)))

;; Degrees wrap into higher octaves
(scale :major :c4 (seq 1 8 15))           ; C4, C5, C6
```

Degree 1 = root, degree 2 = second scale tone, etc. Values beyond the scale length wrap
into higher octaves; values below 1 wrap into lower octaves.

Available scales: `:major` (`:ionian`), `:minor` (`:aeolian`), `:dorian`, `:phrygian`,
`:lydian`, `:mixolydian`, `:locrian`, `:pentatonic`, `:minor-pentatonic`, `:blues`.

### `chord` — stacked chord voicings

Returns a `stack` of the chord tones as Hz values — all voices sound simultaneously for
one full cycle:

```lisp
(chord :major :c4)              ; C E G
(chord :minor7 :a3)             ; A C E G
(chord :dom7 :g3)               ; G B D F

;; Layer a chord under a melody
(stack
  (slow 4 (chord :minor :a3))
  (scale :minor :a3 (fast 2 (seq 1 3 5 6 5 3))))
```

Available chords: `:major`, `:minor`, `:major7`, `:minor7`, `:dom7`, `:m7b5`
(half-diminished), `:dim`, `:dim7`, `:aug`, `:aug7`, `:maj7s11`, `:sus2`, `:sus4`.

### `transpose` — semitone shifting

Shifts all numeric (Hz) values in a pattern up or down by `n` semitones. Keyword values
(`:bd`, `:sd`, etc.) pass through unchanged:

```lisp
(transpose 12 (seq :c4 :e4 :g4))          ; up one octave → C5 E5 G5
(transpose -7 (scale :major :c5 (seq 1 2 3 4)))
(transpose 5 (chord :major :c4))           ; same voicing, up a fourth

;; drum keywords are untouched
(transpose 12 (seq :bd :sd))               ; still :bd :sd
```

### Putting it together

```lisp
(bpm 120)
(stack
  ;; chord progression: Am - F - C - G (one chord per 4 cycles)
  (slow 4 (arrange [[(chord :minor :a3) 4]
                    [(chord :major :f3) 4]
                    [(chord :major :c4) 4]
                    [(chord :major :g3) 4]]))
  ;; melody over the top
  (scale :minor :a3 (seq 1 3 5 6 5 3 1 3))
  ;; drums
  (seq :bd :_ :bd :_)
  (seq :_ :sd :_ :sd))
```

---

## Per-event parameters

Attach synthesis parameters to any pattern using `amp`, `attack`, `decay`, `release`, and
`pan`. Each parameter function accepts a scalar value or a pattern of values, and can be
applied directly or returned as a transformer for composition.

### Thread-last: `->>`

Chain multiple parameters with `->>` (thread-last). The result of each step is passed as
the **last** argument to the next form — consistent with REPuLse's existing convention of
putting the pattern last:

```lisp
(->> (seq :c4 :e4 :g4)
     (amp 0.7)
     (attack 0.02)
     (decay 0.5))
; ≡ (decay 0.5 (attack 0.02 (amp 0.7 (seq :c4 :e4 :g4))))
```

### `amp` — amplitude (0.0–1.0)

```lisp
(amp 0.8 (seq :c4 :e4 :g4))              ; all notes at 80%
(amp (seq 0.9 0.4 0.9 0.4) kick)         ; accent pattern — 1st and 3rd louder
```

### `attack` — onset time in seconds

```lisp
(attack 0.001 melody)   ; percussive / instant
(attack 0.3 pad)        ; slow swell
```

### `decay` — decay time in seconds

```lisp
(decay 0.08 (chord :major :c4))   ; short stab
(decay 2.0 (pure :c3))            ; long bass tone
```

> **Note:** `decay`, `attack`, and `release` only affect **synthesised** sounds (note
> keywords like `:c4`, `saw`, `square`, `sine`, `noise`). They are silently ignored for
> **sample-based** sounds (drum keywords like `:bd`, `:sd`, `:hh`, or any `(bank …)`
> pattern). To shorten a sample, use the `:end` key via `(->> pat (params {:end 0.5}))`
> instead.

### `release` — release time in seconds

```lisp
(release 0.5 melody)   ; tail after note-off
```

### `pan` — stereo position (-1.0 to 1.0)

```lisp
(pan -0.5 melody)                      ; slightly left
(pan (seq -0.8 0.8) (fast 2 hihat))    ; ping-pong hi-hat
```

### `synth` — apply a synthesis voice to a note pattern

Write note sequences as plain keywords, then attach a voice with `synth`. This separates
pitch from timbre — the same note pattern can be tried with different voices by changing
one word:

```lisp
(->> (seq :c4 :eb4 :g4)
     (synth :saw)
     (amp 0.6) (attack 0.02) (decay 0.5))

(->> (seq :c3 :eb3 :g3)
     (synth :square :pw 0.25)    ; 25% duty cycle — brighter, thinner
     (amp 0.4) (decay 0.3))

(->> (seq :c4 :eb4 :g4)
     (synth :fm :index 4 :ratio 2)
     (amp 0.5) (attack 0.05) (decay 1.2))

(->> (seq :_ :_ :_ :_)           ; timing/rest pattern drives noise hits
     (synth :noise)
     (amp 0.5) (decay 0.08))
```

Available voices:

| Voice        | Keyword   | Options                                                      |
|--------------|-----------|--------------------------------------------------------------|
| Sawtooth     | `:saw`    | —                                                            |
| Pulse/square | `:square` | `:pw` 0.0–1.0 — duty cycle (default `0.5` = square wave)   |
| FM synthesis | `:fm`     | `:index` modulation depth (default `1.0`), `:ratio` mod/carrier ratio (default `2.0`) |
| White noise  | `:noise`  | —                                                            |

`synth` works on any note-producing pattern: plain keywords, Hz values from `scale`, or
already-transformed maps. All `amp`/`attack`/`decay`/`pan` parameters compose normally
on top of it.

The per-note forms `(saw :c4)`, `(square :c3 :pw 0.25)`, `(fm :c4 :index 4)` remain
available when you need different voices on individual steps within the same `seq`.

### Named voice presets

One-argument forms return `(pat → pat)` transformers, enabling reusable presets with `def`
and `comp`:

```lisp
(def pluck  (comp (amp 0.8) (attack 0.003) (decay 0.15)))
(def pad    (comp (amp 0.4) (attack 0.3)   (decay 1.5)))
(def punchy (comp (amp 1.0) (attack 0.001) (decay 0.08)))

(stack
  (pluck (scale :minor :a3 (seq 1 3 5 8)))
  (pad   (chord :minor :a3))
  (punchy (seq :bd :_ :bd :_)))
```

### `rate` — sample playback rate

Scale the playback speed of a sample. `1.0` = normal, `2.0` = double speed (one octave up):

```lisp
(->> (sound :tabla 0) (rate 1.5))    ; 50% faster (higher pitch)
(->> (seq :hh :oh)    (rate 0.5))    ; half speed
```

### `begin` / `end` — sample slice

Play only a portion of the sample buffer. Values are fractions of buffer duration (0.0–1.0):

```lisp
(->> (sound :tabla 0) (begin 0.2) (end 0.8))   ; middle 60%
(->> (sound :rave  0) (begin 0.5))              ; second half
```

### `loop-sample` — loop sample

Loop the sample buffer continuously:

```lisp
(->> (sound :pad 0) (loop-sample true) (decay 4.0))
```

### Patterned parameters

Parameters can be patterns themselves — they are combined with the note pattern
cycle-aligned, so different densities create polyrhythmic parameter changes:

```lisp
; 2-step amp pattern over a 3-step melody — creates shifting accents
(amp (seq 0.9 0.4) (seq :c4 :e4 :g4))

; independent amp and attack patterns stacked with melody
(->> (scale :dorian :d3 (fast 3 (seq 1 3 5 6 7)))
     (amp (slow 2 (seq 0.8 0.5 1.0 0.3)))
     (attack 0.01))
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

### `bank` — drum machine prefix

Set a global prefix so bare keywords resolve to a named drum machine bank:

```lisp
(bank :AkaiLinn)
(seq :bd :sd :hh :sd)    ; plays AkaiLinn_bd, AkaiLinn_sd, AkaiLinn_hh, AkaiLinn_sd

; Switch machines:
(bank :RolandTR808)
(seq :bd :sd :cp)

; Clear — bare keywords use the default lookup:
(bank nil)
```

If the prefixed bank doesn't exist (e.g. a machine has no hi-hat), the keyword resolves
via the standard fallback with no error. The active bank is shown in the context panel.

### Built-in synthesis fallback

These keywords always work even if samples haven't loaded yet — they are synthesized
by the Rust/WASM engine (or the JS fallback if WASM is unavailable):

| Keyword | Synthesis |
|---|---|
| `:bd` | Sine sweep kick (150 → 40 Hz) |
| `:sd` | Bandpass-filtered noise + sine crack |
| `:hh` | Highpass-filtered noise, 45 ms (closed) |
| `:oh` | Highpass-filtered noise, 350 ms (open) |

### Loading external samples

`samples!` loads additional sample banks at runtime from a public GitHub repository,
a REPuLse Lisp manifest (`.edn`), or a Strudel-compatible JSON manifest (`.json`).
The load is asynchronous — the built-in returns immediately and samples become
available as soon as the first audio buffer finishes fetching.

**GitHub shorthand** — discovers all audio files in a public repo and groups them
by folder name. Each folder becomes one bank:

```lisp
; Load from the default branch (tries "main", falls back to "master")
(samples! "github:algorave-dave/samples")

; Then inspect what was registered:
(sample-banks)   ; => ("breaks" "drums" "pads" ...)

; Use the loaded banks just like any built-in:
(stack
  (seq :drums :_ :drums :_)
  (seq :_ :breaks :_ :breaks)
  (fast 2 (seq :pads :_)))
```

**Specific branch:**

```lisp
(samples! "github:algorave-dave/samples/main")
```

**REPuLse Lisp manifest** (`.edn`) — a map of bank names to lists of file paths:

```lisp
(samples! "https://raw.githubusercontent.com/algorave-dave/samples/main/samples.edn")
```

**Strudel-compatible JSON manifest** (`.json`):

```lisp
(samples! "https://raw.githubusercontent.com/algorave-dave/samples/main/samples.json")
```

**`sample-banks`** — returns a sorted list of every registered bank name (built-ins
plus anything loaded via `samples!`):

```lisp
(sample-banks)   ; => ("arpy" "bass" "bd" "breaks" "drums" "hh" ...)
```

> **Note:** the unauthenticated GitHub API allows 60 requests/hour per IP.
> One `(samples! "github:…")` call uses exactly one API request.

---

## Mini-notation

`~` parses a compact Tidal/Strudel-style string into a Pattern. It is opt-in sugar — the
Lisp is the host language and `~` is just another function returning a Pattern.

```lisp
(~ "bd sd hh")       ; ≡ (seq :bd :sd :hh)
(~ "bd [sd hh]")     ; subdivision — sd and hh share one step
(~ "hh*4 sd")        ; repetition — four hats then a snare
(~ "<bd sd cp>")     ; alternation — cycles through bd → sd → cp
(~ "bd? sd")         ; 50% probability — bd plays on ~half of cycles
(~ "bd:2 sd:0")      ; sample index — ≡ (sound :bd 2), (sound :sd 0)
(~ "bd@3 sd")        ; elongation — bd takes ¾ of the cycle
(~ "c4 e4 g4")       ; note names → keywords (:c4 :e4 :g4)
(~ "440 330 220")    ; numbers pass through as Hz values
```

### Mini-notation inside Lisp

Mini-notation composes freely with all Lisp combinators:

```lisp
(fast 2 (~ "bd sd"))
(stack (~ "bd _ bd _") (~ "_ sd _ sd"))
(->> (~ "c4 e4 g4") (amp 0.6) (attack 0.02))
(every 4 rev (~ "bd sd hh cp"))
```

### `alt` — cycle-based alternation

`alt` is the Lisp-level equivalent of `<...>` in mini-notation. On each cycle it picks
the next pattern from the list:

```lisp
(alt (seq :bd :bd) (seq :sd :sd))   ; even cycles: bd bd, odd cycles: sd sd
(alt kick fill fill fill)            ; kick on cycle 0, fill on 1–3, loop
```

### `load-gist` — import a GitHub Gist

Fetch a GitHub Gist into the editor and evaluate it:

```lisp
(load-gist "https://gist.github.com/user/abc123def")
(load-gist "https://gist.githubusercontent.com/user/id/raw/file.clj")
```

### `export` — render to WAV

Render `n` cycles of the current tracks to a downloadable WAV file via `OfflineAudioContext`:

```lisp
(export 4)   ; 4 cycles → WAV download
(export 1)   ; 1 cycle loop
```

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

### `tap` — tap tempo

Call `(tap)` repeatedly on the beat to derive BPM from the average tap interval.
The **tap** button in the header UI does the same thing with a mouse click:

```lisp
(tap)   ; call this expression several times in time with the beat
```

### `midi-sync` — MIDI clock input

Synchronise playback to an incoming MIDI clock signal (24 ppqn):

```lisp
(midi-sync true)    ; lock to MIDI clock from connected device
(midi-sync false)   ; return to internal BPM
```

MIDI sync overrides `(bpm ...)` while active. REPuLse listens on all available MIDI inputs.

---

## Named tracks

REPuLse supports multiple simultaneous patterns running on independent **tracks**.
Each track has a keyword name and loops independently.

> **Strudel / TidalCycles users:** a REPuLse track is the same concept as an **orbit** in
> Strudel or a `d1`/`d2` connection in TidalCycles — an independent output stream that
> loops its own pattern on every cycle. In REPuLse you give tracks meaningful keyword names
> (`:kick`, `:bass`, `:lead`) rather than numbers.

### `play` — start or replace a named track

```lisp
(track :kick  (seq :bd :_ :bd :_))
(track :snare (seq :_ :sd :_ :sd))
(track :hats  (fast 2 (seq :hh :_)))
```

Evaluating a new `play` for the same name replaces the pattern without stopping others.

### `mute` / `unmute` — silence a track

```lisp
(mute :kick)      ; kick stops at the next cycle boundary
(unmute :kick)    ; kick resumes
```

### `solo` — isolate one track

```lisp
(solo :lead)   ; all other tracks are muted; re-evaluate another track to un-solo
```

### `clear` — remove a track

```lisp
(clear :kick)   ; remove the kick track
(clear)         ; stop everything and remove all tracks
```

### `tracks` — list active tracks

```lisp
(tracks)   ; => (:kick :snare :hats)
```

### Multi-track example

```lisp
(bpm 130)
(track :kick  (seq :bd :_ :bd :_))
(track :snare (seq :_ :sd :_ :sd))
(track :hats  (fast 2 (seq :hh :_)))
(track :bass  (scale :minor :c2 (seq 1 :_ 8 :_ 1 :_ 6 :_)))
```

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

## Lisp language features

### `defsynth` — user-defined synthesis instruments

Define custom instruments from Web Audio node graphs. UGen functions build the graph;
`defsynth` registers it under a name usable with `synth`:

```lisp
(defsynth pluck [freq]
  (-> (saw freq)
      (lpf (* freq 2))
      (env-perc 0.01 0.3)))

(defsynth pad [freq]
  (-> (mix (sin freq) (sin (* freq 1.002)))
      (lpf 2000)
      (env-asr 0.3 0.8 1.0)))

(->> (scale :minor :c4 (seq 1 3 5 8))
     (synth :pluck)
     (amp 0.7))
```

Available UGens:

| UGen | Description |
|---|---|
| `(sin freq)` | Sine oscillator |
| `(saw freq)` | Sawtooth oscillator |
| `(square freq)` | Square wave |
| `(tri freq)` | Triangle wave |
| `(noise)` | White noise |
| `(lpf cutoff src)` | Lowpass filter |
| `(hpf cutoff src)` | Highpass filter |
| `(bpf freq src)` | Bandpass filter |
| `(gain level src)` | Static gain |
| `(delay-node time src)` | Delay line |
| `(mix a b)` | Mix two signals |
| `(env-perc attack decay src)` | Percussive envelope |
| `(env-asr attack sustain release src)` | Sustain + release envelope |

Synths are ephemeral — nodes are created at event time and disconnected after the envelope.

### `defmacro` — compile-time transforms

Define macros that expand at evaluate-time before execution:

```lisp
(defmacro swing [amount pat]
  `(off ~amount identity ~pat))

(swing 0.1 (seq :bd :sd))
; expands to: (off 0.1 identity (seq :bd :sd))

(defmacro build-seq [& vals]
  `(seq ~@vals))
```

Quasiquote (`` ` ``), unquote (`~`), and splice-unquote (`~@`) work as in Clojure.

### `loop` / `recur` — tail-recursive iteration

Iterate without stack overflow using `loop` and `recur`:

```lisp
(loop [notes [] i 0]
  (if (>= i 8)
    (apply seq notes)
    (recur (conj notes (+ 200 (* i 30))) (+ i 1))))
; => (seq 200 230 260 290 320 350 380 410)
```

`recur` must be in tail position inside `loop`. Bindings are re-bound with the new values
on each iteration.

### Rational number literals

Write time ratios as fractions directly in the source — cleaner than floats:

```lisp
(slow 1/4 (seq :bd :sd))    ; ≡ (slow 0.25 ...)
(fast 3/2 melody)            ; ≡ (fast 1.5 ...)
(late 1/8 (seq :hh :oh))    ; ≡ (late 0.125 ...)
```

### BPM notation

Write BPM inline with a `bpm` suffix — the reader evaluates it as `(bpm N)`:

```lisp
120bpm    ; ≡ (bpm 120)
140bpm    ; set tempo to 140 BPM inline in an expression
```

---

## Effect plugins

REPuLse has a built-in effect chain between the synthesis engine and the audio output.
Eleven effects are loaded automatically at startup, all silent/inactive by default.
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

### Per-track effects

Route a track through its own private effect chain by placing `fx` inside the `->>` pipeline:

```lisp
(track :kick
  (->> (seq :bd :_ :bd :_)
       (fx :filter 1000)))              ; kick only goes through lowpass

(track :lead
  (->> (scale :minor :c3 (seq 1 3 5 8))
       (decay 1.0)
       (fx :reverb 0.4)))              ; lead gets its own reverb

;; Multiple effects on one track
(track :bass
  (->> (seq :c2 :_ :eb2 :_)
       (fx :filter 600)
       (fx :overdrive 0.6)))

;; Named params work the same as global fx
(track :snare
  (->> (seq :_ :sd :_ :sd)
       (fx :delay :wet 0.3 :time 0.25 :feedback 0.4)))
```

To remove a per-track effect, re-evaluate the `play` form without the `(fx ...)` line.

> **Note:** `fx` must come **after** all pattern transformations (`amp`, `decay`, `pan`, etc.) in the `->>` chain — it annotates the final pattern for audio routing.

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

#### `chorus` — stereo chorus

Two LFO-modulated delay lines creating stereo width and shimmer.

| Parameter | Key | Default | Range |
|-----------|-----|---------|-------|
| Wet mix   | `wet` / positional | `0.0` | 0–1 |
| Rate      | `rate` | `1.5` Hz | 0.1–8 Hz |
| Depth     | `depth` | `0.003` s | 0–0.02 s |
| Delay     | `delay` | `0.025` s | 0.005–0.05 s |

```lisp
(fx :chorus 0.5)
(fx :chorus :wet 0.5 :rate 2)
```

#### `phaser` — phase shifter

All-pass filter chain swept by an LFO for classic swirling effect.

| Parameter | Key | Default | Range |
|-----------|-----|---------|-------|
| Wet mix   | `wet` / positional | `0.0` | 0–1 |
| Rate      | `rate` | `0.5` Hz | 0.01–8 Hz |
| Depth     | `depth` | `0.7` | 0–1 |
| Base freq | `freq` | `1000` Hz | 100–8000 Hz |

```lisp
(fx :phaser 0.6)
(fx :phaser :rate 0.8 :depth 0.9)
```

#### `tremolo` — amplitude tremolo

Amplitude modulation at low frequencies — the sound pulses rhythmically.

| Parameter | Key | Default | Range |
|-----------|-----|---------|-------|
| Depth     | `depth` / positional | `0.0` | 0–1 |
| Rate      | `rate` | `4.0` Hz | 0.1–20 Hz |
| Shape     | `shape` | `"sine"` | `"sine"` `"square"` `"sawtooth"` |

```lisp
(fx :tremolo 0.8)
(fx :tremolo :depth 0.8 :rate 6)
(fx :tremolo :shape "square")
```

#### `overdrive` — soft-clip distortion

Waveshaper saturation with a tone control.

| Parameter | Key | Default | Range |
|-----------|-----|---------|-------|
| Drive     | `drive` / positional | `0.0` | 0–1 |
| Tone      | `tone` | `20000` Hz | 500–12000 Hz |
| Wet mix   | `wet` | `0.0` | 0–1 |

```lisp
(fx :overdrive 0.7)
(fx :overdrive :drive 0.8 :tone 4000)
```

#### `bitcrusher` — lo-fi bit/sample-rate reduction

Reduces bit depth and sample rate for crunchy, glitchy textures. Uses an AudioWorklet.

| Parameter | Key | Default | Range |
|-----------|-----|---------|-------|
| Bits      | `bits` / positional | `16` | 1–16 |
| Rate      | `rate` | `1.0` | 0.01–1.0 |
| Wet mix   | `wet` | `0.0` | 0–1 |

```lisp
(fx :bitcrusher 0.8)
(fx :bitcrusher :bits 6)
(fx :bitcrusher :bits 4 :rate 0.25)
```

#### `sidechain` — pattern-aware gain ducking

Ducks the master volume every time a chosen event fires. Unlike a compressor, this uses
the scheduler's pre-known event schedule to drive Web Audio gain automation directly —
so the duck is perfectly in time regardless of BPM, with zero look-ahead latency.

| Parameter | Key | Default | Description |
|-----------|-----|---------|-------------|
| Trigger   | `trigger` | `"bd"` | Event name that causes ducking (e.g. `"bd"`, `"sd"`) |
| Amount    | `amount` / positional | `0.8` | Duck depth — 0.0 = no duck, 1.0 = full silence |
| Release   | `release` | `0.1` s | Seconds to ramp back to unity after each duck |

```lisp
;; Duck the master bus on every kick hit
(track :kick (seq :bd :_ :bd :_))
(track :pad  (slow 2 (seq :c3 :eb3 :g3)))
(fx :sidechain :trigger :bd :amount 0.8 :release 0.15)

;; Positional shorthand — sets amount
(fx :sidechain 0.6)

;; Sidechain on snare instead
(fx :sidechain :trigger :sd :amount 0.5 :release 0.2)

;; Bypass / remove
(fx :off :sidechain)
(fx :remove :sidechain)
```

> **How it works:** On each scheduled event whose name matches `trigger`, the plugin
> instantly drops the master gain to `1 - amount` and ramps it linearly back to `1.0`
> over `release` seconds. The automation is written directly onto the Web Audio gain
> parameter, so it is sample-accurate.

### Effect chain order

The default signal chain is:
```
synthesis → reverb → delay → filter → compressor → dattorro-reverb
         → chorus → phaser → tremolo → overdrive → bitcrusher → sidechain → output
```

`sidechain` sits at the end of the chain so it ducks the fully-processed signal.

Use `(fx :remove :name)` to take an effect out of the chain entirely.
Use `(fx :off :name)` / `(fx :on :name)` for transparent bypass without removing.

### Loading a custom effect

Custom effect plugins follow the same interface as the built-ins and can be loaded at runtime:

```lisp
(load-plugin "/plugins/my-effect.js")
(load-plugin "https://example.com/chorus.js")
```

See [docs/PLUGINS.md](PLUGINS.md) for the full effect plugin protocol, base classes, and worked examples.

---

## Visual plugins

A permanent `AnalyserNode` sits on the master audio bus. Visual plugins read from it and
draw to a canvas in the **plugin panel** that appears below the editor. Multiple visual
plugins stack vertically (up to 40vh total height).

### Built-in visual plugins

The **spectrum** analyser loads automatically at startup.

| Plugin | File | Auto-loaded | Description |
|--------|------|-------------|-------------|
| `spectrum` | `/plugins/spectrum.js` | ✓ | Frequency spectrum via [audiomotion-analyzer](https://audiomotion.dev) — GPU-accelerated octave-band display with peak indicators |
| `oscilloscope` | `/plugins/oscilloscope.js` | — | Time-domain waveform — classic cyan line |
| `p5-waveform` | `/plugins/p5-waveform.js` | — | p5.js waveform sketch example |

Load or reload any plugin at any time:

```lisp
(load-plugin "/plugins/spectrum.js")      ; reload spectrum
(load-plugin "/plugins/oscilloscope.js")  ; add oscilloscope
(load-plugin "/plugins/p5-waveform.js")   ; add p5 waveform sketch
```

Loading a plugin with the same name as an already-mounted one replaces it (old visual
removed, new one mounted in its place).

### p5.js sketch plugins

The `p5-base.js` module provides a `makeP5Plugin` factory that wraps any
[p5.js](https://p5js.org) sketch as a REPuLse visual plugin. p5 is loaded
once from CDN and shared across all p5 sketch plugins.

Load the built-in p5 waveform example:

```lisp
(load-plugin "/plugins/p5-waveform.js")
```

Write your own sketch plugin (save as a `.js` file and load via URL):

```javascript
import { makeP5Plugin } from "/plugins/p5-base.js";

export default makeP5Plugin("my-sketch", "1.0.0", (p, analyser, audioCtx) => {
  const buf = new Uint8Array(1024);

  p.setup = () => {
    p.createCanvas(p.windowWidth, 120);
    p.colorMode(p.HSB, 360, 100, 100, 100);
  };

  p.draw = () => {
    analyser.getByteFrequencyData(buf);
    // ... draw using p5 API ...
  };
});
```

`sketchFn` receives:
- `p` — the p5 instance (instance mode, full p5 API)
- `analyser` — the master `AnalyserNode` (use `getByteTimeDomainData` or `getByteFrequencyData`)
- `audioCtx` — the `AudioContext`

To remove a plugin and its visual entirely:

```lisp
(unload-plugin "oscilloscope")   ; removes its canvas; hides the panel if nothing is left
(unload-plugin "spectrum")
(unload-plugin "p5-waveform")
```

`(unload-plugin "unknown-name")` returns `{:error "no plugin named \"...\""}`.

For the full visual plugin protocol, the `VisualPlugin` base class, and worked examples,
see [docs/PLUGINS.md](PLUGINS.md).

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

## MIDI & External I/O

MIDI features require **Chrome or Edge** (Web MIDI API). Other browsers return a clear
error message. Connect MIDI devices via USB — the browser sees them through the OS MIDI
subsystem automatically.

### MIDI controller input

Map incoming CC messages from any MIDI controller to live parameters:

```lisp
(midi-map :cc 1 :filter)    ; CC #1 → master lowpass filter cutoff
(midi-map :cc 7 :amp)       ; CC #7 → master gain
(midi-map :cc 10 :bpm)      ; CC #10 → tempo (range 60–240)
```

Multiple mappings work simultaneously. Turning a mapped knob changes the parameter in
real time while patterns play.

### MIDI note output

Route pattern events as MIDI Note On/Off to external synths or DAWs:

```lisp
(track :bass (midi-out 1 (seq :c4 :e4 :g4)))         ; channel 1
(->> (scale :minor :c3 (seq 1 3 5 8)) (midi-out 2))  ; channel 2

; Chains with other params — amp maps to MIDI velocity
(->> (seq :c4 :e4 :g4) (midi-out 1) (amp 0.7))
```

Note timing aligns with audio playback. Note Off is sent at event end.

### MIDI clock output

Broadcast 24ppqn MIDI clock so external hardware/DAWs lock to REPuLse tempo:

```lisp
(midi-clock-out! true)   ; sends Start (0xFA) + 24ppqn clock pulses
(bpm 140)                ; clock rate updates automatically
(midi-clock-out! false)  ; sends Stop (0xFC) and halts clock
```

### MIDI file export

Export a track as a standard MIDI file (Type 0, 480 ticks/quarter note):

```lisp
(track :lead (seq :c4 :e4 :g4 :b4))
(midi-export :lead 4)    ; downloads repulse-lead.mid (4 cycles)
(midi-export :lead 8)    ; 8 cycles
```

The `.mid` file opens in any DAW (Logic, Ableton, Reaper). Tempo is embedded in the file.

### MIDI sync input

Lock REPuLse tempo to an external MIDI clock source:

```lisp
(midi-sync! true)    ; listen for 24ppqn clock from external device
(midi-sync! false)   ; stop syncing, return to manual BPM
```

### Freesound

Search and load samples from [freesound.org](https://freesound.org). Requires a free
API key (get one at https://freesound.org/apiv2/apply).

```lisp
; Step 1 — set your API key (once per session)
(freesound-key! "your-api-key")

; Step 2 — search (loads up to 5 results)
(freesound! "kick 808")
; Output panel shows: "loaded 5 sounds: :freesound-521234, :freesound-789012, ..."

; Step 3 — use the actual IDs from the output
(pure :freesound-521234)
(seq :freesound-521234 :freesound-789012)
```

The keywords are based on Freesound's database IDs (not the search terms). Check the
output panel after `freesound!` completes to see which keywords were registered.

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

### Mini-notation

```lisp
;; Mini-notation is a compact string syntax for patterns
(~ "bd sd hh")               ; ≡ (seq :bd :sd :hh)
(~ "bd [sd hh]")             ; subdivision: sd and hh share one slot
(~ "hh*4 sd")                ; repetition: four hats then a snare
(~ "<bd sd cp>")             ; alternation: cycles through bd → sd → cp
(~ "bd? sd")                 ; bd plays with ~50% probability
(~ "bd:2 sd:0")              ; sample index: (sound :bd 2), (sound :sd 0)
(~ "bd@3 sd")                ; elongation: bd takes ¾ of the cycle
(~ "c4 e4 g4")               ; note names → keywords (:c4 :e4 :g4)
(~ "440 330 220")            ; numbers pass through as Hz values

;; Mini-notation composes with all Lisp functions:
(fast 2 (~ "bd sd"))
(stack (~ "bd _ bd _") (~ "_ sd _ sd"))
(->> (~ "c4 e4 g4") (amp 0.6) (attack 0.02))
(every 4 rev (~ "bd sd hh cp"))

;; alt — cycle-based alternation at the Lisp level
(alt (seq :bd :bd) (seq :sd :sd))   ; even cycles: bd bd, odd: sd sd
```

### Gist import

```lisp
;; Load a GitHub Gist into the editor and auto-evaluate it
(load-gist "https://gist.github.com/user/abc123def")

;; Raw Gist URL also works
(load-gist "https://gist.githubusercontent.com/user/id/raw/file.clj")
```

### WAV export

```lisp
;; Render 4 cycles of the current tracks to a WAV file and download it
(export 4)

;; Render 1 cycle (useful for short loops)
(export 1)
```

### Per-track effects

```lisp
(bpm 128)

;; Kick: tight lowpass, no reverb
(track :kick
  (->> (seq :bd :_ :bd :_)
       (fx :filter 800)))

;; Lead: its own reverb + delay, independent of the kick
(track :lead
  (->> (scale :minor :c3 (seq 1 3 5 8))
       (amp 0.6)
       (decay 0.8)
       (fx :reverb 0.5)
       (fx :delay :wet 0.3 :time 0.25)))

;; Global master bus compressor still applies to the whole mix
(fx :compressor 0.8)
```

### Stopping

```lisp
(stop)
```
