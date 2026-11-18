import { Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'

export function FullPageLoader({ label }: { label?: string }) {
  const { t } = useTranslation()
  return (
    <div className="flex min-h-svh items-center justify-center bg-background" role="status" aria-live="polite">
      <div className="flex items-center gap-3 text-sm text-muted-foreground">
        <Loader2 aria-hidden="true" className="size-4 animate-spin" />
        <span>{label ?? t('loading')}</span>
      </div>
    </div>
  )
}
