import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'

import i18n from '@/lib/i18n'
import { renderWithProviders } from '@/test/render'

import { DocumentList } from './Documents'

describe('<DocumentList>', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('de')
  })

  it('shows received documents (metadata only) without preview/download and with a retrieval hint', () => {
    renderWithProviders(
      <DocumentList
        documents={[
          {
            id: null,
            fileName: 'messprotokoll.pdf',
            mediaType: 'application/pdf',
            size: 2048,
            sha256: 'sha256:9f2c4e1a7b3d5f60812a4c6e8b0d2f4a6c8e0b2d4f6a8c0e2b4d6f8a0c2e4b6d',
          },
        ]}
      />,
    )
    expect(screen.getByText('messprotokoll.pdf')).toBeInTheDocument()
    expect(screen.getByText('application/pdf')).toBeInTheDocument()
    expect(screen.getByText('Abruf des Dokuments über Catena-X folgt')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Vorschau|herunterladen/i })).not.toBeInTheDocument()
  })
})
