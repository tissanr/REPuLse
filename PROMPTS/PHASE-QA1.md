# Phase QA1 — Robust Language & Pattern Engine

> **Spec status:** full
> **Automation:** 🤖 auto — every change is an error message, a limit or exact event
> timing. All of it is checked by `cljs.test`, the TEST1 harness and one Playwright test
> on scheduler counters. No sound changes, so no listen check is needed.

**Milestone:** none — quality track
**Depends on:** none

## Goal

Code that evaluates without an error can still break playback. Some inputs hang the tab,
and some mistakes are silently turned into a different sound. Found in a code review of
`packages/core`, `packages/lisp` and the scheduler. After this phase a mistake gives an
eval error with a position, and one broken track never stops the others.

```lisp
(track :kick (seq :bd :bd :bd :bd))
(track :hats (fast "<1 1 x>" (seq :hh)))   ; x is not a number
```

```
before                                    after
──────                                    ─────
cycle 2 throws in schedule-cycle!;        :kick plays on. :hats is skipped in cycles
tick! retries every 25 ms, :cycle never   2, 5, 8 … and plays in the others. The output
advances, every track goes silent         line shows once:
                                          Error in track :hats: Expected a number, got :x
```

```lisp
(run 100000000)          ; before: tab hangs      after: run: n = 100000000 exceeds the limit of 1024
(seq "bd ] sd")          ; before: plays only bd  after: Unexpected "]" in mini-notation at position 3
(def x 5) (defn f [x] x)
(f)                      ; before: 5              after: f expects 1 argument, got 0
(chord :majr :c4)        ; before: C major        after: chord: unknown chord :majr — did you mean :major?
```

## Background

### Scheduler — `app/src/repulse/audio.cljs`

- `schedule-cycle!` (~line 1102) queries every free-running track with
  `(core/query-onsets pattern sp)` inside a `doseq`, with no `try`. In song mode it calls
  `song/song-onsets` (`packages/core/src/repulse/song.cljs` ~line 385), which also queries
  every track with no `try`.
- `tick!` (~line 1196) calls `schedule-cycle!` and then `(swap! scheduler-state update
  :cycle inc)`. A throw skips the increment, so the same cycle is retried every 25 ms and
  every track goes silent.
- `scheduler-state` already holds a callback, `:on-song-end`, that `stop!` keeps (it only
  `assoc`s its own keys). It is set in `eval_orchestrator/init!`.
- Exports share the query code: `offline_render/job-events` (`:cycles` and `:song`) and
  `midi_export/cycles->midi` / `song->midi`.

### Evaluation — `app/src/repulse/eval_orchestrator.cljs`

`evaluate*` runs `lisp/eval-string`. On an eval error it calls `report-eval-error!` and
returns (WS5 decision 3: an error retires nothing). Otherwise `song-transport/activate-buffer!`
starts `@song-transport/pending-tracks` (`[[kw pat] …]`, sources in `pending-track-srcs`),
and a pattern result is played as `:_`. Nothing queries a track before it is scheduled.
`sound_check.cljs` already has pure per-track checks with tests (`sound_check_test.cljs`).

### Pattern engine — `packages/core/src/repulse/core.cljs`

- No count limits: `run*` (~line 612), `euclid-bools` (~777, used by `euclidean*` and
  `euclid-legato*`), `ply*` (~856), `ply-with*`, `ply-for-each*`, `segment*`, `chunk*`
  (~958), `chunk-into*` (~1181), `apply-n` (~1508, `(nth (iterate f p) n)`), `echo-with*`
  (~1622, recursive) and `params/echo*` (`params.cljs` ~154).
- `every*` (~407) does `(mod cycle n)` with raw `n`: `0` gives NaN and `[1 2]` is a
  vector. It also calls `(transform pat)` on every query. So do `last-of*` (`(f pat)`),
  `chunk*` (`within*` per cycle) and `chunk-into*`. `whenmod*` builds `fp` once, which is
  the right way.
- `arrange*` (~421) treats durations as JS numbers (`(+ prev dur)`, `(mod g-cycle total)`).
  The builtin (`packages/lisp/src/repulse/lisp/builtins/arrangement.cljs`) passes
  `(u/unwrap dur)`, so `3/2` arrives as `[3 2]` and `(+ 0 [3 2])` is string concatenation.
- `->int` (~690) and `->rat` exist and are the helpers to use.
- `gcd rat< rat<= rat> rat>= rat-min rat-max rat-neg span-intersect patternify2
  patternify3 polymeter-pats silence` have no test in any `*_test.cljs` (checked with grep).

### Mini-notation — `packages/lisp/src/repulse/lisp/mini.cljs`

- `parse` does `[ast _] (parse-layers tokens 0)`. The end position is dropped, so a stray
  `]`, `>`, `}` or `)` ends parsing silently.
- `parse-suffixes`: `*` and `@` use `(int (parse-number-text …))`, so fractions are cut off.
- `parse-number-text` is `js/parseFloat`, so `"808bd"` → `808` and `"1x"` → `1`.
- `parse-error` carries no position. `parse-element` falls back to
  `(str "Unexpected token: " (pr-str tok))`, which prints the token map. Only atom, `?` and
  `!` tokens record `:str-from`.
- `u/pat-arg` (`util.cljs`) and the `"~"` builtin pass `base-offset` (editor position after
  the opening quote) to `mini/parse`.

### Evaluator — `packages/lisp/src/repulse/lisp/eval.cljs`

- `"->>"` and `"->"` (lines ~240–279) are two near-identical `reduce` blocks. They call
  `f` directly: no `try` (no `:from`, so no highlight), no macro expansion, no keyword
  lookup, and their own `not-a-function` throws have no position.
- `"recur"` returns `recur-sentinel`, a plain map. Outside `loop` the map leaks out as the
  value of the form.
- `make-closure` uses `(zipmap (map str params) args)`. A missing argument stays unbound,
  so the symbol falls through to `env` and then to `*defs*`. Extra arguments are dropped and
  `&` is bound as a normal parameter. `defmacro` has the same binding code.
- `not-a-function-msg` ends with `(str (pr-str head-form) " is not a function")`, which
  prints a `SourcedVal` record. `form-str` (line ~53) already prints forms properly.
- `levenshtein` / `typo-hint` live here. Builtin namespaces cannot require `eval.cljs`
  (circular), but they can require `util.cljs`.
- `"map"` / `"filter"` in `builtins/collection.cljs` return lazy seqs. The user function
  then runs after the call's `try` has returned.

### Reader — `packages/lisp/src/repulse/lisp/reader.cljs`

`read-number` (line ~57): `1/0` → `[1 0]`; `1.2.3` → `js/parseFloat` → `1.2`; the `bpm`
check does `(subs (:src r) @(:pos r))` per number (O(N²)). `read-string*`: a trailing
backslash calls `advance` past the end → bare `Index out of bounds`.

### Theory — `packages/core/src/repulse/theory.cljs`, `builtins/music.cljs`

`note->midi :zz` destructures `nil` and returns 60. `chord` uses
`(get chord-intervals chord-kw [1 3 5])`, a silent fallback to major. `resolve-intervals` maps
unknown names to 0. `scale-semis` accepts `[]`, and `degree->midi` then divides by 0.
`scale-name-arg` (music.cljs) errors on an unknown scale but lists all names and has no hint.
User interval strings are already checked (`transpose: invalid interval "3X"`).

## Decisions

1. **Query errors are per track.** `schedule-cycle!` wraps each track (query, realisation
   with `vec`, scheduling) in `try`. A failing track is skipped for that cycle only. The
   cycle always advances. Song mode does the same per track.
2. **Report once.** A query error is reported once per `[track message]` through a new
   `:on-query-error` callback in `scheduler-state`. The output line shows
   `Error in track :hats: <message>` (`Error in pattern: <message>` for `:_`), status
   `:error`, plus one `console.warn`. The set is cleared for a track when `play-track!`
   replaces it, and cleared completely by `stop!`.
3. **Probe at eval.** After a successful eval, before `activate-buffer!`, every pending track
   and a pattern result (`:_`) is queried for cycle 0 and fully realised. The first throw
   becomes a normal eval error, `track :hats fails at cycle 0: <message>` (`pattern fails at
   cycle 0: <message>` for `:_`). It is located at the error's own `:from/:to` if it has
   one, else at the track's source. Pending state is reset, and nothing is activated or
   retired (WS5 decision 3).
4. **Exports use the same isolation.** A failing track is left out of that cycle and
   reported through the same callback. The export goes on.
5. **Count limits.** Steps and repeats are limited to **1024**: `run`, `euclidean` /
   `euclid-legato` / `euclid-legato-rot` steps, `ply`, `ply-with`, `ply-for-each`,
   `segment`, `chunk` / `chunk-back` / `fast-chunk` / `chunk-into` / `chunk-back-into`, and
   the mini-notation `!n`. Layers and iterations are limited to **64**: `apply-n`,
   `echo` / `stut` times, `echo-with` / `stutter` n. The message is exactly
   `<fn>: <arg> = <value> exceeds the limit of <limit>`, with `<value>` as an integer after
   `->int` (or the rounded-up float for `ply`/`segment`). The check is in the core function,
   so literal arguments fail at eval (the evaluator adds the position). Patterned arguments
   fail at query time and are handled by decisions 1–3.
6. **Mini-notation.** Leftover tokens after the top level are an error. `*` and `@` take
   fractions and rationals (`"hh*0.5"`, `"bd@1.5"`, `"bd*3/2"`), passed exact to
   `core/fast` / `timecat`. `!n` and `:n` stay whole numbers. A token is a number only if
   the whole token matches `-?\d+(\.\d+)?`, `-?\.\d+` or the rational form. Anything else is
   an atom (`"808bd"` → `:808bd`, `"1e3"` → `:1e3`). A non-number after `*`, `@` or `%` is
   an error. Every token records `:text`, `:str-from` and `:str-to`.
7. **Mini error format.** `<text> in mini-notation at position <n>`, 0-based within the
   string: `Unexpected "]" in mini-notation at position 3`,
   `Expected ] in mini-notation at position 4` (end of input: position = string length).
   The ex-data keeps `:type :mini-parse-error` and adds `:str-from :str-to`. When
   `base-offset` is given, it also adds `:from :to`, so only the token is underlined (at end
   of input, the last character).
8. **Threading is a syntactic rewrite.** `(->> x (f a) g)` becomes the form `(g (f a x))`,
   and `->` puts the value first, like Clojure. The new list keeps the step form's
   `{:source …}` metadata (a bare symbol step gets the symbol's source). The result goes
   through `eval-form`, so try/highlight, macros and keyword steps all work, and the two
   blocks become one `thread-form` helper. `(->>)` / `(->)` with no argument is an eval
   error `->> needs at least one argument`. Side effect: `x` is now evaluated after the
   step's head and arguments, as in Clojure.
9. **Closures check arity.** Parameters `[a b & rest]` are supported (`rest` = `nil` when
   empty, else a seq). A wrong count is an eval error: `f expects 1 argument, got 0`,
   `f expects 2 arguments, got 3`, `f expects at least 1 argument, got 0`. The name comes
   from `defn`, otherwise `fn`. Every parameter is always bound, so a global is never read
   in its place. `defmacro` shares the binding helper.
10. **`recur` outside `loop`** is an eval error `recur outside loop`, located at the form.
    `loop` adds `:*in-loop* true` to the env of its body. `make-closure` removes that key
    from the env it captures, because a `fn` body is not a recur target.
11. **Reader errors** (type `:read-error`, range = the whole literal):
    `Invalid rational 1/0 — the denominator is 0`, `Invalid number 1.2.3`. A trailing
    backslash gives the existing `Unterminated string — missing closing "` at the opening
    quote. The bpm check becomes `(.startsWith (:src r) "bpm" @(:pos r))`.
12. **Theory names fail loudly** and suggest a fix with `typo-hint`:
    `scale: unknown scale :majr — did you mean :major?` and
    `chord: unknown chord :majr — did you mean :major?`. With no hint, the message ends with
    ` — known: <sorted list>`. `scale: unknown root :zz — expected a note like :c4, :eb3 or
    :fs` (no hint, because one- and two-letter names give meaningless suggestions; same
    text for `chord`). `scale: the interval vector is empty`. Patterned names and roots are
    checked at query time by the same code. `resolve-intervals` throws on unknown names
    (internal tables only, no user-visible change).
13. **Rational `arrange`.** Durations go through `core/->rat`. A duration ≤ 0 is an eval
    error `arrange: duration must be positive, got <d>`. Results for integer durations do
    not change.
14. **`every` / `first-of`** normalise `n` with `->int`. `n < 1` is an error
    `every: n must be at least 1, got <n>` (rationals printed as `1/2`). `last-of`,
    `chunk*` and `chunk-into*` keep their `n < 1 → pat` behaviour. All four build their
    transformed patterns once, up front.

## Implementation

### 1. Scheduler isolation — `audio.cljs`, `song.cljs`, exports

- `audio.cljs`: add `(defonce ^:private reported-query-errors (atom #{}))` and
  `(defn report-query-error! [track e])`. It dedupes on `[track (ex-message e)]`, warns on
  the console and calls `(:on-query-error @scheduler-state)` with the formatted message
  (decision 2). Clear entries for the track in `play-track!`, and all of them in `stop!`.
- Add `(defonce scheduled-counts (atom {}))` (track → onsets scheduled). Increment it in
  `schedule-cycle!` and reset it in `stop!`. It is a test handle.
- `schedule-cycle!` free-running branch:
  `(try (doseq [ev (vec (core/query-onsets pattern sp))] …) (catch :default e (report-query-error! track-name e)))`
  per track.
- `song/song-onsets`: add a 5-arity `[song tracks muted bar on-error]`. With `on-error`,
  each track's `(vec (core/query-onsets pat sp))` runs in a `try`, and a failing track adds
  no events. The 4-arity keeps today's behaviour (core stays free of app concerns).
  `schedule-cycle!` passes `report-query-error!`.
- `offline_render/job-events`: add a 2-arity `[job on-error]` with the same per-track
  `try` in both branches. The render call site (~line 299) passes
  `audio/report-query-error!`. `midi_export/cycles->midi` / `song->midi`: optional
  `:on-error` key, same treatment, passed by their caller in `env/builtins/midi.cljs`.
- `eval_orchestrator/init!`: `(swap! audio/scheduler-state assoc :on-query-error
  (fn [msg] (set-output!-fn msg :error)))`.
- `app.cljs` `__REPULSE_APP__`: add
  `:onsetCount (fn [track] (get @audio/scheduled-counts (keyword track) 0))`.

### 2. Eval-time probe — `sound_check.cljs` + `eval_orchestrator.cljs`

Add a pure `sound-check/probe-error [tracks]` (`tracks` = `[[kw pat] …]`). It returns `nil`
or `{:track kw :message msg :from f :to t}`, with `:from/:to` only when the ex-data has
them. In `evaluate*`, right after the `eval-error?` branch, probe
`(cond-> @song-transport/pending-tracks (core/pattern? val) (conj [:_ val]))` (also a
`(map? val)` event result wrapped in `core/pure`). On an error: build the message (decision
3), take the source from the error or `(get @song-transport/pending-track-srcs track)`, call
`song-transport/reset-pending!`, then `report-eval-error!` with
`{:message … :source {:from :to}}`, and return `{:ok? false :error msg}`.

Existing `try`s around queries return lazy seqs, so a throw happens after the `try` has
returned. A track that fails only at cycle 2 would then break `evaluate*` (8-cycle orbit
scan) or the timeline render. Realise inside the `try` with `vec` in these places: the
`query-fn` in `report-orbit-warnings!`, `(try (sound-keywords %) …)` in
`sound-check/unknown-sounds`, `ui/timeline.cljs` `track-events`, and the tween probe in
`audio.cljs` (~line 929).

### 3. Count limits — `core.cljs`, `params.cljs`, `mini.cljs`

Add to `core.cljs`:

```clojure
(def max-steps 1024)
(def max-layers 64)
(defn check-count!
  "Throw an :eval-error when n exceeds limit; returns n."
  [fname arg n limit] …)   ; "run: n = 1025 exceeds the limit of 1024"
```

Call it in `run*` (`"run" "n"`), `euclidean*` / `euclid-legato*` (`"n"`, on the step
count before `euclid-bools`), `ply*` / `ply-with*` / `ply-for-each*` / `segment*` (`"n"`,
on `(Math/ceil (rat->float (->rat n)))`), `chunk*` / `chunk-into*` (add an `fname`
parameter, passed by each public fn), `apply-n` (`"n"`, 64), `echo-with*` (add `fname`,
`stutter` passes `"stutter"`, 64) and `params/echo*` (`"echo"` / `"stut"`, `"times"`, 64).
In `mini.cljs` `parse-sequence`, a `!n` with n > 1024 throws a mini parse error
`! count = 100000000 exceeds the limit of 1024 in mini-notation at position 2`.

### 4. Engine fixes — `core.cljs`

- `every*`: `(let [n (->int n)] (when (< n 1) (throw …)) (let [tp (transform pat)] …))`.
- `last-of*`: build `(f pat)` once. `chunk*` / `chunk-into*`: precompute
  `(mapv variant (range n))` and look up `(nth variants (idx-fn c n))` per cycle.
- `arrange*`: the timeline holds rational `:from/:to`, and `total` is a rational. Keep the
  `per-cycle` wrapper. Inside a cycle piece, find the loop index
  `l = floor(start / total)` and walk every section whose window
  `[l·total + from, l·total + to)` (also `l+1` when the piece crosses the loop end)
  intersects the piece. Query the section's pattern on the intersection shifted by
  `-(l·total + from)`, and shift the events back. The arrangement builtin passes raw
  durations (it already does). `play-scenes` keeps passing `1`.

### 5. Mini-notation — `mini.cljs`

Record `:text`, `:str-from` and `:str-to` on every token in `tokenise`. Change `parse-error`
to `[msg tok-or-pos]`, which adds `:str-from` / `:str-to`. Replace `parse-number-text` with a
strict version (decision 6). `:repeat :times` and `:weight` keep the parsed number, which
can be fractional or `[n d]`. In `parse`, check that the returned position equals
`(count tokens)`, else throw `Unexpected "<text>"`. Wrap the body in a `try` that adds
`:from/:to` from `base-offset` (decision 7). Change `parse-element`'s fallback to
`Unexpected "<text>"`.

### 6. Evaluator & reader — `eval.cljs`, `util.cljs`, `collection.cljs`, `reader.cljs`

- Move `levenshtein` and `typo-hint` unchanged into `util.cljs`. Keep
  `(def levenshtein util/levenshtein)` and `(def typo-hint util/typo-hint)` in `eval.cljs`
  (the app layer uses `leval/…`).
- Add `thread-form [kind steps]` (decision 8). `"->"` and `"->>"` both become
  `(eval-form (thread-form … tail) env)`.
- Add `bind-params [fname params args]` → the local binding map, which throws decision 9's
  messages. Use it in `make-closure [params body env fname]` and in `defmacro`. `defn`
  passes `(str name-sym)`.
- Decision 10 in `"loop"` / `"recur"` / `make-closure`.
- `not-a-function-msg`: `(str (form-str head-form) " is not a function")`.
- `collection.cljs`: `"map"` → `(doall (map …))`, `"filter"` → `(doall (filter …))`.
- `reader.cljs`: decision 11. Record `start` at the top of `read-number` for the range.

### 7. Theory — `theory.cljs`, `music.cljs`

Add `theory/chord-names` (sorted keys of `chord-intervals`). In `music.cljs`, validate a
keyword scale name, chord keyword and root at eval time (`theory/note-keyword?`) with
`u/typo-hint` on the names without the colon (decision 12). In `theory.cljs`,
`scale*` / `chord` throw the same messages for patterned values. Throw on an empty vector
in `scale-semis` and on an unknown name in `resolve-intervals`.

### 8. Tests

- `core_test.cljs`: one `deftest` each for the 13 untested functions (e.g. `(c/gcd 12 18)`
  → 6, `(c/rat< [1 3] [1 2])`, `(c/rat-neg [1 2])` → `[-1 2]`, `span-intersect` of disjoint
  spans → `nil`, `patternify2` / `patternify3` with scalars and with patterns,
  `polymeter-pats` with `nil` steps and with `0`-count layers, `(c/query c/silence …)` →
  empty). Add the caps (1024 passes, 1025 throws with the exact text), `every` with 0 and
  `[1 2]`, and rational `arrange*` (Definition of done).
- `song_test.cljs`: `song-onsets` with `on-error` skips one throwing track and keeps the
  other.
- `offline_render_test.cljs`: `job-events` with `on-error` skips the failing track.
- `theory_test.cljs`, `mini_test.cljs`, `eval_test.cljs`: every message in Decisions 6–12.
- `sound_check_test.cljs`: `probe-error` → `nil` for a healthy track, a map for a
  throwing one.
- New `e2e/robust-playback.spec.ts` (Definition of done).

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/audio.cljs` | per-track `try`, `report-query-error!`, `scheduled-counts` |
| `packages/core/src/repulse/song.cljs` | `song-onsets` 5-arity with `on-error` |
| `app/src/repulse/offline_render.cljs`, `midi_export.cljs`, `env/builtins/midi.cljs` | per-track isolation in exports |
| `app/src/repulse/eval_orchestrator.cljs` | eval-time probe, `:on-query-error` in `init!` |
| `app/src/repulse/sound_check.cljs` (+ `_test`) | pure `probe-error`; realise `sound-keywords` inside `try` |
| `app/src/repulse/ui/timeline.cljs` | realise the query inside `try` |
| `app/src/repulse/app.cljs` | `onsetCount` debug handle |
| `packages/core/src/repulse/core.cljs` (+ `_test`) | caps, `every*`/`last-of*`/`chunk*`/`chunk-into*`, rational `arrange*`, 13 new tests |
| `packages/core/src/repulse/params.cljs` | `echo*` cap |
| `packages/core/src/repulse/theory.cljs` (+ `_test`) | name checks, `chord-names` |
| `packages/lisp/src/repulse/lisp/mini.cljs` (+ `_test`) | trailing tokens, positions, fractions, strict numbers, `!` cap |
| `packages/lisp/src/repulse/lisp/eval.cljs` (+ `_test`) | threading rewrite, arity, `& rest`, `recur`, `form-str` |
| `packages/lisp/src/repulse/lisp/util.cljs` | `levenshtein`, `typo-hint` moved here |
| `packages/lisp/src/repulse/lisp/reader.cljs` | rational/number/string errors, `startsWith` |
| `packages/lisp/src/repulse/lisp/builtins/collection.cljs`, `music.cljs` | `doall`; name validation |
| `e2e/robust-playback.spec.ts` | new |
| `docs/USAGE.md` | see Definition of done |
| `ROADMAP.md` | mark delivered |

## Definition of done

- [ ] Buffer `(track :kick (seq :bd :bd :bd :bd))` + `(track :hats (fast "<1 1 x>" (seq :hh)))`,
      Alt+Enter: Playwright (`e2e/robust-playback.spec.ts`) waits until
      `__REPULSE_APP__.cycle()` has advanced by ≥ 5. Then `onsetCount('kick')` has grown by
      ≥ 16 (4 per cycle over ≥ 4 cycles), `onsetCount('hats')` > 0, and `#output` contains
      `Error in track :hats: Expected a number, got :x` exactly once.
- [ ] Same spec: with `:kick` playing, evaluating `(track :kick (seq :bd :bd :bd :bd))
      (track :bad (fast (seq 1 :x) (seq :bd)))` gives
      `Error: track :bad fails at cycle 0: Expected a number, got :x`, and
      `__REPULSE_APP__.tracks()` is still `["kick"]`.
- [ ] `(fmap (fn [v] (nope v)) (seq 1))` as the last form: an eval error that starts with
      `pattern fails at cycle 0: Undefined symbol: nope`, with `nope` underlined.
- [ ] Caps: `(run 1024)` gives 1024 events in cycle 0. `(run 1025)` →
      `run: n = 1025 exceeds the limit of 1024`.
      `(euclidean 3 100000000 :bd)` → `euclidean: n = 100000000 exceeds the limit of 1024`.
      `(apply-n 65 rev (seq 1 2))` → `apply-n: n = 65 exceeds the limit of 64`.
      `(echo 100 1/8 0.5 (seq :bd))` → `echo: times = 100 exceeds the limit of 64`.
      `(seq "bd!100000000")` → `! count = 100000000 exceeds the limit of 1024 in
      mini-notation at position 2`. Each returns within 100 ms.
- [ ] TEST1 `evalToEvents('(seq "hh*0.5")', 4)`: onsets (`wholeStart === start`) at 0 and
      2 only, each with whole length 2. `'(seq "bd@1.5 sd")'` → bd `[0, 0.6)`, sd
      `[0.6, 1)`. `'(seq "bd*1.5")'`, 1 cycle → onsets at 0 and 0.6667.
      `'(seq "808bd")'` → value `":808bd"`.
- [ ] `(seq "bd ] sd")` → `Unexpected "]" in mini-notation at position 3`, only `]`
      underlined. `(seq "bd > sd")` → same with `">"`. `(seq ":bd")` →
      `Unexpected ":" in mini-notation at position 0`.
- [ ] `(-> {:a 1} :a)` → `=> 1`. `` (defmacro twice [p] `(fast 2 ~p)) (->> (seq :bd) (twice)) ``
      → 2 events per cycle. `(->> (seq 1) (fast :x))` underlines `fast`.
- [ ] `(def x 5) (defn f [x] x) (f)` → `f expects 1 argument, got 0`.
      `((fn [a & r] r) 1 2 3)` → `=> (2 3)`. `((fn [a] a) 1 2)` →
      `fn expects 1 argument, got 2`. `(recur 1)` → `recur outside loop`.
      `("a" 1)` → `"a" is not a function` (no `SourcedVal` text).
- [ ] Reader: `1/0` → `Invalid rational 1/0 — the denominator is 0`; `1.2.3` →
      `Invalid number 1.2.3`; `"abc\` → `Unterminated string — missing closing "`.
- [ ] `(scale :majr :c4 (seq 0))` → `scale: unknown scale :majr — did you mean :major?`;
      `(chord :majr :c4)` → `chord: unknown chord :majr — did you mean :major?`;
      `(scale :major :zz (seq 0))` → `scale: unknown root :zz — expected a note like :c4,
      :eb3 or :fs`; `(scale [] :c4 (seq 0))` → `scale: the interval vector is empty`.
- [ ] TEST1 `evalToEvents('(arrange [[(seq :a) 3/2] [(seq :b) 1/2]])', 2)` →
      `:a` whole `[0,1)`; `:a` part `[1,1.5)` whole `[1,2)`; `:b` part `[1.5,2)` whole
      `[1.5,2.5)`. `1.5` in place of `3/2` gives the same. `(every 0 rev (seq 1 2))` →
      `every: n must be at least 1, got 0`; `(every 1/2 rev (seq 1 2))` → `… got 1/2`.
- [ ] All existing demos (`content/demos_test.cljs`) and unit tests pass. A test that relied
      on lenient closure arity is fixed in the test or the demo, not by loosening the check.
- [ ] `npm run test:all` passes.
- [ ] `npx shadow-cljs compile app` reports no warnings.
- [ ] `docs/USAGE.md`: Mini-notation (fractional `*` / `@`, whole-token numbers, error
      positions), a "Count limits" table at the end of *Time & structure*, `fn` (`& rest`,
      arity errors), `arrange` (fractional durations), and a sentence in *Common mistakes*
      that a failing track is skipped and reported once.
- [ ] The PR ends with `## Test protocol` covering the cases above, values copied from the
      TEST1 harness.

## What NOT to do

- Don't deduplicate `xf`, the typo/levenshtein helpers beyond the move into `util.cljs`, or
  the numeric conversion helpers. That is QA4.
- Don't touch the release-build rational overflow guard in `core/rat` (compiled away when
  `goog.DEBUG` is false). Note it in the PR only.
- No UI changes beyond the output line for a query error (no badges, outline marks or
  inspector states). That is QA3.
- Don't touch the WASM engine (`packages/audio`). That is QA2.
- Don't add a global budget for nested `echo` / `stutter` chains. The per-call caps are the
  scope.
- Don't add new built-in names, so no grammar, `completions.js` or `builtin_meta.edn`
  changes.
- Don't make the scheduler retry or auto-remove a failing track. It stays scheduled and is
  queried again every cycle.
