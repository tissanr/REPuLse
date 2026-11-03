import { expect, type Page, test } from '@playwright/test';

// Phase SONG3 — outline panel + go-to-definition against the real app page.

const R = `(def kick (seq :bd :_ :bd :_))
(def snare (seq :_ :sd))
; (def ghost (seq :x)) — commented out, must not appear
(defn humanize [p] (amp 0.9 p))
(defsynth pad [freq] (saw freq))
(track :drums (stack kick snare (seq "(not a paren)")))
(track :bass (seq :c2 :eb2))
(song
  (section :intro 4 [:drums])
  (section :verse 8 [:drums :bass])
  (section :outro 2 :all))`;

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
  }
}

async function load(page: Page, text: string = R) {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(m.text());
  });
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__);
  await setText(page, text);
  // the outline re-extracts 150 ms after the edit; wait for the new content
  await expect(page.locator('#outline-panel .ol-name', { hasText: /^kick$/ })).toBeVisible();
  errors.length = 0; // ignore boot-time noise (e.g. missing optional assets)
  return errors;
}

async function setText(page: Page, text: string) {
  await page.evaluate((t) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
  }, text);
}

async function head(page: Page): Promise<number> {
  return page.evaluate(() => window.__REPULSE_EDITOR__.state.selection.main.head);
}

async function lineOfHead(page: Page): Promise<number> {
  return page.evaluate(() => {
    const s = window.__REPULSE_EDITOR__.state;
    return s.doc.lineAt(s.selection.main.head).number;
  });
}

async function setCursor(page: Page, pos: number) {
  await page.evaluate((p) => window.__REPULSE_EDITOR__.dispatch({ selection: { anchor: p } }), pos);
}

// Viewport coordinates of the middle of `needle`, searched from the nth line.
async function coordsOf(page: Page, needle: string, line: number) {
  return page.evaluate(
    ({ needle, line }) => {
      const v = window.__REPULSE_EDITOR__;
      const l = v.state.doc.line(line);
      const idx = l.text.indexOf(needle);
      const r = v.coordsAtPos(l.from + idx + 1);
      return { x: r.left + 2, y: (r.top + r.bottom) / 2 };
    },
    { needle, line },
  );
}

async function modClick(page: Page, needle: string, line: number) {
  const mac = await page.evaluate(() => /Mac/.test(navigator.platform));
  const { x, y } = await coordsOf(page, needle, line);
  await page.keyboard.down(mac ? 'Meta' : 'Control');
  await page.mouse.click(x, y);
  await page.keyboard.up(mac ? 'Meta' : 'Control');
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    try { localStorage.clear(); } catch {}
  });
  await page.setViewportSize({ width: 1280, height: 800 });
});

test('outline lists 10 rows and the count for the reference buffer', async ({ page }) => {
  await load(page);
  const rows = page.locator('#outline-panel .ol-row');
  await expect(rows).toHaveCount(10);
  await expect(page.locator('#outline-panel .ol-count')).toHaveText('10');
  await expect(page.locator('#outline-panel .ol-partial')).toHaveCount(0);
  const names = await page.locator('#outline-panel .ol-name').allInnerTexts();
  expect(names.join('|')).not.toContain('ghost');
  expect(names[0]).toBe('kick');
  expect(await page.locator('#outline-panel .ol-kind').allInnerTexts()).toContain('synth');
});

test('clicking a row moves the cursor to its paren, reveals it and focuses the editor', async ({ page }) => {
  await load(page);
  await page.locator('#outline-panel .ol-row', { hasText: ':bass' }).first().click();
  const expected = R.indexOf('(track :bass');
  expect(await head(page)).toBe(expected);
  expect(await lineOfHead(page)).toBe(7);
  expect(await page.evaluate(() => document.activeElement?.closest('.cm-editor') !== null)).toBe(true);
  const visible = await page.evaluate(() => {
    const v = window.__REPULSE_EDITOR__;
    const r = v.coordsAtPos(v.state.selection.main.head);
    const box = v.scrollDOM.getBoundingClientRect();
    return !!r && r.top >= box.top && r.bottom <= box.bottom;
  });
  expect(visible).toBe(true);
});

test('Cmd/Ctrl-click jumps to definitions; unknown names do nothing', async ({ page }) => {
  const errors = await load(page);
  await modClick(page, ':bass', 10);
  expect(await lineOfHead(page)).toBe(7);

  await setCursor(page, 0);
  await modClick(page, 'kick', 6);
  expect(await lineOfHead(page)).toBe(1);

  await setCursor(page, R.indexOf('(song'));
  const before = await head(page);
  await modClick(page, ':bd', 1);
  expect(await head(page)).toBe(before);
  expect(errors).toEqual([]);
});

test('F12 jumps to the definition, Shift+F12 returns', async ({ page }) => {
  await load(page);
  const pos = R.indexOf('snare', R.indexOf('(track :drums')) + 2;
  await setCursor(page, pos);
  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
  await page.keyboard.press('F12');
  expect(await lineOfHead(page)).toBe(2);
  await page.keyboard.press('Shift+F12');
  expect(await lineOfHead(page)).toBe(6);
  expect(await head(page)).toBe(pos);
});

test('outline follows edits within 300 ms and shows ~ partial on errors', async ({ page }) => {
  await load(page);
  await page.evaluate(() => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, insert: '(def hats (seq :hh))\n' } });
  });
  await expect(page.locator('#outline-panel .ol-name', { hasText: /^hats$/ })).toBeVisible({ timeout: 300 });

  // delete the ')' that closes (track :drums ...)
  await page.evaluate(() => {
    const v = window.__REPULSE_EDITOR__;
    const t = v.state.doc.toString();
    const at = t.indexOf('"(not a paren)")))') + '"(not a paren)"))'.length;
    v.dispatch({ changes: { from: at, to: at + 1 } });
  });
  await expect(page.locator('#outline-panel .ol-partial')).toBeVisible({ timeout: 300 });
  const names = await page.locator('#outline-panel .ol-name').allInnerTexts();
  for (const n of ['hats', 'kick', 'snare', 'humanize', 'pad']) expect(names).toContain(n);
});

test('cursor-inside row tracks the cursor', async ({ page }) => {
  await load(page);
  await setCursor(page, R.indexOf(':verse') + 2);
  const cur = page.locator('#outline-panel .ol-row.ol-cursor');
  await expect(cur).toHaveCount(1);
  await expect(cur).toContainText(':verse');
  await setCursor(page, R.indexOf(':outro') + 2);
  await expect(cur).toContainText(':outro');
  await setCursor(page, 3);
  await expect(cur).toContainText('kick');
});

test('collapse to the rail, restore, and persist across reload', async ({ page }) => {
  await load(page);
  const panel = page.locator('#outline-panel');
  expect((await panel.boundingBox())!.width).toBe(192);
  await page.locator('#outline-panel .ol-collapse').click();
  await expect(panel).toHaveClass(/collapsed/);
  expect((await panel.boundingBox())!.width).toBe(28);
  expect(await page.evaluate(() => localStorage.getItem('repulse:outline-collapsed'))).toBe('1');

  // addInitScript clears storage on every load, so re-seed it for the reload
  await page.addInitScript(() => {
    try { localStorage.setItem('repulse:outline-collapsed', '1'); } catch {}
  });
  await page.reload();
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__);
  await expect(panel).toHaveClass(/collapsed/);
  await page.locator('#outline-panel .ol-rail').click();
  await expect(panel).not.toHaveClass(/collapsed/);
  expect((await panel.boundingBox())!.width).toBe(192);
});

test('Mod-Shift-o toggles the outline', async ({ page }) => {
  await load(page);
  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
  const mac = await page.evaluate(() => /Mac/.test(navigator.platform));
  await page.keyboard.press(`${mac ? 'Meta' : 'Control'}+Shift+O`);
  await expect(page.locator('#outline-panel')).toHaveClass(/collapsed/);
  await page.keyboard.press(`${mac ? 'Meta' : 'Control'}+Shift+O`);
  await expect(page.locator('#outline-panel')).not.toHaveClass(/collapsed/);
});

test('the playing section is marked while a song plays', async ({ page }) => {
  await load(page, `(bpm 480)\n${R}`);
  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
  await page.keyboard.press('Alt+Enter');
  const playing = page.locator('#outline-panel .ol-row.ol-playing');
  await expect(playing).toContainText(':intro', { timeout: 15_000 });
  await expect(page.locator('#outline-panel .ol-row.ol-playing .ol-name')).toHaveCSS('font-weight', '700');
  await expect(playing).toContainText(':verse', { timeout: 15_000 });
});
