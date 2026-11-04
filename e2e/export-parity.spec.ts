import { expect, type Page, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import {
  assertFinitePcm,
  goertzel,
  mixMono,
  parseWav16,
  rms,
  rmsPerWindow,
  toDb,
  type Wav16,
} from './helpers/audio-analysis';

// Phase EXP1 — offline export parity. These tests drive the production app
// (`/`, decision E16): the real (export …) built-in, the real FX plugins and
// the real WASM worklet, reading the WAV from the browser download.

type ExpApp = {
  evaluate: (code: string) => void;
  audioBackend: () => string;
};

const app = (page: Page) =>
  page.evaluate(() => (window as unknown as { __REPULSE_APP__: ExpApp }).__REPULSE_APP__.audioBackend());

async function load(page: Page) {
  page.on('dialog', (d) => d.accept());
  await page.goto('/');
  await page.waitForFunction(() => {
    const w = window as unknown as { __REPULSE_EDITOR__?: unknown; __REPULSE_APP__?: unknown };
    return !!w.__REPULSE_EDITOR__ && !!w.__REPULSE_APP__;
  });
  await expect.poll(() => app(page), { timeout: 15_000 }).toBe('audioworklet+wasm');
}

async function setText(page: Page, text: string) {
  await page.evaluate((t) => {
    const v = (window as unknown as { __REPULSE_EDITOR__: any }).__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
  }, text);
}

const output = (page: Page) => page.locator('#output');

async function readDownload(page: Page, trigger: () => Promise<unknown>, timeout = 30_000) {
  const [download] = await Promise.all([page.waitForEvent('download', { timeout }), trigger()]);
  const path = await download.path();
  const wav = parseWav16(readFileSync(path));
  assertFinitePcm(wav.left);
  assertFinitePcm(wav.right);
  return { wav, filename: download.suggestedFilename() };
}

/** Put code in the editor and evaluate it with Alt+Enter; resolves with the downloaded WAV. */
async function exportBuffer(page: Page, code: string, timeout?: number) {
  await setText(page, code);
  return readDownload(
    page,
    async () => {
      await page.evaluate(() => (window as unknown as { __REPULSE_EDITOR__: any }).__REPULSE_EDITOR__.focus());
      await page.keyboard.press('Alt+Enter');
    },
    timeout,
  );
}

/** Evaluate code like the command bar; resolves with the downloaded WAV. */
async function exportCommand(page: Page, code: string, timeout?: number) {
  return readDownload(
    page,
    () =>
      page.evaluate((c) => (window as unknown as { __REPULSE_APP__: ExpApp }).__REPULSE_APP__.evaluate(c), code),
    timeout,
  );
}

const sr = (page: Page) =>
  page.evaluate(() => {
    // The app's AudioContext rate — the WAV must use it (decision E3).
    const C = (window as unknown as { AudioContext: typeof AudioContext }).AudioContext;
    return new C().sampleRate;
  });

const mono = (w: Wav16) => mixMono(w.left, w.right);
const seg = (x: number[], rate: number, from: number, to: number) =>
  x.slice(Math.round(from * rate), Math.round(to * rate));

/** Sine-baseline analysis: an ideal sine at f with the same length. */
function sine(f: number, rate: number, secs: number) {
  const n = Math.round(secs * rate);
  return Array.from({ length: n }, (_, i) => 0.5 * Math.sin((2 * Math.PI * f * i) / rate));
}

test.beforeEach(async ({ page }) => {
  // Keep the default sample manifests (GitHub) out: :bd / :sd / :hh then use
  // the built-in WASM drum voices, which the expected values below assume, and
  // the tests do not depend on network access.
  await page.route('https://raw.githubusercontent.com/**', (r) => r.abort());
  await page.addInitScript(() => {
    try {
      localStorage.clear();
    } catch {
      /* ignore */
    }
  });
  await page.setViewportSize({ width: 1280, height: 800 });
});

test('(export 4) length uses the device sample rate', async ({ page }) => {
  await load(page);
  const rate = await sr(page);
  const { wav, filename } = await exportBuffer(page, '(bpm 240)\n(track :a (seq :bd :_ :hh :_))\n(export 4)');
  expect(filename).toBe('repulse-all-4cycles.wav');
  expect(wav.sampleRate).toBe(rate);
  expect(wav.frames).toBe(Math.round(4 * 1.0 * rate));
  expect(rms(mono(wav))).toBeGreaterThan(1e-3);
  await expect(output(page)).toHaveText('exported all tracks — 4 cycles (offline)');
});

test('FM e-piano exports with its sidebands (not a sine)', async ({ page }) => {
  await load(page);
  const { wav } = await exportBuffer(page, '(bpm 240)\n(track :keys (->> (seq :c4) (synth :epiano)))\n(export 4)');
  await expect(output(page)).toHaveText('exported all tracks — 4 cycles (offline)');
  const r = wav.sampleRate;
  const x = seg(mono(wav), r, 0, 0.4);
  const carrier = goertzel(x, r, 261.63);
  expect(carrier).toBeGreaterThan(0);
  const first = [3401.13, 3924.38].map((f) => goertzel(x, r, f) / carrier);
  const second = [7063.89, 7587.14].map((f) => goertzel(x, r, f) / carrier);
  console.log('epiano sideband ratios', first, second);
  for (const v of first) expect(v).toBeGreaterThanOrEqual(0.3);
  for (const v of second) expect(v).toBeGreaterThanOrEqual(0.05);
  // Sine baseline
  const b = sine(261.63, r, 0.4);
  const bc = goertzel(b, r, 261.63);
  for (const f of [3401.13, 3924.38, 7063.89, 7587.14]) expect(goertzel(b, r, f) / bc).toBeLessThan(0.01);
});

/** Magnitude peak of goertzel over [lo, hi] Hz in 1 Hz steps (0.25 Hz near the peak). */
function peak(x: number[], rate: number, lo: number, hi: number) {
  let best = { f: lo, m: -1 };
  for (let f = lo; f <= hi; f += 1) {
    const m = goertzel(x, rate, f);
    if (m > best.m) best = { f, m };
  }
  return best;
}

test('plucked guitar exports as a Karplus-Strong string (not a sine)', async ({ page }) => {
  await load(page);
  const { wav } = await exportBuffer(page, '(bpm 240)\n(track :gtr (->> (seq :a3) (synth :guitar)))\n(export 4)');
  const r = wav.sampleRate;
  const x = seg(mono(wav), r, 0, 0.5);
  const f0 = peak(x, r, 150, 300);
  console.log('guitar f0', f0);
  expect(Math.abs(f0.f - 220)).toBeLessThan(15);
  const harm = (y: number[], f: number) =>
    Math.max(...[2, 3, 4, 5].map((k) => peak(y, r, Math.floor(k * f * 0.98), Math.ceil(k * f * 1.02)).m));
  const ratio = harm(x, f0.f) / f0.m;
  console.log('guitar harmonic ratio', ratio);
  expect(ratio).toBeGreaterThanOrEqual(0.1);
  const b = sine(220, r, 0.5);
  expect(harm(b, 220) / goertzel(b, r, 220)).toBeLessThan(0.01);
});

test('track reverb rings out after the last note', async ({ page }) => {
  await load(page);
  const prog = (fx: string) =>
    `(bpm 240)\n(track :pad (->> (seq :c4 :_ :_ :_) (synth :saw) (decay 0.1)${fx}))\n(export 4)`;
  const wet = await exportBuffer(page, prog(' (fx :reverb 0.4)'));
  const r = wet.wav.sampleRate;
  const tailWet = rms(seg(mono(wet.wav), r, 3.2, 3.9));
  const dry = await exportBuffer(page, prog(''));
  const tailDry = rms(seg(mono(dry.wav), r, 3.2, 3.9));
  console.log('reverb tail', tailWet, tailDry);
  expect(tailWet).toBeGreaterThanOrEqual(1e-3);
  expect(tailDry).toBeLessThanOrEqual(1e-4);
});

function windowPeak(x: number[], rate: number, from: number, to: number) {
  const w = Math.round(0.01 * rate);
  let best = 0;
  for (let i = Math.round(from * rate); i + w <= Math.round(to * rate); i += Math.max(1, Math.round(w / 4))) {
    best = Math.max(best, rms(x.slice(i, i + w)));
  }
  return best;
}

test('master delay echoes at the delay time', async ({ page }) => {
  await load(page);
  const prog = (m: string) => `(bpm 120)\n(track :hat (seq :hh :_ :_ :_))${m}\n(export 1)`;
  const { wav } = await exportBuffer(page, prog('\n(master (fx :delay :wet 0.5 :time 0.25))'));
  const r = wav.sampleRate;
  const x = mono(wav);
  const lag = Math.round(0.25 * r);
  const n = Math.round(0.05 * r);
  let best = { lag: 0, c: -Infinity };
  for (let l = Math.round(0.2 * r); l <= Math.round(0.3 * r); l += 1) {
    let c = 0;
    for (let i = 0; i < n; i += 1) c += x[i] * x[i + l];
    if (c > best.c) best = { lag: l, c };
  }
  console.log('delay lag', best.lag, 'expected', lag);
  expect(Math.abs(best.lag - lag)).toBeLessThanOrEqual(2);
  const echo = windowPeak(x, r, 0.24, 0.3) / windowPeak(x, r, 0, 0.05);
  const dry = await exportBuffer(page, prog(''));
  const xd = mono(dry.wav);
  const echoDry = windowPeak(xd, r, 0.24, 0.3) / windowPeak(xd, r, 0, 0.05);
  console.log('delay echo ratio', echo, echoDry);
  expect(echo).toBeGreaterThanOrEqual(0.25);
  expect(echoDry).toBeLessThanOrEqual(0.02);
});

const SONG_P = `(bpm 240)
(track :kick (seq :bd :_ :bd :_))
(track :hats (fast 2 (seq :hh :_)))
(track :bass (->> (seq :c2 :_ :g2 :_) (synth :saw)))
(track :keys (->> (seq :c4 :e4) (synth :epiano)))
(track :snare (->> (seq :_ :sd) (fx :filter :freq 2000)))
(song (section :a 2 [:kick :bass :snare]) (section :b 2 :all))
(master (fx :delay :wet 0.3 :time 0.25))`;

test('offline (export :song) matches the real-time capture', async ({ page }) => {
  test.setTimeout(60_000);
  await load(page);
  const off = await exportBuffer(page, `${SONG_P}\n(export :song :tail 1)`);
  await expect(output(page)).toHaveText('exported song — 4 bars, 5.0 s (offline)');
  const rt = await exportBuffer(page, `${SONG_P}\n(export :song :tail 1 :realtime)`, 30_000);
  await expect(output(page)).toHaveText('exported song — 4 bars, 5.0 s');
  const r = off.wav.sampleRate;
  expect(rt.wav.sampleRate).toBe(r);
  expect(off.filename).toBe('repulse-song-4bars.wav');
  expect(off.wav.frames).toBe(Math.round(5.0 * r));
  expect(rt.wav.frames).toBe(Math.round(5.0 * r));
  const a = rmsPerWindow(mono(off.wav), r);
  const b = rmsPerWindow(mono(rt.wav), r);
  console.log('song P windows offline', a, 'realtime', b);
  for (let i = 0; i < b.length; i += 1) {
    if (b[i] >= 1e-3) {
      expect(Math.abs(toDb(a[i]) - toDb(b[i]))).toBeLessThanOrEqual(1);
    } else {
      expect(a[i]).toBeLessThan(1e-3);
    }
  }
});

test('plugin worklets render offline (bitcrusher bits, dattorro reverb)', async ({ page }) => {
  await load(page);
  const crush = (m: string) => `(bpm 240)\n(track :t (seq :a4))${m}\n(export 1)`;
  const c1 = await exportBuffer(page, crush('\n(master (fx :bitcrusher :wet 1 :bits 3))'));
  await expect(output(page)).toHaveText('exported all tracks — 1 cycles (offline)');
  const r = c1.wav.sampleRate;
  const h = (w: Wav16) => {
    const x = seg(mono(w), r, 0, 0.3);
    return goertzel(x, r, 1320) / goertzel(x, r, 440);
  };
  const c0 = await exportBuffer(page, crush(''));
  console.log('bitcrusher 3rd harmonic ratio', h(c1.wav), h(c0.wav));
  expect(h(c1.wav)).toBeGreaterThanOrEqual(0.05);
  expect(h(c0.wav)).toBeLessThanOrEqual(0.005);

  const plate = (m: string) => `(bpm 240)\n(track :t (seq :hh :_ :_ :_))${m}\n(export 2)`;
  const d1 = await exportBuffer(page, plate('\n(master (fx :dattorro-reverb 0.5))'));
  await expect(output(page)).toHaveText('exported all tracks — 2 cycles (offline)');
  const d0 = await exportBuffer(page, plate(''));
  // The spec's window was [0.3 s, 1.5 s], but at 240 BPM the second hat of
  // (export 2) starts at 1.0 s, inside that window, so the dry bound can't
  // hold there. The tail is measured between the two hats instead.
  const t1 = rms(seg(mono(d1.wav), r, 0.3, 0.95));
  const t0 = rms(seg(mono(d0.wav), r, 0.3, 0.95));
  console.log('dattorro tail [0.3, 0.95]', t1, t0,
    '[0.3, 1.5]', rms(seg(mono(d1.wav), r, 0.3, 1.5)), rms(seg(mono(d0.wav), r, 0.3, 1.5)));
  expect(t1).toBeGreaterThanOrEqual(1e-3);
  expect(t0).toBeLessThanOrEqual(1e-4);
});

const SONG_F = `(bpm 480)
(track :lead (->> (seq :a3 :a3) (synth :saw)))
(song (section :a 2 [:lead]))`;

test('falls back to real-time capture when the offline worklet cannot start', async ({ page }) => {
  test.setTimeout(60_000);
  await load(page);
  // The live worklet is up; now make every fresh worklet/WASM load fail.
  await page.route('**/worklet.js', (r) => r.abort());
  await page.route('**/repulse_audio_bg.wasm', (r) => r.abort());

  await setText(page, `${SONG_F}\n(export :song :tail 0.5)`);
  const song = readDownload(page, async () => {
    await page.evaluate(() => (window as unknown as { __REPULSE_EDITOR__: any }).__REPULSE_EDITOR__.focus());
    await page.keyboard.press('Alt+Enter');
  });
  await expect(output(page)).toContainText('offline render unavailable (');
  await expect(output(page)).toContainText(') — capturing in real time instead…');
  const notice = await output(page).textContent();
  console.log('fallback notice:', notice);
  const { wav, filename } = await song;
  expect(filename).toBe('repulse-song-2bars.wav');
  const r = wav.sampleRate;
  expect(wav.frames).toBe(Math.round(1.5 * r));
  const x = seg(mono(wav), r, 0, 1.0);
  expect(goertzel(x, r, 440)).toBeGreaterThanOrEqual(0.1 * goertzel(x, r, 220));
  await expect(output(page)).toHaveText('exported song — 2 bars, 1.5 s');

  await setText(page, '(bpm 480)\n(track :lead (->> (seq :a3 :a3) (synth :saw)))\n(export 2)');
  const cyc = readDownload(page, async () => {
    await page.evaluate(() => (window as unknown as { __REPULSE_EDITOR__: any }).__REPULSE_EDITOR__.focus());
    await page.keyboard.press('Alt+Enter');
  });
  await expect(output(page)).toContainText('offline render unavailable (');
  const c = await cyc;
  expect(c.filename).toBe('repulse-all-2cycles.wav');
  expect(c.wav.frames).toBe(Math.round(2 * 0.5 * r));
  await expect(output(page)).toHaveText('exported all tracks — 2 cycles (real time)');
});

test(':realtime keeps the SONG1 capture and its messages', async ({ page }) => {
  test.setTimeout(60_000);
  await load(page);
  await setText(page, `${SONG_F}\n(export :song :tail 0.5 :realtime)`);
  const dl = readDownload(page, async () => {
    await page.evaluate(() => (window as unknown as { __REPULSE_EDITOR__: any }).__REPULSE_EDITOR__.focus());
    await page.keyboard.press('Alt+Enter');
  });
  await expect(output(page)).toHaveText('exporting song — 2 bars, 1.5 s in real time… (stop) cancels');
  const { wav } = await dl;
  expect(wav.frames).toBe(Math.round(1.5 * wav.sampleRate));
  await expect(output(page)).toHaveText('exported song — 2 bars, 1.5 s');

  // (stop) cancels a running real-time capture
  await page.evaluate(
    () => (window as unknown as { __REPULSE_APP__: ExpApp }).__REPULSE_APP__.evaluate('(export :song :realtime)'),
  );
  await expect(output(page)).toContainText('in real time… (stop) cancels');
  await page.evaluate(() => (window as unknown as { __REPULSE_APP__: ExpApp }).__REPULSE_APP__.evaluate('(stop)'));
  await expect(output(page)).toHaveText('export cancelled');

  await page.evaluate(
    () => (window as unknown as { __REPULSE_APP__: ExpApp }).__REPULSE_APP__.evaluate('(export 4 :realtime)'),
  );
  await expect(output(page)).toHaveText('Error: :realtime only applies to (export :song)');
});

test('visualiser plugins do not change the offline export', async ({ page }) => {
  await load(page);
  const before = await exportBuffer(page, '(bpm 240)\n(track :keys (->> (seq :c4 :e4) (synth :epiano)))\n(export 2)');
  await setText(page, '(load-plugin "/plugins/oscilloscope.js")');
  await page.evaluate(() => (window as unknown as { __REPULSE_EDITOR__: any }).__REPULSE_EDITOR__.focus());
  await page.keyboard.press('Alt+Enter');
  await page.waitForTimeout(1000);
  const after = await exportBuffer(
    page,
    '(bpm 240)\n(track :keys (->> (seq :c4 :e4) (synth :epiano) (fx :oscilloscope)))\n(export 2)',
  );
  await expect(output(page)).toHaveText('exported all tracks — 2 cycles (offline)');
  expect(after.wav.frames).toBe(before.wav.frames);
  let maxDiff = 0;
  for (let i = 0; i < before.wav.frames; i += 1) {
    maxDiff = Math.max(
      maxDiff,
      Math.abs(after.wav.left[i] - before.wav.left[i]),
      Math.abs(after.wav.right[i] - before.wav.right[i]),
    );
  }
  console.log('visualiser max diff (LSB)', maxDiff * 32767);
  expect(maxDiff).toBeLessThanOrEqual(1 / 32767 + 1e-9);
  expect(rms(mono(before.wav))).toBeGreaterThan(1e-3);
});

const SONG_T = `(bpm 480)
(track :lead (->> (seq :c4 :e4 :g4 :c5) (synth :saw) (amp (tween :linear 0.0 1.0 4))))
(song (section :a 4 [:lead]))`;

async function tweenPair(page: Page, realtime: boolean) {
  const opt = realtime ? ' :realtime' : '';
  // A: export in the same evaluation as the song
  const a = await exportBuffer(page, `${SONG_T}\n(export :song :tail 0.5${opt})`);
  // B: song playing for ≥ 3 bars, then export from the command bar
  await setText(page, SONG_T);
  await page.evaluate(() => (window as unknown as { __REPULSE_EDITOR__: any }).__REPULSE_EDITOR__.focus());
  await page.keyboard.press('Alt+Enter');
  await page.waitForTimeout(1600);
  const b = await exportCommand(page, `(export :song :tail 0.5${opt})`);
  const r = a.wav.sampleRate;
  const bars = (w: Wav16) => rmsPerWindow(mono(w), Math.round(0.5 * r)).slice(0, 4);
  return { a: bars(a.wav), b: bars(b.wav) };
}

for (const realtime of [false, true]) {
  test(`tweens start at the export's bar 0 (${realtime ? 'real time' : 'offline'})`, async ({ page }) => {
    test.setTimeout(60_000);
    await load(page);
    const { a, b } = await tweenPair(page, realtime);
    console.log('tween bars', realtime ? 'realtime' : 'offline', a, b);
    for (let i = 0; i < 4; i += 1) {
      expect(Math.abs(toDb(a[i]) - toDb(b[i]))).toBeLessThanOrEqual(0.1);
    }
    for (let i = 1; i < 4; i += 1) {
      expect(a[i]).toBeGreaterThan(a[i - 1]);
      expect(b[i]).toBeGreaterThan(b[i - 1]);
    }
  });
}
