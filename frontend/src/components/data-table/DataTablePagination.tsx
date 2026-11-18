import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

import { PAGE_SIZES } from './list-search'

interface DataTablePaginationProps {
  page: number
  size: number
  totalElements: number
  totalPages: number
  onPageChange: (page: number) => void
  onSizeChange: (size: number) => void
}

export function DataTablePagination({
  page,
  size,
  totalElements,
  totalPages,
  onPageChange,
  onSizeChange,
}: DataTablePaginationProps) {
  const { t, i18n } = useTranslation('table')
  const sizeLabelId = useId()
  const fmt = new Intl.NumberFormat(i18n.resolvedLanguage)
  const pages = Math.max(totalPages, 1)
  const from = totalElements === 0 ? 0 : page * size + 1
  const to = Math.min((page + 1) * size, totalElements)
  const isFirst = page <= 0
  const isLast = page >= pages - 1

  return (
    <div className="flex flex-col-reverse gap-3 px-1 pt-3 text-sm sm:flex-row sm:items-center sm:justify-between">
      <p className="text-muted-foreground tabular" aria-live="polite">
        {t('range', { from: fmt.format(from), to: fmt.format(to), total: fmt.format(totalElements) })}
      </p>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <div className="flex items-center gap-2">
          <span id={sizeLabelId} className="text-muted-foreground">
            {t('rowsPerPage')}
          </span>
          <Select value={String(size)} onValueChange={(v) => onSizeChange(Number(v))}>
            <SelectTrigger size="sm" className="w-18" aria-labelledby={sizeLabelId}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent align="end">
              {PAGE_SIZES.map((s) => (
                <SelectItem key={s} value={String(s)}>
                  {s}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <span className="text-muted-foreground tabular">{t('pageOf', { page: page + 1, pages })}</span>
        <nav className="flex items-center gap-1" aria-label={t('pageOf', { page: page + 1, pages })}>
          <Button
            variant="outline"
            size="icon-sm"
            aria-label={t('firstPage')}
            disabled={isFirst}
            onClick={() => onPageChange(0)}
          >
            <ChevronsLeft aria-hidden="true" />
          </Button>
          <Button
            variant="outline"
            size="icon-sm"
            aria-label={t('previousPage')}
            disabled={isFirst}
            onClick={() => onPageChange(page - 1)}
          >
            <ChevronLeft aria-hidden="true" />
          </Button>
          <Button
            variant="outline"
            size="icon-sm"
            aria-label={t('nextPage')}
            disabled={isLast}
            onClick={() => onPageChange(page + 1)}
          >
            <ChevronRight aria-hidden="true" />
          </Button>
          <Button
            variant="outline"
            size="icon-sm"
            aria-label={t('lastPage')}
            disabled={isLast}
            onClick={() => onPageChange(pages - 1)}
          >
            <ChevronsRight aria-hidden="true" />
          </Button>
        </nav>
      </div>
    </div>
  )
}
