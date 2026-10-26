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
(scale-transpose "<0 2>" :minor (seq 1 3 5))       ; transpose in scale steps
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
- **Scale degree base:** REPuLse's `scale` is 1-based and returns Hz; Strudel/Tidal are
  0-based and return notes. Tidal tutorials won't translate one-to-one until this is
  settled. Options: switch to 0-based (breaking), or add a Tidal-compatible variant.
  This is a user decision; ask before implementing.
- Voicing dictionary: port Strudel's `ireal`-style dictionary (check licence) or write a
  smaller own one.

## Definition of done

- [ ] `(arp :up (chord "Cm7"))` → MIDI 60 63 67 70 in order within one cycle.
- [ ] Chord-symbol parser covers at least the 30 most common symbols; table-driven tests.
- [ ] `(voicing (seq "Dm7" "G7" "C^7"))` moves each voice by ≤ 4 semitones between chords.
- [ ] `(echo 3 1/8 0.6 (seq :cp))` → 3 onsets with amps 1, 0.6, 0.36.
- [ ] `(scale-transpose 2 :minor (seq 1 3 5))` → degrees 3 5 7 in the same scale.
- [ ] Rule 3 registration; USAGE.md "Harmony" section; parity rows ✅.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.

## What NOT to do

- No key detection or harmonic analysis UI (backlog "Harmonic trace").
- No MIDI chord input: **SK3**.
- No new synth voices.
