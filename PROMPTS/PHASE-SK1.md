# Phase SK1 — Stem Export & Full-Song MIDI Export

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🤖 auto — offline rendering, WAV/MIDI file structure, durations and per-stem content are all assertable in the TEST1 offline harness and unit tests.

**Milestone:** M4 — Sketchpad
**Depends on:** SONG1 (song length and section form), EXP1 (offline render with WASM voices and FX — stems can't be captured in real time one by one). TL7 recommended (per-event effects to render into stems).

## Goal

Move an idea from REPuLse into a DAW in under a minute: render every track of a song
to its own WAV file (stems) of identical length, and export the whole song — all
melodic tracks, with velocity — as one multi-track MIDI file.

```lisp
;; Before: one track or the mix, fixed cycle count; MIDI one track at a time, channel 1
(export 4)
(export :drums)            ; 4 cycles only
(midi-export :bass 8)

;; After (PROPOSAL)
(export :stems)            ; one WAV per track, full song length, same start/end
(midi-export :song)        ; one .mid, one MIDI track per REPuLse track
```

## Background

- `(export …)` — `app/src/repulse/env/builtins/export.cljs`: accepts a cycle count *or*
  a track keyword (not both — a track export is always 4 cycles); renders via
  `audio/play-event` into an `OfflineAudioContext`; writes 16-bit stereo WAV inline.
  Offline render goes straight to the destination (`output-for-track` in
  `app/src/repulse/audio.cljs`), so **per-track and global FX are not rendered**.
- `(midi-export track n)` — `app/src/repulse/env/builtins/midi.cljs`: one track, fixed
  channel 1, no velocity; converts Hz/note keywords via `theory/note->hz` and
  `midi/hz->midi`; file written by `midi/export-midi` in `app/src/repulse/midi.cljs`
  as a **Type 0** file with fixed velocity 100 and 480 ppqn.
- **Existing bug to fix first:** the `midi-export` built-in computes
  `:time-sec (* (- ps c) cycle-dur)` — time *within* the cycle, not absolute — so all
  exported cycles are stacked on top of cycle 0. Add a regression test.
- Song length comes from SONG1.
- TEST1 harness: `app/src/repulse/test_api.cljs` (`render-offline`, `channel-array`) and
  `e2e/audio-render.spec.ts`.

## Scope

- Stems: render each track separately for the full song length; identical frame counts;
  sensible file names (`<project>-<track>.wav`); download as multiple files or a zip (?).
- Optional "with FX" rendering (per-track FX chain rebuilt in the offline context) —
  include if feasible, otherwise document as a limitation.
- Full-song MIDI: format-1 file, one MIDI track per REPuLse track with notes, tempo
  meta event, track names; velocity from `amp`; section markers as MIDI markers.
- Drum tracks: map sample keywords to General MIDI drum notes on channel 10 (configurable).
- Fix `(export :track n)` to accept both a track and a cycle count.

## Open design questions

1. Multiple downloads vs. a zip (needs a zip writer — no external CLJS libs in core, but app may allow one?).
2. Render FX into stems now, or after TL7 per-event effects land?
3. Velocity mapping: `amp` 0–1 → velocity 1–127 linear, or a curve?

## Definition of done

- [ ] `(export :stems)` produces one WAV per active track; all have identical length equal to the song length (offline harness test).
- [ ] Summing the stems equals the mix export within a small tolerance (offline harness test), or the FX limitation is documented.
- [ ] `(export :drums 8)` exports 8 cycles of a single track.
- [ ] `(midi-export :song)` writes a valid format-1 file: header, tempo meta, one named track per REPuLse track (unit test parsing the bytes).
- [ ] Note velocities reflect `amp` (unit test: amp 0.5 → velocity ≈ 64).
- [ ] Drum keywords map to GM drum notes on channel 10 (unit test).
- [ ] Section names appear as MIDI marker events at the right ticks (unit test).
- [ ] `docs/USAGE.md` documents stems and song MIDI export with a DAW import walkthrough.

## What NOT to do

- No live per-track audio outputs — that is LIVE2.
- No FLAC/MP3 encoding (backlog).
- No loop-region export / timeline UI (backlog).
- No MIDI import — capture is SK3–SK5.
