import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import type { Connect } from 'vite'

// Injected by Spring Boot's /s3-viewer/ui/config.js in production.
// In dev mode the plugin below serves a mock so Spring Boot isn't required.
const DEV_API_BASE = '/s3-viewer/api/v1'

const devConfigPlugin = () => ({
  name: 's3-viewer-dev-config',
  configureServer(server: { middlewares: { use: (path: string, handler: Connect.NextHandleFunction) => void } }) {
    server.middlewares.use('/config.js', (_req, res) => {
      res.setHeader('Content-Type', 'application/javascript')
      res.end(`window.__S3_VIEWER_CONFIG__ = { apiBase: '${DEV_API_BASE}', readOnlyAccess: false };`)
    })
  },
})

export default defineConfig(({ command }) => ({
  plugins: [react(), devConfigPlugin()],

  // Relative paths in the build so the app works behind any reverse-proxy prefix.
  // Dev server stays at '/' so HMR and the proxy rules work normally.
  base: command === 'build' ? './' : '/',

  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },

  server: {
    port: 3000,
    proxy: {
      '/s3-viewer/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },

  build: {
    outDir: 'dist',
    sourcemap: false,
  },
}))
