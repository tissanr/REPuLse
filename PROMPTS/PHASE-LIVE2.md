# Phase LIVE2 — Latency Compensation, Nudge & Per-Track Audio Outputs

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🧑 human-in-loop — offset arithmetic is unit-testable, but whether REPuLse sits tight with external gear, and how multichannel audio interfaces behave, can only be judged with real hardware and other instruments throughout.

**Milestone:** M3 — Plays with others
**Depends on:** LIVE1 (Link/OSC timing benefits from the same offsets); N1 (MIDI CC mapping) recommended for a hands-on nudge control.

## Goal

Make REPuLse usable as one instrument in a rig: compensate for audio/MIDI output
latency so it lines up with other gear, let the performer nudge timing live, and —
where the browser and audio interface allow — send individual tracks to separate
audio outputs (e.g. drums to outputs 3/4 of an interface for a mixer or DAW).

```lisp
;; Before: no user-facing timing offsets; all tracks mix to one stereo output.

;; After (PROPOSAL)
(latency! :audio 12 :midi -5)   ; ms offsets per output path
(nudge! 3)                      ; temporary +3 ms push (also on a key/MIDI CC)
(output :drums [3 4])           ; route a track to interface channels 3/4
(outputs)                       ; list available output channels/devices
```

## Background

- Clock/latency helpers: `app/src/repulse/audio_clock.cljs` — `latency-s` (prefers
  `outputLatency`, falls back to `baseLatency`), `audible-perf-ms`, used for visual sync.
  `clock-snapshot` in `app/src/repulse/audio.cljs` records `:output-latency` / `:base-latency`.
- Scheduling: `schedule-cycle!` / `tick!` in `audio.cljs`; MIDI out via
  `send-note-on!` / `send-note-off!` in `app/src/repulse/midi.cljs`; MIDI clock out via
  `start-clock!`.
- Per-track audio: `ensure-track-node!` creates a `GainNode` per track connected to
  `master-gain`; `output-for-track` picks the node a source connects to; per-track FX
  chains live in `track-nodes`. All tracks end in one stereo destination.
- Web Audio constraints: multichannel output depends on `destination.maxChannelCount`
  and browser support; device selection via `AudioContext.setSinkId` (Chromium). These
  limits are **unverified for REPuLse** and must be prototyped first.

## Scope

- Per-path offsets (audio, MIDI out, MIDI clock out, OSC/Link from LIVE1) applied at
  scheduling time; persisted in the session.
- Live nudge (temporary offset that decays or is cleared) bound to a key/command and
  mappable to a MIDI CC.
- Optional calibration helper (tap along to a click to estimate round-trip offset).
- Per-track output routing to channel pairs when `maxChannelCount > 2`; output device
  selection where supported; clear fallback message elsewhere.
- Latency/jitter readout in the context panel.

## Open design questions

1. Can the WASM AudioWorklet path output > 2 channels with today's architecture, or
   does per-track routing require a channel merger after the worklet? (Prototype first.)
2. Is per-track output worth it given browser limits, or should it shrink to "pick output
   device" + stems via SK1? Decide after the prototype.
3. Nudge semantics: tempo-bend (DJ-style) or fixed time shift?

## Definition of done

- [ ] Offset math is a pure function with unit tests (event time → audio/MIDI/OSC send time per path).
- [ ] `(latency! :midi -5)` shifts MIDI note timestamps by −5 ms relative to audio (unit test on scheduled timestamps).
- [ ] Offsets persist across reload (session test).
- [ ] With an external MIDI synth and REPuLse audio together, a calibrated offset makes them audibly aligned (manual check recorded in PR).
- [ ] Nudge is reachable from a key and a MIDI CC and returns to 0 when released/cleared.
- [ ] On a multichannel interface, `(output :drums [3 4])` sends only drums to channels 3/4 (manual check), or the phase documents why this was descoped.
- [ ] On stereo-only setups, routing commands fail with a clear message; nothing else changes.
- [ ] `docs/USAGE.md` documents offsets, nudge and outputs with limitations per browser.

## What NOT to do

- No Link/OSC transport work — LIVE1 owns the bridge.
- No stem *export* — that is SK1 (offline, not live routing).
- No changes to synthesis or effects.
- Don't promise multichannel output before the prototype confirms browser support.
