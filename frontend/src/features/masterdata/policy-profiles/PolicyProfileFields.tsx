import { Users, UserRound } from 'lucide-react'
import { useId } from 'react'
import { Controller, useFormContext } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { TextareaField, TextField } from '@/components/forms'
import { FieldShell, type BaseFieldProps } from '@/components/forms/FieldShell'
import { useFormReadOnly } from '@/components/forms/form-context'
import { SegmentedControl } from '@/components/SegmentedControl'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'

import { POLICY_ACCESS_TYPES } from '../api/types'
import { UsagePurposesField } from './UsagePurposesField'

/** Known Catena-X usage purposes offered as suggestions (free input stays possible). */
const PURPOSE_SUGGESTIONS = [
  'cx.quality.base:1',
  'cx.core.industrycore:1',
  'cx.core.qualityNotifications:1',
  'cx.pcf.base:1',
]

function BooleanField({
  name,
  label,
  description,
  disabled,
}: {
  name: string
  label: string
  description?: string
  disabled?: boolean
}) {
  const { control } = useFormContext()
  const readOnly = useFormReadOnly()
  const id = useId()
  return (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        <div className="flex items-start gap-2.5 rounded-lg border bg-muted/20 px-3 py-2.5">
          <Checkbox
            id={id}
            ref={field.ref}
            checked={field.value === true}
            disabled={readOnly || disabled}
            onCheckedChange={(v) => field.onChange(v === true)}
            onBlur={field.onBlur}
            aria-describedby={description ? `${id}-hint` : undefined}
            className="mt-0.5"
          />
          <div className="space-y-0.5">
            <Label htmlFor={id} className="font-medium">
              {label}
            </Label>
            {description ? (
              <p id={`${id}-hint`} className="text-xs text-muted-foreground">
                {description}
              </p>
            ) : null}
          </div>
        </div>
      )}
    />
  )
}

function AccessTypeField(base: BaseFieldProps) {
  const { control: formControl } = useFormContext()
  const readOnly = useFormReadOnly()
  const { t } = useTranslation('masterdata')
  return (
    <FieldShell {...base} asFieldset>
      {(control) => (
        <Controller
          control={formControl}
          name={base.name}
          render={({ field }) => (
            <SegmentedControl
              value={field.value as string}
              onValueChange={field.onChange}
              label={base.label}
              describedBy={control['aria-describedby']}
              disabled={readOnly}
              options={POLICY_ACCESS_TYPES.map((type) => ({
                value: type,
                label: t(`policyProfiles.access.${type}`),
                icon: type === 'BPN_GROUP' ? Users : UserRound,
              }))}
            />
          )}
        />
      )}
    </FieldShell>
  )
}

/** Fields of a policy profile (create sheet and detail form). */
/** `protectedProfile` = the seed profile `default` (cannot be deactivated). */
export function PolicyProfileFields({ isNew, protectedProfile }: { isNew: boolean; protectedProfile?: boolean }) {
  const { t } = useTranslation('masterdata')
  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-[12rem_minmax(0,1fr)]">
        <TextField
          name="code"
          label={t('policyProfiles.form.code')}
          description={isNew ? t('policyProfiles.form.codeHint') : undefined}
          required
          mono
          disabled={!isNew}
        />
        <TextField name="name" label={t('policyProfiles.form.name')} required />
      </div>
      <TextareaField name="description" label={t('policyProfiles.form.description')} rows={2} />

      <fieldset className="space-y-3 rounded-lg border p-4">
        <legend className="px-1 text-sm font-semibold">{t('policyProfiles.form.accessTitle')}</legend>
        <p className="text-xs text-muted-foreground">{t('policyProfiles.form.accessHint')}</p>
        <AccessTypeField
          name="accessType"
          label={t('policyProfiles.form.accessType')}
          description={t('policyProfiles.form.accessTypeHint')}
        />
        <BooleanField
          name="membershipRequired"
          label={t('policyProfiles.form.membership')}
          description={t('policyProfiles.form.membershipHint')}
        />
      </fieldset>

      <fieldset className="space-y-3 rounded-lg border p-4">
        <legend className="px-1 text-sm font-semibold">{t('policyProfiles.form.usageTitle')}</legend>
        <p className="text-xs text-muted-foreground">{t('policyProfiles.form.usageHint')}</p>
        <TextField
          name="frameworkAgreement"
          label={t('policyProfiles.form.frameworkAgreement')}
          description={t('policyProfiles.form.frameworkAgreementHint')}
          required
          mono
        />
        <UsagePurposesField
          name="usagePurposes"
          label={t('policyProfiles.form.usagePurposes')}
          description={t('policyProfiles.form.usagePurposesHint')}
          required
          suggestions={PURPOSE_SUGGESTIONS}
        />
      </fieldset>

      <BooleanField
        name="active"
        label={t('policyProfiles.form.active')}
        description={protectedProfile ? t('policyProfiles.form.defaultProtected') : t('policyProfiles.form.activeHint')}
        disabled={protectedProfile}
      />
    </div>
  )
}
