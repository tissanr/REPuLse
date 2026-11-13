# Phase SYN9 — Modal & Additive Voices: Mallets, Bells, Organ

> **Spec status:** outline — expand into a full spec before implementing. Take the
> instrument list from the 🆕 SYN9 rows of
> [docs/SYNTHESIS-COVERAGE.md](../docs/SYNTHESIS-COVERAGE.md).
>
> **Automation:** 🤖👂 auto + human check — partial frequencies and decay times are exact
> and testable with FFT assertions; instrument character needs a human listen.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** none. FX1 adds the rotary speaker that completes the organ sound.

## Goal

Two synthesis methods REPuLse does not have yet, each for a family of instruments the
existing engines reach poorly. **Modal synthesis** plays a struck object as a small set
of decaying sine partials at the object's own (inharmonic) frequency ratios: vibraphone,
glockenspiel, xylophone, kalimba, steel drum, tubular bells, glass, gong, timpani.
**Additive synthesis** sums harmonics at chosen levels: the drawbar organ and pipe organ.

```lisp
;; Before — only the two FM approximations
(->> (seq :c5 :e5 :g5) (synth :bell))
(->> (seq :c4 :e4 :g4) (synth :marimba))

;; After (proposed)
(->> (scale :major :c4 (seq 0 2 4 7)) (synth :vibes) (decay 2.5))
(->> (fast 2 (scale :pentatonic :c5 (seq 0 2 4 2))) (synth :kalimba))
(->> (seq :c3 :g2) (synth :timpani))
(->> (chord :minor7 :d3) (synth :organ :drawbars "888000000"))
(->> (chord :major :c3) (synth :organ :drawbars "888888888") (attack 0.01))
```

## Background

- `packages/audio/src/lib.rs`: the `Voice` enum has `Tone`, `Saw`, `Square`, `Noise`,
  `FM`, `KarplusStrong` and three drum voices. Presets follow one pattern: a `*_preset`
  function returning a tuple, an optional `*_body_filters` chain of `Biquad`s allocated
  at trigger time, a prefixed trigger string (`"fm:<preset>:<freq>"`).
- Existing mallets are FM presets: `:bell` (ratio √2, 1.2 s) and `:marimba` (ratio 3.5).
  FM gives one modulator ratio, so it cannot place three or more independent partials.
- Voices have an amplitude envelope with attack and exponential decay; there is no
  sustained gate, so an organ note needs the event length or a long `decay` (see SYN11
  and TL7c `sustain`/`hold` for the same question).
- Dispatch and registration are as in SYN6 (`audio.cljs` keyword sets, grammar,
  completions, `builtin_meta.edn`).

## Scope

- **`Voice::Modal`:** up to 8 partials, each with frequency ratio, level and decay time;
  fixed-size arrays; a short noise or impulse "strike" whose brightness follows
  `hardness`. Presets: `:vibes` (with motor tremolo), `:glock`, `:xylophone`, `:kalimba`,
  `:steel-drum`, `:tubular`, `:glass`, `:gong`, `:timpani`, `:tabla`.
- **`Voice::Additive`:** 9 drawbar partials (ratios 0.5, 1.5, 1, 2, 3, 4, 5, 6, 8) with
  levels 0–8, key click, optional percussion partial. Voices `:organ` (drawbars) and
  `:pipe-organ` (fixed ranks, slow attack).
- **Parameters:** `hardness` (0–1) for modal voices, `:drawbars "888000000"` as a `synth`
  option; `decay` scales all partial decays.
- Trigger strings `"modal:<preset>:<freq>"` and `"add:<preset>:<freq>:<drawbars>"`, or
  TL7a slots if available.

## Open design questions

- Partial tables: published modal ratios (free bars 1 : 2.76 : 5.40, tuned vibraphone
  bars 1 : 4 : 10, tuned xylophone bars 1 : 3, membranes 1 : 1.59 : 2.14 : 2.30) or
  values tuned by ear? The full spec
  records the source of every table.
- Do `:bell` and `:marimba` stay FM, or move to the modal voice under the same names?
  Changing them changes existing sessions.
- Organ notes should last as long as the event. Pass the event duration to the engine
  now, or ship with `decay`-controlled length and revisit when TL7c adds `sustain`?
- Is `hardness` worth a per-event parameter, or is a preset constant enough for a first version?
- Sine partials are implemented as oscillators with exponential gain (cheap, exact
  frequencies) or as two-pole resonators excited by the strike (more natural attack)?

## Definition of done

- [ ] Every preset renders non-silent output (RMS > 0.001 in the first 100 ms at 440 Hz).
- [ ] `:vibes` at 220 Hz: FFT peaks at 1×, 4× and 10× the fundamental (each ±3 %).
- [ ] `:glock` (untuned bar): a partial within ±3 % of 2.76× the fundamental, and none within ±3 % of 2×.
- [ ] `(synth :organ :drawbars "008000000")`: one peak at the fundamental; every other partial ≥ 40 dB below.
- [ ] `(synth :organ :drawbars "888000000")`: peaks at 0.5×, 1× and 1.5× the fundamental within 2 dB of each other.
- [ ] `decay 3` vs `decay 0.5` on `:tubular`: level at 1 s differs by ≥ 20 dB.
- [ ] `hardness 1` vs `hardness 0` on `:vibes`: energy above 3 kHz in the first 30 ms differs by ≥ 6 dB.
- [ ] The render loop stays allocation-free (fixed partial arrays, no `Vec` growth in `tick`).
- [ ] Rule 3 registration; USAGE.md gets "Mallets & bells" and "Organ" tables with examples.
- [ ] Human listen check recorded in the PR.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## What NOT to do

- No rotary speaker or organ vibrato/chorus scanner: **FX1**.
- No piano (struck strings with soundboard): **SYN5** and the high-fidelity backlog row.
- No user-editable partial tables or additive editor; presets only.
- No full tonewheel-organ emulation (leakage, crosstalk, foldback).
- Don't add FM presets here: **SYN6**.
