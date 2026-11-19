import type { UseQueryResult } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { ChevronDown, PackageSearch, Pencil, TriangleAlert } from 'lucide-react'
import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { SearchInput } from '@/components/data-table/SearchInput'
import { EmptyState } from '@/components/EmptyState'
import { StatusBadge } from '@/components/StatusBadge'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { translateErrorCode } from '@/lib/problem-errors'
import { cn } from '@/lib/utils'

import type { SupplierPartOption, useCatalogue } from '../api'
import { ExchangeReadyBadge } from '../components/badges'
import { ChoiceCards } from '../components/ChoiceCards'
import { DocumentList, DocumentUpload } from '../components/Documents'
import { EvidenceTypeLabel } from '../components/ElementStateIcon'
import { localized, useFormat } from '../components/format'
import { REQUEST_STEPS, type ElementChoice, type RequestStep, type RequestValues } from './request-model'

export interface StepProps {
  values: RequestValues
  patch: (next: Partial<RequestValues>) => void
  errors: Record<string, string>
  options: UseQueryResult<SupplierPartOption[]>
  catalogue: ReturnType<typeof useCatalogue>
}

function useErrorText() {
  const { t } = useTranslation('approvals')
  return (code: string | undefined) =>
    code
      ? t(`wizard.errors.${code}` as 'wizard.errors.REQUIRED', { defaultValue: translateErrorCode(code) })
      : undefined
}

function FieldError({ id, text }: { id: string; text: string | undefined }) {
  if (!text) return null
  return (
    <p id={id} role="alert" className="text-xs font-medium text-destructive">
      {text}
    </p>
  )
}

// --- 1 part -------------------------------------------------------------------------------------

export function PartStep({ values, patch, errors, options }: StepProps) {
  const { t } = useTranslation('approvals')
  const errorText = useErrorText()
  const errorId = useId()
  const [q, setQ] = useState<string>()
  const needle = q?.toLowerCase()
  const list = (options.data ?? []).filter(
    (o) =>
      !needle ||
      o.partNumber.toLowerCase().includes(needle) ||
      o.partNameDe?.toLowerCase().includes(needle) ||
      o.partners.some((p) => p.partnerPartNumber.toLowerCase().includes(needle)),
  )

  if (options.isPending) return <Skeleton className="h-48 w-full" />
  if ((options.data ?? []).length === 0) {
    return (
      <EmptyState
        icon={PackageSearch}
        title={t('wizard.part.none')}
        description={t('wizard.part.noneDescription')}
        action={
          <Button variant="outline" size="sm" asChild>
            <Link to="/masterdata/relationships">{t('wizard.part.toRelations')}</Link>
          </Button>
        }
      />
    )
  }
  return (
    <div className="space-y-3">
      <SearchInput value={q} onCommit={setQ} placeholder={t('wizard.part.search')} />
      {list.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted-foreground">{t('wizard.part.noMatch')}</p>
      ) : (
        <div className="max-h-[26rem] relative overflow-y-auto pr-1">
          <ChoiceCards
            label={t('wizard.steps.part')}
            value={values.partId}
            invalid={Boolean(errors.partId)}
            describedBy={errors.partId ? errorId : undefined}
            onValueChange={(partId) => {
              const option = options.data?.find((o) => o.partId === partId)
              const only = option?.partners.length === 1 ? option.partners[0] : undefined
              patch({ partId, relationId: only?.relationId ?? '', partnerBpnl: only?.partnerBpnl ?? '' })
            }}
            choices={list.map((o) => ({
              value: o.partId,
              title: (
                <span className="flex flex-wrap items-baseline gap-x-2">
                  <span className="font-mono text-xs">{o.partNumber}</span>
                  <span>{o.partNameDe}</span>
                </span>
              ),
              description: o.partners
                .map((p) =>
                  t('wizard.part.supplierNumber', {
                    partner: p.partnerName ?? p.partnerBpnl,
                    number: p.partnerPartNumber,
                  }),
                )
                .join(' · '),
              aside: (
                <span className="text-xs text-muted-foreground">
                  {t('wizard.part.supplierCount', { count: o.partners.length })}
                </span>
              ),
            }))}
          />
        </div>
      )}
      <FieldError id={errorId} text={errorText(errors.partId)} />
    </div>
  )
}

// --- 2 supplier ---------------------------------------------------------------------------------

export function SupplierStep({ values, patch, errors, options }: StepProps) {
  const { t } = useTranslation('approvals')
  const errorText = useErrorText()
  const errorId = useId()
  const option = options.data?.find((o) => o.partId === values.partId)
  const selected = option?.partners.find((p) => p.relationId === values.relationId)
  if (!option) return <Skeleton className="h-32 w-full" />
  return (
    <div className="space-y-4">
      <ChoiceCards
        label={t('wizard.steps.supplier')}
        value={values.relationId}
        invalid={Boolean(errors.relationId)}
        describedBy={errors.relationId ? errorId : undefined}
        onValueChange={(relationId) =>
          patch({
            relationId,
            partnerBpnl: option.partners.find((p) => p.relationId === relationId)?.partnerBpnl ?? '',
          })
        }
        choices={option.partners.map((p) => ({
          value: p.relationId,
          title: p.partnerName ?? p.partnerBpnl,
          description: (
            <span className="flex flex-wrap gap-x-3">
              <span className="font-mono">{p.partnerBpnl}</span>
              <span>{t('wizard.supplier.partNumber', { number: p.partnerPartNumber })}</span>
            </span>
          ),
          aside: <ExchangeReadyBadge ready={p.exchangeReady} />,
        }))}
      />
      <FieldError id={errorId} text={errorText(errors.relationId)} />
      {selected && !selected.exchangeReady ? (
        <Alert>
          <TriangleAlert aria-hidden="true" />
          <AlertTitle>{t('wizard.supplier.notReadyTitle')}</AlertTitle>
          <AlertDescription>{t('wizard.supplier.notReadyDescription')}</AlertDescription>
        </Alert>
      ) : null}
    </div>
  )
}

// --- 3 reason & level ---------------------------------------------------------------------------

export function ReasonStep({ values, patch, errors, catalogue }: StepProps) {
  const { t, i18n } = useTranslation(['approvals', 'forms'])
  const errorText = useErrorText()
  const reasonId = useId()
  const levelErrorId = useId()
  const lng = i18n.resolvedLanguage
  if (catalogue.isPending) return <Skeleton className="h-48 w-full" />
  return (
    <div className="grid gap-6">
      <div className="grid max-w-md gap-2">
        <Label htmlFor={reasonId} className="gap-1">
          {t('wizard.reason.reason')}
          <span aria-hidden="true" className="text-destructive">
            *
          </span>
        </Label>
        <Select value={values.reasonCode || undefined} onValueChange={(reasonCode) => patch({ reasonCode })}>
          <SelectTrigger
            id={reasonId}
            className="w-full"
            aria-invalid={Boolean(errors.reasonCode) || undefined}
            aria-describedby={errors.reasonCode ? `${reasonId}-error` : undefined}
          >
            <SelectValue placeholder={t('forms:selectPlaceholder')} />
          </SelectTrigger>
          <SelectContent>
            {catalogue.reasons.map((r) => (
              <SelectItem key={r.code} value={r.code}>
                {localized(r, lng)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <FieldError id={`${reasonId}-error`} text={errorText(errors.reasonCode)} />
      </div>
      <fieldset className="grid gap-2">
        <legend className="mb-2 flex items-center gap-1 text-sm font-medium">
          {t('wizard.reason.level')}
          <span aria-hidden="true" className="text-destructive">
            *
          </span>
        </legend>
        <ChoiceCards
          label={t('wizard.reason.level')}
          value={values.levelCode}
          onValueChange={(levelCode) => patch({ levelCode })}
          invalid={Boolean(errors.levelCode)}
          describedBy={errors.levelCode ? levelErrorId : undefined}
          className="sm:grid-cols-2"
          choices={catalogue.levels.map((level) => {
            const counts = Object.values(level.requirements)
            return {
              value: level.code,
              title: localized(level, lng),
              description: t('wizard.reason.levelCounts', {
                required: counts.filter((r) => r === 'REQUIRED').length,
                onRequest: counts.filter((r) => r === 'ON_REQUEST').length,
              }),
            }
          })}
        />
        <FieldError id={levelErrorId} text={errorText(errors.levelCode)} />
      </fieldset>
    </div>
  )
}

// --- 4 elements ---------------------------------------------------------------------------------

export function ElementsStep({ values, patch, errors, catalogue }: StepProps) {
  const { t, i18n } = useTranslation('approvals')
  const errorText = useErrorText()
  const errorId = useId()
  const [showAll, setShowAll] = useState(false)
  const byCode = new Map(catalogue.elements.map((e) => [e.code, e]))
  const update = (code: string, change: Partial<ElementChoice>) =>
    patch({ elements: values.elements.map((e) => (e.code === code ? { ...e, ...change } : e)) })
  const inLevel = values.elements.filter((e) => e.levelRequirement !== 'NOT_REQUIRED' || e.selected)
  const others = values.elements.filter((e) => e.levelRequirement === 'NOT_REQUIRED' && !e.selected)
  const visible = showAll ? [...inLevel, ...others] : inLevel
  const selectedCount = values.elements.filter((e) => e.selected).length

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted-foreground" aria-live="polite">
          {t('wizard.elements.selected', { count: selectedCount })}
        </p>
        {others.length > 0 ? (
          <Button variant="ghost" size="sm" onClick={() => setShowAll((s) => !s)} aria-expanded={showAll}>
            <ChevronDown aria-hidden="true" className={cn('transition-transform', showAll && 'rotate-180')} />
            {showAll ? t('wizard.elements.hideOthers') : t('wizard.elements.showOthers', { count: others.length })}
          </Button>
        ) : null}
      </div>
      <div
        className="relative overflow-x-auto rounded-lg border"
        aria-describedby={errors.elements ? errorId : undefined}
      >
        <table className="w-full text-sm" aria-label={t('wizard.steps.elements')}>
          <thead className="bg-muted/60 text-xs text-muted-foreground">
            <tr>
              <th scope="col" className="w-10 px-3 py-2">
                <span className="sr-only">{t('wizard.elements.include')}</span>
              </th>
              <th scope="col" className="px-3 py-2 text-left font-medium">
                {t('elements.element')}
              </th>
              <th scope="col" className="px-3 py-2 text-left font-medium">
                {t('wizard.elements.matrix')}
              </th>
              <th scope="col" className="px-3 py-2 text-left font-medium">
                {t('elements.requirement')}
              </th>
              <th scope="col" className="min-w-56 px-3 py-2 text-left font-medium">
                {t('elements.note')}
              </th>
            </tr>
          </thead>
          <tbody>
            {visible.map((choice) => {
              const element = byCode.get(choice.code)
              const name = element ? localized(element, i18n.resolvedLanguage) : choice.code
              const checkboxId = `el-${choice.code}`
              return (
                <tr
                  key={choice.code}
                  className={cn('border-t align-middle', !choice.selected && 'text-muted-foreground')}
                >
                  <td className="px-3 py-2">
                    <Checkbox
                      id={checkboxId}
                      checked={choice.selected}
                      onCheckedChange={(checked) => update(choice.code, { selected: checked === true })}
                    />
                  </td>
                  <td className="px-3 py-2">
                    <label htmlFor={checkboxId} className="cursor-pointer">
                      <span className="font-mono text-xs text-muted-foreground">{choice.code}</span>
                      <span className={cn('block', choice.selected && 'font-medium text-foreground')}>{name}</span>
                    </label>
                    {element ? <EvidenceTypeLabel type={element.evidenceType} /> : null}
                  </td>
                  <td className="px-3 py-2">
                    <StatusBadge kind="requirement" status={choice.levelRequirement} size="sm" />
                  </td>
                  <td className="px-3 py-2">
                    <Select
                      value={choice.requirement}
                      onValueChange={(v) => update(choice.code, { requirement: v as ElementChoice['requirement'] })}
                      disabled={!choice.selected}
                    >
                      <SelectTrigger
                        size="sm"
                        className="w-40"
                        aria-label={t('wizard.elements.requirementFor', { name })}
                      >
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="REQUIRED">{t('wizard.elements.required')}</SelectItem>
                        <SelectItem value="ON_REQUEST">{t('wizard.elements.onRequest')}</SelectItem>
                      </SelectContent>
                    </Select>
                  </td>
                  <td className="px-3 py-2">
                    <Input
                      value={choice.note}
                      maxLength={2000}
                      disabled={!choice.selected}
                      placeholder={t('wizard.elements.notePlaceholder')}
                      aria-label={t('wizard.elements.noteFor', { name })}
                      onChange={(e) => update(choice.code, { note: e.target.value })}
                      className="h-8"
                    />
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <FieldError id={errorId} text={errorText(errors.elements)} />
    </div>
  )
}

// --- 5 schedule ---------------------------------------------------------------------------------

export function ScheduleStep({ values, patch, errors }: StepProps) {
  const { t } = useTranslation(['approvals', 'forms'])
  const errorText = useErrorText()
  const dueId = useId()
  const remarksId = useId()
  return (
    <div className="grid gap-6">
      <div className="grid max-w-xs gap-2">
        <Label htmlFor={dueId} className="gap-1">
          {t('wizard.schedule.dueDate')}
          <span aria-hidden="true" className="text-destructive">
            *
          </span>
        </Label>
        <Input
          id={dueId}
          type="date"
          value={values.dueDate}
          aria-required="true"
          aria-invalid={Boolean(errors.dueDate) || undefined}
          aria-describedby={errors.dueDate ? `${dueId}-error` : undefined}
          onChange={(e) => patch({ dueDate: e.target.value })}
        />
        <FieldError id={`${dueId}-error`} text={errorText(errors.dueDate)} />
      </div>
      <div className="grid gap-2">
        <Label htmlFor={remarksId}>
          {t('wizard.schedule.remarks')}{' '}
          <span className="font-normal text-muted-foreground">({t('forms:optional')})</span>
        </Label>
        <Textarea
          id={remarksId}
          rows={4}
          maxLength={4000}
          value={values.remarks}
          aria-invalid={Boolean(errors.remarks) || undefined}
          onChange={(e) => patch({ remarks: e.target.value })}
        />
        <FieldError id={`${remarksId}-error`} text={errorText(errors.remarks)} />
      </div>
      <div className="grid gap-2">
        <p className="text-sm font-medium">{t('wizard.schedule.attachments')}</p>
        <p className="text-xs text-muted-foreground">{t('wizard.schedule.attachmentsHint')}</p>
        <DocumentList
          documents={values.attachments}
          emptyText={t('wizard.schedule.noAttachments')}
          onRemove={(doc) => patch({ attachments: values.attachments.filter((d) => d.id !== doc.id) })}
        />
        <DocumentUpload
          compact
          onUploaded={(doc) => patch({ attachments: [...values.attachments.filter((d) => d.id !== doc.id), doc] })}
        />
      </div>
    </div>
  )
}

// --- 6 summary ----------------------------------------------------------------------------------

function SummaryRow({
  step,
  label,
  children,
  onEdit,
}: {
  step: RequestStep
  label: string
  children: React.ReactNode
  onEdit: (index: number) => void
}) {
  const { t } = useTranslation('approvals')
  return (
    <div className="flex items-start justify-between gap-3 border-t py-3 first:border-t-0">
      <div className="min-w-0 space-y-0.5">
        <dt className="text-xs text-muted-foreground">{label}</dt>
        <dd className="text-sm">{children}</dd>
      </div>
      <Button
        variant="ghost"
        size="icon-sm"
        onClick={() => onEdit(REQUEST_STEPS.indexOf(step))}
        aria-label={t('wizard.summary.edit', { section: label })}
      >
        <Pencil aria-hidden="true" />
      </Button>
    </div>
  )
}

export function SummaryStep({
  values,
  options,
  catalogue,
  exchangeReady,
  onEdit,
}: StepProps & { exchangeReady: boolean; onEdit: (index: number) => void }) {
  const { t, i18n } = useTranslation('approvals')
  const fmt = useFormat()
  const lng = i18n.resolvedLanguage
  const part = options.data?.find((o) => o.partId === values.partId)
  const partner = part?.partners.find((p) => p.relationId === values.relationId)
  const reason = catalogue.reasons.find((r) => r.code === values.reasonCode)
  const level = catalogue.levels.find((l) => l.code === values.levelCode)
  const selected = values.elements.filter((e) => e.selected)
  const names = new Map(catalogue.elements.map((e) => [e.code, localized(e, lng)]))

  return (
    <div className="space-y-4">
      {!exchangeReady ? (
        <Alert id="send-blocked-hint">
          <TriangleAlert aria-hidden="true" />
          <AlertTitle>{t('wizard.supplier.notReadyTitle')}</AlertTitle>
          <AlertDescription>{t('wizard.summary.sendBlocked')}</AlertDescription>
        </Alert>
      ) : null}
      <dl className="rounded-lg border px-4">
        <SummaryRow onEdit={onEdit} step="part" label={t('wizard.steps.part')}>
          <span className="font-mono text-xs">{part?.partNumber}</span> {part?.partNameDe}
        </SummaryRow>
        <SummaryRow onEdit={onEdit} step="supplier" label={t('wizard.steps.supplier')}>
          <span className="flex flex-wrap items-center gap-2">
            {partner?.partnerName ?? values.partnerBpnl}
            <span className="font-mono text-xs text-muted-foreground">{values.partnerBpnl}</span>
            {partner ? <ExchangeReadyBadge ready={partner.exchangeReady} /> : null}
          </span>
          {partner ? (
            <span className="block text-xs text-muted-foreground">
              {t('wizard.supplier.partNumber', { number: partner.partnerPartNumber })}
            </span>
          ) : null}
        </SummaryRow>
        <SummaryRow onEdit={onEdit} step="reason" label={t('wizard.summary.reasonLevel')}>
          {reason ? localized(reason, lng) : values.reasonCode} · {level ? localized(level, lng) : values.levelCode}
        </SummaryRow>
        <SummaryRow onEdit={onEdit} step="elements" label={t('wizard.steps.elements')}>
          {t('wizard.elements.selected', { count: selected.length })}
          <span className="mt-1 flex flex-wrap gap-1">
            {selected.map((e) => (
              <span
                key={e.code}
                title={names.get(e.code)}
                className={cn(
                  'rounded border px-1.5 py-0.5 font-mono text-[11px]',
                  e.requirement === 'ON_REQUEST' && 'border-dashed text-muted-foreground',
                )}
              >
                {e.code}
              </span>
            ))}
          </span>
        </SummaryRow>
        <SummaryRow onEdit={onEdit} step="schedule" label={t('wizard.summary.schedule')}>
          {t('wizard.summary.dueDate', { date: fmt.date(values.dueDate) })}
          {values.remarks ? (
            <span className="mt-1 block whitespace-pre-line text-muted-foreground">{values.remarks}</span>
          ) : null}
          <span className="mt-1 block text-xs text-muted-foreground">
            {t('wizard.summary.attachments', { count: values.attachments.length })}
          </span>
        </SummaryRow>
      </dl>
      <p className="text-xs text-muted-foreground">{t('wizard.summary.phaseHint')}</p>
    </div>
  )
}
