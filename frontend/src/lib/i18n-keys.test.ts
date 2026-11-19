import { describe, expect, it } from 'vitest'

import { resources } from './i18n'

/** Flattens nested keys; plural suffixes (_one/_other …) are normalised to the base key. */
function keys(value: unknown, prefix = ''): string[] {
  if (typeof value !== 'object' || value === null) return [prefix]
  return Object.entries(value).flatMap(([k, v]) => keys(v, prefix ? `${prefix}.${k}` : k))
}

describe('i18n resources', () => {
  it('DE and EN have the same namespaces', () => {
    expect(Object.keys(resources.en).sort()).toEqual(Object.keys(resources.de).sort())
  })

  for (const ns of Object.keys(resources.de) as (keyof typeof resources.de)[]) {
    it(`namespace "${ns}" has identical keys in DE and EN`, () => {
      const de = new Set(keys(resources.de[ns]))
      const en = new Set(keys(resources.en[ns]))
      expect(
        [...de].filter((k) => !en.has(k)),
        'missing in EN',
      ).toEqual([])
      expect(
        [...en].filter((k) => !de.has(k)),
        'missing in DE',
      ).toEqual([])
    })
  }

  it('has no empty translations', () => {
    for (const lng of ['de', 'en'] as const) {
      const flat = (obj: unknown, path = ''): [string, unknown][] =>
        typeof obj === 'object' && obj !== null
          ? Object.entries(obj).flatMap(([k, v]) => flat(v, path ? `${path}.${k}` : k))
          : [[path, obj]]
      const empty = Object.entries(resources[lng]).flatMap(([ns, data]) =>
        flat(data)
          .filter(([, v]) => typeof v === 'string' && v.trim() === '')
          .map(([k]) => `${ns}:${k}`),
      )
      expect(empty, lng).toEqual([])
    }
  })
})
