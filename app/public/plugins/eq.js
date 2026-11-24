// Parametric EQ (Phase FX1): low shelf, two peaking bands, high shelf.
//
//   input (GainNode) → lowshelf → peaking → peaking → highshelf → out (GainNode)
//
// All gains at 0 dB is an exact identity, so there is no dry/wet path.

const BANDS = {
  low:  { node: "_low",  type: "lowshelf",  freq: 100 },
  mid:  { node: "_mid",  type: "peaking",   freq: 1000, q: 1 },
  mid2: { node: "_mid2", type: "peaking",   freq: 3000, q: 1 },
  high: { node: "_high", type: "highshelf", freq: 8000 },
};

// name → [band, AudioParam field, min, max]
const PARAMS = {
  "low":       ["low",  "gain",      -24, 24],
  "low-freq":  ["low",  "frequency",  20, 1000],
  "mid":       ["mid",  "gain",      -24, 24],
  "mid-freq":  ["mid",  "frequency", 100, 8000],
  "mid-q":     ["mid",  "Q",         0.1, 10],
  "mid2":      ["mid2", "gain",      -24, 24],
  "mid2-freq": ["mid2", "frequency", 200, 16000],
  "mid2-q":    ["mid2", "Q",         0.1, 10],
  "high":      ["high", "gain",      -24, 24],
  "high-freq": ["high", "frequency", 1000, 20000],
};

const DEFAULTS = {
  "low": 0, "low-freq": 100, "mid": 0, "mid-freq": 1000, "mid-q": 1,
  "mid2": 0, "mid2-freq": 3000, "mid2-q": 1, "high": 0, "high-freq": 8000,
};

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

function setAP(param, v, ctx) {
  // A context that has not started (every offline render) sets the value
  // directly, so a render never begins inside a glide.
  if (ctx.currentTime === 0) { param.cancelScheduledValues(0); param.value = v; }
  else param.setTargetAtTime(v, ctx.currentTime, 0.01);
}

function make() {
  return {
    type: "effect", name: "eq", version: "1.0.0",

    params: {
      "low": "number", "low-freq": "number",
      "mid": "number", "mid-freq": "number", "mid-q": "number",
      "mid2": "number", "mid2-freq": "number", "mid2-q": "number",
      "high": "number", "high-freq": "number",
    },

    init(host) {},

    createNodes(ctx) {
      this._ctx = ctx;
      this._input = ctx.createGain();
      this._out   = ctx.createGain();
      this._values = { ...DEFAULTS };
      let prev = this._input;
      for (const b of Object.values(BANDS)) {
        const f = ctx.createBiquadFilter();
        f.type = b.type;
        f.frequency.value = b.freq;
        if (b.q) f.Q.value = b.q;
        f.gain.value = 0;
        this[b.node] = f;
        prev.connect(f);
        prev = f;
      }
      prev.connect(this._out);
      return { inputNode: this._input, outputNode: this._out };
    },

    _node(band) { return this[BANDS[band].node]; },

    setParam(name, value) {
      const spec = PARAMS[name];
      if (!spec || !this._ctx) return;
      const v = Number(value);
      if (!Number.isFinite(v)) return;
      const [band, field, lo, hi] = spec;
      const c = clamp(v, lo, hi);
      this._values[name] = c;
      if (this._bypassed && field === "gain") return;
      setAP(this._node(band)[field], c, this._ctx);
    },

    bypass(on) {
      this._bypassed = !!on;
      for (const band of Object.keys(BANDS)) {
        const g = on ? 0 : this._values[band];
        setAP(this._node(band).gain, g, this._ctx);
      }
    },

    resetParams() {
      if (!this._ctx) return;
      this._bypassed = false;
      for (const [k, v] of Object.entries(DEFAULTS)) this.setParam(k, v);
    },

    getParams() {
      return this._values ? { ...this._values } : {};
    },

    destroy() {
      if (this._input) this._input.disconnect();
      if (this._out)   this._out.disconnect();
    },

    clone() { return make(); },
  };
}

export default make();
