# Phase SONG2 — Song-Time Automation & Transitions

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🤖 auto — automation curves are pure functions of song time and transitions are pattern transforms, both unit-testable; offline render tests can assert parameter values over time. A final listen is nice-to-have, not required for acceptance.

**Milestone:** M2 — Full songs in code
**Depends on:** SONG1 (song form and song position), TL2 (continuous signals), TL7 (per-event effect parameters, for automating `lpf`, `room`, …).

## Goal

Give songs movement across sections: parameters that change over the song timeline
("open the filter over the 8 bars before the drop") and transitions between sections
(fills, risers, drops) — written in code, addressed by section and bar.

```lisp
;; Before: tween (T1) only ramps global amp/pan, restarts on every re-eval,
;; and knows nothing about sections.
(->> kick (amp (tween :linear 0.2 1.0 2)))

;; After (PROPOSAL)
(song
  (section :build 8 [:kick :hats :bass]
    (automate :bass :lpf (ramp :exp 300 4000)))   ; over the whole section
  (section :drop 16 :all)
  (transition :build :drop
    (fill :snare (~ "sd*8"))                       ; last bar of :build
    (riser 1)))                                    ; 1-bar noise riser
```

## Background

- `tween` — `packages/lisp/src/repulse/lisp/builtins/params.cljs` returns a descriptor
  map; `arm-transitions!` in `app/src/repulse/audio.cljs` queries the first cycle and
  sends one `transition` message to the WASM engine; `schedule-cycle!` replaces tween
  descriptors with neutral values. Only `"amp"` and `"pan"` are supported, globally, and
  the ramp restarts on re-evaluation (see PROMPTS/PHASE-T1.md).
- `interp-tween` / `:tween-state` in `audio/scheduler-state` — per-track tween state.
- Song form and song position come from SONG1.
- Continuous signals (`sine`, `saw`, `range`, …) come from TL2; per-event parameters
  such as `lpf`/`room` come from TL7.

## Scope

- Automation lanes: a parameter on a track (or global) follows a curve over song time,
  scoped to a section or an explicit bar range.
- Curves: at least linear, exponential, and "hold"; plus "follow a continuous pattern" (TL2 signals).
- Automation is deterministic in song time: seeking/starting from a section gives the
  same values as playing through (unlike T1's restart-on-eval).
- Transitions: replace the last N bars of a section with a fill; built-in riser/drop
  helpers implemented as ordinary patterns/params (no special audio path if avoidable).
- Automation works in full-song render (SONG1 export).

## Open design questions

1. Evaluate automation per event (pattern-level param values) or as audio-rate ramps in
   WASM (T1 path)? Per-event is simpler and deterministic; ramps are smoother for gain.
2. Should automation live inside `section` forms, in a separate lane block, or both?
3. How does `tween` relate — deprecate, re-implement on top of automation, or keep?
4. Are risers/drops built-ins, or demo/snippet code built from existing primitives?

## Definition of done

- [ ] A pure function maps (song form, track, param, bar position) → automated value; covered by `cljs.test` (start, middle, end of a section; seek into the middle).
- [ ] Starting playback from a section yields the same automated values as playing through to it.
- [ ] `(automate :bass :lpf (ramp :exp 300 4000))` produces monotonically rising `lpf` values on `:bass` events across the section (event-level test).
- [ ] A fill replaces only the last N bars of the source section; other bars unchanged (event-level test).
- [ ] Offline render of a song with an amp ramp shows rising RMS across the section (TEST1 harness).
- [ ] Re-evaluating the buffer mid-song does not restart automation from zero.
- [ ] New built-ins follow CLAUDE.md Rule 3; `docs/USAGE.md` documents automation and transitions with examples.

## What NOT to do

- No song form changes beyond what automation needs — SONG1 owns sections.
- No new DSP effects (risers built from existing synths/noise only) — DSP is out of the M2 scope.
- No MIDI CC recording into automation lanes — that belongs with N1 / capture phases.
- No timeline drag-and-drop editing of curves.
