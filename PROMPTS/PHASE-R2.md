# Phase R2 — Builtin Table Decomposition

## Goal

Decompose the monolithic built-in map literal in `packages/lisp/src/repulse/lisp/eval.cljs`
(starting at line 306) into domain-grouped namespaces. This is a pure refactor
of the Lisp evaluator — no behaviour change, no new built-ins, no API change.

**Motivation:** `make-env` currently registers every built-in as a single giant
map literal. After 30+ phases of growth, this map mixes pattern combinators,
math, music theory, parameters, effects, samples, audio control, session
management, and more in one file. Finding the right registration slot for a
new built-in requires scrolling through hundreds of entries; reviewing a PR
that touches the map is painful; and it's the last big monolithic piece of
the `packages/lisp` codebase.

**Before:**
```
packages/lisp/src/repulse/lisp/eval.cljs   (557 lines)
├── special forms
├── eval-form dispatch
├── make-env
│   └── one giant map literal (~250 lines of built-in registrations)
└── helpers
```

**After:**
```
packages/lisp/src/repulse/lisp/eval.cljs              (~300 lines — evaluator only)
packages/lisp/src/repulse/lisp/builtins/pattern.cljs  (seq, stack, fast, slow, rev, every, fmap, cat, euclidean, …)
packages/lisp/src/repulse/lisp/builtins/math.cljs     (+, -, *, /, mod, rem, abs, min, max, …)
packages/lisp/src/repulse/lisp/builtins/music.cljs    (scale, chord, transpose, note, …)
packages/lisp/src/repulse/lisp/builtins/params.cljs   (amp, pan, attack, decay, release, rate, begin, end, …)
packages/lisp/src/repulse/lisp/builtins/control.cljs  (if, when, case, not, and/or wrappers…)
packages/lisp/src/repulse/lisp/builtins/collection.cljs (map, filter, reduce, conj, get, nth, …)
packages/lisp/src/repulse/lisp/builtins/core.cljs     (def, let, fn, do, ->> — special forms registered here or kept in eval.cljs)
```

Each namespace exports a single `builtins` map. `make-env` merges them:

```clojure
(ns repulse.lisp.eval
  (:require [repulse.lisp.builtins.pattern :as pattern]
            [repulse.lisp.builtins.math    :as math]
            [repulse.lisp.builtins.music   :as music]
            ;; ...
            ))

(defn make-env []
  (merge pattern/builtins
         math/builtins
         music/builtins
         params/builtins
         collection/builtins))
```

---

## Background

### Why this is separate from R1

R1 refactors `app/src/repulse/app.cljs` (the app layer). R2 refactors
`packages/lisp/src/repulse/lisp/eval.cljs` (the language layer). They're
independent files with independent test coverage, and mixing them would make
both harder to review. R2 also lives in a layer with solid test coverage
(`eval_test.cljs`, `mini_test.cljs`), so it has a real safety net — unlike R1.

### Why this is lower urgency than R1

- `eval.cljs` is 557 lines, not 1997. It's annoying, not blocking.
- The test suite catches regressions in the evaluator reliably.
- No feature is blocked by the current structure.

### Prerequisites

- **R0 must be delivered first** — R0 touches `eval.cljs` to add `and`/`or`
  as special forms and update the eval-error marker. Doing R2 on top of an
  unmodified `eval.cljs` means merge conflicts when R0 lands.
- **R1 need not be delivered first.** R1 and R2 touch disjoint files; either
  order works. My preference: R2 after S1 has shipped, so the language layer
  is touched only once the pressure is gone.

---

## Files to change

| File | Change |
|------|--------|
| `packages/lisp/src/repulse/lisp/eval.cljs` | Remove built-in map; require and merge from `builtins/*` |
| `packages/lisp/src/repulse/lisp/builtins/pattern.cljs` | **New** — pattern constructors and transforms |
| `packages/lisp/src/repulse/lisp/builtins/math.cljs` | **New** — arithmetic and number ops |
| `packages/lisp/src/repulse/lisp/builtins/music.cljs` | **New** — scale, chord, transpose, note ops |
| `packages/lisp/src/repulse/lisp/builtins/params.cljs` | **New** — amp, pan, envelope params |
| `packages/lisp/src/repulse/lisp/builtins/collection.cljs` | **New** — map, filter, reduce, conj, get |
| `packages/lisp/src/repulse/lisp/builtins/control.cljs` | **New** — not, comparison, truthy/falsy helpers |
| `packages/lisp/src/repulse/lisp/eval_test.cljs` | No new tests needed; existing tests must all still pass |
| `docs/ARCHITECTURE.md` | Document the builtins namespace layout |

---

## Grouping rules

- **`pattern`** — anything that constructs or transforms a `Pattern` value: `seq`,
  `stack`, `pure`, `fast`, `slow`, `rev`, `every`, `fmap`, `cat`, `late`, `early`,
  `euclidean`, `sometimes`, `often`, `rarely`, `degrade`, `degrade-by`, `choose`,
  `wchoose`, `jux`, `off`, `palindrome`, `polymeter`, `alt`
- **`math`** — `+`, `-`, `*`, `/`, `mod`, `rem`, `abs`, `min`, `max`, `floor`,
  `ceil`, `round`, `sqrt`, `pow`, `log`, `exp`, comparison operators
- **`music`** — `scale`, `chord`, `transpose`, `note`, music theory helpers
- **`params`** — `amp`, `pan`, `attack`, `decay`, `release`, `rate`, `begin`,
  `end`, `loop-sample`, `tween`, `comp`
- **`collection`** — `map`, `filter`, `reduce`, `conj`, `get`, `nth`, `count`,
  `first`, `rest`, `last`, `vec`, `hash-map`
- **`control`** — `not`, `=`, `<`, `>`, `<=`, `>=`, truthy helpers (`and`/`or`
  remain special forms after R0)

**Ambiguous cases:** any built-in that could fit two buckets goes into the
bucket where users would look first. When in doubt, ask: "if I were searching
for `fast`, which file would I check?" → `pattern.cljs`.

---

## Definition of done

- [ ] `make-env` is <30 lines: imports + merges
- [ ] Each `builtins/*.cljs` is <200 lines
- [ ] Every built-in from the old map exists in exactly one new file
- [ ] No built-in is missing or duplicated — verified by diffing the key sets
- [ ] `npm run test` passes with no regressions (expect 0 net test count change)
- [ ] All existing unit tests in `eval_test.cljs` still pass unchanged
- [ ] `eval.cljs` shrinks from ~557 lines to ~300 lines
- [ ] No circular dependencies between `builtins/*.cljs` files
- [ ] `docs/ARCHITECTURE.md` documents the builtins layout
- [ ] Bundle size unchanged (±2%)

---

## Out of scope

- No changes to built-in behaviour
- No new built-ins
- No changes to the evaluator, reader, or special forms
- No changes to `app/`, `audio/`, or Rust
- No documentation rewrites of `docs/USAGE.md` (table of built-ins stays intact)

---

## Open questions

1. **Should special forms (`if`, `do`, `let`, `fn`, `def`, `->>`, and post-R0
   `and`/`or`) stay in `eval.cljs` or move to `builtins/control.cljs`?** My
   recommendation: **stay in `eval.cljs`**. Special forms are part of the
   evaluator, not the built-in library. Keeps the boundary clean.
2. **Should the namespace be `repulse.lisp.builtins.*` or `repulse.lisp.env.*`?**
   `builtins` describes the content; `env` describes the role. **Resolved:**
   `builtins/*` — more discoverable.
3. **Migration order — one PR or one per file?** **Resolved for MVP:** one PR
   with all files. Mechanical enough to review in one pass.
