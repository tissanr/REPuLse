# Phase TL4b — Pick & Fix

> **Spec status:** full (expanded 2026-11-05 from the outline; function list from the TL4b
> rows of [docs/STRUDEL-PARITY.md](../docs/STRUDEL-PARITY.md); design decisions D1–D5 by
> the project owner, 2026-11-05; decisions K1–K14 taken during expansion)
>
> **Automation:** 🤖 auto — selection is deterministic and produces exact event lists
> testable in `cljs.test`.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL1 (`inner-join`, `outer-join`, `squeeze-join`, `->pat`); TL4 (the
conditional vocabulary this phase completes). TL7 is **not** required: `fix` matches the
event-map keys that exist today and picks up TL7's controls later (D2).

## Goal

Finish the Strudel/Tidal conditional vocabulary that TL4 deferred. The `pick` family
chooses a pattern (or, with `pick-f`, a function) out of a lookup — a vector indexed by
number or a map indexed by name — under the control of an index pattern, and the five
variants differ only in how the chosen pattern is joined in (inner, outer, restart, reset,
squeeze). Tidal's `fix` applies a function only to the events whose control values match a
condition map. Two new joins, `restart-join` and `reset-join`, land in `repulse.core`.
13 new built-ins, no changed ones.

```lisp
;; Before — selecting by an index pattern needs nested every/when-pat chains,
;; and there is no way to transform only the kick of a stacked pattern.
(when-pat "<1 0>" (fn [_] (seq :hh :hh :hh)) (seq :bd :sd))

;; After
(pick {:a (seq :bd :sd) :b (seq :hh :hh :hh)} "<a b>")   ; choose a pattern by name
(pick ["bd sd" "hh*3"] "<0 1>")                           ; or by index; strings are mini-notation
(pick-mod [(seq :bd :sd) (seq :hh :hh)] "0 1 2")          ; index wraps around (2 → 0)
(pick-out [:bd :sd :hh] "0 1 2 1")                        ; structure from the index pattern
(pick-f "<0 1>" [(fast 2) rev] (seq :a :b))               ; choose a function
(pick-restart [(slow 2 (seq :a :b :c :d)) :x] "1 0")      ; chosen pattern restarts at each step
(inhabit {:a (seq :bd :sd) :b :hh} "a b")                 ; squeeze one cycle into each step
(fix (fast 2) {:s :bd} (seq :hh :bd))                     ; only the kick is sped up
(fix (amp 0.3) {:s [:sd :hh]} (seq :bd :hh :sd :hh))      ; any-of: snare or hat
```

## Design decisions

### Decided by the project owner (2026-11-05, verbatim from the outline)

- **D1 — Lookup form:** accept both. A vector is indexed by integer pattern values
  (clamped to the vector's range for `pick`, wrapped with `mod` for `pick-mod`). A map is
  looked up by name: pattern values `"a"` or `:a` match the keyword key `:a`. A name that
  isn't in the map produces no event (silence) for that slot.
- **D2 — `fix` ships in TL4b** (not split out until TL7). It matches against the
  event-map keys that exist today (`:s`, `:note`, `:amp`, …) and will pick up TL7's
  per-event controls later without a rewrite.
- **D3 — `fix` matching rules:** numbers compare numerically (`==`, so `1` matches
  `1.0`); keywords and strings compare exactly (`{:s :bd}` does **not** match
  bank-prefixed `:AkaiLinn_bd`); a key missing from the event means no match; all keys in
  the condition map must match (AND).
- **D4 — any-of:** a vector value in the condition map means "any of"
  (`(fix f {:s [:bd :sd]} p)` applies `f` to bd or sd events).
- **D5 — `restartJoin`/`resetJoin`** live in `core`, with tests.

### Taken during expansion (minor details the outline left open)

| # | Question | Decision |
|---|---|---|
| K1 | Strudel's `mod` variants (`pickmodF`, `pickmodOut`, `pickmodRestart`, `pickmodReset`, `inhabitmod`/`pickmodSqueeze`) | **Separate names**, following the existing `pick-mod` spelling: `pick-mod-f`, `pick-mod-out`, `pick-mod-restart`, `pick-mod-reset`, `inhabit-mod`. The parity doc's "modulo variant as option" note is replaced: a leading `:mod` flag would be ambiguous with a keyword index pattern (`(pick-f :a {...} pat)`) and with the curried arities, and a trailing flag would break `->>`. The parity rows are updated accordingly (step 9). |
| K2 | Argument order | Strudel's function form, index/input pattern **last** so `->>` threads it: `(pick lookup ipat)` (all 10 pick/inhabit names), `(pick-f ipat fs pat)`, `(fix f cond pat)` (Tidal `fix f ctrls pat`). Every name has a curried form without its last argument: `(pick lookup)`, `(pick-f ipat fs)`, `(fix f cond)`. Strudel's backward-compat argument swap in `pick` (array second) is **not** copied: a wrong order gives error E1. |
| K3 | Index → vector element | Strudel `_pick`: `i = Math.round(x)` (JS rounding: half towards +∞, `-0.5 → 0`, `1.5 → 2`, `2.5 → 3`); `pick*` clamps to `[0, n−1]`; `*-mod*` uses **floored** `mod` (`(mod -1 3)` = 2; Strudel's old `%` would give an invalid negative index). `x` may be a number or a REPuLse rational `[n d]` (`1/2` → 0.5 → index 1). Any other value (keyword, string, map, `nil`) on a vector lookup → silence for that slot. |
| K4 | Name → map entry | `(get m v)` first (so maps with number or string keys work by exact key), then, if `v` is a string, `(get m (keyword v))` (D1: `"a"` → `:a`). A miss → silence for that slot (D1). The `mod` variants use the same name lookup on maps (Strudel: modulo only applies to arrays), so `(pick-mod {…} …)` ≡ `(pick {…} …)`. |
| K5 | Lookup entries | Strudel `reify`: a pattern stays a pattern; a string is **mini-notation** (`u/pat-arg`, with editor source offsets so its tokens highlight); any other value `v` → `(core/pure v source)` with the source of the literal (as TL4's `step->pat`). For `pick-f`/`pick-mod-f` every entry must be a function (error E2); each is wrapped by `xf` like every TL4 function argument. An empty vector or map → `silence` (no error). |
| K6 | Joins | Exactly Strudel `pick.mjs`: `pick`, `pick-mod` → `inner-join`; `pick-out`, `pick-mod-out` → `outer-join`; `pick-restart`, `pick-mod-restart` → `restart-join`; `pick-reset`, `pick-mod-reset` → `reset-join`; `inhabit`, `inhabit-mod` → `squeeze-join`; `pick-f`, `pick-mod-f` → `(inner-join (fmap #((f_i) pat) ipat))` (Strudel `pat.apply(pick(lookup, funcs))`, whose two inner joins collapse into one query of `(f_i pat)` over each index event's part). The existing TL1 joins are used unchanged. |
| K7 | `pick` with plain values | **No special case** (Strudel semantics): `inner-join` keeps the inner pattern's wholes, so `(pick [:bd :sd] "0 1")` gives `:bd` whole `[0,1)` part `[0,1/2)` (an onset) and `:sd` whole `[0,1)` part `[1/2,1)` (a fragment, **no** onset) — one sound per cycle. The docs and hover text say: for one value per index step use `pick-out` (structure from the index) or `inhabit`. |
| K8 | `reset-join` / `restart-join` | Strudel `resetJoin(restart)`: analog outer events (no whole) are dropped; for each outer event with whole start `ws`, the inner pattern is shifted `late` by `ws − ⌊ws⌋` (reset: the inner **cycle start** aligns to the step) or by `ws` (restart: inner **cycle 0** aligns to the step); result whole = inner whole ∩ outer whole (`nil` stays `nil` for analog inner events), part = inner part ∩ outer part, events with no part intersection dropped. The inner pattern is queried over the outer **part** (as REPuLse's `inner-join` does) rather than the whole query span — identical for discrete events, and analog inner events are sampled at the outer part start. The inner event's `:source` is kept (as `inner-join`). |
| K9 | `fix`: event value → control view | `fix` compares against a *control view* of each event value. A map is used as is; a bare keyword, number or rational `[n d]` is first upgraded with the existing `to-map` convention to `{:note v}` (what `amp`, `pan`, `set` already do). Because REPuLse has no `:s` key yet (the sound name lives in `:note` for `:bd`, or `:bank` for `(sound :bd 2)` / `"bd:2"`), the view adds a **derived `:s`** when the map has none: `(:bank m)`, else `(:note m)` when that is a keyword that is not a note name (`repulse.theory/note-keyword?`). So `:bd`, `{:note :bd :amp 0.5}`, `(sound :bd 2)` all have `:s :bd`; `:c4` has `:note :c4` but no `:s`. A real `:s` key (TL7) wins over the derived one, so TL7 needs no rewrite (D2). Rests (`:_`, `nil`, maps whose `:note` is `:_`) and other values (strings, booleans, other vectors, functions) have no controls and never match. |
| K10 | `fix`: value comparison | Per D3/D4. Condition value `cv` vs event value `ev` (`ev` = `nil` → no match): if `cv` is a vector → match if any element matches; else if both sides are numeric (a number or an integer pair `[n d]`, `d ≠ 0`) → `(== (num cv) (num ev))`; else `(= cv ev)` (keywords and strings exactly; `"bd"` does not match `:bd`). An empty condition `{}` matches every non-rest event (vacuous AND, Tidal `isSubsetOf`). |
| K11 | `fix`: rational literals in the condition | REPuLse reads `1/2` as the vector `[1 2]`, which D4 would treat as "any of 1, 2". The Lisp wrapper turns a **rational literal** condition value into its number before calling core: the reader leaves a rational as a plain `[n d]` whose elements are *not* `SourcedVal`s, while a vector literal's number elements are `SourcedVal`-wrapped. So `{:amp 1/2}` matches amp 0.5, `{:amp [1 2]}` is any-of 1 or 2, and `{:amp [1/2 1]}` is any-of 0.5 or 1. Test both forms (DoD). Core's `fix` itself always treats a vector as any-of. |
| K12 | `fix` semantics and order | Tidal `fix f = contrast f id`: `(stack* [(f matched) unmatched])`, where `matched`/`unmatched` filter the input by the condition (structure kept, fragments kept). `f` sees only the matched events, so `(fix (fast 2) …)` speeds up only those. The condition is a plain map, **not** patterned (Tidal accepts a control pattern but is almost always given a constant map). |
| K13 | Where `fix` lives | `repulse.params` (core package), next to `to-map`: it needs `repulse.theory/note-keyword?`, and `repulse.theory` requires `repulse.core`, so `core.cljs` cannot host it without a cycle. `params` already requires `core`; it adds `[repulse.theory :as theory]` (no cycle: `theory` requires only `core`). |
| K14 | Errors | Thrown eagerly by the Lisp wrappers (also in the curried arity), as `js/Error` like `tween`: **E1** `"<name>: the lookup must be a vector [...] or a map {...}. Usage: (<name> lookup index-pattern)"` (for `pick-f`/`pick-mod-f`: `"<name>: the lookup must be a vector [...] or a map {...} of functions. Usage: (<name> index-pattern [f1 f2] pat)"`); **E2** `"<name>: every lookup entry must be a function, e.g. (fast 2) or rev"`; **E3** `"fix: the condition must be a map like {:s :bd}. Usage: (fix f {:s :bd} pat)"`. `<name>` is the built-in's own name. |

> **Amendment (2026-11-05, owner-approved, after delivery):** K9 no longer excludes note
> names. The derived `:s` is `(:bank m)`, else **any** keyword `(:note m)`. The
> `note-keyword?` check made sample names that look like pitches (`:cb` cowbell = C-flat,
> single letters `:a`–`:g`) silently unmatchable by `{:s …}`. `{:s :c4}` now matches a `:c4`
> event, which is harmless. K13's `repulse.theory` dependency is gone; `fix` stays in
> `repulse.params`.

## Function list

`ipat` = index pattern (a pattern, a mini-notation string, or a scalar → `pure`).
`lookup` = vector or map (K5). "Clamp"/"mod" per K3, names per K4.

| # | REPuLse-Lisp | Strudel / Tidal | Semantics | Join |
|---|---|---|---|---|
| 1 | `(pick lookup ipat)` | `pick` | Chosen pattern keeps its own structure; vector index clamped | inner |
| 2 | `(pick-mod lookup ipat)` | `pickmod` | As `pick`, vector index wraps | inner |
| 3 | `(pick-f ipat fs pat)` | `pickF` | `(f_i pat)` where `ipat` selects `f_i`; clamped | inner |
| 4 | `(pick-mod-f ipat fs pat)` | `pickmodF` | As `pick-f`, index wraps | inner |
| 5 | `(pick-out lookup ipat)` | `pickOut` | Structure from `ipat`; each step takes the chosen pattern's value at the step start | outer |
| 6 | `(pick-mod-out lookup ipat)` | `pickmodOut` | As `pick-out`, index wraps | outer |
| 7 | `(pick-restart lookup ipat)` | `pickRestart` | Chosen pattern starts from **its cycle 0** at every step | restart |
| 8 | `(pick-mod-restart lookup ipat)` | `pickmodRestart` | As `pick-restart`, index wraps | restart |
| 9 | `(pick-reset lookup ipat)` | `pickReset` | Chosen pattern's **current cycle** starts again at every step | reset |
| 10 | `(pick-mod-reset lookup ipat)` | `pickmodReset` | As `pick-reset`, index wraps | reset |
| 11 | `(inhabit lookup ipat)` | `inhabit` / `pickSqueeze` | One cycle of the chosen pattern squeezed into each step | squeeze |
| 12 | `(inhabit-mod lookup ipat)` | `inhabitmod` / `pickmodSqueeze` | As `inhabit`, index wraps | squeeze |
| 13 | `(fix f cond pat)` | Tidal `fix` | `(stack (f matched) unmatched)`; `cond` a map, K9–K12 | — |

Core only (not Lisp built-ins, like `inner-join`): `restart-join`, `reset-join`.

## Background

### Pattern engine — `packages/core/src/repulse/core.cljs`

- `ns`: `(:refer-clojure :exclude [rand time chunk iter shuffle])` — none of the new names
  clash with `cljs.core`.
- Rationals `[n d]`: `rat`, `rat+`, `rat-`, `rat*`, `rat-div`, `rat<`, `rat<=`,
  `rat->float`, `int->rat`, `->rat`. Spans: `span`, `span-intersect`, `cycle-span`,
  `split-cycles`.
- `pattern`, `pattern?` (tag `::pat`, so event-value maps are never mistaken for patterns),
  `query`, `pure` (optional source), `seq*`, `stack*`, `fmap`, `silence`, private `->pat`
  (`(if (pattern? x) x (pure x))`), private `whole-or-part`, private `with-source`,
  `query-onsets`, `analog?`.
- **Joins section** (`;;; ── Joins ──`, after `combine`):
  - `inner-join` — for each outer event, queries the inner pattern over the outer
    **part** and keeps the inner event with `:part` cut to the query span (inner whole and
    `:source` kept).
  - `outer-join` — queries the inner pattern over the outer whole and keeps the inner
    value whose part contains the outer whole start; result has the outer whole/part,
    `:source` inner-or-outer.
  - `squeeze-join` — maps cycle `c` of the inner pattern into the outer event's whole
    (`c` = floor of the outer whole start); `:source` from the inner event.
  - `reset-join` / `restart-join` do not exist yet (D5).
- Private `late*` `[amount pat]` (amount via `->rat`) — the shift used by the new joins;
  public `late` patternifies it.
- `cat*` (one pattern per cycle) and `seq*` are what the test vectors build index patterns
  with.
- Private `num-val` (`[n d]` → float, else unchanged) — not safe on keywords, so the pick
  index needs its own numeric check (K3).
- TL4 block ends with `;; Conditionals` (`when-pat`, `whenmod`, `apply-n`) right before
  `;;; ── Phase TL5: Layering & arpeggios ──` (`superimpose`). The TL4b block goes between
  them.
- `filter-events` `[pred pat]` keeps events whose **value** satisfies `pred` (structure and
  fragments kept) — `fix` uses it.

### Value maps — `packages/core/src/repulse/params.cljs`

- `ns repulse.params (:require [repulse.core :as core])`.
- Private `to-map`: `(if (map? v) v {:note v})` — the convention every parameter
  transformer (`amp`, `attack`, `decay`, `release`, `pan`, `rate`, `begin`, `end*`,
  `loop-sample`, `midi-out`, via private `apply-param`) uses to upgrade a bare value. So
  `(amp 0.5 (seq :bd))` has value `{:note :bd :amp 0.5}`, and `(pan 0 (seq :hh))`
  `{:note :hh :pan 0}`.
- Public transformers have a 1-arity curried form, e.g. `(amp 0.2)` → `pat → pat`; the
  test vectors use `(params/amp 0.2)` as `f`.
- Event values found in practice (checked against the current code):
  `(seq :bd :sd)` → bare keywords `:bd`, `:sd` (with `:source`);
  `(sound :bd 2)` (`builtins/synth.cljs`) and mini `"bd:2"` (`mini.cljs`) → `{:bank :bd :n 2}`;
  `(amp 1/2 (seq :bd))` → `{:note :bd :amp [1 2]}` (a Lisp rational stays `[n d]`);
  `(synth :saw …)` → `{:note … :synth :saw :freq …}`; rests `:_` are events.
  There is **no** `s`/`note` builder and no `:s` key today (STRUDEL-PARITY: `s`/`sound`
  "keywords, `sound`"; `note` 🟡 TL7) — hence K9's derived `:s`.
- The bank prefix of `(bank :AkaiLinn)` is applied at playback (`app/src/repulse/samples.cljs`,
  `active-bank-prefix`), not in event values; a pattern only contains `:AkaiLinn_bd` when the
  user writes it.

### Music theory — `packages/core/src/repulse/theory.cljs`

- `ns repulse.theory (:require [repulse.core :as core])`.
- `note-keyword?` — `(re-matches #"[a-g][sb]?(-?\d+)?" (name kw))`: `:c4`, `:eb3`, `:a` are
  notes; `:bd`, `:sd`, `:hh`, `:cp`, `:AkaiLinn_bd` are not.

### Lisp — `packages/lisp/src/repulse/lisp/`

- `util.cljs`: `unwrap`, `deep-unwrap` (recursively strips `SourcedVal`s from vectors,
  seqs and maps), `source-of`, `sourced?`, `pat-arg` (strings → `mini/parse` with source
  offsets; everything else unwrapped, never coerced).
- `reader.cljs`: primitives (keyword, number, string, boolean, nil) are wrapped in
  `SourcedVal`; `read-number` returns a rational literal `1/2` as a plain `[1 2]`. Observed
  evaluation results: `[:a 1/2 "s"]` → `[SV(:a) [1 2] SV("s")]`; a map literal
  `{:a (seq :bd) 0 :x}` → keys and top-level values plain (`{:a <pattern>, 0 :x}`), while a
  vector inside a map keeps wrapped elements: `{:amp [1 2] :b 1/2 :c [1/2 1]}` →
  `{:amp [SV(1) SV(2)], :b [1 2], :c [[1 2] SV(1)]}`. Hence `deep-unwrap`
  for the `fix` condition and per-element `unwrap`/`source-of` for lookups, and K11's
  rational test.
- `builtins/random.cljs` (TL4) is the model: private helpers `xf` (wraps a Lisp function
  so `SourcedVal`s never reach core), `step->pat` (value → `pure` with source), `n+p`,
  `f+p`, `n+f+p`; curried arities return `(fn [p] …)`.
- `eval.cljs` `make-env` merges `(b-random/make-builtins)` right after
  `(b-structure/make-builtins)`; the new namespace is merged right after `b-random`.
- `fast`, `rev`, `amp` etc. have curried 1-arities, so `[(fast 2) rev]` and `(amp 0.2)` are
  functions.
- `mini.cljs`: `"<a b c>"` → keyword values `:a :b :c`, one per cycle; `"0 1 2"` → numbers.

### Tests

- `packages/core/src/repulse/core_test.cljs` — `tl4-*` deftests are the model.
- `packages/core/src/repulse/params_test.cljs` — `fix` tests go here.
- `packages/lisp/src/repulse/lisp/integration_test.cljs` — Lisp-level vectors and
  `tl4-documented-examples-evaluate` (the model for `tl4b-documented-examples-evaluate`);
  helper `eval-pattern`, `make-test-env`.
- `e2e/audio-render.spec.ts` — the `// TL4: randomness & conditionals` onset-count block
  (`render`, `mixMono`, `countOnsets`; `:cp` counts as 2 onsets, so avoid it).
- `app/src/repulse/test_api.cljs` — `window.__REPULSE_TEST__.evalToEvents(code, cycles)`
  on `test-harness.html` for the PR test protocol.

### App, editor & AI docs

- `app/src/repulse/lisp-lang/repulse-lisp.grammar` — `BuiltinName` alternatives (TL4's
  line starts `"undegrade-by" | "undegrade" | …`); `npm run gen:grammar` regenerates
  `parser.js` and `parser.terms.js`.
- `app/src/repulse/lisp-lang/completions.js` (`{ label, type: "function", detail }`),
  `app/src/repulse/lisp-lang/hover.js` (`DOCS` entries `{signature, description, example}`),
  `app/src/repulse/content/builtin_meta.edn` (`:category`, `:returns`, `:side-effects`,
  `:examples`, `:see-also`) → `npm run gen:ai-docs` → `docs/ai/builtins.json`.
- None of the 13 names exists anywhere today (checked: no `"pick…"`, `"fix"` or
  `"inhabit"` builtin, grammar entry, completion or meta entry).

### Docs

- `docs/USAGE.md` — `### Conditionals — \`when-pat\` \`whenmod\` \`apply-n\`` (in the
  random/conditionals area) is where the new subsections go.
- `docs/STRUDEL-PARITY.md` — "Randomness & conditionals — TL4" section (rows `pick` …
  `inhabitmod`, all 📋 TL4b or 🔁), the intro sentence "The `pick` family, `inhabit` and
  Tidal `fix` are deferred to **TL4b**.", the Tidal-only row `fix` (📋 TL4b) and the Summary
  row `Randomness & conditionals | TL4/TL4b | 27 | 0 | 6 | 8 | 1 | 2 | 44`.
- `ROADMAP.md` — implementation-order row `| 6a | TL4b | Pick & fix | 🤖 | outline | sonnet |`
  and section `## Phase TL4b — Pick & Fix 📋 *planned*`.
- `CLAUDE.md` in this branch has no phase table any more ("`ROADMAP.md` holds every phase
  and its status") — nothing to update there.

## Implementation

### 0. Verify tooling (CLAUDE.md Rule 4)

`npm run gen:grammar && npm run gen:ai-docs && npm run test` on the unmodified branch.

### 1. Joins — `core.cljs`, Joins section after `squeeze-join`

`late*` is defined further down; add `(declare late*)` above the joins (or move the two
functions below `late*` into the TL4b block — either is fine, keep them public).

```clojure
(defn- reset-join* [pp restart?]
  (pattern
   (fn [sp]
     (for [oe (query pp sp)
           :when (:whole oe)                                   ; drop analog outer events
           :let [ws  (:start (:whole oe))
                 off (if restart?
                       ws
                       (rat- ws (int->rat (Math/floor (rat->float ws)))))]
           ie (query (late* off (->pat (:value oe))) (:part oe))
           :let [p (span-intersect (:part ie) (:part oe))]
           :when p]
       (assoc ie
              :whole (when (:whole ie) (span-intersect (:whole ie) (:whole oe)))
              :part  p)))))

(defn reset-join
  "Strudel resetJoin: each outer event restarts the current cycle of its inner pattern
   (the inner pattern is shifted so its cycle start lines up with the event)."
  [pp] (reset-join* pp false))

(defn restart-join
  "Strudel restartJoin: each outer event restarts its inner pattern from cycle 0."
  [pp] (reset-join* pp true))
```

### 2. Pick family — `core.cljs`, new `;; Pick (TL4b)` block after `apply-n`

```clojure
(defn- pick-num [x]
  (cond (number? x) x
        (and (vector? x) (= 2 (count x)) (every? number? x) (not (zero? (second x))))
        (rat->float x)
        :else nil))

(defn- pick-entry
  "K3/K4: the lookup entry selected by index value v, or nil."
  [lookup v mod?]
  (if (vector? lookup)
    (when-let [x (pick-num v)]
      (let [n (count lookup) i (Math/round x)]
        (nth lookup (if mod? (mod i n) (min (dec n) (max 0 i))))))
    (or (get lookup v)
        (when (string? v) (get lookup (keyword v))))))

(defn- pick-pats [lookup ipat mod?]
  (fmap #(or (pick-entry lookup % mod?) silence) (->pat ipat)))
```

Public functions (lookup entries are already patterns — the Lisp layer converts, K5; an
empty lookup returns `silence`):

```clojure
(defn pick             [lookup ipat] (if (empty? lookup) silence (inner-join   (pick-pats lookup ipat false))))
(defn pick-mod         [lookup ipat] (if (empty? lookup) silence (inner-join   (pick-pats lookup ipat true))))
(defn pick-out         [lookup ipat] … outer-join   false)
(defn pick-mod-out     [lookup ipat] … outer-join   true)
(defn pick-restart     [lookup ipat] … restart-join false)
(defn pick-mod-restart [lookup ipat] … restart-join true)
(defn pick-reset       [lookup ipat] … reset-join   false)
(defn pick-mod-reset   [lookup ipat] … reset-join   true)
(defn inhabit          [lookup ipat] … squeeze-join false)
(defn inhabit-mod      [lookup ipat] … squeeze-join true)

(defn- pick-f* [ipat fs pat mod?]
  (if (empty? fs)
    silence
    (inner-join (fmap #(if-let [f (pick-entry fs % mod?)] (f pat) silence) (->pat ipat)))))
(defn pick-f     [ipat fs pat] (pick-f* ipat fs pat false))
(defn pick-mod-f [ipat fs pat] (pick-f* ipat fs pat true))
```

A missing `pick-f` entry gives silence for that slot (consistent with D1; Strudel would
throw). Each public function gets a one-line docstring naming its Strudel counterpart and
join.

### 3. `fix` — `params.cljs`

`ns` adds `[repulse.theory :as theory]`. New `;;; ── Phase TL4b: fix ──` section:

```clojure
(defn- num-of [x]
  (cond (number? x) x
        (and (vector? x) (= 2 (count x)) (every? number? x) (not (zero? (second x))))
        (/ (first x) (second x))
        :else nil))

(defn- rest-val? [v] (or (nil? v) (= v :_) (and (map? v) (= :_ (:note v)))))

(defn- control-view
  "K9: the control map fix matches against, or nil (never matches)."
  [v]
  (when-not (rest-val? v)
    (let [m (cond (map? v) v
                  (or (keyword? v) (number? v) (num-of v)) (to-map v)
                  :else nil)]
      (when m
        (if (contains? m :s)
          m
          (let [n (:note m)
                s (or (:bank m)
                      (when (and (keyword? n) (not (theory/note-keyword? n))) n))]
            (cond-> m s (assoc :s s))))))))

(defn- val-match? [cv ev]
  (let [a (num-of cv) b (num-of ev)]
    (if (and a b) (== a b) (= cv ev))))

(defn controls-match?
  "K10: every [k cv] of cond matches the event value's control view."
  [cond v]
  (if-let [m (control-view v)]
    (every? (fn [[k cv]]
              (let [ev (get m k)]
                (and (some? ev)
                     (if (vector? cv)
                       (boolean (some #(val-match? % ev) cv))
                       (val-match? cv ev)))))
            cond)
    false))

(defn fix
  "Tidal fix: apply f to the events whose controls match cond, keep the rest."
  [f cond pat]
  (core/stack* [(f (core/filter-events #(controls-match? cond %) pat))
                (core/filter-events #(not (controls-match? cond %)) pat)]))
```

### 4. Lisp builtins — new `packages/lisp/src/repulse/lisp/builtins/pick.cljs`

`(ns repulse.lisp.builtins.pick "TL4b pick family, inhabit and fix — thin Lisp wrappers
over repulse.core and repulse.params." (:require [repulse.core :as core] [repulse.params
:as params] [repulse.lisp.util :as u]))`. Copy the private `xf` helper from
`random.cljs`. Merge `(b-pick/make-builtins)` in `eval.cljs` `make-env` right after
`(b-random/make-builtins)` (require `[repulse.lisp.builtins.pick :as b-pick]`).

```clojure
(defn- entry->pat [x]                       ; K5
  (let [v (u/unwrap x)]
    (cond (core/pattern? v) v
          (string? v)       (u/pat-arg x)
          :else             (core/pure v (u/source-of x)))))

(defn- lookup-of [name conv lk]             ; E1, K5
  (let [v (u/unwrap lk)]
    (cond (vector? v) (mapv conv v)
          (map? v)    (into {} (map (fn [[k e]] [(u/unwrap k) (conv e)])) v)
          :else (throw (js/Error. (str name ": the lookup must be a vector [...] or a map {...}. "
                                       "Usage: (" name " lookup index-pattern)"))))))

(defn- lookup-builtin [name core-fn]
  (fn
    ([lk]      (let [l (lookup-of name entry->pat lk)] (fn [i] (core-fn l (u/pat-arg i)))))
    ([lk ipat] (core-fn (lookup-of name entry->pat lk) (u/pat-arg ipat)))))
```

Register `"pick"`, `"pick-mod"`, `"pick-out"`, `"pick-mod-out"`, `"pick-restart"`,
`"pick-mod-restart"`, `"pick-reset"`, `"pick-mod-reset"`, `"inhabit"`, `"inhabit-mod"`
with `lookup-builtin`. Note the curried form applies `u/pat-arg` to the threaded value, so
`(->> "<0 1>" (pick [...]))` parses the string.

`pick-f` / `pick-mod-f`: a converter `fn-entry` that checks `(fn? (u/unwrap e))` (else E2)
and returns `(xf e)`; the lookup check uses the E1 text for functions (K14). Arities
`[ipat fs]` (curried, returns `(fn [p] …)`) and `[ipat fs p]`; `ipat` via `u/pat-arg`, `p`
via `u/unwrap`.

`fix`: arities `[g cond]` (curried) and `[g cond p]`. `g` via `xf`. Condition:

```clojure
(defn- rational-literal? [x]   ; K11: reader rationals are plain [n d] with unwrapped ints
  (and (vector? x) (= 2 (count x)) (not-any? u/sourced? x)
       (every? integer? x) (not (zero? (second x)))))

(defn- fix-cond [c]
  (let [m (u/unwrap c)]
    (when-not (map? m)
      (throw (js/Error. "fix: the condition must be a map like {:s :bd}. Usage: (fix f {:s :bd} pat)")))
    (into {} (map (fn [[k v]]
                    (let [v' (u/unwrap v)]
                      [(u/unwrap k) (if (rational-literal? v')
                                      (/ (first v') (second v'))
                                      (u/deep-unwrap v'))])))
          m)))
```

Inside an any-of vector, elements that are rational literals (`[1/2 1]` →
`[[1 2] SV(1)]`) need no conversion: after `deep-unwrap` the `[1 2]` element is compared
numerically by `val-match?`.

### 5. Tests

- `core_test.cljs`: `tl4b-reset-join`, `tl4b-restart-join`, `tl4b-pick-*`, `tl4b-inhabit*`,
  one `deftest` per DoD bullet. Compare `[value whole-start whole-end part-start part-end]`
  tuples **sorted by part start, then `pr-str` of the value** (stack order is not part of
  the contract). Build index patterns with `seq*`/`cat*`/`pure`.
- `params_test.cljs`: `tl4b-fix-*` (core-level vectors with `(amp 0.2)` etc. as `f`), plus
  `controls-match?` unit cases for every K9/K10 rule.
- `integration_test.cljs`: Lisp-level vectors (outline examples, strings as mini-notation in
  lookups, map lookups with Lisp functions, curried forms in `->>`, K11 rational literal vs
  any-of, errors E1–E3 via `lisp/eval-error?` and message text), and
  `tl4b-documented-examples-evaluate` (every TL4b example in `builtin_meta.edn` evaluates to
  a pattern).
- `e2e/audio-render.spec.ts`: a `// TL4b: pick & fix` block after the TL4 one (DoD).

### 6. Registration (CLAUDE.md Rule 3)

New `BuiltinName` line after TL4's: `"pick" | "pick-mod" | "pick-f" | "pick-mod-f" |
"pick-out" | "pick-mod-out" | "pick-restart" | "pick-mod-restart" | "pick-reset" |
"pick-mod-reset" | "inhabit" | "inhabit-mod" | "fix" |`. Run `npm run gen:grammar`; check
that `pick-mod-restart`, `pick-mod-f` and `inhabit-mod` each highlight as **one** token and
that `pick` inside `pick-out` and a user symbol like `fixed` or `picker` are not
mis-tokenised. Then `completions.js` (`type: "function"`, details as the Function list,
e.g. `"(pick lookup ipat) — choose a pattern from a vector (by index) or map (by name); inner join"`),
`hover.js` `DOCS` (the `pick` entry says K7: "plain values give one onset per cycle — use
pick-out or inhabit for one value per step"; `fix` lists the matching rules and the derived
`:s`), `builtin_meta.edn` (category `"source"` for the 10 lookup functions, `"transform"`
for `pick-f`, `pick-mod-f`, `fix`; `:returns "Pattern"`, `:side-effects []`, ≥ 1 example
each, `:see-also` within the family — plus `choose-in-with` for `pick` and
`inhabit`, `when-pat` and `sometimes-by` for `fix`), then `npm run gen:ai-docs`. Commit grammar, `parser.js`,
`parser.terms.js`, `completions.js`, `hover.js`, `builtin_meta.edn`,
`docs/ai/builtins.json` together.

### 7. Docs

- `docs/USAGE.md`, after the Conditionals subsection:
  - `### Pick — \`pick\` \`pick-out\` \`pick-restart\` \`pick-reset\` \`inhabit\` \`pick-f\``:
    vector vs map lookup, clamp vs `-mod` wrap, strings are mini-notation, a table of the
    five joins with one example each, the K7 note (plain values + `pick` → one onset per
    cycle; use `pick-out`/`inhabit`), missing names are silent.
  - `### \`fix\` — transform only matching events`: AND / any-of / numeric `==` / exact
    keywords (`:AkaiLinn_bd` ≠ `:bd`; `"bd"` ≠ `:bd`), missing key = no match, the derived
    `:s` (bare keyword, `:note`, `:bank`), `1/2` vs `[1 2]` (K11), `{}` matches all.
- `README.md`: rows `| (pick {:a pat :b pat} "<a b>") / (inhabit lookup "a b") | Choose patterns by index or name |`,
  `| (fix (fast 2) {:s :bd} pat) | Transform only matching events |`.
- `docs/STRUDEL-PARITY.md`: TL4 intro sentence → "The `pick` family, `inhabit` and Tidal
  `fix` followed in **TL4b**."; rows ✅ TL4b: `pick` (`pick`; note "Vector clamped, map by
  name; inner join"), `pickmod` (`pick-mod`; "floored mod"), `pickF / pickmodF`
  (`pick-f`, `pick-mod-f`), the `pickmodF` 🔁 row → `pick-mod-f` 🔁 (note "separate name,
  not an option"), `pickOut / pickmodOut` (`pick-out`, `pick-mod-out`),
  `pickRestart / pickReset (+ mod variants)` (`pick-restart`, `pick-mod-restart`,
  `pick-reset`, `pick-mod-reset`), `inhabit / pickSqueeze` (`inhabit`), `pickSqueeze,
  pickmodSqueeze` 🔁 → `inhabit`, `inhabit-mod`, `inhabitmod` 🔁 → `inhabit-mod` (note
  "separate name"); Tidal-only `fix` ✅ TL4b ("condition map; any-of vectors; derived `:s`").
  Recompute the Summary (Randomness & conditionals: 📋 6 → 0, ✅ +6).
- `ROADMAP.md`: implementation-order row `outline` → `full` (if not already), section →
  `✅ *delivered*` with a **Delivered:** list (TL4 style) naming the 13 built-ins, the two
  joins and the new `builtins/pick.cljs`; update the Key additions bullet to the final names.

## Files to change

| File | Change |
|---|---|
| `packages/core/src/repulse/core.cljs` | `reset-join`, `restart-join` (Joins section); TL4b pick block: `pick-num`, `pick-entry`, `pick-pats`, 10 lookup functions, `pick-f`, `pick-mod-f` |
| `packages/core/src/repulse/core_test.cljs` | `tl4b-*` join and pick vectors |
| `packages/core/src/repulse/params.cljs` | require `repulse.theory`; `num-of`, `rest-val?`, `control-view`, `val-match?`, `controls-match?`, `fix` |
| `packages/core/src/repulse/params_test.cljs` | `tl4b-fix-*`, `controls-match?` cases |
| `packages/lisp/src/repulse/lisp/builtins/pick.cljs` | **New** — 13 builtins, errors E1–E3 |
| `packages/lisp/src/repulse/lisp/eval.cljs` | Require + merge `b-pick` after `b-random` |
| `packages/lisp/src/repulse/lisp/integration_test.cljs` | Lisp vectors, curried forms, K11, errors, `tl4b-documented-examples-evaluate` |
| `e2e/audio-render.spec.ts` | 4 onset-count tests |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` + `parser.js` + `parser.terms.js` | 13 names (`npm run gen:grammar`) |
| `app/src/repulse/lisp-lang/completions.js`, `hover.js` | 13 entries |
| `app/src/repulse/content/builtin_meta.edn` + `docs/ai/builtins.json` | 13 entries (`npm run gen:ai-docs`) |
| `docs/USAGE.md`, `README.md`, `docs/STRUDEL-PARITY.md`, `ROADMAP.md` | Step 7 |
| `PROMPTS/PHASE-TL4b.md` | Unchanged during implementation (already full) |

## Definition of done

Notation: `v [s,e)` = an event with value `v` whose whole and part are both `[s,e)`;
`v w[s,e) p[s',e')` = whole and part differ (a fragment unless `s = s'`). "Cycles a–b" =
`(query pat (cycle-span c))` for each `c`, concatenated. All times are exact rationals.
`S2` = `(slow 2 (seq* [:a :b :c :d]))` (`a [0,1/2) b [1/2,1) c [1,3/2) d [3/2,2)`).
Every vector below was computed by running a reference implementation of steps 1–3 against
the current `inner-join`/`outer-join`/`squeeze-join`, `late`, `seq*`, `cat*` and `amp`.

**Joins (core)**
- [ ] `reset-join` / `restart-join` vs `inner-join` on `(seq* [S2 S2])`, cycles 0–1:
      inner → `a [0,1/2)`, `b [1/2,1)`, `c [1,3/2)`, `d [3/2,2)`;
      reset → `a [0,1/2)`, `a [1/2,1)`, `c [1,3/2)`, `c [3/2,2)`;
      restart → `a [0,1/2)`, `a [1/2,1)`, `a [1,3/2)`, `a [3/2,2)`.
      `(pure S2)`, cycles 0–2: reset → `a b | c d | a b` (halves); restart → `a b | a b | a b`.
      Querying `[0,2)` in one span gives the same events as cycle by cycle.
- [ ] Whole intersection: `(reset-join (seq* [(pure :x) (pure :x)]))` cycle 0 →
      `x [0,1/2)`, `x [1/2,1)` (two onsets), while `inner-join` of the same gives
      `x w[0,1) p[0,1/2)`, `x w[0,1) p[1/2,1)` (one onset). Same for `restart-join`.
- [ ] Analog: `(reset-join (fmap (fn [_] (pure :x)) sine))` and the `restart-join` of it →
      no events; `(reset-join (pure saw))` cycle 0 → one event, value `0`, `:whole nil`,
      part `[0,1)`. `:source`: `(reset-join (pure (pure :x {:from 1 :to 3})))` events carry
      `{:from 1 :to 3}`.

**Pick (inner join)**
- [ ] Map + name pattern: `(pick {:a (seq* [:bd :sd]) :b (seq* [:hh :hh :hh])} (cat* [(pure :a) (pure :b) (pure :c)]))`
      (= mini `"<a b c>"`), cycles 0–2 → `bd [0,1/2)`,
      `sd [1/2,1)`, `hh [1,4/3)`, `hh [4/3,5/3)`, `hh [5/3,2)`, nothing in cycle 2 (`:c`
      missing → silence). String values: `(pick {:a (seq* [:bd :sd]) :b (pure :hh)} (seq* ["a" :b]))`
      cycle 0 → `bd [0,1/2)`, `hh w[0,1) p[1/2,1)`. Lisp:
      `(pick {:a (seq :bd :sd) :b (seq :hh :hh :hh)} "<a b>")` cycles 0–1 → the first five
      events above.
- [ ] Vector clamping: `(pick [(pure :a) (pure :b)] (cat* [(pure -1) (pure 0.4) (pure 0.5) (pure 7)]))`
      cycles 0–3 → `a [0,1)`, `a [1,2)`, `b [2,3)`, `b [3,4)`; with index values
      `-0.5, 1.5, [1 2], :x` → `a [0,1)`, `b [1,2)`, `b [2,3)`, nothing in cycle 3.
- [ ] K7 fragments: `(pick [(pure :a) (pure :b)] (seq* [0 1]))` cycle 0 →
      `a w[0,1) p[0,1/2)`, `b w[0,1) p[1/2,1)`; `query-onsets` → only `a`.
- [ ] `pick-mod` wrapping: `(pick-mod [(pure :a) (pure :b) (pure :c)] (cat* [(pure 3) (pure -1) (pure 4) (pure 7)]))`
      cycles 0–3 → `a [0,1)`, `c [1,2)`, `b [2,3)`, `b [3,4)`. Outline example
      `(pick-mod [(seq :bd :sd) (seq :hh :hh)] "0 1 2")` cycle 0 →
      `bd w[0,1/2) p[0,1/3)`, `hh w[0,1/2) p[1/3,1/2)`, `hh w[1/2,1) p[1/2,2/3)`,
      `sd w[1/2,1) p[2/3,1)`; onsets `bd [0,1/2)`, `hh [1/2,1)`. Map lookups ignore `-mod`:
      `(pick-mod {:a (pure :x)} (pure :a))` ≡ `(pick {:a (pure :x)} (pure :a))`.

**Pick-f**
- [ ] `(pick-f (cat* [(pure 0) (pure 1) (pure 5)]) [#(fast 2 %) rev] (seq* [:a :b]))` cycles 0–2 →
      `a [0,1/4)`, `b [1/4,1/2)`, `a [1/2,3/4)`, `b [3/4,1)` | `b [1,3/2)`, `a [3/2,2)` |
      `b [2,5/2)`, `a [5/2,3)` (5 clamps to `rev`). `(pick-f (seq* [0 1]) [#(fast 2 %) rev] (seq* [:a :b]))`
      cycle 0 → `a [0,1/4)`, `b [1/4,1/2)`, `a [1/2,1)`. `pick-mod-f` with index
      `(cat* [(pure 2) (pure -1)])` cycles 0–1 → `a b a b` at quarters | `b [1,3/2)`, `a [3/2,2)`.
      Map: `(pick-f (cat* [(pure :fast) (pure :rev) (pure :nope)]) {:fast #(fast 2 %) :rev rev} (seq* [:a :b]))`
      cycles 0–2 → as the first vector for cycles 0–1, nothing in cycle 2.
      Lisp: `(pick-f "<0 1>" [(fast 2) rev] (seq :a :b))` cycles 0–1 → `a b a b` at quarters | `b [1,3/2)`, `a [3/2,2)`.

**Pick-out, restart, reset**
- [ ] `pick-out`: `(pick-out [(pure :a) (pure :b)] (seq* [0 1 1 0]))` cycle 0 → `a [0,1/4)`,
      `b [1/4,1/2)`, `b [1/2,3/4)`, `a [3/4,1)` (4 onsets);
      `(pick-out [(seq* [:a :b]) (pure :c)] (seq* [0 0 1 0]))` cycle 0 → `a [0,1/4)`,
      `a [1/4,1/2)`, `c [1/2,3/4)`, `b [3/4,1)`; `(pick-mod-out [(pure :a) (pure :b)] (seq* [0 1 2 3]))`
      → `a b a b` at quarters.
- [ ] Restart vs reset vs pick, lookup `L = [S2 (pure :x)]`, index `(seq* [1 0])`, cycles 0–1:
      `pick` → `x w[0,1) p[0,1/2)`, `b [1/2,1)`, `x w[1,2) p[1,3/2)`, `d [3/2,2)`;
      `pick-reset` → `x [0,1/2)`, `a [1/2,1)`, `x [1,3/2)`, `c [3/2,2)`;
      `pick-restart` → `x [0,1/2)`, `a [1/2,1)`, `x [1,3/2)`, `a [3/2,2)`.
      `pick-mod-reset` / `pick-mod-restart` with index `(seq* [3 2])` give the same two vectors.

**Inhabit (squeeze)**
- [ ] `(inhabit {:a (seq* [:bd :sd]) :b (pure :hh)} (seq* [:a :b]))` cycles 0–1 → `bd [0,1/4)`,
      `sd [1/4,1/2)`, `hh [1/2,1)`, `bd [1,5/4)`, `sd [5/4,3/2)`, `hh [3/2,2)`;
      `(inhabit-mod [(seq* [:x :y]) (pure :z)] (seq* [0 1 2]))` cycle 0 → `x [0,1/6)`,
      `y [1/6,1/3)`, `z [1/3,2/3)`, `x [2/3,5/6)`, `y [5/6,1)`;
      `(inhabit {:a (seq* [:bd :sd])} (seq* [:a :z]))` cycle 0 → `bd [0,1/4)`, `sd [1/4,1/2)` only.
      Lisp outline example `(inhabit {:a (seq :bd :sd) :b (pure :hh)} "a b")` → the first vector.

**Fix** (`params_test.cljs`; `f` = `(amp 0.2)` unless stated)
- [ ] Numeric `==`: `(fix (amp 0.2) {:amp 1} (amp (seq* [1.0 0.5]) (seq* [:bd :sd])))` cycle 0 →
      `{:note :bd :amp 0.2} [0,1/2)`, `{:note :sd :amp 0.5} [1/2,1)` (`1` matches `1.0`);
      rational event value: `(fix (amp 0.9) {:amp 0.5} (amp [1 2] (pure :bd)))` →
      `{:note :bd :amp 0.9} [0,1)`; `{:note 60}` matches `(seq* [60.0])`.
- [ ] Any-of: `(fix (amp 0.2) {:s [:bd :sd]} (seq* [:bd :hh :sd :cp]))` cycle 0 →
      `{:note :bd :amp 0.2} [0,1/4)`, `:hh [1/4,1/2)`, `{:note :sd :amp 0.2} [1/2,3/4)`,
      `:cp [3/4,1)` (unmatched events unchanged — still bare keywords).
- [ ] Bank prefix is not `:bd`: `(fix (amp 0.2) {:s :bd} (seq* [:AkaiLinn_bd :bd]))` →
      `:AkaiLinn_bd [0,1/2)`, `{:note :bd :amp 0.2} [1/2,1)`.
- [ ] Missing key: `(fix (amp 0.2) {:pan 0} (stack* [(seq* [:bd :sd]) (pan 0 (seq* [:hh :hh]))]))` →
      `{:note :hh :pan 0 :amp 0.2} [0,1/2)`, `{:note :hh :pan 0 :amp 0.2} [1/2,1)`,
      `:bd [0,1/2)`, `:sd [1/2,1)` (bare `:bd` has no `:pan` → no match).
- [ ] AND + derived `:s`: `(fix (amp 0.2) {:s :bd :n 2} (seq* [{:bank :bd :n 2} {:bank :bd :n 0}]))`
      (what Lisp `(seq (sound :bd 2) (sound :bd 0))` produces) →
      `{:bank :bd :n 2 :amp 0.2} [0,1/2)`, `{:bank :bd :n 0} [1/2,1)`; Lisp
      `(fix (amp 0.2) {:s :bd :n 2} (~ "bd:2 bd"))` → `{:bank :bd :n 2 :amp 0.2} [0,1/2)`,
      `:bd [1/2,1)`. Notes: `{:note :c4}` matches `:c4`; `{:s :c4}` matches neither `:c4`
      nor `:bd`. Rests: `(fix (amp 0.2) {} (seq* [:bd :_ 3]))` → `{:note :bd :amp 0.2} [0,1/3)`,
      `:_ [1/3,2/3)`, `{:note 3 :amp 0.2} [2/3,1)`. Exact strings: `{:s "bd"}` does not match `:bd`.
- [ ] Structure-changing `f`: `(fix #(fast 2 %) {:s :bd} (seq* [:hh :bd]))` cycle 0 →
      `:hh [0,1/2)`, `:bd [1/4,1/2)`, `:bd [3/4,1)`; Lisp `(fix (fast 2) {:s :bd} (seq :bd :hh))`
      cycles 0–1 → `bd [0,1/4)`, `bd [1/2,3/4)`, `hh [1/2,1)`, `bd [1,5/4)`, `bd [3/2,7/4)`,
      `hh [3/2,2)`; curried `(->> (seq :bd :hh) (fix (fast 2) {:s :bd}))` gives the same.
- [ ] K11 (Lisp): `(fix (amp 0.9) {:amp 1/2} (amp 0.5 (seq :bd)))` → `{:note :bd :amp 0.9}`;
      `(fix (amp 0.9) {:amp [1 2]} (amp (seq 1 0.5 2) (seq :a :b :c)))` → `:a` and `:c`
      changed to amp 0.9, `:b` unchanged; `(fix (amp 0.9) {:amp [1/2 1]} (amp (seq 1 0.5 2) (seq :a :b :c)))`
      → `:a` and `:b` changed, `:c` unchanged.

**Lisp surface, errors, registration, docs, verification**
- [ ] Lisp lookups: strings are mini-notation — `(pick ["bd sd" "hh*3"] "<0 1>")` cycles 0–1 →
      `bd [0,1/2)`, `sd [1/2,1)`, `hh [1,4/3)`, `hh [4/3,5/3)`, `hh [5/3,2)`, and the `bd`/`sd`
      events carry the string's token sources; keyword entries carry their literal's source;
      every name works curried in `->>` (`(->> "<a b>" (pick {:a (seq :bd :sd) :b (seq :hh :hh)}))`
      evaluates to the same events as the uncurried form).
- [ ] Errors (exact text, K14): `(pick "<0 1>" [:bd :sd])` → E1 `pick: the lookup must be a vector [...] or a map {...}. Usage: (pick lookup index-pattern)`;
      `(inhabit 3 "a")` → E1 with `inhabit`; `(pick-f "<0 1>" rev (seq :a))` → E1 function
      variant; `(pick-f "<0 1>" [rev :bd] (seq :a))` → E2 `pick-f: every lookup entry must be a function, e.g. (fast 2) or rev`;
      `(fix rev :bd (seq :bd))` → E3. All surface as eval errors (`lisp/eval-error?`), not
      JS exceptions; empty lookups `(pick [] "0")`, `(pick-f "0" [] (seq :a))` → silence, no error.
- [ ] Playwright (`e2e/audio-render.spec.ts`, TL4b block): `(pick-out [:bd :sd :hh :sd] "0 1 2 3")` → 4 onsets;
      `(inhabit {:a (seq :bd :sd) :b :hh} "a b")` → 3 onsets; `(fix (fast 2) {:s :bd} (seq :hh :bd))`
      → 3 onsets; `(pick [:bd :sd] "0 1")` → 1 onset (K7).
- [ ] Rule 3: the 13 names in `BuiltinName` (+ `npm run gen:grammar`), `completions.js`,
      `hover.js`, `builtin_meta.edn` (+ `npm run gen:ai-docs`); both generators produce no
      diff after the commit; `tl4b-documented-examples-evaluate` passes.
- [ ] Docs: USAGE "Pick" and "`fix`" subsections (step 7, incl. the K7 and K11 notes); README
      rows; STRUDEL-PARITY rows and Summary recomputed (no 📋 TL4b row left); ROADMAP
      implementation-order row `full` and section `✅ *delivered*` with **Delivered:**.
      `CLAUDE.md` needs no change (no phase table in this branch).
- [ ] No regressions: all TL1–TL5 tests pass unchanged (`inner-join`, `outer-join`,
      `squeeze-join`, `when-pat`, `choose-in-with`, `amp`/`pan` maps); `npm run test:all`
      and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a `## Test protocol` section (CLAUDE.md Rule 5): one
      checkbox for **each** of the 13 new built-ins (pasteable snippet + concrete expected
      sound/events, values taken from `window.__REPULSE_TEST__.evalToEvents(code, cycles)`
      on `test-harness.html` via a scratch Playwright spec deleted before committing), one
      for each error message E1, E2, E3 (exact text from the code), the K7 "`pick` with plain
      values plays once per cycle → `pick-out`" case, and at least two regressions
      (`when-pat "<0 1>" rev`, `(choose-in [(seq :bd :sd) (seq :hh :hh)])`, `(amp 0.5 …)`
      event maps).

## What NOT to do

- No change to TL4's random functions, seeds or mini-notation; no new randomness — these
  functions are purely selective.
- Don't change `inner-join`, `outer-join`, `squeeze-join` or any TL1–TL5 semantics; the new
  joins are additions. Don't expose `reset-join`/`restart-join` (or the other joins) as Lisp
  built-ins.
- No `s`, `n` or `note` control builders and no new per-event keys — that is **TL7**; `fix`
  only reads what exists (plus the derived `:s`, K9). No `contrast`, `fix-range`,
  `unfix` or other Tidal `contrast*` relatives; no patterned condition maps.
- No Strudel argument-swap compatibility in `pick`, no `:mod` option flag, no `pick-squeeze`
  alias (K1, K2) and no change to the bank-prefix mechanism.
- Leave the ❓ rows (`cyclesPer`/`per`/`perx`, `open`/`close`) and the ⛔ `whenKey`/`keyDown`
  row untouched.
