import type { PartnerStats } from '@/features/approvals/api'

const HEALTH_ORDER = { FAILING: 0, DEGRADED: 1, OK: 2 } as const

/** Partners sorted by attention: failing exchange first, then open cases. */
export function sortPartners(partners: readonly PartnerStats[]): PartnerStats[] {
  return [...partners].sort(
    (a, b) =>
      (HEALTH_ORDER[a.exchangeHealth ?? 'OK'] ?? 2) - (HEALTH_ORDER[b.exchangeHealth ?? 'OK'] ?? 2) ||
      b.open - a.open ||
      (a.name ?? a.bpnl).localeCompare(b.name ?? b.bpnl),
  )
}
