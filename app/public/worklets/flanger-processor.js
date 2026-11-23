// FlangerProcessor (Phase FX1, D9) — stereo, wet only. A native DelayNode in a
// feedback loop adds one render quantum (2.9 ms), more than a flanger's whole
// range, so the comb with feedback runs here, per sample.
//
// Per channel: a ring buffer of ceil(0.025 · sampleRate) frames;
//   d   = delay · (1 + depth · sin(phase)), clamped to 2 frames … 20 ms
//   out = the linearly interpolated sample d seconds back
//   the value written is in + feedback · out
//   phase += 2π · rate / sampleRate (the same for both channels)
// process() allocates nothing.

class FlangerProcessor extends AudioWorkletProcessor {
  static get parameterDescriptors() {
    return [
      { name: "rate",     defaultValue: 0.25,  minValue: 0.01,   maxValue: 10,    automationRate: "k-rate" },
      { name: "depth",    defaultValue: 0.7,   minValue: 0,      maxValue: 1,     automationRate: "k-rate" },
      { name: "delay",    defaultValue: 0.002, minValue: 0.0005, maxValue: 0.010, automationRate: "k-rate" },
      { name: "feedback", defaultValue: 0,     minValue: -0.95,  maxValue: 0.95,  automationRate: "k-rate" },
    ];
  }

  constructor() {
    super();
    this._n = Math.ceil(0.025 * sampleRate);
    this._bufL = new Float32Array(this._n);
    this._bufR = new Float32Array(this._n);
    this._w = 0;
    this._phase = 0;
  }

  process(inputs, outputs, parameters) {
    const out = outputs[0];
    const oL = out[0];
    const oR = out[1] ?? out[0];
    const inp = inputs[0];
    const iL = inp && inp[0];
    const iR = inp && (inp[1] ?? inp[0]);
    const frames = oL.length;

    const rate = parameters.rate[0];
    const depth = parameters.depth[0];
    const delay = parameters.delay[0];
    const fb = parameters.feedback[0];
    const dPhase = (2 * Math.PI * rate) / sampleRate;
    const maxD = Math.min(0.020 * sampleRate, this._n - 2);
    const N = this._n;
    const bL = this._bufL, bR = this._bufR;
    let w = this._w, phase = this._phase;

    for (let i = 0; i < frames; i++) {
      let d = delay * (1 + depth * Math.sin(phase)) * sampleRate;
      if (d < 2) d = 2; else if (d > maxD) d = maxD;
      let pos = w - d;
      if (pos < 0) pos += N;
      const i0 = Math.floor(pos);
      const frac = pos - i0;
      const i1 = i0 + 1 === N ? 0 : i0 + 1;
      const yL = bL[i0] + (bL[i1] - bL[i0]) * frac;
      const yR = bR[i0] + (bR[i1] - bR[i0]) * frac;
      const xL = iL ? iL[i] : 0;
      const xR = iR ? iR[i] : 0;
      bL[w] = xL + fb * yL;
      bR[w] = xR + fb * yR;
      w = w + 1 === N ? 0 : w + 1;
      oL[i] = yL;
      if (oR !== oL) oR[i] = yR;
      phase += dPhase;
      if (phase > 2 * Math.PI) phase -= 2 * Math.PI;
    }

    this._w = w; this._phase = phase;
    return true;
  }
}

try { registerProcessor("flanger", FlangerProcessor); } catch (_) { /* already registered */ }
