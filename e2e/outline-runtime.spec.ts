import { expect, type Page, test } from '@playwright/test';

// Phase WS6 — the outline shows the union of the code and the scheduler.

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_BUFFERS__: any;
    __REPULSE_APP__: { tracks: () => string[]; evaluate: (code: string) => void };
  }
}

async function load(page: Page) {
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
}

async function setText(page: Page, text: string) {
  await page.evaluate((t) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
  }, text);
}
const doc = (page: Page) => page.evaluate(() => window.__REPULSE_EDITOR__.state.doc.toString());
const tracks = (page: Page) => page.evaluate(() => window.__REPULSE_APP__.tracks());
async function altEnter(page: Page) {
  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
  await page.keyboard.press('Alt+Enter');
}
async function evalText(page: Page, text: string) {
  await setText(page, text);
  await altEnter(page);
}
async function command(page: Page, code: string) {
  await page.evaluate((c) => window.__REPULSE_APP__.evaluate(c), code);
}
async function expectTracks(page: Page, names: string[]) {
  await expect.poll(() => tracks(page), { timeout: 15_000 }).toEqual(names);
}
const codeRow = (page: Page, name: string) =>
  page.locator('#outline-panel .ol-row[data-id]').filter({ has: page.locator('.ol-name', { hasText: new RegExp(`^${name}`) }) });
const runRows = (page: Page) => page.locator('#outline-panel .ol-row:not([data-id]):has(.ol-kind-track)');
const glyph = (r: any) => r.locator('.ol-glyph');

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    try { localStorage.clear(); } catch {}
  });
  await page.setViewportSize({ width: 1280, height: 800 });
});

test('kick / extra / upd / clear! lifecycle', async ({ page }) => {
  await load(page);
  const code = '(track :kick (seq :bd :_ :bd :_))';
  await evalText(page, code);
  await expectTracks(page, ['kick']);
  await expect(glyph(codeRow(page, ':kick'))).toHaveText('•');
  await expect(runRows(page)).toHaveCount(0);

  await command(page, '(track :extra (seq :hh :hh))');
  await expectTracks(page, ['extra', 'kick']);
  await expect(runRows(page)).toHaveCount(1);
  const extra = runRows(page).first();
  await expect(extra.locator('.ol-name')).toContainText(':extra');
  await expect(extra.locator('.ol-meta')).toHaveText('not in code');
  await expect(glyph(extra)).toHaveText('•');
  expect(await doc(page)).toBe(code);

  await command(page, '(upd)');
  await expectTracks(page, ['kick']);
  await expect(runRows(page)).toHaveCount(0);
  await expect(glyph(codeRow(page, ':kick'))).toHaveText('•');

  await command(page, '(clear!)');
  await expectTracks(page, []);
  const k = codeRow(page, ':kick');
  await expect(k).toHaveClass(/ol-off/);
  await expect(k).toHaveAttribute('title', 'not running');
  await expect(glyph(k)).toHaveText('');
  await altEnter(page);
  await expect(glyph(codeRow(page, ':kick'))).toHaveText('•');
});

test('clear! :a dims only :a', async ({ page }) => {
  await load(page);
  await evalText(page, '(track :a (seq :bd :bd))\n(track :b (seq :hh :hh))');
  await expectTracks(page, ['a', 'b']);
  await command(page, '(clear! :a)');
  await expectTracks(page, ['b']);
  await expect(codeRow(page, ':a')).toHaveClass(/ol-off/);
  await expect(codeRow(page, ':b')).not.toHaveClass(/ol-off/);
  await expect(glyph(codeRow(page, ':b'))).toHaveText('•');
});

test('muted track shows ■, after (stop) it is off', async ({ page }) => {
  await load(page);
  await evalText(page, '(track :hats (seq :hh :oh) :mute)');
  await expectTracks(page, ['hats']);
  const r = codeRow(page, ':hats');
  await expect(r).toHaveClass(/ol-muted/);
  await expect(glyph(r)).toHaveText('■');
  await command(page, '(stop)');
  await expectTracks(page, []);
  await expect(codeRow(page, ':hats')).toHaveClass(/ol-off/);
  await expect(glyph(codeRow(page, ':hats'))).toHaveText('');
});

test('a bare pattern shows an anonymous running row', async ({ page }) => {
  await load(page);
  await evalText(page, '(seq :hh :hh)');
  await expectTracks(page, ['_']);
  const r = runRows(page).first();
  await expect(r.locator('.ol-name')).toContainText('_');
  await expect(r.locator('.ol-meta')).toHaveText('anonymous');
});

test('other buffer track: running row jumps to it, Shift+F12 returns', async ({ page }) => {
  await load(page);
  await page.evaluate(() => window.__REPULSE_BUFFERS__.switchTo('main'));
  await setText(page, '(use :drums)');
  const r = await page.evaluate(() => window.__REPULSE_BUFFERS__.create('drums', '(track :hats (seq :hh :hh))'));
  expect(r.ok).toBe('drums');
  await page.evaluate(() => window.__REPULSE_BUFFERS__.switchTo('main'));
  await altEnter(page);
  await expectTracks(page, ['hats']);
  await expect(runRows(page)).toHaveCount(1);
  const row = runRows(page).first();
  await expect(row.locator('.ol-meta')).toHaveText('drums');
  await row.click();
  await expect.poll(() => page.evaluate(() => window.__REPULSE_BUFFERS__.project().active)).toBe('drums');
  await expect(runRows(page)).toHaveCount(0);
  await expect(glyph(codeRow(page, ':hats'))).toHaveText('•');
  const head = await page.evaluate(() => window.__REPULSE_EDITOR__.state.selection.main.head);
  expect(head).toBe(7); // on :hats
  await page.keyboard.press('Shift+F12');
  await expect.poll(() => page.evaluate(() => window.__REPULSE_BUFFERS__.project().active)).toBe('main');
});

test('running rows are not selectable', async ({ page }) => {
  await load(page);
  await evalText(page, '(track :kick (seq :bd :_ :bd :_))');
  await expectTracks(page, ['kick']);
  await command(page, '(track :extra (seq :hh :hh))');
  await expect(runRows(page)).toHaveCount(1);
  await runRows(page).first().click();
  await expect(page.locator('#outline-panel .ol-row.ol-sel')).toHaveCount(0);
});
