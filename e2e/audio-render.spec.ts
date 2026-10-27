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

// TL1: only event onsets trigger sound. A whole that crosses a cycle boundary is
// returned as two fragments; before TL1 both fragments were played.
test('slow 4 triggers each step once, not once per cycle', async ({ page }) => {
  const result = await render(page, '(slow 4 (seq :bd :sd))', 4);
  const mono = mixMono(result.left, result.right);
  expect(countOnsets(mono, result.sampleRate)).toBe(2);
});

test('late 1/2 of a pure kick triggers once per cycle', async ({ page }) => {
  const result = await render(page, '(late 1/2 (pure :bd))', 2);
  const mono = mixMono(result.left, result.right);
  expect(countOnsets(mono, result.sampleRate)).toBe(2);
});

// TL2: a bare continuous signal has no onsets, so it renders silence without error.
test('bare sine signal renders silence without throwing', async ({ page }) => {
  const result = await render(page, 'sine');
  const mono = mixMono(result.left, result.right);
  assertFinitePcm(mono);
  expect(rms(mono)).toBeLessThan(SILENCE_RMS);
});

test('pan sine2 over eight hats has eight onsets', async ({ page }) => {
  const result = await render(page, '(pan sine2 (fast 8 (seq :hh)))');
  const mono = mixMono(result.left, result.right);
  expect(rms(mono)).toBeGreaterThan(NON_SILENT_RMS);
  expect(countOnsets(mono, result.sampleRate)).toBe(8);
});

// TL3: structure combinators
test('struct "t ~ t t" over a pure kick has three onsets', async ({ page }) => {
  const result = await render(page, '(struct "t ~ t t" (pure :bd))');
  const mono = mixMono(result.left, result.right);
  expect(countOnsets(mono, result.sampleRate)).toBe(3);
});

test('euclidean 3 8 has three onsets', async ({ page }) => {
  const result = await render(page, '(euclidean 3 8 :bd)');
  const mono = mixMono(result.left, result.right);
  expect(countOnsets(mono, result.sampleRate)).toBe(3);
});

test('ply 2 doubles a two-step sequence to four onsets', async ({ page }) => {
  const result = await render(page, '(ply 2 (seq :bd :sd))');
  const mono = mixMono(result.left, result.right);
  expect(countOnsets(mono, result.sampleRate)).toBe(4);
});

// TL3b: zoom, compress & structure extras
test('compress 1/4 3/4 over a two-step sequence has two onsets', async ({ page }) => {
  const result = await render(page, '(compress 1/4 3/4 (seq :bd :sd))');
  const mono = mixMono(result.left, result.right);
  expect(countOnsets(mono, result.sampleRate)).toBe(2);
});

test('hurry 2 over a two-step sequence has four onsets', async ({ page }) => {
  const result = await render(page, '(hurry 2 (seq :bd :sd))');
  const mono = mixMono(result.left, result.right);
  expect(countOnsets(mono, result.sampleRate)).toBe(4);
});

test('stitch "t f t t" over bd/sd has four onsets', async ({ page }) => {
  const result = await render(page, '(stitch "t f t t" (pure :bd) (pure :sd))');
  const mono = mixMono(result.left, result.right);
  expect(countOnsets(mono, result.sampleRate)).toBe(4);
});

// TL4: randomness & conditionals (deterministic, so onset counts are exact)
for (const [name, code, expected] of [
  // :cp is a multi-burst clap that the simple onset detector counts as 2, so :bd :sd :hh :bd is used
  ['shuffle 4 plays all four steps once', '(shuffle 4 (seq :bd :sd :hh :bd))', 4],
  ['mini-notation ? is deterministic: bd*4? has one onset', '(~ "bd*4?")', 1],
  ['mini-notation | picks one alternative per cycle', '(~ "[bd bd] | sd")', 2],
  ['segment 4 (choose ...) samples four onsets', '(segment 4 (choose [:bd :sd]))', 4],
] as [string, string, number][]) {
  test(`TL4 ${name}`, async ({ page }) => {
    const result = await render(page, code);
    const mono = mixMono(result.left, result.right);
    expect(countOnsets(mono, result.sampleRate)).toBe(expected);
  });
}

// TL5: layering, pitch & harmony — note keywords from scale/chord/voicing reach the audio path
for (const [name, code, expected] of [
  // Sine notes need a short decay so consecutive notes are separated by silence
  ['scale :minor :c4 (seq 0 2 4 7) has four onsets', '(decay 0.05 (scale :minor :c4 (seq 0 2 4 7)))', 4],
  ['arp :up (chord "Cm7") has four onsets', '(decay 0.05 (arp :up (chord "Cm7")))', 4],
  // :bd instead of :cp (a multi-burst clap the detector counts as 2)
  ['echo 3 1/8 0.6 (seq :bd) has three onsets', '(echo 3 1/8 0.6 (seq :bd))', 3],
  // the 4 simultaneous tones of each voiced chord are one onset to the detector (12 events, 3 onsets)
  ['voicing of Dm7 G7 C^7 has three chord onsets', '(decay 0.05 (voicing (seq "Dm7" "G7" "C^7")))', 3],
] as [string, string, number][]) {
  test(`TL5 ${name}`, async ({ page }) => {
    const result = await render(page, code);
    const mono = mixMono(result.left, result.right);
    expect(countOnsets(mono, result.sampleRate)).toBe(expected);
  });
}
