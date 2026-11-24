import { expect, type Page, test } from '@playwright/test';

// Phase QA3 — dialogs, global keys and keyboard accessibility on the real app
// page. Only the first focus() of each check is programmatic; everything else
// is Tab / Enter / Escape / arrows.

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_APP__: any;
    __REPULSE_BUFFERS__: any;
    __REPULSE_HISTORY__: any;
  }
}

async function load(page: Page) {
  await page.addInitScript(() => {
    try { localStorage.clear(); } catch {}
  });
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
}

const evaluate = (page: Page, code: string) =>
  page.evaluate((c) => window.__REPULSE_APP__.evaluate(c), code);

async function setCode(page: Page, code: string, cursor?: number) {
  await page.evaluate(([c, pos]) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: c },
                 selection: { anchor: pos ?? c.length } });
  }, [code, cursor] as const);
}

const active = (page: Page) => page.evaluate(() => {
  const a = document.activeElement as HTMLElement | null;
  return a ? { tag: a.tagName, cls: a.className, text: a.textContent, act: a.getAttribute('data-act') } : null;
});

test.afterEach(async ({ page }) => {
  await evaluate(page, '(stop)').catch(() => {});
});

test('C dialog: Tab/Enter/Escape stay in the confirm dialog and focus returns', async ({ page }) => {
  await load(page);
  // one history entry, nothing playing
  await setCode(page, '(def x 1)');
  await page.locator('.cm-content').first().focus();
  await page.keyboard.press('Alt+Enter');
  await expect.poll(() => page.evaluate(() => window.__REPULSE_HISTORY__.entries().length), { timeout: 10_000 })
    .toBeGreaterThan(0);
  const entries = await page.evaluate(() => window.__REPULSE_HISTORY__.entries().length);
  // code that would play if Alt+Enter got through
  await setCode(page, '(track :a (seq :bd :sd))');

  await page.locator('#history-toggle-btn').click();
  const clear = page.locator('#history-panel [data-act=clear]');
  await clear.focus();
  await page.keyboard.press('Enter');
  const dlg = page.locator('.rp-dialog');
  await expect(dlg).toBeVisible();

  // aria-labelledby points at the message
  const labelled = await dlg.evaluate((d) => {
    const id = d.getAttribute('aria-labelledby');
    return id ? document.getElementById(id)?.textContent ?? null : null;
  });
  expect(labelled).toContain('Clear the evaluation history?');

  // Tab focuses Cancel, Enter cancels
  await page.keyboard.press('Tab');
  expect((await active(page))?.cls).toContain('rp-dialog-cancel');
  await page.keyboard.press('Enter');
  await expect(dlg).toHaveCount(0);
  expect(await page.evaluate(() => window.__REPULSE_HISTORY__.entries().length)).toBe(entries);
  expect((await active(page))?.act).toBe('clear');

  // Escape cancels
  await page.keyboard.press('Enter');
  await expect(dlg).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(dlg).toHaveCount(0);
  expect(await page.evaluate(() => window.__REPULSE_HISTORY__.entries().length)).toBe(entries);

  // Tab ×3 keeps focus inside; Alt+Enter does not evaluate
  await page.keyboard.press('Enter');
  await expect(dlg).toBeVisible();
  for (let i = 0; i < 3; i++) {
    await page.keyboard.press('Tab');
    expect(await page.evaluate(() => !!document.activeElement?.closest('.rp-dialog'))).toBe(true);
  }
  await page.keyboard.press('Alt+Enter');
  await page.waitForTimeout(300);
  expect(await page.evaluate(() => window.__REPULSE_APP__.playing())).toBe(false);
  await expect(dlg).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(dlg).toHaveCount(0);
  expect((await active(page))?.act).toBe('clear');
  expect(await page.evaluate(() => window.__REPULSE_HISTORY__.entries().length)).toBe(entries);
});

test('C submit modal: dialog semantics, BPM range, Escape closes only the modal', async ({ page }) => {
  await load(page);
  await page.locator('#snippet-toggle-btn').click();
  await expect(page.locator('#snippet-panel')).toBeVisible();
  await page.evaluate(() => window.__REPULSE_APP__.openSubmitModal());
  const modal = page.locator('#snippet-submit-modal');
  await expect(modal).toBeVisible();
  await expect(modal).toHaveAttribute('role', 'dialog');
  await expect(modal).toHaveAttribute('aria-modal', 'true');
  await expect(modal).toHaveAttribute('aria-labelledby', 'ssm-title-label');
  await expect(page.locator('#ssm-title-label')).toHaveText('Share as snippet');
  await expect(page.locator('#ssm-bpm')).toHaveAttribute('max', '640');
  await expect(page.locator('#ssm-bpm')).toHaveAttribute('min', '20');
  await expect(page.locator('#ssm-close-btn')).toHaveAttribute('aria-label', 'Close');
  await expect(page.locator('#ssm-title')).toBeFocused();

  await page.keyboard.press('Escape');
  await expect(modal).toBeHidden();
  await expect(page.locator('#snippet-panel')).toBeVisible();
  // the next Escape belongs to the panel again
  await page.keyboard.press('Escape');
  await expect(page.locator('#snippet-panel')).toBeHidden();
});

test('C keys: Cmd/Ctrl+A in the snippet search selects the input, not the editor', async ({ page }) => {
  await load(page);
  await setCode(page, '(seq :bd :sd)', 0);
  await page.locator('#snippet-toggle-btn').click();
  const search = page.locator('#snippet-search');
  await search.fill('kick');
  await search.focus();
  await page.keyboard.press('ControlOrMeta+A');
  const sel = await search.evaluate((i: HTMLInputElement) => [i.selectionStart, i.selectionEnd]);
  expect(sel).toEqual([0, 4]);
  const editorSel = await page.evaluate(() => {
    const m = window.__REPULSE_EDITOR__.state.selection.main;
    return m.to - m.from;
  });
  expect(editorSel).toBe(0);
  await expect(search).toBeFocused();
});

test('D keyboard: buffer close button, Enter twice deletes the buffer', async ({ page }) => {
  await load(page);
  await page.evaluate(() => window.__REPULSE_BUFFERS__.create('drums', ''));
  const tab = page.locator('.buf-tab[data-buffer="drums"]');
  await expect(tab).toHaveCount(1);
  await tab.locator('.buf-name').focus();
  await page.keyboard.press('Tab');
  await expect(tab.locator('.buf-close')).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(tab.locator('.buf-close')).toHaveText('delete?');
  await expect(tab.locator('.buf-close')).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.locator('.buf-tab[data-buffer="drums"]')).toHaveCount(0);
  await expect(page.locator('.buf-tab')).toHaveCount(1);
});

test('D keyboard: outline rows, session head and history entries react to Enter', async ({ page }) => {
  await load(page);
  await evaluate(page, '(bus :qa3lfo :control)');
  await setCode(page, '(def kick (seq :bd :bd))\n(track :a kick)');
  const row = page.locator('#outline-panel .ol-row[data-id]').first();
  await expect(row).toBeVisible();
  await expect(row).toHaveAttribute('role', 'button');
  await expect(row).toHaveAttribute('tabindex', '0');
  const id = await row.getAttribute('data-id');
  await row.focus();
  await page.keyboard.press('Enter');
  await expect.poll(() => page.evaluate(() => Array.from(window.__REPULSE_APP__.selection())))
    .toContain(id);

  const head = page.locator('#outline-panel .ol-session-head');
  await expect(head).toBeVisible();
  const before = await head.getAttribute('aria-expanded');
  await head.focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#outline-panel .ol-session-head'))
    .toHaveAttribute('aria-expanded', before === 'true' ? 'false' : 'true');

  // history entry
  await page.locator('.cm-content').first().focus();
  await page.keyboard.press('Alt+Enter');
  await expect.poll(() => page.evaluate(() => window.__REPULSE_HISTORY__.entries().length), { timeout: 10_000 })
    .toBeGreaterThan(0);
  await page.locator('#history-toggle-btn').click();
  const entry = page.locator('#history-panel .hist-entry').first();
  await expect(entry).toHaveAttribute('role', 'button');
  await entry.focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#history-panel .hist-entry').first()).toHaveClass(/selected/);
});

test('D keyboard: Mod-k i opens the insert menu, arrows + Enter insert, Escape closes', async ({ page }) => {
  await load(page);
  await setCode(page, '(seq :bd :sd)\n\n');
  await page.locator('.cm-content').first().focus();
  await page.keyboard.press('ControlOrMeta+k');
  await page.keyboard.press('i');
  const dd = page.locator('.insert-dropdown');
  await expect(dd).toBeVisible();
  await expect(dd).toHaveAttribute('role', 'menu');
  const first = dd.locator('.insert-dropdown-item').first();
  await expect(first).toBeFocused();
  await expect(first).toHaveAttribute('role', 'menuitem');

  await page.keyboard.press('ArrowDown');
  const label = await page.evaluate(() => (document.activeElement as HTMLElement).dataset.label);
  const firstLabel = await first.getAttribute('data-label');
  expect(label).toBeTruthy();
  expect(label).not.toBe(firstLabel);
  await page.keyboard.press('Enter');
  await expect(dd).toHaveCount(0);
  expect(await page.evaluate(() => window.__REPULSE_EDITOR__.state.doc.toString())).toContain(label!);

  // Escape closes and gives the editor its focus back
  await setCode(page, '(seq :bd :sd)\n\n');
  await page.locator('.cm-content').first().focus();
  await page.keyboard.press('ControlOrMeta+k');
  await page.keyboard.press('i');
  await expect(dd).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(dd).toHaveCount(0);
  expect(await page.evaluate(() => !!document.activeElement?.closest('.cm-editor'))).toBe(true);
  expect(await page.evaluate(() => window.__REPULSE_EDITOR__.state.doc.toString())).toBe('(seq :bd :sd)\n\n');
});

test('D ARIA: output live region, labelled header buttons, playing dot', async ({ page }) => {
  await load(page);
  await expect(page.locator('#output')).toHaveAttribute('aria-live', 'polite');
  await expect(page.locator('#tap-btn')).toHaveAttribute('aria-label', 'tap tempo');
  await expect(page.locator('#snippet-toggle-btn')).toHaveAttribute('aria-label', 'snippet library');
  await expect(page.locator('#history-toggle-btn')).toHaveAttribute('aria-label', 'evaluation history');
  const dot = page.locator('#playing-dot');
  await expect(dot).toHaveAttribute('role', 'img');
  await expect(dot).toHaveAttribute('aria-label', 'stopped');
  await setCode(page, '(track :a (seq :bd :sd))');
  await page.locator('.cm-content').first().focus();
  await page.keyboard.press('Alt+Enter');
  await expect(dot).toHaveAttribute('aria-label', 'playing', { timeout: 10_000 });
  await evaluate(page, '(stop)');
  await expect(dot).toHaveAttribute('aria-label', 'stopped');
});
