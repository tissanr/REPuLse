// Multi-stage Amp Simulation Plugin
// Cascaded gain stages with inter-stage filtering and a 3-band tone stack.

const STAGE_ASYM = 0.2; // Fixed asymmetry for tube-like character

function makeStageCurve(stageGain) {
  const size = 512;
  const curve = new Float32Array(size);
  const comp = 1 / Math.sqrt(Math.max(1, stageGain));
  for (let i = 0; i < size; i++) {
    const x = (i * 2) / (size - 1) - 1;
    const k = x >= 0
      ? stageGain * (1 + STAGE_ASYM)
      : stageGain * (1 - STAGE_ASYM);
    curve[i] = Math.tanh(x * Math.max(0.01, k)) * comp;
  }
  return curve;
}

const TONESTACKS = {
  neutral:     { low: 0,  mid: 0,  high: 0 },
  bright:      { low: -2, mid: 0,  high: 4 },
  dark:        { low: 2,  mid: 0,  high: -6 },
  "mid-scoop": { low: 3,  mid: -6, high: 3 },
  "mid-hump":  { low: -2, mid: 4,  high: -2 },
};

function dbToGain(db) {
  return Math.pow(10, db / 20);
}

export default {
  type: "effect", name: "amp-sim", version: "1.0.0",

  _gain: 8.0,
  _stages: 3,
  _tone: 4000,
  _tonestack: "neutral",
  _sag: 0.0,
  _mix: 1.0,

  init(_host) {},

  createNodes(ctx) {
    this._ctx = ctx;
    this._input = ctx.createGain();
    this._out = ctx.createGain();
    this._dryGain = ctx.createGain();
    this._wetGain = ctx.createGain();

    this._dryGain.gain.value = 1 - this._mix;
    this._wetGain.gain.value = this._mix;

    // DC blocker
    this._dcBlock = ctx.createIIRFilter([1, -1], [1, -0.9995]);

    // Final tone lowpass
    this._toneLP = ctx.createBiquadFilter();
    this._toneLP.type = "lowpass";
    this._toneLP.frequency.value = this._tone;
    this._toneLP.Q.value = 0.7;

    // Tone stack (3-band EQ)
    this._lowShelf = ctx.createBiquadFilter();
    this._lowShelf.type = "lowshelf";
    this._lowShelf.frequency.value = 250;

    this._midPeak = ctx.createBiquadFilter();
    this._midPeak.type = "peaking";
    this._midPeak.frequency.value = 1000;
    this._midPeak.Q.value = 1.0;

    this._highShelf = ctx.createBiquadFilter();
    this._highShelf.type = "highshelf";
    this._highShelf.frequency.value = 4000;

    this._applyTonestack(this._tonestack, 0);

    // Sag compressor
    this._sagComp = ctx.createDynamicsCompressor();
    this._sagComp.threshold.value = -12;
    this._sagComp.knee.value = 6;
    this._sagComp.ratio.value = 2 + this._sag * 10;
    this._sagComp.attack.value = 0.010;
    this._sagComp.release.value = 0.100;

    this._stageChain = [];
    this._buildStages(ctx);

    // Routing
    this._input.connect(this._dryGain);
    this._dryGain.connect(this._out);

    this._connectWetPath();

    return { inputNode: this._input, outputNode: this._out };
  },

  _connectWetPath() {
    if (!this._input || !this._stageChain.length) return;

    try { this._input.disconnect(this._sagComp); } catch (_) {}
    try { this._sagComp.disconnect(); } catch (_) {}
    
    this._input.connect(this._sagComp);
    this._sagComp.connect(this._stageChain[0].inputGain);

    const lastStage = this._stageChain[this._stageChain.length - 1];
    try { lastStage.outputGain.disconnect(); } catch (_) {}
    
    lastStage.outputGain.connect(this._dcBlock);
    this._dcBlock.connect(this._toneLP);
    this._toneLP.connect(this._lowShelf);
    this._lowShelf.connect(this._midPeak);
    this._midPeak.connect(this._highShelf);
    this._highShelf.connect(this._wetGain);
    this._wetGain.connect(this._out);
  },

  _buildStages(ctx) {
    for (const s of this._stageChain) {
      try { s.inputGain.disconnect(); } catch (_) {}
      try { s.shaper.disconnect(); } catch (_) {}
      try { s.hpFilter.disconnect(); } catch (_) {}
      try { s.lpFilter.disconnect(); } catch (_) {}
      try { s.outputGain.disconnect(); } catch (_) {}
    }
    this._stageChain = [];

    const N = Math.max(1, Math.min(4, Math.round(this._stages)));
    const perStageGain = Math.pow(this._gain, 1 / N);
    const stageLPFreqs = [12000, 8000, 5000, 4000];

    for (let i = 0; i < N; i++) {
      const inputGain = ctx.createGain();
      inputGain.gain.value = perStageGain;

      const shaper = ctx.createWaveShaper();
      shaper.curve = makeStageCurve(perStageGain);
      shaper.oversample = "2x";

      const hpFilter = ctx.createBiquadFilter();
      hpFilter.type = "highpass";
      hpFilter.frequency.value = 80;

      const lpFilter = ctx.createBiquadFilter();
      lpFilter.type = "lowpass";
      lpFilter.frequency.value = stageLPFreqs[i] || 4000;

      const outputGain = ctx.createGain();
      outputGain.gain.value = 1.0;

      inputGain.connect(shaper);
      shaper.connect(hpFilter);
      hpFilter.connect(lpFilter);
      lpFilter.connect(outputGain);

      this._stageChain.push({ inputGain, shaper, hpFilter, lpFilter, outputGain });

      if (i > 0) {
        this._stageChain[i - 1].outputGain.connect(inputGain);
      }
    }
  },

  _applyTonestack(name, time) {
    const ts = TONESTACKS[name] || TONESTACKS.neutral;
    const now = this._ctx?.currentTime || 0;
    const t = time !== undefined ? time : now + 0.02;

    this._lowShelf.gain.linearRampToValueAtTime(dbToGain(ts.low), t);
    this._midPeak.gain.linearRampToValueAtTime(dbToGain(ts.mid), t);
    this._highShelf.gain.linearRampToValueAtTime(dbToGain(ts.high), t);
  },

  setParam(name, value) {
    const now = this._ctx?.currentTime || 0;

    if (name === "gain") {
      this._gain = Math.max(1.0, Math.min(100.0, Number(value)));
      if (this._ctx) {
        this._buildStages(this._ctx);
        this._connectWetPath();
      }
    }
    if (name === "stages") {
      this._stages = Math.max(1, Math.min(4, Math.round(Number(value))));
      if (this._ctx) {
        this._buildStages(this._ctx);
        this._connectWetPath();
      }
    }
    if (name === "tone") {
      this._tone = Math.max(200, Math.min(20000, Number(value)));
      if (this._toneLP) this._toneLP.frequency.linearRampToValueAtTime(this._tone, now + 0.02);
    }
    if (name === "tonestack") {
      const ts = (typeof value === "string" ? value : String(value)).replace(/^:/, "");
      this._tonestack = TONESTACKS[ts] ? ts : "neutral";
      if (this._lowShelf) this._applyTonestack(this._tonestack);
    }
    if (name === "sag") {
      this._sag = Math.max(0, Math.min(1.0, Number(value)));
      if (this._sagComp) this._sagComp.ratio.linearRampToValueAtTime(2 + this._sag * 10, now + 0.02);
    }
    if (name === "mix") {
      this._mix = Math.max(0, Math.min(1, Number(value)));
      if (this._dryGain) this._dryGain.gain.linearRampToValueAtTime(1 - this._mix, now + 0.02);
      if (this._wetGain) this._wetGain.gain.linearRampToValueAtTime(this._mix, now + 0.02);
    }
  },

  bypass(on) {
    const now = this._ctx?.currentTime || 0;
    const target = on ? 0 : this._mix;
    if (this._wetGain) this._wetGain.gain.linearRampToValueAtTime(target, now + 0.02);
    if (this._dryGain) this._dryGain.gain.linearRampToValueAtTime(on ? 1 : (1 - this._mix), now + 0.02);
  },

  getParams() {
    return {
      gain: this._gain,
      stages: this._stages,
      tone: this._tone,
      tonestack: this._tonestack,
      sag: this._sag,
      mix: this._mix,
    };
  },

  destroy() {
    try { this._input?.disconnect(); } catch (_) {}
    try { this._sagComp?.disconnect(); } catch (_) {}
    for (const s of this._stageChain) {
      try { s.inputGain?.disconnect(); } catch (_) {}
      try { s.shaper?.disconnect(); } catch (_) {}
      try { s.hpFilter?.disconnect(); } catch (_) {}
      try { s.lpFilter?.disconnect(); } catch (_) {}
      try { s.outputGain?.disconnect(); } catch (_) {}
    }
    try { this._dcBlock?.disconnect(); } catch (_) {}
    try { this._toneLP?.disconnect(); } catch (_) {}
    try { this._lowShelf?.disconnect(); } catch (_) {}
    try { this._midPeak?.disconnect(); } catch (_) {}
    try { this._highShelf?.disconnect(); } catch (_) {}
    try { this._wetGain?.disconnect(); } catch (_) {}
    try { this._dryGain?.disconnect(); } catch (_) {}
    try { this._out?.disconnect(); } catch (_) {}
  },
};
