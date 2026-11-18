// Drift check (CI): regenerates the API client from ../api/openapi.json into a temp dir
// next to src/api/generated (same depth → identical relative imports) and compares.
import { execSync } from 'node:child_process'
import { existsSync, readdirSync, readFileSync, rmSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const committed = 'src/api/generated'
const temp = 'src/api/.generated-check'

function listFiles(dir) {
  if (!existsSync(dir)) return []
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    return statSync(path).isDirectory() ? listFiles(path) : [path]
  })
}

rmSync(temp, { recursive: true, force: true })
try {
  execSync('npx orval --config orval.config.ts', {
    stdio: ['ignore', 'ignore', 'inherit'],
    env: { ...process.env, ORVAL_OUTPUT_DIR: temp },
  })
  const a = new Set(listFiles(committed).map((f) => relative(committed, f)))
  const b = new Set(listFiles(temp).map((f) => relative(temp, f)))
  const drift = []
  for (const f of new Set([...a, ...b])) {
    if (!a.has(f)) drift.push(`missing in ${committed}: ${f}`)
    else if (!b.has(f)) drift.push(`stale in ${committed}: ${f}`)
    else if (readFileSync(join(committed, f), 'utf8') !== readFileSync(join(temp, f), 'utf8'))
      drift.push(`differs: ${f}`)
  }
  if (drift.length > 0) {
    console.error(`API client is out of date with ../api/openapi.json:\n  ${drift.join('\n  ')}`)
    console.error('Run `npm run api:generate` and commit the result.')
    process.exitCode = 1
  } else {
    console.log('API client is up to date with ../api/openapi.json.')
  }
} finally {
  rmSync(temp, { recursive: true, force: true })
}
