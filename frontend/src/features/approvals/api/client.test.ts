import { afterEach, describe, expect, it, vi } from 'vitest'

import { cancelCase, listCases, putDraftElement, uploadDocument } from './client'

function mockFetch(body: unknown, headers: Record<string, string> = {}) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(
    async () =>
      new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'content-type': 'application/json', ...headers },
      }),
  )
}

describe('approval client over the generated API', () => {
  afterEach(() => vi.restoreAllMocks())

  it('chains optimistic locking: If-Match in, new version from ETag out', async () => {
    const spy = mockFetch({ revision: 1, elements: [] }, { ETag: '"8"' })
    const result = await putDraftElement('c-1', 'PPF-11', { selfAssessment: 'OK', values: [{ a: 1 }], comment: '' }, 7)
    const [url, init] = spy.mock.calls[0]!
    expect(url).toBe('/api/v1/approval-cases/c-1/submissions/draft/elements/PPF-11')
    expect(init?.method).toBe('PUT')
    expect(new Headers(init?.headers).get('If-Match')).toBe('"7"')
    expect(JSON.parse(String(init?.body))).toEqual({ selfAssessment: 'OK', values: [{ a: 1 }], comment: '' })
    expect(result.version).toBe(8)
    expect(init).not.toHaveProperty('onResponse')
  })

  it('maps list filters to query params', async () => {
    const spy = mockFetch({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 })
    await listCases({
      status: ['SUBMITTED', 'IN_REVIEW'],
      overdue: true,
      ownRole: 'CUSTOMER',
      page: 0,
      size: 25,
      sort: 'dueDate,asc',
    })
    const url = new URL(String(spy.mock.calls[0]![0]), 'http://x')
    expect(url.pathname).toBe('/api/v1/approval-cases')
    expect(url.searchParams.getAll('status')).toEqual(['SUBMITTED', 'IN_REVIEW'])
    expect(url.searchParams.get('overdue')).toBe('true')
    expect(url.searchParams.get('ownRole')).toBe('CUSTOMER')
    expect(url.searchParams.get('sort')).toBe('dueDate,asc')
  })

  it('cancels with a reason body and uploads documents as multipart', async () => {
    const spy = mockFetch({ id: 'c-1' })
    await cancelCase('c-1', 'Teil entfällt', 3)
    expect(JSON.parse(String(spy.mock.calls[0]![1]?.body))).toEqual({ reason: 'Teil entfällt' })
    await uploadDocument(new File(['%PDF'], 'a.pdf', { type: 'application/pdf' }))
    expect(spy.mock.calls[1]![0]).toBe('/api/v1/documents')
    expect((spy.mock.calls[1]![1]?.body as FormData).get('file')).toBeInstanceOf(File)
  })
})
