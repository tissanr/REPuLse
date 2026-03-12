# Phase A — More Effects

## Goal

Add five more high-quality effect plugins to the REPuLse effect chain, covering
classic music-production tools: chorus, phaser, tremolo, overdrive, and bitcrusher.
All are accessible via the existing `(fx ...)` built-in — no new Lisp primitives needed.

---

## Effect plugin interface (recap)

Every effect plugin is an ES module default export at `app/public/plugins/<name>.js`:

```javascript
export default {
  type:    "effect",
  name:    "<name>",        // matched by (fx :<name> ...)
  version: "1.0.0",

  init(host)  { },          // called once with the host API — start any async work here
  createNodes(ctx) {        // called synchronously — must return immediately
    // ... build Web Audio graph ...
    return { inputNode: ..., outputNode: ... };
  },
  setParam(name, value) { }, // name matches the keyword in (fx :<name> :param value)
  bypass(on)  { },           // true → transparent; false → restore
  getParams() { return {}; },
  destroy()   { },           // disconnect and release all nodes
};
```

All plugins are registered in `app/src/repulse/app.cljs` `init` — add each new URL to
the existing `doseq` that auto-loads effect plugins.

---

## Effects to implement

### 1. Chorus — `app/public/plugins/chorus.js`

A classic stereo chorus: two modulated delay lines (one per output channel) with a
shared dry path.

| Parameter | `setParam` key | Default | Range | Description |
|-----------|---------------|---------|-------|-------------|
| Wet mix   | `wet` / `value` | `0.0` | 0–1 | Blend of dry and chorus signal |
| Rate      | `rate` | `1.5` | 0.1–8 Hz | LFO speed |
| Depth     | `depth` | `0.003` | 0–0.02 s | Max delay modulation amount |
| Delay     | `delay` | `0.025` | 0.005–0.05 s | Centre delay time |

**Implementation hints:**

- Use a short `DelayNode` (max 2 s buffer) per channel, modulated by an `OscillatorNode`.
- Each channel gets its own LFO at slightly different starting phase (0 and π/2) for stereo width.
- The `OscillatorNode` drives a `GainNode` (depth control) connected to `delayNode.delayTime`.
- Dry path always on; wet mix controlled by a `GainNode`.
- `bypass(on)`: set wet gain to 0, restore on off.

**Lisp usage:**
```lisp
(fx :chorus 0.5)              ; set wet to 0.5
(fx :chorus :wet 0.5 :rate 2) ; named params
```

---

### 2. Phaser — `app/public/plugins/phaser.js`

An all-pass filter stage chain whose centre frequencies are swept by an LFO, producing
the classic phasing effect.

| Parameter | `setParam` key | Default | Range | Description |
|-----------|---------------|---------|-------|-------------|
| Wet mix   | `wet` / `value` | `0.0` | 0–1 | |
| Rate      | `rate` | `0.5` | 0.01–8 Hz | LFO rate |
| Depth     | `depth` | `0.7` | 0–1 | LFO depth (fraction of frequency sweep range) |
| Base freq | `freq` | `1000` | 100–8000 Hz | Centre frequency at LFO midpoint |

**Implementation hints:**

- Build a chain of 4–6 `BiquadFilterNode`s in `allpass` mode.
- Drive all filter frequencies from a single `OscillatorNode` via a `GainNode`.
- Feedback: connect output back to input through a `GainNode` (feedback ≈ 0.7).
- Mix: blend the filtered (wet) path with the dry path.
- `bypass`: zero wet gain.

**Lisp usage:**
```lisp
(fx :phaser 0.6)
(fx :phaser :rate 0.8 :depth 0.9)
```

---

### 3. Tremolo — `app/public/plugins/tremolo.js`

Amplitude modulation at low frequencies — the sound pulses rhythmically.

| Parameter | `setParam` key | Default | Range | Description |
|-----------|---------------|---------|-------|-------------|
| Depth     | `depth` / `value` | `0.0` | 0–1 | Modulation depth (0 = off, 1 = full mute on each cycle) |
| Rate      | `rate` | `4.0` | 0.1–20 Hz | LFO speed |
| Shape     | `shape` | `"sine"` | `"sine"` / `"square"` / `"sawtooth"` | LFO waveform |

**Implementation hints:**

- An `OscillatorNode` (type = shape) drives a `GainNode` (the amplitude modulator).
- Offset the LFO so it oscillates between `1 - depth` and `1.0` (never inverts signal):
  - `gainNode.gain.value` is set by a constant-source + oscillator bias.
  - Alternatively: use a `ConstantSourceNode` at value 1.0 and connect the oscillator
    through a depth gain to the same `GainNode.gain` AudioParam.
- This effect is always fully in-line (no dry/wet blend needed — at depth=0 it is silent
  modulation). Still implement `bypass(on)` by setting oscillator depth gain to 0.

**Lisp usage:**
```lisp
(fx :tremolo 0.8)              ; depth 0.8
(fx :tremolo :depth 0.8 :rate 6)
(fx :tremolo :shape "square")
```

---

### 4. Overdrive — `app/public/plugins/overdrive.js`

Soft-clipping waveshaper distortion using a `WaveShaperNode`.

| Parameter | `setParam` key | Default | Range | Description |
|-----------|---------------|---------|-------|-------------|
| Drive     | `drive` / `value` | `0.0` | 0–1 | Amount of saturation (0 = clean) |
| Tone      | `tone` | `3500` | 500–12000 Hz | Post-distortion lowpass cutoff |
| Wet mix   | `wet` | `1.0` | 0–1 | Blend of distorted and clean signal |

**Implementation hints:**

- Use a `WaveShaperNode` with a pre-computed curve. A good soft-clip formula:
  ```javascript
  function makeDistortionCurve(drive) {
    const n = 256;
    const curve = new Float32Array(n);
    const k = drive * 100;  // map 0–1 → 0–100 coefficient
    for (let i = 0; i < n; i++) {
      const x = (i * 2) / n - 1;
      curve[i] = x * (Math.abs(x) + k) / (x * x + (k - 1) * Math.abs(x) + 1);
    }
    return curve;
  }
  ```
- On `setParam('drive', v)`: recompute and reassign `waveshaper.curve`.
- Add a `BiquadFilterNode` in `lowpass` mode after the waveshaper for the tone control.
- Include an input `GainNode` boosting by `drive * 2 + 1` to push the signal harder into
  the clipper, with a corresponding output `GainNode` normalising back down.
- Dry/wet blend for parallel distortion.

**Lisp usage:**
```lisp
(fx :overdrive 0.7)            ; drive 0.7
(fx :overdrive :drive 0.8 :tone 4000)
```

---

### 5. Bitcrusher — `app/public/plugins/bitcrusher.js`

Sample-rate and bit-depth reduction for lo-fi, crunchy, glitchy textures.

| Parameter | `setParam` key | Default | Range | Description |
|-----------|---------------|---------|-------|-------------|
| Bits      | `bits` / `value` | `16` | 1–16 | Bit depth (lower = more gritty) |
| Rate      | `rate` | `1.0` | 0.01–1.0 | Sample-rate divisor (1 = full rate, 0.1 = 10×) |
| Wet mix   | `wet` | `0.0` | 0–1 | |

**Implementation hints:**

Because `WaveShaperNode` cannot model sample-rate reduction, use a `ScriptProcessorNode`
(deprecated but still widely supported) or — preferably — an `AudioWorkletProcessor`.

Using a dedicated worklet at `app/public/worklets/bitcrusher-processor.js`:

```javascript
class BitcrusherProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this._bits = 16;
    this._rate = 1.0;
    this._held = 0;
    this._phase = 0;
    this.port.onmessage = ({ data }) => {
      if (data.type === 'setParam') {
        if (data.name === 'bits') this._bits = data.value;
        if (data.name === 'rate') this._rate = data.value;
      }
    };
  }
  process(inputs, outputs) {
    const inp = inputs[0][0], out = outputs[0][0];
    if (!inp) return true;
    const step = Math.pow(2, this._bits - 1);
    for (let i = 0; i < inp.length; i++) {
      this._phase += this._rate;
      if (this._phase >= 1) {
        this._phase -= 1;
        this._held = Math.round(inp[i] * step) / step;
      }
      out[i] = this._held;
    }
    return true;
  }
}
registerProcessor('bitcrusher', BitcrusherProcessor);
```

The plugin wrapper (`plugins/bitcrusher.js`) follows the same async-worklet pattern
as the existing `dattorro-reverb.js`: load worklet in `init`, create dry pass-through
immediately in `createNodes`, connect worklet to wet path in `_ready.then()`.

**Lisp usage:**
```lisp
(fx :bitcrusher 0.8)           ; wet mix 0.8
(fx :bitcrusher :bits 6)       ; 6-bit depth
(fx :bitcrusher :bits 4 :rate 0.25)
```

---

## Auto-load registration

In `app/src/repulse/app.cljs`, add each new plugin URL to the existing `doseq`:

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
             "/plugins/bitcrusher.js"]]
  ...)
```

---

## Documentation to update

- `docs/USAGE.md` — add entries for each new effect to the "## Effect plugins" section.
- `README.md` — add rows to the built-in pattern functions table for new effects if helpful.
- `CLAUDE.md` — mark Phase A as ✓ delivered when done.
- `docs/ARCHITECTURE.md` — Phase status table.

---

## Acceptance criteria

- [ ] All 5 effects auto-load silently at startup (wet=0 or depth=0 by default)
- [ ] `(fx :chorus 0.5)` and `(fx :chorus :rate 2)` work
- [ ] `(fx :phaser 0.6)` and `(fx :phaser :rate 0.8)` work
- [ ] `(fx :tremolo 0.8)` and `(fx :tremolo :shape "square")` work
- [ ] `(fx :overdrive 0.7)` and `(fx :overdrive :drive 0.8 :tone 4000)` work
- [ ] `(fx :bitcrusher 0.8)` and `(fx :bitcrusher :bits 4 :rate 0.25)` work
- [ ] `(fx :off :chorus)` / `(fx :on :chorus)` bypass works for all five
- [ ] No audio artifacts (clicks, pops) when enabling/disabling effects mid-playback
- [ ] All plugins follow the dry/wet pass-through pattern so the effect chain remains intact
  when any plugin is bypassed
