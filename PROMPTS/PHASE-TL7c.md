# Phase TL7c — Vowel, Envelopes & Modulators

> **Spec status:** outline — expand into a full spec before implementing. Take the exact
> parameter list from the remaining 📋/🟡 TL7c rows of
> [docs/STRUDEL-PARITY.md](../docs/STRUDEL-PARITY.md).
>
> **Automation:** 🤖👂 auto + human check. Envelope shapes and parameter plumbing are
> testable offline; the musical result needs a listen. Follow CLAUDE.md "FM preset
> tests" guidance: no fake tonal assertions.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL7a (per-event insert chain, `trigger_v3` slots), TL7b (`delay-sync`
extends the orbit delay)

TL7 was split into three phases on 2026-11-10: **TL7a** per-event filters, shape &
crush; **TL7b** reverb & delay sends per orbit; **TL7c** (this phase) the rest of the
per-event control set.

## Goal

Complete the per-event control set. TL7a's filters only take one value per event. TL7c
lets them move *within* a note (filter envelopes), adds pitch envelopes and vibrato, and
adds the effects that native Web Audio nodes can't express (`coarse`, `vowel`).

```lisp
;; Proposed
(->> (seq :c2 :c2 :eb2 :g1) (synth :saw)
     (lpf 300) (lp-env 4) (lp-attack 0.01) (lp-decay 0.2))   ; acid-style pluck
(->> (seq :a3 :e4) (synth :saw) (vowel (seq :a :o)))
(->> (fast 4 (seq :hh)) (coarse (seq 1 4 8)))
(->> (seq :c5) (synth :square) (vib 6) (vib-mod 0.3))
(->> (seq :sd :_ :sd :_) (delay 0.4) (delay-sync 3/16))
```

## Scope (candidate list — the full spec cuts it to one phase)

- **Inserts:** `coarse` (sample-and-hold; no native node, see open questions) and
  `vowel` (formant filter bank: 3–5 bandpasses, as `BiquadFilterNode`s in JS and the
  same biquads in Rust, so it stays within TL7a's native-nodes rule).
- **Envelopes:** filter envelopes `lp-env`/`lp-attack`/`lp-decay`/`lp-sustain`/
  `lp-release` (and `hp-*`, `bp-*`); pitch envelope `penv` `p-attack` `p-decay`
  `p-release` …; `sustain`/`hold`; `adsr` shorthands.
- **Modulators:** `vib`/`vib-mod`; per-event `tremolo` and `phaser` (track-level plugins
  exist).
- **Gain & timing:** `postgain`, `nudge` (also needed by SK3/LIVE2).
- **Distortion:** per-event `distort` (DST curve set, as a `WaveShaperNode` curve +
  Rust table). Decide whether `(distort …)` as a pattern function conflicts with the
  USAGE.md rule "no inline distort fn — use `(fx :distort …)`".
- **Sends:** `delay-sync` (delay time in cycles), `room-lp`/`room-dim`/`room-fade` if cheap.
- **FM per event:** `fm`/`fmh` index/harmonicity for `(synth :fm …)`; decide vs SYN phases.

## Open design questions

- `coarse` has no native node. Options: (a) a small `voice-fx-processor` AudioWorklet
  loaded into live *and* offline contexts, plus the Rust version; (b) WASM-only, and
  samples get it only after EXP1/a samples-in-WASM phase. Decide by keeping the
  "JS side configures native nodes only" rule from TL7a unless a measurement says
  otherwise.
- Filter envelopes in JS: `AudioParam` automation on the `BiquadFilterNode` frequency
  (`setTargetAtTime`/`linearRampToValueAtTime`) vs Rust per-sample coefficient updates.
  The parity test from TL7a must cover the envelope shape (frequency at t = attack).
- Which of the 🟡 track-level rows (`chebyshev`, `compressor`, `chorus`, `leslie`)
  become per-event at all, or stay track-level and get ⛔/🔁 in the parity doc.
- `duck`/`duckorbit` (sidechain per orbit, builds on TL7b) — include or defer.
- Split again if the full spec exceeds one reviewable PR (e.g. TL7c envelopes, TL7d
  modulators).

## Definition of done (outline)

- [ ] Filter envelope: offline render of a saw with `(lpf 300) (lp-env 4) (lp-decay 0.2)` has more energy above 1 kHz in the first 20 ms than at 300 ms (≥ 10 dB), in both JS and Rust paths.
- [ ] `coarse 8` on a 1 kHz sine produces aliasing images (Goertzel energy at `sr/8 − 1000` Hz) in both paths, or the spec documents the path it is limited to.
- [ ] `vowel :a` vs `vowel :i` on the same saw differ in energy around the second formant (≈ 1100 vs 2300 Hz) by ≥ 6 dB.
- [ ] Every new parameter accepts patterns and signals; render loop allocation-free.
- [ ] Rule 3 registration; USAGE.md "Effect parameters" extended; parity rows ✅ or reclassified (⛔/🔁/❓ with reason).
- [ ] Human listen check recorded in the PR.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.

## What NOT to do

- No reorderable/parallel master chains: deferred **P2**.
- No new synthesis models: **SYN3–5**.
- No automation over the song timeline: **SONG2**.
- Don't rework TL7a/TL7b plumbing; extend the slot layout, never reorder it.
