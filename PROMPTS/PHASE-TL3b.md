# Phase TL3b — Zoom, Compress & Structure Extras

> **Spec status:** full (expanded 2026-10-27 from the outline; function list from the TL3b
> rows of [docs/STRUDEL-PARITY.md](../docs/STRUDEL-PARITY.md))
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
cycle repetition, binary rhythms). 25 functions, all in `packages/core` with thin Lisp
wrappers, all with exact rational-time reference tests.

```lisp
;; Before — none of these exist; the nearest workarounds are approximate
(fast 2 (seq :bd :sd))                    ; no way to "play twice as fast, then silence"

;; After (Tidal/Strudel names in kebab-case)
(compress 1/4 3/4 (seq :bd :sd))          ; squeeze the cycle into [1/4, 3/4)
(zoom 1/4 3/4 (seq :a :b :c :d))          ; play only the middle half, stretched: b c
(linger 1/4 (seq 0 1 2 3 4 5 6 7))        ; repeat the first quarter: 0 1 0 1 0 1 0 1
(fast-gap 2 (seq :bd :sd))                ; double speed, then silence
(hurry 2 (seq :bd :sd))                   ; fast 2 and playback rate × 2
(repeat-cycles 2 (cat (seq :a) (seq :b))) ; a a b b
(sew "t f" (seq :a :a) (seq :b :b))       ; switch between two patterns
(struct (binary 5) (pure :x))             ; 5 = 101 → x ~ x
(beat "0,7,10" 16 (pure :bd))             ; hits on steps 0, 7 and 10 of 16
```

## Decisions (resolving the outline's open questions)

| Question | Decision |
|---|---|
| Batch size | **Kept as one phase: 25 functions.** Every function is a few lines on top of TL3's helpers plus one new private helper (`zoom*`); no split into TL3c. |
| `binary-l` | **Dropped** (decided in the outline). Every REPuLse built-in returns a pattern; `binary-n` gives the same bits as a step pattern. Parity row stays ⛔. |
| `hurry` | **Multiplies the existing `rate` control** (`repulse.params/rate`): `(hurry r pat)` = `(fast r pat)` with every event's `:rate` multiplied by `r` (Tidal `hurry x p = fast x p \|* speed x`). An event without `:rate` counts as rate 1, so it gets `:rate r`. Non-map values are upgraded to `{:note v :rate r}` exactly as `rate` does. Rests (`:_`) and `nil` values pass through unchanged. **No `speed` alias** — control names belong to TL7. |
| `zoom` | **Tidal `zoomArc` semantics** (Strudel's `zoom` is the same formula): cycle c of the result plays the window `[c+s, c+e)` of the source stretched linearly onto `[c, c+1)`. Matches the outline vector `(zoom 1/4 3/4 (seq :a :b :c :d))` → `b [0,1/2)`, `c [1/2,1)`. `s`/`e` may lie outside `[0,1]` (the window may cross a cycle boundary); `e ≤ s` → silence. **Deviation:** Tidal/Strudel map each span with the cycle (`sam`) of that span's own start; REPuLse maps whole and part with the **query cycle** c, so `part ⊆ whole` always holds. The two agree whenever an event's whole starts in the queried cycle (every test vector below). |
| `focus` | **Strudel `focus`:** `(late s (fast (/ 1 (- e s)) (early (sam s) pat)))` where `sam s = floor(s)`. Difference from `zoom`: `zoom` *stretches* a window of the cycle to fill the whole cycle (everything outside the window is discarded); `focus` *squeezes* the whole cycle into `[s, e)` and keeps playing (the neighbouring cycles fill the rest — no gaps, unlike `compress`). `e ≤ s` → silence. |
| `zoom` vs `zoom-arc` names | Only `zoom` (positional `s e`); `zoomArc`/`focusSpan`/`compressSpan` are 🔁 aliases in the parity doc and get no name. |
| `linger` restart | **Each cycle lingers on its own first `n`** ("restarts every cycle", as Tidal's `linger` documents): result time `c + t` plays source time `c + (t mod n)`. Strudel's literal `zoom(0,n).slow(n)` would instead play the first `n` of *different* source cycles inside one result cycle when the input varies per cycle. Negative `n` lingers on the last `|n|` of the cycle (no reversal). `n = 0` → silence; `|n| ≥ 1` → `pat` unchanged. |
| `fast-gap` factor < 1 | Tidal: `factor = 0` → silence; any other factor below 1 (incl. negative) is clamped to 1 (pattern unchanged). |
| `ribbon` | Strudel `ribbon(offset, cycles)` = `pat.early(offset).restart(pure(1).slow(cycles))`: result time `t` in block `k = floor(t / L)` (`L = cycles`) plays source time `offset + (t − k·L)`. `L ≤ 0` → silence. `L` may be a rational. |
| `revv` | Strudel `revv`: reflect time around 0 (`t ↦ −t`): result cycle c plays source cycle `−c−1` reversed. |
| `repeat-cycles` | Tidal `_repeatCycles n p = cat (replicate n p)` with Tidal `cat` offsets = Strudel `repeatCycles`: result cycle c plays source cycle `floor(c/n)` — exactly TL3's `slowcat-held*`. `n < 1` → `pat` unchanged. |
| `trunc` | Tidal `_trunc t = compress (0,t) . zoomArc (Arc 0 t)`: only `[c, c+t)` of each cycle plays; an event crossing `c+t` keeps its whole and its part is cut at `c+t` (still an onset if it started before `c+t`). `t ≤ 0` → silence, `t ≥ 1` → unchanged. |
| `press-by` | Tidal/Strudel: `squeezeJoin (compress r 1 . pure <$> pat)`: every event shifted to start `r` of the way through its own whole and shortened accordingly. `r` outside `[0,1)` → silence (that is what `compress*` returns). `press` = `(press-by 1/2 pat)`. |
| `bite` | Tidal/Strudel `bite n ipat pat`: cut each cycle into `n` slices; every event of `ipat` (an index `i`) plays slice `i mod n` (a `zoom`) squeezed into that event's whole (`squeeze-join`). Indices are truncated to integers; `:_` / non-numeric index values produce nothing; `n < 1` → silence. |
| `chunk-into` / `chunk-back-into` | Strudel: like `chunk`, but `f` is applied to the chunk's **looped sub-cycle**: the window `[i/n, (i+1)/n)` is zoomed to a full cycle, `f` is applied, the result is compressed back into the window (`(compress* a b (f (zoom* a b pat)))`, events selected by whole start like `within`). Chunk index: `chunk-into` `c mod n` (0, 1, …, n−1, as Strudel's `iterBack` of `[t f … f]`); `chunk-back-into` `(−(c+1)) mod n` (n−1, n−2, …, 0 — Strudel's `iter(n).early(1)`; note this differs from TL3's `chunk-back`, which starts at chunk 0). **Not cycle-holding** (Strudel `into` reads the actual cycle). `n < 1` → `pat` unchanged. |
| `euclidish` | Strudel `euclidish(k, n, groove)` = `pat.struct(morph(bjork(k,n), even(k), groove))`: hit j (0-based) of the Björklund rhythm sits at `p_j = idx_j / n`; the perfectly even position is `q_j = j / k`; it is placed at `p_j + (q_j − p_j)·g` with duration `1/n`. `g` is clamped to `[0,1]`. `g = 0` ≡ `(euclidean k n pat)` struct form. `k ≤ 0` or `n ≤ 0` → silence; `k > n` clamps (as `bjorklund`). No rotation argument. |
| `ply-for-each` | Strudel `plyForEach(n, f)`: each event becomes `k = max(1, ceil n)` sub-steps inside its whole; sub-step 0 is the plain value, sub-step `i ≥ 1` is `(f (pure v) i)` (Lisp `f` takes **two** arguments: the pattern and the integer index). |
| `filter-events` | Strudel `filter`/Tidal `filterValues`, renamed because Lisp `filter` (sequences) exists. `pred` receives the event **value** (Lisp users never see event maps); keep events where it is truthy. Not patterned. |
| `filter-when` | Strudel `filterWhen`: `pred` receives the **absolute** start of the event's whole (part start for analog events), keep where truthy. The time is passed as the Lisp reader would write it: a plain integer when the denominator is 1, otherwise the exact rational `[n d]`, so `(= t 0)` and `(< t 1/2)` both work. |
| `bypass` | Strudel `bypass(on)`: `on` true (`step-true?`) → silence, false → `pat`. Patterned via `patternify` (inner join), so `(bypass "0 1" pat)` silences the second half. |
| `beat` | Strudel `beat(t, div)`: every value of `pat` is placed on step `t mod div` of `div` (`pure v` compressed into `[t'/div, (t'+1)/div)`), inner-joined with `pat` (so an event only appears where `pat` has an event at that time). `t`, `div` truncated to integers; `div ≤ 0` → silence. Several steps via a stacked `t` (`"0,7,10"`). |
| `sew` | Tidal `sew pb a b = overlay (mask pb a) (mask (inv <$> pb) b)`: structure from `a` and `b`, cut to where `pb` is true / false. Rests in `pb` (`:_`) select neither. |
| `stitch` | Tidal `stitch pb a b = overlay (struct (filterValues (== True) pb) a) (struct (inv <$> filterValues (== False) pb) b)`: structure from `pb`; true steps take `a`'s value, false steps `b`'s. Rests select neither. |
| `binary` / `binary-n` | **Strudel semantics and argument order** (the parity rows are Strudel's): `(binary n)` = `n`'s bits MSB first with the minimal bit count (`5` → `1 0 1`); `(binary-n n bits)` = the low `bits` bits, MSB first; `bits` defaults to **16** (Strudel default). Tidal's `binaryN bits n` (bits first) and `binary = binaryN 8` are not used. Values are the booleans `true`/`false` (Tidal `Pattern Bool`, same as `invert`'s output). `n` truncated to an integer; `(binary n)` with `n ≤ 0` → one step `false`; `binary-n` with negative `n` uses `n mod 2^bits` (two's complement); `bits ≤ 0` → silence, `bits > 52` clamps to 52. Both arguments patterned (inner join). |
| Curried forms | Every function whose last argument is a single `pat` has the TL3 curried form (all args except `pat` → `(fn [p] …)`) for `->>`. **Exceptions:** `sew` and `stitch` (two pattern arguments) and `binary`/`binary-n` (no input pattern) have no curried form. |
| Patterned arguments | Numeric arguments go through TL1's `patternify`/`patternify2`/`patternify3` (inner join, Tidal `tParam`) and `u/pat-arg` in Lisp (strings → mini-notation). Functions without a numeric argument: `revv`, `press`, `filter-events`, `filter-when` (function arg), `sew`/`stitch` (the boolean argument is a pattern by nature). |
| Location of `hurry` | `packages/core/src/repulse/params.cljs` (it needs the private `to-map`, and `repulse.core` must not depend on `repulse.params`). The other 24 functions go into `packages/core/src/repulse/core.cljs`. |

## Function list (TL3b — 25 functions)

All times are exact rationals; `c` is the queried cycle. "Patterned?" lists the arguments
that accept a pattern or a mini-notation string.

| # | REPuLse-Lisp | Tidal / Strudel | Semantics | Patterned? |
|---|---|---|---|---|
| 1 | `(compress s e pat)` | `compress` / `compressArc` | Cycle c squeezed into `[c+s, c+e)`, silence elsewhere. `0 ≤ s < e ≤ 1`, otherwise silence. Public wrapper of `compress*`. | `s e` |
| 2 | `(zoom s e pat)` | `zoom` / `zoomArc` | Window `[c+s, c+e)` of the source stretched onto `[c, c+1)`: source time `c + s + (t−c)·(e−s)`. | `s e` |
| 3 | `(focus s e pat)` | `focus` | `(late s (fast (/ 1 (- e s)) (early (floor s) pat)))` — one cycle squeezed into `[s,e)`, no gaps. | `s e` |
| 4 | `(fast-gap n pat)` | `fastGap` | `(compress 0 (/ 1 n) pat)`: n× faster, then silence until the next cycle. `n=0` → silence, `n<1` → unchanged. | `n` |
| 5 | `(linger n pat)` | `linger` | Result `c+t` plays source `c + (t mod n)` (`n>0`) or `c + 1 − |n| + (t mod |n|)` (`n<0`). | `n` |
| 6 | `(bite n ipat pat)` | `bite` | Each `ipat` event `i` plays `(zoom (i mod n)/n (i mod n + 1)/n pat)` squeezed into its whole. | `n` (`ipat` is a pattern) |
| 7 | `(ribbon offset cycles pat)` | `ribbon` / `rib` | Loop `cycles` cycles of the source starting at `offset`: `t ↦ offset + (t mod cycles)`. | `offset cycles` |
| 8 | `(hurry r pat)` | `hurry` | `(fast r pat)` and every event's `:rate` × `r` (missing rate = 1). | `r` |
| 9 | `(revv pat)` | `revv` | `t ↦ −t`: reverse across cycles. | — |
| 10 | `(repeat-cycles n pat)` | `repeatCycles` | Cycle c plays source cycle `floor(c/n)`. | `n` |
| 11 | `(trunc t pat)` | Tidal `trunc` | Only `[c, c+t)` of each cycle; parts cut at `c+t`, wholes kept. | `t` |
| 12 | `(press pat)` | `press` | `(press-by 1/2 pat)`. | — |
| 13 | `(press-by r pat)` | `pressBy` | Each event squeezed into the last `1−r` of its own whole. | `r` |
| 14 | `(chunk-into n f pat)` | `chunkInto` | Chunk `c mod n`: `f` applied to the zoomed-out window, compressed back. | `n` |
| 15 | `(chunk-back-into n f pat)` | `chunkBackInto` | As `chunk-into`, chunk `(−(c+1)) mod n`. | `n` |
| 16 | `(euclidish k n g pat)` | `euclidish` / `eish` | Björklund hits morphed towards even spacing by `g ∈ [0,1]`; `pat` gets the rhythm (`struct`). | `k n g` |
| 17 | `(ply-for-each n f pat)` | `plyForEach` | Each event → `ceil n` sub-steps; sub-step `i ≥ 1` is `(f (pure v) i)`. | `n` |
| 18 | `(filter-events pred pat)` | `filter` / `filterValues` | Keep events whose value satisfies `pred`. | — |
| 19 | `(filter-when pred pat)` | `filterWhen` | Keep events whose whole start satisfies `pred`. | — |
| 20 | `(bypass on pat)` | `bypass` | `on` true → silence, else `pat`. | `on` |
| 21 | `(beat t div pat)` | `beat` | Values of `pat` placed on step `t mod div` of `div` (inner join). | `t div` |
| 22 | `(sew bools a b)` | Tidal `sew` | `(stack (mask bools a) (mask (invert bools) b))`. | `bools` |
| 23 | `(stitch bools a b)` | Tidal `stitch` | `(stack (struct bools a) (struct (invert bools) b))`. | `bools` |
| 24 | `(binary n)` | `binary` | Bits of `n`, MSB first, minimal bit count, as a `true`/`false` step sequence. | `n` |
| 25 | `(binary-n n [bits])` | `binaryN` | The low `bits` (default 16) bits of `n`, MSB first. | `n bits` |

## Background

### Pattern engine — `packages/core/src/repulse/core.cljs`

- Rationals are `[n d]` vectors: `rat`, `rat+`, `rat-`, `rat*`, `rat-div`, `rat<`, `rat<=`,
  `rat>`, `rat>=`, `rat-min`, `rat-max`, `rat->float`, `int->rat`, `rat-neg`; `->rat`
  coerces user input (ints, `[n d]`, floats → best rational).
- `span`, `span-intersect` (returns `nil` for empty/zero-width intersections),
  `cycle-span`, `split-cycles`; private `per-cycle` (lift a one-cycle query fn to
  multi-cycle spans), `map-spans` (map `:part` and, when present, `:whole`), `->pat`,
  `whole-or-part`, `with-source`.
- `pattern`, `pattern?`, `query`, `pure` (optional `source` arg), `seq*`, `stack*`, `fmap`,
  `silence`, `onset?`, `query-onsets`.
- TL1: `app-left`, `app-right`, `app-both`, `inner-join`, `outer-join`, `squeeze-join`
  (the outer event in cycle c plays cycle c of its inner pattern, squeezed into its
  whole), `patternify` `(f arg pat)`, `patternify2` `(f a b pat)`, `patternify3`
  `(f a b c pat)` — scalars take the fast path, patterned args are inner-joined.
- Private scalar `fast*`, `slow*`, `late*`; public patternified `fast`, `slow`, `late`,
  `early`; `rev` (per-cycle), `cat*` (child `c mod n` at cycle c, no Tidal offset).
- TL3 section (`;;; ── Phase TL3: Time & structure combinators`):
  - public `step-true?` (false for `false nil 0 :_ :f :false`), `timecat`, `struct`,
    `mask`, `invert` (`:_` stays `:_`, other values → `true`/`false`), `bjorklund`.
  - private `keep-values`, `cycle-of`, `cycle-pos-rat`, `shift-event`, `->int`
    (truncate), `fmap-event` (value ← `(f event)`), `euclid-bools` (rotated Björklund
    vector).
  - private `compress*` `[s e pat]` — Tidal `compressArc`; returns `silence` unless
    `0 ≤ s < e ≤ 1`; parts are clipped to the window.
  - private `slowcat-held*` `[n pat-of]` — result cycle c plays `(pat-of c)` at source
    cycle `floor(c/n)`.
  - private `within* [s e f pat]` — `f pat` events whose whole starts at cycle position in
    `[s,e)` stacked with `pat` events whose whole starts outside it.
  - private `fastcat* [xs]` = `(timecat (mapv (fn [x] [1 x]) xs))`.
  - `ply*`/`ply-with*` show the `squeeze-join (fmap-event …)` idiom (keeps `:source`
    via `(pure (:value %) (:source %))`); `chunk*` shows the per-cycle window variant
    and the `n < 1 → pat` rule.
- `ns`: `(:refer-clojure :exclude [rand time chunk iter])`. None of the 24 new core names
  shadows a `cljs.core` var (checked); if `npx shadow-cljs compile test` warns otherwise,
  add the name to the exclude list.
- Tests: `packages/core/src/repulse/core_test.cljs` (TL3 tests are named `tl3-…`).

### Parameters — `packages/core/src/repulse/params.cljs`

- `(:require [repulse.core :as core])`. Private `to-map` (`(if (map? v) v {:note v})`)
  and `apply-param` (structure from the note pattern via `core/app-right`).
- `rate` — `([r] …)` / `([r pat] (apply-param :rate r pat))`: sets `:rate`, a float
  playback-rate multiplier. `app/src/repulse/audio.cljs` reads `(:rate value)` when it
  triggers samples. There is no `speed` control.
- Tests: `packages/core/src/repulse/params_test.cljs`.

### Lisp — `packages/lisp/src/repulse/lisp/`

- `builtins/structure.cljs` (TL3; merged in `eval.cljs` `make-env` right after
  `b-signals`): helpers `xf` (wrap a 1-arg Lisp fn so `SourcedVal`s never reach core),
  `step->pat`, and the arity builders `p1` `(f pat)`, `n+p` `(f n pat)` + curried,
  `n+f+p` `(f n fn pat)` + curried, `a+b+p` `(f a b pat)` + curried; numeric args go
  through `u/pat-arg`, patterns through `u/unwrap`. TL3b extends this file.
- `util.cljs`: `u/unwrap`, `u/source-of`, `u/pat-arg` (strings → `mini/parse` with
  editor offsets; numbers, `[n d]`, patterns pass raw), `u/->num` (`[n d]` → float; used
  by the Lisp `<`, `>`, `*`, `mod` in `builtins/math.cljs`, so rationals compare fine).
- `builtins/collection.cljs` already defines `"filter"` (sequences) — hence
  `filter-events`. None of the 25 TL3b names exists anywhere in `packages/lisp/src`,
  the grammar, `completions.js` or `builtin_meta.edn` (checked with grep).
- `builtins/params.cljs` wraps `repulse.params` (`"rate"` etc.).
- Tests: `integration_test.cljs` (`eval-pattern`, `tl3-documented-examples-evaluate`
  evaluates every TL3 example), `mini_test.cljs`. Mini-notation already supports `,`
  stacks (`"0,7,10"`), `<…>` alternation and rational atoms (`"1/4"` → `[1 4]`).

### App, editor & AI docs

- `app/src/repulse/audio.cljs` triggers only `core/query-onsets` and skips `:_` — so
  fragments (non-onset parts produced by `zoom`, `sew`, `focus`, …) are silent.
- `app/src/repulse/lisp-lang/repulse-lisp.grammar` `BuiltinName` (TL3 names are on one
  line starting `"struct" | "mask" | …`), `parser.js`/`parser.terms.js` via
  `npm run gen:grammar`; `completions.js`; `hover.js` (`DOCS` map); `insert-categories.js`
  is not touched.
- `app/src/repulse/content/builtin_meta.edn` → `docs/ai/builtins.json` via
  `npm run gen:ai-docs` (`scripts/gen_ai_docs.mjs` fails if a completion label has no
  meta entry).
- `e2e/audio-render.spec.ts`: `render(page, code)`, `countOnsets`.

### Docs

- `docs/USAGE.md` section `## Time & structure — \`struct\` \`ply\` \`chunk\` \`iter\` \`swing\` \`polymeter\``
  (reference table "Function | Meaning | Patterned args", ~line 943).
- `docs/STRUDEL-PARITY.md`: 20 TL3b rows in "Time & structure — TL3 / TL3b"
  (`euclidish plyForEach compress fastGap focus zoom linger bite press pressBy revv
  repeatCycles chunkInto chunkBackInto ribbon hurry filter filterWhen bypass beat`), the
  `binary` / `binaryN` row in the signals section, `sew stitch trunc` in "Tidal-only
  functions", and the Summary table.
- `ROADMAP.md` "Phase TL3b" section; `README.md` "Built-in pattern functions" table.

## Implementation

Work in this order; each step ends with `npm run test` green.

### 0. Verify tooling (CLAUDE.md Rule 4)

`npm run gen:grammar && npm run gen:ai-docs && npm run test` on the unmodified branch.

### 1. Windows — `core.cljs` (new block `;;; ── Phase TL3b: …` after the TL3 section)

```clojure
(defn compress
  "Squeeze each cycle into [s, e) of the cycle (0 <= s < e <= 1), silence elsewhere."
  [s e pat] ((patternify2 compress*) s e pat))

(defn- zoom*
  "Tidal zoomArc: window [c+s, c+e) of cycle c stretched onto [c, c+1)."
  [s e pat]
  (let [s (->rat s) e (->rat e) d (rat- e s)]
    (if (rat<= e s)
      silence
      (pattern
       (per-cycle
        (fn [piece]
          (let [c      (int->rat (cycle-of piece))
                to-src (fn [t] (rat+ (rat+ c s) (rat* (rat- t c) d)))
                to-res (fn [t] (rat+ c (rat-div (rat- t (rat+ c s)) d)))]
            (map (fn [ev] (map-spans #(span (to-res (:start %)) (to-res (:end %))) ev))
                 (query pat (span (to-src (:start piece)) (to-src (:end piece))))))))))))

(defn zoom [s e pat] ((patternify2 zoom*) s e pat))

(defn- focus* [s e pat]
  (let [s (->rat s) e (->rat e)]
    (if (rat<= e s)
      silence
      (late* s (fast* (rat-div [1 1] (rat- e s))
                      (late* (rat-neg (int->rat (cycle-of (span s s)))) pat))))))

(defn focus [s e pat] ((patternify2 focus*) s e pat))

(defn- fast-gap* [n pat]
  (let [n (->rat n)]
    (cond (zero? (first n)) silence
          (rat< n [1 1])    pat
          :else             (compress* [0 1] (rat-div [1 1] n) pat))))
```

**`linger*`** — per-cycle stack of shifted copies; uses only `late*`:

```clojure
(defn- linger* [n pat]
  (let [n (->rat n) len (if (neg? (first n)) (rat-neg n) n)]
    (cond
      (zero? (first n))    silence
      (rat>= len [1 1])    pat
      :else
      (let [off (if (neg? (first n)) (rat- [1 1] len) [0 1])   ; window start in the cycle
            k   (int (Math/ceil (rat->float (rat-div [1 1] len))))]
        (pattern
         (per-cycle
          (fn [piece]
            (let [c (int->rat (cycle-of piece))]
              (mapcat (fn [i]
                        (let [ws  (rat+ c (rat* (int->rat i) len))
                              win (span-intersect piece (span ws (rat-min (rat+ ws len) (rat+ c [1 1]))))]
                          (when win
                            ;; copy i: result c + i·len + u  ←  source c + off + u
                            (query (late* (rat- (rat* (int->rat i) len) off) pat) win))))
                      (range k))))))))))
```

**`ribbon*`** — split the query at multiples of `L`, shift each block (new private helper
`split-at-multiples [L sp]`, the rational analogue of `split-cycles`):

```clojure
(defn- ribbon* [offset cycles pat]
  (let [off (->rat offset) L (->rat cycles)]
    (if (rat<= L [0 1])
      silence
      (pattern
       (fn [sp]
         (mapcat (fn [[k piece]]          ; k = block index, piece ⊆ [k·L, (k+1)·L)
                   (query (late* (rat- (rat* (int->rat k) L) off) pat) piece))
                 (split-at-multiples L sp)))))))
```

**`trunc*`** — `t ≤ 0` → silence, `t ≥ 1` → `pat`; otherwise per-cycle
`(when-let [w (span-intersect piece (span c (rat+ c t)))] (query pat w))` (wholes are kept,
parts are already clipped by the query).

**`revv`** — `(let [neg (fn [{s :start e :end}] (span (rat-neg e) (rat-neg s)))] (pattern (fn [sp] (map #(map-spans neg %) (query pat (neg sp))))))`.

**`repeat-cycles*`** — `(let [n (->int n)] (if (< n 1) pat (slowcat-held* n (constantly pat))))`.

Public wrappers: `fast-gap`, `linger`, `trunc`, `repeat-cycles` via `patternify`; `ribbon`
via `patternify2`.

### 2. Squeezes & slices — `core.cljs`

```clojure
(defn- press-by* [r pat]
  (squeeze-join (fmap-event #(compress* r [1 1] (pure (:value %) (:source %))) pat)))
(defn press-by [r pat] ((patternify press-by*) r pat))
(defn press [pat] (press-by* [1 2] pat))

(defn- bite* [n ipat pat]
  (let [n (->int n)]
    (if (< n 1)
      silence
      (squeeze-join
       (pattern
        (fn [sp]
          (keep (fn [ev]
                  (let [v (:value ev)]
                    (when (or (number? v) (vector? v))      ; [n d] rationals too
                      (let [i (mod (->int v) n)]
                        (assoc ev :value (zoom* (rat i n) (rat (inc i) n) pat))))))
                (query ipat sp))))))))
(defn bite [n ipat pat] ((patternify (fn [n' p] (bite* n' (->pat ipat) p))) n pat))
```

`chunk-into*` / `chunk-back-into*` — per-cycle variant (no holding):

```clojure
(defn- chunk-into* [n f pat idx-fn]
  (let [n (->int n)]
    (if (< n 1)
      pat
      (pattern
       (per-cycle
        (fn [sp]
          (let [i (idx-fn (cycle-of sp) n) a (rat i n) b (rat (inc i) n)]
            (query (within* a b #(compress* a b (f (zoom* a b %))) pat) sp))))))))
;; chunk-into: #(mod %1 %2)   chunk-back-into: #(mod (- (inc %1)) %2)
```

`ply-for-each*` — like `ply-with*`:

```clojure
(defn- ply-for-each* [n f pat]
  (let [k (max 1 (int (Math/ceil (rat->float (->rat n)))))]
    (squeeze-join
     (fmap-event (fn [ev]
                   (let [p0 (pure (:value ev) (:source ev))]
                     (fastcat* (into [p0] (map #(f p0 %) (range 1 k))))))
                 pat))))
```

`euclidish*` — booleans from `euclid-bools` (rotation 0), positions morphed, built from
`compress*` copies of `(pure true)`, then `struct`:

```clojure
(defn- euclidish* [k n g pat]
  (let [bs   (euclid-bools k n 0)
        n'   (count bs)
        idxs (vec (keep-indexed (fn [i b] (when b i)) bs))
        k'   (count idxs)
        g    (rat-max [0 1] (rat-min [1 1] (->rat g)))]
    (if (or (<= (->int k) 0) (zero? n') (zero? k'))   ; negative k → silence (no inversion)
      silence
      (struct (stack* (map-indexed
                       (fn [j i]
                         (let [p (rat i n') q (rat j k')
                               b (rat+ p (rat* (rat- q p) g))]
                           (compress* b (rat+ b (rat 1 n')) (pure true))))
                       idxs))
              pat))))
(defn euclidish [k n g pat] ((patternify3 euclidish*) k n g pat))
```

### 3. Filters & switches — `core.cljs`

```clojure
(defn filter-events [pred pat] (keep-values (comp boolean pred) pat))

(defn- lisp-time [[n d]] (if (= d 1) n [n d]))
(defn filter-when [pred pat]
  (pattern (fn [sp] (filter #(pred (lisp-time (:start (whole-or-part %)))) (query pat sp)))))

(defn bypass [on pat]
  ((patternify (fn [o p] (if (step-true? o) silence p))) on pat))

(defn- beat* [t div pat]
  (let [div (->int div)]
    (if (<= div 0)
      silence
      (let [t (mod (->int t) div)]
        (inner-join (fmap-event #(compress* (rat t div) (rat (inc t) div)
                                            (pure (:value %) (:source %)))
                                pat))))))
(defn beat [t div pat] ((patternify2 beat*) t div pat))

(defn sew [bools a b]
  (let [bools (->pat bools)]
    (stack* [(mask bools a) (mask (invert bools) b)])))

(defn stitch [bools a b]
  (let [bools (->pat bools)]
    (stack* [(struct bools a) (struct (invert bools) b)])))
```

`struct`/`mask` already drop rest/false steps via `step-true?`; `invert` keeps `:_`, so
rests select neither side.

### 4. Binary — `core.cljs`

```clojure
(defn- bits->pat [n bits]                          ; n >= 0 integer, bits >= 1
  (seq* (mapv #(odd? (Math/floor (/ n (Math/pow 2 %)))) (range (dec bits) -1 -1))))

(defn- binary* [n]
  (let [n (->int n)]
    (if (<= n 0) (seq* [false]) (bits->pat n (count (.toString n 2))))))
(defn binary [n] (if (pattern? n) (inner-join (fmap binary* n)) (binary* n)))

(defn- binary-n* [n bits]
  (let [bits (min 52 (->int bits))]
    (if (<= bits 0)
      silence
      (bits->pat (mod (->int n) (Math/pow 2 bits)) bits))))
(defn binary-n
  ([n] (binary-n n 16))
  ([n bits]
   (if (or (pattern? n) (pattern? bits))
     (inner-join (app-both binary-n* (->pat n) (->pat bits)))
     (binary-n* n bits))))
```

### 5. `hurry` — `params.cljs`

```clojure
(defn- hurry* [r pat]
  (let [f (core/rat->float (core/->rat r))]
    (core/fmap (fn [v]
                 (if (or (nil? v) (= :_ v))
                   v
                   (let [m (to-map v)] (assoc m :rate (* (or (:rate m) 1) f)))))
               (core/fast r pat))))

(defn hurry
  "fast r, and multiply every event's :rate by r (missing :rate counts as 1)."
  ([r]     (fn [pat] (hurry r pat)))
  ([r pat] ((core/patternify hurry*) r pat)))
```

(`core/fast` with a scalar takes the fast path; `core/fast` with `r = 0` is silence.)

### 6. Lisp builtins — `builtins/structure.cljs`

Add `[repulse.params :as params]` to the `ns` require and update the docstring. New
helpers:

```clojure
(defn- xf2 [f] (let [f (u/unwrap f)] (fn [p i] (u/unwrap (f p i)))))
(defn- pred [f] (let [f (u/unwrap f)] (fn [v] (u/unwrap (f v)))))
```

| Name | Builder / arities |
|---|---|
| `revv`, `press` | `p1` |
| `fast-gap`, `linger`, `trunc`, `repeat-cycles`, `press-by`, `bypass` | `n+p` |
| `hurry` | `n+p` over `params/hurry` |
| `compress`, `zoom`, `focus`, `ribbon`, `beat` | `a+b+p` |
| `bite` | `a+b+p` (`n` and `ipat` both through `u/pat-arg`) |
| `chunk-into`, `chunk-back-into` | `n+f+p` |
| `ply-for-each` | `[n f]`, `[n f p]` — `n` via `u/pat-arg`, `f` via `xf2` |
| `euclidish` | `[k n g]`, `[k n g p]` — all three via `u/pat-arg` (same shape as `euclid-legato-rot`) |
| `filter-events`, `filter-when` | `[f]`, `[f p]` — `f` via `pred` |
| `sew`, `stitch` | `[b x y]` only — `b` via `u/pat-arg`, `x`/`y` via `u/unwrap` |
| `binary` | `[n]` via `u/pat-arg` |
| `binary-n` | `[n]`, `[n bits]` via `u/pat-arg` |

### 7. Tests

- `packages/core/src/repulse/core_test.cljs`: one `deftest tl3b-<name>` per DoD vector
  below (compare `[value whole part]` triples sorted by whole start; `query-onsets` where a
  vector says "onsets").
- `packages/core/src/repulse/params_test.cljs`: `hurry` vectors.
- `packages/lisp/src/repulse/lisp/integration_test.cljs`: Lisp-level vectors for the
  functions that take Lisp closures (`ply-for-each`, `filter-events`, `filter-when`,
  `chunk-into` with `rev`), curried forms in `->>`, and
  `tl3b-documented-examples-evaluate` (every TL3b example in `builtin_meta.edn` evaluates
  to a pattern).
- `e2e/audio-render.spec.ts`: `(compress 1/4 3/4 (seq :bd :sd))` → 2 onsets;
  `(hurry 2 (seq :bd :sd))` → 4 onsets; `(stitch "t f t t" (pure :bd) (pure :sd))` → 4 onsets.

### 8. Registration (CLAUDE.md Rule 3)

New `BuiltinName` line: `compress zoom focus fast-gap linger bite ribbon hurry revv
repeat-cycles trunc press press-by chunk-into chunk-back-into euclidish ply-for-each
filter-events filter-when bypass beat sew stitch binary binary-n`. Run `npm run gen:grammar`
and check that `fast-gap`, `press-by`, `chunk-back-into`, `ply-for-each`, `filter-when`,
`repeat-cycles` and `binary-n` each highlight as **one** token, and that `press` inside
`press-by` and a user symbol like `zoomed` are not mis-tokenised. Then `completions.js`
(`type: "function"`), `hover.js` `DOCS`, `builtin_meta.edn` (category `"transform"`,
except `binary`/`binary-n` → `"source"`; `:returns "Pattern"`, `:side-effects []`, ≥ 1
example — one patterned example for every function with a patterned argument —
`:see-also`), `npm run gen:ai-docs`. Commit grammar, `parser.js`, `parser.terms.js`,
`completions.js`, `hover.js`, `builtin_meta.edn`, `docs/ai/builtins.json` together.

### 9. Docs

- `docs/USAGE.md`: extend the "Time & structure" reference table with 25 rows (signature,
  meaning, patterned args) and add a subsection "Windows, switches & binary rhythms" with
  examples: `zoom` vs `focus` vs `compress` (one example each, same input), `linger`,
  `hurry` (note: multiplies `rate`), `sew`/`stitch`, `beat "0,7,10" 16`, `binary`. Note
  the `filter-events` name (Lisp `filter` is for sequences) and the `filter-when` time
  argument format.
- `README.md`: rows `| (zoom 1/4 3/4 pat) / (compress 1/4 3/4 pat) | Play a window / squeeze into a window |`
  and `| (sew "t f" a b) / (struct (binary 5) pat) | Switch patterns, binary rhythm |`.
- `docs/STRUDEL-PARITY.md`: the 20 Time & structure TL3b rows, `binary`/`binaryN`, and the
  Tidal-only `sew`, `stitch`, `trunc` → ✅ (hurry note: "`fast` + multiplies `rate`";
  linger note: "restarts every cycle"; zoom note: "Tidal zoomArc"; binary note: "Strudel
  arg order, default 16 bits"); recompute the Summary table.
- `ROADMAP.md`: TL3b → `✅ *delivered*` with a **Delivered:** list (TL3 style).

## Files to change

| File | Change |
|---|---|
| `packages/core/src/repulse/core.cljs` | TL3b block: private `zoom* focus* fast-gap* linger* split-at-multiples ribbon* trunc* repeat-cycles* press-by* bite* chunk-into* ply-for-each* euclidish* beat* lisp-time bits->pat binary* binary-n*`; public `compress zoom focus fast-gap linger bite ribbon revv repeat-cycles trunc press press-by chunk-into chunk-back-into euclidish ply-for-each filter-events filter-when bypass beat sew stitch binary binary-n` |
| `packages/core/src/repulse/params.cljs` | `hurry*`, `hurry` |
| `packages/core/src/repulse/core_test.cljs` | `tl3b-*` DoD vectors |
| `packages/core/src/repulse/params_test.cljs` | `hurry` vectors |
| `packages/lisp/src/repulse/lisp/builtins/structure.cljs` | Require `repulse.params`; `xf2`, `pred`; 25 builtins |
| `packages/lisp/src/repulse/lisp/integration_test.cljs` | Lisp vectors, curried forms, `tl3b-documented-examples-evaluate` |
| `e2e/audio-render.spec.ts` | 3 onset-count tests |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` + `parser.js` + `parser.terms.js` | 25 names (`npm run gen:grammar`) |
| `app/src/repulse/lisp-lang/completions.js`, `hover.js` | 25 entries each |
| `app/src/repulse/content/builtin_meta.edn` + `docs/ai/builtins.json` | 25 entries (`npm run gen:ai-docs`) |
| `docs/USAGE.md`, `README.md`, `docs/STRUDEL-PARITY.md`, `ROADMAP.md` | Step 9 |

## Definition of done

"Cycle c" means `(query pat (cycle-span c))`; "onsets" means `query-onsets`. Events are
written `value [whole-start, whole-end)`; whole = part unless a part is given. All times
are exact rationals. One `deftest` per bullet.

**Windows**
- [ ] `compress`: `(compress 1/4 3/4 (seq :bd :sd))` → `bd [1/4,1/2)`, `sd [1/2,3/4)`; cycle 1 → `bd [5/4,3/2)`,
      `sd [3/2,7/4)`; `(compress 3/4 1/4 (seq :bd :sd))` and `(compress 1/2 3/2 (seq :bd :sd))` → no events;
      patterned `(compress "<0 1/2>" 1 (seq :a :b))` cycle 0 → `a [0,1/2)`, `b [1/2,1)`; cycle 1 → `a [3/2,7/4)`, `b [7/4,2)`.
- [ ] `zoom`: `(zoom 1/4 3/4 (seq :a :b :c :d))` → `b [0,1/2)`, `c [1/2,1)`;
      crossing a cycle `(zoom 1/2 3/2 (cat (seq :a :b) (seq :c :d)))` cycle 0 → `b [0,1/2)`, `c [1/2,1)`;
      `(zoom 1/4 3/4 (pure :x))` → one event `x` whole `[-1/2,3/2)` part `[0,1)` (no onset);
      patterned `(zoom 0 "<1 1/2>" (seq :a :b :c :d))` cycle 1 → `a [1,3/2)`, `b [3/2,2)`.
- [ ] `focus`: `(focus 1/4 3/4 (seq :a :b :c :d))` cycle 0 values in time order `c d a b c d a b`, each 1/8 long,
      first `c [0,1/8)`, `a [1/4,3/8)`; `(focus 1/4 3/4 (cat (pure :a) (pure :b)))` cycle 0 → `b` whole `[-1/4,1/4)` part
      `[0,1/4)`, `a [1/4,3/4)`, `b` whole `[3/4,5/4)` part `[3/4,1)`;
      patterned `(focus 0 "<1 1/2>" (seq :a :b))` cycle 0 → `a [0,1/2)`, `b [1/2,1)`; cycle 1 → `a b a b` at quarters.
- [ ] `fast-gap`: `(fast-gap 2 (seq :bd :sd))` → `bd [0,1/4)`, `sd [1/4,1/2)`; cycle 1 → `bd [1,5/4)`, `sd [5/4,3/2)`;
      `(fast-gap 1/2 (seq :a :b))` ≡ `(seq :a :b)`; `(fast-gap 0 (seq :a :b))` → no events;
      patterned `(fast-gap "<1 2>" (seq :a :b))` cycle 1 → `a [1,5/4)`, `b [5/4,3/2)`.
- [ ] `linger`: `(linger 1/4 (seq 0 1 2 3 4 5 6 7))` → values `0 1 0 1 0 1 0 1`, wholes `[0,1/8)` … `[7/8,1)`;
      `(linger 3/8 (seq 0 1 2 3 4 5 6 7))` → `0 1 2 0 1 2 0 1`; `(linger -1/4 (seq 0 1 2 3 4 5 6 7))` → `6 7 6 7 6 7 6 7`;
      restart: `(linger 1/2 (cat (seq :a :b) (seq :c :d)))` cycle 1 → `c [1,3/2)`, `c [3/2,2)`;
      patterned `(linger "<1/4 1/2>" (seq 0 1 2 3 4 5 6 7))` cycle 1 → `0 1 2 3 0 1 2 3` at eighths.
- [ ] `ribbon`: `(ribbon 1 2 (cat (pure :a) (pure :b) (pure :c) (pure :d)))` cycles 0–3 → `b [0,1)`, `c [1,2)`, `b [2,3)`, `c [3,4)`;
      `(ribbon 1/2 1 (seq :a :b))` cycle 0 → `b [0,1/2)`, `a [1/2,1)`;
      patterned `(ribbon "<0 1>" 1 (cat (pure :a) (pure :b) (pure :c)))` cycle 0 → `a [0,1)`, cycle 1 → `b [1,2)` (offset 1 at cycle 1 plays source time `1 + (t − 1)`).
- [ ] `trunc`: `(trunc 3/4 (seq :a :b :c :d))` → `a [0,1/4)`, `b [1/4,1/2)`, `c [1/2,3/4)`;
      `(trunc 1/2 (seq :a :b :c))` → `a [0,1/3)`, `b` whole `[1/3,2/3)` part `[1/3,1/2)` (an onset);
      patterned `(trunc "<1 1/2>" (seq :a :b :c :d))` cycle 1 → `a [1,5/4)`, `b [5/4,3/2)`.
- [ ] `revv`: `(revv (seq :a :b :c))` → `c [0,1/3)`, `b [1/3,2/3)`, `a [2/3,1)`;
      `(revv (cat (pure :a) (pure :b) (pure :c)))` cycles 0–2 → `c [0,1)`, `b [1,2)`, `a [2,3)`.
- [ ] `repeat-cycles`: `(repeat-cycles 2 (cat (pure :a) (pure :b)))` cycles 0–3 → `a a b b`;
      patterned `(repeat-cycles "<1 2>" (cat (pure :a) (pure :b) (pure :c)))` cycles 0–3 → `a a c b`.

**Squeezes, slices & chunks**
- [ ] `press`/`press-by`: `(press (seq :a :b))` → `a [1/4,1/2)`, `b [3/4,1)`; `(press-by 1/4 (seq :a :b))` → `a [1/8,1/2)`, `b [5/8,1)`;
      `(press-by 1 (seq :a :b))` → no events; patterned `(press-by "<0 1/2>" (seq :a :b))` cycle 0 → `a [0,1/2)`, `b [1/2,1)`;
      cycle 1 → `a [5/4,3/2)`, `b [7/4,2)`; `:source` of `(press (~ "a b"))` events equals the source of the tokens.
- [ ] `bite`: `(bite 4 (seq 0 2) (seq :a :b :c :d :e :f :g :h))` → `a [0,1/4)`, `b [1/4,1/2)`, `e [1/2,3/4)`, `f [3/4,1)`;
      `(bite 4 (seq 0 5) …same…)` → `a b c d` at quarters (5 mod 4 = 1);
      patterned `(bite "<4 2>" (seq 0 1) (seq :a :b :c :d :e :f :g :h))` cycle 1 → `a b c d e f g h` at eighths of cycle 1.
- [ ] `chunk-into`: `(chunk-into 2 rev (seq 0 1 2 3))` cycles 0–1 → `1 0 2 3 | 0 1 3 2` (cycle 0: `1 [0,1/4)`, `0 [1/4,1/2)`);
      `(chunk-into 4 (fast 2) (seq 0 1 2 3 4 5 6 7))` cycle 0 → `0 [0,1/16)`, `1 [1/16,1/8)`, `0 [1/8,3/16)`, `1 [3/16,1/4)`,
      then `2 … 7` unchanged at eighths; patterned `(chunk-into "<4 2>" rev (seq 0 1 2 3))` cycle 1 → `0 1 3 2`.
- [ ] `chunk-back-into`: `(chunk-back-into 2 rev (seq 0 1 2 3))` cycles 0–1 → `0 1 3 2 | 1 0 2 3`;
      patterned `(chunk-back-into "<4 2>" rev (seq 0 1 2 3))` cycle 1 → `1 0 2 3`.
- [ ] `euclidish`: `(euclidish 3 8 0 (pure :x))` → `[0,1/8)`, `[3/8,1/2)`, `[3/4,7/8)` (same as `(euclidean 3 8 (pure :x))`);
      `(euclidish 3 8 1 (pure :x))` → `[0,1/8)`, `[1/3,11/24)`, `[2/3,19/24)`;
      `(euclidish 3 8 1/2 (pure :x))` → `[0,1/8)`, `[17/48,23/48)`, `[17/24,5/6)`;
      patterned `(euclidish 3 8 "<0 1>" (pure :x))` cycle 1 onsets `1 4/3 5/3`;
      `(euclidish "<3 5>" 8 0 (pure :x))` cycle 1 onsets `1 5/4 11/8 13/8 7/4`.
- [ ] `ply-for-each`: `(ply-for-each 3 (fn [p i] (add (* i 12) p)) (seq 0 7))` → `0 [0,1/6)`, `12 [1/6,1/3)`, `24 [1/3,1/2)`,
      `7 [1/2,2/3)`, `19 [2/3,5/6)`, `31 [5/6,1)`; patterned `(ply-for-each "<1 2>" (fn [p i] (add i p)) (seq 0 7))`
      cycle 0 → `0 [0,1/2)`, `7 [1/2,1)`; cycle 1 → `0 [1,5/4)`, `1 [5/4,3/2)`, `7 [3/2,7/4)`, `8 [7/4,2)`.

**Filters & switches**
- [ ] `filter-events`: `(filter-events (fn [v] (> v 2)) (seq 1 2 3 4))` → `3 [1/2,3/4)`, `4 [3/4,1)`;
      `(filter-events (fn [v] (= v :bd)) (seq :bd :sd :bd))` → `bd [0,1/3)`, `bd [2/3,1)`.
- [ ] `filter-when`: `(filter-when (fn [t] (< t 1/2)) (seq :a :b :c :d))` cycle 0 → `a [0,1/4)`, `b [1/4,1/2)`, cycle 1 → no events;
      `(filter-when (fn [t] (= t 0)) (seq :a :b))` → `a [0,1/2)` (integer times are plain integers).
- [ ] `bypass`: `(bypass 1 (seq :a :b))` → no events; `(bypass 0 (seq :a :b))` ≡ `(seq :a :b)`;
      patterned `(bypass "<0 1>" (seq :a :b))` cycle 0 → `a b`, cycle 1 → no events;
      `(bypass "0 1" (seq :a :b :c :d))` → `a [0,1/4)`, `b [1/4,1/2)`.
- [ ] `beat`: `(beat "0,7,10" 16 (pure :bd))` → `bd [0,1/16)`, `bd [7/16,1/2)`, `bd [5/8,11/16)`;
      `(beat 2 4 (seq :a :b))` → only `b [1/2,3/4)`; `(beat 17 16 (pure :x))` → `x [1/16,1/8)`;
      patterned `(beat "<0 1>" 4 (pure :bd))` cycle 1 → `bd [5/4,3/2)`.
- [ ] `sew`: `(sew "t f" (seq :a :a :a :a) (seq :b :b :b :b))` → `a [0,1/4)`, `a [1/4,1/2)`, `b [1/2,3/4)`, `b [3/4,1)`;
      `(sew "t f" (seq :a :b :c) (pure :x))` → `a [0,1/3)`, `b` whole `[1/3,2/3)` part `[1/3,1/2)`, `x` whole `[0,1)` part `[1/2,1)` (not an onset);
      patterned `(sew "<t f>" (pure :a) (pure :b))` cycle 0 → `a [0,1)`, cycle 1 → `b [1,2)`.
- [ ] `stitch`: `(stitch "t f t t" (pure :bd) (pure :sn))` → `bd [0,1/4)`, `sn [1/4,1/2)`, `bd [1/2,3/4)`, `bd [3/4,1)`;
      `(stitch "t ~ f" (pure :a) (pure :b))` → `a [0,1/3)`, `b [2/3,1)`.

**Binary**
- [ ] `binary`: `(binary 5)` → `true [0,1/3)`, `false [1/3,2/3)`, `true [2/3,1)`; `(struct (binary 5) (pure :x))` → `x [0,1/3)`, `x [2/3,1)`;
      `(binary 1)` → `true [0,1)`; `(binary 0)` → `false [0,1)`; patterned `(binary "<5 3>")` cycle 1 → `true [1,3/2)`, `true [3/2,2)`.
- [ ] `binary-n`: `(binary-n 5 4)` → `false true false true` at quarters; `(binary-n 5)` → 16 events of 1/16, the last three
      `true [13/16,7/8)`, `false [7/8,15/16)`, `true [15/16,1)`, the first 13 `false`; `(binary-n -1 4)` → four `true`;
      `(binary-n 5 0)` → no events; patterned `(binary-n 1 "<2 4>")` cycle 0 → `false [0,1/2)`, `true [1/2,1)`;
      cycle 1 → `false false false true` at quarters of cycle 1.

**`hurry`** (`params_test.cljs`)
- [ ] `(hurry 2 (seq :bd :sd))` → `{:note :bd :rate 2} [0,1/4)`, `{:note :sd :rate 2} [1/4,1/2)`, `{:note :bd :rate 2} [1/2,3/4)`,
      `{:note :sd :rate 2} [3/4,1)`; `(hurry 2 (rate 1.5 (seq :bd)))` → `{:note :bd :rate 3}` at `[0,1/2)` and `[1/2,1)`;
      `(hurry 1/2 (seq :a :b))` cycle 0 → only `{:note :a :rate 0.5}` whole `[0,1)` (cycle 1 → only `:b`); `(hurry 2 (seq :bd :_))` keeps `:_` as `:_`;
      patterned `(hurry "<1 2>" (seq :bd :sd))` cycle 0 → 2 events with `:rate 1`, cycle 1 → 4 events with `:rate 2`.

**Registration, docs, verification**
- [ ] Every function with a numeric argument accepts a pattern there (the patterned vectors above: `compress zoom focus
      fast-gap linger ribbon trunc repeat-cycles press-by bite chunk-into chunk-back-into euclidish ply-for-each bypass beat
      binary binary-n hurry`); `revv press filter-events filter-when sew stitch` have no numeric argument. Curried forms work:
      `(->> (seq :bd :sd) (fast-gap 2) (linger 1/2) (hurry 2) (zoom 0 1/2))` evaluates to a pattern.
- [ ] Rule 3: all 25 names in `BuiltinName` (+ `npm run gen:grammar`), `completions.js`, `hover.js`, `builtin_meta.edn`
      (+ `npm run gen:ai-docs`); both generators produce no diff after the commit; `tl3b-documented-examples-evaluate` passes.
- [ ] `docs/USAGE.md` reference rows with examples for all 25 functions (incl. the `zoom`/`focus`/`compress` comparison and
      the `hurry` → `rate` note); README rows; STRUDEL-PARITY TL3b rows (20 Time & structure, `binary`/`binaryN`,
      `sew` `stitch` `trunc`) ✅ and the Summary recomputed; ROADMAP TL3b → delivered.
- [ ] Playwright: `(compress 1/4 3/4 (seq :bd :sd))` → 2 onsets, `(hurry 2 (seq :bd :sd))` → 4 onsets,
      `(stitch "t f t t" (pure :bd) (pure :sd))` → 4 onsets.
- [ ] All existing TL1/TL2/TL3 tests pass unchanged; `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a `## Test protocol` section (CLAUDE.md Rule 5) covering at least `zoom` vs `focus`,
      `linger`, `hurry`, `sew`, `beat "0,7,10" 16` and `struct (binary 5)` by ear.

## What NOT to do

- Don't change any TL3 function's semantics (`compress*`, `slowcat-held*`, `within*`, `chunk`, `chunk-back`, `struct`,
  `mask`, `invert`, …); reuse its private helpers instead of copying them. Don't change public `cat`/`cat*` or `rate`.
- No `binary-l` / `binaryNL` (dropped), no `isobite`/`cycleStruct` (❓ in the parity doc), no Strudel stepwise API
  (`take`, `drop`, `extend`, …), no `restart`/`reset` as public names.
- No probability or random-choice functions (`degrade-by` variants, `sometimes-*`, `brand`, `randrun`, `whenmod`): **TL4**.
- No layering functions (`superimpose`, `echo`, `stut`, `off` variants, …): **TL5**.
- No sample slicing (`chop`, `striate`, `slice`, `splice`, …): **TL6**. No `speed` alias or other new control names: **TL7**.
