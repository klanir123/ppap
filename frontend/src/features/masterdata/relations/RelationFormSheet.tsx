import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormSheet, SelectField, TextField } from '@/components/forms'

import { relationsApi } from '../api'
import { EXCHANGE_MODES, PARTNER_ROLES, type Relation } from '../api/types'
import { usePartnerOptions } from '../shared/usePartnerOptions'
import { relationDefaults, relationSchema, toRelationInput } from './relation-schema'

interface RelationFormSheetProps {
  /** `'new'` → create, a relation → edit, `null` → closed */
  editing: Relation | 'new' | null
  onClose: () => void
  onReloaded: (relation: Relation) => void
  readOnly: boolean
}

export function RelationFormSheet({ editing, onClose, onReloaded, readOnly }: RelationFormSheetProps) {
  const { t } = useTranslation(['masterdata', 'forms'])
  const save = relationsApi.useSave()
  const partners = usePartnerOptions({ includeOwn: false })
  const isNew = editing === 'new'
  const relation = editing && editing !== 'new' ? editing : undefined

  return (
    <FormSheet
      open={editing !== null}
      onOpenChange={(open) => !open && onClose()}
      title={isNew ? t('relations.new') : t('relations.editTitle')}
      description={isNew ? t('relations.newDescription') : relation?.partnerName}
      schema={relationSchema}
      defaultValues={relationDefaults(relation)}
      formKey={relation ? `${relation.id}:${relation.version}` : 'new'}
      readOnly={readOnly}
      submitLabel={isNew ? t('forms:create') : undefined}
      onReload={() => relation && void relationsApi.fetchOne(relation.id).then(onReloaded)}
      onSubmit={async (values) => {
        await save.mutateAsync({ id: relation?.id, version: relation?.version, input: toRelationInput(values) })
        const name = partners.options.find((o) => o.value === values.partnerBpnl)?.label ?? values.partnerBpnl
        toast.success(t(isNew ? 'common.created' : 'common.saved', { name }))
      }}
    >
      {() => (
        <>
          <SelectField
            name="partnerBpnl"
            label={t('relations.partner')}
            required
            disabled={!isNew}
            options={
              relation && !partners.options.some((o) => o.value === relation.partnerBpnl)
                ? [
                    { value: relation.partnerBpnl, label: `${relation.partnerName ?? ''} · ${relation.partnerBpnl}` },
                    ...partners.options,
                  ]
                : partners.options
            }
          />
          <SelectField
            name="partnerRole"
            label={t('relations.partnerRole')}
            required
            disabled={!isNew}
            options={PARTNER_ROLES.map((role) => ({ value: role, label: t(`relations.partnerRoleValues.${role}`) }))}
          />
          <SelectField
            name="exchangeMode"
            label={t('relations.exchangeMode')}
            required
            options={EXCHANGE_MODES.map((mode) => ({ value: mode, label: t(`relations.exchangeModeValues.${mode}`) }))}
          />
          <TextField name="policyProfile" label={t('relations.policyProfile')} mono placeholder="default" />
        </>
      )}
    </FormSheet>
  )
}
