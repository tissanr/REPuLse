# REPuLse — Plugin Development

Plugins extend REPuLse with new visuals and audio effects. They are plain ES modules
loaded at runtime — no build step, no ClojureScript knowledge required.

Two plugin types exist:

| Type | Purpose | Interface |
|---|---|---|
| `visual` | Draw to a canvas panel below the editor | `VisualPlugin` |
| `effect` | Insert a Web Audio sub-graph into the master signal chain | `EffectPlugin` |

---

## Table of Contents

1. [Loading plugins](#loading-plugins)
2. [The Host API](#the-host-api)
3. [Visual plugins](#visual-plugins)
4. [Effect plugins](#effect-plugins)
5. [Registration and validation](#registration-and-validation)
6. [Plain object style](#plain-object-style)

---

## Loading plugins

Any ES module that exports a plugin as its default export can be loaded at runtime:

```lisp
(load-plugin "/plugins/my-effect.js")          ; served from your dev server
(load-plugin "https://example.com/spectrum.js") ; remote URL
```

Loading a plugin with the same `name` as an existing one destroys the old registration
and replaces it. The built-in oscilloscope can be reloaded this way to reset it.

---

## The Host API

Every plugin receives a `host` object in its `init` method. Store any references you
need from it — `init` is called once.

| Property | Type | Description |
|---|---|---|
| `host.audioCtx` | `AudioContext` | The shared audio context |
| `host.analyser` | `AnalyserNode` | Permanent node on the master bus (fftSize 2048, smoothing 0.8) |
| `host.masterGain` | `GainNode` | Master volume control point |
| `host.sampleRate` | `number` | `audioCtx.sampleRate` (convenience) |
| `host.registerLisp(name, fn)` | `function` | Add a new built-in to the Lisp environment |

Visual plugins typically store `host.analyser` for their render loop. Effect plugins
rarely need `host` at all — their audio graph is built in `createNodes` which receives
the `AudioContext` directly.

---

## Visual plugins

### Extending `VisualPlugin`

Import the base class from `/plugin-base.js`:

```javascript
import { VisualPlugin } from '/plugin-base.js';

export default class SpectrumBars extends VisualPlugin {
  constructor() {
    super({ name: "spectrum-bars", version: "1.0.0" });
  }

  init(host) {
    this._analyser = host.analyser;
  }

  mount(container) {
    this._canvas        = document.createElement("canvas");
    this._canvas.width  = container.clientWidth || 600;
    this._canvas.height = 80;
    container.appendChild(this._canvas);
    this._running = true;
    this._draw();
  }

  unmount() {
    this._running = false;
    cancelAnimationFrame(this._raf);
    this._canvas?.remove();
    this._canvas = null;
  }

  // _draw is private — not part of the protocol
  _draw() {
    if (!this._running) return;
    const ctx = this._canvas.getContext("2d");
    const buf = new Uint8Array(this._analyser.frequencyBinCount);
    this._analyser.getByteFrequencyData(buf);
    const W = this._canvas.width, H = this._canvas.height;
    const bw = W / buf.length;
    ctx.clearRect(0, 0, W, H);
    ctx.fillStyle = "#7fffff";
    for (let i = 0; i < buf.length; i++) {
      const h = (buf[i] / 255) * H;
      ctx.fillRect(i * bw, H - h, bw - 1, h);
    }
    this._raf = requestAnimationFrame(() => this._draw());
  }
}
```

### Visual plugin protocol

| Method | Required | Default | Description |
|---|---|---|---|
| `constructor({ name, version })` | — | — | Sets `this.type = "visual"` automatically |
| `init(host)` | optional | no-op | Store host references. Called once at registration. |
| `mount(container)` | **required** | throws | Append DOM, start render loop. `container` is a `div`. |
| `unmount()` | **required** | throws | Stop render loop, remove DOM. |
| `destroy()` | optional | calls `unmount()` | Full teardown. Override only if you hold extra refs. |

The `AnalyserNode` provides two data sources for drawing:

```javascript
// Time-domain waveform (oscilloscope)
const buf = new Uint8Array(this._analyser.fftSize);           // 2048 samples
this._analyser.getByteTimeDomainData(buf);                    // 0–255, centre = 128

// Frequency spectrum
const buf = new Uint8Array(this._analyser.frequencyBinCount); // 1024 bins
this._analyser.getByteFrequencyData(buf);                     // 0–255 magnitude
```

---

## Effect plugins

### Extending `EffectPlugin`

Import the base class from `/plugin-base.js`:

```javascript
import { EffectPlugin } from '/plugin-base.js';

export default class Tremolo extends EffectPlugin {
  constructor() {
    super({ name: "tremolo", version: "1.0.0" });
  }

  createNodes(ctx) {
    this._input = ctx.createGain();
    this._out   = ctx.createGain();
    this._lfo   = ctx.createOscillator();
    this._depth = ctx.createGain();

    this._lfo.type            = "sine";
    this._lfo.frequency.value = 4;    // 4 Hz tremolo rate
    this._depth.gain.value    = 0.5;  // depth: 0 = no modulation, 1 = full

    // LFO → depth gain → modulates output amplitude
    this._lfo.connect(this._depth);
    this._depth.connect(this._out.gain);
    this._input.connect(this._out);
    this._lfo.start();

    return { inputNode: this._input, outputNode: this._out };
  }

  setParam(name, value) {
    if (name === "rate")  this._lfo.frequency.value = value;
    if (name === "depth") this._depth.gain.value    = Math.max(0, Math.min(1, value));
  }

  bypass(on) {
    if (on) {
      this._savedDepth       = this._depth.gain.value;
      this._depth.gain.value = 0;      // freeze modulation
      this._out.gain.value   = 1;      // restore output to unity
    } else {
      this._depth.gain.value = this._savedDepth ?? 0.5;
    }
  }

  getParams() {
    return {
      rate:  this._lfo?.frequency.value,
      depth: this._depth?.gain.value,
    };
  }

  destroy() {
    this._lfo?.stop();
    this._input?.disconnect();
    this._out?.disconnect();
  }
}
```

After loading, control the tremolo from the REPL:

```lisp
(load-plugin "/plugins/tremolo.js")
(fx :tremolo :rate 6 :depth 0.7)   ; 6 Hz, deep modulation
(fx :off :tremolo)                  ; bypass
```

### Effect plugin protocol

| Method | Required | Default | Description |
|---|---|---|---|
| `constructor({ name, version })` | — | — | Sets `this.type = "effect"` automatically |
| `init(host)` | optional | no-op | Called once at registration. Use for async setup (e.g. `addModule`). |
| `createNodes(ctx)` | **required** | throws | Build the audio sub-graph. Return `{ inputNode, outputNode }`. Must be synchronous. |
| `setParam(name, value)` | **required** | throws | Update a named parameter. |
| `bypass(on)` | optional | no-op | `on = true` → transparent pass-through. `on = false` → restore. |
| `getParams()` | optional | `{}` | Return current `{ name: value }` state. |
| `destroy()` | **required** | throws | Disconnect all nodes and release references. |

### Audio graph contract

`createNodes(ctx)` must return an object with exactly these two properties:

```javascript
return { inputNode: AudioNode, outputNode: AudioNode }
```

REPuLse connects `masterGain → inputNode` and `outputNode → nextEffect.inputNode`
(or `outputNode → analyser` if this is the last effect). Failing to return this object
will produce silence and a disconnected graph.

### Dry/wet topology

The built-in effects use an explicit dry/wet gain structure for click-free bypassing:

```
inputNode ─┬─── dry GainNode ───────────────────────┬── outputNode
           └─── processing nodes ─── wet GainNode ──┘
```

This pattern lets `bypass` silence the wet path without touching the dry path, avoiding
audio clicks. It is the recommended topology for any effect with a mix control.

---

## Registration and validation

When `(load-plugin url)` is called, REPuLse:

1. Dynamically imports the ES module
2. Calls `plugins/register!` with the default export
3. `register!` runs `validate!` — checking that all required methods exist
4. If validation passes, calls `plugin.init(host)` and adds the plugin to the registry

**If a required method is missing**, registration throws with a descriptive error:

```
[REPuLse] Plugin "tremolo" is missing required method(s): createNodes, destroy
```

This check works for both class instances (methods resolved via prototype chain) and
plain JS objects, so the feedback is always accurate regardless of authoring style.

---

## Plain object style

If you prefer not to import the base class, you can export a plain object directly.
All methods the protocol marks as "optional" must then be provided explicitly:

```javascript
// Visual plugin — plain object
export default {
  type: "visual", name: "oscilloscope", version: "1.0.0",

  init(host)      { this._analyser = host.analyser; },
  mount(container){ /* ... */ },
  unmount()       { /* ... */ },
  destroy()       { this.unmount(); this._analyser = null; },
};

// Effect plugin — plain object
export default {
  type: "effect", name: "reverb", version: "1.0.0",

  init(host)           {},
  createNodes(ctx)     { /* ... */ return { inputNode, outputNode }; },
  setParam(name, value){ /* ... */ },
  bypass(on)           { /* ... */ },
  getParams()          { return { wet: this._wet?.gain.value }; },
  destroy()            { /* ... */ },
};
```

Both styles pass the same validation. Use the base class for shorter code and clearer
error messages when you forget an abstract method; use a plain object when you want
zero dependencies or are porting an existing plugin.
