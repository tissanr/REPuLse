# Phase SK4 — MIDI Capture, Stage 2: Always-On Capture

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🤖👂 auto + human check — the ring buffer, bar-alignment and "grab last N bars" logic are pure and testable with synthetic note streams; whether it catches what a player actually meant needs a real keyboard session.

**Milestone:** M4 — Sketchpad
**Depends on:** SK3 (note input, quantiser, code generator).

## Goal

Ideas happen when you're not recording. With **always-on capture** enabled, REPuLse
silently keeps listening to the MIDI keyboard in time with the running session; at any
moment you can grab "the last N bars" and turn them into code — no count-in, no take.

```lisp
;; Before (SK3): capture only after explicitly starting a take with a count-in.

;; After (PROPOSAL)
(capture-mode! :always)       ; keep listening while the session plays
;; …noodle along for a while…
(grab! 4)                     ; last 4 complete bars → code at the cursor
(grab! 2 :to :new-buffer)     ; or into a new buffer
```

## Background

- Note input, quantiser and mini-notation generator from SK3.
- Bar position: `audio/scheduler-state` (`:cycle`, `:cycle-dur`; 1 cycle = 1 bar) and
  the clock snapshot in `app/src/repulse/audio.cljs` give the bar grid to align to.
- Visual feedback can follow the existing timeline panel (`app/src/repulse/ui/timeline.cljs`)
  and context panel (`app/src/repulse/ui/context_panel.cljs`).
- Session persistence (`app/src/repulse/session.cljs`) — capture mode setting could persist; the buffer itself should not.

## Scope

- Ring buffer of timestamped note events covering the last M bars (e.g. 32), aligned to session bars.
- `grab!` (built-in + key/button + optional MIDI CC/note trigger): take the last N
  **complete** bars, quantise, generate code (SK3 generator), insert.
- Works without playback too: uses the capture BPM and the first played note as bar 1.
- Indicator showing capture is active and how many bars contain notes.
- Privacy/clarity: nothing is recorded unless the mode is on; clearing the buffer.

## Open design questions

1. "Last N bars" = last N complete bars, or ending now (partial bar)?
2. Without a running session, how is the bar grid anchored?
3. Should grabbing auto-trim leading/trailing empty bars?
4. Memory bound: fixed bar count vs. time window.

## Definition of done

- [ ] Ring buffer keeps exactly the configured number of bars and evicts older events (unit test).
- [ ] `(grab! 4)` returns code for the last 4 complete bars aligned to session bar boundaries (unit test with synthetic events spanning 6 bars).
- [ ] Grabbing with no notes in range gives a clear message and inserts nothing.
- [ ] Capture mode on/off persists across reload; the note buffer does not.
- [ ] Indicator shows active state (Playwright).
- [ ] Grab via key and via a mocked MIDI trigger both work (Playwright).
- [ ] Manual check: play along to a running pattern, grab 4 bars, inserted code matches what was played (recorded in PR).
- [ ] New built-ins follow CLAUDE.md Rule 3; `docs/USAGE.md` documents always-on capture.

## What NOT to do

- No grid detection or feel preservation — SK5.
- No audio recording.
- Don't change SK3's explicit-take flow; both modes coexist.
- No cloud storage of captured material.
