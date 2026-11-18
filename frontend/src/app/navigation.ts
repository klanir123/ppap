import {
  Activity,
  Boxes,
  Cable,
  Database,
  FileCheck2,
  GitFork,
  LayoutDashboard,
  ListChecks,
  MapPin,
  Package,
  Users,
  type LucideIcon,
} from 'lucide-react'

import type { Permission } from '@/lib/permissions'

/** Keys of the `nav` namespace used as labels. */
export type NavLabelKey =
  | 'dashboard'
  | 'approvals'
  | 'masterdata'
  | 'parts'
  | 'partners'
  | 'sites'
  | 'relationships'
  | 'elementCatalog'
  | 'cockpit'
  | 'dataspace'
  | 'system'

export interface NavItem {
  labelKey: NavLabelKey
  to: string
  icon: LucideIcon
  permission: Permission
  children?: NavItem[]
}

export interface NavGroup {
  labelKey: 'work' | 'operations'
  items: NavItem[]
}

/** Single source of truth for the sidebar. Visibility is derived from `permissions.ts`. */
export const NAVIGATION: NavGroup[] = [
  {
    labelKey: 'work',
    items: [
      { labelKey: 'dashboard', to: '/', icon: LayoutDashboard, permission: 'dashboard:view' },
      { labelKey: 'approvals', to: '/approvals', icon: FileCheck2, permission: 'approvals:view' },
      {
        labelKey: 'masterdata',
        to: '/masterdata/parts',
        icon: Database,
        permission: 'masterdata:view',
        children: [
          { labelKey: 'parts', to: '/masterdata/parts', icon: Package, permission: 'masterdata:view' },
          { labelKey: 'partners', to: '/masterdata/partners', icon: Users, permission: 'masterdata:view' },
          { labelKey: 'sites', to: '/masterdata/sites', icon: MapPin, permission: 'masterdata:view' },
          { labelKey: 'relationships', to: '/masterdata/relationships', icon: GitFork, permission: 'masterdata:view' },
          {
            labelKey: 'elementCatalog',
            to: '/masterdata/element-catalog',
            icon: ListChecks,
            permission: 'masterdata:view',
          },
        ],
      },
    ],
  },
  {
    labelKey: 'operations',
    items: [
      { labelKey: 'cockpit', to: '/cockpit', icon: Cable, permission: 'cockpit:view' },
      { labelKey: 'dataspace', to: '/dataspace', icon: Boxes, permission: 'dataspace:view' },
      { labelKey: 'system', to: '/system', icon: Activity, permission: 'system:view' },
    ],
  },
]
