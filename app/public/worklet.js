// REPuLse AudioWorkletProcessor
// Hosts the WASM PCM synthesis engine on the dedicated audio thread.
// The process() callback generates raw PCM samples — no Web Audio API node creation
// (OscillatorNode etc. are only available on the main thread).
//
// Dynamic import() is banned in AudioWorkletGlobalScope; only static imports work.
// The main thread compiles the WebAssembly.Module and sends it here via postMessage
// (WebAssembly.Module is serialisable via structured clone).
//
// worklet-polyfills.js MUST be imported first — ES module evaluation is depth-first
// in source order, so the polyfills run before repulse_audio.js top-level code
// (which calls `new TextDecoder()` / `new TextEncoder()` unconditionally).
import '/worklet-polyfills.js';
import init, { AudioEngine } from '/repulse_audio.js';

class RepulseProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.engine = null;
    this.port.onmessage = (e) => this._onMessage(e.data);
  }

  async _onMessage(msg) {
    if (msg.type === 'init') {
      try {
        // Passing a WebAssembly.Module to init() bypasses the fetch/import.meta.url
        // path in the wasm-pack glue — safe to call on the audio thread.
        await init(msg.wasmModule);
        this.engine = new AudioEngine(sampleRate);
        this.port.postMessage({ type: 'ready' });
      } catch (err) {
        this.port.postMessage({ type: 'error', message: String(err) });
      }
    } else if (msg.type === 'trigger') {
      if (this.engine) this.engine.trigger(msg.value, msg.time);
    } else if (msg.type === 'stop') {
      if (this.engine) this.engine.stop_all();
    }
  }

  process(_inputs, outputs) {
    const ch = outputs[0]?.[0];
    if (!ch || !this.engine) return true;
    const samples = this.engine.process_block(ch.length, currentTime);
    ch.set(samples);
    // Duplicate mono to additional channels (e.g. stereo output)
    for (let i = 1; i < outputs[0].length; i++) outputs[0][i].set(ch);
    return true;
  }
}

registerProcessor('repulse-processor', RepulseProcessor);
