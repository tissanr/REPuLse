# Phase FX3 — Filters & Resonators

> **Spec status:** outline — expand into a full spec before implementing.
>
> **Automation:** 🤖👂 auto + human check — slopes, resonance peaks and comb spacing are
> measurable in offline renders; filter character at high resonance needs a human listen.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** none. Track-level plugins. Coordinates with TL7a/TL7c (per-event filters
use the plain biquad; this phase is about filter *models* and filters that react).

## Goal

More kinds of filter than the one 12 dB/oct biquad. The filter is the main tone control
of subtractive synthesis, and the models differ audibly: a 24 dB/oct ladder filter that
can self-oscillate, comb filters and tuned resonators that add pitch to noise and drums,
an envelope filter that opens with the playing level (auto-wah), and a wavefolder, which
adds harmonics where a filter removes them.

```lisp
;; Before
(fx :filter :freq 1200 :q 4)                    ; biquad, 12 dB/oct

;; After (proposed)
(track :bass (->> pat (fx :ladder :freq 400 :res 0.85 :drive 2)))
(track :hats (->> (fast 8 (seq :hh)) (fx :comb :note :a3 :feedback 0.8)))
(track :drums (->> pat (fx :resonator :chord [:d3 :a3 :f4] :decay 0.6 :wet 0.4)))
(track :clav (->> pat (fx :auto-wah :sens 0.7 :range 2500 :q 6)))
(track :lead (->> pat (fx :fold :amount 3)))
(fx :filter :type "peaking" :freq 900 :gain 5 :q 2)
```

## Background

- `app/public/plugins/filter.js`: one `BiquadFilterNode`; parameters `freq`, `q`, `type`
  with `"lowpass" "highpass" "bandpass" "notch"`. The node also supports `"lowshelf"`,
  `"highshelf"`, `"peaking"` and `"allpass"` and has a `gain` parameter; neither is exposed.
- Per-event filters come with TL7a (`lpf`, `hpf`, `bpf`, native `BiquadFilterNode` in JS,
  `Biquad` in Rust) and their envelopes with TL7c. They stay 12 dB/oct.
- Waveshaping exists as `(fx :waveshape …)` (lookup table, Chebyshev) and
  `(fx :distort …)`; FUTURE-FEATURES.md lists the wavefolder as a backlog effect.
- Worklet precedent: `app/public/worklets/bitcrusher-processor.js` and
  `dattorro-reverb-processor.js`, loaded by their plugins in `init(host)`, with `ready()`
  for offline export.
- A `DelayNode` inside a feedback loop is clamped to at least one render quantum
  (128 samples ≈ 2.9 ms at 44.1 kHz), so a comb filter built from native nodes cannot be
  tuned above about 344 Hz.
- Plugin protocol, auto-load list, slider ranges and docs: as described in PHASE-FX1.

## Scope

- `ladder`: four-pole low-pass with resonance up to self-oscillation and input drive;
  `freq`, `res` (0–1), `drive`, `mode` (`:lp24`, `:lp12`, `:bp`, `:hp` if cheap).
- `comb`: feedback comb tuned by `freq` or `note`; `feedback` (−0.99 … 0.99), `damp`, `wet`.
- `resonator`: up to 6 tuned resonators set by `chord` or `notes`; `decay`, `wet`.
- `auto-wah`: envelope follower driving a band-pass or low-pass cutoff; `sens`, `range`,
  `q`, `attack`, `release`, `direction` (up/down).
- `fold`: wavefolder with `amount` and `symmetry`, oversampled.
- `filter`: expose the four missing biquad types and `gain`.

## Open design questions

- `ladder`: a non-linear zero-delay-feedback model in a worklet (sounds right, most
  code), or four cascaded one-pole stages with a one-sample feedback delay (simple,
  detunes at high cutoff)? Pick by listening at `res 0.9`.
- `comb` and `resonator` need short feedback loops, so they need a worklet. One
  "resonators" processor for both?
- `auto-wah` can be all native (`WaveShaperNode` as rectifier → low-pass → gain →
  `filter.frequency`). Check that it renders identically offline.
- `fold`: a `WaveShaperNode` curve with `oversample = "4x"` vs a worklet. The existing
  `waveshape` plugin may only need a `:fold` curve generator.
- Are tempo-synced filter LFOs (`:lfo-rate`, `:lfo-depth` on `filter`/`ladder`) in scope,
  or left to the pattern layer (`(fx :filter :freq (range 300 2000 sine))`)? Check what
  patterned `fx` parameters already support.
- Tuning by note name: accept note keywords in `fx` parameters (`:note :a3`); verify the
  `fx` built-in passes keywords through.

## Definition of done

- [ ] `ladder :freq 1000 :res 0`: white noise through it is 24 ± 3 dB lower at 4 kHz than at 2 kHz (one octave, both well above the cutoff).
- [ ] `ladder :freq 1000 :res 0.9`: a peak at 1 kHz (±5 %) at least 12 dB above the level at 200 Hz.
- [ ] `ladder :res 1` with silent input after a click self-oscillates at the cutoff (±5 %) for ≥ 1 s, and output never exceeds ±1.5.
- [ ] `comb :freq 440 :feedback 0.9` on white noise: spectral peaks at 440, 880, 1320 Hz (each ±2 %).
- [ ] `comb :note :a4` tunes above the native-node limit: peak at 440 Hz, not ≈ 344 Hz.
- [ ] `resonator :notes [:a3 :e4]` on a single click: the tail contains 220 Hz and 329.6 Hz (±2 %), audible for `decay` seconds (−60 dB point within ±25 %).
- [ ] `auto-wah`: the spectral centroid of the output for a −6 dBFS saw is ≥ 1.5× the centroid for the same saw at −30 dBFS.
- [ ] `fold :amount 3` on a full-scale sine: the 3rd and 5th harmonics are each above −20 dB relative to the fundamental; `amount 0` is transparent.
- [ ] `(fx :filter :type "peaking" :freq 1000 :gain 6)`: +6 ± 1 dB at 1 kHz, within ±0.5 dB at 100 Hz.
- [ ] Every effect renders in `(export …)` offline and works in a per-track chain.
- [ ] USAGE.md sections with parameter tables and examples; FUTURE-FEATURES.md wavefolder row marked scheduled.
- [ ] Human listen check recorded in the PR: ladder sweep at high resonance, comb on noise.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## What NOT to do

- No per-event filter models or `ftype` selection: the parity row `ftype`/`fanchor` stays ❓.
- No vowel/formant filter: **TL7c**.
- No parametric EQ: **FX2**.
- No emulation of a named hardware filter; "ladder" is the topology.
- No external DSP libraries; hand-written worklet processors only.
