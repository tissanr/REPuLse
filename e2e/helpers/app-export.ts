import { expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { assertFinitePcm, mixMono, parseWav16, type Wav16 } from './audio-analysis';

// Helpers for e2e specs that drive the full app (`/`) and read the WAV written by
// the real (export …) built-in (same approach as export-parity.spec.ts).

declare global {
  interface Window {
    __REPULSE_EDITOR__: any;
    __REPULSE_APP__: any;
  }
}

export async function loadApp(page: Page) {
  // Keep the default sample manifests (GitHub) out: :bd / :sd / :hh then use the
  // built-in WASM drum voices, and the tests need no network.
  await page.route('https://raw.githubusercontent.com/**', (r) => r.abort());
  await page.addInitScript(() => {
    try {
      localStorage.clear();
    } catch {
      /* ignore */
    }
  });
  await page.setViewportSize({ width: 1280, height: 800 });
  page.on('dialog', (d) => d.accept());
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_EDITOR__ && !!window.__REPULSE_APP__);
  await expect
    .poll(() => page.evaluate(() => window.__REPULSE_APP__.audioBackend()), { timeout: 15_000 })
    .toBe('audioworklet+wasm');
}

/** Put code in the editor, Alt+Enter it, and parse the WAV the code's (export …) downloads. */
export async function exportBuffer(page: Page, code: string, timeout = 30_000): Promise<Wav16> {
  await page.evaluate((t) => {
    const v = window.__REPULSE_EDITOR__;
    v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: t }, selection: { anchor: 0 } });
    v.focus();
  }, code);
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout }),
    page.keyboard.press('Alt+Enter'),
  ]);
  const wav = parseWav16(readFileSync((await download.path())!));
  assertFinitePcm(wav.left);
  assertFinitePcm(wav.right);
  return wav;
}

export const monoOf = (w: Wav16) => mixMono(w.left, w.right);
