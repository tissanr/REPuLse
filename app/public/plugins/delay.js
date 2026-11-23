// Delay (Phase FX1): tempo sync, stereo / mono / ping-pong, multi-tap and
// filters in the feedback path. The positional value is the wet level (Q3):
// (fx :delay 0.4) = wet 0.4; the time is :time (seconds) or :sync (cycles).
//
// A Web Audio feedback loop adds one render quantum (128 frames) to the delay
// inside it (FX1 spec, D9). So the loop holds exactly one DelayNode set to the
// repeat period minus that quantum, and the taps read the loop from outside it:
//
//   input ─┬─ dry ─────────────────────────────────────────────┬─ out
//          └─ send ─┬─ tap[k] (DelayNode t_k) ─ [pan] ─── wet ─┘
//                   └─ loop (DelayNode P − q) → [lowcut → highcut] → fb ─┐
//                      ↑──────────────────────────────────────────────────┘
//
// stereo / mono: P = the longest tap, fb = feedback; every tap repeats every P.
// ping-pong (taps unset): P = 2T, fb = feedback²; a left tap at T and a right
// tap at 2T × feedback, so echo k arrives at k·T with level feedback^(k−1),
// odd echoes left, even echoes right.

const MODES = ["stereo", "mono", "ping-pong"];
const MAX_TIME = 4;
const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

function make() {
  return {
    type: "effect", name: "delay", version: "2.0.0",

    params: {
      value: "wet",
      wet: "number", time: "number", sync: "number", feedback: "number",
      mode: MODES, taps: "list", lowcut: "number", highcut: "number",
    },

    init(host) {},

    createNodes(ctx) {
      this._ctx   = ctx;
      this._input = ctx.createGain();
      this._dry   = ctx.createGain();
      this._send  = ctx.createGain();
      this._wet   = ctx.createGain();
      this._out   = ctx.createGain();
      this._dry.gain.value = 1.0;
      this._wet.gain.value = 0.0;   // off by default

      this._input.connect(this._dry);
      this._input.connect(this._send);
      this._dry.connect(this._out);
      this._wet.connect(this._out);

      this._cycleDur = 2.0;
      this._defaults();
      this._wetNodes = [];
      this._shape = null;
      this._rebuild();
      return { inputNode: this._input, outputNode: this._out };
    },

    _defaults() {
      this._time = 0.375;          // 3/8 beat at 120 BPM
      this._sync = null;
      this._taps = null;
      this._fb = 0.35;
      this._mode = "stereo";
      this._lowcut = 20;
      this._highcut = 20000;
    },

    /** Tap times in seconds. */
    _times() {
      let ts;
      if (this._taps && this._taps.length) ts = this._taps.map(x => x * this._cycleDur);
      else if (this._sync) ts = [this._sync * this._cycleDur];
      else ts = [this._time];
      return ts.map(t => {
        if (t > MAX_TIME && !this._warned) {
          this._warned = true;
          console.warn(`[REPuLse] delay: time ${t.toFixed(3)} s clamped to ${MAX_TIME} s`);
        }
        return clamp(t, 0.001, MAX_TIME);
      });
    },

    _filtered() { return this._lowcut > 20 || this._highcut < 20000; },

    _shapeKey() {
      return `${this._mode}|${this._taps ? this._taps.length : 0}|${this._filtered()}`;
    },

    _setTime(param, t) {
      const ctx = this._ctx;
      if (ctx.currentTime === 0) { param.cancelScheduledValues(0); param.value = t; }
      else {
        const now = ctx.currentTime;
        param.cancelScheduledValues(now);
        param.setValueAtTime(param.value, now);
        param.linearRampToValueAtTime(t, now + 0.05);
      }
    },

    _setGain(param, v) {
      const ctx = this._ctx;
      if (ctx.currentTime === 0) { param.cancelScheduledValues(0); param.value = v; }
      else param.setTargetAtTime(v, ctx.currentTime, 0.01);
    },

    _quantum() { return 128 / this._ctx.sampleRate; },

    _pingPong() { return this._mode === "ping-pong" && !this._taps; },

    /** → { taps: [{t, pan, gain}], period, loopGain } */
    _layout() {
      const ts = this._times();
      if (this._pingPong()) {
        const T = ts[0];
        return {
          taps: [{ t: T, pan: -1, gain: 1 }, { t: Math.min(2 * T, MAX_TIME), pan: 1, gain: this._fb }],
          period: 2 * T,
          loopGain: this._fb * this._fb,
        };
      }
      const pp = this._mode === "ping-pong";
      return {
        taps: ts.map((t, k) => ({ t, pan: pp ? (k % 2 === 0 ? -1 : 1) : null, gain: 1 })),
        period: Math.max(...ts),
        loopGain: this._fb,
      };
    },

    _loopTime(period) {
      return clamp(period - this._quantum(), 0, 2 * MAX_TIME);
    },

    /** Rebuild the wet side when its shape changed, else update times and gains. */
    _update() {
      if (this._shapeKey() !== this._shape) { this._rebuild(); return; }
      const lay = this._layout();
      lay.taps.forEach((tp, k) => {
        this._setTime(this._tapNodes[k].delayTime, tp.t);
        this._setGain(this._tapGains[k].gain, tp.gain);
      });
      this._setTime(this._loop.delayTime, this._loopTime(lay.period));
      this._setGain(this._fbGain.gain, lay.loopGain);
      if (this._lc) this._lc.frequency.value = this._lowcut;
      if (this._hc) this._hc.frequency.value = this._highcut;
    },

    _rebuild() {
      const ctx = this._ctx;
      for (const n of this._wetNodes) n.disconnect();
      this._send.disconnect();
      this._wetNodes = [];
      const keep = (n) => { this._wetNodes.push(n); return n; };

      const mono = this._mode !== "stereo";
      this._send.channelCount = mono ? 1 : 2;
      this._send.channelCountMode = mono ? "explicit" : "max";

      const lay = this._layout();
      this._tapNodes = [];
      this._tapGains = [];
      for (const tp of lay.taps) {
        const d = keep(ctx.createDelay(MAX_TIME));
        const g = keep(ctx.createGain());
        d.delayTime.value = tp.t;
        g.gain.value = tp.gain;
        this._send.connect(d);
        d.connect(g);
        if (tp.pan !== null) {
          const p = keep(ctx.createStereoPanner());
          p.pan.value = tp.pan;
          g.connect(p); p.connect(this._wet);
        } else {
          g.connect(this._wet);
        }
        this._tapNodes.push(d);
        this._tapGains.push(g);
      }

      // the feedback loop: one DelayNode, period − one render quantum
      this._loop = keep(ctx.createDelay(2 * MAX_TIME));
      this._loop.delayTime.value = this._loopTime(lay.period);
      this._fbGain = keep(ctx.createGain());
      this._fbGain.gain.value = lay.loopGain;
      this._lc = this._hc = null;
      this._send.connect(this._loop);
      let n = this._loop;
      if (this._filtered()) {
        this._lc = keep(ctx.createBiquadFilter());
        this._lc.type = "highpass"; this._lc.Q.value = 0; this._lc.frequency.value = this._lowcut;
        this._hc = keep(ctx.createBiquadFilter());
        this._hc.type = "lowpass";  this._hc.Q.value = 0; this._hc.frequency.value = this._highcut;
        n.connect(this._lc); this._lc.connect(this._hc); n = this._hc;
      }
      n.connect(this._fbGain);
      this._fbGain.connect(this._send);
      this._shape = this._shapeKey();
    },

    setTempo(cycleDur, origin) {
      if (!(cycleDur > 0)) return;
      this._cycleDur = cycleDur;
      if (this._ctx && (this._sync || this._taps)) this._update();
    },

    setParam(name, value) {
      if (!this._ctx) return;
      if (name === "mode") {
        const m = String(value).replace(/^:/, "");
        if (!MODES.includes(m)) return;
        this._mode = m;
        this._update();
        return;
      }
      if (name === "taps") {
        const arr = value == null ? [] : (typeof value === "object" && value.length !== undefined
          ? Array.from(value) : [value]);
        const taps = arr.map(Number).filter(x => Number.isFinite(x) && x > 0).slice(0, 4);
        this._taps = taps.length ? taps : null;
        this._update();
        return;
      }
      if (name === "sync" && value == null) { this._sync = null; this._update(); return; }
      const v = Number(value);
      if (!Number.isFinite(v)) return;
      switch (name) {
        case "wet": case "value":
          this._wet.gain.value = clamp(v, 0, 1); return;
        case "time":     this._time = clamp(v, 0.001, MAX_TIME); break;
        case "sync":     this._sync = v > 0 ? v : null; break;
        case "feedback": this._fb = clamp(v, 0, 0.95); break;
        case "lowcut":   this._lowcut = clamp(v, 20, 2000); break;
        case "highcut":  this._highcut = clamp(v, 500, 20000); break;
        default: return;
      }
      this._update();
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
      this._defaults();
      this._update();
    },

    getParams() {
      if (!this._ctx) return {};
      return {
        wet: this._wet.gain.value,
        time: this._time,
        sync: this._sync,
        feedback: this._fb,
        mode: this._mode,
        taps: this._taps ? [...this._taps] : null,
        lowcut: this._lowcut,
        highcut: this._highcut,
      };
    },

    destroy() {
      for (const n of this._wetNodes ?? []) n.disconnect();
      if (this._input) this._input.disconnect();
      if (this._out)   this._out.disconnect();
    },

    clone() { return make(); },
  };
}

export default make();
