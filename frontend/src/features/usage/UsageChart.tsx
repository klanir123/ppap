import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useElementWidth } from '@/hooks/use-element-width'

import type { UsageMonth } from './api'
import { formatMonth, niceScale } from './usage-model'

const HEIGHT = 220
const PAD = { top: 12, right: 8, bottom: 28, left: 32 }
const SERIES = [
  { key: 'approvalCases', color: 'var(--chart-1)' },
  { key: 'revisions', color: 'var(--chart-2)' },
] as const

/** Rounded top, square base (bars anchored to the baseline). */
function barPath(x: number, y: number, w: number, h: number, r = 4): string {
  if (h <= 0) return ''
  const rr = Math.min(r, w / 2, h)
  return `M${x},${y + h}V${y + rr}Q${x},${y} ${x + rr},${y}H${x + w - rr}Q${x + w},${y} ${x + w},${y + rr}V${y + h}Z`
}

/**
 * Grouped bar chart per month (approval cases, revisions) – hand-rolled SVG on the chart tokens; the data table
 * below the chart is the accessible equivalent, the chart itself is labelled as an image with a summary.
 */
export function UsageChart({ data, summary }: { data: UsageMonth[]; summary: string }) {
  const { t, i18n } = useTranslation('usage')
  const [hover, setHover] = useState<number | null>(null)
  const titleId = useId()
  const [containerRef, containerWidth] = useElementWidth<HTMLDivElement>()
  const width = Math.max(320, data.length * 44, containerWidth)
  const innerW = width - PAD.left - PAD.right
  const innerH = HEIGHT - PAD.top - PAD.bottom
  const max = Math.max(0, ...data.map((d) => Math.max(d.approvalCases, d.revisions)))
  const scale = niceScale(max)
  const band = innerW / Math.max(1, data.length)
  const barW = Math.min(16, (band - 12) / 2)
  const y = (v: number) => PAD.top + innerH - (v / scale.max) * innerH
  const hovered = hover !== null ? data[hover] : undefined

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-4 text-xs text-muted-foreground" aria-hidden="true">
        {SERIES.map((s) => (
          <span key={s.key} className="inline-flex items-center gap-1.5">
            <span className="size-2.5 rounded-sm" style={{ background: s.color }} />
            {t(`series.${s.key}`)}
          </span>
        ))}
      </div>
      <div ref={containerRef} className="relative overflow-x-auto">
        <svg
          role="img"
          aria-labelledby={titleId}
          width={width}
          height={HEIGHT}
          viewBox={`0 0 ${width} ${HEIGHT}`}
          className="block"
          data-testid="usage-chart"
        >
          <title id={titleId}>{summary}</title>
          {scale.ticks.map((tick) => (
            <g key={tick}>
              <line
                x1={PAD.left}
                x2={width - PAD.right}
                y1={y(tick)}
                y2={y(tick)}
                stroke="var(--border)"
                strokeWidth={1}
              />
              <text
                x={PAD.left - 6}
                y={y(tick)}
                dy="0.32em"
                textAnchor="end"
                className="fill-muted-foreground text-[10px] tabular"
              >
                {tick}
              </text>
            </g>
          ))}
          {data.map((d, i) => {
            const x0 = PAD.left + i * band + (band - (barW * 2 + 2)) / 2
            return (
              <g
                key={d.month}
                onMouseEnter={() => setHover(i)}
                onMouseLeave={() => setHover(null)}
                data-month={d.month}
              >
                <rect
                  x={PAD.left + i * band}
                  y={PAD.top}
                  width={band}
                  height={innerH}
                  fill={hover === i ? 'var(--muted)' : 'transparent'}
                  opacity={0.6}
                />
                {SERIES.map((s, si) => {
                  const v = d[s.key]
                  return <path key={s.key} d={barPath(x0 + si * (barW + 2), y(v), barW, y(0) - y(v))} fill={s.color} />
                })}
                <text
                  x={PAD.left + i * band + band / 2}
                  y={HEIGHT - 10}
                  textAnchor="middle"
                  className="fill-muted-foreground text-[10px]"
                >
                  {formatMonth(d.month, i18n.resolvedLanguage)}
                </text>
              </g>
            )
          })}
        </svg>
        {hovered ? (
          <div
            className="pointer-events-none absolute top-1 rounded-md border bg-popover px-2.5 py-1.5 text-xs shadow-md"
            style={{ left: `${((PAD.left + (hover! + 0.5) * band) / width) * 100}%`, transform: 'translateX(-50%)' }}
            aria-hidden="true"
          >
            <p className="font-medium">{formatMonth(hovered.month, i18n.resolvedLanguage, 'long')}</p>
            {SERIES.map((s) => (
              <p key={s.key} className="flex items-center gap-1.5 tabular">
                <span className="size-2 rounded-sm" style={{ background: s.color }} />
                {t(`series.${s.key}`)}: {hovered[s.key]}
              </p>
            ))}
          </div>
        ) : null}
      </div>
    </div>
  )
}
