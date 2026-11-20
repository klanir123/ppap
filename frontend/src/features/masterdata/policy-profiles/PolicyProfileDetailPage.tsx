import { zodResolver } from '@hookform/resolvers/zod'
import { getRouteApi, Link } from '@tanstack/react-router'
import { ArrowLeft, Loader2, RefreshCw, Save, SearchX, TriangleAlert } from 'lucide-react'
import { useEffect, useState } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import type { z } from 'zod'

import { EmptyState } from '@/components/EmptyState'
import { applyProblemToForm } from '@/components/forms'
import { StatusBadge } from '@/components/StatusBadge'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useFormat } from '@/features/approvals/components/format'
import { translateErrorCode, type MappedProblem } from '@/lib/problem-errors'

import { ListAuditEventsObjectType } from '@/api/generated/model'

import { policyProfilesApi, type PolicyProfile } from '../api'
import { AuditTrail } from '../shared/AuditTrail'
import { PolicyPreviewPanel } from './PolicyPreviewPanel'
import { PolicyProfileFields } from './PolicyProfileFields'
import { policyProfileDefaults, policyProfileSchema, toPolicyProfileInput } from './policy-profile-schema'
import { toastReconciliation } from './reconciliation'

const routeApi = getRouteApi('/policy-profiles/$profileId')

export function PolicyProfileDetailPage() {
  const { t } = useTranslation('masterdata')
  const { profileId } = routeApi.useParams()
  const query = policyProfilesApi.useOne(profileId)
  if (query.isPending) {
    return (
      <div className="space-y-6" aria-busy="true">
        <Skeleton className="h-8 w-64" />
        <div className="grid gap-6 lg:grid-cols-2">
          <Skeleton className="h-[32rem] w-full" />
          <Skeleton className="h-[32rem] w-full" />
        </div>
      </div>
    )
  }
  if (query.isError || !query.data) {
    return (
      <EmptyState
        icon={SearchX}
        title={t('policyProfiles.notFound')}
        action={
          <Button variant="outline" size="sm" asChild>
            <Link to="/policy-profiles">
              <ArrowLeft aria-hidden="true" />
              {t('policyProfiles.title')}
            </Link>
          </Button>
        }
      />
    )
  }
  return (
    <ProfileEditor
      key={`${query.data.id}:${query.data.version}`}
      profile={query.data}
      onReload={() => void query.refetch()}
    />
  )
}

function ProfileEditor({ profile, onReload }: { profile: PolicyProfile; onReload: () => void }) {
  const { t } = useTranslation(['masterdata', 'forms'])
  const fmt = useFormat()
  const save = policyProfilesApi.useSave()
  const defaults = policyProfileDefaults(profile)
  const form = useForm<z.input<typeof policyProfileSchema>, unknown, z.output<typeof policyProfileSchema>>({
    resolver: zodResolver(policyProfileSchema),
    defaultValues: defaults,
    mode: 'onTouched',
  })
  const [problem, setProblem] = useState<MappedProblem | null>(null)
  const { isDirty, isSubmitting } = form.formState

  // warn before leaving with unsaved changes
  useEffect(() => {
    if (!isDirty) return
    const handler = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [isDirty])

  const submit = form.handleSubmit(async (values) => {
    setProblem(null)
    try {
      const saved = await save.mutateAsync({
        id: profile.id,
        version: profile.version,
        input: toPolicyProfileInput(values, profile),
      })
      toastReconciliation(saved.reconciliation, t('policyProfiles.saved', { name: saved.name }))
    } catch (error) {
      const mapped = applyProblemToForm(error, Object.keys(defaults), form.setError)
      if (mapped.formCode || mapped.unmapped.length > 0) setProblem(mapped)
    }
  })

  return (
    <div className="space-y-6">
      <Button variant="ghost" size="sm" className="-ml-2" asChild>
        <Link to="/policy-profiles">
          <ArrowLeft aria-hidden="true" />
          {t('policyProfiles.title')}
        </Link>
      </Button>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight">{profile.name}</h1>
            <StatusBadge kind="active" status={profile.active} size="sm" />
          </div>
          <p className="text-sm text-muted-foreground">
            <code className="font-mono">{profile.code}</code> ·{' '}
            {t('policyProfiles.versionLabel', { version: profile.version })}
            {profile.updatedAt ? ` · ${t('policyProfiles.updatedAt', { at: fmt.dateTime(profile.updatedAt) })}` : ''}
          </p>
        </div>
      </div>

      <Alert>
        <RefreshCw aria-hidden="true" />
        <AlertTitle>{t('policyProfiles.applyTitle')}</AlertTitle>
        <AlertDescription>{t('policyProfiles.applyHint')}</AlertDescription>
      </Alert>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <Card>
          <CardContent>
            <FormProvider {...form}>
              <form
                onSubmit={(e) => void submit(e)}
                noValidate
                className="space-y-6"
                aria-label={t('policyProfiles.form.label')}
              >
                {problem ? (
                  <Alert variant="destructive" role="alert">
                    <TriangleAlert aria-hidden="true" />
                    <AlertTitle>{t('forms:errorSummary')}</AlertTitle>
                    <AlertDescription>
                      {problem.isVersionConflict ? (
                        <>
                          <p>{t('forms:versionConflictDescription')}</p>
                          <Button type="button" size="sm" variant="outline" className="mt-2" onClick={onReload}>
                            <RefreshCw aria-hidden="true" />
                            {t('forms:reload')}
                          </Button>
                        </>
                      ) : (
                        <p>{translateErrorCode(problem.formCode ?? 'VALIDATION_FAILED', problem.formMessage)}</p>
                      )}
                    </AlertDescription>
                  </Alert>
                ) : null}
                <PolicyProfileFields isNew={false} protectedProfile={profile.code === 'default'} />
                <div className="flex flex-wrap items-center justify-end gap-2 border-t pt-4">
                  {isDirty ? (
                    <span className="mr-auto text-xs text-muted-foreground" role="status">
                      {t('policyProfiles.unsaved')}
                    </span>
                  ) : null}
                  <Button
                    type="button"
                    variant="outline"
                    disabled={!isDirty || isSubmitting}
                    onClick={() => form.reset(defaults)}
                  >
                    {t('forms:cancel')}
                  </Button>
                  <Button type="submit" disabled={!isDirty || isSubmitting} data-action="save-profile">
                    {isSubmitting ? (
                      <Loader2 aria-hidden="true" className="animate-spin" />
                    ) : (
                      <Save aria-hidden="true" />
                    )}
                    {isSubmitting ? t('forms:saving') : t('forms:save')}
                  </Button>
                </div>
              </form>
            </FormProvider>
          </CardContent>
        </Card>
        <div className="space-y-6">
          <PolicyPreviewPanel profileId={profile.id} version={profile.version} dirty={isDirty} />
        </div>
      </div>
      <Card>
        <CardContent>
          <AuditTrail objectType={ListAuditEventsObjectType.POLICY_PROFILE} objectId={profile.id} />
        </CardContent>
      </Card>
    </div>
  )
}
