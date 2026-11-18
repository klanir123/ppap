import { afterEach, describe, expect, it, vi } from 'vitest'

import { deletePart, updatePart } from '@/api/generated/endpoints/parts/parts'

import { importApi } from '../import/api'
import { ifMatch } from './resource'

function mockFetch(body: unknown, contentType = 'application/json') {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(
    async () =>
      new Response(typeof body === 'string' ? body : JSON.stringify(body), {
        status: 200,
        headers: { 'content-type': contentType },
      }),
  )
}

describe('generated client wiring', () => {
  afterEach(() => vi.restoreAllMocks())

  it('sends If-Match with the quoted version on update and delete', async () => {
    const spy = mockFetch({ id: '1' })
    await updatePart('1', { partNumber: 'P-1', nameDe: 'Teil' }, ifMatch(3))
    let [url, init] = spy.mock.calls[0]!
    expect(url).toBe('/api/v1/parts/1')
    expect(new Headers(init?.headers).get('If-Match')).toBe('"3"')
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json')

    spy.mockImplementation(async () => new Response(null, { status: 204 }))
    await deletePart('1', ifMatch(4))
    ;[url, init] = spy.mock.calls[1]!
    expect(init?.method).toBe('DELETE')
    expect(new Headers(init?.headers).get('If-Match')).toBe('"4"')
  })

  it('posts import files as multipart with the mapping as JSON text part', async () => {
    const spy = mockFetch({ entity: 'parts', rows: [] })
    const file = new File(['a;b'], 'teile.csv', { type: 'text/csv' })
    await importApi('parts').preview(file, { Sachnummer: 'partNumber', Notiz: null })
    const [url, init] = spy.mock.calls[0]!
    expect(url).toBe('/api/v1/imports/parts/preview')
    const body = init?.body as FormData
    expect(body.get('file')).toBeInstanceOf(File)
    expect(body.get('mapping')).toBe('{"Sachnummer":"partNumber","Notiz":null}')

    await importApi('part-mappings').execute(file, {}, true)
    expect(spy.mock.calls[1]![0]).toBe('/api/v1/imports/part-mappings/execute?skipErrors=true')
  })

  it('returns the CSV error report as Blob', async () => {
    mockFetch('row;message\n3;invalid', 'text/csv')
    const blob = await importApi('parts').errorReport(new File(['x'], 'a.csv'), {})
    // jsdom and Node use different Blob classes – check the shape
    expect(blob.type).toBe('text/csv')
    expect(await blob.text()).toContain('3;invalid')
  })
})

describe('reactivation request mapping', () => {
  it('sends only request fields, keeping the level matrix', async () => {
    const { levelRequest, partnerRequest } = await import('./requests')
    const level = levelRequest({
      id: 'l1',
      version: 2,
      active: false,
      code: 'VDA-3',
      nameDe: 'Stufe 3',
      nameEn: 'Level 3',
      scheme: 'VDA',
      sortOrder: 30,
      requirements: { 'PPF-01': 'REQUIRED' },
    })
    expect(level).not.toHaveProperty('id')
    expect(level.requirements).toEqual({ 'PPF-01': 'REQUIRED' })
    const partner = partnerRequest({
      id: 'p1',
      version: 1,
      active: false,
      bpnl: 'BPNL00000003AYRE',
      name: 'OEM',
      roles: ['CUSTOMER'],
      exchangeReady: false,
    })
    expect(partner).not.toHaveProperty('exchangeReady')
  })
})
