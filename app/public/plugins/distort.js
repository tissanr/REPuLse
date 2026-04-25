// Transfer functions — all produce output in [-1, 1] for any input
const ALGOS = {
  tanh:    (x, k) => Math.tanh(x * k),
  sigmoid: (x, k) => (2 / (1 + Math.exp(-x * k))) - 1,
  atan:    (x, k) => (2 / Math.PI) * Math.atan(x * k),
};

function makeCurve(drive, algo) {
  const N = 512;
  const curve = new Float32Array(N);
  const fn = ALGOS[algo] ?? ALGOS.tanh;
  // Gain compensation: as drive increases, scale output down to keep loudness constant.
  // 1/sqrt(drive) keeps RMS roughly flat across the drive range.
  const comp = 1 / Math.sqrt(Math.max(1, drive));
  for (let i = 0; i < N; i++) {
    const x = (i * 2) / (N - 1) - 1;   // [-1, 1]
    curve[i] = fn(x, drive) * comp;
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

  createNodes(ctx) {
    // Nodes
    this._input    = ctx.createGain();
    this._shaper   = ctx.createWaveShaper();
    this._toneLP   = ctx.createBiquadFilter();
    this._wetGain  = ctx.createGain();
    this._dryGain  = ctx.createGain();
    this._out      = ctx.createGain();

    // Initial state
    this._shaper.curve         = makeCurve(this._drive, this._algo);
    this._shaper.oversample    = "2x";        // native anti-alias at no extra cost
    this._toneLP.type          = "lowpass";
    this._toneLP.frequency.value = this._tone;
    this._toneLP.Q.value       = 0.7;
    this._dryGain.gain.value   = 1 - this._mix;
    this._wetGain.gain.value   = this._mix;

    // Routing:
    //   input → dry → out
    //   input → shaper → toneLP → wet → out
    this._input.connect(this._dryGain);
    this._input.connect(this._shaper);
    this._shaper.connect(this._toneLP);
    this._toneLP.connect(this._wetGain);
    this._dryGain.connect(this._out);
    this._wetGain.connect(this._out);

    return { inputNode: this._input, outputNode: this._out };
  },

  setParam(name, value) {
    const now = this._input?.context?.currentTime ?? 0;
    if (name === "drive") {
      this._drive = Math.max(1.0, Math.min(100.0, value));
      this._shaper.curve = makeCurve(this._drive, this._algo);
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
      this._shaper.curve = makeCurve(this._drive, this._algo);
    }
  },

  bypass(on) {
    const now = this._input?.context?.currentTime ?? 0;
    const target = on ? 0 : this._mix;
    this._wetGain.gain.linearRampToValueAtTime(target,       now + 0.02);
    this._dryGain.gain.linearRampToValueAtTime(on ? 1 : (1 - this._mix), now + 0.02);
  },

  getParams() {
    return { drive: this._drive, tone: this._tone, mix: this._mix, algo: this._algo };
  },

  destroy() {
    try { this._input.disconnect(); } catch (_) {}
    try { this._out.disconnect();   } catch (_) {}
  },
};
