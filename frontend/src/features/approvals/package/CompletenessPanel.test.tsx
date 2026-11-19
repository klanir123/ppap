import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { renderWithProviders } from '@/test/render'

import { CompletenessPanel } from './CompletenessPanel'

const names = new Map([
  ['PPF-08', 'Prozess-FMEA'],
  ['PPF-11', 'Prüfergebnisse Maß'],
])

describe('<CompletenessPanel>', () => {
  it('lists server issues per element and jumps to the element on click', async () => {
    const onSelect = vi.fn()
    renderWithProviders(
      <CompletenessPanel
        names={names}
        onSelect={onSelect}
        completeness={{
          complete: false,
          requiredTotal: 17,
          requiredDone: 12,
          issues: [
            { elementCode: 'PPF-08', code: 'MISSING_SELF_ASSESSMENT', message: 'MISSING_SELF_ASSESSMENT' },
            { elementCode: 'PPF-11', code: 'DOCUMENT_REQUIRED', message: 'DOCUMENT_REQUIRED' },
            { elementCode: 'PPF-11', code: 'VALUES_REQUIRED', message: 'VALUES_REQUIRED' },
          ],
        }}
      />,
    )
    expect(screen.getByText('12/17 Pflichtelemente')).toBeInTheDocument()
    expect(screen.getByText('3 offene Punkte')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Vollständigkeit 71 %' })).toBeInTheDocument()

    const item = screen.getByRole('button', { name: /PPF-11/ })
    expect(item).toHaveTextContent('Nachweisdokument fehlt · Strukturierte Werte fehlen')
    await userEvent.click(item)
    expect(onSelect).toHaveBeenCalledWith('PPF-11')
  })

  it('confirms a complete package', () => {
    renderWithProviders(
      <CompletenessPanel
        names={names}
        onSelect={() => {}}
        completeness={{ complete: true, requiredTotal: 2, requiredDone: 2, issues: [] }}
      />,
    )
    expect(screen.getByRole('status')).toHaveTextContent('Alle Pflichtangaben sind vollständig.')
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('falls back to the server message for unknown issue codes', () => {
    renderWithProviders(
      <CompletenessPanel
        names={names}
        onSelect={() => {}}
        completeness={{
          complete: false,
          requiredTotal: 1,
          requiredDone: 0,
          issues: [{ elementCode: 'PPF-08', code: 'SUBSUPPLIER_MISSING', message: 'Sub-supplier reference missing' }],
        }}
      />,
    )
    expect(screen.getByText('Sub-supplier reference missing')).toBeInTheDocument()
  })
})
