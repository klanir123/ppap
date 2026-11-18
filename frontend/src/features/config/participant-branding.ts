import type { ParticipantInfo } from '@/api/generated/model'
import { participantAccent } from '@/lib/participant'

/**
 * Applies the participant accent to the document: CSS hue variable, tab title prefix
 * and a tinted favicon – so OEM / Tier-1 / Tier-2 tabs are distinguishable at a glance.
 */
export function applyParticipantBranding(participant: ParticipantInfo, productName: string) {
  const { hue, key } = participantAccent(participant.roleLabel, participant.bpnl)
  const root = document.documentElement
  root.style.setProperty('--participant-hue', String(hue))
  root.dataset.participant = key
  document.title = `${participant.roleLabel} · ${participant.name} – ${productName}`

  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">` +
    `<rect width="32" height="32" rx="8" fill="oklch(0.52 0.15 ${hue})"/>` +
    `<path d="M10 22V10h6.5a4 4 0 0 1 0 8H10" fill="none" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>` +
    `</svg>`
  let link = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
  if (!link) {
    link = document.createElement('link')
    link.rel = 'icon'
    document.head.appendChild(link)
  }
  link.type = 'image/svg+xml'
  link.href = `data:image/svg+xml,${encodeURIComponent(svg)}`
}
