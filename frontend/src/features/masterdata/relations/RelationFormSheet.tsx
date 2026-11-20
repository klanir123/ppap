import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormSheet, SelectField } from '@/components/forms'

import { policyProfilesApi, relationsApi, type RelationWithReconciliation } from '../api'
import { toastReconciliation } from '../policy-profiles/reconciliation'
import { PARTNER_ROLES, type Relation } from '../api/types'
import { usePartnerOptions } from '../shared/usePartnerOptions'
import { ExchangeModeField } from './ExchangeModeField'
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
  const profiles = policyProfilesApi.useAll({ active: true })
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
        const saved: RelationWithReconciliation = await save.mutateAsync({
          id: relation?.id,
          version: relation?.version,
          input: toRelationInput(values),
        })
        const name = partners.options.find((o) => o.value === values.partnerBpnl)?.label ?? values.partnerBpnl
        const message = t(isNew ? 'common.created' : 'common.saved', { name })
        // a write that changed profile or exchange mode reports its effect on the own EDC
        if (saved.reconciliation) toastReconciliation(saved.reconciliation, message)
        else toast.success(message)
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
          <ExchangeModeField />
          <SelectField
            name="policyProfile"
            label={t('relations.policyProfile')}
            description={t('relations.policyProfileHint')}
            options={policyProfileOptions(profiles.data, relation?.policyProfile)}
          />
        </>
      )}
    </FormSheet>
  )
}

/** Active profiles (code → "name · code"); the current assignment stays selectable even if not listed. */
function policyProfileOptions(profiles: { code: string; name: string }[] | undefined, current: string | undefined) {
  const list = (profiles ?? []).map((p) => ({ value: p.code, label: `${p.name} · ${p.code}` }))
  for (const code of [current, 'default']) {
    if (code && !list.some((o) => o.value === code)) list.unshift({ value: code, label: code })
  }
  return list
}
