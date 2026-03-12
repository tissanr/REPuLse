// BitcrusherProcessor — bit depth + sample-rate reduction AudioWorkletProcessor.
// Parameters received via MessagePort:
//   { type: "setParam", name: "bits", value: 1–16 }
//   { type: "setParam", name: "rate", value: 0.01–1.0 }

class BitcrusherProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this._bits  = 16;
    this._rate  = 1.0;
    this._held  = 0;
    this._phase = 0;
    this.port.onmessage = ({ data }) => {
      if (data.type === "setParam") {
        if (data.name === "bits") this._bits = Math.max(1, Math.min(16, data.value));
        if (data.name === "rate") this._rate = Math.max(0.01, Math.min(1.0, data.value));
      }
    };
  }

  process(inputs, outputs) {
    const inp = inputs[0]?.[0];
    const out = outputs[0]?.[0];
    if (!inp || !out) return true;
    const step = Math.pow(2, this._bits - 1);
    for (let i = 0; i < inp.length; i++) {
      this._phase += this._rate;
      if (this._phase >= 1) {
        this._phase -= 1;
        this._held = Math.round(inp[i] * step) / step;
      }
      out[i] = this._held;
    }
    return true;
  }
}

registerProcessor("bitcrusher", BitcrusherProcessor);
