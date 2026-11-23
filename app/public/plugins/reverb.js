// Convolution reverb (Phase FX1, D5/D6): seven generated types, decay, size,
// damping and pre-delay.
//
//   input ─┬─ dry ────────────────────────────────────────────┬─ out
//          └─ pre (DelayNode) ─┬─ convA ─ xA ─┬─ trim ─ wet ──┘
//                              └─ convB ─ xB ─┘
//
// One convolver carries the sound, the other is idle (buffer null). A change of
// type/decay/size/damp is debounced (30 ms); when the wanted response differs
// from the loaded one it is taken from the cache (or generated), loaded into the
// idle convolver and crossfaded in over 50 ms. ready() flushes a pending change,
// so an offline export never renders with a stale response.

import { TYPES, PRESETS, irKey, makeIR } from "./lib/reverb-ir.js";

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
const DEBOUNCE_MS = 30;
const XFADE = 0.05;

function make() {
  return {
    type: "effect", name: "reverb", version: "2.0.0",

    params: {
      value: "wet",
      wet: "number", dry: "number",
      type: TYPES,
      decay: "number", size: "number", damp: "number", predelay: "number",
    },

    init(host) {},

    createNodes(ctx) {
      this._ctx   = ctx;
      this._input = ctx.createGain();
      this._dry   = ctx.createGain();
      this._pre   = ctx.createDelay(0.25);
      this._convA = ctx.createConvolver();
      this._convB = ctx.createConvolver();
      this._xA    = ctx.createGain();
      this._xB    = ctx.createGain();
      this._trim  = ctx.createGain();
      this._wet   = ctx.createGain();
      this._out   = ctx.createGain();

      this._dry.gain.value = 0.8;
      this._wet.gain.value = 0.0;   // off by default
      this._pre.delayTime.value = 0;
      this._xA.gain.value = 1;
      this._xB.gain.value = 0;

      this._type = "classic";
      this._user = {};               // decay/size/damp the user set
      this._active = "A";
      this._timer = null;
      this._swaps = 0;               // crossfades scheduled (tests)

      // the classic response, synchronously, as before FX1
      this._loadedKey = irKey("classic", {}, ctx.sampleRate);
      this._trim.gain.value = PRESETS.classic.trim;
      this._convA.buffer = this._buffer(makeIR("classic", {}, ctx.sampleRate));

      this._input.connect(this._dry);
      this._input.connect(this._pre);
      this._pre.connect(this._convA);
      this._pre.connect(this._convB);
      this._convA.connect(this._xA);
      this._convB.connect(this._xB);
      this._xA.connect(this._trim);
      this._xB.connect(this._trim);
      this._trim.connect(this._wet);
      this._dry.connect(this._out);
      this._wet.connect(this._out);

      return { inputNode: this._input, outputNode: this._out };
    },

    _buffer([L, R]) {
      const b = this._ctx.createBuffer(2, L.length, this._ctx.sampleRate);
      b.copyToChannel(L, 0);
      b.copyToChannel(R, 1);
      return b;
    },

    _schedule() {
      if (this._timer) clearTimeout(this._timer);
      this._timer = setTimeout(() => { this._timer = null; this._apply(); }, DEBOUNCE_MS);
    },

    _apply() {
      if (!this._ctx) return;
      const sr = this._ctx.sampleRate;
      const key = irKey(this._type, this._user, sr);
      if (key === this._loadedKey) return;
      const buf = this._buffer(makeIR(this._type, this._user, sr));
      const fromA = this._active === "A";
      const next = fromA ? this._convB : this._convA;
      const prev = fromA ? this._convA : this._convB;
      const xNext = fromA ? this._xB : this._xA;
      const xPrev = fromA ? this._xA : this._xB;
      next.buffer = buf;
      const ctx = this._ctx;
      const trim = PRESETS[this._type].trim;
      if (ctx.currentTime === 0) {
        xNext.gain.cancelScheduledValues(0); xNext.gain.value = 1;
        xPrev.gain.cancelScheduledValues(0); xPrev.gain.value = 0;
        this._trim.gain.cancelScheduledValues(0); this._trim.gain.value = trim;
        prev.buffer = null;
      } else {
        const t = ctx.currentTime;
        for (const [g, v] of [[xNext.gain, 1], [xPrev.gain, 0], [this._trim.gain, trim]]) {
          g.cancelScheduledValues(t);
          g.setValueAtTime(g.value, t);
          g.linearRampToValueAtTime(v, t + XFADE);
        }
        setTimeout(() => { if (this._active !== (fromA ? "A" : "B")) prev.buffer = null; }, 80);
      }
      this._active = fromA ? "B" : "A";
      this._loadedKey = key;
      this._swaps += 1;
    },

    ready() {
      if (this._timer) { clearTimeout(this._timer); this._timer = null; this._apply(); }
      return Promise.resolve();
    },

    setParam(name, value) {
      if (!this._ctx) return;
      if (name === "type") {
        const t = String(value).replace(/^:/, "");
        if (!TYPES.includes(t)) return;
        this._type = t;
        this._schedule();
        return;
      }
      const v = Number(value);
      if (!Number.isFinite(v)) return;
      switch (name) {
        case "wet": case "value":
          this._wet.gain.value = clamp(v, 0, 1); break;
        case "dry":
          this._dry.gain.value = clamp(v, 0, 1); break;
        case "decay":
          this._user.decay = clamp(v, 0.1, 10); this._schedule(); break;
        case "size":
          this._user.size = clamp(v, 0, 1); this._schedule(); break;
        case "damp":
          this._user.damp = clamp(v, 0, 1); this._schedule(); break;
        case "predelay": {
          const d = clamp(v, 0, 0.25);
          const p = this._pre.delayTime;
          if (this._ctx.currentTime === 0) { p.cancelScheduledValues(0); p.value = d; }
          else p.setTargetAtTime(d, this._ctx.currentTime, 0.01);
          break;
        }
      }
    },

    bypass(on) {
      if (on) {
        this._savedWet       = this._wet.gain.value;
        this._wet.gain.value = 0;
      } else {
        this._wet.gain.value = this._savedWet ?? 0.4;
      }
    },

    resetParams() {
      if (!this._ctx) return;
      this._wet.gain.value = 0.0;
      this._dry.gain.value = 0.8;
      this._pre.delayTime.value = 0;
      this._type = "classic";
      this._user = {};
      this._schedule();
    },

    getParams() {
      if (!this._ctx) return {};
      const pr = PRESETS[this._type];
      return {
        wet: this._wet.gain.value,
        dry: this._dry.gain.value,
        type: this._type,
        decay: this._user.decay ?? pr.decay,
        size: this._user.size ?? pr.size,
        damp: this._user.damp ?? pr.damp,
        predelay: this._pre.delayTime.value,
      };
    },

    destroy() {
      if (this._timer) clearTimeout(this._timer);
      if (this._input) this._input.disconnect();
      if (this._out)   this._out.disconnect();
    },

    clone() { return make(); },
  };
}

export default make();
