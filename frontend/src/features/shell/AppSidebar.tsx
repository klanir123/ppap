import { Link, useRouterState } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { NAVIGATION, type NavItem } from '@/app/navigation'
import { BrandMark } from '@/components/BrandMark'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  SidebarRail,
} from '@/components/ui/sidebar'
import { useAppConfig } from '@/features/config/config-context'
import { useCurrentUser } from '@/features/auth/current-user'
import { hasPermission } from '@/lib/permissions'

function isActivePath(pathname: string, to: string) {
  return to === '/' ? pathname === '/' : pathname === to || pathname.startsWith(`${to}/`)
}

export function AppSidebar() {
  const { t } = useTranslation(['nav', 'common'])
  const { version } = useAppConfig()
  const { roles } = useCurrentUser()
  const pathname = useRouterState({ select: (s) => s.location.pathname })

  const visible = (item: NavItem) => hasPermission(roles, item.permission)
  const groups = NAVIGATION.map((group) => ({ ...group, items: group.items.filter(visible) })).filter(
    (group) => group.items.length > 0,
  )

  return (
    <Sidebar collapsible="icon" aria-label={t('common:sidebar.title')}>
      <SidebarHeader className="h-[3.625rem] justify-center border-b border-sidebar-border">
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" asChild className="hover:bg-transparent active:bg-transparent">
              <Link to="/">
                <BrandMark />
                <span className="grid min-w-0 leading-tight">
                  <span className="truncate text-sm font-semibold text-sidebar-accent-foreground">
                    {t('common:productName')}
                  </span>
                  <span className="truncate text-[11px] text-muted-foreground">{t('common:vendor')}</span>
                </span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent className="pt-2">
        {groups.map((group) => (
          <SidebarGroup key={group.labelKey}>
            <SidebarGroupLabel>{t(`groups.${group.labelKey}`)}</SidebarGroupLabel>
            <SidebarMenu>
              {group.items.map((item) => {
                const childActive = item.children?.some((c) => isActivePath(pathname, c.to)) ?? false
                const active = item.children ? childActive : isActivePath(pathname, item.to)
                const label = t(item.labelKey)
                return (
                  <SidebarMenuItem key={item.labelKey}>
                    <SidebarMenuButton
                      asChild
                      isActive={active}
                      tooltip={label}
                      className="data-[active=true]:font-medium data-[active=true]:text-sidebar-accent-foreground data-[active=true]:[&>svg]:text-participant"
                    >
                      <Link to={item.to} aria-current={active && !item.children ? 'page' : undefined}>
                        <item.icon aria-hidden="true" />
                        <span>{label}</span>
                      </Link>
                    </SidebarMenuButton>
                    {item.children ? (
                      <SidebarMenuSub>
                        {item.children.filter(visible).map((child) => {
                          const subActive = isActivePath(pathname, child.to)
                          return (
                            <SidebarMenuSubItem key={child.labelKey}>
                              <SidebarMenuSubButton asChild isActive={subActive}>
                                <Link to={child.to} aria-current={subActive ? 'page' : undefined}>
                                  <span>{t(child.labelKey)}</span>
                                </Link>
                              </SidebarMenuSubButton>
                            </SidebarMenuSubItem>
                          )
                        })}
                      </SidebarMenuSub>
                    ) : null}
                  </SidebarMenuItem>
                )
              })}
            </SidebarMenu>
          </SidebarGroup>
        ))}
      </SidebarContent>

      <SidebarFooter className="border-t border-sidebar-border">
        <p className="truncate px-2 py-1 text-[11px] text-muted-foreground group-data-[collapsible=icon]:hidden">
          {t('common:version', { version })}
        </p>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}
