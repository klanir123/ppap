import { useState, type ComponentProps } from 'react'

import { Input } from '@/components/ui/input'

import type { JsonValue } from '../api/types'
import { parseNumberInput } from './schema'

type NumberInputProps = Omit<ComponentProps<typeof Input>, 'value' | 'onChange' | 'type'> & {
  value: JsonValue | undefined
  onValueChange: (value: JsonValue | undefined) => void
  integer?: boolean
}

const display = (value: JsonValue | undefined) =>
  value === undefined || value === null ? '' : typeof value === 'number' ? String(value) : String(value)

/**
 * Text input for numbers: keeps the typed text (e.g. "1," while typing), accepts a decimal comma
 * and emits a number – or the raw text, so validation can flag it.
 */
export function NumberInput({ value, onValueChange, integer, ...props }: NumberInputProps) {
  const [text, setText] = useState(() => display(value))
  // external value changes (reload, import) replace the local text unless it already represents the value
  const [lastValue, setLastValue] = useState(value)
  if (value !== lastValue) {
    setLastValue(value)
    if (parseNumberInput(text) !== value) setText(display(value))
  }
  return (
    <Input
      {...props}
      type="text"
      inputMode={integer ? 'numeric' : 'decimal'}
      autoComplete="off"
      value={text}
      onChange={(e) => {
        setText(e.target.value)
        const parsed = parseNumberInput(e.target.value)
        setLastValue(parsed)
        onValueChange(parsed)
      }}
    />
  )
}
