// Tremolo effect plugin — LFO amplitude modulation.
// The LFO swings the output gain between (1 - depth) and 1.0,
// so at depth=0 the signal is unchanged and at depth=1 it fully mutes each cycle.
//
// Gain equation:  out.gain = (1 - depth/2) + (depth/2) × sin(ωt)
//   → min = 1 - depth,  max = 1
//
// Signal graph: input → ampGain (modulated by LFO) → out

function startWhenReady(osc, ctx) {
  if (ctx.state === "running") {
    osc.start();
  } else {
    const handler = () => { if (ctx.state === "running") { osc.start(); ctx.removeEventListener("statechange", handler); } };
    ctx.addEventListener("statechange", handler);
  }
}

export default {
  type: "effect", name: "tremolo", version: "1.0.0",

  init(host) {},

  createNodes(ctx) {
    this._input   = ctx.createGain();
    this._ampGain = ctx.createGain();   // amplitude modulator — IS the output
    this._lfo     = ctx.createOscillator();
    this._lfoGain = ctx.createGain();   // scales LFO output to depth/2

    this._depth = 0;

    this._lfo.type = "sine";
    this._lfo.frequency.value = 4.0;
    this._ampGain.gain.value  = 1.0;   // base = 1 at depth=0
    this._lfoGain.gain.value  = 0.0;   // no modulation at startup

    // LFO → lfoGain → modulates ampGain.gain AudioParam
    this._lfo.connect(this._lfoGain);
    this._lfoGain.connect(this._ampGain.gain);

    // Signal path: input → ampGain
    this._input.connect(this._ampGain);

    startWhenReady(this._lfo, ctx);

    return { inputNode: this._input, outputNode: this._ampGain };
  },

  setParam(name, value) {
    if (name === "depth" || name === "value") {
      this._depth = Math.max(0, Math.min(1, value));
      // Set base (DC) so gain oscillates between (1 - depth) and 1
      this._ampGain.gain.value = 1 - this._depth / 2;
      this._lfoGain.gain.value = this._depth / 2;
    } else if (name === "rate") {
      this._lfo.frequency.value = value;
    } else if (name === "shape") {
      if (["sine", "square", "sawtooth", "triangle"].includes(value)) {
        this._lfo.type = value;
      }
    }
  },

  bypass(on) {
    if (on) {
      this._savedDepth      = this._depth;
      this._depth           = 0;
      this._ampGain.gain.value = 1;
      this._lfoGain.gain.value = 0;
    } else {
      // restore from saved depth
      const d = this._savedDepth ?? 0;
      this._depth              = d;
      this._ampGain.gain.value = 1 - d / 2;
      this._lfoGain.gain.value = d / 2;
    }
  },

  getParams() {
    return { depth: this._depth, rate: this._lfo?.frequency.value };
  },

  destroy() {
    this._lfo?.stop();
    if (this._input)   this._input.disconnect();
    if (this._ampGain) this._ampGain.disconnect();
  },
};
