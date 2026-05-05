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
