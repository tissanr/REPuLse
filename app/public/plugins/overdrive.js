// Overdrive effect plugin — soft-clipping distortion via WaveShaperNode.
//
// Signal graph:
//   input ──┬──────────────────────────────────────── dry ──┬── out
//           └── preGain → waveshaper → toneFilter → wet ──┘
//
// preGain boosts input to push harder into the clipper (drive * 2 + 1).
// The waveshaper applies a smooth asymmetric saturation curve.
// toneFilter is a lowpass that rolls off harshness at high drive settings.

function makeDistortionCurve(drive) {
  const n    = 256;
  const curve = new Float32Array(n);
  const k    = drive * 100;
  for (let i = 0; i < n; i++) {
    const x = (i * 2) / n - 1;
    curve[i] = k === 0
      ? x
      : x * (Math.abs(x) + k) / (x * x + (k - 1) * Math.abs(x) + 1);
  }
  return curve;
}

export default {
  type: "effect", name: "overdrive", version: "1.0.0",

  init(host) {},

  createNodes(ctx) {
    this._input      = ctx.createGain();
    this._preGain    = ctx.createGain();
    this._waveshaper = ctx.createWaveShaper();
    this._tone       = ctx.createBiquadFilter();
    this._dry        = ctx.createGain();
    this._wet        = ctx.createGain();
    this._out        = ctx.createGain();

    this._drive = 0.0;

    this._preGain.gain.value        = 1;     // drive * 2 + 1, starts at 1
    this._waveshaper.curve          = makeDistortionCurve(0);
    this._waveshaper.oversample     = "4x";
    this._tone.type                 = "lowpass";
    this._tone.frequency.value      = 20000; // fully open by default — no coloration
    this._dry.gain.value            = 1.0;
    this._wet.gain.value            = 0.0;

    // Wet path: input → preGain → waveshaper → toneFilter → wet → out
    this._input.connect(this._preGain);
    this._preGain.connect(this._waveshaper);
    this._waveshaper.connect(this._tone);
    this._tone.connect(this._wet);

    // Dry path
    this._input.connect(this._dry);
    this._dry.connect(this._out);
    this._wet.connect(this._out);

    return { inputNode: this._input, outputNode: this._out };
  },

  setParam(name, value) {
    if (name === "drive" || name === "value") {
      this._drive                = Math.max(0, Math.min(1, value));
      this._preGain.gain.value   = this._drive * 2 + 1;
      this._waveshaper.curve     = makeDistortionCurve(this._drive);
    } else if (name === "tone") {
      this._tone.frequency.value = Math.max(500, Math.min(20000, value));
    } else if (name === "wet") {
      this._wet.gain.value = Math.max(0, Math.min(1, value));
    }
  },

  bypass(on) {
    if (on) {
      this._savedWet       = this._wet.gain.value;
      this._wet.gain.value = 0;
    } else {
      this._wet.gain.value = this._savedWet ?? 0.7;
    }
  },

  getParams() {
    return {
      drive: this._drive,
      tone:  this._tone?.frequency.value,
      wet:   this._wet?.gain.value,
    };
  },

  destroy() {
    if (this._input) this._input.disconnect();
    if (this._out)   this._out.disconnect();
  },
};
