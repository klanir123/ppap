import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'

import i18n from '@/lib/i18n'
import { APPROVAL_STATUSES } from '@/lib/status'

import { StatusBadge } from './StatusBadge'

describe('StatusBadge', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('de')
  })

  it('renders an accessible health badge with text and icon', () => {
    render(<StatusBadge kind="health" status="UP" />)
    const badge = screen.getByRole('status', { name: 'Status: Verfügbar' })
    expect(badge).toHaveTextContent('Verfügbar')
    expect(badge).toHaveAttribute('data-status', 'up')
    expect(badge.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })

  it('uses the status colour tokens', () => {
    render(<StatusBadge kind="health" status="DOWN" />)
    const badge = screen.getByRole('status', { name: 'Status: Gestört' })
    expect(badge.style.getPropertyValue('--sb-bg')).toBe('var(--status-down-bg)')
    expect(badge.style.getPropertyValue('--sb-fg')).toBe('var(--status-down-fg)')
  })

  it.each(APPROVAL_STATUSES)('has a translated label for approval status %s', (status) => {
    render(<StatusBadge kind="approval" status={status} />)
    const badge = screen.getByRole('status')
    expect(badge).toHaveAttribute('data-status', status)
    expect(badge.textContent).not.toContain('approval.')
  })

  it('switches language', async () => {
    await i18n.changeLanguage('en')
    render(<StatusBadge kind="approval" status="conditionally-approved" />)
    expect(screen.getByRole('status', { name: 'Status: Conditionally approved' })).toBeInTheDocument()
  })
})
