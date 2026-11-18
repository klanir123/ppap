import { cn } from '@/lib/utils'

/** Role badge of a participant ("OEM", "Tier-1" …) in the participant accent colour. */
export function ParticipantRoleBadge({ roleLabel, className }: { roleLabel: string; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex h-5 shrink-0 items-center rounded-md px-1.5 text-[11px] font-semibold tracking-wide uppercase',
        'bg-participant text-participant-foreground',
        className,
      )}
    >
      {roleLabel}
    </span>
  )
}
