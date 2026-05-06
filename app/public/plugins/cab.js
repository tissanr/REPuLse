// Cabinet IR specifications:
// Each spec defines the filter chain applied to white noise to generate the IR.
// duration: IR length in seconds (max 0.2 = 200ms at 48kHz = 9600 samples)
const CAB_SPECS = {
  "1x12": {
    duration: 0.12,
    decay: 4.0,
    filters: [
      { type: "highpass",  freq: 150,  Q: 0.8 },  // cut deep sub
      { type: "lowpass",   freq: 4000, Q: 0.8 },  // cut harsh highs
      { type: "peaking",   freq: 2500, Q: 2.0, gain: 6 },  // presence peak
    ],
  },
  "2x12": {
    duration: 0.15,
    decay: 3.5,
    filters: [
      { type: "highpass",  freq: 100,  Q: 0.7 },
      { type: "lowpass",   freq: 5000, Q: 0.7 },
      { type: "peaking",   freq: 1000, Q: 1.5, gain: 3 },  // low-mid warmth
      { type: "peaking",   freq: 3500, Q: 2.0, gain: 4 },  // presence
    ],
  },
  "4x12": {
    duration: 0.18,
    decay: 3.0,
    filters: [
      { type: "highpass",  freq: 80,   Q: 0.7 },
      { type: "lowpass",   freq: 4500, Q: 0.7 },
      { type: "peaking",   freq: 800,  Q: 1.5, gain: 5 },  // low-mid bump
      { type: "lowshelf",  freq: 200,  gain: 4 },           // extra low-end
      { type: "highshelf", freq: 4000, gain: -4 },          // tuck the highs
    ],
  },
};

function generateIR(ctx, spec) {
  const sr     = ctx.sampleRate;
  const length = Math.floor(sr * spec.duration);
  const buf    = ctx.createBuffer(2, length, sr);

  for (let ch = 0; ch < 2; ch++) {
    const d = buf.getChannelData(ch);
    // White noise burst with exponential decay
    for (let i = 0; i < length; i++) {
      const decay = Math.pow(1 - i / length, spec.decay);
      d[i] = (Math.random() * 2 - 1) * decay;
    }
  }

  // Apply the filter chain offline using OfflineAudioContext
  return new Promise((resolve) => {
    const offCtx = new OfflineAudioContext(2, length, sr);
    const src = offCtx.createBufferSource();
    src.buffer = buf;

    // Chain the filters
    let node = src;
    for (const f of spec.filters) {
      const filt = offCtx.createBiquadFilter();
      filt.type = f.type;
      filt.frequency.value = f.freq ?? 1000;
      filt.Q.value = f.Q ?? 1.0;
      if (f.gain !== undefined) filt.gain.value = f.gain;
      node.connect(filt);
      node = filt;
    }
    node.connect(offCtx.destination);
    src.start(0);

    offCtx.startRendering().then(resolve);
  });
}

export default {
  type: "effect", name: "cab", version: "1.0.0",

  _ir: "1x12",
  _mix: 1.0,
  _bypassed: false,
  _irBuffers: {},  // cache: ir name → AudioBuffer

  init(host) {
    this._host = host;
  },

  createNodes(ctx) {
    this._ctx     = ctx;
    this._input   = ctx.createGain();
    this._conv    = ctx.createConvolver();
    this._wetGain = ctx.createGain();
    this._dryGain = ctx.createGain();
    this._out     = ctx.createGain();

    this._dryGain.gain.value = 1 - this._mix;
    this._wetGain.gain.value = this._mix;

    // Routing:
    //   input → dry → out
    //   input → conv → wet → out
    this._input.connect(this._dryGain);
    this._input.connect(this._conv);
    this._conv.connect(this._wetGain);
    this._dryGain.connect(this._out);
    this._wetGain.connect(this._out);

    // Load the default IR asynchronously
    this._loadIR(this._ir);

    return { inputNode: this._input, outputNode: this._out };
  },

  _loadIR(name) {
    if (name === "di") {
      // Bypass mode: disconnect conv, route input directly to wet path
      this._conv.buffer = null;
      return;
    }
    const spec = CAB_SPECS[name];
    if (!spec) {
      console.warn(`[cab] unknown :ir "${name}", using 1x12`);
      this._loadIR("1x12");
      return;
    }
    if (this._irBuffers[name]) {
      this._conv.buffer = this._irBuffers[name];
      return;
    }
    generateIR(this._ctx, spec).then((buf) => {
      this._irBuffers[name] = buf;
      if (this._ir === name) {
        this._conv.buffer = buf;
      }
    });
  },

  setParam(name, value) {
    const now = this._ctx?.currentTime ?? 0;

    if (name === "ir") {
      const irName = (typeof value === "string" ? value : String(value)).replace(/^:/, "");
      this._ir = irName;
      if (irName === "di") {
        this._conv.buffer = null;
      } else {
        this._loadIR(irName);
      }
    }

    if (name === "mix") {
      this._mix = Math.max(0, Math.min(1, value));
      this._dryGain.gain.linearRampToValueAtTime(1 - this._mix, now + 0.02);
      this._wetGain.gain.linearRampToValueAtTime(this._mix,     now + 0.02);
    }
  },

  bypass(on) {
    const now = this._ctx?.currentTime ?? 0;
    this._bypassed = on;
    this._wetGain.gain.linearRampToValueAtTime(on ? 0 : this._mix,       now + 0.02);
    this._dryGain.gain.linearRampToValueAtTime(on ? 1 : (1 - this._mix), now + 0.02);
  },

  getParams() {
    return { ir: this._ir, mix: this._mix };
  },

  destroy() {
    // Release IR buffer references so GC can reclaim them
    this._irBuffers = {};
    try { this._input?.disconnect(); } catch (_) {}
    try { this._conv?.disconnect(); } catch (_) {}
    try { this._wetGain?.disconnect(); } catch (_) {}
    try { this._dryGain?.disconnect(); } catch (_) {}
    try { this._out?.disconnect();   } catch (_) {}
  },
};
