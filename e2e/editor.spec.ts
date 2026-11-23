import { expect, type Page, test } from '@playwright/test';
import { readFileSync } from 'node:fs';

// Phase TEST3 — editor behaviour of the full app page: completion, hover docs,
// syntax classes, Ctrl/Cmd+K S, comment toggle and Alt+Enter.
//
// Hover docs are generated from app/src/repulse/content/builtin_meta.edn into
// app/src/repulse/lisp-lang/builtins.generated.js (npm run gen:ai-docs); hover.js reads them.

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_APP__: any;
  }
}

async function load(page: Page, text = '') {
  await page.addInitScript(() => {
    try { localStorage.clear(); } catch {}
  });
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
  await setText(page, text);
}

async function setText(page: Page, text: string, anchor = 0, head = anchor) {
  await page.evaluate(
    ([t, a, h]) => {
      const v = window.__REPULSE_EDITOR__;
      v.dispatch({
        changes: { from: 0, to: v.state.doc.length, insert: t },
        selection: { anchor: a, head: h },
      });
      v.focus();
    },
    [text, anchor, head] as const,
  );
}

const doc = (page: Page) => page.evaluate(() => window.__REPULSE_EDITOR__.state.doc.toString());

test('E1: typing (slid and (mas offers slider and master with their detail text', async ({ page }) => {
  await load(page);
  await page.locator('#editor-container .cm-content').click();
  await page.keyboard.type('(slid');
  const popup = page.locator('.cm-tooltip-autocomplete');
  await expect(popup).toBeVisible();
  const item = popup.locator('li', { has: page.locator('.cm-completionLabel', { hasText: /^slider$/ }) });
  await expect(item).toHaveCount(1);
  await expect(item.locator('.cm-completionDetail')).toContainText(
    '(slider value min max [:log|:lin]) — a number drawn as a draggable slider in the editor',
  );

  await setText(page, '');
  await page.keyboard.type('(mas');
  await expect(popup).toBeVisible();
  const master = popup.locator('li', { has: page.locator('.cm-completionLabel', { hasText: /^master$/ }) });
  await expect(master).toHaveCount(1);
  await expect(master.locator('.cm-completionDetail')).toContainText(
    '(master (fx …) …) — global effect chain block; only (fx …) forms inside, at most one per buffer',
  );
});

test('E2: hovering master and slider shows the signature line', async ({ page }) => {
  const text = '(master (fx :reverb 0.3))\n(amp (slider 0.7 0 1) (seq :c4))';
  // the cursor sits inside the slider form: otherwise it is drawn as a widget
  await load(page);
  await setText(page, text, text.indexOf('slider') + 2);
  const hover = async (needle: string) => {
    const { x, y } = await page.evaluate((n) => {
      const v = window.__REPULSE_EDITOR__;
      const r = v.coordsAtPos(v.state.doc.toString().indexOf(n) + 2);
      return { x: r.left + 1, y: (r.top + r.bottom) / 2 };
    }, needle);
    await page.mouse.move(0, 0);
    await page.mouse.move(x, y, { steps: 4 });
  };
  await hover('master');
  await expect(page.locator('.cm-tooltip-hover .repulse-hover-sig')).toHaveText('(master (fx :name …) …)');
  await hover('slider');
  await expect(page.locator('.cm-tooltip-hover .repulse-hover-sig')).toHaveText(
    '(slider value min max) or (slider value min max :log)',
  );
});

test('E3: master is highlighted like fx, slider like amp', async ({ page }) => {
  const text = '(fx :reverb 0.3)\n(master (fx :delay 0.2))\n(amp (slider 0.7 0 1) (seq :c4))\n(def kick 1)';
  await load(page);
  // the cursor sits inside the slider form: otherwise it is drawn as a widget
  await setText(page, text, text.indexOf('slider') + 2);
  const classes = await page.evaluate(() => {
    const out: Record<string, string> = {};
    for (const s of Array.from(document.querySelectorAll('#editor-container .cm-line span'))) {
      const t = s.textContent ?? '';
      if (['fx', 'master', 'amp', 'slider', 'kick'].includes(t) && !(t in out)) out[t] = s.className;
    }
    return out;
  });
  expect(classes.fx).toBeTruthy();
  expect(classes.master).toBe(classes.fx);
  expect(classes.slider).toBe(classes.amp);
  expect(classes.master).toBe(classes.slider); // all four are BuiltinName tokens
  expect(classes.kick).not.toBe(classes.fx); // a user symbol is styled differently
});

// Phase QA4 — hover docs and highlighting come from builtin_meta.edn (via docs/ai/builtins.json,
// generated from the same file), including names that had no hover text before.
test('E2b: hover shows the :signature from builtin_meta.edn for defn, legato and slice', async ({ page }) => {
  const meta = JSON.parse(readFileSync('docs/ai/builtins.json', 'utf8')) as Record<string, { signature: string }>;
  const text = '(defn twice [p] (fast 2 p))\n(legato 0.5 (seq :bd))\n(slice 4 "0 2" (seq :bd))\n(play :x 1)';
  await load(page);
  await setText(page, text, text.length);
  const hover = async (needle: string, offset = 2) => {
    const { x, y } = await page.evaluate(([n, o]) => {
      const v = window.__REPULSE_EDITOR__;
      const r = v.coordsAtPos(v.state.doc.toString().indexOf(n as string) + (o as number));
      return { x: r.left + 1, y: (r.top + r.bottom) / 2 };
    }, [needle, offset] as const);
    await page.mouse.move(0, 0);
    await page.mouse.move(x, y, { steps: 4 });
  };
  for (const name of ['defn', 'legato', 'slice']) {
    await hover(`(${name} `, 2);
    await expect(page.locator('.cm-tooltip-hover .repulse-hover-sig')).toHaveText(meta[name].signature);
  }
  expect(meta.play).toBeUndefined();
  await hover('(play ', 2);
  await page.waitForTimeout(600);
  await expect(page.locator('.cm-tooltip-hover .repulse-hover-sig')).toHaveCount(0);
});

test('E3b: samples!, + and not= are BuiltinName tokens like amp', async ({ page }) => {
  const text = '(samples! "x")\n(+ 1 2)\n(not= a b)\n(keys m)\n(amp 1 (seq :c4))\n(def kick 1)';
  await load(page);
  // the cursor sits at the end, outside every form, so no widget replaces a token
  await setText(page, text, text.length);
  const classes = await page.evaluate(() => {
    const out: Record<string, string> = {};
    for (const s of Array.from(document.querySelectorAll('#editor-container .cm-line span'))) {
      const t = s.textContent ?? '';
      if (['samples!', '+', 'not=', 'keys', 'amp', 'kick'].includes(t) && !(t in out)) out[t] = s.className;
    }
    return out;
  });
  expect(classes.amp).toBeTruthy();
  for (const n of ['samples!', '+', 'not=', 'keys']) expect(classes[n]).toBe(classes.amp);
  expect(classes.kick).not.toBe(classes.amp); // a user symbol is styled differently
});

test('E4: Ctrl/Cmd+K S wraps numbers in (slider …) and unwraps again', async ({ page }) => {
  await load(page);
  const wrap = async (text: string, needle: string) => {
    const pos = text.indexOf(needle) + 1;
    await setText(page, text, pos);
    await page.keyboard.press('ControlOrMeta+k');
    await page.keyboard.press('s');
  };

  await wrap('(fx :filter :freq 800)', '800');
  expect(await doc(page)).toBe('(fx :filter :freq (slider 800 20 20000))');

  await wrap('(fx :filter 800)', '800');
  expect(await doc(page)).toBe('(fx :filter (slider 800 20 20000))');

  await wrap('(amp 1.5 (seq :c4))', '1.5');
  expect(await doc(page)).toBe('(amp (slider 1.5 0 1.5) (seq :c4))');

  // the cursor is on the literal inside the slider: the same chord unwraps it
  await page.keyboard.press('ControlOrMeta+k');
  await page.keyboard.press('s');
  expect(await doc(page)).toBe('(amp 1.5 (seq :c4))');
});

const THREE = '(seq :bd :sd)\n(fast 2 (seq :hh))\n(rev (seq :cp))';

/** Shift+Mod+7 without depending on the CI keyboard layout (German: / needs Shift). */
async function shiftMod7(page: Page) {
  await page.keyboard.down('Shift');
  await page.keyboard.press('ControlOrMeta+Digit7');
  await page.keyboard.up('Shift');
}

test('E5: comment toggle with Shift+Mod+7 and Mod+/', async ({ page }) => {
  await load(page);

  // German layout: Shift+Ctrl/Cmd+7
  await setText(page, THREE, 0, THREE.length);
  await shiftMod7(page);
  const commented = await doc(page);
  expect(commented.split('\n').every((l) => l.startsWith(';'))).toBe(true);
  expect(commented.split('\n')).toHaveLength(3);
  await shiftMod7(page);
  expect(await doc(page)).toBe(THREE);

  // US layout: Ctrl/Cmd+/ (default keymap)
  await setText(page, THREE, 0, THREE.length);
  await page.keyboard.press('ControlOrMeta+/');
  expect((await doc(page)).split('\n').every((l) => l.startsWith(';'))).toBe(true);
  await page.keyboard.press('ControlOrMeta+/');
  expect(await doc(page)).toBe(THREE);
});

test('E6: Alt+Enter still evaluates after the comment-toggle binding', async ({ page }) => {
  await load(page);
  await setText(page, '(seq :bd :sd)', 0, 13);
  await shiftMod7(page);
  await shiftMod7(page);
  expect(await doc(page)).toBe('(seq :bd :sd)');
  await page.keyboard.press('Alt+Enter');
  await expect(page.locator('#output')).toHaveText(
    'playing pattern — Alt+Enter to re-evaluate, (stop) to stop',
  );
  await page.evaluate(() => window.__REPULSE_APP__.evaluate('(stop)'));
});
