import { expect, type Page, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import {
  assertFinitePcm,
  mixMono,
  parseWav16,
  rms,
  type Wav16,
} from './helpers/audio-analysis';
import { parseZipStore } from './helpers/zip';

// Phase SK1 — stems (export :stems), (export :track n) and (midi-export :song).
// Like e2e/export-parity.spec.ts these drive the production app at `/`: the
// real WASM worklet, the real FX plugins, files read from the browser download.

type SkApp = {
  evaluate: (code: string) => void;
  audioBackend: () => string;
  renderDryMix: (tail: number) => Promise<{ sampleRate: number; frames: number; left: number[]; right: number[] }>;
};

const backend = (page: Page) =>
  page.evaluate(() => (window as unknown as { __REPULSE_APP__: SkApp }).__REPULSE_APP__.audioBackend());

async function load(page: Page) {
  page.on('dialog', (d) => d.accept());
  await page.goto('/');
  await page.waitForFunction(() => {
    const w = window as unknown as { __REPULSE_EDITOR__?: unknown; __REPULSE_APP__?: unknown };
    return !!w.__REPULSE_EDITOR__ && !!w.__REPULSE_APP__;
  });
  await expect.poll(() => backend(page), { timeout: 15_000 }).toBe('audioworklet+wasm');
}

async function setText(page: Page, text: string) {
  await page.evaluate((t) => {
    const v = (window as unknown as { __REPULSE_EDITOR__: any }).__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
  }, text);
}

const output = (page: Page) => page.locator('#output');

async function evalBuffer(page: Page, code: string) {
  await setText(page, code);
  await page.evaluate(() => (window as unknown as { __REPULSE_EDITOR__: any }).__REPULSE_EDITOR__.focus());
  await page.keyboard.press('Alt+Enter');
}

/** Evaluate the buffer with Alt+Enter and resolve with the raw downloaded file. */
async function downloadOf(page: Page, code: string, timeout = 60_000) {
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout }),
    evalBuffer(page, code),
  ]);
  return { buf: readFileSync(await download.path()), filename: download.suggestedFilename() };
}

function wavOf(buf: Buffer): Wav16 {
  const w = parseWav16(buf);
  assertFinitePcm(w.left);
  assertFinitePcm(w.right);
  return w;
}

const sr = (page: Page) =>
  page.evaluate(() => new (window as unknown as { AudioContext: typeof AudioContext }).AudioContext().sampleRate);

const mono = (w: Wav16) => mixMono(w.left, w.right);
const seg = (x: number[], rate: number, from: number, to: number) =>
  x.slice(Math.round(from * rate), Math.round(to * rate));
const peakRms = (x: number[], rate: number, from: number, to: number, win = 0.01) => {
  const s = seg(x, rate, from, to);
  const n = Math.round(win * rate);
  let best = 0;
  for (let i = 0; i + n <= s.length; i += n) best = Math.max(best, rms(s.slice(i, i + n)));
  return best;
};

const SONG_S = `(bpm 120)
(track :kick (seq :bd :sd :hh :oh))
(track :bass (amp 0.5 (seq :c2 :_ :g2 :_)))
(song (section :intro 2 [:kick]) (section :verse 4 :all) (section :outro 2 [:bass]))`;

const hexBytes = (s: string) => Buffer.from(s.replace(/\s+/g, ''), 'hex');

test.beforeEach(async ({ page }) => {
  // :bd :sd :hh use the built-in WASM drum voices (no GitHub sample manifests).
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

test('stems: one WAV per track, identical length, in a zip', async ({ page }) => {
  await load(page);
  const rate = await sr(page);
  // Record every status line the output panel shows during the export.
  await page.evaluate(() => {
    const w = window as unknown as { __outs: string[] };
    w.__outs = [];
    const el = document.getElementById('output')!;
    new MutationObserver(() => w.__outs.push(el.textContent ?? '')).observe(el, {
      childList: true,
      characterData: true,
      subtree: true,
    });
  });
  const { buf, filename } = await downloadOf(page, `${SONG_S}\n(export :stems)`);
  expect(filename).toBe('repulse-stems.zip');
  const entries = parseZipStore(buf);
  expect(entries.map((e) => e.name)).toEqual(['repulse-kick.wav', 'repulse-bass.wav']);
  const wavs = entries.map((e) => wavOf(e.data));
  for (const w of wavs) {
    expect(w.sampleRate).toBe(rate);
    expect(w.frames).toBe(Math.round((8 * 2 + 2) * rate));
  }
  await expect(output(page)).toHaveText('exported 2 stems — 8 bars, 18.0 s (offline)');
  const outs = await page.evaluate(() => (window as unknown as { __outs: string[] }).__outs);
  console.log('status lines:', outs);
  // (the eval status "rendering stems — …" is replaced by the first progress line in the same tick; unit-tested)
  expect(outs).toContain('rendering stem 1/2 — :kick…');
  expect(outs).toContain('rendering stem 2/2 — :bass…');
});

test('(export :stems n) renders n cycles of every track', async ({ page }) => {
  await load(page);
  const rate = await sr(page);
  const { buf, filename } = await downloadOf(
    page,
    '(bpm 240)\n(track :b (seq :bd))\n(track :a (seq :hh))\n(export :stems 4)',
  );
  expect(filename).toBe('repulse-stems.zip');
  const entries = parseZipStore(buf);
  expect(entries.map((e) => e.name)).toEqual(['repulse-a.wav', 'repulse-b.wav']);
  for (const e of entries) expect(wavOf(e.data).frames).toBe(Math.round(4 * 1.0 * rate));
  await expect(output(page)).toHaveText('exported 2 stems — 4 cycles (offline)');
});

test('stems: each stem is silent outside its sections', async ({ page }) => {
  await load(page);
  const { buf } = await downloadOf(page, `${SONG_S}\n(export :stems)`);
  const [kick, bass] = parseZipStore(buf).map((e) => wavOf(e.data));
  const r = kick.sampleRate;
  const k = mono(kick);
  const b = mono(bass);
  for (let w = 0; w < 12; w += 1) expect(rms(seg(k, r, w, w + 1))).toBeGreaterThanOrEqual(1e-3);
  expect(rms(seg(k, r, 12.5, 16))).toBeLessThanOrEqual(1e-4);
  expect(rms(seg(b, r, 0, 3.9))).toBeLessThanOrEqual(1e-4);
  for (let w = 4; w < 16; w += 1) expect(rms(seg(b, r, w, w + 1))).toBeGreaterThanOrEqual(1e-3);
});

const SONG_D = `(bpm 120)
(track :kick (->> (seq :bd :_ :bd :_) (amp 0.4)))
(track :bass (->> (seq :c2 :_ :g2 :_) (synth :saw) (amp 0.2)))
(track :keys (->> (seq :c4 :e4) (synth :epiano) (amp 0.2)))
(track :lead (->> (seq :a3 :_ :e4 :_) (synth :saw) (amp 0.2) (fx :filter :freq 1200)))
(song (section :a 2 [:kick :bass]) (section :b 2 :all))`;

test('sum of the stems equals the pre-master mix', async ({ page }) => {
  test.setTimeout(120_000);
  await load(page);
  const { buf } = await downloadOf(page, `${SONG_D}\n(export :stems :tail 1)`);
  const stems = parseZipStore(buf).map((e) => wavOf(e.data));
  expect(stems).toHaveLength(4);
  const dry = await page.evaluate(
    () => (window as unknown as { __REPULSE_APP__: SkApp }).__REPULSE_APP__.renderDryMix(1),
  );
  expect(stems[0].frames).toBe(dry.frames);
  let worst = 0;
  const sumL: number[] = new Array(dry.frames).fill(0);
  for (let i = 0; i < dry.frames; i += 1) {
    let l = 0;
    let rr = 0;
    for (const s of stems) {
      l += s.left[i];
      rr += s.right[i];
    }
    sumL[i] = l;
    worst = Math.max(worst, Math.abs(l - dry.left[i]), Math.abs(rr - dry.right[i]));
  }
  console.log('max |sum(stems) - dry mix| =', worst);
  expect(worst).toBeLessThanOrEqual(2.5e-4);
  expect(rms(sumL)).toBeGreaterThanOrEqual(1e-2);
});

test('stems have per-track FX but no master FX', async ({ page }) => {
  await load(page);
  const code = `(bpm 120)
(track :hat (seq :hh :_ :_ :_))
(master (fx :delay :wet 0.5 :time 0.25))
(song (section :a 1 [:hat]))`;
  const stem = wavOf(parseZipStore((await downloadOf(page, `${code}\n(export :stems :tail 0)`)).buf)[0].data);
  const r = stem.sampleRate;
  const stemEcho = peakRms(mono(stem), r, 0.24, 0.3) / peakRms(mono(stem), r, 0, 0.05);
  const mix = wavOf((await downloadOf(page, `${code}\n(export :song :tail 0)`)).buf);
  const mixEcho = peakRms(mono(mix), r, 0.24, 0.3) / peakRms(mono(mix), r, 0, 0.05);
  console.log('echo ratio stem', stemEcho, 'mix', mixEcho);
  expect(stemEcho).toBeLessThanOrEqual(0.02);
  expect(mixEcho).toBeGreaterThanOrEqual(0.25);
});

test('stems: a sidechained pad still ducks', async ({ page }) => {
  await load(page);
  const code = `(bpm 120)
(track :k (seq :bd :_ :bd :_))
(track :pad (->> (seq :c3) (synth :saw) (decay 4) (amp 0.3)
                 (fx :sidechain :trigger :bd :amount 0.8 :release 0.1)))
(song (section :a 1 [:k :pad]))
(export :stems :tail 0)`;
  const { buf } = await downloadOf(page, code);
  const entries = parseZipStore(buf);
  const pad = wavOf(entries.find((e) => e.name === 'repulse-pad.wav')!.data);
  const r = pad.sampleRate;
  const x = mono(pad);
  const before = rms(seg(x, r, 0.92, 0.98));
  const during = rms(seg(x, r, 1.0, 1.02));
  console.log('pad rms before/during duck', before, during);
  expect(during).toBeLessThanOrEqual(0.5 * before);
});

test('stems need the offline renderer: no real-time fallback', async ({ page }) => {
  await load(page);
  await page.route('**/worklet.js', (r) => r.abort());
  await page.route('**/repulse_audio_bg.wasm', (r) => r.abort());
  let downloaded = false;
  page.on('download', () => {
    downloaded = true;
  });
  await evalBuffer(page, `${SONG_S}\n(export :stems)`);
  await expect(output(page)).toContainText(
    'Error: stems need the offline renderer — WASM worklet failed to start offline:',
  );
  await page.waitForTimeout(5_000);
  expect(downloaded).toBe(false);
});

test('(export :track n) renders n cycles of one track', async ({ page }) => {
  await load(page);
  const rate = await sr(page);
  const { buf, filename } = await downloadOf(page, '(bpm 240)\n(track :drums (seq :bd :hh))\n(export :drums 8)');
  expect(filename).toBe('repulse-drums-8cycles.wav');
  const w = wavOf(buf);
  expect(w.frames).toBe(Math.round(8 * 1.0 * rate));
  await expect(output(page)).toHaveText('exported :drums — 8 cycles (offline)');
});

// ── MIDI ────────────────────────────────────────────────────────────────

const drumBar = [0x24, 0x26, 0x2a, 0x2e]
  .map((n) => `00 99 ${n.toString(16)} 7F 83 60 89 ${n.toString(16)} 00`)
  .join(' ');
const bassBar = '83 60 91 24 40 83 60 81 24 00 83 60 91 2B 40 83 60 81 2B 00';

const SONG_S_MIDI = Buffer.concat([
  hexBytes('4D 54 68 64 00 00 00 06 00 01 00 03 01 E0'),
  hexBytes(
    '4D 54 72 6B 00 00 00 31 00 FF 58 04 04 02 18 08 00 FF 51 03 07 A1 20 ' +
      '00 FF 06 05 69 6E 74 72 6F 9E 00 FF 06 05 76 65 72 73 65 ' +
      'BC 00 FF 06 05 6F 75 74 72 6F 9E 00 FF 2F 00',
  ),
  hexBytes(`4D 54 72 6B 00 00 00 E5 00 FF 03 04 6B 69 63 6B ${Array(6).fill(drumBar).join(' ')} 9E 00 FF 2F 00`),
  hexBytes(
    '4D 54 72 6B 00 00 00 85 00 FF 03 04 62 61 73 73 9E 00 91 24 40 83 60 81 24 00 83 60 91 2B 40 83 60 81 2B 00 ' +
      `${Array(5).fill(bassBar).join(' ')} 83 60 FF 2F 00`,
  ),
]);

test('(midi-export :song) downloads the format-1 file of song S', async ({ page }) => {
  await load(page);
  const { buf, filename } = await downloadOf(page, `${SONG_S}\n(midi-export :song)`, 15_000);
  expect(filename).toBe('repulse-song-8bars.mid');
  expect(buf.length).toBe(449);
  expect(buf.equals(SONG_S_MIDI)).toBe(true);
  await expect(output(page)).toHaveText('exported song as MIDI — 2 tracks, 8 bars');
});

test('(midi-export :track n :drum-map …) maps drum names and warns about the rest', async ({ page }) => {
  await load(page);
  const { buf, filename } = await downloadOf(
    page,
    '(track :p (seq :bd :tabla :tabla :perc))\n(midi-export :p 2 :drum-map {:tabla 60})',
    15_000,
  );
  expect(filename).toBe('repulse-p.mid');
  expect(buf.toString('hex')).toContain('993c7f');
  // The app appends its own unknown-sound warnings after the status.
  await expect(output(page)).toContainText(
    'exported 2 cycles of :p as MIDI — skipped 2 events with no GM drum note (:perc); map them with :drum-map',
  );
});
