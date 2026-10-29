// RecorderProcessor — pass-through master-output recorder for (export :song).
//
// Sits in-line between the end of the master FX chain (master-tap) and the
// analyser. It always copies its input to its output unchanged; while armed it
// also records the frames in [startFrame, endFrame) of the AudioContext clock
// and streams them to the main thread in chunks.
//
// Messages in:
//   { type: "start", startFrame, endFrame, chunkFrames }
//   { type: "cancel" }
// Messages out:
//   { type: "chunk", offset, frames, left, right }   // offset relative to startFrame
//   { type: "done", frames, late }                   // late: recording began after startFrame
//   { type: "cancelled" }

class RecorderProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.armed = false;
    this.port.onmessage = (e) => {
      const d = e.data;
      if (d.type === 'start') {
        this.startFrame = d.startFrame;
        this.endFrame = d.endFrame;
        this.chunkFrames = Math.max(128, d.chunkFrames | 0);
        this.allocChunk();
        this.chunkOffset = 0;
        this.started = false;
        this.late = false;
        this.armed = true;
      } else if (d.type === 'cancel') {
        this.armed = false;
        this.left = null;
        this.right = null;
        this.port.postMessage({ type: 'cancelled' });
      }
    };
  }

  allocChunk() {
    this.left = new Float32Array(this.chunkFrames);
    this.right = new Float32Array(this.chunkFrames);
    this.fill = 0;
  }

  flush() {
    if (this.fill > 0) {
      const left = this.left;
      const right = this.right;
      this.port.postMessage(
        { type: 'chunk', offset: this.chunkOffset, frames: this.fill, left, right },
        [left.buffer, right.buffer],
      );
      this.chunkOffset += this.fill;
      this.allocChunk();
    }
  }

  finish() {
    this.flush();
    this.armed = false;
    this.left = null;
    this.right = null;
    this.port.postMessage({
      type: 'done',
      frames: this.endFrame - this.startFrame,
      late: this.late || !this.started,
    });
  }

  process(inputs, outputs) {
    const inp = inputs[0] || [];
    const out = outputs[0] || [];
    for (let c = 0; c < out.length; c++) {          // pass-through
      const src = inp[c] || inp[0];
      if (src) out[c].set(src); else out[c].fill(0);
    }

    if (this.armed) {
      const n = out[0] ? out[0].length : 128;
      const inL = inp[0];
      const inR = inp[1] || inp[0];
      for (let i = 0; i < n; i++) {
        const frame = currentFrame + i;
        if (frame < this.startFrame) continue;
        if (frame >= this.endFrame) break;
        if (!this.started) {
          this.started = true;
          // A gap before the first recorded frame stays zero-filled on the main thread.
          this.late = frame > this.startFrame;
          this.chunkOffset = frame - this.startFrame;
        }
        this.left[this.fill] = inL ? inL[i] : 0;
        this.right[this.fill] = inR ? inR[i] : 0;
        this.fill += 1;
        if (this.fill === this.chunkFrames) this.flush();
      }
      if (currentFrame + n >= this.endFrame) this.finish();
    }
    return true;
  }
}

registerProcessor('repulse-recorder', RecorderProcessor);
