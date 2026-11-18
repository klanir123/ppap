import { defineConfig } from 'orval'

/**
 * API client generation from the backend-exported spec (single source of truth).
 * - `npm run api:generate` → writes src/api/generated
 * - `npm run api:check`    → regenerates into a sibling temp dir (ORVAL_OUTPUT_DIR) and fails on drift
 */
const input = '../api/openapi.json'
const outputDir = process.env.ORVAL_OUTPUT_DIR ?? 'src/api/generated'

export default defineConfig({
  ppap: {
    input: { target: input },
    output: {
      mode: 'tags-split',
      target: `${outputDir}/endpoints`,
      schemas: `${outputDir}/model`,
      client: 'react-query',
      httpClient: 'fetch',
      clean: true,
      override: {
        mutator: { path: 'src/api/fetcher.ts', name: 'apiFetch' },
        fetch: { includeHttpResponseReturnType: false },
        query: { useQuery: true, useSuspenseQuery: false, signal: true },
      },
    },
    hooks: { afterAllFilesWrite: 'prettier --write' },
  },
})
