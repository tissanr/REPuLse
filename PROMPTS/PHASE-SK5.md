# Phase SK5 — MIDI Capture, Stage 3: Grid Detection & Feel

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🤖👂 auto + human check — detection and feel extraction are pure and testable against synthetic performances with known grid, swing and humanisation; tuning thresholds needs recordings of real players and a human judging whether the result "feels" right.

**Milestone:** M4 — Sketchpad
**Depends on:** SK3 (capture pipeline), SK4 (always-on buffer), TL3 (`swing`, `nudge` and related time combinators).

## Goal

Stop forcing everything onto a fixed 16th grid. Detect the grid the player actually
used (straight 16ths, triplets, mixed) and keep their **feel** — swing and consistent
push/pull — as code (`swing`, `nudge`) instead of quantising it away.

```lisp
;; Before (SK3): everything snapped to the chosen grid; the groove is lost.
(~ "c4 e4 g4 e4 c4 e4 g4 e4")

;; After (PROPOSAL): grid detected as 8ths, swing kept
(->> (~ "c4 e4 g4 e4 c4 e4 g4 e4")
     (swing 0.12)         ; detected 62% swing on off-beats
     (nudge -0.01))       ; player sits slightly ahead of the beat
```

## Background

- SK3 quantiser and generator (fixed grid) and SK4 ring buffer.
- `swing`, `nudge` (and possibly `late`/`early`, already in
  `packages/core/src/repulse/core.cljs`) as the code targets — `swing`/`nudge` arrive in TL3.
- Tempo detection: `tap!` in `app/src/repulse/audio.cljs` (BPM from taps) — the only existing tempo-inference code.

## Scope

- Grid detection (pure): choose among candidate grids (8, 16, 32, triplet 8/16, mixed per beat) by minimising quantisation error with a complexity penalty.
- Feel extraction: swing ratio on off-beats; global offset (ahead/behind); optionally per-step micro-timing as a pattern.
- Optional tempo detection when capturing without a running session.
- Code generation picks the simplest code that reproduces the performance within a tolerance.
- User control: "keep feel" toggle and a strictness setting; fallback to SK3's fixed grid.

## Open design questions

1. Mixed grids within one bar: mini-notation subdivisions (`[a b c]` for triplets) vs. separate patterns?
2. How much micro-timing to keep before code becomes unreadable?
3. Default: keep feel on or off?
4. Reference set of real performances for tuning — where does it live in the repo (`e2e/fixtures/`?).

## Definition of done

- [ ] Synthetic straight-16th performance with ±10 ms jitter is detected as a 16th grid with no swing (unit test).
- [ ] Synthetic swung-8ths performance (off-beats delayed by a known ratio) is detected as 8ths with swing within ±0.02 of the true value (unit test).
- [ ] Triplet performance is detected as a triplet grid and generates bracketed triplet subdivisions (unit test).
- [ ] Constant 15 ms early playing becomes a `nudge` of ≈ −15 ms (unit test).
- [ ] Generated code re-rendered as events matches the original performance within the tolerance (round-trip unit test).
- [ ] "Keep feel" off reproduces SK3 output exactly (regression test).
- [ ] Manual check: two or more real recorded performances are captured and judged by a human to keep their groove (recorded in PR).
- [ ] `docs/USAGE.md` documents grid detection and feel options.

## What NOT to do

- No audio-to-MIDI transcription.
- No harmonic analysis or key detection (backlog "harmonic trace").
- Don't change the fixed-grid default flow of SK3 unless the feel toggle is on.
- No generative "humanize" functions for non-captured patterns (separate idea).
