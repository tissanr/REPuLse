# Phase SYN8 — Band-Limited Oscillators, Unison & Noise Colours

> **Spec status:** outline — expand into a full spec before implementing.
>
> **Automation:** 🤖👂 auto + human check — aliasing, detune and noise slopes are
> measurable with FFT assertions; the change to the existing `:saw` and `:square` sound
> needs a human listen.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** none. Coordinates with TL7a (`trigger_v3` parameter slots).

## Goal

Fix and extend the oscillator core that every subtractive sound is built on. The WASM
`:saw` and `:square` voices are naive: they jump from +1 to −1 in one sample, which
aliases audibly on high notes and in sweeps. This phase makes them band-limited, adds the
missing basic waveforms as voices, adds unison (several detuned copies of one note) and
adds coloured noise.

```lisp
;; Before
(->> (seq :c7 :e7 :g7) (synth :saw))            ; harsh, inharmonic aliasing
(->> (seq :c4) (synth :tri))                    ; no such voice — plays a sine

;; After
(->> (seq :c7 :e7 :g7) (synth :saw))            ; clean
(->> (seq :c4 :eb4 :g4) (synth :tri))
(->> (chord :minor :a3) (synth :supersaw) (detune 18) (spread 0.8))
(->> (seq :a1 :a1 :c2 :e1) (synth :saw) (unison 3) (detune 12) (sub 0.5))
(->> (seq :_ :_ :_ :_) (synth :noise :color :pink) (decay 0.4))
```

## Background

- `packages/audio/src/lib.rs`
  - `Voice::Saw`: `2.0 * (phase % 1.0) - 1.0`. `Voice::Square`: `if p < pulse_width { 1 } else { -1 }`.
    Neither is band-limited. `Voice::Tone` is the sine; `Voice::Noise` is white LCG noise.
  - Voices are mono; `ActiveVoice { voice, pan }` pans them in `process_block_raw`.
  - Trigger strings: `"saw:<hz>"`, `"square:<hz>:<pw>"`, `"noise"`.
- `app/src/repulse/audio.cljs`: `play-event` dispatches on `:synth`; the JS fallbacks
  (`make-saw`, `make-square`, `make-pulse-wave`) use native `OscillatorNode`s, which are
  already band-limited, so only the WASM path aliases.
- `defsynth` (`app/src/repulse/synth.cljs`) has `sin saw square tri noise` UGens on native
  nodes. `tri` exists there but not as a `synth` voice.
- STRUDEL-PARITY.md lists `detune` / `unison` / `spread` as ❓ "synth-design scope; see
  SYN phases". This phase decides them.

## Scope

- **Band-limiting:** PolyBLEP correction for saw and pulse; triangle by integrating the
  band-limited square (or PolyBLAMP).
- **New voices:** `:tri`, `:sine` (explicit name for the default tone), `:pulse` (alias of
  `:square` with `:pw`), `:supersaw` (unison preset: 7 voices, default detune and spread).
- **Unison parameters** on saw, pulse, triangle: `unison` (1–7 voices), `detune` (cents,
  total spread), `spread` (0–1 stereo width), `sub` (level of a sine one octave below).
- **PWM:** `pw` as a patternable parameter, plus `pw-rate`/`pw-depth` for movement within
  a note.
- **Noise colours:** `(synth :noise :color …)` with `:white` (default), `:pink`
  (−3 dB/oct), `:brown` (−6 dB/oct), `:dust` (sparse random impulses), `:lfsr` (chip noise).
- JS fallback voices for the new names, so the TEST1 harness and a missing worklet still
  produce the right waveform class.

## Open design questions

- Unison needs stereo output per voice. Options: `Voice::tick` returns `(l, r)`; or a
  unison voice is expanded into N mono `ActiveVoice`s with their own pan at trigger time
  (no engine change, N × the voice count). Measure the cost of 7 × polyphony.
- Fixed phase arrays (`[f64; 7]`) keep the render path allocation-free. Random or fixed
  start phases? Random sounds wider but makes offline renders differ unless seeded from
  `noise_seed`.
- Does band-limiting change `:saw`/`:square` loudness or tone enough to break existing
  sessions? State the measured difference in the PR.
- Parameter transport: more colon fields in the trigger string, or TL7a slots.
- Names: `unison` vs Strudel `unison`, `detune`, `spread` (keep the parity names).

## Definition of done

- [ ] Saw at 3520 Hz (A7), 44.1 kHz, 1 s: energy in non-harmonic FFT bins below 10 kHz is ≥ 30 dB lower than the same measurement on the pre-phase oscillator (keep the naive version in the test as the reference).
- [ ] Same assertion for pulse at 50 % and 25 % width.
- [ ] `:tri` at 440 Hz: 3rd harmonic 19 ± 2 dB below the fundamental, even harmonics ≥ 40 dB below.
- [ ] `(unison 3) (detune 20)`: the FFT around the fundamental shows three peaks within ±12 cents of the note.
- [ ] `(spread 1)` with `unison 7`: left/right correlation < 0.9; `(spread 0)`: left and right identical.
- [ ] `(sub 0.5)`: a peak one octave below the note, 6 ± 2 dB below the fundamental.
- [ ] Pink noise: octave-band levels fall 3 ± 1 dB per octave between 100 Hz and 10 kHz; brown: 6 ± 1.5 dB.
- [ ] Two offline renders of the same unison pattern are bit-identical.
- [ ] The render loop stays allocation-free.
- [ ] Parity rows `detune` / `unison` / `spread` updated in STRUDEL-PARITY.md.
- [ ] Rule 3 registration; USAGE.md voice table and a "Unison" section with examples.
- [ ] Human listen check recorded in the PR: old vs new saw on a high melody, supersaw chord.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## What NOT to do

- No filter, filter envelope or instrument presets: **TL7a**, **TL7c**, **SYN10**.
- No hard sync or glide: **SYN10**.
- No wavetable oscillator (out of Milestone 1, see STRUDEL-PARITY.md).
- Don't touch the `defsynth` UGens; they already use band-limited native oscillators.
- Don't move allocation into the render path to make unison "dynamic".
