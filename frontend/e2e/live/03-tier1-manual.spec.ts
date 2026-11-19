import { expect, test } from '@playwright/test'

import { inDays, login, shot } from './support'

test('Tier-1 – capture a manual request of a customer without Catena-X', async ({ page }) => {
  await login(page, 'tier1', 'tier1.quality', '/approvals')
  await page.getByRole('button', { name: 'Anforderung manuell erfassen' }).click()
  const dialog = page.getByRole('dialog', { name: 'Anforderung manuell erfassen' })

  await dialog.getByRole('combobox', { name: 'Kunde' }).click()
  await page.getByRole('option', { name: 'Beispiel Nutzfahrzeuge AG' }).click()
  await dialog.getByRole('combobox', { name: 'Teil' }).click()
  await page.getByRole('option').first().click()
  await expect(dialog.getByLabel('Kundensachnummer')).not.toHaveValue('')
  await dialog.getByLabel('Externe Referenz').fill(`E2E-Portal-${Date.now()}`)
  await dialog.getByRole('combobox', { name: 'Vorlagegrund' }).click()
  await page.getByRole('option').first().click()
  await dialog.getByRole('combobox', { name: 'Vorlagestufe' }).click()
  await page.getByRole('option').nth(1).click()
  await dialog.getByLabel(/^Termin/).fill(inDays(14))
  await shot(page, 'tier1-manual-dialog')
  await dialog.getByRole('button', { name: 'Erfassen' }).click()

  await expect(page).toHaveURL(/\/approvals\/[0-9a-f-]{36}$/)
  await expect(page.getByRole('status', { name: 'Status: Angefordert' }).first()).toBeVisible()
  await expect(page.getByText('Manuell erfasst').first()).toBeVisible()
  await expect(page.getByText('Beispiel Nutzfahrzeuge AG').first()).toBeVisible()
  await expect(page.getByRole('button', { name: 'Bearbeitung starten' })).toBeVisible()
  await shot(page, 'tier1-manual-case')
})
