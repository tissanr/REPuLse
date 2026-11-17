import { expect, type Page, test } from '@playwright/test';

// Phase WS4 — AI assistant rail, ⌘I shortcut, working state and context chip,
// against the real app page with a mocked provider (no network).

const C = `(def kick (seq :bd :_ :bd :_))
(track :drums (->> kick (amp 0.8)))
(track :bass (->> (seq :c2 :eb2) (amp 0.6)))
(song
  (section :intro 4 [:drums])
  (section :chorus 8 :all))`;

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_APP__: any;
  }
}

const SEED = {
  'repulse:ai:enabled': 'true',
  'repulse:ai:provider': 'anthropic',
  'repulse:ai:key:anthropic': 'test-key',
};

const TEXT = { content: [{ type: 'text', text: 'Try (fast 2 kick).' }], usage: { input_tokens: 10, output_tokens: 5 } };
const EDIT = {
  content: [{ type: 'tool_use', id: 'toolu_1', name: 'propose_edit',
              input: { match: '(amp 0.6)', replacement: '(amp 0.4)' } }],
  usage: { input_tokens: 10, output_tokens: 5 },
};

let requests: any[] = [];
let release: () => void = () => {};

// Clear storage once per test (sessionStorage survives page.reload()), then seed.
async function setup(page: Page, seed: Record<string, string> = {}) {
  requests = [];
  release = () => {};
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.addInitScript((s) => {
    try {
      if (!sessionStorage.getItem('ws4-init')) {
        localStorage.clear();
        for (const [k, v] of Object.entries(s)) localStorage.setItem(k, v as string);
        sessionStorage.setItem('ws4-init', '1');
      }
    } catch {}
  }, seed);
  await page.route('**/api/ai-models', (r) => r.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ data: [{ id: 'claude-sonnet-5-5', display_name: 'Claude Sonnet 5.5', created_at: '2026-01-01T00:00:00Z' }] }),
  }));
}

async function mockStream(page: Page, responses: object[], gated = false) {
  await page.route('**/api/ai-stream', async (route) => {
    const env = route.request().postDataJSON();
    requests.push(JSON.parse(env.body));
    const n = requests.length;
    if (gated) await new Promise<void>((r) => (release = r));
    const next = responses[Math.min(n - 1, responses.length - 1)];
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(next) });
  });
}

async function load(page: Page, text?: string) {
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
  if (text !== undefined) {
    await setText(page, text);
    await expect(page.locator('#outline-panel .ol-name', { hasText: /^kick$/ })).toBeVisible();
  }
}

async function setText(page: Page, text: string) {
  await page.evaluate((t) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
  }, text);
}

const doc = (page: Page) => page.evaluate(() => window.__REPULSE_EDITOR__.state.doc.toString());
const key = (page: Page, k: string) => page.evaluate((x) => localStorage.getItem(x), k);
const box = async (page: Page, sel: string) => (await page.locator(sel).boundingBox())!;

// Opening the panel refreshes the model catalogue, whose arrival re-renders the
// panel (and would wipe typed text). Wait for it before interacting.
async function openRail(page: Page) {
  const models = page.waitForResponse('**/api/ai-models');
  await page.locator('#ai-rail').click();
  await models;
  await page.waitForTimeout(150);
}

async function send(page: Page, text: string) {
  await page.locator('#ai-input').fill(text);
  await page.locator('#ai-send-btn').click();
}

async function dispatchSel(page: Page, anchor: number) {
  await page.evaluate((a) => window.__REPULSE_EDITOR__.dispatch({ selection: { anchor: a } }), anchor);
}

test('T1: rail replaces the nav button, closed by default, wide editor', async ({ page }) => {
  await setup(page);
  await load(page);
  await expect(page.locator('#ai-toggle-btn')).toHaveCount(0);
  await expect(page.locator('.top-nav button', { hasText: /^ai$/ })).toHaveCount(0);
  await expect(page.locator('.workbench #ai-panel')).toHaveCount(0);
  await expect(page.locator('.app-shell > #ai-panel + #ai-rail')).toHaveCount(1);
  expect(await page.evaluate(() => document.querySelector('.app-shell')!.lastElementChild!.id)).toBe('ai-rail');
  await expect(page.locator('#ai-panel')).toBeHidden();
  const rail = await box(page, '#ai-rail');
  const shell = await box(page, '.app-shell');
  expect(Math.abs(rail.width - 28)).toBeLessThanOrEqual(0.5);
  expect(Math.abs(rail.x + rail.width - 1280)).toBeLessThanOrEqual(0.5);
  expect(Math.abs(rail.height - shell.height)).toBeLessThanOrEqual(1);
  await expect(page.locator('#ai-rail .ai-rail-arrow')).toHaveText('‹');
  await expect(page.locator('#ai-rail')).toHaveAttribute('aria-expanded', 'false');
  await expect(page.locator('#outline-panel')).not.toHaveClass(/collapsed/);
  expect((await box(page, '#editor-container .cm-editor')).width).toBeGreaterThanOrEqual(1000);
  await expect(page.locator('#ai-rail .ai-rail-dot')).toBeHidden();
});

test('T2: rail toggle persists across reloads', async ({ page }) => {
  await setup(page);
  await load(page);
  const closedW = (await box(page, '#editor-container .cm-editor')).width;
  await page.locator('#ai-rail').click();
  await expect(page.locator('#ai-panel')).toBeVisible();
  const p = await box(page, '#ai-panel');
  expect(Math.abs(p.width - 300)).toBeLessThanOrEqual(0.5);
  expect(Math.abs(p.x - 952)).toBeLessThanOrEqual(1);
  await expect(page.locator('#ai-rail .ai-rail-arrow')).toHaveText('›');
  await expect(page.locator('#ai-rail')).toHaveAttribute('aria-expanded', 'true');
  await expect(page.locator('#ai-rail')).toHaveClass(/open/);
  expect(await key(page, 'repulse:ai:panel-open')).toBe('1');
  expect(Math.abs((await box(page, '#editor-container .cm-editor')).width - (closedW - 300))).toBeLessThanOrEqual(2);
  await page.reload();
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__);
  await expect(page.locator('#ai-panel')).toBeVisible();
  await expect(page.locator('#ai-rail .ai-rail-arrow')).toHaveText('›');
  await page.locator('#ai-rail').click();
  await expect(page.locator('#ai-panel')).toBeHidden();
  expect(await key(page, 'repulse:ai:panel-open')).toBe('0');
  await expect(page.locator('#ai-rail .ai-rail-arrow')).toHaveText('‹');
  await page.reload();
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__);
  await expect(page.locator('#ai-panel')).toBeHidden();
});

test('T3: Mod+I toggles exactly once from editor, textarea and body', async ({ page }) => {
  await setup(page, SEED);
  await load(page, C);
  await dispatchSel(page, 75);
  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
  await page.keyboard.press('ControlOrMeta+i');
  await expect(page.locator('#ai-panel')).toBeVisible();
  await expect(page.locator('#ai-input')).toBeFocused();
  expect(await doc(page)).toBe(C);
  expect(await page.evaluate(() => {
    const s = window.__REPULSE_EDITOR__.state.selection.main; return [s.from, s.to];
  })).toEqual([75, 75]);
  await page.keyboard.press('ControlOrMeta+i');
  await expect(page.locator('#ai-panel')).toBeHidden();
  // DOM focus, not CodeMirror's .cm-focused class (which can lag a frame behind)
  await expect(page.locator('#editor-container .cm-content')).toBeFocused();
  await page.evaluate(() => (document.activeElement as HTMLElement).blur());
  await page.keyboard.press('ControlOrMeta+i');
  await expect(page.locator('#ai-panel')).toBeVisible();
});

test('T4: working state, rail dot only while closed', async ({ page }) => {
  await setup(page, SEED);
  await mockStream(page, [TEXT], true);
  await load(page);
  await openRail(page);
  await send(page, 'make it swing');
  await expect(page.locator('#ai-panel #ai-working')).toBeVisible();
  await expect(page.locator('#ai-working')).toContainText('thinking');
  await expect(page.locator('#ai-working .ai-dot')).toHaveCount(3);
  const dots = await page.locator('#ai-working .ai-dot').evaluateAll((els) =>
    els.map((e) => { const s = getComputedStyle(e); return [s.animationName, s.animationDuration, s.animationDelay]; }));
  expect(dots).toEqual([['ai-dot-pulse', '1.1s', '0s'], ['ai-dot-pulse', '1.1s', '0.18s'], ['ai-dot-pulse', '1.1s', '0.36s']]);
  await expect(page.locator('.ai-input-row.ai-input-row--busy > .ai-sweep')).toHaveCount(1);
  const sweep = await page.locator('.ai-sweep').evaluate((e) => {
    const s = getComputedStyle(e); return [s.animationName, s.animationDuration];
  });
  expect(sweep).toEqual(['ai-sweep', '1.4s']);
  await expect(page.locator('#ai-input')).toHaveAttribute('placeholder', 'working…');
  await expect(page.locator('#ai-cancel-btn')).toBeVisible();
  await page.locator('#ai-rail').click();
  await expect(page.locator('#ai-rail')).toHaveClass(/busy/);
  await expect(page.locator('#ai-rail .ai-rail-dot')).toBeVisible();
  await page.locator('#ai-rail').click();
  await expect(page.locator('#ai-rail .ai-rail-dot')).toBeHidden();
  release();
  await expect(page.locator('#ai-working')).toHaveCount(0);
  await expect(page.locator('.ai-sweep')).toHaveCount(0);
  await expect(page.locator('#ai-send-btn')).toBeVisible();
  await expect(page.locator('.ai-msg--assistant').last()).toContainText('Try (fast 2 kick).');
  await expect(page.locator('#ai-rail')).not.toHaveClass(/busy/);
  expect(requests.length).toBe(1);
});

test('T5: cancel clears the working state and drops the reply', async ({ page }) => {
  await setup(page, SEED);
  await mockStream(page, [TEXT], true);
  await load(page);
  await openRail(page);
  await send(page, 'make it swing');
  await expect(page.locator('#ai-working')).toBeVisible();
  await page.locator('#ai-cancel-btn').click();
  await expect(page.locator('#ai-working')).toHaveCount(0);
  await expect(page.locator('#ai-send-btn')).toBeVisible();
  release();
  await page.waitForTimeout(500);
  await expect(page.locator('.ai-msg--assistant', { hasText: 'Try (fast 2 kick).' })).toHaveCount(0);
});

test('T6: context chip follows selection, cursor, and the system prompt carries it', async ({ page }) => {
  await setup(page, SEED);
  await mockStream(page, [TEXT]);
  await load(page, C);
  await expect(page.locator('#outline-panel .ol-row[data-id="track::bass"]')).toBeVisible();
  await openRail(page);
  await page.locator('#outline-panel .ol-row[data-id="track::bass"]').click();
  const chip = page.locator('#ai-ctx-chip');
  await expect(chip).toHaveText('ctx :bass');
  await expect(chip).toBeVisible();
  await expect(chip).toHaveAttribute('title', 'Sent to the AI as context');
  await send(page, 'more movement');
  await expect(page.locator('.ai-msg--assistant').last()).toContainText('Try (fast 2 kick).');
  const sys = requests[0].system as string;
  expect(sys).toContain('User focus (selected in the outline)');
  expect(sys).toContain('<untrusted>\ntrack :bass\n</untrusted>');
  expect(sys).not.toContain('(track :bass (->> (seq :c2 :eb2) (amp 0.6)))');
  await page.locator('#ai-input').fill('abc');
  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
  await page.keyboard.press('Escape');
  await dispatchSel(page, 160);
  await expect(chip).toHaveText('ctx :chorus');
  await dispatchSel(page, 115);
  await expect(chip).toHaveText('ctx song');
  await setText(page, '');
  await expect(chip).toBeHidden();
  await expect(chip).toHaveText('');
  await expect(page.locator('#ai-input')).toHaveValue('abc');
});

test('T7: auto-apply edit, then revert', async ({ page }) => {
  await setup(page, { ...SEED, 'repulse:ai:auto-apply': 'true' });
  await mockStream(page, [EDIT, TEXT]);
  await load(page, C);
  await openRail(page);
  await send(page, 'quieter bass');
  await expect.poll(() => doc(page)).toContain('(amp 0.4)');
  expect(await doc(page)).not.toContain('(amp 0.6)');
  await expect(page.locator('#ai-revert-btn')).toBeVisible();
  expect(requests.length).toBe(2);
  await page.locator('#ai-revert-btn').click();
  expect(await doc(page)).toBe(C);
});

test('T8: Apply on the diff overlay below the editor', async ({ page }) => {
  await setup(page, SEED);
  await mockStream(page, [EDIT, TEXT]);
  await load(page, C);
  await openRail(page);
  await send(page, 'quieter bass');
  await expect(page.locator('.workbench > #editor-container + #ai-proposal-overlay')).toHaveCount(1);
  await page.locator('#ai-apply-btn').click();
  await expect(page.locator('#ai-proposal-overlay')).toHaveCount(0);
  expect(await doc(page)).toContain('(amp 0.4)');
  await expect(page.locator('.ai-msg--assistant').last()).toContainText('Try (fast 2 kick).');
});

test('T9: settings drawer and back', async ({ page }) => {
  await setup(page, SEED);
  await load(page);
  await openRail(page);
  await page.locator('#ai-settings-btn').click();
  await expect(page.locator('#ai-panel #ai-provider-sel')).toBeVisible();
  await expect(page.locator('#ai-provider-sel')).toHaveValue('anthropic');
  await page.locator('#ai-settings-back-btn').click();
  await expect(page.locator('#ai-input')).toBeVisible();
  await expect(page.locator('#ai-ctx-chip')).toHaveCount(1);
});

test('T10: (ai) opens the rail panel', async ({ page }) => {
  await setup(page, SEED);
  await load(page);
  await page.locator('#cmd-container .cm-content').click();
  await page.keyboard.type('(ai)');
  await page.keyboard.press('Enter');
  await expect(page.locator('#ai-panel')).toBeVisible();
  expect(await key(page, 'repulse:ai:panel-open')).toBe('1');
  await expect(page.locator('#ai-rail .ai-rail-arrow')).toHaveText('›');
});
