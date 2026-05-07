// Cabinet simulation effect plugin.
// Uses Web Audio's ConvolverNode with short procedurally generated cabinet IRs.

const ACTIVE_MIX = 1.0;

const DEFAULTS = {
  ir: "1x12",
  mix: 0.0,
};

const CAB_SPECS = {
  "1x12": {
    duration: 0.12,
    decay: 4.0,
    filters: [
      { type: "highpass", freq: 150, Q: 0.8 },
      { type: "lowpass", freq: 4000, Q: 0.8 },
      { type: "peaking", freq: 2500, Q: 2.0, gain: 6 },
    ],
  },
  "2x12": {
    duration: 0.15,
    decay: 3.5,
    filters: [
      { type: "highpass", freq: 100, Q: 0.7 },
      { type: "lowpass", freq: 5000, Q: 0.7 },
      { type: "peaking", freq: 1000, Q: 1.5, gain: 3 },
      { type: "peaking", freq: 3500, Q: 2.0, gain: 4 },
    ],
  },
  "4x12": {
    duration: 0.18,
    decay: 3.0,
    filters: [
      { type: "highpass", freq: 80, Q: 0.7 },
      { type: "lowpass", freq: 4500, Q: 0.7 },
      { type: "peaking", freq: 800, Q: 1.5, gain: 5 },
      { type: "lowshelf", freq: 200, gain: 4 },
      { type: "highshelf", freq: 4000, gain: -4 },
    ],
  },
};

function offlineContext(channels, length, sampleRate) {
  const Ctor = globalThis.OfflineAudioContext || globalThis.webkitOfflineAudioContext;
  if (!Ctor) return null;
  return new Ctor(channels, length, sampleRate);
}

function normalizeBuffer(buf) {
  let peak = 0;
  for (let ch = 0; ch < buf.numberOfChannels; ch++) {
    const data = buf.getChannelData(ch);
    for (let i = 0; i < data.length; i++) {
      peak = Math.max(peak, Math.abs(data[i]));
    }
  }
  if (peak <= 0) return buf;

  const gain = 1 / peak;
  for (let ch = 0; ch < buf.numberOfChannels; ch++) {
    const data = buf.getChannelData(ch);
    for (let i = 0; i < data.length; i++) {
      data[i] *= gain;
    }
  }
  return buf;
}

function generateNoiseBurst(ctx, spec) {
  const sr = ctx.sampleRate;
  const length = Math.max(1, Math.floor(sr * spec.duration));
  const buf = ctx.createBuffer(2, length, sr);

  for (let ch = 0; ch < 2; ch++) {
    const data = buf.getChannelData(ch);
    for (let i = 0; i < length; i++) {
      const decay = Math.pow(1 - i / length, spec.decay);
      data[i] = (Math.random() * 2 - 1) * decay;
    }
  }

  return buf;
}

function generateIR(ctx, spec) {
  const sourceBuffer = generateNoiseBurst(ctx, spec);
  const offCtx = offlineContext(2, sourceBuffer.length, sourceBuffer.sampleRate);

  if (!offCtx) {
    console.warn("[cab] OfflineAudioContext unavailable; using unfiltered cabinet noise burst");
    return Promise.resolve(normalizeBuffer(sourceBuffer));
  }

  const src = offCtx.createBufferSource();
  src.buffer = sourceBuffer;

  let node = src;
  for (const f of spec.filters) {
    const filter = offCtx.createBiquadFilter();
    filter.type = f.type;
    filter.frequency.value = f.freq ?? 1000;
    if (f.Q !== undefined) filter.Q.value = f.Q;
    if (f.gain !== undefined) filter.gain.value = f.gain;
    node.connect(filter);
    node = filter;
  }

  node.connect(offCtx.destination);
  src.start(0);

  return offCtx.startRendering().then(normalizeBuffer);
}

function cleanIRName(value) {
  return (typeof value === "string" ? value : String(value)).replace(/^:/, "");
}

export default {
  type: "effect", name: "cab", version: "1.0.0",

  _ir: DEFAULTS.ir,
  _mix: DEFAULTS.mix,
  _irBuffers: null,
  _loadSerial: 0,
  _destroyed: false,

  init(_host) {},

  createNodes(ctx) {
    this._ctx = ctx;
    this._destroyed = false;
    this._irBuffers = this._irBuffers || {};
    this._input = ctx.createGain();
    this._conv = ctx.createConvolver();
    this._wetGain = ctx.createGain();
    this._dryGain = ctx.createGain();
    this._out = ctx.createGain();

    this._dryGain.gain.value = 1.0;
    this._wetGain.gain.value = 0.0;

    this._input.connect(this._dryGain);
    this._dryGain.connect(this._out);
    this._wetGain.connect(this._out);
    this._connectWetPath();
    this._loadIR(this._ir);

    // Short cabinet IRs are below 200ms; ConvolverNode latency is typically a
    // few milliseconds and does not need musical compensation here.
    return { inputNode: this._input, outputNode: this._out };
  },

  _connectWetPath() {
    if (!this._input || !this._conv || !this._wetGain) return;

    try { this._input.disconnect(this._conv); } catch (_) {}
    try { this._input.disconnect(this._wetGain); } catch (_) {}
    try { this._conv.disconnect(this._wetGain); } catch (_) {}

    if (this._ir === "di") {
      this._input.connect(this._wetGain);
    } else {
      this._input.connect(this._conv);
      this._conv.connect(this._wetGain);
    }
  },

  _activate(now) {
    if (this._mix !== 0.0 || !this._dryGain || !this._wetGain) return;
    this._mix = ACTIVE_MIX;
    this._dryGain.gain.cancelScheduledValues(now);
    this._dryGain.gain.setValueAtTime(1 - this._mix, now);
    this._wetGain.gain.cancelScheduledValues(now);
    this._wetGain.gain.setValueAtTime(this._mix, now);
  },

  _loadIR(name) {
    if (!this._ctx || !this._conv) return;
    if (name === "di") {
      this._conv.buffer = null;
      this._connectWetPath();
      return;
    }

    const spec = CAB_SPECS[name];
    if (!spec) {
      console.warn(`[cab] unknown :ir "${name}", using 1x12`);
      this._ir = "1x12";
      this._loadIR("1x12");
      return;
    }

    this._connectWetPath();

    if (this._irBuffers[name]) {
      this._conv.buffer = this._irBuffers[name];
      return;
    }

    const serial = ++this._loadSerial;
    generateIR(this._ctx, spec).then((buf) => {
      if (this._destroyed) return;
      this._irBuffers[name] = buf;
      if (this._ir === name && this._loadSerial === serial && this._conv) {
        this._conv.buffer = buf;
      }
    }).catch((err) => {
      console.warn(`[cab] failed to generate :${name} IR`, err);
    });
  },

  setParam(name, value) {
    const now = this._ctx?.currentTime ?? 0;

    if (name === "ir") {
      this._ir = cleanIRName(value);
      this._activate(now);
      this._loadIR(this._ir);
    }

    if (name === "mix" || name === "value") {
      this._mix = Math.max(0, Math.min(1, Number(value)));
      if (this._dryGain) {
        this._dryGain.gain.cancelScheduledValues(now);
        this._dryGain.gain.linearRampToValueAtTime(1 - this._mix, now + 0.02);
      }
      if (this._wetGain) {
        this._wetGain.gain.cancelScheduledValues(now);
        this._wetGain.gain.linearRampToValueAtTime(this._mix, now + 0.02);
      }
    }
  },

  resetParams() {
    this._ir = DEFAULTS.ir;
    this._mix = DEFAULTS.mix;
    const now = this._ctx?.currentTime ?? 0;
    if (this._dryGain) {
      this._dryGain.gain.cancelScheduledValues(now);
      this._dryGain.gain.setValueAtTime(1.0, now);
    }
    if (this._wetGain) {
      this._wetGain.gain.cancelScheduledValues(now);
      this._wetGain.gain.setValueAtTime(0.0, now);
    }
    this._loadIR(this._ir);
  },

  clone() {
    const clone = { ...this };
    clone._ctx = null;
    clone._input = null;
    clone._conv = null;
    clone._wetGain = null;
    clone._dryGain = null;
    clone._out = null;
    clone._irBuffers = {};
    clone._loadSerial = 0;
    clone._destroyed = false;
    clone.resetParams();
    return clone;
  },

  bypass(on) {
    const now = this._ctx?.currentTime ?? 0;
    if (this._wetGain) {
      this._wetGain.gain.cancelScheduledValues(now);
      this._wetGain.gain.linearRampToValueAtTime(on ? 0 : this._mix, now + 0.02);
    }
    if (this._dryGain) {
      this._dryGain.gain.cancelScheduledValues(now);
      this._dryGain.gain.linearRampToValueAtTime(on ? 1 : (1 - this._mix), now + 0.02);
    }
  },

  getParams() {
    return { ir: this._ir, mix: this._mix };
  },

  destroy() {
    this._destroyed = true;
    this._irBuffers = {};
    try { this._input?.disconnect(); } catch (_) {}
    try { this._conv?.disconnect(); } catch (_) {}
    try { this._wetGain?.disconnect(); } catch (_) {}
    try { this._dryGain?.disconnect(); } catch (_) {}
    try { this._out?.disconnect(); } catch (_) {}
  },
};
