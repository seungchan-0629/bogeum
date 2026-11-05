import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { Agent } from 'node:http'

// 요청마다 연결을 닫을 때 발생하는 ECONNRESET을 피하려고 백엔드 연결을 재사용한다.
const backendProxy = {
  target: 'http://localhost:8080',
  changeOrigin: true,
  agent: new Agent({ keepAlive: true }),
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': backendProxy,
      '/auth': backendProxy,
      '/users': backendProxy,
      '/openbanking': backendProxy,
    },
  },
})
