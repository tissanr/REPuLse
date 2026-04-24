// Transfer functions — all produce output in [-1, 1] for any input
const ALGOS = {
  tanh:    (x, k) => Math.tanh(x * k),
  sigmoid: (x, k) => (2 / (1 + Math.exp(-x * k))) - 1,
  atan:    (x, k) => (2 / Math.PI) * Math.atan(x * k),
};

function makeCurve(drive, algo, asym) {
  const size = 512;
  const curve = new Float32Array(size);
  const fn = ALGOS[algo] ?? ALGOS.tanh;
  // Gain compensation: as drive increases, scale output down to keep loudness constant.
  // 1/sqrt(drive) keeps RMS roughly flat across the drive range.
  const comp = 1 / Math.sqrt(Math.max(1, drive));

  for (let i = 0; i < size; i++) {
    const x = (i * 2) / (size - 1) - 1;
    const effectiveDrive = x >= 0
      ? drive * (1 + asym)
      : drive * (1 - asym);
    curve[i] = fn(x, Math.max(0.01, effectiveDrive)) * comp;
  }
  return curve;
}

export default {
  type: "effect", name: "distort", version: "1.0.0",

  // State
  _drive: 4.0,
  _tone: 3000,
  _mix: 1.0,
  _algo: "tanh",
  _asym: 0.0,

  createNodes(ctx) {
    this._input = ctx.createGain();
    this._shaper = ctx.createWaveShaper();
    this._dcBlock = ctx.createIIRFilter([1, -1], [1, -0.9995]);
    this._toneLP = ctx.createBiquadFilter();
    this._wetGain = ctx.createGain();
    this._dryGain = ctx.createGain();
    this._out = ctx.createGain();

    this._shaper.curve = makeCurve(this._drive, this._algo, this._asym);
    this._shaper.oversample = "2x";
    this._toneLP.type = "lowpass";
    this._toneLP.frequency.value = this._tone;
    this._toneLP.Q.value       = 0.7;
    this._dryGain.gain.value   = 1 - this._mix;
    this._wetGain.gain.value   = this._mix;

    // Routing:
    //   input → dry → out
    //   input → shaper → toneLP → wet → out
    this._input.connect(this._dryGain);
    this._input.connect(this._shaper);
    this._shaper.connect(this._dcBlock);
    this._dcBlock.connect(this._toneLP);
    this._toneLP.connect(this._wetGain);
    this._dryGain.connect(this._out);
    this._wetGain.connect(this._out);

    return { inputNode: this._input, outputNode: this._out };
  },

  setParam(name, value) {
    const now = this._input?.context?.currentTime ?? 0;

    if (name === "drive" || name === "value") {
      this._drive = Math.max(1.0, Math.min(100.0, Number(value)));
      if (this._shaper) this._shaper.curve = makeCurve(this._drive, this._algo, this._asym);
    }
    if (name === "tone") {
      this._tone = Math.max(200, Math.min(20000, value));
      this._toneLP.frequency.linearRampToValueAtTime(this._tone, now + 0.02);
    }
    if (name === "mix") {
      this._mix = Math.max(0, Math.min(1, value));
      this._dryGain.gain.linearRampToValueAtTime(1 - this._mix, now + 0.02);
      this._wetGain.gain.linearRampToValueAtTime(this._mix,     now + 0.02);
    }
    if (name === "algo") {
      // value is a string: "tanh", "sigmoid", "atan"
      // Lisp keywords arrive as strings here (keyword -> name already done in eval.cljs)
      this._algo = (typeof value === "string" ? value : String(value)).replace(/^:/, "");
      if (!ALGOS[this._algo]) {
        console.warn(`[distort] unknown algo "${this._algo}", defaulting to tanh`);
        this._algo = "tanh";
      }
      if (this._shaper) this._shaper.curve = makeCurve(this._drive, this._algo, this._asym);
    }

    if (name === "asym") {
      this._asym = Math.max(-1.0, Math.min(1.0, Number(value)));
      if (this._shaper) this._shaper.curve = makeCurve(this._drive, this._algo, this._asym);
    }
  },

  bypass(on) {
    const now = this._input?.context?.currentTime ?? 0;
    const target = on ? 0 : this._mix;
    this._wetGain.gain.linearRampToValueAtTime(target,       now + 0.02);
    this._dryGain.gain.linearRampToValueAtTime(on ? 1 : (1 - this._mix), now + 0.02);
  },

  getParams() {
    return {
      drive: this._drive,
      tone: this._tone,
      mix: this._mix,
      algo: this._algo,
      asym: this._asym,
    };
  },

  destroy() {
    try { this._input?.disconnect(); } catch (_) {}
    try { this._shaper?.disconnect(); } catch (_) {}
    try { this._dcBlock?.disconnect(); } catch (_) {}
    try { this._toneLP?.disconnect(); } catch (_) {}
    try { this._wetGain?.disconnect(); } catch (_) {}
    try { this._dryGain?.disconnect(); } catch (_) {}
    try { this._out?.disconnect(); } catch (_) {}
  },
};
