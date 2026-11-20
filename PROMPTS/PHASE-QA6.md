# Phase QA6 — Audio & MIDI Timing Quality

> **Spec status:** outline. Expand it into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🤖👂 auto + human check. The MIDI dispatch and the numeric behaviour are testable with a mocked `requestMIDIAccess` and offline renders. Whether voice stealing clicks, and whether MIDI clock and sync hold tight against a real device, needs a human listen and real MIDI hardware.

**Milestone:** none (quality track)
**Depends on:** QA2 (allocation-free engine, hard voice cap). It relates to N1 (MIDI CC mapping), which touches the same `onmidimessage` path. Do whichever comes second on top of the first.

## Goal

Fix the timing and voice-management issues that need ears or hardware to accept:

- MIDI input handlers that overwrite each other,
- tempo sync that can't be switched off,
- MIDI clock out driven by a jittery `setInterval`,
- voice stealing instead of QA2's "drop when full",
- small DSP precision issues.

```lisp
;; Before: enabling sync silently disables CC mapping
(midi-map 74 :cutoff)
(midi-sync! true)       ; CC 74 no longer moves the filter
(midi-sync! false)      ; tempo still follows the external clock

;; After: both work together; sync off really stops following
(midi-map 74 :cutoff)
(midi-sync! true)       ; CC 74 still mapped, tempo follows clock
(midi-sync! false)      ; tempo stays where it is
```

## Background

- **Two MIDI handlers overwrite each other.** `app/src/repulse/midi.cljs` (around line 51)
  and `app/src/repulse/audio.cljs` (`handle-midi-msg!`, around line 1459) both assign
  `input.onmidimessage`, and the last one wins. `handle-midi-msg!` never checks
  `midi-sync-enabled?`.
- **MIDI clock out jitters.** `midi.cljs` `start-clock!` sends 24-ppqn pulses from
  `setInterval` (~20.8 ms at 120 BPM). That jitters, drifts, and is throttled in
  background tabs. Notes already use timestamped `output.send`.
- **Wall-clock timestamps.** Clock-in pulses and `tap!` use `Date.now()`. QA3 moves `tap!`
  to `performance.now()`; clock-in should use the `MIDIMessageEvent.timeStamp`.
- **Voice limit.** After QA2 the engine has a hard voice cap and drops new events when
  full. Dense patterns can hit the cap audibly.
- **DSP precision:**
  - No denormal handling in Biquad state or decaying gains (mostly masked by culling
    at 1e-4/1e-5).
  - Summed voices are hard-clipped at ±1. An opt-in limiter is FX1's job.

## Scope

1. One MIDI input dispatcher per input. It fans out to clock sync, the CC map (N1) and
   later capture (SK3). `midi-sync!` off stops tempo following immediately.
2. MIDI clock out is scheduled with timestamps from the lookahead scheduler
   (`output.send(bytes, t)`) for each cycle window, not from `setInterval`.
3. Clock-in uses the event timestamps, smoothed. It re-anchors tempo through QA3's anchor.
4. Voice stealing when the QA2 cap is reached: oldest released voice first, then the
   quietest. Short fade (≈5 ms), not a hard cut.
5. Flush denormals in filter state and envelopes. Golden-render check as in QA2:
   unchanged within 1e-6 for normal input.

## Open design questions

- **Q1. Stealing policy.** Oldest released voice first, else the quietest.
  *Recommendation:* yes, with a 5 ms fade. Verify by listening to a 300-voice stress
  pattern.
- **Q2. Clock-in smoothing window.** *Recommendation:* moving average over 24 pulses
  (one beat), re-anchoring at most once per beat.

## Definition of done

- [ ] With a mocked MIDI input, a CC message still reaches `midi-map` while sync is on.
      With sync off, clock pulses don't change `get-bpm`. Playwright, automated.
- [ ] Clock-out pulses are sent with timestamps. A mocked output records 24 pulses per
      beat with jitter < 1 ms against the expected times.
- [ ] Stress pattern over the voice cap renders with no NaN and no hard cuts in the
      offline render, checked numerically: no sample-to-sample jump > 0.5.
- [ ] **Hardware check (human):** REPuLse as clock master drives a hardware sequencer or
      DAW for 5 minutes without audible drift. As clock slave it follows a tempo change
      within one bar.
- [ ] **Listen check (human):** the stress pattern shows no clicks from voice stealing.
- [ ] `npm run test:all` passes. The PR ends with a `## Test protocol` that includes both
      human checks.

## What NOT to do

- Don't add a default-on master limiter. FX1 decides that (opt-in recommended there).
- Don't implement new MIDI features (capture, new CC targets). Those are N1 and SK3–SK5.
- Don't change Ableton Link or OSC. That's LIVE1.
