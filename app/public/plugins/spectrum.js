// Spectrum analyser visual plugin — powered by audiomotion-analyzer.
// Renders a real-time frequency spectrum on the plugin panel canvas.
//
// Connects to the existing master AnalyserNode (host.analyser) so no extra
// Web Audio nodes are created. connectSpeakers:false prevents a duplicate
// output path to the speakers.

import { VisualPlugin } from '/plugin-base.js';

class Spectrum extends VisualPlugin {
  constructor() { super({ name: "spectrum", version: "1.0.0" }); }

  init(host) {
    this._analyser = host.analyser;
    this._am       = null;
    // Load audiomotion-analyzer lazily — pinned to 4.5.4 (native ESM source)
    this._ready = import("https://cdn.jsdelivr.net/npm/audiomotion-analyzer@4.5.4/src/audioMotion-analyzer.js")
      .then(m => { this._AudioMotion = m.default; });
  }

  mount(container) {
    this._ready.then(() => {
      this._am = new this._AudioMotion(container, {
        source:          this._analyser,
        connectSpeakers: false,   // audio already routed to destination via master bus
        height:          120,
        gradient:        "prism",
        showPeaks:       true,
        mode:            2,       // 1/12 octave bands
        showBgColor:     false,
        overlay:         true,
      });
    });
  }

  unmount() {
    if (this._am) { this._am.destroy(); this._am = null; }
  }
}

export default new Spectrum();
