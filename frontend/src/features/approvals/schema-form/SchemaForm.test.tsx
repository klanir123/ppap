import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { renderWithProviders } from '@/test/render'

import type { JsonSchema, JsonValue } from '../api/types'
import {
  joinPointer,
  newRow,
  normalizePointer,
  parseNumberInput,
  parsePointer,
  renderablePointers,
  setIn,
  validate,
  type SchemaError,
} from './schema'
import { SchemaForm } from './SchemaForm'
import { SchemaValueView } from './SchemaValueView'

const measurements: JsonSchema = {
  type: 'array',
  minItems: 1,
  items: {
    type: 'object',
    properties: {
      characteristic: { type: 'string', maxLength: 10 },
      measured: { type: 'number', minimum: 0, maximum: 100 },
      sampleSize: { type: 'integer', minimum: 1 },
      ok: { type: 'boolean' },
    },
    required: ['characteristic', 'measured', 'ok'],
  },
}

const sample: JsonSchema = {
  type: 'object',
  properties: {
    quantity: { type: 'integer', minimum: 1 },
    shippedOn: { type: 'string', format: 'date' },
    imdsId: { type: 'string', pattern: '^[0-9]{3,12}$' },
    declared: { type: 'boolean' },
    grade: { type: 'string', enum: ['A', 'B'] },
  },
  required: ['quantity', 'declared'],
}

describe('schema validation', () => {
  it('checks required, types, min/max, length, pattern, date and enum', () => {
    const errors = validate(sample, { quantity: 0, shippedOn: '2026-13-01', imdsId: 'x1', grade: 'C' })
    expect(errors).toEqual(
      expect.arrayContaining([
        { pointer: '/quantity', code: 'MINIMUM', params: { limit: 1 } },
        { pointer: '/shippedOn', code: 'DATE' },
        expect.objectContaining({ pointer: '/imdsId', code: 'PATTERN' }),
        { pointer: '/declared', code: 'REQUIRED' },
        { pointer: '/grade', code: 'ENUM' },
      ]),
    )
    expect(validate(sample, { quantity: 1.5, declared: true })).toEqual([
      { pointer: '/quantity', code: 'TYPE_INTEGER' },
    ])
    expect(validate(sample, { quantity: 3, declared: false, shippedOn: '2026-12-01', imdsId: '12345' })).toEqual([])
  })

  it('validates arrays: minItems, empty rows and per-cell rules', () => {
    expect(validate(measurements, [])).toEqual([{ pointer: '', code: 'MIN_ITEMS', params: { limit: 1 } }])
    const errors = validate(measurements, [{ characteristic: 'A', measured: 120, ok: true }, {}])
    expect(errors).toEqual([
      { pointer: '/0/measured', code: 'MAXIMUM', params: { limit: 100 } },
      { pointer: '/1/characteristic', code: 'REQUIRED' },
      { pointer: '/1/measured', code: 'REQUIRED' },
      { pointer: '/1/ok', code: 'REQUIRED' },
    ])
    expect(validate(measurements, [{ characteristic: 'A', measured: 'abc', ok: true }])).toEqual([
      { pointer: '/0/measured', code: 'TYPE_NUMBER' },
    ])
  })

  it('does not report an empty (not yet filled) root value', () => {
    expect(validate(sample, undefined)).toEqual([])
    expect(validate(null, { a: 1 })).toEqual([])
  })
})

describe('JSON pointer helpers', () => {
  it('escapes and parses tokens (RFC 6901)', () => {
    expect(joinPointer('', 'a/b~c')).toBe('/a~1b~0c')
    expect(parsePointer('/a~1b~0c/0')).toEqual(['a/b~c', '0'])
    expect(parsePointer('')).toEqual([])
  })

  it('normalises server field notations', () => {
    expect(normalizePointer('/0/measured')).toBe('/0/measured')
    expect(normalizePointer('#/0/measured')).toBe('/0/measured')
    expect(normalizePointer('$[0].measured')).toBe('/0/measured')
    expect(normalizePointer('quantity')).toBe('/quantity')
    expect(normalizePointer(undefined)).toBe('')
  })

  it('sets values immutably and deletes cleared keys', () => {
    const root = [{ a: 1, b: 2 }]
    const next = setIn(root, [0, 'b'], undefined)
    expect(next).toEqual([{ a: 1 }])
    expect(root).toEqual([{ a: 1, b: 2 }])
    expect(setIn(undefined, ['x'], 5)).toEqual({ x: 5 })
  })

  it('parses numbers with decimal comma, keeps invalid text', () => {
    expect(parseNumberInput('1,5')).toBe(1.5)
    expect(parseNumberInput(' -2 ')).toBe(-2)
    expect(parseNumberInput('')).toBeUndefined()
    expect(parseNumberInput('1.2.3')).toBe('1.2.3')
  })

  it('initialises required booleans of new rows with false', () => {
    expect(newRow(measurements.items as JsonSchema)).toEqual({ ok: false })
  })

  it('lists the pointers the form can show errors at', () => {
    const pointers = renderablePointers(measurements, [{}])
    expect(pointers.has('')).toBe(true)
    expect(pointers.has('/0')).toBe(true)
    expect(pointers.has('/0/measured')).toBe(true)
    expect(pointers.has('/5/measured')).toBe(false)
  })
})

function Harness({
  schema,
  initial,
  serverErrors,
  showAllErrors,
  onValue,
}: {
  schema: JsonSchema
  initial?: JsonValue
  serverErrors?: SchemaError[]
  showAllErrors?: boolean
  onValue?: (v: JsonValue | undefined) => void
}) {
  const [value, setValue] = useState<JsonValue | undefined>(initial)
  return (
    <SchemaForm
      schema={schema}
      value={value}
      label="Prüfergebnisse"
      serverErrors={serverErrors}
      showAllErrors={showAllErrors}
      onChange={(v) => {
        setValue(v)
        onValue?.(v)
      }}
    />
  )
}

describe('<SchemaForm>', () => {
  it('renders object fields with labels, required markers and typed values', async () => {
    const user = userEvent.setup()
    const onValue = vi.fn()
    renderWithProviders(<Harness schema={sample} onValue={onValue} />)

    const quantity = screen.getByLabelText(/^Menge/)
    expect(quantity).toHaveAttribute('aria-required', 'true')
    await user.type(quantity, '12')
    expect(onValue).toHaveBeenLastCalledWith({ quantity: 12 })

    await user.click(screen.getByRole('radio', { name: 'Ja' }))
    expect(onValue).toHaveBeenLastCalledWith({ quantity: 12, declared: true })

    fireEvent.change(screen.getByLabelText('Versanddatum'), { target: { value: '2026-12-24' } })
    expect(onValue).toHaveBeenLastCalledWith({ quantity: 12, declared: true, shippedOn: '2026-12-24' })

    // clearing removes the key
    await user.clear(quantity)
    expect(onValue).toHaveBeenLastCalledWith({ declared: true, shippedOn: '2026-12-24' })
  })

  it('shows client errors after blur only (or all when requested)', async () => {
    const user = userEvent.setup()
    renderWithProviders(<Harness schema={sample} initial={{ quantity: 0 }} />)
    expect(screen.queryByText('Mindestens 1.')).not.toBeInTheDocument()
    await user.click(screen.getByLabelText(/^Menge/))
    await user.tab()
    expect(screen.getByText('Mindestens 1.')).toBeInTheDocument()
    expect(screen.getByLabelText(/^Menge/)).toHaveAttribute('aria-invalid', 'true')
  })

  it('edits arrays of objects as a table with add/remove rows', async () => {
    const user = userEvent.setup()
    const onValue = vi.fn()
    renderWithProviders(<Harness schema={measurements} onValue={onValue} />)
    const table = screen.getByRole('table', { name: 'Prüfergebnisse' })
    expect(within(table).getByText('Noch keine Zeilen erfasst.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Zeile hinzufügen' }))
    expect(onValue).toHaveBeenLastCalledWith([{ ok: false }])

    await user.type(screen.getByRole('textbox', { name: 'Merkmal, Zeile 1' }), 'D1')
    await user.type(screen.getByRole('textbox', { name: 'Istwert, Zeile 1' }), '4,2')
    await user.click(screen.getByRole('checkbox', { name: 'i. O., Zeile 1' }))
    expect(onValue).toHaveBeenLastCalledWith([{ ok: true, characteristic: 'D1', measured: 4.2 }])

    await user.click(screen.getByRole('button', { name: 'Zeile hinzufügen' }))
    expect(screen.getByText('2 Zeilen')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Zeile 1 entfernen' }))
    expect(onValue).toHaveBeenLastCalledWith([{ ok: false }])
    expect(screen.getByRole('textbox', { name: 'Merkmal, Zeile 1' })).toHaveValue('')
  })

  it('flags invalid cells with aria-invalid and lists cell errors when showing all errors', () => {
    renderWithProviders(
      <Harness schema={measurements} showAllErrors initial={[{ characteristic: 'A', measured: 120, ok: true }]} />,
    )
    expect(screen.getByRole('textbox', { name: 'Istwert, Zeile 1' })).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('Zeile 1 · Istwert: Höchstens 100.')).toBeInTheDocument()
  })

  it('shows server VALUE_SCHEMA_VIOLATION errors at their JSON pointer, others in a summary', () => {
    renderWithProviders(
      <Harness
        schema={measurements}
        initial={[{ characteristic: 'A', measured: 1, ok: true }]}
        serverErrors={[
          { pointer: '/0/characteristic', code: 'VALUE_SCHEMA_VIOLATION', message: 'unknown characteristic' },
          { pointer: '/9/x', code: 'VALUE_SCHEMA_VIOLATION', message: 'stale row' },
        ]}
      />,
    )
    expect(screen.getByRole('textbox', { name: 'Merkmal, Zeile 1' })).toHaveAttribute('aria-invalid', 'true')
    expect(
      screen.getByText('Zeile 1 · Merkmal: Wert entspricht nicht dem Werteformat (unknown characteristic)'),
    ).toBeInTheDocument()
    const summary = screen.getByRole('alert')
    expect(within(summary).getByText('/9/x')).toBeInTheDocument()
  })

  it('renders values read-only', () => {
    renderWithProviders(
      <SchemaValueView
        schema={measurements}
        value={[{ characteristic: 'D1', measured: 4.2, ok: true }]}
        label="Werte"
      />,
    )
    const table = screen.getByRole('table', { name: 'Werte' })
    expect(within(table).getByText('D1')).toBeInTheDocument()
    expect(within(table).getByText('4,2')).toBeInTheDocument()
    expect(within(table).getByText('Ja')).toBeInTheDocument()
  })
})
