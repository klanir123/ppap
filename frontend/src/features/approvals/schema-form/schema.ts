/**
 * Minimal JSON Schema (draft 2020-12) support for element value forms:
 * object, array (of objects or primitives), string (+ format date, pattern, enum), number, integer,
 * boolean, required, minimum/maximum, minLength/maxLength, minItems/maxItems.
 * The server validates authoritatively; this gives immediate feedback and the form structure.
 */
import type { JsonSchema, JsonValue } from '../api/types'

export type FieldKind =
  'object' | 'array' | 'string' | 'date' | 'number' | 'integer' | 'boolean' | 'enum' | 'unsupported'

export interface SchemaError {
  /** RFC 6901 JSON pointer relative to the value root ('' = root). */
  pointer: string
  code: SchemaErrorCode
  params?: Record<string, string | number>
  /** Server message (English), for server-side errors. */
  message?: string
}

export type SchemaErrorCode =
  | 'REQUIRED'
  | 'TYPE_NUMBER'
  | 'TYPE_INTEGER'
  | 'TYPE_STRING'
  | 'TYPE_BOOLEAN'
  | 'TYPE_OBJECT'
  | 'TYPE_ARRAY'
  | 'MINIMUM'
  | 'MAXIMUM'
  | 'MIN_LENGTH'
  | 'MAX_LENGTH'
  | 'PATTERN'
  | 'ENUM'
  | 'DATE'
  | 'MIN_ITEMS'
  | 'MAX_ITEMS'
  | 'VALUE_SCHEMA_VIOLATION'

// --- JSON pointer -------------------------------------------------------------------------------

export function escapeToken(token: string | number): string {
  return String(token).replace(/~/g, '~0').replace(/\//g, '~1')
}

export function unescapeToken(token: string): string {
  return token.replace(/~1/g, '/').replace(/~0/g, '~')
}

export function joinPointer(base: string, token: string | number): string {
  return `${base}/${escapeToken(token)}`
}

export function parsePointer(pointer: string): string[] {
  if (!pointer) return []
  const normalized = pointer.startsWith('#') ? pointer.slice(1) : pointer
  return normalized.split('/').slice(1).map(unescapeToken)
}

/** Server pointers may come as `/0/measured`, `#/0/measured` or `$[0].measured` – normalise to RFC 6901. */
export function normalizePointer(field: string | undefined): string {
  if (!field) return ''
  if (field.startsWith('/') || field.startsWith('#')) return field.replace(/^#/, '')
  // JSONPath-ish `$.a[0].b` / `a[0].b`
  const tokens = field
    .replace(/^\$\.?/, '')
    .split(/\.|\[(\d+)\]/)
    .filter((t) => t !== undefined && t !== '')
  return tokens.map((t) => `/${escapeToken(t)}`).join('')
}

// --- schema helpers -----------------------------------------------------------------------------

type Schema = JsonSchema

const typeOf = (schema: Schema): string | undefined => {
  const t = schema.type
  if (Array.isArray(t)) return t.find((x) => x !== 'null') as string | undefined
  return typeof t === 'string' ? t : undefined
}

export function fieldKind(schema: Schema | undefined): FieldKind {
  if (!schema) return 'unsupported'
  if (Array.isArray(schema.enum)) return 'enum'
  const type = typeOf(schema)
  switch (type) {
    case 'object':
      return 'object'
    case 'array':
      return 'array'
    case 'string':
      return schema.format === 'date' ? 'date' : 'string'
    case 'number':
    case 'integer':
    case 'boolean':
      return type
    default:
      if (schema.properties) return 'object'
      return 'unsupported'
  }
}

export function properties(schema: Schema): [string, Schema][] {
  const props = schema.properties
  if (!props || typeof props !== 'object') return []
  return Object.entries(props as Record<string, Schema>)
}

export function requiredSet(schema: Schema): Set<string> {
  return new Set(Array.isArray(schema.required) ? (schema.required as string[]) : [])
}

export function itemSchema(schema: Schema): Schema | undefined {
  const items = schema.items
  return items && typeof items === 'object' && !Array.isArray(items) ? (items as Schema) : undefined
}

export const num = (value: unknown): number | undefined => (typeof value === 'number' ? value : undefined)

const isPlainObject = (v: unknown): v is Record<string, JsonValue> =>
  typeof v === 'object' && v !== null && !Array.isArray(v)

const DATE_RE = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/

/** Empty = not filled in (undefined, null, '' ). */
export function isEmpty(value: unknown): boolean {
  return value === undefined || value === null || value === ''
}

/** Initial value for a new array row: required booleans start as `false`, everything else empty. */
export function newRow(items: Schema | undefined): JsonValue {
  if (!items) return null
  if (fieldKind(items) !== 'object') return null
  const row: Record<string, JsonValue> = {}
  const required = requiredSet(items)
  for (const [key, prop] of properties(items)) {
    if (fieldKind(prop) === 'boolean' && required.has(key)) row[key] = false
  }
  return row
}

// --- validation ---------------------------------------------------------------------------------

/** Validates `value` against `schema`. Empty optional values are skipped. */
export function validate(schema: Schema | undefined | null, value: unknown): SchemaError[] {
  const errors: SchemaError[] = []
  if (schema) visit(schema, value, '', errors)
  return errors
}

function visit(schema: Schema, value: unknown, pointer: string, errors: SchemaError[]) {
  if (isEmpty(value)) return
  const kind = fieldKind(schema)
  switch (kind) {
    case 'object': {
      if (!isPlainObject(value)) return void errors.push({ pointer, code: 'TYPE_OBJECT' })
      const required = requiredSet(schema)
      for (const [key, prop] of properties(schema)) {
        const child = joinPointer(pointer, key)
        if (isEmpty(value[key])) {
          if (required.has(key)) errors.push({ pointer: child, code: 'REQUIRED' })
        } else visit(prop, value[key], child, errors)
      }
      return
    }
    case 'array': {
      if (!Array.isArray(value)) return void errors.push({ pointer, code: 'TYPE_ARRAY' })
      const minItems = num(schema.minItems)
      const maxItems = num(schema.maxItems)
      if (minItems !== undefined && value.length < minItems)
        errors.push({ pointer, code: 'MIN_ITEMS', params: { limit: minItems } })
      if (maxItems !== undefined && value.length > maxItems)
        errors.push({ pointer, code: 'MAX_ITEMS', params: { limit: maxItems } })
      const items = itemSchema(schema)
      if (items) value.forEach((item, i) => visitItem(items, item, joinPointer(pointer, i), errors))
      return
    }
    case 'enum': {
      const options = schema.enum as unknown[]
      if (!options.includes(value)) errors.push({ pointer, code: 'ENUM' })
      return
    }
    case 'boolean':
      if (typeof value !== 'boolean') errors.push({ pointer, code: 'TYPE_BOOLEAN' })
      return
    case 'number':
    case 'integer': {
      if (typeof value !== 'number' || Number.isNaN(value))
        return void errors.push({ pointer, code: kind === 'integer' ? 'TYPE_INTEGER' : 'TYPE_NUMBER' })
      if (kind === 'integer' && !Number.isInteger(value)) return void errors.push({ pointer, code: 'TYPE_INTEGER' })
      const minimum = num(schema.minimum)
      const maximum = num(schema.maximum)
      if (minimum !== undefined && value < minimum)
        errors.push({ pointer, code: 'MINIMUM', params: { limit: minimum } })
      if (maximum !== undefined && value > maximum)
        errors.push({ pointer, code: 'MAXIMUM', params: { limit: maximum } })
      return
    }
    case 'string':
    case 'date': {
      if (typeof value !== 'string') return void errors.push({ pointer, code: 'TYPE_STRING' })
      if (kind === 'date' && !DATE_RE.test(value)) return void errors.push({ pointer, code: 'DATE' })
      const minLength = num(schema.minLength)
      const maxLength = num(schema.maxLength)
      if (minLength !== undefined && value.length < minLength)
        errors.push({ pointer, code: 'MIN_LENGTH', params: { limit: minLength } })
      if (maxLength !== undefined && value.length > maxLength)
        errors.push({ pointer, code: 'MAX_LENGTH', params: { limit: maxLength } })
      if (typeof schema.pattern === 'string') {
        try {
          if (!new RegExp(schema.pattern, 'u').test(value))
            errors.push({ pointer, code: 'PATTERN', params: { pattern: schema.pattern } })
        } catch {
          // invalid pattern in the schema – leave it to the server
        }
      }
      return
    }
    default:
      return
  }
}

/** Array items: an empty row object still has to satisfy `required`. */
function visitItem(schema: Schema, value: unknown, pointer: string, errors: SchemaError[]) {
  if (isEmpty(value) && fieldKind(schema) === 'object') visit(schema, {}, pointer, errors)
  else if (isEmpty(value)) errors.push({ pointer, code: 'REQUIRED' })
  else visit(schema, value, pointer, errors)
}

/**
 * Pointers the form renders an error slot for (fields, table cells, rows, the array itself).
 * Errors at other pointers are shown in the form-level summary.
 */
export function renderablePointers(schema: Schema | undefined | null, value: unknown): Set<string> {
  const result = new Set<string>()
  if (!schema) return result
  const collect = (s: Schema, v: unknown, pointer: string, depth: number) => {
    const kind = fieldKind(s)
    result.add(pointer)
    if (kind === 'object' && depth < 2) {
      const obj = isPlainObject(v) ? v : {}
      for (const [key, prop] of properties(s)) collect(prop, obj[key], joinPointer(pointer, key), depth + 1)
    } else if (kind === 'array' && Array.isArray(v)) {
      const items = itemSchema(s)
      v.forEach((item, i) => {
        const rowPointer = joinPointer(pointer, i)
        result.add(rowPointer)
        if (items && fieldKind(items) === 'object') {
          for (const [key] of properties(items)) result.add(joinPointer(rowPointer, key))
        }
        void item
      })
    }
  }
  collect(schema, value, '', 0)
  return result
}

/** Parses user input for number/integer fields; accepts a decimal comma. Invalid text stays a string. */
export function parseNumberInput(text: string): number | string | undefined {
  const trimmed = text.trim()
  if (!trimmed) return undefined
  const normalized = trimmed.replace(',', '.')
  if (/^[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?$/.test(normalized)) return Number(normalized)
  return trimmed
}

/** Immutable set by pointer tokens; `undefined` deletes object keys. */
export function setIn(root: JsonValue | undefined, tokens: readonly (string | number)[], value: JsonValue | undefined) {
  if (tokens.length === 0) return value
  const [head, ...rest] = tokens
  if (typeof head === 'number') {
    const arr = Array.isArray(root) ? [...root] : []
    const next = setIn(arr[head], rest, value)
    arr[head] = next === undefined ? null : next
    return arr
  }
  const obj: Record<string, JsonValue> = isPlainObject(root) ? { ...root } : {}
  const next = setIn(obj[head!], rest, value)
  if (next === undefined) delete obj[head!]
  else obj[head!] = next
  return obj
}
