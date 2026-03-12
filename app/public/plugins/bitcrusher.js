// Bitcrusher effect plugin — bit depth + sample-rate reduction via AudioWorklet.
// Follows the dattorro-reverb pattern: worklet loads asynchronously in init(),
// dry pass-through is available immediately, wet path connects once worklet is ready.
//
// Signal graph:
//   input ──┬─────────────── dry ──┬── out
//           └── bitcrusher ─ wet ──┘

export default {
  type: "effect", name: "bitcrusher", version: "1.0.0",

  init(host) {
    this._ready = host.audioCtx.audioWorklet
      .addModule("/worklets/bitcrusher-processor.js");
  },

  createNodes(ctx) {
    this._ctx     = ctx;
    this._input   = ctx.createGain();
    this._dry     = ctx.createGain();
    this._wet     = ctx.createGain();
    this._out     = ctx.createGain();
    this._worklet = null;
    this._pending = {};

    this._dry.gain.value = 1.0;
    this._wet.gain.value = 0.0;

    // Dry path — always connected
    this._input.connect(this._dry);
    this._dry.connect(this._out);
    this._wet.connect(this._out);

    // Wire the worklet once its module is loaded
    this._ready
      .then(() => {
        this._worklet = new AudioWorkletNode(ctx, "bitcrusher");
        this._input.connect(this._worklet);
        this._worklet.connect(this._wet);
        // Flush any params that arrived before the worklet was ready
        for (const [name, value] of Object.entries(this._pending ?? {})) {
          this._worklet.port.postMessage({ type: "setParam", name, value });
        }
        this._pending = null;
      })
      .catch(e => console.warn("[REPuLse] bitcrusher worklet failed to load:", e));

    return { inputNode: this._input, outputNode: this._out };
  },

  setParam(name, value) {
    if (name === "wet" || name === "value") {
      this._wet.gain.value = Math.max(0, Math.min(1, +value));
      return;
    }
    // Forward bits/rate to the worklet processor
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
    } else {
      this._wet.gain.value = this._savedWet ?? 0.5;
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
