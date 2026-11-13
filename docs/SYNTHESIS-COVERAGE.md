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
| 📋 | Planned in an existing phase (TL7a–c, KIT1) |
| ⏸ | Missing; specified in a deferred phase (SYN3–5, SYN7, FX2, P2), revisited after M2 |
| 🆕 | Missing; scheduled in Milestone 1 by the phases added with this document (SYN6, FX1) |
| 💤 | Missing and not scheduled; reason in the row |

The gaps found by this inventory are split by one question, the same one that deferred
SYN3–SYN5: *does a song need it?* What a song needs (clean oscillators, a synth drum
kit, the standard synth instruments, mixing and space effects) is scheduled in **SYN6**
and **FX1**. The rest (more acoustic instruments, mallets, organ, choir, sound-effect
generators, filter models, tape colour, pitch and time effects) is specified in **SYN7**
and **FX2** and deferred: samples cover those roles until then.

TL7a–c are the three phases TL7 was split into (PR #171 on `main`; `dev` still lists the
single outline `TL7` until that is merged back).

## Summary

| Area | ✅ | 🟡 | 📋 | ⏸ | 🆕 | 💤 | Total |
|---|---|---|---|---|---|---|---|
| Synthesis methods | 5 | 4 | 0 | 5 | 4 | 5 | 23 |
| Modulation sources | 2 | 3 | 5 | 0 | 2 | 0 | 12 |
| Drums & percussion | 0 | 4 | 0 | 2 | 12 | 0 | 18 |
| Bass | 1 | 4 | 0 | 1 | 3 | 0 | 9 |
| Leads & keys | 1 | 4 | 0 | 6 | 5 | 0 | 16 |
| Pads, strings & voices | 1 | 1 | 0 | 4 | 2 | 0 | 8 |
| Plucked strings | 7 | 0 | 0 | 7 | 0 | 0 | 14 |
| Winds & brass | 7 | 0 | 0 | 5 | 2 | 0 | 14 |
| Mallets & bells | 2 | 0 | 0 | 8 | 0 | 0 | 10 |
| Sound effects & textures | 1 | 1 | 0 | 9 | 1 | 0 | 12 |
| Effects | 14 | 4 | 9 | 22 | 12 | 4 | 65 |
| **Total** | **41** | **25** | **14** | **69** | **43** | **9** | **201** |

Rows that group several names (e.g. "toms lo/mid/hi") count once.

---

## Part 1 — Synthesis methods

| Method | What it is good for | Status | Phase | Notes |
|---|---|---|---|---|
| Sine oscillator | Sub bass, pure tones, drones | ✅ | — | Default voice for note keywords and Hz numbers (`Voice::Tone`) |
| Sawtooth, pulse/square | Subtractive basses, leads, pads | 🟡 | SYN6 | `(synth :saw)`, `(synth :square :pw …)`. The WASM oscillators are naive (not band-limited) and alias on high notes |
| Triangle | Flute-like tones, chip bass | 🟡 | SYN6 | Only as a `defsynth` UGen (`tri`), not as a `synth` voice |
| Band-limited oscillators (PolyBLEP) | Clean high notes, sweeps | 🆕 | SYN6 | `defsynth` already uses band-limited native `OscillatorNode`s; the WASM voices do not |
| Pulse-width modulation | Moving, chorused pulse tones | 🟡 | SYN6 | Pulse width is fixed per event (`:pw`), not modulated within a note |
| Unison / supersaw | Trance leads, reese bass, string ensembles | 🆕 | SYN6 | Resolves the ❓ `detune`/`unison`/`spread` parity row |
| Oscillator hard sync | Tearing sync leads | 🆕 | SYN6 | |
| White noise | Hats, snares, sweeps | ✅ | — | `(synth :noise)`; LCG generator in Rust |
| Coloured noise (pink, brown), dust/crackle, LFSR chip noise | Wind, surf, vinyl, chiptune drums | 🆕 | SYN6 | |
| Subtractive voice (oscillator → filter → amp, each with an envelope) | The classic analog synth sound | 🟡 | TL7a, TL7c, SYN6 | Built-in voices have no filter. `defsynth` has `lpf`/`hpf`/`bpf`. TL7a adds a per-event filter, TL7c its envelope, SYN6 ready-made instruments |
| FM / phase modulation, 2 operators | Bells, electric pianos, brass, reeds | ✅ | — | `(synth :fm :index … :ratio …)` plus 11 presets |
| FM, 4–6 operators with algorithms and feedback | DX-style keys, basses, evolving timbres | 💤 | — | Large engine and UI surface; 2-operator presets cover the common cases. Per-event FM controls are a TL7c decision |
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
| `adsr` shorthands, `sustain`/`hold` for synth voices | 📋 | TL7c | |
| Filter envelope | 📋 | TL7c | `lp-env`, `lp-attack`, … |
| Pitch envelope | 📋 | TL7c | `penv` …; drum voices get their own sweeps in SYN6 |
| Vibrato | 📋 | TL7c | `vib`, `vib-mod`; koto and mandolin presets have fixed vibrato |
| Velocity | 📋 | TL7a, KIT1 | |
| LFO on a synth parameter | 🟡 | — | Only through a control bus in `defsynth` |
| Pattern-rate modulation (one value per event) | 🟡 | TL7a | Signals (`sine`, `saw`, `perlin`, …) drive `amp`, `pan`, `rate` today; filters follow in TL7a |
| Session-time ramps | 🟡 | SONG2 | `tween` for `amp` and `pan` only; song-time automation planned |
| Glide / portamento between notes | 🆕 | SYN6 | |
| Per-voice LFO and PWM within a note | 🆕 | SYN6 | |

---

## Part 2 — Sounds by instrument family

### Drums & percussion

Synthesized drums are the oldest use of synthesis (analog drum machines): tuned sine
sweeps, filtered noise and clusters of detuned square waves.

| Sound | How it is synthesized | Status | Phase | Notes |
|---|---|---|---|---|
| Kick | Sine with a fast downward pitch sweep | 🟡 | SYN6 | `:bd`, fixed 150 → 40 Hz, not tunable. Heard only while no `bd` sample bank is loaded |
| Snare | Tuned sine(s) + band-passed noise | 🟡 | SYN6 | `:sd`, fixed |
| Closed hi-hat | High-passed noise or 6 detuned squares, short | 🟡 | SYN6 | `:hh`, noise only (no metallic source) |
| Open hi-hat | Same source, long decay, choked by the closed hat | 🟡 | SYN6 | `:oh`; no choke for synthesized hats (`cut` works on samples only) |
| Long sub kick / pitched bass drum | Sine, slow decay, tuned to the song key | 🆕 | SYN6 | |
| Clap | 3–4 noise bursts through a band-pass + tail | 🆕 | SYN6 | Samples: `:cp` |
| Rimshot / side stick | Very short tuned pulse through band-passes | 🆕 | SYN6 | |
| Toms lo/mid/hi | Sine with a mild pitch drop | 🆕 | SYN6 | |
| Cowbell | Two detuned squares through a band-pass | 🆕 | SYN6 | |
| Claves / woodblock | Short high sine blip | 🆕 | SYN6 | |
| Crash / ride cymbal | Metallic square cluster, long decay | 🆕 | SYN6 | |
| Shaker / maracas / cabasa | High-passed noise with a soft attack | 🆕 | SYN6 | |
| Tambourine | Noise + metallic cluster, jingle envelope | 🆕 | SYN6 | |
| Conga / bongo | Sine with a short pitch drop, tuned | 🆕 | SYN6 | |
| Syn-tom / zap | Fast exponential pitch sweep | 🆕 | SYN6 | Drum voice `:zap` |
| Snap / click / tick | Filtered impulse | 🆕 | SYN6 | |
| Timpani | Membrane modes | ⏸ | SYN7 | |
| Tabla / hand drums | Membrane modes with pitch bend | ⏸ | SYN7 | Samples: `:tabla` |

Recorded kits: Dirt-Samples and Tidal Drum Machines today, multisampled kits with
velocity layers in KIT1.

### Bass

| Sound | How it is synthesized | Status | Phase | Notes |
|---|---|---|---|---|
| Sub bass | Sine, short attack | ✅ | — | Note keyword + `decay` |
| Saw / square bass | Oscillator → low-pass | 🟡 | TL7a, SYN6 | Unfiltered today |
| Acid bass | Saw/square → resonant low-pass with envelope, slide and accent | 🆕 | SYN6 | Filter and envelope from TL7a/TL7c |
| Reese bass | Two or more detuned saws → low-pass | 🆕 | SYN6 | |
| FM bass | 2-operator FM, short index decay | 🟡 | SYN6 | By hand with `(synth :fm :index … :ratio …)`; no preset |
| Wobble bass | Saw → low-pass with an LFO on the cutoff | 🟡 | SYN6 | By hand with `defsynth` + a control bus |
| Plucked / upright bass | Karplus-Strong | 🟡 | SYN7 | `:pizz` on low notes; no bass preset |
| Electric bass guitar | Karplus-Strong + pickup filter | ⏸ | SYN7 | |
| Long pitched sub kick as a bass line | Tunable drum voice | 🆕 | SYN6 | |

### Leads & keys

| Sound | How it is synthesized | Status | Phase | Notes |
|---|---|---|---|---|
| Saw / square lead | Oscillator, optionally filtered | 🟡 | SYN6 | Aliasing on high notes |
| Supersaw lead | 5–7 detuned saws, stereo spread | 🆕 | SYN6 | |
| Sync lead | Hard-synced oscillator with a pitch sweep | 🆕 | SYN6 | |
| PWM lead | Pulse with an LFO on the width | 🆕 | SYN6 | |
| Chiptune voices | 12.5/25/50 % pulse, triangle, LFSR noise | 🟡 | SYN6 | Pulse via `:pw`; triangle voice and LFSR noise missing |
| Electric piano (tine) | FM, high modulator ratio | ✅ | — | `:epiano` |
| Clavinet | Plucked string + pickup filter | ⏸ | SYN7 | |
| Harpsichord | Bright plucked string, fast release | ⏸ | SYN7 | |
| Celesta / music box | FM or modal, high register | ⏸ | SYN7 | |
| Drawbar organ | 9 sine drawbars + key click + percussion | ⏸ | SYN7 | Rotary speaker in FX2 |
| Pipe / church organ | Additive ranks, slow attack | ⏸ | SYN7 | |
| Accordion / harmonica (free reeds) | Detuned pulse pair → band-pass | 🆕 | SYN6 | |
| Piano | Hammered-string model | ⏸ | SYN5 | |
| Synth brass | Saws with a filter envelope, or FM | 🟡 | SYN6 | FM `:synth-brass` exists in the engine but is undocumented |
| Pluck synth | Filtered saw with a fast envelope | 🟡 | SYN6 | By hand with `defsynth` |
| Theremin / whistle lead | Sine with glide and vibrato | 🆕 | SYN6 | Vibrato from TL7c |

### Pads, strings & voices

| Sound | How it is synthesized | Status | Phase | Notes |
|---|---|---|---|---|
| Drone | Sustained sine | ✅ | — | `(pure 60)` |
| Warm pad | Detuned oscillators → low-pass, slow attack | 🟡 | SYN6 | By hand with `defsynth` |
| String ensemble (string machine) | Unison saws + ensemble chorus, slow attack | 🆕 | SYN6 | |
| Sweep pad | Pad with a slow filter envelope | 🆕 | SYN6 | |
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
| Harmon-muted trumpet | ✅ | — | `:harmon-out`, `:harmon-in`; in the engine, missing from the USAGE.md table |
| Trombone | ✅ | — | `:trombone` |
| Flute | ✅ | — | `:flute` |
| Synth brass | ✅ | — | `:synth-brass`; missing from the USAGE.md table |
| Cup-muted trumpet | 🆕 | SYN6 | USAGE.md documents `:trumpet-cup`, but no such preset exists; it plays a plain sine |
| Clarinet | ⏸ | SYN7 | Odd harmonics (modulator ratio 2) |
| Oboe / bassoon | ⏸ | SYN7 | |
| French horn / tuba | ⏸ | SYN7 | |
| Pan flute / blown bottle | ⏸ | SYN7 | FM + breath noise |
| Recorder / ocarina / whistle | ⏸ | SYN7 | Near-sine with a soft attack |
| Harmonica / accordion | 🆕 | SYN6 | See *Leads & keys* |

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
| Laser / zap | Fast exponential pitch drop | 🆕 | SYN6 | The `:zap` drum voice |
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
| Filter envelope per event | 📋 | TL7c | |
| DJ filter (one knob LP↔HP) | 📋 | TL7c | `djf` |
| Vowel / formant filter | 📋 | TL7c | `vowel` |
| Shelving, peaking, all-pass types on `filter` | 🆕 | FX1 | Native `BiquadFilterNode` types, not exposed |
| Ladder filter (24 dB/oct, self-oscillating) | ⏸ | FX2 | |
| Comb filter | ⏸ | FX2 | |
| Tuned resonator bank | ⏸ | FX2 | |
| Auto-wah / envelope filter | ⏸ | FX2 | |
| Parametric EQ (shelves + peaking bands) | 🆕 | FX1 | FUTURE-FEATURES backlog row |

### Dynamics

| Effect | Status | Phase | Notes |
|---|---|---|---|
| Compressor | ✅ | — | `(fx :compressor …)`, `DynamicsCompressorNode` |
| Sidechain ducking | ✅ | — | `(fx :sidechain :trigger :bd …)`, pattern-triggered |
| Per-orbit ducking | 📋 | TL7c | `duck`, undecided there |
| Limiter (output ceiling) | 🆕 | FX1 | The engine hard-clips at ±1 today |
| Noise gate / expander | 🆕 | FX1 | |
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
| Per-event `coarse`, `distort` | 📋 | TL7c | |
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
| Flanger | 🆕 | FX1 | |
| Vibrato (pitch modulation of a whole track) | 🆕 | FX1 | Per-event `vib` is TL7c |
| Ring modulator | 🆕 | FX1 | FUTURE-FEATURES backlog row; `(* a b)` exists inside `defsynth` |
| Rotary speaker | ⏸ | FX2 | Parity row `leslie` is undecided; FX2 settles it |
| Frequency shifter | ⏸ | FX2 | |

### Stereo & space

| Effect | Status | Phase | Notes |
|---|---|---|---|
| Pan per event | ✅ | — | `pan`, `jux` |
| Convolution reverb | 🟡 | FX1 | `(fx :reverb …)`: one generated impulse response, no size/decay/damping control |
| Plate reverb | ✅ | — | `(fx :dattorro-reverb …)` with decay, damping, pre-delay |
| Reverb send per event | 📋 | TL7b | `room`, `size`, `orbit` |
| Room / hall / spring / gated / reverse reverb | 🆕 | FX1 | |
| Shimmer reverb | ⏸ | FX2 | Needs the pitch shifter |
| Auto-pan | 🆕 | FX1 | |
| Stereo width (mid/side), Haas widener | 🆕 | FX1 | |
| Multichannel panning | 💤 | — | Parity doc: ⛔ (SuperDirt multichannel) |
| Effect routing (reorder, parallel, sends) | ⏸ | P2 | |

### Delay

| Effect | Status | Phase | Notes |
|---|---|---|---|
| Feedback delay | 🟡 | FX1 | `(fx :delay …)`: time in seconds only, mono feedback path |
| Delay send per event | 📋 | TL7b | `delay`, `delay-time`, `delay-feedback` |
| Tempo-synced delay time | 🟡 | TL7c, FX1 | `delay-sync` per event (TL7c); `:sync` on the track effect (FX1) |
| Ping-pong delay | 🆕 | FX1 | |
| Multi-tap delay | 🆕 | FX1 | |
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
