import { AlarmClock, ChevronDown, ListFilter, TriangleAlert, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { SearchInput } from '@/components/data-table/SearchInput'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Toggle } from '@/components/ui/toggle'
import { usePermission } from '@/features/auth/current-user'
import { useClarificationCount } from '@/features/exchange/api/hooks'
import { partnersApi } from '@/features/masterdata/api'
import { cn } from '@/lib/utils'

import { CASE_ROLES, CASE_STATUSES, type CaseStatus } from '../api/types'
import { CaseStatusBadge } from '../components/badges'
import { hasFilters, RESET_FILTERS, type ApprovalsSearch } from './approvals-search'

const ALL = '__all__'

interface ApprovalFiltersProps {
  search: ApprovalsSearch
  onChange: (patch: Partial<ApprovalsSearch>) => void
  /** Render the search field (board view; the table renders its own). */
  withSearch?: boolean
}

/** Filters of the approval list: status (multi), own role, partner, overdue. */
export function ApprovalFilters({ search, onChange, withSearch }: ApprovalFiltersProps) {
  const { t } = useTranslation('approvals')
  const partners = partnersApi.usePage({ active: true, size: 200, sort: ['name,asc'] })
  return (
    <>
      {withSearch ? (
        <SearchInput
          value={search.q}
          placeholder={t('list.search')}
          onCommit={(q) => onChange({ q, page: undefined })}
        />
      ) : null}
      <StatusFilter value={search.status ?? []} onChange={(status) => onChange({ status, page: undefined })} />
      <Select
        value={search.role ?? ALL}
        onValueChange={(v) =>
          onChange({ role: v === ALL ? undefined : (v as ApprovalsSearch['role']), page: undefined })
        }
      >
        <SelectTrigger size="sm" className="w-full sm:w-40" aria-label={t('filters.role')}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL}>{t('filters.allRoles')}</SelectItem>
          {CASE_ROLES.map((role) => (
            <SelectItem key={role} value={role}>
              {t(`filters.roleValues.${role}`)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Select
        value={search.partner ?? ALL}
        onValueChange={(v) => onChange({ partner: v === ALL ? undefined : v, page: undefined })}
      >
        <SelectTrigger size="sm" className="w-full sm:w-48" aria-label={t('filters.partner')}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL}>{t('filters.allPartners')}</SelectItem>
          {(partners.data?.content ?? []).map((p) => (
            <SelectItem key={p.bpnl} value={p.bpnl}>
              {p.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Toggle
        variant="outline"
        size="sm"
        pressed={Boolean(search.overdue)}
        onPressedChange={(pressed) => onChange({ overdue: pressed || undefined, page: undefined })}
        className="data-[state=on]:border-(--status-error-border) data-[state=on]:bg-(--status-error-bg) data-[state=on]:text-(--status-error-fg)"
      >
        <AlarmClock aria-hidden="true" />
        {t('filters.overdue')}
      </Toggle>
      <ClarificationFilter
        pressed={Boolean(search.clarification)}
        onChange={(clarification) => onChange({ clarification: clarification || undefined, page: undefined })}
      />
      {hasFilters(search) ? (
        <Button variant="ghost" size="sm" onClick={() => onChange(RESET_FILTERS)}>
          <X aria-hidden="true" />
          {t('filters.reset')}
        </Button>
      ) : null}
    </>
  )
}

function StatusFilter({
  value,
  onChange,
}: {
  value: CaseStatus[]
  onChange: (value: CaseStatus[] | undefined) => void
}) {
  const { t } = useTranslation(['approvals', 'status'])
  const toggle = (status: CaseStatus, checked: boolean) => {
    const next = checked ? [...value, status] : value.filter((s) => s !== status)
    onChange(next.length ? CASE_STATUSES.filter((s) => next.includes(s)) : undefined)
  }
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm" className={cn('justify-between', value.length && 'border-participant/50')}>
          <ListFilter aria-hidden="true" />
          {t('filters.status')}
          {value.length ? (
            <span className="rounded-full bg-participant px-1.5 text-[11px] text-participant-foreground tabular">
              <span className="sr-only">{t('filters.selected')}: </span>
              {value.length}
            </span>
          ) : null}
          <ChevronDown aria-hidden="true" className="opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-64 p-2">
        <fieldset>
          <legend className="px-2 py-1.5 text-xs font-medium text-muted-foreground">{t('filters.status')}</legend>
          <div className="grid">
            {CASE_STATUSES.map((status) => {
              const id = `status-filter-${status}`
              return (
                <Label
                  key={status}
                  htmlFor={id}
                  className="flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 font-normal hover:bg-muted"
                >
                  <Checkbox
                    id={id}
                    checked={value.includes(status)}
                    onCheckedChange={(checked) => toggle(status, checked === true)}
                  />
                  <CaseStatusBadge status={status} size="sm" />
                </Label>
              )
            })}
          </div>
        </fieldset>
        {value.length ? (
          <Button variant="ghost" size="sm" className="mt-1 w-full" onClick={() => onChange(undefined)}>
            {t('filters.clearStatus')}
          </Button>
        ) : null}
      </PopoverContent>
    </Popover>
  )
}

/** Chip "Klärung erforderlich" with count (suppliers; shown to others only while active). */
function ClarificationFilter({ pressed, onChange }: { pressed: boolean; onChange: (pressed: boolean) => void }) {
  const { t } = useTranslation('exchange')
  const canSupply = usePermission('approvals:supply')
  const count = useClarificationCount(canSupply || pressed)
  if (!canSupply && !pressed) return null
  return (
    <Toggle
      variant="outline"
      size="sm"
      pressed={pressed}
      onPressedChange={onChange}
      className="data-[state=on]:border-(--status-warning-border) data-[state=on]:bg-(--status-warning-bg) data-[state=on]:text-(--status-warning-fg)"
    >
      <TriangleAlert aria-hidden="true" />
      {t('clarification.filter')}
      {count.data ? (
        <span className="rounded-full bg-(--status-warning-solid) px-1.5 text-[11px] font-semibold text-black/80 tabular">
          {count.data}
        </span>
      ) : null}
    </Toggle>
  )
}
