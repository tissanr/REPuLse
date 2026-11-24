// LimiterProcessor (Phase FX1, D2) — look-ahead peak limiter, stereo-linked.
//
// Per frame (L = round(0.005 · sampleRate)):
//   p   = max(|l|, |r|)
//   req = p > c ? c / p : 1                       c = 10^(ceiling/20)
//   m   = min of req over the last L + 1 frames
//   env = min(m, a · env + (1 − a))               a = exp(−1 / (release · sampleRate))
//   g   = mean of env over the last L frames
//   out = in[n − L] · g
//
// The gain applied to a delayed sample is an average of envelope values that
// were all computed while that sample's req was inside the minimum window, so
// the output cannot exceed the ceiling. A signal below the ceiling passes
// unchanged (g = 1 exactly), L samples late. All buffers are allocated in the
// constructor; process() allocates nothing.

class LimiterProcessor extends AudioWorkletProcessor {
  static get parameterDescriptors() {
    return [
      { name: "ceiling", defaultValue: -1,  minValue: -24,  maxValue: 0, automationRate: "k-rate" },
      { name: "release", defaultValue: 0.1, minValue: 0.01, maxValue: 1, automationRate: "k-rate" },
    ];
  }

  constructor() {
    super();
    const L = Math.max(1, Math.round(0.005 * sampleRate));
    this._L = L;
    this._delL = new Float32Array(L);   // input delay lines
    this._delR = new Float32Array(L);
    this._dPos = 0;
    this._req = new Float64Array(L + 1).fill(1);   // required gains, window minimum
    this._rPos = 0;
    this._avg = new Float64Array(L).fill(1);       // envelope values, moving average
    this._aPos = 0;
    this._sum = L;
    this._env = 1;
    this._lastRelease = -1;
    this._a = 0;
  }

  process(inputs, outputs, parameters) {
    const out = outputs[0];
    const oL = out[0];
    const oR = out[1] ?? out[0];
    const inp = inputs[0];
    const iL = inp && inp[0];
    const iR = inp && (inp[1] ?? inp[0]);
    const n = oL.length;

    const c = Math.pow(10, parameters.ceiling[0] / 20);
    const release = parameters.release[0];
    if (release !== this._lastRelease) {
      this._lastRelease = release;
      this._a = Math.exp(-1 / (release * sampleRate));
    }
    const a = this._a;
    const L = this._L;
    const req = this._req, avg = this._avg, delL = this._delL, delR = this._delR;
    let env = this._env, sum = this._sum;
    let dPos = this._dPos, rPos = this._rPos, aPos = this._aPos;

    for (let i = 0; i < n; i++) {
      const l = iL ? iL[i] : 0;
      const r = iR ? iR[i] : 0;
      const p = Math.max(Math.abs(l), Math.abs(r));
      req[rPos] = p > c ? c / p : 1;
      rPos = rPos + 1 === L + 1 ? 0 : rPos + 1;
      let m = 1;
      for (let k = 0; k <= L; k++) if (req[k] < m) m = req[k];

      env = Math.min(m, a * env + (1 - a));

      sum += env - avg[aPos];
      avg[aPos] = env;
      aPos = aPos + 1 === L ? 0 : aPos + 1;
      if (aPos === 0) {                      // re-sum once per window: no drift
        let s = 0;
        for (let k = 0; k < L; k++) s += avg[k];
        sum = s;
      }
      const g = Math.min(1, sum / L);

      const dl = delL[dPos], dr = delR[dPos];
      delL[dPos] = l;
      delR[dPos] = r;
      dPos = dPos + 1 === L ? 0 : dPos + 1;

      oL[i] = dl * g;
      if (oR !== oL) oR[i] = dr * g;
    }

    this._env = env; this._sum = sum;
    this._dPos = dPos; this._rPos = rPos; this._aPos = aPos;
    return true;
  }
}

// A second addModule of this file (e.g. after (load-plugin …) of the built-in)
// must not fail on the already registered name.
try { registerProcessor("limiter", LimiterProcessor); } catch (_) { /* already registered */ }
