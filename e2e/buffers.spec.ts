import { expect, type Page, test } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

// Phase SONG4 — multi-buffer projects against the real app page.
// Every Playwright test gets a fresh browser context, so localStorage starts
// empty; `fresh()` clears it explicitly as well.

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_BUFFERS__: any;
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

async function text(page: Page): Promise<string> {
  return page.evaluate(() => window.__REPULSE_EDITOR__.state.doc.toString());
}

async function project(page: Page) {
  return page.evaluate(() => window.__REPULSE_BUFFERS__.project());
}

/** Replace the whole project: main gets `main`, then each [name, code] buffer is created. */
async function setProject(page: Page, main: string, others: [string, string][]) {
  await page.evaluate(() => window.__REPULSE_BUFFERS__.switchTo('main'));
  await setText(page, main);
  for (const [n, c] of others) {
    const r = await page.evaluate(([n, c]) => window.__REPULSE_BUFFERS__.create(n, c), [n, c]);
    expect(r.ok).toBe(n);
  }
}

async function switchTo(page: Page, name: string) {
  await page.evaluate((n) => window.__REPULSE_BUFFERS__.switchTo(n), name);
}

const tab = (page: Page, name: string) => page.locator(`.buf-tab[data-buffer="${name}"]`);
const output = (page: Page) => page.locator('#output');

async function altEnter(page: Page) {
  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
  await page.keyboard.press('Alt+Enter');
}

async function lineOfHead(page: Page): Promise<number> {
  return page.evaluate(() => {
    const s = window.__REPULSE_EDITOR__.state;
    return s.doc.lineAt(s.selection.main.head).number;
  });
}

async function modClick(page: Page, needle: string, line: number) {
  const mac = await page.evaluate(() => /Mac/.test(navigator.platform));
  const { x, y } = await page.evaluate(
    ({ needle, line }) => {
      const v = window.__REPULSE_EDITOR__;
      const l = v.state.doc.line(line);
      const r = v.coordsAtPos(l.from + l.text.indexOf(needle) + 1);
      return { x: r.left + 2, y: (r.top + r.bottom) / 2 };
    },
    { needle, line },
  );
  await page.keyboard.down(mac ? 'Meta' : 'Control');
  await page.mouse.click(x, y);
  await page.keyboard.up(mac ? 'Meta' : 'Control');
}

test.beforeEach(async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
});

test('W1: first load has a single active main tab', async ({ page }) => {
  await fresh(page);
  await expect(page.locator('.buf-tab')).toHaveCount(1);
  await expect(page.locator('.buf-tab.main.active')).toHaveText('main');
  await expect(page.locator('.buf-tab.main .buf-close')).toHaveCount(0);
  const p = await project(page);
  expect(p.buffers.length).toBe(1);
  expect(p.main).toBe('main');
});

test('W2: create, rename, switch, persist and delete buffers', async ({ page }) => {
  await fresh(page);
  await setText(page, '(use :kit)');

  page.once('dialog', (d) => d.accept('drums'));
  await page.locator('.buf-add').click();
  await expect(tab(page, 'drums')).toHaveClass(/active/);
  await setText(page, '(track :kick (seq :bd))');

  page.once('dialog', (d) => d.accept('kit'));
  await tab(page, 'drums').dblclick();
  await expect(tab(page, 'kit')).toHaveClass(/active/);
  await expect(tab(page, 'drums')).toHaveCount(0);

  page.once('dialog', (d) => d.accept('Drums!'));
  await page.locator('.buf-add').click();
  await expect(output(page)).toHaveText(
    'Error: invalid buffer name "Drums!" — use a-z, 0-9 and -, starting with a letter (max 32)',
  );
  await expect(page.locator('.buf-tab')).toHaveCount(2);

  await tab(page, 'main').click();
  expect(await text(page)).toBe('(use :kit)');
  await tab(page, 'kit').click();
  expect(await text(page)).toBe('(track :kick (seq :bd))');

  // the session save is debounced by 300 ms
  await page.waitForTimeout(600);
  await page.reload();
  await page.waitForFunction(() => !!window.__REPULSE_BUFFERS__);
  expect(
    await page.locator('.buf-tab').evaluateAll((els) => els.map((e) => e.getAttribute('data-buffer'))),
  ).toEqual(['main', 'kit']);
  await expect(tab(page, 'kit')).toHaveClass(/active/);
  expect(await text(page)).toBe('(track :kick (seq :bd))');
  await tab(page, 'main').click();
  expect(await text(page)).toBe('(use :kit)');

  page.once('dialog', (d) => d.accept());
  await tab(page, 'kit').locator('.buf-close').click();
  await expect(page.locator('.buf-tab')).toHaveCount(1);
  await expect(page.locator('.buf-tab.main .buf-close')).toHaveCount(0);
  expect((await project(page)).buffers.map((b: any) => b.name)).toEqual(['main']);
});

test('W3: Alt+Enter in a non-main tab evaluates the project from main', async ({ page }) => {
  await fresh(page);
  await setProject(page, '(use :drums)\n(song (section :a 4 :all))', [['drums', '(track :kick (seq :bd :sd))']]);
  await expect(tab(page, 'drums')).toHaveClass(/active/);
  await altEnter(page);
  await expect(output(page)).toHaveText(/^song: 1 sections/);
  await expect(tab(page, 'drums')).toHaveClass(/active/);
  await page.locator('#play-btn').click(); // stop
});

test('W4: an error in a used buffer switches to it and underlines the spot', async ({ page }) => {
  await fresh(page);
  await setProject(page, '(use :drums)', [['drums', '(track :kick (seq :bd))\n(def x (qqqqqqqq 2 kick))']]);
  await switchTo(page, 'main');
  await altEnter(page);
  await expect(output(page)).toHaveText('Error in :drums: Undefined symbol: qqqqqqqq');
  await expect(tab(page, 'drums')).toHaveClass(/active/);
  await expect(page.locator('.cm-lintRange-error')).toHaveText('qqqqqqqq');
  const t = await text(page);
  expect(t.indexOf('qqqqqqqq')).toBe(32);
});

test('W5: Cmd/Ctrl-click jumps into another buffer and Shift+F12 returns', async ({ page }) => {
  await fresh(page);
  await setProject(page, '(use :drums)\n(fast 2 kick)', [['drums', '(def kick (seq :bd))']]);
  await switchTo(page, 'main');
  const before = await page.evaluate(() => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ selection: { anchor: 3 } });
    return v.state.selection.main.head;
  });
  await modClick(page, 'kick', 2);
  await expect(tab(page, 'drums')).toHaveClass(/active/);
  expect(await lineOfHead(page)).toBe(1);
  await page.keyboard.press('Shift+F12');
  await expect(tab(page, 'main')).toHaveClass(/active/);
  expect(await page.evaluate(() => window.__REPULSE_EDITOR__.state.selection.main.head)).toBe(before);
});

test('W6: a main-only project plays a pattern exactly like before', async ({ page }) => {
  await fresh(page);
  await setText(page, '(seq :bd :sd)');
  await altEnter(page);
  await expect(output(page)).toHaveText('playing pattern — Alt+Enter to re-evaluate, (stop) to stop');
  await page.locator('#play-btn').click(); // stop
});

test('W7: export downloads main.repulse; import replaces the project or rejects bad files', async ({ page }, info) => {
  await fresh(page);
  await setProject(page, '(use :drums)\n(song (section :a 4 :all))', [['drums', '(track :kick (~ "bd sd"))']]);

  const [download] = await Promise.all([page.waitForEvent('download'), page.locator('[data-act="export"]').click()]);
  expect(download.suggestedFilename()).toBe('main.repulse');
  const file = info.outputPath('main.repulse');
  await download.saveAs(file);
  const exported = readFileSync(file, 'utf8');
  expect(exported).toBe(
    '{:version 3\n' +
      ' :main "main"\n' +
      ' :buffers [{:name "main" :code "(use :drums)\\n(song (section :a 4 :all))"}\n' +
      '           {:name "drums" :code "(track :kick (~ \\"bd sd\\"))"}]}\n',
  );

  // replace the project with something else, then import the export back
  await page.evaluate(() => window.__REPULSE_BUFFERS__.switchTo('drums'));
  page.once('dialog', (d) => d.accept());
  await tab(page, 'drums').locator('.buf-close').click();
  await setProject(page, '(seq :hh)', [['other', '(def a 1)']]);

  page.once('dialog', (d) => d.accept());
  await page.locator('.buf-import-input').setInputFiles(file);
  await expect(output(page)).toHaveText('imported main.repulse — 2 buffers');
  const p = await project(page);
  expect(p.buffers).toEqual([
    { name: 'main', code: '(use :drums)\n(song (section :a 4 :all))' },
    { name: 'drums', code: '(track :kick (~ "bd sd"))' },
  ]);
  expect(p.active).toBe('main');

  const bad = info.outputPath('bad.repulse');
  writeFileSync(bad, '[1 2]');
  await page.locator('.buf-import-input').setInputFiles(bad);
  await expect(output(page)).toHaveText('Import failed: expected a map');
  expect((await project(page)).buffers.length).toBe(2);
});

test('W8: the embed does not depend on buffers, project-eval or goto', async () => {
  const src = readFileSync(join(__dirname, '..', 'app', 'src', 'repulse', 'embed.cljs'), 'utf8');
  expect(src).not.toMatch(/repulse\.ui\.buffers|repulse\.project-eval|repulse\.ui\.goto/);
  expect(src).toMatch(/editor\/make-editor\s+\S+\s+\S+\s+\S+/);
});
