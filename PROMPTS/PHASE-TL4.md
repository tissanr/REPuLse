# Phase TL4 — Randomness & Conditionals

> **Spec status:** full (expanded 2026-10-27 from the outline; function list from the TL4
> rows of [docs/STRUDEL-PARITY.md](../docs/STRUDEL-PARITY.md) plus the `brand`, `brand-by`,
> `berlin`, `rand-l`, `randrun`, `with-seed` rows moved there from TL2)
>
> **Automation:** 🤖 auto — randomness is deterministic (seeded by time), so outputs are
> exactly testable; statistical tests check distributions over many cycles.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL1 (`patternify*`, joins, `app-left`); TL2 (`time->rand`, `xorwise`,
`time->int-seed`, `signal`, `segment`, `rand`, `irand`); TL3/TL3b (`step-true?`,
`keep-values`, `zoom*`, `repeat-cycles*`)

## Goal

Give patterns controlled unpredictability and conditional logic with Tidal/Strudel
semantics, so that ported code behaves the same. Every random decision is re-based on
TL2's Tidal-exact `time->rand`, replacing the coarse, visibly periodic `cycle-hash` /
`event-hash` (100 levels, linear in the cycle number). The `sometimes` family switches to
Strudel's **per-event** semantics, the old per-cycle behaviour moves to `some-cycles*`,
`choose`/`wchoose` become Strudel's continuous signals (per-cycle picks move to
`choose-cycles`/`randcat`), and mini-notation `?` becomes deterministic (it calls
`Math/random` today). 27 new built-ins, 8 re-based ones, two mini-notation additions.

```lisp
;; Before — per-cycle decisions from a 0–99 hash; "bd?" is truly random (Math/random)
(sometimes rev (seq :bd :sd :hh))           ; whole cycle reversed or not
(choose [:bd :sd :hh])                      ; one value per cycle
(~ "hh*8?")                                 ; different on every query

;; After (Tidal/Strudel names in kebab-case)
(sometimes (fast 2) (seq :bd :sd :hh :cp))  ; each EVENT goes to the fast branch with p = 0.5
(some-cycles-by 0.3 (fast 2) (seq :bd :sd)) ; the old per-cycle behaviour
(randcat (seq :bd :sd) (seq :hh :hh :oh) (seq :cp))  ; one random pattern per cycle
(segment 4 (choose [:bd :sd :hh]))          ; choose is a signal: sample it
(shuffle 4 (seq :a :b :c :d))               ; a random permutation of the 4 quarters, per cycle
(whenmod 8 6 rev (seq 0 2 4 7))             ; rev in cycles 6 and 7 of every 8
(degrade-by "<0.2 0.6>" (fast 16 (seq :hh)))
(with-seed 7 (shuffle 4 (seq :a :b :c :d))) ; a different, still deterministic, permutation
(~ "hh*8?0.3 [bd | sd sd | cp*3]")          ; ?amount and | random choice, deterministic
```

## Decisions (resolving the outline's open questions)

| Question | Decision |
|---|---|
| Back-compat of random sequences | **Accept the change** (outline recommendation; REPuLse is pre-1.0). Which cycles/events are chosen changes for `sometimes-by`, `sometimes`, `often`, `rarely`, `degrade-by`, `degrade`, `choose`, `wchoose` and mini `?`. No legacy flag. USAGE.md gets a "Random sequences changed in TL4" note listing the old → new mapping. `cycle-hash` and `event-hash` are **deleted** (only `core.cljs` and `core_test.cljs` use them). |
| Per-cycle vs per-event | **Strudel semantics** (outline recommendation): `sometimes-by x f pat` = `(stack (degrade-by x pat) (f (undegrade-by x pat)))` — every event independently goes to the `f` branch with probability `x`. `sometimes`/`often`/`rarely`/`almost-never`/`almost-always` = `sometimes-by` 0.5/0.75/0.25/0.1/0.9. The old whole-cycle behaviour is `some-cycles-by` / `some-cycles` (as the parity doc says). `choose`/`wchoose` become continuous signals (Strudel); the old one-value-per-cycle behaviour is `choose-cycles` / `wchoose-cycles` (= Strudel `chooseCycles`/`wchooseCycles` = `randcat`/`wrandcat`, which also accept plain values). |
| Global vs per-track seed | **Per-pattern seed via `with-seed`; no global or per-track seed setting.** *Derived from the parity doc, flagged for human review:* the parity row is Strudel `withSeed`/`seed` → `with-seed`, a pattern transformer; Strudel has no global seed and Tidal has none at all. `(with-seed n pat)` = Strudel legacy-RNG `seed(n)`: every random function queried **inside** `pat` reads its random number at time `t + n` instead of `t` (structure is not shifted). Innermost `with-seed` wins. A per-track seed is just `(track :x (with-seed 3 …))`. |
| Seed mechanism | A dynamic var `*rand-seed*` (default `[0 1]`) in `repulse.core`, bound by `with-seed` around a **fully realised** (`vec`) query. All random sources read `rand-at` = `(time->rand (rat+ t *rand-seed*))`. `time->rand` itself stays pure and unchanged (TL2 tests). Limitation (documented): a pattern that is stored as an event **value** and joined outside the `with-seed` is not seeded. |
| Threshold comparison | **Tidal** `>=`: `degrade-by x` keeps an event when `r >= x` (Strudel uses `>`, which makes `(degrade-by 0 p)` drop the event at time 0 because `rand` is exactly 0 there). `undegrade-by x` keeps `r < x`. The two are exact complements: `(stack (degrade-by x p) (undegrade-by x p))` ≡ `p`. |
| `undegrade-by` parameter | **Tidal** `unDegradeBy`: `(undegrade-by x p)` keeps the events with `r < x` (a fraction `x` survives). Strudel's `undegradeBy(y)` keeps `r < 1 − y` (its own `sometimesBy` calls `undegradeBy(1 − x)`), i.e. REPuLse `(undegrade-by x …)` = Strudel `undegradeBy(1 − x)`. Recorded in the parity note. `undegrade` = 0.5 in both. |
| Where `r` is sampled | `degrade-by`, `undegrade-by`, `sometimes-by`: `rand` at the **start of each event's whole** (TL2 sampling rule via `app-left`), plus the seed offset. `some-cycles-by`, `randcat`, `wrandcat`, `choose-cycles`, `wchoose-cycles`: `rand` at the integer cycle start `c` (Strudel `rand.segment(1)`). `randrun`/`shuffle`: Tidal `timeToRands` at `c + 1/2` (Strudel adds `0.5` because at `t = 0` all values are 0 and the permutation would be the identity). `scramble`: `irand n` sampled at each slot start. `choose`/`wchoose`/`brand`/`rand-l`/`berlin`: signals, sampled at the query/structure start. |
| `some-cycles-by` | **Tidal** `somecyclesBy` (`when (\c -> timeToRand c < x)`): in cycle `c`, if `rand-at c < x` the cycle is answered by `(f pat)`, otherwise by `pat` — exactly today's `sometimes-by*` with the new random source. (Strudel's version degrades both branches per cycle; it only differs when `f` moves events across cycle boundaries.) |
| `choose` / `wchoose` arguments | Keep REPuLse's **vector** argument: `(choose [:bd :sd :hh])`, `(wchoose [[3 :bd] [1 :sd]])` — **weight first**, as today (Strudel/Tidal put the value first; changing the order would silently break existing code). Same order for `wchoose-cycles` and `wrandcat`: `(wrandcat [3 (seq :bd :sd)] [1 (pure :cp)])`. `randcat` is variadic (Strudel). Elements may be values or patterns. Index rule (Strudel `__chooseWith`): `i = clamp(floor(r·n), 0, n−1)`; weighted (Strudel `_wchooseWith`): the first entry whose cumulative weight `> r·total`; negative weights count as 0; empty list or `total ≤ 0` → `silence`. |
| `choose` is silent on its own | Yes — a signal has no onsets, so `(choose [:bd :sd])` alone produces no sound after TL4. Use `(segment 4 (choose …))`, `(struct "t ~ t t" (choose …))`, or `choose-cycles` for the old behaviour. This is the main user-visible break; USAGE and the hover text say it. |
| `choose-in` | Strudel `chooseIn` = inner join: the chosen pattern keeps its own structure. Because `rand` is sampled at the query start, the choice depends on how the scheduler chunks queries (Strudel behaves the same). Documented; recommend `randcat` for stable per-cycle choice. |
| Name clashes | Checked: no Lisp builtin or special form named `shuffle`, `when`, `while`, `always`, `never`, `seed`, `scramble` exists (`packages/lisp/src/repulse/lisp/eval.cljs` special forms: `def defn let fn lambda if and or quote do ->> -> quasiquote defmacro defsynth loop recur`; `builtins/collection.cljs` has no `shuffle`). `shuffle` keeps its name (a Lisp sequence shuffle would be non-deterministic and is not planned). `when` → **`when-pat`** as the parity doc says, keeping `when` free for a future Clojure-style conditional. In `repulse.core` the ns must add `shuffle` to `:refer-clojure :exclude`. |
| `when-pat` | **Strudel `when`** (patterned condition, inner join): where the condition is true (`step-true?`), events come from `(f pat)`, elsewhere from `pat`. Additionally a **Lisp function** as condition gives Tidal's `when`: it is called with the integer cycle number, `(when-pat (fn [c] (> c 3)) rev pat)`. Tidal's `while b f pat` (= `sew b (f pat) pat`) is the patterned form of `when-pat`, so **no separate `while`** (outline item dropped). |
| `whenmod` vs `when-mod` | **`whenmod`** — the Tidal name has no camel-case boundary, so the naming rule keeps it as one word (parity doc row). The outline's `when-mod` spelling is not added. Semantics: Tidal `whenmod a b`: in cycle `c`, apply `f` when `c mod a >= b` (decided on the integer cycle number, like `every`). `a`, `b` may be rationals or patterns (`patternify2`); `a <= 0` → `pat` unchanged. |
| `apply-n` | Strudel `applyN`: `f` applied `n` times (`n` truncated; `n <= 0` → `pat`). `n` patterned (inner join). |
| `randrun` / `time->rands` RNG | Tidal `timeToRands` (64-bit `xorwise` chain, floored mod — consistent with TL2's `time->rand`; the first element equals `time->rand t`). Strudel's 32-bit legacy port gives different numbers. Ties in the sort keep index order (stable sort). |
| `shuffle` / `scramble` | Strudel `_rearrangeWith`: slice `i` = `(fast n (repeat-cycles n (zoom i/n (i+1)/n pat)))`; the index pattern (`randrun n` for `shuffle`, `(segment n (irand n))` for `scramble`) picks a slice per step (inner join). Slot `k` of cycle `c` plays window `i` of **cycle `c`** of the source. `n` truncated; `n < 1` → silence. |
| `berlin` | Strudel `_berlin`: `(rand(⌊t⌋) + frac(t)·rand(⌊t⌋+1)) / 2` — a sawtooth-like ridge noise in `[0, 1)`. |
| `rand-l` | Strudel `randL(n)`: a signal whose value is a **vector** of `n` floats (`time->rands t n`). `n < 1` → silence. Useful with `fmap`; it is not a sound on its own. |
| `brand` / `brand-by` | Strudel: `(fmap #(< % p) rand)`; `brand` = `(brand-by 0.5)`. Values are `true`/`false` (usable with `struct`, `mask`, `sew`, `when-pat`). |
| `degrade-by-with` | **In scope** (outline lists it; parity row ❓ → ✅). Strudel `degradeByWith(withPat, x, pat)`: keep events where the value of `withPat` sampled at the event's whole is `>= x` (Tidal comparison, see above). Non-numeric `withPat` values drop the event. |
| Mini-notation `?` | **Deterministic, Strudel seeding:** `x?` = `(degrade-by-with (early (* seed 3/10000) rand) 0.5 x)`; `x?0.3` uses amount 0.3 (the number must touch the `?`: `"a?0.3"`; `"a? 0.3"` is two steps). `seed` is a per-string counter starting at 0, incremented in parse completion order (post-order) for every `?`, every `|` group and every `.`-feet group (krill's `seed++`). The rational `3/10000` replaces Strudel's float `0.0003`. Because the degrade wraps the element **before** the sequence squeezes it, `r` is sampled at the element's inner cycle start (Strudel behaviour), not at the slot start. |
| Mini-notation `\|` | Strudel `chooseInWith(rand.early(seed·0.0003).segment(1), alternatives)`: one alternative per cycle, picked by `rand` at `c + seed·3/10000`. Allowed at top level and inside `[ ]`; inside `< >` or `{ }`, or mixed with `,` in the same group → parse error with a clear message. |
| Probability arguments | Every probability/count argument is patternable (`patternify` inner join; Lisp strings → mini-notation via `u/pat-arg`). Rationals (`1/4`) and floats are accepted (`num-val`). |
| Curried forms | Every `(f fn pat)` and `(f n pat)` / `(f n fn pat)` builtin gets the curried form without the final `pat` (as TL3), so `(->> pat (sometimes rev) (shuffle 4))` works. `choose*`, `randcat`, `wrandcat`, `randrun`, `rand-l`, `brand-by` take no input pattern. |
| Scope cut | The parity doc's `pick` family and `inhabit` (7 rows) and Tidal `fix` are **deferred** (see What NOT to do). They are not random and need their own design (lookup maps, `restartJoin`/`resetJoin`, control-value matching). |

## Function list

"r(t)" means `rand-at t` = `time->rand (t + seed)`. All times are exact rationals.

### Re-based / changed (8)

| # | REPuLse-Lisp | Strudel / Tidal | Semantics after TL4 | Patterned? |
|---|---|---|---|---|
| 1 | `(degrade-by x pat)` | `degradeBy` | Keep event iff `r(whole start) >= x`. | `x` |
| 2 | `(degrade pat)` | `degrade` | `(degrade-by 0.5 pat)`. | — |
| 3 | `(sometimes-by x f pat)` | `sometimesBy` | `(stack (degrade-by x pat) (f (undegrade-by x pat)))` — **per event**. | `x` |
| 4 | `(sometimes f pat)` | `sometimes` | `sometimes-by 0.5`. | — |
| 5 | `(often f pat)` | `often` | `sometimes-by 0.75`. | — |
| 6 | `(rarely f pat)` | `rarely` | `sometimes-by 0.25`. | — |
| 7 | `(choose xs)` | `choose` | Signal: `xs[clamp(floor(r·n))]` (outer join). Silent unless sampled. | — |
| 8 | `(wchoose pairs)` | `wchoose` | Weighted signal; `pairs` = `[[w x] …]`, weight first. | — |

### New (27)

| # | REPuLse-Lisp | Strudel / Tidal | Semantics | Patterned? |
|---|---|---|---|---|
| 9 | `(undegrade-by x pat)` | Tidal `unDegradeBy` | Keep event iff `r(whole start) < x`. | `x` |
| 10 | `(undegrade pat)` | `undegrade` | `(undegrade-by 0.5 pat)`. | — |
| 11 | `(degrade-by-with rpat x pat)` | `degradeByWith` | Keep event iff `rpat` at its whole start `>= x`. | `rpat x` |
| 12 | `(almost-never f pat)` | `almostNever` | `sometimes-by 0.1`. | — |
| 13 | `(almost-always f pat)` | `almostAlways` | `sometimes-by 0.9`. | — |
| 14 | `(always f pat)` | `always` | `(f pat)`. | — |
| 15 | `(never f pat)` | `never` | `pat`. | — |
| 16 | `(some-cycles-by x f pat)` | `someCyclesBy` | Cycle `c` from `(f pat)` iff `r(c) < x`, else from `pat`. | `x` |
| 17 | `(some-cycles f pat)` | `someCycles` | `some-cycles-by 0.5`. | — |
| 18 | `(choose-with sig xs)` | `chooseWith` | `xs[clamp(floor(v·n))]` for `sig` value `v ∈ [0,1)`, outer join. | `sig` |
| 19 | `(choose-in-with sig xs)` | `chooseInWith` | Same index, inner join (chosen pattern keeps its structure). | `sig` |
| 20 | `(choose-in xs)` | `chooseIn` | `(choose-in-with rand xs)`. | — |
| 21 | `(choose-cycles xs)` | `chooseCycles` | `(choose-in-with (segment 1 rand) xs)`: one pick per cycle (old `choose`). | — |
| 22 | `(wchoose-cycles pairs)` | `wchooseCycles` | Weighted, one pick per cycle (old `wchoose`). | — |
| 23 | `(randcat p …)` | `randcat` | `(choose-cycles [p …])`, variadic. | — |
| 24 | `(wrandcat [w p] …)` | `wrandcat` | `(wchoose-cycles [[w p] …])`, variadic. | — |
| 25 | `brand` | `brand` | Signal: `r(t) < 0.5` → `true`/`false`. | — |
| 26 | `(brand-by p)` | `brandBy` | Signal: `r(t) < p`. | `p` |
| 27 | `berlin` | `berlin` | Signal: `(r(⌊t⌋) + frac(t)·r(⌊t⌋+1)) / 2`. | — |
| 28 | `(rand-l n)` | `randL` | Signal: vector of `n` floats `time->rands(t + seed, n)`. | `n` |
| 29 | `(randrun n)` | `randrun` | `n` events per cycle: a random permutation of `0 … n−1` from `time->rands(c + 1/2 + seed, n)` (argsort). | `n` |
| 30 | `(with-seed n pat)` | `withSeed` / `seed` | Every random read inside `pat` uses `t + n`. | `n` |
| 31 | `(shuffle n pat)` | `shuffle` | Each cycle: the `n` slices of the cycle in a random order (`randrun n`); every slice exactly once. | `n` |
| 32 | `(scramble n pat)` | `scramble` | Each of `n` slots plays a random slice (`(segment n (irand n))`), with repetition. | `n` |
| 33 | `(when-pat on f pat)` | `when` | `on` true → `(f pat)`, else `pat` (inner join); `on` a Lisp fn → called with the cycle number. | `on` |
| 34 | `(whenmod a b f pat)` | Tidal `whenmod` | Cycle `c`: `(f pat)` iff `c mod a >= b`. | `a b` |
| 35 | `(apply-n n f pat)` | `applyN` | `f` applied `n` times. | `n` |

### Mini-notation

| Syntax | Meaning |
|---|---|
| `x?` | `(degrade-by-with (early seed·3/10000 rand) 0.5 x)` — deterministic (was `Math/random`). |
| `x?0.3` | Same with amount 0.3. |
| `[a \| b c \| d*2]` / top-level `"a \| b"` | One alternative per cycle: `(choose-in-with (segment 1 (early seed·3/10000 rand)) [a, b c, d*2])`. |

## Background

### Pattern engine — `packages/core/src/repulse/core.cljs`

- `ns`: `(:refer-clojure :exclude [rand time chunk iter])`. TL4 adds `shuffle`.
- Rationals `[n d]`: `rat`, `rat+`, `rat-`, `rat*`, `rat-div`, `rat<`, `rat<=`, `rat>=`,
  `rat->float`, `int->rat`, `->rat`. Spans: `span`, `span-intersect`, `cycle-span`,
  `split-cycles`; private `per-cycle`, `map-spans`, `->pat`, `whole-or-part`, `with-source`.
- `pattern`, `pattern?`, `query`, `pure` (optional source), `seq*`, `stack*`, `fmap`,
  `silence`, `event`, `onset?`, `query-onsets`, `analog?`.
- TL1: `app-left` (structure from the left pattern; the right one is queried over each
  left event's **whole**, so a signal is sampled at the whole start), `app-right`,
  `app-both`, `inner-join`, `outer-join` (each outer event takes the inner value at the
  start of its whole; an analog outer event stays analog), `squeeze-join`, `patternify`,
  `patternify2`, `patternify3`.
- Private `fast*`, `slow*`, `late*`; public `fast`, `slow`, `late`, `early`, `rev`,
  `every` (per-cycle decision via `per-cycle` — the model for `whenmod`), `cat*`, `off`.
- **The block TL4 replaces** (lines ~493–616, between `early` and `off`): public
  `cycle-hash` (`(mod (+ (* (Math/abs cycle) 48271) 12345) 100)`), private
  `sometimes-by*` (per-cycle: `(< (cycle-hash cycle) (int (* prob 100)))`), public
  `sometimes-by` (patternify), `sometimes`, `often`, `rarely`, private `event-hash`
  (hash of the whole start `[n d]`), private `degrade-by*`, public `degrade-by`,
  `degrade`, `choose` (`[xs]` / `[xs sources]`, one event per cycle, index
  `(mod (cycle-hash c) n)`), `wchoose` (`[pairs]` / `[pairs sources]`, `[[weight value] …]`,
  cumulative thresholds on the 0–99 hash). `off` follows the block and stays.
- TL2 RNG section (`;;; ── Phase TL2: Deterministic RNG (Tidal timeToRand)`): private
  `two-29`, `i64`; public `xorwise` (BigInt → BigInt, documented "TL4's random
  combinators reuse it"), `time->int-seed` (rational → BigInt, before `xorwise`),
  `time->rand` (`xorwise` of the int seed, floored mod 2²⁹, / 2²⁹).
- TL2 signals: `signal` (one analog event over the query span, sampled at the span
  start), `steady`, private `cycle-pos`, `sine`, `saw`, … , `rand` = `(signal time->rand)`,
  `perlin` (uses `time->rand` at `⌊t⌋` and `⌊t⌋+1`), `rand2`, private `irand*`, public
  `irand`, `run`, private `segment*` (`(app-left (fn [_ v] v) (fast* n (pure true)) pat)`),
  public `segment`, private `num-val` (`[n d]` → float, numbers unchanged).
- TL3: public `step-true?` (false for `false nil 0 :_ :f :false`); private `keep-values`,
  `cycle-of`, `->int` (truncate number/rational), `fmap-event`, `compress*`,
  `slowcat-held*`; public `struct`, `mask`, `invert`.
- TL3b (end of file, `;;; ── Phase TL3b: …`): private `zoom*` `[s e pat]`,
  `repeat-cycles*` `[n pat]`; public `zoom`, `repeat-cycles`, `sew`, `stitch`, … .
- Tests: `packages/core/src/repulse/core_test.cljs`. Tests that use the old functions:
  `sometimes-by-multi-cycle-span` (~line 154), `sometimes-deterministic`,
  `sometimes-by-zero-never-applies`, `sometimes-by-one-always-applies`,
  `degrade-drops-some-events`, `degrade-by-zero-keeps-all`, `degrade-deterministic`,
  `choose-picks-one-per-cycle`, `choose-deterministic`, `wchoose-picks-one-per-cycle`,
  `choose-attaches-source` (uses `c/cycle-hash`), `wchoose-attaches-source` (~216–275),
  `degrade-sometimes-off-patterned` (~451), `tl2-time->rand` (~537),
  `tl2-irand-perlin-run` (~557). Helpers in the file: `vals-in`, `cyc`, `by-time`.

### Mini-notation — `packages/lisp/src/repulse/lisp/mini.cljs`

- `tokenise`: `?` → `{:type :question}` (no position); `!` → `{:type :bang :str-from i}`;
  atoms carry `:str-from`/`:str-to`; `special?` lists the single-char tokens (no `|`
  today, so `"a | b"` currently parses `|` as the atom keyword `:|`).
- Parser: `parse-suffixes` (`:question` → `{:type :degrade :child node :weight …}`),
  `parse-element` (`[ ]` → `parse-layers`; `< >` builds `:alt` nodes from the layers;
  `{ }` → `:polymeter`), `adjacent-int` (the `!n` adjacency check — the model for `?0.3`),
  `parse-sequence` (stops at `] > } ) ,`; `.` splits into groups → nested `:seq`),
  `parse-layers` (`,` → `{:type :stack :layers …}`), `parse-number-text`.
- Compiler: `alt*`, private `degrade` (**`(> (Math/random) 0.5)`** — non-deterministic),
  `compile-ast` (`:degrade` → `degrade`), public `parse [s]` / `[s base-offset]`.
- Tests: `packages/lisp/src/repulse/lisp/mini_test.cljs` (`degrade-produces-subset`
  ~line 104 relies on randomness; `one-cycle` helper).

### Lisp — `packages/lisp/src/repulse/lisp/`

- `builtins/pattern.cljs` `make-builtins` registers today's random builtins:
  `"sometimes" "often" "rarely"` (`[f p]`), `"sometimes-by"` (`[prob f p]`), `"degrade"`,
  `"degrade-by"` (`[prob]` curried / `[prob p]`), `"choose"` (`[xs]`, passes
  `(mapv u/source-of xs')` as sources), `"wchoose"` (`[pairs]`, sources of the values).
- `builtins/structure.cljs` (TL3/TL3b): private helpers `xf` (unwrap a Lisp fn and its
  result), `step->pat`, builders `p1`, `n+p`, `n+f+p`, `a+b+p`, `pred` — the model for the
  new file.
- `builtins/signals.cljs`: `"rand" core/rand "rand2" core/rand2 "perlin" core/perlin …`,
  `"irand"`, `"run"`, `"segment"` (bare-symbol signal values).
- `eval.cljs`: `make-env` merges `b-pattern`, `b-signals`, `b-structure`, … (~line 363);
  `not-a-function-msg` already gives the "is a pattern, not a function" hint for any
  pattern value, so `(brand)` / `(berlin)` get it for free. Special forms: see Decisions.
- `util.cljs`: `u/unwrap`, `u/source-of`, `u/pat-arg` (strings → mini-notation).
- Tests: `integration_test.cljs` (`eval-pattern`, `tl3b-documented-examples-evaluate`
  ~line 880 — the model for `tl4-documented-examples-evaluate`), `eval_test.cljs`
  (`lisp-choose-attaches-source` ~line 69).

### App, editor & AI docs

- `app/src/repulse/audio.cljs` triggers only `core/query-onsets` → analog events (signals
  such as `choose`, `brand`) are silent unless sampled.
- `app/src/repulse/lisp-lang/repulse-lisp.grammar` `BuiltinName` (line with
  `"sometimes" | "often" | "rarely" | "sometimes-by" |`, `"degrade" | "degrade-by" |`,
  `"choose" | "wchoose" |`), `parser.js`/`parser.terms.js` via `npm run gen:grammar`;
  `completions.js` (entries for `sometimes`…`wchoose` ~lines 31–38, `rand`/`irand`/`perlin`
  ~133–135); `hover.js` `DOCS` (entries `"sometimes"` … `"wchoose"` ~lines 75–115);
  `insert-categories.js` (`sometimes`/`often`/`rarely`/`degrade` insert helpers — names
  unchanged, no edit needed).
- `app/src/repulse/content/builtin_meta.edn` (categories `"transform"`, `"source"`,
  `"signal"`) → `docs/ai/builtins.json` via `npm run gen:ai-docs`
  (`scripts/gen_ai_docs.mjs` fails if a completion label has no meta entry).
- `e2e/audio-render.spec.ts`: `render(page, code)`, `countOnsets` (TL3b block ~line 163).

### Docs

- `docs/USAGE.md`: `### sometimes / often / rarely` (~line 694), `### degrade /
  degrade-by` (~712), `### choose / wchoose — random selection per cycle` (~722),
  `### Randomness - rand irand perlin` (~915, says "until TL4 vary with `(early 7 rand)`"
  in spirit), `## Mini-notation` (~1623; `(~ "bd? sd")` example ~1633), the grammar
  appendix (~2962).
- `docs/STRUDEL-PARITY.md`: "Randomness & conditionals — TL4" section (semantics
  warning + table), the `brand brandBy berlin randL randrun withSeed` rows in
  "Continuous signals — TL2", Mini-notation rows `?0.3` and `|`, Tidal-only `whenmod`
  and `fix`, and the Summary table.
- `ROADMAP.md` "Phase TL4" section; `README.md` "Built-in pattern functions" table.

## Implementation

Work in this order; each step ends with `npm run test` green.

### 0. Verify tooling (CLAUDE.md Rule 4)

`npm run gen:grammar && npm run gen:ai-docs && npm run test` on the unmodified branch.

### 1. Seeded RNG — `core.cljs`, TL2 RNG section

Extract the floored-mod step of `time->rand` into `seed->rand` (defined before
`time->rand`, which becomes `(seed->rand (xorwise (time->int-seed (->rat t))))` — same
values), then add the rest right after `time->rand` (it must precede `rand`/`perlin`):

```clojure
(defn- seed->rand [s]                          ; before time->rand
  (let [m (js* "(((~{} % 536870912n) + 536870912n) % 536870912n)" s)]
    (/ (js/Number m) two-29)))

(def ^:dynamic *rand-seed*
  "Time offset added to every random read; bound by with-seed (TL4)."
  [0 1])

(defn time->rands
  "Tidal timeToRands: n floats in [0,1) for rational t. (first (time->rands t n)) = (time->rand t)."
  [t n]
  (loop [s (xorwise (time->int-seed (->rat t))) k n out []]
    (if (<= k 0) out (recur (xorwise s) (dec k) (conj out (seed->rand s))))))

(defn- rand-at  [t]   (time->rand  (rat+ (->rat t) *rand-seed*)))
(defn- rands-at [t n] (time->rands (rat+ (->rat t) *rand-seed*) n))
```

Then `(def rand (signal rand-at))`, and `perlin` uses `rand-at` for `a`/`b`. `rand2`,
`irand` derive from `rand` and become seed-aware automatically.

### 2. Delete the old block — `core.cljs`

Remove `cycle-hash`, `sometimes-by*`, `sometimes-by`, `sometimes`, `often`, `rarely`,
`event-hash`, `degrade-by*`, `degrade-by`, `degrade`, `choose`, `wchoose` (keep `off`).
Their replacements go into a new block at the end of the file (they need TL3/TL3b
helpers): `;;; ── Phase TL4: Randomness & conditionals ───`. Add `shuffle` to
`:refer-clojure :exclude`.

### 3. Degrade family & sometimes — TL4 block

```clojure
(defn- degrade-by-with* [rpat x pat]
  (let [x (num-val x)]
    (app-left (fn [v _] v) pat
              (keep-values #(and (number? %) (>= % x)) (->pat rpat)))))

(defn degrade-by-with [rpat x pat]
  ((patternify (fn [x' p] (degrade-by-with* rpat x' p))) x pat))
(defn degrade-by [x pat] ((patternify (fn [x' p] (degrade-by-with* rand x' p))) x pat))
(defn degrade [pat] (degrade-by 0.5 pat))

(defn- undegrade-by* [x pat]
  (let [x (num-val x)] (app-left (fn [v _] v) pat (keep-values #(< % x) rand))))
(defn undegrade-by [x pat] ((patternify undegrade-by*) x pat))
(defn undegrade [pat] (undegrade-by 0.5 pat))

(defn- sometimes-by* [x f pat]
  (stack* [(degrade-by-with* rand x pat) (f (undegrade-by* x pat))]))
(defn sometimes-by [x f pat] ((patternify (fn [x' p] (sometimes-by* x' f p))) x pat))
(defn sometimes      [f pat] (sometimes-by 0.5 f pat))
(defn often          [f pat] (sometimes-by 0.75 f pat))
(defn rarely         [f pat] (sometimes-by 0.25 f pat))
(defn almost-never   [f pat] (sometimes-by 0.1 f pat))
(defn almost-always  [f pat] (sometimes-by 0.9 f pat))
(defn always [f pat] (f pat))
(defn never  [_f pat] pat)

(defn- some-cycles-by* [x f pat]
  (let [x (num-val x) fp (f pat)]
    (pattern (per-cycle (fn [sp] (query (if (< (rand-at (int->rat (cycle-of sp))) x) fp pat) sp))))))
(defn some-cycles-by [x f pat] ((patternify (fn [x' p] (some-cycles-by* x' f p))) x pat))
(defn some-cycles [f pat] (some-cycles-by 0.5 f pat))
```

`app-left` keeps the left event's `:source`, so editor highlighting survives.

### 4. Choice family — TL4 block

```clojure
(defn- with-sources [xs sources]
  (mapv (fn [x s] (if (pattern? x) x (pure x s))) xs (concat sources (repeat nil))))

(defn- choose-index [v n] (int (min (dec n) (max 0 (Math/floor (* (num-val v) n))))))

(defn- choose-pats [sig xs]                    ; pattern of patterns
  (let [xs (mapv ->pat xs) n (count xs)]
    (fmap #(nth xs (choose-index % n)) (->pat sig))))

(defn choose-with    [sig xs] (if (empty? xs) silence (outer-join (choose-pats sig xs))))
(defn choose-in-with [sig xs] (if (empty? xs) silence (inner-join (choose-pats sig xs))))
(defn choose ([xs] (choose xs nil)) ([xs sources] (choose-with rand (with-sources xs sources))))
(defn choose-in [xs] (choose-in-with rand xs))
(defn choose-cycles
  ([xs] (choose-cycles xs nil))
  ([xs sources] (choose-in-with (segment* 1 rand) (with-sources xs sources))))
(defn randcat [pats] (choose-cycles pats))
```

Weighted (`pairs` = `[[w x] …]`, weight first):

```clojure
(defn- wchoose-pats [sig pairs]
  (let [ws    (mapv #(max 0 (num-val (first %))) pairs)
        xs    (mapv #(->pat (second %)) pairs)
        total (reduce + ws)
        cum   (vec (reductions + ws))]
    (when (pos? total)
      (fmap (fn [v] (let [find (* total (num-val v))]
                      (nth xs (or (first (keep-indexed (fn [i c] (when (> c find) i)) cum))
                                  (dec (count xs))))))
            (->pat sig)))))

(defn wchoose
  ([pairs] (wchoose pairs nil))
  ([pairs sources]
   (if-let [pp (wchoose-pats rand (map (fn [[w x] s] [w (if (pattern? x) x (pure x s))])
                                       pairs (concat sources (repeat nil))))]
     (outer-join pp) silence)))
(defn wchoose-cycles
  ([pairs] (wchoose-cycles pairs nil))
  ([pairs sources]
   (if-let [pp (wchoose-pats (segment* 1 rand)
                             (map (fn [[w x] s] [w (if (pattern? x) x (pure x s))])
                                  pairs (concat sources (repeat nil))))]
     (inner-join pp) silence)))
(defn wrandcat [pairs] (wchoose-cycles pairs))
```

### 5. Signals — TL4 block (they need `num-val`, `->int`)

```clojure
(defn- brand-by* [p] (let [p (num-val p)] (fmap #(< % p) rand)))
(defn brand-by [p] (if (pattern? p) (inner-join (fmap brand-by* p)) (brand-by* p)))
(def brand (brand-by* 0.5))

(def berlin
  (signal (fn [[n d :as t]]
            (let [fl (Math/floor (/ n d))]
              (/ (+ (rand-at [fl 1]) (* (- (rat->float t) fl) (rand-at [(inc fl) 1]))) 2)))))

(defn- rand-l* [n] (let [k (->int n)] (if (< k 1) silence (signal #(rands-at % k)))))
(defn rand-l [n] (if (pattern? n) (inner-join (fmap rand-l* n)) (rand-l* n)))

(defn- randrun* [n]
  (let [k (->int n)]
    (if (< k 1)
      silence
      (pattern
       (per-cycle
        (fn [sp]
          (let [c     (cycle-of sp)
                order (mapv second (sort-by first (map vector (rands-at (rat+ (int->rat c) [1 2]) k) (range k))))]
            (keep (fn [i]
                    (let [w (span (rat (+ (* c k) i) k) (rat (+ (* c k) i 1) k))]
                      (when-let [p (span-intersect w sp)] (event (nth order i) w p))))
                  (range k)))))))))
(defn randrun [n] (if (pattern? n) (inner-join (fmap randrun* n)) (randrun* n)))
```

### 6. Seed — TL4 block

```clojure
(defn- with-seed* [s pat]
  (let [s (->rat s)]
    ;; vec: realise every nested query while the binding is active
    (pattern (fn [sp] (binding [*rand-seed* s] (vec (query pat sp)))))))
(defn with-seed [s pat] ((patternify with-seed*) s pat))
```

### 7. Rearrangement — TL4 block

```clojure
(defn- rearrange-with* [ipat k pat]
  (let [slices (mapv #(fast* k (repeat-cycles* k (zoom* (rat % k) (rat (inc %) k) pat))) (range k))]
    (inner-join (fmap #(nth slices (mod (->int %) k)) ipat))))

(defn- shuffle* [n pat]
  (let [k (->int n)] (if (< k 1) silence (rearrange-with* (randrun* k) k pat))))
(defn shuffle [n pat] ((patternify shuffle*) n pat))

(defn- scramble* [n pat]
  (let [k (->int n)] (if (< k 1) silence (rearrange-with* (segment* k (irand* k)) k pat))))
(defn scramble [n pat] ((patternify scramble*) n pat))
```

### 8. Conditionals — TL4 block

```clojure
(defn when-pat
  "on: a boolean pattern (Strudel when) or a fn of the cycle number (Tidal when)."
  [on f pat]
  (if (fn? on)
    (let [fp (f pat)] (pattern (per-cycle (fn [sp] (query (if (on (cycle-of sp)) fp pat) sp)))))
    ((patternify (fn [o p] (if (step-true? o) (f p) p))) on pat)))

(defn- whenmod* [a b f pat]
  (let [a (->rat a) b (->rat b)]
    (if (rat<= a [0 1])
      pat
      (let [fp (f pat)]
        (pattern
         (per-cycle
          (fn [sp]
            (let [c (int->rat (cycle-of sp))
                  m (rat- c (rat* a (int->rat (Math/floor (rat->float (rat-div c a))))))]
              (query (if (rat>= m b) fp pat) sp)))))))))
(defn whenmod [a b f pat] ((patternify2 (fn [a' b' p] (whenmod* a' b' f p))) a b pat))

(defn apply-n [n f pat]
  ((patternify (fn [n' p] (nth (iterate f p) (max 0 (->int n'))))) n pat))
```

### 9. Mini-notation — `mini.cljs`

1. `tokenise`: `?` → `{:type :question :str-from i}`; new `|` → `{:type :pipe}`; add `\|`
   to `special?`.
2. `parse-suffixes` `:question`: if the next token is an `:atom` with
   `(= (:str-from nxt) (inc (:str-from tok)))` and `parse-number-text` gives a number,
   consume it as `:amount`; node `{:type :degrade :child node :amount a-or-nil :weight …}`.
3. `parse-sequence`: also stop at `:pipe`; when `.` produced more than one group, mark the
   outer node `:feet true` (it consumes a seed, see 5).
4. `parse-layers`: collect sequences separated by `,` **or** `|`; mixing both in one group
   → `(parse-error "Cannot mix , and | in one group")`; `|` result:
   `{:type :rand :choices [seq …]}`. `parse-element` for `< >` and `{ }`: a `:rand` node
   inside → `(parse-error "| is only allowed at top level and inside [ ]")`.
5. New private `number-seeds`: walk the AST post-order with a counter starting at 0 —
   children first, in order (`:child`, then each of `:args`, `:children`, `:layers`,
   `:choices`), then the node; `:degrade`, `:rand` and `:feet` nodes take the next seed
   (`assoc :seed`). Call it in `parse` between `parse-layers` and `compile-ast`.
6. `compile-ast`: delete the private `degrade` (the `Math/random` one).

```clojure
:degrade (core/degrade-by-with (core/early (core/rat (* 3 (:seed node)) 10000) core/rand)
                               (or (:amount node) 0.5)
                               (compile-ast (:child node) base-offset))
:rand    (core/choose-in-with (core/segment 1 (core/early (core/rat (* 3 (:seed node)) 10000) core/rand))
                              (mapv #(compile-ast % base-offset) (:choices node)))
```

### 10. Lisp builtins — new `builtins/random.cljs`

New namespace `repulse.lisp.builtins.random` (`:require [repulse.core :as core]
[repulse.lisp.util :as u]`), merged in `eval.cljs` `make-env` right after
`(b-structure/make-builtins)`. **Move** the eight random entries out of
`builtins/pattern.cljs` into it. Local helpers mirror `structure.cljs` (`xf`,
`step->pat`, `n+p`, `n+f+p`) plus `f+p`:
`(fn ([g] (let [g (xf g)] (fn [p] (f g (u/unwrap p))))) ([g p] (f (xf g) (u/unwrap p))))`.

| Name | Builder / arities |
|---|---|
| `degrade`, `undegrade` | `[p]` |
| `degrade-by`, `undegrade-by`, `with-seed`, `shuffle`, `scramble` | `n+p` |
| `degrade-by-with` | `[r x]` curried, `[r x p]` — `r`, `x` via `u/pat-arg` |
| `sometimes`, `often`, `rarely`, `almost-never`, `almost-always`, `always`, `never`, `some-cycles` | `f+p` |
| `sometimes-by`, `some-cycles-by`, `apply-n` | `n+f+p` |
| `choose`, `choose-cycles` | `[xs]` — values unwrapped, sources from `u/source-of` (as today's `choose`) |
| `choose-in` | `[xs]` — each element via `step->pat` |
| `choose-with`, `choose-in-with` | `[sig xs]` — `sig` via `u/pat-arg` |
| `wchoose`, `wchoose-cycles` | `[pairs]` — as today's `wchoose` (weight first, sources of the values) |
| `randcat` | `[& ps]` — each via `step->pat` |
| `wrandcat` | `[& pairs]` — each `[w p]`, `p` via `step->pat` |
| `brand-by`, `rand-l`, `randrun` | `[n]` via `u/pat-arg` |
| `when-pat` | `[on g]` curried, `[on g p]` — `on`: if `(fn? (u/unwrap on))` wrap as `(fn [c] (u/unwrap (on' c)))`, else `u/pat-arg` |
| `whenmod` | `[a b g]` curried, `[a b g p]` — `a`, `b` via `u/pat-arg`, `g` via `xf` |

`brand` and `berlin` are bare signal values: add `"brand" core/brand "berlin" core/berlin`
to `builtins/signals.cljs`.

### 11. Tests

- `core_test.cljs`: one `deftest tl4-<name>` per DoD bullet below (compare
  `[value whole-start whole-end]` sorted by whole start; "values c" = onset values of
  cycle c in time order). Update the old tests: the `choose-*`/`wchoose-*` per-cycle tests
  move to `choose-cycles`/`wchoose-cycles`; `choose-attaches-source` no longer uses
  `cycle-hash` (assert the source of the chosen value for cycles 0–7 against the DoD
  vector); `sometimes-*`, `degrade-*`, `degrade-sometimes-off-patterned` keep passing
  unchanged (verify).
- `mini_test.cljs`: replace `degrade-produces-subset` with the deterministic vectors; add
  `?amount`, `|`, seed numbering and parse-error tests.
- `integration_test.cljs`: Lisp-level vectors (closures in `when-pat`, `apply-n`,
  `sometimes`), curried forms in `->>`, `tl4-documented-examples-evaluate` (every TL4
  example in `builtin_meta.edn` evaluates to a pattern). `eval_test.cljs`:
  `lisp-choose-attaches-source` → `choose-cycles`, plus one for `choose` (analog event
  carries the source).
- `e2e/audio-render.spec.ts`: see DoD.

### 12. Registration (CLAUDE.md Rule 3)

New `BuiltinName` line: `undegrade-by undegrade degrade-by-with almost-never
almost-always always never some-cycles-by some-cycles choose-with choose-in-with
choose-in choose-cycles wchoose-cycles randcat wrandcat brand brand-by berlin rand-l
randrun with-seed shuffle scramble when-pat whenmod apply-n`. Run `npm run gen:grammar`
and check that `some-cycles-by`, `choose-in-with`, `degrade-by-with`, `wchoose-cycles`,
`rand-l` and `brand-by` each highlight as **one** token, and that `rand` inside `brand`,
`some-cycles` inside `some-cycles-by` and a user symbol like `random-x` are not
mis-tokenised. Then `completions.js` (`type: "function"`; `brand`/`berlin` `type:
"variable"` like `rand`; update the details of the 8 changed names — e.g. `choose`: "(choose
[vals]) — random signal; sample it: (segment 4 (choose [:bd :sd]))"), `hover.js` `DOCS`
(new + 8 updated), `builtin_meta.edn` (category `"transform"` for the transformers,
`"source"` for `choose*`, `randcat`, `wrandcat`, `randrun`, `"signal"` for `brand`,
`brand-by`, `berlin`, `rand-l`; `:returns "Pattern"`, `:side-effects []`, ≥ 1 example —
one patterned example for every function with a patterned argument — `:see-also`), then
`npm run gen:ai-docs`. Commit grammar, `parser.js`, `parser.terms.js`, `completions.js`,
`hover.js`, `builtin_meta.edn`, `docs/ai/builtins.json` together.

### 13. Docs

- `docs/USAGE.md`:
  - Rewrite `sometimes / often / rarely` (per event; table of the 7 probability names),
    add `some-cycles` / `some-cycles-by` (per cycle), `degrade` / `undegrade` /
    `degrade-by-with`, replace `choose / wchoose — random selection per cycle` with
    "Random choice" (`choose`/`wchoose` as signals, `choose-cycles`, `randcat`, `wrandcat`,
    `choose-with`, `choose-in`), extend "Randomness" with `brand`, `brand-by`, `berlin`,
    `rand-l`, `randrun`, `with-seed` (replace the `(early 7 rand)` advice with `with-seed`),
    add `shuffle`/`scramble` and a "Conditionals" subsection (`when-pat` both forms,
    `whenmod`, `apply-n`, `always`/`never`).
  - **"Random sequences changed in TL4"** box: old → new (`sometimes*` now per event —
    use `some-cycles*` for whole cycles; `choose`/`wchoose` are signals — use
    `choose-cycles`/`wchoose-cycles` or `segment`; `degrade-by`, `sometimes*` and mini `?`
    pick different events than before; `?` is now repeatable).
  - Mini-notation: `?0.3`, `|`, determinism and the seed rule; extend the grammar appendix.
- `README.md`: rows `| (sometimes f pat) / (some-cycles f pat) | Random per event / per cycle |`,
  `| (randcat a b c) / (shuffle 4 pat) | Random pattern per cycle / random slice order |`,
  `| (whenmod 8 6 f pat) / (when-pat "<0 1>" f pat) | Conditional transforms |`;
  update any `choose` row.
- `docs/STRUDEL-PARITY.md`: rewrite the TL4 semantics warning as resolved; rows ✅:
  `undegradeBy` (note: "Tidal parameter; = Strudel undegradeBy(1−x)"), `undegrade`,
  `degradeByWith` (❓ → ✅ TL4), `sometimesBy` `sometimes` `often` `rarely` (🟡 → ✅,
  "per event"), `almostNever` `almostAlways` `never` `always` `someCyclesBy`
  `someCycles`, `choose` (🟡 → ✅ "signal; vector arg"), `chooseWith/chooseInWith`
  (`choose-with`, `choose-in-with`), `chooseIn`, `chooseCycles/randcat`
  (`choose-cycles`, `randcat`), `wchoose` (🟡 → ✅ "weight first"),
  `wchooseCycles/wrandcat` (`wchoose-cycles`, `wrandcat`; "weight first"), `shuffle`,
  `scramble`, `when` (`when-pat`; "also Tidal fn-of-cycle form"), `applyN`; `degradeBy`
  note "Tidal `>=` on TL2 rand"; signals rows `brand` `brandBy` `berlin` `randL` `randrun`
  `withSeed/seed` → ✅ TL4; mini `?0.3` and `|` → ✅ TL4; Tidal-only `whenmod` → ✅.
  The deferred rows (What NOT to do) stay 📋 with Phase **TL4b**. Recompute the Summary.
- Deferred rows need a home (CLAUDE.md Rule 1): add `PROMPTS/PHASE-TL4b.md`
  (`Spec status: outline`, "Pick & fix" — `pick pick-mod pick-f pick-out pick-restart
  pick-reset inhabit fix`), its `ROADMAP.md` section and implementation-order row, in the
  same PR.
- `ROADMAP.md`: TL4 → `✅ *delivered*` with a **Delivered:** list (TL3b style).

## Files to change

| File | Change |
|---|---|
| `packages/core/src/repulse/core.cljs` | `ns` exclude `shuffle`; TL2 section: `*rand-seed*`, `seed->rand`, `time->rands`, `rand-at`, `rands-at`, seeded `rand`/`perlin`; delete `cycle-hash … wchoose`; TL4 block: steps 3–8 |
| `packages/core/src/repulse/core_test.cljs` | `tl4-*` vectors; migrate the old `choose`/`wchoose` tests |
| `packages/lisp/src/repulse/lisp/mini.cljs` | `?` position + amount, `|` token, `:rand` nodes, `number-seeds`, deterministic `:degrade`; delete `degrade` |
| `packages/lisp/src/repulse/lisp/mini_test.cljs` | Deterministic `?`, `?0.3`, `|`, seeds, parse errors |
| `packages/lisp/src/repulse/lisp/builtins/random.cljs` | **New** — 35 builtins |
| `packages/lisp/src/repulse/lisp/builtins/pattern.cljs` | Remove the 8 moved random entries |
| `packages/lisp/src/repulse/lisp/builtins/signals.cljs` | `"brand"`, `"berlin"` |
| `packages/lisp/src/repulse/lisp/eval.cljs` | Require + merge `b-random` after `b-structure` |
| `packages/lisp/src/repulse/lisp/integration_test.cljs`, `eval_test.cljs` | Lisp vectors, curried forms, `tl4-documented-examples-evaluate`, choose source test |
| `e2e/audio-render.spec.ts` | 4 onset-count tests (DoD) |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` + `parser.js` + `parser.terms.js` | 27 names (`npm run gen:grammar`) |
| `app/src/repulse/lisp-lang/completions.js`, `hover.js` | 27 new + 8 updated entries |
| `app/src/repulse/content/builtin_meta.edn` + `docs/ai/builtins.json` | 27 new + 8 updated entries (`npm run gen:ai-docs`) |
| `docs/USAGE.md`, `README.md`, `docs/STRUDEL-PARITY.md`, `ROADMAP.md` (TL4 delivered; TL4b section + implementation-order row), `PROMPTS/PHASE-TL4b.md` (new outline) | Step 13 |

## Definition of done

Notation: "values c" = values of `(query-onsets pat (cycle-span c))` in time order;
events are `value [whole-start, whole-end)`. `r(t)` reference values (from TL2's
`time->rand`, unseeded) used below:
`r(0..7)` = `0, 0.5177721455693245, 0.03601731359958649, 0.8026083502918482,
0.07203462719917297, 0.11536062508821487, 0.6052166428416967, 0.146883986890316`;
`r(1/4) = 0.6295689214020967`, `r(1/2) = 0.2591378428041935`,
`r(3/4) = 0.20052618719637394`, `r(3/2) = 0.4010523743927479`.
`s8` = `(seq 0 1 2 3 4 5 6 7)`, `s4` = `(seq :a :b :c :d)`, `+100` = `(fmap #(+ % 100))`.
Every vector below was computed by running the reference implementation of this spec
against the real `time->rand`. One `deftest` per bullet.

**RNG & seed**
- [ ] `time->rands`: `(time->rands 1/2 4)` → `[0.2591378428041935 0.712890088558197 0.9065967965871096
      0.6629501506686211]`; `(time->rands 3/2 4)` → `[0.4010523743927479 0.35760145634412766 0.1534953359514475
      0.363248186185956]`; `(time->rands 0 3)` → `[0 0 0]`; `(first (time->rands t n))` = `(time->rand t)` for
      `t ∈ {1, 1/4, 7/3}`. `tl2-time->rand` and `tl2-irand-perlin-run` pass unchanged (default seed 0).
- [ ] `with-seed`: `(with-seed 1 (segment 4 rand))` cycle 0 values = `(segment 4 rand)` cycle 1 values =
      `[0.5177721455693245 0.2788401562720537 0.4010523743927479 0.7870833929628134]`;
      nested `(with-seed 1 (with-seed 2 (segment 4 rand)))` cycle 0 = `(segment 4 rand)` cycle 2 =
      `[0.03601731359958649 0.35768954269587994 0.5576803125441074 0.6513602118939161]` (innermost wins);
      `(with-seed 3 (degrade-by 0.5 s8))` values 0 → `0 1 4 6 7` (= `(degrade-by 0.5 s8)` values 3);
      `(with-seed 7 (randcat (pure :a) (pure :b) (pure :c)))` values 0–7 → `a a b a b a a a`;
      patterned `(with-seed "<0 1>" (segment 1 rand))` values 0 → `0`, values 1 → `0.03601731359958649`.
- [ ] Same seed → identical events across two independent evaluations of the same code (Lisp level, for
      `shuffle`, `randcat`, `degrade-by`, `sometimes`, `(~ "hh*8?")`); different seed → different events:
      over cycles 0–99, `(with-seed 1 (randcat (pure :a) (pure :b) (pure :c)))` differs from the unseeded one
      in 70 cycles (assert ≥ 50).

**Degrade family**
- [ ] `degrade-by`: `(degrade-by 0.5 s8)` values 0 → `2 3`, values 1 → `0 1 3 5 6`; `(degrade-by 0 s8)` keeps all 8
      (time 0 included: `r(0) = 0 >= 0`); `(degrade-by 1 s8)` → no events; `(degrade s8)` ≡ `(degrade-by 0.5 s8)`;
      patterned `(degrade-by "<0.2 0.6>" s8)` values 0 → `1 2 3 4 6 7`, values 1 → `1 3 6`;
      `(degrade-by "<0.2 0.6>" (fast 16 (seq :hh)))` onset counts cycles 0–3 → `13 7 12 8`;
      `:source` of `(degrade-by 0.5 (~ "a b c d"))` events equals the token sources.
- [ ] `undegrade-by`: `(undegrade-by 0.5 s8)` values 0 → `0 1 4 5 6 7`, values 1 → `2 4 7`; complement:
      for x ∈ {0.1, 0.5, 0.9} and cycles 0–9, `(stack (degrade-by x s8) (undegrade-by x s8))` has exactly
      the events of `s8`; `(undegrade s8)` ≡ `(undegrade-by 0.5 s8)`.
- [ ] `degrade-by-with`: `(degrade-by-with saw 1/2 s8)` values 0 → `4 5 6 7`;
      `(degrade-by-with (segment 1 rand) 0.5 s8)` values 0 → none, values 1 → all 8.

**Sometimes family (per event)**
- [ ] `sometimes-by`: `(sometimes-by 0.5 +100 (seq 0 1 2 3))` cycle 0 → `100 [0,1/4)`, `1 [1/4,1/2)`, `102 [1/2,3/4)`,
      `103 [3/4,1)`; cycle 1 → `0 [1,5/4)`, `101 [5/4,3/2)`, `102 [3/2,7/4)`, `3 [7/4,2)`;
      `(sometimes-by 0.5 (fast 2) s4)` cycle 0 → `a [0,1/8)`, `c [1/4,3/8)`, `d [3/8,1/2)`, `b [1/4,1/2)`, `b [5/8,3/4)`,
      `c [3/4,7/8)` (each event goes to exactly one branch before `f`);
      `sometimes-by 0` never applies `f`, `sometimes-by 1` applies it to every event (existing tests);
      patterned `(sometimes-by "<0 1>" rev (seq :a :b))` values 0 → `a b`, values 1 → `b a` (existing test).
- [ ] Named probabilities on `s8` with `+100`: `sometimes` cycle 0 = `sometimes-by 0.5`; `often` values 0 → `100 … 107`
      (all); `rarely` values 0 → `100 1 2 3 4 105 106 7`; `almost-never` values 0 → `100 1 2 3 4 5 6 7`, values 1 → `0 … 7`
      (none); `almost-always` values 0 and 1 → all `+100`; `(always +100 s8)` ≡ `+100` of `s8`; `(never +100 s8)` ≡ `s8`.
- [ ] `some-cycles-by`: `(some-cycles-by 0.5 rev s4)` values 0–7 → `dcba abcd dcba abcd dcba dcba abcd dcba`
      (`r(c) < 0.5` for c = 0, 2, 4, 5, 7); `(some-cycles +100 (seq 0 1 2 3))` values 0–3 → `100 101 102 103 | 0 1 2 3 |
      100 101 102 103 | 0 1 2 3`; `sometimes-by-multi-cycle-span`-style check: querying `[0,6)` at once equals
      cycle-by-cycle; patterned `(some-cycles-by "<0 1>" rev s4)` values 0 → `a b c d`, values 1 → `d c b a`.

**Choice**
- [ ] `choose`: `(segment 4 (choose [:a :b :c]))` values 0 → `a b a a`, values 1 → `b a b c`;
      `(choose [:a :b :c])` queried over `[1/4,1/2)` → one analog event (`:whole nil`) with value `:b`;
      `(query-onsets (choose [:a :b]) (cycle-span 0))` → empty; `(choose [])` → silence;
      `:source` of the sampled value matches the chosen element (core 2-arity and Lisp level).
- [ ] `wchoose`: `(segment 4 (wchoose [[1 :a] [3 :b]]))` values 0 → `a b b a`, values 1 → `b b b b`;
      `(wchoose [[0 :a] [0 :b]])` → silence.
- [ ] `choose-with` / `choose-in` / `choose-in-with`: `(segment 4 (choose-with sine [:a :b :c]))` values 0 → `b c b a`;
      `(choose-in [(seq :a :b) (seq :c :d)])` queried over `[1,2)` → `c [1,3/2)`, `d [3/2,2)`; over `[3/2,2)` → `b [3/2,2)`;
      `(choose-in-with (segment 1 rand) [(seq :a :b) (pure :c)])` values 1 → `c` (`r(1)·2 = 1.04`).
- [ ] `randcat` / `choose-cycles`: `(randcat (pure :a) (seq :b :b) (pure :c))` values 0–7 → `a | b b | a | c | a | a | b b | a`,
      cycle 1 events `b [1,3/2)`, `b [3/2,2)` (the chosen pattern plays at its own time — Strudel, no cycle offset);
      `(choose-cycles [:a :b :c])` gives exactly one whole-cycle event per cycle for cycles 0–9 (migrated test);
      `(choose-cycles [:a :b] [{:from 0 :to 2} {:from 3 :to 5}])` cycle 1 → `:b` with source `{:from 3 :to 5}`.
- [ ] `wrandcat` / `wchoose-cycles`: `(wrandcat [3 (pure :a)] [1 (pure :b)])` values 0–7 → `a a a b a a a a`;
      `(wchoose-cycles [[1 :a] [1 :b] [2 :c]])` values 0–7 → `a c a c a a c a`.

**Signals**
- [ ] `brand` / `brand-by`: `(segment 4 brand)` values 0 → `true false true true`, values 1 → `false true true false`;
      `(segment 4 (brand-by 0.25))` values 0 → `true false false true`, values 1 → `false false false false`;
      patterned `(segment 1 (brand-by "<0 1>"))` values 0 → `false`, values 1 → `true`.
- [ ] `berlin` sampled at `0, 1/2, 1, 3/2, 5/2` → `0, 0.12944303639233112, 0.25888607278466225, 0.26789040118455887,
      0.2186607443727553`; `(with-seed 1 berlin)` at `1/2` = `berlin` at `3/2`.
- [ ] `rand-l`: `(rand-l 3)` sampled at `1/2` → `[0.2591378428041935 0.712890088558197 0.9065967965871096]`, at `0` →
      `[0 0 0]`; `(rand-l 0)` → silence.
- [ ] `randrun`: `(randrun 4)` values 0–3 → `0 3 1 2 | 2 1 3 0 | 1 3 2 0 | 1 3 0 2`, wholes at quarters;
      `(randrun 1)` → `0 [0,1)`; `(randrun 0)` → silence; every cycle 0–999 of `(randrun 5)` is a permutation of `0..4`.

**Rearrangement**
- [ ] `shuffle`: `(shuffle 4 s4)` values 0–3 → `a d b c | c b d a | b d c a | b d a c`, cycle 0 wholes `[0,1/4)` …
      `[3/4,1)`; `(shuffle 2 s4)` values 0–3 → `a b c d | c d a b | c d a b | c d a b`; `(with-seed 7 (shuffle 4 s4))` values 0–3 →
      `b d c a | c a d b | b a d c | c a d b`; `(shuffle 0 s4)` → silence; patterned `(shuffle "<1 4>" s4)` values 0 →
      `a b c d`, values 1 = `(shuffle 4 s4)` values 1.
- [ ] `scramble`: `(scramble 4 s4)` values 0–3 → `a c b a | c b b d | a b c c | d a c d`.

**Conditionals**
- [ ] `when-pat`: `(when-pat "<0 1>" rev (seq :a :b))` values 0 → `a b`, values 1 → `b a`;
      `(when-pat "1 0" (fast 2) s4)` → `a [0,1/8)`, `b [1/8,1/4)`, `c [1/4,3/8)`, `d [3/8,1/2)`, `c [1/2,3/4)`, `d [3/4,1)`;
      Lisp `(when-pat (fn [c] (> c 1)) rev (seq :a :b))` values 0–2 → `a b | a b | b a`.
- [ ] `whenmod`: `(whenmod 8 6 rev (seq 0 2 4 7))` reversed (`7 4 2 0`) in cycles 6, 7, 14, 15 and unchanged in
      cycles 0–5 and 8–13; `(whenmod 4 2 rev (seq :a :b))` values 0–3 → `a b | a b | b a | b a`;
      `(whenmod 0 0 rev s4)` ≡ `s4`; patterned `(whenmod "<4 2>" 1 rev (seq :a :b))` values 1 → `b a`.
- [ ] `apply-n`: `(apply-n 2 (fast 2) (seq :a :b))` → `a b a b a b a b` at eighths; `(apply-n 0 rev (seq :a :b))` ≡
      `(seq :a :b)`; patterned `(apply-n "<1 2>" (fast 2) (seq :a :b))` cycle 0 → 4 events at quarters, cycle 1 → 8 at eighths.

**Mini-notation** (`mini_test.cljs`)
- [ ] `?` is deterministic: `"bd?"` values 0–3 → `– | bd | – | bd`; two parses of `"hh*8?"` give identical events;
      `"a? b? c? d?"` (seeds 0–3) values 0–3 → `c d | a d | c d | a d`; `"[a b c d]?"` values 0–3 → `b | a d | c d | a c d`;
      `"hh*8?"` onset counts cycles 0–1 → `2 5`.
- [ ] `?amount`: `"hh*8?0.3"` onset counts cycles 0–1 → `4 7`; `"a?0.3 b"` values 0–3 → `b | a b | b | a b`;
      `"a? 0.3"` is two steps (`:a` degraded, then `0.3`).
- [ ] `|`: `"a | b"` values 0–7 → `a b a b a a b a`; `"[bd | sd sd | hh*3]"` values 0–5 → `bd | sd sd | bd | hh hh hh | bd | bd`;
      `"a? | b"` (`a?` seed 0, `|` seed 1) values 0–7 → `– | a | – | a | b | – | a | –`;
      `"<a | b>"`, `"{a | b}"` and `"[a, b | c]"` throw a parse error with the messages from step 9.

**Statistics** (±5 percentage points over 1000 cycles; reference values from the computation in brackets)
- [ ] Existing random functions keep their probabilities within ±5% over 1000 cycles of `s4`: `degrade-by 0.3` keeps
      0.70 [0.69825]; `degrade` keeps 0.50 [0.4995]; `sometimes-by 0.25` sends 0.25 of events to `f` [0.24925];
      `some-cycles-by 0.3` applies in 0.30 of cycles [0.287]; `(~ "hh*16?")` keeps 0.50.
- [ ] `(randcat a b c)` picks each pattern ~1/3 of cycles over 900 cycles (each count in `300 ± 45`) [285 / 306 / 309];
      `(wrandcat [3 a] [1 b])` over 1000 cycles → `a` 0.75 ± 0.05 [0.746]; `(segment 4 (choose [:a :b :c]))` over 1000
      cycles → each value 1/3 ± 0.05 [1312 / 1331 / 1357 of 4000]; `(segment 4 brand)` → `true` 0.5 ± 0.05 [0.5005];
      `(segment 4 (brand-by 0.25))` → 0.25 ± 0.05 [0.24925].
- [ ] `(shuffle 4 (seq :a :b :c :d))` uses each element exactly once per cycle (cycles 0–999); first slot holds each
      element 0.25 ± 0.05 of cycles [0.248 / 0.240 / 0.293 / 0.219]; `(scramble 4 s4)` → each value 0.25 ± 0.05 of
      4000 events [997 / 1005 / 1001 / 997].
- [ ] `(when-mod 8 6 rev pat)` from the outline is spelled `(whenmod 8 6 rev pat)` and applies `rev` in cycles 6 and 7
      of every 8 (vector above).

**Registration, docs, verification**
- [ ] Probability/count arguments accept patterns (`"<0.2 0.6>"`): the patterned vectors above for `degrade-by
      undegrade-by degrade-by-with sometimes-by some-cycles-by brand-by rand-l randrun with-seed shuffle scramble
      when-pat whenmod apply-n`. Curried forms work: `(->> (seq :bd :sd :hh :cp) (sometimes (fast 2)) (shuffle 4)
      (degrade-by 0.2) (whenmod 4 3 rev))` evaluates to a pattern.
- [ ] `cycle-hash`, `event-hash` and mini `Math/random` are gone: `git grep -n "cycle-hash\|event-hash\|Math/random"
      -- packages/` finds nothing.
- [ ] Rule 3: all 27 new names in `BuiltinName` (+ `npm run gen:grammar`), `completions.js`, `hover.js`,
      `builtin_meta.edn` (+ `npm run gen:ai-docs`); the 8 changed names have updated completion/hover/meta text;
      both generators produce no diff after the commit; `tl4-documented-examples-evaluate` passes.
- [ ] `docs/USAGE.md` sections from step 13 incl. the "Random sequences changed in TL4" note; README rows;
      STRUDEL-PARITY rows listed in step 13 ✅ and the Summary recomputed; deferred rows 📋 TL4b with
      `PROMPTS/PHASE-TL4b.md` outline + ROADMAP section and implementation-order row; ROADMAP TL4 → delivered.
- [ ] Playwright: `(shuffle 4 (seq :bd :sd :hh :cp))` → 4 onsets; `(~ "bd*4?")` → 1 onset;
      `(~ "[bd bd] | sd")` → 2 onsets; `(segment 4 (choose [:bd :sd]))` → 4 onsets.
- [ ] All existing TL1/TL2/TL3/TL3b tests pass (the migrated `choose`/`wchoose`/mini-`?` tests as described);
      `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a `## Test protocol` section (CLAUDE.md Rule 5) covering at least: `sometimes`
      vs `some-cycles` by ear (per event vs whole cycle), `randcat`, `shuffle 4` (all four drums once per cycle),
      `whenmod 4 3`, `(~ "hh*8?")` repeating identically every time it is evaluated, `?0.3` and `|`, `with-seed`
      changing the variation, the warning case `(choose [:bd :sd])` alone being silent (and `choose-cycles`
      restoring the old sound), and regressions for `every`, `degrade-by` patterned probability and `sew`.

## What NOT to do

- **Deferred to TL4b** (parity rows stay 📋, Phase column → TL4b): `pick`, `pickmod` (`pick-mod`),
  `pickF`/`pickmodF` (`pick-f`), `pickOut`/`pickmodOut` (`pick-out`), `pickRestart`/`pickReset` (+ mod variants),
  `inhabit`/`pickSqueeze`/`inhabitmod`, and Tidal-only `fix`. They are not random, need lookup-map semantics,
  `restartJoin`/`resetJoin` (not in core) and control-value matching (better after TL7). Leave the ❓ rows
  (`cyclesPer`/`per`/`perx`, `open`/`close`) untouched.
- No global or per-track seed setting, no `seed` alias, no Strudel `useRNG('precise')` murmur-hash RNG, no
  `with-seed` taking a function. No separate `while` (use `when-pat` with a pattern), no `when-mod` alias, no
  Lisp `when` special form.
- Don't change `time->rand`, `xorwise` or `time->int-seed` output, or TL2's sampling rule; don't change any
  TL1–TL3b function's semantics (`segment`, `zoom`, `repeat-cycles`, `sew`, `every`, …).
- Don't keep the old hash-based behaviour behind a flag, and don't change the `[weight value]` pair order.
- No Markov chains or generative grammars; possible later phase.
- No structure combinators: **TL3/TL3b**. No change to `arrange`/song form: **SONG1**.
