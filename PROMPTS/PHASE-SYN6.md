# Phase SYN6 — FM & Plucked-String Preset Expansion

> **Spec status:** outline — expand into a full spec before implementing. Take the
> instrument list from the 🆕 SYN6 rows of
> [docs/SYNTHESIS-COVERAGE.md](../docs/SYNTHESIS-COVERAGE.md).
>
> **Automation:** 🤖👂 auto + human check — presets are table entries and testable for
> non-silence and decay; whether a preset sounds like its instrument needs a human listen.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** SYN1 (Karplus-Strong engine), SYN2 (FM presets). No engine changes needed.

## Goal

Widen the instrument palette with the two engines that already exist. SYN1 and SYN2
proved that a preset is one row of numbers plus a body EQ chain. This phase adds the
winds, brass, keys and plucked strings that are still missing, fixes one documented
voice that does not exist, and makes an unknown voice name a warning instead of a
silent sine.

```lisp
;; Before — unknown voice names fall through to a plain sine, without a message
(->> (scale :minor :c4 (seq 0 2 4 7)) (synth :clarinet))
(->> (seq :c4 :e4) (synth :trumpet-cup))        ; documented in USAGE.md, plays a sine

;; After
(->> (scale :minor :c4 (seq 0 2 4 7)) (synth :clarinet))
(->> (seq :c2 :_ :g2 :_) (synth :bass-guitar) (amp 0.8))
(->> (chord :major :c4 (fast 2 (seq 0 2 4 2))) (synth :banjo))
(->> (seq :c4 :e4) (synth :clarinett))
;; => Warning: unknown synth voice :clarinett — did you mean :clarinet?
```

## Background

- `packages/audio/src/lib.rs`
  - `fm_preset(name)` → `(index, ratio, attack, decay, sustain, release)`;
    `fm_body_filters(name, sr)` → formant `Biquad` chain; `fm_noise_amp(name)` → breath
    noise level. Trigger string `"fm:<preset>:<freq>"`.
  - `ks_preset(name)` → `(feedback, brightness, pick_pos, vib_depth, vib_rate, excitation)`;
    `ks_body_filters(name, sr)`. Trigger string `"ks:<preset>:<freq>"`. The delay buffer
    is 2205 samples, so the lowest note is 20 Hz (bass presets fit).
  - Tests `fm_presets_all_produce_output` and `karplus_strong_all_presets_produce_output`
    loop over a literal preset list.
- `app/src/repulse/audio.cljs`, `play-event`: two literal keyword sets decide which
  `:synth` values go to `"fm:"` and `"ks:"`. Any other `:synth` value falls through to
  the note dispatch and plays `Voice::Tone` (sine).
- `app/src/repulse/sound_check.cljs` warns about unknown *sample* names after an
  evaluation; it does not look at `:synth`.
- Registration (Rule 3): synth names are in `BuiltinName` in `repulse-lisp.grammar`,
  `completions.js` and `builtin_meta.edn` (category `"synth"`).
- Existing drift: USAGE.md lists `:trumpet-cup` (not implemented) and omits
  `:synth-brass`, `:harmon-out`, `:harmon-in` (implemented).

## Scope

- **FM presets:** `:clarinet` (modulator ratio 2 → odd harmonics), `:oboe`, `:bassoon`,
  `:horn`, `:tuba`, `:pan-flute`, `:recorder`, `:whistle`, `:celesta`, `:music-box`,
  `:trumpet-cup`.
- **Karplus-Strong presets:** `:bass-guitar`, `:upright`, `:banjo`, `:ukulele`,
  `:harpsichord`, `:clav`, `:shamisen`, `:oud`, `:dulcimer`; `:sitar` only if the full
  spec finds a buzz-bridge model that fits the existing voice.
- **One shared preset list** used by both the Rust tests and the `audio.cljs` dispatch
  (today the names are repeated in four places).
- **Unknown voice warning** in `sound_check.cljs`: `(synth :name …)` with a name that is
  neither a built-in voice nor a `defsynth`, with a fuzzy-matched suggestion.
- **Docs:** complete USAGE.md voice tables, including the three undocumented presets.

## Open design questions

- Which instruments need more than the 2-operator FM voice can give (oboe and bassoon
  rely on strong formants)? Drop a preset rather than ship one nobody recognises.
- `:sitar`: add a nonlinearity field to `Voice::KarplusStrong`, or leave it to SYN4?
- Should the unknown-voice check be a warning (playback continues) or an eval error?
  Sample names are warnings; stay consistent unless there is a reason.
- Per-preset default `decay`: FM presets carry their own envelope, KS presets ignore
  `decay`. Document or unify?

## Definition of done

- [ ] Every new preset renders 0.1 s at 440 Hz with RMS > 0.001 (extend the two preset loops).
- [ ] `ks:bass-guitar:41.2` (E1) is non-silent and its FFT peak lies within ±5 % of 41.2 Hz.
- [ ] If the spec claims odd-harmonic character for `:clarinet`, a test asserts the 2nd harmonic is ≥ 10 dB below the 3rd at 220 Hz; otherwise no tonal claim is tested (CLAUDE.md "FM preset tests").
- [ ] `(->> (seq :c4) (synth :trumpet-cup))` triggers `"fm:trumpet-cup:…"`, not a sine.
- [ ] `(->> (seq :c4) (synth :clarinett))` shows `unknown synth voice :clarinett — did you mean :clarinet?` in the output line.
- [ ] A `defsynth` name passed to `synth` produces no warning.
- [ ] The preset names exist in exactly one list per language (Rust, CLJS); adding a preset touches no test code.
- [ ] USAGE.md voice tables list every preset the engine knows, each with one example.
- [ ] Rule 3 registration for every new name; `npm run gen:grammar` and `npm run gen:ai-docs` clean.
- [ ] Human listen check recorded in the PR: each preset played over two octaves.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## What NOT to do

- No new `Voice` variants or DSP models: bowed strings are **SYN3**, electric guitars
  **SYN4**, piano **SYN5**, mallets and organ **SYN9**.
- No per-event preset parameters (`pick-pos`, `tone`): **SYN4**.
- No sampled instruments.
- Don't retune the existing SYN1/SYN2 presets.
