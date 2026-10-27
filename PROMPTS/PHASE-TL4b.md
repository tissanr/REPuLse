# Phase TL4b — Pick & Fix

> **Spec status:** outline — expand into a full spec before implementing. Take the exact
> function list from the TL4b rows of [docs/STRUDEL-PARITY.md](../docs/STRUDEL-PARITY.md).
>
> **Automation:** 🤖 auto — selection is deterministic and produces exact event lists
> testable in `cljs.test`.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL4 (randomness & conditionals); TL1 joins; `fix` is best scheduled after TL7

## Goal

Finish the Strudel/Tidal conditional vocabulary that TL4 deferred: pick a pattern or a
function out of a lookup by an index/name pattern, and apply a function only to events
that match given control values.

```lisp
;; Proposed
(pick {:a (seq :bd :sd) :b (seq :hh :hh :hh)} "<a b>")        ; choose a pattern by name
(pick-mod [(seq :bd :sd) (seq :hh :hh)] "0 1 2")               ; index wraps around
(pick-f "<0 1>" [(fast 2) rev] (seq :a :b))                     ; choose a function
(inhabit {:a (seq :bd :sd) :b (pure :hh)} "a b")               ; squeeze patterns into steps
(fix (fast 2) {:s :bd} (stack (s :bd) (s :hh)))                 ; only events matching :s :bd
```

## Scope

- `pick`, `pick-mod`, `pick-f`, `pick-out`, `pick-restart`, `pick-reset` (and the `mod`
  variants) — Strudel `pick.mjs` lookup semantics; `restartJoin`/`resetJoin` are not in
  `core` yet and must be added.
- `inhabit` (Strudel `pickSqueeze` / `inhabitmod`) — squeeze-join variant.
- Tidal `fix` — apply a function to events whose control map matches the given values;
  needs control-value matching, better after TL7.

## Open design questions

- Lookup form: Lisp map with keyword keys vs vector with indices; how a string pattern
  index maps to map keys.
- `fix` matching rules for numeric controls and sample banks.
- Whether `restartJoin`/`resetJoin` belong in `core` (yes, with tests) or in the app layer.

## What NOT to do

- No change to TL4's random functions, seeds or mini-notation.
- No new randomness; these functions are purely selective.
