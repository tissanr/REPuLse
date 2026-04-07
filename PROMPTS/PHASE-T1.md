# Phase T1 — Parameter Transitions

## Goal

Add a `tween` built-in that makes any numeric parameter change smoothly over musical
time. The user wraps any value with `tween` and every note that fires during the
transition picks up the interpolated value at its exact scheduled time.

```lisp
;; Amp fade-in over 2 bars
(->> (seq :c4 :e4 :g4 :c5)
     (synth :saw)
     (amp (tween :linear 0.0 1.0 2)))

;; Exponential volume fade-out over 8 bars
(->> (fast 2 (seq :hh))
     (amp (tween :exp 1.0 0.0 8)))

;; S-curve pan sweep over half a bar
(->> (fast 4 (seq :sd))
     (pan (tween :sine -1.0 1.0 0.5)))

;; Different transitions per layer in a stack
(stack
  (->> (seq :bd :_ :bd :_) (amp (tween :linear 0.2 1.0 2)))
  (->> (fast 4 (seq :hh))  (pan (tween :sine -0.8 0.8 1))))
```

When the user live-edits and re-evaluates, the transition restarts. Static values work
identically to before — no regressions.

---

## Background

### How parameters work today

`packages/core/src/repulse/params.cljs` defines `apply-param`, shared by `amp`, `pan`,
`attack`, `decay`, `rate`, etc.:

```clojure
(defn- apply-param [kw param-val-or-pat note-pat]
  (core/combine (fn [pv nv] (assoc (to-map nv) kw pv))
                (if (pat? param-val-or-pat)
                  param-val-or-pat
                  (core/pure param-val-or-pat))
                note-pat'))
```

`pv` (the parameter value) is stored directly into each event under `kw`. The scheduler
in `audio.cljs` later extracts it with `(float (:amp value 1.0))`.

For `tween`, `pv` will be a plain map `{:type :tween ...}`. `apply-param` stores it
without modification — **no change to `params.cljs` is needed**. The scheduler must
detect this map and resolve it to a float before calling the audio layer.

### Scheduler timing

`schedule-cycle!` computes, for each event:

```clojure
event-offset = (* (- part-start cycle) cycle-dur)
t            = (+ cycle-audio-start event-offset)   ; absolute Web Audio time
```

`t` is the exact Web Audio clock time when that note fires. This is what we use to
compute how far into the transition has elapsed at the moment the note triggers.

### What "control-rate transition" means

Transitions are **not** audio-rate (not sample-by-sample modulation). They are
control-rate: each note that fires during the transition gets a scalar value computed
for its scheduled time. Notes triggered before the transition hold the start value;
notes after the transition hold the end value. The audio layer receives a normal
static float — it has no knowledge of transitions.

This is architecturally identical to `linearRampToValueAtTime` used for e.g. the kick
frequency sweep, but expressed over bars instead of milliseconds.

---

## Implementation

### 1. `tween` built-in — `packages/lisp/src/repulse/lisp/eval.cljs`

Add to `make-env` alongside the other parameter functions:

```clojure
"tween" (fn [curve-arg start-arg end-arg dur-arg]
          (let [curve (unwrap curve-arg)
                start (->num start-arg)
                end   (->num end-arg)
                dur   (->num dur-arg)]
            (when-not (#{:linear :exp :sine} curve)
              (throw (js/Error.
                      (str "Unknown curve type " curve
                           ". Available: :linear, :exp, :sine"))))
            (when-not (pos? dur)
              (throw (js/Error. "Transition duration must be > 0")))
            {:type :tween :curve curve :start start :end end :duration-bars dur}))
```

`tween` returns a plain data map — a **tween descriptor**. It is not a pattern and not
a function. `(amp (tween :linear 0 1 2))` stores this map as `:amp` in each event.

Error cases:
- Unknown curve → `"Unknown curve type :bogus. Available: :linear, :exp, :sine"`
- Duration ≤ 0 → `"Transition duration must be > 0"`
- Non-numeric `start`/`end` — `->num` will throw naturally; let it propagate

### 2. Transition start tracking — `app/src/repulse/audio.cljs`

Extend `scheduler-state` with a per-track start-time map:

```clojure
(def scheduler-state
  (atom {:playing?          false
         :tracks            {}
         :muted             #{}
         :cycle             0
         :cycle-dur         2.0
         :lookahead         0.2
         :interval-id       nil
         :on-beat           nil
         :on-event          nil
         :on-fx-event       nil
         :transition-starts {}}))  ; ← new: track-name → audio-time
```

Add `arm-transitions!` — called every time a track is (re-)evaluated:

```clojure
(defn- arm-transitions!
  "Record the current audio time as the transition start for track-name.
   Called on every play-track! so re-evaluation restarts running transitions."
  [track-name ac]
  (swap! scheduler-state update :transition-starts
         assoc track-name (.-currentTime ac)))
```

Call it from `play-track!` before `ensure-running!`:

```clojure
(defn play-track! [track-name pattern on-beat-fn on-event-fn]
  (let [ac (get-ctx)]
    (.resume ac)
    (ensure-track-node! ac track-name)
    (arm-transitions! track-name ac)    ; ← add this line
    (swap! scheduler-state update :tracks assoc track-name pattern)
    (ensure-running! ac on-beat-fn on-event-fn)))
```

### 3. Curve interpolation — `app/src/repulse/audio.cljs`

Add a pure interpolation helper (mirrors the Rust implementation below):

```clojure
(defn- tween-value
  "Interpolate between start and end at normalised time t (clamped 0–1).
   :linear — constant rate
   :exp    — t² — slow start, fast end (good for volume fades)
   :sine   — half-cosine S-curve — slow at both ends, fast in the middle"
  [curve start end t]
  (let [t' (js/Math.max 0.0 (js/Math.min 1.0 t))
        k  (case curve
             :linear t'
             :exp    (* t' t')
             :sine   (* 0.5 (- 1.0 (js/Math.cos (* js/Math.PI t'))))
             t')]    ; unknown curve → treat as linear
    (+ start (* (- end start) k))))
```

Add `resolve-param` — no-op for plain values, interpolation for tween descriptors:

```clojure
(defn- resolve-param
  "If v is a tween descriptor, return the interpolated float at audio time t.
   Otherwise return v unchanged — this is a no-op for all non-tween values."
  [v t track-name cycle-dur]
  (if (and (map? v) (= (:type v) :tween))
    (let [started-at (get-in @scheduler-state [:transition-starts track-name] t)
          elapsed    (- t started-at)
          duration-s (* (:duration-bars v) cycle-dur)
          t-norm     (if (pos? duration-s) (/ elapsed duration-s) 1.0)]
      (tween-value (:curve v) (:start v) (:end v) t-norm))
    v))
```

### 4. Resolve tweens in `schedule-cycle!` — `app/src/repulse/audio.cljs`

In `schedule-cycle!`, after computing `t` for each event and before calling `play-event`,
resolve any tween descriptors in the event value:

```clojure
;; existing:
(let [... t ... dur ...]
  (when (> t (.-currentTime ac))
    ;; NEW: resolve tween descriptors to floats at the event's scheduled time
    (let [raw   (:value ev)
          value (if (map? raw)
                  (reduce-kv
                    (fn [m k v] (assoc m k (resolve-param v t track-name cycle-dur)))
                    raw raw)
                  raw)]
      (play-event ac t value track-name)
      ;; … rest of the existing when block (MIDI, on-fx-event, on-event, on-beat) …
      )))
```

`resolve-param` is a pure no-op for non-tween values (returns them unchanged), so
events with static parameters are unaffected. The `map?` guard means keyword values
like `:bd` skip the reduce entirely.

### 5. Rust Transition struct — `packages/audio/src/lib.rs`

Add the canonical algorithm definition. The struct is not wired into `AudioEngine` in
T1 (CLJS handles interpolation), but it:
- Defines the reference implementation for the three curves
- Establishes the extensible match-arm pattern for future curves
- Enables unit tests that verify curve correctness independent of CLJS

```rust
#[derive(Clone, Copy, Debug)]
pub enum CurveType { Linear, Exp, Sine }

/// A one-shot parameter transition from start_value to end_value.
/// Zero heap allocation — all state is inline on the stack.
#[derive(Clone, Copy, Debug)]
pub struct Transition {
    pub start_value:      f32,
    pub end_value:        f32,
    pub duration_samples: u64,
    pub elapsed_samples:  u64,
    pub curve:            CurveType,
}

impl Transition {
    pub fn new(start: f32, end: f32, duration_samples: u64, curve: CurveType) -> Self {
        Self { start_value: start, end_value: end,
               duration_samples, elapsed_samples: 0, curve }
    }

    /// Advance by one sample and return the interpolated value.
    /// Clamps at end_value after duration — never overshoots, never resets.
    pub fn tick(&mut self) -> f32 {
        self.elapsed_samples = self.elapsed_samples.saturating_add(1);
        let t = if self.duration_samples == 0 { 1.0_f32 }
                else {
                    (self.elapsed_samples as f32 / self.duration_samples as f32).min(1.0)
                };
        let range = self.end_value - self.start_value;
        self.start_value + range * match self.curve {
            CurveType::Linear => t,
            CurveType::Exp    => t * t,
            CurveType::Sine   => {
                0.5 * (1.0 - f32::cos(std::f32::consts::PI * t))
            }
            // Adding a new curve: add one arm here. Nothing else changes.
        }
    }

    pub fn current_value(&self) -> f32 {
        // Read current value without advancing (for inspection/testing).
        let t = if self.duration_samples == 0 { 1.0_f32 }
                else {
                    (self.elapsed_samples as f32 / self.duration_samples as f32).min(1.0)
                };
        let range = self.end_value - self.start_value;
        self.start_value + range * match self.curve {
            CurveType::Linear => t,
            CurveType::Exp    => t * t,
            CurveType::Sine   => 0.5 * (1.0 - f32::cos(std::f32::consts::PI * t)),
        }
    }

    pub fn is_done(&self) -> bool {
        self.elapsed_samples >= self.duration_samples
    }
}
```

Add unit tests at the bottom of `lib.rs` (inside `#[cfg(test)]`):

```rust
#[cfg(test)]
mod transition_tests {
    use super::{Transition, CurveType};

    #[test]
    fn linear_midpoint() {
        let mut tr = Transition::new(0.0, 1.0, 100, CurveType::Linear);
        for _ in 0..50 { tr.tick(); }
        assert!((tr.current_value() - 0.5).abs() < 1e-4,
                "linear at t=0.5 should be 0.5, got {}", tr.current_value());
    }

    #[test]
    fn exp_midpoint_below_linear() {
        let mut tr = Transition::new(0.0, 1.0, 100, CurveType::Exp);
        for _ in 0..50 { tr.tick(); }
        // t² at t=0.5 is 0.25 — slower than linear
        assert!(tr.current_value() < 0.5,
                "exp at t=0.5 should be < 0.5, got {}", tr.current_value());
    }

    #[test]
    fn sine_midpoint_near_half() {
        let mut tr = Transition::new(0.0, 1.0, 100, CurveType::Sine);
        for _ in 0..50 { tr.tick(); }
        // sine S-curve is symmetric: midpoint = 0.5
        assert!((tr.current_value() - 0.5).abs() < 1e-4,
                "sine at t=0.5 should be ≈0.5, got {}", tr.current_value());
    }

    #[test]
    fn clamps_at_end_value() {
        let mut tr = Transition::new(0.0, 1.0, 100, CurveType::Linear);
        for _ in 0..200 { tr.tick(); }   // 2× the duration
        assert!((tr.current_value() - 1.0).abs() < 1e-6,
                "should hold at end value, got {}", tr.current_value());
        assert!(tr.is_done());
    }

    #[test]
    fn start_value_before_any_ticks() {
        let tr = Transition::new(0.3, 0.9, 100, CurveType::Linear);
        assert!((tr.current_value() - 0.3).abs() < 1e-6);
    }

    #[test]
    fn no_heap_allocation_in_tick() {
        // Regression guard — Transition is Copy, tick takes &mut self.
        // If this compiles without a Box/Vec, the constraint is satisfied.
        let mut tr = Transition::new(0.0, 1.0, 48000, CurveType::Sine);
        let _ = tr.tick();
    }
}
```

### 6. Grammar, completions, hover docs

`app/src/repulse/lisp-lang/repulse-lisp.grammar` — add to `BuiltinName`:

```
BuiltinName { ... | "tween" }
```

Run `npm run gen:grammar` after editing.

`app/src/repulse/lisp-lang/completions.js`:

```javascript
{ label: "tween", type: "function", detail: "smooth parameter transition over bars" },
```

Hover doc (wherever the existing parameter hover docs live — search for `"amp"` in the
hover docs file):

```javascript
"tween": `(tween curve start end bars)
Interpolate a parameter from start to end over the given duration in bars.
Every note that fires during the transition picks up the value at its scheduled time.
After the duration expires, the end value is held indefinitely.
Re-evaluating the code restarts the transition from the current moment.
  curve  :linear  — constant rate of change
         :exp     — slow start, fast end (good for volume fades)
         :sine    — S-curve, slow at both ends, fast in the middle
  start  — initial value
  end    — final value (held after transition completes)
  bars   — duration in bars/cycles (must be > 0)
Examples:
  (amp (tween :linear 0.0 1.0 2))   ; fade in over 2 bars
  (pan (tween :sine -1 1 4))        ; S-curve pan sweep over 4 bars
  (amp (tween :exp 1.0 0.0 8))      ; exponential fade-out over 8 bars`,
```

### 7. `docs/USAGE.md`

Add a **Transitions** section after the "Per-event parameters" section and before
"Effects":

```markdown
## Transitions

A **transition** changes a parameter smoothly from one value to another over musical
time. Wrap any numeric value with `tween`:

    (tween curve start end bars)

Every note that fires during the transition picks up the interpolated value at its exact
scheduled time. When the transition completes, the end value is held. Re-evaluating
the code restarts the transition.

| Curve      | Behaviour                                           |
|------------|-----------------------------------------------------|
| `:linear`  | Constant rate — equal change per unit time          |
| `:exp`     | Slow start, fast end — good for volume fades         |
| `:sine`    | S-curve — slow at both ends, fast in the middle     |

### Examples

    ;; Amp fade-in over 2 bars
    (->> (seq :c4 :e4 :g4 :c5)
         (synth :saw)
         (amp (tween :linear 0.0 1.0 2)))

    ;; Exponential fade-out over 8 bars
    (->> (fast 2 (seq :hh))
         (amp (tween :exp 1.0 0.0 8)))

    ;; S-curve pan sweep over half a bar
    (->> (fast 4 (seq :sd))
         (pan (tween :sine -1.0 1.0 0.5)))

    ;; Different transitions per voice in a stack
    (stack
      (->> (seq :bd :_ :bd :_) (amp (tween :linear 0.2 1.0 2)))
      (->> (fast 4 (seq :hh))  (pan (tween :sine -0.8 0.8 1))))

### Transitions vs. envelopes

`attack`, `decay`, and `release` shape the amplitude of **individual notes** over
milliseconds. `tween` changes **any parameter** across multiple bars. They operate at
different timescales and complement each other:

    ;; A slow-attack envelope on each note, AND a fade-in transition across all notes
    (->> (seq :c4 :e4 :g4)
         (synth :saw)
         (attack 0.3)               ; each note swells in over 300ms
         (amp (tween :linear 0 1 4))) ; and the whole sequence fades in over 4 bars
```

---

## Files to change

| File | Change |
|------|--------|
| `packages/lisp/src/repulse/lisp/eval.cljs` | Add `"tween"` to `make-env` |
| `app/src/repulse/audio.cljs` | Add `tween-value`, `arm-transitions!`, `resolve-param`; extend `scheduler-state`; update `play-track!` and `schedule-cycle!` |
| `packages/audio/src/lib.rs` | Add `CurveType`, `Transition` struct, unit tests |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `"tween"` to `BuiltinName` |
| `app/src/repulse/lisp-lang/completions.js` | Add `tween` entry |
| hover docs | Add `"tween"` hover doc |
| `docs/USAGE.md` | Add Transitions section |
| `CLAUDE.md` | Mark T1 as ✓ delivered |

Run `npm run gen:grammar` after editing the grammar.

No changes to `packages/core/` (pattern algebra is unaware of tweens).
No changes to `params.cljs` (existing `apply-param` already stores arbitrary values).
No changes to `fx.cljs`, `synth.cljs`, `worklet.js`, or any effect plugin.
No changes to the WASM `AudioEngine` message protocol in T1.

---

## Definition of done

- [ ] `(->> (seq :c4 :e4 :g4) (synth :saw) (amp (tween :linear 0.0 1.0 2)))` —
      notes audibly fade in over 2 bars, starting near-silent
- [ ] `(amp (tween :exp 1.0 0.0 4))` — volume stays high early, then drops quickly
      (audibly different curve from `:linear`)
- [ ] `(pan (tween :sine -1.0 1.0 1))` — pan sweeps with S-curve (slow at start
      and end, faster through centre)
- [ ] After duration expires, value holds at `end` — no reset, no loop, no jump
- [ ] Re-evaluating code with a new tween immediately restarts the transition from
      the current moment (old tween does not persist)
- [ ] Re-evaluating with a static value `(amp 0.8)` cancels any prior tween for
      that track (static value applied immediately)
- [ ] `(tween :bogus 0 1 1)` → error: `"Unknown curve type :bogus. Available: :linear, :exp, :sine"`
- [ ] `(tween :linear 0 1 0)` → error: `"Transition duration must be > 0"`
- [ ] `(tween :linear 0 1 -2)` → error: `"Transition duration must be > 0"`
- [ ] `(tween :linear "foo" 1 1)` → evaluator error with a readable message, no crash
- [ ] Patterns without tweens play identically to before — no regression
- [ ] Rust unit tests pass: `cargo test` in `packages/audio/`
- [ ] All core tests pass: `npm run test:core`
- [ ] Grammar change committed with regenerated `parser.js`
- [ ] No console errors during playback

---

## What NOT to do in this phase

- **No LFO.** `tween` goes A → B once. Looping/cycling is a separate future feature.
- **No modulation routing.** One parameter cannot drive another.
- **No audio-rate modulation.** `tween` is control-rate — one value per triggered note,
  not one value per audio sample.
- **No `cutoff` parameter.** Neither WASM nor the JS path gains a per-voice filter in
  this phase. `cutoff` is T2.
- **No changes to `params.cljs`.** `apply-param` already works as-is for tween
  descriptors. Do not add special tween handling there.
- **No changes to the WASM message protocol.** The Rust `Transition` struct is
  infrastructure; the AudioWorklet is not taught about transitions in T1.
- **Do not replace** `attack`/`decay`/`release` with `tween`. They solve different
  problems at different timescales.
