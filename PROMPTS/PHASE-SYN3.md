# Phase SYN3 — Bowed String Waveguide

## Goal

Add a full bi-directional digital waveguide bowed-string voice to the WASM engine,
producing `(synth :violin)`, `(synth :viola)`, `(synth :cello)`, and `(synth :bass-arco)`
as first-class voices. The waveguide model simulates the physical interaction of a bow
with a string: two delay lines (nut→bridge, bridge→nut), a piecewise-linear bow
friction table, and per-instrument body resonance IIR filters. Unlike the Karplus-Strong
plucked voice (SYN1), the waveguide sustains continuously and responds to `bow-pressure`
and `bow-pos` per-event parameters.

```lisp
;; Before — no bowed string; only plucked (SYN1) or oscillators:
(->> (scale :minor :c4 (seq 0 3 5 7))
     (synth :violin))   ;; not yet available

;; After — sustained bowed violin melody:
(->> (scale :minor :c4 (seq 0 3 5 7))
     (synth :violin)
     (amp 0.7))

;; Cello bass line with heavier bow:
(->> (scale :minor :c2 (seq 0 :_ 5 :_ 3 :_ 7 :_))
     (synth :cello)
     (bow-pressure 0.8)
     (amp 0.6))

;; Sul ponticello (near bridge) — bright, glassy:
(->> (pure :g4) (synth :violin) (bow-pos 0.9))

;; Sul tasto (over fingerboard) — dark, full:
(->> (pure :g4) (synth :violin) (bow-pos 0.1))
```

---

## Background

### Architectural decision (recorded in CLAUDE.md)

A Karplus-Strong bow approximation (slow-attack + high feedback) was evaluated and
rejected. It sounds plucked with a slow onset — not bowed — and re-triggers on every
note rather than sustaining continuously. The full bi-directional waveguide is used.

### Bi-directional waveguide model

Based on Smith (1992) / Valimaki & Tolonen (1998). The physical model:

```
  Nut ─────────────────────── Bridge
       →  delay line 1  →
       ←  delay line 2  ←
                 ↕ bow interaction junction
```

At each sample tick:
1. Read the travelling waves at the bow contact point from both delay lines.
2. Apply the bow friction function to compute the reflected wave:
   `v_rel = v_bow - (wave1 + wave2)` (bow–string relative velocity)
   `friction = bow_table(v_rel)` (piecewise-linear Schelleng diagram)
   `reflected = bow_force × friction`
3. Feed reflected wave back into both delay lines.
4. Apply body resonance IIR at the bridge output.
5. Output is the bridge end of delay line 2.

### Body resonance filters

Each instrument has a 2-pole IIR body resonance filter applied to the bridge output.
Coefficients model the principal resonance peaks of each instrument body:

| Preset | Resonance freq | Q |
|---|---|---|
| `:violin` | 280 Hz | 8 |
| `:viola` | 230 Hz | 7 |
| `:cello` | 130 Hz | 6 |
| `:bass-arco` | 90 Hz | 5 |

### WASM voice architecture

`packages/audio/src/lib.rs` — see SYN1/SYN2 for overall structure. New variant:
`Voice::BowedString`. Trigger dispatch via `"bow:{preset}:{freq}:{amp}:{pressure}:{pos}"`.

---

## Implementation

### 1. `Voice::BowedString` struct

```rust
BowedString {
    // Two delay lines (ring buffers), pre-allocated to 2205 samples each
    dl1: Vec<f32>,   // nut → bridge
    dl2: Vec<f32>,   // bridge → nut
    dl_len: usize,   // floor(sr / freq / 2) — half period per delay line
    pos1: usize,     // write pointer for dl1
    pos2: usize,     // write pointer for dl2
    bow_pos: f32,    // 0.0 = nut, 1.0 = bridge; tap point in delay line
    bow_force: f32,  // bow normal force (bow pressure)
    bow_vel: f32,    // bow velocity (fixed at 0.2 — controls overall level)
    // Body resonance (2-pole IIR)
    body_b0: f32,
    body_b1: f32,
    body_b2: f32,
    body_a1: f32,
    body_a2: f32,
    body_x1: f32,
    body_x2: f32,
    body_y1: f32,
    body_y2: f32,
    // Amplitude
    gain: f32,
    env_phase: f32,  // seconds since trigger
    attack: f32,     // per-preset attack time
},
```

**Memory:** pre-allocate both `dl1` and `dl2` at 2205 `f32` samples each — consistent
with the SYN1 pre-allocation decision in CLAUDE.md.

### 2. Bow friction table

Piecewise-linear approximation of the Schelleng diagram bow–hair friction curve:

```rust
fn bow_table(v_rel: f32, bow_force: f32) -> f32 {
    // Simplified 4-segment friction model
    let v = v_rel.abs();
    let sign = v_rel.signum();
    let friction = if v < 0.1 {
        bow_force * (1.0 - v * 5.0)      // static friction region
    } else if v < 0.5 {
        bow_force * 0.5 * (1.0 - v)      // Helmholtz motion region
    } else {
        bow_force * 0.1 / (v + 0.1)      // sliding friction tail
    };
    friction * sign
}
```

### 3. Preset table

```rust
fn bow_preset(name: &str) -> (f32, f32, f32, f32) {
    // (bow_force, resonance_freq, resonance_q, attack)
    match name {
        "viola"     => (0.5, 230.0, 7.0, 0.04),
        "cello"     => (0.6, 130.0, 6.0, 0.05),
        "bass-arco" => (0.7,  90.0, 5.0, 0.08),
        _           => (0.45, 280.0, 8.0, 0.03), // "violin" default
    }
}
```

Body resonance IIR coefficients are computed from `resonance_freq` and `resonance_q`
using the standard 2-pole bandpass formula (same `Biquad::bandpass` helper already in
`lib.rs`).

### 4. `tick` implementation

```rust
Voice::BowedString { dl1, dl2, dl_len, pos1, pos2,
                     bow_pos, bow_force, bow_vel,
                     body_b0, body_b1, body_b2, body_a1, body_a2,
                     body_x1, body_x2, body_y1, body_y2,
                     gain, env_phase, attack, .. } => {
    // Bow contact index in delay line
    let tap = (*bow_pos * *dl_len as f32) as usize % *dl_len;
    let w1 = dl1[tap];
    let w2 = dl2[tap];

    // Bow–string interaction
    let v_rel = *bow_vel - (w1 + w2);
    let reflected = bow_table(v_rel, *bow_force);

    // Propagate: write reflected wave into delay lines
    dl1[*pos1] = reflected - w2;
    dl2[*pos2] = reflected - w1;
    *pos1 = (*pos1 + 1) % *dl_len;
    *pos2 = (*pos2 + 1) % *dl_len;

    // Bridge output
    let bridge = dl2[*pos2];

    // Body resonance
    let body_out = *body_b0 * bridge + *body_b1 * *body_x1 + *body_b2 * *body_x2
                   - *body_a1 * *body_y1 - *body_a2 * *body_y2;
    *body_x2 = *body_x1; *body_x1 = bridge;
    *body_y2 = *body_y1; *body_y1 = body_out;

    // Attack envelope
    let env = if *env_phase < *attack {
        *env_phase / *attack
    } else { 1.0 };
    *env_phase += 1.0 / sr;

    body_out * env * *gain
}
```

`is_silent`: `false` — bowed strings sustain indefinitely. The note ends when the
scheduler's event duration expires and the `ActiveVoice` is removed from `self.voices`.

### 5. New per-event parameters: `bow-pressure` and `bow-pos`

Per-event parameter transformers (like `amp`, `decay`, `pan`) live in two files —
**not** in `tracks.cljs` (that file handles track-level builtins like `track`/`mute!`).

**Step 1:** Add core functions to `packages/core/src/repulse/params.cljs`:

```clojure
(defn bow-pressure
  "Bow normal force; higher = more biting tone. 0.0–1.0.
   (bow-pressure 0.8 pat) — apply directly
   (bow-pressure 0.5)     — return transformer"
  ([v]     (fn [pat] (bow-pressure v pat)))
  ([v pat] (apply-param :bow-pressure v pat)))

(defn bow-pos
  "Bow contact point along the string; 0.0 = nut, 1.0 = bridge.
   (bow-pos 0.9 pat) — sul ponticello (bright/glassy)
   (bow-pos 0.1 pat) — sul tasto (dark/full)
   (bow-pos 0.15)    — return transformer"
  ([v]     (fn [pat] (bow-pos v pat)))
  ([v pat] (apply-param :bow-pos v pat)))
```

**Step 2:** Expose them as Lisp builtins in `packages/lisp/src/repulse/lisp/builtins/params.cljs`:

```clojure
"bow-pressure" (fn
                 ([v]   (params/bow-pressure (u/unwrap v)))
                 ([v p] (params/bow-pressure (u/unwrap v) (u/unwrap p))))
"bow-pos"      (fn
                 ([v]   (params/bow-pos (u/unwrap v)))
                 ([v p] (params/bow-pos (u/unwrap v) (u/unwrap p))))
```

**Step 3:** In `play-event` (`app/src/repulse/audio.cljs`), read `:bow-pressure` and
`:bow-pos` from the event map alongside `:amp`/`:decay`, then encode in the trigger:
`"bow:{preset}:{freq}:{amp}:{pressure}:{pos}"`

Defaults: `bow-pressure` → preset's `bow_force`; `bow-pos` → `0.15` (slightly off-nut,
realistic bowing position).

### 6. Grammar, completions, metadata

Add `:violin :viola :cello :bass-arco :bow-pressure :bow-pos` to the grammar and
completions. Note: `bow-pressure` and `bow-pos` are parameter transformers like `amp`
and `decay`, not `synth` preset names — add them to the correct grammar rule.

Run `npm run gen:grammar` and `npm run gen:ai-docs`.

---

## Files to change

| File | Change |
|---|---|
| `packages/audio/src/lib.rs` | Add `Voice::BowedString`, `bow_preset()`, `bow_table()`, trigger dispatch, `tick`, `is_silent` |
| `packages/core/src/repulse/params.cljs` | Add `bow-pressure` and `bow-pos` core param functions |
| `packages/lisp/src/repulse/lisp/builtins/params.cljs` | Expose `bow-pressure` and `bow-pos` as Lisp builtins |
| `app/src/repulse/audio.cljs` | Add `#{:violin :viola :cello :bass-arco}` branch to `play-event`; encode `bow-pressure`/`bow-pos` in trigger string |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add four synth names + two param names |
| `app/src/repulse/lisp-lang/completions.js` | Add completion entries |
| `app/src/repulse/content/builtin_meta.edn` | Add metadata for all six new names |
| `npm run gen:grammar` | After grammar edit |
| `npm run build:wasm` | After any `lib.rs` change |
| `npm run gen:ai-docs` | After `builtin_meta.edn` edit |

---

## Definition of done

- [ ] `(->> (scale :minor :c4 (seq 0 3 5 7)) (synth :violin))` produces a sustained bowed string tone on all four pitches
- [ ] `(->> (pure :g4) (synth :violin))` sustains continuously for the note duration without re-triggering
- [ ] `(->> (pure :g4) (synth :violin) (bow-pos 0.9))` sounds brighter/glassier than `(bow-pos 0.1)` (sul ponticello vs sul tasto)
- [ ] `(->> (pure :c3) (synth :cello) (bow-pressure 0.8))` sounds heavier/denser than `(bow-pressure 0.3)` at the same pitch
- [ ] `(->> (scale :minor :c2 (seq 0 :_ 5 :_)) (synth :bass-arco) (amp 0.6))` produces a deep bowed bass line
- [ ] All four presets produce perceptibly distinct timbres at the same pitch
- [ ] `(->> (pure :d4) (synth :viola))` has a darker character than `(synth :violin)` at a nearby pitch
- [ ] `npm run build:wasm` succeeds; `npm run test:rust` passes
- [ ] `npm run test` passes; `npx shadow-cljs compile app` clean
- [ ] `bow-pressure` and `bow-pos` appear in editor autocomplete and hover docs
- [ ] All four synth names appear in editor autocomplete with correct detail strings
- [ ] `npm run gen:ai-docs` was run; `docs/ai/builtins.json` includes all six new names

---

## What NOT to do

- Do not implement polyphonic legato / slur mode (bow holds across note boundaries without re-trigger) — a significant scheduler change, deferrable.
- Do not implement vibrato as an automatic parameter on bowed strings — use the existing `(fx :chorus …)` or a future vibrato LFO effect.
- Do not implement electric bowed strings (e.g., electric violin) — route `:violin` through DST phases for that effect.
- Do not add more than the two new parameters (`bow-pressure`, `bow-pos`) — keep the surface minimal.
- Do not implement plucked strings here — that is SYN1 (Karplus-Strong).
