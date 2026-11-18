import { History, Pencil, Plus, Power, Trash2, Upload, type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { useListAuditEvents } from '@/api/generated/endpoints/audit/audit'
import type { ListAuditEventsObjectType } from '@/api/generated/model'
import { Skeleton } from '@/components/ui/skeleton'

const ACTION_ICONS: Record<string, LucideIcon> = {
  CREATED: Plus,
  UPDATED: Pencil,
  DEACTIVATED: Power,
  DELETED: Trash2,
  IMPORTED: Upload,
}

const PAGE_SIZE = 20

/** Read-only audit trail of one master data record (GET /api/v1/audit-events). */
export function AuditTrail({ objectType, objectId }: { objectType: ListAuditEventsObjectType; objectId: string }) {
  const { t, i18n } = useTranslation('masterdata')
  const query = useListAuditEvents({ objectType, objectId, size: PAGE_SIZE, page: 0 })
  const events = query.data?.content ?? []
  const fmt = new Intl.DateTimeFormat(i18n.resolvedLanguage, { dateStyle: 'medium', timeStyle: 'short' })
  const headingId = `audit-${objectId}`

  return (
    <section aria-labelledby={headingId} className="space-y-3">
      <div>
        <h3 id={headingId} className="flex items-center gap-2 text-sm font-semibold">
          <History aria-hidden="true" className="size-4 text-muted-foreground" />
          {t('audit.title')}
        </h3>
        <p className="text-xs text-muted-foreground">{t('audit.description')}</p>
      </div>
      {query.isPending ? (
        <div className="space-y-2" aria-busy="true">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      ) : query.isError ? (
        <p className="text-xs text-destructive">{t('audit.error')}</p>
      ) : events.length === 0 ? (
        <p className="text-xs text-muted-foreground">{t('audit.empty')}</p>
      ) : (
        <ol className="relative space-y-3 border-l pl-4">
          {events.map((event) => {
            const Icon = ACTION_ICONS[event.action] ?? History
            const actor = event.actor === 'system:seed' ? t('audit.seedActor') : event.actor
            return (
              <li key={event.id} className="relative">
                <span
                  aria-hidden="true"
                  className="absolute top-0.5 -left-[25px] flex size-4 items-center justify-center rounded-full border bg-card text-muted-foreground"
                >
                  <Icon className="size-2.5" />
                </span>
                <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5 text-xs">
                  <span className="font-medium text-foreground">
                    {t(`audit.actions.${event.action}` as 'audit.actions.CREATED', { defaultValue: event.action })}
                  </span>
                  <time dateTime={event.occurredAt} className="text-muted-foreground tabular">
                    {fmt.format(new Date(event.occurredAt))}
                  </time>
                </div>
                <div className="mt-0.5 flex flex-wrap items-center gap-x-3 text-xs text-muted-foreground">
                  <span>
                    <span className="sr-only">{t('audit.actor')}: </span>
                    {actor}
                  </span>
                  {event.businessKey ? (
                    <span className="font-mono">
                      <span className="sr-only">{t('audit.businessKey')}: </span>
                      {event.businessKey}
                    </span>
                  ) : null}
                  <span className="font-mono" title={t('audit.hashFull', { hash: event.hash })}>
                    <span className="sr-only">{t('audit.hash')}: </span>
                    {event.hash.slice(0, 12)}…
                  </span>
                </div>
              </li>
            )
          })}
        </ol>
      )}
      {query.data && query.data.totalElements > events.length ? (
        <p className="text-xs text-muted-foreground">
          {t('audit.more', { count: query.data.totalElements - events.length })}
        </p>
      ) : null}
    </section>
  )
}
