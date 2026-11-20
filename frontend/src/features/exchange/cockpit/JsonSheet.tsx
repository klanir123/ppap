import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'

import { JsonViewer } from '../components/JsonViewer'

export interface JsonSection {
  title: string
  value: unknown
  /** Shown above the JSON (e.g. constraint chips). */
  summary?: ReactNode
}

/** Side sheet with one or more JSON documents (asset properties, ODRL policies, selectors). */
export function JsonSheet({
  open,
  onClose,
  title,
  description,
  badges,
  sections,
}: {
  open: boolean
  onClose: () => void
  title: string
  description?: string
  badges?: ReactNode
  sections: JsonSection[]
}) {
  const { t } = useTranslation('exchange')
  return (
    <Sheet open={open} onOpenChange={(next) => !next && onClose()}>
      <SheetContent className="w-full gap-0 overflow-y-auto data-[side=right]:sm:max-w-2xl">
        <SheetHeader className="border-b">
          <SheetTitle className="pr-8 font-mono text-sm break-all">{title}</SheetTitle>
          {description ? <SheetDescription>{description}</SheetDescription> : null}
          {badges ? <div className="flex flex-wrap items-center gap-1.5 pt-1">{badges}</div> : null}
        </SheetHeader>
        <div className="space-y-6 p-4">
          {sections.map((section) => (
            <section key={section.title} className="space-y-2">
              <h3 className="text-xs font-medium text-muted-foreground">{section.title}</h3>
              {section.summary}
              {section.value === undefined || section.value === null ? (
                <p className="text-sm text-muted-foreground">{t('cockpit.json.empty')}</p>
              ) : (
                <JsonViewer
                  value={section.value}
                  label={section.title}
                  copyLabel={t('cockpit.json.copy', { title: section.title })}
                />
              )}
            </section>
          ))}
        </div>
      </SheetContent>
    </Sheet>
  )
}
