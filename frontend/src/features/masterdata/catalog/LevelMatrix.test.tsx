import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '@/lib/i18n'
import { renderWithProviders } from '@/test/render'

import { LevelMatrix, type MatrixElement, type MatrixLevel } from './LevelMatrix'

const elements: MatrixElement[] = [
  { code: 'EL-01', name: 'Deckblatt', active: true },
  { code: 'EL-09', name: 'Prüfergebnisse', active: true },
]
const levels: MatrixLevel[] = [
  { id: 'l1', code: 'LVL-1', name: 'Stufe 1', version: 1, requirements: { 'EL-01': 'REQUIRED' } },
  {
    id: 'l2',
    code: 'LVL-2',
    name: 'Stufe 2',
    version: 3,
    requirements: { 'EL-01': 'REQUIRED', 'EL-09': 'ON_REQUEST' },
  },
]

describe('LevelMatrix', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('de')
  })

  it('gives every cell an accessible name with element, level and value', () => {
    renderWithProviders(<LevelMatrix elements={elements} levels={levels} onSaveLevel={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'EL-01 Deckblatt, Stufe 1: Gefordert' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'EL-09 Prüfergebnisse, Stufe 1: Nicht gefordert' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'EL-09 Prüfergebnisse, Stufe 2: Auf Anforderung' })).toBeInTheDocument()
  })

  it('supports arrow-key navigation with a single tab stop', async () => {
    const user = userEvent.setup()
    renderWithProviders(<LevelMatrix elements={elements} levels={levels} onSaveLevel={vi.fn()} />)
    const first = screen.getByRole('button', { name: /EL-01 Deckblatt, Stufe 1/ })
    expect(first).toHaveAttribute('tabindex', '0')
    expect(screen.getByRole('button', { name: /EL-09 Prüfergebnisse, Stufe 2/ })).toHaveAttribute('tabindex', '-1')

    first.focus()
    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('button', { name: /EL-01 Deckblatt, Stufe 2/ })).toHaveFocus()
    await user.keyboard('{ArrowDown}')
    expect(screen.getByRole('button', { name: /EL-09 Prüfergebnisse, Stufe 2/ })).toHaveFocus()
  })

  it('tracks changes per level and saves the normalised matrix', async () => {
    const user = userEvent.setup()
    const onSaveLevel = vi.fn().mockResolvedValue(undefined)
    renderWithProviders(<LevelMatrix elements={elements} levels={levels} onSaveLevel={onSaveLevel} />)

    screen.getByRole('button', { name: /EL-09 Prüfergebnisse, Stufe 1/ }).focus()
    await user.keyboard('1') // shortcut: REQUIRED
    expect(screen.getByRole('button', { name: 'EL-09 Prüfergebnisse, Stufe 1: Gefordert' })).toBeInTheDocument()

    // change back via the menu on another cell
    await user.click(screen.getByRole('button', { name: 'EL-01 Deckblatt, Stufe 1: Gefordert' }))
    await user.click(await screen.findByRole('menuitemradio', { name: /Nicht gefordert/ }))

    const save = screen.getByRole('button', { name: 'Änderungen an Stufe 1 speichern' })
    expect(save).toHaveTextContent('2 Änderungen')
    expect(screen.queryByRole('button', { name: 'Änderungen an Stufe 2 speichern' })).not.toBeInTheDocument()

    await user.click(save)
    expect(onSaveLevel).toHaveBeenCalledWith(levels[0], { 'EL-09': 'REQUIRED' })
  })

  it('is read-only without permission', async () => {
    const user = userEvent.setup()
    renderWithProviders(<LevelMatrix elements={elements} levels={levels} onSaveLevel={vi.fn()} readOnly />)
    const cell = screen.getByRole('button', { name: /EL-09 Prüfergebnisse, Stufe 1/ })
    cell.focus()
    await user.keyboard('1')
    expect(cell).toHaveAccessibleName('EL-09 Prüfergebnisse, Stufe 1: Nicht gefordert')
    expect(screen.queryByText(/Pfeiltasten/)).not.toBeInTheDocument()
  })
})
