import { expect, test, type Browser, type Page } from '@playwright/test'

import {
  api,
  createOemDraft,
  findRelation,
  inDays,
  login,
  resolveIssues,
  setExchangeMode,
  shot,
  submitPackage,
  URLS,
  waitForCase,
  waitForDocumentsFetched,
  waitForStatus,
  type CaseRow,
  uniquePdf,
} from './support'

/**
 * Phase 6 – cascade (UC-09) over three participants: OEM requests Tier-1 → Tier-1 requests the sub-part from
 * Tier-2 out of the case → Tier-2 submits → Tier-1 approves → Tier-1 references the decided child in its own
 * package and submits → the OEM sees the sub-supplier reference (metadata only) and none of Tier-2's documents.
 */

async function participant(browser: Browser, name: keyof typeof URLS, user: string): Promise<Page> {
  const context = await browser.newContext()
  const page = await context.newPage()
  await login(page, name, user)
  return page
}

interface Submission {
  elements: { documents: { sha256: string }[] }[]
  subSupplierReferences: { caseNumber: string; subSupplierName?: string; decisionResult: string }[]
}

const hashes = (s: Submission) => s.elements.flatMap((e) => e.documents.map((d) => d.sha256))

/** Customer review in the workspace: every element accepted, overall approval, confirmed. */
async function approve(page: Page, base: string, id: string) {
  await page.goto(`${base}/approvals/${id}/review`)
  const elements = page.getByRole('navigation', { name: 'Elemente' })
  await expect(elements).toBeVisible()
  const start = page.getByRole('button', { name: 'Prüfung starten' })
  if ((await start.count()) > 0) {
    await start.first().click()
    await waitForStatus(page, id, 'IN_REVIEW')
  }
  for (const button of await elements.getByRole('button').all()) {
    await button.click()
    await page
      .getByRole('radiogroup', { name: 'Prüfergebnis' })
      .getByRole('radio', { name: 'Akzeptiert', exact: true })
      .click()
  }
  await page.getByRole('radiogroup', { name: 'Ergebnis' }).getByRole('radio', { name: 'Freigabe', exact: true }).click()
  await expect(page.getByText('Alle Voraussetzungen erfüllt.')).toBeVisible()
  await page.getByRole('button', { name: 'Entscheidung bestätigen …' }).click()
  const confirm = page.getByRole('dialog', { name: 'Entscheidung bestätigen' })
  await confirm.getByRole('checkbox').click()
  await confirm.getByRole('button', { name: 'Verbindlich entscheiden' }).click()
}

test('Cascade OEM → Tier-1 → Tier-2: sub-supplier approval referenced, no Tier-2 documents at the OEM', async ({
  browser,
}) => {
  test.setTimeout(600_000)
  const oem = await participant(browser, 'oem', 'oem.quality')
  const tier1 = await participant(browser, 'tier1', 'tier1.quality')
  const tier2 = await participant(browser, 'tier2', 'tier2.quality')
  await setExchangeMode(tier1, (await findRelation(tier1, /OEM/i, 'CUSTOMER')).id, 'PUSH_NOTIFY_PULL_PAYLOAD')

  // --- OEM → Tier-1 -------------------------------------------------------------------------------
  const draft = await createOemDraft(oem, 'E2E Phase 6: Kaskade')
  await oem.goto(`${URLS.oem}/approvals/${draft.id}`)
  await oem.getByRole('button', { name: 'Anfordern', exact: true }).click()
  await expect(oem.getByRole('status', { name: 'Status: Angefordert' }).first()).toBeVisible()
  const parent = await waitForCase(tier1, draft.exchangeId)

  // --- Tier-1: sub-supplier request out of the case (wizard prefilled) ------------------------------
  await tier1.goto(`${URLS.tier1}/approvals/${parent.id}`)
  await expect(tier1.getByRole('heading', { level: 1, name: parent.caseNumber })).toBeVisible()
  await tier1.getByRole('button', { name: 'Unterlieferanten-Vorlage anfordern' }).click()
  await expect(tier1.getByRole('heading', { level: 1, name: 'Unterlieferanten-Vorlage anfordern' })).toBeVisible()
  await expect(tier1.getByTestId('sub-request-parent')).toContainText(parent.caseNumber)
  await tier1
    .getByRole('radio', { name: /Demo-Tier2/ })
    .first()
    .click()
  await tier1.getByRole('button', { name: 'Weiter' }).click()
  const supplier = tier1.getByRole('radio', { name: /Demo-Tier2/ }).first()
  if ((await supplier.getAttribute('aria-checked')) !== 'true') await supplier.click()
  await tier1.getByRole('button', { name: 'Weiter' }).click()
  // reason & level taken over from the customer request
  await tier1.getByRole('button', { name: 'Weiter' }).click()
  await expect(tier1.getByText(/\d+ Elemente? ausgewählt/).first()).toBeVisible()
  await tier1.getByRole('button', { name: 'Weiter' }).click()
  await tier1.getByLabel(/^Termin/).fill(inDays(10))
  await tier1.getByRole('button', { name: 'Weiter' }).click()
  await shot(tier1, 'p6-tier1-sub-request-summary')
  await tier1.getByRole('button', { name: 'Anfordern', exact: true }).click()
  await expect(tier1).toHaveURL(/\/approvals\/[0-9a-f-]{36}$/)
  await expect(tier1.getByTestId('parent-case-link')).toContainText(parent.caseNumber)
  const childId = tier1.url().split('/').pop()!
  const child = await api<CaseRow>(tier1, `/api/v1/approval-cases/${childId}`)
  expect(child.ownRole).toBe('CUSTOMER')

  // parent shows the child as blocking
  await tier1.goto(`${URLS.tier1}/approvals/${parent.id}`)
  const panel = tier1.getByTestId('sub-suppliers-panel')
  await expect(panel.getByRole('link', { name: new RegExp(child.caseNumber) })).toBeVisible()
  await expect(panel).toContainText('noch offen')
  await shot(tier1, 'p6-tier1-parent-with-child')

  // --- Tier-2: works and submits ------------------------------------------------------------------
  const tier2Case = await waitForCase(tier2, child.exchangeId)
  await tier2.goto(`${URLS.tier2}/approvals/${tier2Case.id}`)
  await tier2.getByRole('button', { name: 'Bearbeitung starten' }).click()
  await expect(tier2.getByRole('heading', { level: 1, name: 'PPF-Paket erstellen' })).toBeVisible()
  // unique documents: Tier-2 evidence must never reach the OEM (checked by hash below), and Tier-1 may not attach
  // anything it received from a sub-supplier – seed PDFs would collide with earlier runs and other specs
  await resolveIssues(tier2, tier2Case.id, undefined, uniquePdf('e2e-tier2-nachweis'))
  await tier2.reload()
  await submitPackage(tier2)

  // --- Tier-1: reviews and approves the child ---------------------------------------------------------
  await waitForStatus(tier1, childId, 'SUBMITTED', 120_000)
  await waitForDocumentsFetched(tier1, childId, 1)
  const childSubmission = await api<Submission>(tier1, `/api/v1/approval-cases/${childId}/submissions/1`)
  const tier2Hashes = hashes(childSubmission)
  expect(tier2Hashes.length).toBeGreaterThan(0)
  await approve(tier1, URLS.tier1, childId)
  await waitForStatus(tier1, childId, 'APPROVED')

  // --- Tier-1: references the decided child in its own package and submits --------------------------
  await tier1.goto(`${URLS.tier1}/approvals/${parent.id}`)
  await tier1.getByRole('button', { name: 'Bearbeitung starten' }).click()
  await expect(tier1.getByRole('heading', { level: 1, name: 'PPF-Paket erstellen' })).toBeVisible()
  const references = tier1.getByTestId('sub-supplier-references')
  const candidate = references.getByTestId('sub-supplier-candidate').filter({ hasText: child.caseNumber })
  await candidate.getByRole('checkbox').check()
  await expect(references).toContainText('1 Freigabe referenziert')
  await expect
    .poll(
      async () =>
        (await api<Submission>(tier1, `/api/v1/approval-cases/${parent.id}/submissions/draft`)).subSupplierReferences
          .length,
    )
    .toBe(1)
  await shot(tier1, 'p6-tier1-package-references')
  await resolveIssues(tier1, parent.id, undefined, uniquePdf('e2e-tier1-nachweis'))
  await tier1.reload()
  await submitPackage(tier1)

  // --- OEM: sees the reference, but no Tier-2 documents --------------------------------------------------
  await waitForStatus(oem, draft.id, 'SUBMITTED', 120_000)
  await oem.goto(`${URLS.oem}/approvals/${draft.id}?tab=revisions`)
  const received = oem.getByTestId('sub-supplier-reference-list')
  await expect(received).toBeVisible({ timeout: 30_000 })
  await expect(received.getByTestId('sub-supplier-reference')).toContainText('Demo-Tier2')
  await expect(received.getByTestId('sub-supplier-reference')).toContainText('Freigegeben')
  await expect(received.getByTestId('sub-supplier-no-documents')).toContainText('nicht weitergegeben')
  await oem.goto(`${URLS.oem}/approvals/${draft.id}/review`)
  await expect(oem.getByTestId('sub-supplier-reference-list')).toBeVisible()
  await shot(oem, 'p6-oem-review-sub-supplier')

  const oemSubmission = await api<Submission>(oem, `/api/v1/approval-cases/${draft.id}/submissions/1`)
  expect(oemSubmission.subSupplierReferences.map((r) => r.caseNumber)).toContain(child.caseNumber)
  for (const hash of tier2Hashes) expect(hashes(oemSubmission)).not.toContain(hash)
})
