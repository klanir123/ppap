import { useTranslation } from 'react-i18next'

import { StatusBadge } from '@/components/StatusBadge'

import type { ApprovalCaseDetail, Decision } from '../api'
import { caseStatusToken } from '../model/status'
import { localized, useFormat } from './format'
import { IntegrityInfo } from './RevisionView'

/** Decision with result, comment, conditions, element findings and integrity data. */
export function DecisionView({ approvalCase, decision }: { approvalCase: ApprovalCaseDetail; decision: Decision }) {
  const { t, i18n } = useTranslation('approvals')
  const fmt = useFormat()
  const names = new Map(
    approvalCase.request.elements.map((e) => [e.code, localized(e.snapshot, i18n.resolvedLanguage)]),
  )
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-3">
        <StatusBadge kind="approval" status={caseStatusToken(decision.result)} />
        <span className="text-sm text-muted-foreground">
          {t('decision.decidedBy', {
            revision: decision.revision,
            at: fmt.dateTime(decision.decidedAt),
            by: decision.decidedBy,
          })}
        </span>
      </div>
      {decision.comment ? <p className="text-sm whitespace-pre-line">{decision.comment}</p> : null}
      {decision.conditions.length > 0 ? (
        <section aria-labelledby="decision-conditions" className="space-y-2 rounded-lg border p-4">
          <h3 id="decision-conditions" className="text-sm font-semibold">
            {t('decision.conditions')}
            {decision.conditionDueDate ? (
              <span className="ml-2 text-xs font-normal text-muted-foreground">
                {t('decision.conditionDueDate', { date: fmt.date(decision.conditionDueDate) })}
              </span>
            ) : null}
          </h3>
          <ol className="list-decimal space-y-1 pl-5 text-sm">
            {decision.conditions.map((c, i) => (
              <li key={i}>{c.text}</li>
            ))}
          </ol>
        </section>
      ) : null}
      {decision.elementFindings.length > 0 ? (
        <div className="relative overflow-x-auto rounded-lg border">
          <table className="w-full text-sm" aria-label={t('decision.findings')}>
            <thead className="bg-muted/60 text-xs text-muted-foreground">
              <tr>
                <th scope="col" className="px-3 py-2 text-left font-medium">
                  {t('elements.element')}
                </th>
                <th scope="col" className="px-3 py-2 text-left font-medium">
                  {t('decision.finding')}
                </th>
                <th scope="col" className="px-3 py-2 text-left font-medium">
                  {t('decision.findingComment')}
                </th>
              </tr>
            </thead>
            <tbody>
              {decision.elementFindings.map((f) => (
                <tr key={f.code} className="border-t align-top">
                  <td className="px-3 py-2">
                    <span className="font-mono text-xs text-muted-foreground">{f.code}</span>
                    <span className="block">{names.get(f.code) ?? ''}</span>
                  </td>
                  <td className="px-3 py-2">
                    <StatusBadge kind="finding" status={f.result} size="sm" />
                  </td>
                  <td className="px-3 py-2 text-muted-foreground">{f.comment || '–'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      <IntegrityInfo
        caseNumber={approvalCase.caseNumber}
        caseId={approvalCase.id}
        submission={{ revision: decision.revision, payloadHash: decision.payloadHash, signature: decision.signature }}
        kind="decision"
      />
    </div>
  )
}
