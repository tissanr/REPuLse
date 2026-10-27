# REPuLse — Strudel / Tidal Parity Checklist

Tracks how much of the Strudel (and TidalCycles) pattern vocabulary REPuLse covers.
This is the measuring stick for **Milestone 1 — Tidal-Lisp core** (phases TL1–TL7 in
[ROADMAP.md](../ROADMAP.md)): every row is either done, scheduled into a phase, an
alias of something else, or explicitly rejected with a reason.

Source of the Strudel vocabulary: `packages/core/pattern.mjs`, `signal.mjs`,
`pick.mjs`, `euclid.mjs`, `controls.mjs` and `packages/tonal/*` in
[uzu/strudel](https://codeberg.org/uzu/strudel) (October 2026). Tidal-only functions
are listed in their own section.

---

## Naming rule

Strudel/Tidal names are converted to kebab-case Lisp names with **the same
semantics**: `someCyclesBy` → `some-cycles-by`, `echoWith` → `echo-with`,
`fastGap` → `fast-gap`. Tidal tutorials should translate one-to-one. Where REPuLse
already uses a different name (`seq`, `euclidean`, `amp`, `rate`), the existing name
stays and the Strudel name becomes an alias only if it adds clarity.

Method-chaining (`s("bd").fast(2)`) maps to Lisp application or threading:
`(fast 2 (s :bd))` / `(->> (s :bd) (fast 2))`.

## Status legend

| Mark | Meaning |
|---|---|
| ✅ | Done — REPuLse has it with matching semantics |
| 🟡 | Partial — exists, but arguments or semantics differ; completion is scheduled in the listed phase |
| 📋 | Planned in the listed phase |
| 🔁 | Alias of another row — no separate implementation work |
| ⛔ | Won't do (reason in Notes) |
| ❓ | Undecided |

---

## Summary

| Family | Phase | ✅ | 🟡 | 📋 | 🔁 | ⛔ | ❓ | Total |
|---|---|---|---|---|---|---|---|---|
| Core & pattern arithmetic | TL1 | 15 | 2 | 18 | 7 | 4 | 8 | 54 |
| Continuous signals | TL2 | 24 | 0 | 6 | 3 | 1 | 2 | 36 |
| Time & structure | TL3/TL3b | 44 | 0 | 0 | 9 | 2 | 6 | 61 |
| Randomness & conditionals | TL4 | 4 | 6 | 22 | 8 | 1 | 3 | 44 |
| Layering, pitch & harmony | TL5 | 4 | 3 | 12 | 5 | 0 | 2 | 26 |
| Sample manipulation | TL6 | 3 | 1 | 11 | 2 | 1 | 2 | 20 |
| Control parameters & effects | TL7 | 4 | 14 | 20 | 4 | 7 | 9 | 58 |
| Mini-notation | TL1/TL3/TL4 | 13 | 0 | 4 | 0 | 0 | 0 | 17 |
| Tidal-only functions | TL3–TL5 | 4 | 0 | 4 | 3 | 0 | 2 | 13 |
| Visuals, I/O & JS-specific | — | 4 | 2 | 1 | 1 | 13 | 3 | 24 |
| **Total** | | **119** | **28** | **98** | **42** | **29** | **37** | **353** |

Rows grouped in one line (e.g. `sine2 / cosine2 / …`) count once.

---

## Core & pattern arithmetic — TL1

The machinery the rest of Milestone 1 depends on: patterned arguments (every numeric
argument may itself be a pattern), structure-aware combination of two patterns, and
arithmetic on pattern values.

| Strudel | REPuLse | Status | Phase | Notes |
|---|---|---|---|---|
| `pure` | `pure` | ✅ | — | |
| `silence` | `silence` | 📋 | TL1 | `:_` already rests inside `seq`; a first-class empty pattern is still missing |
| `nothing` / `gap` | `gap` | 📋 | TL1 | Silence that occupies steps (stepwise) |
| `seq` / `sequence` / `fastcat` | `seq` | ✅ | — | |
| `cat` / `slowcat` | `cat` | ✅ | — | REPuLse `cat` is one-pattern-per-cycle, same as Strudel |
| `slowcatPrime` | — | ❓ | — | Slowcat that keeps each pattern's own cycle position; niche |
| `stack` | `stack` | ✅ | — | |
| `polyrhythm` / `pr` | `stack` | 🔁 | — | |
| `stackLeft` / `stackRight` / `stackCentre` / `stackBy` | — | ❓ | — | Step-aligned stacking from Strudel's experimental stepwise API |
| `stackRepeat` | — | ❓ | — | Stepwise |
| `arrange` | `arrange` | 🟡 | TL1 | REPuLse takes `[[pat cycles] …]`, Strudel `[cycles, pat]`; keep REPuLse order, document the difference |
| `seqPLoop` | — | ❓ | — | |
| `fmap` / `withValue` | `fmap` | ✅ | — | |
| `withValue` | `fmap` | 🔁 | — | |
| `fast` | `fast` | ✅ | TL1 | Factor may be a number, rational, pattern or mini-notation string: `(fast "<1 2>" p)` |
| `slow` / `sparsity` | `slow` | ✅ | TL1 | Patterned factor, exact rationals |
| `sparsity` | `slow` | 🔁 | — | |
| `set` (`#` in Tidal) | `set` | ✅ | TL1 | `(set [mode] ctrl pat)` — merge control maps, ctrl wins, structure from `pat` |
| `keep` | `keep` | 📋 | TL1 | |
| `keepif` | `keep-if` | 📋 | TL1 | |
| `add` | `add` | ✅ | TL1 | Numbers, Hz and note keywords (via MIDI numbers); maps apply to `:note` |
| `sub` | `sub` | ✅ | TL1 | |
| `mul` | `mul` | ✅ | TL1 | |
| `div` | `div` | ✅ | TL1 | Division by zero leaves the value unchanged |
| `mod` | `mod` (pattern-aware) | 📋 | TL1 | Lisp `mod` exists for numbers only |
| `pow` | `pow` | 📋 | TL1 | |
| `band` / `bor` / `bxor` / `blshift` / `brshift` | — | ❓ | — | Bitwise ops on patterns; low musical value |
| `lt` / `gt` / `lte` / `gte` | `lt` `gt` `lte` `gte` | 📋 | TL1 | Produce boolean patterns usable with `struct`/`mask` |
| `eq` / `ne` / `eqt` / `net` | `eq` `ne` | 📋 | TL1 | `eqt`/`net` are strict variants |
| `eqt`, `net` | `eq`, `ne` | 🔁 | — | |
| `and` / `or` (pattern) | `and` `or` | 📋 | TL1 | Lisp special forms exist; pattern versions needed |
| `round` | `round` | 📋 | TL1 | |
| `floor` | `floor` | 📋 | TL1 | |
| `ceil` | `ceil` | 📋 | TL1 | |
| `log2` | `log2` | 📋 | TL1 | |
| `ratio` | — | ❓ | — | Parses `"3:2"` strings to numbers |
| `func` | — | ⛔ | — | JS function wrapper; Lisp functions are already values |
| Structure modes `.in` / `.out` / `.mix` / `.squeeze` / `.reset` / `.restart` | leading mode keyword: `(add :out amt pat)` | 🟡 | TL1 | `:in` `:out` `:mix` `:squeeze` done (Tidal `\|+`, `+\|`, `\|+\|`, `\|\|+`); `.reset` / `.restart` not yet |
| `bind` / `innerBind` / `outerBind` / `squeezeBind` | `inner-join`, `outer-join`, `squeeze-join` | ✅ | TL1 | Core functions (`repulse.core`) used for patterned arguments; not exposed as Lisp built-ins |
| `stepBind` / `polyBind` | — | ❓ | — | Stepwise joins |
| `squeeze` | `squeeze` | 📋 | TL1 | |
| `reset` / `restart` | `reset`, `restart` | 📋 | TL1 | Retrigger a pattern on events of another |
| `apply` | `apply-pat` | 📋 | TL1 | Lisp `apply` already exists for functions; needs a distinct name |
| `register` | `defn` | ⛔ | — | Lisp `defn`/`defmacro` already define new pattern functions |
| `reify` / `isPattern` | — | ⛔ | — | Internal coercion; REPuLse coerces in the evaluator |
| `steps` / `pace` | `pace` | 📋 | TL1 | Strudel step-count metadata for stepwise functions |
| `early` | `early` | ✅ | TL1 | Exact rationals; floats → nearest rational (denominator ≤ 10⁶); patterned amount |
| `late` | `late` | ✅ | TL1 | Same as `early` |
| `hush` | `stop` | 🔁 | — | |
| `layer` | `layer` | 📋 | TL1 | Apply several functions and stack results |
| `compressSpan`, `focusSpan`, `zoomArc` | see TL3 | 🔁 | — | |
| `ref` | — | ❓ | — | Reference to a live value; overlaps with T1 tweens and N1 CC mapping |
| `cpm` | `bpm` | 🔁 | — | Strudel sets cycles/min per pattern; REPuLse tempo is global |
| `sequenceP` | — | ⛔ | — | Internal |

---

## Continuous signals — TL2

Signals are sampled at the **start** of the structure event's whole (`app-left`/`app-right` query the signal over the whole), so `(segment 4 saw)` gives `0 0.25 0.5 0.75`; this follows Strudel and differs from Tidal ≥ 1.9's midpoint rule. `rand` is Tidal's `timeToRand` (64-bit) exactly. **Name clashes, decided (2026-10-26):** UGen names inside `defsynth` live
in their own environment and are unaffected. At top level, `saw` and `square` become
signals (the voice shortcuts `(saw :c4)` / `(square :c4)` give way to `(synth :saw …)`),
`(noise)` stays a voice, and `range` dispatches on whether its last argument is a pattern.

| Strudel | REPuLse | Status | Phase | Notes |
|---|---|---|---|---|
| `signal` | `signal` | ✅ | TL2 | Build a signal from a function of time |
| `steady` | `steady` | ✅ | TL2 | Constant continuous value |
| `time` | `time` | ✅ | TL2 | Current cycle position as a signal |
| `sine` | `sine` | ✅ | TL2 | 0..1 |
| `cosine` | `cosine` | ✅ | TL2 | Tidal phase (`0.25 ~> sine`, 0 at t = 0); Strudel's is `early 1/4` (1 at t = 0) |
| `saw` | `saw` (signal) | ✅ | TL2 | Voice shortcut removed; use `(synth :saw …)`; calling `(saw :c4)` is an error with a hint |
| `isaw` | `isaw` | ✅ | TL2 |  |
| `tri` | `tri` (signal) | ✅ | TL2 | Tidal `fastAppend saw isaw`; top-level signal (the `defsynth` UGen `tri` is unaffected) |
| `itri` | `itri` | ✅ | TL2 |  |
| `square` | `square` (signal) | ✅ | TL2 | Voice shortcut removed; use `(synth :square :pw … )` |
| `isquare` | `isquare` | ✅ | TL2 |  |
| `sine2` / `cosine2` / `saw2` / `isaw2` / `tri2` / `itri2` / `square2` / `isquare2` | same | ✅ | TL2 | Bipolar −1..1 versions |
| `rand` | `rand` | ✅ | TL2 | Tidal `timeToRand` exactly (64-bit); sampled at span start like Strudel |
| `rand2` | `rand2` | ✅ | TL2 |  |
| `irand` | `irand` | ✅ | TL2 |  |
| `brand` | `brand` | 📋 | TL4 | Random booleans; moved from TL2 (TL2 spec) |
| `brandBy` | `brand-by` | 📋 | TL4 | moved from TL2 (TL2 spec) |
| `perlin` | `perlin` | ✅ | TL2 |  |
| `berlin` | `berlin` | 📋 | TL4 | moved from TL2 (TL2 spec) |
| `run` | `run` | ✅ | TL2 | `0 1 2 … n-1` — discrete, but defined with the signals |
| `binary` / `binaryN` | `binary`, `binary-n` | ✅ | TL3b | Number → boolean step pattern; Strudel arg order, default 16 bits |
| `binaryL` / `binaryNL` | — | ⛔ | — | Returns a list, not a pattern; `binary-n` covers the use case (TL3b decision) |
| `randL` | `rand-l` | 📋 | TL4 | moved from TL2 (TL2 spec) |
| `randrun` | `randrun` | 📋 | TL4 | Random permutation of `0..n-1`; moved from TL2 (TL2 spec) |
| `segment` / `seg` | `segment` | ✅ | TL2 | Sample a signal n times per cycle |
| `seg` | `segment` | 🔁 | — | |
| `range` | `range` | ✅ | TL2 | Dispatches on the last argument: pattern → Tidal `range`, otherwise the Lisp collection `range` |
| `rangex` | `rangex` | ✅ | TL2 | Exponential range |
| `range2` | `range2` | ✅ | TL2 |  |
| `toBipolar` | `to-bipolar` | ✅ | TL2 |  |
| `fromBipolar` | `from-bipolar` | ✅ | TL2 |  |
| `mousex` / `mousey` | — | ❓ | — | UI-driven signals; app layer, not `core` |
| `withSeed` / `seed` | `with-seed` | 📋 | TL4 | moved from TL2 (TL2 spec); until then vary randomness with `(early 7 rand)` |
| `useRNG` / `getRandsAtTime` | — | ❓ | — | Internal RNG plumbing |
| `envelope` signals (`lfo`, `env` modulators) | — | 🔁 | — | See TL7 modulators; T1 `tween` covers parameter ramps |
| `discretise` (Tidal) | `segment` | 🔁 | — | |

---

## Time & structure — TL3 / TL3b

| Strudel | REPuLse | Status | Phase | Notes |
|---|---|---|---|---|
| `struct` | `struct` | ✅ | TL3 | Boolean pattern as rhythm. A step is false for false/nil/0/~/f |
| `mask` | `mask` | ✅ | TL3 |  |
| `euclid` | `euclidean` | ✅ | TL3 | Tidal bjorklund (fixed in TL3); struct form when the 3rd arg is a pattern, value form otherwise; patterned k/n/r |
| `euclidRot` / `euclidrot` | `euclidean` (4th arg) | ✅ | TL3 | Tidal direction (rotate left); patterned rotation |
| `bjork` | `euclidean` | 🔁 | — | |
| `bjorklund` | — | ⛔ | — | Internal helper |
| `euclidLegato` | `euclid-legato` | ✅ | TL3 |  |
| `euclidLegatoRot` | `euclid-legato-rot` | ✅ | TL3 |  |
| `euclidish` / `eish` | `euclidish` | ✅ | TL3b | |
| `ply` | `ply` | ✅ | TL3 |  |
| `plyWith` | `ply-with` | ✅ | TL3 |  |
| `plyForEach` | `ply-for-each` | ✅ | TL3b | |
| `compress` | `compress` | ✅ | TL3b | |
| `compressSpan` | `compress` | 🔁 | — | |
| `fastGap` / `fastgap` | `fast-gap` | ✅ | TL3b | |
| `focus` | `focus` | ✅ | TL3b | Strudel focus (squeezes into [s, e), no gaps) |
| `focusSpan` | `focus` | 🔁 | — | |
| `zoom` | `zoom` | ✅ | TL3b | Tidal zoomArc |
| `zoomArc` | `zoom` | 🔁 | — | |
| `inside` | `inside` | ✅ | TL3 |  |
| `outside` | `outside` | ✅ | TL3 |  |
| `linger` | `linger` | ✅ | TL3b | Restarts every cycle |
| `bite` | `bite` | ✅ | TL3b | |
| `isobite` / `cycleStruct` | — | ❓ | — | Recent Strudel addition; semantics still moving |
| `swingBy` | `swing-by` | ✅ | TL3 | Also needed by MIDI capture (keep the played feel) |
| `swing` | `swing` | ✅ | TL3 |  |
| `invert` / `inv` | `invert` | ✅ | TL3 | Boolean patterns |
| `inv` | `invert` | 🔁 | — | |
| `brak` | `brak` | ✅ | TL3 |  |
| `press` | `press` | ✅ | TL3b | |
| `pressBy` | `press-by` | ✅ | TL3b | |
| `rev` | `rev` | ✅ | — | |
| `revv` | `revv` | ✅ | TL3b | Reverse across cycles, not within |
| `palindrome` | `palindrome` | ✅ | TL3 |  |
| `iter` | `iter` | ✅ | TL3 | Tidal cat cycle-holding |
| `iterBack` | `iter-back` | ✅ | TL3 | Tidal cat cycle-holding |
| `repeatCycles` | `repeat-cycles` | ✅ | TL3b | |
| `chunk` / `slowChunk` | `chunk` | ✅ | TL3 | Tidal cat cycle-holding; window via within |
| `slowChunk` | `chunk` | 🔁 | — | |
| `chunkBack` | `chunk-back` | ✅ | TL3 | Strudel order (0, n-1, …); cycle-holding |
| `fastChunk` | `fast-chunk` | ✅ | TL3 |  |
| `chunkInto` | `chunk-into` | ✅ | TL3b | |
| `chunkBackInto` | `chunk-back-into` | ✅ | TL3b | |
| `ribbon` / `rib` | `ribbon` | ✅ | TL3b | Loop a section of the timeline |
| `rib` | `ribbon` | 🔁 | — | |
| `hurry` | `hurry` | ✅ | TL3b | `fast` + multiplies `rate` (no `speed` alias) |
| `within` | `within` | ✅ | TL3 |  |
| `filter` | `filter-events` | ✅ | TL3b | Lisp `filter` already exists for sequences; needs a distinct name |
| `filterWhen` | `filter-when` | ✅ | TL3b | |
| `polymeter` | `polymeter` | ✅ | TL3 |  |
| `polymeterSteps` | `polymeter` | 🔁 | — | |
| `stepcat` / `timecat` / `timeCat` | `timecat` | ✅ | TL3 | Weighted concatenation; mini-notation `@` already covers the string form |
| `timecat`, `s_cat` | `timecat` | 🔁 | — | |
| `stepalt` | — | ❓ | — | Stepwise API |
| `take` / `drop` | — | ❓ | — | Stepwise API (`s_add` / `s_sub`); Lisp `take`/`drop` name clash |
| `extend` / `replicate` / `expand` / `contract` | — | ❓ | — | Stepwise API |
| `shrink` / `grow` / `shrinklist` / `growlist` | — | ❓ | — | Stepwise API |
| `tour` / `zip` | — | ❓ | — | Stepwise API |
| `bypass` | `bypass` | ✅ | TL3b | Silence a pattern by a boolean pattern |
| `beat` | `beat` | ✅ | TL3b | Place hits on numbered steps |
| `match` (internal `_match`) | — | ⛔ | — | Internal |

---

## Randomness & conditionals — TL4

**Semantics warning:** REPuLse's `sometimes-by` / `sometimes` / `often` / `rarely`
decide **per cycle** (they behave like Strudel `someCyclesBy`). Strudel's
`sometimesBy` decides **per event**. TL4 should switch them to per-event semantics
and add `some-cycles-by` for the current behaviour. The REPuLse hash
(`cycle-hash` in `core.cljs`) is also a linear `(c·48271 + 12345) mod 100`, so
consecutive cycles follow a visible pattern and only 100 probability levels exist;
TL2's seeded `rand` should replace it.

| Strudel | REPuLse | Status | Phase | Notes |
|---|---|---|---|---|
| `degradeBy` | `degrade-by` | ✅ | — | Per event, deterministic |
| `degrade` | `degrade` | ✅ | — | |
| `undegradeBy` | `undegrade-by` | 📋 | TL4 | |
| `undegrade` | `undegrade` | 📋 | TL4 | |
| `degradeByWith` | — | ❓ | — | Takes a custom random pattern |
| `sometimesBy` | `sometimes-by` | 🟡 | TL4 | Per-cycle today; Strudel is per-event |
| `sometimes` | `sometimes` | 🟡 | TL4 | Same |
| `often` | `often` | 🟡 | TL4 | Same |
| `rarely` | `rarely` | 🟡 | TL4 | Same |
| `almostNever` | `almost-never` | 📋 | TL4 | |
| `almostAlways` | `almost-always` | 📋 | TL4 | |
| `never` | `never` | 📋 | TL4 | |
| `always` | `always` | 📋 | TL4 | |
| `someCyclesBy` | `some-cycles-by` | 📋 | TL4 | = today's `sometimes-by` behaviour |
| `someCycles` | `some-cycles` | 📋 | TL4 | |
| `choose` | `choose` | 🟡 | TL4 | REPuLse `choose` returns one value per cycle; Strudel `choose` is a continuous signal you `segment`. Keep REPuLse behaviour under `choose-cycles`, align `choose` |
| `chooseWith` / `chooseInWith` | `choose-with` | 📋 | TL4 | |
| `chooseIn` | `choose-in` | 📋 | TL4 | |
| `chooseCycles` / `randcat` | `randcat` | 📋 | TL4 | Random pattern per cycle |
| `randcat` | `randcat` | 🔁 | — | |
| `wchoose` | `wchoose` | 🟡 | TL4 | Same per-cycle vs signal difference as `choose` |
| `wchooseCycles` / `wrandcat` | `wrandcat` | 📋 | TL4 | |
| `wrandcat` | `wrandcat` | 🔁 | — | |
| `shuffle` | `shuffle` | 📋 | TL4 | Lisp-level name clash check needed |
| `scramble` | `scramble` | 📋 | TL4 | |
| `when` | `when-pat` | 📋 | TL4 | Lisp `when` would collide if added as a special form |
| `firstOf` / `every` | `every` | ✅ | — | |
| `firstOf` | `every` | 🔁 | — | |
| `lastOf` | `last-of` | ✅ | TL3 | Exact Tidal `_lastOf` (moved from TL4); delivered in TL3 |
| `applyN` | `apply-n` | 📋 | TL4 | |
| `pick` | `pick` | 📋 | TL4 | Select patterns by index/name pattern |
| `pickmod` | `pick-mod` | 📋 | TL4 | |
| `pickF` / `pickmodF` | `pick-f` | 📋 | TL4 | Pick a function |
| `pickmodF` | `pick-f` | 🔁 | — | Modulo variant as option |
| `pickOut` / `pickmodOut` | `pick-out` | 📋 | TL4 | |
| `pickRestart` / `pickReset` (+ `mod` variants) | `pick-restart`, `pick-reset` | 📋 | TL4 | |
| `inhabit` / `pickSqueeze` | `inhabit` | 📋 | TL4 | |
| `pickSqueeze`, `pickmodSqueeze` | `inhabit` | 🔁 | — | |
| `inhabitmod` | `inhabit` | 🔁 | — | Option |
| `whenKey` / `keyDown` | — | ⛔ | — | Computer-keyboard triggers; REPuLse's editor owns the keyboard |
| `cyclesPer` / `per` / `perx` | — | ❓ | — | Recent Strudel addition |
| `open` / `close` | — | ❓ | — | Recent Strudel addition |
| `alt` (REPuLse) | `alt` | 🔁 | — | REPuLse-only; same as `cat` |
| Mini `\|` random choice | see Mini-notation | 🔁 | — | |

---

## Layering, pitch & harmony — TL5

**Decided (2026-10-26):** REPuLse `scale` is currently 1-indexed and returns Hz. TL5
switches it to **0-indexed degrees returning notes**, as in Tidal/Strudel, so tutorials
translate directly. This is a deliberate breaking change.

| Strudel | REPuLse | Status | Phase | Notes |
|---|---|---|---|---|
| `superimpose` | `superimpose` | 📋 | TL5 | |
| `off` | `off` | ✅ | — | |
| `echo` | `echo` | 📋 | TL5 | |
| `stut` | `stut` | 🔁 | — | Strudel `stut` = `echo` with Tidal argument order |
| `echoWith` / `stutWith` | `echo-with` | 📋 | TL5 | |
| `stutWith` | `echo-with` | 🔁 | — | |
| `jux` | `jux` | ✅ | — | |
| `juxBy` | `jux-by` | ✅ | — | |
| `juxFlip` / `flux` | `jux-flip` | 📋 | TL5 | |
| `juxFlipBy` / `fluxBy` | `jux-flip-by` | 📋 | TL5 | |
| `arp` | `arp` | 📋 | TL5 | |
| `arpWith` | `arp-with` | 📋 | TL5 | |
| `voicing` | `voicing` | 📋 | TL5 | |
| `voicings` | `voicing` | 🔁 | — | Older API |
| `rootNotes` | `root-notes` | 📋 | TL5 | |
| `chord` (control, `"<C Am F G>"`) | `chord` | 🟡 | TL5 | REPuLse `(chord :major7 :c4)` builds a stacked pattern; Strudel uses chord symbols + `voicing`. Add chord-symbol parsing (`"Cm7"`) |
| `dictionary` / `anchor` / `mode` / `offset` / `octaves` | voicing options | 📋 | TL5 | Voicing controls |
| `setVoicingRange` / `addVoicings` / `registerVoicings` / `voicingAlias` | — | ❓ | — | Custom voicing dictionaries |
| `scale` | `scale` | 🟡 | TL5 | **Decided:** switch to 0-based degrees returning notes (breaking); patterned scale name |
| `scaleTranspose` / `scaleTrans` / `strans` | `scale-transpose` | 📋 | TL5 | |
| `scaleTrans`, `strans` | `scale-transpose` | 🔁 | — | |
| `transpose` / `trans` | `transpose` | 🟡 | TL5 | Semitones work; interval strings (`"3M"`) and patterned amount missing |
| `trans` | `transpose` | 🔁 | — | |
| `xfade` | `xfade` | 📋 | TL5 | Crossfade two patterns by gain |
| `morph` | — | ❓ | — | Strudel's new morph; overlaps the FUTURE-FEATURES `morph` idea |
| Note names (`c4`, `eb3`) | `:c4`, `:eb3` | ✅ | — | |

---

## Sample manipulation — TL6

| Strudel | REPuLse | Status | Phase | Notes |
|---|---|---|---|---|
| `chop` | `chop` | 📋 | TL6 | |
| `striate` | `striate` | 📋 | TL6 | |
| `slice` | `slice` | 📋 | TL6 | |
| `splice` | `splice` | 📋 | TL6 | |
| `loopAt` | `loop-at` | 📋 | TL6 | |
| `loopAtCps` | `loop-at` | ⛔ | — | REPuLse has one global tempo |
| `fit` | `fit` | 📋 | TL6 | |
| `cut` | `cut` | 📋 | TL6 | Cut groups (choke) — audio-engine work |
| `clip` / `legato` | `legato` | 📋 | TL6 | Note length relative to event |
| `clip` | `legato` | 🔁 | — | |
| `speed` | `rate` | 🟡 | TL6 | `rate` exists; negative speed (reverse playback) unverified. Add `speed` alias |
| `unit` | `unit` | 📋 | TL6 | `"c"` = speed in cycles |
| `accelerate` | `accelerate` | 📋 | TL6 | |
| `begin` | `begin` | ✅ | — | |
| `end` | `end` | ✅ | — | |
| `loop` | `loop-sample` | ✅ | — | |
| `loopBegin` / `loopEnd` | `loop-begin`, `loop-end` | 📋 | TL6 | |
| `stretch` | — | ❓ | — | Time-stretch; needs a granular/phase-vocoder engine |
| `scrub` | — | ❓ | — | |
| `n` (sample index) | `(sound :bd 2)`, `"bd:2"` | 🔁 | — | Exists via `sound` and mini-notation `:` |

---

## Control parameters & effects — TL7

REPuLse's effects (`fx`) are **per track**, not **per event**: `(fx :filter 800)`
filters a whole track. Strudel controls (`lpf`, `room`, `delay`, …) are per event and
patternable. TL7 adds per-event controls on the WASM voice path; the track-level
`fx` chain stays for mixing.

| Strudel | REPuLse | Status | Phase | Notes |
|---|---|---|---|---|
| `s` / `sound` | keywords, `sound` | ✅ | — | `:bd` or `(sound :bd 2)` |
| `bank` | `bank` | 🟡 | TL7 | REPuLse `bank` is a global session prefix; Strudel `bank` is a patternable per-event control |
| `note` | note keywords, `{:note …}` | 🟡 | TL7 | No `note` function that sets the control on a pattern |
| `freq` | Hz numbers | 🔁 | — | |
| `gain` | `amp` | 🟡 | TL7 | Add `gain` with Strudel's curve; `amp` stays linear |
| `amp` | `amp` | ✅ | — | |
| `velocity` | `velocity` | 📋 | TL7 | Also written by MIDI capture |
| `postgain` | `postgain` | 📋 | TL7 | |
| `attack` / `decay` / `release` | same | ✅ | — | |
| `sustain` / `hold` | `sustain`, `hold` | 📋 | TL7 | |
| `adsr` / `ad` / `ar` / `ds` | `adsr` | 📋 | TL7 | Shorthands |
| `pan` | `pan` | ✅ | — | |
| `panspan` / `pansplay` / `panwidth` / `panorient` | — | ⛔ | — | Multichannel SuperDirt panning |
| `lpf` / `cutoff` / `ctf` / `lp` | `lpf` | 📋 | TL7 | `lpf` exists today only as a `defsynth` UGen |
| `cutoff`, `ctf`, `lp` | `lpf` | 🔁 | — | |
| `resonance` / `lpq` | `resonance` | 📋 | TL7 | |
| `hpf` / `hp` / `hcutoff`, `hresonance` / `hpq` | `hpf`, `hpq` | 📋 | TL7 | |
| `bpf` / `bandf` / `bp`, `bandq` / `bpq` | `bpf`, `bpq` | 📋 | TL7 | |
| `djf` | `djf` | 📋 | TL7 | DJ filter (one knob LP↔HP) |
| `ftype` / `fanchor` | — | ❓ | — | Filter model selection |
| Filter envelopes `lpenv` `lpattack` `lpdecay` `lpsustain` `lprelease` (and `hp*`, `bp*`) | `lp-env` … | 📋 | TL7 | |
| Filter LFOs `lprate` `lpdepth` `lpshape` … | — | ❓ | — | Overlaps generic `lfo` modulator |
| `vowel` | `vowel` | 📋 | TL7 | Formant filter |
| `room` | `room` | 🟡 | TL7 | Track-level `(fx :reverb …)` exists; per-event send planned |
| `roomsize` / `size` / `sz`, `roomlp`, `roomdim`, `roomfade` | `room-size` … | 📋 | TL7 | |
| `ir` / `irspeed` / `irbegin` | — | ❓ | — | Custom impulse responses; `(fx :cab :ir …)` covers cabinets |
| `delay` | `delay` | 🟡 | TL7 | Track-level `(fx :delay …)` exists; per-event send planned |
| `delaytime` / `delayfeedback` / `delaysync` | `delay-time`, `delay-feedback` | 📋 | TL7 | |
| `crush` | `crush` | 🟡 | TL7 | Track-level `bitcrush` exists |
| `coarse` | `coarse` | 📋 | TL7 | Sample-rate reduction |
| `shape` | `shape` | 📋 | TL7 | |
| `distort` / `distortvol` / `distorttype` | `distort` | 🟡 | TL7 | Track-level DST effects exist |
| `drive`, `triode`, `krush`, `squiz`, `fold`/`sinefold`, `soft`/`hard`/`cubic`/`diode`/`asym` | — | ❓ | — | Waveshaper variants; DST1–6 covers most at track level |
| `chebyshev` | `chebyshev` | 🟡 | TL7 | Track-level only |
| `compressor` (+ ratio/knee/attack/release) | `(fx :compressor …)` | 🟡 | TL7 | Track-level only |
| `phaser` / `phaserrate` / `phaserdepth` / … | `phaser` | 🟡 | TL7 | Track-level `(fx :phaser …)` plugin exists (Phase A); per-event planned |
| `tremolo` (+ sync/depth/shape) | `tremolo` | 🟡 | TL7 | Track-level `(fx :tremolo …)` plugin exists (Phase A); per-event planned |
| `chorus` / `leslie` | `(fx :chorus …)` | 🟡 | TL7 | Track-level chorus exists (Phase A); `leslie` undecided |
| `vib` / `vibmod` | `vib` | 📋 | TL7 | |
| Pitch envelope `penv` `pattack` `pdecay` `psustain` `prelease` `pcurve` `panchor` | `penv` … | 📋 | TL7 | |
| `detune` / `unison` / `spread` (supersaw) | — | ❓ | — | Synth-design scope; see SYN phases |
| `fm` / `fmh` / `fmenv` / `fmattack` … (8 operators, matrix) | `(synth :fm :index …)` | 🟡 | TL7 | 2-operator FM exists; per-event FM controls missing |
| Wavetable `wt*` / `warp*` | — | ⛔ | — | Synth-design scope, out of Milestone 1 |
| ZZFX `z*` / `zzfx` | — | ⛔ | — | Strudel-specific chiptune engine |
| Spectral `freeze` `xsdelay` `tsdelay` `real` `imag` `enhance` `comb` `smear` `scram` `binshift` `hbrick` `lbrick` `waveloss` `ring*` `fshift*` `octer*` | — | ⛔ | — | SuperDirt-only effects |
| `orbit` / `o`, `bus`, `busgain` | `track` | 🔁 | — | REPuLse routes by track (Phase L) and `bus` in `defsynth` (Phase P) |
| `duckorbit` / `duckdepth` / `duckattack` | `duck` | ❓ | — | Sidechain ducking — musically valuable, decide in TL7 |
| `nudge` | `nudge` | 📋 | TL7 | Per-event timing offset (also used by MIDI capture) |
| `cps` | `bpm` | 🔁 | — | |
| `channel` / `channels` | — | ❓ | — | Multi-output; Milestone 3 |
| `lfo` / `env` / `bmod` modulators | — | ❓ | — | Generic modulation; T1 `tween` covers ramps |
| `midichan` / `midiport` / `midicmd` | `midi-out` | 🟡 | M3 | Channel works; port/cmd selection missing |
| `ccn` / `ccv` / `nrpnn` / `progNum` / `sysex` / `midibend` / `miditouch` | — | 📋 | M3 | MIDI output controls |
| `oschost` / `oscport` | — | 📋 | M3 | OSC output via the companion bridge |
| `label` / `color` / `activeLabel` | — | ⛔ | — | Visual metadata; REPuLse highlights by source position |
| `byteBeatExpression` / `byteBeatStartTime` | — | ⛔ | — | Strudel-specific |
| `analyze` / `fft` | — | ⛔ | — | REPuLse visual plugins tap the master analyser |
| `degree` / `mtranspose` / `ctranspose` / `harmonic` / `stepsPerOctave` / `octaveR` / `octave` | — | ❓ | — | SuperCollider-style pitch model; overlaps TL5 |

---

## Mini-notation

REPuLse's `~` parser (`packages/lisp/src/repulse/lisp/mini.cljs`) supports
`[ ] < > * ? : @` plus `~`/`_` rests.

| Strudel mini | REPuLse | Status | Phase | Notes |
|---|---|---|---|---|
| space sequence | ✅ | ✅ | — | |
| `[ ]` subdivision | ✅ | ✅ | — | |
| `< >` alternation | ✅ | ✅ | — | |
| `*n` | ✅ | ✅ | — | |
| `?` | ✅ | ✅ | — | |
| `?0.3` probability | — | 📋 | TL4 | Only bare `?` (50%) is parsed today |
| `:n` | ✅ | ✅ | — | |
| `@n` | ✅ | ✅ | — | |
| `~` rest | ✅ | ✅ | — | |
| `,` stack inside brackets | — | ✅ | TL1 | `"[c3,e3,g3]"` chords; delivered in TL3 |
| `/n` slow | — | 📋 | TL1 | |
| `!` / `!n` replicate | — | ✅ | TL3 | delivered in TL3 |
| `_` elongation | `@` weights | ⛔ | — | **Decided:** `_` stays a rest in REPuLse (consistent with `:_`); use `@` for elongation. Deliberate difference |
| `{ }` polymeter, `{ }%n` | — | ✅ | TL3 | delivered in TL3 |
| `(k,n,r)` euclid | — | ✅ | TL3 |  |
| `.` grouping | — | ✅ | TL3 |  |
| `\|` random choice | — | 📋 | TL4 | |

---

## Tidal-only functions

Not in Strudel, but common in Tidal tutorials.

| Tidal | REPuLse | Status | Phase | Notes |
|---|---|---|---|---|
| `whenmod` | `whenmod` | 📋 | TL4 | |
| `rot` | `rot` | ✅ | TL3 | Rotate values, keep structure; rests keep their place |
| `stutter` | `stutter` | 📋 | TL5 | |
| `sew` | `sew` | ✅ | TL3b | |
| `stitch` | `stitch` | ✅ | TL3b | |
| `fix` | `fix` | 📋 | TL4 | Apply a function to events matching control values |
| `trunc` | `trunc` | ✅ | TL3b | |
| `rolled` / `rolledBy` | `rolled` | 📋 | TL5 | Strum chords |
| `toScale` | `scale` | 🔁 | — | |
| `discretise` | `segment` | 🔁 | — | |
| `fastspread` / `spread` | `spread` | ❓ | — | |
| `nTake` | — | ❓ | — | |
| `euclidOff` | `euclidean` | 🔁 | — | Rotation argument |

---

## Visuals, I/O & JS-specific

| Strudel | REPuLse | Status | Phase | Notes |
|---|---|---|---|---|
| `$:` labelled blocks | `track` | ✅ | — | |
| `_$:` muted block | `mute!` | ✅ | — | |
| `hush` | `stop` | ✅ | — | |
| `setcps` / `setcpm` | `bpm` | 🟡 | — | Tempo in BPM, not cycles |
| `samples()` | `samples!` | ✅ | — | Strudel JSON manifests supported |
| `soundAlias` | — | ❓ | — | |
| `all` / `each` | — | ❓ | — | Apply a function to every track |
| `pianoroll` / `punchcard` / `spiral` / `scope` / `spectrum` / `pitchwheel` | visual plugins | 🟡 | — | Oscilloscope and spectrum exist; piano roll / punchcard are in FUTURE-FEATURES |
| `color` / `hsl` / `hsla` / `markcss` | — | ⛔ | — | Visual styling of highlights |
| Hydra (`H`, `initHydra`) | — | ⛔ | — | p5.js visuals exist (Phase B) instead |
| Csound | — | ⛔ | — | Out of scope |
| `speak` (TTS) | — | ⛔ | — | |
| `worklet` | — | ⛔ | — | Raw AudioWorklet code; `defsynth` covers custom sound |
| `partials` / `phases` / `parray` | — | ⛔ | — | Additive-synthesis controls; synth-design scope |
| `midi` output | `midi-out` | 🔁 | — | |
| `midin` (MIDI input as patterns) | — | 📋 | M3 | With N1 CC mapping |
| `osc` | — | ⛔ | — | Replaced by the M3 companion bridge |
| `mqtt` | — | ⛔ | — | |
| `serial` | — | ⛔ | — | |
| `gamepad` | — | ⛔ | — | |
| `mousex` / `mousey` | — | ❓ | — | |
| `device motion` / `acceleration` | — | ⛔ | — | Mobile sensors |
| `evaluate` / `initStrudel` | — | ⛔ | — | Embedding API; REPuLse has `<repulse-editor>` (O1) |
| Tidal syntax mode | — | ⛔ | — | REPuLse's language is the Lisp |
