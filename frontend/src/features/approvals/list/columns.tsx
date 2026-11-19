import type { ColumnDef } from '@tanstack/react-table'
import type { TFunction } from 'i18next'

import { ClarificationChip } from '@/features/exchange/clarification/ClarificationChip'
import { LastOutboundBadge } from '@/features/exchange/components/Delivery'

import type { ApprovalCase } from '../api/types'
import { CasePartText, CaseStatusBadge, DueDate, PartnerText, RoleBadge, SourceBadge } from '../components/badges'
import { CaseNumberLink } from '../components/CaseNumberLink'
import { localized } from '../components/format'

export function caseColumns(t: TFunction<'approvals'>, lng: string | undefined): ColumnDef<ApprovalCase, unknown>[] {
  return [
    {
      id: 'caseNumber',
      header: t('columns.caseNumber'),
      meta: { label: t('columns.caseNumber'), hideable: false },
      cell: ({ row }) => (
        <div className="flex flex-col items-start gap-1">
          <CaseNumberLink approvalCase={row.original} />
          <span className="flex flex-wrap gap-1">
            <SourceBadge source={row.original.source} />
            <ClarificationChip approvalCase={row.original} />
          </span>
        </div>
      ),
    },
    {
      id: 'status',
      header: t('columns.status'),
      meta: { label: t('columns.status') },
      cell: ({ row }) => (
        <div className="flex flex-col items-start gap-1">
          <CaseStatusBadge status={row.original.status} size="sm" />
          <LastOutboundBadge last={row.original.lastOutbound} />
        </div>
      ),
    },
    {
      id: 'part',
      header: t('columns.part'),
      meta: { label: t('columns.part'), sortField: 'part.partNumber' },
      cell: ({ row }) => (
        <span className="flex min-w-0 flex-col">
          <CasePartText approvalCase={row.original} layout="stacked" nameClassName="max-w-[16rem]" />
        </span>
      ),
    },
    {
      id: 'partner',
      header: t('columns.partner'),
      meta: { label: t('columns.partner'), sortField: 'partner.name' },
      cell: ({ row }) => <PartnerText name={row.original.partner.name} bpnl={row.original.partner.bpnl} />,
    },
    {
      id: 'ownRole',
      header: t('columns.ownRole'),
      meta: { label: t('columns.ownRole') },
      cell: ({ row }) => <RoleBadge role={row.original.ownRole} />,
    },
    {
      id: 'reason',
      header: t('columns.reasonLevel'),
      meta: { label: t('columns.reasonLevel'), sortField: false },
      cell: ({ row }) => (
        <span className="flex flex-col text-xs">
          <span className="max-w-[14rem] truncate">{localized(row.original.reason, lng)}</span>
          <span className="text-muted-foreground">{row.original.level.code}</span>
        </span>
      ),
    },
    {
      id: 'dueDate',
      header: t('columns.dueDate'),
      meta: { label: t('columns.dueDate'), hideable: false },
      cell: ({ row }) => (
        <DueDate dueDate={row.original.dueDate} overdue={row.original.overdue} status={row.original.status} compact />
      ),
    },
    {
      id: 'currentRevision',
      header: t('columns.revision'),
      meta: { label: t('columns.revision'), className: 'text-right tabular', headerClassName: 'text-right' },
      cell: ({ row }) => row.original.currentRevision ?? '–',
    },
    {
      id: 'updatedAt',
      header: t('columns.updatedAt'),
      meta: { label: t('columns.updatedAt') },
      cell: ({ row }) =>
        row.original.updatedAt ? (
          <time dateTime={row.original.updatedAt} className="text-xs whitespace-nowrap text-muted-foreground tabular">
            {new Intl.DateTimeFormat(lng, { dateStyle: 'short', timeStyle: 'short' }).format(
              new Date(row.original.updatedAt),
            )}
          </time>
        ) : (
          '–'
        ),
    },
  ]
}
