# Phase SYN6 — Synth Essentials: Oscillators, Drum Kit & Synth Instruments

> **Spec status:** outline — expand into a full spec before implementing. This phase is
> large on purpose: it holds everything a song needs from the built-in synthesizer in one
> phase. The full spec fixes the build order of parts A–D; each part is testable on its own.
>
> **Automation:** 🤖👂 auto + human check — aliasing, tuning, detune, envelopes and glide
> are measurable with FFT assertions in offline renders; whether the kit and the
> instruments sound good needs a human listen.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** parts A, B and D: none. Part C: TL7a (per-voice filter) and TL7c (filter
envelope, vibrato). Coordinates with KIT1 (recorded kits) and with TL7a's
`trigger_v3` parameter slots.

## Goal

Make the built-in synthesizer complete enough to write a whole song without samples:
clean oscillators, a full drum kit, and the standard synth instruments by name.

Today the WASM `:saw` and `:square` voices alias on high notes, there is no triangle,
unison or coloured noise, the synthesized drums are four fixed voices that can't be
selected once samples have loaded, and a bass or pad has to be built by hand.

```lisp
;; Before
(->> (seq :c7 :e7 :g7) (synth :saw))            ; harsh, inharmonic aliasing
(->> (seq :c4) (synth :tri))                    ; no such voice — plays a sine, no warning
(seq :bd :cp :bd :cp)                           ; :cp is a sample or nothing
(->> (seq :c4 :e4) (synth :trumpet-cup))        ; documented in USAGE.md, plays a sine

;; After (proposed)
(->> (seq :c7 :e7 :g7) (synth :saw))            ; clean
(->> (chord :minor :a3) (synth :supersaw) (detune 18) (spread 0.8))
(->> (seq :a1 :a1 :c2 :e1) (synth :saw) (unison 3) (detune 12) (sub 0.5))

(bank :synth)                                   ; bare drum names use the synth kit
(seq :bd :cp :bd [:cp :rim])
(->> (seq :bd :bd :bd :bd) (tune (seq 0 0 -5 0)) (decay 0.8))
(->> (fast 4 (seq :hh :hh :oh :hh)) (tone (range 0.2 0.9 sine)))

(->> (seq :c2 :c2 :eb2 :g1) (synth :acid) (glide (seq 0 0 0.06 0)))
(->> (chord :minor7 :d3) (synth :strings) (attack 0.4))
(->> (chord :major :f3) (synth :pad) (lpf 900))  ; preset values stay overridable

(->> (seq :c4) (synth :trii))
;; => Warning: unknown synth voice :trii — did you mean :tri?
```

## Decisions already made (2026-11-13)

- **Synth drums get their own bank: `(bank :synth)`.** Samples keep priority for bare
  names when no bank is selected; the synthesized voices stay the fallback while samples
  have not loaded. `(bank :synth)` makes bare drum names skip the sample lookup.
- **The oscillators are band-limited at the source.** A filter after the oscillator
  cannot remove aliasing (see Background), so this is not left to the user.
- **An unknown `(synth :name)` is a warning**, like an unknown sample name. Playback
  continues.

## Background

- `packages/audio/src/lib.rs`
  - `Voice::Saw`: `2.0 * (phase % 1.0) - 1.0`. `Voice::Square`:
    `if p < pulse_width { 1 } else { -1 }`. `Voice::Tone` is the sine, `Voice::Noise`
    white LCG noise. Voices are mono; `ActiveVoice { voice, pan }` pans them in
    `process_block_raw`.
  - **Why filtering does not fix aliasing:** a naive saw at 3520 Hz has harmonics far
    above the Nyquist frequency (22 kHz). They are not lost; they fold back *down* into
    the audible range at frequencies that are not multiples of the note (e.g. the 7th
    harmonic, 24.6 kHz, lands at 19.5 kHz, the 12th at 1.9 kHz). These folded tones sit
    between the wanted harmonics, so a low-pass that removes them also removes the
    sound. PolyBLEP smooths the two samples around each jump, which costs a few
    operations per sample and removes most of the fold-back.
  - Drums: `Voice::Kick` (sine sweep 150 → 40 Hz over 60 ms), `Voice::Snare`
    (band-passed noise + 180 Hz sine), `Voice::Hihat` (noise through an 8 kHz high-pass;
    `:oh` is the same voice with a 1 s decay). Constants are set in
    `AudioEngine::activate`; `Pending` carries only `amp attack decay pan`, and the drum
    voices ignore `attack` and `decay`.
  - Presets today are Rust tables (`fm_preset`, `ks_preset`), selected through the
    trigger string (`"fm:<preset>:<freq>"`, `"ks:<preset>:<freq>"`). Other trigger
    strings: `"saw:<hz>"`, `"square:<hz>:<pw>"`, `"noise"`, `"bd" "sd" "hh" "oh"`.
- `app/src/repulse/audio.cljs`, `play-event`
  - A keyword is resolved with `samples/resolve-keyword`; if `samples/has-bank?` the
    sample plays, otherwise the name goes to the worklet. `(bank :X)` turns `:bd` into
    `:X_bd` when that bank exists (`samples.cljs`).
  - Two literal keyword sets decide which `:synth` values go to `"fm:"` and `"ks:"`. Any
    other `:synth` value falls through to the note dispatch and plays a sine.
  - The JS fallbacks (`make-saw`, `make-square`, `make-kick`, …) use native
    `OscillatorNode`s, which are already band-limited; only the WASM path aliases.
  - Every event is an independent voice. Nothing carries the previous note's pitch into
    the next event, so glide cannot be expressed today.
- `app/src/repulse/sound_check.cljs` warns about unknown *sample* names after an
  evaluation; it does not look at `:synth`.
- Choke groups (`cut`, TL6) are implemented in the sample player only.
- Tidal Drum Machines sample names: `bd sd hh oh cp rim lt mt ht cb cr rd sh tb`. Using
  the same names lets one pattern switch between sampled and synthesized kits.
- `defsynth` (`app/src/repulse/synth.cljs`) has `sin saw square tri noise` UGens on
  native nodes; `tri` exists there but not as a `synth` voice.
- STRUDEL-PARITY.md lists `detune` / `unison` / `spread` as ❓ "synth-design scope; see
  SYN phases". This phase decides them.
- Drift found 2026-11-13: USAGE.md lists `:trumpet-cup` (not implemented) and omits
  `:synth-brass`, `:harmon-out`, `:harmon-in` (implemented).

## Scope

### Part A — Oscillator core

- **Band-limiting:** PolyBLEP correction for saw and pulse; triangle by integrating the
  band-limited square (or PolyBLAMP).
- **New voices:** `:tri`, `:sine` (explicit name for the default tone), `:pulse` (alias
  of `:square` with `:pw`), `:supersaw` (unison preset: 7 voices, default detune and
  spread).
- **Unison** on saw, pulse and triangle: `unison` (1–7 voices), `detune` (cents, total
  spread), `spread` (0–1 stereo width), `sub` (level of a sine one octave below).
- **PWM:** `pw` as a patternable parameter, plus `pw-rate`/`pw-depth` for movement
  within a note.
- **Noise colours:** `(synth :noise :color …)` with `:white` (default), `:pink`
  (−3 dB/oct), `:brown` (−6 dB/oct), `:dust` (sparse random impulses), `:lfsr` (chip
  noise).
- **Hard sync:** a slave oscillator reset by the note-frequency master, with a ratio
  parameter (`sync`).
- JS fallback voices for the new names, so the TEST1 harness and a missing worklet still
  produce the right waveform class.

### Part B — Synthesized drum kit

- **Voices:** `:bd :sd :hh :oh :cp :rim :lt :mt :ht :cb :cr :rd :sh :tb`, plus `:cl`
  (claves), `:cg` (conga), `:zap`, `:click`. Kick (sine + pitch sweep + click), snare
  (two tuned sines + noise with `snap`), clap (noise bursts through a band-pass + tail),
  rimshot, toms, cowbell (two squares through a band-pass), claves, closed/open hat and
  crash/ride from one metallic source (six detuned squares → band-pass → high-pass),
  shaker, tambourine, conga.
- **Per-event parameters:** `tune` (semitones), `decay` (now honoured by drum voices),
  `tone` (0–1: brightness or noise/body balance, per voice).
- **Selection:** `(bank :synth)` as decided above. Single synthesized hits inside a
  sampled kit use the name the bank mechanism already produces: `:synth_bd`.
- **Choke:** the closed hat stops a ringing open hat inside the engine.
- **Fallback:** while samples have not loaded, bare names fall back to the synthesized
  voices, now for the whole kit.

### Part C — Synth instruments (needs TL7a and TL7c)

Named voices that bundle oscillator, filter and envelope settings, the same way `:sax`
or `:harp` are named voices today.

- **Bass:** `:acid` (saw or square, resonant low-pass, short filter envelope; `accent`),
  `:reese` (detuned saws, low-pass), `:sub-bass` (sine + soft saturation), `:fm-bass`,
  `:wobble` (low-pass with a tempo-synced cutoff LFO).
- **Lead:** `:lead` (filtered saw with vibrato), `:sync-lead`, `:pluck`, `:theremin`
  (sine with glide and vibrato), `:reed` (accordion/harmonica: detuned pulse pair
  through a band-pass).
- **Pad & strings:** `:pad`, `:strings` (unison saws, slow attack, ensemble detune),
  `:sweep-pad`, `:brass-pad`.
- **Glide:** `(glide secs pat)`: the voice starts at the previous note's pitch of the
  same track and slides to its own.
- **Overrides:** any explicit per-event parameter (`lpf`, `decay`, `detune`, …) replaces
  the preset's value.

### Part D — Housekeeping

- **Unknown voice warning** in `sound_check.cljs`: `(synth :name …)` with a name that is
  neither a built-in voice nor a `defsynth`, with a fuzzy-matched suggestion.
- **`:trumpet-cup`:** add the FM preset USAGE.md already documents.
- **One shared voice list** per language (Rust, CLJS) used by dispatch, tests, the
  warning and completions (today the names are repeated in four places).
- **Docs:** USAGE.md voice tables list every voice the engine knows, including
  `:synth-brass`, `:harmon-out`, `:harmon-in`.

## Open design questions

- Unison needs stereo output per voice. Options: `Voice::tick` returns `(l, r)`; or a
  unison voice is expanded into N mono `ActiveVoice`s with their own pan at trigger time
  (no engine change, N × the voice count). Measure the cost of 7 × polyphony.
- Fixed phase arrays (`[f64; 7]`) keep the render path allocation-free. Random start
  phases sound wider but must be seeded from `noise_seed` so offline renders repeat.
- Does band-limiting change `:saw`/`:square` loudness or tone enough to matter for
  existing sessions? State the measured difference in the PR.
- Parameter transport for `tune`, `tone`, `unison`, …: more colon fields in the trigger
  string (as `"square:<hz>:<pw>"` today) or TL7a `trigger_v3` slots? Prefer the slots if
  TL7a has landed.
- One generic `Voice::Drum` with a model enum, or one variant per model? Either way no
  allocation in `tick`.
- JS fallback and the TEST1 harness (`offline-js` backend): port the new drum voices, or
  map them to the nearest of the four existing ones and test the real voices in Rust only?
- `(bank :synth)` and KIT1's `(kit …)`: `:synth` becomes a reserved bank name. What
  happens if a user loads a sample bank called `synth`?
- `tone` is also the name deferred SYN4 plans for its guitar tone control. One shared
  0–1 brightness parameter for both, or a different name here?
- Where do the Part C presets live? (a) A Rust table expanded at trigger time (works in
  the worklet and offline, consistent with `fm_preset`); (b) a ClojureScript table that
  fills in the event's parameter map before the trigger (no engine change, overrides
  come for free); (c) a Lisp prelude of `defsynth`s (JS path only). Test (b) first.
- Glide needs the previous pitch. The scheduler in `audio.cljs` can remember the last
  frequency per track and send it with the trigger; the pattern engine stays pure. How
  do chords and overlapping notes pick "the previous note"?
- Tempo-synced LFO for `:wobble`: a per-voice LFO in the engine (`pw-rate` shows the
  mechanism) or the planned generic modulators (❓ `lfo` parity row)?
- If TL7a or TL7c is late: ship parts A, B and D first and Part C when both have landed,
  or wait?

## Definition of done

**Part A**

- [ ] Saw at 3520 Hz (A7), 44.1 kHz, 1 s: energy in non-harmonic FFT bins below 10 kHz is ≥ 30 dB lower than the same measurement on the pre-phase oscillator (keep the naive version in the test as the reference). Same for pulse at 50 % and 25 % width.
- [ ] `:tri` at 440 Hz: 3rd harmonic 19 ± 2 dB below the fundamental, even harmonics ≥ 40 dB below.
- [ ] `(unison 3) (detune 20)`: the FFT around the fundamental shows three peaks within ±12 cents of the note.
- [ ] `(spread 1)` with `unison 7`: left/right correlation < 0.9; `(spread 0)`: left and right identical.
- [ ] `(sub 0.5)`: a peak one octave below the note, 6 ± 2 dB below the fundamental.
- [ ] Pink noise: octave-band levels fall 3 ± 1 dB per octave between 100 Hz and 10 kHz; brown: 6 ± 1.5 dB.
- [ ] Two offline renders of the same unison pattern are bit-identical.

**Part B**

- [ ] Every drum voice renders non-silent output (RMS > 0.001 over its first 100 ms).
- [ ] `tune 12` on the kick doubles its settled fundamental (FFT peak of the tail, ±5 %).
- [ ] `decay 0.8` vs `decay 0.2` on `:bd`: RMS in the 300–400 ms window differs by ≥ 20 dB.
- [ ] `:hh` has ≥ 80 % of its energy above 5 kHz; `:bd` has ≥ 80 % below 200 Hz.
- [ ] Clap: the amplitude envelope of the first 40 ms has at least three separate peaks.
- [ ] With `(bank :synth)` active and a `bd` sample bank loaded, `(seq :bd)` triggers the engine voice, not the sample (asserted on the trigger path in the test harness). Without it, `(seq :bd)` behaves exactly as before.
- [ ] `(seq :synth_bd)` triggers the engine voice while another bank is active.
- [ ] A closed hat 50 ms after an open hat reduces the open hat's level by ≥ 40 dB within 10 ms.
- [ ] `tune`, `tone` and `decay` accept patterns and signals: `(tune (seq 0 7) (seq :lt :lt))` yields two different fundamentals.

**Part C**

- [ ] `(synth :acid)` on one note: energy above 1 kHz in the first 20 ms is ≥ 10 dB higher than at 300 ms.
- [ ] `(synth :reese)`: the amplitude envelope of a 2 s note shows periodic beating (modulation depth ≥ 20 %).
- [ ] `(synth :strings)` with the preset attack: RMS at 20 ms is < 25 % of RMS at 400 ms.
- [ ] `(glide 0.1)` from `:c3` to `:c4`: the measured pitch 50 ms after the second onset lies between the two notes; with `(glide 0)` it is within ±1 % of `:c4`. Glide state is per track and reset by `(stop)`.
- [ ] `(synth :sync-lead)`: the strongest partial lies above the 3rd harmonic.
- [ ] `(->> pat (synth :pad) (lpf 900))` uses 900 Hz, not the preset cutoff (asserted on the trigger parameters).
- [ ] Each preset renders non-silent output; no preset clips at `amp 1` (peak ≤ 1.0).

**Part D**

- [ ] `(->> (seq :c4) (synth :trii))` shows `unknown synth voice :trii — did you mean :tri?` in the output line; a `defsynth` name passed to `synth` produces no warning.
- [ ] `(->> (seq :c4) (synth :trumpet-cup))` triggers `"fm:trumpet-cup:…"`, not a sine.
- [ ] Voice names exist in exactly one list per language; adding a voice touches no test code.

**All parts**

- [ ] The render loop stays allocation-free (fixed-size voice state, no `Vec` growth in `tick`).
- [ ] Parity rows `detune` / `unison` / `spread` updated in STRUDEL-PARITY.md; SYNTHESIS-COVERAGE.md rows for this phase set to ✅.
- [ ] Rule 3 registration for every new name; USAGE.md: voice table, "Unison", "Synth drum kit" and "Synth instruments" sections with one example per voice and parameter.
- [ ] Human listen check recorded in the PR: old vs new saw on a high melody, a supersaw chord, a full pattern using every drum voice, each instrument in a short phrase.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## What NOT to do

- No more acoustic instrument presets (clarinet, banjo, vibraphone, organ, choir) and no
  sound-effect generators: deferred **SYN7**.
- No recorded or multisampled kits, velocity layers or round-robin: **KIT1**.
- No circuit-level emulation of a specific drum machine or synthesizer, and no brand
  names as voice names.
- No new filter models (ladder, comb): deferred **FX2**. Presets use the TL7a filter.
- No effects baked into presets (chorus, reverb): those stay track-level `fx`.
- No wavetable oscillator (out of Milestone 1, see STRUDEL-PARITY.md).
- No preset browser, step sequencer or drum-pad UI.
- Don't touch the `defsynth` UGens; they already use band-limited native oscillators.
- Don't make the pattern engine stateful for glide; the state lives in the scheduler.
- Don't change what bare `:bd :sd :hh :oh` play when no synth bank is selected.
