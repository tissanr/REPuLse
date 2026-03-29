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
    const t = this._filter.context.currentTime;
    if (name === "freq" || name === "value")
      this._filter.frequency.setTargetAtTime(Math.max(20, value), t, 0.01);
    if (name === "q")
      this._filter.Q.setTargetAtTime(Math.max(0.001, value), t, 0.01);
    if (name === "type")
      this._filter.type = value;
  },

  bypass(on) {
    const t = this._filter.context.currentTime;
    if (on) {
      this._savedFreq = this._filter.frequency.value;
      this._filter.frequency.setTargetAtTime(20000, t, 0.01);
    } else {
      this._filter.frequency.setTargetAtTime(this._savedFreq ?? 2000, t, 0.01);
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
