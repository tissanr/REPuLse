// Single BiquadFilterNode filter. Phase FX1: every node type, a gain for the
// shelf/peaking types, a params descriptor (keyword types, rational values) and
// a bypass that is an exact identity for every type ("peaking", gain 0).

const TYPES = ["lowpass", "highpass", "bandpass", "notch",
               "lowshelf", "highshelf", "peaking", "allpass"];

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

function setAP(param, v, ctx) {
  if (ctx.currentTime === 0) { param.cancelScheduledValues(0); param.value = v; }
  else param.setTargetAtTime(v, ctx.currentTime, 0.01);
}

function make() {
  return {
    type: "effect", name: "filter", version: "1.1.0",

    params: {
      value: "freq",
      freq:  "number",
      q:     "number",
      type:  TYPES,
      gain:  "number",
    },

    init(host) {},

    createNodes(ctx) {
      this._ctx = ctx;
      this._filter = ctx.createBiquadFilter();
      this._filter.type            = "lowpass";
      this._filter.frequency.value = 20000;   // fully open by default
      this._filter.Q.value         = 1.0;
      this._filter.gain.value      = 0;
      this._type = "lowpass";
      this._gain = 0;
      this._bypassed = false;
      return { inputNode: this._filter, outputNode: this._filter };
    },

    setParam(name, value) {
      if (!this._filter) return;
      if (name === "freq" || name === "value") {
        const v = Number(value);
        if (Number.isFinite(v)) setAP(this._filter.frequency, clamp(v, 20, 20000), this._ctx);
      } else if (name === "q") {
        const v = Number(value);
        if (Number.isFinite(v)) setAP(this._filter.Q, clamp(v, 0.001, 30), this._ctx);
      } else if (name === "type") {
        const t = String(value).replace(/^:/, "");
        if (!TYPES.includes(t)) return;
        this._type = t;
        if (!this._bypassed) this._filter.type = t;
      } else if (name === "gain") {
        const v = Number(value);
        if (!Number.isFinite(v)) return;
        this._gain = clamp(v, -24, 24);
        if (!this._bypassed) setAP(this._filter.gain, this._gain, this._ctx);
      }
    },

    bypass(on) {
      if (!this._filter) return;
      this._bypassed = !!on;
      if (on) {
        this._filter.type = "peaking";
        this._filter.gain.cancelScheduledValues(0);
        this._filter.gain.value = 0;
      } else {
        this._filter.type = this._type;
        setAP(this._filter.gain, this._gain, this._ctx);
      }
    },

    resetParams() {
      if (!this._filter) return;
      this._bypassed = false;
      setAP(this._filter.frequency, 20000, this._ctx);
      setAP(this._filter.Q, 1.0, this._ctx);
      this._type = "lowpass";
      this._gain = 0;
      this._filter.type = "lowpass";
      setAP(this._filter.gain, 0, this._ctx);
    },

    getParams() {
      return {
        freq: this._filter?.frequency.value,
        q:    this._filter?.Q.value,
        type: this._type,
        gain: this._gain,
      };
    },

    destroy() {
      if (this._filter) this._filter.disconnect();
    },

    clone() { return make(); },
  };
}

export default make();
