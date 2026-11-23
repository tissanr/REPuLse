// Auto-pan (Phase FX1, D11): an LFO-driven equal-power balance. Each channel
// stays on its own side and the total power stays constant (a StereoPannerNode
// would lose 3 dB at the centre and fold both channels to one side).
//
//   input (channelCount 2, explicit) ─┬─ dry ──────────────────────┬─ out
//                                     └─ splitter ─┬─ gL ─┬─ merger ─ wet ─┘
//                                                  └─ gR ─┘
//   lfo → smooth (lowpass 100 Hz) → depth ─┬─ shapeL → gL.gain
//                                          └─ shapeR → gR.gain
//
// shapeL(x) = √2 · cos((x + 1) · π/4), shapeR(x) = √2 · sin((x + 1) · π/4):
// both 1 at the centre, √2 and 0 at the ends. A sweep starts at the centre
// moving right. With `sync` the LFO is phase-locked to the cycle grid.

const SHAPES = { sine: "sine", tri: "triangle", square: "square" };
const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

function curve(f) {
  const n = 1025;
  const c = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const x = (i / (n - 1)) * 2 - 1;
    c[i] = f(x);
  }
  return c;
}
const CURVE_L = curve((x) => Math.SQRT2 * Math.cos(((x + 1) * Math.PI) / 4));
const CURVE_R = curve((x) => Math.SQRT2 * Math.sin(((x + 1) * Math.PI) / 4));

function setAP(param, v, ctx, tc = 0.01) {
  if (ctx.currentTime === 0) { param.cancelScheduledValues(0); param.value = v; }
  else param.setTargetAtTime(v, ctx.currentTime, tc);
}

function make() {
  return {
    type: "effect", name: "auto-pan", version: "1.0.0",
    params: { value: "depth", depth: "number", rate: "number", sync: "number", shape: Object.keys(SHAPES) },

    init(host) {},

    createNodes(ctx) {
      this._ctx = ctx;
      this._input = ctx.createGain();
      this._input.channelCount = 2;
      this._input.channelCountMode = "explicit";
      this._dry = ctx.createGain();
      this._split = ctx.createChannelSplitter(2);
      this._gL = ctx.createGain();
      this._gR = ctx.createGain();
      this._merge = ctx.createChannelMerger(2);
      this._wet = ctx.createGain();
      this._out = ctx.createGain();
      this._smooth = ctx.createBiquadFilter();
      this._depthG = ctx.createGain();
      this._shL = ctx.createWaveShaper();
      this._shR = ctx.createWaveShaper();

      this._depth = 1; this._rate = 1; this._sync = null; this._shape = "sine";
      this._cycleDur = 2; this._origin = 0;
      this._active = false; this._bypassed = false;

      this._gL.gain.value = 0;   // the shapers set the gains
      this._gR.gain.value = 0;
      this._smooth.type = "lowpass";
      this._smooth.frequency.value = 100;
      this._smooth.Q.value = 0.707;
      this._shL.curve = CURVE_L;
      this._shR.curve = CURVE_R;
      this._dry.gain.value = 1;
      this._wet.gain.value = 0;

      this._input.connect(this._dry);
      this._dry.connect(this._out);
      this._input.connect(this._split);
      this._split.connect(this._gL, 0);
      this._split.connect(this._gR, 1);
      this._gL.connect(this._merge, 0, 0);
      this._gR.connect(this._merge, 0, 1);
      this._merge.connect(this._wet);
      this._wet.connect(this._out);
      this._smooth.connect(this._depthG);
      this._depthG.connect(this._shL);
      this._depthG.connect(this._shR);
      this._shL.connect(this._gL.gain);
      this._shR.connect(this._gR.gain);
      this._depthG.gain.value = 1;

      this._osc = this._newOsc(0);
      return { inputNode: this._input, outputNode: this._out };
    },

    _hz() { return this._sync ? 1 / (this._sync * this._cycleDur) : this._rate; },

    _newOsc(at) {
      const o = this._ctx.createOscillator();
      o.type = SHAPES[this._shape];
      o.frequency.value = this._hz();
      o.connect(this._smooth);
      o.start(at);
      return o;
    },

    /** Phase-lock: a fresh oscillator from the next sweep boundary of the grid. */
    _resync() {
      const ctx = this._ctx;
      const P = this._sync * this._cycleDur;
      const now = ctx.currentTime;
      const at = Math.max(0, this._origin + Math.ceil((now + 0.01 - this._origin) / P) * P);
      const old = this._osc;
      // a context that has not started: stop the free-running LFO at once, so the
      // signal stays centred until the first boundary
      try { old.stop(now === 0 ? 0 : at); } catch (_) { /* already stopped */ }
      this._osc = this._newOsc(at);
    },

    _mix() {
      const on = this._active && !this._bypassed;
      setAP(this._dry.gain, on ? 0 : 1, this._ctx, 0.003);
      setAP(this._wet.gain, on ? 1 : 0, this._ctx, 0.003);
    },

    _activate() { if (!this._active) { this._active = true; this._mix(); } },

    setTempo(cycleDur, origin) {
      if (!(cycleDur > 0)) return;
      this._cycleDur = cycleDur;
      this._origin = origin ?? 0;
      if (this._ctx && this._sync) this._resync();
    },

    setParam(name, value) {
      if (!this._ctx) return;
      if (name === "shape") {
        const s = String(value).replace(/^:/, "");
        if (!SHAPES[s]) return;
        this._shape = s;
        this._osc.type = SHAPES[s];
        this._activate();
        return;
      }
      if (name === "sync" && value == null) {
        this._sync = null;
        setAP(this._osc.frequency, this._rate, this._ctx);
        return;
      }
      const v = Number(value);
      if (!Number.isFinite(v)) return;
      if (name === "depth" || name === "value") {
        this._depth = clamp(v, 0, 1);
        setAP(this._depthG.gain, this._depth, this._ctx);
      } else if (name === "rate") {
        this._rate = clamp(v, 0.01, 20);
        if (!this._sync) setAP(this._osc.frequency, this._rate, this._ctx);
      } else if (name === "sync") {
        if (v > 0) { this._sync = v; this._resync(); }
        else { this._sync = null; setAP(this._osc.frequency, this._rate, this._ctx); }
      } else return;
      this._activate();
    },

    bypass(on) { this._bypassed = !!on; this._mix(); },

    resetParams() {
      if (!this._ctx) return;
      this._depth = 1; this._rate = 1; this._shape = "sine";
      this._active = false; this._bypassed = false;
      setAP(this._depthG.gain, 1, this._ctx);
      if (this._sync) {
        this._sync = null;
        try { this._osc.stop(); } catch (_) { /* already stopped */ }
        this._osc = this._newOsc(this._ctx.currentTime);
      } else {
        this._osc.type = "sine";
        setAP(this._osc.frequency, 1, this._ctx);
      }
      this._mix();
    },

    getParams() {
      // inactive → nothing to replay (the offline export would switch it on)
      if (!this._active) return {};
      return { depth: this._depth, rate: this._rate, sync: this._sync, shape: this._shape };
    },

    destroy() {
      try { this._osc?.stop(); } catch (_) { /* not started */ }
      if (this._input) this._input.disconnect();
      if (this._out) this._out.disconnect();
    },

    clone() { return make(); },
  };
}

export default make();
