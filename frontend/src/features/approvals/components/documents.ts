import { approvalsApi, type DocumentDto, type TransferStatus } from '../api'

/** Upload whitelist (server checks magic bytes; this only avoids pointless uploads). */
export const ACCEPTED_DOCUMENT_TYPES = '.pdf,.png,.jpg,.jpeg,.webp,.xlsx,.docx,.csv,.txt,.zip'
const ACCEPTED = ACCEPTED_DOCUMENT_TYPES.split(',')
/** Default of PPAP_UPLOAD_MAX_SIZE; the server enforces the configured value. */
export const MAX_DOCUMENT_BYTES = 50 * 1024 * 1024

export function documentFileError(file: File): 'UNSUPPORTED_FILE_TYPE' | 'FILE_TOO_LARGE' | undefined {
  const name = file.name.toLowerCase()
  if (!ACCEPTED.some((ext) => name.endsWith(ext))) return 'UNSUPPORTED_FILE_TYPE'
  if (file.size > MAX_DOCUMENT_BYTES) return 'FILE_TOO_LARGE'
  return undefined
}

export type PreviewKind = 'pdf' | 'image' | 'none'

export function previewKind(doc: Pick<DocumentDto, 'mediaType' | 'fileName'>): PreviewKind {
  const type = doc.mediaType.toLowerCase()
  if (type === 'application/pdf' || doc.fileName.toLowerCase().endsWith('.pdf')) return 'pdf'
  if (['image/png', 'image/jpeg', 'image/webp'].includes(type)) return 'image'
  return 'none'
}

/** SHA-256 (hex) of a blob – verifies the stored document hash in the preview (AK-07-4). */
export async function sha256Hex(blob: Blob): Promise<string | undefined> {
  if (!globalThis.crypto?.subtle) return undefined
  const digest = await globalThis.crypto.subtle.digest('SHA-256', await blob.arrayBuffer())
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('')
}

export function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export async function downloadDocument(doc: DocumentDto) {
  if (!doc.id) return
  saveBlob(await approvalsApi.getDocumentContent(doc.id, 'attachment'), doc.fileName)
}

/** Transfer state; references without one (older payloads) are LOCAL when stored, otherwise NOT_AVAILABLE. */
export function transferStatusOf(doc: Pick<DocumentDto, 'id' | 'transferStatus'>): TransferStatus {
  return doc.transferStatus ?? (doc.id ? 'LOCAL' : 'NOT_AVAILABLE')
}

/** Content is stored locally → preview and download work (contract: `id` set once available). */
export function isStored(doc: Pick<DocumentDto, 'id'>): boolean {
  return Boolean(doc.id)
}
