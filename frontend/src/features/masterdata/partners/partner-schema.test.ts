import { describe, expect, it } from 'vitest'

import { createPartnerSchema } from './partner-schema'

const schema = createPartnerSchema('BPNL0000000TIER1')
const valid = {
  bpnl: 'BPNL00000003AYRE',
  name: 'Demo-OEM AG',
  roles: ['CUSTOMER'],
  dspEndpoint: '',
  did: '',
  contactEmail: '',
}

function codes(input: unknown) {
  const result = schema.safeParse(input)
  return result.success ? {} : Object.fromEntries(result.error.issues.map((i) => [i.path.join('.'), i.message]))
}

describe('partner schema (contract validation rules)', () => {
  it('accepts a valid partner and turns empty optionals into undefined', () => {
    const parsed = schema.parse(valid)
    expect(parsed.dspEndpoint).toBeUndefined()
    expect(parsed.did).toBeUndefined()
  })

  it('rejects malformed BPNL, own tenant, missing role, bad URL/DID/email with error codes', () => {
    expect(codes({ ...valid, bpnl: 'BPNL123' })).toEqual({ bpnl: 'INVALID_BPNL' })
    expect(codes({ ...valid, bpnl: 'BPNL0000000TIER1' })).toEqual({ bpnl: 'SELF_REFERENCE' })
    expect(codes({ ...valid, roles: [] })).toEqual({ roles: 'MIN_ONE' })
    expect(codes({ ...valid, dspEndpoint: 'ftp://x' })).toEqual({ dspEndpoint: 'INVALID_URL' })
    expect(codes({ ...valid, did: 'web:x' })).toEqual({ did: 'INVALID_DID' })
    expect(codes({ ...valid, contactEmail: 'nope' })).toEqual({ contactEmail: 'INVALID_EMAIL' })
  })
})
