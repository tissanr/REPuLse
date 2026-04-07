# Phase DST6 — Cabinet Simulation

## Goal

Add `(fx :cab ...)` — convolution-based speaker cabinet simulation using
Web Audio's `ConvolverNode` with procedurally generated impulse responses.
Best combined with `(fx :amp-sim ...)` from Phase DST3.

```lisp
;; Full amp chain: distortion → cabinet:
(->> (seq :e2 :_ :e2 :g2)
     (synth :saw)
     (fx :amp-sim :gain 30 :stages 3 :tonestack :mid-scoop)
     (fx :cab :ir :4x12))

;; Distort + cabinet:
(->> (seq :c2 :e2 :g2 :c3)
     (synth :saw)
     (fx :distort :drive 10)
     (fx :cab :ir :1x12 :mix 0.8))

;; Bypass for A/B comparison:
(->> (seq :c2 :e2) (synth :saw)
     (fx :distort :drive 8)
     (fx :cab :ir :di))
```

---

## Background

### Depends on Phase DST1

`:cab` is an independent effect — it does not depend on DST2–DST5. It is most
musically useful after `:distort` or `:amp-sim`, but it works in any position in
the chain, or standalone on clean synths.

### Why cabinet simulation matters

A raw distorted signal without speaker coloration sounds harsh, fizzy, and
unpleasant — this is the main reason "amp sim without cab" sounds digital. Speaker
cabinets act as bandpass filters with complex resonance peaks that emphasise the
musical frequencies and roll off the harshness above ~5 kHz. Cabinet simulation
is the single biggest factor in making digital distortion sound natural.

### `ConvolverNode` — no manual convolution needed

Web Audio's `ConvolverNode` performs FFT-partitioned convolution. Feed it an impulse
response `AudioBuffer` and it produces convolved output in real time. This is the
exact use case `ConvolverNode` was designed for. Do NOT implement convolution manually.

### Cabinet IRs as procedural synthesis

Real cabinet impulse responses are recorded in studios from physical cabinets. Using
them would require licensing or copyright clearance. Instead, generate synthetic IRs
procedurally: a short burst of noise shaped by the cabinet's characteristic EQ profile.
Synthetic IRs don't sound exactly like the real thing, but they produce convincing
bandpass-filtered coloration at zero legal risk.

The three cabinet types each get their own filter chain applied to white noise:

| IR keyword | Frequency range | Character |
|------------|----------------|-----------|
| `:1x12`    | 150–4000 Hz    | Tight, clear — jazz/blues |
| `:2x12`    | 100–5000 Hz    | Balanced — rock, two resonance peaks |
| `:4x12`    | 80–4500 Hz     | Heavy, dark low-end — metal/classic rock |

`:di` bypasses the `ConvolverNode` entirely (dry signal passthrough).

---

## Implementation

### `app/public/plugins/cab.js`

```javascript
// Cabinet IR specifications:
// Each spec defines the filter chain applied to white noise to generate the IR.
// duration: IR length in seconds (max 0.2 = 200ms at 48kHz = 9600 samples)
const CAB_SPECS = {
  "1x12": {
    duration: 0.12,
    decay: 4.0,
    filters: [
      { type: "highpass",  freq: 150,  Q: 0.8 },  // cut deep sub
      { type: "lowpass",   freq: 4000, Q: 0.8 },  // cut harsh highs
      { type: "peaking",   freq: 2500, Q: 2.0, gain: 6 },  // presence peak
    ],
  },
  "2x12": {
    duration: 0.15,
    decay: 3.5,
    filters: [
      { type: "highpass",  freq: 100,  Q: 0.7 },
      { type: "lowpass",   freq: 5000, Q: 0.7 },
      { type: "peaking",   freq: 1000, Q: 1.5, gain: 3 },  // low-mid warmth
      { type: "peaking",   freq: 3500, Q: 2.0, gain: 4 },  // presence
    ],
  },
  "4x12": {
    duration: 0.18,
    decay: 3.0,
    filters: [
      { type: "highpass",  freq: 80,   Q: 0.7 },
      { type: "lowpass",   freq: 4500, Q: 0.7 },
      { type: "peaking",   freq: 800,  Q: 1.5, gain: 5 },  // low-mid bump
      { type: "lowshelf",  freq: 200,  gain: 4 },           // extra low-end
      { type: "highshelf", freq: 4000, gain: -4 },          // tuck the highs
    ],
  },
};

function generateIR(ctx, spec) {
  const sr     = ctx.sampleRate;
  const length = Math.floor(sr * spec.duration);
  const buf    = ctx.createBuffer(2, length, sr);

  for (let ch = 0; ch < 2; ch++) {
    const d = buf.getChannelData(ch);
    // White noise burst with exponential decay
    for (let i = 0; i < length; i++) {
      const decay = Math.pow(1 - i / length, spec.decay);
      d[i] = (Math.random() * 2 - 1) * decay;
    }
  }

  // Apply the filter chain offline using OfflineAudioContext
  // This is done synchronously via a workaround: render the filter chain
  // on the buffer using ScriptProcessor sampling, or use OfflineAudioContext.
  // Use OfflineAudioContext for correctness:
  // NOTE: OfflineAudioContext.startRendering() is async.
  // Return a Promise<AudioBuffer>.
  return new Promise((resolve) => {
    const offCtx = new OfflineAudioContext(2, length, sr);
    const src = offCtx.createBufferSource();
    src.buffer = buf;

    // Chain the filters
    let node = src;
    for (const f of spec.filters) {
      const filt = offCtx.createBiquadFilter();
      filt.type = f.type;
      filt.frequency.value = f.freq ?? 1000;
      filt.Q.value = f.Q ?? 1.0;
      if (f.gain !== undefined) filt.gain.value = f.gain;
      node.connect(filt);
      node = filt;
    }
    node.connect(offCtx.destination);
    src.start(0);

    offCtx.startRendering().then(resolve);
  });
}

export default {
  type: "effect", name: "cab", version: "1.0.0",

  _ir: "1x12",
  _mix: 1.0,
  _bypassed: false,
  _irBuffers: {},  // cache: ir name → AudioBuffer

  init(host) {
    this._host = host;
  },

  createNodes(ctx) {
    this._ctx     = ctx;
    this._input   = ctx.createGain();
    this._conv    = ctx.createConvolver();
    this._wetGain = ctx.createGain();
    this._dryGain = ctx.createGain();
    this._out     = ctx.createGain();

    this._dryGain.gain.value = 1 - this._mix;
    this._wetGain.gain.value = this._mix;

    // Routing:
    //   input → dry → out
    //   input → conv → wet → out
    this._input.connect(this._dryGain);
    this._input.connect(this._conv);
    this._conv.connect(this._wetGain);
    this._dryGain.connect(this._out);
    this._wetGain.connect(this._out);

    // Load the default IR asynchronously
    this._loadIR(this._ir);

    return { inputNode: this._input, outputNode: this._out };
  },

  _loadIR(name) {
    if (name === "di") {
      // Bypass mode: disconnect conv, route input directly to wet path
      this._conv.buffer = null;
      return;
    }
    const spec = CAB_SPECS[name];
    if (!spec) {
      console.warn(`[cab] unknown :ir "${name}", using 1x12`);
      this._loadIR("1x12");
      return;
    }
    if (this._irBuffers[name]) {
      this._conv.buffer = this._irBuffers[name];
      return;
    }
    generateIR(this._ctx, spec).then((buf) => {
      this._irBuffers[name] = buf;
      if (this._ir === name) {
        this._conv.buffer = buf;
      }
    });
  },

  setParam(name, value) {
    const now = this._ctx?.currentTime ?? 0;

    if (name === "ir") {
      const irName = (typeof value === "string" ? value : String(value)).replace(/^:/, "");
      this._ir = irName;
      if (irName === "di") {
        this._conv.buffer = null;
      } else {
        this._loadIR(irName);
      }
    }

    if (name === "mix") {
      this._mix = Math.max(0, Math.min(1, value));
      this._dryGain.gain.linearRampToValueAtTime(1 - this._mix, now + 0.02);
      this._wetGain.gain.linearRampToValueAtTime(this._mix,     now + 0.02);
    }
  },

  bypass(on) {
    const now = this._ctx?.currentTime ?? 0;
    this._bypassed = on;
    this._wetGain.gain.linearRampToValueAtTime(on ? 0 : this._mix,       now + 0.02);
    this._dryGain.gain.linearRampToValueAtTime(on ? 1 : (1 - this._mix), now + 0.02);
  },

  getParams() {
    return { ir: this._ir, mix: this._mix };
  },

  destroy() {
    // Release IR buffer references so GC can reclaim them
    this._irBuffers = {};
    try { this._input.disconnect(); } catch (_) {}
    try { this._out.disconnect();   } catch (_) {}
  },
};
```

### `ConvolverNode` latency

`ConvolverNode` introduces some inherent latency due to FFT block processing. For
short IRs (< 200ms), this is typically one render quantum (~2.6ms at 48kHz/128 frames)
plus the FFT block overhead. In practice it is below 10ms and is inaudible in a musical
context. No compensation is needed. Document this in a comment.

### WASM path note

`:cab` is Web Audio only — the WASM worklet processes individual note events, not the
master bus signal. The `:cab` plugin sits on the master bus (or track bus) in the
`fx.cljs` chain, downstream of the WASM output, so it operates on the mixed audio
signal. No WASM changes are needed or desired.

### Register in `app/src/repulse/app.cljs`

```clojure
(doseq [url [...
             "/plugins/distort.js"
             "/plugins/amp-sim.js"
             "/plugins/waveshape.js"
             "/plugins/cab.js"]]   ; ← add this
  ...)
```

### Grammar and completions

Add `"cab"` to `BuiltinName` in the grammar. Run `npm run gen:grammar`.

```javascript
{ label: "cab", type: "keyword", detail: "effect — speaker cabinet simulation" },
```

### Hover docs

```
"cab": `(fx :cab [:ir kw] [:mix N])
Speaker cabinet impulse response convolution.
  :ir    :1x12 | :2x12 | :4x12 | :di  cabinet type (default :1x12)
         :1x12 — 1×12\" speaker (tight/clear), :2x12 — 2×12\" (balanced),
         :4x12 — 4×12\" (heavy/dark), :di — bypass (dry signal)
  :mix   0.0–1.0  dry/wet blend (default 1.0)
Pairs well with (fx :amp-sim ...) or (fx :distort ...).`,
```

### `docs/USAGE.md` update

Add `:cab` to the effects table. Add an "Amp chain" section to examples:

```lisp
;; Classic amp + cab chain:
(->> (seq :e2 :_ :e2 :g2)
     (synth :saw)
     (fx :amp-sim :gain 12 :stages 3 :tonestack :bright)
     (fx :cab :ir :2x12))

;; Heavy metal:
(->> (seq :c1 :_ :c1 :_ :c1 :_)
     (synth :square)
     (fx :amp-sim :gain 80 :stages 4 :tonestack :mid-scoop)
     (fx :cab :ir :4x12))

;; A/B: cab vs. no cab
(def no-cab (->> (seq :c2 :e2 :g2) (synth :saw) (fx :distort :drive 10)))
(def with-cab (->> (seq :c2 :e2 :g2) (synth :saw)
                   (fx :distort :drive 10)
                   (fx :cab :ir :1x12)))
```

---

## Files to change

| File | Change |
|------|--------|
| `app/public/plugins/cab.js` | **New** — `:cab` effect plugin with procedural IRs |
| `app/src/repulse/app.cljs` | Add `"/plugins/cab.js"` to auto-load list |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `"cab"` to `BuiltinName` |
| `app/src/repulse/lisp-lang/completions.js` | Add `:cab` entry |
| hover docs map | Add docs for `:cab` |
| `docs/USAGE.md` | Add `:cab` to effects table + amp chain examples |
| `CLAUDE.md` | Mark DST6 as ✓ delivered |

Run `npm run gen:grammar` after editing the grammar.

No changes to `packages/core/`, `packages/lisp/`, or `packages/audio/`.
No changes to `fx.cljs`, `eval.cljs`, `distort.js`, or `amp-sim.js`.

---

## Definition of done

- [ ] `(fx :cab :ir :1x12)` after `(fx :distort :drive 10)` produces a distinctly
      different (darker, more shaped) tone than the bare distortion
- [ ] `:ir :2x12` and `:ir :4x12` produce audibly different frequency responses from `:1x12`
- [ ] `:ir :di` produces output identical to bypassed input (null convolution)
- [ ] `(fx :off :cab)` passes dry signal; `(fx :on :cab)` restores convolution
- [ ] `:mix 0.0` passes dry signal; `:mix 1.0` passes fully convolved signal
- [ ] Switching `:ir` from `:1x12` to `:4x12` while playing updates the cabinet
      without crashing (may have a brief crossfade gap while async IR generates)
- [ ] `:ir :unknown` → console warning, falls back to `1x12`
- [ ] No `AudioBuffer` memory leaks when switching IRs repeatedly (IRs are cached per-name)
- [ ] Works in `->>` chains with all existing effects
- [ ] ConvolverNode latency is below 10ms (verify via Web Audio inspector or manual test)
- [ ] Grammar change committed with regenerated `parser.js`
- [ ] All existing core tests pass (`npm run test:core`)
- [ ] No audio glitches or dropouts in the browser console

---

## What NOT to do in this phase

- Do not implement manual convolution — use `ConvolverNode`
- Do not load external WAV files for IRs — only procedural generation
- Do not add user IR loading (`load-ir`) in this phase (stretch goal for a future phase)
- Do not modify the WASM audio engine
- Do not change how `fx.cljs` or the master bus chain works
