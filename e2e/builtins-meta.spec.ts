import { expect, test } from '@playwright/test';
import { readFileSync } from 'node:fs';

// Phase QA4 — the full app env (every builtin bound by repulse.env.builtins plus the
// evaluator's special forms) and docs/ai/builtins.json (generated from
// builtin_meta.edn) must list the same names, in both directions. The :test build
// checks the lisp layer only (app/src/repulse/content/builtin_meta_test.cljs).

declare global {
  interface Window {
    __REPULSE_APP__: any;
  }
}

// Bound in the env but deliberately undocumented. A name may go here only if it is
// internal and not meant for users.
//  - play: a stub kept only to throw "play is renamed to track" (tracks.cljs); it is
//    deliberately undocumented, so it gets no completion and no hover.
const ENV_EXCLUDED = new Set<string>(['play']);

// Documented in builtin_meta.edn but not an env binding: synth voices are keywords
// passed to (synth :name …), effect names are keywords passed to (fx :name …), and
// UGens exist only inside a defsynth body. Entries carry these categories; nothing
// else may be documented without being bound.
const META_ONLY_CATEGORIES = new Set(['synth', 'effect', 'ugen']);

test('B1: env names and builtin_meta.edn names agree in both directions', async ({ page }) => {
  await page.goto('/');
  await page.waitForFunction(() => !!window.__REPULSE_APP__?.builtinNames);
  const env: string[] = await page.evaluate(() => window.__REPULSE_APP__.builtinNames());
  const meta = JSON.parse(readFileSync('docs/ai/builtins.json', 'utf8')) as Record<
    string,
    { category: string }
  >;

  expect(env.length).toBeGreaterThan(300);
  const envMissingFromMeta = env.filter((n) => !(n in meta) && !ENV_EXCLUDED.has(n));
  const metaMissingFromEnv = Object.keys(meta).filter(
    (n) => !env.includes(n) && !META_ONLY_CATEGORIES.has(meta[n].category),
  );
  expect(envMissingFromMeta, 'bound in the env but not in builtin_meta.edn').toEqual([]);
  expect(metaMissingFromEnv, 'in builtin_meta.edn but not bound in the env').toEqual([]);
});
