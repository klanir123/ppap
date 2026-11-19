import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { renderWithProviders } from '@/test/render'

import type { DecisionDraft, FindingDraft } from '../model/decision-rules'
import { DecisionPanel } from './DecisionPanel'

const codes = ['PPF-01', 'PPF-11']
const names = new Map([
  ['PPF-01', 'Deckblatt'],
  ['PPF-11', 'Prüfergebnisse Maß'],
])

function Harness({ findings, onDecide = () => {} }: { findings: Record<string, FindingDraft>; onDecide?: () => void }) {
  const [draft, setDraft] = useState<DecisionDraft>({ conditions: [] })
  return (
    <DecisionPanel
      draft={draft}
      onChange={setDraft}
      findings={findings}
      elementCodes={codes}
      names={names}
      onDecide={onDecide}
      onSelectElement={() => {}}
    />
  )
}

describe('<DecisionPanel>', () => {
  it('blocks approval while an element is not accepted', async () => {
    const user = userEvent.setup()
    renderWithProviders(
      <Harness findings={{ 'PPF-01': { result: 'ACCEPTED' }, 'PPF-11': { result: 'NOT_ACCEPTED', comment: '' } }} />,
    )
    expect(screen.getByText('Vorschlag aus den Prüfergebnissen: Abgelehnt')).toBeInTheDocument()
    const decide = screen.getByRole('button', { name: 'Entscheidung bestätigen …' })
    expect(decide).toBeDisabled()

    await user.click(screen.getByRole('radio', { name: /^Freigabe$/ }))
    const checks = screen.getByTestId('decision-checks')
    expect(
      within(checks).getByText(/Nicht akzeptiert – keine Freigabe möglich: PPF-11 Prüfergebnisse Maß/),
    ).toBeInTheDocument()
    expect(decide).toBeDisabled()

    // rejection: comment required per NOT_ACCEPTED element
    await user.click(screen.getByRole('radio', { name: /^Ablehnung$/ }))
    expect(within(checks).getByText(/Begründung fehlt: PPF-11/)).toBeInTheDocument()
    expect(decide).toBeDisabled()
  })

  it('shows conditions and due date for conditional approval and enables the decision when complete', async () => {
    const user = userEvent.setup()
    const onDecide = vi.fn()
    renderWithProviders(
      <Harness
        onDecide={onDecide}
        findings={{ 'PPF-01': { result: 'ACCEPTED' }, 'PPF-11': { result: 'ACCEPTED_WITH_CONDITION', comment: 'x' } }}
      />,
    )
    await user.click(screen.getByRole('radio', { name: /^Bedingte Freigabe$/ }))
    const checks = screen.getByTestId('decision-checks')
    expect(within(checks).getByText('Mindestens eine Auflage angeben.')).toBeInTheDocument()
    expect(within(checks).getByText('Frist für die Auflagen angeben.')).toBeInTheDocument()

    await user.type(screen.getByRole('textbox', { name: 'Auflage 1' }), 'Nachmessung Merkmal 12')
    await user.click(screen.getByRole('button', { name: 'Auflage hinzufügen' }))
    expect(screen.getByRole('textbox', { name: 'Auflage 2' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Auflage 2 entfernen' }))

    await user.type(screen.getByLabelText(/^Frist/), '2099-01-31')
    expect(within(checks).getByText('Alle Voraussetzungen erfüllt.')).toBeInTheDocument()
    const decide = screen.getByRole('button', { name: 'Entscheidung bestätigen …' })
    expect(decide).toBeEnabled()
    await user.click(decide)
    expect(onDecide).toHaveBeenCalled()
  })

  it('allows a plain approval when all elements are accepted', async () => {
    const user = userEvent.setup()
    renderWithProviders(<Harness findings={{ 'PPF-01': { result: 'ACCEPTED' }, 'PPF-11': { result: 'ACCEPTED' } }} />)
    await user.click(screen.getByRole('radio', { name: /^Freigabe$/ }))
    expect(screen.getByRole('button', { name: 'Entscheidung bestätigen …' })).toBeEnabled()
  })
})
