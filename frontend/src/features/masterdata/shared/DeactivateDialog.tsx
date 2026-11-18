import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { ConfirmDialog } from '@/components/ConfirmDialog'

interface DeactivateDialogProps {
  name: string | undefined
  onCancel: () => void
  onConfirm: () => Promise<unknown>
  title?: string
  description?: string
  confirmLabel?: string
  successMessage?: string
}

/** Confirms DELETE (= deactivate, or physical delete if never referenced). Errors are toasted globally. */
export function DeactivateDialog({
  name,
  onCancel,
  onConfirm,
  title,
  description,
  confirmLabel,
  successMessage,
}: DeactivateDialogProps) {
  const { t } = useTranslation('masterdata')
  return (
    <ConfirmDialog
      open={name !== undefined}
      onOpenChange={(open) => !open && onCancel()}
      title={title ?? t('common.deactivateTitle', { name })}
      description={description ?? t('common.deactivateDescription')}
      confirmLabel={confirmLabel ?? t('common.deactivate')}
      destructive
      onConfirm={() => {
        void onConfirm().then(() => {
          toast.success(successMessage ?? t('common.deactivated', { name }))
          onCancel()
        }, onCancel)
      }}
    />
  )
}
