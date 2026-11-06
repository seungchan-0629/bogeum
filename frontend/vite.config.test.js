import assert from 'node:assert/strict'
import test from 'node:test'
import { once } from 'node:events'
import { createServer as createHttpServer } from 'node:http'
import { createServer } from 'vite'
import config from './vite.config.js'

test('계좌 콜백·관리자 화면은 API 프록시를 거치지 않고 프론트에서 연다', { timeout: 15000 }, async () => {
  const server = await createServer({
    ...config,
    configFile: false,
    server: { ...config.server, host: '127.0.0.1', port: 0, hmr: false },
  })
  try {
    await server.listen()
    const origin = `http://127.0.0.1:${server.httpServer.address().port}`
    for (const path of ['/mypage/openbanking/callback?code=test&state=test', '/admin/properties']) {
      const response = await fetch(origin + path, { headers: { Accept: 'text/html' } })
      assert.equal(response.status, 200)
      assert.match(response.headers.get('content-type'), /text\/html/)
      assert.match(await response.text(), /id="root"/)
    }
  } finally {
    await server.close()
  }
})

test('로그인·회원 조회·기존 API를 프록시로 연속 호출해도 연결이 끊기지 않는다', { timeout: 30000 }, async () => {
  const backend = createHttpServer((request, response) => {
    request.resume()
    request.on('end', () => {
      response.writeHead(200, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify({ ok: true }))
    })
  })
  backend.listen(0, '127.0.0.1')
  await once(backend, 'listening')
  const target = `http://127.0.0.1:${backend.address().port}`
  const proxy = Object.fromEntries(['/auth', '/users', '/api'].map((path) => {
    const current = config.server.proxy[path]
    return [path, { ...(typeof current === 'string' ? { changeOrigin: true } : current), target }]
  }))
  const server = await createServer({
    ...config,
    configFile: false,
    server: { ...config.server, host: '127.0.0.1', port: 0, hmr: false, proxy },
  })
  try {
    await server.listen()
    const origin = `http://127.0.0.1:${server.httpServer.address().port}`
    const paths = ['/auth/login', '/users/me', '/api/v1/agents/me']
    for (let index = 0; index < 60; index += 1) {
      const path = paths[index % paths.length]
      const response = await fetch(origin + path,
        path === '/auth/login' ? { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' } : {})
      assert.equal(response.status, 200)
      assert.deepEqual(await response.json(), { ok: true })
    }
  } finally {
    await server.close()
    backend.closeAllConnections()
    await new Promise((resolve) => backend.close(resolve))
  }
})
