export default {
  type: "effect", name: "compressor", version: "1.0.0",

  init(host) {},

  createNodes(ctx) {
    this._input = ctx.createGain();
    this._comp  = ctx.createDynamicsCompressor();
    this._dry   = ctx.createGain();
    this._wet   = ctx.createGain();
    this._out   = ctx.createGain();

    this._comp.threshold.value = -24;
    this._comp.knee.value      = 10;
    this._comp.ratio.value     = 4;
    this._comp.attack.value    = 0.003;
    this._comp.release.value   = 0.25;
    this._dry.gain.value       = 0.0;   // fully compressed by default
    this._wet.gain.value       = 1.0;

    // input → dry → out; input → comp → wet → out
    this._input.connect(this._dry);
    this._input.connect(this._comp);
    this._comp.connect(this._wet);
    this._dry.connect(this._out);
    this._wet.connect(this._out);

    return { inputNode: this._input, outputNode: this._out };
  },

  setParam(name, value) {
    const params = { threshold: "threshold", ratio: "ratio",
                     attack: "attack", release: "release", knee: "knee" };
    if (params[name]) this._comp[params[name]].value = value;
  },

  bypass(on) {
    if (on) {
      this._savedWet       = this._wet.gain.value;
      this._wet.gain.value = 0;
      this._dry.gain.value = 1;
    } else {
      this._wet.gain.value = this._savedWet ?? 1.0;
      this._dry.gain.value = 0;
    }
  },

  getParams() {
    return {
      threshold: this._comp?.threshold.value,
      ratio:     this._comp?.ratio.value,
      attack:    this._comp?.attack.value,
      release:   this._comp?.release.value
    };
  },

  destroy() {
    if (this._input) this._input.disconnect();
    if (this._out)   this._out.disconnect();
  }
};
