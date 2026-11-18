import type { RequirementLevel } from '@/lib/status'

export type Requirements = Record<string, RequirementLevel>

/** Missing element code = NOT_REQUIRED. */
export function requirementOf(requirements: Requirements | undefined, code: string): RequirementLevel {
  return requirements?.[code] ?? 'NOT_REQUIRED'
}

/** Normalised map for saving: NOT_REQUIRED entries are dropped (PUT replaces the matrix). */
export function normaliseRequirements(requirements: Requirements): Requirements {
  return Object.fromEntries(Object.entries(requirements).filter(([, v]) => v !== 'NOT_REQUIRED'))
}

/** Element codes whose effective value differs between two matrices. */
export function changedCodes(original: Requirements | undefined, edited: Requirements, codes: string[]): string[] {
  return codes.filter((code) => requirementOf(original, code) !== requirementOf(edited, code))
}
