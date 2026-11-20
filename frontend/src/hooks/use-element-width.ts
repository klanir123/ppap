import { useEffect, useRef, useState } from 'react'

/** Observed content width of an element (0 until measured / without ResizeObserver). */
export function useElementWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  const [width, setWidth] = useState(0)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    setWidth(el.clientWidth)
    if (typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(([entry]) => setWidth(Math.round(entry?.contentRect.width ?? el.clientWidth)))
    observer.observe(el)
    return () => observer.disconnect()
  }, [])
  return [ref, width] as const
}
