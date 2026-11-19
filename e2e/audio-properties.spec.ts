import { expect, type Page, test } from '@playwright/test';
import {
  estimatePitch,
  mixMono,
  rms,
  spectralCentroid,
  tailRms,
} from './helpers/audio-analysis';
import { exportBuffer, loadApp, monoOf } from './helpers/app-export';

// Phase TEST3 — measured sound properties ("duller", "quieter", "higher", "has a
// reverb tail") instead of listening. Nothing here claims tonal quality: every
// assertion is a ratio of two renders of the same code that differ in one thing.
//
// M6 (a global reverb changes the filtered track's centroid by < 5 %) is NOT
// automated: over 20 local runs the measured difference spread from 0.4 % to 28 %
// (drums) and 0.2 % to 4.6 % (saw bass) between identical renders, so no 5 %
// threshold with a 2× margin exists. It stays manual (docs/TESTPLAN.md).
//
// M2–M4 use the offline-js harness (`/test-harness.html`, pattern + per-event
// params, no track FX). M1 and M5 need track and master FX, which only the
// real app has: they read the WAV of `(export N)` (offline render with the real
// WASM voices and FX plugins, same path as export-parity.spec.ts).
// Thresholds are set from the measured values below with ≥ 2× safety margin.

type RenderResult = { sampleRate: number; left: number[]; right: number[] };

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

async function harness(page: Page) {
  await page.goto('/test-harness.html');
  await expect.poll(() => page.evaluate(() => Boolean(window.__REPULSE_TEST_READY__))).toBe(true);
  await page.evaluate(() => {
    window.__REPULSE_TEST__!.reset();
    window.__REPULSE_TEST__!.registerTestSample('tp-tone', 'tone'); // 1 s of 440 Hz
  });
}

async function render(page: Page, code: string, cycles = 1, cycleDur = 2.0) {
  const r = await test.step(`render ${code}`, async () =>
    page.evaluate(
      async ({ src, n, cd }) => window.__REPULSE_TEST__!.renderOffline(src, n, { cycleDur: cd }),
      { src: code, n: cycles, cd: cycleDur },
    ));
  return { sr: r.sampleRate, mono: mixMono(r.left, r.right) };
}

const seg = (x: number[], sr: number, from: number, to: number) =>
  x.slice(Math.round(from * sr), Math.round(to * sr));

test.describe('helpers', () => {
  const SR = 44100;
  const sine = (hz: number, secs: number) =>
    Array.from({ length: Math.round(secs * SR) }, (_, i) => 0.5 * Math.sin((2 * Math.PI * hz * i) / SR));

  test('spectralCentroid: a sine sits at its frequency, higher sine = higher centroid', () => {
    const c1 = spectralCentroid(sine(1000, 0.5), SR);
    const c2 = spectralCentroid(sine(4000, 0.5), SR);
    expect(Math.abs(c1 - 1000)).toBeLessThan(50);
    expect(Math.abs(c2 - 4000)).toBeLessThan(100);
  });

  test('spectralCentroid: white noise is near Nyquist/2, silence gives 0', () => {
    let s = 12345;
    const noise = Array.from({ length: SR }, () => {
      s = (s * 16807) % 2147483647;
      return 0.5 * ((2 * s) / 2147483647 - 1);
    });
    const c = spectralCentroid(noise, SR);
    expect(c).toBeGreaterThan(9000);
    expect(c).toBeLessThan(13000);
    expect(spectralCentroid(new Array(SR).fill(0), SR)).toBe(0);
    // a −60 dBFS sine is below the −40 dBFS gate
    expect(spectralCentroid(sine(1000, 0.5).map((v) => v * 0.002), SR)).toBe(0);
  });

  test('estimatePitch: finds 110, 220 and 440 Hz sines within 1 %', () => {
    for (const hz of [110, 220, 440, 880]) {
      const p = estimatePitch(sine(hz, 0.5), SR, 60, 1200);
      expect(Math.abs(p - hz) / hz, `${hz} Hz`).toBeLessThan(0.01);
    }
    // a sine with a strong 2nd harmonic is still reported at its fundamental
    const rich = sine(220, 0.5).map((v, i) => v + 0.4 * Math.sin((2 * Math.PI * 440 * i) / SR));
    expect(Math.abs(estimatePitch(rich, SR, 60, 1200) - 220) / 220).toBeLessThan(0.01);
    expect(estimatePitch(new Array(SR).fill(0), SR, 60, 1200)).toBe(0);
  });
});

test('M1: a track filter at 800 Hz has under half the spectral centroid', async ({ page }) => {
  test.setTimeout(90_000);
  await loadApp(page);
  const prog = (fx: string) =>
    `(bpm 120)\n(track :drums (->> (seq :bd :sd :bd :sd)${fx}))\n(export 1)`;
  const dry = monoOf(await exportBuffer(page, prog('')));
  const wet = monoOf(await exportBuffer(page, prog(' (fx :filter :freq 800)')));
  const sr = (await page.evaluate(() => new AudioContext().sampleRate)) as number;
  const cDry = spectralCentroid(dry, sr);
  const cWet = spectralCentroid(wet, sr);
  console.log('M1 centroid dry', cDry, 'filtered', cWet, 'ratio', cWet / cDry);
  expect(cDry).toBeGreaterThan(0);
  expect(cWet).toBeGreaterThan(0);
  // measured over 20 runs: dry 806 Hz, filtered 232–250 Hz, ratio 0.29–0.31. The 0.5 limit
  // is fixed by the phase spec (1.6× margin, not 2×); the spread of the ratio is ±4 %.
  expect(cWet).toBeLessThan(0.5 * cDry);
});

test('M2: amp scales the level; (slider …) renders like its literal', async ({ page }) => {
  await harness(page);
  const level = async (code: string) => rms((await render(page, code)).mono);
  const full = await level('(amp 1 (seq :c4))');
  const quiet = await level('(amp 0.1 (seq :c4))');
  const lit = await level('(amp 0.7 (seq :c4))');
  const sl = await level('(amp (slider 0.7 0 1) (seq :c4))');
  console.log('M2 rms full', full, 'amp 0.1', quiet, 'ratio', quiet / full, 'literal', lit, 'slider', sl);
  expect(full).toBeGreaterThan(0.001);
  // measured: amp 0.1 / amp 1 = 0.117 (limit 0.25); slider and literal are bit-identical (limit 5 %)
  expect(quiet).toBeLessThan(0.25 * full);
  expect(Math.abs(sl - lit) / lit).toBeLessThan(0.05);
});

test('M3: (rate 1.5) raises a sample by a factor of 1.5', async ({ page }) => {
  await harness(page);
  const pitch = async (code: string) => {
    const { sr, mono } = await render(page, code, 1, 2.0);
    return estimatePitch(seg(mono, sr, 0.05, 0.45), sr, 200, 1200);
  };
  const plain = await pitch('(pure (sound :tp-tone 0))');
  const fast = await pitch('(->> (pure (sound :tp-tone 0)) (rate 1.5))');
  console.log('M3 pitch plain', plain, 'rate 1.5', fast, 'ratio', fast / plain);
  expect(Math.abs(plain - 440) / 440).toBeLessThan(0.05);
  // measured: 439.9 Hz and 659.9 Hz, ratio 1.5001 (limit ±5 %)
  expect(Math.abs(fast / plain - 1.5) / 1.5).toBeLessThan(0.05);
});

test('M4: (accelerate -0.5) lowers the pitch over the event', async ({ page }) => {
  await harness(page);
  // 1 s cycle: the 1 s sample fills the whole event
  const { sr, mono } = await render(
    page,
    '(->> (pure (sound :tp-tone 0)) (accelerate -0.5))',
    1,
    1.0,
  );
  const first = estimatePitch(seg(mono, sr, 0.0, 0.25), sr, 150, 1200);
  const last = estimatePitch(seg(mono, sr, 0.5, 0.75), sr, 150, 1200);
  console.log('M4 pitch first quarter', first, 'third-fourth', last);
  expect(first).toBeGreaterThan(0);
  expect(last).toBeGreaterThan(0);
  // measured: 414 Hz in the first quarter, 304 Hz in the third (a 27 % drop; the limit is just "lower")
  expect(last).toBeLessThan(first);
});

const PAD = (fx: string) =>
  `(bpm 240)\n(track :pad (->> (seq :c4 :_ :_ :_) (synth :saw) (decay 0.1)))${fx}\n(export 4)`;

test('M5: (master (fx :reverb 0.4)) leaves a tail after the last note', async ({ page }) => {
  test.setTimeout(90_000);
  await loadApp(page);
  const dry = await exportBuffer(page, PAD(''));
  const wet = await exportBuffer(page, PAD('\n(master (fx :reverb 0.4))'));
  const sr = wet.sampleRate;
  // last note at 3.0 s; the final 0.6 s of the 4 s export are after its decay
  const tailDry = tailRms(monoOf(dry), sr, 0.6);
  const tailWet = tailRms(monoOf(wet), sr, 0.6);
  console.log('M5 tail dry', tailDry, 'wet', tailWet, 'ratio', tailWet / Math.max(tailDry, 1e-9));
  // measured: dry 0, wet ≈ 1.7e-3 → thresholds 2e-4 (8× margin) and 10× over a 1e-6 floor
  expect(tailWet).toBeGreaterThan(2e-4);
  expect(tailWet).toBeGreaterThan(10 * Math.max(tailDry, 1e-6));
});

// The same property on the real-time capture of the live master output
// ((export :song … :realtime), SONG1): one bar, then a 1 s tail.
const SONG_PAD = (fx: string) =>
  `(bpm 240)\n(track :pad (->> (seq :c4 :_ :_ :_) (synth :saw) (decay 0.1)))\n(song (section :a 1 :all))${fx}\n(export :song :tail 1 :realtime)`;

test('M5: the real-time capture shows the same master-reverb tail', async ({ page }) => {
  test.setTimeout(90_000);
  await loadApp(page);
  const dry = await exportBuffer(page, SONG_PAD(''), 30_000);
  const wet = await exportBuffer(page, SONG_PAD('\n(master (fx :reverb 0.4))'), 30_000);
  const sr = wet.sampleRate;
  // the note (0.1 s decay) is long gone by 0.3 s; the bar ends at 1.0 s
  const tailDry = rms(seg(monoOf(dry), sr, 0.3, 1.0));
  const tailWet = rms(seg(monoOf(wet), sr, 0.3, 1.0));
  console.log('M5 realtime tail dry', tailDry, 'wet', tailWet);
  expect(tailWet).toBeGreaterThan(2e-4);
  expect(tailWet).toBeGreaterThan(10 * Math.max(tailDry, 1e-6));
});
