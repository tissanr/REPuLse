import { expect, type Page, test } from '@playwright/test';

// Phase VIZ1 — honest track visuals & in-code visualizers, on the real app page
// through the __REPULSE_APP__ test handles (vizSummary, trackAudio, heardCycle)
// and on the TEST1 harness (events unchanged by a visualizer).

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_APP__: any;
    __REPULSE_TEST__: any;
  }
}

async function load(page: Page) {
  // No GitHub sample manifests: :bd / :sd use the WASM drum voices.
  await page.route('https://raw.githubusercontent.com/**', (r) => r.abort());
  await page.addInitScript(() => {
    try { localStorage.clear(); } catch {}
  });
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
  await expect
    .poll(() => page.evaluate(() => window.__REPULSE_APP__.audioBackend()), { timeout: 15_000 })
    .toBe('audioworklet+wasm');
}

const app = <T>(page: Page, fn: string, ...args: unknown[]) =>
  page.evaluate(([f, a]) => window.__REPULSE_APP__[f as string](...(a as unknown[])), [fn, args] as const) as Promise<T>;

async function altEnter(page: Page, code: string) {
  await page.evaluate((c) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: c }, selection: { anchor: 0 } });
    v.focus();
  }, code);
  await page.keyboard.press('Alt+Enter');
}

async function waitPlaying(page: Page) {
  await expect.poll(() => app<boolean>(page, 'playing'), { timeout: 15_000 }).toBe(true);
}

const summary = (page: Page, track: string, kind: string, source = 'inline') =>
  app<any>(page, 'vizSummary', track, kind, source);

const output = (page: Page) => page.locator('#output');
const widgets = (page: Page) => page.locator('#editor-container .cm-inline-viz');

test.afterEach(async ({ page }) => {
  await page.evaluate(() => window.__REPULSE_APP__?.evaluate('(stop)')).catch(() => {});
});

test('harness: a visualizer leaves the events unchanged', async ({ page }) => {
  await page.goto('/test-harness.html');
  await page.waitForFunction(() => !!window.__REPULSE_TEST__);
  const pairs = await page.evaluate(() => {
    const t = window.__REPULSE_TEST__;
    return [
      [t.evalToEvents('(pianoroll (seq :c3 :eb3 :g3 :bb3))'), t.evalToEvents('(seq :c3 :eb3 :g3 :bb3)')],
      [t.evalToEvents('(punchcard {:labels true} (seq :bd :sd :bd :sd))'), t.evalToEvents('(seq :bd :sd :bd :sd)')],
      [t.evalToEvents('(scope (spectrum (->> (seq :c2 :g1) (synth :saw))))'), t.evalToEvents('(->> (seq :c2 :g1) (synth :saw))')],
    ];
  });
  for (const [a, b] of pairs) {
    expect(a.length).toBeGreaterThan(0);
    expect(a).toEqual(b);
  }
});

test('pianoroll: an inline canvas with 4 notes on the rows of MIDI 48 51 55 58', async ({ page }) => {
  await load(page);
  await altEnter(page, '(track :lead (pianoroll (seq :c3 :eb3 :g3 :bb3)))');
  await waitPlaying(page);
  await expect(widgets(page)).toHaveCount(1);
  await expect(widgets(page).first()).toHaveAttribute('data-track', 'lead');
  await expect.poll(async () => (await summary(page, 'lead', 'pianoroll'))?.notes?.length ?? 0,
    { timeout: 10_000 }).toBeGreaterThanOrEqual(4);
  const s = await summary(page, 'lead', 'pianoroll');
  const midis = [...new Set(s.notes.map((n: any) => n.midi))].sort((a: any, b: any) => a - b);
  expect(midis).toEqual([48, 51, 55, 58]);
  // one row per pitch, highest pitch on top
  const rowOf = (m: number) => s.notes.find((n: any) => n.midi === m).row;
  expect(rowOf(58)).toBeLessThan(rowOf(55));
  expect(rowOf(55)).toBeLessThan(rowOf(51));
  expect(rowOf(51)).toBeLessThan(rowOf(48));
  expect(s.rows).toBe(4);
});

test('punchcard: 4 cells in 2 drum lanes labelled bd and sd', async ({ page }) => {
  await load(page);
  await altEnter(page, '(track :d (punchcard {:labels true} (seq :bd :sd :bd :sd)))');
  await waitPlaying(page);
  await expect.poll(async () => (await summary(page, 'd', 'punchcard'))?.notes?.length ?? 0,
    { timeout: 10_000 }).toBe(4);
  const s = await summary(page, 'd', 'punchcard');
  expect(s.rows).toBe(2);
  expect(s.notes.map((n: any) => n.lane)).toEqual(['bd', 'sd', 'bd', 'sd']);
  expect(s.notes.map((n: any) => n.label)).toEqual(['bd', 'sd', 'bd', 'sd']);
  expect(new Set(s.notes.map((n: any) => n.row))).toEqual(new Set([0, 1]));
});

test('scope: WASM and JS voices show their own audio; muted and silent tracks stay flat', async ({ page }) => {
  await load(page);
  await altEnter(page, [
    '(bpm 120)',
    // WASM voice (no track FX → the worklet plays it)
    '(track :b (scope (->> (seq :c2 :g1) (synth :saw))))',
    // JS voice (a track FX routes its voices through Web Audio nodes)
    '(track :k (scope (->> (seq :c3 :g3) (fx :reverb 0.2))))',
    '(track :m (scope (seq :c4 :e4)) :mute)',
    '(track :s (scope (seq :_)))',
  ].join('\n'));
  await waitPlaying(page);
  const rms = (t: string) => page.evaluate((tr) => {
    window.__REPULSE_APP__.requestMeters();
    return window.__REPULSE_APP__.trackAudio(tr).rms;
  }, t);
  await expect.poll(() => rms('b'), { timeout: 10_000 }).toBeGreaterThan(0.001);
  await expect.poll(() => rms('k'), { timeout: 10_000 }).toBeGreaterThan(0.001);
  for (let i = 0; i < 5; i++) {
    expect(await rms('m')).toBe(0);
    expect(await rms('s')).toBe(0);
    await page.waitForTimeout(200);
  }
  const slots = await page.evaluate(() =>
    ['b', 'k', 'm', 's'].map((t) => window.__REPULSE_APP__.trackAudio(t).slot));
  expect(new Set(slots).size).toBe(4);
});

async function cyclesPerSecond(page: Page) {
  await expect.poll(async () => (await summary(page, 'lead', 'pianoroll'))?.heard ?? null,
    { timeout: 10_000 }).not.toBeNull();
  const a = await page.evaluate(() => ({ h: window.__REPULSE_APP__.heardCycle(), t: performance.now() }));
  await page.waitForTimeout(2500);
  const b = await page.evaluate(() => ({ h: window.__REPULSE_APP__.heardCycle(), t: performance.now() }));
  return (b.h - a.h) / ((b.t - a.t) / 1000);
}

test('playhead follows the tempo: one cycle in 4 s at 60 BPM, 1.33 s at 180 BPM', async ({ page }) => {
  await load(page);
  await altEnter(page, '(bpm 60)\n(track :lead (pianoroll (seq :c3 :e3)))');
  await waitPlaying(page);
  const slow = await cyclesPerSecond(page);
  expect(1 / slow).toBeGreaterThan(4 * 0.95);
  expect(1 / slow).toBeLessThan(4 * 1.05);
  await altEnter(page, '(bpm 180)\n(track :lead (pianoroll (seq :c3 :e3)))');
  // a tempo change takes effect at the next bar (≤ 4 s at 60 BPM)
  await page.waitForTimeout(4500);
  const fast = await cyclesPerSecond(page);
  expect(1 / fast).toBeGreaterThan((4 / 3) * 0.95);
  expect(1 / fast).toBeLessThan((4 / 3) * 1.05);
});

test('song mode: a resting track is dimmed and the playhead follows the song bar', async ({ page }) => {
  await load(page);
  await altEnter(page, [
    '(bpm 240)',
    '(track :a (pianoroll (seq :c3 :e3)))',
    '(track :b (pianoroll (seq :g3 :b3)))',
    '(song (section :x 1 [:a]) (section :y 1 [:b]) :end :loop)',
  ].join('\n'));
  await waitPlaying(page);
  // over a few bars, :a and :b alternate between dimmed and lit, never both lit
  const seen = new Set<string>();
  // sample until both alternations were seen (≤ 10 s; one bar is 1 s)
  for (let i = 0; i < 66 && !(seen.has('false-true') && seen.has('true-false')); i++) {
    const [sa, sb] = await Promise.all([summary(page, 'a', 'pianoroll'), summary(page, 'b', 'pianoroll')]);
    if (sa && sb) {
      seen.add(`${sa['muted?']}-${sb['muted?']}`);
      // song time: a 2-bar :loop song keeps pattern time in [0, 2)
      expect(sa.heard).toBeGreaterThanOrEqual(0);
      expect(sa.heard).toBeLessThan(2.05);
    }
    await page.waitForTimeout(150);
  }
  expect(seen.has('false-true')).toBe(true);
  expect(seen.has('true-false')).toBe(true);
  expect(seen.has('false-false')).toBe(false);
});

test('a visualizer under a combinator warns with the exact text and shows the placeholder', async ({ page }) => {
  await load(page);
  await altEnter(page, '(track :x (fast 2 (pianoroll (seq :c3))))');
  await waitPlaying(page);
  await expect(output(page)).toContainText(
    'pianoroll has no effect here — it must wrap the whole track pattern, e.g. (track :x (pianoroll …))');
  await expect(widgets(page)).toHaveCount(1);
  await page.waitForTimeout(300);
  expect(await summary(page, 'x', 'pianoroll')).toBeNull();
});

test('option and argument errors use the exact texts', async ({ page }) => {
  await load(page);
  await altEnter(page, '(track :x (pianoroll {:cycle 2} (seq :c3)))');
  await expect(output(page)).toContainText('pianoroll: unknown option :cycle — did you mean :cycles?');
  await altEnter(page, '(track :x (pianoroll 3))');
  await expect(output(page)).toContainText('pianoroll expects a pattern as its last argument, got 3');
  await altEnter(page, '(track :x (pianoroll {:cycles 40} (seq :c3)))');
  await expect(output(page)).toContainText('pianoroll: :cycles must be between 1 and 16, got 40');
});

test('panel: the modes are punch roll scope spec level, no fake waveform', async ({ page }) => {
  await load(page);
  // A sustained voice: the scope check measures RMS over a ~46 ms window, and
  // short drum hits leave that window in their decay tail most of the time.
  await altEnter(page, '(track :d (->> (seq :c2 :g1) (synth :saw)))');
  await waitPlaying(page);
  const ids = await page.locator('#track-panel .viz-mode-btn').evaluateAll((bs) =>
    bs.map((b) => (b as HTMLElement).dataset.mode));
  expect(ids).toEqual(['punch', 'roll', 'scope', 'spec', 'level']);
  await expect(page.locator('#track-panel .viz-mode-btn.active')).toHaveAttribute('data-mode', 'punch');
  await expect.poll(async () => (await summary(page, 'd', 'punchcard', 'panel'))?.notes?.length ?? 0,
    { timeout: 10_000 }).toBe(2);
  await page.locator('#track-panel .viz-mode-btn[data-mode="scope"]').click();
  await expect.poll(() => page.evaluate(() => {
    window.__REPULSE_APP__.requestMeters();
    return window.__REPULSE_APP__.trackAudio('d').rms;
  }), { timeout: 10_000 }).toBeGreaterThan(0.001);
});

test('bottom panels: × hides track visuals and the analyser one by one; the status bar brings them back', async ({ page }) => {
  await load(page);
  await altEnter(page, '(track :d (seq :bd :sd))');
  await waitPlaying(page);
  const tracks = page.locator('#track-panel');
  const spectrum = page.locator('#plugin-panel');
  const restoreTracks = page.locator('.panel-restore[data-panel="tracks"]');
  const restoreSpectrum = page.locator('.panel-restore[data-panel="spectrum"]');
  await expect(spectrum).toBeVisible();
  await expect(restoreTracks).toBeHidden();
  await expect(restoreSpectrum).toBeHidden();

  // the toolbar is not rebuilt on every scheduler tick: the × stays one element
  const close = page.locator('#track-panel .panel-close');
  const handle = await close.elementHandle();
  await page.waitForTimeout(500);
  expect(await handle!.evaluate((b) => b.isConnected)).toBe(true);

  await close.click();
  await expect(tracks).toBeHidden();
  await expect(spectrum).toBeVisible();
  await expect(restoreTracks).toBeVisible();
  await expect(restoreTracks).toHaveText('+track viz');

  await page.locator('#plugin-panel .panel-close').click();
  await expect(spectrum).toBeHidden();
  await expect(restoreSpectrum).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('repulse.closed-panels'))).toBe('["spectrum","tracks"]');

  await restoreTracks.click();
  await expect(tracks).toBeVisible();
  await expect(restoreTracks).toBeHidden();
  await expect.poll(async () => (await summary(page, 'd', 'punchcard', 'panel'))?.notes?.length ?? 0,
    { timeout: 10_000 }).toBe(2);
  await restoreSpectrum.click();
  await expect(spectrum).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('repulse.closed-panels'))).toBe('[]');
});

test('muted code: a track that does not play is greyed out in the editor', async ({ page }) => {
  await load(page);
  const greyed = () => page.locator('#editor-container .cm-muted-track').allTextContents();
  await altEnter(page, [
    '(track :a (seq :bd :sd))',
    '(track :b (seq :hh :hh) :mute)',
    '(track :c (seq :c3 :e3))',
  ].join('\n'));
  await waitPlaying(page);
  await expect.poll(greyed).toEqual(['(track :b (seq :hh :hh) :mute)']);
  await expect(page.locator('#editor-container .cm-muted-track')).toHaveCSS('opacity', '0.38');
  // the track panel's mute button writes :mute into the code and greys it
  await page.locator('#track-panel .viz-track-btn[data-name="a"]').click();
  await expect.poll(greyed).toEqual(['(track :a (seq :bd :sd) :mute)', '(track :b (seq :hh :hh) :mute)']);
  // :solo silences every other track
  await altEnter(page, [
    '(track :a (seq :bd :sd))',
    '(track :b (seq :hh :hh))',
    '(track :c (seq :c3 :e3) :solo)',
  ].join('\n'));
  await expect.poll(greyed).toEqual(['(track :a (seq :bd :sd))', '(track :b (seq :hh :hh))']);
  // nothing muted → nothing greyed
  await altEnter(page, '(track :a (seq :bd :sd))\n(track :b (seq :hh :hh))');
  await expect.poll(greyed).toEqual([]);
});
