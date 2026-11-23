import type { Page } from '@playwright/test';

// Phase FX1 — test bench: one effect plugin, a known input, rendered in an
// OfflineAudioContext inside the page, without the app. The sequence is the one
// the offline export runs: clone → init(host) → createNodes → setTempo →
// setParam … → bypass → ready() → startRendering().

export type BenchInput =
  | { kind: 'sine'; hz: number; db: number; hzRight?: number; seconds?: number }
  | { kind: 'click' } // one sample of 1.0 at t = 0, both channels
  | { kind: 'clickLeft' } // one sample of 1.0 at t = 0, left channel only
  | { kind: 'noise'; db: number; seed?: number; seconds?: number } // uniform white noise, peak level
  | { kind: 'samples'; left: number[]; right: number[] };

export type BenchOptions = {
  plugin: string | null; // '/plugins/eq.js'; null renders the input unprocessed
  params?: [string, unknown][]; // already in setParam form
  input: BenchInput;
  seconds: number;
  sampleRate?: number; // default 44100
  tempo?: { cycleDur: number; origin: number };
  bypass?: boolean;
  /** steps after the first setParam round, before ready() */
  after?: [string, unknown][];
};

export type BenchResult = { left: number[]; right: number[]; sampleRate: number };

let opened = new WeakSet<Page>();

async function ensureHarness(page: Page) {
  if (opened.has(page)) return;
  await page.goto('/test-harness.html');
  opened.add(page);
}

/** Render the bench input through one plugin. */
export async function renderFx(page: Page, o: BenchOptions): Promise<BenchResult> {
  await ensureHarness(page);
  return page.evaluate(async (o) => {
    const sr = o.sampleRate ?? 44100;
    const frames = Math.round(o.seconds * sr);
    const ctx = new OfflineAudioContext(2, frames, sr);
    let p: any = null;
    let nodes: any;
    if (o.plugin === null) {
      const g = ctx.createGain();
      nodes = { inputNode: g, outputNode: g };
    } else {
      const mod = await import(o.plugin);
      const base = mod.default;
      p = typeof base.clone === 'function' ? base.clone() : Object.assign(Object.create(Object.getPrototypeOf(base)), base);
      const host = { audioCtx: ctx, sampleRate: sr, masterGain: null, analyser: null, version: '1.0.0', registerLisp() {} };
      if (typeof p.init === 'function') p.init(host);
      nodes = p.createNodes(ctx);
      if (o.tempo && typeof p.setTempo === 'function') p.setTempo(o.tempo.cycleDur, o.tempo.origin);
      for (const [k, v] of o.params ?? []) p.setParam(k, v);
      for (const [k, v] of o.after ?? []) p.setParam(k, v);
      if (o.bypass) p.bypass(true);
      if (typeof p.ready === 'function') await p.ready();
    }

    // input buffer
    const inp = o.input;
    const buf = ctx.createBuffer(2, frames, sr);
    const L = buf.getChannelData(0);
    const R = buf.getChannelData(1);
    if (inp.kind === 'sine') {
      const a = Math.pow(10, inp.db / 20);
      const n = Math.min(frames, Math.round((inp.seconds ?? o.seconds) * sr));
      const hr = inp.hzRight ?? inp.hz;
      for (let i = 0; i < n; i++) {
        L[i] = a * Math.sin((2 * Math.PI * inp.hz * i) / sr);
        R[i] = a * Math.sin((2 * Math.PI * hr * i) / sr);
      }
    } else if (inp.kind === 'click') {
      L[0] = 1;
      R[0] = 1;
    } else if (inp.kind === 'clickLeft') {
      L[0] = 1;
    } else if (inp.kind === 'noise') {
      const a = Math.pow(10, inp.db / 20);
      let s = (inp.seed ?? 12345) >>> 0;
      const rnd = () => {
        s = (s + 0x6d2b79f5) >>> 0;
        let t = s;
        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
      };
      const n = Math.min(frames, Math.round((inp.seconds ?? o.seconds) * sr));
      for (let i = 0; i < n; i++) {
        L[i] = a * (rnd() * 2 - 1);
        R[i] = a * (rnd() * 2 - 1);
      }
    } else {
      L.set(inp.left.slice(0, frames));
      R.set(inp.right.slice(0, frames));
    }
    const src = ctx.createBufferSource();
    src.buffer = buf;
    src.connect(nodes.inputNode);
    nodes.outputNode.connect(ctx.destination);
    src.start(0);
    const out = await ctx.startRendering();
    if (p && typeof p.destroy === 'function') p.destroy();
    return { left: Array.from(out.getChannelData(0)), right: Array.from(out.getChannelData(1)), sampleRate: sr };
  }, o);
}

/** The bench input itself (a render with no plugin), for level comparisons. */
export async function renderDry(page: Page, input: BenchInput, seconds: number, sampleRate = 44100) {
  return renderFx(page, { plugin: null, input, seconds, sampleRate });
}

/** Evaluate JS module code in the harness page (pure plugin libs). */
export async function inPage<T>(page: Page, fn: () => Promise<T> | T): Promise<T> {
  await ensureHarness(page);
  return page.evaluate(fn);
}

export function resetBench() {
  opened = new WeakSet<Page>();
}
