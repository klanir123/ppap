import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { useEffect, useEffectEvent, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { getAccessToken } from '@/api/auth-token'
import { approvalKeys, approvalsApi } from '@/features/approvals/api'
import { caseStatusToken } from '@/features/approvals/model/status'

import { EVENTS_URL } from '../api/client'
import type { LiveEvent } from '../api/types'
import { affectedByResume, affectsQuery, parseLiveEvent } from './invalidation'
import { LiveStatusContext } from './live-context'
import { mayNotify, notificationFor, type LiveNotification } from './notifications'
import { createEventStream, type LiveStatus } from './sse'

/**
 * Keeps one SSE connection (`GET /api/v1/events`) per signed-in tab: invalidates the affected queries,
 * shows toasts for partner-initiated changes and exposes the connection status (top bar indicator).
 * Mounted inside the authenticated shell – unmounting on sign-out closes the stream.
 */
export function LiveEventsProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { t } = useTranslation(['exchange', 'status'])
  const [status, setStatus] = useState<LiveStatus>('idle')
  const notified = useRef(new Set<string>())

  const notify = useEffectEvent((n: LiveNotification) => {
    const title =
      n.kind === 'newRequest'
        ? t('live.toast.newRequest', { partner: n.partner })
        : n.kind === 'submissionReceived'
          ? t('live.toast.submissionReceived', { partner: n.partner })
          : t('live.toast.decisionReceived', { result: t(`status:approval.${caseStatusToken(n.result)}`) })
    toast.info(title, {
      id: `live-${n.caseId}-${n.kind}`,
      description: n.kind === 'decisionReceived' ? `${n.caseNumber} · ${n.partner}` : n.caseNumber,
      duration: 10_000,
      action: {
        label: t('live.toast.open'),
        onClick: () => void navigate({ to: '/approvals/$caseId', params: { caseId: n.caseId } }),
      },
    })
  })

  useEffect(() => {
    async function maybeNotify(event: LiveEvent) {
      if (!mayNotify(event)) return
      const dedupe = `${event.caseId}:${event.status}`
      if (notified.current.has(dedupe)) return
      notified.current.add(dedupe)
      try {
        const detail = await queryClient.fetchQuery({
          queryKey: approvalKeys.detail(event.caseId),
          queryFn: ({ signal }) => approvalsApi.getCase(event.caseId, { signal }),
          staleTime: 0,
        })
        const notification = notificationFor(detail)
        if (notification) notify(notification)
      } catch {
        // no toast without case data – lists are refreshed anyway
      }
    }

    const stream = createEventStream({
      url: EVENTS_URL,
      getToken: getAccessToken,
      onStatus: setStatus,
      onOpen: (resumed) => {
        if (resumed) void queryClient.invalidateQueries({ predicate: (q) => affectedByResume(q.queryKey) })
      },
      onMessage: (message) => {
        const event = parseLiveEvent(message)
        if (!event) return
        void queryClient.invalidateQueries({ predicate: (q) => affectsQuery(event, q.queryKey) })
        void maybeNotify(event)
      },
    })
    stream.start()
    return () => stream.stop()
  }, [queryClient])

  return <LiveStatusContext value={status}>{children}</LiveStatusContext>
}
