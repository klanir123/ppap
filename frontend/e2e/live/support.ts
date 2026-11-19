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
  await page.waitForLoadState('networkidle').catch(() => undefined)
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
