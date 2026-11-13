# Phase SYN7 — Synthesized Drum Voices

> **Spec status:** outline — expand into a full spec before implementing. Take the voice
> list from the SYN7 rows of
> [docs/SYNTHESIS-COVERAGE.md](../docs/SYNTHESIS-COVERAGE.md).
>
> **Automation:** 🤖👂 auto + human check — tuning, decay and spectral balance are
> measurable offline; whether the kit sounds good needs a human listen.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** none. Coordinates with TL7a (parameter slots of `trigger_v3`) and KIT1
(recorded kits; both must work alongside `(bank …)`).

## Goal

A complete drum kit made by synthesis: always available, no download, tunable per event.
Today the engine has four fixed voices (`:bd :sd :hh :oh`), and they are a fallback only:
as soon as a sample bank with the same name has loaded, the sample plays and the
synthesized voice can no longer be reached. This phase adds the missing voices (clap,
rimshot, toms, cowbell, claves, cymbals, shaker, tambourine, conga), makes all of them
tunable, and gives the user an explicit way to choose the synthesized kit.

```lisp
;; Before — :cp is a sample or nothing; the synth kick cannot be selected or tuned
(seq :bd :cp :bd :cp)

;; After (proposed)
(bank :synth)                                        ; bare drum names use the synth kit
(seq :bd :cp :bd [:cp :rim])
(->> (seq :bd :bd :bd :bd) (tune (seq 0 0 -5 0)) (decay 0.8))   ; long, pitched sub kick
(->> (fast 4 (seq :hh :hh :oh :hh)) (tone (range 0.2 0.9 sine)))
(->> (seq :lt :mt :ht :cb) (tune 3))
```

## Background

- `packages/audio/src/lib.rs`: `Voice::Kick` (sine sweep 150 → 40 Hz over 60 ms),
  `Voice::Snare` (band-passed noise at 200 Hz + 180 Hz sine), `Voice::Hihat` (noise through
  an 8 kHz high-pass; `:oh` is the same voice with a 1 s decay). All constants are set in
  `AudioEngine::activate`; `Pending` carries only `amp attack decay pan`, and the drum
  voices ignore `attack` and `decay`.
- `app/src/repulse/audio.cljs`, `play-event`: a keyword is resolved with
  `samples/resolve-keyword`; if `samples/has-bank?` the sample plays, otherwise the name
  goes to the worklet. The JS fallback `js-synth` knows the same four names
  (`make-kick`, `make-snare`, `make-hihat`).
- `app/src/repulse/samples.cljs`, `resolve-keyword`: `(bank :X)` turns `:bd` into `:X_bd`
  when that bank exists.
- Choke groups (`cut`, TL6) are implemented in the sample player only.
- Sample names in the Tidal Drum Machines banks: `bd sd hh oh cp rim lt mt ht cb cr rd sh tb`.
  Using the same names lets one pattern switch between sampled and synthesized kits.

## Scope

- **Voices** (names as above, plus `:cl` claves, `:cg` conga, `:zap`, `:click`):
  kick (sine + pitch sweep + click), snare (two tuned sines + noise with `snap`), clap
  (noise bursts through a band-pass + tail), rimshot, toms, cowbell (two squares through
  a band-pass), claves, closed/open/pedal hat and crash/ride from one metallic source
  (six detuned squares → band-pass → high-pass), shaker, tambourine, conga.
- **Per-event parameters:** `tune` (semitones), `decay` (now honoured by drum voices),
  `tone` (0–1: brightness or noise/body balance, per voice), `snap`/`punch` if needed.
- **Explicit selection:** a reserved bank name (proposal: `(bank :synth)`) that makes
  bare drum keywords skip the sample lookup; plus a per-event form for single synthesized
  hits inside a sampled kit (syntax in the full spec).
- **Choke:** the closed hat stops a ringing open hat inside the engine.
- **Fallback:** when samples have not loaded, the bare names keep falling back to the
  synthesized voices, now for the whole kit.

## Open design questions

- Selection syntax: reserved bank `(bank :synth)`, voice names via `(synth :kick pat)`,
  or a keyword prefix (`:syn-bd`)? Decide by what composes with `bank`, KIT1 `(kit …)`
  and the unknown-sound check.
- Parameter transport: extend the value string (`"drum:bd:<tune>:<decay>:<tone>"`, like
  `"square:<hz>:<pw>"` today) or use TL7a `trigger_v3` slots? Prefer the slots if TL7a
  has landed.
- One generic `Voice::Drum` with a model enum, or one variant per model? Either way no
  allocation in `tick`.
- JS fallback and the TEST1 harness (`offline-js` backend): port the new voices, or map
  them to the nearest of the four existing ones and test the real voices in Rust only?
- Do kick and tom `tune` values follow note names (`(tune :c2)`) as well as semitones?
- `tone` is also the name deferred SYN4 plans for its guitar tone control. One shared
  0–1 brightness parameter for both, or a different name here?

## Definition of done

- [ ] Every voice in the list renders non-silent output (RMS > 0.001 over its first 100 ms).
- [ ] `tune 12` on the kick doubles its settled fundamental (FFT peak of the tail, ±5 %).
- [ ] `decay 0.8` vs `decay 0.2` on `:bd`: RMS in the 300–400 ms window differs by ≥ 20 dB.
- [ ] `:hh` has ≥ 80 % of its energy above 5 kHz; `:bd` has ≥ 80 % below 200 Hz.
- [ ] Clap: the amplitude envelope of the first 40 ms has at least three separate peaks.
- [ ] With `(bank :synth)` active and a `bd` sample bank loaded, `(seq :bd)` triggers the engine voice, not the sample (asserted on the trigger path in the test harness).
- [ ] Without `(bank :synth)`, `(seq :bd)` behaves exactly as before (sample if loaded).
- [ ] A closed hat 50 ms after an open hat reduces the open hat's level by ≥ 40 dB within 10 ms.
- [ ] `tune`, `tone` and `decay` accept patterns and signals: `(tune (seq 0 7) (seq :lt :lt))` yields two different fundamentals.
- [ ] The render loop stays allocation-free (voices own fixed-size state).
- [ ] Rule 3 registration; USAGE.md "Built-in synthesis" table lists every voice and parameter with an example.
- [ ] Human listen check recorded in the PR: a full pattern using every voice.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## What NOT to do

- No recorded or multisampled kits, velocity layers or round-robin: **KIT1**.
- No circuit-level emulation of a specific drum machine and no machine brand names as
  voice names; the voices are generic synthesis models.
- No step-sequencer or drum-pad UI.
- No per-voice effects (filter, drive): **TL7a** inserts apply to these voices like to any other.
- Don't change what bare `:bd :sd :hh :oh` play when no synth bank is selected.
