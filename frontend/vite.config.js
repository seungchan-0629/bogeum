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
      // AGENT_001: API만 변환하고 화면 /admin/agents의 직접 진입·새로고침은 Vite가 처리한다.
      '^/api/admin/agents(?:\\?|$)': {
        ...backendProxy,
        rewrite: (path) => path.replace(/^\/api\/admin\/agents/, '/admin/agents'),
      },
      '/api': backendProxy,
      // 매물 목록 API(/property, /property?cursor...)만 정확히 넘긴다. 화면 주소 /properties는 Vite가 처리한다 (PROPERTY_004)
      '^/property(\\?.*)?$': backendProxy,
      // 매물 API(/property/...). 끝의 /가 있어야 화면 주소 /properties/...가 백엔드로 넘어가지 않는다
      '/property/': backendProxy,
      // 계약 API(/contract/..., /contract-details). 화면 주소에는 /contract로 시작하는 곳이 없다
      '/contract/': backendProxy,
      '/contract-details': backendProxy,
      // AS API(/as-requests/...). 화면 주소 /care/as와 겹치지 않는다
      '/as-requests': backendProxy,
      '/auth': backendProxy,
      '/users': backendProxy,
      '/openbanking': backendProxy,
    },
  },
})
