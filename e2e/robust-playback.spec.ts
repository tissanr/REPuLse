import { expect, type Page, test } from '@playwright/test';

// Phase QA1 — one broken track never stops the others. A track whose query
// throws is skipped for that cycle and reported once; a track that fails at
// cycle 0 is an eval error and nothing changes; count limits fail fast.
// Real app page (scheduler counters via __REPULSE_APP__.onsetCount).

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_APP__: {
      tracks: () => string[];
      cycle: () => number;
      onsetCount: (track: string) => number;
      evaluate: (code: string) => void;
    };
  }
}

async function load(page: Page) {
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
}

async function evalText(page: Page, text: string) {
  await page.evaluate((t) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
    v.focus();
  }, text);
  await page.keyboard.press('Alt+Enter');
}

const output = (page: Page) => page.locator('#output');

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    try { localStorage.clear(); } catch {}
  });
  await page.setViewportSize({ width: 1280, height: 800 });
  await load(page);
  // Twice the default tempo so five cycles pass in ~5 s.
  await page.evaluate(() => window.__REPULSE_APP__.evaluate('(bpm 240)'));
});

test('a track failing in some cycles is skipped there; the others keep playing', async ({ page }) => {
  await evalText(page,
    '(track :kick (seq :bd :bd :bd :bd))\n(track :hats (fast "<1 1 x>" (seq :hh)))');
  await expect.poll(() => page.evaluate(() => window.__REPULSE_APP__.tracks()), { timeout: 15_000 })
    .toEqual(['hats', 'kick']);

  const start = await page.evaluate(() => ({
    cycle: window.__REPULSE_APP__.cycle(),
    kick: window.__REPULSE_APP__.onsetCount('kick'),
  }));
  await expect.poll(() => page.evaluate(() => window.__REPULSE_APP__.cycle()), { timeout: 20_000 })
    .toBeGreaterThanOrEqual(start.cycle + 5);

  const kick = await page.evaluate(() => window.__REPULSE_APP__.onsetCount('kick'));
  const hats = await page.evaluate(() => window.__REPULSE_APP__.onsetCount('hats'));
  expect(kick - start.kick).toBeGreaterThanOrEqual(16);
  expect(hats).toBeGreaterThan(0);

  const text = (await output(page).textContent()) ?? '';
  const msg = 'Error in track :hats: Expected a number, got :x';
  expect(text.split(msg).length - 1).toBe(1);
});

test('a track failing at cycle 0 is an eval error and changes nothing', async ({ page }) => {
  await evalText(page, '(track :kick (seq :bd :bd :bd :bd))');
  await expect.poll(() => page.evaluate(() => window.__REPULSE_APP__.tracks()), { timeout: 15_000 })
    .toEqual(['kick']);

  await evalText(page,
    '(track :kick (seq :bd :bd :bd :bd))\n(track :bad (fast (seq 1 :x) (seq :bd)))');
  await expect(output(page)).toHaveText('Error: track :bad fails at cycle 0: Expected a number, got :x');
  expect(await page.evaluate(() => window.__REPULSE_APP__.tracks())).toEqual(['kick']);
});

test('a pattern result failing at cycle 0 underlines the failing symbol', async ({ page }) => {
  await evalText(page, '(fmap (fn [v] (nope v)) (seq 1))');
  await expect(output(page)).toHaveText(/^Error: pattern fails at cycle 0: Undefined symbol: nope/);
  await expect(page.locator('#editor-container .cm-lintRange-error')).toHaveText('nope');
});

test('count limits fail at eval within 100 ms', async ({ page }) => {
  const cases: [string, string][] = [
    ['(run 1025)', 'run: n = 1025 exceeds the limit of 1024'],
    ['(euclidean 3 100000000 :bd)', 'euclidean: n = 100000000 exceeds the limit of 1024'],
    ['(apply-n 65 rev (seq 1 2))', 'apply-n: n = 65 exceeds the limit of 64'],
    ['(echo 100 1/8 0.5 (seq :bd))', 'echo: times = 100 exceeds the limit of 64'],
    ['(~ "bd!100000000")',
      '! count = 100000000 exceeds the limit of 1024 in mini-notation at position 2'],
  ];
  for (const [code, msg] of cases) {
    const ms = await page.evaluate((c) => {
      const t0 = performance.now();
      window.__REPULSE_APP__.evaluate(c);
      return performance.now() - t0;
    }, code);
    await expect(output(page)).toHaveText(`Error: ${msg}`);
    expect(ms, code).toBeLessThan(100);
  }
});
