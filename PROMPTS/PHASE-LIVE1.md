# Phase LIVE1 — Companion Bridge: Ableton Link & OSC Output

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🧑 human-in-loop — the protocol and message encoding can be unit-tested, but acceptance needs real Link peers (Ableton Live or another Link app) and an OSC receiver (e.g. SuperDirt) on the network throughout.

**Milestone:** M3 — Plays with others
**Depends on:** none in M1/M2 strictly; benefits from SONG1 (song position for Link phase alignment).

## Goal

Let REPuLse play in sync with other software and drive it. Browsers cannot speak
Ableton Link (UDP multicast) or OSC (UDP) directly, so this phase adds a small
optional **companion bridge** — a local helper process the browser talks to over
WebSocket — that joins a Link session and relays OSC.

```lisp
;; Before: only MIDI clock in/out (Phase N) for sync
(midi-sync! true)

;; After (PROPOSAL)
(bridge! "ws://localhost:7400")                 ; connect to the companion bridge
(link! true)                                    ; join the Link session: tempo + phase
(osc-out "127.0.0.1:57120" "/dirt/play"         ; send pattern events as OSC
  (->> (~ "bd sd [~ bd] sd") (s)))
```

## Background

- MIDI clock in: `set-midi-sync!` / `handle-clock-pulse!` in `app/src/repulse/audio.cljs`
  derive BPM from 24 ppqn pulses. MIDI clock out: `start-clock!` / `update-clock-bpm!` in
  `app/src/repulse/midi.cljs`. These are the existing sync paths.
- Tempo: `set-bpm!` / `get-bpm` and `:cycle-dur` in `audio/scheduler-state` (1 cycle = 1 bar).
- Scheduler: `tick!` / `schedule-cycle!` in `audio.cljs` (lookahead window, `:lookahead 0.2`).
- `docs/FUTURE-FEATURES.md` "Connectivity" sketches OSC via a ~30-line Node relay and
  Link via a bridge wrapping the Link SDK.
- Production CSP in `vercel.json` (`connect-src`) must allow `ws://localhost:<port>` —
  see AI2c spec for the same localhost/CSP concerns.
- No bridge exists today; implementation language/distribution is an open question.

## Scope

- Companion bridge (separate package, e.g. `bridge/`): WebSocket server on localhost;
  Link session (tempo, beat phase, start/stop sync); OSC UDP send.
- Browser client: connect/disconnect, status in the context panel, auto-reconnect.
- Link: REPuLse follows and proposes tempo; cycle start aligned to Link bar phase.
- `osc-out`: send pattern events as OSC messages with timestamps (OSC bundles), so the
  receiver schedules them accurately despite WebSocket jitter.
- Clear behaviour when the bridge is absent (REPuLse stays fully usable).

## Open design questions

1. Bridge implementation: Node (`abletonlink` npm bindings) vs. Rust (Link via C++ FFI) vs.
   a reused existing tool (e.g. an open-source Link-to-WebSocket bridge)?
2. Distribution: `npx repulse-bridge`, a downloadable binary, or both?
3. OSC message shape: SuperDirt-compatible (`/dirt/play` with `s`, `n`, …) by default?
4. Security: bind to localhost only; how does the browser authenticate to the bridge?
5. Link phase vs. REPuLse cycle: quantum = 4 beats = 1 cycle?

## Definition of done

- [ ] Bridge protocol (browser ↔ bridge messages) documented and unit-tested for encode/decode.
- [ ] OSC encoding unit tests: message + bundle with timetag, matching the OSC 1.0 spec byte layout.
- [ ] With the bridge running and Ableton Live (or LinkHut) in the session, tempo changes in either direction propagate within one bar (manual check, recorded in the PR).
- [ ] REPuLse bar 1 aligns with the Link bar phase within ~10 ms by ear/scope (manual check).
- [ ] `osc-out` drives SuperDirt (or an OSC monitor) with correctly timed events (manual check).
- [ ] With no bridge running, `(link! true)` returns a clear error and nothing else breaks.
- [ ] CSP allows the localhost bridge connection in production.
- [ ] `docs/USAGE.md` documents installation, commands and troubleshooting.

## What NOT to do

- No OSC *input* / parameter control from OSC in this phase (follow-on).
- No latency compensation or per-track outputs — that is LIVE2.
- No changes to the existing MIDI clock paths beyond sharing a tempo source.
- Don't make the bridge required for any existing feature.
