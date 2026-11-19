import { useTranslation } from 'react-i18next'

import { CopyButton } from '@/components/CopyButton'
import { Card, CardContent } from '@/components/ui/card'
import { CaseDeliveryIndicator } from '@/features/exchange/components/Delivery'

import type { ApprovalCaseDetail } from '../api'
import { CasePartText, CaseStatusBadge, DueDate, RoleBadge, SourceBadge } from '../components/badges'
import { CaseActions } from '../components/CaseActions'
import { localized, useFormat } from '../components/format'

function Item({ label, children, wide }: { label: string; children: React.ReactNode; wide?: boolean }) {
  return (
    <div className={wide ? 'min-w-0 space-y-1 sm:col-span-2' : 'min-w-0 space-y-1'}>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm">{children}</dd>
    </div>
  )
}

/** Case header: number, status, role, source, part/partner master data, reason, level, due date + actions. */
export function CaseHeader({ approvalCase: c }: { approvalCase: ApprovalCaseDetail }) {
  const { t, i18n } = useTranslation(['approvals', 'common'])
  const fmt = useFormat()
  const lng = i18n.resolvedLanguage
  return (
    <Card className="gap-5">
      <CardContent className="space-y-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0 space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="font-mono text-xl font-semibold tracking-tight sm:text-2xl">{c.caseNumber}</h1>
              <CaseStatusBadge status={c.status} />
              <CaseDeliveryIndicator caseId={c.id} />
            </div>
            <p className="text-sm text-muted-foreground">
              <CasePartText approvalCase={c} />
            </p>
            <div className="flex flex-wrap items-center gap-1.5">
              <RoleBadge role={c.ownRole} />
              <SourceBadge source={c.source} showLocal />
            </div>
          </div>
          <CaseActions approvalCase={c} />
        </div>
        <dl className="grid grid-cols-2 gap-x-6 gap-y-4 border-t pt-5 lg:grid-cols-4 lg:gap-x-8">
          <Item label={t('detail.partner')}>
            <span className="block truncate font-medium">{c.partner.name ?? c.partner.bpnl}</span>
            <span className="inline-flex items-center gap-1 font-mono text-xs text-muted-foreground">
              {c.partner.bpnl}
              <CopyButton value={c.partner.bpnl} label={t('common:copyBpnl')} />
            </span>
          </Item>
          <Item label={t('detail.partNumbers')}>
            <span className="grid grid-cols-[auto_1fr] gap-x-2 text-xs">
              <span className="text-muted-foreground">{t('detail.customerPartNumber')}</span>
              <span className="font-mono">{c.customerPartNumber ?? '–'}</span>
              <span className="text-muted-foreground">{t('detail.supplierPartNumber')}</span>
              <span className="font-mono">{c.supplierPartNumber ?? '–'}</span>
            </span>
          </Item>
          <Item label={t('detail.reason')}>{localized(c.reason, lng)}</Item>
          <Item label={t('detail.level')}>
            <span className="font-mono text-xs">{c.level.code}</span>
            <span className="block text-xs text-muted-foreground">{localized(c.level, lng)}</span>
          </Item>
          <Item label={t('detail.dueDate')}>
            <DueDate dueDate={c.dueDate} overdue={c.overdue} status={c.status} />
          </Item>
          <Item label={t('detail.revision')}>
            <span className="tabular">{c.currentRevision ?? '–'}</span>
          </Item>
          <Item label={t('detail.requestedAt')}>
            {c.requestedAt ? fmt.dateTime(c.requestedAt) : '–'}
            {c.request.requestedBy ? (
              <span className="block text-xs text-muted-foreground">{c.request.requestedBy}</span>
            ) : null}
          </Item>
          {c.externalReference ? (
            <Item label={t('detail.externalReference')}>
              <span className="font-mono text-xs">{c.externalReference}</span>
            </Item>
          ) : (
            <Item label={t('detail.exchangeId')}>
              <span className="block truncate font-mono text-[11px] text-muted-foreground" title={c.exchangeId}>
                {c.exchangeId}
              </span>
            </Item>
          )}
          {c.remarks ? (
            <Item label={t('detail.remarks')} wide>
              <p className="whitespace-pre-line">{c.remarks}</p>
            </Item>
          ) : null}
        </dl>
      </CardContent>
    </Card>
  )
}
