import { Building2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { CopyButton } from '@/components/CopyButton'
import { ParticipantRoleBadge } from '@/components/ParticipantBadge'
import { useAppConfig } from '@/features/config/config-context'

/** Prominent "who am I acting as" indicator: participant name, BPNL and role. */
export function ParticipantContext() {
  const { t } = useTranslation()
  const { participant } = useAppConfig()

  return (
    <section
      aria-label={t('participantContext')}
      data-testid="participant-context"
      className="flex min-w-0 items-center gap-2.5 rounded-lg border bg-card py-1 pr-1 pl-1.5 shadow-xs"
    >
      <span className="hidden size-7 shrink-0 items-center justify-center rounded-md bg-participant-soft text-participant-soft-foreground sm:flex">
        <Building2 aria-hidden="true" className="size-4" />
      </span>
      <div className="flex min-w-0 flex-col leading-tight">
        <span className="flex min-w-0 items-center gap-1.5">
          <span className="truncate text-sm font-semibold">{participant.name}</span>
          <ParticipantRoleBadge roleLabel={participant.roleLabel} />
        </span>
        <span className="hidden text-[11px] text-muted-foreground md:block">
          <span className="sr-only">{t('bpnl')}: </span>
          <span className="font-mono">{participant.bpnl}</span>
        </span>
      </div>
      <span className="hidden md:inline-flex">
        <CopyButton value={participant.bpnl} label={t('copyBpnl')} />
      </span>
    </section>
  )
}
