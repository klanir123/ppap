import {
  Ban,
  CircleAlert,
  CircleCheck,
  CircleDashed,
  CircleDot,
  CircleHelp,
  CircleMinus,
  CircleX,
  Clock,
  Eye,
  FileEdit,
  Send,
  ShieldAlert,
  TriangleAlert,
  type LucideIcon,
} from 'lucide-react'

import type { ApprovalStatus, HealthStatus, RequirementLevel, ValidationStatus } from '@/lib/status'

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
}

export type StatusKind = keyof typeof STATUS_ICONS
