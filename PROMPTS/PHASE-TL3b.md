# Phase TL3b — Zoom, Compress & Structure Extras

> **Spec status:** outline — expand into a full spec before implementing. Take the exact
> function list from the TL3b rows of [docs/STRUDEL-PARITY.md](../docs/STRUDEL-PARITY.md).
>
> **Automation:** 🤖 auto — every function is a pure time/structure transform whose Tidal
> or Strudel event output can be asserted exactly in `cljs.test`.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL3 (private `compress*`, `slowcat-held*`, `within*`, `fastcat*`,
`step-true?`, `timecat`, `struct`/`mask`)

## Goal

Finish Tidal's time & structure vocabulary that TL3 deferred when it was split (TL3 took
the 25 core-rhythm functions): the zoom/compress family that plays a window of one cycle
inside another, plus the remaining structure helpers (boolean switching, event filtering,
cycle repetition, binary rhythms).

```lisp
;; After (proposed — Tidal/Strudel names in kebab-case)
(compress 1/4 3/4 (seq :bd :sd))          ; squeeze the cycle into [1/4, 3/4)
(zoom 1/4 3/4 (seq :a :b :c :d))          ; play only the middle half, stretched: b c
(linger 1/4 (seq 0 1 2 3 4 5 6 7))        ; repeat the first quarter: 0 1 0 1 0 1 0 1
(fast-gap 2 (seq :bd :sd))                ; double speed, then silence
(hurry 2 (seq :bd :sd))                   ; fast + playback rate
(repeat-cycles 2 (cat (seq :a) (seq :b))) ; a a b b
(sew "t f" (seq :a :a) (seq :b :b))       ; switch between two patterns
(struct (binary 5) (pure :x))             ; 5 = 101 → x ~ x
```

## Background

- TL3 adds to `packages/core/src/repulse/core.cljs` the private `compress*` (Tidal
  `compressArc`: cycle c squeezed into `[c+s, c+e)`), `slowcat-held*` (Tidal `cat` cycle
  offsets; Strudel `repeatCycles` is the same hold), `within*`, `fastcat*`, and the
  public `step-true?`, `timecat`, `struct`, `mask`, `invert`.
- Lisp builtins for TL3 live in `packages/lisp/src/repulse/lisp/builtins/structure.cljs`;
  TL3b extends that file.
- Parameters: REPuLse's playback-rate control is `rate` (`packages/core/src/repulse/params.cljs`);
  Tidal `hurry` = `fast` + `speed`.

## Scope

Function list (26, from the TL3b-tagged parity rows): `compress`, `zoom`, `focus`,
`fast-gap`, `linger`, `bite`, `ribbon`, `hurry`, `revv`, `repeat-cycles`, `trunc`,
`press`, `press-by`, `chunk-into`, `chunk-back-into`, `euclidish`, `ply-for-each`,
`filter-events`, `filter-when`, `bypass`, `beat`, `sew`, `stitch`, `binary`, `binary-n`,
`binary-l`.

## Open design questions

- `hurry`: multiply `rate` (REPuLse's control) or add a `speed` alias first (TL7 owns
  control names)?
- `zoom` vs `zoom-arc`/`focus`: Strudel's `zoom` keeps cycle alignment differently from
  Tidal's `zoomArc`; pick one and record it.
- `binary-l` returns a list, not a pattern: keep, or drop as JS-specific?
- Batch size: 26 functions. If the full spec finds this too large, split again
  (e.g. TL3c for `filter-*`/`bypass`/`beat`/`binary*`).

## Definition of done

- [ ] Each function has at least one test reproducing a Tidal/Strudel doc example's events exactly.
- [ ] `(zoom 1/4 3/4 (seq :a :b :c :d))` → `b [0,1/2)`, `c [1/2,1)`.
- [ ] `(linger 1/4 (seq 0 1 2 3 4 5 6 7))` → values `0 1 0 1 0 1 0 1`.
- [ ] `(compress 1/4 3/4 (seq :bd :sd))` → `bd [1/4,1/2)`, `sd [1/2,3/4)`.
- [ ] Every new function accepts patterned numeric arguments (one test each).
- [ ] Rule 3 registration; USAGE.md reference rows with examples; parity rows ✅.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.

## What NOT to do

- Don't change any TL3 function's semantics; reuse its private helpers instead of copying them.
- No probability or random-choice functions: **TL4**.
- No layering functions (`superimpose`, `echo`, …): **TL5**.
- No sample slicing (`chop`, `striate`, `slice`, …): **TL6**.
