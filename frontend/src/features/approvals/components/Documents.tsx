import { useMutation, useQuery } from '@tanstack/react-query'
import {
  Download,
  Eye,
  File,
  FileImage,
  FileSpreadsheet,
  FileText,
  Loader2,
  ShieldCheck,
  ShieldX,
  Trash2,
  Upload,
  type LucideIcon,
} from 'lucide-react'
import { lazy, Suspense, useEffect, useId, useMemo, useRef, useState, type DragEvent } from 'react'
import { useTranslation } from 'react-i18next'

import { isApiError } from '@/api/problem'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { translateErrorCode } from '@/lib/problem-errors'
import { statusStyle } from '@/lib/status-style'
import { cn } from '@/lib/utils'

import type { DocumentDto as StoredDocument } from '@/api/generated/model'

import { approvalsApi, type DocumentDto } from '../api'
import { HashText } from './badges'
import {
  ACCEPTED_DOCUMENT_TYPES,
  documentFileError,
  downloadDocument,
  isStored,
  previewKind,
  sha256Hex,
  transferStatusOf,
} from './documents'
import { RefetchDocumentButton, TransferStatusBadge } from './DocumentTransfer'
import { useFormat } from './format'

const PdfViewer = lazy(() => import('./PdfViewer'))

function iconFor(doc: Pick<DocumentDto, 'mediaType' | 'fileName'>): LucideIcon {
  const kind = previewKind(doc)
  if (kind === 'pdf') return FileText
  if (kind === 'image') return FileImage
  if (/sheet|csv|excel/.test(doc.mediaType) || /\.(xlsx|csv)$/i.test(doc.fileName)) return FileSpreadsheet
  return File
}

interface DocumentListProps {
  documents: DocumentDto[]
  onPreview?: (doc: DocumentDto) => void
  onRemove?: (doc: DocumentDto) => void
  removingId?: string
  emptyText?: string
  /** Case of received documents → "Erneut abrufen" for failed pulls. */
  caseId?: string
}

/**
 * Documents with name, size, SHA-256 (copy), transfer state of received documents (pull over EDC),
 * preview/download once stored locally and optional remove.
 */
export function DocumentList({ documents, onPreview, onRemove, removingId, emptyText, caseId }: DocumentListProps) {
  const { t } = useTranslation('approvals')
  const fmt = useFormat()
  const [previewing, setPreviewing] = useState<DocumentDto | null>(null)
  const preview = onPreview ?? setPreviewing

  if (documents.length === 0) {
    return <p className="text-sm text-muted-foreground">{emptyText ?? t('documents.empty')}</p>
  }
  return (
    <>
      <ul className="divide-y rounded-md border">
        {documents.map((doc) => {
          const Icon = iconFor(doc)
          const stored = isStored(doc)
          const transfer = transferStatusOf(doc)
          return (
            <li
              key={doc.id ?? doc.sha256}
              data-transfer-status={transfer}
              className="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2"
            >
              <Icon aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
              <div className="min-w-0 flex-1">
                {stored ? (
                  <button
                    type="button"
                    className="block max-w-full truncate rounded-sm text-left text-sm font-medium underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
                    onClick={() => preview(doc)}
                    title={doc.fileName}
                  >
                    {doc.fileName}
                  </button>
                ) : (
                  <span className="block max-w-full truncate text-sm font-medium" title={doc.fileName}>
                    {doc.fileName}
                  </span>
                )}
                <div className="flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
                  <span className="tabular">{fmt.bytes(doc.size)}</span>
                  {stored ? null : (
                    <>
                      <span aria-hidden="true">·</span>
                      <span>{doc.mediaType}</span>
                    </>
                  )}
                  <span aria-hidden="true">·</span>
                  <span className="sr-only">{t('documents.sha256')}: </span>
                  <HashText hash={doc.sha256} label={t('documents.copyHash', { name: doc.fileName })} />
                </div>
                {transfer !== 'LOCAL' ? (
                  <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1">
                    <TransferStatusBadge doc={doc} />
                    {transfer === 'FAILED' && doc.transferError ? (
                      <span className="text-xs text-(--status-failed-fg)">
                        {translateErrorCode(doc.transferError, doc.transferError)}
                      </span>
                    ) : null}
                  </div>
                ) : null}
              </div>
              <div className="flex items-center gap-0.5">
                {stored ? (
                  <>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label={t('documents.preview', { name: doc.fileName })}
                      onClick={() => preview(doc)}
                    >
                      <Eye aria-hidden="true" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label={t('documents.download', { name: doc.fileName })}
                      onClick={() => void downloadDocument(doc)}
                    >
                      <Download aria-hidden="true" />
                    </Button>
                  </>
                ) : caseId ? (
                  <RefetchDocumentButton caseId={caseId} doc={doc} />
                ) : null}
                {onRemove ? (
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={t('documents.remove', { name: doc.fileName })}
                    disabled={removingId === doc.id}
                    onClick={() => onRemove(doc)}
                  >
                    {removingId === doc.id ? (
                      <Loader2 aria-hidden="true" className="animate-spin" />
                    ) : (
                      <Trash2 aria-hidden="true" />
                    )}
                  </Button>
                ) : null}
              </div>
            </li>
          )
        })}
      </ul>
      {onPreview ? null : <DocumentPreviewDialog doc={previewing} onClose={() => setPreviewing(null)} />}
    </>
  )
}

interface PendingUpload {
  key: string
  name: string
  error?: string
}

/** Drop zone + file picker. Uploads via POST /documents, then hands the DocumentDto to the caller. */
export function DocumentUpload({
  onUploaded,
  disabled,
  compact,
  label,
}: {
  onUploaded: (doc: StoredDocument) => Promise<unknown> | void
  disabled?: boolean
  compact?: boolean
  label?: string
}) {
  const { t } = useTranslation('approvals')
  const inputId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragOver, setDragOver] = useState(false)
  const [pending, setPending] = useState<PendingUpload[]>([])
  const upload = useMutation({ meta: { silent: true }, mutationFn: (file: File) => approvalsApi.uploadDocument(file) })

  async function handle(files: FileList | File[] | null) {
    if (!files) return
    for (const file of Array.from(files)) {
      const key = `${file.name}-${file.size}-${Date.now()}`
      const localError = documentFileError(file)
      if (localError) {
        setPending((p) => [...p, { key, name: file.name, error: translateErrorCode(localError) }])
        continue
      }
      setPending((p) => [...p, { key, name: file.name }])
      try {
        const doc = await upload.mutateAsync(file)
        await onUploaded(doc)
        setPending((p) => p.filter((x) => x.key !== key))
      } catch (error) {
        const code = isApiError(error) ? error.problem?.code : undefined
        const message = translateErrorCode(code, isApiError(error) ? error.problem?.detail : undefined)
        setPending((p) => p.map((x) => (x.key === key ? { ...x, error: message } : x)))
      }
    }
  }

  const onDrop = (e: DragEvent) => {
    e.preventDefault()
    setDragOver(false)
    if (!disabled) void handle(e.dataTransfer.files)
  }

  return (
    <div className="space-y-2">
      <div
        onDragOver={(e) => {
          e.preventDefault()
          if (!disabled) setDragOver(true)
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={onDrop}
        className={cn(
          'flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed text-center transition-colors sm:flex-row sm:gap-3',
          compact ? 'px-3 py-3' : 'px-4 py-6',
          dragOver && 'border-participant bg-participant-soft/50',
          disabled && 'opacity-60',
        )}
      >
        <Upload aria-hidden="true" className="size-4 text-muted-foreground" />
        <p className="text-sm text-muted-foreground">{t('documents.dropHint')}</p>
        <input
          ref={inputRef}
          id={inputId}
          type="file"
          multiple
          accept={ACCEPTED_DOCUMENT_TYPES}
          className="sr-only"
          tabIndex={-1}
          disabled={disabled}
          onChange={(e) => {
            void handle(e.target.files)
            e.target.value = ''
          }}
        />
        <Button type="button" variant="outline" size="sm" disabled={disabled} onClick={() => inputRef.current?.click()}>
          {label ?? t('documents.choose')}
        </Button>
      </div>
      <p className="text-xs text-muted-foreground">{t('documents.constraints')}</p>
      {pending.length > 0 ? (
        <ul className="space-y-1" aria-live="polite">
          {pending.map((p) => (
            <li key={p.key} className="flex items-center gap-2 text-xs">
              {p.error ? (
                <ShieldX aria-hidden="true" className="size-3.5 text-destructive" />
              ) : (
                <Loader2 aria-hidden="true" className="size-3.5 animate-spin text-muted-foreground" />
              )}
              <span className="truncate font-medium">{p.name}</span>
              <span className={p.error ? 'text-destructive' : 'text-muted-foreground'}>
                {p.error ?? t('documents.uploading')}
              </span>
              {p.error ? (
                <Button
                  variant="ghost"
                  size="xs"
                  className="ml-auto"
                  onClick={() => setPending((list) => list.filter((x) => x.key !== p.key))}
                >
                  {t('documents.dismiss')}
                </Button>
              ) : null}
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

/** Inline preview: PDF (react-pdf), images, else download hint; verifies the SHA-256 client-side. */
export function DocumentPreview({ doc }: { doc: DocumentDto }) {
  const { t } = useTranslation('approvals')
  const containerRef = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(560)
  const kind = previewKind(doc)
  const content = useQuery({
    queryKey: ['/api/v1/documents', doc.id, 'content'],
    queryFn: async () => {
      const blob = await approvalsApi.getDocumentContent(doc.id!, 'inline')
      const hash = await sha256Hex(blob)
      return { blob, hash }
    },
    staleTime: Infinity,
    enabled: Boolean(doc.id),
  })

  useEffect(() => {
    const el = containerRef.current
    if (!el) return
    const observer = new ResizeObserver(([entry]) => {
      if (entry) setWidth(Math.min(720, Math.max(240, Math.floor(entry.contentRect.width))))
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  const imageUrl = useMemo(
    () => (kind === 'image' && content.data ? URL.createObjectURL(content.data.blob) : undefined),
    [kind, content.data],
  )
  useEffect(() => () => (imageUrl ? URL.revokeObjectURL(imageUrl) : undefined), [imageUrl])

  const expected = doc.sha256.replace(/^sha256:/i, '').toLowerCase()
  const verified = content.data?.hash === undefined ? undefined : content.data.hash === expected

  return (
    <div ref={containerRef} className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <HashVerification verified={verified} loading={content.isPending} />
        <Button variant="outline" size="sm" onClick={() => void downloadDocument(doc)}>
          <Download aria-hidden="true" />
          {t('documents.downloadShort')}
        </Button>
      </div>
      {content.isPending ? (
        <Skeleton className="aspect-[1/1.2] w-full" />
      ) : content.isError ? (
        <p className="py-8 text-center text-sm text-destructive">{t('documents.previewFailed')}</p>
      ) : kind === 'pdf' ? (
        <Suspense fallback={<Skeleton className="aspect-[1/1.414] w-full" />}>
          <PdfViewer file={content.data.blob} width={width} />
        </Suspense>
      ) : kind === 'image' && imageUrl ? (
        <img src={imageUrl} alt={doc.fileName} className="mx-auto max-h-[70svh] rounded-md border object-contain" />
      ) : (
        <p className="rounded-md border border-dashed px-4 py-8 text-center text-sm text-muted-foreground">
          {t('documents.noPreview')}
        </p>
      )}
    </div>
  )
}

function HashVerification({ verified, loading }: { verified: boolean | undefined; loading: boolean }) {
  const { t } = useTranslation('approvals')
  if (loading) return <span className="text-xs text-muted-foreground">{t('documents.hashChecking')}</span>
  if (verified === undefined)
    return <span className="text-xs text-muted-foreground">{t('documents.hashUnchecked')}</span>
  const Icon = verified ? ShieldCheck : ShieldX
  return (
    <span
      role="status"
      style={statusStyle(verified ? 'valid' : 'invalid')}
      className="inline-flex h-6 items-center gap-1.5 rounded-full border border-(--sb-border) bg-(--sb-bg) px-2.5 text-xs font-medium text-(--sb-fg)"
    >
      <Icon aria-hidden="true" className="size-3.5" />
      {verified ? t('documents.hashOk') : t('documents.hashMismatch')}
    </span>
  )
}

export function DocumentPreviewDialog({ doc, onClose }: { doc: DocumentDto | null; onClose: () => void }) {
  const { t } = useTranslation('approvals')
  const fmt = useFormat()
  return (
    <Dialog open={doc !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[92svh] relative overflow-y-auto sm:max-w-3xl">
        {doc ? (
          <>
            <DialogHeader>
              <DialogTitle className="truncate pr-6">{doc.fileName}</DialogTitle>
              <DialogDescription>
                {t('documents.meta', { size: fmt.bytes(doc.size), type: doc.mediaType })}
              </DialogDescription>
            </DialogHeader>
            <DocumentPreview doc={doc} />
          </>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}
