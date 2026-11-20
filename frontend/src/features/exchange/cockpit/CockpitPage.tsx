import { getRouteApi } from '@tanstack/react-router'
import { BookOpen, Gauge, Handshake, MessagesSquare, Package, Radio, ShieldCheck, type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { PageHeader } from '@/components/PageHeader'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

import { useConnectorOverview } from '../api/connector-hooks'
import { AssetsTab } from './AssetsTab'
import { CatalogTab } from './CatalogTab'
import { activeTab, COCKPIT_TABS, type CockpitSearch, type CockpitTab } from './cockpit-search'
import { LiveLogTab } from './LiveLogTab'
import { MessagesTab } from './MessagesTab'
import { HealthBadge, OverviewTab } from './OverviewTab'
import { PoliciesTab } from './PoliciesTab'
import { ProcessesTab } from './ProcessesTab'

const routeApi = getRouteApi('/cockpit')

const TAB_ICONS: Record<CockpitTab, LucideIcon> = {
  overview: Gauge,
  offers: Package,
  policies: ShieldCheck,
  catalog: BookOpen,
  processes: Handshake,
  messages: MessagesSquare,
  log: Radio,
}

/** Connector cockpit (UC-10): own EDC, offers, policies, partner catalog, negotiations / transfers, messages, live log. */
export function CockpitPage() {
  const { t } = useTranslation('exchange')
  const search = routeApi.useSearch()
  const navigate = routeApi.useNavigate()
  const tab = activeTab(search)

  const update = (patch: Partial<CockpitSearch>) =>
    void navigate({ search: (prev) => ({ ...prev, ...patch }), replace: true })
  const switchTab = (next: CockpitTab) =>
    void navigate({
      // the message drawer / asset detail belong to their tab
      search: (prev) => ({
        ...prev,
        tab: next === 'overview' ? undefined : next,
        message: undefined,
        asset: undefined,
      }),
      replace: true,
    })

  return (
    <div className="space-y-6">
      <PageHeader title={t('cockpit.title')} description={t('cockpit.description')} actions={<StatusStrip />} />
      <Tabs value={tab} onValueChange={(v) => switchTab(v as CockpitTab)} className="gap-4">
        <div className="relative -mx-1 overflow-x-auto px-1 pb-1">
          <TabsList aria-label={t('cockpit.tabs.label')}>
            {COCKPIT_TABS.map((key) => {
              const Icon = TAB_ICONS[key]
              return (
                <TabsTrigger key={key} value={key} className="px-2.5">
                  <Icon aria-hidden="true" />
                  {t(`cockpit.tabs.${key}`)}
                </TabsTrigger>
              )
            })}
          </TabsList>
        </div>
        <TabsContent value="overview">
          <OverviewTab
            onNavigate={(patch) =>
              void navigate({ search: (prev) => ({ ...prev, ...patch, page: undefined }), replace: true })
            }
          />
        </TabsContent>
        <TabsContent value="offers">
          <AssetsTab selected={search.asset} onSelect={(asset) => update({ asset })} />
        </TabsContent>
        <TabsContent value="policies">
          <PoliciesTab />
        </TabsContent>
        <TabsContent value="catalog">
          <CatalogTab
            partner={search.catalogPartner}
            onPartnerChange={(catalogPartner) => update({ catalogPartner })}
          />
        </TabsContent>
        <TabsContent value="processes">
          <ProcessesTab
            negotiationState={search.negotiationState}
            transferState={search.transferState}
            onChange={update}
          />
        </TabsContent>
        <TabsContent value="messages">
          <MessagesTab />
        </TabsContent>
        {/* kept mounted: the live log collects events while other tabs are shown */}
        <TabsContent value="log" forceMount className="data-[state=inactive]:hidden">
          <LiveLogTab />
        </TabsContent>
      </Tabs>
    </div>
  )
}

/** Compact EDC / DTR status in the page header (visible on every tab). */
function StatusStrip() {
  const { t } = useTranslation('exchange')
  const query = useConnectorOverview()
  if (query.isPending) return <Skeleton className="h-6 w-48 rounded-full" />
  if (!query.data) return null
  const o = query.data
  return (
    <dl className="flex flex-wrap items-center gap-3 text-xs" aria-label={t('cockpit.status.title')}>
      <div className="flex items-center gap-1.5">
        <dt className="text-muted-foreground">{t('cockpit.overview.edcShort')}</dt>
        <dd>
          <HealthBadge status={o.edc.status} size="sm" />
        </dd>
      </div>
      {o.dtr ? (
        <div className="flex items-center gap-1.5">
          <dt className="text-muted-foreground">{t('cockpit.overview.dtrShort')}</dt>
          <dd>
            <HealthBadge status={o.dtr.status} size="sm" />
          </dd>
        </div>
      ) : null}
    </dl>
  )
}
