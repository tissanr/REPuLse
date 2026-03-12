export default {
  type: "effect", name: "delay", version: "1.0.0",

  init(host) {},

  createNodes(ctx) {
    this._input    = ctx.createGain();
    this._delay    = ctx.createDelay(2.0);
    this._feedback = ctx.createGain();
    this._wet      = ctx.createGain();
    this._dry      = ctx.createGain();
    this._out      = ctx.createGain();

    this._delay.delayTime.value = 0.375;   // 3/8 beat at 120 BPM
    this._feedback.gain.value   = 0.35;
    this._wet.gain.value        = 0.0;     // off by default
    this._dry.gain.value        = 1.0;

    // input → dry → out; input → delay → feedback → delay (loop); delay → wet → out
    this._input.connect(this._dry);
    this._input.connect(this._delay);
    this._delay.connect(this._feedback);
    this._feedback.connect(this._delay);   // feedback loop
    this._delay.connect(this._wet);
    this._dry.connect(this._out);
    this._wet.connect(this._out);

    return { inputNode: this._input, outputNode: this._out };
  },

  setParam(name, value) {
    if (name === "time" || name === "value")
      this._delay.delayTime.value = value;
    if (name === "feedback")
      this._feedback.gain.value = Math.min(0.95, value);
    if (name === "wet")
      this._wet.gain.value = value;
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
    return {
      time:     this._delay?.delayTime.value,
      feedback: this._feedback?.gain.value,
      wet:      this._wet?.gain.value
    };
  },

  destroy() {
    if (this._input) this._input.disconnect();
    if (this._out)   this._out.disconnect();
  }
};
