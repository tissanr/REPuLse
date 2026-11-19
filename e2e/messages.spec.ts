import { expect, type Page, test } from '@playwright/test';

// Phase TEST3 — output and console messages from the release protocol that no
// other spec asserts: inspector "not evaluated" notes, the loose-fx console hint,
// the command bar printing a (slider …) value, and the one-export-at-a-time guard.

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_APP__: any;
  }
}

const W = `(def kick (seq :bd :_ :bd :_))
(track :drums (->> kick (amp 0.8) (fx :filter :freq 2400)))
(track :bass (->> (seq :c2 :eb2) (synth :saw) (amp 0.6) (decay 0.25)))
(fx :reverb 0.3)
(song
  (section :intro 4 [:drums])
  (section :verse 8 [:drums :bass])
  (section :chorus 8 :all))`;

async function load(page: Page, text = '') {
  await page.addInitScript(() => {
    try { localStorage.clear(); } catch {}
  });
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
  await setText(page, text);
}

async function setText(page: Page, text: string) {
  await page.evaluate((t) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
  }, text);
}

const output = (page: Page) => page.locator('#output');
const row = (page: Page, id: string) => page.locator(`#outline-panel .ol-row[data-id="${id}"]`);
const evaluate = (page: Page, code: string) => page.evaluate((c) => window.__REPULSE_APP__.evaluate(c), code);

async function altEnter(page: Page) {
  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
  await page.keyboard.press('Alt+Enter');
}

test('R45: nothing evaluated yet — the inspector says so for tracks and fx', async ({ page }) => {
  await load(page, W);
  await expect(row(page, 'track::bass')).toBeVisible();
  await row(page, 'track::bass').click();
  await expect(page.locator('#inspector .ins-note')).toHaveText('not evaluated — Alt+Enter');
  await row(page, 'fx:0').click();
  await expect(page.locator('#inspector .ins-note')).toHaveText('not active yet — Alt+Enter');
});

test('R46: after a code change the section cue is disabled; unused defs are flagged', async ({ page }) => {
  await load(page, W);
  await altEnter(page);
  await expect(output(page)).toHaveText(/^song: 3 sections/);
  // rename :verse → :bridge in the code, without evaluating
  await page.evaluate(() => {
    const v = window.__REPULSE_EDITOR__;
    const s = v.state.doc.toString();
    const i = s.indexOf(':verse');
    v.dispatch({ changes: { from: i, to: i + ':verse'.length, insert: ':bridge' } });
  });
  await expect(row(page, 'section:1')).toContainText(':bridge');
  await row(page, 'section:1').click();
  const cue = page.locator('#inspector .ins-cue');
  await expect(cue).toBeDisabled();
  await expect(cue).toHaveAttribute('title', 'evaluate first');

  await page.evaluate(() => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: v.state.doc.length, insert: '\n(def x 1)' } });
  });
  await row(page, 'def:x').click();
  await expect(page.locator('#inspector .ins-note')).toHaveText('Not used by any track or def.');
  await evaluate(page, '(stop)');
});

test('R59: the loose-fx hint is logged once per page load, not an error', async ({ page }) => {
  const HINT =
    '[REPuLse] Hint: this code uses (master …) and top-level (fx …) — consider moving the top-level (fx …) forms into (master …)';
  const logs: { type: string; text: string }[] = [];
  page.on('console', (m) => logs.push({ type: m.type(), text: m.text() }));
  await load(page, '(fx :delay 0.25)\n(master (fx :reverb 0.3))');
  await altEnter(page);
  await expect.poll(() => logs.filter((l) => l.text === HINT).length).toBe(1);
  expect(logs.find((l) => l.text === HINT)!.type).toBe('info');
  await expect(output(page)).not.toHaveClass(/error/);
  await altEnter(page);
  await page.waitForTimeout(500);
  expect(logs.filter((l) => l.text === HINT).length).toBe(1);
  expect(logs.filter((l) => l.type === 'error' && !/Failed to load resource/.test(l.text))).toEqual([]);
});

test('R84: (slider 0.7 0 1) in the command bar prints the value as text and draws no widget', async ({ page }) => {
  await load(page);
  await page.locator('#cmd-container .cm-content').click();
  await page.keyboard.type('(slider 0.7 0 1)');
  await page.keyboard.press('Enter');
  await expect(output(page)).toHaveText('0.7');
  await expect(page.locator('.cm-inline-slider')).toHaveCount(0);
});

const SONG = `(bpm 240)
(track :kick (seq :bd :_ :bd :_))
(song (section :a 4 :all))`;

test('R107: a second export is refused while one runs, and (stop) cancels without a download', async ({ page }) => {
  await load(page, SONG);
  await altEnter(page);
  await expect(output(page)).toHaveText(/^song: 1 sections/);
  let downloads = 0;
  page.on('download', () => { downloads += 1; });

  await evaluate(page, '(export :song :realtime)');
  await expect(output(page)).toHaveText(/^exporting song — 4 bars, .* in real time… \(stop\) cancels$/);
  await evaluate(page, '(export :song)');
  await expect(output(page)).toHaveText('Error: an export is already running — (stop) cancels it');
  await evaluate(page, '(stop)');
  await expect(output(page)).toHaveText('export cancelled');
  await page.waitForTimeout(1500);
  expect(downloads).toBe(0);
});
