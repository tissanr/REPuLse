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
7. [ClojureScript plugins](#clojurescript-plugins)

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

---

## ClojureScript plugins

Plugins don't have to be JavaScript files. The built-in compressor is implemented in
ClojureScript (`app/src/repulse/plugins/compressor.cljs`). The protocol is identical —
a JS-compatible object with the required methods — but CLJS gives you access to
Clojure's immutable data model, persistent atoms, and the rest of the app namespaces.

**When to choose CLJS over JS:**
- The plugin needs to read or write app-level state (BPM atom, pattern registry, etc.)
- You want Clojure data structures for internal plugin state
- You want the plugin compiled into the main CLJS build rather than loaded as a separate module

### Creating a protocol-compatible object: `#js {}`

The plugin registry expects a plain JS object. In CLJS, use the `#js {}` literal reader
macro — keys are keywords in source, string properties in the emitted JS:

```clojure
#js {:type    "effect"
     :name    "my-effect"
     :version "1.0.0"
     :init        (fn [_host] nil)
     :createNodes (fn [ctx] ...)
     :setParam    (fn [param-name value] ...)
     :bypass      (fn [on] ...)
     :getParams   (fn [] #js {})
     :destroy     (fn [] ...)}
```

`validate!` calls `(fn? (aget plugin "createNodes"))` — this resolves correctly to the
ClojureScript function stored at that JS property.

### Closure-based state instead of `this`

JS plugins use `this._foo` for instance state, but `this` is unreliable in CLJS when
methods are passed as values (the call site controls `this`). The idiomatic alternative
is a **closure over a Clojure atom**:

```clojure
(defn make []
  (let [state (atom nil)]      ; closed over by all method fns

    #js {:type "effect"
         :name "my-effect"
         ...

         :createNodes
         (fn [ctx]
           (let [input (.createGain ctx)
                 out   (.createGain ctx)]
             ;; store all nodes in the atom — never in `this`
             (reset! state {:input input :out out})
             #js {:inputNode input :outputNode out}))

         :setParam
         (fn [param-name value]
           (when-let [{:keys [out]} @state]
             (case param-name
               "gain" (set! (.. out -gain -value) value)
               nil)))

         :destroy
         (fn []
           (when-let [{:keys [input out]} @state]
             (.disconnect input)
             (.disconnect out))
           (reset! state nil))}))

(def plugin (make))
```

`(make)` creates a fresh atom and a new set of closures. The `(def plugin (make))` at
the bottom produces the singleton instance registered in `app.cljs`.

### CLJS interop for Web Audio

| Goal | CLJS expression |
|---|---|
| Create a GainNode | `(.createGain ctx)` |
| Set a numeric property | `(set! (.. gain -gain -value) 0.5)` |
| Read a chained property | `(.. comp -threshold -value)` |
| Connect two nodes | `(.connect source destination)` |
| Return a JS object to the host | `#js {:inputNode input :outputNode out}` |
| Return JS `null` | `nil` |

### How CLJS plugins are registered

CLJS plugins compile into the main build — they don't need dynamic `import()`. Register
them directly in `app.cljs` `init` after the audio context is ready:

```clojure
(ns repulse.app
  (:require [repulse.plugins           :as plugins]
            [repulse.fx                :as fx]
            [repulse.plugins.compressor :as compressor]))

;; In init:
(plugins/register! compressor/plugin (make-host))
(fx/add-effect!    compressor/plugin)
```

The same `validate!` runs — if the `#js {}` object is missing a required method, you
get the same descriptive error as with any other plugin.

### Complete example: the compressor

`app/src/repulse/plugins/compressor.cljs` is the canonical reference. Key points:

- All mutable state in one `(atom nil)`, reset to a map of Web Audio nodes after
  `createNodes` runs
- `case param-name` for dispatch in `setParam` — exhaustive, readable, fast
- `(.. node -property -value)` chains for AudioParam access
- `#js {}` for both the plugin object and the `getParams` return value
- `(def plugin (make))` at the bottom creates the module-level singleton

### Where to put CLJS plugins

```
app/src/repulse/plugins/
├── compressor.cljs      ← existing built-in
└── my-effect.cljs       ← add new ones here
```

All files under `app/src/` are on the shadow-cljs source path and compile automatically.
Register the plugin in `app.cljs` `init` — no other wiring needed.
