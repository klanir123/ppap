import { Link } from '@tanstack/react-router'
import { Activity, LogOut } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from 'react-oidc-context'

import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useCurrentUser, userDisplayName } from '@/features/auth/current-user'
import { isRole } from '@/lib/permissions'

function initials(name: string) {
  return (
    name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase())
      .join('') || '?'
  )
}

export function UserMenu() {
  const { t } = useTranslation()
  const auth = useAuth()
  const me = useCurrentUser()
  const displayName = userDisplayName(me)

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="h-9 gap-2 px-1.5" aria-label={`${t('user.menu')}: ${displayName}`}>
          <Avatar className="size-7">
            <AvatarFallback className="bg-primary text-[11px] font-semibold text-primary-foreground">
              {initials(displayName)}
            </AvatarFallback>
          </Avatar>
          <span className="hidden max-w-32 truncate text-sm font-medium xl:inline">{displayName}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-72">
        <DropdownMenuLabel className="font-normal">
          <p className="truncate text-sm font-semibold text-foreground">{displayName}</p>
          {me.email ? <p className="truncate text-xs text-muted-foreground">{me.email}</p> : null}
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuLabel className="text-xs font-medium text-muted-foreground">{t('user.roles')}</DropdownMenuLabel>
        <div className="flex flex-wrap gap-1 px-2 pb-2">
          {me.roles.map((role) => (
            <span key={role} className="rounded-md border bg-muted/60 px-1.5 py-0.5 text-[11px] font-medium">
              {isRole(role) ? t(`roles.${role}`) : role}
            </span>
          ))}
        </div>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <DropdownMenuItem asChild>
            <Link to="/system">
              <Activity aria-hidden="true" />
              {t('user.systemStatus')}
            </Link>
          </DropdownMenuItem>
          <DropdownMenuItem
            onSelect={() => void auth.signoutRedirect({ post_logout_redirect_uri: `${window.location.origin}/` })}
          >
            <LogOut aria-hidden="true" />
            {t('user.logout')}
          </DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
