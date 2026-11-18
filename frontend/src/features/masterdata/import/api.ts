import { executeImport, importErrorReport, previewImport } from '@/api/generated/endpoints/imports/imports'

import type { ColumnMapping, ImportEntity, ImportHandlers, ImportPreview, ImportResult } from './types'

/**
 * Stateless two-step import over the generated client: the file is sent again for execute / error report.
 * `mapping` is a text part with the JSON object `{column: field|null}` (see spec `PreviewImportBody`).
 */
export function importApi(entity: ImportEntity): ImportHandlers {
  const mappingPart = (mapping?: ColumnMapping) => (mapping ? JSON.stringify(mapping) : undefined)
  return {
    preview: (file, mapping) =>
      previewImport(entity, { file, mapping: mappingPart(mapping) }) satisfies Promise<ImportPreview>,
    execute: (file, mapping, skipErrors) =>
      executeImport(entity, { file, mapping: mappingPart(mapping) }, { skipErrors }) satisfies Promise<ImportResult>,
    errorReport: (file, mapping) => importErrorReport(entity, { file, mapping: mappingPart(mapping) }),
  }
}
