// Shared factory for the FX1 worklet effects (limiter, gate, flanger).
//
//   input ─┬─ dry ──────────────────────┬─ out
//          └─ AudioWorkletNode ── wet ──┘
//
// Worklet parameters are k-rate AudioParams (set synchronously, so they cannot
// race an offline render). Until the node exists the values wait in _vals and
// are applied when the worklet is wired.
//
// mode "activate": inactive (dry 1 / wet 0) after createNodes and resetParams;
//                  the first setParam makes it active (dry 0 / wet 1).
// mode "wet":      dry stays 1; the `wetParam` parameter is the wet level (0 at start).

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

export function setGain(param, v, ctx) {
  // 10 ms switch in a running context; direct in one that has not started
  // (every offline render), so a render never begins inside a ramp.
  if (ctx.currentTime === 0) { param.cancelScheduledValues(0); param.value = v; }
  else {
    const t = ctx.currentTime;
    param.cancelScheduledValues(t);
    param.setValueAtTime(param.value, t);
    param.linearRampToValueAtTime(v, t + 0.01);
  }
}

/**
 * spec: { name, module, processor, params (descriptor), defs: {param: [default, min, max]},
 *         mode: "activate" | "wet", wetParam? }
 */
export function makeWorkletEffect(spec) {
  function make() {
    return {
      type: "effect", name: spec.name, version: "1.0.0",
      params: spec.params,

      init(host) {
        this._ready = host.audioCtx.audioWorklet.addModule(spec.module);
      },

      createNodes(ctx) {
        this._ctx = ctx;
        this._input = ctx.createGain();
        this._dry   = ctx.createGain();
        this._wet   = ctx.createGain();
        this._out   = ctx.createGain();
        this._node  = null;
        this._vals  = {};
        for (const [k, [d]] of Object.entries(spec.defs)) this._vals[k] = d;
        this._active = false;
        this._bypassed = false;
        this._wetLevel = 0;

        this._dry.gain.value = 1;
        this._wet.gain.value = 0;
        this._input.connect(this._dry);
        this._dry.connect(this._out);
        this._wet.connect(this._out);

        if (!this._ready) this._ready = ctx.audioWorklet.addModule(spec.module);
        this._wired = this._ready.then(() => {
          this._node = new AudioWorkletNode(ctx, spec.processor, {
            numberOfInputs: 1, numberOfOutputs: 1,
            channelCount: 2, channelCountMode: "explicit",
            outputChannelCount: [2],
          });
          this._input.connect(this._node);
          this._node.connect(this._wet);
          for (const [k, v] of Object.entries(this._vals)) this._applyParam(k, v);
          this._mix();
        });
        this._wired.catch(e => console.warn(`[REPuLse] ${spec.name} worklet failed to load:`, e));
        return { inputNode: this._input, outputNode: this._out };
      },

      ready() { return this._wired ?? Promise.resolve(); },

      _applyParam(k, v) {
        const p = this._node?.parameters.get(k);
        if (p) p.value = v;
      },

      _mix() {
        if (!this._ctx) return;
        let dry, wet;
        if (spec.mode === "wet") {
          dry = 1;
          wet = this._bypassed || !this._node ? 0 : this._wetLevel;
        } else {
          const on = this._active && !this._bypassed && !!this._node;
          dry = on ? 0 : 1;
          wet = on ? 1 : 0;
        }
        setGain(this._dry.gain, dry, this._ctx);
        setGain(this._wet.gain, wet, this._ctx);
      },

      setParam(name, value) {
        if (!this._ctx) return;
        const v = Number(value);
        if (!Number.isFinite(v)) return;
        if (spec.mode === "wet" && (name === spec.wetParam || name === "value")) {
          this._wetLevel = clamp(v, 0, 1);
          this._mix();
          return;
        }
        if (name === "value" && spec.params.value) name = spec.params.value;
        const def = spec.defs[name];
        if (!def) return;
        const c = clamp(v, def[1], def[2]);
        this._vals[name] = c;
        this._applyParam(name, c);
        if (spec.mode === "activate" && !this._active) { this._active = true; this._mix(); }
      },

      bypass(on) {
        this._bypassed = !!on;
        this._mix();
      },

      resetParams() {
        if (!this._ctx) return;
        for (const [k, [d]] of Object.entries(spec.defs)) {
          this._vals[k] = d;
          this._applyParam(k, d);
        }
        this._active = false;
        this._bypassed = false;
        this._wetLevel = 0;
        this._mix();
      },

      getParams() {
        // An inactive "activate" effect reports nothing: the offline export
        // replays getParams() into setParam(), which would switch it on.
        if (spec.mode === "activate" && !this._active) return {};
        const out = { ...(this._vals ?? {}) };
        if (spec.mode === "wet") out[spec.wetParam] = this._wetLevel ?? 0;
        return out;
      },

      destroy() {
        if (this._node)  { this._node.disconnect(); this._node = null; }
        if (this._input) this._input.disconnect();
        if (this._out)   this._out.disconnect();
      },

      clone() {
        const c = make();
        c._ready = this._ready;   // same context; init(host) replaces it offline
        return c;
      },
    };
  }
  return make;
}
