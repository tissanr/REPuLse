# Phase TL2 — Continuous Signals

> **Spec status:** outline — expand into a full spec (Background with exact code paths,
> step-by-step Implementation, Files to change) before implementing.
>
> **Automation:** 🤖 auto — signals are pure functions of time; sampling them at known
> points gives exact expected values in `cljs.test`.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL1 (joins, `app-left`, onset-only triggering)

## Goal

Add Tidal's continuous patterns: values that exist at every point in time instead of as
discrete events. Combined with TL1's structure rules, they give smooth movement (filter
sweeps, panning, random variation) that is sampled by a rhythmic pattern.

```lisp
;; Before — movement needs hand-written sequences or tween
(pan (seq -1 -0.5 0 0.5 1) (fast 4 (seq :hh)))

;; After (proposed)
(pan sine (fast 8 (seq :hh)))                              ; smooth stereo sweep each cycle
(->> (fast 16 (seq :hh)) (amp (range 0.3 0.9 (slow 4 saw))))
(->> (segment 8 (range 1 8 rand)) (scale :minor :c4))      ; 8 random degrees per cycle
(pan (range -0.5 0.5 perlin) (fast 4 (seq :hh)))
```

## Background

- Every event today has a `:whole`. Tidal represents continuous ("analog") events with
  `whole = nil`: the value is sampled at the query span (Tidal samples at the span's start).
- TL1's `app-left`/`app-right` must already handle a nil whole on the non-structure side.
- `cycle-hash` in `core.cljs` is a weak linear hash. `rand` needs a proper deterministic
  time → random function (Tidal's `timeToRand` xorshift). TL4 will reuse it.

## Scope

- Analog event support in `core.cljs`: `signal` constructor (fn of time → value), nil-whole events.
- Signals: `sine`, `cosine`, `saw`, `isaw`, `tri`, `square`, `rand`, `irand n`, `perlin`,
  plus bipolar variants (`sine2`, `saw2`, …) if parity says so.
- Shaping: `range lo hi sig`, `rangex` (exponential), `segment n pat` (discretise),
  `sample-and-hold`, if it appears in the parity doc.
- Deterministic seeded random: `time->rand` (Tidal algorithm, so values match Tidal for
  the same time), with `seed` support if cheap.
- Guard: a pattern with no discrete structure that reaches the scheduler produces no
  sound and a clear warning ("signal needs structure, e.g. (segment 8 …)").

## Open design questions

- Bare symbols (`sine`) vs calls (`(sine)`): Tidal uses values. Recommend values, which
  read naturally in Lisp.
- Should `rand` match Tidal numerically? Recommend yes; it makes ported examples sound
  the same.
- Interaction with `tween` (T1): keep both; document when to use which.
- ~~Name clashes~~ **Decided (2026-10-26):**
  - UGen names inside `defsynth` (`sin saw square tri noise lpf …`) live in their own
    environment (`ugen-env` in `app/src/repulse/synth.cljs`) and are unaffected.
  - At top level, `saw` and `square` become Tidal **signals**. Their current role as
    voice shortcuts (`(saw :c4)`, `(square :c4 :pw 0.3)` in
    `packages/lisp/src/repulse/lisp/builtins/synth.cljs`) is removed; `(synth :saw pat)`
    covers it. Calling a signal like a function gives a helpful eval error suggesting
    `(synth :saw …)`. Update demos, tutorial, completions and `builtin_meta.edn`.
  - `tri`, `sine`, `cosine` and the rest are new top-level signal names (Tidal uses
    `sine`, not `sin`).
  - `(noise)` stays as the noise voice; Tidal/Strudel have no `noise` signal.
  - `range` dispatches on its last argument: a pattern or signal means Tidal's
    `range lo hi pat`; otherwise it stays the Lisp collection `range`.

## Definition of done

- [ ] `(query (segment 4 saw) cycle-0)` → values `0 1/4 1/2 3/4` at onsets `0 1/4 1/2 3/4`.
- [ ] `sine` sampled at 0, 1/4, 1/2, 3/4 gives `0.5 1 0.5 0` (unipolar convention, as Tidal).
- [ ] `rand` is deterministic: same time → same value across runs; matches Tidal's `timeToRand` for 5 reference times.
- [ ] `(pan sine (fast 8 (seq :hh)))` yields 8 events with increasing-then-decreasing pan.
- [ ] `(range 2 6 saw)` stays within [2, 6]; `rangex` is exponential.
- [ ] A bare `sine` evaluated as a track logs the structure warning and does not crash the scheduler.
- [ ] Rule 3 registration for every new name; USAGE.md "Signals" section; parity doc updated.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.

## What NOT to do

- No new structure combinators beyond `segment`: **TL3**.
- No random *combinators* (`shuffle`, `randcat`, …): **TL4** (but build the RNG they will use).
- No audio-rate modulation inside the WASM engine; signals are sampled per event.
