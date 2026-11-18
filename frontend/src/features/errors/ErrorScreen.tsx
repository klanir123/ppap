import { RefreshCw, TriangleAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { isApiError } from '@/api/problem'
import { Button } from '@/components/ui/button'

type ErrorKey = 'config.title' | 'config.description' | 'generic.title' | 'generic.description'

interface ErrorScreenProps {
  titleKey?: ErrorKey
  descriptionKey?: ErrorKey
  /** Already translated texts (take precedence over keys). */
  title?: string
  description?: string
  error?: unknown
  onRetry?: () => void
  retryLabel?: string
}

/** Friendly full-page error used for bootstrap failures (config, auth, /me). */
export function ErrorScreen({
  titleKey = 'generic.title',
  descriptionKey = 'generic.description',
  title,
  description,
  error,
  onRetry,
  retryLabel,
}: ErrorScreenProps) {
  const { t } = useTranslation(['errors', 'common'])
  const technical = describeError(error)
  const correlationId = isApiError(error) ? error.correlationId : undefined

  return (
    <main className="flex min-h-svh items-center justify-center bg-background px-4 py-16">
      <div className="w-full max-w-md text-center">
        <div className="mx-auto mb-6 flex size-12 items-center justify-center rounded-full bg-(--status-rejected-bg) text-(--status-rejected-fg) ring-8 ring-(--status-rejected-bg)/40">
          <TriangleAlert aria-hidden="true" className="size-5" />
        </div>
        <h1 className="text-xl font-semibold tracking-tight">{title ?? t(titleKey)}</h1>
        <p className="mt-2 text-sm text-muted-foreground">{description ?? t(descriptionKey)}</p>
        {onRetry ? (
          <Button className="mt-6" onClick={onRetry}>
            <RefreshCw aria-hidden="true" />
            {retryLabel ?? t('common:retry')}
          </Button>
        ) : null}
        {technical ? (
          <details className="mt-8 rounded-md border bg-muted/40 p-3 text-left text-xs text-muted-foreground">
            <summary className="cursor-pointer font-medium">{t('technicalDetails')}</summary>
            <p className="mt-2 font-mono break-all">{technical}</p>
            {correlationId ? (
              <p className="mt-1 font-mono break-all">
                {t('correlationId')}: {correlationId}
              </p>
            ) : null}
          </details>
        ) : null}
      </div>
    </main>
  )
}

function describeError(error: unknown): string | undefined {
  if (!error) return undefined
  if (isApiError(error)) return `${error.status} ${error.problem?.title ?? ''} ${error.problem?.detail ?? ''}`.trim()
  if (error instanceof Error) return error.message
  return String(error)
}
