import { useMemo } from 'react'

import { CopyButton } from '@/components/CopyButton'
import { cn } from '@/lib/utils'

import { highlightJson } from './json-highlight'

export function JsonViewer({
  value,
  label,
  copyLabel,
  className,
}: {
  value: unknown
  label: string
  copyLabel: string
  className?: string
}) {
  const json = useMemo(() => (typeof value === 'string' ? value : JSON.stringify(value, null, 2)), [value])
  const highlighted = useMemo(() => highlightJson(json), [json])
  return (
    <div className={cn('relative rounded-lg border bg-muted/40', className)}>
      <div className="absolute top-1.5 right-1.5">
        <CopyButton value={json} label={copyLabel} showValue={false} />
      </div>
      <pre
        // scrollable region must be focusable for keyboard users
        // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
        tabIndex={0}
        aria-label={label}
        className="max-h-[28rem] overflow-auto p-3 pr-10 font-mono text-[11px] leading-relaxed focus-visible:outline-2 focus-visible:outline-ring"
      >
        <code>{highlighted}</code>
      </pre>
    </div>
  )
}
