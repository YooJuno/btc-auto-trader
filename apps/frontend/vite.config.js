import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
const defaultAllowedHosts = [
  'localhost',
  '127.0.0.1',
  'btc-trading-agent.com',
  'www.btc-trading-agent.com',
]

const currentDir = dirname(fileURLToPath(import.meta.url))

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, currentDir, '')
  const allowedHosts = env.VITE_ALLOWED_HOSTS
    ? env.VITE_ALLOWED_HOSTS.split(',').map((host) => host.trim()).filter(Boolean)
    : defaultAllowedHosts

  return {
    plugins: [react()],
    server: {
      allowedHosts,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: false,
        },
        '/oauth2': {
          target: 'http://localhost:8080',
          changeOrigin: false,
        },
        '/login': {
          target: 'http://localhost:8080',
          changeOrigin: false,
        },
      },
    },
    preview: {
      allowedHosts,
    },
  }
})
