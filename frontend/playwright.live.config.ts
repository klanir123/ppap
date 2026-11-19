import { defineConfig, devices } from '@playwright/test'

/**
 * E2E against the running multi-participant stack (`make up PROFILE=tier2`).
 * Participant URLs: E2E_OEM_URL, E2E_TIER1_URL, E2E_TIER2_URL (defaults: local compose ports).
 * E2E_CHROMIUM_PATH optionally points to a preinstalled Chromium (instead of `playwright install`).
 * E2E_SCREENSHOT_DIR: where key screens are saved (default test-results/live-screens).
 */
export default defineConfig({
  testDir: './e2e/live',
  // scenarios share the live databases – run them one after another
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 180_000,
  expect: { timeout: 15_000 },
  reporter: 'list',
  outputDir: 'test-results/live',
  use: {
    locale: 'de-DE',
    viewport: { width: 1440, height: 960 },
    deviceScaleFactor: 2,
    actionTimeout: 20_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    launchOptions: process.env.E2E_CHROMIUM_PATH ? { executablePath: process.env.E2E_CHROMIUM_PATH } : {},
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 960 }, deviceScaleFactor: 2 },
    },
  ],
})
