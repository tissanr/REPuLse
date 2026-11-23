// GateProcessor (Phase FX1, D3) — noise gate with hysteresis, hold and
// separate attack/release, stereo-linked, no look-ahead.
//
// Per frame:
//   p = max(|l|, |r|)
//   d = max(p, d · k)            k = exp(−1 / (0.010 · sampleRate))  (instant rise, 10 ms fall)
//   opens when d > threshold; starts closing once d has stayed below
//   threshold − 3 dB for `hold` seconds
//   gain → 1 (open) or 10^(range/20) (closed), one-pole with time constant
//   `attack` (opening) or `release` (closing)
// process() allocates nothing.

class GateProcessor extends AudioWorkletProcessor {
  static get parameterDescriptors() {
    return [
      { name: "threshold", defaultValue: -40,   minValue: -80,    maxValue: 0,   automationRate: "k-rate" },
      { name: "attack",    defaultValue: 0.001, minValue: 0.0001, maxValue: 0.1, automationRate: "k-rate" },
      { name: "hold",      defaultValue: 0.05,  minValue: 0,      maxValue: 2,   automationRate: "k-rate" },
      { name: "release",   defaultValue: 0.1,   minValue: 0.005,  maxValue: 2,   automationRate: "k-rate" },
      { name: "range",     defaultValue: -80,   minValue: -80,    maxValue: 0,   automationRate: "k-rate" },
    ];
  }

  constructor() {
    super();
    this._k = Math.exp(-1 / (0.010 * sampleRate));
    this._d = 0;
    this._open = false;
    this._below = 0;        // frames the detector has been below the close level
    this._gain = -1;        // set to the closed gain on the first block
  }

  process(inputs, outputs, parameters) {
    const out = outputs[0];
    const oL = out[0];
    const oR = out[1] ?? out[0];
    const inp = inputs[0];
    const iL = inp && inp[0];
    const iR = inp && (inp[1] ?? inp[0]);
    const n = oL.length;

    const thr = Math.pow(10, parameters.threshold[0] / 20);
    const closeLvl = thr * Math.pow(10, -3 / 20);
    const holdFrames = parameters.hold[0] * sampleRate;
    const cA = Math.exp(-1 / (parameters.attack[0] * sampleRate));
    const cR = Math.exp(-1 / (parameters.release[0] * sampleRate));
    const closed = Math.pow(10, parameters.range[0] / 20);
    const k = this._k;
    let d = this._d, open = this._open, below = this._below;
    let g = this._gain < 0 ? closed : this._gain;

    for (let i = 0; i < n; i++) {
      const l = iL ? iL[i] : 0;
      const r = iR ? iR[i] : 0;
      const p = Math.max(Math.abs(l), Math.abs(r));
      d = Math.max(p, d * k);
      if (d > thr) { open = true; below = 0; }
      else if (open) {
        if (d < closeLvl) { below++; if (below >= holdFrames) open = false; }
        else below = 0;
      }
      const target = open ? 1 : closed;
      const c = target > g ? cA : cR;
      g = target + (g - target) * c;
      oL[i] = l * g;
      if (oR !== oL) oR[i] = r * g;
    }

    this._d = d; this._open = open; this._below = below; this._gain = g;
    return true;
  }
}

try { registerProcessor("gate", GateProcessor); } catch (_) { /* already registered */ }
