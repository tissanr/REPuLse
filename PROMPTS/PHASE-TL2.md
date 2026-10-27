# Phase TL2 — Continuous Signals

> **Spec status:** full (expanded 2026-10-26)
>
> **Automation:** 🤖 auto — signals are pure functions of time; sampling them at known
> points gives exact expected values in `cljs.test`.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL1 (joins, `app-left`, onset-only triggering)

## Goal

Add Tidal's continuous patterns ("signals"): values that exist at every point in time
instead of as discrete events. A signal has no rhythm of its own. Combined with TL1's
structure rules, a rhythmic pattern samples it, which gives smooth movement (filter
sweeps, panning, swells) and deterministic random variation. This phase also adds
Tidal's deterministic time → random function (`time->rand`). TL4 and KIT1 build on it.

```lisp
;; Before — movement needs hand-written sequences or a one-shot tween
(pan (seq -1 -0.5 0 0.5 1) (fast 4 (seq :hh)))

;; After
(pan sine2 (fast 8 (seq :hh)))                             ; smooth L↔R sweep every cycle
(->> (fast 16 (seq :hh)) (amp (range 0.3 0.9 (slow 4 saw))))   ; 4-cycle swell
(->> (segment 8 (range 1 8 rand)) (scale :minor :c4))      ; 8 random degrees per cycle
(pan (range -0.5 0.5 perlin) (fast 4 (seq :hh)))           ; smooth random drift
(segment 4 saw)                                            ; 0 0.25 0.5 0.75 as events

;; Breaking change: saw/square are signals now, not voice shortcuts
(saw :c4)                    ; → eval error: "saw is a signal … use (synth :saw …)"
(synth :saw (seq :c4 :e4))   ; the voice, as before
```

## Decisions (resolving the outline's open questions)

| Question | Decision |
|---|---|
| Bare symbols vs calls | **Values.** `sine`, `saw`, `rand`, … evaluate to pattern values. Calling one, as in `(sine)` or `(saw :c4)`, is an eval error with a hint. Functions that take arguments stay functions: `(irand 8)`, `(segment 8 p)`, `(range lo hi p)`, `(signal f)`, `(steady v)`, `(run n)`. |
| Sampling point | **Start of the query span** (Strudel's `signal`), not Tidal ≥1.9's midpoint. `app-left`/`app-right` query the signal over the structure event's **whole**, so the sampled value is the signal at the event's onset. That gives the outline's exact grid values: `(segment 4 saw)` → `0 0.25 0.5 0.75`. With Tidal's midpoint rule the same expression gives `1/8 3/8 5/8 7/8`. Every signal, `rand` included, samples at the start. Side effect, documented in USAGE: `rand` at time 0 (and every multiple of 300 cycles) is exactly `0`. |
| Does `rand` match Tidal numerically? | **Yes, at the function level.** `time->rand` is Tidal's `timeToRand` bit for bit, using 64-bit integer semantics (see Implementation step 2). Strudel's JS port uses 32-bit shifts and gives different numbers. Because sampling is at the span start, `(segment 4 rand)` gives `timeToRand` at `0, 1/4, 1/2, 3/4`, where Tidal samples `1/8, 3/8, …`. |
| Waveform definitions | **Tidal's** for every shape: `saw = t mod 1`, `isaw = 1 − saw`, `tri = fastAppend saw isaw` (0→1→0), `square` = 0 for the first half of the cycle and 1 for the second, `cosine = 0.25 ~> sine` (so `cosine` is 0 at t = 0), `perlin = perlinWith`. Strudel's `cosine` is `sine` shifted *early* by 1/4 (1 at t = 0); we follow Tidal and record the difference in the parity doc. |
| `tween` (T1) vs signals | **Keep both.** `tween` is a one-shot ramp from the moment of evaluation, held at its end value. Signals are cyclic, deterministic functions of cycle position, the same every time the cycle comes round. USAGE gets a "Signals vs tween" note. |
| Bipolar variants | **All that the parity doc lists:** `sine2 cosine2 saw2 isaw2 tri2 itri2 square2 isquare2`, plus `rand2`, `range2`, `to-bipolar`, `from-bipolar`. Each is a one-line `fmap`. |
| `sample-and-hold` | **Out of scope.** It is not in `docs/STRUDEL-PARITY.md` (neither Strudel nor Tidal has it under that name); `segment` is the discretiser. |
| `seed` / `with-seed` | **Deferred to TL4** (parity row moved). Tidal has no seed parameter for `rand`; until TL4, vary randomness with a time offset: `(early 7 rand)`. |
| Other parity rows assigned to TL2 | **In:** `signal steady time run` (cheap and defined with the signals). **Moved to TL4:** `brand`, `brand-by`, `berlin`, `rand-l`, `randrun`, `with-seed` (randomness family, next to the RNG combinators). **Moved to TL3:** `binary`/`binary-n`, `binary-l` (boolean step patterns are only useful with `struct`/`mask`, which are TL3). |
| Name clashes (**decided 2026-10-26**, kept as written) | UGen names inside `defsynth` (`sin saw square tri noise lpf …`) live in their own environment (`ugen-env` in `app/src/repulse/synth.cljs`) and are unaffected. At top level, `saw` and `square` become Tidal **signals**. Their current role as voice shortcuts (`(saw :c4)`, `(square :c4 :pw 0.3)` in `packages/lisp/src/repulse/lisp/builtins/synth.cljs`) is removed; `(synth :saw pat)` covers it. Calling a signal like a function gives a helpful eval error suggesting `(synth :saw …)`. Update demos, tutorial, completions and `builtin_meta.edn`. `tri`, `sine`, `cosine` and the rest are new top-level signal names (Tidal uses `sine`, not `sin`). `(noise)` stays as the noise voice; Tidal/Strudel have no `noise` signal. `range` dispatches on its last argument: a pattern or signal means Tidal's `range lo hi pat`; otherwise it stays the Lisp collection `range`. |
| Patterned `range` bounds | `lo`/`hi` may be numbers or patterns (strings are mini-notation, as in TL1). Scalars take an `fmap` fast path. Patterned bounds use `app-left` with **structure from the last argument** (TL1's `:in` convention), so an analog input stays analog. |
| Event representation | Analog event = `{:value v :whole nil :part span}`: the `:whole` key is present with value `nil`. A signal query returns **one** event whose part is the whole query span (Tidal behaviour, no splitting at cycle boundaries). An empty or inverted span (`start ≥ end`) returns `[]`. |
| Time → number | Time stays rational. A signal function receives the rational `[n d]`. Waveforms use the exact cycle position `(mod n d) / d` (floored mod, so `-1/4` → `3/4`) converted to a float once. `time->rand` works on the rational directly. Lisp-level `(signal f)` passes `f` the float `n/d`. |

## Background

### Pattern engine — `packages/core/src/repulse/core.cljs`

- Rationals are `[n d]` vectors of JS numbers (`rat`, `rat+`, `rat-`, `rat*`, `rat-div`,
  `rat->float`, `->rat`). The `goog.DEBUG` overflow guard in `rat` throws
  `{:type :rational-overflow}`.
- `span`, `span-intersect` (returns `nil` unless `start < end`), `cycle-span`,
  `split-cycles`, `per-cycle`.
- `event [value whole part]` builds `{:value :whole :part}`. `pattern` wraps a query fn
  as `{:query f ::pat true}`; `pattern?` checks the tag; `query` runs it.
- **TL1 machinery:**
  - `onset?` is true only when the event *has* a `:whole` and the whole's start equals the part's start.
    Analog events (nil whole) are therefore never onsets. `query-onsets` filters with it.
  - `whole-or-part` (private) already falls back to `:part`.
  - `app-left [f pat-a pat-b]`: for each event `ea` of `pat-a`, query `pat-b` over
    `(whole-or-part ea)`, keep `ea`'s whole, and set part = intersection of both parts.
    **With a signal as `pat-b`**, the signal is queried over `ea`'s whole, so it returns one
    event sampled at the **whole's start** with part = that whole. The result is `ea`'s whole
    and `ea`'s part, carrying the onset value. Fragments of the same whole get the same value.
  - `app-right` mirrors this (structure from `pat-b`). `params/apply-param` uses
    `app-right` with structure from the note pattern, so `(pan sine pat)` samples `sine`
    at the start of each `pat` event's whole.
  - `app-both` already handles a nil whole on one side (`(or (:whole ea) (:whole eb))`).
  - `inner-join`, `outer-join` and `squeeze-join` use `whole-or-part`, so a nil whole does not crash them.
  - `patternify` / `patternify2`: scalar fast path, patterned argument → `inner-join`.
- **Functions that crash on a nil `:whole` today** because they `(update ev :whole f)` with
  `f` reading `(:start %)`: `fast*` (so also `fast`, `slow`, `slow*`), `rev`, `late*`
  (`late`, `early`, `off`), and `arrange*`. `degrade-by*`'s `event-hash` destructures
  `(:start (:whole event))`; with `nil` it hashes as 0 and does not crash. TL4 replaces
  that hash, so leave it.
- `cycle-hash` (linear `(c·48271+12345) mod 100`) stays untouched; TL4 replaces it using
  this phase's `time->rand`.

### Core specs — `packages/core/src/repulse/core/specs.cljs`

`event?` requires `(span? (:whole x))`, which rejects analog events. `span?` requires
`start < end`.

### Lisp — `packages/lisp/src/repulse/lisp/`

- `eval.cljs` `make-env` merges the builtin maps in order: `b-pattern`, `b-arith`,
  `b-math`, `b-music`, `b-params`, `b-collection`, `b-types`, `b-synth`,
  `b-arrangement`. Later maps win. Symbols resolve to env values, so a pattern stored
  under `"sine"` is a value. Undefined symbols get a Levenshtein hint (`typo-hint`), so
  `sin` at top level will suggest `sine` automatically.
- Calling a non-function: the general call path (`:else` branch at the end of
  `eval-form`, `"is not a function"` with `:from/:to`) and the `->>` / `->` branches
  (four more `"is not a function"` throws). Patterns are maps (`IFn` but not `Fn`),
  so `fn?` is false and they reach these branches. `eval.cljs` does not require
  `repulse.core` today.
- `builtins/synth.cljs` `make-builtins` defines `"saw"` → `{:note n :synth :saw}` and
  `"square"` → `{:note n :synth :square :pw pw}` (the voice shortcuts to remove), plus
  `"noise"`, `"fm"`, `"synth"`, `"sound"`, `"chebyshev"`, `"fold"`, `"bitcrush"`.
  `(synth :square :pw 0.3 pat)` already passes `:pw` through its option map.
- `builtins/collection.cljs` `"range"`: 1/2/3-arity over `u/unwrap`ped numbers
  (Clojure `range`). Its 3-arity `(range a b step)` is the one that must dispatch.
- `util.cljs`: `u/unwrap`, `u/pat-arg` (strings → mini-notation, others raw), `u/->num`.
- Tests live next to the sources: `packages/lisp/src/repulse/lisp/integration_test.cljs`
  (helpers `make-test-env`, `eval-pattern`, `eval-result`, `cycle-evs`, `eval-pat`;
  test `saw-produces-synth-map` asserts the old `(saw :c4)` behaviour and must be
  replaced), `eval_test.cljs` (`(vec (range 5))` test must keep passing).
- Test runner: `packages/core/src/repulse/test_runner.cljs` (lists namespaces; a new test
  namespace must be added to both the `:require` and `run-tests`).

### App — `app/src/repulse/`

- `audio.cljs` `schedule-cycle!` already calls `core/query-onsets` per track per cycle.
  Analog events are therefore already skipped and cannot crash the scheduler (it reads
  `(:whole ev)` only for onsets). The same holds for `test_api.cljs`
  `schedule-pattern!`, `env/builtins/export.cljs` and `env/builtins/midi.cljs`. Nothing
  warns the user, though. `play-track!` is called once per track per evaluation.
- `eval_orchestrator.cljs` `evaluate!`: plays a bare pattern on the anonymous `:_`
  track. `track` (in `env/builtins/tracks.cljs`) records names in
  `builtins/seen-tracks`. At the end, `report-unknown-sounds!` (async, after sample
  manifests load) appends a `:warning` to the last output line via a `set-output!`
  that remembers `last-output`. The comment at the `(and (map? val) …)` branch mentions `(saw :c4)`.
- `sound_check.cljs` is the pure, node-tested home for "this evaluation will not sound
  as you expect" checks (`unknown-sounds`, `warning-message`); tests are in
  `sound_check_test.cljs`.
- `test_api.cljs` `span->event-summary` calls `rat->float` on `(:start whole)`; for a
  nil whole this yields `NaN`.
- `ui/timeline.cljs`, `ui/context_panel.cljs`, `arm-transitions!` read only `:part`/`:value`.
- `synth.cljs` `make-build-fn`: `local (merge closed-env param-bindings ugen-env)`.
  UGens win inside `defsynth` bodies, so `(saw freq)` inside `defsynth` keeps working
  even though top-level `saw` becomes a signal.

### Editor & AI docs

- Grammar `app/src/repulse/lisp-lang/repulse-lisp.grammar` `BuiltinName` already contains
  `"saw" | "square" | "noise"`, `"tri"` (UGen line) and `"range"`.
- `completions.js`: `saw` and `square` entries describe the voice shortcuts (lines ~63–64),
  `tri` describes the UGen (~148), `range` the collection form (~180).
- `hover.js` `DOCS` map: richer docs, read by `gen:ai-docs` for signature/description.
- `content/builtin_meta.edn` is a map keyed by name, so each name can have only one entry. It has
  `:saw`/`:square` (category "source", voice examples), `:tri`/`:sin` (category "ugen"),
  `:range` (category "util"). `:synth`, `:noise` and `:fm` list `"saw"`/`"square"` in `:see-also`.
- `scripts/gen_ai_docs.mjs` fails if a completions label has no `builtin_meta.edn` entry
  and writes `docs/ai/builtins.json`. `docs/ai/concepts.json` is hand-written; its
  `"synth-voices"` entry mentions `(saw :c4), (square :c4)`.
- Demos (`content/demos.cljs`), tutorial (`content/tutorial.cljs`), first-visit content
  and `app/public/snippets/library.json` contain **no** `(saw …)`/`(square …)` voice
  calls (verified with grep). Only docs, completions, meta and one test use them.

### Docs

- `docs/USAGE.md`: "Patterned arguments" and "Pattern arithmetic" sections (TL1), the
  `synth` section with the paragraph "The per-note forms `(saw :c4)`, `(square :c3 :pw 0.25)` …
  remain available" (~line 1065), "Transitions" (`tween`), the `defsynth` UGen table
  (~line 1800), "Common mistakes", "Error messages".
- `docs/STRUDEL-PARITY.md` "Continuous signals — TL2" section (36 rows: 31 📋, 3 🔁, 2 ❓)
  and the summary table row `| Continuous signals | TL2 | 0 | 0 | 31 | 3 | 0 | 2 | 36 |`.
- `docs/ARCHITECTURE.md` documents the event shape with a mandatory `:whole`.

## Implementation

Work in this order. Each step ends with tests green (`npm run test`).

### 0. Verify tooling (CLAUDE.md Rule 6)

```bash
npm run gen:grammar && npm run gen:ai-docs && npm run test
```

All three must exit cleanly on the unmodified branch before you change anything.

### 1. Nil-whole safety in `core.cljs`

Add a private helper and use it wherever both spans are mapped:

```clojure
(defn- map-spans
  "Apply f (span → span) to the event's :part, and to its :whole when present.
   Analog events (whole = nil) keep whole = nil."
  [f ev]
  (cond-> (update ev :part f)
    (:whole ev) (update :whole f)))
```

Rewrite the `(-> e (update :whole …) (update :part …))` blocks in `fast*`, `rev`,
`late*` and `arrange*` to `(map-spans <span-fn> e)`. Behaviour for discrete events must
be identical (existing tests unchanged).

Add public helpers next to `onset?`:

```clojure
(defn analog?
  "True for a continuous (analog) event: it has no :whole."
  [ev]
  (nil? (:whole ev)))
```

Update `core/specs.cljs` `event?` to `(and (map? x) (contains? x :value) (contains? x :whole)
(or (nil? (:whole x)) (span? (:whole x))) (span? (:part x)))`, and add
`(defn analog-event? [x] (and (event? x) (nil? (:whole x))))`.

### 2. Deterministic RNG — `time->rand` in `core.cljs`

Port Tidal's `Sound.Tidal.UI.timeToRand` exactly:

```haskell
xorwise x = let a = (x `shiftL` 13) `xor` x
                b = (a `shiftR` 17) `xor` a
            in  (b `shiftL` 5) `xor` b
timeToIntSeed  = xorwise . truncate . (* 536870912) . snd . properFraction . (/ 300)
intSeedToRand  = (/ 536870912) . realToFrac . (`mod` 536870912)
timeToRand     = intSeedToRand . timeToIntSeed
```

**Why 32-bit JS ops are wrong:** the seed is in (−2²⁹, 2²⁹), so `x << 13` needs up to 42 bits
and `b << 5` up to 47. Haskell `Int` is 64-bit, so nothing overflows there. JS `<<`/`^`
truncate to 32 bits and give different numbers (Strudel's port does this: its value at
t = 1/4 is 0.3697…, Tidal's is 0.6295…). The final `mod 2²⁹` keeps only the low 29 bits,
but those depend on bits up to 46 of the intermediates, so we cannot just truncate to 32 bits.

**Implement with `js/BigInt` and 64-bit wrap** (`BigInt.asIntN(64, …)` after every
shift-left and xor). Everything is exact and also correct for negative times, which
happen with `late`: properFraction and truncate both round toward zero, Haskell
`shiftR` on `Int` is arithmetic, and `mod` is floored.

```clojure
(def ^:private two-29 536870912)

(defn- i64 [x] (js/BigInt.asIntN 64 x))

(defn xorwise
  "Tidal's xorshift step on a 64-bit signed integer (BigInt in, BigInt out)."
  [x]
  (let [a (i64 (js* "(~{} << 13n) ^ ~{}" x x))
        b (i64 (js* "(~{} >> 17n) ^ ~{}" a a))]
    (i64 (js* "(~{} << 5n) ^ ~{}" b b))))

(defn time->int-seed
  "Tidal timeToIntSeed on an exact rational time [n d] → BigInt."
  [[n d]]
  (let [n   (js/BigInt n)
        den (js* "~{} * 300n" (js/BigInt d))
        r   (js* "~{} % ~{}" n den)]                 ; truncating remainder = properFraction
    (xorwise (js* "(~{} * 536870912n) / ~{}" r den)))) ; BigInt / truncates toward zero

(defn time->rand
  "Tidal timeToRand: deterministic float in [0, 1) for rational time t."
  [t]
  (let [s (time->int-seed (->rat t))
        m (js* "((~{} % 536870912n) + 536870912n) % 536870912n" s)] ; floored mod
    (/ (js/Number m) two-29)))
```

Notes for the implementer:
- Use JS operators (`js*` as above, or `js-mod`) for BigInt arithmetic. Do **not** use
  `cljs.core/rem`, `quot`, `mod` or `bit-shift-*`: they go through `Math.floor` or
  number coercion and throw on BigInt. If `js*` with `13n` literals upsets the Closure
  compiler, bind `(js/BigInt 13)` etc. to private vars and use `(js* "~{} << ~{}" x k13)`.
  The reference values below catch any mistake.
- `xorwise` and `time->int-seed` are public because TL4 (`timeToRands`, `randcat`, …)
  reuses them.
- Do not touch `cycle-hash`.

Reference values (Tidal = this implementation; verified with an independent arbitrary-precision
port). All values are exact doubles (k/2²⁹), so compare with `=`:

| t | `time->int-seed` | `xorwise` result | `time->rand` |
|---|---|---|---|
| `0` | 0 | 0 | `0` |
| `1/4` | 447392 | 118449597881 | `0.6295689214020967` |
| `1/2` | 894784 | 236899195762 | `0.2591378428041935` |
| `3/4` | 1342177 | 358200554981 | `0.20052618719637394` |
| `1` | 1789569 | 473798121188 | `0.5177721455693245` |
| `3/2` | 2684354 | 716401109962 | `0.4010523743927479` |
| `73/10` | 13063858 | 3322856674554 | `0.30286644771695137` |
| `-1/4` | −447392 | 118461655578 | `0.6520281322300434` |

### 3. Signals — `core.cljs`

`repulse.core` will define vars named `rand` and `time`, which clash with `cljs.core/rand`
and the `cljs.core/time` macro. Change the ns form to
`(ns repulse.core (:refer-clojure :exclude [rand time]))` and confirm that `core.cljs` does not
use either name internally. Without this, shadow-cljs and clj-kondo warn.

```clojure
(defn signal
  "Continuous pattern from f : rational time → value. A query returns one analog
   event (whole nil) covering the query span, sampled at the span's start."
  [f]
  (pattern
   (fn [{:keys [start end] :as sp}]
     (if (rat< start end)
       [(event (f start) nil sp)]
       []))))

(defn steady [v] (signal (constantly v)))

(defn- cycle-pos
  "Exact position within the cycle, as a float in [0, 1): (n mod d) / d."
  [[n d]]
  (/ (mod n d) d))
```

Define (all values floats; `x` = `(cycle-pos t)`):

| Name | Definition | Values at t = 0, 1/4, 1/2, 3/4 |
|---|---|---|
| `time` | `(signal rat->float)` (absolute cycle time) | 0, 0.25, 0.5, 0.75 (cycle 1: 1, 1.25, …) |
| `saw` | `x` | 0, 0.25, 0.5, 0.75 |
| `isaw` | `1 − x` | 1, 0.75, 0.5, 0.25 |
| `sine` | `(sin(2πx) + 1) / 2` | 0.5, 1, 0.5 (±1e-12), 0 |
| `cosine` | `sine` at `t − 1/4`: use `x' = cycle-pos(t − 1/4)` (Tidal `0.25 ~> sine`) | 0, 0.5, 1, 0.5 (±1e-12) |
| `tri` | `x < 1/2 ? 2x : 2 − 2x` (Tidal `fastAppend saw isaw`) | 0, 0.5, 1, 0.5 |
| `itri` | `1 − tri` | 1, 0.5, 0, 0.5 |
| `square` | `x < 1/2 ? 0 : 1` | 0, 0, 1, 1 |
| `isquare` | `1 − square` | 1, 1, 0, 0 |
| `rand` | `(signal time->rand)` | 0, 0.6295689214020967, 0.2591378428041935, 0.20052618719637394 |
| `perlin` | see below | 0, 0.053597507256199606, 0.25888607278466225, … |

Compute `sine`/`cosine` from `cycle-pos`, not from the absolute float time, so large
cycle numbers do not lose precision.

**`perlin`** (Tidal `perlinWith (sig fromRational)`), sampled at time `t`:
`fl = floor(t)` (an integer cycle), `x = t − fl`, `a = time->rand(fl)`,
`b = time->rand(fl + 1)`, `smootherstep(x) = 6x⁵ − 15x⁴ + 10x³`,
value `a + smootherstep(x)·(b − a)`. Reference: `perlin(0) = 0`,
`perlin(1/4) = 0.053597507256199606`, `perlin(1/2) = 0.25888607278466225`,
`perlin(3/2) = 0.2768947295844555`, `perlin(73/10) = 0.1463428430059552` (±1e-12).
Use exact rationals for `fl` (`[(floor n/d) 1]`) when calling `time->rand`.

**Bipolar:**

```clojure
(defn to-bipolar   [pat] (fmap #(if (number? %) (- (* 2 %) 1) %) pat))
(defn from-bipolar [pat] (fmap #(if (number? %) (/ (+ % 1) 2) %) pat))
(def sine2 (to-bipolar sine))  ; likewise cosine2 saw2 isaw2 tri2 itri2 square2 isquare2 rand2
```

**`irand`** (Tidal `_irand i = floor . (* i) <$> rand`), with the count patternable:

```clojure
(defn- irand* [n] (fmap #(Math/floor (* % (rat->float (->rat n)))) rand))
(defn irand [n] (if (pattern? n) (inner-join (fmap irand* n)) (irand* n)))
```

`(irand 8)` at 0, 1/4, 1/2, 3/4 → `0 5 2 1`.

**`run`** (Tidal `run`): discrete `0 … n−1`, one cycle. `n` is patternable via `inner-join`;
`n ≤ 0` → `silence`.

```clojure
(defn- run* [n] (let [k (int (rat->float (->rat n)))] (if (pos? k) (seq* (vec (range k))) silence)))
(defn run [n] (if (pattern? n) (inner-join (fmap run* n)) (run* n)))
```

### 4. Shaping — `core.cljs`

The names avoid shadowing `cljs.core/range` inside `repulse.core`, which uses `range` internally:

```clojure
(defn- segment* [n pat]
  ;; Tidal: _segment n p = _fast n (pure id) <* p
  (app-left (fn [_ v] v) (fast* n (pure true)) pat))

(defn segment
  "Sample pat n times per cycle: n discrete events with values taken from pat at
   each onset. n may be a number, rational or pattern."
  [n pat]
  ((patternify segment*) n pat))

(defn range-pat
  "Tidal range: scale numeric values v ∈ [0,1] to lo + (hi − lo)·v.
   lo/hi: numbers (fast path, fmap) or patterns (app-left, structure from pat).
   Non-numeric values pass through unchanged."
  [lo hi pat] …)

(defn rangex-pat
  "Exponential range: exp(range(log lo, log hi, v)). lo and hi must be > 0,
   otherwise throw ex-info {:type :eval-error} \"rangex needs positive bounds\"."
  [lo hi pat] …)

(defn range2-pat
  "Strudel range2: range over a bipolar input = (range-pat lo hi (from-bipolar pat))."
  [lo hi pat] …)
```

`range-pat` with patterned bounds:
`(app-left (fn [v [l h]] (scale v l h)) pat (app-both vector (->pat lo) (->pat hi)))`.
`->pat` is private in `core.cljs` and already wraps scalars in `pure`.

### 5. Lisp builtins

**New namespace `packages/lisp/src/repulse/lisp/builtins/signals.cljs`** with
`make-builtins` returning:

- Values: `"sine" "cosine" "saw" "isaw" "tri" "itri" "square" "isquare"`, the eight
  `…2` variants, `"rand" "rand2" "perlin" "time"` → the `core` vars.
- Functions:
  - `"signal"` `(fn [f] (core/signal (fn [t] (u/unwrap (f (core/rat->float t))))))`
  - `"steady"` `(fn [v] (core/steady (u/unwrap v)))`
  - `"irand"` `(fn [n] (core/irand (u/pat-arg n)))`
  - `"run"` `(fn [n] (core/run (u/pat-arg n)))`
  - `"segment"`: 1-arity curried transformer and 2-arity `(core/segment (u/pat-arg n) (u/unwrap p))`
  - `"rangex"`, `"range2"`: 2-arity curried transformer and 3-arity, bounds via `u/pat-arg`
  - `"to-bipolar"`, `"from-bipolar"`: `(fn [p] (core/to-bipolar (u/unwrap p)))`

Register it in `eval.cljs` (require + add `(b-signals/make-builtins)` to the `merge` in
`make-env`, right after `(b-pattern/make-builtins)`).

**`range` dispatch** in `builtins/collection.cljs`. Add `[repulse.core :as core]`:

```clojure
"range" (fn
          ([n]   (range (u/unwrap n)))
          ([a b] (range (u/unwrap a) (u/unwrap b)))
          ([a b c]
           (let [c' (u/unwrap c)]
             (if (core/pattern? c')
               (core/range-pat (u/pat-arg a) (u/pat-arg b) c')        ; Tidal range
               (range (u/unwrap a) (u/unwrap b) c')))))               ; Lisp range
```

Only a **pattern** as the last argument selects Tidal's `range`. A string last argument
stays a Lisp range step, per the decision. `(range lo hi)` with two numbers stays the Lisp
collection, so `range` has no curried form. Inside `->>` it still works because the
pattern is appended as the third argument: `(->> saw (range 2 6))`.

**Remove the voice shortcuts** `"saw"` and `"square"` from `builtins/synth.cljs` and update its ns docstring.
`"noise"`, `"fm"` and `"synth"` stay.

**Helpful error when a pattern is called** in `eval.cljs`. Require `[repulse.core :as core]`
and add:

```clojure
(defn- not-a-function-msg
  "Error text for calling a non-function. Signals/patterns get a hint."
  [head-form f]
  (let [nm  (let [h (unwrap head-form)] (when (symbol? h) (str h)))
        raw (unwrap f)]
    (cond
      (and (core/pattern? raw) (#{"saw" "square"} nm))
      (str nm " is a signal (a continuous pattern), not a function. Use it without "
           "parentheses, e.g. (segment 8 " nm "). For the " nm " synth voice use "
           "(synth :" nm " pat), e.g. (synth :" nm " (seq :c4 :e4))")
      (core/pattern? raw)
      (str nm " is a pattern, not a function. Use it without parentheses, "
           "e.g. (segment 8 " nm ") or (pan " nm " pat)")
      :else
      (str (pr-str head-form) " is not a function"))))
```

Use it in all five "is not a function" throws (general call `:else` branch, and the
two branches each of `->>` and `->`). Keep `:type :eval-error` and the existing
`:from/:to` data. Every message still contains "not a function", so
`type-error-returns-error` keeps passing.

### 6. Structure guard (scheduler + output)

The scheduler needs no code change: `schedule-cycle!` (`audio.cljs`) already uses
`core/query-onsets`, which never yields analog events. Add a one-line comment there saying
analog events are skipped on purpose and the user is warned at eval time.

Add to `app/src/repulse/sound_check.cljs` (pure, node-tested):

```clojure
(defn has-analog-events?
  "True when pat yields any continuous (whole-less) event in cycle 0.
   Such events never trigger sound: a signal needs structure."
  [pat]
  (boolean (some core/analog? (core/query pat (core/cycle-span 0)))))

(defn structure-warning-message [track-name]
  (str "track :" (name track-name) " contains a continuous signal with no rhythm, "
       "which plays nothing. A signal needs structure, e.g. (segment 8 …) "
       "or (pan sine pat)"))
```

In `eval_orchestrator.cljs` `evaluate!`, just **before** the `report-unknown-sounds!` call,
check the tracks this evaluation defined (`(conj @builtins/seen-tracks :_)`) against
`(:tracks @audio/scheduler-state)`. For each track whose pattern satisfies
`has-analog-events?` (wrap in `try`; a throwing query counts as no warning):

- `(js/console.warn "[REPuLse]" msg)`. Each track is warned once per evaluation,
  because the check runs once per `evaluate!`, never per cycle.
- `(set-output! (str @last-output " — Warning: " msgs) :warning)`, the same append style as
  unknown sounds, so a later unknown-sound warning appends after it.

Make `test_api.cljs` `span->event-summary` return `wholeStart`/`wholeEnd` as `nil` for
analog events instead of `NaN`. Update the stale `(saw :c4)` comment in
`eval_orchestrator.cljs` to `(sound :tabla 0), (noise), (fm :c4)`.

### 7. Tests

**`packages/core/src/repulse/core_test.cljs`**: new `deftest`s for every DoD vector:
signal shape and sampling, waveform values, `time->rand` table, `perlin`, `irand`, `run`,
`segment`, `range-pat`/`rangex-pat`/`range2-pat`, bipolar, nil-whole safety of
`fast`/`slow`/`rev`/`late`/`early`, `app-left`/`app-right` with a signal, specs.

**`packages/lisp/src/repulse/lisp/integration_test.cljs`**: replace
`saw-produces-synth-map` with `calling-a-signal-is-a-helpful-error`. Add Lisp-level
tests for the DoD expressions and a `tl2-documented-examples-evaluate` test (like
TL1's) that evaluates every TL2 example in `builtin_meta.edn`/USAGE and checks that it
returns a pattern. For signals, use `segment` or `query` directly, since bare signals
have no onsets.

**`app/src/repulse/sound_check_test.cljs`**: `has-analog-events?` is true for `core/sine`,
`(core/stack* [core/sine (core/pure :bd)])`; false for `(core/segment 8 core/sine)` and
`(params/pan core/sine (core/pure :hh))`; message contains "signal needs structure".

**`e2e/audio-render.spec.ts`**: `renderOffline("sine")` resolves (no throw) with
`rms < SILENCE_RMS`; `renderOffline("(pan sine2 (fast 8 (seq :hh)))")` is non-silent
with 8 onsets (`countOnsets`).

### 8. Registration (CLAUDE.md Rules 3 & 5)

New `BuiltinName` entries (grammar): `signal steady time sine cosine isaw itri isquare
sine2 cosine2 saw2 isaw2 tri2 itri2 square2 isquare2 rand rand2 irand perlin segment
rangex range2 to-bipolar from-bipolar run`. `saw square tri range` are already present.
Put the new names on their own line (`"sine" | "cosine" | …`).

1. Edit the grammar, then **`npm run gen:grammar`** (regenerates `parser.js` and `parser.terms.js`).
   Then check in the browser editor (or with `parser.parse`) that `sine2`, `to-bipolar` and
   `rangex` each highlight as **one** builtin token, and that a user symbol such as
   `sinewave` or `random` stays a plain `Symbol`. Existing pairs like
   `sometimes`/`sometimes-by` show that longest-match works, but verify it.
2. `completions.js`: one entry per new name. Use `type: "variable"` for signal values
   and `type: "function"` for the rest. **Replace** the `saw`/`square` voice entries with
   signal entries; e.g. `saw` gets the detail `"saw — signal 0→1 each cycle; (segment 4 saw). Voice: (synth :saw pat). Inside defsynth: (saw freq) UGen"`.
   **Update** `tri` to describe both the signal and the `defsynth` UGen, and `range` to
   `"(range n) / (range a b step) — numbers; (range lo hi pat) — scale a signal/pattern from 0..1 to lo..hi"`.
3. `hover.js`: `DOCS` entries (signature, description, example) for every new name,
   and updated `saw`/`square`/`tri`/`range` entries.
4. `builtin_meta.edn`: an entry for every new name, using a new category `"signal"` for the
   signal values and `segment`/`range*`/bipolar converters (`run` is `"source"`).
   Each entry needs `:returns "Pattern"`, `:side-effects []`, ≥1 example that evaluates,
   and `:see-also`. **Replace** `:saw`/`:square` (category `"signal"`, examples
   `"(segment 4 saw)"`, `"(->> (fast 8 (seq :hh)) (amp (range 0.3 1 saw)))"`).
   **Replace** `:tri` with category `"signal"`, a top-level example, and a description that
   names the `defsynth` UGen use. **Extend** `:range` examples with
   `"(segment 4 (range 200 2000 saw))"`. Remove `"saw" "square"` from the `:see-also` of
   `:synth`, `:noise` and `:fm` (use `"synth"`/`"noise"`).
5. **`npm run gen:ai-docs`** regenerates `docs/ai/builtins.json`.
6. Edit `docs/ai/concepts.json` `"synth-voices"` by hand: drop "Alternatively: (saw :c4),
   (square :c4)", keep `(fm :c4 :index 3)`. Add a `"signals"` concept: "sine, saw, tri,
   square, rand, perlin, … are continuous patterns (values, no parentheses). Give them rhythm
   with (segment n sig) or by using them as a parameter of a rhythmic pattern: (pan sine pat)."
7. Commit grammar, `parser.js`, `parser.terms.js`, `completions.js`, `hover.js`,
   `builtin_meta.edn`, `builtins.json`, `concepts.json` together.

### 9. Docs

- `docs/USAGE.md`:
  - New **"Signals"** section after "Pattern arithmetic", with a TOC entry. Cover the
    concept (no rhythm; sampled at onsets), a reference table of every signal with its
    range and t = 0 value, `segment`, `range`/`rangex`/`range2`, bipolar helpers,
    `rand`/`irand`/`perlin` (deterministic; matches Tidal's `timeToRand`; `rand` is 0 at
    t = 0; vary with `(early 7 rand)`), `run`, `signal`/`steady`/`time`, and the
    examples from the Goal.
  - A **"Signals vs tween"** subsection in "Transitions": tween is a one-shot ramp from
    evaluation time; signals are cyclic and the same on every evaluation; use
    `(slow 8 saw)` for a repeating 8-cycle sweep.
  - **"Common mistakes"**: "Calling a signal": `(saw :c4)` / `(sine)` → use
    `(synth :saw …)` / bare `sine`. A bare signal as a track plays nothing.
  - `synth` section: replace the "per-note forms `(saw :c4)`, `(square :c3 :pw 0.25)` …
    remain available" paragraph. Only `(fm :c4 …)` and `(noise)` remain as per-note
    forms; show `(synth :square :pw 0.25 pat)` for pulse width.
  - `defsynth` UGen table: note that `saw`, `square`, `tri` inside `defsynth` are UGens,
    independent of the top-level signals.
  - "Error messages": add the signal-call error and the structure warning.
- `README.md`: add rows to "Built-in pattern functions":
  `| (pan sine2 pat) | Continuous signals: sine saw tri square rand perlin … sampled by a rhythm |`
  and `| (segment 8 (range 1 8 rand)) | Discretise a signal into n events per cycle |`.
- `docs/ARCHITECTURE.md`: event shape note: `:whole` is `nil` for continuous (analog) events.
- `docs/STRUDEL-PARITY.md` TL2 section:
  - ✅ for `signal steady time sine cosine saw isaw tri itri square isquare`, the bipolar
    row, `rand rand2 irand perlin run segment range rangex range2 toBipolar fromBipolar`
    (23 rows). Notes: `cosine`: "Tidal phase (`0.25 ~> sine`, 0 at t = 0); Strudel's
    is `early 1/4` (1 at t = 0)". `tri`: "Tidal `fastAppend saw isaw`". `rand`:
    "Tidal `timeToRand` exactly (64-bit); sampled at span start like Strudel".
    `saw`/`square`: "voice shortcut removed; use `(synth :saw …)`".
  - Change Phase to **TL4** for `brand`, `brandBy`, `berlin`, `randL`, `randrun`,
    `withSeed`/`seed`, and to **TL3** for `binary`/`binaryN` and `binaryL`/`binaryNL`,
    each with the note "moved from TL2 (TL2 spec)".
  - Replace the "None exist yet." intro sentence with a short summary of the sampling
    rule (sampled at the start of the structure event's whole).
  - Recompute the summary table: the TL2 row becomes `| 23 | 0 | 8 | 3 | 0 | 2 | 36 |`
    (✅ 🟡 📋 🔁 ⛔ ❓ Total), and update the totals row accordingly.
- `ROADMAP.md`: TL2 heading → `✅ *delivered*` with a **Delivered:** list (TL1 style);
  milestone table row 37 spec column `outline` → `full`.
- `CLAUDE.md`: the phase table has no TL2 row yet; add
  `| TL2   | Continuous signals — sine/saw/rand/perlin, segment, range      | ✓ delivered  |`.

## Files to change

| File | Change |
|---|---|
| `packages/core/src/repulse/core.cljs` | `map-spans` nil-whole safety in `fast*` `rev` `late*` `arrange*`; `analog?`; `xorwise` `time->int-seed` `time->rand`; `signal` `steady` `time`; waveforms + `…2` variants; `rand` `rand2` `irand` `perlin` `run`; `segment`; `range-pat` `rangex-pat` `range2-pat`; `to-bipolar` `from-bipolar` |
| `packages/core/src/repulse/core/specs.cljs` | `event?` accepts nil whole; `analog-event?` |
| `packages/core/src/repulse/core_test.cljs` | Tests for all of the above (DoD vectors) |
| `packages/lisp/src/repulse/lisp/builtins/signals.cljs` (new) | Signal values and functions |
| `packages/lisp/src/repulse/lisp/builtins/collection.cljs` | `range` 3-arity dispatches on a pattern last argument |
| `packages/lisp/src/repulse/lisp/builtins/synth.cljs` | Remove `saw`/`square` voice shortcuts |
| `packages/lisp/src/repulse/lisp/eval.cljs` | Register `b-signals`; require `repulse.core`; `not-a-function-msg` in the 5 throw sites |
| `packages/lisp/src/repulse/lisp/integration_test.cljs` | Replace `saw-produces-synth-map`; TL2 Lisp tests; documented-examples test |
| `app/src/repulse/sound_check.cljs` + `sound_check_test.cljs` | `has-analog-events?`, `structure-warning-message` + tests |
| `app/src/repulse/eval_orchestrator.cljs` | Structure warning (console + output) once per track per eval; fix stale comment |
| `app/src/repulse/audio.cljs` | Comment only (analog events skipped by `query-onsets`) |
| `app/src/repulse/test_api.cljs` | `wholeStart`/`wholeEnd` nil for analog events |
| `e2e/audio-render.spec.ts` | Bare `sine` renders silence without error; `pan sine2` render has 8 onsets |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` + `parser.js` + `parser.terms.js` | New `BuiltinName`s (`npm run gen:grammar`) |
| `app/src/repulse/lisp-lang/completions.js`, `hover.js` | New entries; `saw` `square` `tri` `range` rewritten |
| `app/src/repulse/content/builtin_meta.edn` | New entries; `:saw` `:square` `:tri` `:range` updated; `:see-also` fixes |
| `docs/ai/builtins.json` | Regenerated (`npm run gen:ai-docs`) |
| `docs/ai/concepts.json` | `synth-voices` text; new `signals` concept |
| `docs/USAGE.md`, `README.md`, `docs/ARCHITECTURE.md`, `docs/STRUDEL-PARITY.md` | See step 9 |
| `ROADMAP.md`, `CLAUDE.md` | Delivered markers |

## Definition of done

Unless stated otherwise, "cycle 0" means `(query pat (cycle-span 0))`. Exact values use
`=`; values marked ± use `(< (Math/abs (- a b)) tol)`.

- [ ] **Signal shape:** `(query sine (span [1 4] [1 2]))` returns exactly one event
      `{:value 1 :whole nil :part {:start [1 4] :end [1 2]}}` (value ±1e-12); `(query saw (span [1 2] [5 2]))`
      returns one event with part `[1/2, 5/2)` and value `0.5`; a query with `start ≥ end` returns `[]`.
      `(query-onsets sine (cycle-span 0))` is empty. `specs/valid-event?` accepts these analog events.
- [ ] **Segment:** `(segment 4 saw)` over cycle 0 yields 4 onsets with wholes
      `[0,1/4) [1/4,1/2) [1/2,3/4) [3/4,1)` and values `0 0.25 0.5 0.75`; over cycle 1,
      the same values with wholes shifted by 1. `(segment (seq 2 4) saw)` yields onsets at
      `0 1/2 3/4` with values `0 0.5 0.75`.
- [ ] **Waveforms** sampled via `(segment 4 sig)` in cycle 0 match the table in step 3:
      `sine` `0.5 1 0.5 0`, `cosine` `0 0.5 1 0.5`, `tri` `0 0.5 1 0.5`, `square` `0 0 1 1`,
      `isaw` `1 0.75 0.5 0.25` (±1e-12); bipolar `sine2` `0 1 0 -1`, `saw2` `-1 -0.5 0 0.5` (±1e-12).
      `(segment 4 time)` in cycle 1 → `1 1.25 1.5 1.75`.
- [ ] **RNG matches Tidal:** `(time->rand t)` equals exactly `0`, `0.6295689214020967`,
      `0.2591378428041935`, `0.4010523743927479`, `0.30286644771695137`, `0.6520281322300434`
      for `t` = `[0 1] [1 4] [1 2] [3 2] [73 10] [-1 4]`, and `(time->int-seed [1 4])` → BigInt
      `447392`. Calling twice gives identical results (deterministic across runs).
      `(segment 4 rand)` in cycle 0 → `0 0.6295689214020967 0.2591378428041935 0.20052618719637394`.
- [ ] **irand / perlin / run:** `(segment 4 (irand 8))` → `0 5 2 1`; `perlin` sampled at
      `0 1/4 1/2 3/2 73/10` → `0 0.053597507256199606 0.25888607278466225 0.2768947295844555
      0.1463428430059552` (±1e-12); `(run 4)` → onsets `0 1/4 1/2 3/4` with values `0 1 2 3`.
- [ ] **Parameters sampled at onsets:** `(pan sine (fast 8 (seq :hh)))` in cycle 0 yields
      8 onsets with `:pan` = `0.5 0.8535533905932737 1 0.8535533905932737 0.5 0.14644660940672627 0 0.14644660940672616`
      (±1e-12): rising to 1, falling to 0, then rising. `(->> (fast 16 (seq :hh)) (amp (range 0.3 0.9 (slow 4 saw))))`
      → `:amp` `0.3` at onset 0, `0.375` at onset 1/2 and `0.440625` at onset 15/16 in cycle 0, and
      `0.890625` at onset 63/16 (cycle 3) (±1e-12).
- [ ] **Range:** `(segment 16 (range 2 6 saw))` values all lie in `[2, 6)` and the first is `2`;
      `(range 2 6 sine)` sampled at 1/4 → `6`. `(rangex 20 2000 saw)` sampled at `0 1/4 1/2`
      → `20 63.245553203367585 200` (±1e-9). `(rangex 0 10 saw)` is an eval error. `(range2 -1 1 sine2)` equals
      `sine2` at the four quarter points. Lisp `(vec (range 5))` → `[0 1 2 3 4]` and `(range 0 8 2)` →
      `(0 2 4 6)` (collection behaviour unchanged).
- [ ] **Nil-whole safety:** `fast 2`, `slow 4`, `rev`, `late 1/4`, `early 1/4` on `sine` do not throw
      and keep `:whole nil`; `(segment 4 (fast 2 saw))` → `0 0.5 0 0.5`; `(segment 4 (late 1/4 saw))` → `0.75 0 0.25 0.5`.
- [ ] **Lisp examples:** `(->> (segment 8 (range 1 8 rand)) (scale :minor :c4))` yields 8 onsets with
      numeric Hz values; `(pan (range -0.5 0.5 perlin) (fast 4 (seq :hh)))` yields 4 onsets with
      `:pan` in `[-0.5, 0.5]`; curried forms work: `(->> saw (segment 4))`, `(->> saw (range 2 6))`,
      `(->> sine (rangex 100 1000) (segment 2))`.
- [ ] **Voice shortcut removed:** `(saw :c4)` and `(square :c4 :pw 0.3)` are eval errors whose
      message contains "not a function" and `(synth :saw` / `(synth :square`; `(sine)` is an eval
      error mentioning "without parentheses"; `(synth :saw (seq :c4 :e4))` still yields maps with
      `:synth :saw`; a `defsynth` body using `(saw freq)` still builds (UGen env wins).
- [ ] **Structure guard:** `has-analog-events?` is true for `sine` and for `(stack sine (pure :bd))`,
      false for `(segment 8 sine)`. Evaluating `sine` in the app plays nothing, shows an output
      warning containing "signal needs structure, e.g. (segment 8 …)", logs one `console.warn` for
      that evaluation, and the scheduler keeps running (other tracks keep playing). The Playwright
      test `renderOffline("sine")` resolves with RMS below `SILENCE_RMS`.
- [ ] **Registration (Rules 3/5):** every new name (list in step 8) is in `BuiltinName`,
      `completions.js`, `hover.js` and `builtin_meta.edn`; `saw`/`square`/`tri`/`range` entries are updated.
      `npm run gen:grammar` and `npm run gen:ai-docs` produce no diff after the commit, and every
      `builtin_meta.edn` example for the new names evaluates (documented-examples test).
- [ ] **Docs:** USAGE "Signals" section (with TOC entry), "Signals vs tween", common-mistake and
      error-message entries, `synth`/`defsynth` paragraphs fixed; README rows; ARCHITECTURE event
      note; STRUDEL-PARITY TL2 rows and summary counts as in step 9; ROADMAP TL2 delivered (and table
      row `full`); CLAUDE.md TL2 row `✓ delivered`.
- [ ] **Verification:** `npm run test:all` and `npx shadow-cljs compile app` both pass; existing
      core, lisp, session and TL1 tests pass unchanged (apart from the replaced
      `saw-produces-synth-map`).

## What NOT to do

- No new structure combinators beyond `segment`: `struct`, `mask`, `ply`, `chunk`, … are **TL3**
  (and so are the `binary*` rows moved there).
- No random *combinators* (`shuffle`, `scramble`, `randcat`, `wchoose` as a signal, `degrade-by`
  rework, `sometimes` per-event semantics, `with-seed`): **TL4**. Build only the RNG they will use
  (`xorwise`, `time->int-seed`, `time->rand`) and leave `cycle-hash` alone.
- No audio-rate modulation inside the WASM engine: signals are sampled once per event, at the
  event's onset, in ClojureScript.
- Don't remove or change `tween`, `noise`, `fm` or `synth`. Don't touch `ugen-env` in
  `app/src/repulse/synth.cljs`: `saw`/`square`/`tri` inside `defsynth` stay UGens.
- Don't add midpoint sampling or a per-signal sampling option. The start-of-span rule is
  decided; a later phase can revisit it with its own test vectors.
- No `sample-and-hold`, `mousex`/`mousey` or `berlin` in this phase.
