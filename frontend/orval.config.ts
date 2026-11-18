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
      // header parameters (If-Match) become function arguments
      headers: true,
      override: {
        mutator: { path: 'src/api/fetcher.ts', name: 'apiFetch' },
        fetch: { includeHttpResponseReturnType: false },
        // queries for GET, mutations for POST/PUT/DELETE
        query: { useSuspenseQuery: false, signal: true },
      },
    },
    hooks: { afterAllFilesWrite: 'prettier --write' },
  },
})
