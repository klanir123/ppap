import { expect, test, type Browser, type Page } from '@playwright/test'

import {
  api,
  createOemDraft,
  findRelation,
  login,
  resolveIssues,
  setExchangeMode,
  shot,
  submitPackage,
  URLS,
  waitForCase,
  waitForDocumentsFetched,
  waitForStatus,
  type ExchangeMode,
} from './support'

/**
 * Phase 5 – exchange mode per relation (decided by the supplier): Tier-1 (supplier towards the OEM) switches its
 * relation to PUSH in the UI → full flow → revision badge "Push"; then PULL → the OEM finds the revision by polling
 * the Tier-1 DTR (poll interval, generous timeout) → badge "Pull". The seed default PUSH_NOTIFY_PULL_PAYLOAD is
 * restored afterwards (also when the test fails).
 */

/** Generous: the customer polls the supplier DTR every PPAP_PULL_POLL_INTERVAL (default 60 s). */
const PULL_TIMEOUT = 240_000

async function participant(browser: Browser, name: keyof typeof URLS, user: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage()
  await login(page, name, user)
  return page
}

/** Tier-1 master data manager switches the relation to the OEM in the relation form (mode cards). */
async function switchMode(page: Page, relationId: string, mode: ExchangeMode, label: RegExp) {
  await page.goto(`${URLS.tier1}/masterdata/relationships/${relationId}`)
  await page.getByRole('button', { name: 'Bearbeiten' }).click()
  const sheet = page.getByRole('dialog')
  const radio = sheet.getByRole('radiogroup', { name: 'Austauschmodus' }).getByRole('radio', { name: label })
  await radio.click()
  await expect(radio).toHaveAttribute('aria-checked', 'true')
  await expect(sheet.getByText(/gilt ab der nächsten Einreichung/)).toBeVisible()
  await shot(page, `p5-tier1-mode-${mode.toLowerCase()}`)
  await sheet.getByRole('button', { name: 'Speichern' }).click()
  await expect(sheet).toBeHidden()
  await expect
    .poll(async () => (await api<{ exchangeMode: string }>(page, `/api/v1/relations/${relationId}`)).exchangeMode)
    .toBe(mode)
}

/** OEM requests (API), Tier-1 works on the received request in the UI and submits. */
async function requestAndSubmit(oem: Page, tier1: Page, remarks: string) {
  const draft = await createOemDraft(oem, remarks)
  await api(oem, `/api/v1/approval-cases/${draft.id}/send`, {
    method: 'POST',
    headers: { 'If-Match': `"${draft.version}"` },
  })
  const received = await waitForCase(tier1, draft.exchangeId)
  await tier1.goto(`${URLS.tier1}/approvals/${received.id}`)
  await expect(tier1.getByRole('heading', { level: 1, name: received.caseNumber })).toBeVisible()
  await tier1.getByRole('button', { name: 'Bearbeitung starten' }).click()
  await expect(tier1.getByRole('heading', { level: 1, name: 'PPF-Paket erstellen' })).toBeVisible()
  await resolveIssues(tier1, received.id)
  await tier1.reload()
  await expect(tier1.getByText('Alle Pflichtangaben sind vollständig.')).toBeVisible()
  await submitPackage(tier1)
  return { draft, received }
}

async function expectRevisionMode(page: Page, url: string, mode: ExchangeMode, text: string) {
  await page.goto(`${url}?tab=revisions`)
  // revision list chip (visible text + screen-reader prefix "Austauschmodus:") and the header badge with tooltip
  const chip = page.locator(`nav [data-exchange-mode="${mode}"]`).first()
  await expect(chip).toBeVisible()
  await expect(chip).toContainText(text)
  const badge = page.getByRole('button', { name: new RegExp(`^Austauschmodus: ${text.replace('+', '\\+')}\\.`) })
  await expect(badge).toBeVisible()
  await expect(badge).toHaveText(text)
}

let relationId: string | undefined

test.afterAll(async ({ browser }) => {
  if (!relationId) return
  const page = await participant(browser, 'tier1', 'tier1.masterdata')
  await setExchangeMode(page, relationId, 'PUSH_NOTIFY_PULL_PAYLOAD')
})

test('Tier-1 switches the exchange mode: PUSH, then PULL', async ({ browser }) => {
  test.setTimeout(900_000)
  const masterdata = await participant(browser, 'tier1', 'tier1.masterdata')
  const oem = await participant(browser, 'oem', 'oem.quality')
  const tier1 = await participant(browser, 'tier1', 'tier1.quality')
  const relation = await findRelation(masterdata, /OEM/i, 'CUSTOMER')
  relationId = relation.id

  // --- PUSH: the full package travels as notification; documents are still pulled ---------------
  await switchMode(masterdata, relation.id, 'PUSH', /^Push$/)
  const push = await requestAndSubmit(oem, tier1, 'E2E Phase 5: Modus Push')
  const delivery = tier1.getByRole('region', { name: 'Zustellung an den Kunden' })
  await expect(delivery.getByText('Zugestellt', { exact: true })).toBeVisible({ timeout: 90_000 })
  await waitForStatus(oem, push.draft.id, 'SUBMITTED')
  await expectRevisionMode(oem, `${URLS.oem}/approvals/${push.draft.id}`, 'PUSH', 'Push')
  await waitForDocumentsFetched(oem, push.draft.id, 1)
  await oem.goto(`${URLS.oem}/approvals/${push.draft.id}?tab=exchange`)
  await expect(
    oem.getByRole('article', { name: /^PPF-Paket/ }).getByRole('status', { name: 'Status: Verarbeitet' }),
  ).toBeVisible()
  await expect(oem.getByRole('article', { name: /^Paketabruf/ })).toHaveCount(0)
  await expect(oem.getByRole('article', { name: /^Dokumentabruf/ }).first()).toBeVisible()
  await shot(oem, 'p5-oem-push-exchange')
  await expectRevisionMode(tier1, `${URLS.tier1}/approvals/${push.received.id}`, 'PUSH', 'Push')
  // PUSH registers no digital twin
  await tier1.getByRole('tab', { name: 'Datenaustausch' }).click()
  await expect(tier1.getByText(/Im Modus Push wird kein Digital Twin registriert/)).toBeVisible()

  // --- PULL: twin only, no notification; the OEM finds the revision by polling the Tier-1 DTR -----
  await switchMode(masterdata, relation.id, 'PULL', /^Pull$/)
  const pull = await requestAndSubmit(oem, tier1, 'E2E Phase 5: Modus Pull')
  const provision = tier1.getByRole('region', { name: 'Bereitstellung für den Kunden' })
  await expect(provision.getByText('Bereitgestellt', { exact: true })).toBeVisible({ timeout: 90_000 })
  await shot(tier1, 'p5-tier1-pull-provided')
  await waitForStatus(oem, pull.draft.id, 'SUBMITTED', PULL_TIMEOUT)
  await expectRevisionMode(oem, `${URLS.oem}/approvals/${pull.draft.id}`, 'PULL', 'Pull')
  await oem.goto(`${URLS.oem}/approvals/${pull.draft.id}?tab=exchange`)
  await expect(
    oem.getByRole('article', { name: /^Paketabruf/ }).getByRole('status', { name: 'Status: Verarbeitet' }),
  ).toBeVisible()
  await expect(oem.getByRole('article', { name: /^Paket bereitgestellt/ })).toHaveCount(0)
  await shot(oem, 'p5-oem-pull-exchange')

  // supplier diagnostics: the registered twin with one descriptor for revision 1
  await expectRevisionMode(tier1, `${URLS.tier1}/approvals/${pull.received.id}`, 'PULL', 'Pull')
  await tier1.getByRole('tab', { name: 'Datenaustausch' }).click()
  const twin = tier1.getByRole('region', { name: 'Digital Twin' })
  await expect(twin.getByText(`urn:uuid:${pull.received.exchangeId}`)).toBeVisible()
  await twin.getByRole('button', { name: 'Technische Details anzeigen' }).click()
  await expect(twin.getByText('Revision 1')).toBeVisible()
  await expect(twin.getByText('urn:samm:com.msg.ppap:0.1.0#PpapSubmission').first()).toBeVisible()
  await shot(tier1, 'p5-tier1-twin')

  // --- back to the default (the UI shows the recommendation) -------------------------------------
  await switchMode(masterdata, relation.id, 'PUSH_NOTIFY_PULL_PAYLOAD', /^Push-Benachrichtigung, Pull-Nutzdaten/)
})
