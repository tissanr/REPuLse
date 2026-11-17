import { expect, type Page, test } from '@playwright/test';

// Phase WS3 — inline (slider v min max) widgets, drag write-back, ⌘K S and the
// inspector ≋ toggle against the real app page.

const T = `(track :bass (->> (seq :e1 :g1) (synth :saw) (amp (slider 0.7 0 1))))
(track :hats (->> (seq :hh :hh) (decay 0.25)))
(def cutoff (slider 632 20 20000))`;

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
    __REPULSE_TEST__: any;
  }
}

async function setText(page: Page, text: string) {
  await page.evaluate((t) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
  }, text);
}

async function load(page: Page, text: string = T) {
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
  await setText(page, text);
}

const doc = (page: Page) => page.evaluate(() => window.__REPULSE_EDITOR__.state.doc.toString());
const head = (page: Page) => page.evaluate(() => window.__REPULSE_EDITOR__.state.selection.main.head);
const line = async (page: Page, n: number) => (await doc(page)).split('\n')[n - 1];
const widgets = (page: Page) => page.locator('#editor-container .cm-inline-slider');
const row = (page: Page, id: string) => page.locator(`#outline-panel .ol-row[data-id="${id}"]`);
const output = (page: Page) => page.locator('#output');

async function select(page: Page, pos: number) {
  await page.evaluate((p) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ selection: { anchor: p } });
    v.focus();
  }, pos);
}

async function focusEditor(page: Page) {
  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
}

// Alt+Enter on buffer T, then wait until the bass track plays.
async function evalT(page: Page) {
  await focusEditor(page);
  await page.keyboard.press('Alt+Enter');
  await expect
    .poll(() => page.evaluate(() => window.__REPULSE_APP__.trackParam('bass', 'amp')), { timeout: 15_000 })
    .toBe(0.7);
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    try { localStorage.clear(); } catch {}
  });
  await page.setViewportSize({ width: 1280, height: 800 });
});

test('harness: (slider …) inside amp gives the same events as the literal', async ({ page }) => {
  await page.goto('/test-harness.html');
  await page.waitForFunction(() => !!window.__REPULSE_TEST__);
  const [a, b] = await page.evaluate(() => [
    window.__REPULSE_TEST__.evalToEvents('(->> (seq :c4 :e4) (amp (slider 0.7 0 1)))'),
    window.__REPULSE_TEST__.evalToEvents('(->> (seq :c4 :e4) (amp 0.7))'),
  ]);
  expect(a).toEqual(b);
  expect(a).toHaveLength(2);
  for (const ev of a) expect(ev.value.amp).toBe(0.7);
});

test('min ≥ max is an eval error and draws no widget', async ({ page }) => {
  await load(page, '(slider 0.5 1 0)');
  await focusEditor(page);
  await page.keyboard.press('Alt+Enter');
  await expect(output(page)).toHaveText('Error: slider min must be less than max — got min 1, max 0');
  await expect(widgets(page)).toHaveCount(0);
});

test('a clamped value warns, plays clamped and shows the clamped label', async ({ page }) => {
  await load(page, '(track :a (->> (seq :c4) (amp (slider 2 0 1))))');
  await focusEditor(page);
  await page.keyboard.press('Alt+Enter');
  await expect(output(page)).toContainText('Warning: slider value 2 is outside [0, 1] — clamped to 1', {
    timeout: 15_000,
  });
  await expect
    .poll(() => page.evaluate(() => window.__REPULSE_APP__.trackParam('a', 'amp')), { timeout: 1000 })
    .toBe(1);
  expect(await page.locator('.cm-lint-marker-warning').count()).toBeGreaterThanOrEqual(1);
  await expect(widgets(page).first().locator('.cm-inline-slider-val')).toHaveText('1.00');
});

test('widgets render with label, size, fill and tooltip', async ({ page }) => {
  await load(page);
  await expect(widgets(page)).toHaveCount(2);
  const first = widgets(page).nth(0);
  await expect(first.locator('.cm-inline-slider-val')).toHaveText('0.70');
  const box = await first.boundingBox();
  expect(Math.abs(box!.width - 70)).toBeLessThanOrEqual(0.5);
  expect(await first.locator('.cm-inline-slider-fill').evaluate((e) => (e as HTMLElement).style.width)).toBe('70%');
  await expect(first).toHaveAttribute('title', '(slider 0.7 0 1) · 0–1 · drag, ⇧ fine');
  const second = widgets(page).nth(1);
  await expect(second.locator('.cm-inline-slider-val')).toHaveText('632');
  const w = await second.locator('.cm-inline-slider-fill').evaluate((e) => parseFloat((e as HTMLElement).style.width));
  expect(w).toBeGreaterThanOrEqual(49.9);
  expect(w).toBeLessThanOrEqual(50.1);
  expect(await second.getAttribute('title')).toContain('20–20k · log');
});

test('drag rewrites the value, re-evaluates and undoes in one step', async ({ page }) => {
  await load(page);
  await evalT(page);
  const box = (await widgets(page).nth(0).boundingBox())!;
  const x = box.x + box.width / 2;
  const y = box.y + box.height / 2;
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x + 14, y);
  await page.mouse.up();
  expect(await doc(page)).toContain('(amp (slider 0.80 0 1))');
  await expect
    .poll(() => page.evaluate(() => window.__REPULSE_APP__.trackParam('bass', 'amp')), { timeout: 1000 })
    .toBe(0.8);
  await focusEditor(page);
  await page.keyboard.press('ControlOrMeta+z');
  expect(await doc(page)).toBe(T);
});

test('⇧-drag is 10× finer and shows the ×0.1 tag', async ({ page }) => {
  await load(page);
  const box = (await widgets(page).nth(0).boundingBox())!;
  const x = box.x + box.width / 2;
  const y = box.y + box.height / 2;
  await page.mouse.move(x, y);
  await page.keyboard.down('Shift');
  await page.mouse.down();
  await page.mouse.move(x + 14, y);
  await expect(page.locator('#editor-container .cm-inline-slider-fine').first()).toBeVisible();
  await expect(page.locator('#editor-container .cm-inline-slider-fine').first()).toHaveText('×0.1');
  await page.mouse.up();
  await page.keyboard.up('Shift');
  expect(await doc(page)).toContain('(amp (slider 0.71 0 1))');
});

test('a cursor touching the form shows the raw text, which stays editable', async ({ page }) => {
  await load(page);
  await select(page, 59);
  await expect(widgets(page)).toHaveCount(1);
  await expect(page.locator('#editor-container .cm-content')).toContainText('(slider 0.7 0 1)');
  await select(page, 61);
  await page.keyboard.type('5');
  expect(await doc(page)).toContain('(slider 0.75 0 1)');
  await select(page, 50);
  await expect(widgets(page)).toHaveCount(1);
  await select(page, 49);
  await expect(widgets(page)).toHaveCount(2);
  await select(page, 0);
  await expect(widgets(page)).toHaveCount(2);
});

test('⌘K S wraps and unwraps a number literal', async ({ page }) => {
  await load(page);
  await select(page, 111);
  await page.keyboard.press('ControlOrMeta+k');
  await page.keyboard.press('s');
  expect(await line(page, 2)).toBe('(track :hats (->> (seq :hh :hh) (decay (slider 0.25 0 2))))');
  expect(await head(page)).toBe(121);
  await page.keyboard.press('ControlOrMeta+k');
  await page.keyboard.press('s');
  expect(await line(page, 2)).toBe('(track :hats (->> (seq :hh :hh) (decay 0.25)))');
  await page.keyboard.press('ControlOrMeta+k');
  await page.keyboard.press('s');
  expect(await line(page, 2)).toContain('(decay (slider 0.25 0 2))');
  await select(page, 0);
  await expect(widgets(page)).toHaveCount(3);
  await page.keyboard.press('ControlOrMeta+z');
  expect(await doc(page)).toBe(T);
});

test('⌘K S on (fx :filter :freq 800) wraps with the freq range', async ({ page }) => {
  await load(page, '(fx :filter :freq 800)');
  await select(page, 19);
  await page.keyboard.press('ControlOrMeta+k');
  await page.keyboard.press('s');
  expect(await doc(page)).toBe('(fx :filter :freq (slider 800 20 20000))');
});

test('inspector ≋ shows and toggles inline sliders for track params', async ({ page }) => {
  await load(page);
  await evalT(page);
  await row(page, 'track::bass').click();
  const ampInl = page.locator('#inspector .ins-inl[data-param="amp"]');
  await expect(ampInl).toHaveClass(/\bon\b/);
  await row(page, 'track::hats').click();
  const decayInl = page.locator('#inspector .ins-inl[data-param="decay"]');
  await expect(decayInl).toBeVisible();
  await expect(decayInl).not.toHaveClass(/\bon\b/);
  await decayInl.click();
  expect(await line(page, 2)).toContain('(decay (slider 0.25 0 2))');
  await expect(page.locator('#inspector .ins-inl[data-param="decay"]')).toHaveClass(/\bon\b/);
  await page.locator('#inspector .ins-inl[data-param="decay"]').click();
  expect(await line(page, 2)).toContain('(decay 0.25)');
});

test('inspector slider writes into the inline slider value', async ({ page }) => {
  await load(page);
  await evalT(page);
  await row(page, 'track::bass').click();
  const slider = page.locator('#inspector .ins-slider[data-key="bass|amp"]');
  await slider.focus();
  await page.keyboard.press('ArrowRight');
  expect(await doc(page)).toBe(T.replace('(amp (slider 0.7 0 1))', '(amp (slider 0.71 0 1))'));
});
