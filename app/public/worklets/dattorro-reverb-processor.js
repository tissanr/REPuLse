// Dattorro plate reverb — AudioWorkletProcessor
// Reference: Jon Dattorro, "Effect Design: Part 1 — Reverberator and Other Filters"
//             Journal AES, vol 45 no 9/10, Sept/Oct 1997.
//
// All delay-line lengths are specified at the reference sample rate (29761 Hz) and
// scaled to the actual AudioContext sample rate at construction time.

const REF_SR = 29761;

// ---------------------------------------------------------------------------
// Ring buffer — fixed-size circular buffer used for delay lines.
// readAt(n) returns the sample written n steps before the most recent write.
// ---------------------------------------------------------------------------
class Ring {
  constructor(size) {
    this.size = size;
    this.buf  = new Float32Array(size);
    this.pos  = 0;
  }
  write(x) {
    this.buf[this.pos] = x;
    this.pos = (this.pos + 1) % this.size;
  }
  // delay=0  → last written sample
  // delay=N  → sample written N steps ago
  readAt(delay) {
    return this.buf[(this.pos - 1 - delay + this.size * 4) % this.size];
  }
}

// ---------------------------------------------------------------------------
// Schroeder all-pass filter of fixed delay N.
// Transfer function: H(z) = (-g + z^-N) / (1 - g*z^-N)
// Stores w(n) = x(n) + g*w(n-N) in the ring; outputs y(n) = w(n-N) - g*w(n).
// ---------------------------------------------------------------------------
class Allpass {
  constructor(delay, g) {
    this.delay = delay;
    this.g     = g;
    this.ring  = new Ring(delay);
  }
  tick(x) {
    const r  = this.ring;
    const wN = r.buf[r.pos];          // w(n-N)
    const w  = x + this.g * wN;      // w(n)
    const y  = wN - this.g * w;      // y(n)
    r.buf[r.pos] = w;
    r.pos = (r.pos + 1) % r.size;
    return y;
  }
}

// ---------------------------------------------------------------------------
// Modulated all-pass — delay length varies each sample via an external offset.
// maxDelay is the maximum possible delay (allocates buffer of that size + 1).
// ---------------------------------------------------------------------------
class ModAllpass {
  constructor(maxDelay, g) {
    this.maxDelay = maxDelay;
    this.g   = g;
    this.buf  = new Float32Array(maxDelay + 1);
    this.size = maxDelay + 1;
    this.pos  = 0;
  }
  // delay: instantaneous delay in samples (integer, 1..maxDelay)
  tick(x, delay) {
    const d       = Math.max(1, Math.min(this.maxDelay, delay | 0));
    const readPos = (this.pos - d + this.size * 4) % this.size;
    const wN      = this.buf[readPos];
    const w       = x + this.g * wN;
    const y       = wN - this.g * w;
    this.buf[this.pos] = w;
    this.pos = (this.pos + 1) % this.size;
    return y;
  }
}

// ---------------------------------------------------------------------------
// DattorroReverbProcessor
// ---------------------------------------------------------------------------
class DattorroReverbProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    const k = sampleRate / REF_SR;   // scale factor
    this._k = k;

    // Algorithm state
    this._bwY   = 0;          // bandwidth filter state
    this._ldamp = 0;          // left tank damping filter state
    this._rdamp = 0;          // right tank damping filter state
    this._ltank = 0;          // left tank output (fed into right)
    this._rtank = 0;          // right tank output (fed into left)
    this._lfo1  = 0;
    this._lfo2  = Math.PI * 0.5;
    this._lfoInc = (2 * Math.PI * 0.5) / sampleRate;  // 0.5 Hz LFO

    // Modulation depth: ±8 samples at REF_SR
    this._modDepth = Math.round(8 * k);

    // Default parameters
    this._decay     = 0.5;
    this._damping   = 0.0005;    // very bright by default
    this._bandwidth = 0.9999;    // full bandwidth by default
    this._pdSamples = 0;         // pre-delay in samples

    // Pre-delay (max 500 ms)
    this._pd = new Ring(Math.ceil(0.5 * sampleRate) + 2);

    // Input diffusion (4 series all-passes)
    this._a1 = new Allpass(Math.round(142 * k), 0.75);
    this._a2 = new Allpass(Math.round(107 * k), 0.75);
    this._a3 = new Allpass(Math.round(379 * k), 0.625);
    this._a4 = new Allpass(Math.round(277 * k), 0.625);

    // Left tank
    //   ModAP5: base delay 672, max delay 672+16 (LFO range)
    //   D6:     delay 4453
    //   AP7:    delay 1800
    //   D8:     delay 3720
    this._la1 = new ModAllpass(Math.round((672 + 16) * k), 0.7);
    this._ld1 = new Ring(Math.round(4453 * k) + 2);
    this._la2 = new Allpass(Math.round(1800 * k), 0.5);
    this._ld2 = new Ring(Math.round(3720 * k) + 2);

    // Right tank
    //   ModAP9: base delay 908, max delay 908+16
    //   D10:    delay 4217
    //   AP11:   delay 2656
    //   D12:    delay 3163
    this._ra1 = new ModAllpass(Math.round((908 + 16) * k), 0.7);
    this._rd1 = new Ring(Math.round(4217 * k) + 2);
    this._ra2 = new Allpass(Math.round(2656 * k), 0.5);
    this._rd2 = new Ring(Math.round(3163 * k) + 2);

    this.port.onmessage = ({ data }) => {
      if (data.type === 'setParam') this._applyParam(data.name, data.value);
    };
  }

  _applyParam(name, value) {
    switch (name) {
      case 'decay':     this._decay     = Math.max(0, Math.min(0.9999, +value)); break;
      case 'damping':   this._damping   = Math.max(0, Math.min(0.9999, +value)); break;
      case 'bandwidth': this._bandwidth = Math.max(0, Math.min(0.9999, +value)); break;
      case 'predelay':  this._pdSamples = Math.min(
                          Math.round(+value * sampleRate),
                          Math.ceil(0.5 * sampleRate)); break;
    }
  }

  process(inputs, outputs) {
    const inp    = inputs[0];
    const outp   = outputs[0];
    if (!outp || !outp[0]) return true;

    const inL  = inp && inp[0] ? inp[0] : null;
    const inR  = inp && inp[1] ? inp[1] : inL;
    const outL = outp[0];
    const outR = outp[1] || outp[0];

    const { _decay: dec, _damping: dam, _k: k } = this;
    const bw       = this._bandwidth;
    const modDepth = this._modDepth;
    const d672k    = Math.round(672 * k);
    const d908k    = Math.round(908 * k);
    const d4453k   = Math.round(4453 * k);
    const d4217k   = Math.round(4217 * k);
    const d3720k   = Math.round(3720 * k);
    const d3163k   = Math.round(3163 * k);

    for (let i = 0; i < outL.length; i++) {
      // Mono mix of input
      const dry = inL ? (inL[i] + (inR ? inR[i] : inL[i])) * 0.5 : 0;

      // Pre-delay
      this._pd.write(dry);
      const pd = this._pd.readAt(this._pdSamples);

      // Bandwidth (input low-pass)
      this._bwY += bw * (pd - this._bwY);

      // Input diffusion
      let d = this._bwY;
      d = this._a1.tick(d);
      d = this._a2.tick(d);
      d = this._a3.tick(d);
      d = this._a4.tick(d);

      // LFO
      this._lfo1 += this._lfoInc;
      this._lfo2 += this._lfoInc;
      const lm1 = d672k + Math.round(Math.sin(this._lfo1) * modDepth);
      const lm2 = d908k + Math.round(Math.sin(this._lfo2) * modDepth);

      // Left tank  (fed by d + decay * rtank from previous sample)
      const leftIn = d + dec * this._rtank;
      this._ld1.write(this._la1.tick(leftIn, lm1));
      const d6out   = this._ld1.readAt(d4453k);
      this._ldamp   = (1 - dam) * d6out + dam * this._ldamp;   // damping LPF
      this._ld2.write(this._la2.tick(this._ldamp * dec));
      this._ltank   = this._ld2.readAt(d3720k);

      // Right tank (fed by d + decay * ltank just computed above)
      const rightIn = d + dec * this._ltank;
      this._rd1.write(this._ra1.tick(rightIn, lm2));
      const d10out  = this._rd1.readAt(d4217k);
      this._rdamp   = (1 - dam) * d10out + dam * this._rdamp;  // damping LPF
      this._rd2.write(this._ra2.tick(this._rdamp * dec));
      this._rtank   = this._rd2.readAt(d3163k);

      // Output taps — Dattorro 1997, Appendix
      const s  = k;
      const wL = 0.6 * (
        + this._ld1.readAt(Math.round(266  * s))
        + this._ld1.readAt(Math.round(2974 * s))
        + this._ld2.readAt(Math.round(1990 * s))
        - this._rd1.readAt(Math.round(141  * s))
        - this._rd2.readAt(Math.round(335  * s))
      );
      const wR = 0.6 * (
        + this._rd1.readAt(Math.round(353  * s))
        + this._rd1.readAt(Math.round(3627 * s))
        + this._rd2.readAt(Math.round(2111 * s))
        - this._ld1.readAt(Math.round(1990 * s))
        - this._ld2.readAt(Math.round(112  * s))
      );

      outL[i] = wL;
      outR[i] = wR;
    }
    return true;
  }
}

registerProcessor('dattorro-reverb', DattorroReverbProcessor);
