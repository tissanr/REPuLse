# Phase FX1 — Mixing, Space & Modulation Effects

> **Spec status:** outline — expand into a full spec before implementing. This phase is
> large on purpose: it holds every effect a finished song needs in one phase. The full
> spec fixes the build order of parts A–C; each part is testable on its own.
>
> **Automation:** 🤖👂 auto + human check — gains, ceilings, decay times, echo spacing,
> notch positions and channel balance are measurable in offline renders; attack/release
> feel, room character and "does the mix sound better" need a human listen.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** none. All effects are track-level plugins. Coordinates with TL7b
(per-event sends reuse the same reverb and delay building blocks per orbit).

## Goal

The effects a song needs to be mixed and placed in a room. REPuLse can compress and
duck, but it cannot equalise a track or stop the output from clipping: the engine clamps
at ±1, so a dense mix distorts harshly. The reverb has one control (the wet level) and
the delay takes its time in seconds only and repeats in mono. The modulation family
stops at chorus, phaser and tremolo, and nothing works on the stereo image.

```lisp
;; Before
(fx :filter :freq 500 :type "highpass")
(fx :reverb 0.4)                                   ; no size, no decay
(fx :delay :wet 0.4 :time 0.25 :feedback 0.5)      ; seconds, mono

;; After (proposed)
(track :bass (->> pat (fx :eq :low 3 :low-freq 80 :mid -4 :mid-freq 400 :mid-q 1.5)))
(track :snare (->> pat (fx :gate :threshold -35 :release 0.08)))
(master
  (fx :eq :high 2 :high-freq 8000)
  (fx :limiter :ceiling -1))

(fx :reverb :type :hall :decay 3.5 :damp 0.4 :predelay 0.02 :wet 0.3)
(track :snare (->> pat (fx :reverb :type :gated :decay 0.4 :wet 0.6)))
(fx :delay :sync 3/16 :mode :ping-pong :feedback 0.5 :wet 0.35)

(fx :flanger :rate 0.25 :depth 0.7 :feedback 0.6)
(track :hats (->> (fast 8 (seq :hh)) (fx :auto-pan :rate 0.5)))
(fx :width 1.4)                       ; 0 = mono, 1 = unchanged, 2 = extra wide
```

## Decisions already made (2026-11-13)

- **The reverb size parameter is `size`, not `room`.** USAGE.md showed
  `(fx :reverb :wet 0.4 :room 0.8)`, but `reverb.js` ignores `room`; the example was
  corrected on 2026-11-13. `room` stays free for the per-event send level of TL7b (the
  Strudel meaning).

## Background

- Effect plugins: `app/public/plugins/*.js`, protocol in [docs/PLUGINS.md](../docs/PLUGINS.md)
  (`createNodes(ctx)` → `{inputNode, outputNode}`, `setParam`, `bypass`, `getParams`,
  `destroy`, `clone`, optional `ready`). Built-ins use a dry/wet gain pair for
  click-free bypass.
- Auto-loading: the URL list in `app/src/repulse/app.cljs` (after the compressor
  registration); order in that list is the default chain order documented in USAGE.md.
- Per-track chains clone the plugin (`clone()`); offline export (`(export …)`, EXP1)
  re-creates each effect in an `OfflineAudioContext` and needs a real `getParams()`.
- Names and ranges are also listed in `app/src/repulse/ui/slider_model.cljs` (slider
  ranges and the positional parameter), `lisp-lang/insert-categories.js` and
  `builtin_meta.edn` (the `fx` entry).
- Worklet precedent: `app/public/plugins/bitcrusher.js` loads
  `app/public/worklets/bitcrusher-processor.js` in `init(host)` and keeps the promise
  for the offline context (`ready()`).
- Dynamics today: `app/src/repulse/plugins/compressor.cljs` (`DynamicsCompressorNode`
  with dry/wet) and `sidechain.js` (gain ducking driven by the event schedule).
  `packages/audio/src/lib.rs`, `process_block_raw`: the stereo sum is
  `.clamp(-1.0, 1.0)`; the Web Audio destination clips again after the effect chain.
- `filter.js`: one `BiquadFilterNode` with `freq`, `q`, `type` (`"lowpass" "highpass"
  "bandpass" "notch"`). The node also supports `"lowshelf"`, `"highshelf"`, `"peaking"`,
  `"allpass"` and a `gain` parameter; neither is exposed.
- `reverb.js`: `ConvolverNode` with one procedurally generated impulse response;
  `setParam` knows `wet` and `dry` only. `dattorro-reverb.js` is a separate plate reverb
  with `decay`, `damping`, `predelay`.
- `delay.js`: one `DelayNode` with a feedback `GainNode`; `time` (seconds), `feedback`
  (max 0.95), `wet`.
- `chorus.js` is the model for modulated delays: two `DelayNode`s whose `delayTime` is
  modulated by `OscillatorNode` → `GainNode`, with `startWhenReady` for a suspended
  context.
- A `DelayNode` inside a feedback loop is clamped to at least one render quantum
  (128 samples ≈ 2.9 ms at 44.1 kHz).
- TL7b plans one convolver reverb and one feedback delay per orbit for per-event sends;
  TL7c plans `delay-sync`.
- STRUDEL-PARITY.md: `ring*` is ⛔ as a per-event SuperDirt control. A track-level
  plugin doesn't change that.

## Scope

### Part A — Mixing

- `eq`: low shelf, two peaking bands, high shelf (`BiquadFilterNode`s in series);
  `low`/`low-freq`, `mid`/`mid-freq`/`mid-q`, `mid2…`, `high`/`high-freq`, gains in dB.
  Flat by default.
- `limiter`: output ceiling in dBFS with a short look-ahead; `ceiling`, `release`.
- `gate`: level-driven gate/expander; `threshold`, `attack`, `hold`, `release`, `range`.
- `filter`: expose `"lowshelf"`, `"highshelf"`, `"peaking"`, `"allpass"` and `gain`.

### Part B — Space

- `reverb`: `type` (`:room`, `:hall`, `:plate`, `:spring`, `:gated`, `:reverse`),
  `decay` (seconds), `size`, `damp`, `predelay`. Each type is an impulse-response
  generator, so the effect stays one `ConvolverNode`; the response is regenerated when a
  parameter changes. `(fx :reverb 0.4)` keeps its current sound.
- `delay`: `sync` (cycles, e.g. `3/16`), `mode` (`:mono`, `:stereo`, `:ping-pong`),
  `taps` (list of tap positions), `lowcut`/`highcut` in the feedback path.

### Part C — Modulation & stereo

- `flanger`: one modulated delay (0.5–10 ms) with feedback (positive and negative);
  `rate`, `depth`, `delay`, `feedback`, `wet`.
- `vibrato`: fully wet modulated delay; `rate`, `depth` (cents).
- `auto-pan`: LFO on a `StereoPannerNode`; `rate` (Hz), `depth`, `shape`, `sync`.
- `ring-mod`: carrier oscillator on a `GainNode`'s gain; `freq`, `shape`, `mix`.
- `width`: mid/side matrix with `ChannelSplitterNode`/`ChannelMergerNode`; `width` 0–2;
  optional `haas` (ms) for a delay-based widener.

Default chain positions, slider ranges, completions and docs for every new effect.

## Open design questions

- Tempo: can a plugin read the cycle length through the host API (`init(host)`), and
  what happens on a BPM change? If not, the `fx` built-in converts cycles to seconds
  before `setParam` and re-sends on BPM change. One answer for `delay :sync` and
  `auto-pan :sync`.
- Limiter: `DynamicsCompressorNode` (ratio 20, fast attack) is native but overshoots; a
  true ceiling needs look-ahead in an `AudioWorkletProcessor`. The DoD requires the
  ceiling, so a worklet is likely.
- Should a limiter sit on the master output by default? It would stop the harsh
  clipping, but it changes the loudness of every existing session.
- The Rust engine's `.clamp(-1.0, 1.0)` happens before the chain. Remove it (float audio
  can exceed 1.0 and the limiter handles it) or keep it as a safety net?
- The gate needs an envelope follower. Native trick (`WaveShaperNode` |x| → low-pass →
  `AudioParam`) or one shared "dynamics" worklet for limiter and gate?
- Regenerating an impulse response on every parameter change clicks or costs CPU while
  a slider moves. Crossfade between two convolvers, or debounce and swap?
- Spring reverb: is a chirped (dispersive) impulse response convincing enough? Decide
  by listening.
- Do the impulse-response generators live in one module that TL7b's orbit reverbs also
  use?
- A flanger needs feedback loops shorter than 2.9 ms. Feed-forward only below 3 ms, or
  a small worklet? Decide with a measurement.
- `width` on the master chain vs per track: is the per-track signal already stereo at
  that point (panned voices)?

## Definition of done

**Part A**

- [ ] `eq :low 6 :low-freq 100`: a 50 Hz sine comes out 6 ± 1 dB louder, a 5 kHz sine within ±0.5 dB.
- [ ] `eq :mid -6 :mid-freq 1000 :mid-q 2`: 1 kHz is 6 ± 1 dB quieter, 250 Hz and 4 kHz within ±1 dB.
- [ ] `eq` with defaults is transparent (null test ≤ −60 dBFS).
- [ ] `limiter :ceiling -3` on a signal peaking at +6 dBFS: no output sample above −2.5 dBFS; a signal peaking at −10 dBFS passes within ±0.5 dB.
- [ ] `gate :threshold -40`: noise at −50 dBFS is attenuated by ≥ 30 dB; a −20 dBFS tone passes within ±1 dB.
- [ ] `(fx :filter :type "peaking" :freq 1000 :gain 6)`: +6 ± 1 dB at 1 kHz, within ±0.5 dB at 100 Hz.

**Part B**

- [ ] `reverb :type :hall :decay 3` fed one click: RT60 from the energy decay curve (Schroeder integration) is 3 s ± 20 %; `:decay 0.5` gives 0.5 s ± 20 %.
- [ ] `reverb :predelay 0.05`: the first wet energy arrives 50 ± 5 ms after the click.
- [ ] `reverb :type :gated :decay 0.3`: the tail level 100 ms after the gate time is ≥ 40 dB below its level just before it.
- [ ] `reverb :damp 0.9` vs `:damp 0.1`: energy above 4 kHz in the tail differs by ≥ 6 dB.
- [ ] `(fx :reverb 0.4)` with no other parameters sounds as before (RMS of a reference render within ±1 dB).
- [ ] `(fx :reverb :room 0.8)` shows a warning naming `size` (unknown parameter), instead of being ignored silently.
- [ ] `delay :sync 3/16` at 120 BPM: echoes are 375 ± 2 ms apart; after `(bpm 90)` they are 500 ± 2 ms apart.
- [ ] `delay :mode :ping-pong` fed one click: successive echoes alternate between left-only and right-only (channel ratio ≥ 20 dB each).
- [ ] `delay :taps [1/8 3/8]`: exactly two first-generation echoes at those positions.

**Part C**

- [ ] `flanger` with `depth 0`, `delay 0.002`, `wet 0.5`, no feedback, fed white noise: spectral notches at 250, 750 and 1250 Hz, each ≥ 10 dB below the neighbouring peaks.
- [ ] `vibrato :rate 5 :depth 50` on a 440 Hz sine: the measured pitch swings ±50 cents (±15 %) at 5 Hz.
- [ ] `auto-pan :rate 1`: the left/right RMS ratio alternates with a 1 s period; total power stays within ±1.5 dB.
- [ ] `ring-mod :freq 100 :mix 1` on a 440 Hz sine: peaks at 340 and 540 Hz; 440 Hz is ≥ 20 dB below them.
- [ ] `width 0`: left and right are identical (difference ≤ −60 dBFS); `width 1`: output equals input (null test ≤ −60 dBFS).

**All parts**

- [ ] Each new effect is transparent when bypassed (null test ≤ −60 dBFS) and inactive after auto-load.
- [ ] `(export …)` with each effect active renders offline (no fall-back to real-time capture).
- [ ] Each effect works per track: `(track :a (->> pat (fx :flanger 0.5)))` leaves other tracks dry.
- [ ] USAGE.md "Built-in effects": a section per new effect (parameter table + example), updated reverb and delay tables, a short "Mixing" example using `eq` + `limiter` in `(master …)`, updated effect count and chain order. FUTURE-FEATURES.md ring-modulator and parametric-EQ rows marked done; SYNTHESIS-COVERAGE.md rows set to ✅.
- [ ] Human listen check recorded in the PR: a full mix with and without the limiter, each reverb type on a snare, ping-pong delay on a lead.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## What NOT to do

- No per-event effects or sends (`room`, `delay`, `orbit`, `phaser`, `vib` as pattern
  parameters): **TL7a–c**.
- No character or special effects (ladder filter, comb, wavefolder, tape, vinyl, rotary
  speaker, transient shaper, multiband compressor, pitch shifter, freeze, vocoder):
  deferred **FX2**.
- No audio-keyed sidechain between tracks, no reorderable or parallel chains: **P2**.
- No de-esser, no loudness (LUFS) metering, no graphical EQ curve editor.
- No user impulse-response loading (parity row `ir` stays ❓); `cab` keeps its own IRs.
- Don't change `compressor`, `sidechain` or `dattorro-reverb`.
- No external DSP libraries; native nodes first, a hand-written worklet only where a
  measurement shows native nodes cannot do it.
