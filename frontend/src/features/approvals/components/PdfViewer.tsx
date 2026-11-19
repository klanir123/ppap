import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Document, Page, pdfjs } from 'react-pdf'

import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

// must live in the module that renders react-pdf components (see react-pdf README)
pdfjs.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url).toString()

/** PDF preview (lazy-loaded chunk): one page at a time, text/annotation layers off. */
export default function PdfViewer({ file, width }: { file: Blob; width: number }) {
  const { t } = useTranslation('approvals')
  const [pages, setPages] = useState(0)
  const [page, setPage] = useState(1)
  return (
    <div className="flex flex-col items-center gap-3">
      <Document
        file={file}
        onLoadSuccess={({ numPages }) => setPages(numPages)}
        loading={<Skeleton className="aspect-[1/1.414] w-full max-w-[640px]" />}
        error={<p className="py-10 text-sm text-destructive">{t('documents.previewFailed')}</p>}
      >
        <Page
          pageNumber={page}
          width={width}
          renderTextLayer={false}
          renderAnnotationLayer={false}
          className="overflow-hidden rounded-md border shadow-sm"
        />
      </Document>
      {pages > 1 ? (
        <div className="flex items-center gap-2" role="group" aria-label={t('documents.pageNav')}>
          <Button
            variant="outline"
            size="icon-sm"
            disabled={page <= 1}
            onClick={() => setPage((p) => p - 1)}
            aria-label={t('documents.prevPage')}
          >
            <ChevronLeft aria-hidden="true" />
          </Button>
          <span className="text-xs text-muted-foreground tabular" aria-live="polite">
            {t('documents.pageOf', { page, pages })}
          </span>
          <Button
            variant="outline"
            size="icon-sm"
            disabled={page >= pages}
            onClick={() => setPage((p) => p + 1)}
            aria-label={t('documents.nextPage')}
          >
            <ChevronRight aria-hidden="true" />
          </Button>
        </div>
      ) : null}
    </div>
  )
}
