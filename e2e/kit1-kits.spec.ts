import { expect, type Page, test } from '@playwright/test';
import { assertFinitePcm, goertzel, mixMono, rms } from './helpers/audio-analysis';

// Phase KIT1 — layered kit banks on the offline-js harness.
// Fixture kit FixKit_* with generated WAVs (0.2 s sines, route-mocked, no network):
//   FixKit_bd   layers v 0.3 / 0.6 / 1;  layer L (1…3) take T (1…2) = 200·L + 20·T Hz
//   FixKit_oh   one layer, 2 s of 1000 Hz, choke "hat"
//   FixKit_hh   one layer, 0.5 s of 3000 Hz, choke "hat"
// The real JazzKit / StudioKit are served from app/public/kits/.

type RenderResult = { sampleRate: number; left: number[]; right: number[] };

declare global {
  interface Window {
    __REPULSE_TEST_READY__?: boolean;
    __REPULSE_TEST__?: {
      reset(): boolean;
      loadManifest(url: string): Promise<unknown>;
      setBankPrefix(p: string | null): boolean;
      registerTestSample(bank: string, kind: string): string;
      renderOffline(code: string, cycles?: number, options?: unknown): Promise<RenderResult>;
    };
  }
}

const SR = 44100;
const CYCLE = 2.0;
const FIX = 'https://fixkit.test/';

function wav(hz: number, secs: number, amp = 0.5): Buffer {
  const n = Math.round(SR * secs);
  const b = Buffer.alloc(44 + n * 2);
  b.write('RIFF', 0); b.writeUInt32LE(36 + n * 2, 4); b.write('WAVEfmt ', 8);
  b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22);
  b.writeUInt32LE(SR, 24); b.writeUInt32LE(SR * 2, 28); b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34);
  b.write('data', 36); b.writeUInt32LE(n * 2, 40);
  for (let i = 0; i < n; i += 1) {
    b.writeInt16LE(Math.round(amp * 32767 * Math.sin((2 * Math.PI * hz * i) / SR)), 44 + i * 2);
  }
  return b;
}

const manifest = {
  _format: 'repulse-kit/1',
  _base: FIX,
  FixKit_bd: {
    layers: [1, 2, 3].map((L) => ({
      v: [0.3, 0.6, 1][L - 1],
      gain: 1,
      files: [1, 2].map((T) => `bd-${L}-${T}.wav`),
    })),
  },
  FixKit_oh: { choke: 'hat', layers: [{ v: 1, files: ['oh.wav'] }] },
  FixKit_hh: { choke: 'hat', layers: [{ v: 1, files: ['hh.wav'] }] },
};

async function setup(page: Page) {
  await page.route(`${FIX}**`, async (route) => {
    const f = route.request().url().slice(FIX.length);
    const m = /^bd-(\d)-(\d)\.wav$/.exec(f);
    if (f === 'manifest.json') {
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify(manifest), headers: { 'access-control-allow-origin': '*' } });
    }
    const hz = m ? 200 * +m[1] + 20 * +m[2] : f === 'oh.wav' ? 1000 : 3000;
    const secs = f === 'oh.wav' ? 2 : f === 'hh.wav' ? 0.5 : 0.2;
    return route.fulfill({ contentType: 'audio/wav', body: wav(hz, secs), headers: { 'access-control-allow-origin': '*' } });
  });
  await page.goto('/test-harness.html');
  await page.waitForFunction(() => window.__REPULSE_TEST_READY__ === true);
  await page.evaluate(() => window.__REPULSE_TEST__!.reset());
  await page.evaluate((u) => window.__REPULSE_TEST__!.loadManifest(u), `${FIX}manifest.json`);
}

async function render(page: Page, code: string, cycles = 1, prefix: string | null = null) {
  await page.evaluate((p) => window.__REPULSE_TEST__!.setBankPrefix(p), prefix);
  const r = await page.evaluate(
    async ({ src, n }) => window.__REPULSE_TEST__!.renderOffline(src, n, { cycleDur: 2.0 }),
    { src: code, n: cycles },
  );
  assertFinitePcm(r.left);
  return { sr: r.sampleRate, mono: mixMono(r.left, r.right), left: r.left };
}

const FREQS = [220, 240, 420, 440, 620, 640];
/** Which fixture bd frequency (220…640) dominates the 150 ms after t. */
function bdAt(mono: number[], sr: number, t: number): number {
  const seg = mono.slice(Math.round(t * sr), Math.round((t + 0.15) * sr));
  return FREQS.map((hz) => [hz, goertzel(seg, sr, hz)] as const).sort((a, b) => b[1] - a[1])[0][0];
}

test.describe('KIT1 layered banks', () => {
  test('velocity selects the layer', async ({ page }) => {
    await setup(page);
    const soft = await render(page, '(->> (seq :FixKit_bd) (velocity 0.1))');
    expect([220, 240]).toContain(bdAt(soft.mono, soft.sr, 0));
    const hard = await render(page, '(->> (seq :FixKit_bd) (velocity 0.9))');
    expect([620, 640]).toContain(bdAt(hard.mono, hard.sr, 0));
    const dflt = await render(page, '(seq :FixKit_bd)');
    expect([620, 640]).toContain(bdAt(dflt.mono, dflt.sr, 0));
  });

  test('round-robin: varies, never repeats, reproducible', async ({ page }) => {
    await setup(page);
    const code = '(fast 8 (seq :bd))';
    const a = await render(page, code, 1, 'FixKit');
    const seq = (r: typeof a) => Array.from({ length: 8 }, (_, i) => bdAt(r.mono, r.sr, (i * CYCLE) / 8));
    const s1 = seq(a);
    expect(new Set(s1).size).toBeGreaterThanOrEqual(2);
    for (let i = 1; i < s1.length; i += 1) expect(s1[i]).not.toBe(s1[i - 1]);
    const b = await render(page, code, 1, 'FixKit');
    expect(seq(b)).toEqual(s1);
  });

  test('sound plays one fixed recording', async ({ page }) => {
    await setup(page);
    const r = await render(page, '(fast 4 (seq (sound :FixKit_bd 3)))');
    for (let i = 0; i < 4; i += 1) expect(bdAt(r.mono, r.sr, (i * CYCLE) / 4)).toBe(440);
  });

  test('velocity 0 is silent', async ({ page }) => {
    await setup(page);
    const r = await render(page, '(->> (seq :FixKit_bd) (velocity 0))');
    expect(rms(r.mono)).toBeLessThan(1e-6);
  });

  test('level follows the formulas across a layer boundary', async ({ page }) => {
    await setup(page);
    const at = async (v: string) => {
      const r = await render(page, `(->> (seq :FixKit_bd) (velocity ${v}))`);
      return rms(r.mono.slice(0, Math.round(0.15 * r.sr)));
    };
    // fixture gains are 1: v = 0.3 → layer 1 scale 1.0; v = 0.31 → layer 2 scale 0.6 + 0.4·0.01/0.3
    const dbDiff = 20 * Math.log10((await at('0.31')) / (await at('0.3')));
    const expected = 20 * Math.log10(0.6133 / 1.0);
    expect(Math.abs(dbDiff - expected)).toBeLessThan(1.5);
  });

  test('closed hat chokes an open hat; (cut 3) on the hat does not', async ({ page }) => {
    await setup(page);
    const ohEnergy = (r: Awaited<ReturnType<typeof render>>, from: number, to: number) =>
      goertzel(r.mono.slice(Math.round(from * r.sr), Math.round(to * r.sr)), r.sr, 1000);
    const choked = await render(page, '(seq :FixKit_oh :FixKit_hh)');
    const free = await render(page, '(seq :FixKit_oh (cut 3 :FixKit_hh))');
    const before = ohEnergy(choked, 0.9, 0.99);
    const after = 20 * Math.log10(ohEnergy(choked, 1.02, 1.12) / before);
    expect(after).toBeLessThan(-30);
    const stillRings = 20 * Math.log10(ohEnergy(free, 1.02, 1.12) / ohEnergy(free, 0.9, 0.99));
    expect(stillRings).toBeGreaterThan(-6);
  });

  test('existing banks still render (regression)', async ({ page }) => {
    await setup(page);
    const synth = await render(page, '(seq :bd :sd)');
    expect(rms(synth.mono)).toBeGreaterThan(0.001);
    await page.evaluate(() => window.__REPULSE_TEST__!.registerTestSample('plainbank', 'tone'));
    const flat = await render(page, '(seq (sound :plainbank 0))');
    expect(rms(flat.mono)).toBeGreaterThan(0.01);
  });
});

test.describe('KIT1 shipped kits', () => {
  for (const kit of ['JazzKit', 'StudioKit']) {
    test(`${kit} renders every drum non-silent and reproducibly`, async ({ page }) => {
      await page.goto('/test-harness.html');
      await page.waitForFunction(() => window.__REPULSE_TEST_READY__ === true);
      await page.evaluate(() => window.__REPULSE_TEST__!.reset());
      await page.evaluate(() => window.__REPULSE_TEST__!.loadManifest(new URL('kits/kits.json', document.baseURI).href));
      const code = '(seq :bd :sd :hh :oh :lt :cr)';
      const a = await render(page, code, 1, kit);
      const win = CYCLE / 6;
      for (let i = 0; i < 6; i += 1) {
        const seg = a.mono.slice(Math.round(i * win * a.sr), Math.round((i * win + 0.1) * a.sr));
        expect(rms(seg), `${kit} onset ${i}`).toBeGreaterThan(0.001);
      }
      const b = await render(page, code, 1, kit);
      // Same takes → same samples; Web Audio mixing is only guaranteed to float rounding.
      let maxd = 0;
      for (let i = 0; i < a.left.length; i += 1) maxd = Math.max(maxd, Math.abs(a.left[i] - b.left[i]));
      expect(maxd).toBeLessThan(1e-5);
    });
  }
});

test('(bank nil) clears the prefix without an error', async ({ page }) => {
  await page.goto('/');
  await page.waitForFunction(() => !!(window as any).__REPULSE_APP__);
  const ev = (c: string) => page.evaluate((x) => (window as any).__REPULSE_APP__.evaluate(x), c);
  await ev('(bank :JazzKit)');
  await expect(page.locator('#output')).toHaveText(/bank: JazzKit/);
  await ev('(bank nil)');
  await expect(page.locator('#output')).toHaveText(/bank: cleared/);
});
