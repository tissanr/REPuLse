# Phase TL3 — Time & Structure Combinators

> **Spec status:** full (expanded 2026-10-27)
>
> **Automation:** 🤖 auto — each combinator has Tidal-documented event output that can be
> asserted exactly in `cljs.test`.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL1 (patterned args, joins, `app-left`, onset-only triggering); TL2 (nothing
functional — TL3 does not use signals)
**Unblocks:** TL3b (zoom/compress family reuses TL3's private `compress*` and
`slowcat-held*` helpers), TL4, TL5

## Goal

Port Tidal's core rhythmic vocabulary for reshaping time: boolean structure (`struct`,
`mask`, `invert`), repetition (`ply`, `ply-with`), Euclidean rhythms that restructure an
existing pattern, window transforms (`inside`, `outside`, `within`), groove (`swing`,
`swing-by`, `brak`), rotation (`iter`, `iter-back`, `palindrome`, `rot`), rotating
transforms (`chunk`, `chunk-back`, `fast-chunk`), meters (`polymeter`, `timecat`) and the
Tidal `every` variants `first-of`/`last-of`. The mini-notation gets the matching syntax
(`!`, `.`, `,`, `{ }%n`, `(k,n,r)`). This phase also fixes two existing bugs found while
writing the spec: `euclidean` currently puts all k hits at the start of the cycle, and
mini-notation sub-sequences always play cycle 0 of their children.

```lisp
;; Before
(seq :bd :_ :bd :bd)
(euclidean 3 8 :bd)                          ; today: bd bd bd ~ ~ ~ ~ ~  (bug)

;; After
(struct "t ~ t t" (pure :bd))                ; onsets 0, 1/2, 3/4
(euclidean 3 8 :bd)                          ; bd ~ ~ bd ~ ~ bd ~  (Tidal bjorklund)
(euclidean 3 8 (seq :bd :sd))                ; restructure a pattern: bd bd sd
(ply 2 (seq :bd :sd))                        ; bd bd sd sd
(chunk 4 (add 10) (seq 0 1 2 3))             ; transform a rotating quarter
(iter 4 (seq :a :b :c :d))                   ; a b c d | b c d a | c d a b | …
(swing 4 (fast 8 (pure :hh)))                ; every 2nd hat of each quarter delayed by 1/12
(polymeter [:bd :sd :hh] [:c3 :e3])          ; 3 against 2
(timecat [3 :e3] [1 :g3])                    ; e3 for 3/4 of the cycle, g3 for 1/4
(~ "bd!3 sd . hh hh hh, cp(3,8)")            ; new mini-notation
```

## Decisions (resolving the outline's open questions)

| Question | Decision |
|---|---|
| Batch size | The TL3-tagged parity rows give **51** functions: 43 Time & structure rows (41 📋 + the 2 🟡 euclid rows, which are one function, `euclidean`) = 42, the 2 `binary*` rows moved from TL2 (`binary binary-n binary-l`) = 3, 4 Tidal-only rows (`rot sew stitch trunc`), plus `first-of`/`last-of` = 2. That exceeds ~25, so the phase is **split**. **TL3** (this spec) = the 25 core-rhythm functions listed under *Function list* below. **TL3b** ([PROMPTS/PHASE-TL3b.md](PHASE-TL3b.md), outline) = the zoom/compress family and the remaining structure extras: `compress zoom focus fast-gap linger bite ribbon hurry revv repeat-cycles trunc press press-by chunk-into chunk-back-into euclidish ply-for-each filter-events filter-when bypass beat sew stitch binary binary-n binary-l`. The parity-doc rows of the moved functions are tagged **TL3b**. |
| `every` / `first-of` / `last-of` | **Add both names, keep `every` unchanged.** `first-of` is an exact synonym of `every` (cycles where `c mod n = 0`); `last-of` applies on cycles where `c mod n = n-1` (Tidal `_lastOf`). The parity row `lastOf` moves from TL4 to TL3. |
| Mini-notation `_` | **Decided (2026-10-26):** `_` stays a rest, as do `~` and `:_`. Elongation is `@`. Documented as a deliberate parity difference in USAGE (mini-notation section) and in the parity doc (row already says so). |
| Outline names not in the parity doc | Dropped from TL3: `nudge` (parity row is a TL7 control parameter), `euclid-full` (no parity row; write `(stack (euclidean k n a) (euclidean (- k) n b))`), `euclid-off` (parity: 🔁 `euclidean` rotation argument), `polymeter-steps` (parity: 🔁 `polymeter`; covered by the optional leading steps argument), `when-mod`/`whenmod` (parity: TL4), "`range`-free helpers" (no parity row). |
| Bjorklund algorithm | The current `euclidean*` loop returns immediately (`(= (count groups) n)` is true on the first iteration), so `(euclidean 3 8 :bd)` gives `x x x . . . . .`. **Replace it with a literal port of Tidal's `bjorklund`** (Haskell source in step 2). Expected outputs: (2,5) `x.x..`, (3,8) `x..x..x.`, (5,8) `x.xx.xx.`, (7,12) `x.xx.x.xx.x.`. This changes the sound of existing code that uses `euclidean`; call it out in the ROADMAP delivered notes and in USAGE. |
| Euclid rotation direction | **Tidal's:** rotation `r` rotates the boolean list **left** (events move earlier), as Tidal `euclidOff`/mini `bd(3,8,2)` do. This is the current REPuLse direction; keep it and add a test. |
| Strudel `euclid(k,n)` (restructure a pattern) | **No new name.** `euclidean` dispatches on its third argument: a **pattern** → Strudel `euclid` semantics, i.e. `(struct <bjorklund booleans> pat)` (no events on false steps); any other value → the existing value form (a `seq*` of `val` and `:_` rests). Tidal's `_euclid` uses `fastcat` copies instead of `struct`; for `pure` inputs the two agree, and Strudel's `struct` form is the documented intent of the parity row. |
| Negative k, k > n, n ≤ 0 | Tidal: negative `k` gives the inverted rhythm (`bjorklund (-k, n)` with booleans flipped). `k > n` clamps to `n` (all steps hit). `n ≤ 0` or `k = 0` → all-false (value form: `n` rests; struct form: silence). |
| Boolean values | A step counts as **false** when its value is one of `false nil 0 :_ :f :false`; everything else is true (`true 1 :t :x …`). So `"t ~ t t"`, `"1 0 1 1"`, `"x ~ x x"` and `(seq true false true true)` all work. Implemented once as `core/step-true?`. |
| `struct` highlighting | The result keeps the `:source` of the **value** event (the sound that plays), falling back to the boolean event's source. `mask` keeps the value pattern's source (that is already what `app-left` does when the value pattern is the left argument). |
| `swing` signature | **Tidal's:** `(swing n pat)` = `(swing-by 1/3 n pat)`; `(swing-by x n pat)` = Tidal `swingBy x n = inside n (within (0.5,1) (x ~>))`. The outline's `(swing 1/3 (fast 8 (seq :hh)))` used the wrong argument; the reference vectors below use `(swing-by 1/3 4 (fast 8 (pure :hh)))` (the Tidal doc example `swingBy (1/3) 4 $ sound "hh*8"`). |
| Cycle-holding (`iter`, `iter-back`, `palindrome`, `chunk`, `chunk-back`) | **Tidal `cat` semantics.** These are defined in Tidal/Strudel as `cat`/`slowcat` of n variants, and Tidal's `cat` plays each variant at source cycle `floor(c / n)` (variant `i` at result cycle `c` is queried at `(c - i) div n`). So a cycle-varying input is *held* for n cycles. Implement this once as private `slowcat-held*`. REPuLse's public `cat` (`cat*`, which plays every child at the actual cycle) is **not** changed. `fast-chunk` (Strudel `fastChunk`) is the non-holding variant. |
| `chunk-back` order | **Strudel `chunkBack`** (the function is named after Strudel): cycle 0 transforms chunk 0, then chunk n-1, n-2, …, 1, i.e. chunk index `(mod (- c) n)`. (Tidal's `chunk'` starts at chunk n-1; not used.) Holding as in `chunk`. |
| `chunk` window | Tidal `within` (filter by whole start), not Strudel's `when` (time-window inner-join). |
| `euclid-legato` / `euclid-legato-rot` | Each hit lasts until the next hit. The **last hit lasts to the end of the cycle** (no wrap-around). With rotation, steps **before the first hit are a rest** (Strudel's implementation silently drops them and stretches the rest; that quirk is not copied). |
| `rot` and rests | REPuLse rests are events with value `:_`; Tidal rests are absent. To keep Tidal's "rotate values, keep structure", `:_` events and analog events are **not rotated** and keep their place; only the other events' values rotate. |
| `ply-with` copies | Strudel `plyWith`: `max(1, ceil(n))` copies, copy `i` = `f` applied `i` times, squeezed into each event (`squeeze-join` of a fastcat). |
| Function arguments from Lisp | Lisp closures may return `SourcedVal`s; every function argument `f` is wrapped as `(fn [p] (u/unwrap (f p)))` before it reaches `core`. |
| Positional vs tuple args | Tidal tuples become positional args: `within (0, 0.5) f p` → `(within 0 1/2 f p)`; `timecat [(3, a), (1, b)]` → `(timecat [3 a] [1 b])`. |
| `polymeter` argument shape | `(polymeter [:a :b :c] [:d :e])` — each **vector** is one layer of steps (values or patterns). An optional leading non-vector (integer, mini-notation string or pattern) is the steps-per-cycle (`polymeterSteps`, Tidal `{…}%n`); default = the first layer's step count. A rational steps value is not supported (a rational literal is a 2-vector and would be read as a layer). |
| Edge values | Integer-valued args (`ply-with` copies, `iter`, `chunk*`, `rot`, `first-of`, `last-of`, euclid k/n/r) are truncated with `int`. `iter`/`iter-back`/`chunk`/`chunk-back`/`fast-chunk`/`first-of`/`last-of` with n < 1 return `pat` unchanged. `ply` with n ≤ 0 → silence. `polymeter` steps ≤ 0 → silence. `timecat` entries with weight ≤ 0 are dropped; no entries → silence. |
| Patternable arguments | Via TL1's `patternify`/`patternify2`/`patternify3` (Tidal `tParam`, inner-join) and `u/pat-arg` in Lisp (strings → mini-notation). See the *Patterned?* column of the function list. `struct`/`mask` take a pattern argument by nature; `brak`, `palindrome`, `invert` have no numeric argument; `timecat` weights are structural and stay scalars. |
| Mini-notation extensions | `mini.cljs` lacks all of `!`, `.`, `,`, `{ }`/`%`, `( , , )` (checked: the tokeniser treats `, ! . % ( ) { }` as word characters). All five are in scope. `,` is a TL1 parity row that TL1 never delivered; TL3 delivers it because `{a b, c d}` needs it. `/n` (slow) is also still missing but stays a TL1 row and is **not** in scope. |
| Mini sequences | `seq-of-pats` / `weighted-seq*` query every child over `[0,1)` regardless of the cycle and convert times through floats (`float->rat`, denominator 100 000). They are **replaced by `core/timecat`**, which uses exact rationals and plays child cycle c in result cycle c (Tidal `fastcat`/`timeCat`). Side effect (bug fix, document it): `"<a b> c"` now alternates; before, it always played `a`. |
| `!` adjacency | `!` directly followed (no whitespace) by a number atom = replicate: `bd!3` is 3 steps. Any other `!` (`bd!`, `bd ! sd`) adds one copy of the previous step. |
| `.` grouping | Only a standalone `.` token (surrounded by whitespace or string ends) groups; `0.5` stays a number. Precedence: `,` (lowest) splits layers, then `.` splits groups, then spaces split steps. |
| `{ }%n` | `n` must be a number atom. Without `%`, steps = the first layer's step count. A layer's step count is the **sum of its step weights** (`@`), so `{a@2 b, c d e}` has 3 steps. |
| Euclid in mini-notation | `x(k,n)` and `x(k,n,r)` after any atom or group; each argument is a sub-sequence, so `bd(<3 5>,8)` is patterned. Compiles to `core/euclidean` with the compiled element as a pattern (struct form). |
| cljs.core name clashes | `chunk` and `iter` exist in `cljs.core`; add them to `(:refer-clojure :exclude [rand time chunk iter])` in `repulse.core`. If `npx shadow-cljs compile test` warns that another new name shadows a `cljs.core` var, add that name too. |

## Function list (TL3 — 25 functions)

All times are rational. "Patterned?" lists the arguments that accept a pattern or a
mini-notation string. Every function with a trailing `pat` also has a **curried form**:
called with all arguments except `pat`, it returns a transformer `(fn [p] …)` for `->>`.

| # | REPuLse-Lisp | Tidal / Strudel | Semantics | Patterned? |
|---|---|---|---|---|
| 1 | `(struct bools pat)` | `struct` | Structure from `bools`: for every true event of `bools`, the value(s) of `pat` over that event's whole (`app-left`); false/rest steps produce nothing. | `bools` |
| 2 | `(mask bools pat)` | `mask` | Structure from `pat`; keep only the parts of `pat`'s events that overlap true events of `bools` (`app-left` with `pat` on the left, false steps removed from `bools` first). | `bools` |
| 3 | `(invert pat)` | `invert` / `inv` | Boolean `not` on every non-rest value: true → `false`, false → `true`; `:_` stays `:_`. Output values are the booleans `true`/`false`. | — |
| 4 | `(euclidean k n x [r])` | `euclid`, `euclidRot`, Tidal `euclidOff` | Existing function, fixed and extended: Tidal `bjorklund` (k, n), rotated left by r. `x` a pattern → `struct` form; else value form (`x`/`:_` steps). | `k n r` (already) |
| 5 | `(euclid-legato k n pat)` | `euclidLegato` | Like the struct form, but each hit's whole extends to the next hit (last hit to cycle end). | `k n` |
| 6 | `(euclid-legato-rot k n r pat)` | `euclidLegatoRot` | `euclid-legato` on the rotated boolean list; steps before the first hit rest. | `k n r` |
| 7 | `(ply n pat)` | `ply` | Tidal `_ply n p = squeezeJoin $ _fast n . pure <$> p`: each event repeated n times inside its own whole. | `n` |
| 8 | `(ply-with n f pat)` | `plyWith` | Each event becomes `ceil(n)` sub-steps inside its whole; sub-step i has `f` applied i times. | `n` |
| 9 | `(inside n f pat)` | `inside` | `fast n (f (slow n pat))` — apply `f` as if each 1/n of a cycle were a cycle. | `n` |
| 10 | `(outside n f pat)` | `outside` | `inside (1/n) f pat` — apply `f` across n cycles. | `n` |
| 11 | `(within s e f pat)` | `within` | Stack of `f pat` events whose whole starts at cycle position in `[s, e)` and `pat` events whose whole starts outside it (Tidal `playWhen` on whole start). | `s e` |
| 12 | `(swing-by x n pat)` | `swingBy` | `inside n (within 1/2 1 (late x)) pat`: in each 1/n slice, events starting in the second half are delayed by x·(1/n) cycles. | `x n` |
| 13 | `(swing n pat)` | `swing` | `(swing-by 1/3 n pat)`. | `n` |
| 14 | `(brak pat)` | `brak` | Odd cycles: the cycle squeezed into its first half, then shifted right by 1/4 (Tidal: on odd cycles, rotR 1/4 of fastcat [x, silence]). Even cycles unchanged. | — |
| 15 | `(iter n pat)` | `iter` | Tidal `_iter`: slowcat of `p` shifted earlier by i/n for i = 0 … n-1 (cycle-holding). | `n` |
| 16 | `(iter-back n pat)` | `iterBack` / `iter'` | Same with `rotR` (`late`). | `n` |
| 17 | `(palindrome pat)` | `palindrome` | Tidal `slowAppend p (rev p)` = `slowcat [p, rev p]` (cycle-holding). | — |
| 18 | `(rot n pat)` | Tidal `rot` | Per cycle: sort the (non-rest) events by whole start, shift their values n places left (negative n: right), keep the structure. | `n` |
| 19 | `(chunk n f pat)` | `chunk` / `slowChunk` | Cycle c applies `f` (via `within`) to chunk `c mod n`, i.e. the window `[i/n, (i+1)/n)`; cycle-holding (Tidal `cat`). | `n` |
| 20 | `(chunk-back n f pat)` | `chunkBack` | As `chunk` with chunk index `(mod (- c) n)`: 0, n-1, …, 1. | `n` |
| 21 | `(fast-chunk n f pat)` | `fastChunk` | As `chunk` (index `c mod n`) without cycle-holding. | `n` |
| 22 | `(polymeter [steps] layer…)` | `polymeter`, `polymeterSteps`, mini `{…}%n` | Each layer of k steps is a fastcat of its steps, played at `fast (steps/k)`; layers stacked. | `steps` |
| 23 | `(timecat [w x]…)` | `timecat` / `timeCat` / `stepcat` | Weighted concatenation inside one cycle: entry i occupies `[Σw<i / W, Σw≤i / W)`, playing its cycle c in cycle c (Tidal `compressArc`). | — |
| 24 | `(first-of n f pat)` | `firstOf` | Exactly `every`: apply `f` when `c mod n = 0`. | `n` |
| 25 | `(last-of n f pat)` | `lastOf` | Apply `f` when `c mod n = n - 1`. | `n` |

Plus the mini-notation extensions `x!n`, `x !`, `a . b`, `a, b`, `{…}%n`, `x(k,n,r)`.

## Background

### Pattern engine — `packages/core/src/repulse/core.cljs`

- Rationals are `[n d]` vectors (`rat`, `rat+`, `rat-`, `rat*`, `rat-div`, `rat<`,
  `rat<=`, `rat-min`, `rat-max`, `int->rat`, `rat-neg`, `rat->float`); `->rat` coerces user
  input (ints, `[n d]`, floats → best rational, denominator ≤ 10⁶).
- `span`, `span-intersect`, `cycle-span`, `split-cycles`; private `per-cycle` lifts a
  one-cycle query fn to multi-cycle spans; private `map-spans` maps `:part` and (if
  present) `:whole` of an event; private `->pat` wraps non-patterns in `pure`; private
  `whole-or-part`, `with-source`.
- `pattern`, `pattern?`, `query`, `pure`, `seq*` (values only, not patterns), `stack*`,
  `fmap`, `silence`, `onset?`, `analog?`, `query-onsets`.
- TL1: `app-left` (structure from the left; queries the right over each left whole;
  part = intersection; keeps the left `:source`), `app-right`, `app-both`, `inner-join`,
  `outer-join`, `squeeze-join` (one cycle of the inner pattern squeezed into each outer
  whole), `patternify` (`(f arg pat)`), `patternify2` (`(f a b pat)`), `patternify3`
  (`(f a b c pat)`). Scalars take the fast path; patterned args are inner-joined.
- Private `fast*`, `slow*`, `late*` take scalar amounts; public `fast`, `slow`, `late`,
  `early` are patternified. `rev` is per-cycle. `every*`/`every`: `(zero? (mod cycle n))`.
- `euclidean*` (private, value form, **buggy Björklund loop**), `euclidean` (public,
  `patternify3` over k, n, rotation; rotation is a left rotation `(into (subvec v r) (subvec v 0 r))`).
- `cat*`: per-cycle, plays child `(mod c n)` **at cycle c** (no Tidal offset).
- TL2: `segment` (`app-left` of `(fast* n (pure true))`), signals with `:whole nil`.
- `ns` form: `(:refer-clojure :exclude [rand time])`.

### Mini-notation — `packages/lisp/src/repulse/lisp/mini.cljs`

- `tokenise`: whitespace-separated; `special?` = `[ ] < > * ? : @`. Every other
  character (including `, ! . % ( ) { }`) is part of an atom word. Atom tokens carry
  `:str-from`/`:str-to` (offsets inside the string).
- Parser: `parse-sequence` (stops at `]`, `>` or end), `parse-element` (`[ … ]` group with
  `* ? @` suffixes, `< … >` alternation, atom), `parse-atom` (atom + `* ? : @` suffixes).
  AST node types: `:atom` (`:value :weight :str-from :str-to`, optional
  `:sample-index`), `:seq` (`:children`), `:alt`, `:repeat` (`:child :times`), `:degrade`.
- `atom-value`: `~`/`_` → `:_`; `1/8` → exact `[1 8]`; numbers; note names and other
  words → keywords (`t` → `:t`, `f` → `:f`).
- Compiler `compile-ast`: `:seq` → `seq-of-pats` (unit weights) or `weighted-seq*`
  (`@` weights), both through `scale-events`, which **always queries the child over
  `[0 1)`** and converts via floats (`float->rat`); `:alt` → public `alt*` (used by the
  Lisp `alt` builtin); `:repeat` → `core/fast`; `:degrade` → private `degrade`
  (`Math/random`, TL4 replaces it).
- `parse` (public): `(parse s base-offset)`; `base-offset` = editor position after the
  opening quote, used for per-token `:source` ranges.
- Tests: `packages/lisp/src/repulse/lisp/mini_test.cljs` (`weight-simple` compares
  floats with tolerance; alternation tests are top-level only).

### Lisp — `packages/lisp/src/repulse/lisp/`

- `eval.cljs` `make-env` merges `b-pattern`, `b-signals`, `b-arith`, `b-math`, `b-music`,
  `b-params`, `b-collection`, `b-types`, `b-synth`, `b-arrangement` (later maps win;
  none of the TL3 names exist yet — checked with grep).
- `builtins/pattern.cljs`: `euclidean` passes `(u/unwrap v)` as the value, so a pattern
  third argument already reaches `core/euclidean` unchanged. `fmap` shows the wrapping of
  Lisp closures (`(fn [v] (u/unwrap (f v)))`). `every` passes `t` raw (keep it).
  `wchoose` shows how vector-of-pairs arguments are unwrapped.
- `util.cljs`: `u/unwrap`, `u/deep-unwrap`, `u/source-of`, `u/pat-arg` (strings →
  `mini/parse` with source offset; numbers, `[n d]` rationals and patterns pass raw).
- Reader: `1/4` → `[1 4]`; `true`/`false` literals exist. A rational and a 2-element
  number vector are the same value at runtime.
- Tests: `integration_test.cljs` (helpers `make-test-env`, `eval-pattern` (cycle 0
  events), `eval-result`, `event-values`; `euclidean-3-8` counts hits only;
  `tl1-documented-examples-evaluate`, `tl2-documented-examples-evaluate` evaluate every
  documented example). `eval_test.cljs`. Runner: `packages/core/src/repulse/test_runner.cljs`.
- `core_test.cljs` `euclidean-5-8`, `euclidean-3-8-rotation`, `euclidean-4-4`,
  `euclidean-patterned-k` only count hits / compare with itself, so they stay green after
  the Björklund fix.

### App, editor & AI docs

- `app/src/repulse/audio.cljs` triggers only `core/query-onsets` and skips `:_` values
  (`(= value :_) nil`), so non-onset fragments produced by `struct`, `mask`, `swing-by`,
  `rot` etc. are silent, and the whole's duration is used.
- Grammar `app/src/repulse/lisp-lang/repulse-lisp.grammar` `BuiltinName` (one name group
  per line); `npm run gen:grammar` regenerates `parser.js` (and `parser.terms.js`).
- `app/src/repulse/lisp-lang/completions.js` (`{ label, type, detail }`),
  `app/src/repulse/lisp-lang/hover.js` (`DOCS` map: `signature`, `description`,
  `example`), `app/src/repulse/content/builtin_meta.edn` (keyed by name; categories used:
  `"transform"` for `every`/`fast`…, `"source"` for `cat`/`stack`), `docs/ai/builtins.json`
  (generated by `npm run gen:ai-docs` from `scripts/gen_ai_docs.mjs`, which fails if a
  completions label has no meta entry).
- `e2e/audio-render.spec.ts`: `render(page, code)`, `countOnsets`, `mixMono`.

### Docs

- `docs/USAGE.md`: TOC, "Pattern combinators" (`euclidean`, `cat`, `late`/`early`, …),
  "Patterned arguments", "Pattern arithmetic", "Signals", "Mini-notation" (~line 1460,
  example block of supported syntax).
- `README.md` "Built-in pattern functions" table (~line 114).
- `docs/STRUDEL-PARITY.md`: "Time & structure — TL3 / TL3b" section (moved rows already tagged TL3b), `binary*` rows in the TL2
  section, `lastOf`/`firstOf` rows in the TL4 section, "Mini-notation" rows, "Tidal-only
  functions" (`rot`), summary table.
- `ROADMAP.md`: TL3 section and *Implementation order* table.

## Implementation

Work in this order; each step ends with `npm run test` green.

### 0. Verify tooling (CLAUDE.md Rule 4)

`npm run gen:grammar && npm run gen:ai-docs && npm run test` on the unmodified branch.

### 1. Helpers — `core.cljs`

Add to the `ns` exclude list: `(:refer-clojure :exclude [rand time chunk iter])`.

```clojure
(defn step-true?
  "Boolean reading of a step value: false for false nil 0 :_ :f :false."
  [v]
  (not (contains? #{false nil 0 :_ :f :false} v)))

(defn- keep-values [pred pat]
  (pattern (fn [sp] (filter #(pred (:value %)) (query pat sp)))))

(defn- cycle-of [sp] (int (Math/floor (rat->float (:start sp)))))

(defn- cycle-pos-rat
  "Exact position of rational t inside its cycle, in [0, 1)."
  [[n d]] (rat (mod n d) d))

(defn- shift-event [ev off]
  (map-spans #(span (rat+ (:start %) off) (rat+ (:end %) off)) ev))
```

**`compress*`** (private; Tidal `compressArc`, TL3b exposes it as `compress`): cycle c of
`pat` squeezed into `[c+s, c+e)` of cycle c. `s`, `e` rationals with `0 ≤ s < e ≤ 1`,
otherwise `silence`.

```clojure
(defn- compress* [s e pat]
  (let [s (->rat s) e (->rat e) d (rat- e s)]
    (if (or (rat< s [0 1]) (rat> e [1 1]) (rat<= e s))
      silence
      (pattern
       (per-cycle
        (fn [piece]
          (let [c   (int->rat (cycle-of piece))
                win (span-intersect piece (span (rat+ c s) (rat+ c e)))]
            (when win
              (let [to-inner (fn [t] (rat+ c (rat-div (rat- t (rat+ c s)) d)))
                    to-outer (fn [t] (rat+ (rat+ c s) (rat* (rat- t c) d)))
                    in-sp    (span (to-inner (:start win)) (to-inner (:end win)))]
                (keep (fn [ev]
                        (let [ev' (map-spans #(span (to-outer (:start %)) (to-outer (:end %))) ev)]
                          (when-let [p (span-intersect (:part ev') win)]
                            (assoc ev' :part p))))
                      (query pat in-sp)))))))))))
```

**`slowcat-held*`** (private; Tidal `cat` offsets): result cycle c plays pattern
`(pat-of c)` at source cycle `floor(c/n)`.

```clojure
(defn- slowcat-held* [n pat-of]
  (pattern
   (per-cycle
    (fn [sp]
      (let [c     (cycle-of sp)
            delta (int->rat (- c (Math/floor (/ c n))))
            p     (pat-of c)]
        (map #(shift-event % delta)
             (query p (span (rat- (:start sp) delta) (rat- (:end sp) delta)))))))))
```

**`within*`** (private) and **`fastcat*`** (private):

```clojure
(defn- within* [s e f pat]
  (let [s  (->rat s) e (->rat e)
        fp (f pat)                                   ; apply f once, not per query
        in? (fn [ev] (let [p (cycle-pos-rat (:start (whole-or-part ev)))]
                       (and (rat<= s p) (rat< p e))))]
    (stack* [(pattern (fn [sp] (filter in? (query fp sp))))
             (pattern (fn [sp] (remove in? (query pat sp))))])))

(defn- fastcat* [xs] (timecat (mapv (fn [x] [1 x]) xs)))   ; after timecat is defined
```

### 2. Björklund + Euclid — `core.cljs`

Replace the loop in `euclidean*` with a public `bjorklund` that is a literal port of
Tidal's:

```haskell
bjorklund (i,j') = let j = j' - i; x = replicate i [True]; y = replicate j [False]
                       (_,x',y') = bjorklund' ((i,j),(x,y)) in concat x' ++ concat y'
left  ((i,j),(xs,ys)) = let (xs',xs'') = splitAt j xs in ((j,i-j),(zipWith (++) xs' ys, xs''))
right ((i,j),(xs,ys)) = let (ys',ys'') = splitAt i ys in ((i,j-i),(zipWith (++) xs ys', ys''))
bjorklund' (n,x) = let (i,j) = n in if min i j <= 1 then (n,x)
                   else bjorklund' (if i > j then left (n,x) else right (n,x))
```

```clojure
(defn bjorklund
  "Tidal's bjorklund: vector of n booleans with k trues spread evenly.
   Negative k inverts; k > n clamps to n; n <= 0 → []."
  [k n]
  (let [k (int k) n (int n)]
    (cond
      (<= n 0) []
      (neg? k) (mapv not (bjorklund (- k) n))
      :else
      (let [k (min k n)]
        (loop [i k j (- n k) xs (vec (repeat k [true])) ys (vec (repeat (- n k) [false]))]
          (if (<= (min i j) 1)
            (vec (concat (apply concat xs) (apply concat ys)))
            (if (> i j)
              (recur j (- i j) (mapv into (subvec xs 0 j) ys) (subvec xs j))
              (recur i (- j i) (mapv into xs (subvec ys 0 i)) (subvec ys i)))))))))
```

(Check: `(mapv into a b)` zips pairwise and stops at the shorter vector, like `zipWith`.
`(bjorklund 0 n)`: `min 0 n ≤ 1` → n falses.)

- Private `euclid-bools [k n r]` = `(bjorklund k n)` rotated left by `(mod r n)` (empty if n ≤ 0).
- `euclidean*` value form: `(seq* (mapv #(if % val :_) (euclid-bools k n r)))`; when the
  bools are empty return `silence`.
- Struct form: `(struct (seq* (euclid-bools k n r)) pat)` (empty bools → `silence`).
- `euclidean`: keep the 3/4-arity signature and `patternify3`; the inner fn picks the
  struct form when `(pattern? val)`, else the value form.
- `euclid-legato* [k n r pat]`: bools → leading falses count `lead`, then one
  `[len true]` per hit where `len` = steps until the next hit (or the cycle end). Build
  `(timecat (into (if (pos? lead) [[lead false]] []) hits))` and `struct` `pat` with it.
  No hit → `silence`. Public `euclid-legato` = `(patternify2 (fn [k n p] (euclid-legato* k n 0 p)))`,
  `euclid-legato-rot` = `patternify3` over k n r.

### 3. Boolean structure — `core.cljs`

```clojure
(defn struct [bools pat]
  (let [b (keep-values step-true? (->pat bools))]
    (pattern
     (fn [sp]
       (for [eb (query b sp)
             ev (query pat (whole-or-part eb))
             :let [p (span-intersect (:part eb) (:part ev))]
             :when p]
         (with-source (event (:value ev) (:whole eb) p)
                      (or (:source ev) (:source eb))))))))

(defn mask [bools pat]
  (app-left (fn [v _] v) pat (keep-values step-true? (->pat bools))))

(defn invert [pat]
  (fmap #(if (= :_ %) % (not (step-true? %))) pat))
```

### 4. Repetition — `core.cljs`

`squeeze-join` keeps the **inner** event's keys, so the inner `pure` must carry the
outer event's `:source` (otherwise highlighting is lost). Hence a private
`fmap-event` (like `fmap`, but `f` receives the whole event):

```clojure
(defn- fmap-event [f pat]
  (pattern (fn [sp] (map #(assoc % :value (f %)) (query pat sp)))))

(defn- ply* [n pat]
  (squeeze-join (fmap-event #(fast* n (pure (:value %) (:source %))) pat)))
(defn ply [n pat] ((patternify ply*) n pat))

(defn- ply-with* [n f pat]
  (let [k (max 1 (int (Math/ceil (rat->float (->rat n)))))]
    (squeeze-join
     (fmap-event #(fastcat* (vec (take k (iterate f (pure (:value %) (:source %)))))) pat))))
(defn ply-with [n f pat] ((patternify (fn [n' p] (ply-with* n' f p))) n pat))
```

### 5. Windows & groove — `core.cljs`

```clojure
(defn- inside* [n f pat] (fast* n (f (slow* n pat))))
(defn inside  [n f pat] ((patternify (fn [n' p] (inside* n' f p))) n pat))
(defn outside [n f pat]
  ((patternify (fn [n' p] (let [r (->rat n')]
                            (if (zero? (first r)) silence (inside* (rat-div [1 1] r) f p)))))
   n pat))
(defn within  [s e f pat] ((patternify2 (fn [s' e' p] (within* s' e' f p))) s e pat))

(defn- swing-by* [x n pat] (inside* n #(within* [1 2] [1 1] (fn [q] (late* x q)) %) pat))
(defn swing-by [x n pat] ((patternify2 swing-by*) x n pat))
(defn swing [n pat] ((patternify (fn [n' p] (swing-by* [1 3] n' p))) n pat))

(defn brak [pat]
  (let [odd-pat (late* [1 4] (compress* [0 1] [1 2] pat))]
    (pattern (per-cycle (fn [sp] (query (if (odd? (cycle-of sp)) odd-pat pat) sp))))))
```

`inside` with n = 0 is silence already (`fast*`/`slow*` return `silence` for 0).

### 6. Rotation — `core.cljs`

```clojure
(defn- iter* [n pat back?]
  (let [n (int (rat->float (->rat n)))]
    (if (< n 1) pat
        (slowcat-held* n (fn [c] (let [amt (rat (mod c n) n)]
                                   (if back? (late* amt pat) (late* (rat-neg amt) pat))))))))
(defn iter      [n pat] ((patternify #(iter* %1 %2 false)) n pat))
(defn iter-back [n pat] ((patternify #(iter* %1 %2 true)) n pat))
(defn palindrome [pat] (let [r (rev pat)] (slowcat-held* 2 #(if (even? %) pat r))))
```

`rot*` (Tidal `_rot`): per cycle piece `sp` of cycle c, `evs` = `(query pat (cycle-span c))`.
Split into rotatable (`(and (:whole e) (not= :_ (:value e)))`) and fixed. Sort rotatable by
`[(:start (:whole e)) (:start (:part e))]` using a rational comparator
(`#(cond (rat< a b) -1 (rat< b a) 1 :else 0)`). With `vs` = their values and
`m` = count, event j gets value `(nth vs (mod (+ j n) m))`. Concatenate with the fixed
events, then keep only events whose `:part` intersects `sp`, with `:part` clipped to it.
`rot` = `(patternify rot*)`; n truncated with `int`; `m = 0` → no rotation.

### 7. Chunks — `core.cljs`

```clojure
(defn- chunk* [n f pat idx-fn hold?]
  (let [n (int (rat->float (->rat n)))]
    (if (< n 1) pat
      (let [variant (fn [c] (let [i (idx-fn c n)] (within* (rat i n) (rat (inc i) n) f pat)))]
        (if hold?
          (slowcat-held* n variant)
          (pattern (per-cycle (fn [sp] (query (variant (cycle-of sp)) sp)))))))))
(defn chunk      [n f pat] ((patternify (fn [n' p] (chunk* n' f p #(mod %1 %2) true))) n pat))
(defn chunk-back [n f pat] ((patternify (fn [n' p] (chunk* n' f p #(mod (- %1) %2) true))) n pat))
(defn fast-chunk [n f pat] ((patternify (fn [n' p] (chunk* n' f p #(mod %1 %2) false))) n pat))
```

### 8. Meters & conditionals — `core.cljs`

```clojure
(defn timecat
  "pairs: [[weight x] …]; x a value or a pattern. Tidal timeCat."
  [pairs]
  (let [pairs (->> pairs (map (fn [[w x]] [(->rat w) (->pat x)]))
                   (filter #(rat< [0 1] (first %))) vec)
        total (reduce rat+ [0 1] (map first pairs))]
    (if (empty? pairs) silence
      (stack* (second (reduce (fn [[acc out] [w p]]
                                (let [nxt (rat+ acc w)]
                                  [nxt (conj out (compress* (rat-div acc total) (rat-div nxt total) p))]))
                              [[0 1] []] pairs))))))

(defn polymeter-pats
  "pairs: [[pat step-count] …]. steps nil → first layer's count; number or pattern."
  [steps pairs] …)
(defn polymeter
  "layers: [[x …] …] step vectors (values or patterns)."
  [steps layers] (polymeter-pats steps (keep #(when (seq %) [(fastcat* %) (count %)]) layers)))
```

`polymeter-pats`: drop pairs with step count ≤ 0; no pairs → `silence`. Private scalar
path `(poly* st pairs)` = `(stack* (map (fn [[p k]] (fast* (rat-div (->rat st) (->rat k)) p)) pairs))`,
`silence` when `st` ≤ 0. `steps` nil → `(poly* (second (first pairs)) pairs)`; a number →
`(poly* steps pairs)`; a pattern → `(inner-join (fmap #(poly* % pairs) steps))` (the same
inner-join that `patternify` uses).

```clojure
(defn first-of [n f pat] (every n f pat))
(defn- last-of* [n f pat]
  (let [n (int (rat->float (->rat n)))]
    (if (< n 1) pat
      (pattern (per-cycle (fn [sp] (query (if (= (mod (cycle-of sp) n) (dec n)) (f pat) pat) sp)))))))
(defn last-of [n f pat] ((patternify (fn [n' p] (last-of* n' f p))) n pat))
```

Order inside `core.cljs`: helpers (step 1) → `timecat` → `fastcat*` → the rest, so
everything is defined before use (`declare` where needed).

### 9. Mini-notation — `mini.cljs`

**Tokeniser.** Add to `special?`: `\, \! \% \( \) \{ \}` and emit tokens `:comma`,
`:bang` (with `:str-from` = its index), `:percent`, `:open-paren`, `:close-paren`,
`:open-brace`, `:close-brace`. After word accumulation, a word whose text is exactly
`"."` becomes `{:type :dot}` (so `0.5`, `1.5` stay numbers).

**Parser.** Replace `parse-sequence` by three levels:

```
layers   = sequence ("," sequence)*        → {:type :stack :layers [...]} (1 layer → that :seq)
sequence = steps, split on :dot tokens      → {:type :seq :children [...]};
           with dots each group is a :seq child of weight 1
step     = element suffix*  |  :bang (bare, not followed by an adjacent number)
element  = "[" layers "]" | "<" layers ">" | "{" layers "}" ("%" number)? | atom
suffix   = "*" n | "?" | ":" n (atoms only) | "@" n | "!" n (adjacent) | "(" seq "," seq ["," seq] ")"
```

- Stop tokens for `sequence`: `:close-bracket :close-angle :close-brace :close-paren :comma`, end.
- `!n` replicates the step n times (n ≥ 1, `int`); bare `!` or `!` not directly followed
  by a number atom (adjacency: next token's `:str-from` = bang's `:str-from` + 1 and the
  text parses as a number) appends one more copy of the previous step. A bare `!` at the
  start of a sequence → `ex-info "Nothing to repeat before !" {:type :mini-parse-error}`.
- Unify the suffix loop for atoms and groups in one `parse-suffixes` fn (keep `:` atom-only).
- `< layers >` with several layers → `{:type :stack :layers [alt …]}` (stack of alternations).
- `{ … }` → `{:type :polymeter :layers [seq-node …] :steps n-or-nil}`.
- `x(k,n[,r])` → `{:type :euclid :child node :args [seq-node seq-node seq-node?]}`.
- Missing closers throw `{:type :mini-parse-error}` with "Expected ]", "Expected >",
  "Expected }", "Expected )".

**Compiler.**

- `:seq` with ≥ 2 children → `(core/timecat (mapv (fn [c] [(:weight c 1) (compile-ast c base-offset)]) children))`.
  Remove `scale-events`, `seq-of-pats`, `weighted-seq*` and `float->rat`. Keep `alt*`.
- `:stack` → `(core/stack* (mapv #(compile-ast % base-offset) layers))`.
- `:polymeter` → `(core/polymeter-pats steps (mapv (fn [l] [(compile-ast l base-offset) (step-count l)]) layers))`
  where `step-count` = sum of the layer's children weights (1 for a single non-seq node).
- `:euclid` → `(core/euclidean k n (compile-ast child) r)` where each arg node that is a
  single numeric `:atom` compiles to its number, otherwise to its pattern; `r` defaults to 0.

### 10. Lisp builtins — new `packages/lisp/src/repulse/lisp/builtins/structure.cljs`

`(ns repulse.lisp.builtins.structure (:require [repulse.core :as core] [repulse.lisp.util :as u]))`;
register `[repulse.lisp.builtins.structure :as b-structure]` in `eval.cljs` and merge
`(b-structure/make-builtins)` right after `(b-signals/make-builtins)` in `make-env`.

```clojure
(defn- xf [f] (let [f (u/unwrap f)] (fn [p] (u/unwrap (f p)))))
(defn- step->pat [x]
  (let [v (u/unwrap x)] (if (core/pattern? v) v (core/pure v (u/source-of x)))))
```

| Name | Arities (curried form first) |
|---|---|
| `struct`, `mask` | `[b]`, `[b p]` — `b` via `u/pat-arg` |
| `invert`, `brak`, `palindrome` | `[p]` |
| `ply`, `swing`, `iter`, `iter-back`, `rot` | `[n]`, `[n p]` — `n` via `u/pat-arg` |
| `ply-with`, `inside`, `outside`, `chunk`, `chunk-back`, `fast-chunk`, `first-of`, `last-of` | `[n f]`, `[n f p]` — `n` via `u/pat-arg`, `f` via `xf` |
| `within` | `[s e f]`, `[s e f p]` |
| `swing-by`, `euclid-legato` | `[a b]`, `[a b p]` |
| `euclid-legato-rot` | `[k n r]`, `[k n r p]` |
| `timecat` | `[& pairs]`: each pair `(u/unwrap pair)` → `[(u/unwrap w) (step->pat x)]` |
| `polymeter` | `[& args]`: if the first arg (unwrapped) is not a vector it is `steps` (`u/pat-arg`), the rest are layers; each layer `(mapv step->pat (u/unwrap layer))` → `core/polymeter` |

`p` is always `(u/unwrap p)`. `euclidean` in `builtins/pattern.cljs` is unchanged (the
pattern third argument already passes through).

### 11. Tests

- `packages/core/src/repulse/core_test.cljs`: one `deftest` per DoD vector below (use
  `query-onsets` where the vector says "onsets"; compare `[value whole]` pairs sorted by
  whole start unless stated). Add `bjorklund` table tests.
- `packages/lisp/src/repulse/lisp/mini_test.cljs`: the mini-notation DoD vectors.
  `weight-simple` keeps passing (exact values satisfy the tolerance). If any existing
  assertion encodes a float approximation (e.g. `33333/100000`), change it to the exact
  rational and say so in the PR.
- `packages/lisp/src/repulse/lisp/integration_test.cljs`: Lisp-level DoD expressions,
  curried forms in `->>`, and `tl3-documented-examples-evaluate` (every TL3 example from
  `builtin_meta.edn`/USAGE evaluates to a pattern).
- `e2e/audio-render.spec.ts`: `(struct "t ~ t t" (pure :bd))` → 3 onsets;
  `(euclidean 3 8 :bd)` → 3 onsets; `(ply 2 (seq :bd :sd))` → 4 onsets.

### 12. Registration (CLAUDE.md Rule 3)

New `BuiltinName`s, on their own grammar line: `struct mask invert euclid-legato
euclid-legato-rot ply ply-with inside outside within swing-by swing brak iter iter-back
palindrome rot chunk chunk-back fast-chunk polymeter timecat first-of last-of`
(`euclidean` exists). Run `npm run gen:grammar` and check that `swing-by`, `iter-back`,
`chunk-back`, `ply-with`, `euclid-legato-rot` and `first-of` each highlight as **one**
token and a user symbol like `structure` or `rotate` stays a `Symbol`. Then:
`completions.js` entries (`type: "function"`), `hover.js` `DOCS` entries, `builtin_meta.edn`
entries (category `"transform"`, except `polymeter`/`timecat` → `"source"`;
`:returns "Pattern"`, `:side-effects []`, ≥ 1 example (one patterned example for every
patterned function), `:see-also`), update the `:euclidean` entry (struct form +
`(euclidean 3 8 (seq :bd :sd))` example) and the `~` entry/hover (new syntax),
`npm run gen:ai-docs`. Commit grammar, `parser.js`, `parser.terms.js`, `completions.js`,
`hover.js`, `builtin_meta.edn`, `docs/ai/builtins.json` together.

### 13. Docs

- `docs/USAGE.md`: new section **"Time & structure — `struct` `ply` `chunk` `iter` `swing` `polymeter`"**
  after "Signals" (TOC entry under "Pattern combinators") with a reference table (all 25
  rows: signature, meaning, patterned args) and examples; boolean-value rule; the
  cycle-holding note for `iter`/`chunk`/`palindrome`; `euclidean` section: fixed
  distribution (old vs new for (3,8)), struct form, rotation direction, negative k;
  "Mini-notation" section: examples for `!`, `.`, `,`, `{ }%n`, `(k,n,r)`, the note that
  `"<a b> c"` now alternates, and a sentence that `_` is a rest (use `@` to elongate —
  deliberate difference from Strudel).
- `README.md`: rows `| (struct "t ~ t t" (pure :bd)) | Boolean rhythm |`,
  `| (ply 2 pat) / (chunk 4 f pat) / (iter 4 pat) | Repeat, rotating transform, rotate start |`,
  `| (swing 4 pat) / (polymeter [:a :b :c] [:d :e]) | Groove, polymeter |`.
- `docs/STRUDEL-PARITY.md`: ✅ for the 23 TL3 rows (`struct mask euclid euclidRot
  euclidLegato euclidLegatoRot ply plyWith inside outside swingBy swing invert brak
  palindrome iter iterBack chunk chunkBack fastChunk within polymeter stepcat/timecat`),
  Tidal-only `rot` ✅; `firstOf` rows → REPuLse `first-of`/`every` ✅; `lastOf` → ✅ TL3;
  mini-notation `!`, `{ }`, `(k,n,r)`, `.` ✅ and `,` ✅ (note "delivered in TL3"). Notes:
  `euclid` "struct form when the 3rd arg is a pattern", `euclidRot` "Tidal direction
  (rotate left)", `chunk`/`iter` "Tidal cat cycle-holding", `chunkBack` "Strudel order".
  Recompute the summary table counts.
- `ROADMAP.md`: TL3 → `✅ *delivered*` with a **Delivered:** list (TL2 style) that
  mentions the Björklund fix and the mini-notation nested-alternation fix.

## Files to change

| File | Change |
|---|---|
| `packages/core/src/repulse/core.cljs` | exclude `chunk iter`; helpers; `bjorklund`, fixed `euclidean*`, struct form; `struct mask invert euclid-legato euclid-legato-rot ply ply-with inside outside within swing-by swing brak iter iter-back palindrome rot chunk chunk-back fast-chunk timecat polymeter polymeter-pats first-of last-of`; private `compress* slowcat-held* within* fastcat*` |
| `packages/core/src/repulse/core_test.cljs` | DoD vectors |
| `packages/lisp/src/repulse/lisp/mini.cljs` | Tokeniser, 3-level parser, `!` `.` `,` `{}%n` `(k,n,r)`, `:seq` via `core/timecat`; remove float helpers |
| `packages/lisp/src/repulse/lisp/mini_test.cljs` | Mini DoD vectors |
| `packages/lisp/src/repulse/lisp/builtins/structure.cljs` (new) | Lisp builtins |
| `packages/lisp/src/repulse/lisp/eval.cljs` | Require + merge `b-structure` |
| `packages/lisp/src/repulse/lisp/integration_test.cljs` | Lisp DoD + `tl3-documented-examples-evaluate` |
| `e2e/audio-render.spec.ts` | 3 onset-count tests |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` + `parser.js` + `parser.terms.js` | 24 names (`npm run gen:grammar`) |
| `app/src/repulse/lisp-lang/completions.js`, `hover.js` | New entries; `euclidean`, `~` updated |
| `app/src/repulse/content/builtin_meta.edn` + `docs/ai/builtins.json` | New entries; `:euclidean` updated (`npm run gen:ai-docs`) |
| `docs/USAGE.md`, `README.md`, `docs/STRUDEL-PARITY.md`, `ROADMAP.md` | Step 13 |

## Definition of done

"Cycle c" means `(query pat (cycle-span c))`; "onsets" means `query-onsets`. Events are
written `value [whole-start, whole-end)`; whole = part unless a part is given. All
values are exact rationals.

**Bug fixes**
- [ ] `(bjorklund 2 5)` → `[true false true false false]`; `(bjorklund 3 8)` → `x..x..x.`;
      `(bjorklund 5 8)` → `x.xx.xx.`; `(bjorklund 7 12)` → `x.xx.x.xx.x.`; `(bjorklund 0 4)` → 4 falses;
      `(bjorklund -3 8)` → `.xx.xx.x`; `(bjorklund 9 8)` → 8 trues.
- [ ] `(euclidean 3 8 :x)` cycle 0 values `:x :_ :_ :x :_ :_ :x :_`; `(euclidean 3 8 :x 2)` →
      `:_ :x :_ :_ :x :_ :x :_` (left rotation); `(euclidean 5 8 :x)` → `x _ x x _ x x _`.
- [ ] `(~ "<a b> c")` cycle 1 → `b [1,3/2)`, `c [3/2,2)`; `(~ "a b c")` cycle 0 wholes are
      exactly `[0 1]–[1 3]`, `[1 3]–[2 3]`, `[2 3]–[1 1]`.

**Functions** (one `deftest` each; Tidal doc examples where they exist)
- [ ] `struct`: `(struct "t ~ t t" (pure :bd))` → `bd [0,1/4)`, `bd [1/2,3/4)`, `bd [3/4,1)`;
      `(struct (seq true false true) (seq :a :b))` → `a [0,1/3)`, `b [2/3,1)`;
      `(struct "t" (seq :a :b))` → `a [0,1)` part `[0,1/2)` (onset) and `b [0,1)` part `[1/2,1)` (not an onset);
      `(struct (seq 1 0 1 1) (pure :bd))` → same onsets as the first example.
- [ ] `mask`: `(mask "1 0 1 1" (fast 8 (pure :hh)))` → 6 onsets at `0 1/8 1/2 5/8 3/4 7/8`;
      `(mask "t f" (seq :a :b :c))` → `a [0,1/3)` and `b [1/3,2/3)` with part `[1/3,1/2)`, no `c`.
- [ ] `invert`: `(invert (seq true false :_ 1))` values `false true :_ false`;
      `(struct (invert "t f t f") (pure :x))` → `x [1/4,1/2)`, `x [3/4,1)`.
- [ ] `euclidean` struct form: `(euclidean 3 8 (seq :bd :sd))` → `bd [0,1/8)`, `bd [3/8,1/2)`, `sd [3/4,7/8)`;
      `(euclidean 3 8 (pure :x) 2)` → `x` at `[1/8,1/4)`, `[1/2,5/8)`, `[3/4,7/8)`;
      `(euclidean -3 8 (pure :x))` → onsets `1/8 1/4 1/2 5/8 7/8`;
      patterned `(euclidean "<3 5>" 8 (pure :x))` cycle 1 → onsets `1 5/4 11/8 13/8 7/4`.
- [ ] `euclid-legato`: `(euclid-legato 3 8 (pure :x))` → `[0,3/8)`, `[3/8,3/4)`, `[3/4,1)`;
      `(euclid-legato 5 8 (pure :x))` → `[0,1/4)`, `[1/4,3/8)`, `[3/8,5/8)`, `[5/8,3/4)`, `[3/4,1)`;
      `(euclid-legato-rot 3 8 2 (pure :x))` → `[1/8,1/2)`, `[1/2,3/4)`, `[3/4,1)`;
      patterned `(euclid-legato "<3 5>" 8 (pure :x))` cycle 1 has 5 events.
- [ ] `ply`: `(ply 2 (seq :bd :sd))` → `bd [0,1/4)`, `bd [1/4,1/2)`, `sd [1/2,3/4)`, `sd [3/4,1)`;
      `(ply "<2 3>" (seq :a :b))` cycle 1 → `a` at `[1,7/6) [7/6,4/3) [4/3,3/2)`, `b` at `[3/2,5/3) [5/3,11/6) [11/6,2)`;
      `(ply (seq 1 2) (seq :a :b))` → `a [0,1/2)`, `b [1/2,3/4)`, `b [3/4,1)`;
      `:source` of `(ply 2 (~ "bd sd"))` events equals the source of the original tokens.
- [ ] `ply-with`: `(ply-with 3 (add 12) (seq 0 7))` → `0 [0,1/6)`, `12 [1/6,1/3)`, `24 [1/3,1/2)`,
      `7 [1/2,2/3)`, `19 [2/3,5/6)`, `31 [5/6,1)`; `(ply-with "<1 2>" (add 12) (seq 0 7))` cycle 1 has 4 events.
- [ ] `inside`/`outside`: `(inside 2 rev (seq 0 1 2 3 4 5 6 7))` values in time order `3 2 1 0 7 6 5 4`
      (Tidal doc); `(inside "<1 2>" rev (seq 0 1 2 3))` cycle 1 → `1 0 3 2`;
      `(outside 2 rev (cat (seq :a :b) (seq :c :d)))` cycle 0 → `d [0,1/2)`, `c [1/2,1)`; cycle 1 → `b [1,3/2)`, `a [3/2,2)`.
- [ ] `within`: `(within 0 1/2 (fast 2) (seq :a :b :c :d))` onsets `a 0`, `b 1/8`, `c 1/4`, `d 3/8`, `c 1/2`, `d 3/4`
      (wholes 1/8 long for the first four, 1/4 for the last two); `(within "<0 1/2>" 1 (add 10) (seq 0 1))`
      cycle 0 → `10 11`, cycle 1 → `0 11`.
- [ ] `swing-by`/`swing`: `(swing-by 1/3 4 (fast 8 (pure :hh)))` cycle 0 onsets `0 5/24 1/4 11/24 1/2 17/24 3/4 23/24`,
      every whole 1/8 long (Tidal doc `swingBy (1/3) 4 $ sound "hh*8"`); `(swing 4 (fast 8 (pure :hh)))` gives
      the same onsets; `(swing-by "<0 1/3>" 4 (fast 8 (pure :hh)))` cycle 0 onsets `0 1/8 … 7/8`, cycle 1 onsets
      `1 29/24 5/4 35/24 3/2 41/24 7/4 47/24`.
- [ ] `brak`: `(brak (seq :a :b))` cycle 0 → `a [0,1/2)`, `b [1/2,1)`; cycle 1 → `a [5/4,3/2)`, `b [3/2,7/4)`.
- [ ] `iter`/`iter-back`: `(iter 4 (seq :a :b :c :d))` cycle 1 → `b [1,5/4)`, `c [5/4,3/2)`, `d [3/2,7/4)`, `a [7/4,2)`;
      cycle 2 values `c d a b`, cycle 3 `d a b c`, cycle 4 `a b c d`; `(iter-back 4 …)` cycle 1 `d a b c`, cycle 3 `b c d a`;
      holding: `(iter 2 (cat (seq :a :b) (seq :c :d)))` cycles 0–3 → `a b | b c | c d | d a`;
      patterned `(iter "<4 2>" (seq :a :b :c :d))` cycle 1 → `c d a b`.
- [ ] `palindrome`: `(palindrome (seq :a :b :c))` cycle 0 `a b c`, cycle 1 `c b a`;
      `(palindrome (cat (seq :a :b) (seq :c :d)))` cycles 0–3 → `a b | b a | c d | d c`.
- [ ] `rot`: `(rot 1 (seq :a :b :c :d))` → `b c d a` in the same slots; `(rot -1 …)` → `d a b c`;
      `(rot 1 (seq :a :_ :b :c))` → `b _ c a`; `(rot "<0 1 2>" (seq :a :b :c))` cycle 2 → `c a b`;
      `(query (rot 1 (seq :a :b :c :d)) (span [1 2] [1 1]))` → `d [1/2,3/4)`, `a [3/4,1)`.
- [ ] `chunk`: `(chunk 4 (add 10) (seq 0 1 2 3))` cycles 0–4 → `10 1 2 3 | 0 11 2 3 | 0 1 12 3 | 0 1 2 13 | 10 1 2 3`;
      holding: `(chunk 2 (add 10) (cat (seq 0 1) (seq 4 5)))` cycles 0–3 → `10 1 | 0 11 | 14 5 | 4 15`;
      patterned `(chunk "<2 4>" (add 10) (seq 0 1 2 3))` cycle 1 → `0 11 2 3`.
- [ ] `chunk-back`: `(chunk-back 4 (add 10) (seq 0 1 2 3))` cycles 0–3 → `10 1 2 3 | 0 1 2 13 | 0 1 12 3 | 0 11 2 3`.
- [ ] `fast-chunk`: `(fast-chunk 2 (add 10) (cat (seq 0 1) (seq 4 5)))` cycles 0–3 → `10 1 | 4 15 | 10 1 | 4 15`.
- [ ] `polymeter`: `(polymeter [:a :b :c] [:d :e])` cycle 0 → `a b c` at thirds plus `d [0,1/3)`, `e [1/3,2/3)`, `d [2/3,1)`;
      cycle 1 second layer `e d e`; `(polymeter 4 [:a :b :c])` cycle 0 → `a b c a` at quarters, cycle 1 → `b c a b`;
      patterned `(polymeter "<2 4>" [:a :b :c :d])` cycle 0 → `a [0,1/2)`, `b [1/2,1)`; cycle 1 → `a b c d` at quarters.
- [ ] `timecat`: `(timecat [3 :e3] [1 :g3])` → `e3 [0,3/4)`, `g3 [3/4,1)`; `(timecat [1 (seq :bd :sd)] [2 :hh])` →
      `bd [0,1/6)`, `sd [1/6,1/3)`, `hh [1/3,1)`; `(timecat [1 (cat (pure :a) (pure :b))] [1 :c])` cycle 1 → `b [1,3/2)`, `c [3/2,2)`.
- [ ] `first-of`/`last-of`: `(first-of 4 (add 10) (seq 0 1))` → `10 11` in cycles 0 and 4, `0 1` in 1–3;
      `(last-of 4 (add 10) (seq 0 1))` → `10 11` in cycles 3 and 7 only (of 0–7); `(last-of "<4 2>" (add 10) (seq 0 1))`
      transforms cycles 1 and 3, not 0 and 2 (n is 4, 2, 4, 2 in cycles 0–3); `(first-of "<2 3>" (add 10) (seq 0 1))`
      transforms cycles 0, 2 and 3, not 1; the existing `every` tests pass unchanged.

**Mini-notation** (compare `[value whole part]`, ignoring `:source` unless stated)
- [ ] `"bd!3 sd"` ≡ `(seq :bd :bd :bd :sd)`; `"bd ! sd"` ≡ `(seq :bd :bd :sd)`; `"[bd sd]!2"` ≡ `"[bd sd] [bd sd]"`;
      the three `bd` events of `"bd!3 sd"` carry the same `:source` `{:from base :to (+ base 2)}`.
- [ ] `"bd sd . hh hh hh"` → `bd [0,1/4)`, `sd [1/4,1/2)`, `hh [1/2,2/3)`, `hh [2/3,5/6)`, `hh [5/6,1)`; `"0.5 1"` still has values `0.5 1`.
- [ ] `"[bd, hh hh]"` → `bd [0,1)`, `hh [0,1/2)`, `hh [1/2,1)`; `"[c3,e3,g3]"` → three events over `[0,1)`;
      top-level `"bd sd, hh hh hh"` = stack of both sequences.
- [ ] `"{a b c}%4"` cycle 0 → `a b c a` at quarters, cycle 1 → `b c a b`; `"{a b c, d e}"` ≡ `(polymeter [:a :b :c] [:d :e])`.
- [ ] `"bd(3,8)"` onsets `0 3/8 3/4` (wholes 1/8); `"bd(3,8,2)"` onsets `1/8 1/2 3/4`; `"bd(<3 5>,8)"` cycle 1 onsets `1 5/4 11/8 13/8 7/4`.
- [ ] `"{a b"`, `"bd(3,8"` and `"! bd"` throw `{:type :mini-parse-error}`; all existing `mini_test.cljs` tests pass.

**Registration, docs, verification**
- [ ] Every function with a numeric argument accepts a pattern there (the patterned vectors above, plus):
      `(euclid-legato-rot 3 8 "<0 2>" (pure :x))` cycle 1 → `[9/8,3/2)`, `[3/2,7/4)`, `[7/4,2)`;
      `(outside "<1 2>" rev (cat (seq :a :b) (seq :c :d)))` cycle 0 → `b [0,1/2)`, `a [1/2,1)`, cycle 1 → `b [1,3/2)`, `a [3/2,2)`;
      `(swing "<2 4>" (fast 8 (pure :hh)))` cycle 1 onsets `1 29/24 5/4 35/24 3/2 41/24 7/4 47/24`;
      `(iter-back "<4 2>" (seq :a :b :c :d))` cycle 1 → `c d a b`;
      `(chunk-back "<4 2>" (add 10) (seq 0 1 2 3))` cycle 1 → `0 1 12 13`;
      `(fast-chunk "<4 2>" (add 10) (seq 0 1 2 3))` cycle 1 → `0 1 12 13`.
      Every curried form works in `->>`: `(->> (seq :bd :sd) (ply 2) (iter 4) (swing 4))` evaluates to a pattern.
- [ ] Rule 3: all 24 names in `BuiltinName`, `completions.js`, `hover.js`, `builtin_meta.edn`;
      `npm run gen:grammar` and `npm run gen:ai-docs` produce no diff after the commit; `tl3-documented-examples-evaluate` passes.
- [ ] `docs/USAGE.md` reference rows with examples for all 25 functions and the mini-notation syntax (incl. the `_`
      note); README rows; STRUDEL-PARITY rows ✅ and summary recomputed; ROADMAP TL3 delivered.
- [ ] Playwright: `(struct "t ~ t t" (pure :bd))` → 3 onsets, `(euclidean 3 8 :bd)` → 3 onsets, `(ply 2 (seq :bd :sd))` → 4 onsets.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.

## What NOT to do

- No zoom/compress family or the other deferred structure extras (`compress zoom focus fast-gap linger bite
  ribbon hurry revv repeat-cycles trunc press press-by chunk-into chunk-back-into euclidish ply-for-each
  filter-events filter-when bypass beat sew stitch binary*`): **TL3b**. Keep `compress*` and
  `slowcat-held*` private.
- Don't change public `cat`/`cat*` or `alt`/`alt*` semantics (no Tidal cat offsets there), and don't change `every`.
- No probability or random-choice functions, no `when-mod`, no mini `?0.3` / `|`, no rework of the mini `?`
  (`Math/random`): **TL4**. No mini `/n` (TL1 row, still open).
- No layering functions (`superimpose`, `echo`, `stut`, …): **TL5**. No sample slicing (`chop`, `striate`, …): **TL6**.
  No `nudge` (TL7 control parameter).
- Don't add a separate `euclid` name; the struct form lives in `euclidean`.
