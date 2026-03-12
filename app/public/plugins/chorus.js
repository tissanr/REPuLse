// Chorus effect plugin — two LFO-modulated delay lines for classic chorus shimmer.
// Both delay lines are fed from the same mono input; the slightly different LFO
// rates (1% apart) create evolving stereo-like detuning.
//
// Dry/wet topology:
//   input ──┬── delay1 (lfo1 modulated) ──┐
//           ├── delay2 (lfo2 modulated) ──┴── wet ──┬── out
//           └── dry ──────────────────────────────────┘

export default {
  type: "effect", name: "chorus", version: "1.0.0",

  init(host) {},

  createNodes(ctx) {
    this._input  = ctx.createGain();
    this._dry    = ctx.createGain();
    this._wet    = ctx.createGain();
    this._out    = ctx.createGain();
    this._delay1 = ctx.createDelay(0.1);
    this._delay2 = ctx.createDelay(0.1);
    this._lfo1   = ctx.createOscillator();
    this._lfo2   = ctx.createOscillator();
    this._mod1   = ctx.createGain();   // LFO depth gain (seconds) for delay1
    this._mod2   = ctx.createGain();   // LFO depth gain (seconds) for delay2
    this._mix1   = ctx.createGain();   // balance the two wet paths
    this._mix2   = ctx.createGain();

    this._rate = 1.5;

    this._delay1.delayTime.value = 0.025;
    this._delay2.delayTime.value = 0.025;
    this._mod1.gain.value        = 0.003;
    this._mod2.gain.value        = 0.003;
    this._mix1.gain.value        = 0.5;
    this._mix2.gain.value        = 0.5;
    this._lfo1.frequency.value   = this._rate;
    this._lfo2.frequency.value   = this._rate * 1.01;  // 1% offset → natural detuning
    this._dry.gain.value         = 1.0;
    this._wet.gain.value         = 0.0;

    // LFO → mod gain → delay time AudioParam
    this._lfo1.connect(this._mod1);
    this._lfo2.connect(this._mod2);
    this._mod1.connect(this._delay1.delayTime);
    this._mod2.connect(this._delay2.delayTime);

    // Signal: input → each delay → mix gain → wet summing node
    this._input.connect(this._delay1);
    this._input.connect(this._delay2);
    this._delay1.connect(this._mix1);
    this._delay2.connect(this._mix2);
    this._mix1.connect(this._wet);
    this._mix2.connect(this._wet);

    // Dry path
    this._input.connect(this._dry);
    this._dry.connect(this._out);
    this._wet.connect(this._out);

    this._lfo1.start();
    this._lfo2.start();

    return { inputNode: this._input, outputNode: this._out };
  },

  setParam(name, value) {
    if (name === "wet" || name === "value") {
      this._wet.gain.value = Math.max(0, Math.min(1, value));
    } else if (name === "rate") {
      this._rate = value;
      this._lfo1.frequency.value = value;
      this._lfo2.frequency.value = value * 1.01;
    } else if (name === "depth") {
      this._mod1.gain.value = value;
      this._mod2.gain.value = value;
    } else if (name === "delay") {
      this._delay1.delayTime.value = value;
      this._delay2.delayTime.value = value;
    }
  },

  bypass(on) {
    if (on) {
      this._savedWet       = this._wet.gain.value;
      this._wet.gain.value = 0;
    } else {
      this._wet.gain.value = this._savedWet ?? 0.5;
    }
  },

  getParams() {
    return {
      wet:   this._wet?.gain.value,
      rate:  this._lfo1?.frequency.value,
      depth: this._mod1?.gain.value,
      delay: this._delay1?.delayTime.value,
    };
  },

  destroy() {
    this._lfo1?.stop();
    this._lfo2?.stop();
    if (this._input) this._input.disconnect();
    if (this._out)   this._out.disconnect();
  },
};
