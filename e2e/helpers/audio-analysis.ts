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
