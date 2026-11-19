import { expect, type Page, test } from '@playwright/test';
import { assertFinitePcm, mixMono, rms } from './helpers/audio-analysis';

// Phase TL7b — reverb & delay sends per orbit, checked on the offline-js harness
// (JS voices → per-event send GainNodes → orbit ConvolverNode / DelayNode).
// Energy and onset assertions only, no claims about how it sounds.

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
      renderOffline(code: string, cycles?: number, options?: unknown): Promise<RenderResult>;
    };
    __TL7B_NODES__?: { convolver: number; delay: number };
  }
}

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

const db = (a: number, b: number) => 20 * Math.log10(Math.max(a, 1e-12) / Math.max(b, 1e-12));

const peak = (buf: number[]) => buf.reduce((m, x) => Math.max(m, Math.abs(x)), 0);

/** Onset times (s): first sample ≥ threshold after ≥ minGap below it. */
function onsetTimes(mono: number[], sr: number, threshold: number, minGap = 0.15): number[] {
  const gap = Math.floor(minGap * sr);
  const out: number[] = [];
  let last = -gap;
  for (let i = 0; i < mono.length; i += 1) {
    if (Math.abs(mono[i]) >= threshold) {
      if (i - last >= gap) out.push(i / sr);
      last = i;
    }
  }
  return out;
}

test.beforeEach(async ({ page }) => {
  await page.goto('/test-harness.html');
  await expect
    .poll(() => page.evaluate(() => Boolean(window.__REPULSE_TEST_READY__)))
    .toBe(true);
  await page.evaluate(() => window.__REPULSE_TEST__!.reset());
});

test('room 0.8 leaves a reverb tail ≥ 20 dB above room 0; the dry onset is unchanged', async ({ page }) => {
  const code = (r: number) => `(->> (seq :c4 :_ :_ :_) (room ${r}))`;
  const dry = await render(page, code(0));
  const wet = await render(page, code(0.8));
  const tail = (m: number[], sr: number) => rms(slice(m, sr, 1.5, 2.0));
  const head = (m: number[], sr: number) => rms(slice(m, sr, 0, 0.05));
  expect(db(tail(wet.mono, wet.sr), tail(dry.mono, dry.sr))).toBeGreaterThanOrEqual(20);
  expect(Math.abs(db(head(wet.mono, wet.sr), head(dry.mono, dry.sr)))).toBeLessThan(1);
});

test('size 1 keeps the tail ≥ 10 dB louder than size 0.1, 1.5 s after the onset', async ({ page }) => {
  const code = (s: number) => `(->> (seq :c4 :_ :_ :_) (room 0.8) (size ${s}))`;
  const small = await render(page, code(0.1));
  const large = await render(page, code(1));
  const at = (m: number[], sr: number) => rms(slice(m, sr, 1.5, 1.7));
  expect(db(at(large.mono, large.sr), at(small.mono, small.sr))).toBeGreaterThanOrEqual(10);
});

test('delay 0.5 / 0.25 s / feedback 0.5: the hit plus ≥ 3 echoes 0.25 s apart, each quieter', async ({ page }) => {
  const { sr, mono } = await render(
    page,
    '(->> (seq :sd :_ :_ :_) (delay 0.5) (delay-time 0.25) (delay-feedback 0.5))',
  );
  const p = peak(slice(mono, sr, 0, 0.2));
  const times = onsetTimes(mono, sr, p * 0.04);
  expect(times.length).toBeGreaterThanOrEqual(4);
  for (let k = 1; k < 4; k += 1) {
    expect(Math.abs(times[k] - times[0] - 0.25 * k)).toBeLessThanOrEqual(0.01);
  }
  const peaks = times.slice(0, 4).map((t) => peak(slice(mono, sr, t, t + 0.1)));
  for (let k = 1; k < 4; k += 1) expect(peaks[k]).toBeLessThan(peaks[k - 1]);
});

test('delay 0 gives exactly one onset and creates no orbit nodes', async ({ page }) => {
  await page.evaluate(() => {
    const counts = { convolver: 0, delay: 0 };
    window.__TL7B_NODES__ = counts;
    const proto = BaseAudioContext.prototype;
    const conv = proto.createConvolver;
    const dl = proto.createDelay;
    proto.createConvolver = function patched(this: BaseAudioContext) {
      counts.convolver += 1;
      return conv.call(this);
    };
    proto.createDelay = function patched(this: BaseAudioContext, max?: number) {
      counts.delay += 1;
      return dl.call(this, max);
    };
  });
  const nodes = () => page.evaluate(() => ({ ...window.__TL7B_NODES__! }));
  const { sr, mono } = await render(page, '(->> (seq :sd :_ :_ :_) (delay 0) (room 0))');
  const p = peak(slice(mono, sr, 0, 0.2));
  expect(onsetTimes(mono, sr, p * 0.04)).toHaveLength(1);
  expect(await nodes()).toEqual({ convolver: 0, delay: 0 });
  // One orbit with sends → exactly one bus (one convolver + one delay).
  await render(page, '(->> (seq :sd :sd) (delay 0.3) (room 0.3) (orbit 2))');
  expect(await nodes()).toEqual({ convolver: 1, delay: 1 });
});

test('orbits are independent: the size-1 orbit rings ≥ 10 dB longer than the size-0.1 orbit', async ({ page }) => {
  // Hits at 0 s (orbit 0, size 0.1) and 1 s (orbit 1, size 1); 2 cycles = 4 s.
  const { sr, mono } = await render(
    page,
    `(stack (->> (seq :bd :_) (room 0.8) (size 0.1) (orbit 0))
            (->> (late 1/2 (seq :bd :_)) (room 0.8) (size 1) (orbit 1)))`,
    2,
  );
  // Each hit's tail 1 s after it, in the first cycle (before the next hits at 2 s).
  const first = rms(slice(mono, sr, 0.9, 1.0));
  const second = rms(slice(mono, sr, 1.9, 2.0));
  expect(db(second, first)).toBeGreaterThanOrEqual(10);
});

test('patterned room: a tail only after the second note', async ({ page }) => {
  // Short notes (decay 0.1) so the dry signal is gone after ~0.1 s.
  const { sr, mono } = await render(page, '(->> (seq :c4 :c4) (decay 0.1) (room (seq 0 0.8)))');
  const afterFirst = rms(slice(mono, sr, 0.5, 0.95));
  const afterSecond = rms(slice(mono, sr, 1.5, 1.95));
  expect(db(afterSecond, afterFirst)).toBeGreaterThanOrEqual(20);
});
