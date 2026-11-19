import type { ReactNode } from 'react'

const TOKEN = /("(?:\\.|[^"\\])*")(\s*:)?|\b(true|false|null)\b|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)/g

/** Minimal JSON highlighting: keys, strings, numbers, literals (no dependency, no HTML injection). */
export function highlightJson(json: string): ReactNode[] {
  const out: ReactNode[] = []
  let last = 0
  let i = 0
  for (const match of json.matchAll(TOKEN)) {
    const index = match.index
    if (index > last) out.push(json.slice(last, index))
    const [text, str, colon, literal, num] = match
    if (str !== undefined && colon !== undefined) {
      out.push(
        <span key={i++} className="text-participant-soft-foreground">
          {str}
        </span>,
        colon,
      )
    } else if (str !== undefined) {
      out.push(
        <span key={i++} className="text-(--status-approved-fg)">
          {str}
        </span>,
      )
    } else if (literal !== undefined) {
      out.push(
        <span key={i++} className="text-(--status-in-review-fg)">
          {literal}
        </span>,
      )
    } else if (num !== undefined) {
      out.push(
        <span key={i++} className="text-(--status-conditionally-approved-fg)">
          {num}
        </span>,
      )
    } else out.push(text)
    last = index + text.length
  }
  if (last < json.length) out.push(json.slice(last))
  return out
}
