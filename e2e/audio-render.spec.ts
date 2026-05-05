import { expect, type Page, test } from '@playwright/test';
import {
  assertFinitePcm,
  countOnsets,
  mixMono,
  rms,
  rmsStereo,
  tailRms,
} from './helpers/audio-analysis';

type RenderResult = {
  sampleRate: number;
  channels: number;
  length: number;
  duration: number;
  backend: string;
  left: number[];
  right: number[];
};

declare global {
  interface Window {
    __REPULSE_TEST_READY__?: boolean;
    __REPULSE_TEST__?: {
      backend(): string;
      reset(): boolean;
      renderOffline(code: string, cycles?: number, options?: unknown): Promise<RenderResult>;
    };
  }
}

const NON_SILENT_RMS = 0.001;
const SILENCE_RMS = 0.0002;

async function render(page: Page, code: string, cycles = 1): Promise<RenderResult> {
  return test.step(`render ${code}`, async () =>
    page.evaluate(
      async ({ src, n }) => window.__REPULSE_TEST__!.renderOffline(src, n),
      { src: code, n: cycles },
    ));
}

test.beforeEach(async ({ page }) => {
  await page.goto('/test-harness.html');
  await expect
    .poll(() => page.evaluate(() => Boolean(window.__REPULSE_TEST_READY__)))
    .toBe(true);
  await page.evaluate(() => window.__REPULSE_TEST__!.reset());
});

test('harness reports offline-js backend', async ({ page }) => {
  const backend = await page.evaluate(() => window.__REPULSE_TEST__!.backend());
  expect(backend).toBe('offline-js');
});

test('pure kick renders non-silent finite audio', async ({ page }) => {
  const result = await render(page, '(pure :bd)');
  const mono = mixMono(result.left, result.right);
  assertFinitePcm(result.left);
  assertFinitePcm(result.right);
  expect(result.backend).toBe('offline-js');
  expect(rms(mono)).toBeGreaterThan(NON_SILENT_RMS);
});

test('basic sequence has four onsets', async ({ page }) => {
  const result = await render(page, '(seq :bd :sd :hh :sd)');
  const mono = mixMono(result.left, result.right);
  expect(countOnsets(mono, result.sampleRate)).toBe(4);
});

test('fast doubles a two-step sequence to four onsets', async ({ page }) => {
  const result = await render(page, '(fast 2 (seq :bd :sd))');
  const mono = mixMono(result.left, result.right);
  expect(countOnsets(mono, result.sampleRate)).toBe(4);
});

test('amp lowers rendered RMS', async ({ page }) => {
  const full = await render(page, '(pure :c4)');
  const half = await render(page, '(amp 0.5 (pure :c4))');
  expect(rms(mixMono(half.left, half.right))).toBeLessThan(
    rms(mixMono(full.left, full.right)) * 0.75,
  );
});

test('right pan is right-heavy', async ({ page }) => {
  const result = await render(page, '(pan 1.0 (pure :c4))');
  const stereo = rmsStereo(result.left, result.right);
  expect(stereo.right).toBeGreaterThan(stereo.left * 5);
});

test('left pan is left-heavy', async ({ page }) => {
  const result = await render(page, '(pan -1.0 (pure :c4))');
  const stereo = rmsStereo(result.left, result.right);
  expect(stereo.left).toBeGreaterThan(stereo.right * 5);
});

test('stack renders mixed non-silent audio', async ({ page }) => {
  const result = await render(page, '(stack (pure :bd) (pure :c4))');
  expect(rms(mixMono(result.left, result.right))).toBeGreaterThan(NON_SILENT_RMS);
});

test('short decay returns to silence', async ({ page }) => {
  const result = await render(page, '(decay 0.1 (pure :c4))');
  const mono = mixMono(result.left, result.right);
  expect(tailRms(mono, result.sampleRate, 0.25)).toBeLessThan(SILENCE_RMS);
});

test('rests render silence', async ({ page }) => {
  const result = await render(page, '(seq :_ :_ :_ :_)');
  const mono = mixMono(result.left, result.right);
  assertFinitePcm(mono);
  expect(rms(mono)).toBeLessThan(SILENCE_RMS);
});
