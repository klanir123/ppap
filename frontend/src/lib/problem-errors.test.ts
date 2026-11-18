import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/api/problem'
import { fieldErrorText } from '@/components/forms/server-errors'
import i18n from '@/lib/i18n'

import { mapProblemToFields, rootField, translateErrorCode, translateFieldError } from './problem-errors'

const fields = ['bpnl', 'name', 'roles', 'dspEndpoint']

describe('mapProblemToFields', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('de')
  })

  it('maps errors[] onto form fields by exact name and root segment', () => {
    const error = new ApiError(
      400,
      {
        code: 'VALIDATION_FAILED',
        errors: [
          { field: 'bpnl', code: 'INVALID_FORMAT', message: 'must match ^BPNL…' },
          { field: 'roles[0]', code: 'INVALID_VALUE', message: 'unknown role' },
          { field: 'tenantId', code: 'REQUIRED', message: 'not a form field' },
        ],
      },
      'corr-1',
    )
    const mapped = mapProblemToFields(error, fields)
    expect(mapped.fieldErrors).toEqual([
      { field: 'bpnl', code: 'INVALID_FORMAT', message: 'must match ^BPNL…' },
      { field: 'roles', code: 'INVALID_VALUE', message: 'unknown role' },
    ])
    expect(mapped.unmapped).toEqual([{ field: 'tenantId', code: 'REQUIRED', message: 'not a form field' }])
    expect(mapped.formCode).toBe('VALIDATION_FAILED')
  })

  it('treats problems without field errors as form-level errors', () => {
    const mapped = mapProblemToFields(
      new ApiError(409, { code: 'DUPLICATE_KEY', detail: 'bpnl exists' }, undefined),
      fields,
    )
    expect(mapped.fieldErrors).toHaveLength(0)
    expect(mapped.formCode).toBe('DUPLICATE_KEY')
    expect(mapped.formMessage).toBe('bpnl exists')
  })

  it('detects version conflicts (code or HTTP 412)', () => {
    expect(mapProblemToFields(new ApiError(412, undefined, undefined), fields).isVersionConflict).toBe(true)
    expect(mapProblemToFields(new ApiError(409, { code: 'VERSION_CONFLICT' }, undefined), fields).formCode).toBe(
      'VERSION_CONFLICT',
    )
  })

  it('handles non-API errors', () => {
    expect(mapProblemToFields(new Error('network'), fields).formCode).toBe('UNEXPECTED')
  })

  it('extracts the root field', () => {
    expect(rootField('roles[1]')).toBe('roles')
    expect(rootField('address.city')).toBe('address')
  })
})

describe('error code translation', () => {
  it('translates known codes in DE and EN, falls back to the server message', async () => {
    await i18n.changeLanguage('de')
    expect(translateErrorCode('SELF_REFERENCE')).toMatch(/eigene Mandant/)
    expect(translateErrorCode('SOMETHING_NEW', 'server says no')).toBe('server says no')
    await i18n.changeLanguage('en')
    expect(translateErrorCode('DUPLICATE_KEY')).toBe('A record with this key already exists.')
    await i18n.changeLanguage('de')
  })

  it('shows the specific rule for generic INVALID_FORMAT on known fields', () => {
    expect(translateFieldError('INVALID_FORMAT', 'partnerBpnl')).toMatch(/Ungültige BPNL/)
    expect(translateFieldError('INVALID_FORMAT', 'ownSiteBpns')).toMatch(/Ungültige BPNS/)
    expect(translateFieldError('INVALID_FORMAT', 'name')).toBe('Das Format ist ungültig.')
    expect(translateFieldError('MALFORMED_REQUEST', undefined)).toMatch(/fehlerhaft/)
    expect(translateFieldError('UNSUPPORTED_FILE_TYPE', undefined)).toMatch(/CSV und XLSX/)
  })

  it('renders server and client field errors', () => {
    expect(fieldErrorText({ type: 'server:INVALID_BPNL', message: 'bad' })).toMatch(/Ungültige BPNL/)
    expect(fieldErrorText({ type: 'too_small', message: 'REQUIRED' })).toBe('Pflichtfeld')
    expect(fieldErrorText(undefined)).toBeUndefined()
  })
})

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
