import { expect, test, type Browser, type Page } from '@playwright/test'

import { api, createOemDraft, login, shot, URLS, type CaseRow } from './support'

/**
 * Phase 6 – connector cockpit, policy profiles, usage and dataspace view (UC-10 … UC-12, E5-06) as OEM connector
 * administrator. The profile change is reverted in afterAll.
 */

interface Profile {
  id: string
  code: string
  name: string
  description?: string
  access: { type: string; membershipRequired: boolean }
  usage: { frameworkAgreement: string; usagePurposes: string[]; additionalConstraints: unknown[] }
  active: boolean
  version: number
}

const EXTRA_PURPOSE = 'cx.core.industrycore:1'
let original: Profile | undefined
let admin: Page

async function participant(browser: Browser, name: keyof typeof URLS, user: string): Promise<Page> {
  const context = await browser.newContext()
  const page = await context.newPage()
  await login(page, name, user)
  return page
}

/** OEM request to Tier-1 sent via API – produces exchange steps (live log, dataspace animation). */
async function sendRequest(page: Page, remarks: string) {
  const draft = await createOemDraft(page, remarks)
  await api<CaseRow>(page, `/api/v1/approval-cases/${draft.id}/send`, {
    method: 'POST',
    headers: { 'If-Match': `"${draft.version}"` },
  })
  return draft
}

test.describe.serial('OEM connector admin', () => {
  test.beforeAll(async ({ browser }) => {
    admin = await participant(browser, 'oem', 'oem.admin')
  })

  test.afterAll(async () => {
    if (!original || !admin) return
    const current = await api<Profile>(admin, `/api/v1/policy-profiles/${original.id}`)
    if (JSON.stringify(current.usage.usagePurposes) === JSON.stringify(original.usage.usagePurposes)) return
    await api(admin, `/api/v1/policy-profiles/${original.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', 'If-Match': `"${current.version}"` },
      body: JSON.stringify({
        code: original.code,
        name: original.name,
        description: original.description,
        access: original.access,
        usage: original.usage,
        active: original.active,
      }),
    })
  })

  test('cockpit: overview, live partner catalog, live log', async () => {
    await admin.goto(`${URLS.oem}/cockpit`)
    await expect(admin.getByRole('heading', { level: 1, name: 'Connector-Cockpit' })).toBeVisible()
    const overview = admin.getByTestId('cockpit-overview')
    await expect(overview).toContainText('EDC-Connector')
    await expect(admin.getByRole('status', { name: 'Status: Verfügbar' }).first()).toBeVisible()
    await shot(admin, 'p6-cockpit-overview')

    // partner catalog of Tier-1, requested live via DSP
    await admin.getByRole('tab', { name: 'Partner-Katalog' }).click()
    await admin.getByTestId('catalog-partner').click()
    await admin.getByRole('option', { name: /Tier1/ }).click()
    await admin.getByRole('button', { name: 'Katalog abrufen' }).click()
    const result = admin.getByTestId('catalog-result')
    await expect(result).toBeVisible({ timeout: 60_000 })
    await expect(result.getByRole('article', { name: 'ppap-notification-receive' })).toBeVisible()
    await expect(admin.getByTestId('catalog-duration')).toContainText(/Antwort in [\d.]+ ms/)
    await expect(result.getByText(/UsagePurpose/).first()).toBeVisible()
    await shot(admin, 'p6-cockpit-catalog')

    // live log shows the steps of a message sent meanwhile
    await admin.getByRole('tab', { name: 'Live-Log' }).click()
    await expect(admin.getByRole('heading', { name: 'Live-Log' })).toBeVisible()
    await sendRequest(admin, 'E2E Phase 6: Live-Log')
    const log = admin.getByTestId('live-log')
    await expect(log.getByRole('listitem').first()).toBeVisible({ timeout: 90_000 })
    await expect(log).toContainText(/Anforderung/)
    await shot(admin, 'p6-cockpit-live-log')
  })

  test('policy profile: edit usage purposes → ODRL preview changes', async () => {
    const profiles = await api<Profile[]>(admin, '/api/v1/policy-profiles')
    original = profiles.find((p) => p.code === 'default')
    expect(original, 'policy profile "default"').toBeTruthy()

    await admin.goto(`${URLS.oem}/policy-profiles`)
    await expect(admin.getByRole('heading', { level: 1, name: 'Policy-Profile' })).toBeVisible()
    await admin.getByRole('link', { name: 'default', exact: true }).click()
    const preview = admin.getByTestId('policy-preview')
    await expect(preview).toContainText('Nutzungs-Policy')
    await expect(preview).not.toContainText(EXTRA_PURPOSE)

    if (!original!.usage.usagePurposes.includes(EXTRA_PURPOSE)) {
      await admin.getByPlaceholder('cx.quality.base:1').fill(EXTRA_PURPOSE)
      await admin.getByRole('button', { name: 'Hinzufügen' }).click()
    }
    await expect(admin.getByTestId('usage-purposes')).toContainText(EXTRA_PURPOSE)
    await admin.getByRole('button', { name: 'Speichern' }).click()
    await expect(admin.getByText(/Profil .* gespeichert/).first()).toBeVisible()
    await expect(preview).toContainText(EXTRA_PURPOSE, { timeout: 20_000 })
    await shot(admin, 'p6-policy-profile-preview')
  })

  test('usage: counters of the earlier specs', async () => {
    await admin.goto(`${URLS.oem}/usage`)
    await expect(admin.getByRole('heading', { level: 1, name: 'Nutzung' })).toBeVisible()
    const table = admin.getByTestId('usage-table')
    await expect(table).toBeVisible()
    const usage = await api<{ approvalCases: number }[]>(admin, '/api/v1/usage')
    expect(usage.reduce((n, m) => n + m.approvalCases, 0)).toBeGreaterThan(0)
    await expect(admin.getByTestId('usage-chart')).toBeVisible()
    await shot(admin, 'p6-usage')
  })

  test('dataspace view: partner nodes, animation on a new message', async () => {
    await admin.goto(`${URLS.oem}/dataspace`)
    await expect(admin.getByRole('heading', { level: 1, name: 'Datenraum' })).toBeVisible()
    await expect(admin.getByTestId('dataspace-caption')).toContainText(
      'jeder Teilnehmer sieht nur seinen eigenen Datenaustausch',
    )
    await expect(admin.getByTestId('dataspace-self')).toBeVisible()
    await expect(admin.locator('[data-testid="dataspace-partner"]').first()).toBeVisible()
    expect(await admin.getByTestId('dataspace-partner').count()).toBeGreaterThan(0)
    await sendRequest(admin, 'E2E Phase 6: Datenraum')
    await expect(admin.getByTestId('dataspace-comet').first()).toBeVisible({ timeout: 90_000 })
    await shot(admin, 'p6-dataspace-animation')

    await admin.goto(`${URLS.oem}/dataspace?present=1`)
    await expect(admin.getByTestId('dataspace-present')).toBeVisible()
    await shot(admin, 'p6-dataspace-present')
  })
})
