// p5.js shared loader and plugin factory for REPuLse visual plugins.
//
// Usage — write a sketch plugin in ~30 lines:
//
//   import { makeP5Plugin } from "/plugins/p5-base.js";
//
//   export default makeP5Plugin("my-sketch", "1.0.0", (p, analyser, audioCtx) => {
//     p.setup = () => { p.createCanvas(p.windowWidth, 120); };
//     p.draw  = () => { /* draw using p5 API */ };
//   });
//
// makeP5Plugin returns a ready-to-register VisualPlugin instance.
// p5.js (v1.11.11) is loaded once from esm.sh and shared across all p5 sketch plugins.

import { VisualPlugin } from '/plugin-base.js';

// Shared p5.js loader — resolves to the p5 constructor.
// esm.sh provides an ESM wrapper for p5 v1.x (no native ESM build exists in that branch).
// Pinned to 1.11.11.
let _p5Promise = null;

export function loadP5() {
  if (!_p5Promise) {
    _p5Promise = import("https://esm.sh/p5@1.11.11")
      .then(m => m.default);
  }
  return _p5Promise;
}

/**
 * Create a REPuLse visual plugin from a p5 instance-mode sketch function.
 *
 * @param {string}   name      Plugin name (must be unique in the registry)
 * @param {string}   version   Semver string
 * @param {Function} sketchFn  (p, analyser, audioCtx) => void
 *                             Sets up p.setup and p.draw using p5 instance API.
 * @returns {VisualPlugin}     A ready-to-register plugin instance.
 */
export function makeP5Plugin(name, version, sketchFn) {
  class P5Plugin extends VisualPlugin {
    constructor() { super({ name, version }); }

    init(host) {
      this._analyser = host.analyser;
      this._audioCtx = host.audioCtx;
      this._ready    = loadP5();
      this._p5       = null;
    }

    mount(container) {
      this._ready.then(P5 => {
        this._p5 = new P5(p => sketchFn(p, this._analyser, this._audioCtx), container);
      });
    }

    unmount() {
      if (this._p5) { this._p5.remove(); this._p5 = null; }
    }
  }

  return new P5Plugin();
}
