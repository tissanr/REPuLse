import { expect, type Page, test } from '@playwright/test';
import { assertFinitePcm, countOnsets, goertzel, mixMono, rms } from './helpers/audio-analysis';

// Phase TL7a — per-event filters, shape, crush, gain and velocity, checked on the
// offline-js harness (JS voices → native BiquadFilterNode / WaveShaperNode).
// Concrete spectral assertions only (Goertzel), no claims about how it sounds.

type RenderResult = {
  sampleRate: number;
  left: number[];
  right: number[];
};

declare global {
  interface Window {
    __REPULSE_TEST_READY__?: boolean;
    __REPULSE_TEST__?: {
      reset(): boolean;
      registerTestSample(bank: string, kind: string): string;
      renderOffline(code: string, cycles?: number, options?: unknown): Promise<RenderResult>;
    };
    __TL7A_NODES__?: { biquad: number; shaper: number };
  }
}

const C4 = 261.6255653005986;

async function render(page: Page, code: string, cycles = 1) {
  const r = await test.step(`render ${code}`, async () =>
    page.evaluate(
      async ({ src, n }) => window.__REPULSE_TEST__!.renderOffline(src, n),
      { src: code, n: cycles },
    ));
  assertFinitePcm(r.left);
  assertFinitePcm(r.right);
  return { sr: r.sampleRate, mono: mixMono(r.left, r.right) };
}

function slice(mono: number[], sr: number, from: number, to: number): number[] {
  return mono.slice(Math.round(from * sr), Math.round(to * sr));
}

const db = (ratio: number) => 20 * Math.log10(ratio);

/**
 * Mean Goertzel magnitude (or power, when `power`) over `from`…`to` Hz in
 * `step` Hz steps — a noise-robust band energy.
 */
function band(buf: number[], sr: number, from: number, to: number, step = 250, power = false): number {
  let sum = 0;
  let n = 0;
  for (let hz = from; hz <= to; hz += step) {
    const g = goertzel(buf, sr, hz);
    sum += power ? g * g : g;
    n += 1;
  }
  return sum / n;
}

test.beforeEach(async ({ page }) => {
  await page.goto('/test-harness.html');
  await expect
    .poll(() => page.evaluate(() => Boolean(window.__REPULSE_TEST_READY__)))
    .toBe(true);
  await page.evaluate(() => {
    window.__REPULSE_TEST__!.reset();
    window.__REPULSE_TEST__!.registerTestSample('tl7a-noise', 'noise');
  });
});

test('lpf 400 lowers the 8th saw harmonic by ≥ 15 dB vs lpf 8000', async ({ page }) => {
  const code = (hz: number) => `(->> (seq :c4 :c4 :c4 :c4) (synth :saw) (lpf ${hz}))`;
  const dark = await render(page, code(400));
  const bright = await render(page, code(8000));
  const h8 = (m: number[], sr: number) => goertzel(slice(m, sr, 0.05, 0.45), sr, C4 * 8);
  const drop = db(h8(bright.mono, bright.sr) / h8(dark.mono, dark.sr));
  expect(drop).toBeGreaterThanOrEqual(15);
});

test('signal-driven lpf sweep: 8 hats with rising high-band energy', async ({ page }) => {
  // The JS hi-hat is Math.random noise and the top sweep steps differ by only
  // 1–2 dB, so each hat slot's 4–8 kHz power is averaged over 8 identical cycles.
  const cycles = 8;
  const { sr, mono } = await render(page, '(lpf (range 400 6000 saw) (fast 8 (seq :hh)))', cycles);
  expect(countOnsets(slice(mono, sr, 0, 2), sr, { threshold: 0.0005, minGapSeconds: 0.1 })).toBe(8);
  const energy = Array.from({ length: 8 }, (_, i) => {
    let p = 0;
    for (let c = 0; c < cycles; c += 1) {
      const t = c * 2 + i * 0.25;
      p += band(slice(mono, sr, t, t + 0.05), sr, 4000, 8000, 25, true);
    }
    return p / cycles;
  });
  for (let i = 1; i < 8; i += 1) {
    expect(energy[i], `hat ${i} brighter than hat ${i - 1}`).toBeGreaterThan(energy[i - 1]);
  }
});

test('gain 0.5 is 6 dB quieter; velocity 0.5 × gain 2 is unchanged', async ({ page }) => {
  const plain = rms((await render(page, '(seq :bd)')).mono);
  const half = rms((await render(page, '(gain 0.5 (seq :bd))')).mono);
  const unity = rms((await render(page, '(velocity 0.5 (gain 2 (seq :bd)))')).mono);
  expect(Math.abs(db(half / plain) + 6.02)).toBeLessThanOrEqual(0.5);
  expect(Math.abs(db(unity / plain))).toBeLessThanOrEqual(0.1);
});

test('lpf 300 on a sample event drops its high band by ≥ 15 dB', async ({ page }) => {
  const dry = await render(page, '(pure (sound :tl7a-noise 0))');
  const wet = await render(page, '(lpf 300 (sound :tl7a-noise 0))');
  const hi = (m: number[], sr: number) => band(slice(m, sr, 0.05, 0.9), sr, 5000, 8000);
  expect(db(hi(dry.mono, dry.sr) / hi(wet.mono, wet.sr))).toBeGreaterThanOrEqual(15);
});

test('shape 0.8 raises the 3rd harmonic of a sine by ≥ 20 dB', async ({ page }) => {
  const dry = await render(page, '(seq :c4)');
  const wet = await render(page, '(shape 0.8 (seq :c4))');
  const h3 = (m: number[], sr: number) => goertzel(slice(m, sr, 0.05, 0.45), sr, C4 * 3);
  expect(db(h3(wet.mono, wet.sr) / h3(dry.mono, dry.sr))).toBeGreaterThanOrEqual(20);
});

test('crush 4 quantises a saw to at most 17 levels', async ({ page }) => {
  const { sr, mono } = await render(page, '(->> (seq :c4) (synth :saw) (crush 4))');
  const values = new Set(slice(mono, sr, 0.05, 0.15).map((v) => Math.round(v * 1e6)));
  expect(values.size).toBeLessThanOrEqual(17);
  expect(values.size).toBeGreaterThanOrEqual(3);
});

test('events without inserts create no extra filter or shaper nodes', async ({ page }) => {
  await page.evaluate(() => {
    const counts = { biquad: 0, shaper: 0 };
    window.__TL7A_NODES__ = counts;
    const proto = BaseAudioContext.prototype;
    const biquad = proto.createBiquadFilter;
    const shaper = proto.createWaveShaper;
    proto.createBiquadFilter = function patched(this: BaseAudioContext) {
      counts.biquad += 1;
      return biquad.call(this);
    };
    proto.createWaveShaper = function patched(this: BaseAudioContext) {
      counts.shaper += 1;
      return shaper.call(this);
    };
  });
  const nodes = () => page.evaluate(() => ({ ...window.__TL7A_NODES__! }));
  await render(page, '(->> (seq :c4 :e4) (synth :saw) (amp 0.8) (gain 0.5))');
  expect(await nodes()).toEqual({ biquad: 0, shaper: 0 });
  await render(page, '(->> (seq :c4) (synth :saw) (lpf 800) (crush 8) (shape 0.3))');
  expect(await nodes()).toEqual({ biquad: 1, shaper: 2 });
});

// JS↔WASM parity. PARITY_DB is the WASM render of the same parameters, pinned in
// packages/audio/src/lib.rs (tl7a_insert_tests::PARITY_DB): the 5th (1308 Hz,
// nearest 1200 Hz) and 15th (3924 Hz, nearest 4000 Hz) harmonics of a C4 saw
// through lpf 1200 / resonance 4, in dB relative to the 262 Hz fundamental.
const PARITY_DB = [-4.69, -44.36];

test('lpf + resonance on the JS path matches the WASM render within 2 dB', async ({ page }) => {
  const { sr, mono } = await render(page, '(->> (seq :c4) (synth :saw) (lpf 1200) (resonance 4))');
  const w = slice(mono, sr, 0.05, 0.5);
  const f0 = goertzel(w, sr, C4);
  const levels = [5, 15].map((k) => db(goertzel(w, sr, C4 * k) / f0));
  test.info().annotations.push({ type: 'js-parity-db', description: levels.join(', ') });
  levels.forEach((got, i) => {
    expect(Math.abs(got - PARITY_DB[i]), `harmonic ${[5, 15][i]}: JS ${got} dB`).toBeLessThanOrEqual(2);
  });
});
