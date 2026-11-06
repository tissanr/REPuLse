# Phase TL6 — Sample Slicing & Playback Control

> **Spec status:** full (expanded 2026-11-06 from the outline; open design questions and
> decisions S1–S14 decided during expansion). Function list taken from the TL6 rows of
> [docs/STRUDEL-PARITY.md](../docs/STRUDEL-PARITY.md).
>
> **Automation:** 🤖👂 auto + human check — event output (begin/end/speed values) is
> exactly testable and the offline-render tests can check onset timing and non-silence,
> but whether a sliced break *sounds* right needs a listen.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL1 (onset-only triggering, patterned args), TL3 (`timecat`, `squeeze-join`).

## Goal

Cut, rearrange and time-stretch samples the Tidal way: the core of breakbeat and
sample-based live coding.

```lisp
;; Before — one-shot playback with begin/end/rate
(->> (sound :breaks152 0) (begin 0.25) (end 0.5))

;; After
(chop 8 (sound :breaks152 0))                     ; 8 equal slices played in order
(striate 4 (sound :breaks152 0))                  ; 4 interleaved slices
(slice 8 (seq 0 3 2 7) (sound :breaks152 0))      ; pick slices by index
(splice 8 (seq 0 1 6 7) (sound :breaks152 0))     ; slices + speed fitted to slot length
(loop-at 2 (chop 16 (sound :breaks152 0)))        ; fit the sample to 2 cycles
(fit (seq (sound :breaks152 0) :_))               ; sample stretched to its event
(->> (fast 2 (seq :ho :hc :ho :hc)) (cut 1))           ; choke group: next hit cuts the open hat
(->> (sound :breaks152 0) (speed -1))             ; reverse playback
(->> (seq :bd :sd) (legato 0.5))              ; play half of each event's length
```

## Background

- Phase L added `rate`, `begin`, `end`, `loop-sample` (`packages/core/src/repulse/params.cljs`).
- Sample playback is **JavaScript only**: `samples/play!` creates an
  `AudioBufferSourceNode` per event (`app/src/repulse/samples.cljs`), for the live
  context, the EXP1 offline export (`offline_render.cljs`) and the TEST1 harness alike.
  The WASM worklet never plays samples. So everything audio-level in TL6 lives in
  `samples.cljs` and is automatically identical in offline render.
- `audio/play-event` currently receives no event duration or cycle length; the scheduler
  (`schedule-cycle!`), the export (`job-events`) and the harness know both.

## Scope

New built-ins (14):

| Name | Kind | Event key |
|---|---|---|
| `chop n pat` | pattern | `:begin :end` |
| `striate n pat` | pattern | `:begin :end` |
| `slice n ipat pat` | pattern | `:begin :end` |
| `splice n ipat pat` | pattern | `:begin :end :rate :unit` |
| `fit pat` | pattern | `:rate :unit` |
| `loop-at n pat` | pattern | `:rate :unit` (+ `slow n`) |
| `speed v pat` | param | `:rate` (alias of `rate`) |
| `unit u pat` | param | `:unit` (`:r` default, `:c`) |
| `accelerate v pat` | param | `:accelerate` |
| `legato v pat` | param | `:legato` |
| `sustain secs pat` | param | `:sustain` |
| `cut group pat` | param | `:cut` |
| `loop-begin v pat` | param | `:loop-begin` |
| `loop-end v pat` | param | `:loop-end` |

Params all have the curried 1-arg form and take patterned values (like `rate`).
`chop`/`striate`/`loop-at` take a patterned `n`; `slice`/`splice` take patterned `n`
and index pattern (structure from the index pattern).

## Design decisions

**Open questions from the outline:**

1. *`rate` vs `speed`* — **both names, one key.** `speed` writes `:rate`; `hurry`,
   `splice`, `fit`, `loop-at` multiply `:rate`. No second key to reconcile.
2. *Where does `cut` live?* — **`samples.cljs`, JS.** Samples never reach WASM, so a
   per-`AudioContext` choke registry next to `play!` is identical live, in export and in
   the harness. Synth voices ignore `:cut` (documented).

**Decisions taken during expansion:**

- **S1 — Relative ranges.** `chop`, `striate` subdivide the event's existing
  `begin`–`end` range (default 0–1), like Tidal's `mergePlayRange`. `slice`/`splice`
  set absolute ranges.
- **S2 — Values are floats.** `:begin`/`:end` are buffer fractions (not time), stored as
  doubles, matching the existing `begin`/`end`. Pattern *time* stays rational.
- **S3 — Rests stay rests.** `:_` events pass through every slicing function unchanged.
- **S4 — `slice` indices wrap.** Index `i` → `(mod i n)`; non-numeric index values are
  dropped.
- **S5 — `unit :c`.** Effective playback rate = `rate × buffer-seconds / cycle-seconds`
  (SuperDirt). Without a known cycle length `:c` falls back to `:r`. Accepts `:c`/`:r`
  and the strings `"c"`/`"r"`; anything else is an eval error.
- **S6 — `splice`** = `slice`, then `:rate = (1/n) / whole-cycles × (rate or 1)`,
  `:unit :c` (Tidal). **`fit`** (Strudel semantics): `:rate = (end − begin) /
  whole-cycles × (rate or 1)`, `:unit :c`. **`loop-at n`** = `slow n`, `:rate ×= 1/n`,
  `:unit :c`.
- **S7 — Negative rate plays backwards** using a reversed copy of the buffer (cached
  per buffer in a `WeakMap`), not a negative `playbackRate`, so the result is the same in
  every browser and offline. `begin`/`end` keep their forward meaning: `(speed -1)` with
  `begin 0.25 end 0.5` plays that quarter backwards. Rate 0 → no sound.
- **S8 — Length.** A one-shot sample stops at `end` (buffer-content bound, unaffected by
  rate). Additionally it stops after `sustain` seconds, or `legato × event seconds`,
  whichever is shorter, with a 5 ms linear fade. Legato/sustain apply to samples only.
- **S9 — Looped samples stop at the event end.** Before TL6 a `loop-sample` voice looped
  forever. Now it stops after the event's duration (or `legato`/`sustain`). Changed
  behaviour, listed in the PR protocol. `loop-begin`/`loop-end` (fractions) override the
  loop region, which otherwise is `begin`–`end`.
- **S10 — `accelerate a`.** Playback rate glides linearly from `r` to `r + a` over the
  sample's unaccelerated playing time (`r` after `unit` conversion), clamped at ≥ 0.01.
- **S11 — Cut groups** are global per audio context (not per track/orbit — REPuLse has
  no orbits yet). A new voice in group g fades out (5 ms) every voice of g that started
  earlier and is still playing; it is itself cut by a voice of g that starts later
  (events may be scheduled out of time order across tracks in an export). Same start
  time: both play. Voices of a group are pruned once their end time has passed; at most
  64 tracked per group.
- **S12 — Timing context.** `audio/play-event` gains an optional 5th argument
  `{:dur secs :cycle-dur secs}`; the scheduler, export and harness pass it. The 4-arity
  keeps working (no legato/unit :c then).
- **S13 — Pure planning.** The playback maths is a pure function
  `samples/playback-plan` (rate, reverse, offset, content duration, stop time, loop
  region, accelerate target) and the choke logic a pure `samples/cut-plan`; both are
  unit-tested in `samples_test.cljs` with known buffer lengths.
- **S14 — `n` is not added.** The parity row marks it 🔁 (`(sound :bd 2)`, `"bd:2"`);
  a one-letter built-in would shadow and highlight every user variable named `n`.

## Files to change

| File | Change |
|---|---|
| `packages/core/src/repulse/params.cljs` | `chop striate slice splice fit loop-at speed unit accelerate legato sustain cut loop-begin loop-end` |
| `packages/core/src/repulse/params_test.cljs` | Event-level tests for every function |
| `packages/lisp/src/repulse/lisp/builtins/params.cljs` | Lisp wrappers (patterned args via `u/pat-arg`) |
| `packages/lisp/src/repulse/lisp/integration_test.cljs` | Evaluated-snippet tests |
| `app/src/repulse/samples.cljs` | `playback-plan`, `cut-plan`, reversed buffers, choke registry, `play!` rewrite |
| `app/src/repulse/samples_test.cljs` | Unit tests for the two pure planners |
| `app/src/repulse/audio.cljs` | `play-event` 5-arity + extra keys; scheduler passes timing |
| `app/src/repulse/offline_render.cljs` | Pass timing to `play-event` |
| `app/src/repulse/test_api.cljs` | Pass timing; await sample promises; `registerTestSample` |
| `e2e/sample-slicing.spec.ts` | Offline-render tests with synthetic sample buffers |
| grammar, `parser.js`, `completions.js`, `builtin_meta.edn`, `docs/ai/builtins.json` | Rule 3 |
| `docs/USAGE.md`, `docs/STRUDEL-PARITY.md`, `ROADMAP.md` | Docs, parity ✅, delivered |

## Definition of done

- [ ] `(chop 4 (sound :breaks152 0))` → 4 events with `begin` 0, 1/4, 1/2, 3/4 and matching `end`.
- [ ] `(slice 8 (seq 0 3) …)` → begin/end 0–1/8 and 3/8–1/2.
- [ ] `(splice 4 …)` sets speed so each slice fills its slot (unit test with a known sample length).
- [ ] `(cut 1 …)` stops the previous voice in group 1: offline render shows the earlier sample truncated.
- [ ] Offline render of a chopped sample has onsets at expected times (±1 ms).
- [ ] Reverse playback, legato and `loop-at` verified in offline render.
- [ ] Human listen check recorded in the PR (two demo snippets).
- [ ] Rule 3 registration; USAGE.md "Sample slicing" section; parity rows ✅.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.

## What NOT to do

- No granular synthesis / time-stretch without pitch change (backlog: `stretch`).
- No per-event filters/effects: **TL7**.
- No new sample sources or kits: **KIT1**.
- No `cut`/`legato` for WASM synth voices.
