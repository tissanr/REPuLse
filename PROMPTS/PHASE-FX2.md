# Phase FX2 — EQ & Dynamics

> **Spec status:** outline — expand into a full spec before implementing.
>
> **Automation:** 🤖👂 auto + human check — gains, ceilings and thresholds are exact and
> testable in offline renders; attack/release feel and "does the mix sound better" need a
> human listen.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** none. Track-level plugins.

## Goal

The mixing tools. REPuLse can compress and duck, but it cannot equalise a track, stop the
output from clipping, or shape the attack of a drum. Today the engine clamps the output
at ±1, so a dense mix distorts harshly instead of being limited. This phase adds a
parametric EQ, a limiter, a noise gate, a transient shaper and a multiband compressor.

```lisp
;; Before — one filter type per track, hard clipping at the output
(fx :filter :freq 500 :type "highpass")
(fx :compressor :threshold -18 :ratio 6)

;; After (proposed)
(track :bass (->> pat (fx :eq :low 3 :low-freq 80 :mid -4 :mid-freq 400 :mid-q 1.5)))
(master
  (fx :eq :high 2 :high-freq 8000)
  (fx :limiter :ceiling -1))
(track :snare (->> pat (fx :gate :threshold -35 :release 0.08)))
(track :drums (->> pat (fx :transient :attack 0.6 :sustain -0.3)))
(fx :multiband :low-ratio 4 :mid-ratio 2 :high-ratio 3)
```

## Background

- `app/src/repulse/plugins/compressor.cljs`: the one ClojureScript plugin, a
  `DynamicsCompressorNode` with dry/wet. `app/public/plugins/sidechain.js`: gain ducking
  driven by the event schedule, not by the audio level.
- `app/public/plugins/filter.js`: one `BiquadFilterNode` with `freq`, `q`, `type`
  (low-pass, high-pass, band-pass, notch). Shelving and peaking types exist in the node
  but are not exposed.
- `packages/audio/src/lib.rs`, `process_block_raw`: the stereo sum is `.clamp(-1.0, 1.0)`.
  The Web Audio destination clips again after the effect chain.
- `app/public/plugins/bitcrusher.js` shows how a plugin loads an `AudioWorkletProcessor`
  (`app/public/worklets/bitcrusher-processor.js`) in `init(host)` and keeps the promise
  for the offline context (`ready()`).
- Plugin protocol, auto-load list, slider ranges and docs: as described in PHASE-FX1.
- `(master …)` (WS2) groups global `fx` calls; per-track `fx` works for any plugin with
  `clone()`.

## Scope

- `eq`: low shelf, two peaking bands, high shelf (`BiquadFilterNode`s in series);
  `low`/`low-freq`, `mid`/`mid-freq`/`mid-q`, `mid2…`, `high`/`high-freq`, gains in dB.
  Flat by default.
- `limiter`: output ceiling in dBFS with a short look-ahead; `ceiling`, `release`.
  Recommended as the last master effect; decide whether it is on by default.
- `gate`: level-driven gate/expander; `threshold`, `attack`, `hold`, `release`, `range`.
- `transient`: attack and sustain gain from the difference of a fast and a slow envelope
  follower; `attack`, `sustain` (−1 … 1).
- `multiband`: three bands split with crossover filters, one compressor each, summed;
  `low-freq`, `high-freq`, per-band `threshold`/`ratio`, `wet`.
- Optional: expose `"lowshelf"`, `"highshelf"`, `"peaking"`, `"allpass"` on `filter`
  (if not done in FX3).

## Open design questions

- Limiter implementation: `DynamicsCompressorNode` (ratio 20, fast attack) is native but
  overshoots; a true ceiling needs look-ahead in an `AudioWorkletProcessor`. The DoD
  below requires the ceiling, so a worklet is likely.
- Gate and transient shaper need an envelope follower. Native trick
  (`WaveShaperNode` |x| → low-pass → `AudioParam`) or one shared "dynamics" worklet with
  modes? One worklet file is less code to keep offline-compatible.
- Should a limiter sit on the master output by default (inactive effects cost nothing,
  but an always-on limiter changes every existing session's loudness)?
- Multiband crossovers: Linkwitz-Riley from two cascaded biquads per split so the bands
  sum flat. Verify the flat sum in a test.
- The Rust engine's `.clamp(-1.0, 1.0)` happens before the chain. Remove it (float audio
  can exceed 1.0 and the limiter handles it) or keep it as a safety net?

## Definition of done

- [ ] `eq :low 6 :low-freq 100`: a 50 Hz sine comes out 6 ± 1 dB louder, a 5 kHz sine within ±0.5 dB.
- [ ] `eq :mid -6 :mid-freq 1000 :mid-q 2`: 1 kHz is 6 ± 1 dB quieter, 250 Hz and 4 kHz within ±1 dB.
- [ ] `eq` with defaults is transparent (null test ≤ −60 dBFS).
- [ ] `limiter :ceiling -3` on a signal peaking at +6 dBFS: no output sample above −2.5 dBFS; a signal peaking at −10 dBFS passes within ±0.5 dB.
- [ ] `gate :threshold -40`: noise at −50 dBFS is attenuated by ≥ 30 dB; a −20 dBFS tone passes within ±1 dB.
- [ ] `transient :attack 1` on a drum loop raises the crest factor (peak ÷ RMS) by ≥ 2 dB; `:attack -1` lowers it.
- [ ] `multiband` with all ratios 1: output equals input within ±0.5 dB from 40 Hz to 16 kHz (flat band sum).
- [ ] `multiband :low-ratio 8 :low-threshold -30`: a loud 60 Hz tone is reduced by ≥ 6 dB while a simultaneous 5 kHz tone stays within ±1 dB.
- [ ] Every effect renders in `(export …)` offline and works in a per-track chain.
- [ ] USAGE.md sections with parameter tables and examples; a short "Mixing" example using `eq` + `limiter` in `(master …)`.
- [ ] Human listen check recorded in the PR: a full mix with and without the limiter.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## What NOT to do

- No audio-keyed sidechain compression between tracks (needs routing): **P2**.
- No de-esser, no loudness (LUFS) metering or normalisation.
- No graphical EQ curve editor; parameters and the existing sliders only.
- No per-event dynamics: **TL7c** decides `compressor`/`duck` per event.
- Don't change `compressor` or `sidechain` behaviour.
