// REPuLse AudioWorkletProcessor
// Hosts the WASM PCM synthesis engine on the dedicated audio thread.
// The process() callback generates raw PCM samples — no Web Audio API node creation
// (OscillatorNode etc. are only available on the main thread).
//
// WASM loading strategy: the main thread fetches the raw WASM bytes and transfers
// them here as an ArrayBuffer via postMessage.  The worklet then uses initSync()
// which calls new WebAssembly.Module(bytes) + new WebAssembly.Instance(module) —
// both synchronous, both allowed in workers/worklets on all browsers.
//
// Why not the alternatives:
//   • WebAssembly.Module postMessage  → silently dropped on Chrome
//   • fetch() in worklet              → not available in Safari AudioWorklet
//   • new URL() in worklet            → not available in Firefox AudioWorklet
//   • async WebAssembly.instantiate() → Promise hangs silently on Chrome
//
// worklet-polyfills.js MUST be imported first — ES module evaluation is depth-first
// in source order, so the polyfills run before repulse_audio.js top-level code
// (which calls `new TextDecoder()` / `new TextEncoder()` unconditionally).
import './worklet-polyfills.js';
import { initSync, AudioEngine } from './repulse_audio.js';

// Set globalThis.__REPULSE_DEBUG__ = true (before page load) to enable verbose logging.
const DEBUG = !!globalThis.__REPULSE_DEBUG__;

class RepulseProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.engine = null;
    this.wasm = null;   // wasm exports (initSync's return value); has `memory`
    this.nBuses = 1;
    this._v = null;     // cached Float32Array view into WASM memory (Phase QA2)
    this._mv = null;    // cached view of the meter snapshot (Phase VIZ1)
    this.port.onmessage = (e) => this._onMessage(e.data);
    if (DEBUG) console.log("[REPuLse Worklet] Processor created");
  }

  _onMessage(msg) {
    if (msg.type === 'init') {
      try {
        if (DEBUG) console.log("[REPuLse Worklet] Received WASM bytes, initializing...");
        // initSync({ module: ArrayBuffer }) does:
        //   1. new WebAssembly.Module(bytes)    — synchronous compile from bytes
        //   2. new WebAssembly.Instance(module) — synchronous instantiate
        // Both are allowed in workers/worklets regardless of module size.
        this.wasm = initSync({ module: msg.wasmBytes });
        this.engine = new AudioEngine(sampleRate);
        this.nBuses = this.engine.bus_count();
        this.port.postMessage({ type: 'ready' });
      } catch (err) {
        console.error("[REPuLse Worklet] Init failed:", err);
        this.port.postMessage({ type: 'error', message: String(err) });
      }
    } else if (msg.type === 'meter-req') {
      // Phase VIZ1: ping-pong — fill the transferred buffer with the per-track
      // meter snapshot and send it straight back (no allocation in steady state).
      this._meter(msg.buf);
    } else if (msg.type === 'trigger') {
      if (this.engine) {
        this._meterNext(msg);
        this.engine.trigger(msg.value, msg.time);
      }
    } else if (msg.type === 'trigger_v2') {
      if (this.engine) {
        this._meterNext(msg);
        this.engine.trigger_v2(
          msg.value, msg.time,
          msg.amp, msg.attack, msg.decay, msg.pan
        );
      }
    } else if (msg.type === 'trigger_v3') {
      // Phase TL7a/TL7b/TL7c: params is a Float32Array in the lib.rs trigger_v3 slot layout.
      if (this.engine) {
        this._meterNext(msg);
        this.engine.trigger_v3(msg.value, msg.time, msg.params);
      }
    } else if (msg.type === 'stop') {
      if (this.engine) this.engine.stop_all();
    } else if (msg.type === 'sync') {
      // Phase EXP1: the offline export posts every trigger, then 'sync'; messages
      // on one port arrive in order, so 'synced' means all triggers are queued.
      this.port.postMessage({ type: 'synced', id: msg.id });
    } else if (msg.type === 'transition') {
      if (this.engine) {
        this.engine.start_transition(
          msg.param,
          msg.start,
          msg.end,
          BigInt(Math.round(msg.duration_samples)),
          msg.curve
        );
      }
    }
  }

  // Phase VIZ1: a trigger's `meter` field is its track's meter slot (0–15);
  // meter_next applies it to the next trigger call only.
  _meterNext(msg) {
    if (typeof msg.meter === 'number' && this.engine.meter_next) {
      this.engine.meter_next(msg.meter);
    }
  }

  _meter(buf) {
    if (!buf) return;
    const e = this.engine;
    if (e && e.meter_snapshot) {
      const len = e.meter_snapshot_len();
      const ptr = e.meter_snapshot();
      const mem = this.wasm.memory.buffer;
      let v = this._mv;
      if (v === null || v.buffer !== mem || v.byteOffset !== ptr || v.length !== len) {
        v = this._mv = new Float32Array(mem, ptr, len);
      }
      if (buf.length >= len) buf.set(v);
    }
    this.port.postMessage({ type: 'meter', buf }, [buf.buffer]);
  }

  // Phase QA2: a Float32Array view of `len` floats at `ptr` in WASM memory.
  // render_block / render_block_buses return a pointer into a buffer the
  // engine pre-allocates, so the view is rebuilt only when that pointer or
  // length changes, or when WASM memory grew (which detaches the old buffer).
  // No typed array is created per block.
  _view(ptr, len) {
    const v = this._v;
    const buf = this.wasm.memory.buffer;
    if (v === null || v.buffer !== buf || v.byteOffset !== ptr || v.length !== len) {
      this._v = new Float32Array(buf, ptr, len);
    }
    return this._v;
  }

  process(_inputs, outputs) {
    if (!this.engine) return true;
    const out = outputs[0];
    if (!out || out.length === 0) {
      if (DEBUG) console.warn("[REPuLse Worklet] No output channels available");
      return true;
    }

    const n = out[0].length;

    // Phase TL7b: a node built with 9 outputs gets the orbit send buses.
    // render_block_buses writes [bus][frame][channel]: bus 0 = dry,
    // 1–4 = room sends of orbits 0–3, 5–8 = delay sends of orbits 0–3.
    if (outputs.length > 1) {
      const ptr = this.engine.render_block_buses(n, currentTime);
      const raw = this._view(ptr, this.nBuses * n * 2);
      const nb = Math.min(outputs.length, this.nBuses);
      for (let b = 0; b < nb; b++) {
        const o = outputs[b];
        const base = b * n * 2;
        if (o.length >= 2) {
          for (let i = 0; i < n; i++) {
            o[0][i] = raw[base + i * 2];
            o[1][i] = raw[base + i * 2 + 1];
          }
        } else if (o.length === 1) {
          for (let i = 0; i < n; i++) {
            o[0][i] = (raw[base + i * 2] + raw[base + i * 2 + 1]) * 0.5;
          }
        }
      }
      return true;
    }

    // render_block writes interleaved stereo: [L0, R0, L1, R1, …]
    const raw = this._view(this.engine.render_block(n, currentTime), n * 2);

    if (out.length >= 2) {
      // Stereo output — split interleaved channels
      for (let i = 0; i < n; i++) {
        out[0][i] = raw[i * 2];      // L
        out[1][i] = raw[i * 2 + 1]; // R
      }
    } else {
      // Mono fallback — average L + R
      for (let i = 0; i < n; i++) {
        out[0][i] = (raw[i * 2] + raw[i * 2 + 1]) * 0.5;
      }
    }
    return true;
  }
}

registerProcessor('repulse-processor', RepulseProcessor);
