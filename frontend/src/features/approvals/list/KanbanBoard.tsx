import { Link } from '@tanstack/react-router'
import { Inbox } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Skeleton } from '@/components/ui/skeleton'
import { statusStyle } from '@/lib/status-style'
import { cn } from '@/lib/utils'

import type { ApprovalCase, CaseStatus } from '../api/types'
import { CaseStatusBadge, DueDate, RoleBadge, SourceBadge } from '../components/badges'
import { localized } from '../components/format'
import { groupByStatus } from '../model/kanban'
import { caseStatusToken } from '../model/status'

interface KanbanBoardProps {
  cases: ApprovalCase[]
  statuses: CaseStatus[]
  isLoading?: boolean
}

/** Read-only board – status changes only through explicit actions in the case (no drag & drop). */
export function KanbanBoard({ cases, statuses, isLoading }: KanbanBoardProps) {
  const { t } = useTranslation('approvals')
  const columns = groupByStatus(cases, statuses)
  return (
    <div className="space-y-2">
      <p className="text-xs text-muted-foreground">{t('board.hint')}</p>
      <div className="-mx-1 relative overflow-x-auto px-1 pb-2" role="region" aria-label={t('board.label')}>
        <div className="flex gap-3">
          {columns.map((column) => (
            <section
              key={column.status}
              aria-labelledby={`board-col-${column.status}`}
              className="flex w-[17rem] shrink-0 flex-col rounded-lg border bg-muted/40"
              style={statusStyle(caseStatusToken(column.status))}
            >
              <header className="flex items-center justify-between gap-2 border-b border-t-2 border-t-(--sb-solid) px-3 py-2.5">
                <h2 id={`board-col-${column.status}`} className="flex items-center gap-2 text-sm">
                  <CaseStatusBadge status={column.status} size="sm" />
                </h2>
                <span
                  className="text-xs text-muted-foreground tabular"
                  aria-label={t('board.count', { count: column.cases.length })}
                >
                  {column.cases.length}
                </span>
              </header>
              <ol className="flex max-h-[min(65svh,44rem)] flex-col gap-2 relative overflow-y-auto p-2">
                {isLoading ? (
                  Array.from({ length: 2 }, (_, i) => (
                    <li key={i} aria-hidden="true">
                      <Skeleton className="h-24 w-full" />
                    </li>
                  ))
                ) : column.cases.length === 0 ? (
                  <li className="flex flex-col items-center gap-1 px-2 py-6 text-center text-xs text-muted-foreground">
                    <Inbox aria-hidden="true" className="size-4" />
                    {t('board.emptyColumn')}
                  </li>
                ) : (
                  column.cases.map((c) => (
                    <li key={c.id}>
                      <KanbanCard approvalCase={c} />
                    </li>
                  ))
                )}
              </ol>
            </section>
          ))}
        </div>
      </div>
    </div>
  )
}

function KanbanCard({ approvalCase: c }: { approvalCase: ApprovalCase }) {
  const { i18n } = useTranslation('approvals')
  return (
    <Link
      to="/approvals/$caseId"
      params={{ caseId: c.id }}
      className={cn(
        'block space-y-2 rounded-md border bg-card p-3 text-sm shadow-xs transition-colors hover:border-foreground/20 hover:bg-accent/40',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
        c.overdue && 'border-l-4 border-l-(--status-error-solid)',
      )}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="font-mono text-xs font-semibold">{c.caseNumber}</span>
        <RoleBadge role={c.ownRole} />
      </div>
      <div className="min-w-0">
        <p className="truncate font-mono text-xs">{c.part.partNumber}</p>
        <p className="truncate text-xs text-muted-foreground">{localized(c.part, i18n.resolvedLanguage)}</p>
      </div>
      <p className="truncate text-xs">{c.partner.name ?? c.partner.bpnl}</p>
      <div className="flex flex-wrap items-center justify-between gap-1.5 text-xs">
        <DueDate dueDate={c.dueDate} overdue={c.overdue} status={c.status} compact />
        <SourceBadge source={c.source} />
      </div>
    </Link>
  )
}
