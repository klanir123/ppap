import { zodResolver } from '@hookform/resolvers/zod'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FormProvider, useForm } from 'react-hook-form'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { renderWithProviders } from '@/test/render'

import { normalizePreview } from '../api/policy-profiles'
import { PolicyPreviewPanel } from './PolicyPreviewPanel'
import {
  policyProfileDefaults,
  policyProfileSchema,
  toPolicyProfileInput,
  usagePurposeError,
  type PolicyProfileFormValues,
} from './policy-profile-schema'
import { UsagePurposesField } from './UsagePurposesField'

const valid: PolicyProfileFormValues = {
  ...policyProfileDefaults(),
  code: 'strict',
  name: 'Streng',
  usagePurposes: ['cx.quality.base:1'],
}

const codes = (values: PolicyProfileFormValues) => {
  const r = policyProfileSchema.safeParse(values)
  return r.success ? {} : Object.fromEntries([...r.error.issues].reverse().map((i) => [String(i.path[0]), i.message]))
}

afterEach(() => vi.unstubAllGlobals())

describe('policy profile validation', () => {
  it('accepts a valid profile and maps it to the API shape (constraints kept)', () => {
    expect(codes(valid)).toEqual({})
    const input = toPolicyProfileInput(policyProfileSchema.parse(valid), {
      id: 'p',
      code: 'strict',
      name: 'x',
      access: { type: 'BPN', membershipRequired: false },
      usage: {
        frameworkAgreement: 'x',
        usagePurposes: [],
        additionalConstraints: [{ leftOperand: 'a', operator: 'eq', rightOperand: 'b' }],
      },
      active: true,
      version: 2,
    })
    expect(input).toMatchObject({
      access: { type: 'BPN_GROUP', membershipRequired: true },
      usage: {
        frameworkAgreement: 'DataExchangeGovernance:1.0',
        usagePurposes: ['cx.quality.base:1'],
        additionalConstraints: [{ leftOperand: 'a' }],
      },
    })
  })

  it('reports usage purpose format, at least one purpose, framework agreement and code', () => {
    expect(codes({ ...valid, usagePurposes: [] })).toMatchObject({ usagePurposes: 'MIN_ONE_USAGE_PURPOSE' })
    expect(codes({ ...valid, usagePurposes: ['quality'] })).toMatchObject({ usagePurposes: 'INVALID_USAGE_PURPOSE' })
    expect(codes({ ...valid, frameworkAgreement: '' })).toMatchObject({
      frameworkAgreement: 'FRAMEWORK_AGREEMENT_REQUIRED',
    })
    expect(codes({ ...valid, frameworkAgreement: 'Data Exchange' })).toMatchObject({
      frameworkAgreement: 'INVALID_FRAMEWORK_AGREEMENT',
    })
    expect(codes({ ...valid, code: 'Strict Profile' })).toMatchObject({ code: 'INVALID_PROFILE_CODE' })
    expect(usagePurposeError('cx.quality.base:1', ['cx.quality.base:1'])).toBe('DUPLICATE_USAGE_PURPOSE')
    expect(usagePurposeError('cx.core.industrycore:1')).toBeUndefined()
  })
})

function ChipsForm({ onValues }: { onValues: (v: string[]) => void }) {
  const form = useForm<PolicyProfileFormValues>({
    resolver: zodResolver(policyProfileSchema) as never,
    defaultValues: valid,
  })
  return (
    <FormProvider {...form}>
      <UsagePurposesField name="usagePurposes" label="Nutzungszwecke" />
      <button type="button" onClick={() => onValues(form.getValues('usagePurposes'))}>
        read
      </button>
    </FormProvider>
  )
}

describe('<UsagePurposesField>', () => {
  it('adds valid purposes as chips, rejects invalid ones and removes chips', async () => {
    const onValues = vi.fn()
    renderWithProviders(<ChipsForm onValues={onValues} />)
    const input = screen.getByPlaceholderText('cx.quality.base:1')
    await userEvent.type(input, 'quality{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent('Ungültiger Nutzungszweck')
    await userEvent.clear(input)
    await userEvent.type(input, 'cx.core.industrycore:1{Enter}')
    await userEvent.click(screen.getByRole('button', { name: 'cx.quality.base:1 entfernen' }))
    await userEvent.click(screen.getByRole('button', { name: 'read' }))
    expect(onValues).toHaveBeenCalledWith(['cx.core.industrycore:1'])
  })
})

describe('ODRL preview', () => {
  const preview = {
    profileId: 'p-1',
    code: 'default',
    version: 3,
    relations: ['BPNL00000000OEM1/CUSTOMER'],
    policies: [
      {
        role: 'USAGE',
        policyId: 'ppap-usage-default-8d8fc92c',
        policy: {
          policy: {
            permission: [
              {
                action: 'use',
                constraint: {
                  leftOperand: 'FrameworkAgreement',
                  operator: 'eq',
                  rightOperand: 'DataExchangeGovernance:1.0',
                },
              },
            ],
          },
        },
      },
    ],
  }

  it('normalises the server preview (role → kind, policyId → id)', () => {
    expect(normalizePreview(preview)).toMatchObject({
      relations: ['BPNL00000000OEM1/CUSTOMER'],
      policies: [{ kind: 'USAGE', id: 'ppap-usage-default-8d8fc92c' }],
    })
  })

  it('renders kind, policy id, constraints and the JSON', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify(preview), { headers: { 'Content-Type': 'application/json' } })),
    )
    renderWithProviders(<PolicyPreviewPanel profileId="p-1" version={3} />)
    await waitFor(() => expect(screen.getByText('Nutzungs-Policy')).toBeInTheDocument())
    const panel = screen.getByTestId('policy-preview')
    expect(panel).toHaveTextContent('ppap-usage-default-8d8fc92c')
    expect(panel).toHaveTextContent('Verwendet von: BPNL00000000OEM1/CUSTOMER')
    expect(screen.getByLabelText('Nutzungs-Policy')).toHaveTextContent('DataExchangeGovernance:1.0')
  })
})
