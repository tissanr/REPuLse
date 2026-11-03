# Phase TL6 — Sample Slicing & Playback Control

> **Spec status:** outline — expand into a full spec before implementing. Take the exact
> function list from the TL6 rows of [docs/STRUDEL-PARITY.md](../docs/STRUDEL-PARITY.md).
>
> **Automation:** 🤖👂 auto + human check — event output (begin/end/speed values) is
> exactly testable and the offline-render tests can check onset timing and non-silence,
> but whether a sliced break *sounds* right needs a listen.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL1, TL3 (`fit`/`loop-at` rely on `segment`/`hurry`-style helpers)

## Goal

Cut, rearrange and time-stretch samples the Tidal way: the core of breakbeat and
sample-based live coding.

```lisp
;; Before — one-shot playback with begin/end/rate
(->> (sound :break 0) (begin 0.25) (end 0.5))

;; After (proposed)
(chop 8 (sound :break 0))                     ; 8 equal slices played in order
(slice 8 (seq 0 3 2 7) (sound :break 0))      ; pick slices by index
(splice 8 (seq 0 1 6 7) (sound :break 0))     ; slices + speed fitted to slot length
(loop-at 2 (chop 16 (sound :break 0)))         ; fit the sample to 2 cycles
(->> (seq :bd :oh) (cut 1))                      ; choke group: :bd cuts the open hat
```

## Background

- Phase L added `rate`, `begin`, `end`, `loop-sample` (`packages/core/src/repulse/params.cljs`)
  and sample playback in `app/src/repulse/audio.cljs` / `samples.cljs`.
- Sample durations are only known after decoding; `loop-at`/`splice` need the length or
  a `unit :c` (cycle-relative speed) mode like Tidal.

## Scope

- Pattern-level: `chop`, `striate`, `slice`, `splice`, `fit`, `loop-at`, `legato`,
  `sustain`/`clip`, `n`, `speed` (alias of `rate` or vice versa), `unit`.
- Audio-level: `cut` choke groups (voice stealing per group in the scheduler/worklet),
  `unit :c` speed computation once sample length is known.
- Retrigger-safe: TL1's onset-only triggering is required so slices don't double-fire.

## Open design questions

- `rate` vs Tidal `speed`: keep both names (one as alias) for parity.
- Where does `cut` live? JS sample playback path vs WASM; must be identical in offline render.

## Definition of done

- [ ] `(chop 4 (sound :break 0))` → 4 events with `begin` 0, 1/4, 1/2, 3/4 and matching `end`.
- [ ] `(slice 8 (seq 0 3) …)` → begin/end 0–1/8 and 3/8–1/2.
- [ ] `(splice 4 …)` sets speed so each slice fills its slot (unit test with a known sample length).
- [ ] `(cut 1 …)` stops the previous voice in group 1: offline render shows the earlier sample truncated.
- [ ] Offline render of a chopped break has onsets at expected times (±1 ms).
- [ ] Human listen check recorded in the PR (two demo snippets).
- [ ] Rule 3 registration; USAGE.md "Sample slicing" section; parity rows ✅.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.

## What NOT to do

- No granular synthesis (backlog).
- No per-event filters/effects: **TL7**.
- No new sample sources or kits: **KIT1**.
