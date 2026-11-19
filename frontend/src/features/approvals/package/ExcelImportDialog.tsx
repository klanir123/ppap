import { ArrowLeft, FileSpreadsheet, Loader2, Upload } from 'lucide-react'
import { useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

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

import { approvalsApi, useFormTemplates, type ExcelPreview } from '../api'
import { localized } from '../components/format'
import { problemMessage } from '../components/problem'
import type { DraftSaver } from './use-draft-saver'

const AUTO = '__auto__'
const MAX_XLSX_BYTES = 10 * 1024 * 1024

interface ExcelImportDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  caseId: string
  saver: DraftSaver
  /** Element code → name (for the preview). */
  names: ReadonlyMap<string, string>
}

/** UC-05 A1: upload filled PPF form → preview (header, recognised element values) → select → apply. */
export function ExcelImportDialog(props: ExcelImportDialogProps) {
  const [session, setSession] = useState(0)
  return (
    <Dialog
      open={props.open}
      onOpenChange={(next) => {
        props.onOpenChange(next)
        if (!next) setSession((s) => s + 1)
      }}
    >
      <DialogContent className="flex max-h-[92svh] flex-col gap-0 p-0 sm:max-w-4xl">
        <ImportBody key={session} {...props} />
      </DialogContent>
    </Dialog>
  )
}

function ImportBody({ onOpenChange, caseId, saver, names }: ExcelImportDialogProps) {
  const { t, i18n } = useTranslation(['approvals', 'forms'])
  const inputId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const templates = useFormTemplates()
  const [file, setFile] = useState<File | null>(null)
  const [templateCode, setTemplateCode] = useState<string>(AUTO)
  const [preview, setPreview] = useState<ExcelPreview | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [overwrite, setOverwrite] = useState(false)
  const [busy, setBusy] = useState<'preview' | 'apply' | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function runPreview() {
    if (!file) return
    setBusy('preview')
    setError(null)
    try {
      const data = await approvalsApi.excelPreview(caseId, file, templateCode === AUTO ? undefined : templateCode)
      setPreview(data)
      setSelected(new Set(data.elements.filter((e) => e.status !== 'ERROR').map((e) => e.code)))
    } catch (e) {
      setError(problemMessage(e))
    } finally {
      setBusy(null)
    }
  }

  async function apply() {
    if (!file || !preview) return
    setBusy('apply')
    setError(null)
    try {
      await saver.flush()
      await approvalsApi
        .excelApply(caseId, file, preview.templateCode, { elementCodes: [...selected], overwrite }, saver.version())
        .then(async (res) => {
          if (res.version !== undefined) saver.setVersion(res.version)
          else await saver.refreshVersion()
        })
      await saver.resetEditors()
      toast.success(t('excel.applied', { count: selected.size }))
      onOpenChange(false)
    } catch (e) {
      setError(problemMessage(e))
    } finally {
      setBusy(null)
    }
  }

  const header = preview ? Object.entries(preview.header) : []
  const allSelectable = preview?.elements.filter((e) => e.status !== 'ERROR').map((e) => e.code) ?? []

  return (
    <>
      <DialogHeader className="border-b px-6 py-4">
        <DialogTitle className="flex items-center gap-2">
          <FileSpreadsheet aria-hidden="true" className="size-4" />
          {t('excel.title')}
        </DialogTitle>
        <DialogDescription>{preview ? t('excel.previewDescription') : t('excel.description')}</DialogDescription>
      </DialogHeader>

      <div className="flex-1 space-y-5 relative overflow-y-auto px-6 py-5">
        {!preview ? (
          <div className="grid gap-5">
            <div className="grid gap-2">
              <Label htmlFor={inputId}>{t('excel.file')}</Label>
              <div className="flex flex-col items-start gap-2 rounded-lg border border-dashed px-4 py-6 sm:flex-row sm:items-center">
                <input
                  ref={inputRef}
                  id={inputId}
                  type="file"
                  accept=".xlsx"
                  className="sr-only"
                  onChange={(e) => {
                    const f = e.target.files?.[0] ?? null
                    setError(null)
                    if (f && !f.name.toLowerCase().endsWith('.xlsx')) return setError(t('excel.onlyXlsx'))
                    if (f && f.size > MAX_XLSX_BYTES) return setError(t('excel.tooLarge'))
                    setFile(f)
                  }}
                />
                <Button type="button" variant="outline" size="sm" onClick={() => inputRef.current?.click()}>
                  <Upload aria-hidden="true" />
                  {t('excel.choose')}
                </Button>
                <span className="text-sm text-muted-foreground">{file ? file.name : t('excel.noFile')}</span>
              </div>
            </div>
            <div className="grid max-w-sm gap-2">
              <Label htmlFor={`${inputId}-template`}>{t('excel.template')}</Label>
              <Select value={templateCode} onValueChange={setTemplateCode}>
                <SelectTrigger id={`${inputId}-template`} className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={AUTO}>{t('excel.autoDetect')}</SelectItem>
                  {(templates.data ?? []).map((tpl) => (
                    <SelectItem key={tpl.code} value={tpl.code}>
                      {localized(tpl, i18n.resolvedLanguage)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        ) : (
          <>
            <p className="text-sm">
              {t('excel.recognized', {
                template: localized(
                  templates.data?.find((x) => x.code === preview.templateCode) ?? { nameDe: preview.templateCode },
                  i18n.resolvedLanguage,
                ),
              })}
            </p>
            {preview.warnings.length > 0 ? (
              <Alert>
                <AlertTitle>{t('excel.warnings')}</AlertTitle>
                <AlertDescription>
                  <ul className="list-disc pl-4">
                    {preview.warnings.map((w, i) => (
                      <li key={i}>{w.message}</li>
                    ))}
                  </ul>
                </AlertDescription>
              </Alert>
            ) : null}
            {header.length > 0 ? (
              <section aria-labelledby="excel-header" className="space-y-2">
                <h3 id="excel-header" className="text-sm font-semibold">
                  {t('excel.header')}
                </h3>
                <dl className="grid gap-x-6 gap-y-2 rounded-lg border p-3 text-sm sm:grid-cols-2">
                  {header.map(([field, entry]) => (
                    <div key={field} className="flex items-baseline justify-between gap-3">
                      <dt className="text-xs text-muted-foreground">
                        {t(`excel.headerFields.${field}` as 'excel.headerFields.supplierName', { defaultValue: field })}
                        <span className="ml-1 font-mono text-[10px]">{entry.cell}</span>
                      </dt>
                      <dd className="truncate">{entry.value ?? '–'}</dd>
                    </div>
                  ))}
                </dl>
              </section>
            ) : null}
            <section aria-labelledby="excel-elements" className="space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 id="excel-elements" className="text-sm font-semibold">
                  {t('excel.elements')}
                </h3>
                <Button
                  variant="ghost"
                  size="xs"
                  onClick={() =>
                    setSelected(selected.size === allSelectable.length ? new Set() : new Set(allSelectable))
                  }
                >
                  {selected.size === allSelectable.length ? t('excel.selectNone') : t('excel.selectAll')}
                </Button>
              </div>
              <div className="relative overflow-x-auto rounded-lg border">
                <table className="w-full text-sm" aria-label={t('excel.elements')}>
                  <thead className="bg-muted/60 text-xs text-muted-foreground">
                    <tr>
                      <th scope="col" className="w-10 px-3 py-2">
                        <span className="sr-only">{t('excel.apply')}</span>
                      </th>
                      <th scope="col" className="px-3 py-2 text-left font-medium">
                        {t('elements.element')}
                      </th>
                      <th scope="col" className="px-3 py-2 text-left font-medium">
                        {t('package.selfAssessment')}
                      </th>
                      <th scope="col" className="px-3 py-2 text-left font-medium">
                        {t('package.comment')}
                      </th>
                      <th scope="col" className="px-3 py-2 text-left font-medium">
                        {t('excel.status')}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {preview.elements.map((row) => {
                      const id = `excel-${row.code}`
                      return (
                        <tr key={row.code} className="border-t align-top">
                          <td className="px-3 py-2">
                            <Checkbox
                              id={id}
                              checked={selected.has(row.code)}
                              disabled={row.status === 'ERROR'}
                              onCheckedChange={(checked) =>
                                setSelected((prev) => {
                                  const next = new Set(prev)
                                  if (checked) next.add(row.code)
                                  else next.delete(row.code)
                                  return next
                                })
                              }
                            />
                          </td>
                          <td className="px-3 py-2">
                            <label htmlFor={id} className="cursor-pointer">
                              <span className="font-mono text-xs text-muted-foreground">{row.code}</span>
                              <span className="block">{names.get(row.code) ?? ''}</span>
                            </label>
                            {row.cell ? (
                              <span className="font-mono text-[10px] text-muted-foreground">{row.cell}</span>
                            ) : null}
                          </td>
                          <td className="px-3 py-2">
                            {row.selfAssessment ? (
                              <StatusBadge kind="assessment" status={row.selfAssessment} size="sm" />
                            ) : (
                              '–'
                            )}
                          </td>
                          <td className="max-w-xs px-3 py-2 text-xs text-muted-foreground">{row.comment || '–'}</td>
                          <td className="px-3 py-2">
                            <StatusBadge kind="validation" status={row.status} size="sm" />
                            {row.messages.length ? (
                              <ul className="mt-1 space-y-0.5 text-xs text-muted-foreground">
                                {row.messages.map((m, i) => (
                                  <li key={i}>{m.message}</li>
                                ))}
                              </ul>
                            ) : null}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
              {preview.unknownCodes.length > 0 ? (
                <p className="text-xs text-muted-foreground">
                  {t('excel.unknownCodes', { codes: preview.unknownCodes.join(', ') })}
                </p>
              ) : null}
            </section>
            <Label className="flex items-start gap-2 font-normal">
              <Checkbox checked={overwrite} onCheckedChange={(c) => setOverwrite(c === true)} className="mt-0.5" />
              <span>
                <span className="font-medium">{t('excel.overwrite')}</span>
                <span className="block text-xs text-muted-foreground">{t('excel.overwriteHint')}</span>
              </span>
            </Label>
          </>
        )}
        {error ? (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        ) : null}
      </div>

      <DialogFooter className="border-t px-6 py-4">
        {preview ? (
          <>
            <Button variant="outline" onClick={() => setPreview(null)} disabled={busy !== null}>
              <ArrowLeft aria-hidden="true" />
              {t('excel.back')}
            </Button>
            <Button onClick={() => void apply()} disabled={busy !== null || selected.size === 0}>
              {busy === 'apply' ? <Loader2 aria-hidden="true" className="animate-spin" /> : null}
              {t('excel.applyCount', { count: selected.size })}
            </Button>
          </>
        ) : (
          <>
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              {t('forms:cancel')}
            </Button>
            <Button onClick={() => void runPreview()} disabled={!file || busy !== null}>
              {busy === 'preview' ? <Loader2 aria-hidden="true" className="animate-spin" /> : null}
              {t('excel.preview')}
            </Button>
          </>
        )}
      </DialogFooter>
    </>
  )
}
