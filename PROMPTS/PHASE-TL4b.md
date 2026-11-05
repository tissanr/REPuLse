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

All decided by the user on 2026-11-05.

- **Decided — Lookup form:** accept both. A vector is indexed by integer pattern values
  (clamped to the vector's range for `pick`, wrapped with `mod` for `pick-mod`). A map is
  looked up by name: pattern values `"a"` or `:a` match the keyword key `:a`. A name that
  isn't in the map produces no event (silence) for that slot.
- **Decided — `fix` ships in TL4b** (not split out until TL7). It matches against the
  event-map keys that exist today (`:s`, `:note`, `:amp`, …) and will pick up TL7's
  per-event controls later without a rewrite.
- **Decided — `fix` matching rules:** numbers compare numerically (`==`, so `1` matches
  `1.0`); keywords and strings compare exactly (`{:s :bd}` does **not** match
  bank-prefixed `:AkaiLinn_bd`); a key missing from the event means no match; all keys in
  the condition map must match (AND).
- **Decided — any-of:** a vector value in the condition map means "any of"
  (`(fix f {:s [:bd :sd]} p)` applies `f` to bd or sd events).
- **Decided — `restartJoin`/`resetJoin`** live in `core`, with tests.

## What NOT to do

- No change to TL4's random functions, seeds or mini-notation.
- No new randomness; these functions are purely selective.
