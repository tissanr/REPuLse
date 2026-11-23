// Ring modulator (Phase FX1): input × carrier, crossfaded with the dry signal.
//
//   input ─┬─ dry (1 − mix) ──────────────────┬─ out
//          └─ ring (GainNode, gain 0) ─ wet ──┘      OscillatorNode(freq) → ring.gain
//
// The output of `ring` is input × carrier. mix 0 (the default) is the dry signal.

const SHAPES = { sine: "sine", tri: "triangle", square: "square", saw: "sawtooth" };
const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

function setAP(param, v, ctx, tc = 0.01) {
  if (ctx.currentTime === 0) { param.cancelScheduledValues(0); param.value = v; }
  else param.setTargetAtTime(v, ctx.currentTime, tc);
}

function make() {
  return {
    type: "effect", name: "ring-mod", version: "1.0.0",
    params: { value: "mix", mix: "number", freq: "number", shape: Object.keys(SHAPES) },

    init(host) {},

    createNodes(ctx) {
      this._ctx = ctx;
      this._input = ctx.createGain();
      this._dry = ctx.createGain();
      this._ring = ctx.createGain();
      this._wet = ctx.createGain();
      this._out = ctx.createGain();
      this._osc = ctx.createOscillator();
      this._mixV = 0; this._freq = 440; this._shape = "sine";
      this._bypassed = false;

      this._ring.gain.value = 0;
      this._osc.type = "sine";
      this._osc.frequency.value = 440;
      this._input.connect(this._dry);
      this._input.connect(this._ring);
      this._ring.connect(this._wet);
      this._dry.connect(this._out);
      this._wet.connect(this._out);
      this._osc.connect(this._ring.gain);
      this._mix();
      this._osc.start(0);
      return { inputNode: this._input, outputNode: this._out };
    },

    _mix() {
      const m = this._bypassed ? 0 : this._mixV;
      setAP(this._dry.gain, 1 - m, this._ctx);
      setAP(this._wet.gain, m, this._ctx);
    },

    setParam(name, value) {
      if (!this._ctx) return;
      if (name === "shape") {
        const s = String(value).replace(/^:/, "");
        if (!SHAPES[s]) return;
        this._shape = s;
        this._osc.type = SHAPES[s];
        return;
      }
      const v = Number(value);
      if (!Number.isFinite(v)) return;
      if (name === "mix" || name === "value") { this._mixV = clamp(v, 0, 1); this._mix(); }
      else if (name === "freq") { this._freq = clamp(v, 0.1, 5000); setAP(this._osc.frequency, this._freq, this._ctx); }
    },

    bypass(on) { this._bypassed = !!on; this._mix(); },

    resetParams() {
      if (!this._ctx) return;
      this._mixV = 0; this._freq = 440; this._shape = "sine";
      this._bypassed = false;
      this._osc.type = "sine";
      setAP(this._osc.frequency, 440, this._ctx);
      this._mix();
    },

    getParams() { return { mix: this._mixV, freq: this._freq, shape: this._shape }; },

    destroy() {
      try { this._osc?.stop(); } catch (_) { /* not started */ }
      if (this._input) this._input.disconnect();
      if (this._out) this._out.disconnect();
    },

    clone() { return make(); },
  };
}

export default make();
