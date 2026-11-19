# Phase SONG2 — Song-Time Automation & Transitions

> **Spec status:** full (expanded 2026-11-13 from the outline; open design questions 1–4
> decided as A1–A4 below, decisions A5–A16 taken during expansion)
>
> **Automation:** 🤖 auto — automation lanes and fills are pure functions of song time
> inside `repulse.song/song-onsets`, so they are unit-testable in `packages/core` and
> visible through the TEST1 `songEvents` harness and the pure `offline-render/job-events`
> plan. A final listen is nice-to-have, not required for acceptance.

**Milestone:** M2 — Full songs in code
**Depends on:** SONG1 (song form and song position), TL2 (continuous signals).
**Recommended (not blocking):** TL7a and TL7b. Automation writes any numeric per-event
parameter, so `lpf`, `room`, … become automatable the moment those phases add them; SONG2
itself is specified and tested with the parameters that exist today (`amp`, `pan`,
`attack`, `decay`, `release`, `rate`, `speed`, …).

## Goal

Give songs movement across sections: parameters that change over the song timeline
("fade the hats in over the build") and transitions between sections (fills, drops,
risers), written in code, addressed by section and bar, and identical in playback,
`play-from`, re-evaluation, every export and the MIDI file.

```lisp
;; Before: tween (T1) ramps a parameter from the moment you evaluate, restarts on
;; every re-eval, and knows nothing about sections.
(->> kick (amp (tween :linear 0.2 1.0 2)))

;; After
(track :kick  (seq :bd :bd :bd :bd))
(track :hats  (fast 2 (seq :hh :hh :hh :hh)))
(track :snare (seq :_ :sd :_ :sd))
(track :bass  (->> (seq :c2 :_ :c2 :_) (synth :saw)))

(song
  (section :intro 2 [:kick])
  (section :build 4 [:kick :hats :bass]
    (automate :hats :amp (ramp :linear 0 1))          ; over the whole section
    (automate :bass :pan (range -1 1 (slow 4 saw)))   ; follow a signal, section-local
    (fill :snare 1 (fast 4 (seq :sd :sd)))            ; snare roll in the last bar
    (fill :kick 1 :_))                                ; kick drops out in the last bar
  (section :drop 4 :all))
```

## Scope

- `automate`: a lane that sets one parameter on one track (or `:all`) inside a section,
  optionally restricted to a bar range of that section.
- Curves: a constant (hold), `(ramp curve from to)` with `:linear`, `:exp`, `:sine`, or
  any pattern / TL2 signal sampled in section-local time.
- Combining with the track's own values: `:op :set` (default), `:mul`, `:add`.
- `fill`: replace (or add, or silence) one track's events in the last N bars of a section.
- Risers and drops are recipes built from `fill` + `automate` + ordinary tracks, documented
  in USAGE; no new audio path, no new DSP.
- Deterministic in song time everywhere `song-onsets` is used: scheduler, `play-from`,
  `:end :loop`, re-evaluation, `(export :song)` offline and real time, `(export :stems)`,
  MIDI export, TEST1 harness.

## Design decisions

### The outline's open questions

**A1 — Per-event values, not WASM ramps.** Automation is evaluated once per event, at the
event's onset in song time, and written into the event's value map before scheduling.
This is deterministic, needs no audio-layer state, and works unchanged in every path that
consumes `song-onsets`. Trade-off (documented in USAGE): a long sustained note keeps the
value it started with; automation moves *between* events, not within one. Smooth
within-note gain rides remain the job of `tween` and the audio engine.

**A2 — Automation lives inside `section` forms.** SONG1 already reserved `& more` on
`section` for this. Lanes are scoped to their section; `:bars [from to]` narrows to a
sub-range. There is no separate song-level lane block: a sweep across two sections is two
lanes (e.g. `(ramp :linear 0 0.5)` then `(ramp :linear 0.5 1)`). Transitions are also
section items (`fill`) rather than a `(transition :a :b …)` form, because a fill always
belongs to the end of exactly one section — naming two sections adds a way to be wrong
(non-adjacent sections, a song reorder) without adding meaning.

**A3 — `tween` stays as it is.** It answers a different question (a one-shot ramp from
*now*, useful in free-running live coding without a song) and is used in demos. It is
not reimplemented or deprecated. USAGE gets a short "`tween` vs `automate`" comparison.
Inside a song, an automation lane on the same parameter overrides a tween on the events
it covers (`:set` replaces the tween descriptor; `:mul`/`:add` treat a non-number base as
missing — A9).

**A4 — Risers and drops are recipes, not built-ins.** A drop is `(fill :kick 1 :_)`; a
riser is an ordinary track that only plays through a `fill`, with an `automate` amp ramp.
USAGE documents both recipes. Only three built-ins are added: `automate`, `ramp`, `fill`.

### Decisions taken during spec expansion

**A5 — Lane time is section-local.** For a lane covering song bars `[L0, L1)` an event
with song-time onset `t` has local time `x = t − L0` (rational bars) and progress
`u = x / (L1 − L0)` as a float in `[0, 1)`. Ramps use `u`; patterns and signals are
sampled at `x`. So `(slow 4 saw)` in a 4-bar lane rises exactly once from the lane's
first bar, wherever the section sits in the song. (Tracks themselves keep playing in
song time — SONG1 rule; only the automation curve is section-local.)

**A6 — Ramp curves.**

| Curve | Value at progress `u` | Use |
|---|---|---|
| `:linear` | `from + (to − from)·u` | amp, pan |
| `:exp` | `from · (to/from)^u` — equal ratio per bar; needs `from > 0` and `to > 0` | frequencies, `rate`, `speed` |
| `:sine` | `from + (to − from)·½(1 − cos πu)` — S-curve (same as `tween :sine`) | smooth fades |

`ramp :exp` is a true exponential and therefore **differs** from `tween :exp` (a quadratic
ease-in, `u²`). USAGE says so in one sentence. Holding a value needs no curve: a plain
number is a constant lane (`(automate :bass :amp 0.5)`).

**A7 — The ramp never reaches `to` inside the lane.** `u` is in `[0, 1)`; an event exactly
at `L1` belongs to the next bar range. After the lane the parameter returns to the
track's own value — there is no hold-after. To keep a value, add a lane in the next
section. Documented with an example.

**A8 — Patterns as curves.** Any pattern (signal or discrete) is sampled at local time `x`
by `song/sample-at`: query the span `[x, x + 1/1000000)` and take the value of the first
event returned. A non-number sample (e.g. a keyword) leaves the parameter untouched —
no error at play time.

**A9 — `:op`.** `:set` (default) replaces the parameter. `:mul` multiplies the event's
existing value; `:add` adds to it. If the event has no numeric value for the parameter
(missing, or a tween descriptor), `:mul`/`:add` behave like `:set`. `:mul` is the way to
fade a track that has accents: `(automate :hats :amp (ramp :linear 0 1) :op :mul)`.

**A10 — Order and overlap.** Lanes are applied in the order they are written in the
section. Two lanes on the same track and parameter may overlap; the later one sees the
earlier result as its base (so with `:set` the later wins, with `:mul` they compound).
No error.

**A11 — `:all` targets every track sounding in that bar** (section tracks plus fill
tracks, minus muted — mute still wins).

**A12 — Rests are never automated.** Events whose value is `:_` or `{:note :_ …}` pass
through untouched, so a rest never becomes `{:note :_ :amp …}`.

**A13 — `fill` semantics.** `(fill track bars pattern)` covers the last `bars` bars of the
section. In those bars the track's events come from `pattern` instead of the track's
own pattern, queried in song time like every track. The track sounds there even if the
section's track list does not include it (that is how a riser track enters). `pattern`
may be `:_` (silence — a drop), a pattern, or a single value (coerced with `pure`). At
most one fill per track per section. Automation lanes apply to fill events like any
other events of that track. Mute wins.

**A14 — Fill track names must be real tracks.** `fill` needs a track defined above the
song (its routing, per-track FX and stem live there); `:all` is not allowed for `fill`.
`automate` accepts `:all` or a defined track. Unknown names get the SONG1 fuzzy hint.

**A15 — Validation splits like SONG1.** Values that can be checked alone are checked when
the built-in is called (`ramp`, `automate`, `fill` arguments); ranges against the
section length are checked in `make-section`; track names against the buffer in
`make-song`. Every error carries a source range (A16).

**A16 — Error locations.** `automate` and `fill` values carry `:source` = the source
range of their track argument (fallback: the whole call's first argument). `make-song`
track errors for items add `:item-source`; `register-song!` prefers it over
`:track-sources` and the section source.

**A17 — Summary line.** The song output line gains counts when present:
`song: 3 sections · 10 bars · 20.0 s at 120 BPM · 2 automations · 2 fills · :end :stop`.
Zero counts are omitted, so SONG1 songs print exactly as before.

## Background

### Song model — `packages/core/src/repulse/song.cljs`

- `make-section [name bars tracks]` → `{:type :section :name :bars :tracks}`; validates
  name, bars, tracks with `song-error` (`ex-info` with `:type :song-error`).
- `make-song [sections opts known-tracks hint-fn]` validates duplicates and unknown track
  names (fuzzy hint via `hint-fn`) and resolves each section to absolute `:start`/`:end`
  bars; `:all` sections get `:tracks known` and `:all? true`.
- `section-at-bar`, `active-tracks-at-bar`, `wrap-bar` (`:loop` wraps, `:stop` → nil
  after the end), `song-status`, `position-after-reeval`.
- `song-onsets [song tracks muted bar]` — the single place where song time becomes
  events: for scheduler bar `bar` it computes song bar `q = (wrap-bar song bar)`, queries
  each active, unmuted track with `core/query-onsets` over `(core/cycle-span q)` and
  shifts events by `bar − q` (`shift-event`). Returns `[{:track kw :event ev} …]` in
  `(:tracks song)` order.
- Rationals are `[n d]` vectors; helpers in `repulse.core`: `rat`, `rat+`, `rat-`,
  `rat-div`, `rat<`, `rat<=`, `rat->float`, `int->rat`, `span`, `cycle-span`, `query`,
  `query-onsets`, `pure`, `pattern?`.

### Consumers of `song-onsets` (all get SONG2 for free)

- Scheduler: `schedule-cycle!` in `app/src/repulse/audio.cljs` (song branch).
- Offline song render and stems: `job-events` in `app/src/repulse/offline_render.cljs`
  (`:song` kind iterates `(range (song/song-length s))`).
- MIDI export: `app/src/repulse/midi_export.cljs` (velocity from `(:amp v)` via
  `midi/amp->velocity`, so amp automation shapes velocities).
- TEST1 harness: `song-events` in `app/src/repulse/test_api.cljs`
  (`window.__REPULSE_TEST__.songEvents(code, fromBar, toBar, {muted})`), used by
  `e2e/song.spec.ts`; drops rests via `rest-value?`.

### Lisp layer — `packages/lisp/src/repulse/lisp/builtins/arrangement.cljs`

- `"section"` takes `name-arg bars-arg tracks-arg & more` and currently throws
  `section :x: unexpected argument … — sections take (section name bars tracks)` for any
  `more`. It wraps `song/make-section` errors with source ranges via `source-for` and
  attaches `:source` and `:track-sources`.
- Helpers: `u/unwrap`, `u/deep-unwrap`, `u/source-of` (`repulse.lisp.util`).

### App layer

- `app/src/repulse/env/builtins/song.cljs` — `song` and `play-from`; calls
  `st/register-song!`.
- `app/src/repulse/song_transport.cljs` — `register-song!` builds the song with
  `song/make-song` and maps `:song-error` data to a source range
  (`:track-sources` → section `:source` → `src`).
- `app/src/repulse/eval_orchestrator.cljs` ≈ line 293 — the `song: … sections · …` line.

### Parameters today

`packages/core/src/repulse/params.cljs` — `apply-param` turns raw values into maps with
`(if (map? v) v {:note v})` and `assoc`s the key. Automation uses the same upgrade so
`:hh` becomes `{:note :hh :amp 0.25}`, which the scheduler already plays.
`tween` (`packages/lisp/src/repulse/lisp/builtins/params.cljs`) returns
`{:type :tween …}`; `resolve-tweens` in `audio.cljs` interpolates it per event.

### Built-in registration (CLAUDE.md Rule 3)

Grammar `app/src/repulse/lisp-lang/repulse-lisp.grammar` (`BuiltinName`), `npm run
gen:grammar`, `app/src/repulse/lisp-lang/completions.js`,
`app/src/repulse/content/builtin_meta.edn`, `npm run gen:ai-docs` → `docs/ai/builtins.json`.

## Implementation

### 0. Session start

Rule 4: `npm run gen:grammar && npm run gen:ai-docs && npm run test` on the unmodified
branch. Confirm none of `automate`, `ramp`, `fill` is already a built-in or special form
(`grep -rn '"fill"\|"ramp"\|"automate"' packages app/src`).

### 1. Core model — `packages/core/src/repulse/song.cljs`

Add (all pure, all with `cljs.test` coverage in `song_test.cljs`):

```clojure
(defn ramp? [x] (and (map? x) (= :ramp (:type x))))
(defn automation? [x] (and (map? x) (= :automation (:type x))))
(defn fill? [x] (and (map? x) (= :fill (:type x))))

(defn make-ramp [curve from to])
;; → {:type :ramp :curve :from :to}
;; errors: "ramp: unknown curve :log — use :linear, :exp or :sine"
;;         "ramp: from and to must be numbers, got …"
;;         "ramp :exp needs both values > 0, got 0 and 1 — use :linear or :sine"

(defn make-automation [track param curve opts])
;; track — keyword (:all allowed); param — keyword; curve — number | ramp | pattern
;; opts  — {:bars [a b] :op :set|:mul|:add} (both optional)
;; → {:type :automation :track :param :curve :bars (or nil) :op (default :set)}
;; errors: "automate: track must be a track name like :hats or :all, got …"
;;         "automate: parameter must be a keyword like :amp, got …"
;;         "automate :hats :amp: expected a number, (ramp …) or a pattern, got …"
;;         "automate: :bars must be [from to] with whole numbers, got …"
;;         "automate: :op must be :set, :mul or :add, got :foo"

(defn make-fill [track bars pattern])
;; pattern :_ → nil (silence); pattern? → as is; other value → (core/pure v)
;; → {:type :fill :track :bars :pattern}
;; errors: "fill: needs one track name, got :all"
;;         "fill: track must be a track name like :snare, got …"
;;         "fill :snare: bars must be a whole number ≥ 1, got 0"
```

Extend `make-section` with a 4-arity `[name bars tracks items]` (3-arity delegates with
`[]`). Items must be automations or fills (anything else:
`section :build: unexpected argument 7 — after the tracks, sections take (automate …) and (fill …)`).
Checks against the section length:

- `automate :hats :amp: :bars [3 6] is outside section :build (4 bars) — use 0 ≤ from < to ≤ 4`
- `fill :snare: 5 bars is longer than section :build (4 bars)`
- `Two fills for :snare in section :build — use one (fill …) per track`

Store `:automations` (vector, written order) and `:fills` (vector) on the section.

In `make-song`, check every item's track against `known` exactly like section tracks
(`:all` allowed only for automations), with the message
`Unknown track :hatz in (automate …) in section :build — did you mean :hats?` /
`… in (fill …) …`, and ex-data `{:section name :track t :item-source (:source item)}`.
While resolving sections, add absolute ranges: automation `:from`/`:to`
(`start + a`, `start + b`, default the whole section), fill `:from` (`end − bars`) and
`:to` (`end`).

New query helpers:

```clojure
(defn ramp-value [{:keys [curve from to]} u])     ; A6 formulas, u float in [0,1)
(defn sample-at [pat x])                           ; A8; nil when no event
(defn curve-value [curve x len])                   ; number | ramp (u = x/len) | pattern
(defn automate-value [song track value t])
;; Apply every lane of the section at song time t (rational) whose track is `track` or
;; :all and whose [from,to) contains t, in written order, with :op (A9). Rest values
;; (A12) are returned unchanged; a non-map value is upgraded with {:note v} only when a
;; lane actually applies. Returns the (possibly unchanged) value.
(defn automation-value [song track param t])
;; (get (automate-value song track {} t) param) — the DoD's pure lookup; nil when no lane.
(defn fill-at [song track q])                      ; the fill covering song bar q, or nil
```

Change `song-onsets`:

1. `sec` = section at `q`; `fills` = fills of `sec` with `:from ≤ q < :to`.
2. Active set = section tracks ∪ fill tracks, iterated in `(:tracks song)` order, minus
   `muted`.
3. Source pattern for track `t` = the fill's `:pattern` if a fill covers it (nil →
   no events), else `(get tracks t)` (skip if not a pattern).
4. For each onset `ev` (still in song time), `ev' = (update ev :value #(automate-value
   song t % (:start (:whole ev))))` — only when `sec` has automations, so SONG1 songs take
   the old path untouched.
5. Shift as before.

Performance: lanes are filtered per bar once (step 1), not per event.

### 2. Lisp built-ins — `packages/lisp/src/repulse/lisp/builtins/arrangement.cljs`

- `"ramp" (fn [curve from to])` → `song/make-ramp` on unwrapped args; wrap `song-error`
  into `:eval-error` with the source of the offending argument.
- `"automate" (fn [track param curve & opts])` — parse keyword pairs `:bars`, `:op`
  (`automate: unknown option :foo — expected :bars or :op`;
  `automate: option :bars needs a value`), call `song/make-automation`, `assoc :source`
  (A16).
- `"fill" (fn [track bars pattern])` — `song/make-fill`, `assoc :source`.
- `"section"`: replace the `more` rejection by `(song/make-section nm bars tracks (mapv
  u/unwrap more))`; on an item error point the source at that item (use its `:source`, or
  `u/source-of` of the argument). Update the comment that reserved `more`.

### 3. App wiring

- `app/src/repulse/song_transport.cljs` `register-song!`: source lookup order becomes
  `(:item-source data)` → `:track-sources` → section `:source` → `src`.
- `app/src/repulse/eval_orchestrator.cljs`: A17 counts
  (`(reduce + (map (comp count :automations) (:sections val)))`, same for `:fills`;
  singular `1 automation` / `1 fill`).
- No changes to `audio.cljs`, `offline_render.cljs`, `midi_export.cljs` or `test_api.cljs`
  — they consume `song-onsets`. Verify this with the tests in step 5 rather than editing.

### 4. Built-in checklist (Rule 3)

For `automate`, `ramp`, `fill`: grammar `BuiltinName`, `npm run gen:grammar`,
`completions.js` entries (`(automate :track :param curve [:bars [a b]] [:op :mul])`,
`(ramp :linear|:exp|:sine from to)`, `(fill :track bars pattern|:_)`),
`builtin_meta.edn` (category `:song`, `returns`, `side-effects false`, ≥1 example,
`see-also` `section` `song` `tween`), `npm run gen:ai-docs`. Commit all generated files.

### 5. Tests

- `packages/core/src/repulse/song_test.cljs`: all reference vectors below (R1–R14).
- `packages/lisp` tests (where `section` errors are tested today): argument errors and the
  new `section` item error, with source ranges present.
- `app/src/repulse/offline_render_test.cljs`: R15 (`job-events` of an automated song).
- `app/src/repulse/song_transport_test.cljs`: R16 (`:item-source` wins).
- `e2e/song.spec.ts`: R17 via `songEvents` (end-to-end through the evaluator).

### 6. Docs

`docs/USAGE.md`, section "Writing a song":

- Built-ins table: rows for `automate`, `ramp`, `fill`.
- New subsection **"Automation — `automate` and `ramp`"**: syntax, curve table (A6),
  section-local time (A5), `:bars`, `:op`, no hold-after (A7), per-event resolution and
  sustained notes (A1), "`ramp :exp` vs `tween :exp`".
- New subsection **"Fills, drops and risers — `fill`"**: A13, the drop recipe
  `(fill :kick 1 :_)`, a riser recipe (a track listed in no section, entered by `fill`,
  with `(automate :riser :amp (ramp :exp 0.05 1) :bars [3 4])` — use a sample or synth
  that exists, e.g. a fast `(synth :saw)` line, checked with the harness).
- Errors list: every new message.
- "Transitions" section: add "`tween` vs `automate`" (A3).
- Update the TOC entry list if subsections are listed.

`README.md`: no change unless its song example is extended. `ROADMAP.md`: mark delivered
(Rule 2).

## Files to change

| File | Change |
|---|---|
| `packages/core/src/repulse/song.cljs` | ramps, automations, fills, `make-section` 4-arity, `make-song` checks and ranges, `sample-at`, `automate-value`, `automation-value`, `fill-at`, `song-onsets` |
| `packages/core/src/repulse/song_test.cljs` | R1–R14 |
| `packages/lisp/src/repulse/lisp/builtins/arrangement.cljs` | `ramp`, `automate`, `fill`; `section` items |
| `packages/lisp/src/repulse/lisp/integration_test.cljs` (≈ line 246, existing `section` error tests; update the old `unexpected argument` expectation) | argument and item errors |
| `app/src/repulse/song_transport.cljs` | `:item-source` lookup |
| `app/src/repulse/song_transport_test.cljs` | R16 |
| `app/src/repulse/eval_orchestrator.cljs` | A17 summary counts |
| `app/src/repulse/offline_render_test.cljs` | R15 |
| `e2e/song.spec.ts` | R17 |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` + generated `parser.js` | 3 names |
| `app/src/repulse/lisp-lang/completions.js` | 3 entries |
| `app/src/repulse/content/builtin_meta.edn` + generated `docs/ai/builtins.json` | 3 entries |
| `docs/USAGE.md` | see step 6 |
| `ROADMAP.md` | delivered |

## Definition of done

### Reference test vectors

Fixture (core, `song_test.cljs`), known tracks `[:kick :hats :snare :bass]`:

```clojure
(def tracks-a
  {:kick  (core/seq* [:bd :bd :bd :bd])
   :hats  (core/fast 4 (core/pure :hh))          ; onsets at k/4
   :snare (core/seq* [:_ :sd :_ :sd])
   :bass  (core/pure :c2)})

;; A: intro 2 bars (0–1), build 4 bars (2–5), drop 2 bars (6–7) → 8 bars
(def A
  (song/make-song
    [(song/make-section :intro 2 [:kick])
     (song/make-section :build 4 [:kick :hats :bass]
       [(song/make-automation :hats :amp (song/make-ramp :linear 0 1) {})
        (song/make-fill :snare 1 (core/fast 2 (core/pure :sd)))
        (song/make-fill :kick 1 :_)])
     (song/make-section :drop 2 :all)]
    {} [:kick :hats :snare :bass] hint))
```

| # | Call / scenario | Expected |
|---|---|---|
| R1 | `(song/ramp-value (make-ramp :linear 0 1) u)` for u = 0, 0.25, 0.5 | 0, 0.25, 0.5 |
| R2 | `:exp 300 4000` at u = 0.25 / 0.5 / 0.75 | ≈ 573.27 / 1095.45 / 2093.27 (±0.01) |
| R3 | `:sine 0 1` at u = 0.25 / 0.5 / 0.75 | ≈ 0.1464 / 0.5 / 0.8536 |
| R4 | `song-onsets A tracks-a #{} 2` — hats | 4 events, values `{:note :hh :amp 0}`, `{… :amp 0.0625}`, `{… :amp 0.125}`, `{… :amp 0.1875}` at 2, 9/4, 5/2, 11/4 |
| R5 | `(automation-value A :hats :amp t)` for t = [3 1], [9 2], [23 4] | 0.25, 0.625, 0.9375 |
| R6 | `(automation-value A :hats :amp [6 1])` and `… :bass :amp [3 1]` | nil, nil (lane ended; other track) |
| R7 | Seek: `song-onsets A tracks-a #{} 4` called alone vs. after calling bars 0–3 | identical results (purity) |
| R8 | `song-onsets A … 5` (last bar of build) | no `:kick`; `:snare` `:sd` at 5 and 11/2; hats amps 0.75 … 0.9375; `:bass` `:c2` at 5 |
| R9 | `song-onsets A … 4` | `:kick` 4 events (fill only covers bar 5); no `:snare` |
| R10 | `song-onsets A … 6` (drop) | hats values plain `:hh` (no `:amp`), kick 4 events, snare `:sd` at 25/4 and 27/4 from its own pattern (the fill is over) |
| R11 | `:op :mul`: hats track `(core/fast 4 (core/pure {:note :hh :amp 0.8}))`, lane `(ramp :linear 0 1) {:op :mul}` → `automate-value` at t = [4 1] | `{:note :hh :amp 0.4}` |
| R12 | Signal, section-local: lane `:bass :pan` with curve `(range -1 1 (slow 4 saw))` built in core in section :build → `automation-value A' :bass :pan` at t = [2 1], [3 1], [4 1] | -1, -0.5, 0 (song-time sampling would give 0 at bar 2) |
| R13 | `:bars [2 4]` on the hats lane → at t = [3 1] / [4 1] / [5 1] | nil / 0 / 0.5 |
| R14 | `:end :loop` copy of A, `song-onsets … 10` | same values as bar 2, shifted by 8 |
| R15 | `offline-render/job-events {:kind :song :song A :tracks tracks-a :cycle-dur 2}` | the `:hats` events' `:amp` values are strictly increasing over bars 2–5 and `:t` of the first is 4.0 s |
| R16 | `register-song!` with an automation on `:hatz` whose `:source` is `{:from 120 :to 125}` | thrown ex-data has `:from 120 :to 125` and message `Unknown track :hatz in (automate …) in section :build — did you mean :hats?` |
| R17 | `songEvents` over bars 2–6 for the Goal snippet (minus the `:bass` pan lane) | 32 hats events in bars 2–5 (8 per bar) with amp 0, 0.03125 … 0.96875 in steps of 1/32; no `:kick` in bar 5; 8 `:sd` in bar 5 (`(fast 4 (seq :sd :sd))`) |

Errors (Lisp layer, message text exact, source range present):

```
ramp: unknown curve :log — use :linear, :exp or :sine
ramp :exp needs both values > 0, got 0 and 1 — use :linear or :sine
automate: :op must be :set, :mul or :add, got :foo
automate: unknown option :foo — expected :bars or :op
automate :hats :amp: expected a number, (ramp …) or a pattern, got "x"
automate :hats :amp: :bars [3 6] is outside section :build (4 bars) — use 0 ≤ from < to ≤ 4
fill: needs one track name, got :all
fill :snare: bars must be a whole number ≥ 1, got 0
fill :snare: 5 bars is longer than section :build (4 bars)
Two fills for :snare in section :build — use one (fill …) per track
section :build: unexpected argument 7 — after the tracks, sections take (automate …) and (fill …)
Unknown track :hatz in (automate …) in section :build — did you mean :hats?
```

### Checklist

- [ ] R1–R14 in `song_test.cljs`; R15 in `offline_render_test.cljs`; R16 in
      `song_transport_test.cljs`; R17 in `e2e/song.spec.ts`.
- [ ] Every error above tested with exact text.
- [ ] SONG1 tests unchanged and green (songs without items take the old path).
- [ ] Re-evaluating mid-song does not restart automation (follows from purity; R7 covers it).
- [ ] Rule 3 for `automate`, `ramp`, `fill`; generated files committed.
- [ ] USAGE step 6; ROADMAP delivered.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.

## Test protocol

Setup: `npm run dev:full`, open http://localhost:3000, headphones on. Evaluate with
Alt+Enter; `(stop)` between tests. Before writing the PR, re-check every value with the
TEST1 harness and correct it here if the implementation differs.

### New features
- [ ] **1. Fade-in, fill and drop.** The Goal snippet.
  Expected: output `song: 3 sections · 10 bars · 20.0 s at 120 BPM · 2 automations · 2 fills · :end :stop`;
  bars 0–1 kick only; bars 2–5 hats fade in from silent to near full; bass pans left →
  right across the build; in bar 5 the kick stops and a snare roll (8 hits) plays; bars
  6–9 everything at full level, hats no longer automated.
- [ ] **2. Seek.** While 1 plays, `(play-from :build)` after `(stop)`.
  Expected: hats start silent again at the build, same fade as in 1.
- [ ] **3. Re-eval keeps the curve.** During bar 4 change the bass note and re-evaluate.
  Expected: hats fade continues from where it was (≈ half volume), no restart.
- [ ] **4. Exponential sweep.** Replace the hats lane by
  `(automate :hats :rate (ramp :exp 0.5 2))`.
  Expected: hats pitch rises evenly from an octave below to an octave above over 4 bars.
- [ ] **5. Export.** `(export :song)`.
  Expected: the WAV has the same fade, roll and drop as playback.

### Warnings and errors
- [ ] **6.** `(ramp :exp 0 1)` → `ramp :exp needs both values > 0, got 0 and 1 — use :linear or :sine`
- [ ] **7.** A fill of 5 bars in a 4-bar section → `fill :snare: 5 bars is longer than section :build (4 bars)`, the fill call underlined.
- [ ] **8.** `(automate :hatz :amp 0.5)` in a section → `Unknown track :hatz in (automate …) in section :build — did you mean :hats?`

### Regressions
- [ ] **9. Plain song.** The SONG1 example from USAGE "Writing a song".
  Expected: output line without automation/fill counts; plays as before.
- [ ] **10. tween.** `(->> (fast 4 (seq :hh)) (amp (tween :linear 0 1 4)))` without a song.
  Expected: 4-bar fade-in from evaluation, as before.
- [ ] **11. MIDI export.** Export MIDI from test 1. Expected: hat velocities rise over the build.

## What NOT to do

- No audio-rate automation, no new messages to the WASM engine, no changes to
  `audio.cljs` scheduling (A1).
- No song-level lane block and no `(transition :a :b …)` form (A2).
- No changes to `tween` (A3); no built-in `riser`/`drop` (A4); no new DSP or synth voices.
- No automation of per-track `(fx …)` parameters or the master chain — those are
  stateful plugin params, not event params; a later phase can add them.
- No MIDI CC recording into lanes (N1 / capture phases), no timeline curve editing, no
  inspector or outline UI for lanes.
- Don't change song form semantics owned by SONG1 (section lengths, `:end`, `play-from`).
