import type { UsageMonth } from './api'

/** `YYYY-MM` of a date (local). */
export function monthKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

export function addMonths(month: string, delta: number): string {
  const [y, m] = month.split('-').map(Number)
  return monthKey(new Date(y!, m! - 1 + delta, 1))
}

/** The last `count` months up to and including `to`. */
export function lastMonths(count: number, to: string): { from: string; to: string } {
  return { from: addMonths(to, -(count - 1)), to }
}

/** All months from..to; months without data are filled with zeros (AK-12-2: 12 months shown). */
export function fillMonths(data: readonly UsageMonth[], from: string, to: string): UsageMonth[] {
  const byMonth = new Map(data.map((d) => [d.month, d]))
  const result: UsageMonth[] = []
  for (let m = from; m <= to; m = addMonths(m, 1)) {
    result.push(byMonth.get(m) ?? { month: m, approvalCases: 0, revisions: 0, details: {} })
    if (result.length > 120) break
  }
  return result
}

export function totals(data: readonly UsageMonth[]) {
  return data.reduce(
    (acc, d) => ({ approvalCases: acc.approvalCases + d.approvalCases, revisions: acc.revisions + d.revisions }),
    {
      approvalCases: 0,
      revisions: 0,
    },
  )
}

/** "Nice" axis maximum and ticks (1-2-5 steps, at least 1). */
export function niceScale(max: number, tickCount = 4): { max: number; ticks: number[] } {
  if (max <= 0) return { max: 4, ticks: [0, 1, 2, 3, 4] }
  const raw = max / tickCount
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 5, 10].map((f) => f * magnitude).find((s) => s >= raw) ?? 10 * magnitude
  const niceStep = Math.max(1, step)
  const niceMax = Math.ceil(max / niceStep) * niceStep
  const ticks: number[] = []
  for (let v = 0; v <= niceMax; v += niceStep) ticks.push(v)
  return { max: niceMax, ticks }
}

/** Localised month label (`Nov. 26`). */
export function formatMonth(month: string, lng: string | undefined, style: 'short' | 'long' = 'short'): string {
  const [y, m] = month.split('-').map(Number)
  return new Intl.DateTimeFormat(
    lng,
    style === 'short' ? { month: 'short', year: '2-digit' } : { month: 'long', year: 'numeric' },
  ).format(new Date(y!, m! - 1, 1))
}
