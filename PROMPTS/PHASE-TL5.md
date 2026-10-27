# Phase TL5 — Layering, Pitch & Harmony

> **Spec status:** outline — expand into a full spec before implementing. Take the exact
> function list from the TL5 rows of [docs/STRUDEL-PARITY.md](../docs/STRUDEL-PARITY.md).
>
> **Automation:** 🤖 auto — layering and harmony produce exact event/value lists
> (note numbers, chord tones) testable in `cljs.test`.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL1 (pattern arithmetic for transposition); TL3 for `echo` timing helpers

## Goal

Layer variations of a pattern on itself, and work with harmony musically: arpeggios,
chord symbols, voicings and scale-aware transposition.

```lisp
;; Before
(chord :minor7 :c4)
(off 1/8 (fast 2) pat)

;; After (proposed)
(superimpose (comp (fast 2) (add 12)) (seq :c3 :e3 :g3))
(echo 3 1/8 0.6 (seq :cp))                       ; 3 repeats, 1/8 apart, decaying
(arp :up-down (chord "Cm7"))                       ; chord symbols as strings
(voicing (seq "C^7" "A7b13" "Dm7" "G7"))           ; smooth voice leading
(scale-transpose "<0 2>" :minor (seq 0 2 4))       ; transpose in scale steps
```

## Background

- `packages/core/src/repulse/theory.cljs`: `note->midi`, `midi->hz`, `scale`, `chord`,
  `transpose`; named scale and chord interval tables.
- `off`, `jux`, `jux-by`, `stack` exist; `comp` composes transformers.

## Scope

- Layering: `superimpose`, `layer`, `echo`, `echo-with` (Tidal `stutWith`), `stut`
  alias policy per parity doc.
- Harmony: chord-symbol parser (`"Cm7"`, `"F#7b9"`, `"C^7"`), `arp` with modes
  (`:up :down :up-down :converge :diverge :thumb-up …`), `voicing` with a voice-leading
  dictionary, `root-notes`, `scale-transpose`, `to-scale`, more scales/modes.
- Note-name arithmetic consistency with TL1's `add` on note keywords.

## Open design questions

- Chord symbol input: strings (`"Cm7"`) vs keywords (`:Cm7`). Recommend strings, since
  keywords can't hold `^` or `#` cleanly.
- ~~Scale degree base~~ **Decided (2026-10-26):** `scale` becomes **0-based** (degree 0 =
  root, negative degrees go below it) and returns **notes** (MIDI-note values that the
  audio layer converts to Hz), not Hz. This matches Tidal/Strudel, and makes
  `(add 12 (scale …))` an octave rather than +12 Hz. It is a deliberate breaking change:
  - Update `theory/scale` and its tests, plus `chord`/`transpose` if they rely on Hz.
  - Make sure every audio path (WASM, JS fallback, MIDI out, `midi-export`) accepts note values.
  - Migrate bundled demos (`content/demos.cljs`), tutorial chapters, USAGE.md and README
    examples to 0-based degrees.
  - Community snippets (S3) can't be migrated automatically: add a one-line note to the
    release notes and USAGE.md.
- ~~Voicing dictionary~~ **Decided (2026-10-27):** write a **small own voicing dictionary**
  (MIT-clean). Do **not** port Strudel's `ireal`-style dictionary: Strudel is AGPL-3.0 and
  REPuLse is MIT. Keep the dictionary to what the chord-symbol parser covers and pick
  voicings by nearest voice leading to satisfy the ≤ 4-semitone criterion below.

## Definition of done

- [ ] `(arp :up (chord "Cm7"))` → MIDI 60 63 67 70 in order within one cycle.
- [ ] Chord-symbol parser covers at least the 30 most common symbols; table-driven tests.
- [ ] `(voicing (seq "Dm7" "G7" "C^7"))` moves each voice by ≤ 4 semitones between chords.
- [ ] `(echo 3 1/8 0.6 (seq :cp))` → 3 onsets with amps 1, 0.6, 0.36.
- [ ] `(scale :minor :c4 (seq 0 2 4 7))` → MIDI 60 63 67 72; `(seq -1)` → 58.
- [ ] `(scale-transpose 2 :minor (seq 0 2 4))` → degrees 2 4 6 in the same scale.
- [ ] `(add 12 (scale :minor :c3 (seq 0)))` → MIDI 60 (one octave above C3).
- [ ] Rule 3 registration; USAGE.md "Harmony" section; parity rows ✅.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.

## What NOT to do

- No key detection or harmonic analysis UI (backlog "Harmonic trace").
- No MIDI chord input: **SK3**.
- No new synth voices.
