import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
const backendProxy = {
  target: 'http://localhost:8080',
  changeOrigin: true,
}

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': backendProxy,
      '/uploads': backendProxy,
      '/v3/api-docs': backendProxy,
      '/swagger-ui': backendProxy,
    },
  },
})
