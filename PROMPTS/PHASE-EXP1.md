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

## Open design questions

1. Should `(export :song)` keep a `:realtime` option once offline parity exists (e.g. for
   debugging or browsers without offline worklets)?
2. Plugins without `clone()` or with internal state not exposed via `getParams`: refuse
   offline export for them, or fall back to real-time capture?
3. Tween state (`:tween-state`) and SONG2 automation: are they guaranteed deterministic in
   song time so that offline and real time agree, or does EXP1 need to replay them?

## Definition of done

- [ ] `(export 4)` of `(synth :epiano …)` contains energy at the FM sidebands, not only the fundamental (Playwright spectral test).
- [ ] `(export 4)` of a `:guitar` pattern is not a sine (harmonic content test).
- [ ] A track with `(->> pat (fx :reverb 0.4))` exports with a reverb tail: non-zero RMS after the last note's release.
- [ ] A global effect (`(fx :delay :wet 0.5 :time 0.25)`) is audible in the export (echo detected at the delay time).
- [ ] Offline `(export :song)` and the SONG1 real-time capture of the same song match in length exactly and in RMS per bar within 1 dB.
- [ ] `bitcrusher` and `dattorro-reverb` work in offline export (their worklets are loaded).
- [ ] If the offline worklet fails to initialise, export falls back to real-time capture and prints a notice (test with the worklet URL stubbed to fail).
- [ ] Existing TEST1 offline-harness tests still pass (the `"offline-js"` test backend is untouched).
- [ ] `docs/USAGE.md` export section no longer lists the "no FX / sine fallback" limitation.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5)

## What NOT to do

- No stems or MIDI export — that is SK1.
- No new FX plugins or changes to plugin DSP.
- Don't change the real-time audio graph.
- Don't change the TEST1 `"offline-js"` harness backend; it tests something else.
