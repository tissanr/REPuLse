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

(export :song)           ; capture the whole song to WAV (real time, decision 4)
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
  `OfflineAudioContext` and writes a 16-bit WAV. The offline path does **not** sound like
  playback:
  - `play-event` skips the WASM worklet when the context is offline
    (`app/src/repulse/audio.cljs`, `offline?` branch) and uses JS fallbacks — every FM
    preset (`:sax`, `:epiano`, `:bell`, …), every plucked string (`:guitar`, `:harp`, …)
    and plain notes render as a **sine**.
  - `output-for-track` routes offline voices straight to the destination, so
    **per-track and global FX are missing**; `export.cljs` doesn't even pass the track name.
  - Closing that gap offline is Phase EXP1. SONG1 sidesteps it with a real-time capture
    (decision 4).
- Timeline panel `app/src/repulse/ui/timeline.cljs` — per-track visualiser, no song view.

## Scope

- A song form: named sections with bar lengths and per-section track selection.
- Song position state in the scheduler (current section, bar within section, total length).
- End behaviour: `:stop` (default) or `:loop`; transport stops cleanly at the end.
- Start-from-section for rehearsal: `(play-from :chorus)` or `(play-from 17)` (decision 7).
- Full-song WAV export by **real-time capture** of the master output (decision 4): length
  computed from the song form, sound identical to playback (WASM voices, track FX, master FX).
- Context panel / timeline shows current section name and song progress.
- Backward compatibility: `arrange` and `play-scenes` keep working unchanged.

## Design decisions

All questions below were decided by the project owner on 2026-10-28.

1. **`song` is an ordinary built-in, not a special form.** `(song …)` evaluates its
   `section` arguments to plain data and returns a song value, which is registered with
   the scheduler (like `track` registers a track). It validates every referenced track
   name against the tracks registered *above it* in the buffer and reports unknown names
   with a fuzzy-match suggestion. This respects top-to-bottom evaluation: tracks first,
   song last. At most one `song` per buffer (a second one is an eval error).
   **Decided:** ordinary built-in returning a song value.

2. **`track` registers; activation happens after the whole buffer has evaluated.**
   Today `track` calls `audio/play-track!` during evaluation, so tracks would start
   before the song below them is known. Change: during evaluation, `track` only collects
   `name → pattern`; `eval_orchestrator` activates the collected tracks once evaluation
   has finished. If the buffer contains a `song`, the song decides which tracks sound in
   each bar. If it doesn't, all collected tracks play exactly as today, so live-coding
   behaviour without `song` is unchanged.
   **Decided:** register during eval, activate at end of eval; without `song`, unchanged.

3. **Sections hold track lists only (plus `:all`).** No per-section pattern overrides in
   SONG1; a variant is just another track (`(track :bass-chorus …)`). The `section` form
   must accept further trailing arguments without breaking, because SONG2 puts
   `(automate …)` inside `section`. Unknown trailing arguments are an eval error in SONG1.
   **Decided:** track lists and `:all`; `section` stays extensible for SONG2.

4. **The export must sound exactly like playback.** The current offline path can't do
   that (sine fallbacks, no FX, see Background). SONG1 therefore exports the song by
   **real-time capture**: a small recorder `AudioWorkletProcessor` tapped after the master
   FX chain (between the last global effect and the analyser) records PCM while the song
   plays once from bar 0. Capture is trimmed by audio-clock frame positions, from the
   song's scheduled start time to its end plus a release tail (default 2 s, so reverb
   and decays aren't cut), and written as 16-bit stereo WAV with the existing encoder.
   Export takes as long as the song; the context panel shows progress and the
   export can be cancelled with `(stop)`. A fast offline render with the same sound
   (WASM worklet + FX chains in an `OfflineAudioContext`) is **Phase EXP1**, scheduled
   before SK1, which needs it for stems.
   **Decided:** real-time capture in SONG1; offline parity in EXP1.

5. **1 cycle = 1 bar.** Section lengths are in bars, which equal cycles. No time
   signatures in SONG1; that would be its own later phase.
   **Decided:** keep 1 cycle = 1 bar.

### Additional decisions

6. **Re-evaluating during playback keeps the song position.** The new song form takes
   over at the current bar. If the song has become shorter than the current position,
   playback continues at the start of the last section. No jump back to bar 0.
7. **Rehearsal start is a separate built-in: `(play-from :chorus)`.** It starts (or
   relocates) playback at the first bar of the named section; unknown section names get
   a fuzzy-match suggestion. `play-from` also accepts a bar number (`(play-from 17)`).
8. **Mute always wins.** A muted track stays silent even in a section that lists it;
   `solo` likewise keeps its current meaning on top of the section selection.

## Definition of done

- [ ] A buffer with tracks + a song block plays sections in order and stops after the last bar (`:end :stop`).
- [ ] `:end :loop` loops the whole song; `arrange`/`play-scenes` behaviour is unchanged (existing tests pass).
- [ ] Section/track activation is computed by a pure function in `packages/core` with `cljs.test` coverage (active tracks for any bar, total length, section at bar N).
- [ ] Starting from a named section begins playback at that section's first bar.
- [ ] The context panel shows the current section name and bar position.
- [ ] Song length is a pure function in `packages/core` with `cljs.test` coverage (total bars, duration in seconds at a given BPM, start bar of a named section).
- [ ] `(export :song)` captures one pass in real time and writes a WAV whose frame count equals song duration + tail (± 1 render quantum of 128 frames). Asserted by a Playwright test against a real `AudioContext` on a short song (2 bars at high BPM).
- [ ] The captured WAV contains WASM-voice output, not the JS sine fallback: a test song using `:saw` shows energy at the 2nd and 3rd harmonics.
- [ ] Event-level test: a song whose outro has only hats schedules no kick events during the outro.
- [ ] Re-evaluating mid-song keeps the bar position; a song shortened below the current bar continues at the last section's first bar (unit test on the pure position function).
- [ ] A muted track stays silent in a section that lists it.
- [ ] A buffer without `song` behaves exactly as before (existing track tests pass unchanged).
- [ ] New built-ins follow CLAUDE.md Rule 3 (grammar, completions, `builtin_meta.edn`, `gen:ai-docs`).
- [ ] `docs/USAGE.md` gains a "Writing a song" section with a complete example.

## What NOT to do

- No automation or transitions (filter sweeps, risers, fills) — that is SONG2.
- No outline panel or click-to-definition — that is SONG3.
- No multiple buffers / `use` — that is SONG4.
- No stem export or MIDI song export — that is SK1.
- No offline (faster-than-real-time) render with WASM voices and FX — that is EXP1.
- Don't change the existing `(export n)` / `(export :track)` offline path.
- Don't remove or change the semantics of `arrange` / `play-scenes`.
