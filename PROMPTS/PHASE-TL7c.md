# Phase TL7c — Vowel, Envelopes & Modulators

> **Spec status:** full (expanded from the outline on 2026-11-20).
> **Automation:** 🤖👂 auto + human check. Envelope shapes, parameter plumbing and the
> JS↔WASM parity are measured offline; the musical result needs a listen
> ([Listen check](#listen-check)). Follow the CLAUDE.md "FM preset tests" guidance:
> concrete Goertzel/level assertions only, no claims about how something sounds.

**Milestone:** 1 — Tidal-Lisp core
**Depends on:** TL7a (per-event insert chain, `trigger_v3` slots 0–11), TL7b (orbit
sends, slots 12–14, `delay-time`)
**Unblocks:** SYN6 Part C (filter envelope and vibrato in its preset table), SK3/LIVE2
(`nudge`)

TL7 was split into three phases on 2026-11-10: **TL7a** per-event filters, shape &
crush; **TL7b** reverb & delay sends per orbit; **TL7c** (this phase) the rest of the
per-event control set that a song needs.

## Goal

TL7a's filters take one value per event. TL7c lets them move *within* a note (filter
envelopes), adds a pitch envelope and vibrato, a formant filter (`vowel`), sample-rate
reduction (`coarse`), a one-knob DJ filter (`djf`), an output gain after the chain
(`postgain`), a per-event timing offset (`nudge`) and a cycle-synced delay time
(`delay-sync`).

```lisp
(->> (seq :c2 :c2 :eb2 :g1) (synth :saw)
     (lpf 300) (resonance 8) (lp-env 4) (lp-decay 0.2))      ; acid-style pluck
(->> (seq :a3 :e4) (synth :saw) (vowel (seq :a :o)))
(->> (fast 4 (seq :c5 :e5)) (synth :square) (coarse (seq 1 4 8)))
(->> (seq :c5) (synth :square) (vib 6) (vib-mod 0.3))
(->> (seq :c3) (synth :saw) (penv 12) (p-decay 0.08))       ; pitch drop
(->> (fast 8 (seq :hh)) (djf (slow 4 sine)))
(->> (seq :sd :_ :sd :_) (delay 0.4) (delay-sync 3/16))
(->> (fast 4 (seq :hh)) (nudge (seq 0 0.02 0 0.02)))               ; swing the off-beats
```

## Decisions

Decided with the project owner on 2026-11-20.

1. **One phase, four parts** (A envelopes, B pitch, C inserts, D gain/timing/sends),
   one PR. No TL7d.
2. **`coarse` runs on the WASM synth voices only.** It has no native Web Audio node,
   and TL7a's rule stands: the JS path configures native nodes, no hand-written DSP. On
   the JS path (samples, `defsynth`, tracks with an `fx` chain, the TEST1 offline
   harness) `coarse` has no effect and the console warns once per session. Samples get
   it with a later samples-in-WASM phase.
3. **No ADSR/sustain-level in this phase.** `sustain` already means "sample length in
   seconds" (TL6, Tidal semantics), and synth voices have no gate today. `hold`, a
   sustain level and the `adsr`/`ad`/`ar`/`ds` shorthands are deferred; the parity rows
   become ❓ with that reason. Voices without TL7c keys sound exactly as before.
4. **Track-level stays track-level.** `tremolo`, `phaser`, `chorus`, `leslie`,
   `compressor`, `chebyshev` and per-event `distort` are not added per event: the
   `(fx …)` plugins (and FX1) cover them, and per-event `shape` covers per-note drive.
   Parity rows become 🔁 (use `(fx …)` / `shape`). `duck`, `room-lp`/`room-dim`/
   `room-fade` and per-event FM controls (`fm`, `fmh`, …) are deferred (❓ / SYN6).

Design decisions made in this spec (implementer follows them):

5. **Envelopes are linear in the log domain.** A filter envelope moves the cutoff in
   octaves, a pitch envelope moves the pitch in semitones, so a straight line in the
   envelope is an exponential ramp in Hz. Web Audio expresses exactly this with
   `exponentialRampToValueAtTime` on `frequency` and `linearRampToValueAtTime` on
   `detune`; Rust evaluates the same piecewise-linear function. SYN6 already assumes
   these units (`lp-env` in octaves above `lpf`, `vib-mod` in semitones).
6. **The gate is the event's duration.** Release starts at `onset + dur`, where `dur` is
   the event's whole length in seconds (the `{:dur …}` timing `play-event` already
   receives). Without timing (direct `play-event` calls) the gate is infinite: no
   release. The envelope never touches the voice's amplitude envelope.
7. **Rust modulates at control rate.** Envelope and vibrato values are computed every
   16 samples (≈ 0.36 ms at 44.1 kHz); filter coefficients are recomputed then and kept
   between updates. Web Audio computes them per sample; the parity tolerance covers the
   difference.
8. **Pitch modulation applies to tonal voices and samples.** `Tone`, `Saw`, `Square`,
   `FM` (both formats) in Rust; `make-sine`, `make-saw`, `make-square` and sample
   sources (`AudioBufferSourceNode.detune`) in JS. The synthesized drums (`bd sd hh oh`,
   both paths), `noise`, Karplus-Strong (`ks:…`, it has its own vibrato) and `defsynth`
   voices ignore `penv`/`vib`. Documented in USAGE.
9. **`djf` is folded into the existing filter slots** (no new DSP, no new slot):
   below 0.5 it sets `lpf`, above 0.5 `hpf`. An explicit `lpf`/`hpf` on the same event
   wins.

## Parameters

All are two-arity control functions like `amp` (`(f v)` → transformer, `(f v pat)`),
accept patterns and signals, and are sampled once per event at its onset. Core does not
clamp; `app/src/repulse/inserts.cljs` (and `orbits.cljs` for `delay-sync`) does.

| Function | Event key | Range (clamped) | Default | Meaning |
|---|---|---|---|---|
| `lp-env` | `:lp-env` | −10…10 octaves | 0 (off) | depth of the low-pass envelope above `lpf` |
| `lp-attack` | `:lp-attack` | 0–10 s | 0.001 | time from `lpf` to `lpf·2^lp-env` |
| `lp-decay` | `:lp-decay` | 0–10 s | 0.2 | time from the peak to the sustain level |
| `lp-sustain` | `:lp-sustain` | 0–1 | 0 | sustain level, as a fraction of the depth |
| `lp-release` | `:lp-release` | 0–10 s | 0.1 | time from the level at the gate back to `lpf` |
| `hp-env` … `hp-release` | `:hp-env` … | same | same | the same envelope on `hpf` |
| `bp-env` … `bp-release` | `:bp-env` … | same | same | the same envelope on `bpf` |
| `penv` | `:penv` | −48…48 semitones | 0 (off) | depth of the pitch envelope |
| `p-attack` `p-decay` `p-sustain` `p-release` | `:p-attack` … | as above | as above | its times and level |
| `vib` | `:vib` | 0–40 Hz | 0 (off) | vibrato rate |
| `vib-mod` | `:vib-mod` | 0–12 semitones | 0.5 | vibrato depth (peak deviation) |
| `vowel` | `:vowel` | `:a :e :i :o :u` (keyword or string) | off | 5-band formant filter |
| `coarse` | `:coarse` | 1–64, rounded | 1 (off) | sample-and-hold every *n* samples (WASM voices only) |
| `djf` | `:djf` | 0–1 | 0.5 (off) | < 0.5 low-pass, > 0.5 high-pass |
| `postgain` | `:postgain` | 0–4 | 1 | linear gain after the insert chain |
| `nudge` | `:nudge` | −1…1 s | 0 | moves the event's onset in time |
| `delay-sync` | `:delay-sync` | 1/64–16 cycles | — | orbit delay time in cycles; overrides `delay-time` |

A filter envelope without its filter (`(lp-env 3 pat)` with no `lpf`, or `lpf` from
`djf`) has no effect, like a Q without its filter. An envelope with depth 0 is off even
when its times are set. `vib-mod` without `vib` has no effect. An unknown vowel is
ignored.

**Processing order per voice** (TL7a's order, extended; new stages in bold):
source(**pitch: penv + vib**) → lpf(**+env**) → hpf(**+env**) → bpf(**+env**) →
**vowel** → **coarse** → crush → shape → **postgain** → pan → sends.

### Shared formulas (both implementations use exactly these)

**Envelope** `e(t)` for times `A D S R` and gate `G` (`t` = seconds since onset):

```
peak(t)  = t < A ? t/A : (t < A+D ? 1 + (S − 1)·(t − A)/D : S)   (A = 0 → 1 at t = 0;
                                                                  D = 0 → S after A)
e(t)     = t < G ? peak(t)
         : t < G + R ? peak(G) · (1 − (t − G)/R)                  (R = 0 → 0 after G)
         : 0
```

**Filter envelope:** `f(t) = base · 2^(d_eff · e(t))`, with `base` the clamped filter
frequency and the effective depth
`d_eff = log2(clamp(base · 2^depth, 20, min(20000, 0.49·sr)) / base)`, so clipping at
the clamp never bends the curve differently in the two paths.

**Pitch:** `semitones(t) = penv · e_p(t) + vib-mod · sin(2π · vib · t)`, frequency
multiplier `2^(semitones/12)`. Web Audio: `detune` in cents = `100 · semitones`.

**djf:** `d < 0.5 → lpf = 20 · 1000^(2d)`; `d > 0.5 → hpf = 20 · 1000^(2d − 1)`;
`d = 0.5` → off. (`d = 0` → 20 Hz low-pass, `d = 1` → 20 kHz high-pass.)

**vowel:** five parallel band-passes (Audio EQ Cookbook, constant 0 dB peak gain), each
scaled by its formant gain, summed, times a make-up gain of **2.0**. Q = frequency /
bandwidth. Table (Csound bass formants):

| Vowel | F1–F5 (Hz) | Gains (dB) | Bandwidths (Hz) |
|---|---|---|---|
| `a` | 600 1040 2250 2450 2750 | 0 −7 −9 −9 −20 | 60 70 110 120 130 |
| `e` | 400 1620 2400 2800 3100 | 0 −12 −9 −12 −18 | 40 80 100 120 120 |
| `i` | 250 1750 2600 3050 3340 | 0 −30 −16 −22 −28 | 60 90 100 120 120 |
| `o` | 400 750 2400 2600 2900 | 0 −11 −21 −20 −40 | 40 80 100 120 120 |
| `u` | 350 600 2400 2675 2950 | 0 −20 −32 −28 −36 | 40 80 100 120 120 |

**coarse** (Rust only): `n = round(clamp(coarse, 1, 64))`; every `n`-th sample (counting
from the voice's first sample) is taken and held for `n` samples. `n = 1` is bypass.

## Background (where things are)

- **Core controls:** `packages/core/src/repulse/params.cljs` (`apply-param`, two-arity
  fns), tests in `params_test.cljs`. Lisp registration:
  `packages/lisp/src/repulse/lisp/builtins/params.cljs` (`(param f)` helper).
- **Insert spec:** `app/src/repulse/inserts.cljs` — `insert-params` (clamps, nil when
  no inserts), `amp-factor`, `wasm-slots` (slots 4–11), `off`. Tests:
  `app/src/repulse/inserts_test.cljs`.
- **JS chain:** `app/src/repulse/audio.cljs` — `event-output` builds a *linear* node
  list (gain → lpf → hpf → bpf → crush → shape → send gain) and connects it to `dest`;
  `play-event` passes `js-dest` to the voice constructors `make-sine`, `make-saw`,
  `make-square` (they currently return nothing) and to `samples/play!`.
  `worklet-trigger-v3!` sends slots 0–14 (`inserts/wasm-slots`, `orbits/wasm-slots`).
- **Samples:** `app/src/repulse/samples.cljs` `play!` creates the
  `AudioBufferSourceNode` inside a buffer-load promise; it takes an `extra` map.
- **Orbits:** `app/src/repulse/orbits.cljs` `orbit-settings` (clamps `:delay-time`
  0.01–4) and `apply-orbit-settings!`, called from `play-event`.
- **WASM:** `packages/audio/src/lib.rs` — `Biquad` (constructors return fresh state),
  `InsertParams`/`Inserts` (built in `activate`, `tick` per sample), `Pending`,
  `pending_v3` (missing trailing slot = default), `ActiveVoice {voice, pan, inserts,
  sends}`, `render` (per sample: `inserts.tick(voice.tick(sr))` → pan → sends),
  `Voice::tick(&mut self, sr)`. Parity: `parity_levels`/`PARITY_DB` (Rust) ↔
  `e2e/audio-render.spec.ts`.
- **Timing:** `play-event` receives `{:dur :cycle-dur}` from the live scheduler
  (`schedule-event!`), offline export (`offline_render.cljs`) and the TEST1 harness
  (`test_api.cljs`).

## Implementation

### Part A — Filter envelopes

1. **Core + Lisp:** 15 control fns `lp-env lp-attack lp-decay lp-sustain lp-release`,
   `hp-…`, `bp-…` (keys as named). Register them in the Lisp builtins.
2. **`inserts.cljs`:**
   - `env-params [v prefix]` → `{:depth :attack :decay :sustain :release}` clamped with
     the defaults above, or nil when depth is 0/absent. `insert-params` adds
     `:lp-env`, `:hp-env`, `:bp-env` (each such map or nil), only when the matching
     filter is on.
   - `env-value [{:keys [attack decay sustain release]} gate t]` — the formula `e(t)`
     (gate nil = ∞). Pure, unit-tested at breakpoints.
   - `env-points [env gate]` → vector of `[t e kind]` (`kind` `:set` | `:ramp`),
     relative to onset: `[0 e0 :set]`, peak at `A` (when `A > 0`), sustain at `A+D`,
     then — if the gate falls before `A+D` — the point `[G e(G) :ramp]` replaces the
     later ones, and `[G+R 0 :ramp]` ends it (no release points when the gate is ∞).
     Consecutive points with the same `t` keep the last. This is the single schedule
     both the cutoff and the detune automation use.
   - `effective-depth [base depth sr]` (formula above).
3. **JS path (`audio.cljs`):** in `insert-biquad`, when the stage has an envelope,
   schedule on `frequency`: `setValueAtTime` for `:set`, `exponentialRampToValueAtTime`
   for `:ramp`, value `base · 2^(d_eff · e)`, time `t + point-t`. Pass the gate
   (`(:dur timing)`) into `event-output`.
4. **WASM (`lib.rs`):**
   - `struct EnvSpec { depth, attack, decay, sustain, release }` (inline, `Copy`),
     `fn value(&self, gate: f32, t: f32) -> f32` = `e(t)`.
   - `Inserts` keeps, per filter stage, the base frequency, Q, kind and an
     `Option<EnvSpec>` with its effective depth. New
     `fn modulate(&mut self, t: f32, gate: f32, sr: f32)` recomputes the coefficients
     of enveloped stages via a new `Biquad::set_coeffs(&mut self, from: &Biquad)` that
     copies the five coefficients and keeps the state.
   - `render`: each `ActiveVoice` gets `age: u32` (samples since activation); when
     `age % 16 == 0` and the voice has any modulation, call `modulate`.
5. **Slots** (appended; see the table in Part D).

### Part B — Pitch envelope & vibrato

1. **Core + Lisp:** `penv p-attack p-decay p-sustain p-release vib vib-mod`.
2. **`inserts.cljs`:** `pitch-params [v]` → `{:penv env-map-or-nil :vib hz :vib-mod st}`
   or nil when neither is active (`env-params` with prefix `p`, depth key `:penv`).
3. **JS path:**
   - `make-sine`, `make-saw`, `make-square` return their `OscillatorNode`.
   - `pitch-mods! [ac t param pm gate stop-t]` on any `detune` AudioParam: envelope
     points as `setValueAtTime`/`linearRampToValueAtTime` in cents; vibrato as a sine
     `OscillatorNode` (`vib` Hz) → `GainNode` (gain `100 · vib-mod`) → `param`, started
     at `t`, stopped at `stop-t`.
   - `play-event` applies it to the oscillator of the tonal JS voices (decision 8) and
     to sample sources: `samples/play!` takes an optional `:on-source` fn in `extra`,
     called as `(on-source src t')` right after the source is created. No other
     change to `samples.cljs`.
4. **WASM:** `Voice::tick(&mut self, sr: f32, pitch: f64)`; `Tone`, `Saw`, `Square` and
   `FM` multiply their phase increments (FM: carrier and modulator) by `pitch`; every
   other voice ignores it. `ActiveVoice` keeps `pitch_mul: f64` (1.0 = none), updated
   at control rate from `EnvSpec::value` and the vibrato phase. The no-modulation path
   passes exactly `1.0` (bit-identical to before).

### Part C — vowel, coarse, djf

1. **Core + Lisp:** `vowel coarse djf`. `vowel` values stay keywords/strings in the
   event map.
2. **`inserts.cljs`:** `insert-params` gains `:vowel` (index 1–5 for `a e i o u`, nil
   otherwise) and `:coarse` (integer 2–64, nil when 1); `djf` is folded into
   `:lpf`/`:hpf` per the formula when those are absent. `vowel-table` holds the formant
   table above; `vowel-index [x]`.
3. **JS path:** the vowel stage is a sub-graph (input `GainNode` → 5 band-pass
   `BiquadFilterNode`s → 5 formant `GainNode`s → output `GainNode` with gain
   `2.0`). `event-output` switches from a linear node list to a list of `[in out]`
   stages so a stage can be a sub-graph; single nodes are `[n n]`. `coarse` builds no
   node: when it is present and the event takes the JS path, `console.warn` once per
   session: `coarse only works on synth voices (WASM); ignored for samples, defsynth
   voices and tracks with an fx chain`.
4. **WASM:** `Inserts` gets `vowel: Option<[Biquad; 5]>` plus the five linear gains
   (inline array, no heap), and `coarse_n: u32`, `coarse_hold: f32`, `coarse_count:
   u32`. Order per the processing order above.

### Part D — postgain, nudge, delay-sync, slot layout

1. **Core + Lisp:** `postgain nudge delay-sync`.
2. **postgain:** `insert-params` gains `:postgain` (present when ≠ 1). JS: a trailing
   `GainNode` after shape (before the send gain). WASM: multiply after shape.
3. **nudge:** in `play-event`, `t' = t + clamp(nudge, −1, 1)`, and on offline contexts
   `max 0`. Everything in `play-event` (voices, inserts, orbit settings) uses `t'`. The
   scheduler's visual and MIDI callbacks keep `t` (documented: the flash is not
   nudged).
4. **delay-sync:** `orbits/orbit-settings` takes `cycle-dur` (new optional arg) and
   sets `:delay-time (clamp 0.01 4 (* delay-sync cycle-dur))` when `:delay-sync` is a
   number and `cycle-dur` is known; it overrides `:delay-time` on the same event.
   `play-event` passes `(:cycle-dur timing)`.
5. **Slot layout** (`lib.rs` `trigger_v3`, appended, never reordered):

   | Slots | Content | Default |
   |---|---|---|
   | 0–3 | amp attack decay pan | (TL7a) |
   | 4–11 | lpf resonance hpf hpq bpf bpq shape crush | (TL7a) |
   | 12–14 | room delay orbit | (TL7b) |
   | 15 | gate (s, 0 = ∞) | 0 |
   | 16–20 | lp-env lp-attack lp-decay lp-sustain lp-release | 0 0.001 0.2 0 0.1 |
   | 21–25 | hp-env … hp-release | same |
   | 26–30 | bp-env … bp-release | same |
   | 31–35 | penv p-attack p-decay p-sustain p-release | same |
   | 36–37 | vib vib-mod | 0 0.5 |
   | 38 | vowel (0 off, 1–5 = a e i o u) | 0 |
   | 39 | coarse | 1 |
   | 40 | postgain | 1 |

   `inserts/wasm-slots-v3c` produces slots 15–40. `worklet-trigger-v3!` sends the
   41-slot layout only when the event has any TL7c key; otherwise the existing 12/15
   slot or `trigger_v2` messages, unchanged. Missing trailing slots default (as now).
   Envelope depth 0 in a slot means off.
6. `npm run build:wasm`.

### Registration (CLAUDE.md Rule 3)

All 28 names: `lp-env lp-attack lp-decay lp-sustain lp-release hp-env hp-attack
hp-decay hp-sustain hp-release bp-env bp-attack bp-decay bp-sustain bp-release penv
p-attack p-decay p-sustain p-release vib vib-mod vowel coarse djf postgain nudge
delay-sync`. Grammar → `npm run gen:grammar`; `completions.js`; `builtin_meta.edn`
(category `"param"`); `npm run gen:ai-docs`; hover entries; the "Effects" group in
`insert-categories.js` gets `vowel`, `djf`, `lp-env`, `vib`, `coarse`.

### Docs

- `docs/USAGE.md` "Effect parameters (per event)": new sub-sections *Filter
  envelopes*, *Pitch envelope & vibrato*, *Vowel, coarse, djf*, *postgain & nudge*;
  `delay-sync` next to `delay-time`; extend the processing-order line; state decision
  8 (which voices get pitch modulation) and decision 2 (`coarse` WASM only).
- `docs/STRUDEL-PARITY.md`: ✅ for `postgain`, `djf`, filter envelopes, `vowel`,
  `delaysync`, `coarse` (note: synth voices only), `vib`/`vibmod`, pitch envelope
  (note: no `pcurve`/`panchor`), `nudge`. ❓ for `sustain`/`hold`, `adsr` shorthands
  (decision 3), `roomlp/roomdim/roomfade`, `duckorbit`. 🔁 for `distort`,
  `chebyshev`, `compressor`, `phaser`, `tremolo`, `chorus`/`leslie` (decision 4).
  `fm`/`fmh` stays 🟡 pointing to SYN6. Update the family summary counts.

## Files to change

| File | Change |
|---|---|
| `packages/core/src/repulse/params.cljs` (+ `_test`) | 28 control fns + tests |
| `packages/lisp/src/repulse/lisp/builtins/params.cljs` | register 28 names |
| `app/src/repulse/inserts.cljs` (+ `_test`) | env, pitch, vowel, coarse, djf, postgain, slots 15–40 |
| `app/src/repulse/orbits.cljs` (+ `_test`) | `delay-sync` in `orbit-settings` |
| `app/src/repulse/audio.cljs` | stage list, envelopes, vowel graph, pitch mods, nudge, postgain, coarse warning, slots |
| `app/src/repulse/samples.cljs` | `:on-source` hook in `play!` |
| `packages/audio/src/lib.rs` | `EnvSpec`, `Biquad::set_coeffs`, `Inserts` (env, vowel, coarse, postgain), `Voice::tick` pitch arg, control-rate modulation, slots 15–40, tests |
| `app/public/worklet.js` | none expected (passes `params` through); verify |
| grammar + `parser.js`, `completions.js`, `hover.js`, `insert-categories.js`, `builtin_meta.edn` + `builtins.json` | Rule 3 |
| `e2e/audio-render.spec.ts` | offline envelope/vowel/djf/postgain/nudge/delay-sync tests, parity |
| `docs/USAGE.md`, `docs/STRUDEL-PARITY.md`, `ROADMAP.md` | docs, parity, delivered entry |

## Definition of done

Rust (`cargo test`):
- [ ] `EnvSpec::value` matches the formula at `0, A/2, A, A+D/2, A+D, G, G+R/2, G+R` for a
  gate before and after `A+D`.
- [ ] Filter envelope: saw 110 Hz, `lpf 300`, `lp-env 4`, `lp-decay 0.2`: energy at the
  harmonics in 1–3 kHz (Hann-windowed Goertzel; a rectangular window leaks the strong
  low harmonics into these bins) in the window 5–25 ms is ≥ 10 dB above the window
  300–320 ms. Use `decay 10` so the amplitude envelope barely moves.
- [ ] Pitch envelope: `Tone` 440 Hz with `penv 12`, `p-decay 0.5`: Goertzel in the first
  20 ms is ≥ 10 dB stronger at 880 Hz than at 440 Hz, and in 0.6–0.7 s the reverse.
- [ ] Vibrato: `Tone` 440 Hz, `vib 5`, `vib-mod 1`: the zero-crossing frequency measured
  over successive 25 ms windows spans at least 425–455 Hz over one second.
- [ ] `vowel :a` vs `vowel :i` on the same saw (110 Hz): at 1100 Hz (harmonic nearest
  `a`'s F2, 1040 Hz) `a` is ≥ 6 dB above `i`; at 220 Hz (nearest `i`'s F1, 250 Hz) `i`
  is ≥ 6 dB above `a`. (`i`'s F2 is −30 dB in this table, too weak to discriminate.)
- [ ] `coarse 8` on a 1 kHz sine: Goertzel at `sr/8 − 1000` Hz is ≥ 20 dB above the same
  bin without `coarse`.
- [ ] `postgain 0.5` lowers RMS by 6 dB ± 0.1.
- [ ] No-modulation path bit-identical: the existing `trigger_v2_render_is_bit_identical_to_pre_tl7a`
  and `trigger_v3_without_inserts_matches_v2` pass unchanged; a 41-slot trigger with all
  TL7c slots at defaults renders bit-identical to the 15-slot one.
- [ ] Parity reference for the filter envelope pinned (`ENV_PARITY_DB`, as `PARITY_DB`).

Offline (Playwright, TEST1 harness — JS path):
- [ ] `(->> (seq :a2) (synth :saw) (lpf 300) (lp-env 4) (lp-decay 0.2))`: same window
  assertion as Rust (≥ 10 dB), and the early/late ratio agrees with `ENV_PARITY_DB`
  within 3 dB.
- [ ] Release: `(->> (seq :a2 :_) (synth :saw) (decay 2) (lpf 300) (lp-env 4)
  (lp-sustain 1) (lp-release 0.05))` — high-band energy in the second half-cycle is
  ≥ 10 dB below the first half.
- [ ] `penv 12` on `(synth :saw)` and on a sample (`tone` test buffer): early 880 Hz
  above 440 Hz, late the reverse.
- [ ] `vowel` same assertions as Rust (JS formant graph).
- [ ] `djf 0.1` cuts 4 kHz by ≥ 15 dB vs no `djf`; `djf 0.9` cuts 200 Hz by ≥ 15 dB;
  `djf 0.5` renders identically to none.
- [ ] `(postgain 0.5 …)` −6 dB ± 0.5; `postgain` applies after `shape` (a shaped signal
  at `postgain 0.5` peaks at half the unscaled shaped peak, ± 2 %).
- [ ] `nudge 0.05` moves the onset by 50 ms ± 1 ms (`countOnsets`/first-sample index).
- [ ] `delay-sync 1/4` at 120 BPM (cycle 2 s) puts the first echo 0.5 s ± 5 ms after the
  dry hit.
- [ ] Events without TL7c keys create no extra nodes (the TL7a spy test still passes).

Everywhere:
- [ ] Every new parameter accepts patterns and signals (core tests).
- [ ] Render loop allocation-free: new Rust state is inline (`Copy` structs, fixed
  arrays); code review item.
- [ ] Rule 3 registration for all 28 names; no grammar/AI-docs drift.
- [ ] USAGE and parity docs updated; ROADMAP delivered entry.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] Human listen check recorded in the PR (below). The PR ends with `## Test protocol`.

## Listen check

Live app (WASM path) and one sample-based line (JS path):

1. Acid pluck: `(->> (seq :c2 :c2 :eb2 :g1) (synth :saw) (lpf 300) (resonance 8) (lp-env 4) (lp-decay 0.2))` — each note opens bright and closes within ~0.2 s.
2. Filter release: same with `(lp-sustain 1) (lp-release 0.3)` and `(slow 2 …)` — the filter stays open while the note is held and closes after.
3. Pitch drop: `(->> (seq :c3 :c3) (synth :saw) (penv 12) (p-decay 0.08))` — each note starts an octave high and falls.
4. Vibrato: `(->> (seq :a4) (synth :square) (vib 6) (vib-mod 0.3))` — steady 6 Hz wobble.
5. Vowels: `(->> (seq :a2 :a2 :a2 :a2 :a2) (synth :saw) (decay 0.4) (vowel (seq :a :e :i :o :u)))` — five distinct vowels.
6. coarse: `(->> (fast 4 (seq :c5)) (synth :square) (coarse (seq 1 4 8 16)))` — gets grittier step by step.
7. djf sweep: `(->> (fast 8 (seq :hh :oh)) (djf (slow 4 sine)))` on a sample kit.
8. delay-sync: `(->> (seq :sd :_ :_ :_) (delay 0.5) (delay-sync 3/16))` — dotted-eighth echoes locked to the tempo.

## What NOT to do

- No ADSR/gate on the amplitude envelope, no `hold`, no sustain level, no `adsr`
  shorthands (decision 3).
- No per-event `distort`, `tremolo`, `phaser`, `chorus`, `compressor`, `duck`,
  `room-lp`/`room-dim`/`room-fade`, FM operator controls (decision 4).
- No new AudioWorklet processor and no hand-written DSP on the JS side (decision 2).
- No sample playback in WASM.
- Don't reorder TL7a/TL7b slots or change their meaning; append only.
- No reorderable master chains (**P2**), no new synthesis models (**SYN3–5**), no song
  automation (**SONG2**).
