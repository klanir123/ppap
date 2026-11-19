import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { approvalCase } from '@/features/approvals/__tests__/fixtures'
import type { ApprovalCase } from '@/features/approvals/api/types'
import i18n from '@/lib/i18n'

import { renderAs } from '../__tests__/render'
import { ClarificationBanner } from './ClarificationBanner'

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const parts = {
  content: [
    { id: 'p-1', partNumber: 'T1-310000-01', nameDe: 'Halter vorne links', active: true, version: 1 },
    { id: 'p-2', partNumber: 'T1-310001-01', nameDe: 'Halter vorne rechts', active: true, version: 1 },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
}

const received = {
  ...approvalCase({ ownRole: 'SUPPLIER', source: 'EXCHANGE', status: 'REQUESTED', version: 4 }),
  clarification: { reason: 'PART_NOT_MAPPED', partnerPartNumber: 'D10-9999-77' },
} as ApprovalCase

describe('clarification (UC-04 A1)', () => {
  let fetchMock: ReturnType<typeof vi.fn>
  beforeEach(async () => {
    await i18n.changeLanguage('de')
    fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.startsWith('/api/v1/parts')) return json(parts)
      if (url.endsWith('/assign-part') && init?.method === 'POST') return json({ ...received, clarification: null })
      return json({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('shows nothing without clarification', () => {
    const { container } = renderAs(<ClarificationBanner approvalCase={approvalCase()} />, ['SUPPLIER_QUALITY_MANAGER'])
    expect(container).toBeEmptyDOMElement()
  })

  it('read-only users see the warning without the action', () => {
    renderAs(<ClarificationBanner approvalCase={received} />, ['CONNECTOR_ADMIN'])
    expect(screen.getByRole('alert')).toHaveTextContent('Teil nicht zugeordnet: Kundensachnummer D10-9999-77')
    expect(screen.queryByRole('button', { name: 'Teil zuordnen' })).not.toBeInTheDocument()
  })

  it('assigns a part and saves the mapping by default (If-Match with the case version)', async () => {
    const user = userEvent.setup()
    renderAs(<ClarificationBanner approvalCase={received} />, ['SUPPLIER_QUALITY_MANAGER'])
    await user.click(screen.getByRole('button', { name: 'Teil zuordnen' }))
    const dialog = await screen.findByRole('dialog', { name: 'Teil zuordnen' })
    expect(dialog).toHaveTextContent('D10-9999-77')
    expect(screen.getByRole('checkbox', { name: 'Zuordnung als Teilezuordnung speichern' })).toBeChecked()

    // submitting without a selection asks for one
    await user.click(screen.getByRole('button', { name: 'Zuordnen' }))
    expect(screen.getByText('Bitte ein Teil auswählen.')).toBeInTheDocument()

    await user.click(await screen.findByRole('radio', { name: /T1-310001-01/ }))
    await user.click(screen.getByRole('button', { name: 'Zuordnen' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/assign-part'))!
    const init = call[1] as RequestInit
    expect(JSON.parse(String(init.body))).toEqual({ partId: 'p-2', saveMapping: true })
    expect(new Headers(init.headers).get('If-Match')).toBe('"4"')
  })

  it('can assign without saving the mapping', async () => {
    const user = userEvent.setup()
    renderAs(<ClarificationBanner approvalCase={received} />, ['SUPPLIER_QUALITY_MANAGER'])
    await user.click(screen.getByRole('button', { name: 'Teil zuordnen' }))
    await user.click(await screen.findByRole('radio', { name: /T1-310000-01/ }))
    await user.click(screen.getByRole('checkbox', { name: 'Zuordnung als Teilezuordnung speichern' }))
    await user.click(screen.getByRole('button', { name: 'Zuordnen' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/assign-part'))).toBe(true))
    const init = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/assign-part'))![1] as RequestInit
    expect(JSON.parse(String(init.body))).toEqual({ partId: 'p-1', saveMapping: false })
  })
})
