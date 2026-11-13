# Phase SYN10 — Subtractive Instrument Presets: Bass, Lead, Pad, Strings

> **Spec status:** outline — expand into a full spec before implementing. Take the
> instrument list from the SYN10 rows of
> [docs/SYNTHESIS-COVERAGE.md](../docs/SYNTHESIS-COVERAGE.md).
>
> **Automation:** 🤖👂 auto + human check — envelopes, glide and detune are measurable;
> whether a preset is musically useful needs a human listen.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** SYN8 (band-limited oscillators, unison), TL7a (per-voice filter insert),
TL7c (filter envelope, vibrato)

## Goal

The classic synthesizer instruments by name. After SYN8, TL7a and TL7c a user *can* build
an acid bass or a string pad from a saw, a filter and two envelopes, but it takes six
chained parameters per sound. This phase ships those combinations as named voices, the
same way `:sax` or `:harp` are named voices today, and adds the two oscillator features
they need that no earlier phase provides: glide between notes and hard sync.

```lisp
;; Before (after SYN8 + TL7a/c) — everything by hand
(->> (seq :c2 :c2 :eb2 :g1) (synth :saw)
     (lpf 300) (resonance 12) (lp-env 4) (lp-attack 0.005) (lp-decay 0.2))

;; After (proposed)
(->> (seq :c2 :c2 :eb2 :g1) (synth :acid) (glide (seq 0 0 0.06 0)))
(->> (seq :a1 :_ :a1 :c2) (synth :reese))
(->> (chord :minor7 :d3) (synth :strings) (attack 0.4))
(->> (scale :minor :a4 (seq 0 2 3 7)) (synth :sync-lead))
(->> (chord :major :f3) (synth :pad) (lpf 900))          ; preset values stay overridable
```

## Background

- After SYN8 the engine has band-limited saw, pulse, triangle and sine with `unison`,
  `detune`, `spread`, `sub`. After TL7a every voice has an insert chain (filter, shape,
  crush) set through `trigger_v3` slots. TL7c adds filter envelopes, pitch envelope and
  `vib`.
- Presets today are Rust tables (`fm_preset`, `ks_preset` in `packages/audio/src/lib.rs`)
  selected through the trigger string, dispatched from keyword sets in
  `app/src/repulse/audio.cljs`.
- A second way to define an instrument exists: `defsynth` (`app/src/repulse/synth.cljs`)
  builds Web Audio node graphs per event. It runs on the JS side, not in the WASM engine.
- Every event is an independent voice. Nothing carries the previous note's pitch into
  the next event, so glide cannot be expressed today.
- The FM preset `:synth-brass` already exists.

## Scope

- **Bass:** `:acid` (saw or square, resonant low-pass, short filter envelope; `accent`),
  `:reese` (detuned saws, low-pass), `:sub-bass` (sine + soft saturation), `:fm-bass`
  (FM preset), `:wobble` (low-pass with a tempo-synced cutoff LFO).
- **Lead:** `:lead` (filtered saw with vibrato), `:sync-lead`, `:pluck`, `:theremin`
  (sine with glide and vibrato), `:reed` (accordion/harmonica: detuned pulse pair through
  a band-pass).
- **Pad & strings:** `:pad`, `:strings` (unison saws, slow attack, ensemble detune),
  `:sweep-pad`, `:brass-pad`.
- **Glide:** `(glide secs pat)`: the voice starts at the previous note's pitch of the
  same track and slides to its own.
- **Hard sync:** a slave oscillator reset by the note-frequency master, with a ratio
  parameter (`sync`).
- **Overrides:** any explicit per-event parameter (`lpf`, `decay`, `detune`, …) replaces
  the preset's value.

## Open design questions

- Where do presets live? (a) A Rust table of parameter sets expanded at trigger time
  (works in the worklet and offline, consistent with `fm_preset`); (b) a ClojureScript
  table that fills in the event's parameter map before the trigger (no engine change,
  overrides come for free, visible to the user as plain parameters); (c) a Lisp prelude
  of `defsynth`s (JS path only). Recommendation to test first: (b).
- Glide needs the previous pitch. The scheduler in `audio.cljs` can remember the last
  frequency per track and send it with the trigger; the pattern engine stays pure.
  How do chords and overlapping notes pick "the previous note"?
- Is `accent` a new parameter or just `amp` plus extra filter-envelope depth?
- Tempo-synced LFO for `:wobble`: per-voice LFO in the engine (SYN8 `pw-rate` shows the
  mechanism) or the planned generic modulators (❓ `lfo` parity row)?
- Preset names must not collide with sample bank names (`:bass`, `:moog`, `:pad` exist
  in Dirt-Samples); `synth` voice names are a separate namespace, but check completions
  and the unknown-sound check.

## Definition of done

- [ ] `(synth :acid)` on one note: energy above 1 kHz in the first 20 ms is ≥ 10 dB higher than at 300 ms.
- [ ] `(synth :reese)`: the amplitude envelope of a 2 s note shows periodic beating (modulation depth ≥ 20 %).
- [ ] `(synth :strings)` with the preset attack: RMS at 20 ms is < 25 % of RMS at 400 ms.
- [ ] `(glide 0.1)` from `:c3` to `:c4`: the measured pitch 50 ms after the second onset lies between the two notes; with `(glide 0)` it is within ±1 % of `:c4`.
- [ ] `(synth :sync-lead)`: the spectrum has its strongest partial above the 3rd harmonic (formant-like peak from the slave oscillator).
- [ ] `(->> pat (synth :pad) (lpf 900))` uses 900 Hz, not the preset cutoff (asserted on the trigger parameters).
- [ ] Each preset renders non-silent output; no preset clips at `amp 1` (peak ≤ 1.0).
- [ ] Glide state is reset by `(stop)` and is per track.
- [ ] Rule 3 registration; USAGE.md "Synth instruments" table with one example per preset.
- [ ] Human listen check recorded in the PR: each preset in a short musical phrase.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## What NOT to do

- No new filter models (ladder, comb): **FX3**. Presets use the TL7a filter.
- No preset browser or editor UI, no user preset files; `def` + `comp` already let users
  name their own chains.
- No effects baked into presets (chorus, reverb): those stay track-level `fx`.
- No physical models or FM/KS presets: **SYN3–SYN6**, **SYN9**.
- Don't make the pattern engine stateful for glide; the state lives in the scheduler.
