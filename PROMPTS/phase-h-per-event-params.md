# Phase H — Per-Event Parameters: amp, attack, decay, pan

## Goal

Make every sound event expressive by attaching synthesis parameters — amplitude, envelope
shape, and stereo position — directly to pattern values. Parameters are themselves patterns,
so they compose freely with `seq`, `stack`, `def`, and every other combinator.

```lisp
;; Before — all notes play at the same fixed volume and envelope:
(seq :c4 :e4 :g4)

;; After — per-note amplitude:
(amp 0.8 (seq :c4 :e4 :g4))

;; After — amplitude is itself a pattern (accent every first beat):
(amp (seq 0.9 0.5 0.5 0.5) (seq :c4 :e4 :g4 :c5))

;; After — chain parameters with ->>:
(->> (seq :c4 :e4 :g4)
     (amp 0.7)
     (attack 0.05)
     (decay 0.4))

;; After — named voice presets via def:
(def stab (comp (amp 0.9) (attack 0.005) (decay 0.1)))
(stab (chord :minor7 :a3))

;; After — independent amp patterns per voice in a stack:
(stack
  (->> (seq :bd :_ :bd :_) (amp 0.95))
  (->> (scale :minor :c4 (seq 0 2 4 7)) (amp 0.6) (attack 0.02) (decay 0.5))
  (->> (chord :minor :c4) (amp 0.3) (attack 0.1)))
```

---

## Background

### Current event value model

Every event today carries a single `:value`:

```
:_              → silence
keyword :bd     → drum sample / WASM synth lookup
number 440.0    → Hz frequency → WASM Tone voice
map {:bank …}   → sample bank selection (Phase 8)
```

There is no way to attach amplitude, envelope, or panning to a specific event. The WASM
`Voice::Tone` and drum voices all use hardcoded gain/decay values.

### Current WASM trigger interface

```rust
pub fn trigger(&mut self, value: &str, time: f64)
```

Takes only a sound name and scheduled time. `activate` dispatches on the name, constructing
voices with fixed parameters:

```rust
Voice::Tone { gain: 0.5, gain_decay: decay_rate(0.3, sr), … }
```

### Current `core.cljs` combinators

`fmap` transforms each event's value in one pattern. There is no combinator that combines
values from *two* patterns at overlapping time positions — this is the missing piece.

---

## Design

### 1. Map values carry parameters

When a parameter function is applied, the event value is upgraded to a map:

```
:c4           →  {:note :c4 :amp 0.8}       ; after (amp 0.8 …)
440.0         →  {:note 440.0 :amp 0.8}
:bd           →  {:note :bd :amp 0.95}
{:bank …}     →  {:bank … :amp 0.7}         ; bank maps are extended
```

If the value is already a map the parameter is `assoc`-ed in. The audio bridge checks:
if value is a map, extract `:note` as the sound identifier and route all other keys to WASM.

### 2. `combine` — the new core primitive

`combine` is applicative `liftA2` over patterns. For every pair of events from two patterns
whose `:part` spans overlap, it produces a new event at the intersection with the combined
value. This is what lets `(amp (seq 0.9 0.5) melody)` pair each amp value with the note
events at the same time position.

```clojure
(combine f pat-a pat-b)
;; → for each (ea, eb) where (:part ea) and (:part eb) overlap:
;;      {:value (f (:value ea) (:value eb))
;;       :whole (:whole eb)
;;       :part  (span-intersect (:part ea) (:part eb))}
```

When one pattern is `(pure scalar)`, every note event pairs with exactly the one scalar
value — simple, predictable. When both are sequences of different lengths, the behaviour
is Tidal-style polyrhythm: each note event gets a value from whichever amp-event it falls
within.

### 3. Curried parameter functions

Each parameter function accepts either one or two arguments:

```clojure
(amp 0.8 pat)   ; two-arg form — apply immediately
(amp 0.8)       ; one-arg form — returns a (pat → pat) transformer
```

This makes named presets and `comp` chains natural:

```lisp
(def soft (amp 0.4))
(def punchy (comp (amp 0.9) (attack 0.005) (decay 0.08)))
```

### 4. `->>` thread-last macro

Consistent with REPuLse's existing convention of putting the pattern last in all
combinators (`fast n pat`, `slow n pat`, `every n f pat`), the thread-last form `->>` is
the natural chaining operator. Each step passes its result as the **last** argument of the
next form:

```lisp
(->> (seq :c4 :e4 :g4)
     (amp 0.7)           ; ≡ (amp 0.7 (seq :c4 :e4 :g4))
     (attack 0.02)       ; ≡ (attack 0.02 (amp 0.7 …))
     (decay 0.4))        ; ≡ (decay 0.4 (attack 0.02 …))
```

`->>` is a special form in the evaluator — it's syntax sugar and cannot be expressed as a
regular function.

---

## Implementation

### 1. `packages/core/src/repulse/core.cljs` — add `combine`

Add after `fmap`:

```clojure
(defn combine
  "Applicative liftA2: pair events from pat-a and pat-b that overlap in time.
   For each overlapping (ea, eb) pair, produce an event with value (f va vb)
   at the intersection of their :part spans. Uses eb's :whole."
  [f pat-a pat-b]
  (pattern
   (fn [sp]
     (let [evs-a (query pat-a sp)
           evs-b (query pat-b sp)]
       (for [ea evs-a
             eb evs-b
             :let [isect (span-intersect (:part ea) (:part eb))]
             :when isect]
         (event (f (:value ea) (:value eb))
                (:whole eb)
                isect))))))
```

No other changes to `core.cljs`.

---

### 2. New file: `packages/core/src/repulse/params.cljs`

New namespace containing all parameter transformer functions. Requires `repulse.core` only.

```clojure
(ns repulse.params
  (:require [repulse.core :as core]))

;;; ── Value map helpers ────────────────────────────────────────────────

(defn- to-map
  "Upgrade a raw event value to a map with :note key, or leave maps as-is."
  [v]
  (if (map? v) v {:note v}))

(defn- apply-param
  "Merge parameter kw with value from param-pat into each event of note-pat."
  [kw param-val-or-pat note-pat]
  (let [param-pat (if ((:query? core/pattern?) param-val-or-pat param-val-or-pat)
                    param-val-or-pat
                    (core/pure param-val-or-pat))]
    (core/combine (fn [pv nv] (assoc (to-map nv) kw pv))
                  param-pat
                  note-pat)))
```

> **Implementation note:** `pattern?` does not exist yet; the implementer can use
> `(and (map? x) (contains? x :query))` to check whether something is already a pattern.

Define each parameter function using a two-arity helper so one-arg returns a transformer:

```clojure
(defn amp
  "Scale event amplitude. 0.0 = silent, 1.0 = full. Default for tones: 0.5.
   (amp 0.8 pat)     — apply directly
   (amp 0.8)         — return transformer (pat → pat)"
  ([amp-val-or-pat] (fn [pat] (amp amp-val-or-pat pat)))
  ([amp-val-or-pat pat] (apply-param :amp amp-val-or-pat pat)))

(defn attack
  "Envelope attack time in seconds. Default: 0.001.
   (attack 0.05 pat) — soft onset
   (attack 0.001 pat) — percussive"
  ([t] (fn [pat] (attack t pat)))
  ([t pat] (apply-param :attack t pat)))

(defn decay
  "Envelope decay time in seconds (time to reach near silence). Default: 0.3.
   (decay 0.08 pat)  — punchy stab
   (decay 1.5 pat)   — long fade"
  ([t] (fn [pat] (decay t pat)))
  ([t pat] (apply-param :decay t pat)))

(defn release
  "Envelope release time in seconds. When omitted, defaults to decay value.
   Relevant once note-off gating is implemented."
  ([t] (fn [pat] (release t pat)))
  ([t pat] (apply-param :release t pat)))

(defn pan
  "Stereo panning. -1.0 = hard left, 0.0 = centre, 1.0 = hard right.
   Requires stereo WASM output (see WASM section below).
   (pan 0.0 pat)      — centre
   (pan (seq -0.5 0.5) pat) — alternating left/right"
  ([p] (fn [pat] (pan p pat)))
  ([p pat] (apply-param :pan p pat)))
```

---

### 3. `packages/core/test/repulse/params_test.cljs` — unit tests

```clojure
(ns repulse.params-test
  (:require [cljs.test :refer-macros [deftest is]]
            [repulse.core :as core]
            [repulse.params :as params]))

(def ^:private one-cycle
  (core/span (core/int->rat 0) (core/int->rat 1)))

(deftest amp-scalar
  (let [evs (core/query (params/amp 0.8 (core/pure :c4)) one-cycle)]
    (is (= 1 (count evs)))
    (is (= {:note :c4 :amp 0.8} (:value (first evs))))))

(deftest amp-pattern
  ;; amp pattern with 2 events, note pattern with 2 events — each pair matches
  (let [evs (core/query
              (params/amp (core/seq* [0.9 0.5])
                          (core/seq* [:c4 :e4]))
              one-cycle)
        vals (mapv :value evs)]
    (is (= 2 (count vals)))
    (is (= {:note :c4 :amp 0.9} (first vals)))
    (is (= {:note :e4 :amp 0.5} (second vals)))))

(deftest amp-one-arg-returns-fn
  (let [soften (params/amp 0.3)
        evs    (core/query (soften (core/pure :bd)) one-cycle)]
    (is (= {:note :bd :amp 0.3} (:value (first evs))))))

(deftest params-chain
  ;; Multiple params chain into one map
  (let [evs (core/query
              (params/attack 0.02
                (params/amp 0.8
                  (core/pure :c4)))
              one-cycle)]
    (is (= {:note :c4 :amp 0.8 :attack 0.02} (:value (first evs))))))

(deftest combine-core
  ;; combine produces events at intersections
  (let [evs (core/query
              (core/combine
                vector
                (core/seq* [1 2])
                (core/seq* [:a :b]))
              one-cycle)
        vals (mapv :value evs)]
    (is (= 2 (count vals)))
    (is (= [1 :a] (first vals)))
    (is (= [2 :b] (second vals)))))
```

---

### 4. `packages/core/src/repulse/test_runner.cljs` — add params-test

```clojure
(ns repulse.test-runner
  (:require [cljs.test :as test]
            [repulse.core-test]
            [repulse.theory-test]
            [repulse.params-test]))

(defn main []
  (test/run-tests 'repulse.core-test 'repulse.theory-test 'repulse.params-test))
```

---

### 5. `packages/lisp/src/repulse/lisp/eval.cljs` — `->>` and param bindings

**Add `->>` as a special form.** Alongside the existing `let`, `fn`, `def`, `if`, `do`
special-form dispatch, handle `->>`:

```clojure
"->>":
;; (eval ["->>", expr, form1, form2, …])
;; Evaluate expr, then thread result as the last arg of each successive form.
(let [[_ init & forms] ast
      v (eval-expr env init)]
  (reduce
    (fn [acc form]
      ;; form is a list like (amp 0.9) — append acc as last arg, then eval
      (let [extended-form (concat form [acc])]
        (eval-expr env (vec extended-form))))
    v forms))
```

**Add param bindings in `make-env`**, after the theory bindings:

```clojure
;; --- Per-event parameters ---
"amp"     (fn
            ([v]   (params/amp (unwrap v)))
            ([v p] (params/amp (unwrap v) (unwrap p))))
"attack"  (fn
            ([v]   (params/attack (unwrap v)))
            ([v p] (params/attack (unwrap v) (unwrap p))))
"decay"   (fn
            ([v]   (params/decay (unwrap v)))
            ([v p] (params/decay (unwrap v) (unwrap p))))
"release" (fn
            ([v]   (params/release (unwrap v)))
            ([v p] (params/release (unwrap v) (unwrap p))))
"pan"     (fn
            ([v]   (params/pan (unwrap v)))
            ([v p] (params/pan (unwrap v) (unwrap p))))
```

Add `[repulse.params :as params]` to the namespace require.

---

### 6. `packages/audio/src/lib.rs` — per-event WASM parameters

#### 6a. New `Pending` struct with parameters

Replace the current `struct Pending { time: f64, value: String }` with:

```rust
struct Pending {
    time:    f64,
    value:   String,
    amp:     f32,   // 0.0–1.0, default 1.0
    attack:  f32,   // seconds, default 0.001
    decay:   f32,   // seconds, default 0.3
    pan:     f32,   // -1.0–1.0, default 0.0
}
```

#### 6b. New `trigger_v2` export

Keep the existing `trigger` for backward compatibility; add:

```rust
pub fn trigger_v2(
    &mut self,
    value: &str,
    time: f64,
    amp: f32,
    attack: f32,
    decay: f32,
    pan: f32,
) {
    self.pending.push(Pending { time, value: value.to_string(), amp, attack, decay, pan });
}
```

#### 6c. ADSR on `Voice::Tone`

Extend `Voice::Tone` with an attack phase:

```rust
Tone {
    phase:      f64,
    freq:       f64,
    amp:        f32,   // peak amplitude
    gain:       f32,   // current envelope value
    gain_decay: f32,
    attack_inc: f32,   // per-sample increment during attack (0 = instant)
    in_attack:  bool,
}
```

In `Voice::Tone::tick`, ramp up during attack before switching to exponential decay:

```rust
if *in_attack {
    *gain += *attack_inc;
    if *gain >= *amp { *gain = *amp; *in_attack = false; }
} else {
    *gain *= *gain_decay;
}
```

In `activate_v2`, construct Tone with:
```rust
let attack_samples = (params.attack * sr).max(1.0);
Voice::Tone {
    phase: 0.0, freq, amp: params.amp * 0.5,
    gain: 0.0, gain_decay: decay_rate(params.decay, sr),
    attack_inc: params.amp * 0.5 / attack_samples,
    in_attack: true,
}
```

#### 6d. Stereo output for pan

Change `process_block` to return interleaved stereo (L R L R…):

```rust
pub fn process_block(&mut self, n_samples: u32, current_time: f64) -> Float32Array {
    // … activate pending …
    let mut buf = vec![0.0f32; n_samples as usize * 2]; // stereo
    for i in 0..n_samples as usize {
        let mut sum = 0.0f32;
        // sum all voices (apply per-voice pan below if needed)
        for v in self.voices.iter_mut() { sum += v.tick(sr); }
        let s = sum.clamp(-1.0, 1.0);
        buf[i * 2]     = s; // L (pan TBD — phase H ships centre for simplicity)
        buf[i * 2 + 1] = s; // R
    }
    self.voices.retain(|v| !v.is_silent());
    let arr = Float32Array::new_with_length(n_samples * 2);
    arr.copy_from(&buf);
    arr
}
```

Per-voice pan (using constant-power law) can be added in a follow-up once the stereo
pipeline is verified. Phase H ships stereo-compatible output with `pan` stored in the event
map but applied as gain scaling at the JS side if needed.

> **Note on drum voices**: kick/snare/hihat `activate` paths receive `amp` directly via
> `activate_v2`. Apply `params.amp` as a multiplier on their initial `gain` value.
> Attack time is not meaningful for drums; ignore it for those voice types.

---

### 7. `app/src/repulse/audio.cljs` — map value routing

In `play-event`, add a branch for map values **before** the existing keyword/number dispatch:

```clojure
(map? value)
(let [note    (:note value)
      amp-v   (float (:amp value 1.0))
      attack-v (float (:attack value 0.001))
      decay-v  (float (:decay value 0.3))
      pan-v    (float (:pan value 0.0))]
  (cond
    (= note :_) nil   ; rest
    (keyword? note)
    (if (theory/note-keyword? note)
      (let [hz (theory/note->hz note)]
        (worklet-trigger-v2! (str hz) t amp-v attack-v decay-v pan-v))
      (let [resolved (samples/resolve-keyword note)]
        (if (samples/has-bank? resolved)
          (samples/play! ac t resolved 0)   ; TODO: pass amp to sample player
          (worklet-trigger-v2! (name note) t amp-v attack-v decay-v pan-v))))
    (number? note)
    (worklet-trigger-v2! (str note) t amp-v attack-v decay-v pan-v)))
```

Add `worklet-trigger-v2!` — calls `engine.trigger_v2(value, time, amp, attack, decay, pan)`
on the WASM AudioEngine instance.

Update the AudioWorklet JS shim (`audio-worklet.js` or equivalent) to handle stereo output
from `process_block` and copy both channels to the AudioWorkletProcessor output buffers.

---

### 8. `app/src/repulse/lisp-lang/completions.js` — add param entries

```javascript
// --- Per-event parameters ---
{ label: "->>",     type: "keyword",  detail: "(->> pat (amp 0.8) (attack 0.02)) — thread pattern through transformers" },
{ label: "amp",     type: "function", detail: "(amp val pat) — set amplitude 0.0–1.0; (amp val) returns transformer" },
{ label: "attack",  type: "function", detail: "(attack secs pat) — envelope attack time in seconds" },
{ label: "decay",   type: "function", detail: "(decay secs pat) — envelope decay time in seconds" },
{ label: "release", type: "function", detail: "(release secs pat) — envelope release time in seconds" },
{ label: "pan",     type: "function", detail: "(pan pos pat) — stereo pan -1.0 (left) to 1.0 (right)" },
```

---

### 9. `app/src/repulse/lisp-lang/repulse-lisp.grammar` — add to BuiltinName

```
"->>>" | "amp" | "attack" | "decay" | "release" | "pan" |
```

> Use `"->>>"` only if lezer's tokeniser requires it; the actual token is `"->>"`.
> Check that `"-"` is already in `identStart` — it is.

---

### 10. `docs/USAGE.md` — new "Per-event parameters" section

Add after the "Music theory" section.

```markdown
## Per-event parameters

Attach synthesis parameters to any pattern using `amp`, `attack`, `decay`, `release`,
and `pan`. Parameters are themselves patterns — they can be sequences, stacked, or
randomised like any value.

### Thread-last: `->>`

Chain multiple parameters cleanly with `->>` (thread-last):

    (->> (seq :c4 :e4 :g4)
         (amp 0.7)
         (attack 0.02)
         (decay 0.5))

### `amp` — amplitude

    (amp 0.8 (seq :c4 :e4 :g4))          ; all notes at 80%
    (amp (seq 0.9 0.4 0.9 0.4) kick)     ; accent pattern

### `attack` — onset time (seconds)

    (attack 0.001 melody)   ; percussive
    (attack 0.2 pad)        ; slow swell

### `decay` — decay time (seconds)

    (decay 0.08 (chord :major :c4))   ; short stab
    (decay 2.0 (pure :c3))            ; long bass tone

### `pan` — stereo position (-1.0 to 1.0)

    (pan -0.5 melody)                     ; slightly left
    (pan (seq -0.8 0.8) (fast 2 hihat))  ; ping-pong hi-hat

### Named voice presets

    (def pluck  (comp (amp 0.8) (attack 0.003) (decay 0.15)))
    (def pad    (comp (amp 0.4) (attack 0.3)   (decay 1.5)))
    (def punchy (comp (amp 1.0) (attack 0.001) (decay 0.08)))

    (stack (pluck (scale :minor :a3 (seq 0 2 4 7)))
           (pad   (chord :minor :a3))
           (punchy kick))
```

---

## Files to change

| File | Change |
|---|---|
| `packages/core/src/repulse/core.cljs` | Add `combine` |
| `packages/core/src/repulse/params.cljs` | **New** — `amp`, `attack`, `decay`, `release`, `pan` |
| `packages/core/test/repulse/params_test.cljs` | **New** — unit tests for `combine` and all param functions |
| `packages/core/src/repulse/test_runner.cljs` | Require `params-test` |
| `packages/lisp/src/repulse/lisp/eval.cljs` | `->>` special form; require `params`; add 5 bindings to `make-env` |
| `packages/audio/src/lib.rs` | `Pending` with params; `trigger_v2`; ADSR on `Voice::Tone`; stereo `process_block` |
| `app/src/repulse/audio.cljs` | Map-value branch in `play-event`; `worklet-trigger-v2!` helper |
| `app/src/repulse/audio_worklet.js` (or equivalent) | Handle stereo Float32Array from WASM |
| `app/src/repulse/lisp-lang/completions.js` | Add 6 entries |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add 6 tokens to BuiltinName |
| `docs/USAGE.md` | New "Per-event parameters" section |
| `README.md` | Add parameter rows to language reference table |
| `CLAUDE.md` | Mark Phase H as ✓ delivered |

---

## Definition of done

### `combine` and params core

- [ ] `(core/combine vector (core/seq* [1 2]) (core/seq* [:a :b]))` produces `[1 :a]` and `[2 :b]`
- [ ] `(params/amp 0.8 (core/pure :c4))` yields one event with value `{:note :c4 :amp 0.8}`
- [ ] `(params/amp (core/seq* [0.9 0.5]) (core/seq* [:c4 :e4]))` yields correct per-note amps
- [ ] `(params/amp 0.4)` returns a `pat → pat` function; applying it works
- [ ] Chaining `attack` after `amp` produces a map with both keys
- [ ] All params-test unit tests pass (`npm run test:core`)
- [ ] Existing core-test and theory-test still pass

### `->>` in the evaluator

- [ ] `(->> (seq :c4 :e4) (amp 0.8))` is equivalent to `(amp 0.8 (seq :c4 :e4))`
- [ ] Three-step chain `(->> melody (amp 0.7) (attack 0.02) (decay 0.5))` works
- [ ] `(def pluck (comp (amp 0.8) (decay 0.1)))` creates a reusable transformer

### Audio — WASM and bridge

- [ ] `(amp 0.9 (seq :bd :_))` makes the kick noticeably louder than the default
- [ ] `(amp 0.1 (seq :bd :_))` makes it noticeably quieter
- [ ] `(attack 0.2 (pure :c4))` produces a clearly audible slow onset
- [ ] `(decay 0.05 (pure :c4))` produces a short stab; `(decay 2.0 (pure :c4))` a long tone
- [ ] `(pan -0.8 (pure :c4))` sounds left; `(pan 0.8 (pure :c4))` sounds right (stereo output)
- [ ] Raw `(seq :bd :sd)` without any param function still works unchanged (backward compat)
- [ ] Raw note keywords `(seq :c4 :e4)` without params still work unchanged

### Composition

- [ ] `(amp (seq 0.9 0.5 0.9 0.5) kick)` produces a perceptible accent pattern
- [ ] `(->> (scale :minor :a3 (seq 0 2 4)) (amp 0.6) (attack 0.01))` works
- [ ] `(stack (->> kick (amp 0.95)) (->> melody (amp 0.5) (decay 0.8)))` works
- [ ] `(fast 2 (amp 0.7 melody))` and `(amp 0.7 (fast 2 melody))` both work

### UI

- [ ] `amp`, `attack`, `decay`, `release`, `pan`, `->>` appear in autocomplete
- [ ] All six tokens receive syntax highlighting as built-in names
