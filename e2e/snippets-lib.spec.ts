import { expect, type Page, test } from '@playwright/test';

// Phase TEST3 — snippet library panel against a mocked library (no backend, no
// login). Covers search/tag rendering as text (XSS regression of PR #176), the
// tag filter and "↓ insert".

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_APP__: any;
    __pwn?: number;
  }
}

const LIBRARY = {
  version: 1,
  snippets: [
    {
      id: 'four-on-the-floor',
      title: 'Four on the Floor',
      author: 'repulse',
      tags: ['rhythm', 'house'],
      bpm: 124,
      description: 'Classic 4/4 house kick pattern',
      code: '(track :kick (seq :bd :bd :bd :bd))',
    },
    {
      id: 'minimal-techno',
      title: 'Minimal Techno',
      author: 'repulse',
      tags: ['rhythm', 'techno'],
      bpm: 128,
      description: 'Sparse kick with offbeat hats',
      code: '(track :techno (seq :bd :_ :hh :_))',
    },
    {
      id: 'acid-line',
      title: 'Acid Line',
      author: 'repulse',
      tags: ['bassline', 'techno'],
      bpm: 130,
      description: 'Squelchy saw bass',
      code: '(track :acid (->> (seq :c2 :c2 :eb2) (synth :saw)))',
    },
    {
      id: 'evil-tags',
      title: 'Evil Tags',
      author: 'mallory',
      tags: ['My <b>Tag</b>', '<img src=x onerror="window.__pwn=1">'],
      bpm: 120,
      description: 'Tags that must render as text',
      code: '(track :evil (seq :hh))',
    },
  ],
};

async function load(page: Page) {
  await page.addInitScript(() => {
    try { localStorage.clear(); } catch {}
  });
  await page.setViewportSize({ width: 1280, height: 800 });
  // Static library + (unused without login) API, both mocked.
  await page.route('**/snippets/library.json', (route) =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify(LIBRARY) }));
  await page.route('**/api/snippets*', (route) =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify(LIBRARY.snippets) }));
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
  await page.locator('#snippet-toggle-btn').click();
  await expect(page.locator('#snippet-panel')).toBeVisible();
  await expect(page.locator('.snippet-card')).toHaveCount(LIBRARY.snippets.length);
}

const titles = (page: Page) => page.locator('.snippet-card .snippet-title').allTextContents();

test('L1: search input and sort switch keep hostile text as text', async ({ page }) => {
  const dialogs: string[] = [];
  page.on('dialog', async (d) => { dialogs.push(d.message()); await d.dismiss(); });
  await load(page);

  const hostile = '"><b id=pwn>x';
  await page.locator('#snippet-search').fill(hostile);
  await page.locator('#snippet-sort').selectOption('newest');
  // the toolbar is re-rendered after the reload; the field must still hold the text
  await expect(page.locator('#snippet-search')).toHaveValue(hostile);
  await expect(page.locator('#snippet-sort')).toHaveValue('newest');
  await expect(page.locator('#pwn')).toHaveCount(0);
  await expect(page.locator('#snippet-toolbar b')).toHaveCount(0);
  expect(await page.evaluate(() => window.__pwn)).toBeUndefined();
  expect(dialogs).toEqual([]);
});

test('L2: tags with markup render as text', async ({ page }) => {
  const dialogs: string[] = [];
  page.on('dialog', async (d) => { dialogs.push(d.message()); await d.dismiss(); });
  await load(page);

  const evil = page.locator('.snippet-card', { hasText: 'Evil Tags' });
  const tags = evil.locator('.snippet-tag');
  await expect(tags).toHaveCount(2);
  await expect(tags.nth(0)).toHaveText('My <b>Tag</b>');
  await expect(tags.nth(1)).toHaveText('<img src=x onerror="window.__pwn=1">');
  await expect(evil.locator('.snippet-tags b, .snippet-tags img')).toHaveCount(0);
  // the tag dropdown carries the same strings as option text
  await expect(page.locator('#snippet-tag-filter option', { hasText: 'My <b>Tag</b>' })).toHaveCount(1);
  await expect(page.locator('#snippet-panel img')).toHaveCount(0);
  await page.waitForTimeout(300); // an onerror handler would have fired by now
  expect(await page.evaluate(() => window.__pwn)).toBeUndefined();
  expect(dialogs).toEqual([]);
});

test('L3: tag filter techno shows only techno snippets', async ({ page }) => {
  await load(page);
  await page.locator('#snippet-tag-filter').selectOption('techno');
  await expect(page.locator('.snippet-card')).toHaveCount(2);
  expect((await titles(page)).sort()).toEqual(['Acid Line', 'Minimal Techno']);
  await page.locator('#snippet-tag-filter').selectOption('');
  await expect(page.locator('.snippet-card')).toHaveCount(LIBRARY.snippets.length);
});

test('L4: ↓ insert appends the snippet code to the active buffer', async ({ page }) => {
  await load(page);
  await page.evaluate(() => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: '(bpm 124)' } });
  });
  await page
    .locator('.snippet-card', { hasText: 'Four on the Floor' })
    .locator('.snippet-insert-btn')
    .click();
  await expect
    .poll(() => page.evaluate(() => window.__REPULSE_EDITOR__.state.doc.toString()))
    .toBe('(bpm 124)\n\n(track :kick (seq :bd :bd :bd :bd))');
  await page.evaluate(() => window.__REPULSE_APP__.evaluate('(stop)'));
});
