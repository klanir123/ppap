import { expect, test } from '@playwright/test'

import { login, URLS } from './support'

test('master data manager cannot request submissions', async ({ page }) => {
  await login(page, 'oem', process.env.E2E_MASTERDATA_USER ?? 'oem.masterdata')
  await expect(page.getByRole('link', { name: 'Vorlage anfordern' })).toHaveCount(0)
  await page.goto(`${URLS.oem}/approvals/new`)
  await expect(page.getByText('Kein Zugriff')).toBeVisible()
})
