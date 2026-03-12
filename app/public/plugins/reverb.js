function makeImpulseResponse(audioCtx, duration, decay) {
  const sr     = audioCtx.sampleRate;
  const length = sr * duration;
  const buf    = audioCtx.createBuffer(2, length, sr);
  for (let ch = 0; ch < 2; ch++) {
    const d = buf.getChannelData(ch);
    for (let i = 0; i < length; i++)
      d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / length, decay);
  }
  return buf;
}

export default {
  type: "effect", name: "reverb", version: "1.0.0",

  init(host) {},

  createNodes(ctx) {
    this._input = ctx.createGain();
    this._dry   = ctx.createGain();
    this._conv  = ctx.createConvolver();
    this._wet   = ctx.createGain();
    this._out   = ctx.createGain();

    this._dry.gain.value = 0.8;
    this._wet.gain.value = 0.0;   // off by default
    this._conv.buffer    = makeImpulseResponse(ctx, 2.5, 3.0);

    // input → dry → out; input → conv → wet → out
    this._input.connect(this._dry);
    this._input.connect(this._conv);
    this._conv.connect(this._wet);
    this._dry.connect(this._out);
    this._wet.connect(this._out);

    return { inputNode: this._input, outputNode: this._out };
  },

  setParam(name, value) {
    if (name === "wet" || name === "value")
      this._wet.gain.value = Math.min(1, Math.max(0, value));
    if (name === "dry")
      this._dry.gain.value = Math.min(1, Math.max(0, value));
  },

  bypass(on) {
    if (on) {
      this._savedWet       = this._wet.gain.value;
      this._wet.gain.value = 0;
    } else {
      this._wet.gain.value = this._savedWet ?? 0.4;
    }
  },

  getParams() {
    return { wet: this._wet?.gain.value, dry: this._dry?.gain.value };
  },

  destroy() {
    if (this._input) this._input.disconnect();
    if (this._out)   this._out.disconnect();
  }
};
