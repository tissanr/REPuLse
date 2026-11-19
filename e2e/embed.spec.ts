import path from 'node:path';
import { expect, test } from '@playwright/test';

// Phase TEST3 — the <repulse-editor> embed (app/public/embed-test.html).
// The embed has no output line; embed.cljs keeps the last output message on
// window.__REPULSE_EMBED_OUTPUT__ for this test.

declare global {
  interface Window {
    __REPULSE_EMBED_OUTPUT__?: string;
  }
}

// Serve the freshly compiled :embed-e2e bundle (gitignored, built by
// playwright.config.ts) in place of the committed app/public/embed.js.
test.beforeEach(async ({ page }) => {
  await page.route('**/embed.js', (route) =>
    route.fulfill({
      path: path.resolve('app/public/e2e-embed/embed.js'),
      contentType: 'application/javascript',
    }),
  );
});

test('EM1: (history) in the embed is refused and no app chrome is mounted', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto('/embed-test.html');
  const editors = page.locator('repulse-editor');
  await expect(editors).toHaveCount(3);
  const third = editors.nth(2).locator('.cm-content');
  await expect(third).toBeVisible();

  await third.click();
  await page.keyboard.press('ControlOrMeta+a');
  await page.keyboard.type('(history)');
  await page.keyboard.press('Alt+Enter');
  await expect
    .poll(() => page.evaluate(() => window.__REPULSE_EMBED_OUTPUT__))
    .toBe('Error: snapshots and history are only available in the full app');

  // none of the full app's workbench UI exists on the embed page
  await expect(page.locator('#status-head')).toHaveCount(0);
  await expect(page.locator('#inspector')).toHaveCount(0);
  await expect(page.locator('#history-panel')).toHaveCount(0);
  const inShadow = await page.evaluate(() =>
    Array.from(document.querySelectorAll('repulse-editor')).map((el) => ({
      tint: el.shadowRoot!.querySelectorAll('.ws-sel-line').length,
      chrome: el.shadowRoot!.querySelectorAll('#status-head, #inspector, #outline-panel').length,
    })),
  );
  expect(inShadow).toEqual([
    { tint: 0, chrome: 0 },
    { tint: 0, chrome: 0 },
    { tint: 0, chrome: 0 },
  ]);
});

test('embed play/stop button starts and stops playback', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto('/embed-test.html');
  const editors = page.locator('repulse-editor');
  await expect(editors).toHaveCount(3);
  const btn = (i: number) => editors.nth(i).locator('.embed-transport-btn');
  for (const i of [0, 1, 2]) await expect(btn(i)).toHaveAttribute('aria-label', 'Play');

  await editors.nth(0).locator('.embed-play-overlay').click();
  await expect
    .poll(() => page.evaluate(() => window.__REPULSE_EMBED_OUTPUT__))
    .toBe('=> track :kick playing');
  // playback is global: every instance shows stop
  for (const i of [0, 1, 2]) await expect(btn(i)).toHaveAttribute('aria-label', 'Stop');

  await btn(0).click();
  for (const i of [0, 1, 2]) await expect(btn(i)).toHaveAttribute('aria-label', 'Play');

  // ▶ on a non-autoplay instance evaluates its own code
  await btn(2).click();
  await expect
    .poll(() => page.evaluate(() => window.__REPULSE_EMBED_OUTPUT__))
    .toBe('=> track :melody playing');
  await expect(btn(2)).toHaveAttribute('aria-label', 'Stop');
});
