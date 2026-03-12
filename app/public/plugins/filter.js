export default {
  type: "effect", name: "filter", version: "1.0.0",

  init(host) {},

  createNodes(ctx) {
    this._filter = ctx.createBiquadFilter();
    this._filter.type            = "lowpass";
    this._filter.frequency.value = 20000;   // fully open by default
    this._filter.Q.value         = 1.0;
    return { inputNode: this._filter, outputNode: this._filter };
  },

  setParam(name, value) {
    if (name === "freq" || name === "value")
      this._filter.frequency.value = value;
    if (name === "q")
      this._filter.Q.value = value;
    if (name === "type")
      this._filter.type = value;
  },

  bypass(on) {
    if (on) {
      this._savedFreq              = this._filter.frequency.value;
      this._filter.frequency.value = 20000;
    } else {
      this._filter.frequency.value = this._savedFreq ?? 2000;
    }
  },

  getParams() {
    return {
      freq: this._filter?.frequency.value,
      q:    this._filter?.Q.value,
      type: this._filter?.type
    };
  },

  destroy() {
    if (this._filter) this._filter.disconnect();
  }
};
