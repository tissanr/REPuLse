# Phase SYN5 — Lo-Fi Piano

## Goal

Add a lo-fi piano voice — `(synth :piano)` — using an extended Karplus-Strong model
with a bi-linear decay filter that approximates the hammer-struck string character of
a piano: a bright transient that quickly loses high-frequency content as the string
decays, leaving a warm fundamental. The model makes no attempt at physical accuracy
(no inharmonicity, no multi-string beating, no soundboard resonance); the goal is a
characterful, usable lo-fi piano texture suitable for REPuLse's live-coding context.
A `(synth :piano-felt)` variant adds heavy felt damping for a muted prepared-piano
character. Better piano models (with inharmonicity and multi-string beating) are
intentionally deferred to `docs/FUTURE-FEATURES.md`.

```lisp
;; Simple chord voicing:
(->> (chord :maj7 :c4 (slow 2 (seq 0 4 7 11)))
     (synth :piano)
     (amp 0.6))

;; Walking bass line:
(->> (scale :minor :c2 (seq 0 3 5 7 5 3))
     (synth :piano)
     (amp 0.5) (decay 0.8))

;; Felt/muted piano texture:
(->> (fast 2 (scale :pentatonic :d4 (seq 0 2 4 7)))
     (synth :piano-felt)
     (amp 0.7))

;; Lo-fi piano loop with vinyl-style processing:
(->> (chord :min7 :c3 (seq 0 4 7 10))
     (synth :piano)
     (amp 0.5)
     (->> (fx :bitcrusher :bits 10)
          (fx :reverb 0.2)))
```

---

## Background

### Dependency on SYN1

SYN5 uses `Voice::KarplusStrong` from SYN1 as its foundation. The piano model is a
two-stage modification:

1. **Bi-linear decay filter** replaces the simple one-pole LP in SYN1's `tick`.
   The standard Karplus-Strong LP `y = 0.5*(x + prev)` attenuates all frequencies
   equally over time. The bi-linear decay filter uses two separate LP coefficients —
   one for the transient (first ~20 ms: brighter) and one for the sustained tone
   (after 20 ms: darker) — to mimic the hammer-string transient behaviour.

2. **Tuned inharmonicity approximation** via a slight frequency offset per octave:
   higher notes decay faster (the `feedback` coefficient decreases with pitch register)
   to reproduce the natural brightness thinning across the piano keyboard.

### Why lo-fi is the correct scope

Piano is one of the hardest instruments to synthesize convincingly. Accurate models
require: inharmonicity per string (dispersion filtering), two or three strings per note
with slight detuning (beating), a soundboard resonance model (hundreds of partials),
and sympathetic string resonance (una corda). These are all out of scope. The lo-fi
goal — something recognisably piano-like that is musical and characterful — is
achievable with the existing Karplus infrastructure and no new dependencies.

### Felt variant

`:piano-felt` uses a much lower feedback coefficient and a very dark initial
`brightness` (0.3), which kills high harmonics almost immediately and produces the
muffled percussive quality of a felt-damped prepared piano.

---

## Implementation

### 1. Bi-linear decay filter in `Voice::KarplusStrong`

Extend the `KarplusStrong` tick to support a time-varying LP coefficient:

```rust
KarplusStrong {
    // ... existing fields ...
    brightness_peak: f32,   // brightness during first bright_dur seconds
    brightness_low:  f32,   // brightness after transient settles
    bright_dur:      f32,   // seconds for transient (typically 0.015–0.025)
    elapsed:         f32,   // seconds since trigger
}
```

In `tick`:
```rust
let brightness = if *elapsed < *bright_dur {
    // Linear ramp from bright to low over bright_dur
    let t = *elapsed / *bright_dur;
    *brightness_peak * (1.0 - t) + *brightness_low * t
} else {
    *brightness_low
};
*elapsed += 1.0 / sr;
// ... rest of KS tick using local `brightness` ...
```

For acoustic presets (SYN1), set `brightness_peak == brightness_low` and
`bright_dur = 0.0` — no change in existing behaviour.

### 2. Pitch-dependent feedback scaling

Higher pitches decay faster in a real piano. Add a pitch scaling factor computed
at trigger time:

```rust
// Scale feedback: 1.0 at C2 (~65 Hz), 0.98 at C6 (~1046 Hz)
let pitch_scale = 1.0 - (freq / 65.0).log2() * 0.003;
let scaled_feedback = feedback * pitch_scale.clamp(0.96, 1.0);
```

This is applied only for piano presets; `pitch_scale = 1.0` for all others.

### 3. Preset entries

```rust
"piano"      => (0.9940, 0.65, 0.35, 0.018, 0.0,  0.0), // (feedback, brightness_peak, brightness_low, bright_dur, vib_depth, vib_rate)
"piano-felt" => (0.9700, 0.40, 0.28, 0.010, 0.0,  0.0),
```

Piano uses:
- `feedback 0.994` — moderate sustain (not as long as `:lp` guitar)
- `brightness_peak 0.65` — bright transient
- `brightness_low 0.35` — settles to darker sustained tone after 18 ms

Piano-felt uses:
- `feedback 0.970` — short, fast-decaying (felt absorbs energy)
- `brightness_peak / low 0.40 / 0.28` — always dark, minimal transient brightness

### 4. CLJS dispatch entries

```clojure
:piano       (fn [{:keys [freq amp]}] (str "ks:piano:"       freq ":" (or amp 1.0)))
:piano-felt  (fn [{:keys [freq amp]}] (str "ks:piano-felt:"  freq ":" (or amp 1.0)))
```

No new per-event parameters needed; `amp` and `decay` (already in the system) are
sufficient.

### 5. Grammar, completions, metadata

Add `:piano` and `:piano-felt` to `BuiltinName` in the grammar.

`builtin_meta.edn` should reference `:bitcrusher` and `:reverb` in `see-also` — the
most natural lo-fi processing pairings for piano:

```edn
{:name "piano"
 :category "synth"
 :returns "event-param-map"
 :side-effects []
 :examples ["(->> (chord :maj7 :c4 (seq 0 4 7 11)) (synth :piano))"
            "(->> (scale :minor :c2 (seq 0 3 5 7)) (synth :piano) (decay 0.8))"
            "(->> (pure :c4) (synth :piano) (fx :bitcrusher :bits 10))"]
 :see-also ["piano-felt" "epiano" "bitcrusher" "reverb" "decay"]}
```

Run `npm run gen:grammar` and `npm run gen:ai-docs`.

---

## Files to change

| File | Change |
|---|---|
| `packages/audio/src/lib.rs` | Add `brightness_peak`, `brightness_low`, `bright_dur`, `elapsed` to `Voice::KarplusStrong`; update `tick` with bi-linear decay; add pitch-scaling factor; add piano presets to `ks_preset()` |
| `app/src/repulse/synth.cljs` | Add `:piano` and `:piano-felt` to builtin-voice-map |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `piano` and `piano-felt` to `BuiltinName` |
| `app/src/repulse/lisp-lang/completions.js` | Add two completion entries |
| `app/src/repulse/content/builtin_meta.edn` | Add metadata with `see-also` FX references |
| `npm run gen:grammar` | After grammar edit |
| `npm run build:wasm` | After any `lib.rs` change |
| `npm run gen:ai-docs` | After `builtin_meta.edn` edit |

---

## Definition of done

- [ ] `(->> (chord :maj7 :c4 (seq 0 4 7 11)) (synth :piano))` produces a piano-like tone with a bright transient that settles to a warmer sustained fundamental
- [ ] `(->> (scale :minor :c2 (seq 0 3 5 7)) (synth :piano))` produces lower notes with audibly more sustain than the same melody at `(scale :minor :c5 …)`, confirming pitch-dependent decay
- [ ] `(->> (pure :c4) (synth :piano-felt))` has audibly less high-frequency content and shorter sustain than `(synth :piano)` at the same pitch
- [ ] `(->> (pure :c4) (synth :piano) (decay 0.4))` decays faster than without `(decay …)`
- [ ] `(->> (pure :c4) (synth :piano) (fx :bitcrusher :bits 10))` produces a lo-fi piano texture without errors
- [ ] All SYN1 acoustic presets (`:guitar`, `:harp`, etc.) still behave identically after the `brightness_peak/low/bright_dur/elapsed` fields are added to `Voice::KarplusStrong`
- [ ] All SYN4 electric guitar presets still behave identically after the struct extension
- [ ] `npm run build:wasm` succeeds; `npm run test:rust` passes
- [ ] `npm run test` passes; `npx shadow-cljs compile app` clean
- [ ] Both names appear in editor autocomplete with correct detail strings and hover docs
- [ ] `npm run gen:ai-docs` was run; `docs/ai/builtins.json` includes both names

---

## What NOT to do

- Do not implement inharmonicity (dispersion filtering per string) — this requires a
  per-partial all-pass filter chain and is deferred to a future high-fidelity piano phase
  in `docs/FUTURE-FEATURES.md`.
- Do not implement multi-string beating (two or three slightly detuned KS voices per
  note) — this would triple the voice count per note; deferrable.
- Do not implement a sustain pedal parameter — a `pedal` parameter that extends
  feedback is a natural follow-on but out of scope here.
- Do not implement the Rhodes electric piano here — that is `(synth :epiano)` from SYN2
  (FM-based).
- Do not attempt to model soundboard resonance or sympathetic strings.
