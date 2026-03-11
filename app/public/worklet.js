// REPuLse AudioWorkletProcessor
// Hosts the WASM PCM synthesis engine on the dedicated audio thread.
// The process() callback generates raw PCM samples — no Web Audio API node creation
// (OscillatorNode etc. are only available on the main thread).

class RepulseProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.engine = null;
    this.port.onmessage = (e) => this._onMessage(e.data);
  }

  async _onMessage(msg) {
    if (msg.type === 'init') {
      try {
        const module = await import(msg.wasmJsUrl);
        await module.default(msg.wasmBinaryUrl);
        this.engine = new module.AudioEngine(sampleRate);
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
