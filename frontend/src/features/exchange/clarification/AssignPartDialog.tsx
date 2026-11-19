import { Loader2, PackageSearch } from 'lucide-react'
import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { isApiError } from '@/api/problem'
import { SearchInput } from '@/components/data-table/SearchInput'
import { Alert, AlertDescription } from '@/components/ui/alert'
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
import { Skeleton } from '@/components/ui/skeleton'
import { useSupplierPartOptions } from '@/features/approvals/api'
import { ChoiceCards } from '@/features/approvals/components/ChoiceCards'
import { localized } from '@/features/approvals/components/format'
import type { ApprovalCase } from '@/features/approvals/api/types'
import { partsApi } from '@/features/masterdata/api'
import { translateErrorCode } from '@/lib/problem-errors'

import { useAssignPart } from '../api/hooks'

const PAGE = 20

interface AssignPartDialogProps {
  approvalCase: ApprovalCase
  partnerPartNumber?: string | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

/** UC-04 A1: pick an own part for a received request; optionally save the part mapping (default on). */
export function AssignPartDialog({ approvalCase: c, partnerPartNumber, open, onOpenChange }: AssignPartDialogProps) {
  const { t, i18n } = useTranslation(['exchange', 'forms'])
  const saveId = useId()
  const hintId = useId()
  const [q, setQ] = useState<string | undefined>(undefined)
  const [partId, setPartId] = useState('')
  const [saveMapping, setSaveMapping] = useState(true)
  const [error, setError] = useState<string>()
  const parts = partsApi.usePage({ q, active: true, size: PAGE, sort: ['partNumber,asc'] })
  const assign = useAssignPart()
  const partner = c.partner.name ?? c.partner.bpnl
  const options = parts.data?.content ?? []
  // existing mappings of own parts to this customer (one partner part number per part and relation)
  const mapped = useSupplierPartOptions('CUSTOMER', undefined, open)
  const existingNumber = (id: string) =>
    mapped.data?.find((o) => o.partId === id)?.partners.find((m) => m.partnerBpnl === c.partner.bpnl)?.partnerPartNumber
  const selectedExisting = partId ? existingNumber(partId) : undefined

  const reset = () => {
    setQ(undefined)
    setPartId('')
    setSaveMapping(true)
    setError(undefined)
  }

  const submit = () => {
    if (!partId) {
      setError(t('clarification.dialog.selectRequired'))
      return
    }
    setError(undefined)
    assign.mutate(
      { caseId: c.id, input: { partId, saveMapping }, version: c.version },
      {
        onSuccess: () => {
          const part = options.find((p) => p.id === partId)
          toast.success(t('clarification.dialog.success', { part: part?.partNumber ?? '' }))
          onOpenChange(false)
          reset()
        },
        onError: (e) => {
          const code = isApiError(e) ? e.problem?.code : undefined
          if (code === 'DUPLICATE_KEY' && saveMapping) {
            const part = options.find((p) => p.id === partId)?.partNumber ?? ''
            setError(
              selectedExisting
                ? t('clarification.dialog.duplicate', { part, number: selectedExisting, partner })
                : t('clarification.dialog.duplicateUnknown', { part, partner }),
            )
            return
          }
          setError(translateErrorCode(code, isApiError(e) ? e.problem?.detail : undefined))
        },
      },
    )
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        onOpenChange(next)
        if (!next) reset()
      }}
    >
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t('clarification.dialog.title')}</DialogTitle>
          <DialogDescription>
            {t('clarification.dialog.description', { number: partnerPartNumber ?? '–', partner })}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          <div className="[&>div]:max-w-none">
            <SearchInput value={q} onCommit={setQ} placeholder={t('clarification.dialog.searchPlaceholder')} />
          </div>
          <div className="max-h-72 overflow-y-auto pr-1" aria-busy={parts.isFetching || undefined}>
            {parts.isPending ? (
              <div className="grid gap-2" aria-label={t('clarification.dialog.loading')}>
                <Skeleton className="h-14 w-full" />
                <Skeleton className="h-14 w-full" />
                <Skeleton className="h-14 w-full" />
              </div>
            ) : options.length === 0 ? (
              <p className="flex flex-col items-center gap-2 py-8 text-center text-sm text-muted-foreground">
                <PackageSearch aria-hidden="true" className="size-5" />
                {t('clarification.dialog.noResults')}
              </p>
            ) : (
              <ChoiceCards
                label={t('clarification.dialog.parts')}
                value={partId}
                onValueChange={(v) => {
                  setPartId(v)
                  setError(undefined)
                }}
                invalid={Boolean(error) && !partId}
                choices={options.map((p) => ({
                  value: p.id,
                  title: <span className="font-mono">{p.partNumber}</span>,
                  description: localized(p, i18n.resolvedLanguage),
                  aside: existingNumber(p.id) ? (
                    <span className="text-[11px] text-muted-foreground">
                      {t('clarification.dialog.mapped', { number: existingNumber(p.id) })}
                    </span>
                  ) : p.drawingNumber ? (
                    <span className="font-mono text-[11px] text-muted-foreground">{p.drawingNumber}</span>
                  ) : undefined,
                }))}
              />
            )}
          </div>

          <div className="flex items-start gap-2.5 rounded-lg border bg-muted/30 px-3 py-2.5">
            <Checkbox
              id={saveId}
              checked={saveMapping}
              onCheckedChange={(v) => setSaveMapping(v === true)}
              aria-describedby={hintId}
              className="mt-0.5"
            />
            <div className="space-y-0.5">
              <Label htmlFor={saveId} className="font-medium">
                {t('clarification.dialog.saveMapping')}
              </Label>
              <p id={hintId} className="text-xs text-muted-foreground">
                {t('clarification.dialog.saveMappingHint')}
              </p>
            </div>
          </div>

          {saveMapping && selectedExisting && !error ? (
            <p role="status" className="text-xs text-(--status-warning-fg)">
              {t('clarification.dialog.mappedWarning', { number: selectedExisting, partner })}
            </p>
          ) : null}
          {error ? (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('forms:cancel')}
          </Button>
          <Button onClick={submit} disabled={assign.isPending}>
            {assign.isPending ? <Loader2 aria-hidden="true" className="animate-spin" /> : null}
            {t('clarification.dialog.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
