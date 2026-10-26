# Test protocol — Phase TL2: Continuous signals

| | |
|---|---|
| Phase | TL2 — Continuous signals ([spec](../../PROMPTS/PHASE-TL2.md)) |
| Delivered in | [tissanr/REPuLse#133](https://github.com/tissanr/REPuLse/pull/133) (auto-phase, merged into `dev` 2026-10-26) |
| Automated coverage | `tl2-*` tests in `packages/core/src/repulse/core_test.cljs` and `packages/lisp/src/repulse/lisp/integration_test.cljs`, `structure-guard-test`, e2e `bare sine signal renders silence without throwing` and `pan sine2 over eight hats has eight onsets` |
| This protocol covers | what the automated tests can't check: how it sounds, what the app shows, editor behaviour, regressions |

**Tester:** ________ **Date:** ________ **Commit:** ________ **Browser:** ________

## Setup

1. `npm run dev:full` (or `npm run dev` if WASM is already built), open http://localhost:3000.
2. Use headphones: several tests check stereo panning.
3. For each test: clear the editor, paste the snippet, press **Alt+Enter** (macOS **Option+Enter**).
   Stop playback (■) between tests unless the test says otherwise.
4. Keep the browser console open (tests 9, 10).

Note names below assume `scale` degrees start at **1** (degree 1 of `:major :c4` is C4,
degree 0 is B3). That is existing REPuLse behaviour, not new in TL2.

## A. Signals you can hear

### 1. Stereo sweep with `sine2`
```lisp
(pan sine2 (fast 8 (seq :hh)))
```
**Expected:** 8 hi-hats per cycle moving smoothly **centre → right → centre → left**, then
again every cycle. No clicks, no jumps.
Reference pan values: `0 0.71 1 0.71 0 -0.71 -1 -0.71`.
- [ ] Pass  - [ ] Fail  Notes: ____________

### 2. Four-cycle volume swell
```lisp
(->> (fast 16 (seq :hh)) (amp (range 0.3 0.9 (slow 4 saw))))
```
**Expected:** 16 hats per cycle getting steadily louder over **4 cycles** (amp 0.3 → 0.89),
then dropping back to quiet and rising again.
- [ ] Pass  - [ ] Fail  Notes: ____________

### 3. Smooth random drift with `perlin`
```lisp
(pan (range -0.5 0.5 perlin) (fast 4 (seq :hh)))
```
**Expected:** 4 hats per cycle wandering slowly in the stereo field, never further than
half-left or half-right, with no sudden jumps. Starts slightly left of centre
(reference pan: `-0.5 -0.45 -0.24 -0.04 | 0.02 -0.03 -0.22 -0.41 | …`).
- [ ] Pass  - [ ] Fail  Notes: ____________

### 4. Deterministic random melody with `rand`
```lisp
(->> (segment 8 (range 1 8 rand)) (scale :minor :c4))
```
**Expected:** 8 notes per cycle, a different phrase in each cycle.
Cycle 1: **C4 E♭4 G4 G4 D4 C4 D4 E♭4**; cycle 2: **F4 G4 D4 A♭4 E♭4 F4 A♭4 E♭4**.
Then stop, reload the page and evaluate again: **the same notes come back in the same order.**
- [ ] Pass  - [ ] Fail  Notes: ____________

### 5. Random integers with `irand`
```lisp
(->> (segment 8 (irand 8)) (scale :major :c4))
```
**Expected:** 8 notes per cycle. Cycle 1: **B3 D4 G4 F4 D4 C4 C4 E4**; cycle 2: **F4 G4 D4 A4 E4 F4 A4 E4**.
Same notes after a reload.
- [ ] Pass  - [ ] Fail  Notes: ____________

### 6. `segment` with a patterned count
```lisp
(->> (segment "<4 8>" (range 0 7 saw)) (scale :major :c4))
```
**Expected:** cycles alternate between **4 notes** (B3 C4 E4 G4) and **8 notes**
(B3 B3 C4 D4 E4 F4 G4 A4), each a rising line.
- [ ] Pass  - [ ] Fail  Notes: ____________

### 7. Triangle and `run`
```lisp
(->> (segment 4 (range 0 7 tri)) (scale :major :c4))
```
**Expected:** up and down: **B3 E4 B4 E4** every cycle.
```lisp
(->> (run 8) (scale :major :c4))
```
**Expected:** a rising scale of 8 notes, **B3 C4 D4 E4 F4 G4 A4 B4**, every cycle.
- [ ] Pass  - [ ] Fail  Notes: ____________

### 8. Exponential range with `rangex`
```lisp
(segment 8 (rangex 200 2000 saw))
```
**Expected:** 8 rising pitches per cycle (numbers play as Hz) that sound **evenly spaced**
(equal musical steps). Compare with `(segment 8 (range 200 2000 saw))`: there the low steps
sound large and the top steps sound small.
- [ ] Pass  - [ ] Fail  Notes: ____________

## B. Warnings and errors

### 9. A bare signal plays nothing and warns
```lisp
sine
```
**Expected:** silence, no crash, playback state stays usable. The output line (after the
usual "playing …" status) contains
`Warning: track :_ contains a continuous signal with no rhythm, which plays nothing. A signal needs structure, e.g. (segment 8 …) or (pan sine pat)`.
The console shows the same text **once** with the `[REPuLse]` prefix (not once per cycle).
- [ ] Pass  - [ ] Fail  Notes: ____________

### 10. The same for a named track
```lisp
(track :lfo sine)
(track :drums (seq :bd :sd))
```
**Expected:** drums play normally. The warning names `track :lfo`, once per evaluation.
- [ ] Pass  - [ ] Fail  Notes: ____________

### 11. Old voice shortcuts give a helpful error
```lisp
(saw :c4)
```
**Expected:** eval error (output shows `Error: …`, red squiggle in the editor): `saw is a signal (a continuous pattern), not a function. Use it without parentheses, e.g. (segment 8 saw). For the saw synth voice use (synth :saw pat), e.g. (synth :saw (seq :c4 :e4))`.
Repeat with `(square :c4)` (same text with `square`) and `(sine)` (`sine is a pattern, not a function. …`).
- [ ] Pass  - [ ] Fail  Notes: ____________

### 12. `rangex` rejects non-positive bounds
```lisp
(segment 4 (rangex 0 10 sine))
```
**Expected:** eval error `rangex needs positive bounds`, nothing plays.
- [ ] Pass  - [ ] Fail  Notes: ____________

## C. Regressions (things that must still work)

### 13. Saw and square synth voices
```lisp
(->> (seq :c3 :e3 :g3) (synth :saw))
```
```lisp
(->> (seq :c3 :e3 :g3) (synth :square :pw 0.25))
```
**Expected:** both play a buzzy 3-note arpeggio; the square one is thinner/hollow.
- [ ] Pass  - [ ] Fail  Notes: ____________

### 14. `saw` as a UGen inside `defsynth`
```lisp
(defsynth pluck [freq]
  (-> (saw freq)
      (lpf (* freq 2))
      (env-perc 0.01 0.3)))

(->> (scale :minor :c4 (seq 1 3 5 8))
     (synth :pluck)
     (amp 0.7))
```
**Expected:** a plucked 4-note minor arpeggio. No "is a signal" error.
- [ ] Pass  - [ ] Fail  Notes: ____________

### 15. Lisp `range` still makes lists
```lisp
(range 0 8 2)
```
**Expected:** output shows `(0 2 4 6)`; nothing plays.
- [ ] Pass  - [ ] Fail  Notes: ____________

### 16. `tween` still works
```lisp
(->> (seq :c4 :e4 :g4) (synth :saw) (amp (tween :linear 0.0 1.0 2)))
```
**Expected:** arpeggio fades in from silence over 2 bars, then stays at full volume.
- [ ] Pass  - [ ] Fail  Notes: ____________

### 17. Demos still play
Evaluate a few demos, e.g. `(demo :techno)`, `(demo :ambient)` and `(demo :dnb)` (or pick from
the demo menu).
**Expected:** each plays as before, with no new warnings or errors.
- [ ] Pass  - [ ] Fail  Notes: ____________

## D. Editor

### 18. Highlighting, completion, hover
1. Type `sine perlin segment rangex irand` in the editor.
   **Expected:** all five are coloured as built-ins.
2. Type `seg` and wait for completion. **Expected:** `segment` is suggested with a short description.
3. Hover `perlin`. **Expected:** a hover card describing it as a smooth random signal, with an example.
- [ ] Pass  - [ ] Fail  Notes: ____________

### 19. Known issue: names that start with a built-in
Type `(def sinewave 1)`.
**Known, not a blocker:** `sinewave` is coloured as `sine` + `wave` (the same already happens with
`seqx`). It evaluates correctly. Record only if it behaves *differently* from this.
- [ ] As described  - [ ] Different: ____________

## Result

- [ ] All pass. TL2 accepted.
- [ ] Failures. List test numbers and open an issue or fix PR: ____________
