import { expect, type Page, test } from '@playwright/test';

// Phase WS1 — status head, inspector column and outline selection against the
// real app page (the offline TEST1 harness has no outline or inspector).

const W = `(def kick (seq :bd :_ :bd :_))
(track :drums (->> kick (amp 0.8) (fx :filter :freq 2400)))
(track :bass (->> (seq :c2 :eb2) (synth :saw) (amp 0.6) (decay 0.25)))
(fx :reverb 0.3)
(song
  (section :intro 4 [:drums])
  (section :verse 8 [:drums :bass])
  (section :chorus 8 :all))`;

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_APP__: {
      cycleDur: () => number;
      muted: () => string[];
      selection: () => string[];
      trackParam: (track: string, param: string) => number | string | null;
    };
  }
}

async function load(page: Page, text: string = W) {
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
  await setText(page, text);
  await expect(page.locator('#outline-panel .ol-name', { hasText: /^kick$/ })).toBeVisible();
}

async function setText(page: Page, text: string) {
  await page.evaluate((t) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
  }, text);
}

const doc = (page: Page) => page.evaluate(() => window.__REPULSE_EDITOR__.state.doc.toString());
const head = (page: Page) => page.evaluate(() => window.__REPULSE_EDITOR__.state.selection.main.head);
const row = (page: Page, id: string) => page.locator(`#outline-panel .ol-row[data-id="${id}"]`);
const inspector = (page: Page) => page.locator('#inspector');

async function selectedLines(page: Page): Promise<number[]> {
  return page.evaluate(() =>
    [...document.querySelectorAll('#editor-container .cm-line')]
      .map((l, i) => (l.classList.contains('ws-sel-line') ? i + 1 : 0))
      .filter((n) => n > 0),
  );
}

async function focusEditor(page: Page) {
  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
}

// Alt+Enter, then wait until the bass track plays with its amp from the code.
async function evalW(page: Page) {
  await focusEditor(page);
  await page.keyboard.press('Alt+Enter');
  await expect
    .poll(() => page.evaluate(() => window.__REPULSE_APP__.trackParam('bass', 'amp')), { timeout: 15_000 })
    .toBe(0.6);
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    try { localStorage.clear(); } catch {}
  });
  await page.setViewportSize({ width: 1280, height: 800 });
});

test('layout: no context panel, status head visible, inspector closed, wide editor', async ({ page }) => {
  await load(page);
  await expect(page.locator('#context-panel')).toHaveCount(0);
  await expect(page.locator('#status-head')).toBeVisible();
  await expect(page.locator('#status-head .sh-bpm')).toBeVisible();
  await expect(inspector(page)).toBeHidden();
  const box = await page.locator('#editor-container .cm-editor').boundingBox();
  expect(box!.width).toBeGreaterThanOrEqual(1000);
  await expect(page.locator('#status-head')).not.toContainText('cpu');
});

test('selecting a track inspects it without moving the cursor; clicking again closes', async ({ page }) => {
  await load(page);
  await evalW(page);
  await page.evaluate(() => window.__REPULSE_EDITOR__.dispatch({ selection: { anchor: 0 } }));
  await row(page, 'track::bass').click();
  await expect(inspector(page)).toBeVisible();
  const header = page.locator('#inspector .ins-header');
  await expect(header).toContainText('track');
  await expect(header).toContainText(':bass');
  await expect(header).toContainText('↗ L3');
  const amp = page.locator('#inspector .ins-srow', { has: page.locator('[data-key="bass|amp"]') });
  await expect(amp).toContainText('amp');
  await expect(amp).toContainText('0.60');
  const decay = page.locator('#inspector .ins-srow', { has: page.locator('[data-key="bass|decay"]') });
  await expect(decay).toContainText('decay');
  await expect(decay).toContainText('0.25');
  await expect(page.locator('#inspector .ins-trow', { hasText: 'synth' })).toContainText('saw');
  expect(await head(page)).toBe(0);
  expect(await selectedLines(page)).toEqual([3]);
  await expect(row(page, 'track::bass')).toHaveClass(/ol-sel/);
  expect(await page.evaluate(() => window.__REPULSE_APP__.selection())).toEqual(['track::bass']);
  // the status head shows the audio latency once audio runs
  await expect(page.locator('#status-head .sh-meta')).toHaveText(/latency \d+\.\d ms/);

  await row(page, 'track::bass').click();
  await expect(inspector(page)).toBeHidden();
  expect(await selectedLines(page)).toEqual([]);
});

test('⇧-click adds tracks; Esc closes and keeps editor focus; second Esc focuses the command bar', async ({ page }) => {
  await load(page);
  await row(page, 'track::drums').click();
  await row(page, 'track::bass').click({ modifiers: ['Shift'] });
  await expect(page.locator('#inspector .ins-header')).toContainText('tracks');
  await expect(page.locator('#inspector .ins-thead .ins-tname')).toHaveText([':drums', ':bass']);
  expect(await selectedLines(page)).toEqual([2, 3]);
  await expect(page.locator('#inspector .ins-hint')).toContainText('⇧ click a track to add / remove');

  await focusEditor(page);
  await page.keyboard.press('Escape');
  await expect(inspector(page)).toBeHidden();
  expect(await page.evaluate(() => document.activeElement?.closest('#editor-container .cm-editor') !== null)).toBe(true);
  expect(await selectedLines(page)).toEqual([]);

  await page.keyboard.press('Escape');
  expect(await page.evaluate(() => !!document.activeElement?.closest('#cmd-container'))).toBe(true);
});

test('F12 with a selection reveals it; Shift+F12 returns', async ({ page }) => {
  await load(page);
  await row(page, 'track::bass').click();
  expect(await head(page)).toBe(0);
  await focusEditor(page);
  await page.keyboard.press('F12');
  expect(await head(page)).toBe(91);
  await page.keyboard.press('Shift+F12');
  expect(await head(page)).toBe(0);
});

test('keyboard slider step writes the code, updates the live param and undoes in one step', async ({ page }) => {
  await load(page);
  await evalW(page);
  await row(page, 'track::bass').click();
  const slider = page.locator('#inspector .ins-slider[data-key="bass|amp"]');
  await slider.focus();
  await page.keyboard.press('Shift+ArrowRight');
  const text = await doc(page);
  expect(text).toContain('(amp 0.70)');
  expect(text).not.toContain('(amp 0.6)');
  await expect
    .poll(() => page.evaluate(() => window.__REPULSE_APP__.trackParam('bass', 'amp')), { timeout: 1000 })
    .toBe(0.7);
  await focusEditor(page);
  await page.keyboard.press('ControlOrMeta+z');
  expect(await doc(page)).toBe(W);
});

test('pointer drag rewrites only the amp number and is one undo step', async ({ page }) => {
  await load(page);
  await evalW(page);
  await row(page, 'track::bass').click();
  const knob = page.locator('#inspector .ins-slider[data-key="bass|amp"] .ins-knob');
  const b = (await knob.boundingBox())!;
  const x = b.x + b.width / 2;
  const y = b.y + b.height / 2;
  await page.mouse.move(x, y);
  await page.mouse.down();
  for (let i = 1; i <= 4; i++) {
    await page.mouse.move(x + 10 * i, y);
    await page.waitForTimeout(170);
  }
  await page.mouse.up();
  const text = await doc(page);
  const pre = W.slice(0, 142);
  const post = W.slice(145);
  expect(text.startsWith(pre)).toBe(true);
  expect(text.endsWith(post)).toBe(true);
  const num = text.slice(pre.length, text.length - post.length);
  expect(num).toMatch(/^\d+(\.\d+)?$/);
  expect(Number(num)).toBeGreaterThan(0.6);
  await focusEditor(page);
  await page.keyboard.press('ControlOrMeta+z');
  expect(await doc(page)).toBe(W);
});

test('BPM: click to edit, Enter commits, Esc cancels, invalid input is rejected, arrows step', async ({ page }) => {
  await load(page);
  const bpm = page.locator('#status-head .sh-bpm');
  const cycleDur = () => page.evaluate(() => window.__REPULSE_APP__.cycleDur());

  await bpm.click();
  await page.keyboard.type('90');
  await page.keyboard.press('Enter');
  expect(await cycleDur()).toBe(2.6666666666666665);
  await expect(bpm).toHaveText('90');

  await bpm.click();
  await page.keyboard.type('200');
  await page.keyboard.press('Escape');
  await expect(bpm).toHaveText('90');
  expect(await cycleDur()).toBe(2.6666666666666665);

  for (const bad of ['301', '39', '120.5']) {
    await page.locator('#status-head .sh-bpm').click();
    await page.keyboard.type(bad);
    await page.keyboard.press('Enter');
    await expect(page.locator('#status-head .sh-bpm-input.sh-invalid')).toBeVisible();
    expect(await cycleDur()).toBe(2.6666666666666665);
    await page.keyboard.press('Escape');
    await expect(bpm).toHaveText('90');
  }

  await bpm.focus();
  await page.keyboard.press('ArrowUp');
  await expect(bpm).toHaveText('91');
  await page.keyboard.press('Shift+ArrowDown');
  await expect(bpm).toHaveText('81');
});

test('section, def and fx inspectors', async ({ page }) => {
  await load(page);
  await evalW(page);

  await row(page, 'section:1').click();
  const header = page.locator('#inspector .ins-header');
  await expect(header).toContainText('section');
  await expect(header).toContainText(':verse');
  await expect(header).toContainText('↗ L7');
  await expect(page.locator('#inspector .ins-secrow')).toContainText('8 bars');
  await expect(page.locator('#inspector .ins-bars .ins-bar')).toHaveCount(8);
  await expect(page.locator('#inspector .ins-tname2')).toHaveText([':drums', ':bass']);
  const playingVerse = await page.locator('#outline-panel .ol-row.ol-playing', { hasText: ':verse' }).count();
  if (playingVerse === 0) await expect(page.locator('#inspector .ins-cue')).toHaveText('▶ cue');

  await row(page, 'def:kick').click();
  await expect(page.locator('#inspector .ins-pre')).toContainText('(def kick (seq :bd :_ :bd :_))');
  await expect(page.locator('#inspector .ins-note')).toHaveText('Used by :drums.');

  // fx group after the song rows, child :reverb on L4
  const ids = await page.locator('#outline-panel .ol-row[data-id]').evaluateAll((els) =>
    els.map((e) => e.getAttribute('data-id')),
  );
  expect(ids.indexOf('fx')).toBeGreaterThan(ids.indexOf('section:2'));
  expect(ids.indexOf('fx:0')).toBe(ids.indexOf('fx') + 1);
  await expect(row(page, 'fx:0')).toContainText(':reverb');
  await expect(row(page, 'fx:0')).toContainText('L4');
  await row(page, 'fx:0').click();
  await expect(page.locator('#inspector .ins-slider[data-key="fx|reverb|wet"]')).toBeVisible();
  await expect(page.locator('#inspector .ins-foot')).toHaveText('applies after all tracks');
  expect(await selectedLines(page)).toEqual([4]);
});

test('M in the inspector mutes the track and marks the outline row', async ({ page }) => {
  await load(page);
  await evalW(page);
  await row(page, 'track::bass').click();
  await page.locator('#inspector .ins-m[data-track="bass"]').click();
  expect(await page.evaluate(() => window.__REPULSE_APP__.muted())).toEqual(['bass']);
  await expect(row(page, 'track::bass')).toHaveClass(/ol-muted/);
  await expect(page.locator('#inspector .ins-status')).toHaveText('muted');
  await page.locator('#inspector .ins-m[data-track="bass"]').click();
  expect(await page.evaluate(() => window.__REPULSE_APP__.muted())).toEqual([]);
  await page.locator('#inspector .ins-s[data-track="bass"]').click();
  expect(await page.evaluate(() => window.__REPULSE_APP__.muted())).toEqual(['drums']);
  await expect(page.locator('#inspector .ins-status')).toHaveText('solo');
  await page.locator('#inspector .ins-s[data-track="bass"]').click();
  expect(await page.evaluate(() => window.__REPULSE_APP__.muted())).toEqual([]);
});

test('inspector width is clamped and persists across reloads', async ({ page }) => {
  await load(page);
  await row(page, 'track::bass').click();
  await expect(inspector(page)).toBeVisible();
  expect((await inspector(page).boundingBox())!.width).toBe(220);
  const h = (await page.locator('#inspector .ins-resize').boundingBox())!;
  await page.mouse.move(h.x + h.width / 2, h.y + 50);
  await page.mouse.down();
  await page.mouse.move(h.x + h.width / 2 + 250, h.y + 50);
  await page.mouse.move(h.x + h.width / 2 + 500, h.y + 50);
  await page.mouse.up();
  expect((await inspector(page).boundingBox())!.width).toBe(380);
  const stored = await page.evaluate(() => localStorage.getItem('repulse:inspector-width'));
  expect(stored).toBe('380');

  // beforeEach's init script clears storage on every load, so re-seed the saved value
  await page.addInitScript((w) => {
    try { localStorage.setItem('repulse:inspector-width', w); } catch {}
  }, stored!);
  await page.reload();
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__);
  await setText(page, W);
  await expect(row(page, 'track::bass')).toBeVisible();
  await row(page, 'track::bass').click();
  await expect(inspector(page)).toBeVisible();
  expect((await inspector(page).boundingBox())!.width).toBe(380);
});

test('session block summarises buses and opens on click', async ({ page }) => {
  await load(page, `(bus :lfo :control)\n${W}`);
  await focusEditor(page);
  await page.keyboard.press('Alt+Enter');
  const head = page.locator('#outline-panel .ol-session-head');
  await expect(head).toContainText('session', { timeout: 15_000 });
  await expect(head).toContainText('buses 1');
  await expect(page.locator('#outline-panel .ol-session-row')).toHaveCount(0);
  await head.click();
  await expect(page.locator('#outline-panel .ol-session-row', { hasText: ':lfo' })).toContainText('control');
  expect(await page.evaluate(() => localStorage.getItem('repulse:outline-session-open'))).toBe('1');
});

test('clicking a progress segment cues that section at the next bar', async ({ page }) => {
  await load(page, `(bpm 480)\n${W}`);
  await focusEditor(page);
  await page.keyboard.press('Alt+Enter');
  const playing = page.locator('#outline-panel .ol-row.ol-playing');
  await expect(playing).toContainText(':intro', { timeout: 15_000 });
  await expect(page.locator('#status-head .sh-seg')).toHaveCount(3);
  await page.locator('#status-head .sh-seg[data-index="2"]').click();
  // the natural arrival at :chorus is 12 bars (6 s at 480 BPM) away
  await expect(playing).toContainText(':chorus', { timeout: 2000 });
  await expect(page.locator('#status-head .sh-seg[data-index="2"]')).toHaveClass(/playing/);
});

test('collapsing the outline clears the selection', async ({ page }) => {
  await load(page);
  await row(page, 'track::bass').click();
  await expect(inspector(page)).toBeVisible();
  await page.locator('#outline-panel .ol-collapse').click();
  await expect(inspector(page)).toBeHidden();
  expect(await page.evaluate(() => window.__REPULSE_APP__.selection())).toEqual([]);
});
