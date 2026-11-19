import type { Page } from '@playwright/test';

declare global {
  interface Window {
    __cspViolations__: string[];
  }
}

/** Record `securitypolicyviolation` events in the page. Call before `page.goto`. */
export async function trackCsp(page: Page) {
  await page.addInitScript(() => {
    window.__cspViolations__ = [];
    document.addEventListener('securitypolicyviolation', (e) => {
      window.__cspViolations__.push(`${e.violatedDirective} ${e.blockedURI}`);
    });
  });
}

export const cspViolations = (page: Page): Promise<string[]> =>
  page.evaluate(() => window.__cspViolations__ ?? []);
