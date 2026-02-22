import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
const defaultAllowedHosts = [
  'localhost',
  '127.0.0.1',
  'btc-trading-agent.com',
  'www.btc-trading-agent.com',
]

const allowedHosts = process.env.VITE_ALLOWED_HOSTS
  ? process.env.VITE_ALLOWED_HOSTS.split(',').map((host) => host.trim()).filter(Boolean)
  : defaultAllowedHosts

export default defineConfig({
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
})
