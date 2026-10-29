# Phase EXP1 — Export Parity: Offline Render with WASM Voices & FX

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🤖 auto — parity is measurable: render the same pattern offline and through a real-time capture, then compare spectra/RMS in Playwright.

**Milestone:** M4 — Sketchpad
**Depends on:** SONG1 (song length, real-time capture as the reference). TL7 recommended (per-event effects).

## Goal

Make the offline (faster-than-real-time) render sound exactly like playback. Today the
`OfflineAudioContext` path renders every WASM voice as a JS sine fallback and drops all
FX, so `(export 8)` sounds nothing like what you hear. EXP1 loads the WASM worklet and
rebuilds the per-track and global FX chains inside the offline context. SONG1's
`(export :song)` can then switch from real-time capture to a fast offline render, and SK1
gets stems that sound right.

```lisp
;; Before: offline export — :epiano renders as a sine, the reverb is missing
(track :keys (->> (seq :c4 :e4 :g4) (synth :epiano) (fx :reverb 0.4)))
(export 8)                 ; sine tones, dry

;; After: same sound as playback, faster than real time
(export 8)                 ; FM e-piano, with reverb
(export :song)             ; full song, offline, same WAV as the real-time capture
```

## Background

- `audio/play-event` in `app/src/repulse/audio.cljs` checks `offline?` and skips
  `worklet-trigger!` / `worklet-trigger-v2!`, falling back to `make-sine`, `make-saw`, …
  FM presets and Karplus-Strong presets have no JS equivalent and become sines.
- `init-worklet!` loads `worklet.js` via `audioWorklet.addModule`, transfers the WASM bytes
  through the node's port and waits for a `"ready"` message. `OfflineAudioContext` also
  has an `audioWorklet`, so the same steps work offline, but they must complete before
  `startRendering`.
- `output-for-track` routes offline voices straight to the destination: no track gain,
  no track FX chain, no master gain, no global FX.
- FX: `app/src/repulse/fx.cljs`. Global chain `fx/chain`, per-track chains in
  `audio/track-nodes`. Plugins expose `createNodes(ctx)`, `setParam`, `getParams` and
  optionally `clone()`. `bitcrusher.js` and `dattorro-reverb.js` load their own worklet
  modules (`/worklets/*-processor.js`), which must be added to the offline context as well.
- `app/src/repulse/env/builtins/export.cljs` — offline render + WAV encoder; does not pass
  the track name to `play-event`.
- SONG1 delivers `(export :song)` as a real-time capture; that capture is the reference
  this phase compares against.

## Scope

- Offline graph builder: master gain → global FX chain (cloned plugins, current params)
  → destination; per-track gain → track FX chain → master gain.
- WASM worklet in the offline context, initialised before rendering; voices routed per
  track as in the real-time graph.
- Plugin worklet modules (`bitcrusher`, `dattorro-reverb`) loaded into the offline context.
- `export.cljs` passes track names; `(export n)`, `(export :track)` and `(export :song)`
  all use the new offline path.
- Fallback: if the worklet can't initialise offline, export falls back to real-time capture
  (SONG1) and says so in the output panel, never silently to sines.
- Tweens re-armed at export start (bar 0) in both export paths (decision 3).

## Design decisions

All questions below were decided by the project owner on 2026-10-28.

1. **`:realtime` stays as an explicit option.** Default `(export :song)` uses the new
   offline path; `(export :song :realtime)` keeps SONG1's real-time capture. That path
   exists anyway, is the reference for the parity test and is the fallback when the
   offline worklet can't start.
   **Decided:** offline by default, `:realtime` as opt-in.

2. **Plugins are instantiated offline exactly like `fx/add-track-effect!`.** Use
   `clone()` when the plugin has one (the CLJS `compressor` does), otherwise
   `Object.create`/`Object.assign`, as today. Then call `createNodes(offline-ctx)` and copy
   the current state with `getParams` → `setParam`. Every audio plugin in
   `app/public/plugins/` already has `getParams`; plugins without it are visualisers
   (`oscilloscope`, `spectrum`, `p5-*`), which are skipped offline because they don't
   change the sound. If an audio plugin still can't be rebuilt (e.g. a third-party plugin
   without `getParams`), export falls back to real-time capture and prints which plugin
   caused it.
   **Decided:** reuse `add-track-effect!` instantiation + param replay; skip visualisers;
   real-time fallback otherwise.

3. **Tweens are re-armed at export start in both export paths.** Today `:tween-state`
   stores `:start-time` as the audio time of the evaluation (`arm-transitions!` in
   `app/src/repulse/audio.cljs`), so a tween runs from "when you pressed eval", not from
   bar 0. For any export (offline and `:realtime`), every tween's start is set to the
   export's bar 0. This makes exports reproducible and lets offline and real-time output
   agree. EXP1 also changes SONG1's real-time capture accordingly. SONG2 automation is
   already defined in song time and needs no special handling.
   **Decided:** re-arm tweens at export start (bar 0), offline and real time.

## Definition of done

- [ ] `(export 4)` of `(synth :epiano …)` contains energy at the FM sidebands, not only the fundamental (Playwright spectral test).
- [ ] `(export 4)` of a `:guitar` pattern is not a sine (harmonic content test).
- [ ] A track with `(->> pat (fx :reverb 0.4))` exports with a reverb tail: non-zero RMS after the last note's release.
- [ ] A global effect (`(fx :delay :wet 0.5 :time 0.25)`) is audible in the export (echo detected at the delay time).
- [ ] Offline `(export :song)` and the SONG1 real-time capture of the same song match in length exactly and in RMS per bar within 1 dB.
- [ ] `bitcrusher` and `dattorro-reverb` work in offline export (their worklets are loaded).
- [ ] If the offline worklet fails to initialise, export falls back to real-time capture and prints a notice (test with the worklet URL stubbed to fail).
- [ ] `(export :song :realtime)` still produces the SONG1 real-time capture.
- [ ] Visualiser plugins in a track's chain don't block or alter the offline export.
- [ ] A pattern with a 4-bar `tween` exports identically (RMS per bar within 0.1 dB) whether the buffer was evaluated just before export or several bars earlier, offline and `:realtime`.
- [ ] Existing TEST1 offline-harness tests still pass (the `"offline-js"` test backend is untouched).
- [ ] `docs/USAGE.md` export section no longer lists the "no FX / sine fallback" limitation.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5)

## What NOT to do

- No stems or MIDI export — that is SK1.
- No new FX plugins or changes to plugin DSP.
- Don't change the real-time audio graph.
- Don't change the TEST1 `"offline-js"` harness backend; it tests something else.
