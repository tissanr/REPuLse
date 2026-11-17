import { expect, type Page, test } from '@playwright/test';
import { assertFinitePcm, mixMono, rms } from './helpers/audio-analysis';

// Phase TL6 — sample slicing & playback control, checked on the offline-js
// harness with synthetic, network-free sample buffers (test_api.cljs):
//   tl6-steps — 1 s; slice k (k = 0…3) = 0.1 s of 440 Hz at amplitude 0.2·(k+1),
//               then 0.15 s of silence.
//   tl6-tone  — 1 s of 440 Hz at amplitude 0.5.
// A centred StereoPanner scales a mono source by cos(π/4), so the mono mix of a
// sine of amplitude A has RMS ≈ A · 0.707 / √2 = A / 2.

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

const SILENT = 0.002;
const ONSET_THRESHOLD = 0.01;

async function render(page: Page, code: string, cycles = 1, cycleDur = 2.0) {
  const r = await test.step(`render ${code}`, async () =>
    page.evaluate(
      async ({ src, n, cd }) => window.__REPULSE_TEST__!.renderOffline(src, n, { cycleDur: cd }),
      { src: code, n: cycles, cd: cycleDur },
    ));
  assertFinitePcm(r.left);
  assertFinitePcm(r.right);
  return { sr: r.sampleRate, mono: mixMono(r.left, r.right) };
}

function windowRms(mono: number[], sr: number, from: number, to: number): number {
  return rms(mono.slice(Math.round(from * sr), Math.round(to * sr)));
}

/** First time ≥ (t − 2 ms) where the signal crosses the onset threshold. */
function onsetNear(mono: number[], sr: number, t: number): number {
  for (let i = Math.max(0, Math.round((t - 0.002) * sr)); i < mono.length; i += 1) {
    if (Math.abs(mono[i]) >= ONSET_THRESHOLD) return i / sr;
  }
  return Number.POSITIVE_INFINITY;
}

function expectOnsets(mono: number[], sr: number, times: number[]) {
  for (const t of times) {
    expect(Math.abs(onsetNear(mono, sr, t) - t), `onset at ${t}s`).toBeLessThan(0.001);
  }
}

test.beforeEach(async ({ page }) => {
  await page.goto('/test-harness.html');
  await expect
    .poll(() => page.evaluate(() => Boolean(window.__REPULSE_TEST_READY__)))
    .toBe(true);
  await page.evaluate(() => {
    window.__REPULSE_TEST__!.reset();
    window.__REPULSE_TEST__!.registerTestSample('tl6-steps', 'steps');
    window.__REPULSE_TEST__!.registerTestSample('tl6-tone', 'tone');
  });
});

test('chop 4 spreads the slices over the cycle with exact onsets', async ({ page }) => {
  const { sr, mono } = await render(page, '(chop 4 (sound :tl6-steps 0))');
  // 2 s cycle → slices at 0, 0.5, 1.0, 1.5 s (unchopped they would be 0.25 s apart)
  expectOnsets(mono, sr, [0, 0.5, 1.0, 1.5]);
  expect(windowRms(mono, sr, 0.15, 0.48)).toBeLessThan(SILENT);
  const levels = [0, 0.5, 1.0, 1.5].map((t) => windowRms(mono, sr, t + 0.01, t + 0.09));
  [0.2, 0.4, 0.6, 0.8].forEach((amp, i) => expect(levels[i]).toBeCloseTo(amp / 2, 1));
});

test('slice plays the slices in index order', async ({ page }) => {
  const { sr, mono } = await render(page, '(slice 4 (seq 3 0 2 1) (sound :tl6-steps 0))');
  const levels = [0, 0.5, 1.0, 1.5].map((t) => windowRms(mono, sr, t + 0.01, t + 0.09));
  [0.8, 0.2, 0.6, 0.4].forEach((amp, i) => expect(levels[i]).toBeCloseTo(amp / 2, 1));
});

test('splice fits each slice to its slot (half speed in a half-cycle slot)', async ({ page }) => {
  // 1 s cycle, 1 s sample: a 1/4 slice in a 1/2-cycle slot plays at rate 0.5,
  // so the 0.1 s burst lasts 0.2 s
  const { sr, mono } = await render(page, '(splice 4 (seq 0 1) (sound :tl6-steps 0))', 1, 1.0);
  expectOnsets(mono, sr, [0, 0.5]);
  expect(windowRms(mono, sr, 0.12, 0.18)).toBeGreaterThan(0.05);
  expect(windowRms(mono, sr, 0.22, 0.48)).toBeLessThan(SILENT);
});

test('loop-at 2 fits a chopped sample to two short cycles', async ({ page }) => {
  // 0.25 s cycles: the 1 s sample must fill 0.5 s → rate 2
  const { sr, mono } = await render(page, '(loop-at 2 (chop 4 (sound :tl6-steps 0)))', 2, 0.25);
  expectOnsets(mono, sr, [0, 0.125, 0.25, 0.375]);
  expect(windowRms(mono, sr, 0.06, 0.12)).toBeLessThan(SILENT);
});

test('speed -1 plays the sample backwards', async ({ page }) => {
  const { sr, mono } = await render(page, '(->> (sound :tl6-steps 0) (speed -1))');
  // reversed: the loudest slice comes first, its burst at 0.15–0.25 s
  expect(windowRms(mono, sr, 0.02, 0.13)).toBeLessThan(SILENT);
  expect(windowRms(mono, sr, 0.16, 0.24)).toBeCloseTo(0.4, 1);
  expect(windowRms(mono, sr, 0.91, 0.99)).toBeCloseTo(0.1, 1);
});

test('legato 0.5 stops the sample halfway through its event', async ({ page }) => {
  const plain = await render(page, '(seq (sound :tl6-tone 0) :_)');
  expect(windowRms(plain.mono, plain.sr, 0.55, 0.95)).toBeCloseTo(0.25, 1);

  const { sr, mono } = await render(page, '(->> (seq (sound :tl6-tone 0) :_) (legato 0.5))');
  expect(windowRms(mono, sr, 0.1, 0.4)).toBeCloseTo(0.25, 1);
  expect(windowRms(mono, sr, 0.51, 0.95)).toBeLessThan(SILENT);
});

test('cut 1 truncates the earlier sample when the next one starts', async ({ page }) => {
  // tone at 0 s (1 s long), steps at 0.25 s; 0.37–0.48 s is a gap in the steps sample
  const code = '(seq (sound :tl6-tone 0) (sound :tl6-steps 0) :_ :_ :_ :_ :_ :_)';
  const plain = await render(page, code);
  expect(windowRms(plain.mono, plain.sr, 0.37, 0.48)).toBeCloseTo(0.25, 1);

  const { sr, mono } = await render(page, `(cut 1 ${code})`);
  expect(windowRms(mono, sr, 0.05, 0.24)).toBeCloseTo(0.25, 1);
  expect(windowRms(mono, sr, 0.37, 0.48)).toBeLessThan(SILENT);
  expectOnsets(mono, sr, [0.5]);
});
