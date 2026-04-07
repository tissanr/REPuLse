# Phase DST4 — Oversampling Wrapper

## Goal

Add an `:oversample` parameter to `(fx :distort ...)` and `(fx :amp-sim ...)` that
reduces aliasing at high drive settings.

```lisp
;; Before — aliasing audible at drive > 20:
(->> (seq :c2 :e2 :g2) (synth :saw) (fx :distort :drive 25))

;; After — same patch, cleaner:
(->> (seq :c2 :e2 :g2) (synth :saw) (fx :distort :drive 25 :oversample 4))

;; Oversampled amp sim:
(->> (seq :e2 :_ :a2 :_) (synth :saw) (fx :amp-sim :gain 40 :oversample 2))
```

---

## Background

### What aliasing is and why it matters

Nonlinear waveshaping generates harmonics above the signal's original frequency
content. If those harmonics exceed the Nyquist frequency (half the sample rate),
they fold back ("alias") into the audible range as inharmonic artifacts — a harsh,
metallic, unmusical quality that distinguishes cheap digital distortion from analog.

Oversampling — processing at 2× or 4× the normal sample rate and then filtering back
down — pushes the aliasing products above the higher Nyquist frequency, where they
are removed by the anti-alias filter before downsampling.

### Why not always oversample

CPU cost scales linearly with the oversampling factor. 4× oversampling means 4× the
DSP work per voice. At light drive settings `:oversample 1` (no oversampling) is
indistinguishable. The parameter lets the user choose the tradeoff explicitly.

### Web Audio's native oversampling

`WaveShaperNode` already has an `oversample` property that accepts `"none"`, `"2x"`,
and `"4x"`. Web Audio implements the oversampling internally (upsample → waveshape →
downsample), so the JS path gets oversampling for free at the price of a single string
assignment.

**Use Web Audio's native `WaveShaperNode.oversample` for the JS path.** This is the
right tool. Do NOT implement a manual upsample/downsample pipeline in JavaScript — it
would add latency and CPU cost for no benefit over the browser's optimised native
implementation.

The WASM path (currently a no-op for these effects — distortion is JS-only) would
need a manual FIR-based oversampler. Since the WASM engine does not yet process
`:distort` or `:amp-sim` events (those are handled entirely in the JS plugin layer),
document that `:oversample` is a JS-path-only feature for now. Add a console info
message if the WASM worklet is somehow used with these effects.

### Current state of `distort.js`

DST1 already sets `this._shaper.oversample = "2x"` unconditionally. DST4 makes that
value user-controllable.

---

## Implementation

### Changes to `app/public/plugins/distort.js`

#### 1. Add `_oversample` state

```javascript
_oversample: 1,   // values: 1, 2, 4 → maps to "none", "2x", "4x"
```

#### 2. Helper to map integer → Web Audio string

```javascript
function oversampleStr(n) {
  if (n >= 4) return "4x";
  if (n >= 2) return "2x";
  return "none";
}
```

#### 3. Apply in `createNodes`

Replace the hardcoded `"2x"` with the current state:

```javascript
this._shaper.oversample = oversampleStr(this._oversample);
```

#### 4. `setParam` branch

```javascript
if (name === "oversample") {
  const n = Number(value);
  if (![1, 2, 4].includes(n)) {
    console.warn(`[distort] :oversample must be 1, 2, or 4; got ${n}, using 1`);
    this._oversample = 1;
  } else {
    this._oversample = n;
  }
  this._shaper.oversample = oversampleStr(this._oversample);
}
```

No curve recomputation needed — `oversample` is independent of the curve.

#### 5. Update `getParams`

```javascript
getParams() {
  return {
    drive: this._drive, tone: this._tone, mix: this._mix,
    algo: this._algo, asym: this._asym, oversample: this._oversample,
  };
},
```

### Changes to `app/public/plugins/amp-sim.js`

Same pattern. The `_shaper` nodes are inside the stage chain.

#### 1. Add `_oversample` state

```javascript
_oversample: 1,
```

#### 2. Apply in `_buildStages`

For each stage's `WaveShaperNode`:

```javascript
shaper.oversample = oversampleStr(this._oversample);
```

where `oversampleStr` is the same helper (define it at module scope or inline).

#### 3. `setParam` branch

```javascript
if (name === "oversample") {
  const n = Number(value);
  if (![1, 2, 4].includes(n)) {
    console.warn(`[amp-sim] :oversample must be 1, 2, or 4; got ${n}, using 1`);
    this._oversample = 1;
  } else {
    this._oversample = n;
  }
  // Update all stage shapers in place (no rebuild needed)
  for (const s of this._stageChain) {
    s.shaper.oversample = oversampleStr(this._oversample);
  }
}
```

Note: updating `oversample` on the existing `WaveShaperNode` objects does not require
rebuilding the gain stages. It is a live property update.

#### 4. Update `getParams`

```javascript
getParams() {
  return {
    gain: this._gain, stages: this._stages, tone: this._tone,
    tonestack: this._tonestack, sag: this._sag, mix: this._mix,
    oversample: this._oversample,
  };
},
```

### CPU warning

Web Audio's native oversampling is handled by the browser's audio rendering thread.
Its CPU cost is not directly measurable from JS. However, if a user patches a large
number of simultaneous `:distort :oversample 4` voices (unlikely in typical REPuLse
usage), the audio thread could become saturated.

Add a comment in the code:

```javascript
// NOTE: :oversample 4 increases the WaveShaperNode's internal processing cost by
// approximately 4x. For typical 1-2 voice patches this is negligible. With many
// simultaneous voices at high oversample, audio dropouts may occur. The user is
// responsible for the trade-off; no automatic warning is emitted since we cannot
// measure audio thread CPU from JavaScript.
```

### Hover docs update

Add `:oversample` to both `:distort` and `:amp-sim` hover docs:

```
  :oversample  1 | 2 | 4   anti-alias oversampling factor (default 1 = off)
```

### `docs/USAGE.md` update

Add `:oversample` to the `:distort` and `:amp-sim` parameter tables.

```lisp
;; Alias-free high-drive distortion
(->> (seq :c2 :e2 :g2)
     (synth :saw)
     (fx :distort :drive 20 :oversample 4))

;; Oversampled amp sim
(->> (seq :e2 :_ :a2 :_)
     (synth :saw)
     (fx :amp-sim :gain 40 :oversample 2))
```

---

## Files to change

| File | Change |
|------|--------|
| `app/public/plugins/distort.js` | Add `_oversample` state, `setParam` branch, apply in `createNodes` |
| `app/public/plugins/amp-sim.js` | Add `_oversample` state, `setParam` branch, apply in `_buildStages` |
| hover docs map | Add `:oversample` to both `:distort` and `:amp-sim` docs |
| `docs/USAGE.md` | Add `:oversample` to param tables + examples |
| `CLAUDE.md` | Mark DST4 as ✓ delivered |

No grammar changes (`:oversample` does not need to be in the grammar as a `BuiltinName`
— it is a parameter keyword used only inside `(fx ...)` calls, not a top-level built-in).

No changes to `fx.cljs`, `eval.cljs`, `app.cljs`, or `packages/`.

---

## Definition of done

- [ ] `(fx :distort :drive 25 :oversample 1)` is the default (no change from DST1/DST2)
- [ ] `(fx :distort :drive 25 :oversample 2)` produces audibly fewer aliasing artifacts
      on a saw wave at high drive
- [ ] `(fx :distort :drive 25 :oversample 4)` produces even cleaner high-drive distortion
- [ ] `(fx :amp-sim :gain 40 :oversample 2)` and `:oversample 4` both work
- [ ] Switching `:oversample` value while playing produces no crash and no sustained click
- [ ] `:oversample 3` → console warning, falls back to 1, no crash
- [ ] `:oversample 0` → console warning, falls back to 1
- [ ] All DST1, DST2, DST3 acceptance criteria still pass (no regressions)
- [ ] All existing core tests pass (`npm run test:core`)
