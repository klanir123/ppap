import {
  Ban,
  BadgeCheck,
  CircleSlash,
  ShieldCheck,
  ShieldOff,
  ShieldQuestion,
  ShieldX,
  CircleAlert,
  CircleCheck,
  CircleDashed,
  CircleDot,
  CircleHelp,
  CircleMinus,
  CircleX,
  Clock,
  CircleCheckBig,
  Inbox,
  Loader,
  SkipForward,
  Hourglass,
  Eye,
  FileEdit,
  Send,
  ShieldAlert,
  TriangleAlert,
  type LucideIcon,
} from 'lucide-react'

import type {
  ApprovalStatus,
  Assessment,
  ExchangeState,
  StepStateValue,
  Finding,
  HealthStatus,
  RequirementLevel,
  SignatureState,
  ValidationStatus,
} from '@/lib/status'

/** Icon per status – status is never conveyed by colour alone. */
export const STATUS_ICONS = {
  approval: {
    draft: FileEdit,
    requested: Clock,
    'in-progress': CircleDashed,
    submitted: Send,
    'in-review': Eye,
    approved: CircleCheck,
    'conditionally-approved': ShieldAlert,
    rejected: CircleX,
    cancelled: Ban,
  } satisfies Record<ApprovalStatus, LucideIcon>,
  health: { UP: CircleCheck, DOWN: CircleX, UNKNOWN: CircleHelp } satisfies Record<HealthStatus, LucideIcon>,
  validation: { OK: CircleCheck, WARNING: TriangleAlert, ERROR: CircleAlert } satisfies Record<
    ValidationStatus,
    LucideIcon
  >,
  requirement: { REQUIRED: CircleDot, ON_REQUEST: CircleDashed, NOT_REQUIRED: CircleMinus } satisfies Record<
    RequirementLevel,
    LucideIcon
  >,
  active: { true: CircleCheck, false: CircleMinus },
  signature: {
    VALID: ShieldCheck,
    INVALID: ShieldX,
    UNVERIFIED: ShieldQuestion,
    NOT_SIGNED: ShieldOff,
  } satisfies Record<SignatureState, LucideIcon>,
  assessment: { OK: CircleCheck, NOK: CircleX, NA: CircleSlash } satisfies Record<Assessment, LucideIcon>,
  finding: {
    ACCEPTED: BadgeCheck,
    NOT_ACCEPTED: CircleX,
    ACCEPTED_WITH_CONDITION: ShieldAlert,
  } satisfies Record<Finding, LucideIcon>,
  exchange: {
    PENDING: Hourglass,
    IN_PROGRESS: Loader,
    DELIVERED: CircleCheck,
    FAILED: CircleX,
    RECEIVED: Inbox,
    PROCESSED: CircleCheckBig,
    REJECTED: Ban,
  } satisfies Record<ExchangeState, LucideIcon>,
  step: {
    OK: CircleCheck,
    FAILED: CircleX,
    SKIPPED: SkipForward,
    PENDING: CircleDashed,
    NOT_REACHED: CircleMinus,
  } satisfies Record<StepStateValue, LucideIcon>,
}

export type StatusKind = keyof typeof STATUS_ICONS
