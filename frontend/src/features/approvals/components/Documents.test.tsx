import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { renderAs } from '@/features/exchange/__tests__/render'
import i18n from '@/lib/i18n'

import type { DocumentDto } from '../api/types'
import { DocumentList } from './Documents'
import { transferStatusOf } from './documents'

const SHA = 'sha256:9f2c4e1a7b3d5f60812a4c6e8b0d2f4a6c8e0b2d4f6a8c0e2b4d6f8a0c2e4b6d'

const doc = (overrides: Partial<DocumentDto> = {}): DocumentDto => ({
  id: null,
  fileName: 'messprotokoll.pdf',
  mediaType: 'application/pdf',
  size: 2048,
  sha256: SHA,
  ...overrides,
})

const item = (name = 'messprotokoll.pdf') => screen.getByText(name).closest('li')!

describe('transferStatusOf', () => {
  it('uses the server state, else LOCAL for stored and NOT_AVAILABLE for remote references', () => {
    expect(transferStatusOf({ id: null, transferStatus: 'PENDING' })).toBe('PENDING')
    expect(transferStatusOf({ id: 'd-1' })).toBe('LOCAL')
    expect(transferStatusOf({ id: null })).toBe('NOT_AVAILABLE')
  })
})

describe('<DocumentList> transfer states (pull over EDC)', () => {
  let fetchMock: ReturnType<typeof vi.fn>
  beforeEach(async () => {
    await i18n.changeLanguage('de')
    fetchMock = vi.fn(async () => new Response(null, { status: 202 }))
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('LOCAL: preview and download, no transfer badge', () => {
    renderAs(<DocumentList documents={[doc({ id: 'd-1', transferStatus: 'LOCAL' })]} />, ['CUSTOMER_QUALITY_ENGINEER'])
    expect(screen.getByRole('button', { name: 'messprotokoll.pdf anzeigen' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'messprotokoll.pdf herunterladen' })).toBeInTheDocument()
    expect(item()).toHaveAttribute('data-transfer-status', 'LOCAL')
    expect(screen.queryByText(/Catena-X/)).not.toBeInTheDocument()
  })

  it('PENDING: spinner text, no preview/download', () => {
    renderAs(<DocumentList documents={[doc({ transferStatus: 'PENDING' })]} caseId="c-1" />, [
      'CUSTOMER_QUALITY_ENGINEER',
    ])
    expect(screen.getByRole('button', { name: /Wird über Catena-X abgerufen/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /anzeigen|herunterladen/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Erneut abrufen/ })).not.toBeInTheDocument()
  })

  it('FETCHED: preview/download and "Hash geprüft" when the server verified the hash', () => {
    renderAs(
      <DocumentList documents={[doc({ id: 'd-2', transferStatus: 'FETCHED', hashVerified: true })]} caseId="c-1" />,
      ['CUSTOMER_QUALITY_ENGINEER'],
    )
    const li = item()
    expect(within(li).getByText('Über Catena-X abgerufen')).toBeInTheDocument()
    expect(within(li).getByText('Hash geprüft')).toBeInTheDocument()
    expect(within(li).getByRole('button', { name: 'messprotokoll.pdf anzeigen' })).toBeInTheDocument()
  })

  it('FETCHED without verification shows no hash badge', () => {
    renderAs(<DocumentList documents={[doc({ id: 'd-2', transferStatus: 'FETCHED', hashVerified: false })]} />, [
      'CUSTOMER_QUALITY_ENGINEER',
    ])
    expect(screen.queryByText('Hash geprüft')).not.toBeInTheDocument()
  })

  it('FAILED: error code and "Erneut abrufen" → POST …/documents/{sha256}/fetch', async () => {
    const user = userEvent.setup()
    renderAs(
      <DocumentList
        documents={[doc({ transferStatus: 'FAILED', transferError: 'DOCUMENT_HASH_MISMATCH' })]}
        caseId="c-1"
      />,
      ['CUSTOMER_QUALITY_ENGINEER'],
    )
    expect(screen.getByText('Abruf fehlgeschlagen')).toBeInTheDocument()
    expect(screen.getByText(/passt nicht zum SHA-256/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'messprotokoll.pdf erneut abrufen' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`/api/v1/approval-cases/c-1/documents/${SHA.slice(7)}/fetch`)
    expect(init.method).toBe('POST')
  })

  it('FAILED: no retry without permission or without case', () => {
    const { unmount } = renderAs(<DocumentList documents={[doc({ transferStatus: 'FAILED' })]} caseId="c-1" />, [
      'MASTERDATA_MANAGER',
    ])
    expect(screen.queryByRole('button', { name: /erneut abrufen/ })).not.toBeInTheDocument()
    unmount()
    renderAs(<DocumentList documents={[doc({ transferStatus: 'FAILED' })]} />, ['CUSTOMER_QUALITY_ENGINEER'])
    expect(screen.queryByRole('button', { name: /erneut abrufen/ })).not.toBeInTheDocument()
  })

  it('NOT_AVAILABLE: metadata only', () => {
    renderAs(<DocumentList documents={[doc({ transferStatus: 'NOT_AVAILABLE' })]} caseId="c-1" />, [
      'CUSTOMER_QUALITY_ENGINEER',
    ])
    expect(screen.getByText('Nicht verfügbar')).toBeInTheDocument()
    expect(screen.getByText('application/pdf')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /anzeigen|herunterladen|abrufen/ })).not.toBeInTheDocument()
  })

  it('English labels', async () => {
    await i18n.changeLanguage('en')
    renderAs(<DocumentList documents={[doc({ transferStatus: 'PENDING' })]} />, ['CUSTOMER_QUALITY_ENGINEER'])
    expect(screen.getByText('Retrieving via Catena-X …')).toBeInTheDocument()
  })
})
