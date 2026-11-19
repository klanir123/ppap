import { expect, test, type Browser, type Page } from '@playwright/test'

import { api, inDays, login, shot, URLS, waitForCase, waitForStatus, type CaseRow } from './support'

/**
 * UC-04 A1 – a request for a part the supplier has not mapped lands in the clarification list:
 * OEM creates a new part + mapping (only on the OEM side) and requests it → Tier-1 cannot resolve the
 * customer part number → banner "Teil nicht zugeordnet" → assign own part, save mapping → case continues.
 */

const json = { 'Content-Type': 'application/json' }

interface Relation {
  id: string
  partnerBpnl: string
  partnerName?: string
  partnerRole: string
}

async function participant(browser: Browser, name: keyof typeof URLS, user: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage()
  await login(page, name, user)
  return page
}

test('Tier-1 resolves a request for an unmapped part (clarification list)', async ({ browser }) => {
  test.setTimeout(300_000)
  const stamp = Date.now().toString(36).toUpperCase()
  const oemPartNumber = `E2E-CL-${stamp}`
  const tier1PartNumber = `T1-E2E-${stamp}`

  // --- OEM master data: new part + mapping to Tier-1 (Tier-1 has no mapping for it) -------------
  const oemData = await participant(browser, 'oem', 'oem.masterdata')
  const part = await api<{ id: string }>(oemData, '/api/v1/parts', {
    method: 'POST',
    headers: json,
    body: JSON.stringify({ partNumber: oemPartNumber, nameDe: 'E2E Klärungsteil', nameEn: 'E2E clarification part' }),
  })
  const relations = await api<{ content: Relation[] }>(oemData, '/api/v1/relations?partnerRole=SUPPLIER&size=200')
  const relation = relations.content.find((r) => /Tier1/i.test(r.partnerName ?? ''))!
  expect(relation, 'OEM → Demo-Tier1 relation').toBeTruthy()
  await api(oemData, `/api/v1/relations/${relation.id}/part-mappings`, {
    method: 'POST',
    headers: json,
    body: JSON.stringify({ partId: part.id, partnerPartNumber: tier1PartNumber }),
  })
  await oemData.context().close()

  // --- Tier-1 master data: an own part without any mapping yet (one mapping per part and relation) --
  const tier1Data = await participant(browser, 'tier1', 'tier1.masterdata')
  await api(tier1Data, '/api/v1/parts', {
    method: 'POST',
    headers: json,
    body: JSON.stringify({ partNumber: tier1PartNumber, nameDe: 'E2E Klärungsteil Lieferant' }),
  })
  await tier1Data.context().close()

  // --- OEM quality: request the part ------------------------------------------------------------
  const oem = await participant(browser, 'oem', 'oem.quality')
  const draft = await api<CaseRow>(oem, '/api/v1/approval-cases', {
    method: 'POST',
    headers: json,
    body: JSON.stringify({
      partId: part.id,
      relationId: relation.id,
      reasonCode: 'NEW_PART',
      levelCode: 'VDA-2',
      dueDate: inDays(21),
      remarks: 'E2E: Teil ohne Zuordnung beim Lieferanten',
      elements: [{ code: 'PPF-01', requirement: 'REQUIRED' }],
    }),
  })
  await api(oem, `/api/v1/approval-cases/${draft.id}/send`, {
    method: 'POST',
    headers: { 'If-Match': `"${draft.version}"` },
  })

  // --- Tier-1: request lands in the clarification list --------------------------------------------
  const tier1 = await participant(browser, 'tier1', 'tier1.quality')
  const received = await waitForCase(tier1, draft.exchangeId)
  expect(received.source).toBe('EXCHANGE')
  expect(received.clarification?.reason).toBe('PART_NOT_MAPPED')

  await tier1.goto(`${URLS.tier1}/approvals?clarification=true`)
  const table = tier1.getByRole('table', { name: 'Freigabevorgänge' })
  const row = table.getByRole('row').filter({ hasText: received.caseNumber })
  await expect(row).toBeVisible()
  await expect(row.getByText('Klärung')).toBeVisible()
  await expect(row.getByText('Teil nicht zugeordnet')).toBeVisible()
  await expect(tier1.getByRole('button', { name: /Klärung erforderlich/ })).toHaveAttribute('aria-pressed', 'true')
  await shot(tier1, 'p4-tier1-clarification-list')

  await row.getByRole('link', { name: received.caseNumber }).click()
  const banner = tier1.getByRole('alert').filter({ hasText: 'Teil nicht zugeordnet' })
  await expect(banner).toBeVisible()
  await expect(banner).toContainText(new RegExp(`${oemPartNumber}|${tier1PartNumber}`))

  // --- assign an own part and save the mapping ------------------------------------------------------
  await banner.getByRole('button', { name: 'Teil zuordnen' }).click()
  const dialog = tier1.getByRole('dialog', { name: 'Teil zuordnen' })
  await expect(dialog.getByRole('checkbox', { name: 'Zuordnung als Teilezuordnung speichern' })).toBeChecked()
  await dialog.getByRole('searchbox').fill(tier1PartNumber)
  const option = dialog.getByRole('radio', { name: new RegExp(tier1PartNumber) })
  await expect(option).toBeVisible()
  const ownPartNumber = tier1PartNumber
  await option.click()
  await shot(tier1, 'p4-tier1-assign-part')
  await dialog.getByRole('button', { name: 'Zuordnen' }).click()
  await expect(dialog).toBeHidden()
  await expect(banner).toBeHidden()
  await expect(tier1.getByText(ownPartNumber).first()).toBeVisible()

  const assigned = await api<CaseRow & { part?: { partNumber: string } }>(
    tier1,
    `/api/v1/approval-cases/${received.id}`,
  )
  expect(assigned.clarification ?? null).toBeNull()
  expect(assigned.part?.partNumber).toBe(ownPartNumber)

  // mapping saved: future requests for this customer part number resolve automatically
  const mappings = await api<{ content: { part: { partNumber: string } }[] }>(
    tier1,
    `/api/v1/part-mappings?partnerRole=CUSTOMER&size=200&q=${encodeURIComponent(oemPartNumber)}`,
  )
  expect(mappings.content.some((m) => m.part.partNumber === ownPartNumber)).toBe(true)

  // --- the case continues ------------------------------------------------------------------------
  await tier1.getByRole('button', { name: 'Bearbeitung starten' }).click()
  await expect(tier1.getByRole('heading', { level: 1, name: 'PPF-Paket erstellen' })).toBeVisible()
  await waitForStatus(oem, draft.id, 'IN_PROGRESS')
})
