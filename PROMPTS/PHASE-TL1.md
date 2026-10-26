# Phase TL1 — Patterned Arguments & Pattern Arithmetic

> **Automation:** 🤖 auto — pure `packages/core` + `packages/lisp` work; every acceptance
> criterion is checkable with `cljs.test` (event lists) plus `npm run test:all`.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** nothing (first phase of the milestone)
**Unblocks:** TL2–TL7, SK3 (velocity → `amp` patterns)

## Goal

Make REPuLse "patterns all the way down", the way Tidal works: any numeric argument of a
pattern transformer can itself be a pattern, and patterns can be combined arithmetically
with an explicit choice of which side supplies the rhythm. This phase builds the
machinery (joins and structure-aware application) that every later Milestone 1 phase
relies on, and fixes rational-time precision while it is in that code.

```lisp
;; Before — arguments are plain numbers only
(fast 2 (seq :bd :sd))
(scale :minor :c3 (seq 1 3 5))

;; After — arguments may be patterns (or mini-notation strings)
(fast (seq 1 2) (seq :bd :sd))             ; 1× in the first half, 2× in the second
(fast "<1 2 4>" (seq :bd :sd))             ; one factor per cycle
(late "<0 1/8>" (seq :hh :hh :hh :hh))     ; nudge every other cycle
(euclidean "<3 5>" 8 :bd)                  ; alternate 3 and 5 hits per 8 steps

;; After — arithmetic on patterns, rhythm from the pattern (last argument)
;; (scale degrees are 1-based in REPuLse today; see TL5 for the 0-based question)
(->> (seq 1 3 5 8)
     (add "<0 2 4>")                       ; shift degrees per cycle
     (scale :minor :c3))

;; After — choose where the rhythm comes from (audible onsets shown)
(add :in  (seq 10 20) (seq 1 2 3))         ; rhythm of (seq 1 2 3)   → 11 12 23
(add :out (seq 10 20) (seq 1 2 3))         ; rhythm of (seq 10 20)   → 11 22
(add :mix (seq 10 20) (seq 1 2 3))         ; both rhythms intersect  → 11 12 22 23
```

## Background

### Pattern engine — `packages/core/src/repulse/core.cljs`

- Rationals are `[n d]` vectors of JS numbers, normalised by `rat` via `gcd`.
  `int->rat` wraps any number as `[x 1]`.
- `pattern` wraps a query fn (`{:query f ::pat true}`); `query` calls it with a span.
- Events: `{:value v :whole span :part span}`, optional `:source` for editor highlighting.
  Every current event has a `:whole` (no continuous/analog events yet — TL2 adds those).
- `combine f pat-a pat-b` — the only binary combinator. It pairs events whose `:part`s
  overlap and keeps `pat-b`'s `:whole`. This is close to Tidal's `appRight` (structure
  from the right), but it intersects parts instead of querying the left side over each
  right event's whole.
- `fast`, `slow`, `late`, `early`, `every`, `euclidean`, `sometimes-by`, `degrade-by`,
  `off` all take **scalar** first arguments.
- `late`/`early` turn float amounts into rationals with `(rat (int (* amount 1000)) 1000)`,
  so `0.3333` becomes `333/1000`: lossy.
- `fast`/`slow` call `int->rat` on whatever arrives.

### Lisp builtins — `packages/lisp/src/repulse/lisp/builtins/pattern.cljs`

- `fast`/`slow` coerce their factor with `u/->num` (`packages/lisp/src/repulse/lisp/util.cljs`),
  which turns a rational literal `1/3` (`[1 3]`) into the float `0.333…`. `core/fast` then
  wraps it as `[0.333… 1]`, and `rat`'s float `gcd` turns it into the exact binary
  fraction (denominator around 2^54). Products of such values exceed `Number.MAX_SAFE_INTEGER`
  quickly, so `(fast 1/3 …)` is imprecise today. **Add a regression test first.**
- Most builtins support a 1-arg curried form (`(fast 2)` → transformer) for `->>`.
- `~` parses mini-notation (`packages/lisp/src/repulse/lisp/mini.cljs`).

### Parameters — `packages/core/src/repulse/params.cljs`

`apply-param` already accepts a pattern as the parameter value and uses `combine`, so
`(amp (seq 0.9 0.5) pat)` works with structure from `pat`. This is exactly Tidal's
`#`/Strudel's `.set` semantics; TL1 should reuse the new `app-right` for it so there is
one implementation of "structure from the pattern".

### Tidal/Strudel reference semantics (port these, don't invent new ones)

| Tidal | Strudel | Structure (whole) comes from |
|---|---|---|
| `appLeft` / `\|+` | `.add.in` (default) | left / "this" pattern |
| `appRight` / `+\|` | `.add.out` | right / argument pattern |
| `appBoth` / `\|+\|` | `.add.mix` | intersection of both wholes |
| `squeezeJoin` | `.add.squeeze` | inner pattern squeezed into each outer event |
| `innerJoin` (used by `tParam`) | `patternify` | inner pattern |

Argument order in REPuLse is transformer-first, pattern-last (`(add amt pat)`), so the
**pattern argument is Strudel's "this"**. `:in` therefore means "structure from the
last argument".

## Implementation

### 0. Trigger only onsets — `app/src/repulse/audio.cljs`

`schedule-cycle!` queries each cycle and plays **every** returned event at its
`:part` start. An event whose whole crosses a cycle boundary is therefore split into
two fragments and triggered twice. Examples: `(slow 4 (seq :bd :sd))`, where `:bd`'s
whole is `[0,2)`, and `(late 1/2 (pure :bd))`. This was found by reading the code; confirm
it with a test before changing anything. TL1's `app-left`/`app-right` produce non-onset
fragments as a normal part of their output (see the `add :in` example), so this must
be fixed first:

- Add `onset?` to `core.cljs`: true when the event has a `:whole` and
  `(= (:start whole) (:start part))`.
- In `schedule-cycle!`, trigger only onsets; schedule at the whole's start and use the
  **whole's** duration as `dur`.
- Apply the same filter wherever else events are turned into sound or MIDI (offline
  render/export, `midi-export`, MIDI out, snippet preview). Check every `core/query`
  call site in `app/src/repulse/`.
- Visual consumers (active-event highlighting, timeline) may keep showing fragments.

### 1. Rational coercion — `core.cljs`

- Add `->rat`: accepts an integer, a `[n d]` vector, or a float. Floats become the best
  rational approximation with denominator ≤ 1 000 000 (continued fractions), so
  `0.25 → [1 4]`, `1.5 → [3 2]`, `0.3333333 → [1 3]`.
- Use `->rat` in `fast`, `slow`, `late`, `early` (drop the `* 1000` truncation) and in
  every place that currently calls `int->rat` on user input.
- In the Lisp builtins, stop routing time arguments through `u/->num`; pass the raw
  unwrapped value (vector or number) so `1/3` stays `[1 3]`.
- Add a dev-only guard in `rat`: if `|n|` or `|d|` exceeds `Number.MAX_SAFE_INTEGER`,
  throw `ex-info` with `{:type :rational-overflow}`. Gate it behind `goog.DEBUG`
  so release builds pay nothing.

### 2. Structure-aware application — `core.cljs`

Implement, following Tidal's definitions:

```clojure
(defn app-left  [f pat-a pat-b] …)  ; wholes from pat-a; query pat-b over each a-whole
(defn app-right [f pat-a pat-b] …)  ; wholes from pat-b; query pat-a over each b-whole
(defn app-both  [f pat-a pat-b] …)  ; whole = intersection of both wholes
```

- `:part` is always the intersection of both parts; drop pairs with no intersection.
- Keep `:source`: from the side that supplies structure (both sources in a vector for
  `app-both`, if the highlighter can take it; otherwise the left).
- Redefine `combine` as `app-right` (its documented intent). Existing tests in
  `core_test.cljs` and `params_test.cljs` must still pass. If any change, the old
  behaviour was relying on the part-intersection quirk; document it in the PR.

### 3. Joins — `core.cljs`

```clojure
(defn inner-join   [pat-of-pats] …)  ; structure from the inner patterns
(defn outer-join   [pat-of-pats] …)  ; structure from the outer pattern
(defn squeeze-join [pat-of-pats] …)  ; compress each inner cycle into the outer event's whole
```

Then a helper used by every patternified builtin:

```clojure
(defn patternify
  "Lift (f arg pat) so arg may be a scalar or a pattern.
   Scalars take the fast path (identical to today's behaviour and performance)."
  [f]
  (fn [arg pat]
    (if (pattern? arg)
      (inner-join (fmap #(f % pat) arg))
      (f arg pat))))
```

Plus `patternify2`/`patternify3` for multi-argument functions (`euclidean k n v rot`).

### 4. Patternify existing builtins — `core.cljs` + `builtins/pattern.cljs`

Patterned first arguments for: `fast`, `slow`, `late`, `early`, `every` (n),
`sometimes-by` (prob), `degrade-by` (prob), `euclidean` (k, n, rotation), `off` (amount),
`jux-by` (width). Keep the curried 1-arg forms working: `(fast "<1 2>")` returns a
transformer.

### 5. Mini-notation strings as pattern arguments — `builtins/pattern.cljs`

In every patternified argument position, a **string** is parsed with `mini/parse`, so
`(fast "<1 2>" pat)` ≡ `(fast (~ "<1 2>") pat)`. Pass the string's source offset
through so editor highlighting still works. Strings in non-pattern positions are unchanged.

### 6. Arithmetic builtins — new `packages/lisp/src/repulse/lisp/builtins/arith.cljs` (or extend `pattern.cljs`)

| Builtin | Meaning |
|---|---|
| `(add [mode] amt pat)` | value + amt |
| `(sub [mode] amt pat)` | value − amt |
| `(mul [mode] amt pat)` | value × amt |
| `(div [mode] amt pat)` | value ÷ amt |
| `(set [mode] ctrl pat)` | merge map values (`ctrl` wins), like Tidal `#` |

- `mode` is an optional leading keyword: `:in` (default, structure from `pat`),
  `:out` (structure from `amt`), `:mix` (both), `:squeeze`.
- `amt` may be a number, a pattern or a mini-notation string.
- Numeric ops on note keywords (`:c4`) convert via `repulse.theory/note->midi` and back
  so `(add 12 (seq :c3 :e3))` works; on map values they apply to `:note`. Non-numeric
  values pass through unchanged (rests `:_` stay rests).
- 1-arg-after-mode curried forms return transformers for `->>`.

### 7. Built-in registration (CLAUDE.md Rule 3)

For `add`, `sub`, `mul`, `div`, `set`: grammar `BuiltinName` → `npm run gen:grammar` →
`completions.js` → `builtin_meta.edn` (with ≥1 example each) → `npm run gen:ai-docs`.
Update existing `builtin_meta.edn` entries of the patternified functions to say their
arguments accept patterns, with one patterned example each.

### 8. Docs

- `docs/USAGE.md`: new "Patterned arguments" and "Pattern arithmetic" sections;
  note pattern support in each affected function's reference row.
- `docs/STRUDEL-PARITY.md`: mark the TL1 rows ✅.

## Files to change

| File | Change |
|---|---|
| `packages/core/src/repulse/core.cljs` | `->rat`, overflow guard, `app-left/right/both`, joins, `patternify*`, patternified combinators |
| `packages/core/src/repulse/core_test.cljs` | Tests for all of the above (see Definition of done) |
| `packages/core/src/repulse/params.cljs` | `apply-param` uses `app-right` |
| `app/src/repulse/audio.cljs` (+ export/MIDI call sites) | Trigger only onsets, use whole duration |
| `packages/lisp/src/repulse/lisp/builtins/pattern.cljs` | Raw rational pass-through, string → mini-notation args, patterned args |
| `packages/lisp/src/repulse/lisp/builtins/arith.cljs` (new) or `pattern.cljs` | `add sub mul div set` |
| `packages/lisp/src/repulse/lisp/eval.cljs` | Register new builtin namespace if a new file is used |
| `packages/lisp/src/repulse/lisp/eval_test.cljs` / `integration_test.cljs` | Lisp-level tests |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` + `parser.js` | New names (`npm run gen:grammar`) |
| `app/src/repulse/lisp-lang/completions.js` | New entries |
| `app/src/repulse/content/builtin_meta.edn` + `docs/ai/builtins.json` | New + updated entries (`npm run gen:ai-docs`) |
| `docs/USAGE.md`, `docs/STRUDEL-PARITY.md`, `ROADMAP.md` | Docs + delivered marker |

## Definition of done

- [ ] `(slow 4 (seq :bd :sd))` triggers `:bd` once per 4 cycles, not on cycles 1 and 3 too
      (offline-render or scheduler test); `(late 1/2 (pure :bd))` triggers once per cycle.
- [ ] Regression test: `(fast 1/3 (seq :a :b))` queried over cycles 0–3000 produces
      exact rationals with small denominators (no overflow, no float drift).
- [ ] `->rat` unit tests: `0.25 → [1 4]`, `1.5 → [3 2]`, `0.3333333 → [1 3]`, `[2 4] → [1 2]`.
- [ ] `app-left`, `app-right`, `app-both` match Tidal's documented event lists for
      `"10 20" |+ "1 2 3"`, `"10 20" +| "1 2 3"` and `"10 20" |+| "1 2 3"` (wholes, parts, values).
- [ ] `inner-join`, `outer-join`, `squeeze-join` each have a hand-computed test case.
- [ ] `(fast (seq 1 2) (seq :bd :sd))` over cycle 0 yields `:bd` at 0, then `:bd` at 1/2 and `:sd` at 3/4.
- [ ] `(fast "<1 2 4>" (seq :bd :sd))` yields 2, 4 and 8 events in cycles 0, 1 and 2.
- [ ] `(euclidean "<3 5>" 8 :bd)` yields 3 onsets in cycle 0 and 5 in cycle 1.
- [ ] `(add :in (seq 10 20) (seq 1 2 3))` → onsets (fragments whose part starts at the
      whole's start) with values `11 12 23` and the wholes of `(seq 1 2 3)`, plus one
      non-onset fragment `22`, exactly as Tidal's `"1 2 3" |+ "10 20"`;
      `:out` → onsets `11 22`; `:mix` → onsets `11 12 22 23`.
- [ ] `(add 12 (seq :c3 :e3))` yields notes one octave up; `(add 1 (seq :_ 2))` keeps the rest.
- [ ] Every scalar call that worked before TL1 returns identical events (existing core,
      lisp and app session tests pass unchanged).
- [ ] Curried forms work in `->>`: `(->> (seq 1 3 5) (add "<0 2>") (scale :minor :c3))`.
- [ ] Rule 3 artefacts committed together; `npm run gen:grammar` and `npm run gen:ai-docs`
      produce no diff afterwards.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] `docs/USAGE.md` documents patterned arguments and `add/sub/mul/div/set` with examples.

## What NOT to do

- No continuous signals (`sine`, `rand`, `perlin`, …) or events without a `:whole`:
  that is **TL2**.
- No new structure combinators (`struct`, `ply`, `chunk`, `swing`, …): **TL3**.
- No change to how randomness is seeded (`cycle-hash`): **TL4** replaces it.
- No new effect parameters or audio-engine changes: **TL7**.
- Don't switch rationals to BigInt in this phase. The overflow guard tells us whether
  that is ever needed; if it is, propose it as its own phase with benchmarks.
