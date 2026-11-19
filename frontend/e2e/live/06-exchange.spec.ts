import { expect, test, type Browser, type Page } from '@playwright/test'

import {
  createOemDraft,
  findRelation,
  login,
  resolveIssues,
  shot,
  submitPackage,
  URLS,
  waitForCase,
  waitForStatus,
  setExchangeMode,
  waitForDocumentsFetched,
} from './support'

/**
 * Phase 4/5 – full exchange over Catena-X/EDC between two participants (two browser contexts):
 * OEM requests → Tier-1 receives (source EXCHANGE) → works, submits (default mode PUSH_NOTIFY_PULL_PAYLOAD:
 * twin + notification) → OEM pulls the signed revision via the DTR and the documents via EDC (hash verified,
 * PDF preview), reviews and rejects → Tier-1 receives the decision with the finding.
 */

const REJECTION = 'E2E: Merkmal 12 außerhalb der Toleranz'

async function participant(browser: Browser, name: keyof typeof URLS, user: string): Promise<Page> {
  const context = await browser.newContext()
  const page = await context.newPage()
  await login(page, name, user)
  return page
}

test('OEM ↔ Tier-1 over Catena-X: request, submission, decision', async ({ browser }) => {
  test.setTimeout(420_000)
  const oem = await participant(browser, 'oem', 'oem.quality')
  const tier1 = await participant(browser, 'tier1', 'tier1.quality')
  // seed default; a previous aborted run of spec 08 could have left another mode
  await setExchangeMode(tier1, (await findRelation(tier1, /OEM/i, 'CUSTOMER')).id, 'PUSH_NOTIFY_PULL_PAYLOAD')

  // --- OEM: create + send ----------------------------------------------------------------------
  const draft = await createOemDraft(oem, 'E2E Phase 4: Austausch über Catena-X')
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
  // default mode: twin registered in the own DTR, customer notified (PPAP_SUBMISSION_AVAILABLE)
  const delivery = tier1.getByRole('region', { name: 'Zustellung an den Kunden' })
  await expect(delivery.getByText('Zugestellt', { exact: true })).toBeVisible({ timeout: 90_000 })
  await shot(tier1, 'p4-tier1-submitted-delivered')

  // --- OEM: signed revision received ------------------------------------------------------------
  await waitForStatus(oem, draft.id, 'SUBMITTED')
  await oem.goto(`${URLS.oem}/approvals/${draft.id}?tab=revisions`)
  await expect(oem.getByRole('status', { name: 'Status: Eingereicht' }).first()).toBeVisible()
  await expect(oem.getByRole('status', { name: 'Status: Signatur gültig' }).first()).toBeVisible()
  await expect(oem.locator('[data-exchange-mode="PUSH_NOTIFY_PULL_PAYLOAD"]').first()).toBeVisible()
  // documents are pulled over EDC and verified (SHA-256), then previewable like own documents
  await waitForDocumentsFetched(oem, draft.id, 1)
  await oem.reload()
  const evidence = oem.locator('details').filter({ hasText: 'PPF-03' })
  await evidence.locator('summary').click()
  const doc = evidence.locator('li[data-transfer-status="FETCHED"]').filter({ hasText: 'messprotokoll.pdf' }).first()
  await expect(doc.getByText('Über Catena-X abgerufen')).toBeVisible()
  await expect(doc.getByText('Hash geprüft')).toBeVisible()
  await doc.getByRole('button', { name: 'messprotokoll.pdf anzeigen' }).click()
  const preview = oem.getByRole('dialog', { name: 'messprotokoll.pdf' })
  await expect(preview.locator('canvas').first()).toBeVisible({ timeout: 30_000 })
  await expect(preview.getByRole('status').filter({ hasText: 'Hash geprüft' })).toBeVisible()
  await shot(oem, 'p5-oem-document-preview')
  await oem.keyboard.press('Escape')
  await shot(oem, 'p4-oem-received-submission')

  await oem.getByRole('tab', { name: 'Datenaustausch' }).click()
  const request = oem.getByRole('article', { name: /^Anforderung/ })
  await expect(request.getByRole('status', { name: 'Status: Zugestellt' })).toBeVisible()
  const available = oem.getByRole('article', { name: /^Paket bereitgestellt/ })
  await expect(available.getByRole('status', { name: 'Status: Verarbeitet' })).toBeVisible()
  const pull = oem.getByRole('article', { name: /^Paketabruf/ })
  await expect(pull.getByRole('status', { name: 'Status: Verarbeitet' })).toBeVisible()
  await pull.getByRole('button', { name: /Ablauf anzeigen/ }).click()
  await expect(pull.getByRole('region', { name: 'Digital Twin Registry des Lieferanten' })).toBeVisible()
  for (const step of ['Twin-Suche', 'Twin-Beschreibung', 'Paketabruf', 'Hash-Prüfung', 'Signaturprüfung'])
    await expect(pull.getByRole('listitem').filter({ hasText: step }).first()).toBeVisible()
  await expect(oem.getByRole('article', { name: /^Dokumentabruf/ }).first()).toBeVisible()
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
