/**
 * Deterministic participant accent colour.
 *
 * Every participant (OEM, Tier-1, Tier-2 …) runs its own instance of the same build.
 * A subtle accent colour derived from the role label (fallback: BPNL hash) makes
 * several instances opened side by side in a browser easy to tell apart.
 * Only the hue is chosen here; lightness/chroma per theme live in index.css.
 */
export interface ParticipantAccent {
  /** Stable key, e.g. "oem", "tier-1" or "custom". */
  key: string
  /** OKLCH hue in degrees (0–359). */
  hue: number
}

const KNOWN_ACCENTS: Record<string, number> = {
  oem: 277, // indigo
  'tier-1': 185, // teal
  'tier-2': 70, // amber
  'tier-3': 340, // rose
  'tier-4': 145, // green
  'tier-5': 305, // violet
}

/** Normalises "Tier 1", "TIER-1", "tier1", "Tier-1 Supplier" → "tier-1". */
export function normaliseRoleLabel(roleLabel: string | undefined | null): string {
  const value = (roleLabel ?? '').trim().toLowerCase()
  const tier = /tier\s*-?\s*(\d)/.exec(value)
  if (tier) return `tier-${tier[1]}`
  if (/\boem\b/.test(value)) return 'oem'
  return value
}

/** Small, stable string hash (FNV-1a, 32 bit). */
export function hashString(input: string): number {
  let hash = 0x811c9dc5
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i)
    hash = Math.imul(hash, 0x01000193)
  }
  return hash >>> 0
}

export function participantAccent(
  roleLabel: string | undefined | null,
  bpnl: string | undefined | null,
): ParticipantAccent {
  const key = normaliseRoleLabel(roleLabel)
  const known = KNOWN_ACCENTS[key]
  if (known !== undefined) return { key, hue: known }
  const seed = (bpnl ?? '').trim() || key || 'participant'
  return { key: 'custom', hue: hashString(seed) % 360 }
}
