/**
 * Sidechain — pattern-aware gain ducking plugin for REPuLse.
 *
 * Instead of audio-level compression, this plugin uses the scheduler's
 * pre-known event times to schedule gain automation directly, which is
 * both more precise and works cleanly at any BPM.
 *
 * Usage:
 *   (fx :sidechain :trigger :bd :amount 0.8 :release 0.1)
 *   (fx :sidechain :trigger :sd :amount 0.5 :release 0.2)
 *   (fx :off :sidechain)
 */
export default {
  type: "effect",
  name: "sidechain",
  version: "1.0.0",

  // ── State ──────────────────────────────────────────────────────────
  _trigger: "bd",   // event name that triggers ducking
  _amount:  0.8,    // duck depth: 0.0 = no duck, 1.0 = full silence
  _release: 0.1,    // seconds to ramp back to unity after the duck
  _gain:    null,   // the GainNode used for ducking
  _ctx:     null,   // AudioContext reference

  // ── Plugin interface ───────────────────────────────────────────────

  init(_host) {},

  createNodes(ctx) {
    this._ctx  = ctx;
    this._gain = ctx.createGain();
    this._gain.gain.value = 1.0;
    // inputNode === outputNode: the gain node is both entry and exit point
    return { inputNode: this._gain, outputNode: this._gain };
  },

  setParam(name, value) {
    switch (name) {
      case "trigger": this._trigger = String(value).replace(/^:/, ""); break;
      case "amount":  this._amount  = Math.max(0, Math.min(1, Number(value))); break;
      case "release": this._release = Math.max(0.005, Number(value)); break;
      // positional shorthand: (fx :sidechain 0.8) sets amount
      case "value":   this._amount  = Math.max(0, Math.min(1, Number(value))); break;
    }
  },

  /**
   * Called by the scheduler for every event that fires.
   * eventName — string name of the sound (e.g. "bd", "sd", "hh")
   * time      — AudioContext time the event is scheduled for
   */
  onEvent(eventName, time) {
    if (eventName !== this._trigger) return;
    if (!this._gain || !this._ctx)   return;

    const g      = this._gain.gain;
    const target = 1.0 - this._amount;   // floor gain during duck
    const rampEnd = time + this._release;

    // Instantly drop, then ramp back to 1.0 over _release seconds
    g.cancelScheduledValues(time);
    g.setValueAtTime(target, time);
    g.linearRampToValueAtTime(1.0, rampEnd);
  },

  bypass(on) {
    if (!this._gain || !this._ctx) return;
    // Restore gain to unity when bypassed
    if (on) {
      this._gain.gain.cancelScheduledValues(this._ctx.currentTime);
      this._gain.gain.setValueAtTime(1.0, this._ctx.currentTime);
    }
  },

  getParams() {
    return {
      trigger: this._trigger,
      amount:  this._amount,
      release: this._release,
    };
  },

  destroy() {
    if (this._gain) {
      try { this._gain.disconnect(); } catch (_) {}
    }
  },
};
