/** EDC contract negotiation states (DSP state machine). */
export const NEGOTIATION_STATES = [
  'INITIAL',
  'REQUESTING',
  'REQUESTED',
  'OFFERED',
  'ACCEPTED',
  'AGREED',
  'VERIFIED',
  'FINALIZED',
  'TERMINATED',
] as const
/** EDC transfer process states. */
export const TRANSFER_STATES = [
  'INITIAL',
  'PROVISIONING',
  'REQUESTING',
  'REQUESTED',
  'STARTING',
  'STARTED',
  'SUSPENDED',
  'COMPLETED',
  'TERMINATED',
  'DEPROVISIONED',
] as const

export type ProcessTone = 'ok' | 'error' | 'progress'

const OK_STATES = new Set(['FINALIZED', 'AGREED', 'VERIFIED', 'STARTED', 'COMPLETED', 'DEPROVISIONED'])
const ERROR_STATES = new Set(['TERMINATED', 'TERMINATING', 'SUSPENDED'])

export function processTone(state: string): ProcessTone {
  if (OK_STATES.has(state)) return 'ok'
  if (ERROR_STATES.has(state)) return 'error'
  return 'progress'
}
