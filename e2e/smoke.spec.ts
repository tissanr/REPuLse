import { expect, type Page, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { cspViolations, trackCsp } from './helpers/csp';

// Phase TEST3 — smoke checks for the full app page: no console errors, no CSP
// violations, first evaluation + (stop), and the CSP header the static server
// takes from vercel.json (the deployment itself is checked by hand).

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_APP__: any;
  }
}

/** Network errors of resources the offline test server cannot serve are not app bugs:
 *  everything that is not the app origin (sample banks, CDNs, Freesound, …) and the
 *  backend `/api/*` routes (no backend behind the static server). Matched by URL. */
function isIgnorableUrl(url: string, origin: string): boolean {
  if (!url) return false;
  if (!url.startsWith(origin)) return true;
  return new URL(url).pathname.startsWith('/api/');
}

test('S1: loading / logs no console error and no CSP violation', async ({ page, baseURL }) => {
  const errors: string[] = [];
  await trackCsp(page);
  page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
  page.on('console', (m) => {
    if (m.type() !== 'error') return;
    const url = m.location().url;
    if (isIgnorableUrl(url, baseURL!)) return;
    errors.push(`console.error: ${m.text()} (${url})`);
  });
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
  // let the deferred loads (plugins, WASM, sample registry) run
  await page.waitForTimeout(1500);
  expect(errors).toEqual([]);
  expect(await cspViolations(page)).toEqual([]);
});

test('S2: first evaluation plays, (stop) in the command bar stops it', async ({ page }) => {
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
  await page.evaluate(() => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: '(seq :bd :sd :bd :sd)' } });
    v.focus();
  });
  await page.keyboard.press('Alt+Enter');
  await expect(page.locator('#output')).toHaveText(
    'playing pattern — Alt+Enter to re-evaluate, (stop) to stop',
  );
  expect(await page.evaluate(() => window.__REPULSE_APP__.playing())).toBe(true);

  await page.locator('#cmd-container .cm-content').click();
  await page.keyboard.type('(stop)');
  await page.keyboard.press('Enter');
  await expect.poll(() => page.evaluate(() => window.__REPULSE_APP__.playing())).toBe(false);
});

test('S3: / carries the production CSP from vercel.json', async ({ request }) => {
  const res = await request.get('/');
  const csp = res.headers()['content-security-policy'];
  expect(csp).toBeTruthy();
  expect(csp.endsWith("base-uri 'self'; object-src 'none';")).toBe(true);
  const config = JSON.parse(readFileSync('vercel.json', 'utf8'));
  const expected = config.headers[0].headers.find((h: any) => h.key === 'Content-Security-Policy').value;
  // The e2e server serves a dev build, which needs 'unsafe-eval' in script-src
  // (serve-static --dev-eval); everything else must match vercel.json exactly.
  expect(csp.replace("script-src 'unsafe-eval' ", 'script-src ')).toBe(expected);
});
