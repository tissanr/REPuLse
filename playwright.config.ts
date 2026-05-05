import { defineConfig } from '@playwright/test';

const port = Number(process.env.REPULSE_E2E_PORT ?? 3100);
const baseURL = `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: 0,
  use: {
    baseURL,
    headless: true,
  },
  webServer: {
    command:
      `npx shadow-cljs compile test-harness && node scripts/serve-static.mjs app/public ${port}`,
    url: `${baseURL}/test-harness.html`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  projects: [
    {
      name: 'chromium',
      use: {
        browserName: 'chromium',
        launchOptions: {
          args: ['--autoplay-policy=no-user-gesture-required'],
        },
      },
    },
  ],
});
