import { useTranslation } from 'react-i18next'

import { TextareaField, TextField } from '@/components/forms'

/** Fields shared by elements, levels and reasons. */
export function CatalogEntryFields({ isNew }: { isNew: boolean }) {
  const { t } = useTranslation('masterdata')
  return (
    <>
      <div className="grid gap-5 sm:grid-cols-[2fr_1fr]">
        <TextField
          name="code"
          label={t('catalog.code')}
          description={t('catalog.codeHint')}
          required
          mono
          uppercase
          disabled={!isNew}
        />
        <TextField name="sortOrder" label={t('catalog.sortOrder')} type="number" required />
      </div>
      <div className="grid gap-5 sm:grid-cols-2">
        <TextField name="nameDe" label={t('catalog.nameDe')} required />
        <TextField name="nameEn" label={t('catalog.nameEn')} required />
      </div>
      <TextareaField name="descriptionDe" label={t('catalog.descriptionDe')} />
      <TextareaField name="descriptionEn" label={t('catalog.descriptionEn')} />
    </>
  )
}
