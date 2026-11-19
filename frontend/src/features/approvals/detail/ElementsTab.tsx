import { useTranslation } from 'react-i18next'

import { StatusBadge } from '@/components/StatusBadge'
import { Skeleton } from '@/components/ui/skeleton'

import { useSubmission, type ApprovalCaseDetail } from '../api'
import { DocumentList } from '../components/Documents'
import { ElementStateIcon, EvidenceTypeLabel } from '../components/ElementStateIcon'
import { localized } from '../components/format'
import { elementState, issuesByElement } from '../model/elements'
import { currentRevisionRef } from '../model/submissions'

/** Requested elements with requirement, evidence type, note and the status in the current revision. */
export function ElementsTab({ approvalCase: c }: { approvalCase: ApprovalCaseDetail }) {
  const { t, i18n } = useTranslation('approvals')
  const revision = currentRevisionRef(c)
  const submission = useSubmission(c.id, revision)
  const byCode = new Map((submission.data?.elements ?? []).map((e) => [e.code, e]))
  const issues = issuesByElement(submission.data?.completeness)
  const completeness = submission.data?.completeness

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
        <p className="text-muted-foreground">
          {t('elements.summary', {
            count: c.request.elements.length,
            required: c.request.elements.filter((e) => e.requirement === 'REQUIRED').length,
          })}
        </p>
        {completeness && revision === 'draft' ? (
          <p className="text-muted-foreground tabular">
            {t('elements.progress', { done: completeness.requiredDone, total: completeness.requiredTotal })}
          </p>
        ) : null}
      </div>
      <div className="relative overflow-x-auto rounded-lg border">
        <table className="w-full text-sm" aria-label={t('tabs.elements')}>
          <thead className="bg-muted/60 text-xs text-muted-foreground">
            <tr>
              <th scope="col" className="px-3 py-2 text-left font-medium">
                {t('elements.element')}
              </th>
              <th scope="col" className="px-3 py-2 text-left font-medium">
                {t('elements.requirement')}
              </th>
              <th scope="col" className="hidden px-3 py-2 text-left font-medium md:table-cell">
                {t('elements.evidenceType')}
              </th>
              <th scope="col" className="px-3 py-2 text-left font-medium">
                {revision === undefined ? t('elements.note') : t('elements.currentStatus')}
              </th>
            </tr>
          </thead>
          <tbody>
            {c.request.elements.map((el) => {
              const item = byCode.get(el.code)
              const count = issues.get(el.code)?.length ?? item?.issues?.length ?? 0
              return (
                <tr key={el.code} className="border-t align-top">
                  <td className="px-3 py-2.5">
                    <span className="font-mono text-xs text-muted-foreground">{el.code}</span>
                    <span className="block font-medium">{localized(el.snapshot, i18n.resolvedLanguage)}</span>
                    {el.note && revision !== undefined ? (
                      <span className="mt-0.5 block text-xs text-muted-foreground">{el.note}</span>
                    ) : null}
                  </td>
                  <td className="px-3 py-2.5">
                    <StatusBadge kind="requirement" status={el.requirement} size="sm" />
                  </td>
                  <td className="hidden px-3 py-2.5 md:table-cell">
                    <EvidenceTypeLabel type={el.snapshot.evidenceType} />
                  </td>
                  <td className="px-3 py-2.5">
                    {revision === undefined ? (
                      <span className="text-xs text-muted-foreground">{el.note || '–'}</span>
                    ) : submission.isPending ? (
                      <Skeleton className="h-5 w-24" />
                    ) : (
                      <span className="flex flex-wrap items-center gap-2">
                        <ElementStateIcon state={elementState(el.requirement, item, count)} />
                        {item?.selfAssessment ? (
                          <StatusBadge kind="assessment" status={item.selfAssessment} size="sm" />
                        ) : (
                          <span className="text-xs text-muted-foreground">{t('elements.notAssessed')}</span>
                        )}
                        {item && item.documents.length > 0 ? (
                          <span className="text-xs text-muted-foreground">
                            {t('elements.documentCount', { count: item.documents.length })}
                          </span>
                        ) : null}
                      </span>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      {c.request.attachments.length > 0 ? (
        <section className="space-y-2 pt-3" aria-labelledby="request-attachments">
          <div>
            <h3 id="request-attachments" className="text-sm font-medium">
              {t('elements.attachments')}
            </h3>
            <p className="text-xs text-muted-foreground">{t('elements.attachmentsHint')}</p>
          </div>
          <DocumentList documents={c.request.attachments} caseId={c.id} />
        </section>
      ) : null}
    </div>
  )
}
