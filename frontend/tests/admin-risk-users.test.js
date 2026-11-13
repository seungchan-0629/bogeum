import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'

let server
let api
let getAdminRiskUsers

before(async () => {
  server = await createServer({
    configFile: false, plugins: [react()], cacheDir: 'node_modules/.vite-admin-risk-users-test',
    server: { middlewareMode: true, hmr: false },
  })
  api = (await server.ssrLoadModule('/src/utils/jwtUtil.js')).api
  getAdminRiskUsers = (await server.ssrLoadModule('/src/api/admin/index.js')).getAdminRiskUsers
})

after(async () => { await server?.close() })

test('위험 사용자 API는 전용 주소·유형·이름·페이지·취소 신호를 전달한다', async () => {
  const original = api.defaults.adapter
  const controller = new AbortController()
  const data = { riskUsers: [], page: 2, totalPages: 3, totalElements: 12 }
  api.defaults.adapter = async (config) => {
    assert.equal(config.method, 'get')
    assert.equal(config.url, '/api/admin/risk-users')
    assert.deepEqual(config.params, { type: 'TENANT', keyword: '보금 %_', page: 2 })
    assert.equal(config.signal, controller.signal)
    return { status: 200, data, config, headers: {} }
  }
  try {
    assert.deepEqual(await getAdminRiskUsers('TENANT', '보금 %_', 2, controller.signal), data)
  } finally {
    api.defaults.adapter = original
  }
})
