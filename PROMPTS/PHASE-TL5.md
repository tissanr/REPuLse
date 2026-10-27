# Phase TL5 — Layering, Pitch & Harmony

> **Spec status:** full (expanded 2026-10-27 from the outline; function list from the TL5
> rows of [docs/STRUDEL-PARITY.md](../docs/STRUDEL-PARITY.md) plus the TL1 `layer` row and
> the Tidal-only `stutter`, `rolled`/`rolledBy` and `toScale` rows)
>
> **Automation:** 🤖 auto — layering and harmony produce exact event/value lists
> (note numbers, chord tones) testable in `cljs.test`.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL1 (pattern arithmetic for transposition); TL3 for `echo` timing helpers

## Goal

Layer variations of a pattern on itself, and work with harmony musically: arpeggios,
chord symbols, voicings and scale-aware transposition. Layering adds `superimpose`,
`layer`, `echo`, `echo-with`, `stut`, `stutter`, `jux-flip`, `jux-flip-by` and `xfade`.
Harmony adds a chord-symbol parser (`"Cm7"`, `"F#7b9"`, `"C^7"`, `"C/E"`), `arp` (Tidal
modes and Strudel index lists), `arp-with`, `rolled`/`rolled-by`, `voicing` with a small
MIT-licensed voicing dictionary and anchor-based voice leading, `root-notes`,
`scale-transpose`, interval strings and patterned amounts for `transpose`, and 11 more
scales. **Breaking change:** `scale` becomes 0-based and, like `chord` and `transpose`,
returns **note keywords** (MIDI-note values) instead of Hz, so `(add 12 (scale …))` is an
octave. 16 new built-ins, 3 changed ones.

```lisp
;; Before
(chord :minor7 :c4)                              ; Hz values 261.6 311.1 392 466.2
(scale :minor :c4 (seq 1 3 5 8))                 ; 1-based degrees → Hz
(off 1/8 (fast 2) pat)

;; After
(superimpose (comp (fast 2) (add 12)) (seq :c3 :e3 :g3))
(echo 3 1/8 0.6 (seq :cp))                       ; 3 repeats, 1/8 apart, amps 1 0.6 0.36
(arp :up-down (chord "Cm7"))                     ; chord symbols as strings
(voicing (seq "C^7" "A7b13" "Dm7" "G7"))         ; smooth voice leading
(scale-transpose "<0 2>" :minor (seq 0 2 4))     ; transpose in scale steps
(scale :minor :c4 (seq 0 2 4 7))                 ; 0-based → :c4 :ds4 :g4 :c5 (MIDI 60 63 67 72)
(add 12 (scale :minor :c3 (seq 0)))              ; :c4 — an octave, not +12 Hz
```

## Decisions (resolving the outline's open questions)

| Question | Decision |
|---|---|
| Chord-symbol input | **Strings** (outline recommendation): `(chord "Cm7")`, `(voicing "<C^7 Dm7>")`, `(seq "Dm7" "G7")`. Lisp keywords can't hold `^` or `#`. A string argument goes through mini-notation (`u/pat-arg`), whose atoms become keywords (`"Cm7"` → `:Cm7`, `"F#7b9"` → `:F#7b9`); a string inside `seq` stays a string value. The parser accepts **both** (text = `(subs (str v) 1)` for keywords — **not** `(name v)`, because `(keyword "C/E")` splits at `/` into namespace `C` and name `E`). Roots must be upper-case `A`–`G` (lower-case `c4` is a note keyword). |
| Scale degree base | **Decided 2026-10-26, kept:** `scale` is **0-based** (degree 0 = root, negatives go below) and returns notes. Breaking; migration list in step 9. |
| Representation of "notes" | **Note keywords** (`:c4`, `:ds4`, sharps spelling via the existing `theory/midi->note`). A plain number already means **Hz** in every audio path (`audio.cljs` `play-event`: `(number? note)` → `make-sine … note`), so bare MIDI integers would need a second breaking change across all paths. Note keywords are MIDI-note values that every path already converts: `play-event` (WASM `worklet-trigger!`/`worklet-trigger-v2!` and the JS `make-sine`/offline fallbacks), the synth voices (`synth.cljs` computes `:freq` via `theory/note->hz`), MIDI out (`audio.cljs` ~line 649, `note->hz` → `midi/hz->midi`) and `midi-export` (`env/builtins/midi.cljs` ~line 79). TL1's `add`/`sub` already do keyword arithmetic through MIDI numbers (`arith.cljs` `numeric-op`), so `(add 12 (scale :minor :c3 (seq 0)))` → `:c4` with no extra work. No audio code changes. |
| Non-integer degrees | `Math/floor` before lookup (`1.7` → degree 1, `-0.5` → degree −1). Results are therefore always integer MIDI numbers → keywords. |
| `chord` / `transpose` on Hz | `chord` returns keywords (both arities). `transpose` on a **note keyword** returns a note keyword (`midi->note` of `note->midi + n`; a non-integer `n` yields Hz, as `midi->note` does for non-integers). `transpose` on a **number** keeps today's Hz behaviour (`(* v 2^(n/12))`) — numbers are frequencies. |
| `transpose` interval strings & patterned amount | Patterned amount via `patternify` (`(transpose "<0 7>" pat)`). Interval strings `"3M" "5P" "-2M" "4A" "5d" "8P" "9m" …` (Strudel/tonal notation, number then quality `P M m A d`). Because mini-notation would read `"3M"` as `3` (`parseFloat`), the Lisp builtin rewrites each interval token in the string to its semitone count, **padded with spaces to the same width** (`"<3M 5P>"` → `"<4  7 >"`), before `u/pat-arg`, so source offsets stay exact. Invalid quality for the number (`"3P"`, `"5M"`) → eval error. |
| `scale` arguments | `(scale name root pat)` with a curried `(scale name root)`. `name` may be a keyword, a **vector of semitone offsets** (covers Tidal `toScale`, parity row 🔁 — no separate `to-scale` name), or a pattern/string of names (`"<major minor>"`); `root` a note keyword or pattern. Both patterned via `core/patternify2`. Unknown scale name → eval error (literal) / major (patterned, as today's `get … [1 2 3 4 5 6 7]` default). Non-numeric values (`:_`, drum keywords) pass through; maps with a numeric `:note` get it mapped. |
| More scales/modes | Add 11: `:harmonic-minor :melodic-minor :whole-tone :chromatic :major-pentatonic` (alias of `:pentatonic`) `:hirajoshi :phrygian-dominant :lydian-dominant :altered :diminished :bebop-dominant` (table in step 5). |
| `scale-transpose` | `(scale-transpose n scale pat)`, curried `(scale-transpose n scale)`. Numeric values are **degrees** → `degree + n` (the DoD case). Note-keyword values are located in `scale` (root pitch class **C**, or give `scale` as `[name root]`, e.g. `[:dorian :d]`): a note not in the scale snaps **down** to the nearest lower scale tone, then moves `n` scale steps. `n` patterned (inner join). Rests/drums pass through. |
| Echo semantics | `echo` = Strudel `echo(times, time, feedback)`: layer `i ∈ 0…times−1` is `(late (* i time) pat)` with `:amp` **multiplied** by `feedback^i` (`Math/pow`), so existing amps are scaled (`(echo 3 1/8 0.5 (amp 0.8 p))` → 0.8 0.4 0.2). Strudel *sets* `gain`; REPuLse multiplies because `:amp` is the only level control (flagged for review). All three args patterned (`patternify3`); `times` truncated, `times < 1` → silence. |
| `echo-with` | **Tidal `stutWith`** (the outline names it): `(echo-with n t f pat)` = `pat` if `n <= 1`, else `(stack pat (f (late t (echo-with (dec n) t f pat))))` — layer `i` is `(f ∘ late t)` applied `i` times; the original is untouched and `f` takes **one** argument. Strudel's `echoWith` instead calls `func(pat.late(t·i), i)` on every layer including `i = 0`; that difference is recorded in the parity note. `n`, `t` patterned. |
| `stut` alias policy | Per the parity doc row (`stut` → `stut` 🔁, "Strudel `stut` = `echo` with Tidal argument order"): add **`stut`** as a thin alias, `(stut times feedback time pat)` = `(echo times time feedback pat)`. **No** `stut-with` / `stutWith` name (parity row 🔁 → `echo-with`). |
| `stutter` | Tidal `stutter n t p = echoWith n t id p` → `(stutter n t pat)`, no amp change. |
| `superimpose` / `layer` | Strudel: `(superimpose f … pat)` = `(stack pat (f pat) …)`; `(layer f … pat)` = `(stack (f pat) …)` without the original. Both **variadic** in functions, pattern last; if the last argument is a function the call is curried (`(superimpose (fast 2))` → transformer). `layer` moves from the TL1 parity row (📋 TL1, never delivered) into TL5. |
| `jux-flip` / `jux-flip-by` | Strudel `juxFlipBy(by, f)` = `juxBy(slowcat(by, −by), f)`: the original/transformed sides swap every cycle. `jux-flip` = `jux-flip-by 1`. Implemented in `params.cljs` on top of `jux-by` (already patterned). |
| `xfade` | Strudel `xfade(a, pos, b)`: `a` scaled by `fadeGain(pos)`, `b` by `fadeGain(1 − pos)`, `fadeGain(p) = p < 0.5 ? 1 : 2(1 − p)`; stacked. `pos` sampled per event of each side (`app-left`, structure from `a`/`b`); `:amp` multiplied as in `echo`. Events with amp 0 still exist (Strudel behaviour). |
| `arp` semantics | **Tidal modes** for keyword/mode values (outline): group events with equal `:whole`, reorder the group with the mode function, spread the result evenly over the whole (Tidal `arpWith` + `spreadOut`); patterned mode = inner join (Tidal `tParam`). **Strudel indices** for numeric values: `(arp "0 2 1 3" chord)` picks the `i mod n`-th simultaneous note, structure from the index pattern. One builtin covers both: a number, rational or all-numeric string selects the index form (`core/arp-index`), anything else the mode form (`core/arp`). Group order = event order from the query (`stack*` order); `chord`, `voicing` emit tones ascending, so `:up` is low→high. Groups of 1 event pass through unchanged for every mode (Tidal returns nothing for `up-down` on one note — deliberate, documented deviation). |
| Arp mode names | Kebab-case Tidal names, plus the Tidal spellings as aliases so `"<updown thumbup>"` from tutorials works: `:up :down :up-down`/`:updown` `:down-up`/`:downup` `:up-and-down` (`up&down`) `:down-and-up` (`down&up`) `:converge :diverge :disconverge :pinky-up`/`:pinkyup` `:pinky-up-down`/`:pinkyupdown` `:thumb-up`/`:thumbup` `:thumb-up-down`/`:thumbupdown`. Unknown literal mode → eval error listing the modes; unknown patterned value → group unchanged. |
| `arp-with` | Strudel `arpWith(func)`: collect each group of simultaneous events into one event whose value is the **vector of values**, call the Lisp `f` on it, and inner-join the returned pattern (a non-pattern return is `pure`). `(arp-with (fn [ns] (seq (first ns) (last ns))) (chord "Cm7"))`. |
| `rolled` / `rolled-by` | Tidal `rolledWith`: in each group of `d` simultaneous events with whole `[s,e)`, event `n` starts at `s + n·(e−s)·|t|/d` and still ends at `e`; negative `t` rolls top-down (group reversed first); `t = 0` → unchanged. `rolled` = `rolled-by 1/4`. `t` patterned. Both names are added (`rolledBy` → `rolled-by` by the naming rule). |
| Chord symbols | Table of **34 qualities** with aliases (step 3). Root `A`–`G` + optional `#`/`b`; root placed in **octave 4** (C4 = 60 … B4 = 71) when unvoiced; optional slash bass `/X` = the highest `X` strictly below the root. Unknown symbol in a **string literal** → eval error at evaluation (`unknown chord symbol "Cmj7" — did you mean "Cmaj7"?`, edit distance ≤ 2, ties → table order); unknown values reaching a pattern at query time (e.g. from `(seq "Cxyz")`) are dropped. |
| Voicing dictionary | **Decided 2026-10-27, kept:** small own MIT-clean dictionary, no port of Strudel's AGPL `ireal` dictionary. Contents (step 4): for each chord quality, a **voiced tone set** of ≤ 4 pitch classes (reduce > 4 tones by dropping the perfect 5th, then the root) and its **close-position inversions** as the voicings. That is textbook music theory computed by a rule, not a copied table. |
| Voice leading | Strudel's current **anchor** model, deterministic per event (no state carried between events, so patterns stay pure functions of time): `:mode :below` (default) picks the candidate whose top note is the highest `<=` anchor (default `:c5` = 72); `:above` lowest bottom `>=` anchor; `:duck` top `<` anchor; `:root` root-position voicing whose bass (the root) is the highest root `<=` anchor. Because all chords cluster under the same anchor, Dm7 → G7 → C^7 moves each voice ≤ 4 semitones (step 4 shows the notes). |
| Voicing options | `(voicing opts pat)` with a map literal `{:anchor :c5 :mode :below :offset 0}`. `:offset n` moves `n` voicings up (negative = down) in the list of all candidates sorted by top note (ties: bottom note). `dictionary` and `octaves` options are **not** supported (single built-in dictionary) — parity row stays 🟡 with that note. Options are scalars (not patterned). |
| `root-notes` | Strudel `rootNotes(octave = 2)`: `(root-notes octave pat)` / `(root-notes pat)` (octave 2). Value = the chord **root** (not the slash bass) as a note keyword in that octave. `octave` patterned in the 2-arity form; a 1-arity numeric argument is the curried form. |
| Where the code lives | Generic group/structure functions (`superimpose layer echo-with stutter arp arp-with rolled rolled-by`) → `repulse.core`; amp-scaling ones (`echo stut xfade jux-flip jux-flip-by`) → `repulse.params` (needs the `:amp` map logic; `core` can't require `params`); chord symbols, voicing, scale, `scale-transpose`, `transpose`, `root-notes` → `repulse.theory`. Lisp wrappers: layering in `builtins/pattern.cljs`, harmony in `builtins/music.cljs`. |
| Scope cut | `morph`, `setVoicingRange`/`addVoicings`/`registerVoicings`/`voicingAlias` (custom dictionaries) and the SuperCollider pitch model rows stay ❓ (What NOT to do). |

## Function list

All times are exact rationals. "group" = events of one query with the same `:whole`.

### Changed (3)

| # | REPuLse-Lisp | Strudel / Tidal | After TL5 | Patterned? |
|---|---|---|---|---|
| 1 | `(scale name root pat)` | `scale` / Tidal `toScale` | 0-based degrees → note keywords; vector `name` = semitone list; curried `(scale name root)`. | `name root` |
| 2 | `(chord kw root)` / **`(chord sym)`** | `chord` | Keyword form returns note keywords; new 1-arity takes chord symbols (strings/patterns) → stacked root-position notes, root in octave 4. | `sym` |
| 3 | `(transpose n pat)` | `transpose` / `trans` | Note keywords stay keywords; interval strings; patterned `n`. | `n` |

### New (16)

| # | REPuLse-Lisp | Strudel / Tidal | Semantics | Patterned? |
|---|---|---|---|---|
| 4 | `(superimpose f … pat)` | `superimpose` | `(stack pat (f pat) …)`. | — |
| 5 | `(layer f … pat)` | `layer` | `(stack (f pat) …)`. | — |
| 6 | `(echo times time feedback pat)` | `echo` | Layer `i`: `late i·time`, amp × `feedback^i`. | all three |
| 7 | `(stut times feedback time pat)` | `stut` | `(echo times time feedback pat)`. | all three |
| 8 | `(echo-with n t f pat)` | `echoWith` / Tidal `stutWith` | Layer `i` = `(f ∘ late t)^i pat`. | `n t` |
| 9 | `(stutter n t pat)` | Tidal `stutter` | `(echo-with n t identity pat)`. | `n t` |
| 10 | `(jux-flip f pat)` | `juxFlip` / `flux` | `(jux-flip-by 1 f pat)`. | — |
| 11 | `(jux-flip-by w f pat)` | `juxFlipBy` / `fluxBy` | `(jux-by (cat w −w) f pat)`. | `w` |
| 12 | `(xfade a pos b)` | `xfade` | `a` amp × `fadeGain(pos)`, `b` amp × `fadeGain(1−pos)`. | `pos` |
| 13 | `(arp mode pat)` | Tidal `arp` / Strudel `arp` | Mode keyword → reorder + spread each group; number → pick `i mod n`. | `mode` |
| 14 | `(arp-with f pat)` | `arpWith` | `f` gets the vector of group values, returns a pattern; inner join. | — |
| 15 | `(rolled pat)` / `(rolled-by t pat)` | Tidal `rolled` / `rolledBy` | Stagger group starts by `t` of the whole. | `t` |
| 16 | `(voicing pat)` / `(voicing opts pat)` | `voicing` + `anchor`/`mode`/`offset` | Chord symbols → voiced note keywords (step 4). | `pat` |
| 17 | `(root-notes pat)` / `(root-notes oct pat)` | `rootNotes` | Chord symbol → root note keyword in octave `oct` (default 2). | `oct` |
| 18 | `(scale-transpose n scale pat)` | `scaleTranspose` / `strans` | Move degrees/notes `n` steps inside `scale`. | `n` |

(`rolled` and `rolled-by` share row 15: 16 new names in total.)

## Background

### Music theory — `packages/core/src/repulse/theory.cljs`

- `(ns repulse.theory (:require [repulse.core :as core]))`.
- Private `note-semitones` (`{"c" 0 "d" 2 …}`); public `note-keyword?` (regex
  `[a-g][sb]?(-?\d+)?`), `note->midi` (C4 = 60, octave defaults to 4), private
  `midi-note-names` (sharps), `midi->note` (integer → keyword, `60 → :c4`, `51 → :ds3`;
  non-integer → Hz via `midi->hz`), `midi->hz`, `note->hz`.
- Private `interval->semitones` (`{1 0, :b2 1, 2 2, :b3 3, 3 4, 4 5, :s4 6, :b5 6, 5 7,
  :s5 8, :b6 8, 6 9, :bb7 9, :b7 10, 7 11, 8 12}`), private `resolve-intervals`.
- Private `scale-intervals` (12 entries: `:major :ionian :minor :aeolian :dorian :phrygian
  :lydian :mixolydian :locrian :pentatonic :minor-pentatonic :blues`) and private
  `chord-intervals` (13 entries: `:major :minor :major7 :minor7 :dom7 :m7b5 :dim :dim7
  :aug :aug7 :maj7s11 :sus2 :sus4`).
- `scale [scale-kw root pat]`: **1-indexed** (`(dec (int degree))`), returns **Hz**
  (`core/fmap degree->hz pat`), unknown scale → major.
- `chord ([chord-kw root] [chord-kw root source])`: `core/stack*` of `(core/pure hz source)`
  — **Hz**, one whole-cycle event per tone.
- `transpose [semitones pat]`: numbers × ratio, note keywords → **Hz**, maps with `:note`
  updated, other keywords passed through.
- Tests `packages/core/src/repulse/theory_test.cljs`: `scale-major-degrees`,
  `scale-minor-degrees`, `scale-degree-octave-wrapping`, `chord-major-triad`,
  `chord-minor7`, `chord-dim7`, `transpose-up-octave`, `transpose-note-keyword-up-octave`,
  `transpose-note-keyword-down`, `transpose-passes-keywords`, `transpose-passes-rests`,
  `transpose-param-map`, `midi->note-round-trips` (+ note/hz tests).

### Pattern engine — `packages/core/src/repulse/core.cljs`

- `(:refer-clojure :exclude [rand time chunk iter shuffle])`. Rationals `[n d]`: `rat`,
  `rat+ rat- rat* rat-div rat< rat<= rat>= rat->float int->rat rat-neg ->rat`; `span`,
  `span-intersect`, `cycle-span`, `event [value whole part]`, `pattern`, `pattern?`,
  `query`, `pure ([v] [v source])`, `seq*`, `stack* [pats]`, `fmap`, `silence`,
  `onset?`, `analog?`, `query-onsets`.
- TL1: `app-left` (structure from the left; right queried over each left **whole**, keeps
  left `:source`), `app-right`, `app-both`, `inner-join`, `outer-join`, `squeeze-join`,
  `patternify`, `patternify2`, `patternify3`; private `->pat`, `whole-or-part`,
  `with-source`.
- `fast`, `slow`, `late`, `early`, `rev`, `every`, `cat* [pats]`, `off [amount f pat]`
  (`(stack* [pat (late amount (f pat))])` — the model for `superimpose`).
- TL3 private helpers (after `;;; ── Phase TL3`): `keep-values`, `cycle-of`, `->int`
  (truncate number/rational), `fmap-event [f pat]` (`f` gets the event, returns the new
  value), `shift-event`. TL4 block is the last block in the file (`apply-n` at the end).
- Tests: `packages/core/src/repulse/core_test.cljs` (helpers `vals-in`, `cyc`, `by-time`).

### Parameters — `packages/core/src/repulse/params.cljs`

- Private `to-map` (`v` → `{:note v}` unless a map), private `apply-param` (sets a key via
  `core/app-right`, structure from the note pattern); `amp`, `attack`, `decay`, `release`,
  `pan`, `rate`, …, `jux [f pat]` (`(stack* [(pan -1 pat) (pan 1 (f pat))])`), `jux-by
  [width f pat]` (patternified width), `midi-out`, `hurry`.
- `amp` **sets** `:amp`; there is no multiplying helper yet. Tests:
  `packages/core/src/repulse/params_test.cljs`.

### Lisp — `packages/lisp/src/repulse/lisp/`

- `builtins/music.cljs` `make-builtins`: `"scale"` (3-arity, `u/unwrap`), `"chord"`
  (2-arity, passes `(u/source-of kw)`), `"transpose"` (1-arity curried / 2-arity).
- `builtins/pattern.cljs`: `"seq"` (string args stay strings), `"stack"`, `"pure"`,
  `"jux"` `[f p]`, `"jux-by"` `[w f p]` (`u/pat-arg w`), `"off"`.
- `builtins/structure.cljs`: private helpers `xf` (unwrap a Lisp fn and its result),
  `xf2`, `step->pat`, `p1`, `n+p`, `n+f+p`, `a+b+p` — copy the pattern for curried forms.
- `builtins/arith.cljs`: `numeric-op` — note keywords go `note->midi` → op →
  `midi->note`; `make-op` with mode keywords; `"add" "sub" "mul" "div" "set"`.
- `builtins/params.cljs`: `"comp"` (`(apply comp fs)`).
- `util.cljs`: `u/unwrap`, `u/deep-unwrap` (for the options map), `u/source-of`,
  `u/pat-arg` (strings → `mini/parse` with source offset).
- `mini.cljs`: `special?` chars ``[ ] < > * ? : @ , ! % ( ) { } |``; `atom-value`: `~`/`_`
  → `:_`, `-?\d+/\d+` → rational, `parseFloat` success → number (so `"3M"` → `3`),
  lower-case note names → keyword, anything else → `(keyword text)` (`"C^7"` → `:C^7`).
- `eval.cljs`: `make-env` merges `(b-music/make-builtins)` (~line 371); `levenshtein`
  (~line 27) is in `eval.cljs`, which `music.cljs` can't require (circular) — theory gets
  its own small edit-distance helper. Errors: `(ex-info msg {:type :eval-error})`.
- Tests: `integration_test.cljs` (`eval-pattern`, `eval-pat`, `onsets`; chord/transpose
  tests ~lines 121–130; TL1 scale tests ~366–394; `segment … (scale :minor :c4)` ~473–491;
  `tl4-documented-examples-evaluate` ~line 1031 — model for TL5), `eval_test.cljs`
  (`(transpose 12)` ~line 108).

### Audio, MIDI, export (read-only for TL5 — verify, don't change)

- `app/src/repulse/audio.cljs` `play-event`: note keywords handled in the `:note`-map
  branch (every synth voice: `(if (theory/note-keyword? note) (theory/note->hz note)
  (double note))`, ~lines 399–447) and the bare-keyword branch (~483); MIDI note out
  (~649–664) converts `note->hz` → `midi/hz->midi`.
- `app/src/repulse/env/builtins/midi.cljs` `"midi-export"` (~line 63–94): same keyword →
  Hz → `midi/hz->midi` conversion. `packages/lisp/.../builtins/synth.cljs` (~line 34):
  `:freq` from `theory/note->hz` for keyword notes.
- `app/src/repulse/test_api.cljs` exposes `evalToEvents` (TEST1 harness);
  `e2e/audio-render.spec.ts` has `render(page, code)` / `countOnsets`.

### Content using `scale` / `chord` / `transpose`

- `app/src/repulse/content/tutorial.cljs` chapter 7 (~lines 111–131): **1-based**
  (`(seq 1 1 4 6)`, `(seq 1 3 5 8 5 3)`, text "degree numbers (1, 2, 3, …)"); chapter 8
  (~144) already 0-based.
- `app/src/repulse/content/demos.cljs` (lines 27, 46, 74, 121, 145, 166 scale; 40, 115, 173
  chord), `app/public/snippets/library.json`, `docs/ai/cookbook.json`,
  `app/src/repulse/fx_test.cljs:90`: written **with 0 as the root** (today they play the
  scale tone *below* the root for degree 0).
- `README.md:32` (`(seq 1 :_ 5 :_)`, 1-based), `app/public/embed-test.html:45` (0-based).
- `app/src/repulse/ai/system_prompt.cljs:18,56` ("maps degree integers to Hz"),
  `docs/ai/concepts.json` lines 82–86 (hand-written; `gen:ai-docs` only writes
  `builtins.json`).
- Editor: `app/src/repulse/lisp-lang/repulse-lisp.grammar` `BuiltinName` (line 32 has
  `"scale" | "chord" | "transpose" |`), `completions.js` lines 15–17, `hover.js` `"add"`
  example (~522), `"scale"` / `"chord"` / `"transpose"` (~546–560), `"midi-out"` example
  (~827), `builtin_meta.edn` `:scale` (~462), `:chord` (~469), `:transpose` (~475).
- `docs/USAGE.md` `## Music theory` (~line 1284): `### scale` says "one-indexed … Hz",
  `### chord` "Hz values", `### transpose` "numeric (Hz) values".
- `app/public/embed.js` is a committed build artefact — do not hand-edit.

## Implementation

Work in this order; each step ends with `npm run test` green.

### 0. Verify tooling (CLAUDE.md Rule 4)

`npm run gen:grammar && npm run gen:ai-docs && npm run test` on the unmodified branch.

### 1. Layering — `core.cljs`, new block at the end `;;; ── Phase TL5: Layering & arpeggios ──`

```clojure
(defn superimpose [fs pat] (stack* (into [pat] (map #(% pat) fs))))
(defn layer       [fs pat] (stack* (mapv #(% pat) fs)))

(defn- echo-with* [n t f pat]
  (let [n (->int n)]
    (if (<= n 1) pat
        (stack* [pat (f (late t (echo-with* (dec n) t f pat)))]))))
(defn echo-with [n t f pat] ((patternify2 (fn [n' t' p] (echo-with* n' t' f p))) n t pat))
(defn stutter   [n t pat]   (echo-with n t identity pat))
```

`superimpose`/`layer` take a **vector** of functions in core; the Lisp wrapper collects
the variadic args. `late` already accepts numbers, rationals and patterns.

### 2. Groups, `arp`, `arp-with`, `rolled` — same block

```clojure
(defn- group-wholes [evs]                       ; order-preserving, digital events only
  (let [ws (distinct (keep :whole evs))]
    (map (fn [w] (filter #(= w (:whole %)) evs)) ws)))

(defn- with-groups [f pat]                      ; analog events pass through
  (pattern (fn [sp] (let [evs (query pat sp)]
                      (concat (remove :whole evs) (mapcat f (group-wholes evs)))))))

(defn- spread-out [evs]                         ; Tidal spreadOut
  (let [d (count evs)]
    (keep-indexed
     (fn [n ev]
       (let [{s :start e :end} (:whole ev)
             dur (rat-div (rat- e s) (int->rat d))
             ns  (rat+ s (rat* dur (int->rat n)))
             nw  (span ns (rat+ ns dur))
             np  (span-intersect nw (:part ev))]
         (when np (assoc ev :whole nw :part np))))
     evs)))

(defn- converge [xs]                            ; x0, xn, x1, xn-1, …
  (loop [v (vec xs) front? true out []]
    (if (empty? v) out
        (if front? (recur (subvec v 1) false (conj out (first v)))
                   (recur (pop v) true (conj out (peek v)))))))

(def arp-modes                                  ; public: the Lisp wrapper validates names
  (let [init  #(vec (butlast %))
        rev   #(vec (reverse %))
        pinky (fn [x] (vec (mapcat (fn [y] [y (peek x)]) (pop x))))
        thumb (fn [x] (vec (mapcat (fn [y] [(first x) y]) (rest x))))
        m {:up            vec
           :down          rev
           :up-down       #(into (init %) (init (rev %)))
           :down-up       #(into (init (rev %)) (init %))
           :up-and-down   #(into (vec %) (rev %))
           :down-and-up   #(into (rev %) %)
           :converge      converge
           :diverge       #(rev (converge %))
           :disconverge   #(into (converge %) (rest (rev (converge %))))
           :pinky-up      pinky
           :pinky-up-down #(into (init (pinky %)) (init (rev (pinky %))))
           :thumb-up      thumb
           :thumb-up-down #(into (init (thumb %)) (init (rev (thumb %))))}]
    (assoc m :updown (:up-down m) :downup (:down-up m) :pinkyup (:pinky-up m)
             :pinkyupdown (:pinky-up-down m) :thumbup (:thumb-up m)
             :thumbupdown (:thumb-up-down m))))

(defn- arp* [mode pat]                         ; mode path (Tidal)
  (if-let [f (get arp-modes mode)]
    (with-groups #(if (< (count %) 2) % (spread-out (f (vec %)))) pat)
    pat))                                       ; unknown patterned value → unchanged

(defn arp-index [ipat pat]                      ; index path (Strudel): structure from ipat
  (inner-join
   (pattern
    (fn [sp]
      (for [g (group-wholes (query pat sp))]
        (assoc (first g) :value
               (pattern (fn [isp]
                          (for [ie (query (->pat ipat) isp)
                                :let [src (nth g (mod (->int (:value ie)) (count g)))]]
                            (with-source (assoc ie :value (:value src)) (:source src)))))))))))

(defn arp [mode pat]
  (if (or (number? mode) (vector? mode))        ; scalar number or rational
    (arp-index mode pat)
    ((patternify arp*) mode pat)))
```

Dispatch for patterns is decided by the **Lisp wrapper**: a string argument whose mini
atoms are all numbers (`"0 2 1 3"`, `"<0 [1 2]>"`) is
passed to `core/arp-index`; a keyword or a string of mode names (`"<up down>"`) goes to
`core/arp`. The wrapper decides from the literal text (split as in step 7's
`check-chord-literal!`); a non-literal pattern argument goes to `core/arp` (mode path).
In the index path the inner join gives each picked note the index event's whole and
part, and the picked group member's value and `:source`. In the mode path the result of
a group keeps each event's value, `:source` and part (restricted to the new whole).

`arp-with`:

```clojure
(defn arp-with [f pat]
  (inner-join
   (pattern (fn [sp] (for [g (group-wholes (query pat sp))]
                       (assoc (first g) :value (->pat (f (mapv :value g)))))))))
```

`rolled`:

```clojure
(defn- rolled-by* [t pat]
  (let [t (->rat t)]
    (if (= (first t) 0) pat
        (with-groups
          (fn [g]
            (let [g (if (neg? (first t)) (reverse g) g) d (count g)
                  at (rat-div (if (neg? (first t)) (rat-neg t) t) (int->rat d))]
              (keep-indexed
               (fn [n ev]
                 (let [{s :start e :end} (:whole ev)
                       ns (rat+ s (rat* (rat* (rat- e s) at) (int->rat n)))
                       nw (span ns e)
                       np (span-intersect nw (:part ev))]
                   (when np (assoc ev :whole nw :part np))))
               g)))
          pat))))
(defn rolled-by [t pat] ((patternify rolled-by*) t pat))
(defn rolled [pat] (rolled-by [1 4] pat))
```

### 3. Chord symbols — `theory.cljs`

Add a public table `chord-qualities` — a vector (order matters for error suggestions) of
`[aliases semitones]`, root C. These are the **34 supported qualities**:

| # | Aliases (suffix after the root) | Semitones from root | Voiced set (step 4) |
|---|---|---|---|
| 1 | `""` `M` `maj` | 0 4 7 | 0 4 7 |
| 2 | `m` `min` `-` | 0 3 7 | 0 3 7 |
| 3 | `dim` `o` | 0 3 6 | 0 3 6 |
| 4 | `aug` `+` | 0 4 8 | 0 4 8 |
| 5 | `sus2` | 0 2 7 | 0 2 7 |
| 6 | `sus4` `sus` | 0 5 7 | 0 5 7 |
| 7 | `5` | 0 7 | 0 7 |
| 8 | `6` | 0 4 7 9 | 0 4 7 9 |
| 9 | `m6` `-6` | 0 3 7 9 | 0 3 7 9 |
| 10 | `69` | 0 4 7 9 14 | 0 2 4 9 |
| 11 | `add9` `add2` | 0 4 7 14 | 0 2 4 7 |
| 12 | `madd9` | 0 3 7 14 | 0 2 3 7 |
| 13 | `7` | 0 4 7 10 | 0 4 7 10 |
| 14 | `^7` `^` `maj7` `M7` `Δ` `Δ7` | 0 4 7 11 | 0 4 7 11 |
| 15 | `m7` `-7` `min7` | 0 3 7 10 | 0 3 7 10 |
| 16 | `m^7` `mM7` `mmaj7` `-^7` | 0 3 7 11 | 0 3 7 11 |
| 17 | `m7b5` `h7` `h` `ø` `ø7` `-7b5` | 0 3 6 10 | 0 3 6 10 |
| 18 | `o7` `dim7` | 0 3 6 9 | 0 3 6 9 |
| 19 | `7sus4` `7sus` | 0 5 7 10 | 0 5 7 10 |
| 20 | `+7` `7#5` `aug7` | 0 4 8 10 | 0 4 8 10 |
| 21 | `7b5` | 0 4 6 10 | 0 4 6 10 |
| 22 | `9` | 0 4 7 10 14 | 0 2 4 10 |
| 23 | `^9` `maj9` `M9` | 0 4 7 11 14 | 0 2 4 11 |
| 24 | `m9` `-9` | 0 3 7 10 14 | 0 2 3 10 |
| 25 | `7b9` | 0 4 7 10 13 | 0 1 4 10 |
| 26 | `7#9` | 0 4 7 10 15 | 0 3 4 10 |
| 27 | `9sus4` `9sus` | 0 5 7 10 14 | 0 2 5 10 |
| 28 | `11` | 0 7 10 14 17 | 0 2 5 10 |
| 29 | `m11` `-11` | 0 3 7 10 14 17 | 2 3 5 10 |
| 30 | `7#11` | 0 4 7 10 18 | 0 4 6 10 |
| 31 | `^7#11` `maj7#11` | 0 4 7 11 18 | 0 4 6 11 |
| 32 | `13` | 0 4 7 10 14 21 | 2 4 9 10 |
| 33 | `m13` `-13` | 0 3 7 10 14 21 | 2 3 9 10 |
| 34 | `7b13` | 0 4 7 10 20 | 0 4 8 10 |

Parsing — `(parse-chord-symbol s)` → `{:root midi :intervals [...] :bass midi-or-nil}` or
`nil`:

1. `s` = string, or keyword text `(subs (str kw) 1)`; anything else → `nil`.
2. Split a slash bass at the **last** `/` if the part after it matches `[A-G][#b]?`.
3. `#"^([A-G])([#b]?)(.*)$"` → root letter, accidental, quality suffix. Root MIDI =
   `60 + {C 0 D 2 E 4 F 5 G 7 A 9 B 11} + (#: +1, b: −1)` (so `Cb` = 59, `B#` = 72).
4. Look the suffix up in an alias → index map built from `chord-qualities` (exact,
   case-sensitive). Not found → `nil`.
5. Bass: pitch class of the slash note; MIDI = the highest note with that pitch class
   strictly below the root.

`(chord-symbol->notes s)` → ascending MIDI vector: `[bass?] + (root + semitones)`.

Error helper `(chord-symbol-error s)` → message string: `unknown chord symbol "S"` plus
` — did you mean "R<alias>"?` for the alias with the smallest edit distance ≤ 2 to the
suffix (ties → first in table order; private 10-line Levenshtein in theory). Example:
`"Cmj7"` → `unknown chord symbol "Cmj7" — did you mean "Cmaj7"?`.

Fan-out helper (keeps whole, part and `:source` of the symbol event):

```clojure
(defn- fan-out [f pat]                          ; f: value → vector of values, or nil
  (core/pattern
   (fn [sp] (mapcat (fn [ev] (let [v (:value ev)]
                               (if (= v :_) [ev]
                                   (map #(assoc ev :value %) (f v)))))  ; nil → dropped
                    (core/query pat sp)))))
```

`chord`: keep the 2/3-arity keyword form (now `(midi->note (+ root-midi s))`), add
`(chord-symbols pat)` = `(fan-out #(some->> (chord-symbol->notes %) (mapv midi->note)) pat)`.

### 4. Voicing — `theory.cljs`

**Dictionary (MIT, written for REPuLse).** For each quality the voiced set is the last
column of the table above, derived by this rule (implement the rule, and assert the
column in a test): take the semitones mod 12, distinct, in order; while more than 4
remain, drop pitch class 7 (the perfect fifth) if present, otherwise drop 0 (the root).
Sort ascending. The **voicings** of a quality are the close-position inversions of its
voiced set, in order: inversion `k` = `(concat (drop k set) (map #(+ % 12) (take k set)))`.
Expanded for the progression in the DoD:

| Quality | Voicings (semitones from the root, inversion 0 … 3) |
|---|---|
| `m7` | `[0 3 7 10]` `[3 7 10 12]` `[7 10 12 15]` `[10 12 15 19]` |
| `7` | `[0 4 7 10]` `[4 7 10 12]` `[7 10 12 16]` `[10 12 16 19]` |
| `^7` | `[0 4 7 11]` `[4 7 11 12]` `[7 11 12 16]` `[11 12 16 19]` |
| `7b13` | `[0 4 8 10]` `[4 8 10 12]` `[8 10 12 16]` `[10 12 16 20]` |
| `""` (major) | `[0 4 7]` `[4 7 12]` `[7 12 16]` |

**Candidates.** For a symbol with root pitch class `r` = `(mod root 12)`: every voicing
`v` and every octave `k` such that all notes `r + 12k + v_j` lie in 0…127.

**Selection** (`anchor` MIDI `A`, default 72 = `:c5`):

- `:below` (default): candidates with top `<= A`; choose max top.
- `:duck`: top `< A`; choose max top.
- `:above`: bottom `>= A`; choose min bottom.
- `:root`: only inversion 0 (prepend the root `r + 12k` below it when the voiced set has
  no 0, e.g. `11`, `m11`, `13`, `m13`); bottom `<= A`; choose max bottom.

Ties → first voicing in dictionary order. `:offset n` (default 0): sort **all**
candidates by top note (then bottom note), find the selected one and take the one `n`
places later (clamped to the list). Slash bass: prepend the highest note with the bass
pitch class strictly below the voicing's lowest note. Output: note keywords, ascending.

Worked example (default options): Dm7 → `[62 65 69 72]` (`:d4 :f4 :a4 :c5`, top C5 = 72);
G7 → `[62 65 67 71]` (`:d4 :f4 :g4 :b4`, inversion 2, top B4 = 71); C^7 → `[64 67 71 72]`
(`:e4 :g4 :b4 :c5`, inversion 1, top C5 = 72). Voice motion Dm7→G7 `0 0 −2 −1`, G7→C^7
`+2 +2 +4 +1` — all ≤ 4. A7b13 → `[61 65 67 69]` (`:cs4 :f4 :g4 :a4`).

```clojure
(defn voicing
  ([pat] (voicing {} pat))
  ([{:keys [anchor mode offset] :or {anchor :c5 mode :below offset 0}} pat]
   (fan-out #(some->> (voice-symbol % (->midi anchor) mode offset) (mapv midi->note)) pat)))
```

`->midi`: note keyword → `note->midi`, number → itself. `voice-symbol` returns `nil` for
unknown symbols. Unknown `mode` → eval error at the Lisp level.

### 5. Scales, `scale-transpose`, `transpose`, `root-notes` — `theory.cljs`

Add to `scale-intervals` (all expressible with `interval->semitones`):

```clojure
:harmonic-minor    [1 2 :b3 4 5 :b6 7]
:melodic-minor     [1 2 :b3 4 5 6 7]
:whole-tone        [1 2 3 :s4 :s5 :b7]
:chromatic         [1 :b2 2 :b3 3 4 :s4 5 :b6 6 :b7 7]
:major-pentatonic  [1 2 3 5 6]
:hirajoshi         [1 2 :b3 5 :b6]
:phrygian-dominant [1 :b2 3 4 5 :b6 :b7]
:lydian-dominant   [1 2 3 :s4 5 6 :b7]
:altered           [1 :b2 :b3 3 :b5 :b6 :b7]
:diminished        [1 2 :b3 4 :b5 :b6 6 7]
:bebop-dominant    [1 2 3 4 5 6 :b7 7]
```

New `scale` core (0-based, notes):

```clojure
(defn- scale-semis [name]                       ; keyword or semitone vector
  (if (vector? name) name
      (resolve-intervals (get scale-intervals name [1 2 3 4 5 6 7]))))

(defn- degree->midi [semis root-midi d]
  (let [n (count semis) d (int (Math/floor d))
        oct (int (Math/floor (/ d n)))]
    (+ root-midi (* 12 oct) (nth semis (- d (* oct n))))))

(defn- deg-val [x] (cond (number? x) x
                         (and (vector? x) (= 2 (count x))) (/ (first x) (second x))
                         :else nil))

(defn- scale* [name root pat]
  (let [semis (scale-semis name) r (note->midi root)
        f (fn [v] (if-let [d (deg-val v)] (midi->note (degree->midi semis r d)) v))]
    (core/fmap #(if (and (map? %) (deg-val (:note %))) (update % :note f) (f %)) pat)))

(defn scale [name root pat] ((core/patternify2 scale*) name root pat))
```

Careful: `patternify2` treats a vector `name` as a scalar (not a pattern) — correct.

`scale-transpose`:

```clojure
(defn- scale-transpose* [n sc pat]
  (let [[name root] (if (vector? sc) (if (keyword? (first sc)) sc [sc :c]) [sc :c])
        ;; a semitone vector is a scale, [kw note] is name+root
        semis (scale-semis name) k (count semis) n (int (Math/floor (deg-val n)))
        rpc (mod (note->midi root) 12)
        step (fn [m]                            ; MIDI → MIDI, snap down then move n
               (let [rel (- m rpc) oct (int (Math/floor (/ rel 12))) pc (- rel (* 12 oct))
                     idx (last (keep-indexed (fn [i s] (when (<= s pc) i)) semis))]
                 (degree->midi semis (+ rpc (* 12 oct)) (+ idx n))))]
    (core/fmap (fn [v] (cond (deg-val v) (+ (deg-val v) n)
                             (note-keyword? v) (midi->note (step (note->midi v)))
                             :else v))
               pat)))
(defn scale-transpose [n sc pat] ((core/patternify (fn [n' p] (scale-transpose* n' sc p))) n pat))
```

(Disambiguate `sc`: a keyword → name with root C; `[kw note]` → name + root; a vector of
numbers → semitone scale with root C.)

`transpose`:

```clojure
(defn interval->semitones-str [s]               ; "3M" → 4, "-2M" → -2, nil if invalid
  ...)  ; number q: steps [0 2 4 5 7 9 11] for (mod (dec |q|) 7), + 12·(quot (dec |q|) 7);
        ; perfect class (1 4 5): P 0, A +1, d −1; others: M 0, m −1, A +1, d −2; sign of q.

(defn- transpose* [n pat]
  (let [n (deg-val n) ratio (js/Math.pow 2 (/ n 12))
        shift (fn [v] (cond (number? v) (* v ratio)
                            (note-keyword? v) (midi->note (+ (note->midi v) n))
                            :else v))]
    (core/fmap #(if (and (map? %) (:note %)) (update % :note shift) (shift %)) pat)))
(defn transpose [n pat] ((core/patternify transpose*) n pat))
```

`root-notes`:

```clojure
(defn- root-notes* [oct pat]
  (fan-out #(when-let [{:keys [root]} (parse-chord-symbol %)]
              [(midi->note (+ (mod root 12) (* 12 (inc (int (deg-val oct))))))])
           pat))
(defn root-notes ([pat] (root-notes 2 pat)) ([oct pat] ((core/patternify root-notes*) oct pat)))
```

### 6. Amp-scaling layering — `params.cljs`

```clojure
(defn- amp* [f v]                               ; multiply, rests stay rests
  (if (= v :_) v (update (to-map v) :amp #(* (or % 1.0) f))))

(defn- echo* [times time feedback pat]
  (let [n (int (core/rat->float (core/->rat times))) fb (core/rat->float (core/->rat feedback))]
    (core/stack* (mapv (fn [i] (core/fmap #(amp* (Math/pow fb i) %)
                                          (core/late (core/rat* (core/->rat time) [i 1]) pat)))
                       (range n)))))
(defn echo [times time feedback pat] ((core/patternify3 echo*) times time feedback pat))
(defn stut [times feedback time pat] (echo times time feedback pat))

(defn jux-flip-by [w f pat]
  (let [neg #(- (core/rat->float (core/->rat %)))]
    (jux-by (if (core/pattern? w)
              (core/cat* [w (core/fmap neg w)])
              (core/cat* [(core/pure w) (core/pure (neg w))]))
            f pat)))
(defn jux-flip [f pat] (jux-flip-by 1 f pat))

(defn- fade-gain [p] (if (< p 0.5) 1 (* 2 (- 1 p))))
(defn xfade [a pos b]
  (let [pos (if (core/pattern? pos) pos (core/pure pos))
        side (fn [pat g] (core/app-left (fn [v p] (amp* (fade-gain (g (core/rat->float (core/->rat p)))) v))
                                        (if (core/pattern? pat) pat (core/pure pat)) pos))]
    (core/stack* [(side a identity) (side b #(- 1 %))])))
```

`(range 0)` → empty stack = silence for `times < 1`. Check that `core/->rat` accepts
floats (it does: floats → nearest rational) and that `rat->float` of `[1 1]` is `1`
(so the `i = 0` layer gets `{:note v :amp 1}`).

### 7. Lisp builtins

`builtins/pattern.cljs` (layering; use `xf` from structure or a local copy):

```clojure
"superimpose" (variadic-fs core/superimpose)   ; last arg fn → curried
"layer"       (variadic-fs core/layer)
"echo"        3-num + pat, curried at 3 args   ; (u/pat-arg …) for each number
"stut"        same shape → params/stut
"echo-with"   n t f [pat], curried at 3 args   ; f via xf
"stutter"     n t [pat], curried at 2 args
"jux-flip"    f [pat], curried at 1 arg
"jux-flip-by" w f [pat], curried at 2 args
"xfade"       (fn [a pos b] (params/xfade (u/pat-arg a) (u/pat-arg pos) (u/pat-arg b)))
"arp"         mode [pat], curried at 1         ; validate literal keyword/string modes
"arp-with"    f [pat], curried at 1            ; f wraps the vector → (u/unwrap (f v))
"rolled"      (p1 core/rolled)
"rolled-by"   t [pat], curried at 1
```

`variadic-fs`: `(fn [& args] (let [l (u/unwrap (last args))] (if (fn? l) (let [fs (mapv xf args)] (fn [p] (f fs (u/unwrap p)))) (f (mapv xf (butlast args)) (u/pat-arg (last args))))))`.

`builtins/music.cljs` (harmony):

- `"scale"`: 2-arity curried, 3-arity; `name` via `u/pat-arg` unless it is a keyword or
  vector (`u/deep-unwrap` for vectors); literal unknown keyword → eval error
  `scale: unknown scale :foo — known: …` (sorted names).
- `"chord"`: 1-arity → `theory/chord-symbols` of `(u/pat-arg sym)` after validating the
  literal; 2-arity unchanged (keywords).
- `"voicing"`: 1-arity: map → curried, otherwise pattern; 2-arity `(opts pat)`; options via
  `u/deep-unwrap`; unknown option key or `:mode` → eval error.
- `"root-notes"`, `"scale-transpose"` (2-arity curried / 3-arity), `"transpose"`
  (rewrite interval tokens in string args width-preserving, then `u/pat-arg`).
- Literal validation helper `check-chord-literal!`: for a string argument, split the text
  on whitespace and the mini special chars plus `~ _ .`, ignore tokens that parse as
  numbers, and throw `(ex-info (theory/chord-symbol-error tok) {:type :eval-error})` for
  the first token that `theory/parse-chord-symbol` rejects. Used by `chord`, `voicing`,
  `root-notes` and by `arp` when its pattern argument is a string.

### 8. Tests

- `theory_test.cljs`: rewrite the scale/chord/transpose tests for 0-based keyword
  output; table-driven chord-symbol test over **every alias** of all 34 qualities on root
  C plus the roots/slash cases in the DoD; voiced-set column test; voicing vectors;
  scale-transpose; interval strings; new scales.
- `core_test.cljs`: superimpose, layer, echo-with, stutter, arp (modes and indices),
  arp-with, rolled — including chunked-query equality (`[0,1/2)` + `[1/2,1)` = `[0,1)`).
- `params_test.cljs`: echo, stut, jux-flip(-by), xfade.
- `integration_test.cljs`: update lines ~121–130, ~366–394, ~473–491 for keyword output
  (the TL1 tests compare two `scale` calls — keep the relation, update literal values);
  Lisp-level error messages; curried forms; `tl5-documented-examples-evaluate` (every
  example in the new USAGE sections and in `builtin_meta.edn` evaluates to a pattern).
- `eval_test.cljs` `(transpose 12)` still returns a function.
- `e2e/audio-render.spec.ts` (TEST1 offline harness): onset counts for the Playwright DoD
  item.

### 9. Migration (breaking `scale`, keyword output)

- 1-based content → subtract 1 from every degree: `tutorial.cljs` chapter 7
  (`(seq 1 1 4 6)` → `(seq 0 0 3 5)`, `(seq 1 3 5 8 5 3)` → `(seq 0 2 4 7 4 2)`, text
  "degree numbers (1, 2, 3, …)" → "(0, 1, 2, …) — 0 is the root"); `README.md:32`
  (`(seq 1 :_ 5 :_)` → `(seq 0 :_ 4 :_)`); USAGE examples.
- Content already written with 0 as the root (`demos.cljs`, `library.json`,
  `cookbook.json`, `fx_test.cljs`, `embed-test.html`, tutorial chapter 8): **keep the
  numbers** — they now play what they were written for (root instead of the tone below).
  `git grep -nE "\(scale " -- app/src app/public/snippets docs README.md` must show no
  remaining 1-based usage (reviewed by hand; list them in the PR).
- Hz wording → note wording: `completions.js` 15–17, `hover.js` (`scale`, `chord`,
  `transpose`, the `add` example `(seq 1 3 5 8)` → `(seq 0 2 4 7)`), `builtin_meta.edn`,
  `system_prompt.cljs` 18/56, `docs/ai/concepts.json` 82–86.
- Community snippets (S3) can't be migrated: USAGE.md gets a callout "**Changed in TL5:**
  `scale` degrees start at 0 and `scale`/`chord`/`transpose` return note names. Old code:
  subtract 1 from every degree." and the same line goes into the PR body for the release
  notes.

### 10. Registration (CLAUDE.md Rule 3)

1. `BuiltinName` in `app/src/repulse/lisp-lang/repulse-lisp.grammar`: add
   `"superimpose" | "layer" | "echo" | "echo-with" | "stut" | "stutter" | "jux-flip" |
   "jux-flip-by" | "xfade" | "arp" | "arp-with" | "rolled" | "rolled-by" | "voicing" |
   "root-notes" | "scale-transpose"` (16 names; note multi-word names
   must be listed before any prefix they share, as the existing `"jux" | "jux-by"` entries
   are).
2. **`npm run gen:grammar`** — commit `parser.js` and `parser.terms.js`.
3. `completions.js`: 16 new `{ label, type: "function", detail }` entries; rewrite the
   `scale`/`chord`/`transpose` details (notes, 0-based, chord symbols, intervals).
4. `hover.js` `DOCS`: entries for the 16 names; update the three changed ones.
5. `builtin_meta.edn`: 16 entries (`:category` `"transform"` for all but `:chord`
   (`"source"`, existing) and `:voicing`/`:root-notes` (`"transform"`); `:returns
   "Pattern"`; `:side-effects []`; ≥ 1 example each; `:see-also`); update `:scale`,
   `:chord`, `:transpose`.
6. **`npm run gen:ai-docs`** — commit `docs/ai/builtins.json`.

### 11. Docs

- `docs/USAGE.md`: in `## Music theory` rewrite `### scale` (0-based, notes, vector
  scales, patterned names, full scale list incl. the 11 new ones, the TL5 callout),
  `### chord` (keyword form returns notes; chord symbols table — the 34 qualities with
  aliases; slash chords), `### transpose` (notes stay notes, intervals, patterned). Add
  `## Harmony` (after Music theory): `voicing` (options, modes, the Dm7–G7–C^7 example),
  `root-notes`, `scale-transpose`, `arp` (mode table + index form), `arp-with`, `rolled`,
  `rolled-by`. Add `## Layering` (or extend the section that documents `off`/`jux`):
  `superimpose`, `layer`, `echo`, `stut`, `echo-with`, `stutter`, `jux-flip`,
  `jux-flip-by`, `xfade`. Every new built-in in the reference tables with an example.
- `README.md`: line 32 migration; add the new names to the "Built-in pattern functions"
  table.
- `docs/STRUDEL-PARITY.md` "Layering, pitch & harmony — TL5": `superimpose echo echoWith
  juxFlip juxFlipBy arp arpWith voicing rootNotes chord scale scaleTranspose transpose
  xfade` → ✅ (notes: echo multiplies amp; echo-with follows Tidal `stutWith`; chord
  symbols are strings); `stut` stays 🔁 with note "implemented as alias, Tidal argument
  order"; voicing options row → 🟡 "`anchor`/`mode`/`offset` done; single built-in
  dictionary, no `dictionary`/`octaves`"; TL1 `layer` row → ✅ Phase TL5; Tidal-only
  `stutter`, `rolled`/`rolledBy` → ✅; `toScale` 🔁 note "`scale` with a semitone
  vector"; update the decision paragraph to "delivered"; recompute the Summary table.
- `ROADMAP.md` Phase TL5 → ✅ *delivered* with a **Delivered:** section (at delivery).

## Files to change

| File | Change |
|---|---|
| `packages/core/src/repulse/core.cljs` | New TL5 block: `superimpose layer echo-with stutter arp arp-with rolled rolled-by`, private `group-wholes with-groups spread-out converge arp*`, public `arp-modes`, `arp-index` |
| `packages/core/src/repulse/params.cljs` | `echo stut jux-flip jux-flip-by xfade`, private `amp* echo* fade-gain` |
| `packages/core/src/repulse/theory.cljs` | 0-based keyword `scale` (patterned, vectors), keyword `chord`, new scales, `chord-qualities`, `parse-chord-symbol`, `chord-symbol->notes`, `chord-symbol-error`, `chord-symbols`, `voicing`, `root-notes`, `scale-transpose`, `interval->semitones-str`, patterned keyword-preserving `transpose` |
| `packages/core/src/repulse/theory_test.cljs` | Rewritten scale/chord/transpose tests; chord-symbol table, voicing, scale-transpose, interval tests |
| `packages/core/src/repulse/core_test.cljs` | Layering/arp/rolled tests |
| `packages/core/src/repulse/params_test.cljs` | echo/stut/jux-flip/xfade tests |
| `packages/lisp/src/repulse/lisp/builtins/pattern.cljs` | 13 layering/arp builtins |
| `packages/lisp/src/repulse/lisp/builtins/music.cljs` | `chord` 1-arity, `voicing`, `root-notes`, `scale-transpose`, curried `scale`, interval-aware `transpose`, literal validation |
| `packages/lisp/src/repulse/lisp/integration_test.cljs` | Migrated tests, error messages, `tl5-documented-examples-evaluate` |
| `packages/lisp/src/repulse/lisp/eval_test.cljs` | Keep `(transpose 12)` test green |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` + `parser.js` + `parser.terms.js` | 16 names; `npm run gen:grammar` |
| `app/src/repulse/lisp-lang/completions.js` | 16 entries + 3 updated |
| `app/src/repulse/lisp-lang/hover.js` | 16 entries + `scale chord transpose add midi-out` examples |
| `app/src/repulse/content/builtin_meta.edn` + `docs/ai/builtins.json` | 16 entries + 3 updated; `npm run gen:ai-docs` |
| `app/src/repulse/content/tutorial.cljs` | Chapter 7 → 0-based |
| `app/src/repulse/content/demos.cljs` | Review only (already 0-based by intent); optionally showcase `voicing`/`arp` in one demo |
| `app/src/repulse/ai/system_prompt.cljs`, `docs/ai/concepts.json` | Hz → notes, 0-based |
| `e2e/audio-render.spec.ts` | TL5 onset tests |
| `docs/USAGE.md` | Music theory rewrite, `## Harmony`, layering section, TL5 callout |
| `README.md` | Line 32 migration; new names |
| `docs/STRUDEL-PARITY.md` | Rows + Summary |
| `ROADMAP.md` | TL5 delivered (at delivery) |
| `app/src/repulse/audio.cljs`, `env/builtins/midi.cljs`, `packages/audio` | **No change** (keywords already supported) |

## Definition of done

Notation: `:x [s,e)` = value and whole; "values c" = values of the onsets in cycle `c`;
MIDI numbers given for note keywords (`note->midi`).

**Scale (breaking) & transpose**
- [ ] `(scale :minor :c4 (seq 0 2 4 7))` → `:c4 :ds4 :g4 :c5` = MIDI 60 63 67 72 at quarters;
      `(scale :minor :c4 (seq -1))` → `:as3` = 58; `(scale :major :c4 (seq 0 7 14))` → 60 72 84;
      `(scale :major :c4 (seq 1))` → `:d4` (62 — proves 0-based); `(scale :minor :c4 (seq 1.7))` → `:d4`;
      `(scale :minor :c4 (seq :_ 0))` keeps the rest.
- [ ] `(add 12 (scale :minor :c3 (seq 0)))` → `:c4` = MIDI 60 (one octave above C3).
- [ ] Patterned / vector scales: `(scale "<major minor>" :c4 (seq 2))` values 0 → `:e4` (64), values 1 → `:ds4` (63);
      `(scale [0 2 4 5 7 9 11] :c4 (seq 0 1 2))` → 60 62 64; `(->> (seq 0 2) (scale :dorian :d3))` (curried) → 50 53;
      the 11 new scales: `(scale :harmonic-minor :c4 (seq 6))` → 71, `(scale :whole-tone :c4 (seq 5))` → 70,
      `(scale :chromatic :c4 (seq 13))` → 73, `(scale :altered :c4 (seq 3))` → 64, `(scale :bebop-dominant :c4 (seq 8))` → 72.
- [ ] `(chord :minor7 :c4)` → 4 whole-cycle events `:c4 :ds4 :g4 :as4` (60 63 67 70) — keywords, not Hz.
- [ ] `transpose`: `(transpose 12 (seq :c4 :e4))` → `:c5 :e5`; `(transpose "3M" (seq :c4))` → `:e4`;
      `(transpose "-2M" (seq :c4))` → `:as3`; `(transpose "8P" (seq :c4))` → `:c5`; `(transpose "<0 7>" (seq :c4))`
      values 1 → `:g4`; `(transpose "<3M 5P>" (seq :c4))` values 0/1 → `:e4`/`:g4` with the token's source offsets intact;
      `(transpose 12 (seq 440))` → `880`; `(transpose 7 {:note :c4 …})` map → `:note :g4`; drums/rests unchanged;
      `(transpose "3P" (seq :c4))` → eval error.
- [ ] `scale-transpose`: `(scale-transpose 2 :minor (seq 0 2 4))` → `2 4 6`, which through `(scale :minor :c4)` give
      `:ds4 :g4 :as4` (63 67 70); `(scale-transpose "<0 2>" :minor (seq 0 2 4))` values 0 → `0 2 4`, values 1 → `2 4 6`;
      `(scale-transpose 1 :minor (seq :c4 :eb4 :g4))` → `:d4 :f4 :gs4` (62 65 68); `(scale-transpose -1 :major (seq :c4))`
      → `:b3` (59); `(scale-transpose 1 [:dorian :d] (seq :d4 :e4))` → `:e4 :f4`; `(scale-transpose 0 :major (seq :cs4))` →
      `:c4` (snap down).

**Chord symbols**
- [ ] Parser covers the 34 qualities (every alias in the step 3 table) on root C — table-driven test, e.g. `"C"` → 60 64 67;
      `"Cm7"` → 60 63 67 70; `"C^7"` = `"Cmaj7"` = `"CM7"` → 60 64 67 71; `"Co7"` → 60 63 66 69; `"Ch7"` = `"Cm7b5"` → 60 63 66 70;
      `"C69"` → 60 64 67 69 74. Roots/slash: `"F#7b9"` → 66 70 73 76 79; `"Bb7"` → 70 74 77 80; `"A7b13"` → 69 73 76 79 89;
      `"G13"` → 67 71 74 77 81 88; `"C/E"` → 52 60 64 67; `"Am/G"` → 67 69 72 76; `"Cb"` → 59 63 66.
      The voiced-set column of the table is asserted for all 34 rows.
- [ ] `(chord "Cm7")` → 4 events whole `[0,1)` `:c4 :ds4 :g4 :as4`; `(chord "<C F>")` values 1 → `:f4 :a4 :c5`;
      `(chord (seq "Dm7" "G7"))` → 8 events, wholes `[0,1/2)` ×4 and `[1/2,1)` ×4, sources of the string tokens kept;
      `(chord "Cmj7")` → eval error `unknown chord symbol "Cmj7" — did you mean "Cmaj7"?`; `(chord "Cxyz")` → `unknown chord symbol "Cxyz"`.

**Voicing**
- [ ] `(voicing (seq "Dm7" "G7" "C^7"))` → `[0,1/3)` `:d4 :f4 :a4 :c5` (62 65 69 72), `[1/3,2/3)` `:d4 :f4 :g4 :b4`
      (62 65 67 71), `[2/3,1)` `:e4 :g4 :b4 :c5` (64 67 71 72): each voice (sorted index-wise) moves ≤ 4 semitones.
- [ ] `(voicing "<C^7 A7b13 Dm7 G7>")` values 0–3 → `64 67 71 72 | 61 65 67 69 | 62 65 69 72 | 62 65 67 71` (max move 4).
- [ ] Options: `(voicing "C")` → 64 67 72; `(voicing {:mode :above :anchor :c4} "C")` → 60 64 67;
      `(voicing {:mode :duck} "Dm7")` → 60 62 65 69; `(voicing {:offset 1} "Dm7")` → 65 69 72 74; `{:offset -1}` → 60 62 65 69;
      `(voicing {:mode :root :anchor :c4} "G7")` → 55 59 62 65; `(voicing "C/E")` → 52 64 67 72;
      curried `(->> "Dm7" (voicing {:anchor :c4}))` → top note ≤ 60; `(voicing {:mode :sideways} "C")` → eval error.
- [ ] `root-notes`: `(root-notes "<Cm7 F7>")` values 0 → `:c2`, values 1 → `:f2`; `(root-notes 3 (seq "Bb7" "Eb^7"))` →
      `:as3 [0,1/2)`, `:ds3 [1/2,1)`; `(root-notes 2 "C/E")` → `:c2`; patterned `(root-notes "<2 4>" "C")` values 1 → `:c4`.

**Arpeggios & rolls**
- [ ] `(arp :up (chord "Cm7"))` → `:c4 [0,1/4)`, `:ds4 [1/4,1/2)`, `:g4 [1/2,3/4)`, `:as4 [3/4,1)` = MIDI 60 63 67 70 in order.
- [ ] Modes on `(chord "Cm7")` (MIDI, evenly spread over `[0,1)`): `:down` 70 67 63 60; `:up-down` 60 63 67 70 67 63
      (sixths); `:down-up` 70 67 63 60 63 67; `:up-and-down` 60 63 67 70 70 67 63 60; `:converge` 60 70 63 67;
      `:diverge` 67 63 70 60; `:disconverge` 60 70 63 67 63 70 60; `:pinky-up` 60 70 63 70 67 70; `:thumb-up` 60 63 60 67 60 70;
      `"updown"` alias = `:up-down`; `(arp "<up down>" (chord "C"))` values 1 → `:g4 [1,4/3)`, `:e4`, `:c4`;
      `(arp :up (pure :c4))` → `:c4 [0,1)` unchanged; `(arp :sideways …)` → eval error listing the modes.
- [ ] Index form: `(arp "0 2 1 3" (chord "Cm7"))` → `:c4 [0,1/4)`, `:g4 [1/4,1/2)`, `:ds4 [1/2,3/4)`, `:as4 [3/4,1)`;
      `(arp "0 5" (chord "Cm7"))` → `:c4 [0,1/2)`, `:ds4 [1/2,1)`.
- [ ] `(arp :up (chord (seq "C" "F")))` → 60 64 67 at `[0,1/6) [1/6,1/3) [1/3,1/2)`, 65 69 72 at `[1/2,2/3) [2/3,5/6) [5/6,1)`;
      querying `[0,1/2)` and `[1/2,1)` separately gives the same events (chunk independence).
- [ ] `(arp-with (fn [ns] (seq (first ns) (last ns))) (chord "Cm7"))` → `:c4 [0,1/2)`, `:as4 [1/2,1)`.
- [ ] `(rolled (chord "C"))` → `:c4 [0,1)`, `:e4 [1/12,1)`, `:g4 [1/6,1)`; `(rolled-by -1/4 (chord "C"))` → `:g4 [0,1)`,
      `:e4 [1/12,1)`, `:c4 [1/6,1)`; `(rolled-by 0 …)` unchanged; `(rolled-by 1/2 (chord "Cm7"))` starts `0 1/8 1/4 3/8`.

**Layering**
- [ ] `(echo 3 1/8 0.6 (seq :cp))` → 3 onsets in cycle 0: `[0,1)` amp 1, `[1/8,9/8)` amp 0.6, `[1/4,5/4)` amp 0.36
      (values `{:note :cp :amp …}`); `(echo 3 1/8 0.5 (amp 0.8 (seq :cp)))` amps 0.8 0.4 0.2; `(echo 0 1/8 0.5 p)` → silence;
      `(echo 1 1/8 0.5 (seq :cp))` → one onset amp 1; patterned `(echo "<1 2>" 1/4 0.5 (seq :cp))` cycle 1 → 2 onsets.
- [ ] `(stut 3 0.5 1/8 (seq :cp))` → onsets at 0, 1/8, 1/4 with amps 1 0.5 0.25.
- [ ] `(echo-with 3 1/8 (add 12) (seq :c3))` → `:c3 [0,1)`, `:c4 [1/8,9/8)`, `:c5 [1/4,5/4)`; `(echo-with 1 1/8 rev p)` ≡ `p`;
      `(stutter 4 1/16 (seq :bd))` → `:bd` onsets at 0, 1/16, 1/8, 3/16, raw values (no `:amp`).
- [ ] `(superimpose (fast 2) (seq :a :b))` → `a [0,1/2)`, `b [1/2,1)`, `a [0,1/4)`, `b [1/4,1/2)`, `a [1/2,3/4)`, `b [3/4,1)`;
      `(superimpose (comp (fast 2) (add 12)) (seq :c3 :e3 :g3))` → c3 e3 g3 at thirds + c4 e4 g4 c4 e4 g4 at sixths;
      `(superimpose rev (fast 2) (seq :a :b))` → 3 layers; `(layer rev (fast 2) (seq :a :b))` → `b [0,1/2)`, `a [1/2,1)` +
      `a b a b` at quarters (no original).
- [ ] `(jux-flip rev (seq :a :b))` cycle 0 → `{:note :a :pan -1} [0,1/2)`, `{:note :b :pan -1}`, `{:note :b :pan 1} [0,1/2)`,
      `{:note :a :pan 1}`; cycle 1 → original pan 1, reversed pan −1; `(jux-flip-by 0.5 rev …)` → pans ∓0.5 / ±0.5.
- [ ] `(xfade (seq :bd :bd) 0.25 (seq :hh))` → two `:bd` amp 1, one `:hh` amp 0.5; pos 0.75 → `:bd` amp 0.5, `:hh` amp 1;
      pos 0.5 → both 1; `(xfade (seq :bd) "<0 1>" (seq :hh))` values 0 → bd 1 / hh 0, values 1 → bd 0 / hh 1.
- [ ] Curried forms evaluate: `(->> (seq :c3 :e3) (superimpose (add 12)) (echo 3 1/8 0.6) (stutter 2 1/16) (arp :up)
      (rolled-by 1/8) (jux-flip rev))`, `(->> "<Dm7 G7>" (voicing) (arp :up-down))`, `(->> (seq 0 2 4) (scale-transpose 1 :minor)
      (scale :minor :c4))`.

**Audio paths, registration, docs, verification**
- [ ] Note keywords from `scale`/`chord`/`voicing` reach every path unchanged: Playwright (TEST1 harness)
      `(scale :minor :c4 (seq 0 2 4 7))` → 4 onsets, `(arp :up (chord "Cm7"))` → 4, `(echo 3 1/8 0.6 (seq :cp))` → 3,
      `(voicing (seq "Dm7" "G7" "C^7"))` → 12; unit test: `midi/hz->midi (theory/note->hz :ds4)` = 63 (MIDI-out/export math);
      `git diff` touches no file under `app/src/repulse/audio*.cljs`, `env/builtins/midi.cljs` or `packages/audio`.
- [ ] Migration: tutorial chapter 7 and `README.md:32` are 0-based; no Hz wording left for `scale`/`chord`/`transpose` in
      `completions.js`, `hover.js`, `builtin_meta.edn`, `system_prompt.cljs`, `concepts.json`; USAGE.md and the PR body carry
      the TL5 breaking-change line for community snippets.
- [ ] Rule 3: the 16 new names in `BuiltinName` (+ `npm run gen:grammar`), `completions.js`, `hover.js`, `builtin_meta.edn`
      (+ `npm run gen:ai-docs`); both generators produce no diff after the commit; `tl5-documented-examples-evaluate` passes.
- [ ] `docs/USAGE.md`: rewritten Music theory sections, new `## Harmony` section (voicing, root-notes, scale-transpose,
      arp, arp-with, rolled) and the layering section, each new built-in with an example; README updated;
      STRUDEL-PARITY rows ✅ as listed in step 11 and Summary recomputed; ROADMAP TL5 → delivered.
- [ ] All existing tests pass (migrated ones as described); `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a `## Test protocol` section (CLAUDE.md Rule 5) covering at least: `voicing` of the
      `"<C^7 A7b13 Dm7 G7>"` progression (smooth, no jumps), `arp :up-down` and the index form, `rolled` strum,
      `echo`/`stut` decay by ear, `superimpose` octave layer, `jux-flip` sides swapping each cycle, `xfade` with
      `"<0 0.5 1>"`, `scale-transpose "<0 2>"`, `(add 12 (scale …))` an octave up, the warning cases
      (`(chord "Cmj7")` message, `(arp :sideways …)` message), the changed behaviour (`(scale :major :c4 (seq 1))` now
      plays D4; tutorial 7 sounds as before), MIDI out of a `voicing` pattern (if hardware available, else "not
      tested"), and regressions for `off`, `jux`, `(transpose 12 (seq :c4))` and a demo using `scale`. Values copied
      from the TEST1 harness as Rule 5 requires.

## What NOT to do

- No key detection or harmonic analysis UI (backlog "Harmonic trace"). No MIDI chord input: **SK3**. No new synth voices.
- No port of Strudel's voicing dictionaries (AGPL-3.0; REPuLse is MIT) and no custom-dictionary API
  (`setVoicingRange`, `addVoicings`, `registerVoicings`, `voicingAlias` stay ❓); no `dictionary`/`octaves` voicing
  options; no stateful (previous-chord) voice leading — selection must stay a pure function of each event.
- Don't change the meaning of plain numbers (they stay Hz everywhere) and don't touch the audio, MIDI or export code;
  no legacy 1-based `scale` flag; no `to-scale`, `stut-with`, `strans`/`scale-trans`/`trans` aliases (parity 🔁 rows).
- Don't change `off`, `jux`, `jux-by`, `amp`, `add` or any TL1–TL4 semantics; don't hand-edit `app/public/embed.js`.
- No `morph`, no SuperCollider pitch model (`degree`, `mtranspose`, `ctranspose`, …), no intervals inside chord
  symbols beyond the 34-quality table (e.g. `"C7b9#11"`), no `"6/9"` spelling (slash means bass; use `"69"`).
