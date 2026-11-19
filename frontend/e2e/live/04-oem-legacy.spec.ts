import { expect, test } from '@playwright/test'

import { api, login, shot, URLS } from './support'

test('OEM – legacy case: revisions (unsigned, legacy data) and decision', async ({ page }) => {
  await login(page, 'oem', 'oem.quality')
  const found = await api<{ content: { id: string; caseNumber: string }[] }>(
    page,
    '/api/v1/approval-cases?q=PPF-2025-00001',
  )
  const c = found.content.find((x) => x.caseNumber === 'PPF-2025-00001')!
  await page.goto(`${URLS.oem}/approvals/${c.id}`)

  await expect(page.getByRole('heading', { level: 1, name: 'PPF-2025-00001' })).toBeVisible()
  await expect(page.getByText('Altdaten').first()).toBeVisible()

  await page.getByRole('tab', { name: /Revisionen/ }).click()
  const revisions = page.getByRole('navigation', { name: 'Revisionen' }).getByRole('button')
  await expect(revisions).toHaveCount(2)
  await expect(page.getByRole('status', { name: 'Status: Nicht signiert' }).first()).toBeVisible()
  await revisions.last().click()
  await expect(page.getByRole('heading', { name: 'Revision 1' })).toBeVisible()
  await shot(page, 'oem-legacy-revisions')

  await page.getByRole('tab', { name: 'Entscheidung' }).click()
  await expect(page.getByText(/entschieden am/)).toBeVisible()
  await shot(page, 'oem-legacy-decision')
})
