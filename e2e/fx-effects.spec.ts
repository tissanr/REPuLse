import { expect, type Page, test } from '@playwright/test';
import {
  bandEnergyDb,
  goertzel,
  instFreq,
  mixMono,
  nullDb,
  peakDb,
  rms,
  rmsBetween,
  schroederRt60,
  toDb,
} from './helpers/audio-analysis';
import { exportBuffer, loadApp, monoOf } from './helpers/app-export';
import { type BenchInput, type BenchOptions, renderDry, renderFx } from './helpers/fx-bench';

// Phase FX1 — reference tests for the mixing, space and modulation effects.
// Bench tests render one plugin in an OfflineAudioContext at 44.1 kHz
// (e2e/helpers/fx-bench.ts). "Level" is the RMS of the last half of the render
// compared with the same render of the dry input, in dB.

const SR = 44100;

/** Mean RMS (dB) of 0.05–2.5 s of a click through the pre-FX1 reverb at wet 0.4
 *  (mono mix), captured with the bench before reverb.js changed (5 renders,
 *  −69.47 … −69.53 dB). */
const REVERB_CLASSIC_RMS_DB = -69.5;

const lastHalf = (x: number[]) => x.slice(Math.floor(x.length / 2));

async function levelDb(page: Page, o: BenchOptions, channel: 'left' | 'right' = 'left') {
  const wet = await renderFx(page, o);
  const dry = await renderDry(page, o.input, o.seconds);
  return toDb(rms(lastHalf(wet[channel])) / rms(lastHalf(dry[channel])));
}

const sine = (hz: number, db: number, extra: Partial<Extract<BenchInput, { kind: 'sine' }>> = {}): BenchInput => ({
  kind: 'sine',
  hz,
  db,
  ...extra,
});

/** Best-matching lag of out against inp within [lo, hi] samples → {lag, null dB}. */
function bestLag(out: number[], inp: number[], lo: number, hi: number) {
  let best = { lag: -1, db: Infinity };
  for (let lag = lo; lag <= hi; lag += 1) {
    const db = nullDb(out.slice(lag), inp.slice(0, inp.length - lag));
    if (db < best.db) best = { lag, db };
  }
  return best;
}

// ── Part A — mixing ─────────────────────────────────────────────────────

test.describe('Part A — mixing', () => {
  test('A1 eq low shelf +6 dB at 100 Hz', async ({ page }) => {
    const p: [string, unknown][] = [['low', 6], ['low-freq', 100]];
    const lo = await levelDb(page, { plugin: '/plugins/eq.js', params: p, input: sine(50, -12), seconds: 1 });
    const hi = await levelDb(page, { plugin: '/plugins/eq.js', params: p, input: sine(5000, -12), seconds: 1 });
    console.log('A1', lo, hi);
    expect(lo).toBeGreaterThanOrEqual(5);
    expect(lo).toBeLessThanOrEqual(7);
    expect(Math.abs(hi)).toBeLessThanOrEqual(0.5);
  });

  test('A2 eq mid −6 dB at 1 kHz, Q 2', async ({ page }) => {
    const p: [string, unknown][] = [['mid', -6], ['mid-freq', 1000], ['mid-q', 2]];
    const at = async (hz: number) => levelDb(page, { plugin: '/plugins/eq.js', params: p, input: sine(hz, -12), seconds: 1 });
    const [a, b, c] = [await at(1000), await at(250), await at(4000)];
    console.log('A2', a, b, c);
    expect(Math.abs(a + 6)).toBeLessThanOrEqual(1);
    expect(Math.abs(b + 0.1)).toBeLessThanOrEqual(1);
    expect(Math.abs(c + 0.1)).toBeLessThanOrEqual(1);
  });

  test('A3 eq high shelf +6 dB at 8 kHz', async ({ page }) => {
    const p: [string, unknown][] = [['high', 6], ['high-freq', 8000]];
    const hi = await levelDb(page, { plugin: '/plugins/eq.js', params: p, input: sine(16000, -12), seconds: 1 });
    const lo = await levelDb(page, { plugin: '/plugins/eq.js', params: p, input: sine(1000, -12), seconds: 1 });
    console.log('A3', hi, lo);
    expect(hi).toBeGreaterThanOrEqual(5);
    expect(hi).toBeLessThanOrEqual(7);
    expect(Math.abs(lo)).toBeLessThanOrEqual(0.5);
  });

  test('A4 eq at defaults is transparent', async ({ page }) => {
    const input: BenchInput = { kind: 'noise', db: -12 };
    const out = await renderFx(page, { plugin: '/plugins/eq.js', input, seconds: 1 });
    const dry = await renderDry(page, input, 1);
    expect(nullDb(out.left, dry.left)).toBeLessThanOrEqual(-60);
    expect(nullDb(out.right, dry.right)).toBeLessThanOrEqual(-60);
  });

  test('A5 filter peaking +6 dB at 1 kHz', async ({ page }) => {
    const p: [string, unknown][] = [['type', 'peaking'], ['freq', 1000], ['gain', 6]];
    const a = await levelDb(page, { plugin: '/plugins/filter.js', params: p, input: sine(1000, -12), seconds: 1 });
    const b = await levelDb(page, { plugin: '/plugins/filter.js', params: p, input: sine(100, -12), seconds: 1 });
    console.log('A5', a, b);
    expect(Math.abs(a - 6)).toBeLessThanOrEqual(1);
    expect(Math.abs(b - 0.1)).toBeLessThanOrEqual(0.5);
  });

  test('A6 filter high-pass bypass is an identity', async ({ page }) => {
    const p: [string, unknown][] = [['type', 'highpass'], ['freq', 2000]];
    const on = await levelDb(page, { plugin: '/plugins/filter.js', params: p, input: sine(100, -12), seconds: 1 });
    const by = await levelDb(page, { plugin: '/plugins/filter.js', params: p, bypass: true, input: sine(100, -12), seconds: 1 });
    console.log('A6', on, by);
    expect(on).toBeLessThanOrEqual(-40);
    expect(Math.abs(by)).toBeLessThanOrEqual(0.5);
  });

  test('A7 limiter holds a +6 dBFS signal below the ceiling', async ({ page }) => {
    const n1 = Math.round(0.2 * SR);
    const n2 = Math.round(1.0 * SR);
    const x: number[] = [];
    for (let i = 0; i < n1 + n2; i += 1) {
      const a = i < n1 ? 0.05 : 2.0;
      x.push(a * Math.sin((2 * Math.PI * 220 * i) / SR));
    }
    const out = await renderFx(page, {
      plugin: '/plugins/limiter.js',
      params: [['ceiling', -3]],
      input: { kind: 'samples', left: x, right: x },
      seconds: 1.2,
    });
    const pk = Math.max(peakDb(out.left), peakDb(out.right));
    const tail = peakDb(out.left.slice(out.left.length - Math.round(0.5 * SR)));
    console.log('A7', pk, tail);
    expect(pk).toBeLessThanOrEqual(toDb(0.75));
    expect(tail).toBeGreaterThanOrEqual(-3.5);
    expect(tail).toBeLessThanOrEqual(-3.0);
  });

  test('A8 limiter passes a quiet signal unchanged, 5 ms late', async ({ page }) => {
    const input = sine(440, -10);
    const out = await renderFx(page, { plugin: '/plugins/limiter.js', params: [['ceiling', -3]], input, seconds: 1 });
    const dry = await renderDry(page, input, 1);
    const L = Math.round(0.005 * SR);
    const lag = bestLag(out.left, dry.left, L - 1, L + 1);
    console.log('A8', lag, toDb(rms(lastHalf(out.left)) / rms(lastHalf(dry.left))));
    expect(Math.abs(toDb(rms(lastHalf(out.left)) / rms(lastHalf(dry.left))))).toBeLessThanOrEqual(0.5);
    expect(lag.db).toBeLessThanOrEqual(-60);
  });

  test('A9 limiter release time sets the recovery', async ({ page }) => {
    const nb = Math.round(0.2 * SR);
    const total = Math.round(1.0 * SR);
    const x: number[] = [];
    for (let i = 0; i < total; i += 1) {
      const a = i < nb ? 2.0 : 0.1;
      x.push(a * Math.sin((2 * Math.PI * 440 * i) / SR));
    }
    const at = async (release: number) => {
      const out = await renderFx(page, {
        plugin: '/plugins/limiter.js',
        params: [['ceiling', -3], ['release', release]],
        input: { kind: 'samples', left: x, right: x },
        seconds: 1,
      });
      return toDb(rmsBetween(out.left, SR, 0.3, 0.32));
    };
    const fast = await at(0.02);
    const slow = await at(0.5);
    console.log('A9', fast, slow);
    expect(fast - slow).toBeGreaterThanOrEqual(3);
  });

  test('A10 gate closes on quiet noise', async ({ page }) => {
    const l = await levelDb(page, { plugin: '/plugins/gate.js', params: [['threshold', -40]], input: { kind: 'noise', db: -50 }, seconds: 1 });
    console.log('A10', l);
    expect(l).toBeLessThanOrEqual(-30);
  });

  test('A11 gate passes a tone above the threshold', async ({ page }) => {
    const l = await levelDb(page, { plugin: '/plugins/gate.js', params: [['threshold', -40]], input: sine(440, -20), seconds: 1 });
    console.log('A11', l);
    expect(Math.abs(l)).toBeLessThanOrEqual(1);
  });

  test('A12 gate range −12 is an expander', async ({ page }) => {
    const l = await levelDb(page, {
      plugin: '/plugins/gate.js',
      params: [['threshold', -40], ['range', -12]],
      input: { kind: 'noise', db: -50 },
      seconds: 1,
    });
    console.log('A12', l);
    expect(Math.abs(l + 12)).toBeLessThanOrEqual(1);
  });
});

// ── Part B — space ──────────────────────────────────────────────────────

type IrInfo = { len: number[]; finite: boolean; bound?: boolean; same?: boolean; lrDiffer?: boolean };

test.describe('Part B — space', () => {
  test('B1–B2 impulse-response generators', async ({ page }) => {
    await renderDry(page, { kind: 'click' }, 0.01); // opens the harness page
    const r = await page.evaluate(async () => {
      const m = await import('/plugins/lib/reverb-ir.js');
      const sr = 44100;
      const out: Record<string, IrInfo> = {};
      for (const t of m.TYPES) {
        const [L, R] = m.makeIR(t, {}, sr);
        const finite = [...L, ...R].every((x: number) => Number.isFinite(x));
        out[t] = { len: [L.length, R.length], finite };
      }
      const [L, R] = m.makeIR('classic', {}, sr);
      const N = L.length;
      let bound = true;
      for (let i = 0; i < N; i++) if (Math.abs(L[i]) > Math.pow(1 - i / N, 3) + 1e-7) bound = false;
      // a fresh generation (cache bypassed via a slightly different but equal-key param set)
      const again = m.makeIR('classic', { decay: 2.5 }, sr);
      out.classic = { ...out.classic, bound, same: again[0].every((x: number, i: number) => x === L[i]), lrDiffer: L.some((x: number, i: number) => x !== R[i]) };
      out.presets = { len: [], finite: true, ...(m.PRESETS as object) } as unknown as IrInfo;
      return out;
    });
    const sr = 44100;
    expect(r.classic.len).toEqual([2.5 * sr, 2.5 * sr]);
    expect(r.classic.bound).toBe(true);
    expect(r.classic.same).toBe(true);
    expect(r.classic.lrDiffer).toBe(true);
    const P = r.presets as unknown as Record<string, { decay: number }>;
    for (const t of ['room', 'hall', 'plate', 'spring']) {
      expect(r[t].finite).toBe(true);
      expect(r[t].len[0]).toBe(Math.round(1.2 * P[t].decay * sr));
    }
    for (const t of ['gated', 'reverse']) {
      expect(r[t].finite).toBe(true);
      expect(r[t].len[0]).toBe(Math.round((P[t].decay + 0.005) * sr));
    }
  });

  const wetOnly: [string, unknown][] = [['wet', 1], ['dry', 0]];

  test('B3–B4 hall decay sets RT60', async ({ page }) => {
    const r3 = await renderFx(page, { plugin: '/plugins/reverb.js', params: [...wetOnly, ['type', 'hall'], ['decay', 3]], input: { kind: 'click' }, seconds: 4.2 });
    const r4 = await renderFx(page, { plugin: '/plugins/reverb.js', params: [...wetOnly, ['type', 'hall'], ['decay', 0.5]], input: { kind: 'click' }, seconds: 1 });
    const t3 = schroederRt60(mixMono(r3.left, r3.right), SR);
    const t4 = schroederRt60(mixMono(r4.left, r4.right), SR);
    console.log('B3/B4', t3, t4);
    expect(t3).toBeGreaterThanOrEqual(2.4);
    expect(t3).toBeLessThanOrEqual(3.6);
    expect(t4).toBeGreaterThanOrEqual(0.4);
    expect(t4).toBeLessThanOrEqual(0.6);
  });

  test('B5 predelay', async ({ page }) => {
    const r = await renderFx(page, { plugin: '/plugins/reverb.js', params: [...wetOnly, ['predelay', 0.05]], input: { kind: 'click' }, seconds: 0.5 });
    const x = mixMono(r.left, r.right);
    const pk = Math.max(...x.map(Math.abs));
    const first = x.findIndex((v) => Math.abs(v) > 1e-4 * pk);
    console.log('B5', (first / SR) * 1000);
    expect(Math.abs(first / SR - 0.05)).toBeLessThanOrEqual(0.005);
  });

  test('B6 gated reverb stops', async ({ page }) => {
    const r = await renderFx(page, { plugin: '/plugins/reverb.js', params: [...wetOnly, ['type', 'gated'], ['decay', 0.3]], input: { kind: 'click' }, seconds: 0.6 });
    const x = mixMono(r.left, r.right);
    const d = toDb(rmsBetween(x, SR, 0.25, 0.3)) - toDb(rmsBetween(x, SR, 0.4, 0.45));
    console.log('B6', d);
    expect(d).toBeGreaterThanOrEqual(40);
  });

  test('B7 damping darkens the tail', async ({ page }) => {
    const at = async (damp: number) => {
      const r = await renderFx(page, { plugin: '/plugins/reverb.js', params: [...wetOnly, ['type', 'hall'], ['decay', 3], ['damp', damp]], input: { kind: 'click' }, seconds: 2.1 });
      const x = mixMono(r.left, r.right).slice(SR, 2 * SR);
      return bandEnergyDb(x, SR, 4000, 16000);
    };
    const lo = await at(0.1);
    const hi = await at(0.9);
    console.log('B7', lo, hi, lo - hi);
    expect(lo - hi).toBeGreaterThanOrEqual(6);
  });

  test('B8 size moves the energy later; reverse swells', async ({ page }) => {
    const halfTime = async (size: number) => {
      const r = await renderFx(page, { plugin: '/plugins/reverb.js', params: [...wetOnly, ['type', 'hall'], ['size', size]], input: { kind: 'click' }, seconds: 0.25 });
      const x = mixMono(r.left, r.right).slice(0, Math.round(0.2 * SR));
      const tot = x.reduce((a, v) => a + v * v, 0);
      let acc = 0;
      for (let i = 0; i < x.length; i++) {
        acc += x[i] * x[i];
        if (acc >= tot / 2) return i / SR;
      }
      return 0.2;
    };
    const s0 = await halfTime(0);
    const s1 = await halfTime(1);
    const rv = await renderFx(page, { plugin: '/plugins/reverb.js', params: [...wetOnly, ['type', 'reverse'], ['decay', 1]], input: { kind: 'click' }, seconds: 1.1 });
    const x = mixMono(rv.left, rv.right);
    const swell = toDb(rmsBetween(x, SR, 0.75, 1.0)) - toDb(rmsBetween(x, SR, 0, 0.25));
    console.log('B8', s0, s1, swell);
    expect(s1 - s0).toBeGreaterThanOrEqual(0.015);
    expect(swell).toBeGreaterThanOrEqual(15);
  });

  test('B9 (fx :reverb 0.4) keeps its level; same key does not swap', async ({ page }) => {
    const r = await renderFx(page, { plugin: '/plugins/reverb.js', params: [['wet', 0.4]], input: { kind: 'click' }, seconds: 3 });
    const lvl = toDb(rmsBetween(mixMono(r.left, r.right), SR, 0.05, 2.5));
    const back = await renderFx(page, {
      plugin: '/plugins/reverb.js',
      params: [['wet', 0.4], ['type', 'hall']],
      after: [['type', 'classic']],
      input: { kind: 'click' },
      seconds: 3,
    });
    console.log('B9', lvl, REVERB_CLASSIC_RMS_DB);
    expect(Math.abs(lvl - REVERB_CLASSIC_RMS_DB)).toBeLessThanOrEqual(1);
    expect(nullDb(back.left, r.left)).toBeLessThanOrEqual(-100);
    const swaps = await page.evaluate(async () => {
      const m = await import('/plugins/reverb.js');
      const p = m.default.clone();
      const ctx = new OfflineAudioContext(2, 4410, 44100);
      p.createNodes(ctx);
      const key0 = p._loadedKey;
      p.setParam('decay', 2.5); // the value classic already has
      p.setParam('type', 'classic');
      await p.ready();
      const same = p._loadedKey === key0 && p._swaps === 0;
      p.setParam('type', 'hall');
      await p.ready();
      return { same, after: p._swaps };
    });
    expect(swaps.same).toBe(true);
    expect(swaps.after).toBe(1);
  });

  /** Echo onsets: local peaks of |x| above thr, at least gap s apart. */
  function onsetsAt(x: number[], thr: number, gap = 0.05): number[] {
    const out: number[] = [];
    let last = -Infinity;
    for (let i = 0; i < x.length - 1; i++) {
      const a = Math.abs(x[i]);
      if (a >= thr && i - last > gap * SR) {
        // advance to the local peak of this burst
        let j = i;
        while (j + 1 < x.length && Math.abs(x[j + 1]) > Math.abs(x[j]) && j - i < 64) j++;
        out.push(j / SR);
        last = j;
      }
    }
    return out;
  }

  test('B10–B11 synced delay follows the tempo', async ({ page }) => {
    const at = async (cycleDur: number) => {
      const r = await renderFx(page, {
        plugin: '/plugins/delay.js',
        params: [['sync', 0.1875], ['wet', 1], ['feedback', 0.5]],
        tempo: { cycleDur, origin: 0 },
        input: { kind: 'click' },
        seconds: 2,
      });
      return onsetsAt(r.left, 0.05);
    };
    const a = await at(2);
    const b = await at(2.6667);
    console.log('B10/B11', a, b);
    // a[0] is the dry click; then echoes, each quieter
    for (let k = 1; k < 4; k++) expect(Math.abs(a[k] - a[k - 1] - 0.375)).toBeLessThanOrEqual(0.002);
    for (let k = 1; k < 3; k++) expect(Math.abs(b[k] - b[k - 1] - 0.5)).toBeLessThanOrEqual(0.002);
  });

  test('B12 ping-pong alternates sides', async ({ page }) => {
    const r = await renderFx(page, {
      plugin: '/plugins/delay.js',
      params: [['mode', 'ping-pong'], ['time', 0.2], ['feedback', 0.6], ['wet', 1]],
      input: { kind: 'click' },
      seconds: 1,
    });
    const win = (ch: number[], t: number) => rmsBetween(ch, SR, t - 0.005, t + 0.01);
    for (const [k, t] of [[1, 0.2], [2, 0.4], [3, 0.6], [4, 0.8]] as const) {
      const lr = toDb(win(r.left, t)) - toDb(win(r.right, t));
      console.log('B12', k, lr);
      if (k % 2 === 1) expect(lr).toBeGreaterThanOrEqual(20);
      else expect(lr).toBeLessThanOrEqual(-20);
    }
  });

  test('B13 multi-tap gives exactly the tap echoes', async ({ page }) => {
    const r = await renderFx(page, {
      plugin: '/plugins/delay.js',
      params: [['taps', [0.125, 0.375]], ['feedback', 0], ['wet', 1]],
      tempo: { cycleDur: 2, origin: 0 },
      input: { kind: 'click' },
      seconds: 1.5,
    });
    const on = onsetsAt(r.left, 0.05);
    console.log('B13', on);
    expect(on.length).toBe(3);
    expect(Math.abs(on[1] - 0.25)).toBeLessThanOrEqual(0.002);
    expect(Math.abs(on[2] - 0.75)).toBeLessThanOrEqual(0.002);
  });

  test('B14 highcut darkens later echoes', async ({ page }) => {
    const burst = { kind: 'noise', db: -6, seconds: 0.02 } as BenchInput;
    const ratio = async (extra: [string, unknown][]) => {
      const r = await renderFx(page, {
        plugin: '/plugins/delay.js',
        params: [['time', 0.2], ['feedback', 0.7], ['wet', 1], ...extra],
        input: burst,
        seconds: 0.5,
      });
      const e1 = bandEnergyDb(r.left.slice(Math.round(0.2 * SR), Math.round(0.23 * SR)), SR, 4000, 20000);
      const e2 = bandEnergyDb(r.left.slice(Math.round(0.4 * SR), Math.round(0.43 * SR)), SR, 4000, 20000);
      return e2 - e1;
    };
    const plain = await ratio([]);
    const cut = await ratio([['highcut', 1000]]);
    console.log('B14', plain, cut);
    expect(plain - cut).toBeGreaterThanOrEqual(15);
  });

  test('B15 mono vs stereo delay', async ({ page }) => {
    const at = async (mode: string) => {
      const r = await renderFx(page, {
        plugin: '/plugins/delay.js',
        params: [['mode', mode], ['time', 0.2], ['wet', 1]],
        input: { kind: 'clickLeft' },
        seconds: 0.4,
      });
      const l = rmsBetween(r.left, SR, 0.19, 0.22);
      const rr = rmsBetween(r.right, SR, 0.19, 0.22);
      return toDb(l) - toDb(rr);
    };
    const mono = await at('mono');
    const stereo = await at('stereo');
    console.log('B15', mono, stereo);
    expect(Math.abs(mono)).toBeLessThanOrEqual(1);
    expect(stereo).toBeGreaterThanOrEqual(20);
  });
});

// ── Part C — modulation & stereo ────────────────────────────────────────

test.describe('Part C — modulation & stereo', () => {
  test('C1 flanger comb notches', async ({ page }) => {
    const r = await renderFx(page, {
      plugin: '/plugins/flanger.js',
      params: [['depth', 0], ['delay', 0.002], ['wet', 1], ['feedback', 0]],
      input: { kind: 'noise', db: -12 },
      seconds: 2,
    });
    const x = r.left.slice(SR / 2);
    const band = (f: number) => bandEnergyDb(x, SR, f - 20, f + 20);
    const peaks = Math.min(band(500), band(1000));
    const notches = [band(250), band(750), band(1250)];
    console.log('C1', peaks, notches);
    for (const n of notches) expect(peaks - n).toBeGreaterThanOrEqual(10);
  });

  test('C2 flanger at feedback 0.95 dies away', async ({ page }) => {
    const r = await renderFx(page, {
      plugin: '/plugins/flanger.js',
      params: [['feedback', 0.95], ['depth', 0.7], ['wet', 1]],
      input: { kind: 'click' },
      seconds: 3,
    });
    expect(r.left.every(Number.isFinite)).toBe(true);
    const tail = toDb(rms(r.left.slice(r.left.length - SR / 2)));
    console.log('C2', tail);
    expect(tail).toBeLessThanOrEqual(-60);
  });

  test('C3 flanger sweeps', async ({ page }) => {
    const r = await renderFx(page, {
      plugin: '/plugins/flanger.js',
      params: [['depth', 0.7], ['rate', 0.25], ['wet', 1]],
      input: { kind: 'noise', db: -12 },
      seconds: 4,
    });
    const w = Math.round(0.1 * SR);
    const es: number[] = [];
    for (let i = 0; i + w <= r.left.length; i += w) es.push(bandEnergyDb(r.left.slice(i, i + w), SR, 230, 270));
    const span = Math.max(...es) - Math.min(...es);
    console.log('C3', span);
    expect(span).toBeGreaterThanOrEqual(6);
  });

  test('C4 vibrato swings ±50 cents five times a second', async ({ page }) => {
    const r = await renderFx(page, { plugin: '/plugins/vibrato.js', params: [['rate', 5], ['depth', 50]], input: sine(440, -6), seconds: 2 });
    const { t, hz } = instFreq(r.left, SR);
    const cents = hz.map((f) => 1200 * Math.log2(f / 440));
    const sel = cents.filter((_, i) => t[i] > 0.5);
    const hi = Math.max(...sel);
    const lo = Math.min(...sel);
    // period: time between successive upward crossings of 0 cents
    const ups: number[] = [];
    for (let i = 1; i < cents.length; i++) if (t[i] > 0.5 && cents[i - 1] < 0 && cents[i] >= 0) ups.push(t[i]);
    const periods = ups.slice(1).map((u, i) => u - ups[i]);
    const period = periods.reduce((a, b) => a + b, 0) / periods.length;
    console.log('C4', hi, lo, period);
    expect(hi).toBeGreaterThanOrEqual(42.5);
    expect(hi).toBeLessThanOrEqual(57.5);
    expect(lo).toBeLessThanOrEqual(-42.5);
    expect(lo).toBeGreaterThanOrEqual(-57.5);
    expect(Math.abs(period - 0.2)).toBeLessThanOrEqual(0.02);
  });

  const panWindows = (r: { left: number[]; right: number[] }, t: number) => {
    const l = rmsBetween(r.left, SR, t - 0.025, t + 0.025);
    const rr = rmsBetween(r.right, SR, t - 0.025, t + 0.025);
    return { lr: toDb(l) - toDb(rr), pow: 10 * Math.log10(l * l + rr * rr) };
  };

  test('C5 auto-pan sweeps at constant power', async ({ page }) => {
    const r = await renderFx(page, { plugin: '/plugins/auto-pan.js', params: [['rate', 1], ['depth', 1]], input: sine(440, -12), seconds: 3 });
    for (let k = 0; k < 2; k++) {
      const right = panWindows(r, 0.25 + k);
      const left = panWindows(r, 0.75 + k);
      console.log('C5', k, right.lr, left.lr);
      expect(-right.lr).toBeGreaterThanOrEqual(20);
      expect(left.lr).toBeGreaterThanOrEqual(20);
    }
    const pows: number[] = [];
    for (let t = 0.05; t < 2.95; t += 0.05) pows.push(panWindows(r, t).pow);
    const mean = pows.reduce((a, b) => a + b, 0) / pows.length;
    console.log('C5 power span', Math.min(...pows) - mean, Math.max(...pows) - mean);
    for (const p of pows) expect(Math.abs(p - mean)).toBeLessThanOrEqual(1.5);
  });

  test('C6 synced auto-pan locks to the cycle grid', async ({ page }) => {
    const r = await renderFx(page, {
      plugin: '/plugins/auto-pan.js',
      params: [['sync', 1]],
      tempo: { cycleDur: 2, origin: 0.25 },
      input: sine(440, -12),
      seconds: 4.5,
    });
    // the extreme within ±50 ms of each expected time
    const extreme = (t: number, side: 'L' | 'R') => {
      let best = { t: 0, v: -Infinity };
      for (let s = t - 0.06; s <= t + 0.06; s += 0.005) {
        const v = side === 'R' ? -panWindows(r, s).lr : panWindows(r, s).lr;
        if (v > best.v) best = { t: s, v };
      }
      return best;
    };
    const r1 = extreme(0.75, 'R');
    const l1 = extreme(1.75, 'L');
    const r2 = extreme(2.75, 'R');
    const centre = panWindows(r, 0.15).lr;
    console.log('C6', r1, l1, r2, centre);
    for (const [e, t] of [[r1, 0.75], [l1, 1.75], [r2, 2.75]] as const) {
      expect(Math.abs(e.t - t)).toBeLessThanOrEqual(0.05);
      expect(e.v).toBeGreaterThanOrEqual(20);
    }
    expect(Math.abs(centre)).toBeLessThanOrEqual(0.5);
  });

  test('C7 ring-mod gives sum and difference', async ({ page }) => {
    const r = await renderFx(page, { plugin: '/plugins/ring-mod.js', params: [['freq', 100], ['mix', 1]], input: sine(440, -12), seconds: 1 });
    const x = lastHalf(r.left);
    const g = (f: number) => toDb(goertzel(x, SR, f));
    console.log('C7', g(340), g(540), g(440));
    expect(Math.abs(g(340) - g(540))).toBeLessThanOrEqual(1);
    expect(Math.min(g(340), g(540)) - g(440)).toBeGreaterThanOrEqual(20);
  });

  test('C8–C10 width', async ({ page }) => {
    const input = sine(440, -12, { hzRight: 660 });
    const w0 = await renderFx(page, { plugin: '/plugins/width.js', params: [['width', 0]], input, seconds: 0.5 });
    const w1 = await renderFx(page, { plugin: '/plugins/width.js', params: [['width', 1]], input, seconds: 0.5 });
    const dry = await renderDry(page, input, 0.5);
    console.log('C8', nullDb(w0.left, w0.right), 'C9', nullDb(w1.left, dry.left), nullDb(w1.right, dry.right));
    expect(nullDb(w0.left, w0.right)).toBeLessThanOrEqual(-60);
    expect(nullDb(w1.left, dry.left)).toBeLessThanOrEqual(-60);
    expect(nullDb(w1.right, dry.right)).toBeLessThanOrEqual(-60);

    const w2 = await renderFx(page, { plugin: '/plugins/width.js', params: [['width', 2]], input, seconds: 0.5 });
    const side = (r: { left: number[]; right: number[] }) => rms(r.left.map((v, i) => v - r.right[i]));
    const mid = (r: { left: number[]; right: number[] }) => rms(r.left.map((v, i) => v + r.right[i]));
    const sd = toDb(side(w2) / side(dry));
    const md = toDb(mid(w2) / mid(dry));
    const h = await renderFx(page, { plugin: '/plugins/width.js', params: [['haas', 15]], input: { kind: 'click' }, seconds: 0.1 });
    const lag = h.right.findIndex((v) => Math.abs(v) > 0.25) - h.left.findIndex((v) => Math.abs(v) > 0.25);
    console.log('C10', sd, md, lag);
    expect(Math.abs(sd - 6)).toBeLessThanOrEqual(0.5);
    expect(Math.abs(md)).toBeLessThanOrEqual(0.5);
    expect(Math.abs(lag - Math.round(0.015 * SR))).toBeLessThanOrEqual(1);
  });
});

// ── X1 — every new effect is transparent at defaults and when bypassed ───

const NEW_EFFECTS: [string, [string, unknown][]][] = [
  ['eq', [['low', 9], ['mid', -6], ['high', 4]]],
  ['limiter', [['ceiling', -12]]],
  ['gate', [['threshold', -10]]],
  ['flanger', [['wet', 0.8], ['feedback', 0.6]]],
  ['vibrato', [['depth', 60]]],
  ['auto-pan', [['rate', 3]]],
  ['ring-mod', [['mix', 1], ['freq', 220]]],
  ['width', [['width', 1.8], ['haas', 12]]],
];

test.describe('X1 — transparent when inactive or bypassed', () => {
  for (const [name, params] of NEW_EFFECTS) {
    test(`X1 ${name}`, async ({ page }) => {
      const input: BenchInput = { kind: 'noise', db: -6 };
      const dry = await renderDry(page, input, 0.5);
      const plain = await renderFx(page, { plugin: `/plugins/${name}.js`, input, seconds: 0.5 });
      const by = await renderFx(page, { plugin: `/plugins/${name}.js`, params, bypass: true, input, seconds: 0.5 });
      // the offline export replays getParams() of every chain entry into a fresh
      // instance: an untouched effect must stay transparent after that replay
      const defaults = await page.evaluate(async (u) => {
        const p = (await import(u)).default.clone();
        p.createNodes(new OfflineAudioContext(2, 128, 44100));
        return Object.entries(p.getParams()).filter(([, v]) => v !== null && v !== undefined);
      }, `/plugins/${name}.js`);
      const replay = await renderFx(page, { plugin: `/plugins/${name}.js`, params: defaults as [string, unknown][], input, seconds: 0.5 });
      const n = [
        nullDb(plain.left, dry.left), nullDb(plain.right, dry.right),
        nullDb(by.left, dry.left), nullDb(by.right, dry.right),
        nullDb(replay.left, dry.left), nullDb(replay.right, dry.right),
      ];
      console.log('X1', name, n);
      for (const v of n) expect(v).toBeLessThanOrEqual(-60);
    });
  }
});

// ── App-level tests (real app, real (export …)) ─────────────────────────

test.describe('App — fx built-in, export, per track', () => {
  test.beforeEach(async ({ page }) => {
    await loadApp(page);
  });

  const output = (page: Page) => page.locator('#output');

  async function evalCode(page: Page, code: string) {
    await page.evaluate((t) => {
      const v = window.__REPULSE_EDITOR__;
      v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
      v.focus();
    }, code);
    await page.keyboard.press('Alt+Enter');
  }

  test('B16 unknown parameter warning names the real parameters', async ({ page }) => {
    // wait until the auto-loaded reverb plugin is in the chain
    await expect
      .poll(() => page.evaluate(() => window.__REPULSE_APP__.audioBackend()), { timeout: 15_000 })
      .toBe('audioworklet+wasm');
    await page.waitForTimeout(500);
    await evalCode(page, '(fx :reverb :room 0.8)');
    await expect(output(page)).toHaveText(
      /Warning: fx :reverb has no parameter :room — parameters: wet, dry, type, decay, size, damp, predelay$/,
    );
    await evalCode(page, '(fx :eq 3)');
    await expect(output(page)).toHaveText(
      /Warning: fx :eq takes no positional value — parameters: low, low-freq, mid, mid-freq, mid-q, mid2, mid2-freq, mid2-q, high, high-freq$/,
    );
    await evalCode(page, '(fx :reverb :type :cathedral :wet 0.4)');
    await expect(output(page)).toHaveText(
      /Warning: fx :reverb :type has no value :cathedral — values: classic, room, hall, plate, spring, gated, reverse$/,
    );
  });

  const X2_FORMS = [
    '(fx :eq :low 6 :high -3)',
    '(fx :limiter -3)',
    '(fx :gate -40)',
    '(fx :flanger 0.6 :feedback 0.5)',
    '(fx :vibrato 30)',
    '(fx :auto-pan :sync 1)',
    '(fx :ring-mod 0.5 :freq 220)',
    '(fx :width 1.5 :haas 10)',
    '(fx :reverb :type :hall :wet 0.3)',
    '(fx :delay :sync 1/8 :wet 0.4)',
  ];

  test('X2 every new effect exports offline', async ({ page }) => {
    test.setTimeout(180_000);
    for (const form of X2_FORMS) {
      const wav = await exportBuffer(page, `(bpm 120)\n(track :t (seq :a4))\n(master ${form})\n(export 1)`);
      expect(rms(monoOf(wav)), form).toBeGreaterThan(1e-4);
      await expect(output(page), form).toHaveText('exported all tracks — 1 cycles (offline)');
    }
  });

  const X3_FORMS = [
    '(fx :eq :low 9)',
    '(fx :limiter -12)',
    '(fx :gate -20)',
    '(fx :flanger 0.8)',
    '(fx :vibrato 50)',
    '(fx :auto-pan :rate 2)',
    '(fx :ring-mod 1 :freq 100)',
    '(fx :width 0 :haas 20)',
  ];

  test('X3 a per-track effect leaves the other tracks dry', async ({ page }) => {
    test.setTimeout(180_000);
    const other = '(track :b (seq :_ :c5))';
    const ref = await exportBuffer(page, `(bpm 120)\n${other}\n(export 1)`);
    for (const form of X3_FORMS) {
      // track :a is silent, so any change of the export comes from the effect
      // reaching track :b
      const wav = await exportBuffer(page, `(bpm 120)\n(track :a (->> (seq :_) ${form}))\n${other}\n(export 1)`);
      const n = Math.max(nullDb(wav.left, ref.left), nullDb(wav.right, ref.right));
      console.log('X3', form, n);
      expect(n, form).toBeLessThanOrEqual(-60);
    }
    // and the effect does act on its own track
    const dry = await exportBuffer(page, '(bpm 120)\n(track :a (seq :a4))\n(export 1)');
    const wet = await exportBuffer(page, '(bpm 120)\n(track :a (->> (seq :a4) (fx :ring-mod 1 :freq 100)))\n(export 1)');
    expect(nullDb(wet.left, dry.left)).toBeGreaterThan(-20);
  });

  test('X4 synced delay at 90 BPM echoes 500 ms later', async ({ page }) => {
    const wav = await exportBuffer(page, '(bpm 90)\n(track :hat (seq :hh :_ :_ :_))\n(master (fx :delay :sync 3/16 :wet 0.5))\n(export 1)');
    const r = wav.sampleRate;
    const x = monoOf(wav);
    const n = Math.round(0.05 * r);
    let best = { lag: 0, c: -Infinity };
    for (let l = Math.round(0.4 * r); l <= Math.round(0.6 * r); l += 1) {
      let c = 0;
      for (let i = 0; i < n; i += 1) c += x[i] * x[i + l];
      if (c > best.c) best = { lag: l, c };
    }
    console.log('X4 lag', best.lag / r);
    expect(Math.abs(best.lag - Math.round(0.5 * r))).toBeLessThanOrEqual(2);
  });

  test('Q2 a hot synth mix through the master limiter stays below the ceiling', async ({ page }) => {
    // sixteen in-phase sines at amp 1 sum to about +6 dBFS (four reach −6)
    const mix = Array.from({ length: 16 }, (_, i) => `:s${i}`)
      .map((t) => `(track ${t} (->> (seq :c3 :c3) (synth :sine) (amp 1)))`)
      .join('\n');
    // the default master compressor would squash the sum: turn it off
    const plain = await exportBuffer(page, `(bpm 120)\n${mix}\n(master (fx :off :compressor))\n(export 1)`);
    const limited = await exportBuffer(
      page,
      `(bpm 120)\n${mix}\n(master (fx :off :compressor) (fx :limiter -1))\n(export 1)`,
    );
    const pPlain = Math.max(peakDb(plain.left), peakDb(plain.right));
    const pLim = Math.max(peakDb(limited.left), peakDb(limited.right));
    // longest run of equal samples near the ceiling (a flat top = clipping)
    let run = 1;
    let longest = 1;
    for (const ch of [limited.left, limited.right]) {
      for (let i = 1; i < ch.length; i += 1) {
        if (Math.abs(ch[i]) > 0.85 && ch[i] === ch[i - 1]) longest = Math.max(longest, ++run);
        else run = 1;
      }
    }
    console.log('Q2', pPlain, pLim, longest);
    expect(pPlain).toBeGreaterThanOrEqual(-0.01); // the plain mix reaches full scale
    expect(pLim).toBeLessThanOrEqual(-1 + 0.01);
    expect(longest).toBeLessThanOrEqual(3);
  });
});
