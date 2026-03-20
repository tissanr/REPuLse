# Phase P — Modular Routing: Busses & Control Rate

## Goal

Named audio and control-rate busses that let synths talk to each other — modulation,
sidechain, feedback loops — turning REPuLse's defsynth system into a modular patching
environment. Plus a general envelope constructor that replaces the fixed `env-perc` /
`env-asr` shapes with arbitrary breakpoints and curve types.

```lisp
;; Before — synths are isolated, envelopes are fixed shapes:
(defsynth pluck [freq]
  (-> (saw freq) (lpf 2000) (env-perc 0.01 0.3)))

;; After — busses wire synths together, envelopes are programmable:
(bus :lfo :control)

(defsynth lfo-writer []
  (out :lfo (sin 4)))          ; 4 Hz LFO written to control bus

(defsynth pluck [freq]
  (-> (saw freq)
      (lpf (* 2000 (in :lfo))) ; filter cutoff modulated by LFO bus
      (env-gen (env [0 1 0.3 0] [0.01 0.1 0.5] [:lin :exp :exp]))))

;; Sidechain via bus — duck pad when kick fires:
(bus :kick-env :control)

(defsynth kick-signal []
  (out :kick-env (env-gen (env [1 0] [0.1] [:exp]))))

(defsynth pad [freq]
  (-> (mix (sin freq) (sin (* freq 1.002)))
      (lpf 2000)
      (gain (in :kick-env))    ; amplitude follows kick envelope
      (env-asr 0.3 0.8 1.0)))
```

---

## Background

### Current synth system (Phase M)

`app/src/repulse/synth.cljs` provides `defsynth` with a UGen vocabulary: five oscillators
(`sin`, `saw`, `square`, `tri`, `noise`), three filters (`lpf`, `hpf`, `bpf`), and
utilities (`gain`, `delay-node`, `mix`). Two envelope types exist: `env-perc` (linear
attack + exponential decay) and `env-asr` (linear attack + hold + exponential release).

Each synth instance is ephemeral — created at event time, auto-cleaned up after envelope
duration. Synths cannot communicate with each other; there is no shared state between
concurrent synth instances.

### Current routing (Phase L)

`app/src/repulse/audio.cljs` has per-track `GainNode`s feeding per-track FX chains
(`app/src/repulse/fx.cljs`), which feed the master bus. The `track-nodes` atom maps
track keywords to `{:gain-node GainNode :fx-chain [...]}`. There are no named busses
and no concept of control rate.

### Current envelopes

Only two fixed shapes: `env-perc-node` and `env-asr-node` in `synth.cljs`. Both use
`GainNode` parameter automation. `env-asr` has a hardcoded 1.0s sustain hold — there is
no gate-based release. No arbitrary breakpoints, no selectable curve types.

### Web Audio bus primitives

Web Audio has no first-class "bus" concept, but the building blocks exist:
- `ConstantSourceNode` — outputs a constant value that can be automated or modulated
  via its `.offset` AudioParam; can serve as a control-rate bus
- `GainNode` — can multiply two signals (connect one to `.gain` AudioParam)
- `AudioParam.setValueAtTime` / `linearRampToValueAtTime` / `exponentialRampToValueAtTime`
  / `setValueCurveAtTime` — provide per-segment envelope automation
- Feedback loops require at least one `DelayNode` in the cycle (Web Audio spec)

---

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/bus.cljs` | **New** — bus registry (`bus-nodes` atom), `create-bus!`, `destroy-bus!`, `get-bus-node`; audio busses as `GainNode`, control busses as `ConstantSourceNode` |
| `app/src/repulse/synth.cljs` | Add `out-node`, `in-node`, `kr-node` UGen functions; add `env-gen-node` with general envelope; add `env-construct` pure function for breakpoint+curve data |
| `packages/lisp/src/repulse/lisp/eval.cljs` | `bus`, `out`, `in`, `kr`, `env`, `env-gen` bindings; `out`/`in` available inside defsynth scope; `bus` and `env` available at top level |
| `app/src/repulse/audio.cljs` | Bus lifecycle — start/stop persistent synths that write to busses; cleanup busses on `(stop)` / `(clear!)` |
| `app/src/repulse/app.cljs` | Register `bus` in `ensure-env!`; bus inspector in context panel |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `bus`, `out`, `in`, `kr`, `env`, `env-gen` to `BuiltinName` |
| `app/src/repulse/lisp-lang/completions.js` | Completion entries for all new functions |
| `packages/core/test/repulse/envelope_test.cljs` | **New** — pure tests for envelope curve math (lin, exp, sin, welch interpolation) |
| ? | Context panel bus inspector UI |

---

## Definition of done

### Busses

- [ ] `(bus :lfo :control)` creates a named control-rate bus backed by a `ConstantSourceNode`
- [ ] `(bus :aux :audio)` creates a named audio-rate bus backed by a `GainNode`
- [ ] `(bus :lfo)` defaults to `:control` type
- [ ] `(out :bus-name signal)` inside `defsynth` connects the signal node to the bus
- [ ] `(in :bus-name)` inside `defsynth` returns the bus node as a UGen source
- [ ] An LFO synth writing to a control bus modulates another synth's filter cutoff — audible modulation
- [ ] Audio bus: one synth feeds another via `(out :aux ...)` / `(in :aux)`
- [ ] `(stop)` and `(clear!)` clean up all busses and persistent synths
- [ ] Attempting to read a nonexistent bus logs a warning and returns silence (no crash)
- [ ] Bus inspector in the context panel shows active bus names and types

### General envelopes

- [ ] `(env [0 1 0] [0.1 0.5])` creates a 3-point envelope with default linear curves
- [ ] `(env [0 1 0.3 0] [0.01 0.1 0.5] [:lin :exp :exp])` specifies per-segment curves
- [ ] Supported curve types: `:lin`, `:exp`, `:sin`, `:welch`, `:step`, numeric curvature value
- [ ] `(env-gen envelope)` applies a general envelope to a signal via `GainNode` automation
- [ ] `(env-gen envelope :gate gate-signal)` holds at a sustain point until gate releases (stretch goal)
- [ ] `env-perc` and `env-asr` continue to work unchanged (backward compatible)
- [ ] Envelope curve math is tested in `packages/core/test/repulse/envelope_test.cljs`

### Control rate

- [ ] `(kr rate signal)` downsamples a signal for efficient control-rate use
- [ ] Control-rate busses update per audio block, not per sample

### Integration

- [ ] All new tokens highlighted in the editor (grammar regenerated)
- [ ] All new functions have autocomplete entries with detail strings
- [ ] Existing `defsynth` patterns are unaffected — no regressions
- [ ] All existing tests pass (`npm run test:core`)
