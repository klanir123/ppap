import { Link } from '@tanstack/react-router'
import { Activity, Sparkles, type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import type { NavLabelKey } from '@/app/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'

type DescriptionKey =
  | 'approvals.description'
  | 'parts.description'
  | 'partners.description'
  | 'sites.description'
  | 'relationships.description'
  | 'elementCatalog.description'
  | 'cockpit.description'
  | 'dataspace.description'

interface ComingSoonPageProps {
  titleKey: NavLabelKey
  descriptionKey: DescriptionKey
  icon: LucideIcon
  phase: number
  showSystemLink?: boolean
}

/** Placeholder for areas delivered in later phases – keeps navigation complete. */
export function ComingSoonPage({ titleKey, descriptionKey, icon: Icon, phase, showSystemLink }: ComingSoonPageProps) {
  const { t } = useTranslation(['pages', 'nav'])
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold tracking-tight">{t(`nav:${titleKey}`)}</h1>
      <Card className="overflow-hidden">
        <CardContent className="flex flex-col items-center gap-5 px-6 py-16 text-center">
          <div className="relative">
            <div className="flex size-14 items-center justify-center rounded-2xl bg-participant-soft text-participant-soft-foreground ring-1 ring-participant/20">
              <Icon aria-hidden="true" className="size-6" />
            </div>
          </div>
          <div className="space-y-2">
            <p className="inline-flex items-center gap-1.5 rounded-full border bg-muted/60 px-2.5 py-0.5 text-xs font-medium text-muted-foreground">
              <Sparkles aria-hidden="true" className="size-3" />
              {t('comingSoon', { phase })}
            </p>
            <p className="mx-auto max-w-lg text-sm text-muted-foreground">{t(descriptionKey)}</p>
          </div>
          {showSystemLink ? (
            <Button variant="outline" asChild>
              <Link to="/system">
                <Activity aria-hidden="true" />
                {t('nav:system')}
              </Link>
            </Button>
          ) : null}
        </CardContent>
      </Card>
    </div>
  )
}
