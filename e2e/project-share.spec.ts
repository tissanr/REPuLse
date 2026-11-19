import { expect, type Page, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

// Phase TEST3 — multi-buffer projects and share links against the real app page:
// #v3 round trip, #v2 fixture, size limits, buffer-name errors, (use …) in the
// command bar, (upd)/F9 on a non-main tab, snippet insert target.

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_BUFFERS__: any;
    __REPULSE_APP__: any;
    __REPULSE_HISTORY__: any;
  }
}

async function fresh(page: Page) {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_BUFFERS__ && !!window.__REPULSE_APP__);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.waitForFunction(() => !!window.__REPULSE_BUFFERS__ && !!window.__REPULSE_APP__);
}

async function setText(page: Page, text: string) {
  await page.evaluate((t) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
  }, text);
}

const text = (page: Page): Promise<string> =>
  page.evaluate(() => window.__REPULSE_EDITOR__.state.doc.toString());
const project = (page: Page) => page.evaluate(() => window.__REPULSE_BUFFERS__.project());
const switchTo = (page: Page, name: string) =>
  page.evaluate((n) => window.__REPULSE_BUFFERS__.switchTo(n), name);
const output = (page: Page) => page.locator('#output');
const playing = (page: Page): Promise<boolean> => page.evaluate(() => window.__REPULSE_APP__.playing());
const evaluate = (page: Page, code: string) => page.evaluate((c) => window.__REPULSE_APP__.evaluate(c), code);

async function create(page: Page, name: string, code: string) {
  const r = await page.evaluate(([n, c]) => window.__REPULSE_BUFFERS__.create(n, c), [name, code]);
  expect(r.ok).toBe(name);
}

async function addViaTabs(page: Page, name: string) {
  await page.locator('.buf-add').click();
  await page.locator('.buf-name-input').fill(name);
  await page.locator('.buf-name-input').press('Enter');
}

const clipboard = (page: Page): Promise<string> => page.evaluate(() => navigator.clipboard.readText());

test('P1: a 3-buffer project round-trips through a #v3 share link', async ({ page, browser, baseURL }) => {
  const context = page.context();
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await fresh(page);
  await setText(page, '(use :drums :bass)\n(song (section :a 2 :all))');
  await create(page, 'drums', '(track :kick (seq :bd :_ :bd :_))');
  await create(page, 'bass', '(track :bass (->> (seq :c2 :g2) (synth :saw)))');
  await switchTo(page, 'drums');
  await evaluate(page, '(bpm 140)');
  const before = await project(page);

  await page.locator('#share-btn').click();
  await expect(output(page)).toHaveText('URL copied to clipboard');
  const link = await clipboard(page);
  expect(link.startsWith(`${baseURL}/#v3:`)).toBe(true);

  const ctx2 = await browser.newContext({ baseURL: baseURL! });
  const page2 = await ctx2.newPage();
  await page2.setViewportSize({ width: 1280, height: 800 });
  await page2.goto(link);
  await page2.waitForFunction(() => !!window.__REPULSE_BUFFERS__ && !!window.__REPULSE_APP__);
  expect(await project(page2)).toEqual(before);
  expect((await project(page2)).active).toBe('drums');
  await expect(page2.locator('.buf-tab')).toHaveCount(3);
  await expect(page2.locator('.buf-tab[data-buffer="drums"]')).toHaveClass(/active/);
  expect(await text(page2)).toBe('(track :kick (seq :bd :_ :bd :_))');
  // the BPM is part of the restored session (saved back to localStorage)
  await expect
    .poll(() => page2.evaluate(() => JSON.parse(localStorage.getItem('repulse-session') ?? '{}').bpm))
    .toBe(140);
  // opening a link never starts playback
  await page2.waitForTimeout(800);
  expect(await playing(page2)).toBe(false);
  await ctx2.close();
});

test('P2: a pre-SONG4 #v2 link loads as one main tab', async ({ page, baseURL }) => {
  const link = readFileSync(join(__dirname, 'fixtures', 'v2-link.txt'), 'utf8').trim();
  expect(link.startsWith('#v2:')).toBe(true);
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto(`${baseURL}/${link}`);
  await page.waitForFunction(() => !!window.__REPULSE_BUFFERS__ && !!window.__REPULSE_APP__);
  await expect(page.locator('.buf-tab')).toHaveCount(1);
  await expect(page.locator('.buf-tab.main.active')).toHaveText('main');
  expect(await text(page)).toBe(
    '(track :kick (seq :bd :_ :bd :_))\n(track :hats (fast 2 (seq :hh :oh)))  ; äö',
  );
  expect((await project(page)).buffers.length).toBe(1);
  await expect
    .poll(() => page.evaluate(() => JSON.parse(localStorage.getItem('repulse-session') ?? '{}').bpm))
    .toBe(128);
  expect(await playing(page)).toBe(false);
});

test('P3: oversized share and snapshot links fail without touching the clipboard', async ({ page }) => {
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
  await fresh(page);
  await page.waitForFunction(() => window.__REPULSE_HISTORY__.ready());
  await page.evaluate(() => navigator.clipboard.writeText('sentinel'));
  await setText(page, ';' + 'x'.repeat(70000));

  const tooBig = /^Share failed: link would be \d+ characters \(limit 64000\)\. Export the project as a \.repulse file instead\.$/;
  await page.locator('#share-btn').click();
  await expect(output(page)).toHaveText(tooBig);
  expect(await clipboard(page)).toBe('sentinel');

  // the snapshot *link* in the history panel
  await evaluate(page, '(snapshot! "big")');
  await expect(output(page)).toHaveText('snapshot "big" saved');
  await page.locator('#history-toggle-btn').click();
  await page.locator('.hist-snap[data-name="big"] [data-act="link"]').click();
  await expect(output(page)).toHaveText(tooBig);
  expect(await clipboard(page)).toBe('sentinel');
});

test('P4: buffer-name errors for Drums!, main and the 33rd buffer', async ({ page }) => {
  await fresh(page);
  await addViaTabs(page, 'Drums!');
  await expect(output(page)).toHaveText(
    'Error: invalid buffer name "Drums!" — use a-z, 0-9 and -, starting with a letter (max 32)',
  );
  await page.locator('.buf-name-input').press('Escape');

  await addViaTabs(page, 'main');
  await expect(output(page)).toHaveText('Error: a buffer named "main" already exists');
  await page.locator('.buf-name-input').press('Escape');

  // main + 31 more = 32 buffers; the next create is refused
  for (let i = 0; i < 40; i += 1) {
    const r = await page.evaluate((n) => window.__REPULSE_BUFFERS__.create(`b-${n}`, ''), i);
    if (r.error) {
      expect(i).toBe(31);
      expect(r.error).toBe('too many buffers (max 32)');
      break;
    }
  }
  expect((await project(page)).buffers.length).toBe(32);
  await addViaTabs(page, 'one-too-many');
  await expect(output(page)).toHaveText('Error: too many buffers (max 32)');
  expect((await project(page)).buffers.length).toBe(32);
});

test('P5: (use :drums) in the command bar explains it needs a project evaluation', async ({ page }) => {
  await fresh(page);
  await create(page, 'drums', '(def kick (seq :bd :sd))');
  await page.locator('#cmd-container .cm-content').click();
  await page.keyboard.type('(use :drums)');
  await page.keyboard.press('Enter');
  await expect(output(page)).toHaveText(
    'Error: use only works when the project is evaluated (Alt+Enter in the editor)',
  );
});

test('P6: F9 and (upd) take only the active tab text', async ({ page }) => {
  await fresh(page);
  // main would fail to evaluate; the drums tab is a valid pattern
  await setText(page, '(qqqqqqqq)');
  await create(page, 'drums', '(seq :bd :sd)');
  expect(await playing(page)).toBe(false);

  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
  await page.keyboard.press('F9');
  await expect(output(page)).toHaveText('updated');
  expect(await playing(page)).toBe(true);
  await evaluate(page, '(stop)');
  await expect.poll(() => playing(page)).toBe(false);

  // (upd) from the command bar: same active-tab rule
  await page.locator('#cmd-container .cm-content').click();
  await page.keyboard.type('(upd)');
  await page.keyboard.press('Enter');
  await expect(output(page)).toHaveText('updated');
  expect(await playing(page)).toBe(true);
  await evaluate(page, '(stop)');

  // the same key on main evaluates main's text and reports its error
  await switchTo(page, 'main');
  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
  await page.keyboard.press('F9');
  await expect(output(page)).toHaveText('Error: Undefined symbol: qqqqqqqq');
});

test('P7: snippet insert while drums is active appends to drums, not main', async ({ page }) => {
  await page.route('**/snippets/library.json', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        version: 1,
        snippets: [
          {
            id: 'four-on-the-floor',
            title: 'Four on the Floor',
            author: 'repulse',
            tags: ['rhythm'],
            bpm: 124,
            description: 'Classic 4/4 house kick pattern',
            code: '(track :kick (seq :bd :bd :bd :bd))',
          },
        ],
      }),
    }));
  await fresh(page);
  await setText(page, '(use :drums)');
  await create(page, 'drums', '(def hat (seq :hh))');
  await page.locator('#snippet-toggle-btn').click();
  await page.locator('.snippet-card .snippet-insert-btn').click();
  await expect
    .poll(() => text(page))
    .toBe('(def hat (seq :hh))\n\n(track :kick (seq :bd :bd :bd :bd))');
  const p = await project(page);
  expect(p.buffers.find((b: any) => b.name === 'main').code).toBe('(use :drums)');
  await evaluate(page, '(stop)');
});
