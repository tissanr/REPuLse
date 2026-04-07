# Phase DST2 — Asymmetric Soft Clipping

## Goal

Extend `(fx :distort ...)` with an `:asym` parameter that creates asymmetric clipping —
harder on one half-wave, producing even harmonics and a "warm tube" character.

```lisp
;; Before — symmetric only:
(->> (seq :c2 :g2 :c3 :g2) (synth :saw) (fx :distort :drive 6))

;; After — asymmetric saturation:
(->> (seq :c2 :g2 :c3 :g2)
     (synth :saw)
     (fx :distort :drive 6 :asym 0.4 :tone 2500))

;; Lo-fi character:
(->> (seq :c3 :eb3)
     (synth :square)
     (fx :distort :drive 20 :asym 0.8 :algo :atan))

;; :asym 0 is identical to Phase DST1 behaviour:
(->> (seq :c3 :g3) (synth :saw) (fx :distort :drive 8 :asym 0))
```

---

## Background

### Depends on Phase DST1

This phase modifies `app/public/plugins/distort.js` created in DST1. Read that file
before making changes. All existing DST1 parameters (`:drive`, `:tone`, `:mix`,
`:algo`) must continue to work exactly as before.

### Why asymmetric clipping sounds warm

Symmetric waveshaping adds only odd harmonics (3rd, 5th, 7th…). Asymmetric shaping
— where positive and negative half-waves clip at different intensities — adds even
harmonics (2nd, 4th, 6th…), which are musically related to the fundamental and its
octaves. This is how tube triodes sound: the asymmetric device characteristic adds
2nd harmonic coloration perceived as warmth.

### DC offset side effect

Asymmetric clipping shifts the mean of the waveform away from zero — a DC offset.
DC offset causes problems downstream: it wastes headroom, biases subsequent effects,
and causes clicks when switching parameters. A DC blocker (first-order highpass at
~5 Hz) removes it. This must be present in the signal path even at `:asym 0` — if
it is only inserted when `asym != 0`, switching `:asym` from 0 to nonzero mid-playback
will cause an audible click. Keep it always active; its processing cost is negligible.

---

## Implementation

### Changes to `app/public/plugins/distort.js`

Two changes: (1) modify `makeCurve` to accept an asymmetry parameter, (2) add a DC
blocker `IIRFilterNode` to the signal path after the waveshaper.

#### 1. Update `makeCurve` to accept `asym`

```javascript
function makeCurve(drive, algo, asym) {
  const N = 512;
  const curve = new Float32Array(N);
  const fn = ALGOS[algo] ?? ALGOS.tanh;
  const comp = 1 / Math.sqrt(Math.max(1, drive));

  for (let i = 0; i < N; i++) {
    const x = (i * 2) / (N - 1) - 1;   // [-1, 1]
    // Asymmetry: positive half-wave uses drive*(1+asym), negative uses drive*(1-asym).
    // asym is clamped to [-1, 1] so effective drive is always ≥ 0.
    const effectiveDrive = x >= 0
      ? drive * (1 + asym)
      : drive * (1 - asym);
    curve[i] = fn(x, Math.max(0.01, effectiveDrive)) * comp;
  }
  return curve;
}
```

The `:asym 0` path calls `makeCurve(drive, algo, 0)` — both branches compute
`drive * 1`, identical to the DST1 curve. Regression-safe.

#### 2. Add DC blocker in `createNodes`

Use Web Audio's `IIRFilterNode` for a first-order DC blocker:

```javascript
// DC blocker: y[n] = x[n] - x[n-1] + 0.9995 * y[n-1]
// feedforward: [1, -1], feedback: [1, -0.9995]
// Cutoff ~5 Hz at 48 kHz — removes DC without affecting audible bass.
const feedforward = [1, -1];
const feedback    = [1, -0.9995];
this._dcBlock = ctx.createIIRFilter(feedforward, feedback);
```

Insert it between the waveshaper and the tone filter:

```
input → dryGain  → out
input → shaper → dcBlock → toneLP → wetGain → out
```

Updated routing in `createNodes`:

```javascript
this._input.connect(this._dryGain);
this._input.connect(this._shaper);
this._shaper.connect(this._dcBlock);   // ← new
this._dcBlock.connect(this._toneLP);   // ← changed (was shaper → toneLP)
this._toneLP.connect(this._wetGain);
this._dryGain.connect(this._out);
this._wetGain.connect(this._out);
```

#### 3. Add `_asym` state and `setParam` branch

```javascript
// In state section (alongside _drive, _tone, etc.):
_asym: 0.0,

// In setParam:
if (name === "asym") {
  this._asym = Math.max(-1.0, Math.min(1.0, value));
  this._shaper.curve = makeCurve(this._drive, this._algo, this._asym);
}
```

Update every `makeCurve` call in `setParam` to pass `this._asym`:

```javascript
// drive, tone, and algo setParam branches — each call becomes:
this._shaper.curve = makeCurve(this._drive, this._algo, this._asym);
```

#### 4. Update `getParams`

```javascript
getParams() {
  return {
    drive: this._drive, tone: this._tone,
    mix:   this._mix,   algo: this._algo, asym: this._asym,
  };
},
```

#### 5. Update `destroy`

Add `this._dcBlock` to the disconnect sequence (it needs no explicit disconnect — it
is an intermediate node and will be GC'd when the graph is torn down — but for clarity,
include it in the destroy chain or leave a comment).

### Hover docs update

Add `:asym` to the `:distort` hover doc:

```
  :asym    -1.0–1.0   asymmetry (0 = symmetric, >0 = warm/even harmonics, default 0.0)
```

### `docs/USAGE.md` update

Add `:asym` to the `:distort` effects table row and usage examples:

```lisp
;; Warm tube-style asymmetric saturation
(->> (seq :c2 :g2 :c3 :g2)
     (synth :saw)
     (fx :distort :drive 6 :asym 0.4 :tone 2500))

;; Heavy asymmetric distortion
(->> (seq :c3 :eb3)
     (synth :square)
     (fx :distort :drive 20 :asym 0.8 :algo :atan))
```

---

## Files to change

| File | Change |
|------|--------|
| `app/public/plugins/distort.js` | Add `:asym` param; insert DC blocker; update `makeCurve` |
| hover docs map | Add `:asym` to `:distort` docs |
| `docs/USAGE.md` | Add `:asym` to `:distort` row + examples |
| `CLAUDE.md` | Mark DST2 as ✓ delivered |

No grammar changes. No new plugin files. No changes to `fx.cljs`, `eval.cljs`,
`app.cljs`, or `packages/`.

---

## Definition of done

- [ ] `:asym 0` produces output identical to Phase DST1 (regression — bit-exact curve)
- [ ] `:asym 0.5` produces measurably different harmonic content than `:asym 0`
      (even harmonics audibly present — test with a pure sine, check spectrum)
- [ ] `:asym 1.0` at high drive does not crash, output stays in [-1, 1]
- [ ] `:asym -0.5` produces asymmetry in the opposite direction (negative half clips harder)
- [ ] Output has no DC offset: mean of a 1-second output buffer with `:asym 0.6` < 0.01
- [ ] DC blocker is always in the chain — switching `:asym` from 0 to 0.8 mid-playback
      produces no audible click
- [ ] `:asym 1.5` → clamped to 1.0, no error
- [ ] `:asym -2` → clamped to -1.0, no error
- [ ] All DST1 acceptance criteria still pass
- [ ] All existing core tests pass (`npm run test:core`)
