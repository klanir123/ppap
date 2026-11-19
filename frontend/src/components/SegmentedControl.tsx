import type { LucideIcon } from 'lucide-react'
import { RadioGroup as RadioGroupPrimitive } from 'radix-ui'
import type { CSSProperties } from 'react'

import { statusStyle } from '@/lib/status-style'
import { cn } from '@/lib/utils'

export interface SegmentOption<T extends string> {
  value: T
  label: string
  icon?: LucideIcon
  /** Status token for the selected state (e.g. `ok`, `nok`). */
  token?: string
}

interface SegmentedControlProps<T extends string> {
  value: T | null | undefined
  onValueChange: (value: T) => void
  options: SegmentOption<T>[]
  /** Accessible name of the group. */
  label: string
  disabled?: boolean
  invalid?: boolean
  describedBy?: string
  className?: string
  size?: 'sm' | 'md'
}

/**
 * Single choice as a segmented control (radio group semantics: arrow keys move, space selects).
 * Selected segments use the status colour + icon + text.
 */
export function SegmentedControl<T extends string>({
  value,
  onValueChange,
  options,
  label,
  disabled,
  invalid,
  describedBy,
  className,
  size = 'md',
}: SegmentedControlProps<T>) {
  return (
    <RadioGroupPrimitive.Root
      value={value ?? ''}
      onValueChange={(v) => onValueChange(v as T)}
      aria-label={label}
      aria-invalid={invalid || undefined}
      aria-describedby={describedBy}
      disabled={disabled}
      orientation="horizontal"
      className={cn(
        'inline-flex max-w-full flex-wrap items-stretch gap-1 rounded-lg border bg-muted/50 p-1',
        invalid && 'border-destructive',
        className,
      )}
    >
      {options.map((option) => {
        const Icon = option.icon
        const style: CSSProperties | undefined = option.token ? statusStyle(option.token) : undefined
        return (
          <RadioGroupPrimitive.Item
            key={option.value}
            value={option.value}
            style={style}
            className={cn(
              'inline-flex items-center justify-center gap-1.5 rounded-md border border-transparent font-medium whitespace-nowrap text-muted-foreground transition-colors outline-none',
              'hover:bg-background hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-60',
              'data-[state=checked]:shadow-xs',
              option.token
                ? 'data-[state=checked]:border-(--sb-border) data-[state=checked]:bg-(--sb-bg) data-[state=checked]:text-(--sb-fg)'
                : 'data-[state=checked]:bg-background data-[state=checked]:text-foreground',
              size === 'sm' ? 'h-7 px-2.5 text-xs' : 'h-8 px-3 text-sm',
            )}
          >
            {Icon ? <Icon aria-hidden="true" className="size-4" /> : null}
            {option.label}
          </RadioGroupPrimitive.Item>
        )
      })}
    </RadioGroupPrimitive.Root>
  )
}
