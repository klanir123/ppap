import { FileJson, RefreshCw } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ConstraintChips } from '@/features/exchange/cockpit/cockpit-parts'
import { JsonViewer } from '@/features/exchange/components/JsonViewer'

import { usePolicyPreview } from '../api'

/** ODRL preview of a saved profile (`GET /policy-profiles/{id}/preview`), refreshed after every save. */
export function PolicyPreviewPanel({
  profileId,
  version,
  dirty,
}: {
  profileId: string
  version?: number
  dirty?: boolean
}) {
  const { t } = useTranslation('masterdata')
  const preview = usePolicyPreview(profileId, version)
  return (
    <Card className="gap-4" data-testid="policy-preview">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <FileJson aria-hidden="true" className="size-4 text-muted-foreground" />
          {t('policyProfiles.preview.title')}
        </CardTitle>
        <CardDescription className="text-xs">
          {dirty ? t('policyProfiles.preview.dirty') : t('policyProfiles.preview.description')}
        </CardDescription>
        <CardAction>
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => void preview.refetch()}
            aria-label={t('policyProfiles.preview.refresh')}
            disabled={preview.isFetching}
          >
            <RefreshCw aria-hidden="true" className={preview.isFetching ? 'animate-spin' : undefined} />
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-5">
        {preview.data?.relations?.length ? (
          <p className="text-xs text-muted-foreground">
            {t('policyProfiles.preview.relations', { relations: preview.data.relations.join(', ') })}
          </p>
        ) : null}
        {preview.isPending ? (
          <Skeleton className="h-64 w-full" />
        ) : preview.isError ? (
          <p className="text-sm text-destructive">{t('policyProfiles.preview.error')}</p>
        ) : preview.data.policies.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('policyProfiles.preview.empty')}</p>
        ) : (
          preview.data.policies.map((p, index) => {
            const title = p.kind
              ? t(`policyProfiles.preview.kinds.${p.kind}` as 'policyProfiles.preview.kinds.USAGE', {
                  defaultValue: p.kind,
                })
              : (p.id ?? t('policyProfiles.preview.policy', { index: index + 1 }))
            return (
              <section key={p.id ?? index} className="space-y-2">
                <h3 className="flex flex-wrap items-baseline gap-2 text-xs font-medium">
                  {title}
                  {p.id && p.kind ? (
                    <code className="font-mono text-[11px] font-normal text-muted-foreground">{p.id}</code>
                  ) : null}
                </h3>
                <ConstraintChips policy={p.policy} />
                <JsonViewer
                  value={p.policy}
                  label={title}
                  copyLabel={t('policyProfiles.preview.copy', { title })}
                  className={dirty ? 'opacity-60' : undefined}
                />
              </section>
            )
          })
        )}
      </CardContent>
    </Card>
  )
}
