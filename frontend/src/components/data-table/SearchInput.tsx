import { Search, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Input } from '@/components/ui/input'

interface SearchInputProps {
  value: string | undefined
  onCommit: (value: string | undefined) => void
  placeholder?: string
  debounceMs?: number
}

/** Debounced search field; Enter commits immediately, Escape clears. */
export function SearchInput({ value, onCommit, placeholder, debounceMs = 300 }: SearchInputProps) {
  const { t } = useTranslation('table')
  const [text, setText] = useState(value ?? '')
  const [lastValue, setLastValue] = useState(value)

  // Adopt external changes (back/forward navigation, reset filters) – derived state during render.
  if (value !== lastValue) {
    setLastValue(value)
    setText(value ?? '')
  }

  useEffect(() => {
    const next = text.trim() || undefined
    if (next === (value || undefined)) return
    const id = window.setTimeout(() => onCommit(next), debounceMs)
    return () => window.clearTimeout(id)
  }, [text, value, onCommit, debounceMs])

  return (
    <div className="relative w-full sm:max-w-xs">
      <Search
        aria-hidden="true"
        className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground"
      />
      <Input
        type="search"
        role="searchbox"
        aria-label={t('search')}
        placeholder={placeholder ?? t('search')}
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') onCommit(text.trim() || undefined)
          if (e.key === 'Escape' && text) {
            e.preventDefault()
            setText('')
            onCommit(undefined)
          }
        }}
        className="h-8 pr-8 pl-8 [&::-webkit-search-cancel-button]:hidden"
      />
      {text ? (
        <button
          type="button"
          aria-label={t('clearSearch')}
          onClick={() => {
            setText('')
            onCommit(undefined)
          }}
          className="absolute top-1/2 right-1.5 flex size-5 -translate-y-1/2 items-center justify-center rounded-sm text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring"
        >
          <X aria-hidden="true" className="size-3.5" />
        </button>
      ) : null}
    </div>
  )
}
