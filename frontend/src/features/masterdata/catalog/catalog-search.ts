import { z } from 'zod'

import { listSearchSchema } from '@/components/data-table'

export const CATALOG_TABS = ['elements', 'levels', 'reasons'] as const
export type CatalogTab = (typeof CATALOG_TABS)[number]

export const catalogSearchSchema = listSearchSchema.extend({
  tab: z.enum(CATALOG_TABS).optional().catch(undefined),
})
