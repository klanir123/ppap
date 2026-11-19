import type { LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { STATUS_ICONS } from '@/components/status-icons'
import { statusStyle } from '@/lib/status-style'

import type { TimelineEntry } from '../api'
import { caseStatusToken } from '../model/status'
import { useFormat } from './format'

/** Vertical timeline of status changes (newest first). */
export function Timeline({ entries }: { entries: TimelineEntry[] }) {
  const { t } = useTranslation(['approvals', 'status'])
  const fmt = useFormat()
  const sorted = [...entries].sort((a, b) => b.at.localeCompare(a.at))
  if (sorted.length === 0) return <p className="text-sm text-muted-foreground">{t('timeline.empty')}</p>
  return (
    <ol className="relative space-y-5">
      {sorted.map((entry, index) => {
        const token = caseStatusToken(entry.to)
        const Icon = STATUS_ICONS.approval[token] as LucideIcon
        const last = index === sorted.length - 1
        return (
          <li key={`${entry.at}-${index}`} className="relative flex gap-3" style={statusStyle(token)}>
            {last ? null : (
              <span aria-hidden="true" className="absolute top-7 bottom-[-1.25rem] left-[13px] w-px bg-border" />
            )}
            <span
              aria-hidden="true"
              className="relative z-[1] flex size-7 shrink-0 items-center justify-center rounded-full border border-(--sb-border) bg-(--sb-bg) text-(--sb-fg)"
            >
              <Icon className="size-3.5" />
            </span>
            <div className="min-w-0 flex-1 pt-0.5">
              <p className="text-sm font-medium">
                {t(`timeline.actions.${entry.action}` as 'timeline.actions.SEND', {
                  defaultValue: t(`status:approval.${token}`),
                })}
              </p>
              <p className="text-xs text-muted-foreground">
                <time dateTime={entry.at} className="tabular">
                  {fmt.dateTime(entry.at)}
                </time>
                <span aria-hidden="true"> · </span>
                <span className="sr-only">{t('timeline.actor')}: </span>
                {entry.actor}
              </p>
              {entry.from ? (
                <p className="mt-0.5 text-xs text-muted-foreground">
                  {t('timeline.transition', {
                    from: t(`status:approval.${caseStatusToken(entry.from)}`),
                    to: t(`status:approval.${token}`),
                  })}
                </p>
              ) : null}
              {entry.comment ? (
                <p className="mt-1.5 rounded-md bg-muted/60 px-2.5 py-1.5 text-sm whitespace-pre-line">
                  {entry.comment}
                </p>
              ) : null}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
