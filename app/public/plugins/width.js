// Stereo width (Phase FX1, D10): mid/side balance plus a Haas delay.
//
//   input (channelCount 2, explicit) → splitter
//     L → a → mergeL     R → a → mergeR       a = (1 + width) / 2   (same side)
//     L → b → mergeR     R → b → mergeL       b = (1 − width) / 2   (cross)
//   mergeR → DelayNode(haas) → merger 1;  mergeL → merger 0 → out
//
// width 1, haas 0 is the identity, so there is no dry path. Per track it only
// widens what is already stereo (panned events); `haas` also widens mono.

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

function setAP(param, v, ctx) {
  if (ctx.currentTime === 0) { param.cancelScheduledValues(0); param.value = v; }
  else param.setTargetAtTime(v, ctx.currentTime, 0.01);
}

function make() {
  return {
    type: "effect", name: "width", version: "1.0.0",
    params: { value: "width", width: "number", haas: "number" },

    init(host) {},

    createNodes(ctx) {
      this._ctx = ctx;
      this._input = ctx.createGain();
      this._input.channelCount = 2;
      this._input.channelCountMode = "explicit";
      this._split = ctx.createChannelSplitter(2);
      this._aL = ctx.createGain(); this._aR = ctx.createGain();
      this._bL = ctx.createGain(); this._bR = ctx.createGain();
      this._mL = ctx.createGain(); this._mR = ctx.createGain();
      for (const g of [this._mL, this._mR]) { g.channelCount = 1; g.channelCountMode = "explicit"; }
      this._haas = ctx.createDelay(0.03);
      this._merge = ctx.createChannelMerger(2);
      this._out = ctx.createGain();
      this._width = 1; this._haasMs = 0; this._bypassed = false;

      this._input.connect(this._split);
      this._split.connect(this._aL, 0); this._aL.connect(this._mL);
      this._split.connect(this._bL, 0); this._bL.connect(this._mR);
      this._split.connect(this._aR, 1); this._aR.connect(this._mR);
      this._split.connect(this._bR, 1); this._bR.connect(this._mL);
      this._mL.connect(this._merge, 0, 0);
      this._mR.connect(this._haas);
      this._haas.connect(this._merge, 0, 1);
      this._merge.connect(this._out);
      this._haas.delayTime.value = 0;
      this._apply();
      return { inputNode: this._input, outputNode: this._out };
    },

    _apply() {
      const w = this._bypassed ? 1 : this._width;
      const h = this._bypassed ? 0 : this._haasMs / 1000;
      const a = (1 + w) / 2, b = (1 - w) / 2;
      for (const g of [this._aL, this._aR]) setAP(g.gain, a, this._ctx);
      for (const g of [this._bL, this._bR]) setAP(g.gain, b, this._ctx);
      setAP(this._haas.delayTime, h, this._ctx);
    },

    setParam(name, value) {
      if (!this._ctx) return;
      const v = Number(value);
      if (!Number.isFinite(v)) return;
      if (name === "width" || name === "value") this._width = clamp(v, 0, 2);
      else if (name === "haas") this._haasMs = clamp(v, 0, 30);
      else return;
      this._apply();
    },

    bypass(on) { this._bypassed = !!on; this._apply(); },

    resetParams() {
      if (!this._ctx) return;
      this._width = 1; this._haasMs = 0; this._bypassed = false;
      this._apply();
    },

    getParams() { return { width: this._width, haas: this._haasMs }; },

    destroy() {
      if (this._input) this._input.disconnect();
      if (this._out) this._out.disconnect();
    },

    clone() { return make(); },
  };
}

export default make();
