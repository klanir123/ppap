import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '@/lib/i18n'
import { renderWithProviders } from '@/test/render'

import { ImportWizard } from './ImportWizard'
import type { ImportPreview, ImportResult } from './types'

const preview: ImportPreview = {
  entity: 'parts',
  fileName: 'teile.csv',
  sheet: null,
  delimiter: ';',
  columns: ['Sachnummer', 'Bezeichnung', 'Notiz'],
  suggestedMapping: { Sachnummer: 'partNumber', Bezeichnung: null, Notiz: null },
  targetFields: [
    { name: 'partNumber', required: true, labelKey: 'masterdata.parts.partNumber' },
    { name: 'nameDe', required: true, labelKey: 'masterdata.parts.nameDe' },
    { name: 'material', required: false },
  ],
  rows: [
    { rowNumber: 2, values: { partNumber: 'D10-1' }, status: 'OK', action: 'CREATE', messages: [] },
    {
      rowNumber: 3,
      values: { partNumber: 'D10 ?' },
      status: 'ERROR',
      action: 'CREATE',
      messages: [{ field: 'partNumber', code: 'INVALID_PART_NUMBER', message: 'invalid' }],
    },
  ],
  summary: { total: 2, ok: 1, warnings: 0, errors: 1, create: 2, update: 0, unchanged: 0 },
}

const result: ImportResult = {
  entity: 'parts',
  created: 1,
  updated: 0,
  unchanged: 0,
  failed: 1,
  failures: [{ rowNumber: 3, messages: [{ field: 'partNumber', code: 'INVALID_PART_NUMBER' }] }],
}

function setup() {
  const handlers = {
    preview: vi.fn().mockResolvedValue(preview),
    execute: vi.fn().mockResolvedValue(result),
    errorReport: vi.fn().mockResolvedValue(new Blob(['row;message'], { type: 'text/csv' })),
  }
  const onCompleted = vi.fn()
  renderWithProviders(
    <ImportWizard
      open
      onOpenChange={vi.fn()}
      entity="parts"
      entityLabel="Teile"
      onCompleted={onCompleted}
      {...handlers}
    />,
  )
  return { ...handlers, onCompleted, user: userEvent.setup() }
}

const csv = (size = 20) => new File(['x'.repeat(size)], 'teile.csv', { type: 'text/csv' })

describe('ImportWizard', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('de')
  })

  it('rejects files over 10 MB and unsupported types on the client', async () => {
    const { user, preview: previewFn } = setup()
    const input = screen.getByLabelText('Datei auswählen', { selector: 'input' })
    const big = new File([new Uint8Array(11 * 1024 * 1024)], 'gross.csv', { type: 'text/csv' })
    await user.upload(input, big)
    expect(screen.getByRole('alert')).toHaveTextContent('größer als 10 MB')
    expect(screen.getByRole('button', { name: 'Datei prüfen' })).toBeDisabled()

    fireEvent.change(input, { target: { files: [new File(['x'], 'teile.pdf', { type: 'application/pdf' })] } })
    expect(screen.getByRole('alert')).toHaveTextContent('Nur CSV- und XLSX')
    expect(previewFn).not.toHaveBeenCalled()
  })

  it('runs upload → mapping → re-validate → execute → result', async () => {
    const { user, preview: previewFn, execute, errorReport, onCompleted } = setup()

    await user.upload(screen.getByLabelText('Datei auswählen', { selector: 'input' }), csv())
    await user.click(screen.getByRole('button', { name: 'Datei prüfen' }))
    expect(previewFn).toHaveBeenCalledWith(expect.any(File), undefined)

    // step 2: missing required field blocks execution
    expect(await screen.findByText(/Nicht zugeordnete Pflichtfelder: Bezeichnung \(DE\)/)).toBeInTheDocument()
    const run = screen.getByRole('button', { name: 'Import ausführen' })
    expect(run).toBeDisabled()

    // validation report with status badge + translated message
    const report = screen.getByRole('table', { name: 'Validierungsbericht' })
    expect(within(report).getByRole('status', { name: 'Status: Fehler' })).toBeInTheDocument()
    expect(within(report).getByText(/Erlaubt sind Buchstaben/)).toBeInTheDocument()

    // map "Bezeichnung" → nameDe; already used fields are disabled
    await user.click(screen.getByRole('combobox', { name: 'Zielfeld für Spalte Bezeichnung' }))
    expect(screen.getByRole('option', { name: 'Sachnummer *' })).toHaveAttribute('aria-disabled', 'true')
    await user.click(screen.getByRole('option', { name: 'Bezeichnung (DE) *' }))
    expect(screen.getByText('Zuordnung geändert – bitte erneut prüfen.')).toBeInTheDocument()

    previewFn.mockResolvedValueOnce({
      ...preview,
      suggestedMapping: { Sachnummer: 'partNumber', Bezeichnung: 'nameDe', Notiz: null },
    })
    await user.click(screen.getByRole('button', { name: 'Mit Zuordnung erneut prüfen' }))
    expect(previewFn).toHaveBeenLastCalledWith(expect.any(File), {
      Sachnummer: 'partNumber',
      Bezeichnung: 'nameDe',
      Notiz: null,
    })

    // errors present → must opt in to skipping erroneous rows
    expect(await screen.findByText('Alle Pflichtfelder sind zugeordnet.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Import ausführen' })).toBeDisabled()
    await user.click(screen.getByRole('checkbox', { name: /Fehlerhafte Zeilen überspringen/ }))
    await user.click(screen.getByRole('button', { name: 'Import ausführen' }))
    expect(execute).toHaveBeenCalledWith(expect.any(File), expect.objectContaining({ Bezeichnung: 'nameDe' }), true)

    // step 3: result + error report
    expect(await screen.findByText('Import abgeschlossen')).toBeInTheDocument()
    expect(screen.getByText('1 neu')).toBeInTheDocument()
    expect(screen.getByText('1 fehlgeschlagen')).toBeInTheDocument()
    expect(onCompleted).toHaveBeenCalledWith(result)

    globalThis.URL.createObjectURL = vi.fn(() => 'blob:report')
    globalThis.URL.revokeObjectURL = vi.fn()
    await user.click(screen.getByRole('button', { name: 'Fehlerbericht herunterladen (CSV)' }))
    expect(errorReport).toHaveBeenCalledWith(expect.any(File), expect.any(Object))
  })
})
