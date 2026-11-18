import {
  CheckCircle2,
  Download,
  FileSpreadsheet,
  Loader2,
  RefreshCw,
  TriangleAlert,
  Upload,
  XCircle,
} from 'lucide-react'
import { useId, useMemo, useRef, useState, type DragEvent } from 'react'
import { useTranslation } from 'react-i18next'

import { isApiError } from '@/api/problem'
import { StatusBadge } from '@/components/StatusBadge'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { translateErrorCode, translateFieldError } from '@/lib/problem-errors'
import { cn } from '@/lib/utils'

import { assignField, missingRequiredFields, sameMapping, targetFieldLabel } from './mapping'
import {
  ACCEPTED_EXTENSIONS,
  MAX_IMPORT_BYTES,
  type ColumnMapping,
  type ImportEntity,
  type ImportHandlers,
  type ImportPreview,
  type ImportResult,
} from './types'

const IGNORE = '__ignore__'
type Step = 'upload' | 'preview' | 'result'
const STEPS: Step[] = ['upload', 'preview', 'result']
type ReportFilter = 'all' | 'ERROR' | 'WARNING'

export interface ImportWizardProps extends ImportHandlers {
  open: boolean
  onOpenChange: (open: boolean) => void
  entity: ImportEntity
  /** Translated entity name for the title, e.g. "Teile". */
  entityLabel: string
  /** Called after a successful import (e.g. to refresh lists). */
  onCompleted?: (result: ImportResult) => void
}

function formatBytes(bytes: number, lng?: string) {
  const fmt = new Intl.NumberFormat(lng, { maximumFractionDigits: 1 })
  if (bytes < 1024) return `${fmt.format(bytes)} B`
  if (bytes < 1024 * 1024) return `${fmt.format(bytes / 1024)} KB`
  return `${fmt.format(bytes / 1024 / 1024)} MB`
}

function isResult(value: unknown): value is ImportResult {
  return typeof value === 'object' && value !== null && 'failures' in value && 'failed' in value
}

/**
 * Three-step import: file → mapping & validation report → execute & result.
 * Generic over the entity; all server calls come in as callbacks.
 */
export function ImportWizard(props: ImportWizardProps) {
  const { open, onOpenChange, entityLabel } = props
  const { t } = useTranslation('import')
  const [session, setSession] = useState(0)

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        onOpenChange(next)
        if (!next) setSession((s) => s + 1)
      }}
    >
      <DialogContent className="flex max-h-[92svh] flex-col gap-0 p-0 sm:max-w-4xl">
        <DialogHeader className="border-b px-6 py-4">
          <DialogTitle>{t('title', { entity: entityLabel })}</DialogTitle>
          <DialogDescription>{t('description')}</DialogDescription>
        </DialogHeader>
        <WizardBody key={session} {...props} onRestart={() => setSession((s) => s + 1)} />
      </DialogContent>
    </Dialog>
  )
}

function WizardBody({
  entity,
  preview,
  execute,
  errorReport,
  onOpenChange,
  onCompleted,
  onRestart,
}: ImportWizardProps & { onRestart: () => void }) {
  const { t, i18n: i18nInstance } = useTranslation('import')
  const inputId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [step, setStep] = useState<Step>('upload')
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const [dragOver, setDragOver] = useState(false)
  const [busy, setBusy] = useState<'preview' | 'execute' | 'report' | null>(null)
  const [previewData, setPreviewData] = useState<ImportPreview | null>(null)
  const [previewError, setPreviewError] = useState<unknown>(null)
  const [mapping, setMapping] = useState<ColumnMapping>({})
  const [validatedMapping, setValidatedMapping] = useState<ColumnMapping>({})
  const [filter, setFilter] = useState<ReportFilter>('all')
  const [skipErrors, setSkipErrors] = useState(false)
  const [result, setResult] = useState<{ data: ImportResult; rejected: boolean } | null>(null)
  const [executeError, setExecuteError] = useState<unknown>(null)

  function selectFile(candidate: File | undefined) {
    setFileError(null)
    setFile(null)
    if (!candidate) return
    const name = candidate.name.toLowerCase()
    if (!ACCEPTED_EXTENSIONS.some((ext) => name.endsWith(ext))) return setFileError(t('errors.unsupported'))
    if (candidate.size > MAX_IMPORT_BYTES) return setFileError(t('errors.tooLarge'))
    setFile(candidate)
  }

  async function runPreview(withMapping?: ColumnMapping) {
    if (!file) return
    setBusy('preview')
    setPreviewError(null)
    try {
      const data = await preview(file, withMapping)
      const effective: ColumnMapping = {}
      for (const column of data.columns) effective[column] = (withMapping ?? data.suggestedMapping)[column] ?? null
      setPreviewData(data)
      setMapping(effective)
      setValidatedMapping(effective)
      setStep('preview')
    } catch (error) {
      setPreviewError(error)
    } finally {
      setBusy(null)
    }
  }

  async function runExecute() {
    if (!file) return
    setBusy('execute')
    setExecuteError(null)
    try {
      const data = await execute(file, mapping, skipErrors)
      setResult({ data, rejected: false })
      setStep('result')
      onCompleted?.(data)
    } catch (error) {
      // 422: nothing imported, same report in the problem body
      if (isApiError(error) && error.status === 422 && isResult(error.problem)) {
        setResult({ data: error.problem, rejected: true })
        setStep('result')
      } else {
        setExecuteError(error)
      }
    } finally {
      setBusy(null)
    }
  }

  async function downloadReport() {
    if (!file) return
    setBusy('report')
    try {
      const blob = await errorReport(file, mapping)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${entity}-error-report.csv`
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
    } catch {
      setExecuteError(new Error(t('downloadFailed')))
    } finally {
      setBusy(null)
    }
  }

  const missing = previewData ? missingRequiredFields(mapping, previewData.targetFields) : []
  const mappingChanged = previewData ? !sameMapping(mapping, validatedMapping) : false
  const summary = previewData?.summary
  const hasErrors = (summary?.errors ?? 0) > 0
  const importable = (summary?.ok ?? 0) + (summary?.warnings ?? 0)
  const canExecute =
    Boolean(previewData) && !mappingChanged && missing.length === 0 && importable > 0 && (!hasErrors || skipErrors)

  const visibleRows = useMemo(
    () => (previewData?.rows ?? []).filter((r) => filter === 'all' || r.status === filter),
    [previewData, filter],
  )
  const mappedFields = previewData
    ? previewData.targetFields.filter((f) => Object.values(validatedMapping).includes(f.name)).slice(0, 3)
    : []
  const stepIndex = STEPS.indexOf(step)

  return (
    <>
      <ol
        className="flex items-center gap-2 border-b px-6 py-3 text-xs"
        aria-label={t('stepLabel', { current: stepIndex + 1, total: STEPS.length, name: t(`steps.${step}`) })}
      >
        {STEPS.map((s, i) => (
          <li key={s} aria-current={s === step ? 'step' : undefined} className="flex items-center gap-2">
            <span
              className={cn(
                'flex size-5 items-center justify-center rounded-full border text-[11px] font-semibold tabular',
                i < stepIndex && 'border-participant bg-participant text-participant-foreground',
                i === stepIndex && 'border-participant text-participant',
                i > stepIndex && 'text-muted-foreground',
              )}
            >
              {i + 1}
            </span>
            <span className={cn(i === stepIndex ? 'font-medium text-foreground' : 'text-muted-foreground')}>
              {t(`steps.${s}`)}
            </span>
            {i < STEPS.length - 1 ? <span aria-hidden="true" className="mx-1 h-px w-6 bg-border" /> : null}
          </li>
        ))}
      </ol>

      <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5">
        {step === 'upload' ? (
          <div className="space-y-4">
            <div
              onDragOver={(e: DragEvent) => {
                e.preventDefault()
                setDragOver(true)
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e: DragEvent) => {
                e.preventDefault()
                setDragOver(false)
                selectFile(e.dataTransfer.files[0])
              }}
              className={cn(
                'flex flex-col items-center justify-center gap-3 rounded-lg border-2 border-dashed px-6 py-12 text-center transition-colors',
                dragOver ? 'border-participant bg-participant-soft/50' : 'border-border',
              )}
            >
              <div className="flex size-11 items-center justify-center rounded-full bg-muted text-muted-foreground">
                <Upload aria-hidden="true" className="size-5" />
              </div>
              <div className="space-y-1">
                <p className="text-sm font-medium">{t('drop.title')}</p>
                <p className="text-xs text-muted-foreground">{t('drop.hint')}</p>
              </div>
              <input
                ref={inputRef}
                id={inputId}
                type="file"
                accept={`${ACCEPTED_EXTENSIONS.join(',')},text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`}
                className="sr-only"
                aria-label={t('drop.browse')}
                onChange={(e) => selectFile(e.target.files?.[0])}
              />
              <Button type="button" variant="outline" size="sm" onClick={() => inputRef.current?.click()}>
                {t('drop.browse')}
              </Button>
            </div>
            {file ? (
              <p className="flex items-center gap-2 text-sm" aria-live="polite">
                <FileSpreadsheet aria-hidden="true" className="size-4 text-muted-foreground" />
                {t('drop.selected', { name: file.name, size: formatBytes(file.size, i18nInstance.resolvedLanguage) })}
              </p>
            ) : null}
            {fileError ? (
              <p role="alert" className="text-sm font-medium text-destructive">
                {fileError}
              </p>
            ) : null}
            {previewError ? <ProblemAlert title={t('errors.previewFailed')} error={previewError} /> : null}
          </div>
        ) : null}

        {step === 'preview' && previewData && summary ? (
          <div className="space-y-6">
            <div className="flex flex-wrap items-center gap-2">
              <p className="mr-2 text-xs text-muted-foreground">
                {t('mapping.fileInfo', {
                  file: previewData.fileName,
                  sheet: previewData.sheet ?? '–',
                  delimiter: previewData.delimiter ?? '–',
                })}
              </p>
              <SummaryChips summary={summary} />
            </div>

            <section aria-labelledby={`${inputId}-mapping`} className="space-y-3">
              <div>
                <h3 id={`${inputId}-mapping`} className="text-sm font-semibold">
                  {t('mapping.title')}
                </h3>
                <p className="text-xs text-muted-foreground">{t('mapping.description')}</p>
              </div>
              <div className="overflow-x-auto rounded-md border">
                <table className="w-full text-sm">
                  <thead className="bg-muted/60 text-xs text-muted-foreground">
                    <tr>
                      <th scope="col" className="px-3 py-2 text-left font-medium">
                        {t('mapping.column')}
                      </th>
                      <th scope="col" className="px-3 py-2 text-left font-medium">
                        {t('mapping.field')}
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {previewData.columns.map((column) => {
                      const current = mapping[column] ?? null
                      const usedElsewhere = new Set(
                        Object.entries(mapping)
                          .filter(([c, f]) => c !== column && f)
                          .map(([, f]) => f),
                      )
                      return (
                        <tr key={column}>
                          <td className="px-3 py-2 font-mono text-xs">{column}</td>
                          <td className="px-3 py-1.5">
                            <Select
                              value={current ?? IGNORE}
                              onValueChange={(v) => setMapping((m) => assignField(m, column, v === IGNORE ? null : v))}
                            >
                              <SelectTrigger
                                size="sm"
                                className="w-full sm:w-72"
                                aria-label={t('mapping.selectFor', { column })}
                              >
                                <SelectValue />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value={IGNORE}>{t('mapping.ignore')}</SelectItem>
                                {previewData.targetFields.map((f) => (
                                  <SelectItem key={f.name} value={f.name} disabled={usedElsewhere.has(f.name)}>
                                    {targetFieldLabel(entity, f)}
                                    {f.required ? ' *' : ''}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <p
                  role="status"
                  className={cn(
                    'text-xs',
                    missing.length > 0 ? 'font-medium text-destructive' : 'text-muted-foreground',
                  )}
                >
                  {missing.length > 0
                    ? t('mapping.missingRequired', {
                        fields: missing.map((f) => targetFieldLabel(entity, f)).join(', '),
                      })
                    : mappingChanged
                      ? t('mapping.changed')
                      : t('mapping.allRequired')}
                </p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={!mappingChanged || busy !== null}
                  onClick={() => void runPreview(mapping)}
                >
                  {busy === 'preview' ? (
                    <Loader2 aria-hidden="true" className="animate-spin" />
                  ) : (
                    <RefreshCw aria-hidden="true" />
                  )}
                  {t('mapping.revalidate')}
                </Button>
              </div>
              {previewError ? <ProblemAlert title={t('errors.previewFailed')} error={previewError} /> : null}
            </section>

            <section aria-labelledby={`${inputId}-report`} className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 id={`${inputId}-report`} className="text-sm font-semibold">
                  {t('report.title')}
                </h3>
                <ToggleGroup
                  type="single"
                  size="sm"
                  variant="outline"
                  value={filter}
                  onValueChange={(v) => v && setFilter(v as ReportFilter)}
                  aria-label={t('report.title')}
                >
                  <ToggleGroupItem value="all">{t('report.filterAll')}</ToggleGroupItem>
                  <ToggleGroupItem value="ERROR">{t('report.filterErrors')}</ToggleGroupItem>
                  <ToggleGroupItem value="WARNING">{t('report.filterWarnings')}</ToggleGroupItem>
                </ToggleGroup>
              </div>
              <div className="max-h-80 overflow-auto rounded-md border">
                <table className="w-full text-sm" aria-label={t('report.title')}>
                  <thead className="sticky top-0 bg-muted text-xs text-muted-foreground">
                    <tr>
                      <th scope="col" className="px-3 py-2 text-left font-medium">
                        {t('report.row')}
                      </th>
                      <th scope="col" className="px-3 py-2 text-left font-medium">
                        {t('report.status')}
                      </th>
                      <th scope="col" className="px-3 py-2 text-left font-medium">
                        {t('report.action')}
                      </th>
                      <th scope="col" className="px-3 py-2 text-left font-medium">
                        {t('report.values')}
                      </th>
                      <th scope="col" className="px-3 py-2 text-left font-medium">
                        {t('report.messages')}
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {visibleRows.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="px-3 py-6 text-center text-sm text-muted-foreground">
                          {t('report.noRows')}
                        </td>
                      </tr>
                    ) : (
                      visibleRows.map((row) => (
                        <tr key={row.rowNumber} className="align-top">
                          <td className="px-3 py-2 font-mono text-xs tabular">{row.rowNumber}</td>
                          <td className="px-3 py-2">
                            <StatusBadge kind="validation" status={row.status} size="sm" />
                          </td>
                          <td className="px-3 py-2 text-xs whitespace-nowrap">
                            {row.action ? t(`action.${row.action}`) : '–'}
                          </td>
                          <td className="px-3 py-2 text-xs">
                            {mappedFields.map((f) => (
                              <span key={f.name} className="mr-3 inline-block">
                                <span className="text-muted-foreground">{targetFieldLabel(entity, f)}: </span>
                                <span className="font-mono">{String(row.values[f.name] ?? '–')}</span>
                              </span>
                            ))}
                          </td>
                          <td className="px-3 py-2 text-xs">
                            <ul className="space-y-0.5">
                              {row.messages.map((m, i) => (
                                <li key={i}>
                                  {m.field ? (
                                    <span className="font-medium">{targetFieldLabel(entity, { name: m.field })}: </span>
                                  ) : null}
                                  {translateFieldError(m.code, m.field, m.message)}
                                </li>
                              ))}
                            </ul>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
              {previewData.rows.length < summary.total ? (
                <p className="text-xs text-muted-foreground">
                  {t('report.truncated', { shown: previewData.rows.length, total: summary.total })}
                </p>
              ) : null}
            </section>

            {hasErrors ? (
              <div className="space-y-2 rounded-md border border-(--status-error-border) bg-(--status-error-bg) p-3 text-(--status-error-fg)">
                <p className="text-xs">{t('skipErrorsRequired')}</p>
                <div className="flex items-center gap-2">
                  <Checkbox
                    id={`${inputId}-skip`}
                    checked={skipErrors}
                    onCheckedChange={(v) => setSkipErrors(v === true)}
                  />
                  <Label htmlFor={`${inputId}-skip`} className="text-sm font-medium">
                    {t('skipErrors')}
                  </Label>
                </div>
              </div>
            ) : null}
            {executeError ? <ProblemAlert title={t('result.titleFailed')} error={executeError} /> : null}
          </div>
        ) : null}

        {step === 'result' && result ? (
          <div className="space-y-5">
            <div className="flex items-start gap-3">
              {result.rejected ? (
                <XCircle aria-hidden="true" className="mt-0.5 size-6 text-(--status-error-solid)" />
              ) : (
                <CheckCircle2 aria-hidden="true" className="mt-0.5 size-6 text-(--status-ok-solid)" />
              )}
              <div>
                <h3 className="font-semibold" role="status">
                  {result.rejected ? t('result.titleFailed') : t('result.title')}
                </h3>
                {result.rejected ? (
                  <p className="text-sm text-muted-foreground">{t('result.descriptionFailed')}</p>
                ) : null}
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Chip tone="ok">{t('summary.create', { count: result.data.created })}</Chip>
              <Chip tone="ok">{t('summary.update', { count: result.data.updated })}</Chip>
              <Chip tone="neutral">{t('summary.unchanged', { count: result.data.unchanged })}</Chip>
              <Chip tone={result.data.failed > 0 ? 'error' : 'neutral'}>
                {t('summary.failed', { count: result.data.failed })}
              </Chip>
            </div>
            {result.data.failures.length > 0 ? (
              <section className="space-y-2">
                <h4 className="text-sm font-medium">{t('result.failures')}</h4>
                <ul className="max-h-60 divide-y overflow-auto rounded-md border text-xs">
                  {result.data.failures.map((f) => (
                    <li key={f.rowNumber} className="flex gap-3 px-3 py-2">
                      <span className="font-mono tabular">
                        {t('report.row')} {f.rowNumber}
                      </span>
                      <span>
                        {f.messages
                          .map(
                            (m) =>
                              `${m.field ? `${targetFieldLabel(entity, { name: m.field })}: ` : ''}${translateFieldError(m.code, m.field, m.message)}`,
                          )
                          .join(' · ')}
                      </span>
                    </li>
                  ))}
                </ul>
              </section>
            ) : null}
            {executeError ? <ProblemAlert title={t('downloadFailed')} error={executeError} /> : null}
          </div>
        ) : null}
      </div>

      <DialogFooter className="mx-0 mb-0 flex-col-reverse gap-2 border-t px-6 py-4 sm:flex-row sm:justify-between">
        <div>
          {step === 'preview' ? (
            <Button type="button" variant="ghost" onClick={() => setStep('upload')} disabled={busy !== null}>
              {t('back')}
            </Button>
          ) : null}
          {step === 'result' && (result?.data.failed ?? 0) > 0 ? (
            <Button type="button" variant="outline" onClick={() => void downloadReport()} disabled={busy !== null}>
              {busy === 'report' ? (
                <Loader2 aria-hidden="true" className="animate-spin" />
              ) : (
                <Download aria-hidden="true" />
              )}
              {t('downloadReport')}
            </Button>
          ) : null}
        </div>
        <div className="flex flex-col-reverse gap-2 sm:flex-row">
          {step === 'upload' ? (
            <Button type="button" disabled={!file || busy !== null} onClick={() => void runPreview()}>
              {busy === 'preview' ? <Loader2 aria-hidden="true" className="animate-spin" /> : null}
              {busy === 'preview' ? t('analyzing') : t('analyze')}
            </Button>
          ) : null}
          {step === 'preview' ? (
            <>
              {hasErrors ? (
                <Button type="button" variant="outline" onClick={() => void downloadReport()} disabled={busy !== null}>
                  <Download aria-hidden="true" />
                  {t('downloadReport')}
                </Button>
              ) : null}
              <Button type="button" disabled={!canExecute || busy !== null} onClick={() => void runExecute()}>
                {busy === 'execute' ? <Loader2 aria-hidden="true" className="animate-spin" /> : null}
                {busy === 'execute' ? t('executing') : t('execute')}
              </Button>
            </>
          ) : null}
          {step === 'result' ? (
            <>
              <Button type="button" variant="outline" onClick={onRestart}>
                {t('newImport')}
              </Button>
              <Button type="button" onClick={() => onOpenChange(false)}>
                {t('close')}
              </Button>
            </>
          ) : null}
        </div>
      </DialogFooter>
    </>
  )
}

function Chip({ tone, children }: { tone: 'ok' | 'warning' | 'error' | 'neutral'; children: React.ReactNode }) {
  const token = tone === 'neutral' ? 'draft' : tone
  return (
    <span
      className="inline-flex h-6 items-center rounded-full border border-(--c-border) bg-(--c-bg) px-2.5 text-xs font-medium text-(--c-fg) tabular"
      style={
        {
          '--c-bg': `var(--status-${token}-bg)`,
          '--c-fg': `var(--status-${token}-fg)`,
          '--c-border': `var(--status-${token}-border)`,
        } as React.CSSProperties
      }
    >
      {children}
    </span>
  )
}

function SummaryChips({ summary }: { summary: ImportPreview['summary'] }) {
  const { t } = useTranslation('import')
  return (
    <div className="flex flex-wrap gap-1.5" aria-label={t('report.title')}>
      <Chip tone="neutral">{t('summary.total', { count: summary.total })}</Chip>
      <Chip tone="ok">{t('summary.ok', { count: summary.ok })}</Chip>
      <Chip tone={summary.warnings ? 'warning' : 'neutral'}>{t('summary.warnings', { count: summary.warnings })}</Chip>
      <Chip tone={summary.errors ? 'error' : 'neutral'}>{t('summary.errors', { count: summary.errors })}</Chip>
      <Chip tone="neutral">{t('summary.create', { count: summary.create })}</Chip>
      <Chip tone="neutral">{t('summary.update', { count: summary.update })}</Chip>
      <Chip tone="neutral">{t('summary.unchanged', { count: summary.unchanged })}</Chip>
    </div>
  )
}

function ProblemAlert({ title, error }: { title: string; error: unknown }) {
  const message = isApiError(error)
    ? translateErrorCode(error.problem?.code, error.problem?.detail ?? error.problem?.title)
    : error instanceof Error
      ? error.message
      : undefined
  return (
    <Alert variant="destructive" role="alert">
      <TriangleAlert aria-hidden="true" />
      <AlertTitle>{title}</AlertTitle>
      {message ? <AlertDescription>{message}</AlertDescription> : null}
    </Alert>
  )
}
