import { z } from 'zod'

/** Manual capture: zod messages are error codes. Due date may be in the past (backlog from e-mail/portal). */
export const manualSchema = z.object({
  partnerBpnl: z.string().min(1, 'REQUIRED'),
  partId: z.string().min(1, 'REQUIRED'),
  partnerPartNumber: z.string().max(64, 'TOO_LONG'),
  externalReference: z.string().max(200, 'TOO_LONG'),
  reasonCode: z.string().min(1, 'REQUIRED'),
  levelCode: z.string().min(1, 'REQUIRED'),
  dueDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'REQUIRED'),
  remarks: z.string().max(4000, 'TOO_LONG'),
})
