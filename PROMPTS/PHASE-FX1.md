# Phase FX1 — Modulation & Stereo Effects

> **Spec status:** outline — expand into a full spec before implementing.
>
> **Automation:** 🤖👂 auto + human check — notch positions, sidebands and channel
> balance are measurable in offline renders; the musical character needs a human listen.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** none. All effects are track-level plugins built from native Web Audio nodes.

## Goal

Complete the modulation family and add stereo tools. REPuLse has chorus, phaser and
tremolo; the other standard members are missing: flanger, vibrato, ring modulator,
rotary speaker. There is also no effect that works on the stereo image: no auto-pan and
no width control.

```lisp
;; Before
(fx :chorus 0.5)  (fx :phaser 0.6)  (fx :tremolo 0.8)

;; After (proposed)
(fx :flanger :rate 0.25 :depth 0.7 :feedback 0.6)
(track :keys (->> (chord :minor7 :d3) (synth :epiano) (fx :vibrato :rate 5 :depth 30)))
(track :hats (->> (fast 8 (seq :hh)) (fx :auto-pan :rate 0.5)))
(fx :ring-mod :freq 220 :mix 0.5)
(track :organ (->> (chord :major :c3) (fx :rotary :speed :fast)))
(fx :width 1.4)                       ; 0 = mono, 1 = unchanged, 2 = extra wide
```

## Background

- Effect plugins: `app/public/plugins/*.js`, protocol in [docs/PLUGINS.md](../docs/PLUGINS.md)
  (`createNodes(ctx)` → `{inputNode, outputNode}`, `setParam`, `bypass`, `getParams`,
  `destroy`, `clone`, optional `ready`). Built-ins use a dry/wet gain pair for click-free
  bypass.
- `chorus.js` is the closest model: two `DelayNode`s whose `delayTime` is modulated by
  `OscillatorNode` → `GainNode`, with `startWhenReady` for a suspended context.
- Auto-loading: the URL list in `app/src/repulse/app.cljs` (after the compressor
  registration); order in that list is the default chain order documented in USAGE.md.
- Per-track chains clone the plugin (`clone()`); offline export (`(export …)`, EXP1)
  re-creates each effect in an `OfflineAudioContext` and needs a real `getParams()`.
- Names and ranges are also listed in `app/src/repulse/ui/slider_model.cljs` (slider
  ranges and the positional parameter), `lisp-lang/insert-categories.js` and
  `builtin_meta.edn` (the `fx` entry).
- STRUDEL-PARITY.md: `leslie` is undecided (🟡 TL7c row "chorus / leslie"); `ring*` and
  `fshift*` are ⛔ as per-event SuperDirt controls. Track-level plugins don't change that.

## Scope

- `flanger`: one modulated delay (0.5–10 ms) with feedback (positive and negative);
  `rate`, `depth`, `delay`, `feedback`, `wet`.
- `vibrato`: fully wet modulated delay; `rate`, `depth` (cents).
- `auto-pan`: LFO on a `StereoPannerNode`; `rate` (Hz), `depth`, `shape` (sine, triangle,
  square), `sync` (cycles) if the host exposes tempo.
- `ring-mod`: carrier oscillator on a `GainNode`'s gain; `freq`, `shape`, `mix`.
- `rotary`: crossover into horn and drum, each with amplitude, pan and short-delay
  (Doppler) modulation; `speed` (`:slow`/`:fast`/`:stop`) with acceleration between speeds.
- `width`: mid/side matrix with `ChannelSplitterNode`/`ChannelMergerNode`; `width` 0–2;
  optional `haas` (ms) for a delay-based widener.
- Default chain positions, slider ranges, completions and docs for all six.

## Open design questions

- Tempo sync: can a plugin read the cycle length through the host API (`init(host)`),
  and what happens on a BPM change? If not available, ship `rate` in Hz only and leave
  `sync` to a later phase.
- `width` on the master chain vs per track: tracks are summed into the master before the
  global chain; is the per-track signal already stereo at that point (panned voices)?
- `rotary`: how much of the classic sound comes from the cabinet? Reuse `cab` after it
  instead of modelling one.
- Feedback loops built from `DelayNode`s have a minimum loop delay of one render quantum
  (128 samples ≈ 2.9 ms at 44.1 kHz). A flanger needs shorter loops. Options: feed-forward
  only below 3 ms, or a small `AudioWorkletProcessor` (as `bitcrusher.js` does). Decide
  with a measurement.
- Does `ring-mod` settle the parity doc's ⛔ for `ring*`, or stay a separate track-level thing?

## Definition of done

- [ ] `flanger` with `depth 0`, `delay 0.002`, `wet 0.5`, no feedback, fed white noise: spectral notches at 250, 750 and 1250 Hz, each ≥ 10 dB below the neighbouring peaks.
- [ ] `ring-mod :freq 100 :mix 1` on a 440 Hz sine: peaks at 340 and 540 Hz; 440 Hz is ≥ 20 dB below them.
- [ ] `vibrato :rate 5 :depth 50` on a 440 Hz sine: the measured pitch swings ±50 cents (±15 %) at 5 Hz.
- [ ] `auto-pan :rate 1`: the left/right RMS ratio alternates with a 1 s period; total power stays within ±1.5 dB.
- [ ] `width 0`: left and right are identical (difference ≤ −60 dBFS); `width 1`: output equals input (null test ≤ −60 dBFS).
- [ ] `rotary :speed :fast` on a sustained chord: amplitude modulation between 5 and 8 Hz; `:slow` below 1.5 Hz.
- [ ] Each effect is transparent when bypassed (null test ≤ −60 dBFS) and at its default settings after auto-load (all are inactive by default).
- [ ] `(export …)` with each effect active renders offline (no fall-back to real-time capture).
- [ ] Each effect works per track: `(track :a (->> pat (fx :flanger 0.5)))` leaves other tracks dry.
- [ ] USAGE.md "Built-in effects" has a section per effect (parameter table + example); the effect count and chain order there are updated.
- [ ] Human listen check recorded in the PR.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## What NOT to do

- No per-event versions (`phaser`, `tremolo`, `vib` as pattern parameters): **TL7c**.
- No frequency shifter or pitch shifter: **FX5**.
- No ping-pong or tape delay: **FX4**.
- No reorderable or parallel chains: **P2**.
- No external DSP libraries; native nodes first, a hand-written worklet only where a
  measurement shows native nodes cannot do it.
