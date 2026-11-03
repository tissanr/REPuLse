import { expect, type Page, test } from '@playwright/test';

// Phase WS2 — (master …) form, outline group and inspector block against the
// real app page.

const M = `(def kick (seq :bd :_ :bd :_))
(track :drums (->> kick (amp 0.8) (fx :reverb 0.5)))
(song
  (section :a 4 [:drums]))
(master
  (fx :delay :time 0.375 :feedback 0.4)
  (fx :reverb 0.3))`;

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_APP__: {
      cycleDur: () => number;
      muted: () => string[];
      selection: () => string[];
      trackParam: (track: string, param: string) => number | string | null;
      fxParam: (effect: string, param: string) => number | string | null;
    };
  }
}

async function setText(page: Page, text: string) {
  await page.evaluate((t) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
  }, text);
}

async function load(page: Page, text: string = M) {
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
  await setText(page, text);
  await expect(page.locator('#outline-panel .ol-name', { hasText: /^kick$/ })).toBeVisible();
}

const doc = (page: Page) => page.evaluate(() => window.__REPULSE_EDITOR__.state.doc.toString());
const row = (page: Page, id: string) => page.locator(`#outline-panel .ol-row[data-id="${id}"]`);
const inspector = (page: Page) => page.locator('#inspector');
const output = (page: Page) => page.locator('#output');

async function selectedLines(page: Page): Promise<number[]> {
  return page.evaluate(() =>
    [...document.querySelectorAll('#editor-container .cm-line')]
      .map((l, i) => (l.classList.contains('ws-sel-line') ? i + 1 : 0))
      .filter((n) => n > 0),
  );
}

async function focusEditor(page: Page) {
  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
}

async function evalBuffer(page: Page) {
  await focusEditor(page);
  await page.keyboard.press('Alt+Enter');
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    try { localStorage.clear(); } catch {}
  });
  await page.setViewportSize({ width: 1280, height: 800 });
});

test('outline lists the master group last, after a divider', async ({ page }) => {
  await load(page);
  const ids = await page.evaluate(() =>
    [...document.querySelectorAll('#outline-panel .ol-row[data-id]')].map((r) => (r as HTMLElement).dataset.id),
  );
  expect(ids.slice(-4)).toEqual(['section:0', 'master', 'master:0', 'master:1']);
  const prev = await row(page, 'master').evaluate((el) => el.previousElementSibling?.className);
  expect(prev).toContain('ol-group-divider');
  const m = row(page, 'master');
  await expect(m).toContainText('master');
  await expect(m).toContainText('fx');
  await expect(m).toContainText('2');
  await expect(m).toContainText('L5');
  await expect(row(page, 'master:0')).toContainText(':delay');
  await expect(row(page, 'master:0')).toContainText('L6');
  await expect(row(page, 'master:1')).toContainText(':reverb');
  await expect(row(page, 'master:1')).toContainText('L7');
});

test('selecting master opens all global fx; a child opens one', async ({ page }) => {
  await load(page);
  await evalBuffer(page);
  await expect(output(page)).toContainText('song: 1 sections', { timeout: 15_000 });
  await row(page, 'master').click();
  await expect(inspector(page)).toBeVisible();
  const header = page.locator('#inspector .ins-header');
  await expect(header).toContainText('master');
  await expect(header).toContainText('fx');
  await expect(header).toContainText('↗ L5');
  await expect(page.locator('#inspector .ins-slider[data-key="fx|delay|time"]')).toHaveCount(1);
  await expect(page.locator('#inspector .ins-slider[data-key="fx|delay|feedback"]')).toHaveCount(1);
  await expect(page.locator('#inspector .ins-slider[data-key="fx|reverb|wet"]')).toHaveCount(1);
  await expect(page.locator('#inspector .ins-foot')).toContainText('applies after all tracks');
  expect(await selectedLines(page)).toEqual([5, 6, 7]);

  await row(page, 'master:1').click();
  const h2 = page.locator('#inspector .ins-header');
  await expect(h2).toContainText('master fx');
  await expect(h2).toContainText(':reverb');
  await expect(h2).toContainText('↗ L7');
  await expect(page.locator('#inspector .ins-slider[data-key="fx|reverb|wet"]')).toHaveCount(1);
  await expect(page.locator('#inspector .ins-slider[data-key="fx|delay|time"]')).toHaveCount(0);
});

test('inspector slider inside master rewrites the right literal and undoes in one step', async ({ page }) => {
  await load(page);
  await evalBuffer(page);
  await expect(output(page)).toContainText('song: 1 sections', { timeout: 15_000 });
  await row(page, 'master:1').click();
  const slider = page.locator('#inspector .ins-slider[data-key="fx|reverb|wet"]');
  await slider.focus();
  await page.keyboard.press('ArrowRight');
  const text = await doc(page);
  expect(text).toBe(M.replace('(fx :reverb 0.3))', '(fx :reverb 0.31))'));
  expect(text).toContain('(fx :reverb 0.5)');
  await expect
    .poll(() => page.evaluate(() => window.__REPULSE_APP__.fxParam('reverb', 'wet')), { timeout: 1000 })
    .toBeCloseTo(0.31, 4); // AudioParam values are float32
  await focusEditor(page);
  await page.keyboard.press('ControlOrMeta+z');
  expect(await doc(page)).toBe(M);
});

test('(song …) then (master …) prints the song summary; master alone prints its result', async ({ page }) => {
  await load(page);
  await evalBuffer(page);
  await expect(output(page)).toContainText('song: 1 sections', { timeout: 15_000 });

  await setText(page, '(master (fx :reverb 0.3))');
  await evalBuffer(page);
  await expect(output(page)).toContainText('master: 1 fx', { timeout: 15_000 });
});

test('master errors surface in the output', async ({ page }) => {
  await load(page);
  await setText(page, '(master (seq :bd))');
  await evalBuffer(page);
  await expect(output(page)).toHaveText('Error: master only takes (fx …) forms — got (seq :bd)', { timeout: 15_000 });

  await setText(page, '(master (fx :reverb 0.3))\n(master (fx :delay 0.25))');
  await evalBuffer(page);
  await expect(output(page)).toHaveText(
    'Error: Only one (master …) per buffer — move these (fx …) forms into the first (master …)',
    { timeout: 15_000 },
  );
});
