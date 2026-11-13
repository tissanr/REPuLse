# Phase FX2 — Character, Filter Models & Pitch/Time Effects

> **Deferred (2026-11-13):** outside the four-milestone plan; revisit after M2. These
> effects add colour and special tricks. A song can be written and mixed without them
> (FX1 covers mixing and space), and most need hand-written worklets plus a human listen.
>
> **Spec status:** outline — expand into a full spec before implementing. Large on
> purpose; the full spec fixes the build order of parts A–E and may move the vocoder
> (Part E) behind P2.
>
> **Automation:** 🤖👂 auto + human check — slopes, resonance peaks, shifted frequencies
> and repeat lengths are measurable in offline renders; filter character, tape colour
> and the artefacts of pitch shifting need a human listen.

**Milestone:** none (deferred)
**Depends on:** FX1 (reverb for shimmer, delay modes for tape delay, the tempo answer
for synced effects). Part E (vocoder) depends on the audio buses of Phase P and may need
P2 routing.

## Goal

The effects that give a sound character or change audio that is already playing: filter
models beyond the 12 dB/oct biquad, tape and vinyl colour, a rotary speaker, dynamics
shaping, and a group that shifts pitch or captures and replays audio. Everything
REPuLse does to pitch and time today happens *before* the sound exists (`rate`, `chop`,
`stutter` work on samples and events); nothing can transpose a finished track, hold a
moment of it, or repeat the last beat of the mix.

```lisp
;; Before
(fx :filter :freq 1200 :q 4)                    ; biquad, 12 dB/oct
(->> (sound :breaks152 0) (speed 2))            ; pitch and speed are tied, samples only

;; After (proposed)
(track :bass (->> pat (fx :ladder :freq 400 :res 0.85 :drive 2)))
(track :hats (->> (fast 8 (seq :hh)) (fx :comb :note :a3 :feedback 0.8)))
(track :clav (->> pat (fx :auto-wah :sens 0.7 :range 2500 :q 6)))
(track :organ (->> (chord :major :c3) (fx :rotary :speed :fast)))
(master (fx :tape :drive 0.4 :wow 0.2 :hiss 0.05))
(track :lead (->> pat (fx :delay :mode :tape :sync 1/4 :wow 0.4 :feedback 0.6)))
(track :vox (->> pat (fx :pitch-shift :semitones 7 :mix 0.5)))
(fx :shimmer :decay 4 :pitch 12 :wet 0.3)
(master (fx :stutter :div 1/16 :on 1))          ; repeat the last 16th
(track :drums (->> pat (fx :grain :size 0.08 :spray 0.5 :density 12)))
```

## Background

- Plugin protocol, auto-load list, per-track `clone()`, offline export, slider ranges
  and docs: as described in PHASE-FX1.
- Worklet precedent: `app/public/worklets/bitcrusher-processor.js` and
  `dattorro-reverb-processor.js`, loaded by their plugins in `init(host)`, with
  `ready()` for offline export.
- `filter.js` is one 12 dB/oct `BiquadFilterNode`. Per-event filters (TL7a) and their
  envelopes (TL7c) use the same biquad.
- A `DelayNode` inside a feedback loop is clamped to at least one render quantum
  (128 samples ≈ 2.9 ms at 44.1 kHz), so a comb filter built from native nodes cannot
  be tuned above about 344 Hz.
- Waveshaping exists as `(fx :waveshape …)` (lookup table, Chebyshev) and
  `(fx :distort …)`. Lo-fi today: `bitcrusher` and the distortion family; no tape or
  vinyl character.
- All existing effects are memoryless or short-delay. None keeps an audio buffer longer
  than a delay line.
- Sample-level tools (TL6): `rate`/`speed`, `chop`, `striate`, `slice`, `splice`;
  USAGE.md states "pitch follows speed (no time-stretch)". Pattern-level repeats
  (`stutter`, `ply`, `echo`) re-trigger events; they do not capture audio.
- Buses (Phase P): `(bus :name :audio)`, `out`/`in` inside `defsynth`. The effect chain
  has no second input yet; P2 (deferred) plans `(fx :send …)`.
- STRUDEL-PARITY.md: `leslie` is undecided (🟡 TL7c row "chorus / leslie"); the
  spectral SuperDirt controls (`freeze`, `comb`, `smear`, `fshift*`, `octer*`, …) are ⛔
  as per-event controls. Track-level plugins leave those rows as they are.

## Scope

### Part A — Filter models

- `ladder`: four-pole low-pass with resonance up to self-oscillation and input drive;
  `freq`, `res` (0–1), `drive`, `mode` (`:lp24`, `:lp12`, `:bp`, `:hp` if cheap).
- `comb`: feedback comb tuned by `freq` or `note`; `feedback` (−0.99 … 0.99), `damp`, `wet`.
- `resonator`: up to 6 tuned resonators set by `chord` or `notes`; `decay`, `wet`.
- `auto-wah`: envelope follower driving a band-pass or low-pass cutoff; `sens`, `range`,
  `q`, `attack`, `release`, `direction`.

### Part B — Colour & dynamics

- `fold`: wavefolder with `amount` and `symmetry`, oversampled.
- `exciter`: high-passed copy → saturation → mixed back; `freq`, `amount`.
- `tape`: saturation, wow and flutter, high-frequency roll-off, `hiss`.
- `vinyl`: crackle and pops, rumble, band limiting, `age`.
- `delay :mode :tape`: `wow`, `flutter` and saturation inside the feedback loop.
- `rotary`: crossover into horn and drum, each with amplitude, pan and short-delay
  (Doppler) modulation; `speed` (`:slow`/`:fast`/`:stop`) with acceleration.
- `transient`: attack and sustain gain from the difference of a fast and a slow
  envelope follower; `attack`, `sustain` (−1 … 1).
- `multiband`: three bands split with crossover filters, one compressor each, summed;
  `low-freq`, `high-freq`, per-band `threshold`/`ratio`, `wet`.

### Part C — Pitch

- `pitch-shift`: ±24 semitones, `mix`, `window`; time-domain (two crossfaded delay
  readers) as the baseline.
- `octaver`: `sub` (−12), `sub2` (−24), `up` (+12) levels on top of the dry signal.
- `harmonizer`: two shifted voices at intervals in semitones.
- `freq-shift`: single-sideband shift in Hz (Hilbert pair + quadrature oscillator).
- `shimmer`: reverb with a pitch shifter in its feedback path.

### Part D — Buffer & time

- `freeze`: capture a short buffer and loop it with crossfades while `hold` is 1.
- `stutter`: capture the last `div` of a cycle and repeat it while `on` is 1.
- `reverse`: play the last `div` backwards, in tempo.
- `grain`: granular processor; `size`, `density`, `spray`, `pitch`, `pitch-spray`.

### Part E — Vocoder

- `vocoder`: band-pass bank + envelope followers; carrier = the track, modulator = a
  named audio bus; `bands`.

## Open design questions

- `ladder`: a non-linear zero-delay-feedback model in a worklet (sounds right, most
  code), or four cascaded one-pole stages with a one-sample feedback delay (simple,
  detunes at high cutoff)? Pick by listening at `res 0.9`.
- `comb` and `resonator` need short feedback loops, so they need a worklet. One
  "resonators" processor for both?
- `fold`: a `WaveShaperNode` curve with `oversample = "4x"`, or just a `:fold` curve
  generator for the existing `waveshape` plugin?
- Multiband crossovers: Linkwitz-Riley from two cascaded biquads per split so the bands
  sum flat. Verify the flat sum in a test.
- `rotary`: how much of the sound comes from the cabinet? Reuse `cab` after it instead
  of modelling one. Does it settle the parity doc's `leslie` row?
- Noise sources (`hiss`, `crackle`) must be deterministic for offline export: generate
  a seeded noise buffer once and loop it.
- Pitch-shift method: delay-line crossfade (cheap, low latency, warbles on chords) vs
  phase vocoder (clean on chords, FFT latency, much more code). Start with the
  delay-line method and state its limits in USAGE.md?
- One shared "buffer" worklet for `freeze`, `stutter`, `reverse` and `grain` (they all
  record a ring buffer and read it back differently)?
- `stutter` and `reverse` need the cycle clock inside the audio thread or exact
  scheduling from the main thread. Which is tight enough to stay on the grid?
- `hold` and `on` are meant to be switched in time. Patterned `fx` parameters, `tween`,
  or a per-event trigger? Check what `fx` supports by then.
- Latency: pitch shifting and look-back effects delay the track. Document it per effect;
  compensation is LIVE2's topic.
- Vocoder modulator input: wait for P2, or let the plugin read an audio bus through the
  host API? If neither is small, move the vocoder to its own phase.
- Tuning by note name (`:note :a3`, `:chord [...]`): verify the `fx` built-in passes
  keywords and lists through.

## Definition of done

**Part A**

- [ ] `ladder :freq 1000 :res 0`: white noise through it is 24 ± 3 dB lower at 4 kHz than at 2 kHz.
- [ ] `ladder :freq 1000 :res 0.9`: a peak at 1 kHz (±5 %) at least 12 dB above the level at 200 Hz.
- [ ] `ladder :res 1` with silent input after a click self-oscillates at the cutoff (±5 %) for ≥ 1 s, and output never exceeds ±1.5.
- [ ] `comb :freq 440 :feedback 0.9` on white noise: spectral peaks at 440, 880, 1320 Hz (each ±2 %); `comb :note :a4` peaks at 440 Hz, not at the native-node limit of ≈ 344 Hz.
- [ ] `resonator :notes [:a3 :e4]` on a single click: the tail contains 220 Hz and 329.6 Hz (±2 %), audible for `decay` seconds (−60 dB point within ±25 %).
- [ ] `auto-wah`: the spectral centroid of the output for a −6 dBFS saw is ≥ 1.5× the centroid for the same saw at −30 dBFS.

**Part B**

- [ ] `fold :amount 3` on a full-scale sine: the 3rd and 5th harmonics are each above −20 dB relative to the fundamental; `amount 0` is transparent.
- [ ] `tape :wow 1` on a 1 kHz sine: the measured pitch deviates by at least ±5 cents at a rate below 2 Hz; `:wow 0 :flutter 0 :drive 0 :hiss 0` is transparent (null test ≤ −60 dBFS).
- [ ] `vinyl :crackle 1` on silence produces impulses; two offline renders are bit-identical.
- [ ] `rotary :speed :fast` on a sustained chord: amplitude modulation between 5 and 8 Hz; `:slow` below 1.5 Hz.
- [ ] `transient :attack 1` on a drum loop raises the crest factor (peak ÷ RMS) by ≥ 2 dB; `:attack -1` lowers it.
- [ ] `multiband` with all ratios 1: output equals input within ±0.5 dB from 40 Hz to 16 kHz; `:low-ratio 8 :low-threshold -30` reduces a loud 60 Hz tone by ≥ 6 dB while a simultaneous 5 kHz tone stays within ±1 dB.

**Part C**

- [ ] `pitch-shift :semitones 12 :mix 1` on a 440 Hz sine: FFT peak at 880 Hz ± 3 %; `:semitones -12`: 220 Hz ± 3 %; output length unchanged.
- [ ] `pitch-shift :semitones 0` is transparent apart from its documented latency (null test after alignment ≤ −40 dBFS).
- [ ] `octaver :sub 1` on a 440 Hz sine: peaks at 220 and 440 Hz.
- [ ] `freq-shift :hz 100` on a signal with partials at 440 and 880 Hz: peaks at 540 and 980 Hz; the originals are ≥ 20 dB below them.
- [ ] `shimmer`: fed a 440 Hz burst, the tail contains 880 Hz and later 1760 Hz, each appearing after the one below it.

**Part D**

- [ ] `freeze :hold 1`: the output RMS stays within ±3 dB for 2 s after the input stops; `:hold 0` returns to the live signal within 50 ms.
- [ ] `stutter :div 1/8 :on 1` at 120 BPM: consecutive 250 ms segments of the output correlate ≥ 0.95.
- [ ] `reverse :div 1/4`: the output segment correlates ≥ 0.9 with the time-reversed input segment.
- [ ] `grain` with `spray 0`, `pitch 0`, overlapping grains: the output RMS is within ±3 dB of the input; two offline renders are bit-identical (seeded).

**Part E**

- [ ] `vocoder` with a noise carrier and a 1 kHz tone as modulator: output energy is concentrated in the band containing 1 kHz (≥ 12 dB above the other bands).

**All parts**

- [ ] The latency of every effect is stated in USAGE.md and matches a measurement (±1 ms).
- [ ] Every effect renders in `(export …)` offline and works in a per-track chain.
- [ ] USAGE.md sections with parameter tables and examples; FUTURE-FEATURES.md wavefolder and granular-processor rows marked done; SYNTHESIS-COVERAGE.md rows set to ✅.
- [ ] Human listen check recorded in the PR: ladder sweep at high resonance, comb on noise, tape delay on a lead, pitch shifter on a chord.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## What NOT to do

- No per-event versions of these effects, no per-event filter models or `ftype`
  selection (the parity row `ftype`/`fanchor` stays ❓).
- No time-stretching of samples without pitch change (the TL6 decision stands).
- No FFT bin effects (`smear`, `binshift`, brick-wall): ⛔ in the parity doc.
- No pitch correction / auto-tune, no formant-preserving shifting.
- No audio input (microphone) processing; effects act on REPuLse's own tracks.
- No emulation of a named hardware filter, tape machine or record player; "ladder" is
  the topology.
- No vowel/formant filter: **TL7c**. No EQ, limiter or gate: **FX1**.
- No external DSP libraries; hand-written worklet processors only.
