# REPuLse — Plugin System + Visual Plugins (Phase 6a)

## Context

REPuLse is a browser-based live coding instrument. Phases 1–3 delivered the pattern engine,
WASM synthesis, and AudioWorklet. Phase 5 adds active code highlighting. This phase introduces
the **plugin system** — a lightweight extension API — and ships the first plugin type:
**visual plugins** that receive the audio stream and draw to a canvas.

Current state:
- `app/src/repulse/audio.cljs` — `WorkletNode → destination` (nothing tapped between them)
- `app/src/repulse/app.cljs` — CodeMirror editor, play/stop button, footer output line
- No plugin registry, no canvas UI, no AnalyserNode

---

## Goal for this session

By the end of this session:

1. A **plugin registry** exists — plugins can be registered and listed
2. A permanent **`AnalyserNode` tap** sits on the master bus between the worklet and destination
3. A **plugin panel** appears below the editor when at least one visual plugin is active
4. The **host API** is well-defined and passed to every plugin on `init`
5. The first built-in visual plugin **oscilloscope** is included and active by default
6. A `(load-plugin url)` Lisp built-in dynamically loads a third-party plugin from a URL
7. All existing patterns play correctly — the AnalyserNode adds no audible change

---

## Audio graph change

Insert a permanent `AnalyserNode` and a master `GainNode` between the worklet and destination:

```
WorkletNode ──► masterGain ──► analyser ──► destination
```

The `analyser` is created once in `audio.cljs` alongside the `AudioContext`:

```clojure
(defonce master-gain  (atom nil))
(defonce analyser-node (atom nil))

(defn- build-master-chain! [ac]
  (let [gain (doto (.createGain ac)
               (-> .-gain (.setValueAtTime 1.0 (.-currentTime ac))))
        anl  (doto (.createAnalyser ac)
               (aset "fftSize" 2048)
               (aset "smoothingTimeConstant" 0.8))]
    (.connect gain anl)
    (.connect anl (.-destination ac))
    (reset! master-gain gain)
    (reset! analyser-node anl)))
```

`init-worklet!` connects the worklet node to `@master-gain` instead of `destination`.

Expose `analyser-node` publicly so plugins can read it.

---

## Plugin registry

**New file: `app/src/repulse/plugins.cljs`**

```clojure
(ns repulse.plugins)

;; Map of plugin-name → {:plugin js-obj :container dom-el :type keyword}
(defonce registry (atom {}))

(defn register! [plugin host]
  (let [name (.-name plugin)]
    (swap! registry assoc name {:plugin plugin :type (keyword (.-type plugin))})
    (.init plugin host)))

(defn unregister! [name]
  (when-let [{:keys [plugin]} (get @registry name)]
    (.destroy plugin)
    (swap! registry dissoc name)))

(defn visual-plugins []
  (filter #(= :visual (:type %)) (vals @registry)))
```

---

## Host API

The host object is passed to every plugin's `init` call. It is a plain JS object constructed
in `app.cljs`:

```clojure
(defn make-host []
  #js {:audioCtx    (audio/get-ctx)
       :analyser    @audio/analyser-node
       :masterGain  @audio/master-gain
       :sampleRate  (.-sampleRate (audio/get-ctx))
       :version     "1.0.0"
       ;; Register a Lisp built-in name → ClojureScript function
       :registerLisp (fn [name f]
                       (swap! env-atom assoc name f))})
```

Plugins receive this object and can store whatever references they need from it.

---

## Plugin interface (JavaScript)

Plugins are plain ES module default exports:

```javascript
export default {
  type:    "visual",
  name:    "oscilloscope",
  version: "1.0.0",

  init(host) {
    this._analyser = host.analyser;
    this._running  = false;
    this._canvas   = null;
    this._raf      = null;
  },

  mount(container) {
    this._canvas = document.createElement("canvas");
    this._canvas.width  = container.clientWidth  || 600;
    this._canvas.height = 80;
    container.appendChild(this._canvas);
    this._running = true;
    this._draw();
  },

  unmount() {
    this._running = false;
    if (this._raf) cancelAnimationFrame(this._raf);
    if (this._canvas) this._canvas.remove();
    this._canvas = null;
  },

  destroy() {
    this.unmount();
    this._analyser = null;
  },

  _draw() {
    if (!this._running) return;
    const ctx    = this._canvas.getContext("2d");
    const buf    = new Uint8Array(this._analyser.fftSize);
    this._analyser.getByteTimeDomainData(buf);
    const W = this._canvas.width, H = this._canvas.height;
    ctx.clearRect(0, 0, W, H);
    ctx.strokeStyle = "#7fffff";
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    for (let i = 0; i < buf.length; i++) {
      const x = (i / buf.length) * W;
      const y = (buf[i] / 128.0) * (H / 2);
      i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
    }
    ctx.stroke();
    this._raf = requestAnimationFrame(() => this._draw());
  }
};
```

Ship this as `app/public/plugins/oscilloscope.js`.

---

## Plugin panel (DOM)

Add a `<div id="plugin-panel">` below the editor in `build-dom!`. It is hidden by default
and shown whenever at least one visual plugin is mounted.

```html
<div id="plugin-panel" class="plugin-panel hidden"></div>
```

When a visual plugin is registered, call `plugin.mount(panel-el)` and remove the `hidden` class.
When all visual plugins are unregistered, re-add `hidden`.

```clojure
(defn mount-visual! [plugin]
  (let [panel (el "plugin-panel")]
    (.mount plugin panel)
    (.remove (.-classList panel) "hidden")))
```

---

## `(load-plugin url)` Lisp built-in

Add to `make-env` in `eval.cljs`:

```clojure
"load-plugin" (fn [url]
                (-> (js/import url)
                    (.then (fn [m]
                             (plugins/register! (.-default m) (app/make-host))
                             (when (= "visual" (.-type (.-default m)))
                               (plugins/mount-visual! (.-default m)))))
                    (.catch (fn [e]
                              (js/console.warn "[REPuLse] Plugin load failed:" e))))
                nil)
```

Usage from the REPL:

```lisp
(load-plugin "/plugins/oscilloscope.js")
(load-plugin "https://example.com/my-spectrum.js")
```

---

## Built-in oscilloscope auto-loaded at startup

In `app.cljs` `init`, auto-load the oscilloscope plugin after the audio context is ready:

```clojure
;; Auto-load built-in visual plugins
(-> (js/import "/plugins/oscilloscope.js")
    (.then (fn [m]
             (plugins/register! (.-default m) (make-host))
             (plugins/mount-visual! (.-default m)))))
```

---

## CSS

```css
.plugin-panel {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  background: #0a0a0a;
  border-top: 1px solid #222;
  padding: 4px;
  min-height: 88px;
}

.plugin-panel.hidden {
  display: none;
}

.plugin-panel canvas {
  flex: 1;
  min-width: 200px;
  height: 80px;
  background: #111;
  border-radius: 3px;
}
```

---

## Repository structure changes

```
app/
├── public/
│   └── plugins/
│       └── oscilloscope.js     NEW — built-in oscilloscope visual plugin
└── src/repulse/
    ├── audio.cljs               updated — AnalyserNode tap, masterGain
    ├── app.cljs                 updated — plugin panel DOM, make-host, auto-load
    └── plugins.cljs             NEW — plugin registry
```

---

## Definition of Done

- [ ] `AnalyserNode` sits on the master bus — verified: no audible change to sound
- [ ] Oscilloscope draws a waveform in the plugin panel below the editor
- [ ] `(load-plugin "/plugins/oscilloscope.js")` can be evaluated from the REPL
- [ ] Loading the same plugin twice replaces the existing registration (no duplicates)
- [ ] Plugin panel is hidden when no visual plugins are active
- [ ] `(stop)` still works; oscilloscope continues drawing silence after stop
- [ ] Browser console shows no errors on startup

---

## What NOT to do in this phase

- No effect plugins (Phase 6b)
- No plugin settings UI or persistence
- No MIDI, OSC, or recorder plugins
- No changes to the pattern engine, Lisp language, or WASM synthesis
- No spectrum analyser plugin yet — oscilloscope only, to keep the phase focused
