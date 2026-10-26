# Phase TL7 — Per-Event Effects & Control Parameters

> **Spec status:** outline — expand into a full spec before implementing. Take the exact
> parameter list from the TL7 rows of [docs/STRUDEL-PARITY.md](../docs/STRUDEL-PARITY.md).
>
> **Automation:** 🤖👂 auto + human check — parameter plumbing, Rust filter tests and
> offline renders (RMS/spectral-peak assertions) are automatic; the musical result needs
> a listen. Follow CLAUDE.md "FM preset tests" guidance: no fake tonal assertions.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL1 (patterned params), TL2 (signals to drive them)

## Goal

Make effects part of the pattern, as in Tidal/Strudel: every event can carry its own
filter, distortion, reverb send and delay send, and those values can be patterned and
modulated by signals. This is the biggest remaining expressiveness gap.

```lisp
;; Before — effects are global or per track
(fx :filter 800)
(track-fx :bass :filter 600)

;; After (proposed)
(->> (fast 8 (seq :hh))
     (lpf (range 400 6000 (slow 4 sine)))
     (resonance 0.3))
(->> (seq :c3 :eb3 :g3) (synth :saw) (room 0.4) (delay 0.25) (shape 0.3))
(->> (seq :bd :sd) (orbit 1))     ; send group: room/delay per orbit
```

## Background

- Per-event params exist for `amp`, `attack`, `decay`, `release`, `pan`, `rate`, `begin`,
  `end`, `loop-sample` (Phase H/L) and are dispatched through `play-event` in
  `app/src/repulse/audio.cljs` to WASM `trigger_v2` or the JS sample path.
- Global `fx` chain in `app/src/repulse/fx.cljs`; per-track chains via `track-fx` (Phase L).
- WASM has `Biquad` (`peaking_eq`, `highshelf`, …) from SYN1 and distortion/waveshaper
  code from DST1–DST5 in `packages/audio/src/lib.rs`.

## Scope

- **Per-voice insert parameters** (inside the voice, allocation-free): `lpf`/`cutoff`,
  `hpf`, `bpf`, `resonance`, `shape`/`distort`, `crush`, `coarse`, `vowel`, `gain`
  (alias policy vs `amp`), `velocity` (if not already added by KIT1), filter envelope if
  parity requires.
- **Send parameters** (shared effects): `room`, `size`, `delay`, `delay-time`,
  `delay-feedback`, `orbit`. Each orbit owns one reverb + one delay, mixed in by
  per-event send level, like SuperDirt.
- Same behaviour for samples (JS path) and synth voices (WASM path), and in offline render.

## Open design questions

- Samples currently bypass WASM: route samples through WASM voices, or duplicate the
  filter in the JS path? Decide with a measurement; the offline renderer must match.
- How many orbits by default (SuperDirt: 12; recommend 4) and how they relate to
  `track-fx` (orbits per track vs global).
- Keep `fx` global effects as master-bus processing; document the difference.

## Definition of done

- [ ] Rust tests: per-voice LPF at 500 Hz attenuates a 5 kHz tone by ≥ 20 dB (FFT peak assertion).
- [ ] `(lpf 400 …)` and `(lpf 8000 …)` offline renders differ in spectral centroid by a large margin.
- [ ] `(room 0 …)` vs `(room 0.8 …)` offline render: tail energy after note-off differs.
- [ ] Parameters accept patterns and signals (`(lpf (range 400 6000 sine) …)`).
- [ ] Render callback stays allocation-free (per CLAUDE.md WASM rules); no regressions in Rust tests.
- [ ] Human listen check recorded in the PR (filter sweep, reverb send, distortion).
- [ ] Rule 3 registration; USAGE.md "Effect parameters" table; parity rows ✅.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.

## What NOT to do

- No reorderable/parallel master chains: deferred **P2**.
- No new synthesis models: deferred **SYN3–5**.
- No automation over the song timeline: **SONG2**.
