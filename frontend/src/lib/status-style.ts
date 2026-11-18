import type { CSSProperties } from 'react'

/** CSS token suffix: UP → up, ON_REQUEST → on-request, true → active. */
export function statusToken(kind: string, status: string | boolean): string {
  if (kind === 'active') return status ? 'active' : 'inactive'
  return String(status).toLowerCase().replace(/_/g, '-')
}

/** Inline style that exposes the status colours as `--sb-*` variables. */
export function statusStyle(token: string): CSSProperties {
  return {
    '--sb-bg': `var(--status-${token}-bg)`,
    '--sb-fg': `var(--status-${token}-fg)`,
    '--sb-border': `var(--status-${token}-border)`,
    '--sb-solid': `var(--status-${token}-solid)`,
  } as CSSProperties
}
