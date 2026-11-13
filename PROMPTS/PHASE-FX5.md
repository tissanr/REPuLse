# Phase FX5 — Pitch, Time & Spectral Effects

> **Spec status:** outline — expand into a full spec before implementing. This is the
> largest of the FX phases; the full spec may split it (FX5a pitch, FX5b time/grain,
> FX5c vocoder).
>
> **Automation:** 🤖👂 auto + human check — shifted frequencies and repeat lengths are
> measurable in offline renders; artefacts of pitch shifting and granulation need a
> human listen.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** FX4 (shimmer extends the reverb). The vocoder depends on the audio
buses of Phase P and may need P2 routing.

## Goal

Effects that change pitch or time of audio that is already playing. Everything REPuLse
does to pitch and time today happens *before* the sound exists: `rate` and `speed`
resample a sample (pitch and length change together), `chop` and `stutter` rearrange
events. There is no way to transpose a finished track, hold a moment of it, or repeat the
last beat of the mix. This phase adds a pitch shifter and the effects built on it, a
buffer-based group (freeze, stutter, reverse, grains) and a vocoder.

```lisp
;; Before — pitch and speed are tied, and only for samples
(->> (sound :breaks152 0) (speed 2))

;; After (proposed)
(track :vox (->> pat (fx :pitch-shift :semitones 7 :mix 0.5)))     ; harmony a fifth up
(track :bass (->> pat (fx :octaver :sub 0.6)))
(fx :freq-shift :hz 35)                                             ; inharmonic shift
(fx :shimmer :decay 4 :pitch 12 :wet 0.3)
(track :pad (->> pat (fx :freeze :hold 1)))
(master (fx :stutter :div 1/16 :on 1))                              ; repeat the last 16th
(track :drums (->> pat (fx :grain :size 0.08 :spray 0.5 :density 12)))
(track :synth (->> pat (fx :vocoder :mod :voice-bus :bands 16)))
```

## Background

- Sample-level tools (TL6): `rate`/`speed`, `accelerate`, `chop`, `striate`, `slice`,
  `splice`, `loop-at`; USAGE.md states "pitch follows speed (no time-stretch)".
- Pattern-level repeats: `stutter`, `ply`, `echo`, `stut`. They re-trigger events; they do
  not capture audio.
- All existing effects are memoryless or short-delay. None keeps an audio buffer longer
  than a delay line.
- Worklet precedent: `app/public/worklets/bitcrusher-processor.js`,
  `dattorro-reverb-processor.js`; plugins load them in `init(host)` and expose `ready()`
  for the offline export.
- Buses (Phase P): `(bus :name :audio)`, `out`/`in` inside `defsynth`. The effect chain
  has no way to take a second input yet; P2 (deferred) plans `(fx :send …)`.
- FUTURE-FEATURES.md lists a granular processor as a backlog effect.
- STRUDEL-PARITY.md marks SuperDirt's spectral controls (`freeze`, `comb`, `smear`,
  `binshift`, `fshift*`, `octer*`, …) ⛔ as per-event controls. This phase adds some of
  those ideas as track-level effects and leaves the per-event rows as they are.

## Scope

- `pitch-shift`: ±24 semitones, `mix`, `window`; time-domain (two crossfaded delay
  readers) as the baseline.
- `octaver`: `sub` (−12), `sub2` (−24), `up` (+12) levels on top of the dry signal.
- `harmonizer`: two shifted voices at intervals in semitones.
- `freq-shift`: single-sideband shift in Hz (Hilbert pair + quadrature oscillator).
- `shimmer`: reverb with a pitch shifter in its feedback path.
- `freeze`: capture a short buffer and loop it with crossfades while `hold` is 1.
- `stutter`: capture the last `div` of a cycle and repeat it while `on` is 1.
- `reverse`: play the last `div` backwards, in tempo.
- `grain`: granular processor; `size`, `density`, `spray` (position jitter), `pitch`,
  `pitch-spray`.
- `vocoder`: band-pass bank + envelope followers; carrier = the track, modulator = a
  named audio bus.

## Open design questions

- Pitch-shift method: delay-line crossfade (cheap, low latency, warbles on chords) vs
  phase vocoder (clean on chords, FFT latency, much more code). Start with the delay-line
  method and state its limits in USAGE.md?
- One shared "buffer" worklet for `freeze`, `stutter`, `reverse` and `grain` (they all
  record a ring buffer and read it back differently)?
- `stutter`, `reverse` and `sync` need the cycle clock inside the audio thread or exact
  scheduling from the main thread. Which is tight enough to stay on the grid?
- `hold` and `on` are meant to be switched in time. Patterned `fx` parameters, `tween`,
  or a per-event trigger? Check what `fx` supports today.
- Latency: pitch shifting and look-back effects delay the track. Document it per effect;
  compensation is LIVE2's topic.
- Vocoder modulator input: wait for P2, or let the plugin read an audio bus through the
  host API? If neither is small, move the vocoder to its own phase.
- Does anything here need Rust/WASM for speed, or are JS worklets enough (as for
  `bitcrusher` and `dattorro-reverb`)?

## Definition of done

- [ ] `pitch-shift :semitones 12 :mix 1` on a 440 Hz sine: FFT peak at 880 Hz ± 3 %; `:semitones -12`: 220 Hz ± 3 %; output length unchanged.
- [ ] `pitch-shift :semitones 0` is transparent apart from its documented latency (null test after alignment ≤ −40 dBFS).
- [ ] `octaver :sub 1` on a 440 Hz sine: peaks at 220 and 440 Hz.
- [ ] `freq-shift :hz 100` on a signal with partials at 440 and 880 Hz: peaks at 540 and 980 Hz; the originals are ≥ 20 dB below them.
- [ ] `shimmer`: fed a 440 Hz burst, the tail contains 880 Hz and later 1760 Hz, each appearing after the one below it.
- [ ] `freeze :hold 1`: the output RMS stays within ±3 dB for 2 s after the input stops; `:hold 0` returns to the live signal within 50 ms.
- [ ] `stutter :div 1/8 :on 1` at 120 BPM: consecutive 250 ms segments of the output correlate ≥ 0.95.
- [ ] `reverse :div 1/4`: the output segment correlates ≥ 0.9 with the time-reversed input segment.
- [ ] `grain` with `spray 0`, `pitch 0`, overlapping grains: the output RMS is within ±3 dB of the input; two offline renders are bit-identical (seeded).
- [ ] `vocoder` with a noise carrier and a 1 kHz tone as modulator: output energy is concentrated in the band containing 1 kHz (≥ 12 dB above the other bands).
- [ ] The latency of every effect is stated in USAGE.md and matches a measurement (±1 ms).
- [ ] Every effect renders in `(export …)` offline and works in a per-track chain.
- [ ] Human listen check recorded in the PR.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## What NOT to do

- No time-stretching of samples without pitch change (the TL6 decision stands).
- No FFT bin effects (`smear`, `binshift`, brick-wall): ⛔ in the parity doc.
- No pitch correction / auto-tune, no formant-preserving shifting.
- No audio input (microphone) processing; effects act on REPuLse's own tracks.
- No per-event versions of these effects.
- No external DSP libraries; hand-written worklet processors only.
