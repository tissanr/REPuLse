import { expect, type Page, test } from '@playwright/test';

// Phase WS5 — code is the source of truth: Alt+Enter reconciles the scheduled
// tracks to the code, :mute / :solo live in the code, and the inspector, the
// track panel and mute!/unmute!/solo! edit the code. Real app page.

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_BUFFERS__: any;
    __REPULSE_APP__: {
      muted: () => string[];
      tracks: () => string[];
      cycle: () => number;
      playing: () => boolean;
      evaluate: (code: string) => void;
      trackGainNode: (track: string) => GainNode | null;
    };
    __ws5Samples?: { t: number; gain: number; hasB: boolean; node: boolean }[];
  }
}

const GOAL_HATS = '(->> (fast 8 (seq :hh)) (lpf (range 400 6000 (slow 4 sine))) (resonance 4))';
const GOAL_D = '(track :d (->> (seq :bd :sd) (fx :reverb 0.4)))';
const AB = '(track :a (seq :bd :bd))\n(track :b (seq :hh :hh))';

async function load(page: Page) {
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
}

async function setText(page: Page, text: string) {
  await page.evaluate((t) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
  }, text);
}

const doc = (page: Page) => page.evaluate(() => window.__REPULSE_EDITOR__.state.doc.toString());
const tracks = (page: Page) => page.evaluate(() => window.__REPULSE_APP__.tracks());
const muted = (page: Page) => page.evaluate(() => window.__REPULSE_APP__.muted());
const output = (page: Page) => page.locator('#output');
const row = (page: Page, id: string) => page.locator(`#outline-panel .ol-row[data-id="${id}"]`);

async function altEnter(page: Page) {
  await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
  await page.keyboard.press('Alt+Enter');
}

async function evalText(page: Page, text: string) {
  await setText(page, text);
  await altEnter(page);
}

async function command(page: Page, code: string) {
  await page.evaluate((c) => window.__REPULSE_APP__.evaluate(c), code);
}

async function expectTracks(page: Page, names: string[]) {
  await expect.poll(() => tracks(page), { timeout: 15_000 }).toEqual(names);
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    try {
      if (!sessionStorage.getItem('ws5-keep')) localStorage.clear();
    } catch {}
  });
  await page.setViewportSize({ width: 1280, height: 800 });
});

test('goal example: replacing a bare pattern with a named track stops the hi-hats', async ({ page }) => {
  await load(page);
  await evalText(page, GOAL_HATS);
  await expectTracks(page, ['_']);
  await evalText(page, GOAL_D);
  await expectTracks(page, ['d']);
});

test('a deleted track retires with a click-free gain ramp', async ({ page }) => {
  await load(page);
  await evalText(page, AB);
  await expectTracks(page, ['a', 'b']);
  await expect.poll(() => page.evaluate(() => window.__REPULSE_APP__.trackGainNode('b')?.gain.value ?? -1)).toBe(1);
  // Delete the :b line and evaluate (Alt+Enter, dispatched synchronously so
  // the evaluation time is exact), then sample :b's gain every few ms.
  await page.evaluate(() => {
    const v = window.__REPULSE_EDITOR__;
    const g = window.__REPULSE_APP__.trackGainNode('b')!;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: '(track :a (seq :bd :bd))' } });
    const t0 = performance.now();
    v.contentDOM.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', altKey: true, bubbles: true }));
    const samples: { t: number; gain: number; hasB: boolean; node: boolean }[] = [];
    window.__ws5Samples = samples;
    const id = setInterval(() => {
      samples.push({
        t: performance.now() - t0,
        gain: g.gain.value,
        hasB: window.__REPULSE_APP__.tracks().includes('b'),
        node: window.__REPULSE_APP__.trackGainNode('b') === g,
      });
      if (samples.length > 200) clearInterval(id);
    }, 4);
  });
  await expectTracks(page, ['a']);
  await page.waitForTimeout(600);
  const s = await page.evaluate(() => window.__ws5Samples!);
  expect(s.every((x) => !x.hasB)).toBe(true); // retired from :tracks at once
  // reaches 0 within 150 ms of the evaluation
  const zero = s.find((x) => x.gain === 0);
  expect(zero).toBeTruthy();
  expect(zero!.t).toBeLessThanOrEqual(150);
  // no abrupt cut: monotonically non-increasing over the ramp
  for (let i = 1; i < s.length; i++) expect(s[i].gain).toBeLessThanOrEqual(s[i - 1].gain);
  // the node stays connected while it ramps (gain still > 0), then is dropped
  expect(s.filter((x) => x.gain > 0).every((x) => x.node)).toBe(true);
  expect(s[s.length - 1].node).toBe(false);
  expect(await page.evaluate(() => window.__REPULSE_APP__.trackGainNode('b'))).toBeNull();
});

test('a bare pattern plays next to named tracks and keeps the cycle running', async ({ page }) => {
  await load(page);
  const code = '(track :a (seq :bd :sd))\n(seq :hh :hh)';
  await evalText(page, code);
  await expectTracks(page, ['_', 'a']);
  await page.waitForTimeout(500);
  const c1 = await page.evaluate(() => window.__REPULSE_APP__.cycle());
  await altEnter(page);
  await page.waitForTimeout(100);
  const c2 = await page.evaluate(() => window.__REPULSE_APP__.cycle());
  expect(c2).toBeGreaterThanOrEqual(c1);
  expect(await tracks(page)).toEqual(['_', 'a']);
  expect(await page.evaluate(() => window.__REPULSE_APP__.playing())).toBe(true);
});

test('a buffer of only defs stops everything with "nothing to play"', async ({ page }) => {
  await load(page);
  await evalText(page, AB);
  await expectTracks(page, ['a', 'b']);
  await evalText(page, '(def x (seq :bd))');
  await expect(output(page)).toHaveText('nothing to play');
  await expectTracks(page, []);
  await expect.poll(() => page.evaluate(() => window.__REPULSE_APP__.playing())).toBe(false);
});

test('an eval error retires nothing and keeps the mutes', async ({ page }) => {
  await load(page);
  await evalText(page, '(track :a (seq :bd :bd) :mute)\n(track :b (seq :hh :hh))');
  await expectTracks(page, ['a', 'b']);
  expect(await muted(page)).toEqual(['a']);
  await evalText(page, '(track :a (seq :bd)');
  await expect(output(page)).toContainText('Error');
  await page.waitForTimeout(300);
  expect(await tracks(page)).toEqual(['a', 'b']);
  expect(await muted(page)).toEqual(['a']);
  await evalText(page, '(track :a (fsat 2 (seq :bd)))');
  await expect(output(page)).toContainText('Error');
  await page.waitForTimeout(300);
  expect(await tracks(page)).toEqual(['a', 'b']);
  expect(await muted(page)).toEqual(['a']);
});

test('command-bar evaluations never retire a track', async ({ page }) => {
  await load(page);
  await evalText(page, AB);
  await expectTracks(page, ['a', 'b']);
  for (const c of ['(tracks)', '(bpm 130)', '(tap!)']) {
    await command(page, c);
    await page.waitForTimeout(200);
    expect(await tracks(page)).toEqual(['a', 'b']);
  }
});

test(':mute and :solo flags drive the muted set', async ({ page }) => {
  await load(page);
  await evalText(page, '(track :hats (seq :hh :oh) :mute)');
  await expectTracks(page, ['hats']);
  expect(await muted(page)).toEqual(['hats']);
  await evalText(page, '(track :k (seq :bd) :solo)\n(track :h (seq :hh))\n(track :s (seq :sd) :solo)');
  await expectTracks(page, ['h', 'k', 's']);
  expect(await muted(page)).toEqual(['h']);
  await evalText(page, '(track :x (seq :bd) :mute :solo)');
  await expectTracks(page, ['x']);
  expect(await muted(page)).toEqual(['x']);
});

test('a bad track option is an eval error underlined on the option', async ({ page }) => {
  await load(page);
  await evalText(page, '(track :x (seq :bd) :mut)');
  await expect(output(page)).toHaveText('Error: unknown track option :mut — did you mean :mute?');
  await expect(page.locator('#editor-container .cm-lintRange-error')).toHaveText(':mut');
  await evalText(page, '(track :x (seq :bd) 3)');
  await expect(output(page)).toHaveText('Error: track options must be :mute or :solo, got 3');
});

const HATS3 = '(track :hats\n  ;; open hat on the 2\n  (seq :hh :oh))';

test('inspector M writes :mute into the code, one undo step, also for a 3-line form', async ({ page }) => {
  await load(page);
  for (const [src, muted1] of [
    ['(track :hats (seq :hh :oh))', '(track :hats (seq :hh :oh) :mute)'],
    [HATS3, '(track :hats\n  ;; open hat on the 2\n  (seq :hh :oh) :mute)'],
  ]) {
    await evalText(page, src);
    await expectTracks(page, ['hats']);
    if (!(await page.locator('#inspector').isVisible())) await row(page, 'track::hats').click();
    const m = page.locator('#inspector .ins-m[data-track="hats"]');
    await m.click();
    expect(await doc(page)).toBe(muted1);
    expect(await muted(page)).toEqual(['hats']); // silent immediately
    await expect(page.locator('#inspector .ins-status')).toHaveText('muted');
    await page.evaluate(() => window.__REPULSE_EDITOR__.focus());
    await page.keyboard.press('ControlOrMeta+z');
    expect(await doc(page)).toBe(src);
    // CodeMirror redo: Cmd+Shift+Z on macOS, Ctrl+Y elsewhere (Ctrl+Shift+Z is unreliable on Linux CI).
    await page.keyboard.press(process.platform === 'darwin' ? 'Meta+Shift+z' : 'Control+y');
    expect(await doc(page)).toBe(muted1);
    await expect.poll(() => muted(page)).toEqual(['hats']);
    await m.click();
    expect(await doc(page)).toBe(src);
    expect(await muted(page)).toEqual([]);
  }
});

test('inspector S adds :solo to several tracks; clicking again removes it', async ({ page }) => {
  await load(page);
  const src = '(track :kick (seq :bd :bd))\n(track :hats (seq :hh :hh))\n(track :bass (seq :c2 :c2))';
  await evalText(page, src);
  await expectTracks(page, ['bass', 'hats', 'kick']);
  await row(page, 'track::kick').click();
  await page.locator('#inspector .ins-s[data-track="kick"]').click();
  await expect.poll(() => muted(page)).toEqual(['bass', 'hats']);
  await row(page, 'track::hats').click();
  await page.locator('#inspector .ins-s[data-track="hats"]').click();
  expect(await doc(page)).toBe('(track :kick (seq :bd :bd) :solo)\n(track :hats (seq :hh :hh) :solo)\n(track :bass (seq :c2 :c2))');
  await expect.poll(() => muted(page)).toEqual(['bass']);
  await expect(page.locator('#inspector .ins-status')).toHaveText('solo');
  await page.locator('#inspector .ins-s[data-track="hats"]').click();
  expect(await doc(page)).toBe('(track :kick (seq :bd :bd) :solo)\n(track :hats (seq :hh :hh))\n(track :bass (seq :c2 :c2))');
  await expect.poll(() => muted(page)).toEqual(['bass', 'hats']);
  await expect(page.locator('#inspector .ins-status')).toHaveText('muted by solo');
});

test('mute! / unmute! / solo! in the command bar patch the code; in the buffer they are errors', async ({ page }) => {
  await load(page);
  await evalText(page, '(track :hats (seq :hh :oh))\n(track :kick (seq :bd))');
  await expectTracks(page, ['hats', 'kick']);
  // typed into the real command bar
  await page.locator('#cmd-container .cm-content').click();
  await page.keyboard.type('(mute! :hats)');
  await page.keyboard.press('Enter');
  await expect.poll(() => doc(page)).toBe('(track :hats (seq :hh :oh) :mute)\n(track :kick (seq :bd))');
  await expect.poll(() => muted(page)).toEqual(['hats']);
  await command(page, '(unmute! :hats)');
  await expect.poll(() => doc(page)).toBe('(track :hats (seq :hh :oh))\n(track :kick (seq :bd))');
  await command(page, '(solo! :kick)');
  await expect.poll(() => doc(page)).toBe('(track :hats (seq :hh :oh))\n(track :kick (seq :bd) :solo)');
  await expect.poll(() => muted(page)).toEqual(['hats']);
  await command(page, '(mute! :nope)');
  await expect(output(page)).toContainText('track :nope is not in the code');
  await evalText(page, '(track :hats (seq :hh :oh))\n(mute! :hats)');
  await expect(output(page)).toContainText('write (track :hats … :mute) instead');
});

test('the :_ track buttons in the track panel are disabled', async ({ page }) => {
  await load(page);
  await evalText(page, '(seq :bd :sd)');
  await expectTracks(page, ['_']);
  const b = page.locator('#track-panel .viz-track-btn[data-name="_"]');
  await expect(b).toBeDisabled();
  await expect(b).toHaveAttribute('title', 'name the track to mute it: (track :name …)');
});

test('a saved session with :muted loads with :mute in the code; new saves have no :muted', async ({ page }) => {
  await page.addInitScript(() => {
    if (sessionStorage.getItem('ws5-keep')) return;
    sessionStorage.setItem('ws5-keep', '1');
    localStorage.setItem(
      'repulse-session',
      JSON.stringify({
        v: 3, main: 'main', active: 'main',
        buffers: [{ name: 'main', code: '(track :hats (seq :hh :oh))\n(track :kick (seq :bd))' }],
        bpm: 120, fx: [], bank: null, sources: [], muted: ['hats', 'ghost'], midi: {},
      }),
    );
  });
  await load(page);
  expect(await doc(page)).toBe('(track :hats (seq :hh :oh) :mute)\n(track :kick (seq :bd))');
  await setText(page, '(track :hats (seq :hh :oh) :mute)\n(track :kick (seq :bd :bd))');
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem('repulse-session') ?? ''))
    .toContain('(seq :bd :bd)');
  const saved = JSON.parse((await page.evaluate(() => localStorage.getItem('repulse-session')))!);
  expect('muted' in saved).toBe(false);
});

test('a #v3: link with :muted is migrated into the code', async ({ page }) => {
  const sess = {
    v: 3, main: 'main', active: 'main',
    buffers: [{ name: 'main', code: '(track :hats (seq :hh))' }],
    bpm: 120, fx: [], bank: null, sources: [], muted: ['hats'], midi: {},
  };
  const hash = '#v3:' + Buffer.from(JSON.stringify(sess), 'utf8').toString('base64');
  await page.goto('/' + hash);
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
  expect(await doc(page)).toBe('(track :hats (seq :hh) :mute)');
});

test('project mode: a second buffer\'s track retires, and its mute patches that buffer', async ({ page }) => {
  await load(page);
  await page.waitForFunction(() => !!window.__REPULSE_BUFFERS__);
  await setText(page, '(use :drums)\n(track :a (seq :bd :bd))');
  const r = await page.evaluate(() => window.__REPULSE_BUFFERS__.create('drums', '(track :b (seq :hh :hh))'));
  expect(r.ok).toBe('drums');
  await page.evaluate(() => window.__REPULSE_BUFFERS__.switchTo('main'));
  await altEnter(page);
  await expectTracks(page, ['a', 'b']);
  // track-panel mute for :b while main is shown → patches the drums buffer
  await page.locator('#track-panel .viz-track-btn[data-name="b"]').click();
  const p = await page.evaluate(() => window.__REPULSE_BUFFERS__.project());
  const code = (n: string) => p.buffers.find((b: { name: string }) => b.name === n).code;
  expect(code('drums')).toBe('(track :b (seq :hh :hh) :mute)');
  expect(code('main')).toBe('(use :drums)\n(track :a (seq :bd :bd))');
  await expect.poll(() => muted(page)).toEqual(['b']);
  // delete :b's form in its buffer and re-evaluate from main
  await page.evaluate(() => window.__REPULSE_BUFFERS__.switchTo('drums'));
  await setText(page, ';; nothing here');
  await page.evaluate(() => window.__REPULSE_BUFFERS__.switchTo('main'));
  await altEnter(page);
  await expectTracks(page, ['a']);
  expect(await muted(page)).toEqual([]);
});
