# Phase G — Music Theory: Notes, Scales & Chords

## Goal

Add a music theory layer to REPuLse so that melodies and harmonies are as natural to write
as rhythms. Three new capabilities:

1. **Note keywords** — `:c4`, `:eb3`, `:fs5` are resolved to Hz frequencies directly in the
   audio dispatcher, making them first-class tone values alongside raw numbers.
2. **`(scale kw root pat)`** — maps degree integers in a pattern to Hz frequencies using
   a named scale, enabling melodic sequences without hand-computing frequencies.
3. **`(chord kw root)`** — returns a stacked pattern of the chord's tones as Hz values.
4. **`(transpose n pat)`** — shifts all Hz values in a pattern by `n` semitones.

```lisp
;; Before this phase — raw frequencies only:
(seq 261.63 293.66 329.63 349.23)

;; After — note keywords:
(seq :c4 :d4 :e4 :f4)

;; After — scale degrees (0 = root, 1 = second, …):
(scale :minor :c4 (seq 0 2 4 7))

;; After — chord:
(stack
  (chord :minor :c4)
  (slow 2 (seq :bd :sd :bd :sd)))

;; After — transpose:
(transpose 7 (scale :major :c4 (seq 0 1 2 3)))   ; same pattern, up a fifth
```

Once note keywords resolve to Hz, **all existing pattern functions work melodically with
no further changes**: `fast`, `slow`, `rev`, `every`, `fmap`, `arrange`, `stack` — the
entire pattern algebra applies to pitch immediately.

---

## Background: current audio dispatch

`play-event` in `app/src/repulse/audio.cljs` dispatches on value type:

```
:_             → silence
map {:bank …}  → sample lookup
keyword        → bank-prefix resolution → sample registry → WASM synth → JS fallback
number         → WASM synth as Hz string
```

Note keywords like `:c4` currently fall into the keyword branch and look for a sample
bank named `"c4"` — which doesn't exist, so they silently fall through to the WASM synth,
which does nothing useful with the name `"c4"`. They produce no sound today.

---

## Implementation

### 1. New file: `packages/core/src/repulse/theory.cljs`

Create a new namespace in `packages/core`. It requires `repulse.core` for `fmap`,
`pure`, and `stack*`.

```clojure
(ns repulse.theory
  (:require [repulse.core :as core]))

;;; ── Note name parsing ────────────────────────────────────────────────

;; Note letter → semitone offset within octave (chromatic, used only for
;; parsing note names like :c4, :eb3 — not a scale numbering system).
(def ^:private note-semitones
  {"c" 0 "d" 2 "e" 4 "f" 5 "g" 7 "a" 9 "b" 11})

(defn note-keyword?
  "True if kw looks like a note name: a letter a–g, optional accidental (s=sharp, b=flat),
   and an octave number. Examples: :c4, :eb3, :fs5, :bb4, :cs-1."
  [kw]
  (boolean (re-matches #"[a-g][sb]?-?\d+" (name kw))))

(defn note->midi
  "Convert a note keyword to a MIDI note number.
   Convention: C4 = 60, A4 = 69.
   Accidentals: s = sharp (+1), b = flat (−1).
   Examples: :c4 → 60, :a4 → 69, :eb3 → 51, :fs5 → 78, :bb4 → 70."
  [kw]
  (let [[_ letter acc oct-str] (re-matches #"([a-g])([sb])?(-?\d+)" (name kw))
        semitone   (get note-semitones letter 0)
        accidental (case acc "s" 1 "b" -1 0)
        octave     (js/parseInt oct-str 10)]
    (+ semitone accidental (* (+ octave 1) 12))))

(defn midi->hz
  "Convert a MIDI note number to a frequency in Hz.
   Uses equal temperament with A4 (MIDI 69) = 440 Hz."
  [midi]
  (* 440.0 (js/Math.pow 2 (/ (- midi 69) 12))))

(defn note->hz
  "Convert a note keyword directly to Hz. (:c4 → 261.63, :a4 → 440.0)"
  [kw]
  (midi->hz (note->midi kw)))

;;; ── Interval arithmetic ──────────────────────────────────────────────

;; Scales and chords are defined using standard music-theory interval names
;; rather than raw semitone offsets, so that definitions read as musicians
;; write them. A plain integer n means the nth diatonic degree (no alteration).
;; Keyword :b3 means "flat 3", :s4 means "sharp 4", :bb7 means "double flat 7".
;;
;; Interval name → semitone offset from root:
(def ^:private interval->semitones
  {1    0    ; unison / root
   :b2  1    ; minor second
   2    2    ; major second
   :b3  3    ; minor third
   3    4    ; major third
   4    5    ; perfect fourth
   :s4  6    ; augmented fourth (tritone)
   :b5  6    ; diminished fifth  (enharmonic with :s4)
   5    7    ; perfect fifth
   :s5  8    ; augmented fifth
   :b6  8    ; minor sixth       (enharmonic with :s5)
   6    9    ; major sixth
   :bb7 9    ; diminished seventh (double flat — enharmonic with 6)
   :b7  10   ; minor seventh
   7    11   ; major seventh
   8    12}) ; octave

(defn- resolve-intervals
  "Convert a vector of interval names to semitone offsets."
  [ivs]
  (mapv #(get interval->semitones % 0) ivs))

;;; ── Scale tables ─────────────────────────────────────────────────────

;; Defined in interval notation — each entry reads exactly as a musician
;; would describe the scale formula.
(def ^:private scale-intervals
  {:major            [1 2  3   4  5  6   7  ]
   :ionian           [1 2  3   4  5  6   7  ]   ; alias
   :minor            [1 2  :b3 4  5  :b6 :b7]
   :aeolian          [1 2  :b3 4  5  :b6 :b7]   ; alias
   :dorian           [1 2  :b3 4  5  6   :b7]
   :phrygian         [1 :b2 :b3 4 5  :b6 :b7]
   :lydian           [1 2  3   :s4 5 6   7  ]
   :mixolydian       [1 2  3   4  5  6   :b7]
   :locrian          [1 :b2 :b3 4 :b5 :b6 :b7]
   :pentatonic       [1 2  3   5  6         ]
   :minor-pentatonic [1 :b3 4  5  :b7       ]
   :blues            [1 :b3 4  :s4 5 :b7    ]})

;;; ── Chord tables ─────────────────────────────────────────────────────

;; Interval notation makes chord formulas self-documenting. :m7b5 reads as
;; "minor 7, flat 5" — no lookup required to understand the voicing.
(def ^:private chord-intervals
  {:major    [1 3   5          ]   ; major triad
   :minor    [1 :b3 5          ]   ; minor triad
   :major7   [1 3   5   7      ]   ; major 7th
   :minor7   [1 :b3 5   :b7    ]   ; minor 7th
   :dom7     [1 3   5   :b7    ]   ; dominant 7th
   :m7b5     [1 :b3 :b5 :b7    ]   ; half-diminished (minor 7 flat 5)
   :dim      [1 :b3 :b5        ]   ; diminished triad
   :dim7     [1 :b3 :b5 :bb7   ]   ; fully diminished 7th
   :aug      [1 3   :s5        ]   ; augmented triad
   :aug7     [1 3   :s5 :b7    ]   ; augmented dominant 7th
   :maj7s11  [1 3   :s4 5  7   ]   ; major 7 sharp 11 (Lydian chord)
   :sus2     [1 2   5          ]   ; suspended 2nd
   :sus4     [1 4   5          ]}) ; suspended 4th

;;; ── Pattern combinators ──────────────────────────────────────────────

(defn scale
  "Map scale degree integers in pat to Hz frequencies.
   Degrees are zero-indexed from the root; values outside [0, n) wrap
   into higher/lower octaves (e.g. degree 7 in a 7-note scale = root + 1 octave).

   (scale :minor :c4 (seq 0 2 4 7))
   (scale :pentatonic :g3 (fast 2 (seq 0 1 2 3 4)))"
  [scale-kw root pat]
  (let [root-midi  (note->midi root)
        semitones  (resolve-intervals (get scale-intervals scale-kw [1 2 3 4 5 6 7]))
        n          (count semitones)
        degree->hz (fn [degree]
                     (let [degree (int degree)
                           oct    (int (js/Math.floor (/ degree n)))
                           idx    (- degree (* oct n))]
                       (midi->hz (+ root-midi (* oct 12) (nth semitones idx)))))]
    (core/fmap degree->hz pat)))

(defn chord
  "Return a stacked pattern of the chord tones as Hz values.
   Each tone is a (pure hz) pattern lasting one full cycle.

   (chord :major :c4)    ; C E G stacked
   (chord :m7b5 :b3)     ; half-diminished on B3"
  [chord-kw root]
  (let [root-midi (note->midi root)
        semitones (resolve-intervals (get chord-intervals chord-kw [1 3 5]))
        freqs     (map #(midi->hz (+ root-midi %)) semitones)]
    (core/stack* (mapv core/pure freqs))))

(defn transpose
  "Shift all numeric (Hz) values in pat up or down by n semitones.
   Keyword values (drum sounds, rests) are passed through unchanged.

   (transpose 12 (seq :c4 :e4 :g4))   ; up one octave
   (transpose -7 (scale :major :c5 (seq 0 1 2 3)))"
  [semitones pat]
  (let [ratio (js/Math.pow 2 (/ semitones 12))]
    (core/fmap (fn [v] (if (number? v) (* v ratio) v)) pat)))
```

---

### 2. New file: `packages/core/test/repulse/theory_test.cljs`

```clojure
(ns repulse.theory-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [repulse.theory :as theory]
            [repulse.core :as core]))

(deftest note-keyword-predicate
  (is (theory/note-keyword? :c4))
  (is (theory/note-keyword? :eb3))
  (is (theory/note-keyword? :fs5))
  (is (theory/note-keyword? :bb4))
  (is (theory/note-keyword? :cs-1))
  (is (not (theory/note-keyword? :bd)))
  (is (not (theory/note-keyword? :sd)))
  (is (not (theory/note-keyword? :_)))
  (is (not (theory/note-keyword? :hh))))

(deftest note->midi-values
  (is (= 60 (theory/note->midi :c4)))   ; middle C
  (is (= 69 (theory/note->midi :a4)))   ; concert A
  (is (= 61 (theory/note->midi :cs4)))  ; C-sharp 4
  (is (= 63 (theory/note->midi :eb4)))  ; E-flat 4
  (is (= 70 (theory/note->midi :bb4)))  ; B-flat 4
  (is (= 48 (theory/note->midi :c3)))
  (is (= 72 (theory/note->midi :c5))))

(deftest midi->hz-values
  (is (= 440.0 (theory/midi->hz 69)))
  ;; C4 ≈ 261.63 — check within 0.01 Hz
  (is (< (abs (- 261.626 (theory/midi->hz 60))) 0.01)))

(deftest note->hz-roundtrip
  (is (= 440.0 (theory/note->hz :a4)))
  (is (< (abs (- 261.626 (theory/note->hz :c4))) 0.01)))

(deftest scale-degrees
  (let [events (core/query
                 (theory/scale :major :c4 (core/seq* [0 2 4]))
                 {:start 0N :end 1N})
        freqs (map :value events)]
    ;; C4 E4 G4: MIDI 60 64 67
    (is (= 3 (count freqs)))
    (is (< (abs (- (theory/midi->hz 60) (first  freqs))) 0.01))
    (is (< (abs (- (theory/midi->hz 64) (second freqs))) 0.01))
    (is (< (abs (- (theory/midi->hz 67) (nth    freqs 2))) 0.01))))

(deftest scale-octave-wrapping
  ;; Degree 7 in a 7-note scale = root + 1 octave
  (let [events (core/query
                 (theory/scale :major :c4 (core/seq* [0 7]))
                 {:start 0N :end 1N})
        freqs (map :value events)]
    (is (< (abs (- (* 2 (theory/midi->hz 60)) (second freqs))) 0.01))))

(deftest chord-stack
  (let [events (core/query
                 (theory/chord :major :c4)
                 {:start 0N :end 1N})
        freqs (set (map :value events))]
    ;; C major = C4 E4 G4
    (is (= 3 (count freqs)))
    (is (some #(< (abs (- (theory/midi->hz 60) %)) 0.01) freqs))  ; C4
    (is (some #(< (abs (- (theory/midi->hz 64) %)) 0.01) freqs))  ; E4
    (is (some #(< (abs (- (theory/midi->hz 67) %)) 0.01) freqs)))) ; G4

(deftest transpose-shifts-hz
  (let [pat    (core/pure (theory/note->hz :c4))
        up     (theory/transpose 12 pat)
        events (core/query up {:start 0N :end 1N})
        freq   (:value (first events))]
    ;; Up one octave = double the frequency
    (is (< (abs (- (* 2 (theory/note->hz :c4)) freq)) 0.01))))

(deftest transpose-passes-keywords
  (let [pat    (core/pure :bd)
        result (theory/transpose 5 pat)
        events (core/query result {:start 0N :end 1N})]
    (is (= :bd (:value (first events))))))
```

---

### 3. `packages/lisp/src/repulse/lisp/eval.cljs` — add theory bindings

Add `[repulse.theory :as theory]` to the namespace require. Then add three entries to
`make-env`, in the existing pattern-combinator section:

```clojure
"scale"     (fn [kw root pat]
               (theory/scale (unwrap kw) (unwrap root) (unwrap pat)))
"chord"     (fn [kw root]
               (theory/chord (unwrap kw) (unwrap root)))
"transpose" (fn [n pat]
               (theory/transpose (unwrap n) (unwrap pat)))
```

Place them after `"fmap"` — they are all pure pattern combinators with the same character.

---

### 4. `app/src/repulse/audio.cljs` — note keywords in `play-event`

Add `[repulse.theory :as theory]` to the namespace require. In `play-event`, add a
note-keyword check **before** the existing bank-prefix resolution. Note keywords always
take priority over sample lookup (a sample bank named `"c4"` would be an extreme edge
case):

```clojure
;; Keyword — note name (c4, eb3, …) → Hz, or bank prefix → sample → synth fallback
(keyword? value)
(if (theory/note-keyword? value)
  (let [hz (theory/note->hz value)]
    (or (worklet-trigger! (str hz) t)
        (make-sine ac t hz)))
  (let [resolved (samples/resolve-keyword value)]
    (cond
      (samples/has-bank? resolved) (samples/play! ac t resolved 0)
      :else (or (worklet-trigger! (name value) t)
                (js-synth ac t value)))))
```

No changes to any other branch in `play-event`.

---

### 5. `app/src/repulse/lisp-lang/completions.js` — add theory entries

Add to `BUILTINS`, in the Transformations section after `fmap`:

```javascript
// --- Music theory ---
{ label: "scale",     type: "function", detail: "(scale kw root pat) — map degree integers to Hz (e.g. (scale :minor :c4 (seq 0 2 4)))" },
{ label: "chord",     type: "function", detail: "(chord kw root) — stack chord tones as Hz values (e.g. (chord :major7 :c4))" },
{ label: "transpose", type: "function", detail: "(transpose n pat) — shift Hz values by n semitones" },
```

---

### 6. `app/src/repulse/lisp-lang/repulse-lisp.grammar` — add to BuiltinName

```
"scale" | "chord" | "transpose" |
```

Add these to the existing `BuiltinName` token alternatives so they get syntax highlighting.

---

## Interval notation

Scales and chords are defined internally using standard music-theory interval names
rather than raw semitone offsets. This makes definitions self-documenting and alteration
immediately obvious:

| Symbol | Semitones | Name |
|---|---|---|
| `1` | 0 | root / unison |
| `:b2` | 1 | minor second |
| `2` | 2 | major second |
| `:b3` | 3 | minor third |
| `3` | 4 | major third |
| `4` | 5 | perfect fourth |
| `:s4` / `:b5` | 6 | augmented fourth / diminished fifth (tritone) |
| `5` | 7 | perfect fifth |
| `:s5` / `:b6` | 8 | augmented fifth / minor sixth |
| `6` | 9 | major sixth |
| `:bb7` | 9 | diminished seventh (double flat) |
| `:b7` | 10 | minor seventh |
| `7` | 11 | major seventh |

## Supported scales

| Keyword | Formula | Name |
|---|---|---|
| `:major` / `:ionian` | 1 2 3 4 5 6 7 | Major |
| `:minor` / `:aeolian` | 1 2 b3 4 5 b6 b7 | Natural minor |
| `:dorian` | 1 2 b3 4 5 6 b7 | Dorian |
| `:phrygian` | 1 b2 b3 4 5 b6 b7 | Phrygian |
| `:lydian` | 1 2 3 #4 5 6 7 | Lydian |
| `:mixolydian` | 1 2 3 4 5 6 b7 | Mixolydian |
| `:locrian` | 1 b2 b3 4 b5 b6 b7 | Locrian |
| `:pentatonic` | 1 2 3 5 6 | Major pentatonic |
| `:minor-pentatonic` | 1 b3 4 5 b7 | Minor pentatonic |
| `:blues` | 1 b3 4 #4 5 b7 | Blues |

## Supported chords

| Keyword | Formula | Name |
|---|---|---|
| `:major` | 1 3 5 | Major triad |
| `:minor` | 1 b3 5 | Minor triad |
| `:major7` | 1 3 5 7 | Major 7th |
| `:minor7` | 1 b3 5 b7 | Minor 7th |
| `:dom7` | 1 3 5 b7 | Dominant 7th |
| `:m7b5` | 1 b3 b5 b7 | Half-diminished (minor 7 flat 5) |
| `:dim` | 1 b3 b5 | Diminished triad |
| `:dim7` | 1 b3 b5 bb7 | Fully diminished 7th |
| `:aug` | 1 3 #5 | Augmented triad |
| `:aug7` | 1 3 #5 b7 | Augmented dominant 7th |
| `:maj7s11` | 1 3 #4 5 7 | Major 7 sharp 11 (Lydian chord) |
| `:sus2` | 1 2 5 | Suspended 2nd |
| `:sus4` | 1 4 5 | Suspended 4th |

## Note keyword convention

| Notation | Meaning | MIDI | Hz |
|---|---|---|---|
| `:c4` | C, octave 4 (middle C) | 60 | 261.63 |
| `:a4` | A, octave 4 (concert A) | 69 | 440.00 |
| `:eb3` | E-flat, octave 3 | 51 | 155.56 |
| `:cs5` | C-sharp, octave 5 | 73 | 554.37 |
| `:bb4` | B-flat, octave 4 | 70 | 466.16 |

Sharps use `s` suffix (`:cs4`), flats use `b` suffix (`:eb4`). The `#` character is not
valid in CLJS keywords.

---

## Files to change

| File | Change |
|---|---|
| `packages/core/src/repulse/theory.cljs` | **New** — note math + scale/chord/transpose |
| `packages/core/test/repulse/theory_test.cljs` | **New** — unit tests |
| `packages/lisp/src/repulse/lisp/eval.cljs` | Require theory; add `scale`, `chord`, `transpose` to `make-env` |
| `app/src/repulse/audio.cljs` | Require theory; note-keyword branch in `play-event` |
| `app/src/repulse/lisp-lang/completions.js` | Add three completion entries |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `scale`, `chord`, `transpose` to BuiltinName |
| `docs/USAGE.md` | New "Music theory" section |
| `README.md` | Add `scale`, `chord`, `transpose` rows to language reference |
| `CLAUDE.md` | Mark Phase G as ✓ delivered when done |

No changes to `packages/audio` (Rust/WASM) or any CSS files.

---

## Definition of done

- [ ] `:c4`, `:a4`, `:eb3`, `:fs5`, `:bb4` play the correct pitches as sine tones
- [ ] Note keywords coexist cleanly: `:bd` still triggers the kick drum, `:c4` plays a tone
- [ ] `(scale :major :c4 (seq 0 1 2 3 4 5 6))` plays a C major scale
- [ ] `(scale :minor :a3 (seq 0 2 4))` plays A C# E (A minor triad via scale degrees)
- [ ] Degree wrapping: degree 7 in a 7-note scale plays the root one octave up
- [ ] `(chord :major :c4)` produces simultaneous C4 E4 G4
- [ ] `(chord :minor7 :a3)` produces simultaneous A3 C4 E4 G4
- [ ] `(chord :m7b5 :b3)` produces B3 D4 F4 A4 (half-diminished)
- [ ] `(chord :dim7 :c4)` produces C4 Eb4 Gb4 A4 (fully diminished)
- [ ] `(transpose 12 (seq :c4 :e4 :g4))` plays C5 E5 G5
- [ ] `(transpose 7 (scale :major :c4 (seq 0 1 2)))` transposes the result up a fifth
- [ ] `(transpose 5 (seq :bd :sd))` passes drum keywords through unchanged
- [ ] All combinations with existing combinators work: `fast`, `slow`, `rev`, `every`, `stack`, `arrange`
- [ ] Code completion suggests `scale`, `chord`, `transpose` with correct detail strings
- [ ] Syntax highlighting colours `scale`, `chord`, `transpose` as built-in names
- [ ] All new unit tests pass (`npm run test:core`)
- [ ] Existing core tests still pass
