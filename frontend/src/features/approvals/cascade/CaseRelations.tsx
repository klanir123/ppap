import { Link } from '@tanstack/react-router'
import { CornerLeftUp, GitBranchPlus, Network, TriangleAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useCurrentUser } from '@/features/auth/current-user'

import type { ApprovalCaseDetail, ChildCaseRef } from '../api'
import { CaseStatusBadge } from '../components/badges'
import { blockingChildren, canRequestSubSupplier } from './cascade-model'

/** Breadcrumb-like link of a child case to the parent case (own request to a sub-supplier). */
export function ParentCaseLink({ approvalCase: c }: { approvalCase: ApprovalCaseDetail }) {
  const { t } = useTranslation('approvals')
  if (!c.parentCase) return null
  const parent = c.parentCase
  return (
    <nav
      aria-label={t('cascade.parent.label')}
      className="flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg border border-dashed bg-muted/30 px-3 py-2 text-sm"
      data-testid="parent-case-link"
    >
      <CornerLeftUp aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
      <span className="text-muted-foreground">{t('cascade.parent.prefix')}</span>
      <Link
        to="/approvals/$caseId"
        params={{ caseId: parent.id }}
        className="rounded-sm font-mono text-xs font-semibold underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
      >
        {parent.caseNumber}
      </Link>
      <span className="text-muted-foreground">
        {t('cascade.parent.customer', { partner: parent.partner.name ?? parent.partner.bpnl })}
      </span>
    </nav>
  )
}

/** "Unterlieferanten" panel: child cases with status and decision (+ request action for the supplier). */
export function SubSuppliersPanel({ approvalCase: c }: { approvalCase: ApprovalCaseDetail }) {
  const { t } = useTranslation('approvals')
  const me = useCurrentUser()
  const children = c.childCases ?? []
  const canRequest = canRequestSubSupplier(c, me.roles)
  if (children.length === 0 && !canRequest) return null
  const blocking = blockingChildren(children).length

  return (
    <Card className="h-fit gap-4" data-testid="sub-suppliers-panel">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <Network aria-hidden="true" className="size-4 text-muted-foreground" />
          {t('cascade.panel.title')}
        </CardTitle>
        <CardDescription className="text-xs">
          {children.length === 0
            ? t('cascade.panel.empty')
            : blocking > 0
              ? t('cascade.panel.blocking', { count: blocking })
              : t('cascade.panel.allDecided')}
        </CardDescription>
        {canRequest && children.length > 0 ? (
          <CardAction>
            <Button variant="ghost" size="icon-sm" asChild>
              <Link
                to="/approvals/new"
                search={{ parentId: c.id }}
                aria-label={t('cascade.request.action')}
                title={t('cascade.request.action')}
              >
                <GitBranchPlus aria-hidden="true" />
              </Link>
            </Button>
          </CardAction>
        ) : null}
      </CardHeader>
      <CardContent className="space-y-3">
        {children.length > 0 ? (
          <ul className="divide-y rounded-md border">
            {children.map((child) => (
              <ChildRow key={child.id} child={child} />
            ))}
          </ul>
        ) : null}
        {canRequest && children.length === 0 ? (
          <Button variant="outline" size="sm" className="w-full" asChild>
            <Link to="/approvals/new" search={{ parentId: c.id }}>
              <GitBranchPlus aria-hidden="true" />
              {t('cascade.request.action')}
            </Link>
          </Button>
        ) : null}
      </CardContent>
    </Card>
  )
}

function ChildRow({ child }: { child: ChildCaseRef }) {
  const { t } = useTranslation('approvals')
  const blocking = blockingChildren([child]).length > 0
  return (
    <li>
      <Link
        to="/approvals/$caseId"
        params={{ caseId: child.id }}
        className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 gap-y-1 px-3 py-2.5 hover:bg-muted/50 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-ring"
      >
        <span className="min-w-0">
          <span className="block font-mono text-xs font-semibold">{child.caseNumber}</span>
          <span className="block truncate text-xs text-muted-foreground">
            {[child.partNumber, child.partner.name ?? child.partner.bpnl].filter(Boolean).join(' · ')}
          </span>
        </span>
        <CaseStatusBadge status={child.lastDecisionResult ?? child.status} size="sm" />
        {blocking ? (
          <span className="col-span-2 inline-flex items-center gap-1 text-[11px] text-(--status-warning-fg)">
            <TriangleAlert aria-hidden="true" className="size-3" />
            {t('cascade.panel.openChild')}
          </span>
        ) : null}
      </Link>
    </li>
  )
}
