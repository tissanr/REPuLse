# Phase TL4 — Randomness & Conditionals

> **Spec status:** outline — expand into a full spec before implementing. Take the exact
> function list from the TL4 rows of [docs/STRUDEL-PARITY.md](../docs/STRUDEL-PARITY.md).
>
> **Automation:** 🤖 auto — randomness is deterministic (seeded by time), so outputs are
> exactly testable; statistical tests check distributions over many cycles.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL1; TL2 (`time->rand`)

## Goal

Give patterns controlled unpredictability and conditional logic, with Tidal's
semantics so that ported code behaves the same.

```lisp
;; Before — randomness by cycle only, coarse 0–99 hash
(sometimes rev (seq :bd :sd :hh))

;; After (proposed)
(some-cycles-by 0.3 (fast 2) (seq :bd :sd))
(randcat (seq :bd :sd) (seq :hh :hh :oh) (seq :cp))
(shuffle 4 (seq :a :b :c :d))
(when-mod 8 6 rev (seq 0 2 4 7))
(degrade-by "<0.2 0.6>" (fast 16 (seq :hh)))
```

## Background

- Existing: `sometimes-by`, `sometimes`, `often`, `rarely`, `degrade-by`, `degrade`,
  `choose`, `wchoose` (`core.cljs`). They use `cycle-hash` (`(mod (+ (* c 48271) 12345) 100)`)
  and `event-hash`, which are low-resolution and visibly periodic.
- TL2 adds a Tidal-compatible `time->rand`.

## Scope

- Re-base the existing random functions on `time->rand`. Note: this changes which
  cycles/events are chosen; acceptable, but call it out in USAGE.md and release notes.
- New: `some-cycles`, `some-cycles-by`, `sometimes-by'` variants as parity requires,
  `almost-always`, `almost-never`, `always`, `never`, `randcat`, `wrandcat`, `shuffle`,
  `scramble`, `degrade-by-with`/`undegrade-by`, `when`, `when-mod`, `if-then`-style
  `while`, `choose-by` (choose with a signal), `seed`/`with-seed`.
- Mini-notation: `?0.3` probability and `|` random choice if missing in `mini.cljs`.

## Open design questions

- Back-compat for existing users' code: accept the changed random sequence, or keep the
  old functions under a legacy flag? Recommend accepting the change; REPuLse is pre-1.0.
- Global vs per-track seed.
- Semantics differences found by the parity audit: REPuLse's `sometimes-by` family decides
  **per cycle** (that is Strudel's `some-cycles-by`); Strudel's `sometimes-by` decides
  **per event**. `choose`/`wchoose` pick per cycle; Strudel's `choose` is a signal sampled
  by structure. Recommend switching to Strudel semantics (pre-1.0) and keeping the
  per-cycle behaviour under the `some-cycles*` names.

## Definition of done

- [ ] Existing random functions keep their probabilities within ±5% over 1000 cycles.
- [ ] Same seed → identical events across runs; different seed → different events.
- [ ] `(randcat a b c)` picks each pattern ~1/3 of cycles over 900 cycles.
- [ ] `(shuffle 4 (seq :a :b :c :d))` uses each element exactly once per cycle.
- [ ] `(when-mod 8 6 rev pat)` applies `rev` in cycles 6 and 7 of every 8.
- [ ] Probability arguments accept patterns (`"<0.2 0.6>"`).
- [ ] Rule 3 registration; USAGE.md section; parity rows ✅.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.

## What NOT to do

- No Markov chains or generative grammars; possible later phase.
- No structure combinators: **TL3**.
- No change to `arrange`/song form: **SONG1**.
