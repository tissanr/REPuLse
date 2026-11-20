import { expect, type Page, test } from '@playwright/test';
import { assertFinitePcm, mixMono, rms } from './helpers/audio-analysis';

// Phase TL7c — filter envelopes, pitch envelope, vowel, djf, postgain, nudge and
// delay-sync, checked on the offline-js harness (JS voices → native nodes and
// AudioParam automation). Concrete spectral/level/time assertions only.

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
  }
}

const A2 = 110;

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

function win(mono: number[], sr: number, from: number, to: number): number[] {
  return mono.slice(Math.round(from * sr), Math.round(to * sr));
}

const db = (ratio: number) => 20 * Math.log10(Math.max(ratio, 1e-12));

/** Goertzel magnitude at exactly hz (no bin rounding), as in lib.rs tests. */
function goertzel(buf: number[], sr: number, hz: number): number {
  const coeff = 2 * Math.cos((2 * Math.PI * hz) / sr);
  let s1 = 0;
  let s2 = 0;
  for (const x of buf) {
    const s0 = x + coeff * s1 - s2;
    s2 = s1;
    s1 = s0;
  }
  return Math.sqrt(Math.max(0, s1 * s1 + s2 * s2 - coeff * s1 * s2));
}

function hann(buf: number[]): number[] {
  const n = buf.length;
  return buf.map((x, i) => x * 0.5 * (1 - Math.cos((2 * Math.PI * i) / n)));
}

/** Energy of the A2 harmonics in 1–3 kHz (k = 10…27), Hann window — as lib.rs. */
function highBand(buf: number[], sr: number): number {
  const w = hann(buf);
  let p = 0;
  for (let k = 10; k <= 27; k += 1) p += goertzel(w, sr, A2 * k) ** 2;
  return Math.sqrt(p);
}

/** Mean power over from…to Hz in step Hz — a noise-robust band energy. */
function band(buf: number[], sr: number, from: number, to: number, step = 50): number {
  const w = hann(buf);
  let sum = 0;
  let n = 0;
  for (let hz = from; hz <= to; hz += step) {
    sum += goertzel(w, sr, hz) ** 2;
    n += 1;
  }
  return Math.sqrt(sum / n);
}

const peak = (buf: number[]) => buf.reduce((m, x) => Math.max(m, Math.abs(x)), 0);

function firstAbove(buf: number[], threshold: number): number {
  return buf.findIndex((x) => Math.abs(x) >= threshold);
}

test.beforeEach(async ({ page }) => {
  await page.goto('/test-harness.html');
  await expect
    .poll(() => page.evaluate(() => Boolean(window.__REPULSE_TEST_READY__)))
    .toBe(true);
  await page.evaluate(() => {
    window.__REPULSE_TEST__!.reset();
    window.__REPULSE_TEST__!.registerTestSample('tl7c-noise', 'noise');
    window.__REPULSE_TEST__!.registerTestSample('tl7c-tone', 'tone');
  });
});

// JS↔WASM parity for the filter envelope. ENV_PARITY_DB is the WASM render of
// the same parameters, pinned in packages/audio/src/lib.rs
// (tl7c_tests::ENV_PARITY_DB): early (5–25 ms) over late (300–320 ms) energy
// of the 1–3 kHz harmonics of an A2 saw through lpf 300 / lp-env 4 / lp-decay 0.2.
const ENV_PARITY_DB = 30.36;

test('lp-env 4 opens the filter early and closes it by 300 ms (and matches WASM)', async ({ page }) => {
  const { sr, mono } = await render(
    page,
    '(->> (seq :a2) (synth :saw) (decay 10) (lpf 300) (lp-env 4) (lp-decay 0.2))',
  );
  const ratio = db(highBand(win(mono, sr, 0.005, 0.025), sr) / highBand(win(mono, sr, 0.3, 0.32), sr));
  test.info().annotations.push({ type: 'js-env-parity-db', description: String(ratio) });
  expect(ratio).toBeGreaterThanOrEqual(10);
  expect(Math.abs(ratio - ENV_PARITY_DB), `JS ${ratio} dB vs WASM ${ENV_PARITY_DB} dB`).toBeLessThanOrEqual(3);
});

test('lp-release closes the filter after the event ends', async ({ page }) => {
  const { sr, mono } = await render(
    page,
    '(->> (seq :a2 :_) (synth :saw) (decay 10) (lpf 300) (lp-env 4) (lp-sustain 1) (lp-release 0.05))',
  );
  const held = highBand(win(mono, sr, 0.4, 0.6), sr);
  const released = highBand(win(mono, sr, 1.4, 1.6), sr);
  expect(db(held / released)).toBeGreaterThanOrEqual(10);
});

test('envelope without its filter changes nothing', async ({ page }) => {
  const plain = await render(page, '(->> (seq :a2) (synth :saw) (decay 1))');
  const env = await render(page, '(->> (seq :a2) (synth :saw) (decay 1) (lp-env 4))');
  expect(env.mono).toEqual(plain.mono);
});

for (const [name, code] of [
  ['sine voice', '(->> (seq :a4) (decay 10) (penv 12) (p-decay 0.5))'],
  ['sample', '(->> (sound :tl7c-tone 0) (penv 12) (p-decay 0.5))'],
] as const) {
  test(`penv 12 starts an octave up and falls back (${name})`, async ({ page }) => {
    const { sr, mono } = await render(page, code);
    const early = hann(win(mono, sr, 0.0, 0.02));
    const late = hann(win(mono, sr, 0.6, 0.7));
    expect(db(goertzel(early, sr, 880) / goertzel(early, sr, 440))).toBeGreaterThanOrEqual(10);
    expect(db(goertzel(late, sr, 440) / goertzel(late, sr, 880))).toBeGreaterThanOrEqual(10);
  });
}

test('vowel :a and :i differ at their formants', async ({ page }) => {
  const v = async (x: string) => {
    const { sr, mono } = await render(page, `(->> (seq :a2) (synth :saw) (decay 10) (vowel ${x}))`);
    return { sr, w: hann(win(mono, sr, 0.1, 0.5)) };
  };
  const a = await v(':a');
  const i = await v(':i');
  expect(db(goertzel(a.w, a.sr, 1100) / goertzel(i.w, i.sr, 1100)), 'a over i at 1100 Hz').toBeGreaterThanOrEqual(6);
  expect(db(goertzel(i.w, i.sr, 220) / goertzel(a.w, a.sr, 220)), 'i over a at 220 Hz').toBeGreaterThanOrEqual(6);
  const u = await v(':u');
  expect(db(goertzel(i.w, i.sr, 1870) / goertzel(u.w, u.sr, 1870)), 'i over u at its F2, 1870 Hz').toBeGreaterThanOrEqual(12);
});

test('djf: 0.1 cuts the highs, 0.9 cuts the lows, 0.5 is off', async ({ page }) => {
  const r = (code: string) => render(page, code);
  const dry = await r('(pure (sound :tl7c-noise 0))');
  const lo = await r('(djf 0.1 (sound :tl7c-noise 0))');
  const hi = await r('(djf 0.9 (sound :tl7c-noise 0))');
  const mid = await r('(djf 0.5 (sound :tl7c-noise 0))');
  const w = (m: number[], sr: number) => win(m, sr, 0.05, 0.9);
  const at4k = (x: { sr: number; mono: number[] }) => band(w(x.mono, x.sr), x.sr, 3500, 4500);
  const at200 = (x: { sr: number; mono: number[] }) => band(w(x.mono, x.sr), x.sr, 150, 250, 10);
  expect(db(at4k(dry) / at4k(lo))).toBeGreaterThanOrEqual(15);
  expect(db(at200(dry) / at200(hi))).toBeGreaterThanOrEqual(15);
  expect(mid.mono).toEqual(dry.mono);
});

test('postgain 0.5 is 6 dB quieter and applies after shape', async ({ page }) => {
  const plain = rms((await render(page, '(seq :bd)')).mono);
  const half = rms((await render(page, '(postgain 0.5 (seq :bd))')).mono);
  expect(Math.abs(db(half / plain) + 6.02)).toBeLessThanOrEqual(0.5);
  const shaped = peak((await render(page, '(->> (seq :c4) (shape 0.8))')).mono);
  const shapedHalf = peak((await render(page, '(->> (seq :c4) (shape 0.8) (postgain 0.5))')).mono);
  expect(Math.abs(shapedHalf / shaped - 0.5)).toBeLessThanOrEqual(0.01);
});

test('nudge 0.05 moves the onset 50 ms later', async ({ page }) => {
  const a = await render(page, '(seq :bd)');
  const b = await render(page, '(nudge 0.05 (seq :bd))');
  const t0 = firstAbove(a.mono, 0.01) / a.sr;
  const t1 = firstAbove(b.mono, 0.01) / b.sr;
  expect(Math.abs(t1 - t0 - 0.05)).toBeLessThanOrEqual(0.001);
});

test('delay-sync 1/4 puts the first echo a quarter cycle (0.5 s) after the hit', async ({ page }) => {
  const { sr, mono } = await render(page, '(->> (seq :sd :_ :_ :_) (delay 0.5) (delay-sync 1/4))');
  const p = peak(win(mono, sr, 0, 0.2));
  const echoStart = firstAbove(win(mono, sr, 0.3, 1.0), p * 0.04) / sr + 0.3;
  const hitStart = firstAbove(mono, p * 0.04) / sr;
  expect(Math.abs(echoStart - hitStart - 0.5)).toBeLessThanOrEqual(0.005);
});
