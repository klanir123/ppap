import { describe, expect, it } from 'vitest'

import { userDisplayName } from './current-user'

describe('userDisplayName', () => {
  const base = { userId: 'u-1', roles: [], tenant: { bpnl: 'B', name: 'N', roleLabel: 'OEM' } }
  it('prefers displayName, then username, email, userId', () => {
    expect(userDisplayName({ ...base, displayName: 'Erika', username: 'e' })).toBe('Erika')
    expect(userDisplayName({ ...base, displayName: ' ', username: 'erika' })).toBe('erika')
    expect(userDisplayName({ ...base, email: 'e@x.de' })).toBe('e@x.de')
    expect(userDisplayName(base)).toBe('u-1')
  })
})
