import { expect, type Page, test } from '@playwright/test';
import { assertFinitePcm, goertzel, mixMono, rms } from './helpers/audio-analysis';

// Phase SONG1 — song form at event level (TEST1 harness, pure) and the
// real-time (export :song) capture against a real AudioContext + WASM worklet.

type SongEvent = { track: string; value: unknown; wholeStart: number; wholeEnd: number };

type CaptureResult = {
  sampleRate: number;
  length: number;
  left: number[];
  right: number[];
  backend: string;
  expectedFrames: number;
  late: boolean;
};

type SongHarness = {
  reset(): boolean;
  songEvents(code: string, fromBar: number, toBar: number, opts?: { muted?: string[] }): SongEvent[];
  audioReady(): Promise<string>;
  captureSong(code: string, opts?: { tail?: number }): Promise<CaptureResult>;
};

const S = `
(track :kick  (seq :bd :_ :bd :_))
(track :snare (seq :_ :sd :_ :sd))
(track :hats  (fast 2 (seq :hh :_)))
(track :bass  (synth :saw (seq :c2 :_ :g2 :_)))
(song (section :intro  4 [:kick :hats])
      (section :verse  8 [:kick :snare :bass])
      (section :chorus 8 :all)
      (section :outro  4 [:hats]))`;

const M = `
(track :kick (seq :bd :_ :bd :_))
(track :hats (fast 2 (seq :hh :_)))
(song (section :a 1 [:kick :hats]) (section :b 1 [:hats]))`;

const M_LOOP = M.replace('(section :b 1 [:hats]))', '(section :b 1 [:hats]) :end :loop)');

const C = `
(bpm 480)
(track :lead (synth :saw (seq :a3 :a3)))
(song (section :a 2 [:lead]))`;

function harness(page: Page) {
  return {
    songEvents: (code: string, from: number, to: number, opts?: { muted?: string[] }) =>
      page.evaluate(
        ({ c, f, t, o }) =>
          (window as unknown as { __REPULSE_TEST__: SongHarness }).__REPULSE_TEST__.songEvents(c, f, t, o),
        { c: code, f: from, t: to, o: opts },
      ),
  };
}

const summary = (evs: SongEvent[]) =>
  evs.map((e) => [e.track, e.value, e.wholeStart, e.wholeEnd]);

test.beforeEach(async ({ page }) => {
  await page.goto('/test-harness.html');
  await expect
    .poll(() => page.evaluate(() => Boolean((window as { __REPULSE_TEST_READY__?: boolean }).__REPULSE_TEST_READY__)))
    .toBe(true);
  await page.evaluate(() => (window as unknown as { __REPULSE_TEST__: SongHarness }).__REPULSE_TEST__.reset());
});

test('song plays sections in order and stops after the last bar', async ({ page }) => {
  const evs = await harness(page).songEvents(M, 0, 3);
  expect(summary(evs)).toEqual([
    [':hats', ':hh', 0, 0.25],
    [':kick', ':bd', 0, 0.25],
    [':hats', ':hh', 0.5, 0.75],
    [':kick', ':bd', 0.5, 0.75],
    [':hats', ':hh', 1, 1.25],
    [':hats', ':hh', 1.5, 1.75],
  ]);
});

test(':end :loop loops the whole song', async ({ page }) => {
  const evs = await harness(page).songEvents(M_LOOP, 0, 3);
  expect(evs).toHaveLength(10);
  const bar0 = summary(evs.filter((e) => e.wholeStart < 1));
  const bar2 = summary(evs.filter((e) => e.wholeStart >= 2))
    .map(([t, v, s, e]) => [t, v, (s as number) - 2, (e as number) - 2]);
  expect(bar2).toEqual(bar0);
});

test('outro plays only hats', async ({ page }) => {
  const evs = await harness(page).songEvents(S, 20, 24);
  expect(evs.some((e) => e.track === ':kick')).toBe(false);
  expect(evs.every((e) => e.track === ':hats' && e.value === ':hh')).toBe(true);
  expect(evs).toHaveLength(8);
});

test('a muted track stays silent in a section that lists it', async ({ page }) => {
  const evs = await harness(page).songEvents(M, 0, 1, { muted: [':kick'] });
  expect(summary(evs)).toEqual([
    [':hats', ':hh', 0, 0.25],
    [':hats', ':hh', 0.5, 0.75],
  ]);
});

test('(export :song) captures one pass of WASM output in real time', async ({ page }) => {
  test.setTimeout(60_000);
  const backend = await page.evaluate(() =>
    (window as unknown as { __REPULSE_TEST__: SongHarness }).__REPULSE_TEST__.audioReady());
  expect(backend).toBe('audioworklet+wasm');

  const result = await page.evaluate(
    (code) =>
      (window as unknown as { __REPULSE_TEST__: SongHarness }).__REPULSE_TEST__.captureSong(code, { tail: 0.5 }),
    C,
  );

  // 2 bars at 480 BPM = 1.0 s, plus a 0.5 s tail
  expect(Math.abs(result.length - Math.round(1.5 * result.sampleRate))).toBeLessThanOrEqual(128);
  expect(result.length).toBe(result.expectedFrames);
  expect(result.left).toHaveLength(result.length);
  expect(result.right).toHaveLength(result.length);
  expect(result.late).toBe(false);
  expect(result.backend).toBe('audioworklet+wasm');
  assertFinitePcm(result.left);
  assertFinitePcm(result.right);

  const mono = mixMono(result.left, result.right);
  expect(rms(mono)).toBeGreaterThan(0.001);

  // A3 saw from the WASM voice: strong harmonics at 440 and 660 Hz (a sine
  // fallback would have ~0 energy there).
  const voiced = mono.slice(0, Math.round(1.0 * result.sampleRate));
  const f220 = goertzel(voiced, result.sampleRate, 220);
  const f440 = goertzel(voiced, result.sampleRate, 440);
  const f660 = goertzel(voiced, result.sampleRate, 660);
  expect(f220).toBeGreaterThan(0);
  expect(f440).toBeGreaterThanOrEqual(0.1 * f220);
  expect(f660).toBeGreaterThanOrEqual(0.1 * f220);
});
