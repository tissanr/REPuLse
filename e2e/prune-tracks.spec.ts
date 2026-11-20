import { expect, type Page, test } from '@playwright/test';

// A whole-buffer evaluation (Alt+Enter, or (upd) / F9 in a single-buffer
// project) stops the tracks the buffer no longer defines, including the
// anonymous :_ track of a bare pattern. Command-bar evaluations only add.

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_BUFFERS__: any;
    __REPULSE_APP__: any;
  }
}

async function fresh(page: Page) {
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_BUFFERS__);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.waitForFunction(() => !!window.__REPULSE_BUFFERS__);
}

async function setText(page: Page, text: string) {
  await page.evaluate((t) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
  }, text);
}

const output = (page: Page) => page.locator('#output');

async function press(page: Page, key: string) {
  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
  await page.keyboard.press(key);
}

/** Running track names, via the command bar's (tracks), sorted. */
async function tracks(page: Page): Promise<string[]> {
  await page.evaluate(() => window.__REPULSE_APP__.evaluate('(tracks)'));
  const t = (await output(page).textContent()) ?? '';
  const m = t.match(/^(?:=> )?\((.*)\)$/);
  expect(m, `unexpected (tracks) output: ${t}`).not.toBeNull();
  return m![1].split(' ').filter(Boolean).sort();
}

const DARK = `(defsynth dark [freq] (->> (saw freq) (lpf 800) (env-perc 0.01 0.4)))
(synth :dark (seq :c3 :g3))`;
const HATS = `(track :hats (->> (fast 8 (seq :hh)) (fx :filter 800)))`;

test.beforeEach(async ({ page }) => {
  await fresh(page);
});

test('P1: F9 after replacing a bare pattern with a track stops the bare pattern', async ({ page }) => {
  await setText(page, DARK);
  await press(page, 'Alt+Enter');
  await expect(output(page)).toHaveText(/^playing pattern/);
  expect(await tracks(page)).toEqual([':_']);

  await setText(page, HATS);
  await press(page, 'F9');
  expect(await tracks(page)).toEqual([':hats']);
});

test('P2: Alt+Enter after replacing a bare pattern with a track stops the bare pattern', async ({ page }) => {
  await setText(page, DARK);
  await press(page, 'Alt+Enter');
  await setText(page, HATS);
  await press(page, 'Alt+Enter');
  await expect(output(page)).toHaveText('track :hats playing');
  expect(await tracks(page)).toEqual([':hats']);
});

test('P3: deleting a (track …) form stops that track, the others keep playing', async ({ page }) => {
  await setText(page, '(track :kick (seq :bd :_ :bd :_))\n(track :snare (seq :_ :sd :_ :sd))');
  await press(page, 'Alt+Enter');
  expect(await tracks(page)).toEqual([':kick', ':snare']);

  await setText(page, '(track :kick (seq :bd :_ :bd :_))');
  await press(page, 'Alt+Enter');
  expect(await tracks(page)).toEqual([':kick']);

  await setText(page, '(track :kick (seq :bd :bd :bd :bd))');
  await press(page, 'F9');
  expect(await tracks(page)).toEqual([':kick']);
});

test('P4: a command-bar track is kept by command-bar evals, removed by the next buffer eval', async ({ page }) => {
  await setText(page, '(track :kick (seq :bd :_ :bd :_))');
  await press(page, 'Alt+Enter');
  await page.evaluate(() => window.__REPULSE_APP__.evaluate('(track :extra (seq :hh :hh))'));
  expect(await tracks(page)).toEqual([':extra', ':kick']);

  await press(page, 'Alt+Enter');
  expect(await tracks(page)).toEqual([':kick']);
});

test('P5: a buffer with an error changes nothing', async ({ page }) => {
  await setText(page, '(track :kick (seq :bd :_ :bd :_))\n(track :snare (seq :_ :sd :_ :sd))');
  await press(page, 'Alt+Enter');
  await setText(page, '(track :kick (seq :bd :_ :bd :_))\n(qqqqqqqq)');
  await press(page, 'Alt+Enter');
  await expect(output(page)).toHaveText(/^Error/);
  expect(await tracks(page)).toEqual([':kick', ':snare']);
});

test('P6: deleting every track form stops playback', async ({ page }) => {
  await setText(page, '(track :kick (seq :bd :_ :bd :_))');
  await press(page, 'Alt+Enter');
  await setText(page, '(def x 1)');
  await press(page, 'Alt+Enter');
  expect(await tracks(page)).toEqual([]);
});

test('P7: F9 in one buffer of a multi-buffer project prunes nothing', async ({ page }) => {
  await page.evaluate(() => window.__REPULSE_BUFFERS__.switchTo('main'));
  await setText(page, '(use :drums)\n(track :bass (seq :c2 :_ :g2 :_))');
  const r = await page.evaluate(() =>
    window.__REPULSE_BUFFERS__.create('drums', '(track :kick (seq :bd :_ :bd :_))'),
  );
  expect(r.ok).toBe('drums');
  await press(page, 'Alt+Enter');
  expect(await tracks(page)).toEqual([':bass', ':kick']);

  // the drums tab is active; (upd) evaluates only it
  await page.evaluate(() => window.__REPULSE_BUFFERS__.switchTo('drums'));
  await press(page, 'F9');
  expect(await tracks(page)).toEqual([':bass', ':kick']);
});
