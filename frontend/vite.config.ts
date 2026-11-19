/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backend = env.VITE_BACKEND_URL || 'http://localhost:8080'
  const proxied = { target: backend, changeOrigin: true }

  return {
    base: '/',
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      port: 5173,
      proxy: { '/api': proxied, '/actuator': proxied, '/v3': proxied },
    },
    build: {
      outDir: 'dist',
      sourcemap: true,
      // the lazy PDF preview chunk (pdf.js) is ~650 kB; everything eager stays well below
      chunkSizeWarningLimit: 700,
      rolldownOptions: {
        output: {
          codeSplitting: {
            groups: [
              { name: 'react', test: /node_modules[\\/](react|react-dom|scheduler)[\\/]/ },
              { name: 'tanstack', test: /node_modules[\\/]@tanstack[\\/]/ },
              {
                name: 'ui',
                test: /node_modules[\\/](radix-ui|@radix-ui|lucide-react|sonner|next-themes|clsx|tailwind-merge|class-variance-authority)[\\/]/,
              },
              // PDF preview is lazy-loaded (review/revision views) – keep it out of the eager vendor chunk
              {
                name: 'pdf',
                test: /node_modules[\\/](react-pdf|pdfjs-dist|make-cancellable-promise|make-event-props|merge-refs)[\\/]/,
              },
              { name: 'vendor', test: /node_modules[\\/]/ },
            ],
          },
        },
      },
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      css: false,
    },
  }
})
