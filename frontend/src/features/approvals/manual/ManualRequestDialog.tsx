import { useNavigate } from '@tanstack/react-router'
import { ChevronDown, Loader2, PenLine } from 'lucide-react'
import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

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
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { translateErrorCode } from '@/lib/problem-errors'
import { cn } from '@/lib/utils'

import { useCatalogue, useCreateManualCase, useSupplierPartOptions } from '../api'
import type { ManualCaseInput } from '../api/types'
import { localized } from '../components/format'
import { problemMessage } from '../components/problem'
import { todayIso } from '../model/status'
import { prefillElements, type ElementChoice } from '../request/request-model'
import { manualSchema } from './manual-schema'

interface ManualValues {
  partnerBpnl: string
  partId: string
  partnerPartNumber: string
  externalReference: string
  reasonCode: string
  levelCode: string
  dueDate: string
  remarks: string
}

const empty: ManualValues = {
  partnerBpnl: '',
  partId: '',
  partnerPartNumber: '',
  externalReference: '',
  reasonCode: '',
  levelCode: '',
  dueDate: '',
  remarks: '',
}

/** "Anforderung manuell erfassen" (supplier): request of a customer without Catena-X connection. */
export function ManualRequestDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const [session, setSession] = useState(0)
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        onOpenChange(next)
        if (!next) setSession((s) => s + 1)
      }}
    >
      <DialogContent className="max-h-[92svh] relative overflow-y-auto sm:max-w-2xl">
        <ManualForm key={session} onDone={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  )
}

function ManualForm({ onDone }: { onDone: () => void }) {
  const { t, i18n } = useTranslation(['approvals', 'forms'])
  const navigate = useNavigate()
  const id = useId()
  const lng = i18n.resolvedLanguage
  const options = useSupplierPartOptions('CUSTOMER', undefined)
  const catalogue = useCatalogue()
  const create = useCreateManualCase()
  const [values, setValues] = useState<ManualValues>(empty)
  const [elements, setElements] = useState<ElementChoice[]>([])
  const [showElements, setShowElements] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  const result = manualSchema.safeParse(values)
  const errors: Record<string, string> = {}
  if (submitted && !result.success)
    for (const issue of result.error.issues) errors[String(issue.path[0])] ??= issue.message
  const selectedCount = elements.filter((e) => e.selected).length

  const customers = new Map<string, string>()
  for (const o of options.data ?? [])
    for (const p of o.partners) customers.set(p.partnerBpnl, p.partnerName ?? p.partnerBpnl)
  const parts = (options.data ?? []).filter((o) => o.partners.some((p) => p.partnerBpnl === values.partnerBpnl))

  const set = (patch: Partial<ManualValues>) => setValues((prev) => ({ ...prev, ...patch }))
  const field = (name: keyof ManualValues) => ({
    id: `${id}-${name}`,
    'aria-invalid': Boolean(errors[name]) || undefined,
    'aria-describedby': errors[name] ? `${id}-${name}-error` : undefined,
  })
  const err = (name: keyof ManualValues) =>
    errors[name] ? (
      <p id={`${id}-${name}-error`} className="text-xs font-medium text-destructive">
        {translateErrorCode(errors[name])}
      </p>
    ) : null
  const req = (
    <span aria-hidden="true" className="text-destructive">
      *
    </span>
  )

  const submit = () => {
    setSubmitted(true)
    if (!result.success || selectedCount === 0) return
    const input: ManualCaseInput = {
      partnerBpnl: values.partnerBpnl,
      partId: values.partId,
      partnerPartNumber: values.partnerPartNumber.trim() || undefined,
      externalReference: values.externalReference.trim() || undefined,
      reasonCode: values.reasonCode,
      levelCode: values.levelCode,
      dueDate: values.dueDate,
      remarks: values.remarks.trim() || undefined,
      elements: elements.filter((e) => e.selected).map((e) => ({ code: e.code, requirement: e.requirement })),
    }
    create.mutate(input, {
      onSuccess: (created) => {
        toast.success(t('manual.created', { caseNumber: created.caseNumber }))
        onDone()
        void navigate({ to: '/approvals/$caseId', params: { caseId: created.id } })
      },
    })
  }

  return (
    <form
      noValidate
      className="grid gap-5"
      onSubmit={(e) => {
        e.preventDefault()
        submit()
      }}
    >
      <DialogHeader>
        <DialogTitle className="flex items-center gap-2">
          <PenLine aria-hidden="true" className="size-4" />
          {t('manual.title')}
        </DialogTitle>
        <DialogDescription>{t('manual.description')}</DialogDescription>
      </DialogHeader>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="grid gap-2">
          <Label htmlFor={`${id}-partnerBpnl`} className="gap-1">
            {t('manual.customer')}
            {req}
          </Label>
          <Select
            value={values.partnerBpnl || undefined}
            onValueChange={(partnerBpnl) => set({ partnerBpnl, partId: '', partnerPartNumber: '' })}
          >
            <SelectTrigger className="w-full" {...field('partnerBpnl')}>
              <SelectValue placeholder={options.isPending ? t('manual.loading') : t('forms:selectPlaceholder')} />
            </SelectTrigger>
            <SelectContent>
              {[...customers].map(([bpnl, name]) => (
                <SelectItem key={bpnl} value={bpnl}>
                  {name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {err('partnerBpnl')}
          {!options.isPending && customers.size === 0 ? (
            <p className="text-xs text-muted-foreground">{t('manual.noCustomers')}</p>
          ) : null}
        </div>
        <div className="grid gap-2">
          <Label htmlFor={`${id}-partId`} className="gap-1">
            {t('manual.part')}
            {req}
          </Label>
          <Select
            value={values.partId || undefined}
            disabled={!values.partnerBpnl}
            onValueChange={(partId) => {
              const option = parts.find((p) => p.partId === partId)
              const mapping = option?.partners.find((p) => p.partnerBpnl === values.partnerBpnl)
              set({ partId, partnerPartNumber: mapping?.partnerPartNumber ?? '' })
            }}
          >
            <SelectTrigger className="w-full" {...field('partId')}>
              <SelectValue placeholder={t('forms:selectPlaceholder')} />
            </SelectTrigger>
            <SelectContent>
              {parts.map((p) => (
                <SelectItem key={p.partId} value={p.partId}>
                  {p.partNumber} · {p.partNameDe}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {err('partId')}
        </div>
        <div className="grid gap-2">
          <Label htmlFor={`${id}-partnerPartNumber`}>{t('manual.customerPartNumber')}</Label>
          <Input
            {...field('partnerPartNumber')}
            className="font-mono"
            value={values.partnerPartNumber}
            onChange={(e) => set({ partnerPartNumber: e.target.value })}
          />
          {err('partnerPartNumber')}
        </div>
        <div className="grid gap-2">
          <Label htmlFor={`${id}-externalReference`}>{t('manual.externalReference')}</Label>
          <Input
            {...field('externalReference')}
            value={values.externalReference}
            placeholder={t('manual.externalReferencePlaceholder')}
            onChange={(e) => set({ externalReference: e.target.value })}
          />
          {err('externalReference')}
        </div>
        <div className="grid gap-2">
          <Label htmlFor={`${id}-reasonCode`} className="gap-1">
            {t('wizard.reason.reason')}
            {req}
          </Label>
          <Select value={values.reasonCode || undefined} onValueChange={(reasonCode) => set({ reasonCode })}>
            <SelectTrigger className="w-full" {...field('reasonCode')}>
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
          {err('reasonCode')}
        </div>
        <div className="grid gap-2">
          <Label htmlFor={`${id}-levelCode`} className="gap-1">
            {t('wizard.reason.level')}
            {req}
          </Label>
          <Select
            value={values.levelCode || undefined}
            onValueChange={(levelCode) => {
              set({ levelCode })
              const level = catalogue.levels.find((l) => l.code === levelCode)
              // manual: offer everything the level names; ON_REQUEST stays optional
              setElements(
                prefillElements(level, catalogue.elements).map((e) => ({
                  ...e,
                  selected: e.levelRequirement !== 'NOT_REQUIRED',
                })),
              )
            }}
          >
            <SelectTrigger className="w-full" {...field('levelCode')}>
              <SelectValue placeholder={t('forms:selectPlaceholder')} />
            </SelectTrigger>
            <SelectContent>
              {catalogue.levels.map((l) => (
                <SelectItem key={l.code} value={l.code}>
                  {localized(l, lng)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {err('levelCode')}
        </div>
        <div className="grid gap-2">
          <Label htmlFor={`${id}-dueDate`} className="gap-1">
            {t('wizard.schedule.dueDate')}
            {req}
          </Label>
          <Input
            {...field('dueDate')}
            type="date"
            min={todayIso()}
            value={values.dueDate}
            onChange={(e) => set({ dueDate: e.target.value })}
          />
          {err('dueDate')}
        </div>
      </div>

      {values.levelCode ? (
        <div className="rounded-lg border">
          <button
            type="button"
            className="flex w-full items-center justify-between gap-2 rounded-lg px-4 py-3 text-left text-sm hover:bg-muted/40 focus-visible:outline-2 focus-visible:outline-ring"
            aria-expanded={showElements}
            onClick={() => setShowElements((s) => !s)}
          >
            <span>
              <span className="font-medium">{t('manual.elements')}</span>
              <span className="ml-2 text-muted-foreground">
                {t('wizard.elements.selected', { count: selectedCount })}
              </span>
            </span>
            <ChevronDown
              aria-hidden="true"
              className={cn('size-4 transition-transform', showElements && 'rotate-180')}
            />
          </button>
          {showElements ? (
            <ul className="grid max-h-64 gap-1 relative overflow-y-auto border-t p-3 sm:grid-cols-2">
              {elements.map((e) => {
                const element = catalogue.elements.find((c) => c.code === e.code)
                return (
                  <li key={e.code}>
                    <Label className="flex cursor-pointer items-start gap-2 rounded-md px-2 py-1.5 font-normal hover:bg-muted">
                      <Checkbox
                        checked={e.selected}
                        onCheckedChange={(checked) =>
                          setElements((list) =>
                            list.map((x) => (x.code === e.code ? { ...x, selected: checked === true } : x)),
                          )
                        }
                      />
                      <span className="text-xs">
                        <span className="font-mono text-muted-foreground">{e.code}</span>{' '}
                        {element ? localized(element, lng) : ''}
                      </span>
                    </Label>
                  </li>
                )
              })}
            </ul>
          ) : null}
          {submitted && selectedCount === 0 ? (
            <p className="px-4 pb-3 text-xs font-medium text-destructive">{t('wizard.errors.MIN_ONE_ELEMENT')}</p>
          ) : null}
        </div>
      ) : null}

      <div className="grid gap-2">
        <Label htmlFor={`${id}-remarks`}>{t('wizard.schedule.remarks')}</Label>
        <Textarea
          {...field('remarks')}
          rows={3}
          value={values.remarks}
          onChange={(e) => set({ remarks: e.target.value })}
        />
        {err('remarks')}
      </div>

      {create.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {problemMessage(create.error)}
        </p>
      ) : null}

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>
          {t('forms:cancel')}
        </Button>
        <Button type="submit" disabled={create.isPending}>
          {create.isPending ? <Loader2 aria-hidden="true" className="animate-spin" /> : <PenLine aria-hidden="true" />}
          {t('manual.submit')}
        </Button>
      </DialogFooter>
    </form>
  )
}
