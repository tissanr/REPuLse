// Vibrato (Phase FX1): pitch wobble from a sine-modulated delay, native nodes.
//
//   input ─┬─ dry ───────────────────┬─ out
//          └─ DelayNode(0.05) ─ wet ─┘      OscillatorNode(rate) → GainNode(A) → delayTime
//
// The pitch swing of a modulated delay is 2π · rate · A, so
// A = min(0.02, (2^(depth/1200) − 1) / (2π · rate)) s; the base delay is A + 1 ms.
// Fully wet when active; inactive (dry 1 / wet 0) until a parameter is set.

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

function setAP(param, v, ctx, tc = 0.02) {
  if (ctx.currentTime === 0) { param.cancelScheduledValues(0); param.value = v; }
  else param.setTargetAtTime(v, ctx.currentTime, tc);
}

function make() {
  return {
    type: "effect", name: "vibrato", version: "1.0.0",
    params: { value: "depth", depth: "number", rate: "number" },

    init(host) {},

    createNodes(ctx) {
      this._ctx = ctx;
      this._input = ctx.createGain();
      this._dry = ctx.createGain();
      this._wet = ctx.createGain();
      this._out = ctx.createGain();
      this._delay = ctx.createDelay(0.05);
      this._osc = ctx.createOscillator();
      this._mod = ctx.createGain();
      this._depth = 20;
      this._rate = 5;
      this._active = false;
      this._bypassed = false;

      this._osc.type = "sine";
      this._dry.gain.value = 1;
      this._wet.gain.value = 0;
      this._input.connect(this._dry);
      this._input.connect(this._delay);
      this._delay.connect(this._wet);
      this._dry.connect(this._out);
      this._wet.connect(this._out);
      this._osc.connect(this._mod);
      this._mod.connect(this._delay.delayTime);
      this._apply();
      this._osc.start(0);
      return { inputNode: this._input, outputNode: this._out };
    },

    _apply() {
      const A = Math.min(0.02, (Math.pow(2, this._depth / 1200) - 1) / (2 * Math.PI * this._rate));
      setAP(this._osc.frequency, this._rate, this._ctx);
      setAP(this._mod.gain, A, this._ctx);
      setAP(this._delay.delayTime, A + 0.001, this._ctx);
    },

    _mix() {
      const on = this._active && !this._bypassed;
      setAP(this._dry.gain, on ? 0 : 1, this._ctx, 0.003);
      setAP(this._wet.gain, on ? 1 : 0, this._ctx, 0.003);
    },

    setParam(name, value) {
      if (!this._ctx) return;
      const v = Number(value);
      if (!Number.isFinite(v)) return;
      if (name === "depth" || name === "value") this._depth = clamp(v, 0, 100);
      else if (name === "rate") this._rate = clamp(v, 0.5, 20);
      else return;
      this._apply();
      if (!this._active) { this._active = true; this._mix(); }
    },

    bypass(on) { this._bypassed = !!on; this._mix(); },

    resetParams() {
      if (!this._ctx) return;
      this._depth = 20; this._rate = 5;
      this._active = false; this._bypassed = false;
      this._apply(); this._mix();
    },

    // inactive → nothing to replay (the offline export would switch it on)
    getParams() { return this._active ? { depth: this._depth, rate: this._rate } : {}; },

    destroy() {
      try { this._osc?.stop(); } catch (_) { /* not started */ }
      if (this._input) this._input.disconnect();
      if (this._out) this._out.disconnect();
    },

    clone() { return make(); },
  };
}

export default make();
