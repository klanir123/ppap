import { Check, Gavel, Plus, Trash2, X } from 'lucide-react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'

import { STATUS_ICONS } from '@/components/status-icons'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Textarea } from '@/components/ui/textarea'
import { statusStyle } from '@/lib/status-style'
import { cn } from '@/lib/utils'

import { DECISION_RESULTS, type DecisionResult } from '../api/types'
import { checkDecision, suggestResult, type DecisionDraft, type FindingDraft } from '../model/decision-rules'
import { caseStatusToken, todayIso } from '../model/status'

interface DecisionPanelProps {
  draft: DecisionDraft
  onChange: (draft: DecisionDraft) => void
  findings: Readonly<Record<string, FindingDraft | undefined>>
  elementCodes: readonly string[]
  names: ReadonlyMap<string, string>
  disabled?: boolean
  onDecide: () => void
  onSelectElement: (code: string) => void
}

/**
 * Overall decision: result with dynamic required fields; the rule checklist mirrors the server rules
 * (REJECTION_REASON_REQUIRED, CONDITIONS_REQUIRED, ELEMENTS_NOT_ACCEPTED).
 */
export function DecisionPanel({
  draft,
  onChange,
  findings,
  elementCodes,
  names,
  disabled,
  onDecide,
  onSelectElement,
}: DecisionPanelProps) {
  const { t } = useTranslation(['approvals', 'status'])
  const id = useId()
  const check = checkDecision(draft, findings, elementCodes, todayIso())
  const suggestion = suggestResult(elementCodes.map((c) => findings[c]))
  const set = (patch: Partial<DecisionDraft>) => onChange({ ...draft, ...patch })
  const conditions = draft.conditions.length ? draft.conditions : ['']

  return (
    <section aria-labelledby={`${id}-heading`} className="space-y-4 rounded-lg border bg-card p-4">
      <div className="space-y-1">
        <h2 id={`${id}-heading`} className="flex items-center gap-2 text-sm font-semibold">
          <Gavel aria-hidden="true" className="size-4 text-muted-foreground" />
          {t('decisionPanel.title')}
        </h2>
        {suggestion ? (
          <p className="text-xs text-muted-foreground">
            {t('decisionPanel.suggestion', { result: t(`status:approval.${caseStatusToken(suggestion)}`) })}
          </p>
        ) : null}
      </div>

      <RadioGroup
        value={draft.result ?? ''}
        onValueChange={(v) => set({ result: v as DecisionResult })}
        aria-label={t('decisionPanel.result')}
        disabled={disabled}
        className="gap-2"
      >
        {DECISION_RESULTS.map((result) => {
          const token = caseStatusToken(result)
          const Icon = STATUS_ICONS.approval[token]
          const itemId = `${id}-${result}`
          return (
            <Label
              key={result}
              htmlFor={itemId}
              style={statusStyle(token)}
              className={cn(
                'flex cursor-pointer items-center gap-3 rounded-md border px-3 py-2.5 font-normal transition-colors hover:bg-muted/40',
                draft.result === result && 'border-(--sb-border) bg-(--sb-bg) text-(--sb-fg)',
              )}
            >
              <RadioGroupItem id={itemId} value={result} />
              <Icon aria-hidden="true" className="size-4" />
              <span className="text-sm font-medium">{t(`decisionPanel.results.${result}`)}</span>
            </Label>
          )
        })}
      </RadioGroup>

      {draft.result === 'CONDITIONALLY_APPROVED' ? (
        <fieldset className="space-y-3 rounded-md border p-3" disabled={disabled}>
          <legend className="px-1 text-sm font-medium">{t('decisionPanel.conditions')}</legend>
          <ol className="space-y-2">
            {conditions.map((text, index) => (
              <li key={index} className="flex items-center gap-2">
                <span className="w-5 text-right text-xs text-muted-foreground tabular">{index + 1}.</span>
                <Input
                  value={text}
                  aria-label={t('decisionPanel.conditionLabel', { index: index + 1 })}
                  onChange={(e) => set({ conditions: conditions.map((c, i) => (i === index ? e.target.value : c)) })}
                  className="h-8"
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  aria-label={t('decisionPanel.removeCondition', { index: index + 1 })}
                  disabled={conditions.length === 1}
                  onClick={() => set({ conditions: conditions.filter((_, i) => i !== index) })}
                >
                  <Trash2 aria-hidden="true" />
                </Button>
              </li>
            ))}
          </ol>
          <Button type="button" variant="outline" size="sm" onClick={() => set({ conditions: [...conditions, ''] })}>
            <Plus aria-hidden="true" />
            {t('decisionPanel.addCondition')}
          </Button>
          <div className="grid max-w-48 gap-1.5">
            <Label htmlFor={`${id}-due`} className="gap-1">
              {t('decisionPanel.conditionDueDate')}
              <span aria-hidden="true" className="text-destructive">
                *
              </span>
            </Label>
            <Input
              id={`${id}-due`}
              type="date"
              min={todayIso()}
              value={draft.conditionDueDate ?? ''}
              onChange={(e) => set({ conditionDueDate: e.target.value || undefined })}
              className="h-8"
            />
          </div>
        </fieldset>
      ) : null}

      <div className="grid gap-1.5">
        <Label htmlFor={`${id}-comment`}>{t('decisionPanel.comment')}</Label>
        <Textarea
          id={`${id}-comment`}
          rows={3}
          value={draft.comment ?? ''}
          disabled={disabled}
          onChange={(e) => set({ comment: e.target.value })}
        />
      </div>

      {draft.result ? (
        <ul className="space-y-1" aria-label={t('decisionPanel.checks')} data-testid="decision-checks">
          {check.errors.length === 0 ? (
            <li className="flex items-center gap-2 text-xs text-(--status-ok-fg)">
              <Check aria-hidden="true" className="size-3.5" />
              {t('decisionPanel.ready')}
            </li>
          ) : (
            check.errors.map((e, i) => (
              <li key={i} className="flex items-start gap-2 text-xs text-destructive">
                <X aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
                {e.elementCode ? (
                  <button
                    type="button"
                    className="text-left underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
                    onClick={() => onSelectElement(e.elementCode!)}
                  >
                    {t(`decisionPanel.rules.${e.code}`, {
                      element: `${e.elementCode} ${names.get(e.elementCode) ?? ''}`.trim(),
                    })}
                  </button>
                ) : (
                  <span>{t(`decisionPanel.rules.${e.code}`, { element: '' })}</span>
                )}
              </li>
            ))
          )}
          {check.unreviewed.length > 0 ? (
            <li className="text-xs text-muted-foreground">
              {t('decisionPanel.unreviewed', { count: check.unreviewed.length })}
            </li>
          ) : null}
        </ul>
      ) : null}

      <Button className="w-full" onClick={onDecide} disabled={disabled || !check.valid}>
        <Gavel aria-hidden="true" />
        {t('decisionPanel.decide')}
      </Button>
    </section>
  )
}
