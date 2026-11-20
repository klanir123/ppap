import { Fingerprint, Server } from 'lucide-react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { participantAccent } from '@/lib/participant'
import { cn } from '@/lib/utils'

import type { DataspaceMessage, Topology, TopologyPartner } from './api'
import { MessageSteps } from './MessageSteps'
import { flowColor, progress, type FlowAnimation } from './model/flows'
import { edgePath, quadraticPoint, travel, VIEW, type Layout, type LayoutEdge, type LayoutNode } from './model/layout'
import { useFlowLabels } from './use-flow-labels'

interface GraphProps {
  topology: Topology
  layout: Layout
  animations: FlowAnimation[]
  now: number
  reducedMotion: boolean
  /** Partner highlighted from the message list (hover / focus). */
  highlight?: string
  lastByPartner: ReadonlyMap<string, DataspaceMessage>
  className?: string
}

const TRAIL = [0.035, 0.07, 0.105]

/**
 * SVG dataspace map: own connector in the centre, partner connectors around, one edge per relation,
 * messages travel along the edges (or, with reduced motion, the edge and target light up).
 */
export function DataspaceGraph({
  topology,
  layout,
  animations,
  now,
  reducedMotion,
  highlight,
  lastByPartner,
  className,
}: GraphProps) {
  const { t } = useTranslation('dataspace')
  const ids = useId().replace(/:/g, '')
  const edges = new Map(layout.edges.map((e) => [e.id, e]))
  const activeByEdge = new Map<string, FlowAnimation>()
  for (const a of animations) if (now < a.start + a.duration + 900) activeByEdge.set(a.partnerBpnl, a)
  const self = layout.nodes.find((n) => n.group === 'self')!

  return (
    <svg
      viewBox={`0 0 ${VIEW.width} ${VIEW.height}`}
      className={cn('block h-full w-full select-none', className)}
      role="group"
      aria-label={t('graph.label', { name: topology.self.name, count: topology.partners.length })}
      data-testid="dataspace-graph"
    >
      <defs>
        <pattern id={`${ids}-grid`} width="24" height="24" patternUnits="userSpaceOnUse">
          <circle cx="1" cy="1" r="1" fill="var(--border)" />
        </pattern>
        <radialGradient id={`${ids}-halo`}>
          <stop offset="0%" stopColor="var(--participant)" stopOpacity="0.28" />
          <stop offset="70%" stopColor="var(--participant)" stopOpacity="0.06" />
          <stop offset="100%" stopColor="var(--participant)" stopOpacity="0" />
        </radialGradient>
        <filter id={`${ids}-shadow`} x="-20%" y="-20%" width="140%" height="160%">
          <feDropShadow dx="0" dy="2" stdDeviation="4" floodColor="black" floodOpacity="0.08" />
        </filter>
        {(['request', 'status', 'package', 'pull', 'decision'] as const).map((c) => (
          <marker
            key={c}
            id={`${ids}-arrow-${c}`}
            viewBox="0 0 10 10"
            refX="8"
            refY="5"
            markerWidth="7"
            markerHeight="7"
            orient="auto-start-reverse"
          >
            <path d="M0,0 L10,5 L0,10 z" fill={flowColor(c)} />
          </marker>
        ))}
      </defs>

      <rect width={VIEW.width} height={VIEW.height} fill={`url(#${ids}-grid)`} opacity={0.7} />

      {/* core services (identity) – only hinted */}
      <g aria-hidden="true" opacity={0.85}>
        <path
          d={`M${layout.core.x + layout.core.w / 2},${layout.core.y} L${self.x - self.w / 2 - 8},${self.y}`}
          stroke="var(--muted-foreground)"
          strokeOpacity={0.35}
          strokeWidth={1.5}
          strokeDasharray="4 6"
          fill="none"
        />
        <rect
          x={layout.core.x - layout.core.w / 2}
          y={layout.core.y - layout.core.h / 2}
          width={layout.core.w}
          height={layout.core.h}
          rx={layout.core.h / 2}
          fill="var(--background)"
          stroke="var(--muted-foreground)"
          strokeOpacity={0.45}
          strokeDasharray="5 4"
        />
        <Fingerprint
          x={layout.core.x - layout.core.w / 2 + 16}
          y={layout.core.y - 11}
          width={22}
          height={22}
          color="var(--muted-foreground)"
        />
        <text
          x={layout.core.x - layout.core.w / 2 + 46}
          y={layout.core.y - 4}
          className="fill-foreground text-[13px] font-semibold"
        >
          {t('graph.core')}
        </text>
        <text
          x={layout.core.x - layout.core.w / 2 + 46}
          y={layout.core.y + 14}
          className="fill-muted-foreground text-[11px]"
        >
          {t('graph.coreHint')}
        </text>
      </g>

      {/* edges */}
      {layout.edges.map((edge) => {
        const node = layout.nodes.find((n) => n.id === edge.id)!
        return (
          <EdgeView
            key={edge.id}
            edge={edge}
            partner={node.partner!}
            active={activeByEdge.get(edge.id)}
            highlighted={highlight === edge.id}
            reducedMotion={reducedMotion}
            arrowId={(c) => `${ids}-arrow-${c}`}
            last={lastByPartner.get(edge.id)}
          />
        )
      })}

      {/* nodes */}
      <SelfNode node={self} topology={topology} haloId={`${ids}-halo`} shadowId={`${ids}-shadow`} />
      {layout.nodes
        .filter((n) => n.partner)
        .map((n) => {
          const active = activeByEdge.get(n.id)
          const arriving =
            active &&
            active.direction === 'toPartner' &&
            now >= active.start + active.duration * (reducedMotion ? 0 : 1)
          return (
            <PartnerNode
              key={n.id}
              node={n}
              partner={n.partner!}
              shadowId={`${ids}-shadow`}
              ring={arriving ? active : undefined}
            />
          )
        })}
      {(() => {
        const toSelf = animations.find(
          (a) =>
            a.direction === 'toSelf' &&
            now >= a.start + a.duration * (reducedMotion ? 0 : 1) &&
            now < a.start + a.duration + 900,
        )
        return toSelf ? <NodeRing node={self} color={flowColor(toSelf.category)} /> : null
      })()}

      {/* travelling messages */}
      {reducedMotion
        ? null
        : animations.map((a) => {
            const edge = edges.get(a.partnerBpnl)
            if (!edge) return null
            return <Comet key={a.key} animation={a} edge={edge} now={now} lane={laneOf(a, animations, now)} />
          })}
    </svg>
  )
}

function EdgeView({
  edge,
  partner,
  active,
  highlighted,
  reducedMotion,
  arrowId,
  last,
}: {
  edge: LayoutEdge
  partner: TopologyPartner
  active?: FlowAnimation
  highlighted: boolean
  reducedMotion: boolean
  arrowId: (category: string) => string
  last?: DataspaceMessage
}) {
  const { t } = useTranslation(['dataspace', 'exchange'])
  const labels = useFlowLabels()
  const d = edgePath(edge)
  const role = edge.partnerRole
  const mode = partner.relation?.exchangeMode
  const color = active ? flowColor(active.category) : undefined
  const roleText = role ? t(`graph.role.${role}`) : t('graph.role.NONE')
  const modeText = mode
    ? t(`exchange:mode.${mode}.short` as 'exchange:mode.PUSH.short', { defaultValue: mode })
    : undefined
  const labelW = Math.max(roleText.length, (modeText ?? '').length) * 6.4 + 22
  const description = [
    t('graph.edgeLabel', { partner: partner.name, role: roleText }),
    modeText ? t('graph.mode', { mode: modeText }) : undefined,
  ]
    .filter(Boolean)
    .join(', ')

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <g
          tabIndex={0}
          role="img"
          aria-label={description}
          className="cursor-default outline-none [&:focus-visible>.ds-focus]:opacity-100"
          data-testid="dataspace-edge"
          data-partner={edge.id}
        >
          <path d={d} stroke="transparent" strokeWidth={22} fill="none" />
          <path
            className="ds-focus opacity-0"
            d={d}
            stroke="var(--ring)"
            strokeWidth={8}
            strokeOpacity={0.35}
            fill="none"
          />
          <path
            d={d}
            fill="none"
            stroke={color ?? (highlighted ? 'var(--foreground)' : 'var(--muted-foreground)')}
            strokeOpacity={color ? 0.9 : highlighted ? 0.6 : 0.35}
            strokeWidth={color ? 3.5 : highlighted ? 2.5 : 1.75}
            strokeLinecap="round"
            markerEnd={
              color && reducedMotion && active?.direction === 'toPartner'
                ? `url(#${arrowId(active.category)})`
                : undefined
            }
            markerStart={
              color && reducedMotion && active?.direction === 'toSelf' ? `url(#${arrowId(active.category)})` : undefined
            }
            style={{ transition: 'stroke 300ms, stroke-width 300ms' }}
          />
          <g transform={`translate(${edge.label.x},${edge.label.y})`}>
            <rect
              x={-labelW / 2}
              y={modeText ? -19 : -11}
              width={labelW}
              height={modeText ? 38 : 22}
              rx={8}
              fill="var(--card)"
              stroke={color ?? 'var(--border)'}
            />
            <text y={modeText ? -4 : 4} textAnchor="middle" className="fill-foreground text-[11.5px] font-semibold">
              {roleText}
            </text>
            {modeText ? (
              <text y={12} textAnchor="middle" className="fill-muted-foreground text-[10.5px]">
                {modeText}
              </text>
            ) : null}
          </g>
          {active && reducedMotion ? (
            <g
              transform={`translate(${edge.label.x},${edge.label.y + (modeText ? 34 : 26)})`}
              data-testid="dataspace-highlight"
              data-category={active.category}
            >
              <rect
                x={-labels.category(active.category).length * 3.6 - 18}
                y={-12}
                width={labels.category(active.category).length * 7.2 + 36}
                height={24}
                rx={12}
                fill="var(--card)"
                stroke={flowColor(active.category)}
                strokeWidth={2}
              />
              <circle
                cx={-labels.category(active.category).length * 3.6 - 6}
                cy={0}
                r={4}
                fill={flowColor(active.category)}
              />
              <text x={6} y={4} textAnchor="middle" className="fill-foreground text-[11px] font-semibold">
                {labels.category(active.category)}
              </text>
            </g>
          ) : null}
        </g>
      </TooltipTrigger>
      <TooltipContent side="top" className="max-w-sm space-y-1.5 text-left">
        <p className="font-semibold">{partner.name}</p>
        <p className="text-xs opacity-90">
          {roleText}
          {modeText ? ` · ${modeText}` : ''}
          {partner.relation?.policyProfile
            ? ` · ${t('graph.profile', { profile: partner.relation.policyProfile })}`
            : ''}
        </p>
        {last ? (
          <div className="space-y-1 border-t border-current/20 pt-1.5 text-xs">
            <p>
              {t('graph.lastMessage', { type: labels.type(last.messageType) })}
              {last.caseNumber ? ` · ${last.caseNumber}` : ''}
            </p>
            <MessageSteps message={last} inverted />
          </div>
        ) : (
          <p className="text-xs opacity-80">{t('graph.noMessages')}</p>
        )}
      </TooltipContent>
    </Tooltip>
  )
}

function SelfNode({
  node,
  topology,
  haloId,
  shadowId,
}: {
  node: LayoutNode
  topology: Topology
  haloId: string
  shadowId: string
}) {
  const { t } = useTranslation('dataspace')
  const x = node.x - node.w / 2
  const y = node.y - node.h / 2
  const ready = topology.self.connector.status === 'UP'
  return (
    <g data-testid="dataspace-self" aria-label={t('graph.selfLabel', { name: topology.self.name })} role="img">
      <ellipse
        className="ds-halo"
        cx={node.x}
        cy={node.y}
        rx={node.w * 0.85}
        ry={node.h * 1.25}
        fill={`url(#${haloId})`}
      />
      <rect
        x={x}
        y={y}
        width={node.w}
        height={node.h}
        rx={18}
        fill="var(--card)"
        stroke="var(--participant)"
        strokeWidth={2.5}
        filter={`url(#${shadowId})`}
      />
      <rect x={x} y={y} width={node.w} height={30} rx={18} fill="var(--participant)" />
      <rect x={x} y={y + 16} width={node.w} height={14} fill="var(--participant)" />
      <Server x={x + 16} y={y + 7} width={16} height={16} color="var(--participant-foreground)" />
      <text
        x={x + 40}
        y={y + 20}
        className="fill-(--participant-foreground) text-[12px] font-semibold tracking-wide uppercase"
      >
        {t('graph.self', { role: topology.self.roleLabel ?? '' })}
      </text>
      <text x={x + 18} y={y + 58} className="fill-foreground text-[19px] font-semibold">
        {truncate(topology.self.name, 24)}
      </text>
      <text x={x + 18} y={y + 80} className="fill-muted-foreground font-mono text-[11.5px]">
        {topology.self.bpnl}
      </text>
      <circle
        cx={x + node.w - 22}
        cy={y + 76}
        r={5}
        fill={ready ? 'var(--status-ok-solid)' : 'var(--status-error-solid)'}
      />
      <text x={x + node.w - 32} y={y + 80} textAnchor="end" className="fill-muted-foreground text-[11px]">
        {ready ? t('graph.online') : t('graph.offline')}
      </text>
      <text x={x + 18} y={y + 100} className="fill-muted-foreground text-[10.5px]">
        {t('graph.connector')}
      </text>
    </g>
  )
}

function PartnerNode({
  node,
  partner,
  shadowId,
  ring,
}: {
  node: LayoutNode
  partner: TopologyPartner
  shadowId: string
  ring?: FlowAnimation
}) {
  const { t } = useTranslation('dataspace')
  const hue = participantAccent(partner.name, partner.bpnl).hue
  const x = node.x - node.w / 2
  const y = node.y - node.h / 2
  return (
    <g
      className="ds-node"
      style={{ '--node-hue': hue } as React.CSSProperties}
      data-testid="dataspace-partner"
      data-partner={partner.bpnl}
      role="img"
      aria-label={t('graph.partnerLabel', { name: partner.name, bpnl: partner.bpnl })}
    >
      <rect
        x={x}
        y={y}
        width={node.w}
        height={node.h}
        rx={14}
        fill="var(--card)"
        stroke="var(--border)"
        strokeWidth={1.25}
        filter={`url(#${shadowId})`}
      />
      <rect x={x} y={y + 14} width={4} height={node.h - 28} rx={2} fill="var(--node-accent)" />
      <rect x={x + 16} y={y + 14} width={30} height={30} rx={8} fill="var(--node-soft)" />
      <Server x={x + 23} y={y + 21} width={16} height={16} color="var(--node-accent)" />
      <text x={x + 56} y={y + 29} className="fill-foreground text-[15px] font-semibold">
        {truncate(partner.name, 21)}
      </text>
      <text x={x + 56} y={y + 47} className="fill-muted-foreground font-mono text-[10.5px]">
        {partner.bpnl}
      </text>
      <circle
        cx={x + 22}
        cy={y + node.h - 20}
        r={4.5}
        fill={partner.exchangeReady ? 'var(--status-ok-solid)' : 'var(--status-warning-solid)'}
      />
      <text x={x + 32} y={y + node.h - 16} className="fill-muted-foreground text-[11px]">
        {partner.exchangeReady ? t('graph.ready') : t('graph.notReady')}
      </text>
      {ring ? <NodeRing node={node} color={flowColor(ring.category)} /> : null}
    </g>
  )
}

function NodeRing({ node, color }: { node: LayoutNode; color: string }) {
  return (
    <rect
      x={node.x - node.w / 2 - 5}
      y={node.y - node.h / 2 - 5}
      width={node.w + 10}
      height={node.h + 10}
      rx={node.group === 'self' ? 22 : 18}
      fill="none"
      stroke={color}
      strokeWidth={3}
      data-testid="dataspace-ring"
    />
  )
}

/** One travelling message: dot with trail + type label; ripple on arrival. */
/** Index among the animations travelling on the same edge at the same time (labels are stacked). */
function laneOf(a: FlowAnimation, all: FlowAnimation[], now: number): number {
  return all.filter((b) => b.partnerBpnl === a.partnerBpnl && b.start < a.start && now < b.start + b.duration).length
}

function Comet({
  animation: a,
  edge,
  now,
  lane,
}: {
  animation: FlowAnimation
  edge: LayoutEdge
  now: number
  lane: number
}) {
  const labels = useFlowLabels()
  const [from, to] = travel(edge, a)
  const color = flowColor(a.category)
  const raw = (now - a.start) / a.duration
  if (raw < 0) return null
  if (raw >= 1) {
    const r = Math.min(1, (now - a.start - a.duration) / 900)
    return (
      <circle
        cx={to.x}
        cy={to.y}
        r={8 + r * 26}
        fill="none"
        stroke={color}
        strokeWidth={3}
        opacity={0.7 * (1 - r)}
        aria-hidden="true"
      />
    )
  }
  const t = progress(a, now)
  const p = quadraticPoint(from, edge.control, to, t)
  const label = labels.category(a.category)
  const labelW = label.length * 6.8 + 18
  return (
    <g aria-hidden="true" data-testid="dataspace-comet" data-category={a.category}>
      {TRAIL.map((offset, i) => {
        const q = quadraticPoint(from, edge.control, to, Math.max(0, t - offset))
        return <circle key={offset} cx={q.x} cy={q.y} r={6 - i * 1.5} fill={color} opacity={0.45 - i * 0.12} />
      })}
      <circle cx={p.x} cy={p.y} r={9} fill={color} stroke="var(--card)" strokeWidth={2.5} />
      <g transform={`translate(${p.x + 14},${p.y - 28 - lane * 28})`}>
        <rect width={labelW + 14} height={24} rx={12} fill="var(--card)" stroke={color} strokeWidth={1.5} />
        <circle cx={12} cy={12} r={4} fill={color} />
        <text x={22} y={16} className="fill-foreground text-[11.5px] font-semibold">
          {label}
        </text>
      </g>
    </g>
  )
}

function truncate(text: string, max: number) {
  return text.length > max ? `${text.slice(0, max - 1)}…` : text
}
