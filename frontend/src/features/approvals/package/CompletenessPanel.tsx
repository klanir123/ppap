import { CircleAlert, CircleCheck } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Progress } from '@/components/ui/progress'

import type { Completeness } from '../api'

interface CompletenessPanelProps {
  completeness: Completeness | undefined
  /** Element code → display name. */
  names: ReadonlyMap<string, string>
  onSelect: (code: string) => void
}

/** Completeness check of the draft (server issues). Clicking an issue jumps to the element. */
export function CompletenessPanel({ completeness, names, onSelect }: CompletenessPanelProps) {
  const { t } = useTranslation('approvals')
  if (!completeness) return null
  const { requiredDone, requiredTotal, issues, complete } = completeness
  const percent = requiredTotal ? Math.round((requiredDone / requiredTotal) * 100) : 100
  const grouped = new Map<string, typeof issues>()
  for (const issue of issues) grouped.set(issue.elementCode, [...(grouped.get(issue.elementCode) ?? []), issue])

  return (
    <section aria-labelledby="completeness-heading" className="space-y-3 rounded-lg border bg-card p-4">
      <div className="flex items-center justify-between gap-2">
        <h2 id="completeness-heading" className="text-sm font-semibold">
          {t('completeness.title')}
        </h2>
        <span className="text-xs text-muted-foreground tabular">
          {t('completeness.progress', { done: requiredDone, total: requiredTotal })}
        </span>
      </div>
      <Progress
        value={percent}
        aria-label={t('completeness.progressLabel', { percent })}
        indicatorClassName={complete ? 'bg-(--status-ok-solid)' : 'bg-participant'}
      />
      {complete && issues.length === 0 ? (
        <p role="status" className="flex items-center gap-2 text-sm text-(--status-ok-fg)">
          <CircleCheck aria-hidden="true" className="size-4" />
          {t('completeness.complete')}
        </p>
      ) : (
        <>
          <p className="flex items-center gap-2 text-xs text-muted-foreground">
            <CircleAlert aria-hidden="true" className="size-3.5 text-(--status-warning-solid)" />
            {t('completeness.openIssues', { count: issues.length })}
          </p>
          <ul className="max-h-64 space-y-1 relative overflow-y-auto" aria-label={t('completeness.issueList')}>
            {[...grouped].map(([code, list]) => (
              <li key={code}>
                <button
                  type="button"
                  onClick={() => onSelect(code)}
                  className="w-full rounded-md px-2 py-1.5 text-left hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring"
                >
                  <span className="flex items-baseline gap-2 text-xs">
                    <span className="font-mono text-muted-foreground">{code}</span>
                    <span className="truncate font-medium">{names.get(code) ?? ''}</span>
                  </span>
                  <span className="mt-0.5 block text-xs text-muted-foreground">
                    {list
                      .map((i) =>
                        t(`completeness.issues.${i.code}` as 'completeness.issues.MISSING_SELF_ASSESSMENT', {
                          defaultValue: i.message ?? i.code,
                        }),
                      )
                      .join(' · ')}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}
