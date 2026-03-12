// Phaser effect plugin — 4 all-pass filter stages swept by an LFO.
// A feedback path (via a tiny DelayNode to break the cycle) adds
// resonance and the classic metallic phasing character.
//
// Signal graph:
//   input ──┬──────────────────────────────── dry ──┬── out
//           └── [ap1→ap2→ap3→ap4] ──────── wet ──┘
//                    ↑ feedback (0.5 × output, 1ms delay)
// LFO → depthGain → all 4 filter.frequency AudioParams

function startWhenReady(osc, ctx) {
  if (ctx.state === "running") {
    osc.start();
  } else {
    const handler = () => { if (ctx.state === "running") { osc.start(); ctx.removeEventListener("statechange", handler); } };
    ctx.addEventListener("statechange", handler);
  }
}

export default {
  type: "effect", name: "phaser", version: "1.0.0",

  init(host) {},

  createNodes(ctx) {
    this._input    = ctx.createGain();
    this._dry      = ctx.createGain();
    this._wet      = ctx.createGain();
    this._out      = ctx.createGain();
    this._lfo      = ctx.createOscillator();
    this._depthGain = ctx.createGain();
    this._fbGain   = ctx.createGain();     // feedback gain
    this._fbDelay  = ctx.createDelay(0.02); // break the feedback cycle

    this._baseFreq = 1000;
    this._depth    = 0.7;

    // 4 all-pass stages
    this._filters = Array.from({ length: 4 }, () => {
      const f = ctx.createBiquadFilter();
      f.type = "allpass";
      f.frequency.value = this._baseFreq;
      f.Q.value = 0.5;
      return f;
    });

    // Chain the all-pass filters
    for (let i = 0; i < this._filters.length - 1; i++) {
      this._filters[i].connect(this._filters[i + 1]);
    }

    // LFO sweeps all filter frequencies
    this._lfo.type = "sine";
    this._lfo.frequency.value = 0.5;
    this._depthGain.gain.value = this._depth * this._baseFreq;
    this._lfo.connect(this._depthGain);
    this._filters.forEach(f => this._depthGain.connect(f.frequency));

    // Feedback: last filter → fbGain → fbDelay → first filter input
    const last = this._filters[this._filters.length - 1];
    this._fbGain.gain.value = 0.5;
    this._fbDelay.delayTime.value = 0.001;
    last.connect(this._fbGain);
    this._fbGain.connect(this._fbDelay);
    this._fbDelay.connect(this._filters[0]);

    // Dry/wet
    this._dry.gain.value = 1.0;
    this._wet.gain.value = 0.0;

    this._input.connect(this._dry);
    this._input.connect(this._filters[0]);
    last.connect(this._wet);
    this._dry.connect(this._out);
    this._wet.connect(this._out);

    startWhenReady(this._lfo, ctx);

    return { inputNode: this._input, outputNode: this._out };
  },

  setParam(name, value) {
    if (name === "wet" || name === "value") {
      this._wet.gain.value = Math.max(0, Math.min(1, value));
    } else if (name === "rate") {
      this._lfo.frequency.value = value;
    } else if (name === "depth") {
      this._depth = value;
      this._depthGain.gain.value = value * this._baseFreq;
    } else if (name === "freq") {
      this._baseFreq = value;
      this._filters.forEach(f => f.frequency.value = value);
      this._depthGain.gain.value = this._depth * value;
    } else if (name === "feedback") {
      this._fbGain.gain.value = Math.max(0, Math.min(0.95, value));
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
      wet:      this._wet?.gain.value,
      rate:     this._lfo?.frequency.value,
      depth:    this._depth,
      freq:     this._baseFreq,
      feedback: this._fbGain?.gain.value,
    };
  },

  destroy() {
    this._lfo?.stop();
    if (this._input) this._input.disconnect();
    if (this._out)   this._out.disconnect();
  },
};
