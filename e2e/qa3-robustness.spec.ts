import { expect, type Page, test } from '@playwright/test';

// Phase QA3 — app-layer robustness, checked on the real app page through the
// __REPULSE_APP__ test handles: tempo anchor (no gap / no burst), the
// timeline's audible cycle and query cache, throttled autosave, master FX
// surviving failed and one-off evaluations, per-event JS chain cleanup, the
// sample negative cache and the snippet preview allow-list.

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_APP__: any;
    __QA3_WRITES__?: { key: string; t: number }[];
    __QA3_OUTPUTS__?: string[];
  }
}

async function load(page: Page) {
  await page.addInitScript(() => {
    try { localStorage.clear(); } catch {}
  });
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
}

const app = <T>(page: Page, fn: string, ...args: unknown[]) =>
  page.evaluate(([f, a]) => window.__REPULSE_APP__[f as string](...(a as unknown[])), [fn, args] as const) as Promise<T>;

const evaluate = (page: Page, code: string) =>
  page.evaluate((c) => window.__REPULSE_APP__.evaluate(c), code);

async function setCode(page: Page, code: string) {
  await page.evaluate((c) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: c } });
  }, code);
}

/** Put code in the editor and press Alt+Enter (whole-project evaluation). */
async function altEnter(page: Page, code: string) {
  await setCode(page, code);
  await page.locator('.cm-content').first().focus();
  await page.keyboard.press('Alt+Enter');
}

async function waitPlaying(page: Page) {
  await expect.poll(() => app<boolean>(page, 'playing'), { timeout: 15_000 }).toBe(true);
}

test.afterEach(async ({ page }) => {
  await evaluate(page, '(stop)').catch(() => {});
});

test('A1 tempo: 480 → 120 BPM continues at the next bar, no minute-long gap', async ({ page }) => {
  await load(page);
  await altEnter(page, '(bpm 480)\n(track :a (seq :bd :sd))');
  await waitPlaying(page);
  await page.waitForTimeout(3000);
  await evaluate(page, '(bpm 120)');
  const gap = await page.evaluate(
    () => window.__REPULSE_APP__.nextCycleTime() - window.__REPULSE_APP__.audioTime());
  expect(gap).toBeGreaterThanOrEqual(0);
  expect(gap).toBeLessThanOrEqual(0.5 + 0.2);
  const c0 = await app<number>(page, 'cycle');
  await page.waitForTimeout(1000);
  const c1 = await app<number>(page, 'cycle');
  expect(c1 - c0).toBeLessThanOrEqual(2);
  expect(c1 - c0).toBeGreaterThanOrEqual(0);
});

test('A1 tempo: 120 → 480 BPM does not schedule a burst of past bars', async ({ page }) => {
  await load(page);
  await altEnter(page, '(bpm 120)\n(track :a (seq :bd :sd))');
  await waitPlaying(page);
  await page.waitForTimeout(6000);
  await evaluate(page, '(bpm 480)');
  const c0 = await app<number>(page, 'cycle');
  await page.waitForTimeout(300);
  const c1 = await app<number>(page, 'cycle');
  expect(c1 - c0).toBeLessThanOrEqual(2);
});

test('A2 timeline: draws the audible bar and queries once per bar', async ({ page }) => {
  await load(page);
  await altEnter(page, '(bpm 240)\n(track :a (seq :bd :sd))\n(track :b (seq :hh :hh :hh))');
  await waitPlaying(page);
  // a fresh AudioContext starts near 0 s: let the first bars pass
  await expect.poll(() => app<number>(page, 'audibleCycle'), { timeout: 10_000 }).toBeGreaterThanOrEqual(2);
  for (let i = 0; i < 5; i++) {
    const s = await page.evaluate(() => ({
      tl: window.__REPULSE_APP__.timelineCycle(),
      audible: window.__REPULSE_APP__.audibleCycle(),
      cycle: window.__REPULSE_APP__.cycle(),
    }));
    expect([s.audible, s.audible - 1]).toContain(s.tl);
    expect(s.tl).toBeLessThan(s.cycle);
    await page.waitForTimeout(300);
  }

  // 2 tracks for 3 s at 240 BPM (1 s per bar): ≤ 2 × 4 + 2 queries
  const q0 = await app<number>(page, 'timelineQueries');
  await page.waitForTimeout(3000);
  const q1 = await app<number>(page, 'timelineQueries');
  expect(q1 - q0).toBeLessThanOrEqual(2 * 4 + 2);

  // stopped: no queries at all
  await evaluate(page, '(stop)');
  await expect.poll(() => app<boolean>(page, 'playing')).toBe(false);
  await page.waitForTimeout(200);
  const s0 = await app<number>(page, 'timelineQueries');
  await page.waitForTimeout(1000);
  expect(await app<number>(page, 'timelineQueries')).toBe(s0);
});

test('A3 autosave: at most one session write while playing without edits', async ({ page }) => {
  await page.addInitScript(() => {
    const writes: { key: string; t: number }[] = [];
    window.__QA3_WRITES__ = writes;
    const orig = Storage.prototype.setItem;
    Storage.prototype.setItem = function (this: Storage, k: string, v: string) {
      writes.push({ key: k, t: performance.now() });
      return orig.call(this, k, v);
    };
  });
  await load(page);
  await altEnter(page, '(bpm 480)\n(track :a (seq :bd :sd))');
  await waitPlaying(page);
  const t0 = await page.evaluate(() => performance.now());
  await page.waitForTimeout(1500 + 4000);
  const writes = await page.evaluate(
    (start) => window.__QA3_WRITES__!.filter((w) => w.key === 'repulse-session' && w.t >= start + 1500).length,
    t0);
  expect(writes).toBeLessThanOrEqual(1);
});

test('A3 autosave: a full storage is reported once', async ({ page }) => {
  const MSG = 'session not saved — browser storage is full. Export the project to keep your work.';
  await page.addInitScript(() => {
    const orig = Storage.prototype.setItem;
    Storage.prototype.setItem = function (this: Storage, k: string, v: string) {
      if (k === 'repulse-session') throw new DOMException('quota', 'QuotaExceededError');
      return orig.call(this, k, v);
    };
  });
  await load(page);
  await page.evaluate(() => {
    const seen: string[] = [];
    window.__QA3_OUTPUTS__ = seen;
    const out = document.getElementById('output')!;
    new MutationObserver(() => seen.push(out.textContent ?? '')).observe(out, { childList: true, characterData: true, subtree: true });
  });
  const edit = (s: string) => page.evaluate((t) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: v.state.doc.length, insert: t } });
  }, s);
  const shown = () => page.evaluate((m) => window.__QA3_OUTPUTS__!.filter((x) => x === m).length, MSG);

  await edit('\n; one');
  await expect(page.locator('#output')).toHaveText(MSG, { timeout: 5000 });
  await edit('\n; two');
  await page.waitForTimeout(1600);
  expect(await shown()).toBe(1);

  await evaluate(page, '(+ 1 2)');
  await expect(page.locator('#output')).not.toHaveText(MSG);
  await edit('\n; three');
  await page.waitForTimeout(1600);
  await expect(page.locator('#output')).not.toHaveText(MSG);
  expect(await shown()).toBe(1);
});

test('A4 FX: a failed eval or (tap!) leaves the master effects alone', async ({ page }) => {
  await load(page);
  // effect plugins load asynchronously after start-up
  await expect
    .poll(async () => {
      await altEnter(page, '(fx :reverb :wet 0.42)\n(track :a (seq :bd :sd))');
      return app<number | undefined>(page, 'fxParam', 'reverb', 'wet');
    }, { timeout: 15_000 })
    .toBeCloseTo(0.42, 5);
  await evaluate(page, '(tap!)');
  expect(await app<number>(page, 'fxParam', 'reverb', 'wet')).toBeCloseTo(0.42, 5);
  await evaluate(page, '(seq :bd');
  expect(await app<number>(page, 'fxParam', 'reverb', 'wet')).toBeCloseTo(0.42, 5);
});

test('A5 cleanup: per-event JS chains are disconnected by (stop)', async ({ page }) => {
  // JS synthesis path (no worklet): every :bd with room builds a send chain
  await page.route('**/worklet.js', (r) => r.abort());
  await page.route('**/repulse_audio_bg.wasm', (r) => r.abort());
  await load(page);
  await altEnter(page, '(track :a (->> (seq :bd :sd :bd :sd) (room 0.3)))');
  await waitPlaying(page);
  await page.waitForTimeout(2000);
  expect(await app<number>(page, 'pendingDisconnects')).toBeGreaterThan(0);
  await evaluate(page, '(stop)');
  expect(await app<number>(page, 'pendingDisconnects')).toBe(0);
});

test('A6 samples: a failing sample is fetched once and reported once', async ({ page }) => {
  let hits = 0;
  await page.route('https://raw.githubusercontent.com/**', (r) => {
    const body = r.request().url().endsWith('/Dirt-Samples.json')
      ? { _base: 'https://samples.invalid/', qa: ['qa.wav'] }
      : {};
    return r.fulfill({ contentType: 'application/json', body: JSON.stringify(body),
                       headers: { 'Access-Control-Allow-Origin': '*' } });
  });
  await page.route('https://samples.invalid/**', (r) => {
    if (r.request().url().endsWith('/qa.wav')) hits++;
    return r.fulfill({ status: 404, body: 'not found', headers: { 'Access-Control-Allow-Origin': '*' } });
  });
  await load(page);
  await page.waitForTimeout(500); // mocked manifests settle
  await altEnter(page, '(bpm 240)\n(track :a (seq :qa :qa))');
  await waitPlaying(page);
  const c0 = await app<number>(page, 'cycle');
  await expect.poll(() => app<number>(page, 'cycle'), { timeout: 10_000 }).toBeGreaterThanOrEqual(c0 + 3);
  expect(hits).toBe(1);
  const out = (await page.locator('#output').textContent()) ?? '';
  expect(out.startsWith('sample :qa failed to load (HTTP 404')).toBe(true);
  expect(out.endsWith('— it stays silent until reload')).toBe(true);
});

test('B preview: denied built-ins fail the preview, the library previews cleanly', async ({ page }) => {
  const real = await (await page.request.get('/snippets/library.json')).json();
  const evil = {
    id: 'evil-restore', title: 'Evil Restore', author: 'mallory', tags: ['evil'],
    description: 'tries to restore a snapshot', code: '(do (restore! 1) (track :x (seq :bd)))',
  };
  await page.route('**/snippets/library.json', (r) =>
    r.fulfill({ contentType: 'application/json',
                body: JSON.stringify({ ...real, snippets: [evil, ...real.snippets] }) }));
  await load(page);
  await page.locator('#snippet-toggle-btn').click();
  await expect(page.locator('.snippet-card')).toHaveCount(real.snippets.length + 1);

  const card = page.locator('.snippet-card', { hasText: 'Evil Restore' });
  await card.locator('.snippet-preview-btn').click();
  await expect(card).toHaveClass(/snippet-card--error/);
  await expect(card).toHaveAttribute('title', 'restore! is not available in snippet previews');

  expect(real.snippets.length).toBe(24);
  for (const s of real.snippets) {
    const c = page.locator(`.snippet-card:has(.snippet-preview-btn[data-id="${s.id}"])`);
    await c.locator('.snippet-preview-btn').click();
    await expect(c).not.toHaveAttribute('title', /not available in snippet previews/);
  }
  await page.locator('.snippet-card--playing .snippet-preview-btn').click().catch(() => {});
});
