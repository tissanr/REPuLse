# Phase DST1 — Soft Clipping Distortion

## Goal

Add `:distort` to the `(fx ...)` effect chain — a musical soft-clip waveshaper with
drive, tone, dry/wet, and three clipping algorithms.

```lisp
;; Before — no distortion built-in:
(->> (seq :c2 :e2 :g2 :c3) (synth :saw) (fx :reverb 0.2))

;; After:
(->> (seq :c2 :e2 :g2 :c3)
     (synth :saw)
     (fx :distort :drive 8))

(->> (seq :c3 :eb3 :g3)
     (synth :saw)
     (fx :distort :drive 2 :tone 1500 :algo :atan :mix 0.6))

(->> (seq :c2 :_ :c2 :eb2)
     (synth :square)
     (fx :distort :drive 12 :tone 2000)
     (fx :delay :time 0.375 :wet 0.3)
     (fx :reverb 0.2))
```

---

## Background

### Existing overdrive (Phase A)

Phase A shipped `app/public/plugins/overdrive.js` — a simple `WaveShaperNode` with
drive mapped 0–1, a single clipping formula, and no gain compensation. It works, but
it is not the distortion effect described here. Do NOT modify `overdrive.js`.

The new `:distort` plugin is a separate file at `app/public/plugins/distort.js` with:
- Drive mapped 1.0–100.0 (not 0–1)
- Three named algorithms (`:tanh`, `:sigmoid`, `:atan`)
- A mandatory post-clip lowpass (`:tone`)
- Gain compensation so perceived loudness stays roughly constant as drive increases
- A proper dry/wet blend (`:mix`)

### Effect plugin interface (recap)

Every effect is an ES module default export at `app/public/plugins/<name>.js`.
The plain-object style (zero imports) is used by all built-ins:

```javascript
export default {
  type: "effect", name: "<name>", version: "1.0.0",
  init(host)            {},
  createNodes(ctx)      { /* build Web Audio graph */ return { inputNode, outputNode }; },
  setParam(name, value) { /* update named parameter */ },
  bypass(on)            { /* true → transparent pass-through */ },
  getParams()           { return {}; },
  destroy()             { /* disconnect all nodes */ },
};
```

`fx.cljs` (`add-effect!`, `set-param!`, `rewire!`) and the `(fx ...)` Lisp built-in
in `eval.cljs` already handle registration, dispatch, and chain management.
This phase does not change either of those files.

### How `(fx :distort :drive 8)` reaches the plugin

1. Lisp evaluates `(fx :distort :drive 8)`
2. `eval.cljs` `make-env` "fx" handler calls `(fx/set-param! "distort" "drive" 8)`
3. `fx.cljs` `set-param!` finds the chain entry named `"distort"` and calls
   `.setParam(plugin, "drive", 8)`
4. The plugin's `setParam` recomputes the `WaveShaperNode` curve

---

## Implementation

### `app/public/plugins/distort.js`

```javascript
// Transfer functions — all produce output in [-1, 1] for any input
const ALGOS = {
  tanh:    (x, k) => Math.tanh(x * k),
  sigmoid: (x, k) => (2 / (1 + Math.exp(-x * k))) - 1,
  atan:    (x, k) => (2 / Math.PI) * Math.atan(x * k),
};

function makeCurve(drive, algo) {
  const N = 512;
  const curve = new Float32Array(N);
  const fn = ALGOS[algo] ?? ALGOS.tanh;
  // Gain compensation: as drive increases, scale output down to keep loudness constant.
  // 1/sqrt(drive) keeps RMS roughly flat across the drive range.
  const comp = 1 / Math.sqrt(Math.max(1, drive));
  for (let i = 0; i < N; i++) {
    const x = (i * 2) / (N - 1) - 1;   // [-1, 1]
    curve[i] = fn(x, drive) * comp;
  }
  return curve;
}

export default {
  type: "effect", name: "distort", version: "1.0.0",

  // State
  _drive: 4.0,
  _tone: 3000,
  _mix: 1.0,
  _algo: "tanh",

  createNodes(ctx) {
    // Nodes
    this._input    = ctx.createGain();
    this._shaper   = ctx.createWaveShaper();
    this._toneLP   = ctx.createBiquadFilter();
    this._wetGain  = ctx.createGain();
    this._dryGain  = ctx.createGain();
    this._out      = ctx.createGain();

    // Initial state
    this._shaper.curve         = makeCurve(this._drive, this._algo);
    this._shaper.oversample    = "2x";        // native anti-alias at no extra cost
    this._toneLP.type          = "lowpass";
    this._toneLP.frequency.value = this._tone;
    this._toneLP.Q.value       = 0.7;
    this._dryGain.gain.value   = 1 - this._mix;
    this._wetGain.gain.value   = this._mix;

    // Routing:
    //   input → dry → out
    //   input → shaper → toneLP → wet → out
    this._input.connect(this._dryGain);
    this._input.connect(this._shaper);
    this._shaper.connect(this._toneLP);
    this._toneLP.connect(this._wetGain);
    this._dryGain.connect(this._out);
    this._wetGain.connect(this._out);

    return { inputNode: this._input, outputNode: this._out };
  },

  setParam(name, value) {
    const now = this._input?.context?.currentTime ?? 0;
    if (name === "drive") {
      this._drive = Math.max(1.0, Math.min(100.0, value));
      this._shaper.curve = makeCurve(this._drive, this._algo);
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
    if (name === "algo") {
      // value is a string: "tanh", "sigmoid", "atan"
      // Lisp keywords arrive as strings here (keyword -> name already done in eval.cljs)
      this._algo = (typeof value === "string" ? value : String(value)).replace(/^:/, "");
      if (!ALGOS[this._algo]) {
        console.warn(`[distort] unknown algo "${this._algo}", defaulting to tanh`);
        this._algo = "tanh";
      }
      this._shaper.curve = makeCurve(this._drive, this._algo);
    }
  },

  bypass(on) {
    const now = this._input?.context?.currentTime ?? 0;
    const target = on ? 0 : this._mix;
    this._wetGain.gain.linearRampToValueAtTime(target,       now + 0.02);
    this._dryGain.gain.linearRampToValueAtTime(on ? 1 : (1 - this._mix), now + 0.02);
  },

  getParams() {
    return { drive: this._drive, tone: this._tone, mix: this._mix, algo: this._algo };
  },

  destroy() {
    try { this._input.disconnect(); } catch (_) {}
    try { this._out.disconnect();   } catch (_) {}
  },
};
```

### Register in `app/src/repulse/app.cljs`

Add `"/plugins/distort.js"` to the existing `doseq` that auto-loads effect plugins:

```clojure
(doseq [url ["/plugins/reverb.js"
             "/plugins/delay.js"
             "/plugins/filter.js"
             "/plugins/compressor.js"
             "/plugins/dattorro-reverb.js"
             "/plugins/chorus.js"
             "/plugins/phaser.js"
             "/plugins/tremolo.js"
             "/plugins/overdrive.js"
             "/plugins/bitcrusher.js"
             "/plugins/distort.js"]]   ; ← add this
  ...)
```

The plugin loads silently at startup with `mix: 1.0` but `drive: 4.0` — it is active
but the default drive produces only mild saturation, not silence. Unlike reverb/delay,
it does not default to zero-effect, because `mix: 1.0` with `drive: 1.0` is near-unity.
**Important:** the default `drive: 4.0` is intentional — `(fx :distort)` with no
parameters should be immediately audible as a gentle overdrive.

### Keyword dispatch note

The `(fx ...)` handler in `eval.cljs` already converts Lisp keywords to strings before
calling `set-param!`. `:algo :tanh` arrives at `setParam` as `("algo", "tanh")` — no
special handling is needed in the plugin. Verify this by searching for the `(name k)`
call in the `(fx ...)` body in `eval.cljs`.

### Grammar and completions

Add `:distort` to `BuiltinName` in `app/src/repulse/lisp-lang/repulse-lisp.grammar`:

```
BuiltinName {
  ...
  | "distort"
}
```

Run `npm run gen:grammar` after editing the grammar.

Add to `app/src/repulse/lisp-lang/completions.js`:

```javascript
{ label: "distort",  type: "keyword", detail: "effect — soft clipping distortion" },
```

### Hover docs

Add to the hover-docs map (wherever existing `fx` keyword docs live — search for
`"reverb"` in `app/src/repulse/lisp-lang/` or `docs.cljs`):

```javascript
"distort": `(fx :distort [:drive N] [:tone Hz] [:mix N] [:algo kw])
Soft-clipping waveshaper distortion.
  :drive   1.0–100.0  pre-clip gain (default 4.0)
  :tone    200–20000  post-clip lowpass Hz (default 3000)
  :mix     0.0–1.0    dry/wet blend (default 1.0)
  :algo    :tanh | :sigmoid | :atan  clipping curve (default :tanh)`,
```

### `docs/USAGE.md` update

In the effects table, add a row:

```
| `:distort` | `(fx :distort :drive 8)` | Soft-clipping waveshaper. `:drive` 1–100, `:tone` Hz, `:mix` 0–1, `:algo` :tanh/:sigmoid/:atan |
```

Add to the Examples section:

```lisp
;; Gentle saturation on a bass line
(->> (seq :c2 :e2 :g2 :c3)
     (synth :saw)
     (fx :distort :drive 4))

;; Dark, warm atan clip
(->> (seq :c3 :eb3 :g3)
     (synth :saw)
     (fx :distort :drive 6 :tone 1800 :algo :atan))

;; Stacked with delay and reverb
(->> (seq :c2 :_ :c2 :eb2)
     (synth :square)
     (fx :distort :drive 12 :tone 2000)
     (fx :delay :time 0.375 :wet 0.3)
     (fx :reverb 0.2))
```

---

## Files to change

| File | Change |
|------|--------|
| `app/public/plugins/distort.js` | **New** — `:distort` effect plugin |
| `app/src/repulse/app.cljs` | Add `"/plugins/distort.js"` to auto-load list |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `"distort"` to `BuiltinName` |
| `app/src/repulse/lisp-lang/completions.js` | Add `:distort` entry |
| hover docs map | Add docs for `:distort` and its parameters |
| `docs/USAGE.md` | Add `:distort` to effects table + examples |
| `CLAUDE.md` | Mark DST1 as ✓ delivered |

Run `npm run gen:grammar` after editing the grammar file.

No changes to `packages/core/`, `packages/lisp/`, or `packages/audio/`.
No changes to `fx.cljs` or `eval.cljs`.

---

## Definition of done

- [ ] `(fx :distort)` with no parameters produces audible mild overdrive (not silence)
- [ ] `(fx :distort :drive 8)` produces clear distortion on a saw bass
- [ ] `(fx :distort :drive 1)` with `:algo :tanh` is near-unity (minimal distortion)
- [ ] `(fx :distort :drive 100)` produces hard-clipped-like output with no NaN, no digital
      overs, output remains in [-1, 1]
- [ ] All three algos (`:tanh`, `:sigmoid`, `:atan`) produce output in [-1, 1] at all drives
- [ ] `:tone 800` audibly reduces high frequencies compared to `:tone 20000`
- [ ] `:mix 0` passes dry signal, `:mix 1` passes fully wet
- [ ] Changing `:drive`, `:tone`, `:mix` while playing produces no clicks (ramps in place)
- [ ] `(fx :off :distort)` / `(fx :on :distort)` bypass works with no click
- [ ] Works in `->>` chains with `:delay`, `:reverb`, `:filter`, `:chorus`, etc.
- [ ] `:algo :invalid-name` → console warning, falls back to `:tanh`, no crash
- [ ] `:drive -5` → clamped to 1.0, no error thrown
- [ ] Grammar change committed with regenerated `parser.js`
- [ ] All existing core tests pass (`npm run test:core`)
- [ ] No audio glitches or dropouts in the browser console
