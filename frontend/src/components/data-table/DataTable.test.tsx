import type { ColumnDef } from '@tanstack/react-table'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Package } from 'lucide-react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '@/lib/i18n'
import { renderWithProviders } from '@/test/render'

import { DataTable } from './DataTable'
import type { ListState, PageData } from './types'

interface Row {
  id: string
  partNumber: string
  name: string
  active: boolean
}

const columns: ColumnDef<Row, unknown>[] = [
  { id: 'partNumber', accessorKey: 'partNumber', header: 'Sachnummer', meta: { label: 'Sachnummer' } },
  { id: 'name', accessorKey: 'name', header: 'Bezeichnung', meta: { label: 'Bezeichnung' } },
]

function page(content: Row[], overrides: Partial<PageData<Row>> = {}): PageData<Row> {
  return { content, page: 0, size: 25, totalElements: content.length, totalPages: 1, ...overrides }
}

const rows: Row[] = Array.from({ length: 3 }, (_, i) => ({
  id: String(i),
  partNumber: `D10-000${i}`,
  name: `Teil ${i}`,
  active: true,
}))

function renderTable(props: {
  data?: PageData<Row>
  state?: Partial<ListState>
  onStateChange?: (p: Partial<ListState>) => void
  rows?: Row[]
}) {
  const onStateChange = props.onStateChange ?? vi.fn()
  renderWithProviders(
    <DataTable<Row>
      id="test"
      label="Teile"
      columns={columns}
      page={props.data}
      rows={props.rows}
      state={{ page: 0, size: 25, active: 'true', ...props.state }}
      onStateChange={onStateChange}
      getRowId={(r) => r.id}
      getRowLabel={(r) => r.partNumber}
      rowActions={(r) => [{ label: 'Bearbeiten', onSelect: vi.fn(), hidden: r.id === 'x' }]}
      emptyState={{ icon: Package, title: 'Noch keine Teile', action: <button type="button">Neues Teil</button> }}
    />,
  )
  return onStateChange
}

describe('DataTable', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('de')
  })

  it('renders the rows of the current page with an accessible name', () => {
    renderTable({ data: page(rows) })
    const table = screen.getByRole('table', { name: 'Teile' })
    expect(within(table).getAllByRole('row')).toHaveLength(4) // header + 3
    expect(within(table).getByText('D10-0001')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Aktionen für D10-0002' })).toBeInTheDocument()
  })

  it('emits pagination changes', async () => {
    const user = userEvent.setup()
    const onStateChange = renderTable({ data: page(rows, { totalElements: 60, totalPages: 3 }) })
    expect(screen.getByText('1–25 von 60')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Vorherige Seite' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Nächste Seite' }))
    expect(onStateChange).toHaveBeenLastCalledWith({ page: 1 })
    await user.click(screen.getByRole('button', { name: 'Letzte Seite' }))
    expect(onStateChange).toHaveBeenLastCalledWith({ page: 2 })
  })

  it('emits server sorting and marks the sorted column', async () => {
    const user = userEvent.setup()
    const onStateChange = renderTable({ data: page(rows), state: { sort: 'name,desc' } })
    expect(screen.getByRole('columnheader', { name: /Bezeichnung/ })).toHaveAttribute('aria-sort', 'descending')

    await user.click(screen.getByRole('button', { name: 'Nach Sachnummer sortieren' }))
    expect(onStateChange).toHaveBeenLastCalledWith({ sort: 'partNumber,asc', page: 0 })
  })

  it('commits the search on Enter and resets to the first page', async () => {
    const user = userEvent.setup()
    const onStateChange = renderTable({ data: page(rows), state: { page: 2 } })
    await user.type(screen.getByRole('searchbox', { name: 'Suchen' }), 'D10{Enter}')
    expect(onStateChange).toHaveBeenCalledWith({ q: 'D10', page: 0 })
  })

  it('shows the empty state with call to action when there is no data', () => {
    renderTable({ data: page([]) })
    expect(screen.getByText('Noch keine Teile')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Neues Teil' })).toBeInTheDocument()
  })

  it('shows "no results" with reset when filters are active', async () => {
    const user = userEvent.setup()
    const onStateChange = renderTable({ data: page([]), state: { q: 'xyz' } })
    expect(screen.getByText('Keine Treffer')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Filter zurücksetzen' }))
    expect(onStateChange).toHaveBeenLastCalledWith({ q: undefined, active: 'true', page: 0 })
  })

  it('shows skeleton rows while loading', () => {
    renderWithProviders(
      <DataTable<Row>
        id="test"
        label="Teile"
        columns={columns}
        state={{ page: 0, size: 5 }}
        onStateChange={vi.fn()}
        getRowId={(r) => r.id}
        getRowLabel={(r) => r.partNumber}
        isLoading
        emptyState={{ icon: Package, title: 'leer' }}
      />,
    )
    expect(screen.getByRole('table', { name: 'Teile' })).toHaveAttribute('aria-busy', 'true')
    expect(screen.queryByText('leer')).not.toBeInTheDocument()
  })

  it('filters, sorts and pages locally in client mode', () => {
    const many: Row[] = [
      { id: 'a', partNumber: 'B-2', name: 'Bremse', active: true },
      { id: 'b', partNumber: 'A-1', name: 'Achse', active: true },
      { id: 'c', partNumber: 'C-3', name: 'Inaktiv', active: false },
    ]
    renderTable({ rows: many, state: { sort: 'partNumber,asc' } })
    const cells = screen.getAllByRole('cell').filter((c) => /^[A-C]-\d$/.test(c.textContent ?? ''))
    expect(cells.map((c) => c.textContent)).toEqual(['A-1', 'B-2'])
  })
})
