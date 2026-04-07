# Phase DST5 — Waveshaper Lookup Table

## Goal

Add `(fx :waveshape ...)` — arbitrary transfer-function distortion via a user-defined
curve or a built-in generator. This is REPuLse's "everything is programmable" face of
the distortion system.

```lisp
;; Chebyshev 3rd harmonic distortion:
(->> (seq :c3 :e3 :g3)
     (synth :saw)
     (fx :waveshape :curve (chebyshev 3) :drive 2))

;; Wavefolding (waveform folds back on itself):
(->> (seq :c2 :_ :g2 :_)
     (synth :sin)
     (fx :waveshape :curve (fold) :drive 8))

;; Bitcrusher-style staircase:
(->> (fast 2 (seq :c4 :e4 :g4 :c5))
     (synth :saw)
     (fx :waveshape :curve (bitcrush 8) :mix 0.7))

;; Custom curve (list of floats, input [-1,1] maps linearly to indices):
(->> (seq :c3 :g3)
     (synth :sin)
     (fx :waveshape :curve (-1.0 -0.8 -0.3 0 0.3 0.9 1.0) :drive 3))
```

---

## Background

### Depends on Phase DST1

This is a side-path phase — it depends on DST1 for understanding the effect registration
pattern, but does NOT depend on DST2, DST3, or DST4. It can be implemented in any order
after DST1.

### `WaveShaperNode.curve`

The Web Audio `WaveShaperNode` accepts a `Float32Array` as its `.curve` property.
The array defines a transfer function: the input sample value (normalised to [-1, 1])
maps to an array index, and the output sample is the array value at that index.
Linear interpolation is performed between adjacent values.

This is exactly what `:waveshape` exposes to the user. The plugin is a thin wrapper
around `WaveShaperNode` with:
1. A `:curve` parameter that accepts a `Float32Array` directly
2. Built-in generators (`chebyshev`, `fold`, `bitcrush`) that produce `Float32Array`s
3. `:drive` (pre-gain), `:tone` (post lowpass), `:mix` (dry/wet)

### Curve generators

The generators (`chebyshev`, `fold`, `bitcrush`) are **Lisp built-in functions** that
return a `Float32Array`. They are evaluated at read-time when the `(fx :waveshape ...)`
form is evaluated — they are not DSP functions, they run once. The resulting array is
passed to the plugin's `setParam("curve", array)`.

This is the same pattern as `(chord :major :c4)` returning a list of notes — a Lisp
function that produces a data value used as a parameter.

---

## Implementation

### 1. `app/public/plugins/waveshape.js`

```javascript
export default {
  type: "effect", name: "waveshape", version: "1.0.0",

  _drive: 1.0,
  _tone: 20000,
  _mix: 1.0,
  _curve: null,   // Float32Array | null

  createNodes(ctx) {
    this._input   = ctx.createGain();
    this._preGain = ctx.createGain();   // drive boost before shaper
    this._shaper  = ctx.createWaveShaper();
    this._toneLP  = ctx.createBiquadFilter();
    this._wetGain = ctx.createGain();
    this._dryGain = ctx.createGain();
    this._out     = ctx.createGain();

    this._preGain.gain.value    = this._drive;
    this._toneLP.type           = "lowpass";
    this._toneLP.frequency.value = this._tone;
    this._toneLP.Q.value        = 0.7;
    this._dryGain.gain.value    = 1 - this._mix;
    this._wetGain.gain.value    = this._mix;

    // Default curve: identity (linear, no distortion)
    if (!this._curve) {
      this._curve = new Float32Array([-1, 0, 1]);
    }
    this._shaper.curve = this._curve;

    // Routing:
    //   input → dry → out
    //   input → preGain → shaper → toneLP → wet → out
    this._input.connect(this._dryGain);
    this._input.connect(this._preGain);
    this._preGain.connect(this._shaper);
    this._shaper.connect(this._toneLP);
    this._toneLP.connect(this._wetGain);
    this._dryGain.connect(this._out);
    this._wetGain.connect(this._out);

    return { inputNode: this._input, outputNode: this._out };
  },

  setParam(name, value) {
    const now = this._input?.context?.currentTime ?? 0;

    if (name === "curve") {
      if (!(value instanceof Float32Array)) {
        // Accept a plain JS array or CLJS array and convert
        try {
          value = new Float32Array(value);
        } catch (e) {
          console.error("[waveshape] :curve must be a Float32Array or numeric array", e);
          return;
        }
      }
      if (value.length < 3) {
        console.error(`[waveshape] :curve must have at least 3 points (got ${value.length})`);
        return;
      }
      if (value.length > 4097) {
        console.error(`[waveshape] :curve must have at most 4097 points (got ${value.length})`);
        return;
      }
      // Crossfade to new curve over ~10ms to avoid clicks
      // Web Audio doesn't support gradual curve transitions natively.
      // Approximation: write the new curve in two steps separated by a short ramp.
      // For simplicity, just assign directly — at audio-rate the transition is
      // one buffer (~2.6ms at 48kHz/128 samples). If audible clicks occur, add a
      // brief dry/wet crossfade (ramp mix to 0, update curve, ramp back).
      this._curve = value;
      this._shaper.curve = value;
    }

    if (name === "drive") {
      this._drive = Math.max(1.0, Math.min(20.0, value));
      this._preGain.gain.linearRampToValueAtTime(this._drive, now + 0.02);
    }

    if (name === "tone") {
      this._tone = Math.max(200, Math.min(20000, value));
      this._toneLP.frequency.linearRampToValueAtTime(this._tone, now + 0.02);
    }

    if (name === "mix") {
      this._mix = Math.max(0, Math.min(1, value));
      this._dryGain.gain.linearRampToValueAtTime(1 - this._mix, now + 0.02);
      this._wetGain.gain.linearRampToValueAtTime(this._mix,     now + 0.02);
    }
  },

  bypass(on) {
    const now = this._input?.context?.currentTime ?? 0;
    this._wetGain.gain.linearRampToValueAtTime(on ? 0 : this._mix,       now + 0.02);
    this._dryGain.gain.linearRampToValueAtTime(on ? 1 : (1 - this._mix), now + 0.02);
  },

  getParams() {
    return { drive: this._drive, tone: this._tone, mix: this._mix };
  },

  destroy() {
    try { this._input.disconnect(); } catch (_) {}
    try { this._out.disconnect();   } catch (_) {}
  },
};
```

### 2. Curve generator built-ins in `eval.cljs`

Add `chebyshev`, `fold`, and `bitcrush` to `make-env` in
`packages/lisp/src/repulse/lisp/eval.cljs`. These are pure Lisp functions that return
JavaScript `Float32Array` objects. They are computed at eval time and passed as data.

```clojure
;; Chebyshev polynomial of order N evaluated over [-1, 1].
;; Produces exactly the Nth harmonic component when a sine is the input.
;; Order 2 → 2nd harmonic, order 3 → 3rd, etc.
"chebyshev"
(fn [n-arg]
  (let [n (unwrap n-arg)
        N 512
        arr (js/Float32Array. N)]
    (when (or (< n 1) (> n 8))
      (throw (js/Error. (str "chebyshev: order must be 1–8, got " n))))
    ;; Evaluate the Nth Chebyshev polynomial at each point in [-1, 1].
    ;; T_1(x) = x
    ;; T_2(x) = 2x² - 1
    ;; T_N(x) = 2x·T_{N-1}(x) - T_{N-2}(x)
    (dotimes [i N]
      (let [x (- (* 2.0 (/ i (dec N))) 1.0)
            t (loop [t0 1.0 t1 x k 1]
                (if (>= k n) t1
                  (recur t1 (- (* 2.0 x t1) t0) (inc k))))]
        (aset arr i (js/Math.max -1.0 (js/Math.min 1.0 t)))))
    arr))

;; Wavefolder: triangle-wave transfer function.
;; Input above 0.5 or below -0.5 "folds" back, creating additional harmonics.
"fold"
(fn []
  (let [N 512
        arr (js/Float32Array. N)]
    (dotimes [i N]
      (let [x (- (* 2.0 (/ i (dec N))) 1.0)
            ;; Triangle wave with period 1: fold the signal
            folded (let [a (js/Math.abs x)
                         ;; Map [0,1] through a triangle that bounces at ±0.5
                         t (mod a 1.0)
                         v (if (< t 0.5) (* t 2.0) (- 2.0 (* t 2.0)))]
                     (if (neg? x) (- v) v))]
        (aset arr i (js/Math.max -1.0 (js/Math.min 1.0 folded)))))
    arr))

;; Bitcrusher staircase with `bits` quantization levels.
;; (bitcrush 4) → 16 steps (2^4), (bitcrush 8) → 256 steps.
"bitcrush"
(fn [bits-arg]
  (let [bits (unwrap bits-arg)
        N 512
        arr (js/Float32Array. N)
        steps (js/Math.pow 2 bits)]
    (when (or (< bits 1) (> bits 16))
      (throw (js/Error. (str "bitcrush: bits must be 1–16, got " bits))))
    (dotimes [i N]
      (let [x (- (* 2.0 (/ i (dec N))) 1.0)
            quantized (/ (js/Math.round (* x (/ steps 2.0))) (/ steps 2.0))]
        (aset arr i (js/Math.max -1.0 (js/Math.min 1.0 quantized)))))
    arr))
```

Note: these functions live in `eval.cljs` alongside other built-ins. They return a
native `js/Float32Array` which is passed through the `(fx :waveshape :curve ...)` call
directly to the plugin's `setParam`. The plugin accepts `Float32Array` in its `setParam`
implementation.

### How `:curve (chebyshev 3)` is dispatched

1. Lisp evaluates `(chebyshev 3)` → returns a `Float32Array`
2. The outer `(fx :waveshape :curve <Float32Array>)` call passes it to
   `(fx/set-param! "waveshape" "curve" <Float32Array>)`
3. `fx.cljs` `set-param!` calls `.setParam(plugin, "curve", <Float32Array>)`
4. Plugin's `setParam` assigns it to `WaveShaperNode.curve`

No special handling needed in `fx.cljs` or `eval.cljs`'s `(fx ...)` handler — the
`Float32Array` is passed through as a regular Lisp value.

### How `:curve (-1.0 -0.8 ... 1.0)` (list literal) is dispatched

A parenthesised list in Lisp evaluates as a function call, not a data list. The user
must either:
- Use a vector: `(fx :waveshape :curve [-1.0 -0.8 -0.3 0 0.3 0.9 1.0])`
- Or use `(list ...)`: `(fx :waveshape :curve (list -1.0 -0.8 -0.3 0 0.3 0.9 1.0))`

The plugin's `setParam` converts any JS array-like or CLJS persistent vector to
`Float32Array`. Add this conversion in the `"curve"` branch:

```javascript
// Accept Float32Array, plain Array, or CLJS ISeq/PersistentVector
if (!(value instanceof Float32Array)) {
  try {
    // CLJS vectors implement JS iterable protocol
    value = new Float32Array(Array.from(value));
  } catch (e) {
    console.error("[waveshape] :curve must be numeric array or Float32Array", e);
    return;
  }
}
```

Document in hover docs that vectors `[...]` are the preferred literal syntax.

### Grammar and completions

Add to `BuiltinName` in the grammar:

```
BuiltinName {
  ...
  | "waveshape"
  | "chebyshev"
  | "fold"
  | "bitcrush"
}
```

Run `npm run gen:grammar`.

Add to `completions.js`:

```javascript
{ label: "waveshape", type: "keyword", detail: "effect — arbitrary waveshaper" },
{ label: "chebyshev", type: "function", detail: "curve — Chebyshev polynomial (order 1–8)" },
{ label: "fold",      type: "function", detail: "curve — wavefolder transfer function" },
{ label: "bitcrush",  type: "function", detail: "curve — bitcrusher staircase (bits 1–16)" },
```

### Hover docs

```
"waveshape": `(fx :waveshape :curve C [:drive N] [:tone Hz] [:mix N])
Arbitrary waveshaper distortion via a user-defined transfer function.
  :curve   Float32Array | [floats] | (chebyshev N) | (fold) | (bitcrush N)
           Transfer function: input [-1,1] → output. Min 3 pts, max 4097.
  :drive   1.0–20.0   pre-gain before shaping (default 1.0)
  :tone    200–20000  post-shaper lowpass Hz (default 20000 = open)
  :mix     0.0–1.0    dry/wet blend (default 1.0)`,

"chebyshev": `(chebyshev N)
Generate a Chebyshev polynomial curve of order N (1–8) for use with (fx :waveshape).
Order N adds primarily the Nth harmonic. Use as a :curve value.
Example: (fx :waveshape :curve (chebyshev 3) :drive 2)`,

"fold": `(fold)
Generate a wavefolder transfer function for use with (fx :waveshape).
Folds the waveform back on itself, producing rich harmonics.
Example: (fx :waveshape :curve (fold) :drive 8)`,

"bitcrush": `(bitcrush N)
Generate a quantization staircase curve with 2^N steps for use with (fx :waveshape).
N is bit depth (1–16). Low N = aggressive quantization = lo-fi grit.
Example: (fx :waveshape :curve (bitcrush 4))`,
```

### Register in `app/src/repulse/app.cljs`

```clojure
(doseq [url [...
             "/plugins/distort.js"
             "/plugins/amp-sim.js"
             "/plugins/waveshape.js"]]   ; ← add this
  ...)
```

### `docs/USAGE.md` update

Add `:waveshape` to the effects table. Add `chebyshev`, `fold`, `bitcrush` to the
built-in functions reference. Add usage examples:

```lisp
;; Chebyshev 2nd harmonic (adds an octave above)
(->> (seq :c3 :e3 :g3) (synth :sin)
     (fx :waveshape :curve (chebyshev 2) :drive 3))

;; Chebyshev 3rd harmonic
(->> (seq :c3 :e3 :g3) (synth :saw)
     (fx :waveshape :curve (chebyshev 3) :drive 2))

;; Wavefolding
(->> (seq :c2 :_ :g2 :_) (synth :sin)
     (fx :waveshape :curve (fold) :drive 8))

;; 4-bit quantization
(->> (fast 2 (seq :c4 :e4 :g4 :c5)) (synth :saw)
     (fx :waveshape :curve (bitcrush 4) :mix 0.7))

;; Custom curve
(->> (seq :c3 :g3) (synth :sin)
     (fx :waveshape :curve [-1.0 -0.5 0 0.8 1.0] :drive 2))
```

---

## Files to change

| File | Change |
|------|--------|
| `app/public/plugins/waveshape.js` | **New** — `:waveshape` effect plugin |
| `packages/lisp/src/repulse/lisp/eval.cljs` | Add `chebyshev`, `fold`, `bitcrush` to `make-env` |
| `app/src/repulse/app.cljs` | Add `"/plugins/waveshape.js"` to auto-load list |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `"waveshape"`, `"chebyshev"`, `"fold"`, `"bitcrush"` to `BuiltinName` |
| `app/src/repulse/lisp-lang/completions.js` | Add entries for all four new names |
| hover docs map | Add docs for `:waveshape`, `chebyshev`, `fold`, `bitcrush` |
| `docs/USAGE.md` | Add `:waveshape` to effects table + curve generator reference + examples |
| `CLAUDE.md` | Mark DST5 as ✓ delivered |

Run `npm run gen:grammar` after editing the grammar.

No changes to `fx.cljs`. No changes to `distort.js` or `amp-sim.js`.

---

## Definition of done

- [ ] `(fx :waveshape :curve (chebyshev 3) :drive 2)` on a sine produces audible 3rd harmonic
- [ ] `(fx :waveshape :curve (chebyshev 2))` on a sine adds a 2nd harmonic (octave coloration)
- [ ] `(fx :waveshape :curve (fold) :drive 8)` produces audible wavefolding character
- [ ] `(fx :waveshape :curve (bitcrush 4) :mix 0.7)` produces staircase quantization
- [ ] `(fx :waveshape :curve [-1 0 1])` (identity/linear) with `:drive 1` is unity — no distortion
- [ ] Custom vector curve `[-1.0 -0.5 0 0.8 1.0]` works (passed as CLJS vector, converted to Float32Array)
- [ ] Updating `:curve` while playing produces no sustained audio dropout
- [ ] `:curve (chebyshev 9)` → clear error message ("order must be 1–8"), no crash
- [ ] `:curve (bitcrush 0)` → clear error, no crash; `:curve (bitcrush 17)` → clear error
- [ ] `:curve []` (2 points) → clear error ("at least 3 points"), no crash
- [ ] `(fx :off :waveshape)` / `(fx :on :waveshape)` bypass works
- [ ] Works in `->>` chains with all existing effects
- [ ] `chebyshev`, `fold`, `bitcrush` appear in autocomplete
- [ ] Grammar change committed with regenerated `parser.js`
- [ ] All existing core tests pass (`npm run test:core`)
