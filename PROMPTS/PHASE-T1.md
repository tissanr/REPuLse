# Phase T1 — Parameter Transitions

## Goal

Add a `tween` built-in that makes any numeric parameter change smoothly over musical
time. The pattern engine sends a **single message** to the WASM audio layer: "parameter
X transitions from value A to value B over duration D using curve C." The WASM engine
handles per-sample interpolation — there is no polling from ClojureScript.

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

Re-evaluating the code restarts the transition. Patterns without tweens are unaffected.

---

## Background

### How parameters work today

`packages/core/src/repulse/params.cljs` defines `apply-param`, used by `amp`, `pan`,
`attack`, `decay`, `rate`, etc.:

```clojure
(defn- apply-param [kw param-val-or-pat note-pat]
  (core/combine (fn [pv nv] (assoc (to-map nv) kw pv))
                (if (pat? param-val-or-pat) param-val-or-pat (core/pure param-val-or-pat))
                note-pat'))
```

`pv` is stored directly into each event under `kw`. The scheduler extracts it as a
scalar: `(float (:amp value 1.0))`. For tweens, `pv` will be a tagged map
`{:type :tween ...}` — `apply-param` stores it without modification, no change needed.

### The WASM audio loop

`process_block` in `packages/audio/src/lib.rs` generates stereo interleaved PCM:

```rust
for i in 0..n {
    let mut l = 0.0f32;
    let mut r = 0.0f32;
    for av in self.voices.iter_mut() {
        let s = av.voice.tick(sr);
        // constant-power pan applied per voice
        l += s * angle.cos();
        r += s * angle.sin();
    }
    buf[i * 2]     = l.clamp(-1.0, 1.0);
    buf[i * 2 + 1] = r.clamp(-1.0, 1.0);
}
```

Transitions are applied **after the voice mix**, as a per-sample multiplier (amp) or
stereo balance (pan), before the final `clamp`.

### Worklet message flow today

`worklet.js` handles three message types: `init`, `trigger`, `trigger_v2`, `stop`.
This phase adds a fourth: `transition`.

### Scope limitation

The WASM engine renders all voices in one mixed output. A `tween` on `amp` or `pan`
therefore applies to the **entire WASM output**, not to individual tracks. Per-track WASM
isolation requires routing changes that are scoped to T2. In practice, this limitation
is only visible when multiple tracks simultaneously have conflicting tweens — the common
single-pattern fade-in/fade-out works exactly as expected.

The **JS synthesis fallback** (when AudioWorklet is unavailable) uses control-rate
interpolation: the scheduler computes the tween value per event using elapsed time. Less
sample-accurate, but the fallback is rarely hit in production.

---

## Implementation

### 1. `tween` built-in — `packages/lisp/src/repulse/lisp/eval.cljs`

Add to `make-env` alongside `amp`, `pan`, etc.:

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

`tween` returns a plain data map — a **tween descriptor**. It is not a pattern. It is
stored as a parameter value inside event maps (e.g. `{:note :c4 :amp {:type :tween ...}}`).
The scheduler detects and dispatches it; `apply-param` is unmodified.

Error cases:
- Unknown curve → `"Unknown curve type :bogus. Available: :linear, :exp, :sine"`
- `dur ≤ 0` → `"Transition duration must be > 0"`
- Non-numeric `start`/`end` → `->num` throws naturally; let it propagate

### 2. `Transition` struct and `CurveType` enum — `packages/audio/src/lib.rs`

Add before `AudioEngine`:

```rust
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum CurveType { Linear, Exp, Sine }

impl CurveType {
    fn from_str(s: &str) -> Self {
        match s {
            "exp"  => CurveType::Exp,
            "sine" => CurveType::Sine,
            _      => CurveType::Linear,  // "linear" and unknown → linear
        }
    }
}

/// One-shot parameter transition. Zero heap allocation — all state is inline.
#[derive(Clone, Copy, Debug)]
pub struct Transition {
    start_value:      f32,
    end_value:        f32,
    duration_samples: u64,
    elapsed_samples:  u64,
    curve:            CurveType,
}

impl Transition {
    fn new(start: f32, end: f32, duration_samples: u64, curve: CurveType) -> Self {
        Transition { start_value: start, end_value: end,
                     duration_samples, elapsed_samples: 0, curve }
    }

    /// Advance one sample, return interpolated value. Clamps at end — never resets.
    fn tick(&mut self) -> f32 {
        self.elapsed_samples = self.elapsed_samples.saturating_add(1);
        let t = if self.duration_samples == 0 { 1.0_f32 }
                else {
                    (self.elapsed_samples as f32 / self.duration_samples as f32).min(1.0)
                };
        self.interpolate(t)
    }

    fn interpolate(&self, t: f32) -> f32 {
        let k = match self.curve {
            CurveType::Linear => t,
            CurveType::Exp    => t * t,
            CurveType::Sine   => 0.5 * (1.0 - f32::cos(std::f32::consts::PI * t)),
            // Adding a new curve: add one arm here. Nothing else changes.
        };
        self.start_value + (self.end_value - self.start_value) * k
    }
}
```

### 3. Wire `Transition` into `AudioEngine` — `packages/audio/src/lib.rs`

Extend the `AudioEngine` struct:

```rust
pub struct AudioEngine {
    sample_rate:      f32,
    voices:           Vec<ActiveVoice>,
    pending:          Vec<Pending>,
    noise_seed:       u32,
    amp_transition:   Option<Transition>,   // ← new
    pan_transition:   Option<Transition>,   // ← new
}
```

Update `AudioEngine::new`:

```rust
pub fn new(sample_rate: f32) -> AudioEngine {
    log(&format!("[REPuLse WASM] PCM engine ready (sr={})", sample_rate));
    AudioEngine {
        sample_rate, voices: Vec::new(), pending: Vec::new(),
        noise_seed: 0xDEAD_BEEF,
        amp_transition: None,
        pan_transition: None,
    }
}
```

Add a new public method (wasm_bindgen):

```rust
/// Start a parameter transition. Replaces any existing transition for that param.
/// param: "amp" or "pan"
/// duration_samples: pre-computed on the JS side from bars * BPM * sample_rate
pub fn start_transition(
    &mut self, param: &str, start: f32, end: f32,
    duration_samples: u64, curve: &str,
) {
    let tr = Transition::new(start, end, duration_samples, CurveType::from_str(curve));
    match param {
        "amp" => self.amp_transition = Some(tr),
        "pan" => self.pan_transition = Some(tr),
        _     => warn(&format!("[REPuLse WASM] unknown transition param: {}", param)),
    }
}
```

Also add `clear_transitions`:

```rust
/// Clear all active transitions (called by stop_all).
pub fn clear_transitions(&mut self) {
    self.amp_transition = None;
    self.pan_transition = None;
}
```

Call `self.clear_transitions()` inside `stop_all`.

### 4. Apply transitions in `process_block` — `packages/audio/src/lib.rs`

Replace the inner sample loop with:

```rust
for i in 0..n {
    let mut l = 0.0f32;
    let mut r = 0.0f32;
    for av in self.voices.iter_mut() {
        let s = av.voice.tick(sr);
        let angle = (av.pan + 1.0) / 2.0 * std::f32::consts::FRAC_PI_2;
        l += s * angle.cos();
        r += s * angle.sin();
    }

    // Apply amp transition (global post-mix multiplier)
    let amp_scale = if let Some(ref mut tr) = self.amp_transition {
        tr.tick()
    } else {
        1.0
    };

    // Apply pan transition (global stereo balance, additive on top of per-voice pan)
    // pan_val in [-1, 1]: -1 = full left, 0 = centre, 1 = full right
    if let Some(ref mut tr) = self.pan_transition {
        let pan_val = tr.tick().clamp(-1.0, 1.0);
        let bal_angle = (pan_val + 1.0) / 2.0 * std::f32::consts::FRAC_PI_2;
        let (new_l, new_r) = (
            (l + r) * bal_angle.cos() * amp_scale,
            (l + r) * bal_angle.sin() * amp_scale,
        );
        buf[i * 2]     = new_l.clamp(-1.0, 1.0);
        buf[i * 2 + 1] = new_r.clamp(-1.0, 1.0);
    } else {
        buf[i * 2]     = (l * amp_scale).clamp(-1.0, 1.0);
        buf[i * 2 + 1] = (r * amp_scale).clamp(-1.0, 1.0);
    }
}
```

### 5. Worklet message handler — `app/public/worklet.js`

Add a new branch in `_onMessage`:

```javascript
} else if (msg.type === 'transition') {
  if (this.engine) {
    this.engine.start_transition(
      msg.param,
      msg.start,
      msg.end,
      BigInt(msg.duration_samples),  // u64 in Rust; pass as BigInt from JS
      msg.curve
    );
  }
}
```

Note: `duration_samples` is `u64` in Rust. wasm-bindgen maps `u64` to `BigInt` in JS.
Pass `BigInt(Math.round(msg.duration_samples))` to avoid silent truncation.

### 6. Transition dispatch — `app/src/repulse/audio.cljs`

#### 6a. Tween predicate

```clojure
(defn- tween? [v]
  (and (map? v) (= (:type v) :tween)))
```

#### 6b. Extend `scheduler-state`

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
         :tween-armed       {}}))   ; ← new: track-name → #{param-keys already sent}
```

#### 6c. `arm-transitions!`

Called from `play-track!` every time a track is (re-)evaluated. Queries the pattern for
the first cycle, finds tween descriptors, and sends one `transition` message per param.
Re-evaluation clears the armed set so the transition is restarted.

```clojure
(defn- arm-transitions!
  "Detect tween descriptors in the first cycle of pattern and send transition messages
   to the WASM worklet. Idempotent within one play-track! call — sends each param once."
  [track-name pattern ac]
  (let [cycle-dur (:cycle-dur @scheduler-state)
        sr        (.-sampleRate ac)
        sp        {:start [0 1] :end [1 1]}
        evs       (try (core/query pattern sp) (catch :default _ []))
        ;; Collect unique tween descriptors per param key across all events
        tweens    (into {}
                    (for [ev   evs
                          :let [v (:value ev)]
                          :when (map? v)
                          [k pv] v
                          :when (tween? pv)]
                      [k pv]))]
    ;; Clear previously armed set so re-eval restarts the transition
    (swap! scheduler-state update :tween-armed assoc track-name #{})
    (doseq [[k pv] tweens]
      (let [dur-samples (Math/round (* (:duration-bars pv) cycle-dur sr))]
        ;; WASM path: single message, engine handles per-sample interpolation
        (when (and @worklet-ready? @worklet-node)
          (.. @worklet-node -port
              (postMessage #js {:type             "transition"
                                :param            (name k)
                                :start            (float (:start pv))
                                :end              (float (:end pv))
                                :duration_samples dur-samples
                                :curve            (name (:curve pv))})))
        ;; Record as armed so schedule-cycle! sends neutral values for this param
        (swap! scheduler-state update-in [:tween-armed track-name] (fnil conj #{}) k)))))
```

#### 6d. Update `play-track!`

```clojure
(defn play-track! [track-name pattern on-beat-fn on-event-fn]
  (let [ac (get-ctx)]
    (.resume ac)
    (ensure-track-node! ac track-name)
    (arm-transitions! track-name pattern ac)   ; ← add this line
    (swap! scheduler-state update :tracks assoc track-name pattern)
    (ensure-running! ac on-beat-fn on-event-fn)))
```

#### 6e. Neutral values in `schedule-cycle!`

In `schedule-cycle!`, when building the event value for `play-event`, replace any tween
descriptor with a neutral value — the WASM engine applies the actual ramp:

```clojure
(let [armed  (get-in state [:tween-armed track-name] #{})
      raw    (:value ev)
      ;; Replace tween descriptors with neutral values; WASM handles the ramp.
      ;; For the JS synthesis fallback (no worklet), fall back to the start value.
      value  (if (and (map? raw) (seq armed))
               (reduce (fn [m k]
                          (if (tween? (get m k))
                            (assoc m k (if @worklet-ready?
                                         (case k
                                           :amp 1.0
                                           :pan 0.0
                                           (get-in m [k :start]))
                                         (get-in m [k :start])))
                            m))
                       raw armed)
               raw)]
  (play-event ac t value track-name)
  ;; … rest of when block unchanged …
  )
```

The **JS synthesis fallback** uses `:start` as the static value. Notes that fire during
the would-be transition use the start value throughout — not smooth, but the fallback
path is rarely hit in production and avoids the WASM-only architecture complexity.

### 7. Rust unit tests — `packages/audio/src/lib.rs`

Add inside `#[cfg(test)]`:

```rust
#[cfg(test)]
mod transition_tests {
    use super::{Transition, CurveType};

    #[test]
    fn linear_values_at_quartiles() {
        let mut tr = Transition::new(0.0, 1.0, 100, CurveType::Linear);
        for _ in 0..25 { tr.tick(); }
        assert!((tr.interpolate(0.25) - 0.25).abs() < 1e-5);
        for _ in 0..25 { tr.tick(); }
        assert!((tr.interpolate(0.5) - 0.5).abs() < 1e-5);
        for _ in 0..25 { tr.tick(); }
        assert!((tr.interpolate(0.75) - 0.75).abs() < 1e-5);
    }

    #[test]
    fn exp_midpoint_below_linear() {
        let tr = Transition::new(0.0, 1.0, 100, CurveType::Exp);
        // t² at t=0.5 is 0.25 — slower start than linear
        assert!(tr.interpolate(0.5) < 0.5,
                "exp at t=0.5 should be < 0.5, got {}", tr.interpolate(0.5));
    }

    #[test]
    fn sine_midpoint_at_half() {
        let tr = Transition::new(0.0, 1.0, 100, CurveType::Sine);
        // S-curve is symmetric around 0.5
        assert!((tr.interpolate(0.5) - 0.5).abs() < 1e-5,
                "sine at t=0.5 should be ≈0.5, got {}", tr.interpolate(0.5));
    }

    #[test]
    fn clamps_at_end_value_no_overshoot() {
        let mut tr = Transition::new(0.0, 1.0, 100, CurveType::Linear);
        for _ in 0..200 { tr.tick(); }   // 2× the duration
        assert!((tr.tick() - 1.0).abs() < 1e-6, "should hold at end value");
    }

    #[test]
    fn start_value_at_t_zero() {
        let tr = Transition::new(0.3, 0.9, 100, CurveType::Linear);
        assert!((tr.interpolate(0.0) - 0.3).abs() < 1e-6);
    }

    #[test]
    fn end_value_at_t_one() {
        let tr = Transition::new(0.3, 0.9, 100, CurveType::Sine);
        assert!((tr.interpolate(1.0) - 0.9).abs() < 1e-6);
    }

    #[test]
    fn zero_duration_clamps_to_end() {
        let mut tr = Transition::new(0.0, 1.0, 0, CurveType::Linear);
        assert!((tr.tick() - 1.0).abs() < 1e-6);
    }

    #[test]
    fn amp_transition_applied_in_engine() {
        // Smoke test: start_transition("amp") sets amp_transition on the engine.
        // Full integration is verified in the browser.
        use super::AudioEngine;
        let mut eng = AudioEngine::new(44100.0);
        eng.start_transition("amp", 0.0, 1.0, 44100, "linear");
        assert!(eng.amp_transition.is_some());
        eng.clear_transitions();
        assert!(eng.amp_transition.is_none());
    }
}
```

### 8. Grammar, completions, hover docs

`app/src/repulse/lisp-lang/repulse-lisp.grammar`:

```
BuiltinName { ... | "tween" }
```

Run `npm run gen:grammar` after editing.

`app/src/repulse/lisp-lang/completions.js`:

```javascript
{ label: "tween", type: "function", detail: "smooth parameter transition over bars" },
```

Hover doc (search for `"amp"` in the hover docs file to find the right location):

```javascript
"tween": `(tween curve start end bars)
Interpolate a parameter from start to end over the given duration in bars.
The audio engine handles per-sample interpolation — a single message is sent;
no polling occurs. After the transition completes, the end value is held.
Re-evaluating code restarts the transition from the current moment.
  curve   :linear  constant rate of change
          :exp     slow start, fast end (good for volume fades)
          :sine    S-curve: slow at both ends, fast in the middle
  start   initial value
  end     final value, held after transition completes
  bars    duration in bars (must be > 0)
Examples:
  (amp (tween :linear 0.0 1.0 2))    ; fade in over 2 bars
  (amp (tween :exp    1.0 0.0 8))    ; exponential fade-out over 8 bars
  (pan (tween :sine  -1.0 1.0 4))    ; S-curve pan sweep over 4 bars`,
```

### 9. `docs/USAGE.md`

Add a **Transitions** section after the "Per-event parameters" section and before
"Effects":

````markdown
## Transitions

A **transition** changes a parameter smoothly from one value to another over musical
time. The audio engine handles per-sample interpolation — there is no polling:

    (tween curve start end bars)

Every note that fires during the transition picks up the interpolated value at its
scheduled time. When the transition completes the end value is held indefinitely.
Re-evaluating the code restarts the transition.

| Curve      | Behaviour                                                  |
|------------|------------------------------------------------------------|
| `:linear`  | Constant rate — equal change per unit time                 |
| `:exp`     | Slow start, fast end — good for volume fades               |
| `:sine`    | S-curve — slow at both ends, fast in the middle            |

### Examples

```lisp
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
```

### Transitions vs. envelopes

`attack`, `decay`, and `release` shape the amplitude of **individual notes** over
milliseconds. `tween` changes **any parameter** across multiple bars. They operate at
different timescales and complement each other:

```lisp
;; A slow attack on each note, AND a fade-in across all notes
(->> (seq :c4 :e4 :g4)
     (synth :saw)
     (attack 0.3)                     ; each note swells in over 300ms
     (amp (tween :linear 0.0 1.0 4))) ; overall sequence fades in over 4 bars
```
````

---

## Files to change

| File | Change |
|------|--------|
| `packages/audio/src/lib.rs` | Add `CurveType`, `Transition`; extend `AudioEngine` with `amp_transition`, `pan_transition`; add `start_transition`, `clear_transitions`; apply per-sample in `process_block`; unit tests |
| `app/public/worklet.js` | Handle `{type: "transition"}` message; call `engine.start_transition(...)` |
| `packages/lisp/src/repulse/lisp/eval.cljs` | Add `"tween"` to `make-env` |
| `app/src/repulse/audio.cljs` | Add `tween?`, `arm-transitions!`; extend `scheduler-state` with `:tween-armed`; update `play-track!`; update `schedule-cycle!` to pass neutral values for armed tweens |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `"tween"` to `BuiltinName` |
| `app/src/repulse/lisp-lang/completions.js` | Add `tween` entry |
| hover docs | Add `"tween"` hover doc |
| `docs/USAGE.md` | Add Transitions section |
| `CLAUDE.md` | Mark T1 as ✓ delivered |

Run `npm run gen:grammar` after editing the grammar.
Run `npm run build:wasm` after editing `lib.rs`.

No changes to `packages/core/`, `params.cljs`, `fx.cljs`, `synth.cljs`, or any
effect plugin.

---

## Definition of done

- [ ] `(->> (seq :c4 :e4 :g4) (synth :saw) (amp (tween :linear 0.0 1.0 2)))` —
      notes audibly fade in over 2 bars, starting near-silent
- [ ] `(amp (tween :exp 1.0 0.0 4))` — volume stays high early then drops quickly
      (audibly different from `:linear` curve)
- [ ] `(pan (tween :sine -1.0 1.0 1))` — pan sweeps smoothly with S-curve character
- [ ] After duration expires, value holds at `end` — no reset, no loop, no jump
- [ ] Re-evaluating with a new tween immediately restarts the transition (old tween
      replaced, no stacking)
- [ ] Re-evaluating with a static value `(amp 0.8)` cancels any prior tween for that
      track (no ghost fade)
- [ ] WASM unit tests pass: `cargo test` in `packages/audio/`
- [ ] Browser console shows no errors during a 16-bar session with active transitions
- [ ] `(tween :bogus 0 1 1)` → evaluator error:
      `"Unknown curve type :bogus. Available: :linear, :exp, :sine"`
- [ ] `(tween :linear 0 1 0)` → evaluator error: `"Transition duration must be > 0"`
- [ ] `(tween :linear 0 1 -1)` → evaluator error: `"Transition duration must be > 0"`
- [ ] Patterns without tweens play identically to before — no regression
- [ ] All core tests pass: `npm run test:core`
- [ ] Grammar change committed with regenerated `parser.js`

---

## What NOT to do in this phase

- **No LFO.** `tween` goes A → B once. Looping/cycling is a separate feature.
- **No modulation routing.** One parameter cannot drive another.
- **No per-track WASM isolation.** The WASM engine applies amp/pan transitions globally
  across all WASM output. Per-track routing in WASM is T2.
- **No `cutoff` parameter.** The WASM engine has no per-voice filter in T1; cutoff
  is T2.
- **No changes to `params.cljs`.** `apply-param` already stores tween descriptors
  as-is. Do not add special tween handling there.
- **Do not replace** `attack`/`decay`/`release` with `tween`. They solve different
  problems at different timescales.
