import { Loader2, RotateCcw, Save } from 'lucide-react'
import { useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { STATUS_ICONS } from '@/components/status-icons'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { translateErrorCode } from '@/lib/problem-errors'
import { isApiError } from '@/api/problem'
import { REQUIREMENT_LEVELS, type RequirementLevel } from '@/lib/status'
import { statusStyle, statusToken } from '@/lib/status-style'
import { cn } from '@/lib/utils'

import { changedCodes, normaliseRequirements, requirementOf, type Requirements } from './matrix'

export interface MatrixElement {
  code: string
  name: string
  active: boolean
}

export interface MatrixLevel {
  id: string
  code: string
  name: string
  version: number
  requirements: Requirements
}

interface LevelMatrixProps {
  elements: MatrixElement[]
  levels: MatrixLevel[]
  /** Saves the complete matrix of one level (PUT replaces it). */
  onSaveLevel: (level: MatrixLevel, requirements: Requirements) => Promise<void>
  readOnly?: boolean
}

const KEY_SHORTCUTS: Record<string, RequirementLevel> = { '1': 'REQUIRED', '2': 'ON_REQUEST', '3': 'NOT_REQUIRED' }

/**
 * Matrix editor elements (rows) × levels (columns).
 * Each cell is a button (roving tabindex, arrow-key navigation) that opens a radio menu;
 * keys 1/2/3 set the value directly. Value is shown with colour, icon and text.
 */
export function LevelMatrix({ elements, levels, onSaveLevel, readOnly = false }: LevelMatrixProps) {
  const { t } = useTranslation(['masterdata', 'status'])
  const [edits, setEdits] = useState<Record<string, Requirements>>({})
  const [saving, setSaving] = useState<string | null>(null)
  const [focus, setFocus] = useState<{ row: number; col: number }>({ row: 0, col: 0 })
  const gridRef = useRef<HTMLTableElement>(null)
  const codes = elements.map((e) => e.code)

  const valueOf = (level: MatrixLevel, code: string) => requirementOf(edits[level.id] ?? level.requirements, code)

  function setValue(level: MatrixLevel, code: string, value: RequirementLevel) {
    setEdits((prev) => ({ ...prev, [level.id]: { ...(prev[level.id] ?? level.requirements), [code]: value } }))
  }

  function discard(level: MatrixLevel) {
    setEdits((prev) => {
      const next = { ...prev }
      delete next[level.id]
      return next
    })
  }

  async function save(level: MatrixLevel) {
    const edited = edits[level.id]
    if (!edited) return
    setSaving(level.id)
    try {
      await onSaveLevel(level, normaliseRequirements(edited))
      discard(level)
      toast.success(t('catalog.matrix.saved', { level: level.name }))
    } catch (error) {
      toast.error(
        isApiError(error)
          ? translateErrorCode(error.problem?.code, error.problem?.detail)
          : translateErrorCode(undefined),
      )
    } finally {
      setSaving(null)
    }
  }

  function moveFocus(row: number, col: number) {
    const r = Math.max(0, Math.min(elements.length - 1, row))
    const c = Math.max(0, Math.min(levels.length - 1, col))
    setFocus({ row: r, col: c })
    gridRef.current?.querySelector<HTMLButtonElement>(`[data-cell="${r}:${c}"]`)?.focus()
  }

  function onCellKeyDown(e: KeyboardEvent<HTMLButtonElement>, row: number, col: number) {
    const moves: Record<string, [number, number]> = {
      ArrowUp: [row - 1, col],
      ArrowDown: [row + 1, col],
      ArrowLeft: [row, col - 1],
      ArrowRight: [row, col + 1],
      Home: [row, 0],
      End: [row, levels.length - 1],
    }
    const move = moves[e.key]
    if (move) {
      // preventDefault also stops Radix from opening the menu on ArrowDown
      e.preventDefault()
      moveFocus(move[0], move[1])
      return
    }
    const shortcut = KEY_SHORTCUTS[e.key]
    const level = levels[col]
    const element = elements[row]
    if (shortcut && !readOnly && level && element) {
      e.preventDefault()
      setValue(level, element.code, shortcut)
    }
  }

  if (elements.length === 0 || levels.length === 0) {
    return <p className="text-sm text-muted-foreground">{t('catalog.matrix.empty')}</p>
  }

  return (
    <div className="space-y-3">
      {readOnly ? null : <p className="text-xs text-muted-foreground">{t('catalog.matrix.keyboardHint')}</p>}
      <div className="max-h-[min(70svh,48rem)] overflow-auto rounded-lg border bg-card">
        <table
          ref={gridRef}
          className="w-full border-separate border-spacing-0 text-sm"
          aria-label={t('catalog.levels.matrixTitle')}
        >
          <thead className="sticky top-0 z-20 bg-muted/90 backdrop-blur">
            <tr>
              <th
                scope="col"
                className="sticky left-0 z-30 min-w-56 border-b bg-muted px-3 py-2 text-left text-xs font-medium text-muted-foreground"
              >
                {t('catalog.matrix.element')}
              </th>
              {levels.map((level) => {
                const changes = edits[level.id] ? changedCodes(level.requirements, edits[level.id]!, codes).length : 0
                return (
                  <th key={level.id} scope="col" className="min-w-40 border-b px-2 py-2 text-left align-top">
                    <div className="flex flex-col gap-1">
                      <span className="text-xs font-semibold text-foreground">{level.name}</span>
                      <span className="font-mono text-[11px] font-normal text-muted-foreground">{level.code}</span>
                      {changes > 0 && !readOnly ? (
                        <div className="flex items-center gap-1">
                          <Button
                            size="xs"
                            onClick={() => void save(level)}
                            disabled={saving !== null}
                            aria-label={t('catalog.matrix.saveLevel', { level: level.name })}
                          >
                            {saving === level.id ? (
                              <Loader2 aria-hidden="true" className="animate-spin" />
                            ) : (
                              <Save aria-hidden="true" />
                            )}
                            {t('catalog.matrix.changes', { count: changes })}
                          </Button>
                          <Button
                            size="icon-xs"
                            variant="ghost"
                            onClick={() => discard(level)}
                            disabled={saving !== null}
                            aria-label={t('catalog.matrix.resetLevel', { level: level.name })}
                          >
                            <RotateCcw aria-hidden="true" />
                          </Button>
                        </div>
                      ) : null}
                    </div>
                  </th>
                )
              })}
            </tr>
          </thead>
          <tbody>
            {elements.map((element, row) => (
              <tr key={element.code} className="group">
                <th
                  scope="row"
                  className="sticky left-0 z-10 border-b bg-card px-3 py-1.5 text-left font-normal group-hover:bg-muted/40"
                >
                  <span className="block font-mono text-xs text-muted-foreground">{element.code}</span>
                  <span className={cn('block text-sm', !element.active && 'text-muted-foreground line-through')}>
                    {element.name}
                  </span>
                  {element.active ? null : (
                    <span className="text-[11px] text-muted-foreground">{t('catalog.matrix.inactiveElement')}</span>
                  )}
                </th>
                {levels.map((level, col) => {
                  const value = valueOf(level, element.code)
                  const dirty = value !== requirementOf(level.requirements, element.code)
                  const label = t(`status:requirement.${value}`)
                  const Icon = STATUS_ICONS.requirement[value]
                  const token = statusToken('requirement', value)
                  const cellLabel = t('catalog.matrix.cellLabel', {
                    element: `${element.code} ${element.name}`,
                    level: level.name,
                    value: label,
                  })
                  const isFocusCell = focus.row === row && focus.col === col
                  const cell = (
                    <button
                      type="button"
                      data-cell={`${row}:${col}`}
                      tabIndex={isFocusCell ? 0 : -1}
                      aria-label={cellLabel}
                      aria-disabled={readOnly || undefined}
                      onFocus={() => setFocus({ row, col })}
                      onKeyDown={(e) => onCellKeyDown(e, row, col)}
                      style={statusStyle(token)}
                      className={cn(
                        'inline-flex h-7 w-full items-center gap-1.5 rounded-md border px-2 text-xs font-medium whitespace-nowrap',
                        'border-(--sb-border) bg-(--sb-bg) text-(--sb-fg)',
                        'focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-ring',
                        dirty && 'ring-2 ring-participant ring-offset-1 ring-offset-card',
                        readOnly && 'cursor-default',
                      )}
                    >
                      <Icon aria-hidden="true" className="size-3.5 shrink-0" />
                      <span className="truncate">{label}</span>
                    </button>
                  )
                  return (
                    <td key={level.id} className="border-b px-2 py-1.5">
                      {readOnly ? (
                        cell
                      ) : (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>{cell}</DropdownMenuTrigger>
                          <DropdownMenuContent align="start" className="min-w-44">
                            <DropdownMenuRadioGroup
                              value={value}
                              onValueChange={(v) => setValue(level, element.code, v as RequirementLevel)}
                            >
                              {REQUIREMENT_LEVELS.map((option, i) => {
                                const OptionIcon = STATUS_ICONS.requirement[option]
                                return (
                                  <DropdownMenuRadioItem key={option} value={option}>
                                    <OptionIcon aria-hidden="true" />
                                    {t(`status:requirement.${option}`)}
                                    <span className="ml-auto pl-3 font-mono text-[11px] text-muted-foreground">
                                      {i + 1}
                                    </span>
                                  </DropdownMenuRadioItem>
                                )
                              })}
                            </DropdownMenuRadioGroup>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      )}
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
