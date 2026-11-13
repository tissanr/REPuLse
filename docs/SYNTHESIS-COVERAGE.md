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
| ⏸ | Covered by a deferred phase (SYN3–5, P2) |
| 🆕 | Missing; scheduled by the phases added with this document (SYN6–11, FX1–5) |
| 💤 | Missing and not scheduled; reason in the row |

TL7a–c are the three phases TL7 was split into (PR #171 on `main`; `dev` still lists the
single outline `TL7` until that is merged back).

## Summary

| Area | ✅ | 🟡 | 📋 | ⏸ | 🆕 | 💤 | Total |
|---|---|---|---|---|---|---|---|
| Synthesis methods | 5 | 4 | 0 | 2 | 7 | 5 | 23 |
| Modulation sources | 2 | 3 | 5 | 0 | 2 | 0 | 12 |
| Drums & percussion | 0 | 4 | 0 | 0 | 14 | 0 | 18 |
| Bass | 1 | 4 | 0 | 0 | 4 | 0 | 9 |
| Leads & keys | 1 | 4 | 0 | 1 | 10 | 0 | 16 |
| Pads, strings & voices | 1 | 1 | 0 | 1 | 5 | 0 | 8 |
| Plucked strings | 7 | 0 | 0 | 1 | 6 | 0 | 14 |
| Winds & brass | 7 | 0 | 0 | 0 | 7 | 0 | 14 |
| Mallets & bells | 2 | 0 | 0 | 0 | 8 | 0 | 10 |
| Sound effects & textures | 1 | 1 | 0 | 0 | 10 | 0 | 12 |
| Effects | 14 | 4 | 9 | 1 | 33 | 4 | 65 |
| **Total** | **41** | **25** | **14** | **6** | **106** | **9** | **201** |

Rows that group several names (e.g. "toms lo/mid/hi") count once.

---

## Part 1 — Synthesis methods

| Method | What it is good for | Status | Phase | Notes |
|---|---|---|---|---|
| Sine oscillator | Sub bass, pure tones, drones | ✅ | — | Default voice for note keywords and Hz numbers (`Voice::Tone`) |
| Sawtooth, pulse/square | Subtractive basses, leads, pads | 🟡 | SYN8 | `(synth :saw)`, `(synth :square :pw …)`. The WASM oscillators are naive (not band-limited) and alias on high notes |
| Triangle | Flute-like tones, chip bass | 🟡 | SYN8 | Only as a `defsynth` UGen (`tri`), not as a `synth` voice |
| Band-limited oscillators (PolyBLEP) | Clean high notes, sweeps | 🆕 | SYN8 | `defsynth` already uses band-limited native `OscillatorNode`s; the WASM voices do not |
| Pulse-width modulation | Moving, chorused pulse tones | 🟡 | SYN8 | Pulse width is fixed per event (`:pw`), not modulated within a note |
| Unison / supersaw | Trance leads, reese bass, string ensembles | 🆕 | SYN8 | Resolves the ❓ `detune`/`unison`/`spread` parity row |
| Oscillator hard sync | Tearing sync leads | 🆕 | SYN10 | |
| White noise | Hats, snares, sweeps | ✅ | — | `(synth :noise)`; LCG generator in Rust |
| Coloured noise (pink, brown), dust/crackle, LFSR chip noise | Wind, surf, vinyl, chiptune drums | 🆕 | SYN8 | |
| Subtractive voice (oscillator → filter → amp, each with an envelope) | The classic analog synth sound | 🟡 | TL7a, TL7c, SYN10 | Built-in voices have no filter. `defsynth` has `lpf`/`hpf`/`bpf`. TL7a adds a per-event filter, TL7c its envelope, SYN10 ready-made instruments |
| FM / phase modulation, 2 operators | Bells, electric pianos, brass, reeds | ✅ | — | `(synth :fm :index … :ratio …)` plus 11 presets |
| FM, 4–6 operators with algorithms and feedback | DX-style keys, basses, evolving timbres | 💤 | — | Large engine and UI surface; 2-operator presets cover the common cases. Per-event FM controls are a TL7c decision |
| Karplus-Strong plucked string | Guitar, harp, koto, pizzicato | ✅ | — | `Voice::KarplusStrong`, 7 presets + alias, body EQ per instrument |
| Bowed-string waveguide | Violin, viola, cello, arco bass | ⏸ | SYN3 | Full spec exists |
| Wind waveguides (bore + reed/jet model) | Physically modelled flute, clarinet, brass | 💤 | — | FM presets cover winds and brass (SYN2, SYN6); backlog row in FUTURE-FEATURES.md |
| Hammered-string model | Piano | ⏸ | SYN5 | Lo-fi piano; high-fidelity piano is a FUTURE-FEATURES.md backlog row |
| Modal synthesis (bank of decaying resonators) | Vibraphone, glockenspiel, kalimba, steel drum, bells, glass | 🆕 | SYN9 | |
| Additive synthesis (sum of harmonics) | Drawbar organ, pipe organ | 🆕 | SYN9 | |
| Formant synthesis (source + vowel filter bank) | Choirs, vowel leads, talking synths | 🆕 | SYN11 | Shares the formant table with TL7c `vowel` |
| Wavetable | Morphing digital timbres | 💤 | — | Parity doc marks `wt*`/`warp*` out of scope for Milestone 1; backlog row in FUTURE-FEATURES.md |
| Granular synthesis | Clouds, time-stretch textures | 💤 | — | Backlog row in FUTURE-FEATURES.md; FX5 adds a granular *effect* |
| Spectral resynthesis / speech synthesis (FFT, LPC) | Vocoded speech, morphing | 💤 | — | Not planned; FX5 vocoder covers the musical use |
| User-defined node graphs | Anything from the UGens | ✅ | — | `defsynth` with `sin saw square tri noise lpf hpf bpf gain delay-node mix env-perc env-asr env-gen`, buses `bus`/`out`/`in` |

### Modulation sources

| Source | Status | Phase | Notes |
|---|---|---|---|
| Amplitude envelope: attack, decay, release | ✅ | — | `attack`, `decay`, `release` per event; ADSR inside the FM presets |
| Multi-segment envelope with curves | ✅ | — | `env` + `env-gen` inside `defsynth` |
| `adsr` shorthands, `sustain`/`hold` for synth voices | 📋 | TL7c | |
| Filter envelope | 📋 | TL7c | `lp-env`, `lp-attack`, … |
| Pitch envelope | 📋 | TL7c | `penv` …; drum voices get their own sweeps in SYN7 |
| Vibrato | 📋 | TL7c | `vib`, `vib-mod`; koto and mandolin presets have fixed vibrato |
| Velocity | 📋 | TL7a, KIT1 | |
| LFO on a synth parameter | 🟡 | — | Only through a control bus in `defsynth` |
| Pattern-rate modulation (one value per event) | 🟡 | TL7a | Signals (`sine`, `saw`, `perlin`, …) drive `amp`, `pan`, `rate` today; filters follow in TL7a |
| Session-time ramps | 🟡 | SONG2 | `tween` for `amp` and `pan` only; song-time automation planned |
| Glide / portamento between notes | 🆕 | SYN10 | |
| Per-voice LFO and PWM within a note | 🆕 | SYN8 | |

---

## Part 2 — Sounds by instrument family

### Drums & percussion

Synthesized drums are the oldest use of synthesis (analog drum machines): tuned sine
sweeps, filtered noise and clusters of detuned square waves.

| Sound | How it is synthesized | Status | Phase | Notes |
|---|---|---|---|---|
| Kick | Sine with a fast downward pitch sweep | 🟡 | SYN7 | `:bd`, fixed 150 → 40 Hz, not tunable. Heard only while no `bd` sample bank is loaded |
| Snare | Tuned sine(s) + band-passed noise | 🟡 | SYN7 | `:sd`, fixed |
| Closed hi-hat | High-passed noise or 6 detuned squares, short | 🟡 | SYN7 | `:hh`, noise only (no metallic source) |
| Open hi-hat | Same source, long decay, choked by the closed hat | 🟡 | SYN7 | `:oh`; no choke for synthesized hats (`cut` works on samples only) |
| Long sub kick / pitched bass drum | Sine, slow decay, tuned to the song key | 🆕 | SYN7 | |
| Clap | 3–4 noise bursts through a band-pass + tail | 🆕 | SYN7 | Samples: `:cp` |
| Rimshot / side stick | Very short tuned pulse through band-passes | 🆕 | SYN7 | |
| Toms lo/mid/hi | Sine with a mild pitch drop | 🆕 | SYN7 | |
| Cowbell | Two detuned squares through a band-pass | 🆕 | SYN7 | |
| Claves / woodblock | Short high sine blip | 🆕 | SYN7 | |
| Crash / ride cymbal | Metallic square cluster, long decay | 🆕 | SYN7 | |
| Shaker / maracas / cabasa | High-passed noise with a soft attack | 🆕 | SYN7 | |
| Tambourine | Noise + metallic cluster, jingle envelope | 🆕 | SYN7 | |
| Conga / bongo | Sine with a short pitch drop, tuned | 🆕 | SYN7 | |
| Syn-tom / zap | Fast exponential pitch sweep | 🆕 | SYN7 | Also `:zap` as an SFX in SYN11 |
| Snap / click / tick | Filtered impulse | 🆕 | SYN7 | |
| Timpani | Membrane modes | 🆕 | SYN9 | |
| Tabla / hand drums | Membrane modes with pitch bend | 🆕 | SYN9 | Samples: `:tabla` |

Recorded kits: Dirt-Samples and Tidal Drum Machines today, multisampled kits with
velocity layers in KIT1.

### Bass

| Sound | How it is synthesized | Status | Phase | Notes |
|---|---|---|---|---|
| Sub bass | Sine, short attack | ✅ | — | Note keyword + `decay` |
| Saw / square bass | Oscillator → low-pass | 🟡 | TL7a, SYN10 | Unfiltered today |
| Acid bass | Saw/square → resonant low-pass with envelope, slide and accent | 🆕 | SYN10 | Filter and envelope from TL7a/TL7c |
| Reese bass | Two or more detuned saws → low-pass | 🆕 | SYN10 | Unison from SYN8 |
| FM bass | 2-operator FM, short index decay | 🟡 | SYN10 | By hand with `(synth :fm :index … :ratio …)`; no preset |
| Wobble bass | Saw → low-pass with an LFO on the cutoff | 🟡 | SYN10 | By hand with `defsynth` + a control bus |
| Plucked / upright bass | Karplus-Strong | 🟡 | SYN6 | `:pizz` on low notes; no bass preset |
| Electric bass guitar | Karplus-Strong + pickup filter | 🆕 | SYN6 | |
| Long pitched sub kick as a bass line | Tunable drum voice | 🆕 | SYN7 | |

### Leads & keys

| Sound | How it is synthesized | Status | Phase | Notes |
|---|---|---|---|---|
| Saw / square lead | Oscillator, optionally filtered | 🟡 | SYN8, SYN10 | Aliasing on high notes |
| Supersaw lead | 5–7 detuned saws, stereo spread | 🆕 | SYN8 | |
| Sync lead | Hard-synced oscillator with a pitch sweep | 🆕 | SYN10 | |
| PWM lead | Pulse with an LFO on the width | 🆕 | SYN8 | |
| Chiptune voices | 12.5/25/50 % pulse, triangle, LFSR noise | 🟡 | SYN8 | Pulse via `:pw`; triangle voice and LFSR noise missing |
| Electric piano (tine) | FM, high modulator ratio | ✅ | — | `:epiano` |
| Clavinet | Plucked string + pickup filter | 🆕 | SYN6 | |
| Harpsichord | Bright plucked string, fast release | 🆕 | SYN6 | |
| Celesta / music box | FM or modal, high register | 🆕 | SYN6 | |
| Drawbar organ | 9 sine drawbars + key click + percussion | 🆕 | SYN9 | Rotary speaker in FX1 |
| Pipe / church organ | Additive ranks, slow attack | 🆕 | SYN9 | |
| Accordion / harmonica (free reeds) | Detuned pulse pair → band-pass | 🆕 | SYN10 | |
| Piano | Hammered-string model | ⏸ | SYN5 | |
| Synth brass | Saws with a filter envelope, or FM | 🟡 | SYN10 | FM `:synth-brass` exists in the engine but is undocumented |
| Pluck synth | Filtered saw with a fast envelope | 🟡 | SYN10 | By hand with `defsynth` |
| Theremin / whistle lead | Sine with glide and vibrato | 🆕 | SYN10 | Glide from SYN10, vibrato from TL7c |

### Pads, strings & voices

| Sound | How it is synthesized | Status | Phase | Notes |
|---|---|---|---|---|
| Drone | Sustained sine | ✅ | — | `(pure 60)` |
| Warm pad | Detuned oscillators → low-pass, slow attack | 🟡 | SYN10 | By hand with `defsynth` |
| String ensemble (string machine) | Unison saws + ensemble chorus, slow attack | 🆕 | SYN10 | |
| Sweep pad | Pad with a slow filter envelope | 🆕 | SYN10 | |
| Bell / glass pad | Modal partials with long decay | 🆕 | SYN9 | |
| Choir / vox pad ("aah", "ooh") | Pulse source → vowel formants | 🆕 | SYN11 | |
| Vowel lead / talking synth | Formant voice with a patterned vowel | 🆕 | SYN11 | TL7c `vowel` puts the same formants on any sound |
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
| Banjo | 🆕 | SYN6 | Short decay, bright body |
| Ukulele | 🆕 | SYN6 | |
| Sitar | 🆕 | SYN6 | Needs a buzzing-bridge nonlinearity; decide in the full spec |
| Shamisen / oud | 🆕 | SYN6 | |
| Hammered dulcimer | 🆕 | SYN6 | |
| Upright / electric bass | 🆕 | SYN6 | See *Bass* |

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
| Clarinet | 🆕 | SYN6 | Odd harmonics (modulator ratio 2) |
| Oboe / bassoon | 🆕 | SYN6 | |
| French horn / tuba | 🆕 | SYN6 | |
| Pan flute / blown bottle | 🆕 | SYN6 | FM + breath noise |
| Recorder / ocarina / whistle | 🆕 | SYN6 | Near-sine with a soft attack |
| Harmonica / accordion | 🆕 | SYN10 | See *Leads & keys* |

### Mallets & bells

| Sound | Status | Phase | Notes |
|---|---|---|---|
| Bell | ✅ | — | `:bell` (FM, inharmonic ratio) |
| Marimba | ✅ | — | `:marimba` (FM) |
| Vibraphone | 🆕 | SYN9 | Modal partials + motor tremolo |
| Glockenspiel | 🆕 | SYN9 | |
| Xylophone | 🆕 | SYN9 | |
| Kalimba / mbira | 🆕 | SYN9 | |
| Steel drum | 🆕 | SYN9 | |
| Tubular bells / church bell | 🆕 | SYN9 | |
| Glass / singing bowl | 🆕 | SYN9 | |
| Gong / tam-tam | 🆕 | SYN9 | |

### Sound effects & textures

| Sound | How it is synthesized | Status | Phase | Notes |
|---|---|---|---|---|
| Noise hit | White noise with a decay | ✅ | — | `(synth :noise)` |
| Sweeps by hand | Filter or pitch moved by a signal | 🟡 | TL7a | Possible for `rate` today; filter sweeps need TL7a |
| Riser / uplifter | Noise or saw with rising pitch and filter over the whole event | 🆕 | SYN11 | Needs the event length in the engine |
| Downlifter / fall | The reverse | 🆕 | SYN11 | |
| Noise sweep / whoosh | Band-passed noise with a moving centre | 🆕 | SYN11 | |
| Laser / zap | Fast exponential pitch drop | 🆕 | SYN11 | |
| Siren / alarm | Oscillator with a slow pitch LFO | 🆕 | SYN11 | |
| Impact / boom | Sub drop + noise burst | 🆕 | SYN11 | |
| Wind | Band-passed noise, slowly wandering cutoff | 🆕 | SYN11 | Pink noise from SYN8 |
| Rain / vinyl crackle | Dust impulses + filtered noise | 🆕 | SYN11 | |
| Surf / ocean | Brown noise with a slow amplitude swell | 🆕 | SYN11 | |
| Blips / UI tones / telephone | Short sines, dual tones | 🆕 | SYN11 | |

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
| Shelving, peaking, all-pass types on `filter` | 🆕 | FX3 | Native `BiquadFilterNode` types, not exposed |
| Ladder filter (24 dB/oct, self-oscillating) | 🆕 | FX3 | |
| Comb filter | 🆕 | FX3 | |
| Tuned resonator bank | 🆕 | FX3 | |
| Auto-wah / envelope filter | 🆕 | FX3 | |
| Parametric EQ (shelves + peaking bands) | 🆕 | FX2 | FUTURE-FEATURES backlog row |

### Dynamics

| Effect | Status | Phase | Notes |
|---|---|---|---|
| Compressor | ✅ | — | `(fx :compressor …)`, `DynamicsCompressorNode` |
| Sidechain ducking | ✅ | — | `(fx :sidechain :trigger :bd …)`, pattern-triggered |
| Per-orbit ducking | 📋 | TL7c | `duck`, undecided there |
| Limiter (output ceiling) | 🆕 | FX2 | The engine hard-clips at ±1 today |
| Noise gate / expander | 🆕 | FX2 | |
| Transient shaper | 🆕 | FX2 | |
| Multiband compressor | 🆕 | FX2 | |
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
| Wavefolder | 🆕 | FX3 | FUTURE-FEATURES backlog row |
| Tape saturation, wow & flutter, hiss | 🆕 | FX4 | |
| Vinyl (crackle, rumble, band limit) | 🆕 | FX4 | |
| Exciter (added upper harmonics) | 🆕 | FX4 | |

### Modulation

| Effect | Status | Phase | Notes |
|---|---|---|---|
| Chorus | ✅ | — | `(fx :chorus …)` |
| Phaser | ✅ | — | `(fx :phaser …)` |
| Tremolo | ✅ | — | `(fx :tremolo …)` |
| Flanger | 🆕 | FX1 | |
| Vibrato (pitch modulation of a whole track) | 🆕 | FX1 | Per-event `vib` is TL7c |
| Ring modulator | 🆕 | FX1 | FUTURE-FEATURES backlog row; `(* a b)` exists inside `defsynth` |
| Rotary speaker | 🆕 | FX1 | Parity row `leslie` is undecided; FX1 settles it |
| Frequency shifter | 🆕 | FX5 | |

### Stereo & space

| Effect | Status | Phase | Notes |
|---|---|---|---|
| Pan per event | ✅ | — | `pan`, `jux` |
| Convolution reverb | 🟡 | FX4 | `(fx :reverb …)`: one generated impulse response, no size/decay/damping control |
| Plate reverb | ✅ | — | `(fx :dattorro-reverb …)` with decay, damping, pre-delay |
| Reverb send per event | 📋 | TL7b | `room`, `size`, `orbit` |
| Room / hall / spring / gated / reverse reverb | 🆕 | FX4 | |
| Shimmer reverb | 🆕 | FX5 | Needs the pitch shifter |
| Auto-pan | 🆕 | FX1 | |
| Stereo width (mid/side), Haas widener | 🆕 | FX1 | |
| Multichannel panning | 💤 | — | Parity doc: ⛔ (SuperDirt multichannel) |
| Effect routing (reorder, parallel, sends) | ⏸ | P2 | |

### Delay

| Effect | Status | Phase | Notes |
|---|---|---|---|
| Feedback delay | 🟡 | FX4 | `(fx :delay …)`: time in seconds only, mono feedback path |
| Delay send per event | 📋 | TL7b | `delay`, `delay-time`, `delay-feedback` |
| Tempo-synced delay time | 🟡 | TL7c, FX4 | `delay-sync` per event (TL7c); `:sync` on the track effect (FX4) |
| Ping-pong delay | 🆕 | FX4 | |
| Multi-tap delay | 🆕 | FX4 | |
| Tape delay (filtered, saturating, wobbling repeats) | 🆕 | FX4 | |

### Pitch, time & spectral

| Effect | Status | Phase | Notes |
|---|---|---|---|
| Sample speed / reverse / slicing | 🟡 | — | TL6 functions; samples only, pitch follows speed |
| Pitch shifter (pitch without speed) | 🆕 | FX5 | |
| Octaver / harmonizer | 🆕 | FX5 | |
| Freeze / sustain | 🆕 | FX5 | |
| Granular processor | 🆕 | FX5 | FUTURE-FEATURES backlog row |
| Stutter / beat repeat on the audio | 🆕 | FX5 | Pattern-level `stutter`, `ply`, `chop` exist |
| Reverse audio in tempo | 🆕 | FX5 | |
| Vocoder | 🆕 | FX5 | Needs a modulator input (audio bus) |
| Time-stretch without pitch change | 💤 | — | TL6 decision: pitch follows speed |
| FFT spectral effects (bin shift, smear, brick-wall) | 💤 | — | Parity doc: ⛔ (SuperDirt-only) |

---

## Findings outside the gap list

Things the inventory turned up in existing code and docs. They are scheduled in SYN6:

- USAGE.md documents `(synth :trumpet-cup)`, but neither `fm_preset` in `lib.rs` nor the
  dispatch set in `audio.cljs` knows it. It falls through to a plain sine.
- `:synth-brass`, `:harmon-out` and `:harmon-in` work but are missing from the USAGE.md
  voice table.
- Once the Dirt-Samples manifest has loaded, `:bd :sd :hh :oh` play samples. The
  synthesized drums are then unreachable; SYN7 adds an explicit way to select them.
