// Dattorro plate reverb — effect plugin
// Wraps the DattorroReverbProcessor AudioWorklet.
// Before the worklet is loaded the plugin is transparent (dry pass-through, wet=0).

export default {
  type: "effect", name: "dattorro-reverb", version: "1.0.0",

  init(host) {
    // Start loading the worklet module; store the promise for createNodes.
    this._ready = host.audioCtx.audioWorklet
      .addModule("/worklets/dattorro-reverb-processor.js");
  },

  createNodes(ctx) {
    this._ctx    = ctx;
    this._input  = ctx.createGain();
    this._dry    = ctx.createGain();
    this._wet    = ctx.createGain();
    this._out    = ctx.createGain();
    this._worklet = null;

    this._dry.gain.value = 1.0;
    this._wet.gain.value = 0.0;    // silent until the user calls (fx :dattorro-reverb N)

    // Always-connected dry path
    this._input.connect(this._dry);
    this._dry.connect(this._out);

    // Wet path output node connected regardless; signal only arrives once worklet exists
    this._wet.connect(this._out);

    // Wire the worklet when its module has finished loading
    this._ready
      .then(() => {
        this._worklet = new AudioWorkletNode(ctx, "dattorro-reverb", {
          numberOfInputs:    1,
          numberOfOutputs:   1,
          outputChannelCount: [2],   // always stereo reverb output
        });
        this._input.connect(this._worklet);
        this._worklet.connect(this._wet);
        // Flush any params that arrived before the worklet was ready
        for (const [name, value] of Object.entries(this._pending ?? {})) {
          this._worklet.port.postMessage({ type: "setParam", name, value });
        }
        this._pending = null;
      })
      .catch(e => console.warn("[REPuLse] dattorro-reverb worklet failed to load:", e));

    this._pending = {};
    return { inputNode: this._input, outputNode: this._out };
  },

  setParam(name, value) {
    if (name === "wet" || name === "value") {
      this._wet.gain.value = Math.max(0, Math.min(1, +value));
      return;
    }
    // Forward all other params to the worklet processor
    if (this._worklet) {
      this._worklet.port.postMessage({ type: "setParam", name, value: +value });
    } else if (this._pending) {
      this._pending[name] = +value;
    }
  },

  bypass(on) {
    if (on) {
      this._savedWet       = this._wet.gain.value;
      this._wet.gain.value = 0;
      this._dry.gain.value = 1;
    } else {
      this._wet.gain.value = this._savedWet ?? 0.5;
      this._dry.gain.value = 1;
    }
  },

  getParams() {
    return { wet: this._wet?.gain.value };
  },

  destroy() {
    if (this._worklet) { this._worklet.disconnect(); this._worklet = null; }
    if (this._input)   this._input.disconnect();
    if (this._out)     this._out.disconnect();
  },
};
