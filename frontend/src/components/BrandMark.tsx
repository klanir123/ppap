import { cn } from '@/lib/utils'

/** Product mark – a stylised "P" on the participant accent. Decorative. */
export function BrandMark({ className }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex size-8 shrink-0 items-center justify-center rounded-lg bg-participant text-participant-foreground shadow-sm',
        className,
      )}
    >
      <svg viewBox="0 0 32 32" className="size-[62%]" fill="none">
        <path
          d="M10 24V8h7a5 5 0 0 1 0 10h-7"
          stroke="currentColor"
          strokeWidth="3.2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <circle cx="22.5" cy="23.5" r="2.2" fill="currentColor" />
      </svg>
    </span>
  )
}
