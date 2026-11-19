import { expect, type Page } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

export const URLS = {
  oem: process.env.E2E_OEM_URL ?? 'http://localhost:8081',
  tier1: process.env.E2E_TIER1_URL ?? 'http://localhost:8082',
  tier2: process.env.E2E_TIER2_URL ?? 'http://localhost:8083',
} as const

export const PASSWORD = process.env.E2E_PASSWORD ?? 'demo'
export const SEED = path.resolve(import.meta.dirname, '../../../seed')
const SHOTS = process.env.E2E_SCREENSHOT_DIR ?? path.resolve(import.meta.dirname, '../../test-results/live-screens')

/** Login landing page → Keycloak form → back in the app (dashboard heading). */
export async function login(page: Page, participant: keyof typeof URLS, username: string, target = '/') {
  await page.goto(URLS[participant] + target)
  await page.getByRole('button', { name: /^(Anmelden|Sign in)/ }).click()
  await page.locator('#username').fill(username)
  await page.locator('#password').fill(PASSWORD)
  await page.locator('#kc-login, [type=submit]').first().click()
  await expect(page.getByRole('navigation').first()).toBeVisible()
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
}

export async function shot(page: Page, name: string) {
  fs.mkdirSync(SHOTS, { recursive: true })
  // the SSE stream keeps a request open – networkidle is only a best effort
  await page.waitForLoadState('networkidle', { timeout: 3_000 }).catch(() => undefined)
  await page.screenshot({ path: path.join(SHOTS, `${name}.png`), fullPage: true })
}

/** Local date + n days (YYYY-MM-DD) for date inputs. */
export function inDays(n: number): string {
  const d = new Date()
  d.setDate(d.getDate() + n)
  const pad = (x: number) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** Calls the participant API with the signed-in user's token (for setup/assertions only). */
export async function api<T>(page: Page, url: string, init: RequestInit = {}): Promise<T> {
  return page.evaluate(
    async ({ url, init }) => {
      const key = Object.keys(sessionStorage).find((k) => k.startsWith('oidc.user:'))!
      const token = JSON.parse(sessionStorage.getItem(key)!).access_token as string
      const res = await fetch(url, { ...init, headers: { ...(init.headers ?? {}), Authorization: `Bearer ${token}` } })
      const text = await res.text()
      if (!res.ok) throw new Error(`${res.status} ${url}: ${text}`)
      return (text ? JSON.parse(text) : undefined) as T
    },
    { url, init },
  )
}

export interface Draft {
  completeness: { complete: boolean; issues: { elementCode: string; code: string }[] }
}

export const draftOf = (page: Page, id: string) => api<Draft>(page, `/api/v1/approval-cases/${id}/submissions/draft`)

/** Fills every required value input of the open element editor (generic over the schema form). */
export async function fillValues(page: Page) {
  const form = page.getByTestId('schema-form')
  if (!(await form.count())) return
  const add = form.getByRole('button', { name: 'Zeile hinzufügen' })
  if ((await add.count()) && (await form.getByRole('textbox').count()) === 0) await add.click()
  for (const input of await form.locator('input[aria-required="true"]').all()) {
    if ((await input.inputValue()) !== '') continue
    const type = await input.getAttribute('type')
    await input.fill(type === 'date' ? inDays(-3) : '12345')
    await input.blur()
  }
  // required/optional yes-no fields without answer
  for (const group of await form.getByRole('radiogroup').all()) {
    if ((await group.getByRole('radio', { checked: true }).count()) === 0)
      await group.getByRole('radio', { name: 'Ja' }).click()
  }
}

/**
 * Resolves all completeness issues of the draft through the package editor (page must show the workspace):
 * self-assessment i. O., comments, a PDF per document element, schema values.
 */
export async function resolveIssues(page: Page, id: string, afterFirst?: () => Promise<void>) {
  const pdf = path.join(SEED, 'documents/messprotokoll.pdf')
  for (let round = 0; round < 40; round++) {
    const draft = await draftOf(page, id)
    if (draft.completeness.complete) break
    const issue = draft.completeness.issues[0]!
    await page
      .getByRole('navigation', { name: 'Elemente' })
      .getByRole('button', { name: new RegExp(`\\b${issue.elementCode}\\b`) })
      .click()
    const editor = page.getByLabel('Element bearbeiten')
    const assessment = editor.getByRole('radiogroup', { name: 'Selbstbeurteilung' })
    if ((await assessment.getByRole('radio', { checked: true }).count()) === 0)
      await assessment.getByRole('radio', { name: 'i. O.', exact: true }).click()
    if (issue.code === 'COMMENT_REQUIRED') await editor.getByLabel(/^Kommentar/).fill('E2E: Begründung')
    if (issue.code === 'DOCUMENT_REQUIRED') {
      await editor.locator('input[type=file]').setInputFiles(pdf)
      await expect(editor.getByRole('button', { name: /messprotokoll\.pdf anzeigen/ }).first()).toBeVisible()
    }
    if (issue.code === 'VALUES_REQUIRED' || issue.code === 'VALUE_SCHEMA_VIOLATION') await fillValues(page)
    // autosave (debounced) has reached the server once this issue is gone
    await expect
      .poll(async () =>
        (await draftOf(page, id)).completeness.issues.some(
          (i) => i.elementCode === issue.elementCode && i.code === issue.code,
        ),
      )
      .toBe(false)
    if (round === 0) await afterFirst?.()
  }
  await expect.poll(async () => (await draftOf(page, id)).completeness.complete).toBe(true)
}

/** Submits the complete draft from the package workspace (confirmation dialog). */
export async function submitPackage(page: Page) {
  await page.getByRole('button', { name: 'Einreichen' }).click()
  const submit = page.getByRole('dialog')
  await submit.getByRole('checkbox').click()
  await submit.getByRole('button', { name: 'Verbindlich einreichen' }).click()
  await expect(page.getByRole('heading', { level: 1, name: /Revision \d+ eingereicht/ })).toBeVisible()
}

export interface CaseRow {
  id: string
  caseNumber: string
  exchangeId: string
  status: string
  ownRole: string
  source: string
  allowedActions: string[]
  version: number
  clarification?: { reason: string; partnerPartNumber?: string } | null
}

/** Polls the participant's case list until the counterpart case (same exchangeId) exists. */
export async function waitForCase(page: Page, exchangeId: string, timeout = 90_000): Promise<CaseRow> {
  let found: CaseRow | undefined
  await expect
    .poll(
      async () => {
        const list = await api<{ content: CaseRow[] }>(page, '/api/v1/approval-cases?size=200&sort=createdAt,desc')
        found = list.content.find((c) => c.exchangeId === exchangeId)
        return Boolean(found)
      },
      { timeout, intervals: [1000, 2000] },
    )
    .toBe(true)
  return found!
}

/** Polls a case until it reaches the status (exchange is asynchronous). */
export async function waitForStatus(page: Page, id: string, status: string, timeout = 90_000) {
  await expect
    .poll(async () => (await api<CaseRow>(page, `/api/v1/approval-cases/${id}`)).status, {
      timeout,
      intervals: [1000, 2000],
    })
    .toBe(status)
}
