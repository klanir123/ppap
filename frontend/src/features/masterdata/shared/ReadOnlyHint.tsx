import { Lock } from 'lucide-react'
import { useTranslation } from 'react-i18next'

export function ReadOnlyHint() {
  const { t } = useTranslation('masterdata')
  return (
    <p className="flex items-center gap-2 rounded-md border bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
      <Lock aria-hidden="true" className="size-3.5" />
      {t('common.readOnlyHint')}
    </p>
  )
}
