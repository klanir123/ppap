/**
 * Reading ODRL policies (JSON-LD, compacted or expanded with `odrl:` / full IRIs) for display:
 * flattens permission / prohibition / obligation constraints incl. nested `and` / `or` / `xone`.
 */
export interface OdrlConstraint {
  rule: 'permission' | 'prohibition' | 'obligation'
  action?: string
  leftOperand: string
  operator: string
  rightOperand: string
}

const RULES = ['permission', 'prohibition', 'obligation'] as const
const LOGICAL = ['and', 'or', 'xone', 'andSequence']

type Json = Record<string, unknown>

/** `odrl:leftOperand`, `http://www.w3.org/ns/odrl/2/leftOperand`, `leftOperand` → value. */
function prop(obj: Json, name: string): unknown {
  for (const [key, value] of Object.entries(obj)) {
    if (key === name || key.endsWith(`:${name}`) || key.endsWith(`/${name}`) || key.endsWith(`#${name}`)) return value
  }
  return undefined
}

function asArray(value: unknown): unknown[] {
  if (value === undefined || value === null) return []
  return Array.isArray(value) ? value : [value]
}

/** Short, readable term: IRI / CURIE → last segment (`cx-policy:FrameworkAgreement` → FrameworkAgreement). */
export function shortTerm(value: unknown): string {
  if (value === undefined || value === null) return ''
  if (typeof value === 'object') {
    const obj = value as Json
    const inner = obj['@id'] ?? obj['@value'] ?? obj['id']
    if (inner !== undefined) return shortTerm(inner)
    return JSON.stringify(value)
  }
  const s = String(value)
  if (/^https?:\/\//.test(s)) return s.split(/[/#]/).filter(Boolean).pop() ?? s
  const colon = s.indexOf(':')
  // keep values like "DataExchangeGovernance:1.0" or "cx.quality.base:1" (version after the colon)
  if (colon > 0 && /^[a-z][\w-]*$/i.test(s.slice(0, colon)) && /^[A-Za-z]/.test(s.slice(colon + 1))) {
    return s.slice(colon + 1)
  }
  return s
}

function value(v: unknown): string {
  const list = asArray(v).map((x) => shortTermValue(x))
  return list.join(', ')
}

/** Right operands are values, not terms: keep CURIE-like values intact unless they are IRIs / @id objects. */
function shortTermValue(v: unknown): string {
  if (v && typeof v === 'object') return shortTerm(v)
  const s = String(v)
  return /^https?:\/\//.test(s) ? shortTerm(s) : s
}

function collect(constraint: unknown, rule: OdrlConstraint['rule'], action: string | undefined, out: OdrlConstraint[]) {
  if (!constraint || typeof constraint !== 'object') return
  const obj = constraint as Json
  for (const logical of LOGICAL) {
    const nested = prop(obj, logical)
    if (nested !== undefined) {
      for (const c of asArray(nested)) collect(c, rule, action, out)
      return
    }
  }
  const left = prop(obj, 'leftOperand')
  if (left === undefined) return
  out.push({
    rule,
    action,
    leftOperand: shortTerm(left),
    operator: shortTerm(prop(obj, 'operator')) || 'eq',
    rightOperand: value(prop(obj, 'rightOperand')),
  })
}

/** The policy object itself (`policy` / `edc:policy` wrapper of EDC policy definitions, or offers). */
function unwrap(policy: unknown): Json | undefined {
  if (!policy || typeof policy !== 'object') return undefined
  const obj = policy as Json
  const inner = prop(obj, 'policy')
  if (inner && typeof inner === 'object' && !Array.isArray(inner)) return unwrap(inner)
  return obj
}

export function extractConstraints(policy: unknown): OdrlConstraint[] {
  const obj = unwrap(policy)
  if (!obj) return []
  const out: OdrlConstraint[] = []
  for (const rule of RULES) {
    for (const r of asArray(prop(obj, rule))) {
      if (!r || typeof r !== 'object') continue
      const action = shortTerm(prop(r as Json, 'action')) || undefined
      for (const c of asArray(prop(r as Json, 'constraint'))) collect(c, rule, action, out)
    }
  }
  return out
}

/** `@id` of a policy / offer, if present. */
export function policyId(policy: unknown): string | undefined {
  if (!policy || typeof policy !== 'object') return undefined
  const id = (policy as Json)['@id'] ?? (policy as Json)['id']
  return typeof id === 'string' ? id : undefined
}

const OPERATOR_SYMBOL: Record<string, string> = {
  eq: '=',
  neq: '≠',
  gt: '>',
  gteq: '≥',
  lt: '<',
  lteq: '≤',
  isAnyOf: '∈',
  isAllOf: '⊇',
  isNoneOf: '∉',
  isPartOf: '⊆',
}

export function operatorSymbol(operator: string): string {
  return OPERATOR_SYMBOL[operator] ?? operator
}
