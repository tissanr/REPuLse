# Phase SYN7 — Instrument Library: Presets, Mallets, Organ, Voices & Sound Effects

> **Deferred (2026-11-13):** outside the four-milestone plan, like SYN3–SYN5; revisit
> after M2 together with them. A song can be written without these sounds (samples and
> the SYN6 instruments cover the roles), and every instrument here costs a human listen
> loop.
>
> **Spec status:** outline — expand into a full spec before implementing. Take the sound
> list from the SYN7 rows of [docs/SYNTHESIS-COVERAGE.md](../docs/SYNTHESIS-COVERAGE.md).
> Large on purpose; the full spec fixes the build order of parts A–D.
>
> **Automation:** 🤖👂 auto + human check — partial frequencies, decay times, formant
> positions and sweep directions are measurable; whether a preset sounds like its
> instrument needs a human listen.

**Milestone:** none (deferred)
**Depends on:** SYN6 (coloured noise, band-limited pulse, unison, the shared voice list).
Parts C and D: TL7a (event length and parameter slots in the trigger) and TL7c (the
vowel table). Part A needs nothing beyond SYN1 and SYN2.

## Goal

The wider instrument palette: more acoustic instruments from the two engines that
exist, two synthesis methods REPuLse does not have (modal and additive), a formant
voice, and generators for transitions and textures.

```lisp
;; Before — only by loading samples, or not at all
(->> (seq :c5 :e5 :g5) (synth :bell))             ; the one FM bell

;; After (proposed)
(->> (scale :minor :c4 (seq 0 2 4 7)) (synth :clarinet))
(->> (seq :c2 :_ :g2 :_) (synth :bass-guitar) (amp 0.8))
(->> (scale :major :c4 (seq 0 2 4 7)) (synth :vibes) (decay 2.5))
(->> (chord :minor7 :d3) (synth :organ :drawbars "888000000"))
(->> (chord :minor :a3) (synth :choir :vowel :a) (attack 0.3))
(slow 4 (synth :riser (pure :c3)))                ; rises for 4 cycles, then stops
(track :air (->> (pure :c3) (synth :wind) (amp 0.3)))
```

## Background

- `packages/audio/src/lib.rs`
  - `fm_preset(name)` → `(index, ratio, attack, decay, sustain, release)`;
    `fm_body_filters(name, sr)` → formant `Biquad` chain; `fm_noise_amp(name)` → breath
    noise level. `ks_preset(name)` → `(feedback, brightness, pick_pos, vib_depth,
    vib_rate, excitation)`; `ks_body_filters(name, sr)`. The KS delay buffer is 2205
    samples, so the lowest note is 20 Hz (bass presets fit).
  - Existing mallets are FM presets: `:bell` (ratio √2, 1.2 s) and `:marimba` (ratio
    3.5). FM gives one modulator ratio, so it cannot place three or more independent
    partials.
  - Voices are one-shots with `attack` and `decay`; `Pending` has no duration field, so
    a voice does not know how long its event is. `Biquad::bandpass`, `peaking_eq`,
    `highshelf` and `highpass` exist and are reused per voice.
- `app/src/repulse/audio.cljs`, `play-event`: receives `timing` (`{:dur :cycle-dur}`,
  added in TL6) but forwards it to the sample player only.
- TL7c plans `vowel` as a per-event formant *filter* on any sound. Part C adds the
  *source* that makes it sound like a voice and uses the same vowel table.
- USAGE.md documents `(synth :noise)` on a rest pattern, since noise has no pitch. How
  unpitched voices are triggered is an open question below.
- After SYN6: one shared voice list per language, unknown-voice warning, coloured noise,
  unison.

## Scope

### Part A — FM and plucked-string presets

- **FM:** `:clarinet` (modulator ratio 2 → odd harmonics), `:oboe`, `:bassoon`, `:horn`,
  `:tuba`, `:pan-flute`, `:recorder`, `:whistle`, `:celesta`, `:music-box`.
- **Karplus-Strong:** `:bass-guitar`, `:upright`, `:banjo`, `:ukulele`, `:harpsichord`,
  `:clav`, `:shamisen`, `:oud`, `:dulcimer`; `:sitar` only if the full spec finds a
  buzz-bridge model that fits the existing voice.

### Part B — Modal and additive voices

- **`Voice::Modal`:** up to 8 partials, each with frequency ratio, level and decay time;
  fixed-size arrays; a short noise or impulse "strike" whose brightness follows
  `hardness` (0–1). Presets: `:vibes` (with motor tremolo), `:glock`, `:xylophone`,
  `:kalimba`, `:steel-drum`, `:tubular`, `:glass`, `:gong`, `:timpani`, `:tabla`.
- **`Voice::Additive`:** 9 drawbar partials (ratios 0.5, 1.5, 1, 2, 3, 4, 5, 6, 8) with
  levels 0–8, key click, optional percussion partial. Voices `:organ`
  (`:drawbars "888000000"`) and `:pipe-organ` (fixed ranks, slow attack).

### Part C — Formant voice

- `:vox` (one voice, patternable `vowel`, short glide between vowels) and `:choir`
  (unison of formant voices with slow attack). Vowels `:a :e :i :o :u`; voice types by
  formant set (`:soprano`, `:tenor`, `:bass`) if cheap.

### Part D — Sound-effect generators

- **Event-length voices:** `:riser`, `:downer`, `:sweep` (band-passed noise, moving
  centre), `:siren`. They take their duration from the event; options `:from`, `:to`,
  `:curve`.
- **One-shots:** `:impact` (sub drop + noise burst), `:blip`, `:dial` (dual tone).
  (`:zap` is a SYN6 drum voice.)
- **Textures:** `:wind` (band-passed pink noise, slowly wandering cutoff), `:rain`
  (dust impulses + filtered noise), `:surf` (brown noise with a slow swell), `:crackle`.
- **Engine:** the event duration reaches the voice (new `Pending` field or TL7a slot);
  per-sample filter coefficient updates for sweeps stay allocation-free.

## Open design questions

- Which instruments need more than the 2-operator FM voice can give (oboe and bassoon
  rely on strong formants)? Drop a preset rather than ship one nobody recognises.
- `:sitar`: add a nonlinearity field to `Voice::KarplusStrong`, or leave it to SYN4?
- Modal partial tables: published ratios (free bars 1 : 2.76 : 5.40, tuned vibraphone
  bars 1 : 4 : 10, tuned xylophone bars 1 : 3, membranes 1 : 1.59 : 2.14 : 2.30) or
  values tuned by ear? The full spec records the source of every table.
- Do `:bell` and `:marimba` stay FM, or move to the modal voice under the same names?
  Changing them changes existing sessions.
- Sine partials as oscillators with exponential gain (cheap, exact frequencies) or as
  two-pole resonators excited by the strike (more natural attack)?
- Organ notes should last as long as the event. Use the event duration from Part D, or
  TL7c `sustain`?
- How does the duration reach the engine: TL7a `trigger_v3` slot, or an extended value
  string? TL6 already computes `:dur` per event on the CLJS side.
- Sweeping a biquad per sample means recomputing coefficients. Update every 16 or 32
  samples with interpolation, or use a state-variable filter whose cutoff is cheap to
  change? The answer also serves TL7c filter envelopes.
- Wandering parameters (`:wind`) need slow random movement. Reuse the TL2 `perlin` idea
  inside the engine, seeded for reproducibility.
- Unpitched voices (`:wind`, `:rain`, `:impact`): triggered by any note (the note sets a
  centre frequency or is ignored), or by rests as `(synth :noise)` is documented today?
  Pick one rule for all unpitched voices and document it.
- Implement together with SYN3–SYN5 as one instrument block, or before them?

## Definition of done

**Part A**

- [ ] Every new preset renders 0.1 s at 440 Hz with RMS > 0.001 (the preset loops take the names from the shared list).
- [ ] `ks:bass-guitar:41.2` (E1) is non-silent and its FFT peak lies within ±5 % of 41.2 Hz.
- [ ] If the spec claims odd-harmonic character for `:clarinet`, a test asserts the 2nd harmonic is ≥ 10 dB below the 3rd at 220 Hz; otherwise no tonal claim is tested (CLAUDE.md "FM preset tests").

**Part B**

- [ ] `:vibes` at 220 Hz: FFT peaks at 1×, 4× and 10× the fundamental (each ±3 %).
- [ ] `:glock` (untuned bar): a partial within ±3 % of 2.76× the fundamental, and none within ±3 % of 2×.
- [ ] `(synth :organ :drawbars "008000000")`: one peak at the fundamental; every other partial ≥ 40 dB below. `"888000000"`: peaks at 0.5×, 1× and 1.5× within 2 dB of each other.
- [ ] `decay 3` vs `decay 0.5` on `:tubular`: level at 1 s differs by ≥ 20 dB.
- [ ] `hardness 1` vs `hardness 0` on `:vibes`: energy above 3 kHz in the first 30 ms differs by ≥ 6 dB.

**Part C**

- [ ] `(synth :vox :vowel :a)` vs `:vowel :i` on the same note: energy around the second formant (≈ 1100 Hz vs ≈ 2300 Hz) differs by ≥ 6 dB each way.
- [ ] `(vowel (seq :a :o))` on `:vox` changes the formants per event.

**Part D**

- [ ] `:riser` over a 4 s event: the spectral centroid at 90 % of the event is ≥ 2× the centroid at 10 %; `:downer` is the reverse.
- [ ] `:riser` ends with its event: output is below −60 dBFS within 50 ms after the event end.
- [ ] `:impact`: ≥ 60 % of the energy in the first 500 ms lies below 150 Hz.
- [ ] `:wind`, `:rain`, `:surf` are non-silent, and two offline renders of each are bit-identical.
- [ ] `:surf` uses brown noise: octave-band levels fall 6 ± 2 dB per octave (long-term average).

**All parts**

- [ ] Every voice renders non-silent output (RMS > 0.001 in the first 100 ms at 440 Hz).
- [ ] The render loop stays allocation-free (fixed partial arrays, coefficient updates included).
- [ ] Rule 3 registration; USAGE.md voice tables ("Winds & brass", "Plucked strings", "Mallets & bells", "Organ", "Voices & sound effects") with one example each; SYNTHESIS-COVERAGE.md rows set to ✅.
- [ ] Human listen check recorded in the PR: each preset played over two octaves, each generator once.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## What NOT to do

- No bowed strings, electric guitars or piano: **SYN3**, **SYN4**, **SYN5**.
- No rotary speaker or organ vibrato/chorus scanner: deferred **FX2**.
- No vocoder (an effect on existing audio): deferred **FX2**.
- No speech synthesis, text-to-speech, consonants or lyrics. Vowels only.
- No per-event `vowel` filter on arbitrary sounds: **TL7c**.
- No sampled instruments, foley or field recordings.
- No user-editable partial tables, additive editor or full tonewheel-organ emulation.
- No generic "automation over the event" language feature; sweeps are properties of
  these voices. Song-time automation is **SONG2**.
- Don't retune the existing SYN1/SYN2 presets.
