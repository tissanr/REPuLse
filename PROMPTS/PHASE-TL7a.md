# Phase TL7a — Per-Event Filters, Shape & Crush

> **Spec status:** full.
> **Automation:** 🤖👂 auto + human check. Parameter plumbing, Rust filter tests and
> offline-render assertions are automatic. Acceptance needs a human listen (filter sweep,
> resonance, shape, crush) recorded in the PR. Follow the CLAUDE.md "FM preset tests"
> guidance: concrete FFT/Goertzel assertions only, no claims about how something sounds.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL1 (patterned params), TL2 (signals to drive them)
**Unblocks:** TL7b (sends reuse the per-event output chain and the `trigger_v3` slot
layout), TL7c, SONG2 (automating `lpf`), KIT1 (`velocity`)

TL7 was split into three phases on 2026-11-10: **TL7a** per-event inserts (this phase),
**TL7b** sends & orbits, **TL7c** vowel, envelopes & modulators.

## Goal

Every event can carry its own filter, waveshaper and bit-crusher, and those values can
be patterned or driven by signals, as in Tidal/Strudel. Until now filtering and
distortion only worked on a whole track (`(fx :filter …)`, `(fx :distort …)`).
TL7a adds eleven control functions. `amp` and `pan` already work this way: each one
writes a key into the event map, and the audio layer applies it to that one voice.

```lisp
;; Before — effects only per track
(track :hats (->> (fast 8 (seq :hh)) (fx :filter 800)))

;; After — per event, patternable, signal-driven
(->> (fast 8 (seq :hh))
     (lpf (range 400 6000 (slow 4 sine)))
     (resonance 4))
(->> (seq :c3 :eb3 :g3) (synth :saw) (hpf 300) (shape 0.4))
(->> (seq :bd :sd :bd :sd) (crush (seq 16 4)) (velocity (seq 1 0.6)))
```

## Design decisions

Decided with the project owner on 2026-11-10.

1. **Two implementations, one parity test.** Synth voices on the WASM worklet get
   insert DSP in Rust. Every other path (samples, `defsynth` voices, JS fallbacks,
   offline render, events on tracks with an FX chain) gets the same inserts as
   **built-in Web Audio nodes** (`BiquadFilterNode`, `WaveShaperNode`). The JS side
   contains **no hand-written DSP**: it only configures browser nodes, using the same
   formulas as the Rust side (Audio EQ Cookbook biquads, identical shaper and crush
   curves). A parity test renders both paths and compares them. Moving samples into WASM
   was rejected for now: WASM has no sample playback, and adding it would collide with
   TL6's sample rework. EXP1 later makes offline render use the worklet. This split
   keeps the DSP in one place per path. The owner prefers to avoid duplicates, so
   **keep the JS insert code as thin as possible**.
2. **Only effects that native nodes can express.** `coarse` (sample-and-hold rate
   reduction) has no built-in node, so it moves to TL7c with `vowel`. TL7a contains
   no new AudioWorklet processors.
3. **Values are sampled once per event, at onset.** `(lpf sine pat)` gives each event
   the signal value at its onset (like `pan` today). Sweeping within one note is a filter
   envelope: TL7c.
4. **Gain model:** final voice amplitude = `amp × gain × velocity`. All three are linear
   and default to 1. `gain` is clamped to 0–2 and `velocity` to 0–1. `amp` keeps its
   current meaning, and existing code sounds unchanged.
5. **Names inside `defsynth` keep their UGen meaning.** `lpf`, `hpf`, `bpf` and `gain`
   are already `defsynth` UGens (`make-build-fn` in `app/src/repulse/synth.cljs` merges
   the UGen env last, so the local binding wins). Outside a `defsynth` body they are the
   new pattern controls. One grammar/completion/meta entry per name describes both.

## Parameters

| Function | Event key | Range (clamped) | Default when absent | Meaning |
|---|---|---|---|---|
| `lpf`, alias `cutoff` | `:lpf` | 20–20000 Hz | off | 12 dB/oct low-pass (one biquad) |
| `resonance` | `:resonance` | 0.1–30 | 0.707 | Q of the low-pass |
| `hpf` | `:hpf` | 20–20000 Hz | off | 12 dB/oct high-pass |
| `hpq` | `:hpq` | 0.1–30 | 0.707 | Q of the high-pass |
| `bpf` | `:bpf` | 20–20000 Hz | off | band-pass (constant 0 dB peak gain) |
| `bpq` | `:bpq` | 0.1–30 | 1.0 | Q of the band-pass |
| `shape` | `:shape` | 0–0.99 | off (0) | soft waveshaper, amount |
| `crush` | `:crush` | 1–16 bits | off (16 = off) | bit-depth quantiser |
| `gain` | `:gain` | 0–2 | 1 | linear gain, multiplies `amp` |
| `velocity` | `:velocity` | 0–1 | 1 | linear, multiplies `amp` (MIDI capture writes it, SK3) |

A Q parameter without its filter (`(resonance 4 pat)` with no `lpf`) is stored but has no
effect. Processing order per voice: **source → lpf → hpf → bpf → crush → shape → pan**.
`gain`/`velocity` are folded into the voice amplitude before the chain.

Shared formulas (both implementations must use exactly these):

```
shape:  a = min(amount, 0.99);  k = 2a / (1 − a)
        y = (1 + k) · x / (1 + k · |x|)
crush:  levels = 2^(bits − 1)
        y = round(x · levels) / levels          (bits ≥ 16 → bypass)
biquads: Audio EQ Cookbook LPF / HPF / BPF (constant 0 dB peak gain), as in lib.rs
```

**Web Audio Q pitfall:** for `BiquadFilterNode` type `lowpass`/`highpass`, `Q` is in
**dB** (`Q_dB = 20·log10(Q)`). For `bandpass` it is the linear Q. Convert accordingly,
or the parity test fails at every Q ≠ 0.707.

## Background

- **Control functions.** `packages/core/src/repulse/params.cljs` has `apply-param`
  (structure from the note pattern via `core/app-right`, scalar or pattern/signal
  value). Each control is a two-arity fn: `(amp v)` returns a transformer, `(amp v pat)`
  applies it. They are registered for the Lisp in
  `packages/lisp/src/repulse/lisp/builtins/params.cljs` (`u/unwrap` on arguments).
  Unit tests: `packages/core/src/repulse/params_test.cljs`.
- **Dispatch.** `play-event` in `app/src/repulse/audio.cljs` (≈ line 423) reads `:amp`,
  `:attack`, `:decay`, `:pan`, `:synth` from the event map and either
  - sends `worklet-trigger-v2!` (WASM), **only when `dest` is `@master-gain`**, i.e.
    live playback and the track has no FX chain, or
  - falls back to JS voices: `make-sine`, `make-saw`, `make-square`, `make-noise`,
    `js-synth` (`make-kick`/`make-snare`/`make-hihat`), `samples/play!`
    (`app/src/repulse/samples.cljs`), `synth/play-synth!` (`defsynth`). Every one
    takes a `dest` AudioNode and connects its panner/output to it.
  - Offline (`OfflineAudioContext`, used by `(export …)` and the TEST1 harness) always
    takes the JS path; `output-for-track` returns `ac.destination` there.
- **WASM engine** `packages/audio/src/lib.rs`:
  - `struct Biquad` (Direct Form I, `tick(x)`), constructors `bandpass`, `highpass`,
    `peaking_eq`, `highshelf`. **No `lowpass` constructor yet.**
  - `struct Pending { time, value, amp, attack, decay, pan }`, filled by `trigger_v2`.
  - `struct ActiveVoice { voice: Voice, pan: f32 }`; `activate(p)` builds the `Voice`
    from the value string (`"saw:440"`, `"fm:epiano:220"`, `"ks:nylon:110"`, `"bd"`, …)
    and clamps amp to 0–1.
  - `process_block_raw`: per sample, `av.voice.tick(sr)` → equal-power pan → sum.
    Inserts go between `tick` and the pan.
- **Worklet** `app/public/worklet.js`: `_onMessage` maps `trigger_v2` messages onto
  `engine.trigger_v2(value, time, amp, attack, decay, pan)`.
- **Offline test harness**: `app/src/repulse/test_api.cljs` `render-offline` →
  `window.__REPULSE_TEST__.render(code, cycles)`. Helpers in
  `e2e/helpers/audio-analysis.ts`: `rms`, `tailRms`, `countOnsets`, `goertzel`.
  Specs: `e2e/audio-render.spec.ts`.

## Implementation

### 1. Core controls (`packages/core/src/repulse/params.cljs`)

Add the ten functions with the same two-arity shape as `amp`, via `apply-param`:

```clojure
(defn lpf
  "Per-event low-pass cutoff in Hz (20–20000). Patternable, accepts signals.
   (lpf 800 pat) · (lpf (range 400 6000 sine) pat) · (lpf 800) → transformer"
  ([f] (fn [pat] (lpf f pat)))
  ([f pat] (apply-param :lpf f pat)))
```

`resonance`, `hpf`, `hpq`, `bpf`, `bpq`, `shape`, `crush`, `gain` and `velocity` follow
the same pattern with their keys from the table. Core does **not** clamp: clamping is
the audio layer's job, so the event map shows what the user wrote. Register them in
`packages/lisp/src/repulse/lisp/builtins/params.cljs`, with `"cutoff"` bound to the same
fn as `"lpf"`.

### 2. Shared insert spec (`app/src/repulse/inserts.cljs`, new)

One small pure namespace both paths read from, so clamping and defaults live in one
place:

```clojure
(ns repulse.inserts)

(defn insert-params
  "Extract clamped insert params from an event value map, or nil when the event
   has none (the common case: no extra nodes, no extra WASM work)."
  [v] …)  ; → {:lpf 800.0 :resonance 0.707 :hpf nil … :shape 0 :crush 16} or nil

(defn amp-factor [v] …)   ; (* gain velocity), clamped as specified

(defn shape-curve [amount n] …)  ; Float32Array, formula above
(defn crush-curve [bits n] …)    ; Float32Array staircase, n = 65536
(defn q->webaudio [type q] …)    ; dB for lowpass/highpass, linear for bandpass
```

Unit-test it in `app/src/repulse/inserts_test.cljs` and add it to the `:test` build.
Test the clamps and defaults, and that `insert-params` returns nil for
`{:note :c4 :amp 0.5}`.

### 3. JS path: insert chain (`app/src/repulse/audio.cljs`)

```clojure
(defn- with-inserts
  "Return the node a voice should connect to. If the event has insert params, build
   lpf → hpf → bpf → crush → shape (only the stages present) ending in dest,
   and schedule disconnection after the voice ends. Otherwise return dest."
  [ac t value dest dur] …)
```

- Stages are created only when their parameter is present. An event without inserts
  allocates no extra nodes.
- Filter params are set with `setValueAtTime` at `t`.
- `WaveShaperNode.curve` from `inserts/shape-curve` (4096 points) and
  `inserts/crush-curve` (65536 points), `oversample "none"`. Cache curves per
  (amount, bits) rounded to 3 decimals, so repeated values reuse one array.
- Cleanup: `setTimeout` disconnect at `t + dur + 0.5 s` (relative to `ac.currentTime`).
  `dur` is the voice's scheduled length: `decay` for synth voices, the sample's played
  length (`(end − begin) · duration / rate`) for samples, and the synth-def duration
  for `defsynth`. For offline contexts skip the timer: the context is discarded after
  rendering.
- In `play-event`, compute `dest' (with-inserts ac t value dest dur)` once and pass
  `dest'` to every JS voice constructor, `samples/play!` and `synth/play-synth!`, and
  multiply `amp-v` by `inserts/amp-factor`. `samples/play!` and `play-synth!` already
  accept `dest`, so their signatures stay unchanged.
- **WASM eligibility:** the existing check `(= dest @master-gain)` keeps working
  because it is evaluated on the original `dest`. When the worklet takes the event,
  no JS chain is built (call `with-inserts` only on the fallback branch).

### 4. WASM path (`packages/audio/src/lib.rs`, `app/public/worklet.js`)

- Add `Biquad::lowpass(freq, q, sr)` (Cookbook LPF) next to `highpass`, and an explicit
  `q` argument for `bandpass` callers that need it (existing callers keep their values).
- New inline struct, no heap allocation:

```rust
struct Inserts {
    lpf: Option<Biquad>,
    hpf: Option<Biquad>,
    bpf: Option<Biquad>,
    crush_levels: f32, // 0.0 = bypass
    shape_k: f32,      // 0.0 = bypass
}
impl Inserts {
    #[inline] fn tick(&mut self, x: f32) -> f32 { /* lpf → hpf → bpf → crush → shape */ }
}
```

- `ActiveVoice` gains `inserts: Inserts`; `process_block_raw` applies
  `av.inserts.tick(av.voice.tick(sr))` before the pan.
- New entry point, so the API doesn't grow one positional argument per control:

```rust
/// Slot layout (fixed; TL7b appends send slots, never reorders):
/// 0 amp · 1 attack · 2 decay · 3 pan · 4 lpf · 5 resonance · 6 hpf · 7 hpq
/// 8 bpf · 9 bpq · 10 shape · 11 crush        (filter slots: 0.0 = off)
pub fn trigger_v3(&mut self, value: &str, time: f64, params: &[f32]) { … }
```

  `Pending` gets the extra fields. A missing trailing slot means "default", so TL7b can
  append slots. `trigger_v2` stays and forwards with defaults. The `Inserts` are built in
  `activate`, at trigger time, never inside the per-sample loop. The amp clamp in
  `activate` widens from 0–1 to 0–2, because `amp × gain` may exceed 1. The output clamp
  at ±1 is unchanged.
- `worklet.js`: handle `{type: "trigger_v3", value, time, params: Float32Array}` →
  `engine.trigger_v3(...)`. `worklet-trigger-v2!` becomes `worklet-trigger-v3!` in
  `audio.cljs`. It builds the `Float32Array` from `inserts/insert-params` and sends v2
  semantics when the event has no inserts (smaller message).
- Rebuild: `npm run build:wasm`.

### 5. Registration (CLAUDE.md Rule 3)

For each of `lpf cutoff resonance hpf hpq bpf bpq shape crush gain velocity`:
grammar `BuiltinName` → `npm run gen:grammar`; `completions.js`; `builtin_meta.edn`
(category `"param"`, examples, see-also). Then `npm run gen:ai-docs`. Add hover entries
in `app/src/repulse/lisp-lang/hover.js`, and add an "Effects" group to
`insert-categories.js` next to `amp`/`pan`. For `lpf`/`hpf`/`bpf`/`gain`, the detail
text mentions the `defsynth` UGen meaning.

### 6. Docs

- `docs/USAGE.md`: new section **"Effect parameters (per event)"** with the parameter
  table, processing order, the gain model and the three Goal examples. Explain how this
  differs from `(fx …)` (per track / master), and the `defsynth` name rule. Remove the
  "no inline distort fn" remark only if it becomes wrong (it doesn't: `distort` is TL7c).
- `docs/STRUDEL-PARITY.md`: set rows `lpf`, `resonance`, `hpf`/`hpq`, `bpf`/`bpq`,
  `shape`, `crush`, `gain`, `velocity` to ✅ (`crush` notes that track-level `bitcrush`
  remains). Update the family summary counts.

## Files to change

| File | Change |
|---|---|
| `packages/core/src/repulse/params.cljs` | 10 control fns |
| `packages/core/src/repulse/params_test.cljs` | Tests: keys set, patterned values, signal sampled at onset, transformer arity |
| `packages/lisp/src/repulse/lisp/builtins/params.cljs` | Register 11 names (`cutoff` alias) |
| `app/src/repulse/inserts.cljs` (new) | `insert-params`, `amp-factor`, curves, Q conversion |
| `app/src/repulse/inserts_test.cljs` (new) | Clamp/default/curve tests; add to `:test` build |
| `app/src/repulse/audio.cljs` | `with-inserts`, `amp-factor` in `play-event`, `worklet-trigger-v3!` |
| `packages/audio/src/lib.rs` | `Biquad::lowpass`, `Inserts`, `ActiveVoice.inserts`, `trigger_v3`, amp clamp 0–2, Rust tests |
| `app/public/worklet.js` | `trigger_v3` message |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` + `parser.js` | 11 names; `npm run gen:grammar` |
| `app/src/repulse/lisp-lang/completions.js`, `hover.js`, `insert-categories.js` | Entries |
| `app/src/repulse/content/builtin_meta.edn` + `docs/ai/builtins.json` | Entries; `npm run gen:ai-docs` |
| `e2e/audio-render.spec.ts` | Offline filter/shape/crush/gain tests, JS↔WASM parity test |
| `docs/USAGE.md`, `docs/STRUDEL-PARITY.md`, `ROADMAP.md` | Docs, parity rows, delivered entry |
| build | `npm run build:wasm` |

## Definition of done

- [ ] Rust: a 5 kHz sine through `Inserts` with `lpf` 500 Hz is ≥ 20 dB lower (Goertzel at 5 kHz) than without; 200 Hz passes within 1 dB.
- [ ] Rust: `hpf` 2000 attenuates 200 Hz by ≥ 20 dB; `bpf` 1000 (Q 5) attenuates 100 Hz and 8 kHz by ≥ 15 dB relative to 1 kHz.
- [ ] Rust: `crush 4` output has at most 16 distinct sample values over 0.1 s of a sine; `shape 0.8` raises the 3rd harmonic of a 440 Hz sine by ≥ 20 dB.
- [ ] Rust: a voice triggered via `trigger_v2` renders bit-identically before and after this phase (no-insert path unchanged).
- [ ] Offline: `(->> (seq :c4 :c4 :c4 :c4) (synth :saw) (lpf 400))` vs the same with `(lpf 8000)` (JS `make-saw`, 262 Hz fundamental, all harmonics). The 8th harmonic (2093 Hz) is ≥ 15 dB lower in the 400 Hz render. Use a saw, not `shape` on a sine: `shape` comes after the filter in the chain.
- [ ] Offline: `(lpf (range 400 6000 saw) (fast 8 (seq :hh)))` renders 8 onsets whose high-band energy (Goertzel 6 kHz) rises monotonically across the cycle.
- [ ] Offline: `(gain 0.5 (seq :bd))` has RMS 6 dB (±0.5) below `(seq :bd)`; `(velocity 0.5 (gain 2 (seq :bd)))` equals `(seq :bd)` within 0.1 dB.
- [ ] Offline: a sample bank event (`(lpf 300 (sound :bd 0))`, with a test bank stubbed in the harness or the built-in kit) is filtered: high-band energy drops ≥ 15 dB.
- [ ] Parity: for the same `(->> (seq :c4) (synth :saw) (lpf 1200) (resonance 4))`, the WASM render (Rust test rendering the same parameters) and the JS render (offline) agree within 2 dB at 262, 1200 and 4000 Hz, measured relative to each path's fundamental.
- [ ] `(lpf 800 pat)` inside a `defsynth` body still builds the `lpf` UGen (existing defsynth tests pass unchanged).
- [ ] Events without insert keys create no extra Web Audio nodes (spy on `createBiquadFilter` in a unit/e2e test).
- [ ] Per-sample render loop adds no heap allocation: `Inserts` contains only inline fields (code review item), and all existing Rust tests pass.
- [ ] Rule 3 registration for all 11 names; `gen:grammar` and `gen:ai-docs` produce no drift in CI.
- [ ] USAGE.md "Effect parameters (per event)" section; parity rows ✅; ROADMAP delivered entry.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] Human listen recorded in the PR: filter sweep on hats, resonance on a saw bass, `shape` on a kick, `crush` 16→4.
- [ ] The PR description ends with a `## Test protocol` section (CLAUDE.md Rule 5).

## What NOT to do

- No `room`, `delay`, `orbit` or any shared effect: **TL7b**.
- No `coarse`, `vowel`, `distort` (per event), `djf`, filter/pitch envelopes, `vib`,
  `postgain`, `adsr` shorthands, `nudge`: **TL7c**.
- No new AudioWorklet processors or hand-written DSP on the JS side; native nodes only.
- No sample playback in WASM, and no change to the offline backend (EXP1 makes offline
  use the worklet).
- Don't change `(fx …)` / `track-fx` behaviour or the `defsynth` UGens.
