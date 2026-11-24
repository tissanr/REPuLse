# REPuLse — Synthetic Sound & Effect Coverage

Which sounds and effects can be made synthetically (without recordings), which of them
REPuLse has, and which phase closes each gap. Companion to
[STRUDEL-PARITY.md](STRUDEL-PARITY.md): that file tracks *functions*, this one tracks the
*sound palette*.

Inventory taken 2026-11-13 from `packages/audio/src/lib.rs`, `app/src/repulse/audio.cljs`,
`app/src/repulse/synth.cljs`, `app/public/plugins/` and [USAGE.md](USAGE.md).

In principle every sound can be synthesized. In practice the palette is the product of a
handful of **synthesis methods** (part 1), the **instrument families** they reach
(part 2) and the **effects** applied afterwards (part 3). Samples (Dirt-Samples, Tidal
Drum Machines, `samples!`, Freesound) are recordings and are listed only where they
currently stand in for a missing synthesized sound.

## Status legend

| Mark | Meaning |
|---|---|
| ✅ | Available today |
| 🟡 | Partial: exists with a limit named in the row |
| 📋 | Planned in an existing phase |
| ⏸ | Missing; specified in a deferred phase (SYN3–5, SYN7, FX2, P2), revisited after M2 |
| 🆕 | Missing; scheduled in Milestone 1 by the phases added with this document (SYN6, FX1) |
| 💤 | Missing and not scheduled; reason in the row |

The gaps found by this inventory are split by one question, the same one that deferred
SYN3–SYN5: *does a song need it?* What a song needs (clean oscillators, a synth drum
kit, the standard synth instruments, mixing and space effects) is scheduled in **SYN6**
and **FX1**. The rest (more acoustic instruments, mallets, organ, choir, sound-effect
generators, filter models, tape colour, pitch and time effects) is specified in **SYN7**
and **FX2** and deferred: samples cover those roles until then.

TL7a–c are the three phases TL7 was split into.

## Summary

| Area | ✅ | 🟡 | 📋 | ⏸ | 🆕 | 💤 | Total |
|---|---|---|---|---|---|---|---|
| Synthesis methods | 13 | 0 | 0 | 5 | 0 | 5 | 23 |
| Modulation sources | 7 | 3 | 1 | 0 | 0 | 0 | 12 |
| Drums & percussion | 16 | 0 | 0 | 2 | 0 | 0 | 18 |
| Bass | 7 | 1 | 0 | 1 | 0 | 0 | 9 |
| Leads & keys | 10 | 0 | 0 | 6 | 0 | 0 | 16 |
| Pads, strings & voices | 4 | 0 | 0 | 4 | 0 | 0 | 8 |
| Plucked strings | 7 | 0 | 0 | 7 | 0 | 0 | 14 |
| Winds & brass | 9 | 0 | 0 | 5 | 0 | 0 | 14 |
| Mallets & bells | 2 | 0 | 0 | 8 | 0 | 0 | 10 |
| Sound effects & textures | 2 | 1 | 0 | 9 | 0 | 0 | 12 |
| Effects | 18 | 4 | 4 | 22 | 12 | 4 | 65 |
| **Total** | **95** | **9** | **5** | **69** | **12** | **9** | **201** |

Rows that group several names (e.g. "toms lo/mid/hi") count once. Rows marked ❓ (open
parity questions) count only in *Total*. Counts updated 2026-11-20 after SYN6.

---

## Part 1 — Synthesis methods

| Method | What it is good for | Status | Phase | Notes |
|---|---|---|---|---|
| Sine oscillator | Sub bass, pure tones, drones | ✅ | — | Default voice for note keywords and Hz numbers (`Voice::Tone`) |
| Sawtooth, pulse/square | Subtractive basses, leads, pads | ✅ | SYN6 | `(synth :saw)`, `:square` / `:pulse`; band-limited (PolyBLEP) since SYN6 |
| Triangle | Flute-like tones, chip bass | ✅ | SYN6 | `(synth :tri)`, band-limited |
| Band-limited oscillators (PolyBLEP) | Clean high notes, sweeps | ✅ | SYN6 | PolyBLEP saw/pulse, PolyBLAMP triangle in `Voice::Osc` |
| Pulse-width modulation | Moving, chorused pulse tones | ✅ | SYN6 | `pw`, `pw-rate`, `pw-depth` (WASM; static `pw` on FX tracks) |
| Unison / supersaw | Trance leads, reese bass, string ensembles | ✅ | SYN6 | `unison`, `detune`, `spread`, `(synth :supersaw)` |
| Oscillator hard sync | Tearing sync leads | ✅ | SYN6 | `sync` on `:saw` / `:pulse` (WASM) |
| White noise | Hats, snares, sweeps | ✅ | — | `(synth :noise)`; LCG generator in Rust |
| Coloured noise (pink, brown), dust/crackle, LFSR chip noise | Wind, surf, vinyl, chiptune drums | ✅ | SYN6 | `(synth :noise :color :pink)`, `:brown`, `:dust` (`:density`), `:lfsr` |
| Subtractive voice (oscillator → filter → amp, each with an envelope) | The classic analog synth sound | ✅ | TL7a, TL7c, SYN6 | Per-event filter (TL7a) with its own envelope (TL7c); SYN6 instruments `:pad`, `:acid`, … |
| FM / phase modulation, 2 operators | Bells, electric pianos, brass, reeds | ✅ | — | `(synth :fm :index … :ratio …)` plus 11 presets |
| FM, 4–6 operators with algorithms and feedback | DX-style keys, basses, evolving timbres | 💤 | — | Large engine and UI surface; 2-operator presets cover the common cases. Per-event FM controls were deferred by TL7c to SYN6 |
| Karplus-Strong plucked string | Guitar, harp, koto, pizzicato | ✅ | — | `Voice::KarplusStrong`, 7 presets + alias, body EQ per instrument |
| Bowed-string waveguide | Violin, viola, cello, arco bass | ⏸ | SYN3 | Full spec exists |
| Wind waveguides (bore + reed/jet model) | Physically modelled flute, clarinet, brass | 💤 | — | FM presets cover winds and brass (SYN2, SYN7); backlog row in FUTURE-FEATURES.md |
| Hammered-string model | Piano | ⏸ | SYN5 | Lo-fi piano; high-fidelity piano is a FUTURE-FEATURES.md backlog row |
| Modal synthesis (bank of decaying resonators) | Vibraphone, glockenspiel, kalimba, steel drum, bells, glass | ⏸ | SYN7 | |
| Additive synthesis (sum of harmonics) | Drawbar organ, pipe organ | ⏸ | SYN7 | |
| Formant synthesis (source + vowel filter bank) | Choirs, vowel leads, talking synths | ⏸ | SYN7 | Shares the formant table with TL7c `vowel` |
| Wavetable | Morphing digital timbres | 💤 | — | Parity doc marks `wt*`/`warp*` out of scope for Milestone 1; backlog row in FUTURE-FEATURES.md |
| Granular synthesis | Clouds, time-stretch textures | 💤 | — | Backlog row in FUTURE-FEATURES.md; FX2 adds a granular *effect* |
| Spectral resynthesis / speech synthesis (FFT, LPC) | Vocoded speech, morphing | 💤 | — | Not planned; FX2 vocoder covers the musical use |
| User-defined node graphs | Anything from the UGens | ✅ | — | `defsynth` with `sin saw square tri noise lpf hpf bpf gain delay-node mix env-perc env-asr env-gen`, buses `bus`/`out`/`in` |

### Modulation sources

| Source | Status | Phase | Notes |
|---|---|---|---|
| Amplitude envelope: attack, decay, release | ✅ | — | `attack`, `decay`, `release` per event; ADSR inside the FM presets |
| Multi-segment envelope with curves | ✅ | — | `env` + `env-gen` inside `defsynth` |
| `adsr` shorthands, `sustain`/`hold` for synth voices | ❓ | — | Deferred by TL7c: `sustain` already means sample length (TL6), and synth voices have no gate |
| Filter envelope | ✅ | TL7c | `lp-env`, `lp-attack`, … (also `hp-*`, `bp-*`); gate = event length |
| Pitch envelope | ✅ | TL7c | `penv`, `p-attack` …; tonal voices and samples; the SYN6 kit drums have their own sweeps |
| Vibrato | ✅ | TL7c | `vib`, `vib-mod`; koto and mandolin presets have fixed vibrato |
| Velocity | ✅ | TL7a, KIT1 | `velocity`; picks the recording layer on layered kits (`JazzKit`, `StudioKit`) |
| LFO on a synth parameter | 🟡 | — | Only through a control bus in `defsynth` |
| Pattern-rate modulation (one value per event) | 🟡 | TL7a | Signals (`sine`, `saw`, `perlin`, …) drive `amp`, `pan`, `rate` today; filters follow in TL7a |
| Session-time ramps | 🟡 | SONG2 | `tween` for `amp` and `pan` only; song-time automation planned |
| Glide / portamento between notes | ✅ | SYN6 | `glide`, per track, nearest note of the previous chord |
| Per-voice LFO and PWM within a note | ✅ | SYN6 | `pw-rate`/`pw-depth`; cutoff LFO `lp-rate`/`lp-depth` |

---

## Part 2 — Sounds by instrument family

### Drums & percussion

Synthesized drums are the oldest use of synthesis (analog drum machines): tuned sine
sweeps, filtered noise and clusters of detuned square waves.

| Sound | How it is synthesized | Status | Phase | Notes |
|---|---|---|---|---|
| Kick | Sine with a fast downward pitch sweep | ✅ | SYN6 | Synth kit `:bd` (`(bank :synth)` / `:synth_bd`), `tune` `tone` `decay`; the legacy kick stays the no-sample fallback |
| Snare | Tuned sine(s) + band-passed noise | ✅ | SYN6 | Synth kit `:sd`: two swept sines + noise, `tone` = body ↔ noise |
| Closed hi-hat | High-passed noise or 6 detuned squares, short | ✅ | SYN6 | Synth kit `:hh`: six band-limited squares, chokes the open hat |
| Open hi-hat | Same source, long decay, choked by the closed hat | ✅ | SYN6 | Synth kit `:oh`, choked by `:hh` (WASM) |
| Long sub kick / pitched bass drum | Sine, slow decay, tuned to the song key | ✅ | SYN6 | `:bd` with `tune` and a long `decay` |
| Clap | 3–4 noise bursts through a band-pass + tail | ✅ | SYN6 | Synth kit `:cp`: four noise bursts |
| Rimshot / side stick | Very short tuned pulse through band-passes | ✅ | SYN6 | Synth kit `:rim` |
| Toms lo/mid/hi | Sine with a mild pitch drop | ✅ | SYN6 | Synth kit `:lt` `:mt` `:ht` |
| Cowbell | Two detuned squares through a band-pass | ✅ | SYN6 | Synth kit `:cb` |
| Claves / woodblock | Short high sine blip | ✅ | SYN6 | Synth kit `:cl` |
| Crash / ride cymbal | Metallic square cluster, long decay | ✅ | SYN6 | Synth kit `:cr`, `:rd` |
| Shaker / maracas / cabasa | High-passed noise with a soft attack | ✅ | SYN6 | Synth kit `:sh` |
| Tambourine | Noise + metallic cluster, jingle envelope | ✅ | SYN6 | Synth kit `:tb` |
| Conga / bongo | Sine with a short pitch drop, tuned | ✅ | SYN6 | Synth kit `:cg` |
| Syn-tom / zap | Fast exponential pitch sweep | ✅ | SYN6 | Synth kit `:zap` |
| Snap / click / tick | Filtered impulse | ✅ | SYN6 | Synth kit `:click` |
| Timpani | Membrane modes | ⏸ | SYN7 | |
| Tabla / hand drums | Membrane modes with pitch bend | ⏸ | SYN7 | Samples: `:tabla` |

Recorded kits: Dirt-Samples, Tidal Drum Machines, and the multisampled kits `JazzKit`
and `StudioKit` with velocity layers and round-robin (KIT1).

### Bass

| Sound | How it is synthesized | Status | Phase | Notes |
|---|---|---|---|---|
| Sub bass | Sine, short attack | ✅ | — | Note keyword + `decay` |
| Saw / square bass | Oscillator → low-pass | ✅ | TL7a, SYN6 | `(synth :saw)` + `lpf`, or the instruments below |
| Acid bass | Saw/square → resonant low-pass with envelope, slide and accent | ✅ | SYN6 | `(synth :acid)` with `glide` and `accent` |
| Reese bass | Two or more detuned saws → low-pass | ✅ | SYN6 | `(synth :reese)` |
| FM bass | 2-operator FM, short index decay | ✅ | SYN6 | `(synth :fm-bass)` |
| Wobble bass | Saw → low-pass with an LFO on the cutoff | ✅ | SYN6 | `(synth :wobble)`; `lp-rate` / `lp-depth` on any voice |
| Plucked / upright bass | Karplus-Strong | 🟡 | SYN7 | `:pizz` on low notes; no bass preset |
| Electric bass guitar | Karplus-Strong + pickup filter | ⏸ | SYN7 | |
| Long pitched sub kick as a bass line | Tunable drum voice | ✅ | SYN6 | `:bd` with `tune` and `decay` under `(bank :synth)` |

### Leads & keys

| Sound | How it is synthesized | Status | Phase | Notes |
|---|---|---|---|---|
| Saw / square lead | Oscillator, optionally filtered | ✅ | SYN6 | `(synth :lead)`; band-limited oscillators |
| Supersaw lead | 5–7 detuned saws, stereo spread | ✅ | SYN6 | `(synth :supersaw)` |
| Sync lead | Hard-synced oscillator with a pitch sweep | ✅ | SYN6 | `(synth :sync-lead)` |
| PWM lead | Pulse with an LFO on the width | ✅ | SYN6 | `(synth :pulse)` with `pw-rate` / `pw-depth` |
| Chiptune voices | 12.5/25/50 % pulse, triangle, LFSR noise | ✅ | SYN6 | `:pulse` with `pw`, `:tri`, `(synth :noise :color :lfsr)` |
| Electric piano (tine) | FM, high modulator ratio | ✅ | — | `:epiano` |
| Clavinet | Plucked string + pickup filter | ⏸ | SYN7 | |
| Harpsichord | Bright plucked string, fast release | ⏸ | SYN7 | |
| Celesta / music box | FM or modal, high register | ⏸ | SYN7 | |
| Drawbar organ | 9 sine drawbars + key click + percussion | ⏸ | SYN7 | Rotary speaker in FX2 |
| Pipe / church organ | Additive ranks, slow attack | ⏸ | SYN7 | |
| Accordion / harmonica (free reeds) | Detuned pulse pair → band-pass | ✅ | SYN6 | `(synth :reed)` |
| Piano | Hammered-string model | ⏸ | SYN5 | |
| Synth brass | Saws with a filter envelope, or FM | ✅ | SYN6 | `(synth :brass-pad)`; FM `:synth-brass` documented |
| Pluck synth | Filtered saw with a fast envelope | ✅ | SYN6 | `(synth :pluck)` |
| Theremin / whistle lead | Sine with glide and vibrato | ✅ | SYN6 | `(synth :theremin)` |

### Pads, strings & voices

| Sound | How it is synthesized | Status | Phase | Notes |
|---|---|---|---|---|
| Drone | Sustained sine | ✅ | — | `(pure 60)` |
| Warm pad | Detuned oscillators → low-pass, slow attack | ✅ | SYN6 | `(synth :pad)` |
| String ensemble (string machine) | Unison saws + ensemble chorus, slow attack | ✅ | SYN6 | `(synth :strings)` (no ensemble chorus: track `fx`) |
| Sweep pad | Pad with a slow filter envelope | ✅ | SYN6 | `(synth :sweep-pad)` |
| Bell / glass pad | Modal partials with long decay | ⏸ | SYN7 | |
| Choir / vox pad ("aah", "ooh") | Pulse source → vowel formants | ⏸ | SYN7 | |
| Vowel lead / talking synth | Formant voice with a patterned vowel | ⏸ | SYN7 | TL7c `vowel` puts the same formants on any sound |
| Solo bowed strings | Bowed waveguide | ⏸ | SYN3 | |

### Plucked strings

| Sound | Status | Phase | Notes |
|---|---|---|---|
| Steel-string guitar | ✅ | — | `:western` / `:guitar` |
| Classical guitar | ✅ | — | `:nylon` |
| Harp | ✅ | — | `:harp` |
| Koto | ✅ | — | `:koto` |
| Pizzicato strings | ✅ | — | `:pizz` |
| Lute | ✅ | — | `:lute` |
| Mandolin | ✅ | — | `:mandolin` |
| Electric guitars | ⏸ | SYN4 | `:strat :tele :es335 :sg :lp` |
| Banjo | ⏸ | SYN7 | Short decay, bright body |
| Ukulele | ⏸ | SYN7 | |
| Sitar | ⏸ | SYN7 | Needs a buzzing-bridge nonlinearity; decide in the full spec |
| Shamisen / oud | ⏸ | SYN7 | |
| Hammered dulcimer | ⏸ | SYN7 | |
| Upright / electric bass | ⏸ | SYN7 | See *Bass* |

### Winds & brass

| Sound | Status | Phase | Notes |
|---|---|---|---|
| Saxophone | ✅ | — | `:sax` (FM + formant EQ + breath noise) |
| Trumpet | ✅ | — | `:trumpet` |
| Muted trumpet | ✅ | — | `:trumpet-muted` |
| Harmon-muted trumpet | ✅ | — | `:harmon-out`, `:harmon-in` |
| Trombone | ✅ | — | `:trombone` |
| Flute | ✅ | — | `:flute` |
| Synth brass | ✅ | — | `:synth-brass` |
| Cup-muted trumpet | ✅ | SYN6 | `:trumpet-cup` |
| Clarinet | ⏸ | SYN7 | Odd harmonics (modulator ratio 2) |
| Oboe / bassoon | ⏸ | SYN7 | |
| French horn / tuba | ⏸ | SYN7 | |
| Pan flute / blown bottle | ⏸ | SYN7 | FM + breath noise |
| Recorder / ocarina / whistle | ⏸ | SYN7 | Near-sine with a soft attack |
| Harmonica / accordion | ✅ | SYN6 | `(synth :reed)`; see *Leads & keys* |

### Mallets & bells

| Sound | Status | Phase | Notes |
|---|---|---|---|
| Bell | ✅ | — | `:bell` (FM, inharmonic ratio) |
| Marimba | ✅ | — | `:marimba` (FM) |
| Vibraphone | ⏸ | SYN7 | Modal partials + motor tremolo |
| Glockenspiel | ⏸ | SYN7 | |
| Xylophone | ⏸ | SYN7 | |
| Kalimba / mbira | ⏸ | SYN7 | |
| Steel drum | ⏸ | SYN7 | |
| Tubular bells / church bell | ⏸ | SYN7 | |
| Glass / singing bowl | ⏸ | SYN7 | |
| Gong / tam-tam | ⏸ | SYN7 | |

### Sound effects & textures

| Sound | How it is synthesized | Status | Phase | Notes |
|---|---|---|---|---|
| Noise hit | White noise with a decay | ✅ | — | `(synth :noise)` |
| Sweeps by hand | Filter or pitch moved by a signal | 🟡 | TL7a | Possible for `rate` today; filter sweeps need TL7a |
| Riser / uplifter | Noise or saw with rising pitch and filter over the whole event | ⏸ | SYN7 | Needs the event length in the engine |
| Downlifter / fall | The reverse | ⏸ | SYN7 | |
| Noise sweep / whoosh | Band-passed noise with a moving centre | ⏸ | SYN7 | |
| Laser / zap | Fast exponential pitch drop | ✅ | SYN6 | The `:zap` drum voice |
| Siren / alarm | Oscillator with a slow pitch LFO | ⏸ | SYN7 | |
| Impact / boom | Sub drop + noise burst | ⏸ | SYN7 | |
| Wind | Band-passed noise, slowly wandering cutoff | ⏸ | SYN7 | Pink noise from SYN6 |
| Rain / vinyl crackle | Dust impulses + filtered noise | ⏸ | SYN7 | |
| Surf / ocean | Brown noise with a slow amplitude swell | ⏸ | SYN7 | |
| Blips / UI tones / telephone | Short sines, dual tones | ⏸ | SYN7 | |

---

## Part 3 — Effects

All effects below are synthetic by nature (signal processing). REPuLse has 15 track-level
effect plugins (`(fx …)`, global chain or per track) and no per-event effects yet.

### Filters & EQ

| Effect | Status | Phase | Notes |
|---|---|---|---|
| Low-/high-/band-pass, notch (track) | ✅ | — | `(fx :filter :type …)`, 12 dB/oct biquad |
| Low-/high-/band-pass per event | 📋 | TL7a | `lpf` `hpf` `bpf` + resonance |
| Filter envelope per event | ✅ | TL7c | `lp-env` … `bp-release` |
| DJ filter (one knob LP↔HP) | ✅ | TL7c | `djf` |
| Vowel / formant filter | ✅ | TL7c | `vowel` (`:a :e :i :o :u`) |
| Shelving, peaking, all-pass types on `filter` | ✅ | FX1 | `(fx :filter :type :peaking :gain 6 …)`; all 8 `BiquadFilterNode` types |
| Ladder filter (24 dB/oct, self-oscillating) | ⏸ | FX2 | |
| Comb filter | ⏸ | FX2 | |
| Tuned resonator bank | ⏸ | FX2 | |
| Auto-wah / envelope filter | ⏸ | FX2 | |
| Parametric EQ (shelves + peaking bands) | ✅ | FX1 | `(fx :eq :low … :mid … :mid2 … :high …)` |

### Dynamics

| Effect | Status | Phase | Notes |
|---|---|---|---|
| Compressor | ✅ | — | `(fx :compressor …)`, `DynamicsCompressorNode` |
| Sidechain ducking | ✅ | — | `(fx :sidechain :trigger :bd …)`, pattern-triggered |
| Per-orbit ducking | ❓ | — | `duck`: deferred by TL7c; `(fx :sidechain …)` covers track ducking |
| Limiter (output ceiling) | ✅ | FX1 | `(fx :limiter -1)`, 5 ms look-ahead worklet; the engine bound is ±8 since FX1 |
| Noise gate / expander | ✅ | FX1 | `(fx :gate …)` with hold and `:range` (expander) |
| Transient shaper | ⏸ | FX2 | |
| Multiband compressor | ⏸ | FX2 | |
| De-esser | 💤 | — | Vocal-recording tool; no audio input in REPuLse |

### Distortion & lo-fi

| Effect | Status | Phase | Notes |
|---|---|---|---|
| Overdrive | ✅ | — | `(fx :overdrive …)` |
| Soft clipping, symmetric and asymmetric | ✅ | — | `(fx :distort …)` with `:algo`, `:asym`, oversampling |
| Multi-stage amp simulation | ✅ | — | `(fx :amp-sim …)` |
| Waveshaper lookup table, Chebyshev | ✅ | — | `(fx :waveshape …)` |
| Speaker cabinet | ✅ | — | `(fx :cab …)` |
| Bit crusher | ✅ | — | `(fx :bitcrusher :bits … :rate …)` |
| Per-event `shape`, `crush` | 📋 | TL7a | |
| Per-event `coarse` | ✅ | TL7c | Synth voices (WASM) only; samples ignore it |
| Per-event `distort` | 🔁 | — | Per-event `shape` + `postgain`, or track-level `(fx :distort …)` |
| Wavefolder | ⏸ | FX2 | FUTURE-FEATURES backlog row |
| Tape saturation, wow & flutter, hiss | ⏸ | FX2 | |
| Vinyl (crackle, rumble, band limit) | ⏸ | FX2 | |
| Exciter (added upper harmonics) | ⏸ | FX2 | |

### Modulation

| Effect | Status | Phase | Notes |
|---|---|---|---|
| Chorus | ✅ | — | `(fx :chorus …)` |
| Phaser | ✅ | — | `(fx :phaser …)` |
| Tremolo | ✅ | — | `(fx :tremolo …)` |
| Flanger | ✅ | FX1 | `(fx :flanger …)`, worklet with feedback |
| Vibrato (pitch modulation of a whole track) | ✅ | FX1 | `(fx :vibrato …)`; per-event `vib` is TL7c |
| Ring modulator | ✅ | FX1 | `(fx :ring-mod …)`; `(* a b)` also exists inside `defsynth` |
| Rotary speaker | ⏸ | FX2 | Parity row `leslie` is undecided; FX2 settles it |
| Frequency shifter | ⏸ | FX2 | |

### Stereo & space

| Effect | Status | Phase | Notes |
|---|---|---|---|
| Pan per event | ✅ | — | `pan`, `jux` |
| Convolution reverb | ✅ | FX1 | `(fx :reverb …)` with `type`, `decay`, `size`, `damp`, `predelay` |
| Plate reverb | ✅ | — | `(fx :dattorro-reverb …)` with decay, damping, pre-delay |
| Reverb send per event | 📋 | TL7b | `room`, `size`, `orbit` |
| Room / hall / spring / gated / reverse reverb | ✅ | FX1 | `(fx :reverb :type …)`; `:spring` kept after the FX1 listen check (D7) |
| Shimmer reverb | ⏸ | FX2 | Needs the pitch shifter |
| Auto-pan | ✅ | FX1 | `(fx :auto-pan …)`, equal power, `:sync` |
| Stereo width (mid/side), Haas widener | ✅ | FX1 | `(fx :width … :haas …)` |
| Multichannel panning | 💤 | — | Parity doc: ⛔ (SuperDirt multichannel) |
| Effect routing (reorder, parallel, sends) | ⏸ | P2 | |

### Delay

| Effect | Status | Phase | Notes |
|---|---|---|---|
| Feedback delay | ✅ | FX1 | `(fx :delay …)` with `:mode`, `:lowcut`/`:highcut` in the repeats |
| Delay send per event | 📋 | TL7b | `delay`, `delay-time`, `delay-feedback` |
| Tempo-synced delay time | ✅ | TL7c, FX1 | `delay-sync` per event (TL7c); `(fx :delay :sync 3/16)` on the track effect (FX1) |
| Ping-pong delay | ✅ | FX1 | `(fx :delay :mode :ping-pong …)` |
| Multi-tap delay | ✅ | FX1 | `(fx :delay :taps [1/8 3/8] …)` |
| Tape delay (filtered, saturating, wobbling repeats) | ⏸ | FX2 | |

### Pitch, time & spectral

| Effect | Status | Phase | Notes |
|---|---|---|---|
| Sample speed / reverse / slicing | 🟡 | — | TL6 functions; samples only, pitch follows speed |
| Pitch shifter (pitch without speed) | ⏸ | FX2 | |
| Octaver / harmonizer | ⏸ | FX2 | |
| Freeze / sustain | ⏸ | FX2 | |
| Granular processor | ⏸ | FX2 | FUTURE-FEATURES backlog row |
| Stutter / beat repeat on the audio | ⏸ | FX2 | Pattern-level `stutter`, `ply`, `chop` exist |
| Reverse audio in tempo | ⏸ | FX2 | |
| Vocoder | ⏸ | FX2 | Needs a modulator input (audio bus) |
| Time-stretch without pitch change | 💤 | — | TL6 decision: pitch follows speed |
| FFT spectral effects (bin shift, smear, brick-wall) | 💤 | — | Parity doc: ⛔ (SuperDirt-only) |

---

## Findings outside the gap list

Things the inventory turned up in existing code and docs:

- USAGE.md documents `(synth :trumpet-cup)`, but neither `fm_preset` in `lib.rs` nor the
  dispatch set in `audio.cljs` knows it. It falls through to a plain sine. SYN6 adds the
  preset and a warning for unknown voice names.
- `:synth-brass`, `:harmon-out` and `:harmon-in` work but are missing from the USAGE.md
  voice table (SYN6).
- Once the Dirt-Samples manifest has loaded, `:bd :sd :hh :oh` play samples. The
  synthesized drums are a fallback and are then unreachable; SYN6 gives them their own
  bank, `(bank :synth)`.
- The WASM `:saw` and `:square` alias on high notes. A filter after the oscillator
  cannot remove this, because the folded-back partials land between the wanted
  harmonics; SYN6 band-limits the oscillators themselves.
- USAGE.md showed `(fx :reverb :wet 0.4 :room 0.8)`, but the reverb ignores `room`
  (it knows `wet` and `dry`). The example was corrected with this document; FX1 adds
  real `size`, `decay` and `type` controls.
