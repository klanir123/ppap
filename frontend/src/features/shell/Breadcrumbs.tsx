import { Link, useMatches } from '@tanstack/react-router'
import { Fragment } from 'react'
import { useTranslation } from 'react-i18next'

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'

export function Breadcrumbs() {
  const { t } = useTranslation(['nav', 'common'])
  const matches = useMatches()
  const crumbs = matches
    .filter((m) => m.staticData.crumb)
    // de-duplicate layout + index routes with the same label
    .filter((m, i, all) => all.findIndex((o) => o.staticData.crumb === m.staticData.crumb) === i)

  if (crumbs.length === 0) return null

  return (
    <Breadcrumb aria-label={t('common:breadcrumb')} className="min-w-0">
      <BreadcrumbList className="flex-nowrap">
        {crumbs.map((match, index) => {
          const label = t(match.staticData.crumb!)
          const last = index === crumbs.length - 1
          return (
            <Fragment key={match.id}>
              {index > 0 ? <BreadcrumbSeparator /> : null}
              <BreadcrumbItem className="min-w-0">
                {last ? (
                  <BreadcrumbPage className="truncate">{label}</BreadcrumbPage>
                ) : (
                  <BreadcrumbLink asChild>
                    <Link to={match.pathname}>{label}</Link>
                  </BreadcrumbLink>
                )}
              </BreadcrumbItem>
            </Fragment>
          )
        })}
      </BreadcrumbList>
    </Breadcrumb>
  )
}
