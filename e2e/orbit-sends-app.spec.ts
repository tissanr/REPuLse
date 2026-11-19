import { expect, type Page, test } from '@playwright/test';
import { exportBuffer, loadApp, monoOf } from './helpers/app-export';
import { rms } from './helpers/audio-analysis';

// Phase TL7b — orbit sends on the WASM worklet path of the real app. Events on a
// track without track-fx go to the AudioWorklet, which writes per-orbit send
// buses (outputs 1–8) that feed the orbit ConvolverNode / DelayNode. Checked in
// the offline export (EXP1: same worklet.js, same wiring) and in real time.

declare global {
  interface Window {
    __TL7B_TAPS__?: { conv: ConvolverNode; tap: AnalyserNode }[];
  }
}

const db = (a: number, b: number) => 20 * Math.log10(Math.max(a, 1e-12) / Math.max(b, 1e-12));

function seg(m: number[], sr: number, from: number, to: number): number[] {
  return m.slice(Math.round(from * sr), Math.round(to * sr));
}

test('export: an :epiano note with room 0.6 leaves a reverb tail (WASM send buses)', async ({ page }) => {
  await loadApp(page);
  // bpm 60 → one cycle = 4 s; the e-piano rings out within ~1 s.
  const code = (r: number) =>
    `(bpm 60)\n(track :keys (->> (seq :c4 :_ :_ :_) (synth :epiano) (room ${r}) (size 1)))\n(export 1)`;
  const dry = await exportBuffer(page, code(0));
  const wet = await exportBuffer(page, code(0.6));
  const sr = wet.sampleRate;
  const tail = (w: typeof wet) => rms(seg(monoOf(w), sr, 2.0, 3.5));
  const head = (w: typeof wet) => rms(seg(monoOf(w), sr, 0, 0.05));
  expect(db(tail(wet), tail(dry))).toBeGreaterThanOrEqual(20);
  expect(Math.abs(db(head(wet), head(dry)))).toBeLessThan(1);
});

async function tapConvolvers(page: Page) {
  // Tap every ConvolverNode with an AnalyserNode. Effect plugins create their own
  // convolvers; the orbit reverbs are the ones with the default 3.15 s IR
  // (size 0.5), created in orbit order when the worklet loads.
  await page.addInitScript(() => {
    const taps: { conv: ConvolverNode; tap: AnalyserNode }[] = [];
    window.__TL7B_TAPS__ = taps;
    const proto = BaseAudioContext.prototype;
    const create = proto.createConvolver;
    proto.createConvolver = function patched(this: BaseAudioContext) {
      const conv = create.call(this);
      const a = this.createAnalyser();
      a.fftSize = 2048;
      conv.connect(a);
      taps.push({ conv, tap: a });
      return conv;
    };
  });
}

/** Max RMS of each orbit's reverb output over `ms` milliseconds. */
async function orbitLevels(page: Page, ms: number): Promise<number[]> {
  return page.evaluate(async (dur) => {
    const taps = window
      .__TL7B_TAPS__!.filter((t) => Math.abs((t.conv.buffer?.duration ?? 0) - 3.15) < 0.01)
      .map((t) => t.tap);
    const best = taps.map(() => 0);
    const buf = new Float32Array(2048);
    const end = performance.now() + dur;
    while (performance.now() < end) {
      taps.forEach((a, i) => {
        a.getFloatTimeDomainData(buf);
        let s = 0;
        for (const x of buf) s += x * x;
        best[i] = Math.max(best[i], Math.sqrt(s / buf.length));
      });
      await new Promise((r) => setTimeout(r, 20));
    }
    return best;
  }, ms);
}

test('live: room 0.6 on orbit 1 reaches only orbit 1\'s reverb', async ({ page }) => {
  await tapConvolvers(page);
  await loadApp(page);
  await page.evaluate(() => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({
      changes: {
        from: 0,
        to: v.state.doc.length,
        insert: '(->> (seq :c4 :e4 :g4 :c5) (synth :epiano) (room 0.6) (orbit 1))',
      },
    });
    v.focus();
  });
  await page.keyboard.press('Alt+Enter');
  const levels = await orbitLevels(page, 2500);
  await page.evaluate(() => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: '(stop)' } });
    v.focus();
  });
  await page.keyboard.press('Alt+Enter');
  console.log('orbit reverb levels', levels);
  expect(levels).toHaveLength(4);
  expect(levels[1]).toBeGreaterThan(1e-3);
  for (const i of [0, 2, 3]) expect(levels[i]).toBe(0);
});

async function evalCode(page: Page, code: string) {
  await page.evaluate((t) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t } });
    v.focus();
  }, code);
  await page.keyboard.press('Alt+Enter');
}

test('orbit 7 plays on orbit 3 and warns once per evaluation', async ({ page }) => {
  await tapConvolvers(page);
  await loadApp(page);
  await evalCode(page, '(->> (seq :c4 :e4) (synth :epiano) (room 0.6) (orbit (seq 7 7)))');
  await expect(page.locator('#output')).toHaveText(
    'playing pattern — Alt+Enter to re-evaluate, (stop) to stop — Warning: orbit 7 is out of range 0–3; using 3',
  );
  const levels = await orbitLevels(page, 2000);
  await evalCode(page, '(stop)');
  expect(levels[3]).toBeGreaterThan(1e-3);
  for (const i of [0, 1, 2]) expect(levels[i]).toBe(0);
});

test('(stop) resets orbit settings to their defaults', async ({ page }) => {
  // Keep the orbit DelayNodes (maxDelayTime 4 s) to read their parameters.
  await page.addInitScript(() => {
    const found: DelayNode[] = [];
    (window as any).__TL7B_DELAYS__ = found;
    const proto = BaseAudioContext.prototype;
    const create = proto.createDelay;
    proto.createDelay = function patched(this: BaseAudioContext, max?: number) {
      const d = create.call(this, max);
      if (max === 4) found.push(d);
      return d;
    };
  });
  await loadApp(page);
  const delayTime = () =>
    page.evaluate(() => (window as any).__TL7B_DELAYS__.map((d: DelayNode) => d.delayTime.value));
  await evalCode(page, '(->> (seq :c4 :c4) (delay 0.3) (delay-time 0.5) (delay-feedback 0.7))');
  await expect.poll(async () => (await delayTime())[0], { timeout: 5000 }).toBeCloseTo(0.5, 5);
  await evalCode(page, '(stop)');
  await expect.poll(delayTime, { timeout: 5000 }).toEqual([0.25, 0.25, 0.25, 0.25]);
});
