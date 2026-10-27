# Phase SONG1 — Sections, Song Form & Full-Song Render

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🤖 auto — song structure is pure pattern-engine logic plus an offline render, both verifiable with unit tests and the TEST1 offline-audio harness.

**Milestone:** M2 — Full songs in code
**Depends on:** TL1 (patterned arguments) recommended but not required; builds on Phase 8 `arrange`.

## Goal

Turn REPuLse from "loops forever" into "plays a song start to finish". A song is a
buffer of track definitions (top to bottom, define before use) with **one arrangement
block at the bottom** that names sections, gives them lengths in bars, and says which
tracks play in each. A song has an end: playback stops (or optionally loops) after the
last section, and the whole song can be rendered to WAV in one call.

```lisp
;; Before (Phase 8): sections are patterns, the arrangement loops forever
(def verse  (stack kick snare))
(def chorus (stack kick snare hats))
(arrange [[verse 8] [chorus 8]])

;; After (PROPOSAL — syntax to be settled in the full spec)
(track :kick  (seq :bd :_ :bd :_))
(track :snare (seq :_ :sd :_ :sd))
(track :hats  (fast 2 (seq :hh :_)))
(track :bass  (->> (scale :minor :c2 (seq 0 :_ 4 :_)) (synth :saw)))

(song
  (section :intro  4 [:kick :hats])
  (section :verse  8 [:kick :snare :bass])
  (section :chorus 8 :all)
  (section :outro  4 [:hats])
  :end :stop)            ; or :end :loop

(export :song)           ; render the whole song to WAV
```

## Background

- `arrange*` in `packages/core/src/repulse/core.cljs` — time-shifts a `[[pattern cycles] …]`
  plan; sections play in order and the arrangement **loops** (`(mod g-cycle total)`).
  There is no notion of an end, of section names, or of which *tracks* are active.
- `(arrange …)` / `(play-scenes …)` Lisp built-ins — `packages/lisp/src/repulse/lisp/builtins/arrangement.cljs`.
- `(track :name pat)` — `app/src/repulse/env/builtins/tracks.cljs`; calls
  `audio/play-track!` immediately at eval time and enforces unique names per buffer via
  `builtins/seen-tracks`.
- `audio/scheduler-state` in `app/src/repulse/audio.cljs` — `:tracks` (keyword → Pattern),
  `:muted`, `:cycle`, `:cycle-dur` (1 cycle = 1 bar at the current BPM).
- `(export n)` / `(export :track)` — `app/src/repulse/env/builtins/export.cljs`; renders a
  fixed number of cycles (default 4) through `audio/play-event` into an
  `OfflineAudioContext` and writes a 16-bit WAV. Offline render routes straight to the
  destination (`output-for-track`), so **per-track and global FX are not in the export**.
- Timeline panel `app/src/repulse/ui/timeline.cljs` — per-track visualiser, no song view.

## Scope

- A song form: named sections with bar lengths and per-section track selection.
- Song position state in the scheduler (current section, bar within section, total length).
- End behaviour: `:stop` (default) or `:loop`; transport stops cleanly at the end.
- Start-from-section for rehearsal (`(play-from :chorus)` or similar).
- Full-song WAV render whose length is computed from the song form, not a cycle count.
- Context panel / timeline shows current section name and song progress.
- Backward compatibility: `arrange` and `play-scenes` keep working unchanged.

## Open design questions

1. Is `song` a special data form evaluated last, or an ordinary function returning a
   song value the evaluator recognises? (Must respect top-to-bottom evaluation.)
2. Should `track` inside a song buffer *register* rather than *start playing* a track,
   so the song decides when it sounds? How does that coexist with live-coding mode?
3. Section contents: track lists only, or also per-section pattern overrides
   (`(section :chorus 8 {:bass bass-chorus})`)?
4. Should export include FX? Doing so requires rebuilding the FX chain in the offline
   context — decide whether that belongs here or in SK1.
5. Bars vs cycles: keep 1 cycle = 1 bar, or allow a time signature?

## Definition of done

- [ ] A buffer with tracks + a song block plays sections in order and stops after the last bar (`:end :stop`).
- [ ] `:end :loop` loops the whole song; `arrange`/`play-scenes` behaviour is unchanged (existing tests pass).
- [ ] Section/track activation is computed by a pure function in `packages/core` with `cljs.test` coverage (active tracks for any bar, total length, section at bar N).
- [ ] Starting from a named section begins playback at that section's first bar.
- [ ] The context panel shows the current section name and bar position.
- [ ] Full-song WAV render produces a file whose duration equals total bars × bar duration (asserted in the TEST1 offline harness).
- [ ] Offline render test: a song whose outro has only hats has silence in the kick frequency band during the outro (or event-level equivalent).
- [ ] New built-ins follow CLAUDE.md Rule 3 (grammar, completions, `builtin_meta.edn`, `gen:ai-docs`).
- [ ] `docs/USAGE.md` gains a "Writing a song" section with a complete example.

## What NOT to do

- No automation or transitions (filter sweeps, risers, fills) — that is SONG2.
- No outline panel or click-to-definition — that is SONG3.
- No multiple buffers / `use` — that is SONG4.
- No stem export or MIDI song export — that is SK1.
- Don't remove or change the semantics of `arrange` / `play-scenes`.
