# Phase B — Richer Visuals + p5.js Integration

## Goal

Upgrade the visual layer with two additions:

1. **Built-in spectrum analyser** — replace (or augment) the oscilloscope with a full
   frequency-spectrum display powered by [audiomotion-analyzer](https://audiomotion.dev).
2. **p5.js plugin support** — a plugin wrapper pattern that lets users drop in or write
   p5.js sketches as REPuLse visual plugins, opening the entire p5 ecosystem for visuals.

---

## Part 1 — audiomotion-analyzer spectrum

### What it is

[audiomotion-analyzer](https://github.com/hvianna/audioMotion-analyzer) is a small
(~12 kB gzip), dependency-free JS library that wraps `AnalyserNode` and renders a
beautiful, GPU-accelerated spectrum display using a `<canvas>` element. It supports
octave-band or linear frequency scales, multiple colour gradients, mirror modes, and
peak indicators — far more capable than a hand-rolled FFT canvas draw loop.

### Implementation

Create `app/public/plugins/spectrum.js` as a visual plugin that loads audiomotion-analyzer
from a CDN and wraps it:

```javascript
export default {
  type: "visual", name: "spectrum", version: "1.0.0",

  init(host) {
    this._analyser = host.analyser;
    // Load audiomotion-analyzer from jsDelivr (ESM build)
    this._ready = import("https://cdn.jsdelivr.net/npm/audiomotion-analyzer@4/src/audiomotion-analyzer.js")
      .then(m => { this._AudioMotion = m.default; });
  },

  mount(container) {
    this._container = container;
    this._ready.then(() => {
      this._am = new this._AudioMotion(container, {
        source:     this._analyser,
        height:     120,
        gradient:   "prism",
        showPeaks:  true,
        mode:       2,          // 1/24 octave bands
        showBgColor: false,
        overlay:    true,
      });
    });
  },

  unmount() {
    if (this._am) { this._am.destroy(); this._am = null; }
  },

  destroy() { this.unmount(); },
};
```

**Key points:**
- `source: host.analyser` connects audiomotion directly to the existing AnalyserNode
  on the master bus — no additional Web Audio nodes needed.
- The `overlay: true` option renders over a transparent background, so it layers cleanly
  with the oscilloscope or other visuals.
- Loading from jsDelivr uses a stable ESM CDN link. Pin to a specific minor version
  (e.g. `@4.7`) for reproducibility.

**Lisp usage:**
```lisp
; Auto-loaded at startup alongside the oscilloscope.
; To reload with different options:
(load-plugin "/plugins/spectrum.js")
```

Auto-load in `app/src/repulse/app.cljs` `init` alongside the existing oscilloscope:
```clojure
(-> (js* "import('/plugins/spectrum.js')")
    (.then (fn [m]
             (let [plug (.-default m)]
               (plugins/register! plug (make-host))
               (mount-visual! plug)))))
```

---

## Part 2 — p5.js plugin wrapper

### What it is

[p5.js](https://p5js.org) is a browser creative-coding library beloved by artists and
musicians. Enabling p5 as a plugin backend lets users (and plugin authors) write expressive
generative visuals with minimal boilerplate, using only p5 idioms they already know.

### The wrapper pattern

A p5 plugin is a standard REPuLse visual plugin that:
1. Loads p5.js from a CDN (once, shared across plugins)
2. Accepts a user-supplied sketch function
3. Runs that sketch in p5's [instance mode](https://p5js.org/reference/#/p5/p5)
4. Exposes `host.analyser` and `host.audioCtx` to the sketch via closure

**Base plugin (`app/public/plugins/p5-base.js`):**

```javascript
// p5.js shared loader — imported by all p5 sketch plugins.
// Returns a Promise that resolves with the p5 constructor.
let _p5Promise = null;
export function loadP5() {
  if (!_p5Promise) {
    _p5Promise = import("https://cdn.jsdelivr.net/npm/p5@1/lib/p5.esm.js")
      .then(m => m.default);
  }
  return _p5Promise;
}

// Create a REPuLse visual plugin from a p5 instance-mode sketch function.
// sketchFn: (p, analyser, audioCtx) => void   (sets up p.setup and p.draw)
export function makeP5Plugin(name, version, sketchFn) {
  return {
    type: "visual", name, version,
    init(host) {
      this._analyser  = host.analyser;
      this._audioCtx  = host.audioCtx;
      this._ready = loadP5();
    },
    mount(container) {
      this._ready.then(P5 => {
        this._p5 = new P5(p => sketchFn(p, this._analyser, this._audioCtx), container);
      });
    },
    unmount() {
      if (this._p5) { this._p5.remove(); this._p5 = null; }
    },
    destroy() { this.unmount(); },
  };
}
```

**Example sketch plugin (`app/public/plugins/p5-waveform.js`):**

A particle system driven by the audio waveform — shows how a plugin author writes
a full p5 sketch in ~30 lines:

```javascript
import { makeP5Plugin } from "/plugins/p5-base.js";

export default makeP5Plugin("p5-waveform", "1.0.0", (p, analyser) => {
  const N = 1024;
  const waveform = new Uint8Array(N);

  p.setup = () => {
    p.createCanvas(p.windowWidth, 120);
    p.colorMode(p.HSB, 360, 100, 100, 100);
    p.noFill();
  };

  p.draw = () => {
    analyser.getByteTimeDomainData(waveform);
    p.background(0, 0, 10, 80);
    p.stroke(180, 80, 100);
    p.strokeWeight(1.5);
    p.beginShape();
    for (let i = 0; i < N; i++) {
      const x = p.map(i, 0, N, 0, p.width);
      const y = p.map(waveform[i], 0, 255, p.height, 0);
      p.vertex(x, y);
    }
    p.endShape();
  };
});
```

**Lisp usage:**
```lisp
; Load the built-in p5 waveform example
(load-plugin "/plugins/p5-waveform.js")

; Load a custom sketch hosted anywhere
(load-plugin "https://example.com/my-p5-sketch.js")
```

---

## Part 3 — Plugin panel layout

With multiple visual plugins mounted simultaneously (oscilloscope, spectrum, p5 waveform),
the `#plugin-panel` div needs a simple layout upgrade:

- Change from `display: block` to `display: flex; flex-direction: column; gap: 4px`
- Each mounted plugin appends its own element (canvas or div)
- Maximum panel height: `40vh` with `overflow: hidden`

Update the CSS in `app/public/css/style.css` and the panel markup in `app.cljs`.

---

## Auto-load order

In `app/src/repulse/app.cljs` `init`, the recommended startup visual load order:

1. Oscilloscope (already present)
2. Spectrum (new, added here)
3. p5-waveform (optional default — comment out if too heavy for first load)

---

## Documentation to update

- `docs/USAGE.md` — update "## Visual plugins" section with `spectrum`, p5 pattern,
  and the `makeP5Plugin` API.
- `README.md` — add spectrum and p5 plugin examples.
- `docs/ARCHITECTURE.md` — note the p5-base shared loader pattern.
- `CLAUDE.md` and `docs/ARCHITECTURE.md` phase status tables.

---

## Acceptance criteria

- [ ] Spectrum analyser auto-loads and displays a real-time frequency plot
- [ ] `(load-plugin "/plugins/spectrum.js")` reloads / replaces the spectrum plugin
- [ ] `(load-plugin "/plugins/p5-waveform.js")` loads and renders the p5 sketch
- [ ] Multiple visual plugins can be mounted simultaneously without interfering
- [ ] The plugin panel grows to accommodate new visuals and doesn't overflow
- [ ] Removing and reloading a plugin (`(load-plugin ...)` again) works correctly
- [ ] No console errors from p5 or audiomotion in a clean Chrome/Firefox/Safari run
- [ ] CDN imports are pinned to specific semver versions (no floating `@latest`)
