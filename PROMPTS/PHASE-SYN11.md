# Phase SYN11 — Formant Voice & Sound-Effect Generators

> **Spec status:** outline — expand into a full spec before implementing. Take the sound
> list from the 🆕 SYN11 rows of
> [docs/SYNTHESIS-COVERAGE.md](../docs/SYNTHESIS-COVERAGE.md).
>
> **Automation:** 🤖👂 auto + human check — sweep direction, formant positions and
> determinism are measurable; whether a riser builds tension or wind sounds like wind
> needs a human listen.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** SYN8 (coloured noise, band-limited pulse), TL7a (event length and
parameter slots in the trigger). Shares the vowel table with TL7c.

## Goal

Two groups of sounds that are not instruments in the usual sense. **Formant voices**
imitate the human voice: a buzzing source through a few band-pass filters at vowel
positions gives choirs and vowel leads. **Sound-effect generators** are the transitions
and textures of electronic music: risers and downlifters, noise sweeps, zaps, sirens,
impacts, wind, rain, surf. Most of them move over the whole length of their event, which
no current voice can do: voices know their attack and decay, but not how long the event is.

```lisp
;; Before — only by loading samples
(seq :birds :space)

;; After (proposed)
(->> (chord :minor :a3) (synth :choir :vowel :a) (attack 0.3))
(->> (seq :a3 :e4 :c4 :e4) (synth :vox) (vowel (seq :a :o :i :e)))
(slow 4 (synth :riser (pure :c3)))                 ; rises for 4 cycles, then stops
(->> (seq :_ :_ :_ :c5) (synth :zap))
(track :air (->> (pure :c3) (synth :wind) (amp 0.3)))   ; the note sets the centre
```

## Background

- `packages/audio/src/lib.rs`: voices are one-shots with `attack` and `decay`; `Pending`
  has no duration field. Noise is white only until SYN8. `Biquad::bandpass`,
  `peaking_eq`, `highshelf` and `highpass` exist and are reused per voice.
- `app/src/repulse/audio.cljs`, `play-event`: receives `timing` (`{:dur :cycle-dur}`,
  added in TL6) but forwards it to the sample player only.
- TL7c plans `vowel` as a per-event formant *filter* (3–5 band-passes) on any sound.
  This phase adds the *source* that makes it sound like a voice, and uses the same vowel
  table so `:a` means the same formants in both.
- USAGE.md documents `(synth :noise)` on a rest pattern (`(seq :_ :_)`), since noise has
  no pitch. How unpitched voices are triggered is an open question below.
- Determinism: the engine seeds noise from `noise_seed` (LCG), so offline renders repeat.

## Scope

- **Formant voice:** `:vox` (one voice, patternable vowel, short glide between vowels)
  and `:choir` (unison of formant voices with slow attack). Vowels `:a :e :i :o :u`,
  voice types by formant set (`:soprano`, `:tenor`, `:bass`) if cheap.
- **Event-length voices:** `:riser`, `:downer`, `:sweep` (band-passed noise, moving
  centre), `:siren`. They take their duration from the event.
- **One-shots:** `:zap`, `:impact` (sub drop + noise burst), `:blip`, `:dial` (dual tone).
- **Textures:** `:wind` (band-passed pink noise, slowly wandering cutoff), `:rain`
  (dust impulses + filtered noise), `:surf` (brown noise with a slow swell), `:crackle`.
- **Engine:** event duration reaches the voice (new `Pending` field / TL7a slot);
  per-sample filter coefficient updates for sweeps stay allocation-free.
- Options per voice kept to two or three (`:from`, `:to`, `:curve` for sweeps).

## Open design questions

- How does the duration reach the engine: TL7a `trigger_v3` slot, or an extended value
  string? TL6 already computes `:dur` per event on the CLJS side.
- Sweeping a biquad per sample means recomputing coefficients (sin/cos per sample).
  Update every 16 or 32 samples with interpolation, or use a state-variable filter whose
  cutoff is cheap to change? The answer also serves TL7c filter envelopes.
- Are textures voices (`(synth :wind)`) or would a continuous background source per
  track fit better? Voices keep the pattern model intact; prefer them.
- Wandering parameters need slow random movement. Reuse the TL2 `perlin` idea inside the
  engine, seeded for reproducibility.
- Does `:choir` need SYN8 unison, or its own fixed 3-voice detune?
- Unpitched voices (`:wind`, `:rain`, `:impact`): triggered by any note (the note sets a
  centre frequency or is ignored), or by rests as `(synth :noise)` is documented today?
  Pick one rule for all unpitched voices and document it.

## Definition of done

- [ ] `(synth :vox :vowel :a)` vs `:vowel :i` on the same note: energy around the second formant (≈ 1100 Hz vs ≈ 2300 Hz) differs by ≥ 6 dB each way.
- [ ] `(vowel (seq :a :o))` on `:vox` changes the formants per event (same assertion per event).
- [ ] `:riser` over a 4 s event: the spectral centroid at 90 % of the event is ≥ 2× the centroid at 10 %; `:downer` is the reverse.
- [ ] `:riser` ends with its event: output is below −60 dBFS within 50 ms after the event end.
- [ ] `:zap`: the measured pitch falls by ≥ 2 octaves within the first 100 ms.
- [ ] `:impact`: ≥ 60 % of the energy in the first 500 ms lies below 150 Hz.
- [ ] `:wind`, `:rain`, `:surf` are non-silent, and two offline renders of each are bit-identical.
- [ ] `:surf` uses brown noise: octave-band levels fall 6 ± 2 dB per octave (long-term average).
- [ ] The render loop stays allocation-free, including coefficient updates.
- [ ] Rule 3 registration; USAGE.md "Voices & sound effects" section with one example each.
- [ ] Human listen check recorded in the PR.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## What NOT to do

- No speech synthesis, text-to-speech, consonants or lyrics. Vowels only.
- No vocoder (that is an effect on existing audio): **FX5**.
- No per-event `vowel` filter on arbitrary sounds: **TL7c**.
- No sample-based foley or field recordings; these are synthesized generators.
- No generic "automation over the event" language feature; sweeps are properties of
  these voices. Song-time automation is **SONG2**.
