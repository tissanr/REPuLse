// Reverb impulse-response generators (Phase FX1, D6/D8). Pure: no AudioContext,
// so the generators can be tested by importing this module.
//
// Every generator uses mulberry32 noise with a constant seed per type and
// channel: left and right differ, and every render is identical. `classic` is
// the pre-FX1 formula of reverb.js (noise × (1 − i/N)³, 2.5 s), seeded.
//
// TL7b coordination (D8): the orbit reverbs (app code) use the same PRNG and
// the same classic formula; whichever phase lands second copies the constants.

export const TYPES = ["classic", "room", "hall", "plate", "spring", "gated", "reverse"];

export const PRESETS = {
  classic: { decay: 2.5, size: 0.5, damp: 0,    trim: 1.0 },
  room:    { decay: 0.6, size: 0.3, damp: 0.5,  trim: 1.9 },
  hall:    { decay: 3.0, size: 0.8, damp: 0.35, trim: 0.8 },
  plate:   { decay: 1.8, size: 0.5, damp: 0.2,  trim: 1.1 },
  spring:  { decay: 2.0, size: 0.5, damp: 0,    trim: 1.0 },
  gated:   { decay: 0.3, size: 0.5, damp: 0.2,  trim: 2.8 },
  reverse: { decay: 1.0, size: 0.5, damp: 0.2,  trim: 1.6 },
};

const SEEDS = {
  classic: [0x1a2b3c4d, 0x5e6f7081],
  room:    [0x2468ace0, 0x13579bdf],
  hall:    [0x0badf00d, 0x0ddba11a],
  plate:   [0x51ab51ab, 0x7e57ab1e],
  spring:  [0x5b1a6c3d, 0x6e7f8091],
  gated:   [0x6a7e6a7e, 0x9a7ed00d],
  reverse: [0x7e5e75e0, 0x0e5e75e7],
};

const REFLECTIONS = [0.19, 0.31, 0.43, 0.52, 0.66, 0.74, 0.87, 1.0];

/** mulberry32 → uniform values in −1…1. */
export function mulberry32(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return (((t ^ (t >>> 14)) >>> 0) / 4294967296) * 2 - 1;
  };
}

const r2 = (x) => Math.round(x * 100) / 100;

/** The effective parameters: values the user set, else the type's preset. */
export function resolve(type, p = {}) {
  const pr = PRESETS[type] ?? PRESETS.classic;
  return {
    decay: Math.min(10, Math.max(0.1, p.decay ?? pr.decay)),
    size:  Math.min(1, Math.max(0, p.size ?? pr.size)),
    damp:  Math.min(1, Math.max(0, p.damp ?? pr.damp)),
  };
}

/** Cache key: type, decay, size, damp (rounded to 0.01) and the sample rate. */
export function irKey(type, p, sampleRate) {
  const q = resolve(type, p);
  return `${type}|${r2(q.decay)}|${r2(q.size)}|${r2(q.damp)}|${sampleRate}`;
}

/** Two one-pole low-passes in series whose cutoff glides down over the buffer. */
function damped(x, damp, sr) {
  if (damp <= 0) return x;
  const N = x.length;
  let y1 = 0, y2 = 0;
  for (let i = 0; i < N; i++) {
    const fc = Math.min(0.45 * sr, 18000 * Math.pow(2, (-7 * damp * i) / N));
    const a = 1 - Math.exp((-2 * Math.PI * fc) / sr);
    y1 += a * (x[i] - y1);
    y2 += a * (y1 - y2);
    x[i] = y2;
  }
  return x;
}

function fadeTail(x, sr, secs) {
  const n = Math.min(x.length, Math.round(secs * sr));
  const start = x.length - n;
  for (let i = 0; i < n; i++) x[start + i] *= 1 - (i + 1) / n;
}

function gen(type, q, sr, ch) {
  const noise = mulberry32(SEEDS[type][ch]);
  const { decay, size, damp } = q;
  switch (type) {
    case "classic": {
      const N = Math.round(decay * sr);
      const x = new Float32Array(N);
      for (let i = 0; i < N; i++) x[i] = noise() * Math.pow(1 - i / N, 3);
      return damped(x, damp, sr);
    }
    case "room":
    case "hall":
    case "plate": {
      const N = Math.round(1.2 * decay * sr);
      const x = new Float32Array(N);
      const rise = type === "plate" ? 0 : 0.002 + 0.078 * size;
      for (let i = 0; i < N; i++) {
        const t = i / sr;
        let e = Math.exp((-6.908 * t) / decay);
        if (t < rise) e *= t / rise;
        x[i] = noise() * e;
      }
      fadeTail(x, sr, 0.010);
      damped(x, damp, sr);
      if (type !== "plate") {
        const lvl = type === "room" ? 0.6 : 0.3;
        const span = (0.010 + 0.070 * size) * (ch === 1 ? 1.07 : 1);
        REFLECTIONS.forEach((f, k) => {
          const i = Math.round(f * span * sr);
          if (i < N) x[i] += lvl * Math.pow(0.8, k) * (k % 2 === 0 ? 1 : -1);
        });
      }
      return x;
    }
    case "gated":
    case "reverse": {
      const N = Math.round((decay + 0.005) * sr);
      const x = new Float32Array(N);
      const nd = Math.round(decay * sr);
      for (let i = 0; i < N; i++) {
        const t = i / sr;
        const env = type === "gated"
          ? 1 - 0.5 * Math.min(1, t / decay)
          : Math.pow(10, -2 * (1 - Math.min(1, t / decay)));
        x[i] = noise() * env;
        if (i >= nd) x[i] *= 1 - (i - nd + 1) / (N - nd);   // 5 ms fade to 0
      }
      return damped(x, damp, sr);
    }
    case "spring": {
      const N = Math.round(1.2 * decay * sr);
      const x = new Float32Array(N);
      const Td = (0.030 + 0.030 * size) * (ch === 1 ? 1.07 : 1);
      const ln40 = Math.log(40);
      for (let k = 0; k * Td < 1.2 * decay; k++) {
        const D = (k + 1) * 0.012;
        const amp = Math.pow(10, (-3 * k * Td) / decay) / Math.sqrt(D);
        const start = Math.round(k * Td * sr);
        const len = Math.round(D * sr);
        for (let j = 0; j < len && start + j < N; j++) {
          const tau = j / sr;
          const ph = 2 * Math.PI * 100 * (D / ln40) * (Math.pow(40, tau / D) - 1);
          const w = 0.5 - 0.5 * Math.cos((2 * Math.PI * j) / Math.max(1, len - 1));
          x[start + j] += amp * w * Math.sin(ph);
        }
      }
      // the noise generator is unused for spring; keep the seed table uniform
      noise();
      return damped(x, damp, sr);
    }
    default:
      throw new Error(`unknown reverb type ${type}`);
  }
}

const CACHE_MAX = 12;
const cache = new Map();   // key → [L, R], insertion order = recency

/** → [Float32Array L, Float32Array R]; cached (12 entries, LRU). */
export function makeIR(type, p, sampleRate) {
  if (!TYPES.includes(type)) throw new Error(`unknown reverb type ${type}`);
  const key = irKey(type, p, sampleRate);
  const hit = cache.get(key);
  if (hit) {
    cache.delete(key);
    cache.set(key, hit);
    return hit;
  }
  const q = resolve(type, p);
  const ir = [gen(type, q, sampleRate, 0), gen(type, q, sampleRate, 1)];
  cache.set(key, ir);
  while (cache.size > CACHE_MAX) cache.delete(cache.keys().next().value);
  return ir;
}
