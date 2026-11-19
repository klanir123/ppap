import { RadioGroup as RadioGroupPrimitive } from 'radix-ui'
import type { ReactNode } from 'react'

import { cn } from '@/lib/utils'

export interface Choice {
  value: string
  title: ReactNode
  description?: ReactNode
  aside?: ReactNode
  disabled?: boolean
}

/** Single choice rendered as selectable cards (radio group: arrow keys, space). */
export function ChoiceCards({
  value,
  onValueChange,
  choices,
  label,
  invalid,
  describedBy,
  className,
}: {
  value: string
  onValueChange: (value: string) => void
  choices: Choice[]
  label: string
  invalid?: boolean
  describedBy?: string
  className?: string
}) {
  return (
    <RadioGroupPrimitive.Root
      value={value}
      onValueChange={onValueChange}
      aria-label={label}
      aria-invalid={invalid || undefined}
      aria-describedby={describedBy}
      className={cn('grid gap-2', className)}
    >
      {choices.map((choice) => (
        <RadioGroupPrimitive.Item
          key={choice.value}
          value={choice.value}
          disabled={choice.disabled}
          className={cn(
            'group flex w-full items-start gap-3 rounded-lg border bg-card px-4 py-3 text-left transition-colors outline-none',
            'hover:bg-muted/40 focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-60',
            'data-[state=checked]:border-participant data-[state=checked]:bg-participant-soft/50 data-[state=checked]:ring-1 data-[state=checked]:ring-participant/40',
          )}
        >
          <span
            aria-hidden="true"
            className="mt-0.5 flex size-4 shrink-0 items-center justify-center rounded-full border group-data-[state=checked]:border-participant"
          >
            <span className="size-2 rounded-full bg-participant opacity-0 group-data-[state=checked]:opacity-100" />
          </span>
          <span className="min-w-0 flex-1 space-y-0.5">
            <span className="block text-sm font-medium">{choice.title}</span>
            {choice.description ? (
              <span className="block text-xs text-muted-foreground">{choice.description}</span>
            ) : null}
          </span>
          {choice.aside ? <span className="shrink-0">{choice.aside}</span> : null}
        </RadioGroupPrimitive.Item>
      ))}
    </RadioGroupPrimitive.Root>
  )
}
