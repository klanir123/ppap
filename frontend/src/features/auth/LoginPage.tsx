import { ArrowRight, Building2, FileCheck2, Network, ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { BrandMark } from '@/components/BrandMark'
import { LanguageSwitch } from '@/components/LanguageSwitch'
import { ParticipantRoleBadge } from '@/components/ParticipantBadge'
import { ThemeToggle } from '@/components/ThemeToggle'
import { Button } from '@/components/ui/button'
import { useAppConfig } from '@/features/config/config-context'

/** Landing page for anonymous users. Sign-in starts only on explicit user action. */
export function LoginPage({ onLogin }: { onLogin: () => void }) {
  const { t } = useTranslation(['auth', 'common'])
  const { participant, version } = useAppConfig()
  const [redirecting, setRedirecting] = useState(false)

  const features = [
    { icon: ShieldCheck, text: t('login.features.sovereign') },
    { icon: FileCheck2, text: t('login.features.traceable') },
    { icon: Network, text: t('login.features.standard') },
  ]

  return (
    <div className="grid min-h-svh lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      {/* Brand panel */}
      <aside className="relative hidden overflow-hidden bg-[oklch(0.2_0.03_var(--participant-hue))] text-white lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 opacity-[0.07] [background-image:linear-gradient(white_1px,transparent_1px),linear-gradient(90deg,white_1px,transparent_1px)] [background-size:40px_40px]"
        />
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -top-40 -right-40 size-[520px] rounded-full bg-[oklch(0.55_0.17_var(--participant-hue))] opacity-30 blur-3xl"
        />
        <div className="relative flex items-center gap-3">
          <BrandMark className="size-9" />
          <div className="leading-tight">
            <p className="text-sm font-semibold">{t('common:productName')}</p>
            <p className="text-xs text-white/60">{t('common:vendor')}</p>
          </div>
        </div>

        <div className="relative max-w-md space-y-8">
          <h2 className="text-3xl leading-tight font-semibold tracking-tight text-balance">
            {t('common:productTagline')}
          </h2>
          <ul className="space-y-4">
            {features.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-3 text-sm text-white/80">
                <span className="flex size-8 items-center justify-center rounded-md bg-white/10 ring-1 ring-white/15">
                  <Icon aria-hidden="true" className="size-4" />
                </span>
                {text}
              </li>
            ))}
          </ul>
        </div>

        <p className="relative text-xs text-white/50">{t('common:version', { version })}</p>
      </aside>

      {/* Sign-in panel */}
      <main id="main-content" className="flex flex-col bg-background">
        <div className="flex items-center justify-between p-4 sm:p-6">
          <div className="flex items-center gap-2 lg:invisible">
            <BrandMark />
            <span className="text-sm font-semibold">{t('common:productName')}</span>
          </div>
          <div className="flex items-center gap-1">
            <LanguageSwitch />
            <ThemeToggle />
          </div>
        </div>

        <div className="flex flex-1 items-center justify-center px-4 pb-16 sm:px-6">
          <div className="w-full max-w-sm space-y-8">
            <div className="space-y-2">
              <h1 className="text-2xl font-semibold tracking-tight">{t('login.title')}</h1>
              <p className="text-sm text-muted-foreground">{t('login.subtitle')}</p>
            </div>

            <section aria-labelledby="instance-hint" className="space-y-2">
              <p id="instance-hint" className="text-xs font-medium text-muted-foreground">
                {t('login.instanceHint')}
              </p>
              <div className="relative overflow-hidden rounded-lg border bg-card p-4 shadow-xs">
                <span aria-hidden="true" className="absolute inset-y-0 left-0 w-1 bg-participant" />
                <div className="flex items-start gap-3 pl-1">
                  <span className="flex size-9 shrink-0 items-center justify-center rounded-md bg-participant-soft text-participant-soft-foreground">
                    <Building2 aria-hidden="true" className="size-4.5" />
                  </span>
                  <div className="min-w-0 flex-1 space-y-1">
                    <div className="flex items-center gap-2">
                      <p className="truncate text-sm font-semibold">{participant.name}</p>
                      <ParticipantRoleBadge roleLabel={participant.roleLabel} />
                    </div>
                    <p className="text-xs text-muted-foreground">
                      <span className="sr-only">{t('common:bpnl')}: </span>
                      <span className="font-mono tracking-tight">{participant.bpnl}</span>
                    </p>
                  </div>
                </div>
              </div>
            </section>

            <Button
              size="lg"
              className="h-10 w-full text-sm"
              disabled={redirecting}
              onClick={() => {
                setRedirecting(true)
                onLogin()
              }}
            >
              {redirecting ? t('login.redirecting') : t('login.button')}
              {redirecting ? null : <ArrowRight aria-hidden="true" data-icon="inline-end" />}
            </Button>

            <p className="text-center text-xs text-muted-foreground">{t('login.footer')}</p>
          </div>
        </div>
      </main>
    </div>
  )
}
