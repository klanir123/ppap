import { expect, test } from '@playwright/test'

// Requires a running backend (GET /api/v1/config must answer).
test('login landing page shows product and participant context', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: /Willkommen|Welcome/ })).toBeVisible()
  await expect(page.getByRole('button', { name: /Anmelden|Sign in/ })).toBeEnabled()
  await expect(page.getByText(/^BPNL[0-9A-Z]{12}$/)).toBeVisible()
})

test.fixme('signs in via Keycloak and shows the app shell', async () => {
  // TODO(phase 1+): drive the Keycloak login form with a seeded demo user, then assert
  // the participant context in the top bar and run axe-core on the dashboard.
})
