export default {
  type: "effect", name: "waveshape", version: "1.0.0",

  _drive: 1.0,
  _tone: 20000,
  _mix: 1.0,
  _curve: null,   // Float32Array | null

  init(_host) {},

  createNodes(ctx) {
    this._input   = ctx.createGain();
    this._preGain = ctx.createGain();   // drive boost before shaper
    this._shaper  = ctx.createWaveShaper();
    this._toneLP  = ctx.createBiquadFilter();
    this._wetGain = ctx.createGain();
    this._dryGain = ctx.createGain();
    this._out     = ctx.createGain();

    this._preGain.gain.value    = this._drive;
    this._toneLP.type           = "lowpass";
    this._toneLP.frequency.value = this._tone;
    this._toneLP.Q.value        = 0.7;
    this._dryGain.gain.value    = 1 - this._mix;
    this._wetGain.gain.value    = this._mix;

    // Default curve: identity (linear, no distortion)
    if (!this._curve) {
      this._curve = new Float32Array([-1, 0, 1]);
    }
    this._shaper.curve = this._curve;

    // Routing:
    //   input → dry → out
    //   input → preGain → shaper → toneLP → wet → out
    this._input.connect(this._dryGain);
    this._input.connect(this._preGain);
    this._preGain.connect(this._shaper);
    this._shaper.connect(this._toneLP);
    this._toneLP.connect(this._wetGain);
    this._dryGain.connect(this._out);
    this._wetGain.connect(this._out);

    return { inputNode: this._input, outputNode: this._out };
  },

  setParam(name, value) {
    const now = this._input?.context?.currentTime ?? 0;

    if (name === "curve") {
      // Accept Float32Array, plain Array, or CLJS ISeq/PersistentVector
      if (!(value instanceof Float32Array)) {
        try {
          // CLJS vectors and other iterables
          value = new Float32Array(Array.from(value));
        } catch (e) {
          console.error("[waveshape] :curve must be a Float32Array or numeric array", e);
          return;
        }
      }
      if (value.length < 3) {
        console.error(`[waveshape] :curve must have at least 3 points (got ${value.length})`);
        return;
      }
      if (value.length > 4097) {
        console.error(`[waveshape] :curve must have at most 4097 points (got ${value.length})`);
        return;
      }
      this._curve = value;
      if (this._shaper) this._shaper.curve = value;
    }

    if (name === "drive" || name === "value") {
      this._drive = Math.max(1.0, Math.min(20.0, Number(value)));
      if (this._preGain) this._preGain.gain.linearRampToValueAtTime(this._drive, now + 0.02);
    }

    if (name === "tone") {
      this._tone = Math.max(200, Math.min(20000, Number(value)));
      if (this._toneLP) this._toneLP.frequency.linearRampToValueAtTime(this._tone, now + 0.02);
    }

    if (name === "mix") {
      this._mix = Math.max(0, Math.min(1, Number(value)));
      if (this._dryGain) this._dryGain.gain.linearRampToValueAtTime(1 - this._mix, now + 0.02);
      if (this._wetGain) this._wetGain.gain.linearRampToValueAtTime(this._mix,     now + 0.02);
    }
  },

  bypass(on) {
    const now = this._input?.context?.currentTime ?? 0;
    const target = on ? 0 : this._mix;
    if (this._wetGain) this._wetGain.gain.linearRampToValueAtTime(target,       now + 0.02);
    if (this._dryGain) this._dryGain.gain.linearRampToValueAtTime(on ? 1 : (1 - this._mix), now + 0.02);
  },

  getParams() {
    return {
      drive: this._drive,
      tone: this._tone,
      mix: this._mix,
    };
  },

  destroy() {
    try { this._input?.disconnect(); } catch (_) {}
    try { this._preGain?.disconnect(); } catch (_) {}
    try { this._shaper?.disconnect(); } catch (_) {}
    try { this._toneLP?.disconnect(); } catch (_) {}
    try { this._wetGain?.disconnect(); } catch (_) {}
    try { this._dryGain?.disconnect(); } catch (_) {}
    try { this._out?.disconnect(); } catch (_) {}
  },
};
