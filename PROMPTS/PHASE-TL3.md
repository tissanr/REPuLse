# Phase TL3 — Time & Structure Combinators

> **Spec status:** outline — expand into a full spec before implementing. Take the exact
> function list from the TL3 rows of [docs/STRUDEL-PARITY.md](../docs/STRUDEL-PARITY.md).
>
> **Automation:** 🤖 auto — each combinator has Tidal-documented event output that can be
> asserted exactly in `cljs.test`.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL1 (patterned args, joins); TL2 for `segment`-based helpers

## Goal

Port Tidal's rhythmic vocabulary for reshaping time: masking, repeating, rotating,
zooming and grooving. This is the bulk of what makes Tidal patterns feel alive.

```lisp
;; Before
(seq :bd :_ :bd :bd)

;; After (proposed — Tidal names in kebab-case)
(struct "t ~ t t" (pure :bd))
(ply 2 (seq :bd :sd))                        ; each event repeated twice in its slot
(chunk 4 (fast 2) (seq 0 1 2 3))             ; transform a rotating quarter
(iter 4 (seq :a :b :c :d))                   ; shift start by a quarter each cycle
(swing 1/3 (fast 8 (seq :hh)))               ; groove
(polymeter [(seq :bd :sd :hh) (seq :c :d)])
```

## Background

- Existing structure functions: `seq`, `stack`, `cat`, `alt`, `euclidean`, `rev`, `every`,
  `late`/`early`, `off` (`packages/core/src/repulse/core.cljs`); mini-notation in
  `packages/lisp/src/repulse/lisp/mini.cljs`.
- TL1 provides `patternify` so every numeric argument here is patternable from day one.

## Scope

Expected family (final list from the parity doc): `struct`, `mask`, `ply`, `chunk`,
`chunk-back`, `iter`, `iter-back`, `palindrome`, `linger`, `inside`, `outside`,
`compress`, `zoom`, `fastgap`, `hurry`, `rot`, `swing`, `swing-by`, `nudge`,
`euclid-full`, `euclid-off`, `euclid-legato`, `polymeter`, `polymeter-steps`,
`ribbon`, `range`-free helpers, `first-of`/`last-of` (Tidal's `every` variants),
`when-mod`/`whenmod`.

Also: mini-notation extensions that map to these (`{a b c}%4` polymeter, `a!3`
replicate, `a . b` grouping, `,` stacks, `(3,8)` euclid) where `mini.cljs` lacks them.

## Open design questions

- `every` today means "when cycle mod n = 0", like Tidal 1.x. Tidal 1.9 also has
  `first-of`/`last-of`. Recommend adding both as names and keeping `every` unchanged.
- ~~Mini-notation `_`~~ **Decided (2026-10-26):** `_` stays a rest, as do `~` and `:_`
  everywhere in REPuLse. Strudel's "extend the previous step" is expressed with `@`
  weights. This is a documented, deliberate parity difference; say so in `docs/USAGE.md`
  (mini-notation section) and in the parity doc.
- Batch size: if the list exceeds ~25 functions, split into TL3a (core rhythm) and
  TL3b (zoom/compress family).

## Definition of done

- [ ] Each function has at least one test reproducing a Tidal doc example's events exactly.
- [ ] `(struct "t ~ t t" (pure :bd))` → onsets at 0, 1/2, 3/4.
- [ ] `(ply 2 (seq :bd :sd))` → 4 onsets per cycle: bd bd sd sd.
- [ ] `(iter 4 (seq :a :b :c :d))` starts with `:b` in cycle 1 and `:c` in cycle 2.
- [ ] `(swing 1/3 (fast 8 (seq :hh)))` delays every second hi-hat by 1/3 of a sixteenth.
- [ ] Every new function accepts patterned numeric arguments (one test each).
- [ ] Mini-notation extensions round-trip: `"bd!3 sd"` ≡ `(seq :bd :bd :bd :sd)`.
- [ ] Rule 3 registration; USAGE.md reference rows with examples; parity rows ✅.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.

## What NOT to do

- No probability or random-choice functions: **TL4**.
- No layering functions (`superimpose`, `echo`, …): **TL5**.
- No sample slicing (`chop`, `striate`, …): **TL6**.
