import { describe, expect, it } from 'vitest'

import { hashString, normaliseRoleLabel, participantAccent } from './participant'

describe('normaliseRoleLabel', () => {
  it.each([
    ['OEM', 'oem'],
    [' oem ', 'oem'],
    ['Tier-1', 'tier-1'],
    ['TIER 2', 'tier-2'],
    ['tier3', 'tier-3'],
    ['Tier-1 Supplier', 'tier-1'],
    ['Distributor', 'distributor'],
    [undefined, ''],
  ])('%s → %s', (input, expected) => {
    expect(normaliseRoleLabel(input)).toBe(expected)
  })
})

describe('participantAccent', () => {
  it('maps the known roles to fixed, distinct hues', () => {
    const oem = participantAccent('OEM', 'BPNL000000000OEM')
    const t1 = participantAccent('Tier-1', 'BPNL0000000TIER1')
    const t2 = participantAccent('Tier-2', 'BPNL0000000TIER2')
    expect(oem).toEqual({ key: 'oem', hue: 277 })
    expect(t1).toEqual({ key: 'tier-1', hue: 185 })
    expect(t2).toEqual({ key: 'tier-2', hue: 70 })
  })

  it('ignores the BPNL for known roles', () => {
    expect(participantAccent('OEM', 'A').hue).toBe(participantAccent('oem', 'B').hue)
  })

  it('falls back to a deterministic BPNL hash for unknown roles', () => {
    const a = participantAccent('Logistics', 'BPNL00000000LOG1')
    const b = participantAccent('Logistics', 'BPNL00000000LOG1')
    const c = participantAccent('Logistics', 'BPNL00000000LOG2')
    expect(a.key).toBe('custom')
    expect(a).toEqual(b)
    expect(a.hue).toBe(hashString('BPNL00000000LOG1') % 360)
    expect(a.hue).not.toBe(c.hue)
    expect(a.hue).toBeGreaterThanOrEqual(0)
    expect(a.hue).toBeLessThan(360)
  })

  it('handles missing input', () => {
    const accent = participantAccent(undefined, undefined)
    expect(accent.key).toBe('custom')
    expect(Number.isInteger(accent.hue)).toBe(true)
  })
})
