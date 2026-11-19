import { expect, test, type Page } from '@playwright/test'
import path from 'node:path'

import { api, draftOf, inDays, login, resolveIssues, SEED, shot, URLS, type Draft } from './support'

interface CaseRow {
  id: string
  caseNumber: string
  status: string
  ownRole: string
  allowedActions: string[]
  version: number
}

/** Existing IN_PROGRESS supplier case – or a fresh one (manual capture + start work) so the spec is re-runnable. */
async function caseInProgress(page: Page): Promise<CaseRow> {
  const page_ = await api<{ content: CaseRow[] }>(
    page,
    '/api/v1/approval-cases?status=IN_PROGRESS&ownRole=SUPPLIER&size=50',
  )
  const existing = page_.content.find((c) => c.allowedActions.includes('SUBMIT'))
  if (existing) return existing
  const mapping = (
    await api<{ content: { part: { id: string }; partner: { bpnl: string }; partnerPartNumber: string }[] }>(
      page,
      '/api/v1/part-mappings?partnerRole=CUSTOMER&size=1',
    )
  ).content[0]!
  const json = { 'Content-Type': 'application/json' }
  const created = await api<CaseRow>(page, '/api/v1/approval-cases/manual', {
    method: 'POST',
    headers: json,
    body: JSON.stringify({
      partnerBpnl: mapping.partner.bpnl,
      partId: mapping.part.id,
      partnerPartNumber: mapping.partnerPartNumber,
      reasonCode: 'NEW_PART',
      levelCode: 'VDA-2',
      dueDate: inDays(21),
      externalReference: 'E2E',
    }),
  })
  await api(page, `/api/v1/approval-cases/${created.id}/start-work`, {
    method: 'POST',
    headers: { 'If-Match': `"${created.version}"` },
  })
  return created
}

test('Tier-2 – package: Excel import, documents, values, submit', async ({ page }) => {
  await login(page, 'tier2', 'tier2.quality')
  const c = await caseInProgress(page)
  await page.goto(`${URLS.tier2}/approvals/${c.id}/package`)
  await expect(page.getByRole('heading', { level: 1, name: 'PPF-Paket erstellen' })).toBeVisible()
  const before = await draftOf(page, c.id)

  // Excel import: preview → apply
  await page.getByRole('button', { name: 'Excel-Formblatt importieren' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.locator('input[type=file]').setInputFiles(path.join(SEED, 'forms/ppf-formblatt-v1-beispiel-tier2.xlsx'))
  await dialog.getByRole('button', { name: 'Vorschau' }).click()
  await expect(dialog.getByText(/^Erkannte Vorlage:/)).toBeVisible()
  await expect(dialog.getByRole('table', { name: 'Elementbewertungen' })).toBeVisible()
  await shot(page, 'tier2-excel-preview')
  await dialog.getByRole('button', { name: /Elemente? übernehmen$/ }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByText(/übernommen/).first()).toBeVisible()
  const afterImport = await draftOf(page, c.id)
  const missing = (d: Draft) => d.completeness.issues.filter((i) => i.code === 'MISSING_SELF_ASSESSMENT').length
  // imported self-assessments close MISSING_SELF_ASSESSMENT issues (re-runs: nothing left to import)
  expect(missing(afterImport)).toBeLessThanOrEqual(missing(before))
  if (missing(before) > 3) expect(missing(afterImport)).toBeLessThan(missing(before))
  await expect(page.getByText(new RegExp(`${afterImport.completeness.issues.length} offene`))).toBeVisible()

  // resolve the remaining issues through the editor
  await resolveIssues(page, c.id, () => shot(page, 'tier2-package-editor'))
  await expect.poll(async () => (await draftOf(page, c.id)).completeness.complete).toBe(true)
  await page.reload()
  await expect(page.getByText('Alle Pflichtangaben sind vollständig.')).toBeVisible()
  await shot(page, 'tier2-package-complete')

  // submit with explicit confirmation
  await page.getByRole('button', { name: 'Einreichen' }).click()
  const submit = page.getByRole('dialog')
  await submit.getByRole('checkbox').click()
  await submit.getByRole('button', { name: 'Verbindlich einreichen' }).click()
  await expect(page.getByRole('heading', { level: 1, name: /Revision \d+ eingereicht/ })).toBeVisible()
  await expect(page.getByRole('status', { name: 'Status: Signatur gültig' })).toBeVisible()
  await expect(page.locator('code').first()).toBeVisible()
  await shot(page, 'tier2-submitted')
})
