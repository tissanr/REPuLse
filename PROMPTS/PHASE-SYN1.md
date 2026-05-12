# Phase SYN1 — Karplus-Strong Plucked String

## Goal

Add a Karplus-Strong delay-line voice to the WASM synthesis engine — the algorithm
invented by Kevin Karplus and Alex Strong (1983) that produces convincing plucked-string
sounds from a noise-filled delay line fed back through a single low-pass filter. Six
named presets — `:guitar`, `:harp`, `:koto`, `:pizz`, `:lute`, `:mandolin` — are
registered as first-class `(synth :X)` voices, giving REPuLse its first melodic
instrument tier beyond raw waveforms and drums.

```lisp
;; Before — only raw oscillators for melodic content:
(->> (scale :minor :c3 (seq 0 3 5 7))
     (synth :saw)
     (amp 0.5) (decay 0.6))

;; After — acoustic plucked-string character on the same melody:
(->> (scale :minor :c3 (seq 0 3 5 7))
     (synth :guitar)
     (amp 0.8))

;; Harp arpeggio using existing chord combinator:
(->> (chord :major :c4 (slow 2 (seq 0 2 4 7 4 2)))
     (synth :harp))

;; Pizzicato cello bass:
(->> (scale :minor :c2 (seq 0 :_ 5 :_))
     (synth :pizz)
     (amp 0.6))

;; Fast Japanese koto figure:
(->> (scale :pentatonic :d4 (fast 4 (seq 0 2 4 7)))
     (synth :koto))
```

---

## Background

### WASM voice architecture

`packages/audio/src/lib.rs` — the `Voice` enum (line ~92) holds all DSP state. Current
variants: `Kick`, `Snare`, `Hihat`, `Tone`, `Saw`, `Square`, `Noise`, `FM`. Each
implements `fn tick(&mut self, sr: f32) -> f32` (one sample) and `fn is_silent(&self)
-> bool`. New variant: `Voice::KarplusStrong`.

`AudioEngine::trigger_v2` (line ~640) parses the event `value` string and constructs
the appropriate `Voice`. The `Voice::KarplusStrong` branch fires when the value starts
with `"ks:"` (e.g. `"ks:guitar:440.0:0.8"`).

**Memory decision (recorded in CLAUDE.md):** Every `Voice::KarplusStrong` holds a
fixed-capacity `Vec<f32>` pre-allocated to 2205 samples (the maximum needed for ~20 Hz
at 44100 Hz sample rate). High-pitched notes use only the first N slots of this buffer.
This keeps the AudioWorklet render callback allocation-free.

### CLJS synth dispatch

`app/src/repulse/synth.cljs` — `make-build-fn` dispatches on the `:synth` keyword in
an event map. Existing entries: `:saw`, `:square`, `:noise`, `:fm`. New entries for
`:guitar :harp :koto :pizz :lute :mandolin` produce a trigger value string of the form
`"ks:{preset}:{freq}:{amp}"` that `trigger_v2` on the WASM side recognises.

`app/src/repulse/audio.cljs` — `play-event` at line ~368 dispatches events to either
`play-defsynth-or-builtin!` or the WASM worklet `trigger` message. Karplus voices route
through the worklet path, same as existing oscillator voices.

### Existing per-event parameters

`decay` and `amp` are already threaded through the event map via `->>` (Phase H).
`decay` maps to the `Voice`'s gain envelope; for Karplus, shorter `decay` reduces the
feedback coefficient slightly below its preset default, causing the string to die faster.

---

## Implementation

### 1. `packages/audio/src/lib.rs` — new `Voice::KarplusStrong`

Add to the `Voice` enum:

```rust
KarplusStrong {
    buf: Vec<f32>,
    buf_len: usize,        // floor(sr / freq) — active length within buf
    write_pos: usize,
    lp_prev: f32,          // previous output for one-pole LP averaging
    feedback: f32,         // preset feedback coefficient (< 1.0)
    brightness: f32,       // LP weight: y = brightness*x + (1-brightness)*lp_prev
    gain: f32,             // output gain envelope (set to amp, fades to 0)
    vib_phase: f32,        // vibrato LFO phase (radians)
    vib_depth: f32,        // vibrato modulation depth (±samples)
    vib_rate: f32,         // vibrato rate (Hz)
},
```

Preset table as a Rust `fn ks_preset(name: &str) -> (f32, f32, f32, f32, f32)` returning
`(feedback, brightness, pick_pos, vib_depth, vib_rate)`:

```rust
fn ks_preset(name: &str) -> (f32, f32, f32, f32, f32) {
    match name {
        "harp"     => (0.995, 0.40, 0.25, 0.0,  0.0),
        "koto"     => (0.988, 0.60, 0.10, 0.01, 5.5),
        "pizz"     => (0.972, 0.48, 0.20, 0.0,  0.0),
        "lute"     => (0.985, 0.45, 0.18, 0.0,  0.0),
        "mandolin" => (0.982, 0.62, 0.08, 0.02, 6.0),
        _          => (0.990, 0.50, 0.14, 0.0,  0.0), // "guitar" default
    }
}
```

Construction in `trigger_v2` — parse `"ks:{preset}:{freq}:{amp}"`:

```rust
if let Some(rest) = value.strip_prefix("ks:") {
    let parts: Vec<&str> = rest.splitn(3, ':').collect();
    let preset   = parts.get(0).copied().unwrap_or("guitar");
    let freq: f32 = parts.get(1).and_then(|s| s.parse().ok()).unwrap_or(440.0);
    let amp: f32  = parts.get(2).and_then(|s| s.parse().ok()).unwrap_or(1.0);
    let (feedback, brightness, pick_pos, vib_depth, vib_rate) = ks_preset(preset);

    let buf_len = (sample_rate / freq).floor() as usize;
    let buf_len = buf_len.max(2).min(2205); // clamp to pre-allocated max
    let mut buf = vec![0.0f32; 2205];       // pre-allocate full capacity

    // Fill active portion with noise burst
    let mut lcg = self.lcg_state;
    for i in 0..buf_len {
        buf[i] = lcg_next(&mut lcg);
    }
    self.lcg_state = lcg;

    // Apply pick-position comb notch: zero every buf[i * floor(buf_len * pick_pos)]
    let comb = (buf_len as f32 * pick_pos).floor() as usize;
    if comb > 0 {
        for i in (0..buf_len).step_by(comb) {
            buf[i] = 0.0;
        }
    }

    self.voices.push(ActiveVoice {
        voice: Voice::KarplusStrong {
            buf, buf_len, write_pos: 0, lp_prev: 0.0,
            feedback: feedback * amp.clamp(0.1, 1.0), // amp scales feedback slightly
            brightness, gain: amp,
            vib_phase: 0.0, vib_depth, vib_rate,
        },
        pan: 0.0,
    });
}
```

`tick` implementation:

```rust
Voice::KarplusStrong {
    buf, buf_len, write_pos, lp_prev, feedback, brightness, gain,
    vib_phase, vib_depth, vib_rate,
} => {
    // Vibrato: modulate read position
    let vib_offset = (*vib_depth * (*vib_phase).sin()) as isize;
    let read_pos = (*write_pos + 1 + *buf_len as usize) % *buf_len;
    let vib_pos = ((read_pos as isize + vib_offset)
        .rem_euclid(*buf_len as isize)) as usize;

    let x = buf[vib_pos];
    // One-pole LP: y = brightness*x + (1-brightness)*prev
    let y = *brightness * x + (1.0 - *brightness) * *lp_prev;
    let y_fb = y * *feedback;
    buf[*write_pos] = y_fb;
    *write_pos = (*write_pos + 1) % *buf_len;
    *lp_prev = y;
    *vib_phase += 2.0 * std::f32::consts::PI * *vib_rate / sr;

    // Gain decays with feedback; signal naturally fades
    *gain = y_fb.abs().max(*gain * 0.9999); // track envelope loosely
    y_fb
}
```

`is_silent` for `KarplusStrong`: `*gain < 1e-4`.

### 2. `app/src/repulse/synth.cljs` — builtin dispatch

Add to the builtin-voice-map (or equivalent dispatch table that converts `{:synth :X}` to a trigger string):

```clojure
:guitar   (fn [{:keys [freq amp]}] (str "ks:guitar:"   freq ":" (or amp 1.0)))
:harp     (fn [{:keys [freq amp]}] (str "ks:harp:"     freq ":" (or amp 1.0)))
:koto     (fn [{:keys [freq amp]}] (str "ks:koto:"     freq ":" (or amp 1.0)))
:pizz     (fn [{:keys [freq amp]}] (str "ks:pizz:"     freq ":" (or amp 1.0)))
:lute     (fn [{:keys [freq amp]}] (str "ks:lute:"     freq ":" (or amp 1.0)))
:mandolin (fn [{:keys [freq amp]}] (str "ks:mandolin:" freq ":" (or amp 1.0)))
```

### 3. Grammar, completions, metadata

Add all six names to `BuiltinName` in `repulse-lisp.grammar`, then run:

```bash
npm run gen:grammar
```

Add `{ label: "guitar", type: "keyword", detail: "plucked acoustic guitar (Karplus-Strong)" }` etc. to `completions.js`.

Add entries to `builtin_meta.edn`:

```edn
{:name "guitar"
 :category "synth"
 :returns "event-param-map"
 :side-effects []
 :examples ["(->> (scale :minor :c3 (seq 0 3 5 7)) (synth :guitar))"
            "(->> (pure :e2) (synth :guitar) (decay 0.4))"]
 :see-also ["harp" "pizz" "lute" "koto" "mandolin" "synth"]}
```

Then run:

```bash
npm run gen:ai-docs
```

---

## Files to change

| File | Change |
|---|---|
| `packages/audio/src/lib.rs` | Add `Voice::KarplusStrong` variant, `ks_preset()`, trigger parsing, `tick`, `is_silent` |
| `app/src/repulse/synth.cljs` | Add six preset entries to builtin-voice-map |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add six names to `BuiltinName` |
| `app/src/repulse/lisp-lang/completions.js` | Add six completion entries |
| `app/src/repulse/content/builtin_meta.edn` | Add metadata for each preset |
| `npm run gen:grammar` | Run after grammar edit |
| `npm run build:wasm` | Run after any `lib.rs` change |
| `npm run gen:ai-docs` | Run after `builtin_meta.edn` edit |

---

## Definition of done

- [ ] `(->> (pure :c4) (synth :guitar))` produces an audible plucked string tone that decays naturally over ~1.5 s
- [ ] `(->> (pure :c4) (synth :harp))` is brighter and decays longer than `:guitar`; `(synth :pizz)` on the same pitch decays in under 0.5 s
- [ ] `(->> (scale :minor :c3 (seq 0 3 5 7)) (synth :guitar))` produces four pitches in the correct minor scale intervals
- [ ] `(->> (pure :e3) (synth :guitar) (decay 0.3))` decays audibly faster than `(->> (pure :e3) (synth :guitar))` without `(decay …)`
- [ ] `(->> (chord :major :c4 (seq 0 2 4 7)) (synth :harp))` produces four distinct pitches, each correct
- [ ] `(->> (fast 4 (scale :pentatonic :d4 (seq 0 2 4 7))) (synth :koto))` produces a fast pentatonic figure with the koto timbre
- [ ] All six presets produce perceptibly different timbres when the same pitch is played through each
- [ ] `npm run build:wasm` succeeds with no warnings
- [ ] `npm run test:rust` passes all existing and any new Rust unit tests
- [ ] `npm run test` passes; `npx shadow-cljs compile app` completes clean
- [ ] All six names appear in editor autocomplete with correct `detail` strings
- [ ] All six names have hover docs (category, description, examples) in the editor
- [ ] `npm run gen:ai-docs` was run; `docs/ai/builtins.json` includes entries for all six names

---

## What NOT to do

- Do not implement bowed string synthesis here — that is SYN3 (full bi-directional waveguide).
- Do not add new per-event parameters beyond the already-supported `amp` and `decay` — SYN4 introduces `pick-pos` for electric guitar variants.
- Do not implement stereo detuning or chorus on the Karplus voice — that belongs in the FX chain via existing `(fx :chorus …)`.
- Do not add FM instrument presets here — that is SYN2.
- Do not implement piano hammer models here — that is SYN5.
