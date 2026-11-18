import { getRouteApi } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { PageHeader } from '@/components/PageHeader'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { usePermission } from '@/features/auth/current-user'

import { useListState } from '../shared/list-state'
import { ReadOnlyHint } from '../shared/ReadOnlyHint'
import { CATALOG_TABS, type CatalogTab } from './catalog-search'
import { ElementsTab } from './ElementsTab'
import { LevelsTab } from './LevelsTab'
import { ReasonsTab } from './ReasonsTab'

const routeApi = getRouteApi('/masterdata/element-catalog')

export function CatalogPage() {
  const { t } = useTranslation('masterdata')
  const canWrite = usePermission('masterdata:write')
  const search = routeApi.useSearch()
  const navigate = routeApi.useNavigate()
  const tab: CatalogTab = search.tab ?? 'elements'
  const list = useListState(
    search,
    (next) => void navigate({ search: (prev) => ({ ...prev, ...next }), replace: true }),
    {
      sort: 'sortOrder,asc',
      size: 100,
    },
  )

  return (
    <div className="space-y-6">
      <PageHeader title={t('catalog.title')} description={t('catalog.description')} />
      {canWrite ? null : <ReadOnlyHint />}
      <Tabs
        value={tab}
        // switching tabs starts with a fresh list state
        onValueChange={(value) => void navigate({ search: { tab: value as CatalogTab } })}
      >
        <TabsList>
          {CATALOG_TABS.map((value) => (
            <TabsTrigger key={value} value={value}>
              {t(`catalog.tabs.${value}`)}
            </TabsTrigger>
          ))}
        </TabsList>
        <TabsContent value="elements" className="pt-4">
          <ElementsTab canWrite={canWrite} {...list} />
        </TabsContent>
        <TabsContent value="levels" className="pt-4">
          <LevelsTab canWrite={canWrite} />
        </TabsContent>
        <TabsContent value="reasons" className="pt-4">
          <ReasonsTab canWrite={canWrite} {...list} />
        </TabsContent>
      </Tabs>
    </div>
  )
}
