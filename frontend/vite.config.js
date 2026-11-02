import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      // 매물 API(/property/...). 끝의 /가 있어야 화면 주소 /properties/...가 백엔드로 넘어가지 않는다
      '/property/': 'http://localhost:8080',
    },
  },
})
