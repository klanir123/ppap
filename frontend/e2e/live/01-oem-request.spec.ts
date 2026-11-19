import { expect, test } from '@playwright/test'

import { inDays, login, shot } from './support'

test.describe('OEM – customer quality engineer', () => {
  test('dashboard shows live stats, list and board', async ({ page }) => {
    await login(page, 'oem', 'oem.quality')
    const kpis = page.getByRole('region', { name: 'Dashboard' })
    await expect(kpis.getByText('Offene Vorgänge')).toBeVisible()
    // KPI values come from GET /approval-cases/stats (numbers, not the "–" placeholder)
    await expect(kpis.locator('p.text-3xl').first()).toHaveText(/^\d+$/)
    await expect(page.getByText('Aktuelle Freigabevorgänge')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Vorlage anfordern' })).toBeVisible()
    await shot(page, 'oem-dashboard')

    await page.getByRole('link', { name: 'Freigabevorgänge' }).first().click()
    const table = page.getByRole('table', { name: 'Freigabevorgänge' })
    await expect(table.getByRole('link', { name: /^PPF-\d{4}-\d{5}$/ }).first()).toBeVisible()
    await shot(page, 'oem-list')

    await page.getByRole('radio', { name: 'Board' }).click()
    await expect(page).toHaveURL(/view=board/)
    const board = page.getByRole('region', { name: 'Board nach Status' })
    await expect(board.getByRole('heading', { name: /Angefordert/ })).toBeVisible()
    await expect(board.getByRole('link').first()).toBeVisible()
    await shot(page, 'oem-board')
  })

  test('request wizard end-to-end → case requested', async ({ page }) => {
    await login(page, 'oem', 'oem.quality', '/approvals/new')
    await expect(page.getByRole('heading', { level: 1, name: 'Vorlage anfordern' })).toBeVisible()

    // 1 part with a mapping to Demo-Tier1 (exchange-ready)
    await page
      .getByRole('radio', { name: /Demo-Tier1 GmbH/ })
      .first()
      .click()
    await page.getByRole('button', { name: 'Weiter' }).click()
    // 2 supplier (preselected if unique)
    const supplier = page.getByRole('radio', { name: /Demo-Tier1 GmbH/ })
    if ((await supplier.getAttribute('aria-checked')) !== 'true') await supplier.click()
    await expect(page.getByText('Catena-X bereit').first()).toBeVisible()
    await page.getByRole('button', { name: 'Weiter' }).click()
    // 3 reason & level
    await page.getByRole('combobox', { name: 'Vorlagegrund' }).click()
    await page.getByRole('option').first().click()
    await page.getByRole('radiogroup', { name: 'Vorlagestufe' }).getByRole('radio').nth(1).click()
    await page.getByRole('button', { name: 'Weiter' }).click()
    // 4 elements prefilled from the level matrix
    await expect(page.getByText(/\d+ Elemente? ausgewählt/).first()).toBeVisible()
    await shot(page, 'oem-wizard-elements')
    await page.getByRole('button', { name: 'Weiter' }).click()
    // 5 due date
    await page.getByLabel(/^Termin/).fill(inDays(28))
    await page.getByLabel(/^Bemerkungen/).fill('E2E: bitte Messprotokolle mit Rohdaten beifügen.')
    await page.getByRole('button', { name: 'Weiter' }).click()
    // 6 summary → request
    await expect(page.getByRole('heading', { name: 'Zusammenfassung' })).toBeVisible()
    await shot(page, 'oem-wizard-summary')
    await page.getByRole('button', { name: 'Anfordern', exact: true }).click()

    await expect(page).toHaveURL(/\/approvals\/[0-9a-f-]{36}$/)
    await expect(page.getByRole('status', { name: 'Status: Angefordert' }).first()).toBeVisible()
    const timeline = page.getByRole('list').filter({ hasText: 'Entwurf → Angefordert' })
    await expect(timeline).toBeVisible()
    await page.getByRole('tab', { name: 'Datenaustausch' }).click()
    await expect(page.getByText(/ausschließlich über die Catena-X-Connectoren beider Partner/)).toBeVisible()
    await page.getByRole('tab', { name: 'Elemente' }).click()
    await shot(page, 'oem-case-requested')
  })
})
