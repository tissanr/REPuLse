# Phase SYN4 — Electric Guitar Voices

## Goal

Model five classic electric guitar body/pickup configurations as named presets built on
the SYN1 Karplus-Strong string model, each with characteristic feedback, brightness, and
body-resonance coefficients that reproduce the tonal signature of the instrument. Two
per-event parameters — `pick-pos` and `tone` — are exposed for real-time tonal
shaping. Because the DST distortion phases (DST1–DST6) are already delivered, each
preset pairs naturally with the existing `(fx :amp-sim …)` and `(fx :cab …)` effects
for a complete electric guitar signal chain.

```lisp
;; Clean Stratocaster melody:
(->> (scale :blues :e3 (seq 0 2 3 5))
     (synth :strat)
     (amp 0.7))

;; Les Paul through a Marshall stack:
(->> (scale :minor :a2 (seq 0 :_ 0 3 5 :_ 5 7))
     (synth :lp)
     (amp 0.8)
     (->> (fx :amp-sim :marshall)
          (fx :cab :4x12)))

;; Twangy Telecaster bridge pickup, driven:
(->> (fast 2 (seq :e3 :e3 :b3 :e4))
     (synth :tele)
     (pick-pos 0.95)    ;; extreme bridge — maximum twang
     (fx :overdrive 3.0))

;; ES-335 jazz comping:
(->> (chord :min7 :d3 (slow 2 (seq 0 4 7 10)))
     (synth :es335)
     (tone 0.3)         ;; roll tone knob back — dark jazz sound
     (amp 0.5))

;; SG power chords:
(->> (slow 2 (seq :e2 :a2 :d3))
     (synth :sg)
     (fx :distort 4.0))
```

---

## Background

### Dependency on SYN1

SYN4 is built entirely on `Voice::KarplusStrong` from SYN1. Each electric guitar preset
is a different entry in the `ks_preset()` table with carefully tuned coefficients.
**SYN1 must be delivered before SYN4 is implemented.**

The new presets differ from the acoustic presets in three ways:
1. **Higher feedback** (longer sustain — electric guitars have longer natural decay than
   acoustic due to magnetic pickup not loading the string).
2. **Pick-position range** — single-coil pickups (Strat, Tele) sit near the bridge
   (`pick_pos` 0.1–0.15); humbuckers (ES-335, SG, LP) sit nearer the neck (`pick_pos`
   0.18–0.25), producing a different spectral character.
3. **Body resonance IIR** — each body shape has a distinct resonance peak modelled by a
   2-pole bandpass filter applied at the bridge output (same `Biquad::bandpass` already
   in `lib.rs`).

### Pickup character via pick position

Pick position in Karplus-Strong introduces a comb notch at harmonics that are multiples
of `1 / pick_pos`. A Telecaster bridge pickup at `pick_pos = 0.10` notches every 10th
harmonic, creating the characteristic bright nasal cut. A Les Paul humbucker near the
neck at `pick_pos = 0.22` lets more low harmonics through, producing the full warm tone.

### New per-event parameters

`pick-pos` (0.0–1.0): overrides the preset pick position at play time. Useful for
simulating neck vs. bridge pickup switching live.

`tone` (0.0–1.0): maps to the LP filter `brightness` coefficient in the Karplus voice
(0.0 = fully rolled off, 1.0 = fully open). Simulates the guitar's tone knob.

### DST integration

No changes to `fx.cljs` or the DST plugins are needed. The electric guitar presets
produce a raw string signal that the existing FX chain shapes. The `builtin_meta.edn`
entries should include `see-also` references to `:amp-sim`, `:cab`, `:overdrive`,
`:distort` to surface these pairings in hover docs and AI suggestions.

---

## Implementation

### 1. Extend `ks_preset()` with electric guitar entries

```rust
fn ks_preset(name: &str) -> (f32, f32, f32, f32, f32) {
    // existing acoustic entries ...
    "strat"  => (0.9955, 0.52, 0.10, 0.0,  0.0),
    "tele"   => (0.9950, 0.58, 0.09, 0.0,  0.0),
    "es335"  => (0.9960, 0.44, 0.22, 0.0,  0.0),
    "sg"     => (0.9945, 0.50, 0.18, 0.0,  0.0),
    "lp"     => (0.9965, 0.42, 0.23, 0.0,  0.0),
    // ...
}
```

*Design notes:*
- `:strat` / `:tele` — feedback slightly lower than `:lp` (single-coils load the string
  more); higher brightness; lower pick_pos = more nasal cut.
- `:lp` — highest feedback (humbuckers reduce string damping); darkest brightness
  (0.42); pick_pos 0.23 (near neck position).
- `:es335` — semi-hollow warmth modelled via body resonance IIR (see below); moderate
  brightness.

### 2. Body resonance IIR per electric guitar

Extend `Voice::KarplusStrong` with optional body resonance fields (or add them to the
struct unconditionally with identity coefficients for acoustic presets):

```rust
KarplusStrong {
    // ... existing fields ...
    body_b0: f32,
    body_b1: f32,
    body_b2: f32,
    body_a1: f32,
    body_a2: f32,
    body_x1: f32,
    body_x2: f32,
    body_y1: f32,
    body_y2: f32,
}
```

Preset body resonance frequencies:

| Preset | Res. freq | Q | Character |
|---|---|---|---|
| `:strat` | 500 Hz | 4 | Quack/bell-like upper mid |
| `:tele` | 650 Hz | 5 | Twangy bright peak |
| `:es335` | 350 Hz | 6 | Warm semi-hollow bloom |
| `:sg` | 550 Hz | 4 | Mid-forward aggression |
| `:lp` | 300 Hz | 5 | Full low-mid thickness |

For acoustic presets (SYN1), body resonance is identity (b0=1, b1=b2=a1=a2=0).
Extend `ks_preset()` to return resonance parameters, or add a separate
`ks_body_resonance(name)` function.

### 3. `pick-pos` and `tone` per-event parameters

Per-event parameter transformers live in two files — not in `tracks.cljs` (that file
handles track-level builtins like `track`/`mute!`).

**Step 1:** Add core functions to `packages/core/src/repulse/params.cljs`:

```clojure
(defn pick-pos
  "Pick/pluck contact point; 0.0 = nut, 1.0 = bridge. Lower = warmer.
   (pick-pos 0.95 pat) — extreme bridge, maximum twang
   (pick-pos 0.15)     — return transformer"
  ([v]     (fn [pat] (pick-pos v pat)))
  ([v pat] (apply-param :pick-pos v pat)))

(defn tone
  "Maps to LP filter brightness in the KS voice. 0.0 = dark, 1.0 = bright.
   (tone 0.3 pat) — dark/rolled-off
   (tone 0.9)     — return transformer"
  ([v]     (fn [pat] (tone v pat)))
  ([v pat] (apply-param :tone v pat)))
```

**Step 2:** Expose as Lisp builtins in `packages/lisp/src/repulse/lisp/builtins/params.cljs`:

```clojure
"pick-pos" (fn
             ([v]   (params/pick-pos (u/unwrap v)))
             ([v p] (params/pick-pos (u/unwrap v) (u/unwrap p))))
"tone"     (fn
             ([v]   (params/tone (u/unwrap v)))
             ([v p] (params/tone (u/unwrap v) (u/unwrap p))))
```

**Step 3:** In `play-event` (`app/src/repulse/audio.cljs`), read `:pick-pos` and
`:tone` from the event map and encode in the trigger string:
`"ks:{preset}:{freq}:{amp}:{pick_pos_override}:{tone_override}"`

Use sentinels (`-1.0`) to indicate "use preset default".

### 4. Grammar, completions, metadata

Add `:strat :tele :es335 :sg :lp` to `BuiltinName` in the grammar.
Add `pick-pos` and `tone` as parameter transformer names.

`builtin_meta.edn` entries must include `see-also` references to DST effects:

```edn
{:name "lp"
 :category "synth"
 :returns "event-param-map"
 :side-effects []
 :examples ["(->> (scale :minor :a2 (seq 0 3 5 7)) (synth :lp))"
            "(->> (pure :e2) (synth :lp) (fx :amp-sim :marshall) (fx :cab :4x12))"]
 :see-also ["strat" "tele" "sg" "es335" "amp-sim" "cab" "distort" "overdrive"]}
```

Run `npm run gen:grammar` and `npm run gen:ai-docs`.

---

## Files to change

| File | Change |
|---|---|
| `packages/audio/src/lib.rs` | Add five presets to `ks_preset()`; add body resonance fields to `Voice::KarplusStrong`; update `tick` to apply body IIR; handle `pick_pos` and `tone` overrides in trigger parsing |
| `packages/core/src/repulse/params.cljs` | Add `pick-pos` and `tone` core param functions |
| `packages/lisp/src/repulse/lisp/builtins/params.cljs` | Expose `pick-pos` and `tone` as Lisp builtins |
| `app/src/repulse/audio.cljs` | Add `#{:strat :tele :es335 :sg :lp}` branch to `play-event`; encode `pick-pos`/`tone` in trigger string |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add five synth names + `pick-pos` + `tone` |
| `app/src/repulse/lisp-lang/completions.js` | Add completion entries |
| `app/src/repulse/content/builtin_meta.edn` | Add metadata with `see-also` DST references |
| `npm run gen:grammar` | After grammar edit |
| `npm run build:wasm` | After any `lib.rs` change |
| `npm run gen:ai-docs` | After `builtin_meta.edn` edit |

---

## Definition of done

- [ ] `(->> (scale :blues :e3 (seq 0 2 3 5)) (synth :strat))` produces a recognisable bright single-coil character
- [ ] `(->> (pure :e2) (synth :lp))` has audibly more sustain and warmth than `(synth :strat)` at the same pitch
- [ ] `(->> (pure :e3) (synth :tele) (pick-pos 0.95))` sounds brighter/twangier than `(pick-pos 0.15)` on the same preset
- [ ] `(->> (pure :a3) (synth :es335) (tone 0.2))` sounds audibly darker than `(tone 0.9)` on the same preset
- [ ] `(->> (pure :e2) (synth :lp) (fx :amp-sim :marshall) (fx :cab :4x12))` produces a usable distorted guitar tone
- [ ] All five presets produce perceptibly distinct timbres when the same pitch is played
- [ ] SYN1 acoustic presets (`:guitar`, `:harp`, etc.) still work identically after the body resonance fields are added to `Voice::KarplusStrong` (acoustic presets use identity IIR)
- [ ] `npm run build:wasm` succeeds; `npm run test:rust` passes
- [ ] `npm run test` passes; `npx shadow-cljs compile app` clean
- [ ] `pick-pos` and `tone` appear in editor autocomplete and hover docs
- [ ] All five synth names have `see-also` entries pointing to DST effects in hover docs
- [ ] `npm run gen:ai-docs` was run; `docs/ai/builtins.json` includes all seven new names

---

## What NOT to do

- Do not implement polyphonic guitar chord voicings as a single trigger — each note
  in a chord fires separately via the existing `(chord …)` pattern combinator.
- Do not implement string-bending (`bend` parameter) here — a pitch-ramp per-event
  parameter is a larger addition deferred to a future phase.
- Do not implement twelve-string doubling — `(fx :chorus …)` already provides detuned
  doubling and is the correct tool.
- Do not add amp-sim or cab presets here — those are delivered by DST3 and DST6;
  just cross-reference them in metadata.
- Do not implement bass guitar variants — the existing `(synth :pizz)` from SYN1
  covers fretless bass; a dedicated `:bass-elec` preset can be added in a follow-on
  phase if needed.
