import { Link } from '@tanstack/react-router'
import { ArrowLeft } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'

export function NotFoundPage() {
  const { t } = useTranslation('errors')
  return (
    <div className="flex flex-col items-center justify-center py-24 text-center">
      <p className="font-mono text-sm font-semibold text-participant">{t('notFound.code')}</p>
      <h1 className="mt-3 text-2xl font-semibold tracking-tight">{t('notFound.title')}</h1>
      <p className="mt-2 max-w-sm text-sm text-muted-foreground">{t('notFound.description')}</p>
      <Button className="mt-8" variant="outline" asChild>
        <Link to="/">
          <ArrowLeft aria-hidden="true" />
          {t('notFound.back')}
        </Link>
      </Button>
    </div>
  )
}
