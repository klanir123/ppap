import { PanelLeft } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { LanguageSwitch } from '@/components/LanguageSwitch'
import { ThemeToggle } from '@/components/ThemeToggle'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { useSidebar } from '@/components/ui/sidebar'

import { Breadcrumbs } from './Breadcrumbs'
import { ParticipantContext } from './ParticipantContext'
import { UserMenu } from './UserMenu'

export function TopBar() {
  const { t } = useTranslation()
  const { toggleSidebar } = useSidebar()

  return (
    <header className="sticky top-0 z-20 border-b bg-background/85 backdrop-blur supports-[backdrop-filter]:bg-background/70">
      {/* participant accent line – distinguishes OEM / Tier-n instances side by side */}
      <div aria-hidden="true" className="h-0.5 bg-participant" />
      <div className="flex h-14 items-center gap-2 px-3 sm:px-4">
        <Button variant="ghost" size="icon" onClick={toggleSidebar} aria-label={t('sidebar.toggle')}>
          <PanelLeft aria-hidden="true" />
        </Button>
        <Separator orientation="vertical" className="mx-1 hidden h-5! sm:block" />
        <div className="hidden min-w-0 flex-1 sm:block">
          <Breadcrumbs />
        </div>
        <div className="ml-auto flex min-w-0 items-center gap-1 sm:gap-2">
          <ParticipantContext />
          <Separator orientation="vertical" className="mx-1 hidden h-5! lg:block" />
          <LanguageSwitch />
          <ThemeToggle />
          <UserMenu />
        </div>
      </div>
    </header>
  )
}
