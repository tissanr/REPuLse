# Phase SK3 — MIDI Capture, Stage 1: Record to Code

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🤖👂 auto + human check — quantising and code generation are pure functions testable with synthetic MIDI note streams, and the record flow can be driven in Playwright with a mocked Web MIDI input; final acceptance needs a person playing a real keyboard against the click.

**Milestone:** M4 — Sketchpad
**Depends on:** SONG4 (insert into a new buffer), TL1 (patterned params); KIT1 (`velocity` parameter — falls back to `amp` if KIT1 is not delivered yet). Phase N (Web MIDI access) already delivered.

## Goal

Play a part on a MIDI keyboard and get REPuLse code back. Choose a tempo and a length,
get a count-in and a click, play, and the notes are snapped to a fixed grid and written
as **mini-notation** — as **scale degrees** when a scale is active, with **velocity**
captured as a `velocity` pattern. The result is inserted at the cursor or into a new buffer.

```lisp
;; Before: MIDI input only maps CCs (midi-map); played notes are ignored.

;; After (PROPOSAL)
(capture! :bars 2 :grid 16 :bpm 100)        ; count-in, click, record 2 bars
;; → inserted at cursor (scale :minor :c3 active in the buffer):
(->> (slow 2 (~ "0 ~ 2 4 [~ 2] 4 ~ 7 0 ~ 2 4 5 4 2 ~"))
     (scale :minor :c3)
     (velocity (slow 2 (~ "0.9 ~ 0.5 0.7 [~ 0.4] 0.8 ~ 1.0 0.9 ~ 0.6 0.7 0.8 0.6 0.5 ~"))))
```

## Background

- Web MIDI: `ensure-access!` in `app/src/repulse/midi.cljs` (Chrome/Edge only);
  `on-midi-message` currently handles **only CC (0xB0)** — Note On/Off input is not
  handled anywhere yet. MIDI clock input is handled separately in `app/src/repulse/audio.cljs`
  (`handle-midi-msg!`, `handle-clock-pulse!`).
- Note conversion: `theory/note->midi`, `theory/midi->hz`, `theory/note->hz` in
  `packages/core/src/repulse/theory.cljs`; scales via `theory/scale` (scale tables
  `scale-intervals`).
- Mini-notation parser: `packages/lisp/src/repulse/lisp/mini.cljs` — the generator's output must
  round-trip through it.
- Click: synth voices / samples via `audio/play-event`; tempo via `audio/set-bpm!`.
- Insertion: editor view in `app/src/repulse/ui/editor.cljs`; new-buffer API from SONG4.
- "Which scale is active" has no explicit state today — must be inferred from the buffer
  or chosen in the capture dialog (open question).

## Scope

- Note On/Off input handling (all channels or a chosen one) with `performance.now()` /
  event timestamps mapped to audio time.
- Capture dialog and `capture!` built-in: BPM, bars, grid (8/16/32, triplets?), count-in bars, click on/off, target (cursor / new buffer).
- Fixed-grid quantiser (pure): onset → nearest step; note-offs → durations (rests between notes).
- Polyphony: simultaneous notes become chords (see open question 2).
- Code generator (pure): mini-notation string; scale degrees when a scale/root is set
  (out-of-scale notes fall back to note names or accidental degrees — decide);
  optional `velocity` pattern (toggle).
- Preview: play back the captured code before inserting.

## Open design questions

1. How is "the active scale" determined — capture-dialog setting, or parsed from the buffer?
2. Chord notation: `mini.cljs` has **no `,` stack syntax** today. Extend mini-notation with Tidal's `[c4,e4,g4]` (fits TL parity work) or emit `stack`?
3. Long notes: `@N` weights vs. `_` holds vs. a separate `legato`/`decay`?
4. Velocity mapping curve and when to omit the `velocity` pattern (all velocities similar → omit).

## Definition of done

- [ ] Note On/Off input is received and timestamped (unit test with synthetic MIDI messages).
- [ ] Quantiser snaps onsets to a 1/16 grid at a given BPM; ±tolerance cases tested (unit tests).
- [ ] Generator outputs mini-notation that **round-trips**: parsing it yields events matching the quantised notes (unit test).
- [ ] With `(scale :minor :c3)` selected, C3–E♭3–G3 become `0 2 4` (unit test).
- [ ] Velocity 127/64 maps to ≈ 1.0/0.5 in the generated `velocity` pattern (unit test).
- [ ] Chords captured as simultaneous notes appear as stacked events (unit test).
- [ ] Count-in and click play at the chosen BPM; capture stops after N bars (Playwright with mocked MIDI input + offline/event assertions).
- [ ] Result inserts at the cursor or into a new SONG4 buffer (Playwright).
- [ ] Manual check: a person plays a 2-bar riff on a real keyboard and the inserted code plays it back recognisably (recorded in PR).
- [ ] `capture!` follows CLAUDE.md Rule 3; `docs/USAGE.md` documents the capture flow.

## What NOT to do

- No always-on/retroactive capture — that is SK4.
- No grid detection or swing/feel preservation — that is SK5.
- No audio (microphone) to code.
- No CC recording into automation lanes.
