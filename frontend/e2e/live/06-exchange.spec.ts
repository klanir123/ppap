import { expect, test, type Browser, type Page } from '@playwright/test'

import {
  api,
  inDays,
  login,
  resolveIssues,
  shot,
  submitPackage,
  URLS,
  waitForCase,
  waitForStatus,
  type CaseRow,
} from './support'

/**
 * Phase 4 – full exchange over Catena-X/EDC between two participants (two browser contexts):
 * OEM requests → Tier-1 receives (source EXCHANGE) → works, submits → OEM receives the signed revision,
 * reviews and rejects → Tier-1 receives the decision with the finding.
 */

interface Mapping {
  relationId: string
  part: { id: string; partNumber: string }
  partner: { bpnl: string; name?: string; exchangeReady: boolean }
}

const ELEMENTS = [
  { code: 'PPF-01', requirement: 'REQUIRED' },
  { code: 'PPF-03', requirement: 'REQUIRED' },
]
const REJECTION = 'E2E: Merkmal 12 außerhalb der Toleranz'

async function participant(browser: Browser, name: keyof typeof URLS, user: string): Promise<Page> {
  const context = await browser.newContext()
  const page = await context.newPage()
  await login(page, name, user)
  return page
}

/** OEM draft for Demo-Tier1 via API (the wizard itself is covered by spec 01). */
async function createDraft(oem: Page): Promise<CaseRow> {
  const mappings = await api<{ content: Mapping[] }>(oem, '/api/v1/part-mappings?partnerRole=SUPPLIER&size=200')
  const mapping = mappings.content.find((m) => m.partner.exchangeReady && /Tier1/i.test(m.partner.name ?? ''))
  expect(mapping, 'exchange-ready part mapping to Demo-Tier1').toBeTruthy()
  return api<CaseRow>(oem, '/api/v1/approval-cases', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      partId: mapping!.part.id,
      relationId: mapping!.relationId,
      reasonCode: 'NEW_PART',
      levelCode: 'VDA-2',
      dueDate: inDays(21),
      remarks: 'E2E Phase 4: Austausch über Catena-X',
      elements: ELEMENTS,
    }),
  })
}

test('OEM ↔ Tier-1 over Catena-X: request, submission, decision', async ({ browser }) => {
  test.setTimeout(420_000)
  const oem = await participant(browser, 'oem', 'oem.quality')
  const tier1 = await participant(browser, 'tier1', 'tier1.quality')

  // --- OEM: create + send ----------------------------------------------------------------------
  const draft = await createDraft(oem)
  await oem.goto(`${URLS.oem}/approvals/${draft.id}`)
  await expect(oem.getByRole('status', { name: 'Status: Entwurf' }).first()).toBeVisible()
  await oem.getByRole('button', { name: 'Anfordern', exact: true }).click()
  await expect(oem.getByRole('status', { name: 'Status: Angefordert' }).first()).toBeVisible()
  // outbound request delivered via EDC (header badge links to the exchange tab)
  await expect(oem.getByRole('link', { name: /Anforderung: Zugestellt/ })).toBeVisible({ timeout: 90_000 })

  // --- Tier-1: request arrives (live list update, reload as fallback) --------------------------
  await tier1.goto(`${URLS.tier1}/approvals`)
  const received = await waitForCase(tier1, draft.exchangeId)
  expect(received.source).toBe('EXCHANGE')
  expect(received.status).toBe('REQUESTED')
  const row = tier1
    .getByRole('table', { name: 'Freigabevorgänge' })
    .getByRole('row')
    .filter({ hasText: received.caseNumber })
  await expect(row)
    .toBeVisible({ timeout: 10_000 })
    .catch(async () => {
      await tier1.reload()
      await expect(row).toBeVisible()
    })
  await expect(row.getByText('Catena-X')).toBeVisible()
  await shot(tier1, 'p4-tier1-request-received')

  await row.getByRole('link', { name: received.caseNumber }).click()
  await expect(tier1.getByRole('heading', { level: 1, name: received.caseNumber })).toBeVisible()
  await tier1.getByRole('tab', { name: 'Datenaustausch' }).click()
  const inboundRequest = tier1.getByRole('article', { name: /Anforderung/ })
  await expect(inboundRequest.getByRole('status', { name: 'Status: Verarbeitet' })).toBeVisible()
  await expect(inboundRequest).toContainText('Eingehend von')
  await shot(tier1, 'p4-tier1-exchange-tab')

  // --- Tier-1: start work → package → submit ----------------------------------------------------
  await tier1.getByRole('button', { name: 'Bearbeitung starten' }).click()
  await expect(tier1.getByRole('heading', { level: 1, name: 'PPF-Paket erstellen' })).toBeVisible()
  await resolveIssues(tier1, received.id)
  await tier1.reload()
  await expect(tier1.getByText('Alle Pflichtangaben sind vollständig.')).toBeVisible()
  await submitPackage(tier1)
  await expect(tier1.getByRole('status', { name: 'Status: Signatur gültig' })).toBeVisible()
  const delivery = tier1.getByRole('region', { name: 'Zustellung an den Kunden' })
  await expect(delivery.getByText('Zugestellt', { exact: true })).toBeVisible({ timeout: 90_000 })
  await shot(tier1, 'p4-tier1-submitted-delivered')

  // --- OEM: signed revision received ------------------------------------------------------------
  await waitForStatus(oem, draft.id, 'SUBMITTED')
  await oem.goto(`${URLS.oem}/approvals/${draft.id}?tab=revisions`)
  await expect(oem.getByRole('status', { name: 'Status: Eingereicht' }).first()).toBeVisible()
  await expect(oem.getByRole('status', { name: 'Status: Signatur gültig' }).first()).toBeVisible()
  // received documents are metadata only (retrieval via Catena-X follows)
  const evidence = oem.locator('details').filter({ hasText: 'PPF-03' })
  await evidence.locator('summary').click()
  await expect(evidence.getByText('Abruf des Dokuments über Catena-X folgt').first()).toBeVisible()
  await expect(evidence.getByText('messprotokoll.pdf').first()).toBeVisible()
  await shot(oem, 'p4-oem-received-submission')

  await oem.getByRole('tab', { name: 'Datenaustausch' }).click()
  const request = oem.getByRole('article', { name: /^Anforderung/ })
  await expect(request.getByRole('status', { name: 'Status: Zugestellt' })).toBeVisible()
  const submission = oem.getByRole('article', { name: /PPF-Paket/ })
  await expect(submission.getByRole('status', { name: 'Status: Verarbeitet' })).toBeVisible()
  await request.getByRole('button', { name: /Ablauf anzeigen/ }).click()
  for (const step of ['Katalog', 'Vertragsverhandlung', 'Transfer', 'Zugangsschlüssel', 'Zustellung'])
    await expect(request.getByRole('listitem').filter({ hasText: step }).first()).toBeVisible()
  await expect(request.getByLabel('Technische Details zu Zustellung')).toContainText('HTTP-Status')
  await shot(oem, 'p4-oem-exchange-tab')

  // --- OEM: review, finding NOT_ACCEPTED, decision REJECTED -------------------------------------
  await oem.getByRole('button', { name: 'Prüfung starten' }).click()
  await expect(oem).toHaveURL(/\/review/)
  const elements = oem.getByRole('navigation', { name: 'Elemente' })
  await elements.getByRole('button', { name: /PPF-01/ }).click()
  await oem.getByRole('radiogroup', { name: 'Prüfergebnis' }).getByRole('radio', { name: 'Nicht akzeptiert' }).click()
  await oem.getByLabel(/^Kommentar\s*\*?$/).fill(REJECTION)
  await elements.getByRole('button', { name: /PPF-03/ }).click()
  await oem
    .getByRole('radiogroup', { name: 'Prüfergebnis' })
    .getByRole('radio', { name: 'Akzeptiert', exact: true })
    .click()
  await oem.getByRole('radiogroup', { name: 'Ergebnis' }).getByRole('radio', { name: 'Ablehnung' }).click()
  await expect(oem.getByText('Alle Voraussetzungen erfüllt.')).toBeVisible()
  await oem.getByRole('button', { name: 'Entscheidung bestätigen …' }).click()
  const confirm = oem.getByRole('dialog', { name: 'Entscheidung bestätigen' })
  await confirm.getByRole('checkbox').click()
  await confirm.getByRole('button', { name: 'Verbindlich entscheiden' }).click()
  await waitForStatus(oem, draft.id, 'REJECTED')

  // --- Tier-1: decision received with the finding -----------------------------------------------
  await waitForStatus(tier1, received.id, 'REJECTED')
  await tier1.goto(`${URLS.tier1}/approvals/${received.id}?tab=decision`)
  await expect(tier1.getByRole('status', { name: 'Status: Abgelehnt' }).first()).toBeVisible()
  await expect(tier1.getByText(REJECTION)).toBeVisible()
  await shot(tier1, 'p4-tier1-decision-received')

  await oem.goto(`${URLS.oem}/approvals/${draft.id}?tab=exchange`)
  await expect(
    oem.getByRole('article', { name: /Entscheidung/ }).getByRole('status', { name: 'Status: Zugestellt' }),
  ).toBeVisible({ timeout: 90_000 })
  await shot(oem, 'p4-oem-decision-delivered')

  // --- OEM connector admin: cockpit lists the messages of the case ------------------------------
  const admin = await participant(browser, 'oem', 'oem.admin')
  await admin.goto(`${URLS.oem}/cockpit`)
  await expect(admin.getByRole('heading', { level: 1, name: 'Connector-Cockpit' })).toBeVisible()
  await expect(admin.getByRole('status', { name: 'Status: Verfügbar' }).first()).toBeVisible()
  const messages = admin.getByRole('table', { name: 'Nachrichten' })
  const caseRow = messages.getByRole('row').filter({ hasText: draft.caseNumber }).filter({ hasText: 'Entscheidung' })
  await expect(caseRow.first()).toBeVisible()
  await shot(admin, 'p4-oem-cockpit')
  await caseRow.first().getByRole('button', { name: 'Entscheidung', exact: true }).click()
  const drawer = admin.getByRole('dialog')
  await expect(drawer.getByText('Payload (JSON)')).toBeVisible()
  await expect(drawer.locator('pre')).toContainText('PPAP_DECISION')
  await shot(admin, 'p4-oem-cockpit-drawer')
})
