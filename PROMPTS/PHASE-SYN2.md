# Phase SYN2 — FM Instrument Presets

## Goal

Expose the existing WASM FM voice as a library of named instrument presets with
carefully tuned carrier/modulator ratios, modulation indices, and ADSR envelopes — so
that `(synth :sax)`, `(synth :trumpet)`, `(synth :epiano)`, `(synth :bell)`, and five
other voices work out of the box without the user needing to know FM synthesis theory.
The FM algorithm already runs in `packages/audio/src/lib.rs`; this phase adds a
hard-coded preset table and extends the `Voice::FM` struct with a full ADSR envelope.
Lo-fi character is a feature, not a bug: trumpet variants with high modulation index
produce deliberately gritty brass textures.

```lisp
;; Before — FM requires manual index/ratio tuning:
(->> (scale :minor :c3 (seq 0 3 5 7))
     (synth :fm :index 3.5 :ratio 1.0)
     (amp 0.6) (decay 0.4))

;; After — named presets with musically tuned parameters:
(->> (scale :minor :c3 (seq 0 3 5 7))
     (synth :sax)
     (amp 0.7))

;; Lo-fi muted trumpet — classic lofi brass texture:
(->> (scale :major :c4 (seq 0 2 4 7))
     (synth :trumpet-muted)
     (amp 0.6))

;; Electric piano Rhodes-style:
(->> (chord :maj7 :c4 (slow 2 (seq 0 4 7 11)))
     (synth :epiano)
     (amp 0.5))

;; Bell melody:
(->> (fast 2 (scale :major :c5 (seq 0 2 4 5)))
     (synth :bell))
```

---

## Background

### Existing FM voice in WASM

`packages/audio/src/lib.rs` — `Voice::FM` (line ~92):

```rust
FM {
    carrier_phase: f64,   // carrier oscillator phase
    mod_phase:     f64,   // modulator oscillator phase
    carrier_freq:  f64,   // carrier frequency in Hz
    mod_freq:      f64,   // modulator frequency = carrier_freq × ratio (pre-computed at trigger)
    index:         f32,   // modulation index
    amp:           f32,   // peak amplitude (from amp param)
    gain:          f32,   // current envelope amplitude
    gain_decay:    f32,   // per-sample decay multiplier (from decay param)
    attack_inc:    f32,   // per-sample gain increment during attack
    in_attack:     bool,  // whether still in attack phase
}
```

`tick` computes one sample of 2-operator FM:
`out = gain × sin(carrier_phase + index × sin(mod_phase))`

The existing struct already has attack (`attack_inc`/`in_attack`) and decay
(`gain_decay`). What is missing for expressive presets is **sustain level** and a
**per-preset release time** — i.e., an independent hold-to-sustain-to-release shape
per voice rather than driving decay purely through the shared `decay` event parameter.

### Trigger dispatch

`AudioEngine::trigger_v2` parses `"fm:{preset}:{freq}:{amp}"` for the new path, and
`"fm:{freq}:{index}:{ratio}"` for the legacy manual-tuning path (preserve backward
compatibility with any `(synth :fm :index … :ratio …)` expressions users already have).

### CLJS synth dispatch

`app/src/repulse/synth.cljs` — builtin-voice-map entries for `:fm` already produce the
legacy format. New entries for each named preset produce
`"fm:{preset}:{freq}:{amp}"` strings.

---

## Implementation

### 1. Extend `Voice::FM` with ADSR

```rust
FM {
    phase:     f32,
    mod_phase: f32,
    freq:      f32,
    index:     f32,
    ratio:     f32,
    // ADSR envelope
    attack:    f32,   // seconds
    decay:     f32,   // seconds
    sustain:   f32,   // 0.0–1.0 gain level
    release:   f32,   // seconds (starts when note ends — approximated here as decay tail)
    env_phase: f32,   // current position in seconds since trigger
    gain:      f32,   // current envelope amplitude
    peak_gain: f32,   // initial amplitude (from amp param)
},
```

ADSR tick (called each sample, `dt = 1/sr`):

```rust
// Attack
if *env_phase < *attack {
    *gain = *peak_gain * (*env_phase / *attack);
}
// Decay to sustain
else if *env_phase < *attack + *decay {
    let t = (*env_phase - *attack) / *decay;
    *gain = *peak_gain * (1.0 - t * (1.0 - *sustain));
}
// Sustain hold (until natural decay via low sustain)
else {
    *gain = *peak_gain * *sustain * (1.0 - (*env_phase - *attack - *decay) / (*release + 0.01)).clamp(0.0, 1.0);
}
*env_phase += 1.0 / sr;
```

`is_silent`: `*gain < 1e-4`.

### 2. Preset table

Hard-coded `fn fm_preset(name: &str) -> (f32, f32, f32, f32, f32, f32)` returning
`(index, ratio, attack, decay, sustain, release)`:

```rust
fn fm_preset(name: &str) -> (f32, f32, f32, f32, f32, f32) {
    // (index, ratio, attack, decay, sustain, release)
    match name {
        "sax"            => (3.0, 1.0,   0.02, 0.1,  0.7, 0.15),
        "trumpet"        => (4.5, 1.0,   0.01, 0.05, 0.8, 0.10),
        "trumpet-muted"  => (7.0, 1.414, 0.01, 0.04, 0.6, 0.08),
        "trumpet-cup"    => (5.5, 1.0,   0.02, 0.06, 0.7, 0.10),
        "trombone"       => (3.5, 1.0,   0.04, 0.08, 0.75,0.20),
        "epiano"         => (1.5, 14.0,  0.005,0.3,  0.0, 0.40), // Rhodes-style
        "bell"           => (5.0, 1.414, 0.001,0.05, 0.0, 1.20),
        "marimba"        => (2.0, 3.5,   0.001,0.02, 0.0, 0.35),
        "flute"          => (1.8, 1.0,   0.06, 0.05, 0.85,0.10),
        _                => (3.0, 1.0,   0.01, 0.1,  0.7, 0.15),
    }
}
```

*Tuning notes:*
- `:epiano` uses a high modulator ratio (14.0) for the characteristic metallic overtone
  shimmer of a Rhodes; low sustain means it fades naturally after the attack transient.
- `:bell` decays to zero (sustain 0.0) with a long release — it rings, not sustains.
- `:trumpet-muted` uses `ratio 1.414` (√2) for inharmonic sidebands that give the
  muted nasal quality; high index creates lo-fi grit.

### 3. `trigger_v2` parsing

```rust
// New preset path: "fm:{preset}:{freq}:{amp}"
if value.starts_with("fm:") && !value[3..].starts_with(|c: char| c.is_ascii_digit()) {
    let parts: Vec<&str> = value[3..].splitn(3, ':').collect();
    let preset   = parts[0];
    let freq: f32 = parts.get(1).and_then(|s| s.parse().ok()).unwrap_or(440.0);
    let amp: f32  = parts.get(2).and_then(|s| s.parse().ok()).unwrap_or(1.0);
    let (index, ratio, attack, decay, sustain, release) = fm_preset(preset);
    // construct Voice::FM with ADSR fields ...
}
// Legacy manual path: "fm:{freq}:{index}:{ratio}" — unchanged
```

### 4. CLJS dispatch entries in `app/src/repulse/audio.cljs`

`app/src/repulse/synth.cljs` manages only user-defined `defsynth` voices. Built-in
synth keyword dispatch lives in `play-event` in `app/src/repulse/audio.cljs`, where
the existing `:fm` branch lives (~line 415).

Add a new branch in the `(map? value) (:note value)` cond for all nine FM presets:

```clojure
;; After the existing (= synth :fm) manual branch:
(#{:sax :trumpet :trumpet-muted :trumpet-cup :trombone
   :epiano :bell :marimba :flute} synth)
(let [hz     (if (theory/note-keyword? note) (theory/note->hz note) (double note))
      preset (name synth)]
  (or (when-not offline?
        (worklet-trigger-v2! (str "fm:" preset ":" hz)
                              t amp-v attack-v decay-v pan-v dest))
      (make-sine ac t hz decay-v amp-v attack-v pan-v dest)))
```

The existing `:fm` manual dispatch (`"fm:{freq}:{index}:{ratio}"`) is unchanged.
The new preset path (`"fm:{preset}:{freq}"`) is distinguished in `trigger_v2` by
the character after `"fm:"` — a letter indicates a preset name, a digit indicates
the legacy manual format.

### 5. Grammar, completions, metadata

Add all nine names to `BuiltinName` in `repulse-lisp.grammar`, run `npm run gen:grammar`.
Add completion entries and `builtin_meta.edn` entries for each, then `npm run gen:ai-docs`.

---

## Files to change

| File | Change |
|---|---|
| `packages/audio/src/lib.rs` | Extend `Voice::FM` with sustain/release fields; add `fm_preset()` table; update `trigger_v2` to handle preset path; update `tick` with full ADSR envelope |
| `app/src/repulse/audio.cljs` | Add nine-preset `#{:sax :trumpet …}` branch to `play-event` cond |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add nine names to `BuiltinName` |
| `app/src/repulse/lisp-lang/completions.js` | Add nine completion entries |
| `app/src/repulse/content/builtin_meta.edn` | Add metadata for each preset |
| `npm run gen:grammar` | After grammar edit |
| `npm run build:wasm` | After any `lib.rs` change |
| `npm run gen:ai-docs` | After `builtin_meta.edn` edit |

---

## Definition of done

- [ ] `(->> (scale :minor :c3 (seq 0 3 5 7)) (synth :sax))` produces a reed-like tone with a soft attack and sustained body
- [ ] `(->> (scale :major :c4 (seq 0 2 4)) (synth :trumpet))` produces a bright brass tone; `(synth :trumpet-muted)` on the same melody sounds distinctly grittier/narrower
- [ ] `(->> (chord :maj7 :c4 (slow 2 (seq 0 4 7 11))) (synth :epiano))` produces a metallic electric piano timbre that fades after the initial transient
- [ ] `(->> (fast 2 (scale :major :c5 (seq 0 2 4 5))) (synth :bell))` rings with a slow decay of ≥ 1 s
- [ ] `(->> (pure :c3) (synth :trombone))` has a slower attack and warmer character than `(synth :trumpet)` at the same pitch
- [ ] Legacy `(->> (pure :c4) (synth :fm :index 3.5 :ratio 1.0))` still works unchanged after the Voice::FM struct extension
- [ ] All nine presets produce perceptibly different timbres when the same pitch is played
- [ ] `npm run build:wasm` succeeds; `npm run test:rust` passes
- [ ] `npm run test` passes; `npx shadow-cljs compile app` clean
- [ ] All nine names appear in editor autocomplete with correct detail strings
- [ ] `npm run gen:ai-docs` was run; `docs/ai/builtins.json` includes all nine names

---

## What NOT to do

- Do not implement 4-operator FM (DX7-style) here — 2-operator is sufficient for lo-fi presets and keeps the Rust code simple.
- Do not add per-event `index` or `ratio` override for preset voices — if the user wants to tweak FM parameters manually, they use the existing `(synth :fm :index … :ratio …)` form.
- Do not implement wavetable or additive synthesis here — those are separate future phases.
- Do not implement bowed strings or plucked strings here — SYN1 and SYN3.
- Do not break backward compatibility with the existing manual FM path `"fm:{freq}:{index}:{ratio}"`.
