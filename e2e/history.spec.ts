import { expect, type Page, test } from '@playwright/test';

// Phase SK2 — eval history, snapshots and the history panel against the real app page.

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_BUFFERS__: any;
    __REPULSE_APP__: any;
    __REPULSE_HISTORY__: any;
  }
}

async function ready(page: Page) {
  await page.waitForFunction(
    () => !!window.__REPULSE_BUFFERS__ && !!window.__REPULSE_HISTORY__ && window.__REPULSE_HISTORY__.ready(),
  );
}

async function fresh(page: Page) {
  await page.goto('/');
  await ready(page);
}

async function setText(page: Page, text: string) {
  await page.evaluate((t) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
  }, text);
}

async function text(page: Page): Promise<string> {
  return page.evaluate(() => window.__REPULSE_EDITOR__.state.doc.toString());
}

async function altEnter(page: Page) {
  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
  await page.keyboard.press('Alt+Enter');
}

const output = (page: Page) => page.locator('#output');
const evaluate = (page: Page, code: string) => page.evaluate((c) => window.__REPULSE_APP__.evaluate(c), code);
const entries = (page: Page) => page.evaluate(() => window.__REPULSE_HISTORY__.entries());

async function threeEvaluations(page: Page) {
  await setText(page, '(seq :bd :sd)');
  await altEnter(page);
  await altEnter(page);
  await expect.poll(async () => (await entries(page)).length).toBe(1);
  await setText(page, '(seq :bd :hh)');
  await altEnter(page);
  await expect.poll(async () => (await entries(page)).length).toBe(2);
  await setText(page, '(qqqqqqqq)');
  await altEnter(page);
  await expect.poll(async () => (await entries(page)).length).toBe(3);
}

test.beforeEach(async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
});

test('W1-W6: record, panel, preview, snapshots, link, rename/delete/clear', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await fresh(page);

  // W1 — command-bar evaluations are not recorded
  await evaluate(page, '(bpm 130)');
  expect(await entries(page)).toEqual([]);
  await threeEvaluations(page);
  const es = await entries(page);
  expect(es[0].ok).toBe(false);
  expect(es[0].error).toBe('Undefined symbol: qqqqqqqq');
  expect(es[1].ok).toBe(true);

  // W2 — panel
  await page.locator('#history-toggle-btn').click();
  await expect(page.locator('#history-panel')).not.toHaveClass(/hidden/);
  const rows = page.locator('.hist-entry');
  await expect(rows).toHaveCount(3);
  await expect(rows.first()).toHaveAttribute('data-id', '3');
  await expect(rows.first()).toHaveClass(/hist-err/);
  await expect(rows.last().locator('.hist-sum')).toHaveText('+1 -0');
  await expect(rows.nth(1).locator('.hist-sum')).toHaveText('+1 -1');

  // W3 — preview and restore by click
  await page.locator('.hist-entry[data-id="1"]').click();
  await expect(page.locator('.hist-preview .hist-del')).toContainText('- (seq :bd :sd)');
  await expect(page.locator('.hist-preview .hist-add')).toContainText('+ (qqqqqqqq)');
  await page.locator('[data-act="restore-selected"]').click();
  await expect.poll(() => text(page)).toBe('(seq :bd :sd)');
  await expect(output(page)).toHaveText('restored history #1 — previous state is history #3');
  expect((await entries(page)).length).toBe(3);

  // W4 — snapshots via built-ins
  await evaluate(page, '(snapshot! "take A")');
  await expect(output(page)).toHaveText('snapshot "take A" saved');
  await setText(page, '(seq :cp)');
  await evaluate(page, '(restore! "take A")');
  await expect(output(page)).toHaveText('restored snapshot "take A" — previous state is history #4');
  await expect.poll(() => text(page)).toBe('(seq :bd :sd)');
  const e4 = (await entries(page)).find((e: any) => e.id === 4);
  expect(e4.kind).toBe('restore-point');
  await evaluate(page, '(snapshots)');
  await expect(output(page)).toHaveText('["take A"]');
  await evaluate(page, '(restore! "take B")');
  await expect(output(page)).toHaveText('Error: unknown snapshot "take B" — did you mean "take A"?');
  await evaluate(page, '(snapshot! 1)');
  await expect(output(page)).toHaveText('Error: snapshot! expects one name string, e.g. (snapshot! "take A")');

  // W5 — copy link
  await page.locator('.hist-snap[data-name="take A"] [data-act="link"]').click();
  await expect(output(page)).toHaveText('link to snapshot "take A" copied to clipboard');
  const link = await page.evaluate(() => navigator.clipboard.readText());
  expect(link.startsWith('http://127.0.0.1:3100/#v3:')).toBe(true);
  const page2 = await context.newPage();
  await page2.goto(link);
  await ready(page2);
  expect(await text(page2)).toBe('(seq :bd :sd)');
  await page2.close();

  // W6 — rename / delete / clear via in-page dialogs (native ones block audio)
  await page.locator('.hist-snap[data-name="take A"] [data-act="rename"]').click();
  await expect(page.locator('.rp-dialog-input')).toHaveValue('take A');
  await page.locator('.rp-dialog-input').fill('verse');
  await page.locator('.rp-dialog-input').press('Enter');
  await expect(page.locator('.hist-snap[data-name="verse"]')).toHaveCount(1);
  // Escape cancels: nothing is deleted
  await page.locator('.hist-snap[data-name="verse"] [data-act="delete"]').click();
  await page.locator('.rp-dialog').press('Escape');
  await expect(page.locator('.rp-dialog')).toHaveCount(0);
  await expect(page.locator('.hist-snap')).toHaveCount(1);
  await page.locator('.hist-snap[data-name="verse"] [data-act="delete"]').click();
  await page.locator('.rp-dialog-ok').click();
  await expect(page.locator('.hist-snap')).toHaveCount(0);
  expect(await page.evaluate(() => window.__REPULSE_HISTORY__.snapshots())).toEqual([]);
  await page.locator('[data-act="clear"]').click();
  await page.locator('.rp-dialog-ok').click();
  await expect(page.locator('.hist-entry')).toHaveCount(0);
});

test('W7: history and snapshots survive a reload', async ({ page }) => {
  await fresh(page);
  await threeEvaluations(page);
  await evaluate(page, '(snapshot! "take A")');
  await expect(output(page)).toHaveText('snapshot "take A" saved');
  // let the write-behind transaction commit
  await page.waitForTimeout(300);
  const before = await entries(page);

  await page.reload();
  await ready(page);
  expect(await page.evaluate(() => window.__REPULSE_HISTORY__.persistent())).toBe(true);
  const after = await entries(page);
  expect(after.map((e: any) => [e.id, e.hash])).toEqual(before.map((e: any) => [e.id, e.hash]));
  expect(await page.evaluate(() => window.__REPULSE_HISTORY__.snapshots())).toEqual(['take A']);

  await evaluate(page, '(history)');
  await expect(output(page)).toHaveText('history: 3 evaluations, 1 snapshot');
  await expect(page.locator('#history-panel')).toBeVisible();
  // The snapshot holds the state at snapshot time: main = (qqqqqqqq).
  await setText(page, '(seq :cp)');
  await evaluate(page, '(restore! "take A")');
  await expect.poll(() => text(page)).toBe('(qqqqqqqq)');
});
