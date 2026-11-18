export type OnsetOptions = {
  threshold?: number;
  minGapSeconds?: number;
};

export function rms(buffer: number[]): number {
  if (buffer.length === 0) return 0;
  const sum = buffer.reduce((acc, sample) => acc + sample * sample, 0);
  return Math.sqrt(sum / buffer.length);
}

export function rmsStereo(
  left: number[],
  right: number[],
): { left: number; right: number } {
  return { left: rms(left), right: rms(right) };
}

export function mixMono(left: number[], right: number[]): number[] {
  const n = Math.min(left.length, right.length);
  const out = new Array<number>(n);
  for (let i = 0; i < n; i += 1) {
    out[i] = (left[i] + right[i]) * 0.5;
  }
  return out;
}

export function countOnsets(
  buffer: number[],
  sampleRate: number,
  options: OnsetOptions = {},
): number {
  const threshold = options.threshold ?? 0.035;
  const minGap = Math.floor((options.minGapSeconds ?? 0.18) * sampleRate);
  let count = 0;
  let lastOnset = -minGap;
  let wasAbove = false;

  for (let i = 0; i < buffer.length; i += 1) {
    const above = Math.abs(buffer[i]) >= threshold;
    if (above && !wasAbove && i - lastOnset >= minGap) {
      count += 1;
      lastOnset = i;
    }
    wasAbove = above;
  }

  return count;
}

export function tailRms(
  buffer: number[],
  sampleRate: number,
  tailSeconds: number,
): number {
  const n = Math.max(1, Math.floor(sampleRate * tailSeconds));
  return rms(buffer.slice(Math.max(0, buffer.length - n)));
}

export function assertFinitePcm(buffer: number[]): void {
  for (let i = 0; i < buffer.length; i += 1) {
    if (!Number.isFinite(buffer[i])) {
      throw new Error(`PCM sample ${i} is not finite: ${buffer[i]}`);
    }
  }
}

/**
 * Magnitude of a single frequency bin (Goertzel algorithm), normalised by the
 * buffer length so values are comparable across buffer sizes.
 */
export function goertzel(buffer: number[], sampleRate: number, hz: number): number {
  const n = buffer.length;
  if (n === 0) return 0;
  const k = Math.round((n * hz) / sampleRate);
  const w = (2 * Math.PI * k) / n;
  const coeff = 2 * Math.cos(w);
  let s1 = 0;
  let s2 = 0;
  for (let i = 0; i < n; i += 1) {
    const s0 = buffer[i] + coeff * s1 - s2;
    s2 = s1;
    s1 = s0;
  }
  const power = s1 * s1 + s2 * s2 - coeff * s1 * s2;
  return Math.sqrt(Math.max(0, power)) / n;
}

export type Wav16 = {
  sampleRate: number;
  frames: number;
  left: number[];
  right: number[];
};

/**
 * Parse a 16-bit stereo PCM RIFF/WAVE file (as written by repulse.wav) into
 * float channels in [-1, 1] (sample / 32767).
 */
export function parseWav16(buf: Buffer): Wav16 {
  const tag = (off: number) => buf.toString('ascii', off, off + 4);
  if (tag(0) !== 'RIFF' || tag(8) !== 'WAVE') throw new Error('not a RIFF/WAVE file');
  let off = 12;
  let fmt: { format: number; channels: number; sampleRate: number; bits: number } | null = null;
  let dataOff = -1;
  let dataLen = 0;
  while (off + 8 <= buf.length) {
    const id = tag(off);
    const len = buf.readUInt32LE(off + 4);
    if (id === 'fmt ') {
      fmt = {
        format: buf.readUInt16LE(off + 8),
        channels: buf.readUInt16LE(off + 10),
        sampleRate: buf.readUInt32LE(off + 12),
        bits: buf.readUInt16LE(off + 22),
      };
    } else if (id === 'data') {
      dataOff = off + 8;
      dataLen = len;
      break;
    }
    off += 8 + len + (len % 2);
  }
  if (!fmt) throw new Error('WAV has no fmt chunk');
  if (fmt.format !== 1) throw new Error(`WAV is not PCM (format ${fmt.format})`);
  if (fmt.channels !== 2) throw new Error(`WAV has ${fmt.channels} channels, expected 2`);
  if (fmt.bits !== 16) throw new Error(`WAV has ${fmt.bits} bits, expected 16`);
  if (dataOff < 0) throw new Error('WAV has no data chunk');
  const frames = Math.floor(dataLen / 4);
  const left = new Array<number>(frames);
  const right = new Array<number>(frames);
  for (let i = 0; i < frames; i += 1) {
    left[i] = buf.readInt16LE(dataOff + i * 4) / 32767;
    right[i] = buf.readInt16LE(dataOff + i * 4 + 2) / 32767;
  }
  return { sampleRate: fmt.sampleRate, frames, left, right };
}

/** RMS of consecutive non-overlapping windows of `size` samples (last partial window included). */
export function rmsPerWindow(mono: number[], size: number): number[] {
  const out: number[] = [];
  for (let i = 0; i < mono.length; i += size) {
    out.push(rms(mono.slice(i, Math.min(mono.length, i + size))));
  }
  return out;
}

/** Amplitude ratio in dB, floored at 1e-9 to stay finite. */
export function toDb(x: number): number {
  return 20 * Math.log10(Math.max(x, 1e-9));
}

/** In-place radix-2 FFT of (re, im); both length must be a power of two. */
function fftInPlace(re: Float64Array, im: Float64Array): void {
  const n = re.length;
  for (let i = 1, j = 0; i < n; i += 1) {
    let bit = n >> 1;
    for (; j & bit; bit >>= 1) j ^= bit;
    j ^= bit;
    if (i < j) {
      [re[i], re[j]] = [re[j], re[i]];
      [im[i], im[j]] = [im[j], im[i]];
    }
  }
  for (let len = 2; len <= n; len <<= 1) {
    const ang = (-2 * Math.PI) / len;
    const wr = Math.cos(ang);
    const wi = Math.sin(ang);
    for (let i = 0; i < n; i += len) {
      let cr = 1;
      let ci = 0;
      for (let k = 0; k < len / 2; k += 1) {
        const a = i + k;
        const b = a + len / 2;
        const tr = re[b] * cr - im[b] * ci;
        const ti = re[b] * ci + im[b] * cr;
        re[b] = re[a] - tr;
        im[b] = im[a] - ti;
        re[a] += tr;
        im[a] += ti;
        const nr = cr * wr - ci * wi;
        ci = cr * wi + ci * wr;
        cr = nr;
      }
    }
  }
}

const FFT_SIZE = 4096;
const CENTROID_MIN_RMS = 0.01; // −40 dBFS

/**
 * Spectral centroid in Hz: magnitude-weighted mean frequency of a 4096-point
 * Hann-windowed FFT, averaged over the non-overlapping windows whose RMS is above
 * −40 dBFS (silence would only add noise). Returns 0 when no window is loud enough.
 */
export function spectralCentroid(mono: number[], sampleRate: number): number {
  const hann = new Float64Array(FFT_SIZE);
  for (let i = 0; i < FFT_SIZE; i += 1) hann[i] = 0.5 - 0.5 * Math.cos((2 * Math.PI * i) / (FFT_SIZE - 1));
  let sum = 0;
  let count = 0;
  for (let start = 0; start + FFT_SIZE <= mono.length; start += FFT_SIZE) {
    const frame = mono.slice(start, start + FFT_SIZE);
    if (rms(frame) < CENTROID_MIN_RMS) continue;
    const re = new Float64Array(FFT_SIZE);
    const im = new Float64Array(FFT_SIZE);
    for (let i = 0; i < FFT_SIZE; i += 1) re[i] = frame[i] * hann[i];
    fftInPlace(re, im);
    let num = 0;
    let den = 0;
    for (let k = 1; k < FFT_SIZE / 2; k += 1) {
      const mag = Math.hypot(re[k], im[k]);
      num += mag * ((k * sampleRate) / FFT_SIZE);
      den += mag;
    }
    if (den > 0) {
      sum += num / den;
      count += 1;
    }
  }
  return count === 0 ? 0 : sum / count;
}

/**
 * Fundamental frequency in Hz by autocorrelation: the first local maximum within
 * 90 % of the global one over lags sampleRate/maxHz … sampleRate/minHz (so a strong
 * 2nd period cannot win), refined by parabolic interpolation. 0 when there is none.
 */
export function estimatePitch(
  mono: number[],
  sampleRate: number,
  minHz: number,
  maxHz: number,
): number {
  const minLag = Math.max(2, Math.floor(sampleRate / maxHz));
  const maxLag = Math.min(mono.length - 2, Math.ceil(sampleRate / minHz));
  if (maxLag <= minLag) return 0;
  const n = mono.length - maxLag;
  const r = new Float64Array(maxLag + 2);
  for (let lag = minLag - 1; lag <= maxLag + 1; lag += 1) {
    let acc = 0;
    for (let i = 0; i < n; i += 1) acc += mono[i] * mono[i + lag];
    r[lag] = acc;
  }
  let best = -Infinity;
  for (let lag = minLag; lag <= maxLag; lag += 1) best = Math.max(best, r[lag]);
  if (!(best > 0)) return 0;
  for (let lag = minLag; lag <= maxLag; lag += 1) {
    if (r[lag] >= 0.9 * best && r[lag] >= r[lag - 1] && r[lag] >= r[lag + 1]) {
      const a = r[lag - 1];
      const b = r[lag];
      const c = r[lag + 1];
      const denom = a - 2 * b + c;
      const shift = denom === 0 ? 0 : (0.5 * (a - c)) / denom;
      return sampleRate / (lag + shift);
    }
  }
  return 0;
}
