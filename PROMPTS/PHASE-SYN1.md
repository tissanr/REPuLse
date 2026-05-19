# Phase SYN1 — Karplus-Strong Plucked String

## Status: ✅ delivered

---

## What was built

Six plucked-string instruments — `:guitar`, `:harp`, `:koto`, `:pizz`, `:lute`,
`:mandolin` — backed by a Karplus-Strong delay-line voice in the WASM engine. Each
preset is a first-class `(synth :X)` voice with per-instrument body resonance EQ,
giving REPuLse its first melodic instrument tier beyond raw oscillators and drums.

```lisp
;; Acoustic guitar melody
(->> (scale :minor :c3 (seq 0 3 5 7))
     (synth :guitar)
     (amp 0.8))

;; Harp arpeggio
(->> (chord :major :c4 (slow 2 (seq 0 2 4 7 4 2)))
     (synth :harp))

;; Pizzicato violin bass — short, soft finger pluck
(->> (scale :minor :c2 (seq 0 :_ 5 :_))
     (synth :pizz)
     (amp 0.6))

;; Fast koto figure with silk-string darkness
(->> (scale :pentatonic :d4 (fast 4 (seq 0 2 4 7)))
     (synth :koto))
```

---

## Architecture

### `packages/audio/src/lib.rs`

#### `Biquad` — two new constructors added

```rust
// Audio EQ Cookbook peaking EQ
fn peaking_eq(freq: f32, gain_db: f32, q: f32, sr: f32) -> Self

// Audio EQ Cookbook high shelf (shelf slope S = 0.9)
fn highshelf(freq: f32, gain_db: f32, sr: f32) -> Self
```

These are shared with SYN3 (bowed strings), which uses the same `Biquad` primitives for
its body filter chain. That is the MECE boundary: both voice families share filter
infrastructure; they differ only in excitation model and waveguide structure.

#### `ks_preset(name) -> (f32, f32, f32, f32, f32, f32)`

Returns `(feedback, brightness, pick_pos, vib_depth, vib_rate, excitation)`.

| preset          | feedback | brightness | pick_pos | vib_depth | vib_rate | excitation | T60 @A440 |
|-----------------|----------|------------|----------|-----------|----------|------------|-----------|
| western / guitar| 0.997    | 0.60       | 0.12     | 0.0       | 0.0      | 1.00       | ~2.3 s    |
| nylon           | 0.996    | 0.50       | 0.16     | 0.0       | 0.0      | 0.60       | ~2.0 s    |
| harp            | 0.998    | 0.55       | 0.25     | 0.0       | 0.0      | 1.00       | ~3.4 s    |
| koto            | 0.994    | 0.62       | 0.10     | 0.015     | 5.5      | 1.00       | ~1.5 s    |
| pizz            | 0.975    | 0.40       | 0.42     | 0.0       | 0.0      | 0.18       | ~0.27 s   |
| lute            | 0.996    | 0.58       | 0.16     | 0.0       | 0.0      | 1.00       | ~1.7 s    |
| mandolin        | 0.992    | 0.68       | 0.08     | 0.025     | 6.5      | 1.00       | ~0.85 s   |

**`excitation`** scales the initial noise fill amplitude (`fill = amp * 0.5 * excitation`).
`:pizz` uses `0.18` because a violin fingertip pluck is much softer than a guitar pick
stroke — the pluck transient is present but gentle. All other presets use `1.0`.

T60 formula: `-3 * buf_len / (sr * ln(feedback))` evaluated at A440 (~100 samples).

#### `ks_body_filters(name, sr) -> Vec<Biquad>`

Per-instrument body resonance EQ chain, allocated at trigger time and applied every
sample after the KS feedback loop. Models acoustic cavity and top-plate resonances that
distinguish the instruments throughout their sustain — not just at the attack transient
shaped by `pick_pos`.

| preset          | filters |
|-----------------|---------|
| western / guitar| peak(90 Hz, +5 dB, Q=2.5) · peak(200 Hz, +2 dB, Q=1.5) · highshelf(4500 Hz, -4 dB) |
| nylon           | peak(110 Hz, +4 dB, Q=2.0) · peak(230 Hz, +2 dB, Q=1.5) · highshelf(3500 Hz, -5 dB) |
| harp            | peak(110 Hz, +4 dB, Q=1.5) · peak(800 Hz, +1 dB, Q=1.0) · highshelf(5000 Hz, -3 dB) |
| koto     | peak(220 Hz, +6 dB, Q=2.5) · highshelf(3000 Hz, -5 dB) |
| pizz     | peak(270 Hz, +4 dB, Q=3.5) · peak(520 Hz, +3 dB, Q=3.0) · highshelf(4000 Hz, +2 dB) |
| lute     | peak(120 Hz, +4 dB, Q=2.0) · highshelf(2500 Hz, -5 dB) |
| mandolin | peak(300 Hz, +4 dB, Q=2.0) · peak(2000 Hz, +3 dB, Q=1.5) |

#### `Voice::KarplusStrong`

```rust
KarplusStrong {
    buf: Vec<f32>,           // pre-allocated 2205 samples (max for ~20 Hz @ 44100)
    buf_len: usize,          // active length = floor(sr / freq), clamped 2..=2205
    write_pos: usize,
    lp_prev: f32,            // one-pole LP state: y = brightness*x + (1-brightness)*prev
    feedback: f32,           // preset feedback coefficient
    brightness: f32,         // LP weight — higher = more HF survives each cycle
    gain: f32,               // envelope tracker for silence detection (not a volume scaler)
    vib_phase: f32,          // vibrato LFO phase (radians)
    vib_depth: f32,          // vibrato modulation depth (±samples)
    vib_rate: f32,           // vibrato rate (Hz)
    body_filters: Vec<Biquad>, // per-instrument body EQ chain
},
```

**Important:** `gain` is an envelope follower used only by `is_silent` — it tracks
`out.abs().max(gain * 0.9999)`. It does not scale the output signal. Actual loudness is
entirely determined by the initial noise fill amplitude (`fill = amp * 0.5 * excitation`).

#### Construction (in `trigger_raw_v2`, value prefix `"ks:"`)

```
value format: "ks:{preset}:{freq_hz}"
amplitude, attack, decay, pan come from the p: Params struct as with all voices
```

```rust
let (feedback, brightness, pick_pos, vib_depth, vib_rate, excitation) = ks_preset(preset);
let buf_len = ((sr / freq).floor() as usize).clamp(2, 2205);
let peak = amp * 0.5;
let fill = peak * excitation;

// Fill active slots with bandlimited noise
for slot in buf.iter_mut().take(buf_len) {
    *slot = lcg_next(&mut self.noise_seed) * fill;
}

// Pick-position comb: zero every comb-th slot to cancel that harmonic series
let comb = (buf_len as f32 * pick_pos).floor() as usize;
if comb > 0 {
    for i in (0..buf_len).step_by(comb) { buf[i] = 0.0; }
}
```

#### `tick` implementation

```rust
// 1. Vibrato: modulate read position by ±vib_depth samples
let vib_offset = (vib_depth * vib_phase.sin()) as isize;
let read_pos   = (write_pos + 1 + buf_len) % buf_len;
let vib_pos    = ((read_pos as isize + vib_offset).rem_euclid(buf_len as isize)) as usize;

// 2. One-pole LP (string loss filter)
let x    = buf[vib_pos];
let y    = brightness * x + (1.0 - brightness) * lp_prev;
let y_fb = y * feedback;

// 3. Write back and advance
buf[write_pos] = y_fb;
write_pos = (write_pos + 1) % buf_len;
lp_prev   = y;
vib_phase += 2π * vib_rate / sr;

// 4. Body resonance EQ
let out = body_filters.iter_mut().fold(y_fb, |s, f| f.tick(s));

// 5. Update envelope tracker
gain = out.abs().max(gain * 0.9999);
out
```

`is_silent`: `gain < 1e-4`.

---

### `app/src/repulse/audio.cljs` — CLJS dispatch

KS branch in `play-event`, after the FM branch:

```clojure
(#{:western :nylon :guitar :harp :koto :pizz :lute :mandolin} synth)
(let [hz     (if (theory/note-keyword? note) (theory/note->hz note) (double note))
      preset (clojure.core/name synth)]
  (or (when-not offline?
        (worklet-trigger-v2! (str "ks:" preset ":" hz)
                              t amp-v attack-v decay-v pan-v dest))
      (make-sine ac t hz decay-v amp-v attack-v pan-v dest)))
```

Offline fallback is sine (KS needs the WASM AudioWorklet — no pure-JS equivalent).

---

### Grammar, completions, metadata

All six names registered in `repulse-lisp.grammar` under `BuiltinName`, completion
entries in `completions.js`, metadata in `builtin_meta.edn`, and `docs/ai/builtins.json`
regenerated with `npm run gen:ai-docs`.

---

## Files changed

| File | Change |
|---|---|
| `packages/audio/src/lib.rs` | `Biquad::peaking_eq`, `Biquad::highshelf`; `ks_preset` (6-tuple); `ks_body_filters`; `Voice::KarplusStrong` with `body_filters`; `tick` and `is_silent`; trigger parsing |
| `app/src/repulse/audio.cljs` | KS branch in `play-event` after FM dispatch |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Six names added to `BuiltinName` |
| `app/src/repulse/lisp-lang/completions.js` | Six completion entries |
| `app/src/repulse/content/builtin_meta.edn` | Metadata for all six presets |
| `docs/ai/builtins.json` | Regenerated (`npm run gen:ai-docs`) |
| `CLAUDE.md` | Architectural decision note: body filter chain + MECE with SYN3 |

---

## What NOT to do in future phases

- **SYN3** implements bowed strings — full bi-directional waveguide, not KS. It reuses
  `Biquad::peaking_eq` / `Biquad::highshelf` for its own `body_filters: Vec<Biquad>`.
  Do not merge the two voice types.
- **SYN4** adds electric guitar presets (`:strat`, `:tele`, etc.) built on top of the KS
  engine with per-event `pick-pos` and `tone` parameters. Do not add those here.
- Do not add new per-event parameters beyond `amp`, `decay`, `attack`, `pan` in SYN1.
  The `excitation` multiplier is baked into the preset table, not user-facing.
- Do not implement stereo detuning or chorus on the KS voice — route through `(fx :chorus …)`.
