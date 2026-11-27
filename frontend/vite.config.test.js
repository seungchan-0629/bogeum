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
    for (const path of ['/mypage/openbanking/callback?code=test&state=test', '/admin/rent', '/admin/rent?keyword=test', '/admin/properties', '/admin/agents', '/admin/risk-users', '/admin/risk-users?type=TENANT', '/notifications']) {
      const response = await fetch(origin + path, { headers: { Accept: 'text/html' } })
      assert.equal(response.status, 200)
      assert.match(response.headers.get('content-type'), /text\/html/)
      assert.match(await response.text(), /id="root"/)
    }
  } finally {
    await server.close()
  }
})

test('중개사 API만 백엔드 주소로 변환하고 검색·페이지·인증 헤더를 유지한다', { timeout: 15000 }, async () => {
  const backend = createHttpServer((request, response) => {
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify({ url: request.url, authorization: request.headers.authorization }))
  })
  backend.listen(0, '127.0.0.1')
  await once(backend, 'listening')
  const key = '^/api/admin/agents(?:\\?|$)'
  const proxy = Object.fromEntries(Object.entries(config.server.proxy).map(([path, value]) => [
    path, { ...value, target: `http://127.0.0.1:${backend.address().port}` },
  ]))
  assert.ok(config.server.proxy[key], '일반 /api 프록시보다 먼저 전용 규칙이 필요하다')
  const server = await createServer({
    ...config, configFile: false,
    server: { ...config.server, host: '127.0.0.1', port: 0, hmr: false, proxy },
  })
  try {
    await server.listen()
    const origin = `http://127.0.0.1:${server.httpServer.address().port}`
    const response = await fetch(origin + '/api/admin/agents?keyword=%EC%84%9C%EC%9A%B8&page=2', {
      headers: { Authorization: 'Bearer test-admin' },
    })
    assert.equal(response.status, 200)
    const received = await response.json()
    const url = new URL(received.url, origin)
    assert.equal(url.pathname, '/admin/agents')
    assert.equal(url.searchParams.get('keyword'), '서울')
    assert.equal(url.searchParams.get('page'), '2')
    assert.equal(received.authorization, 'Bearer test-admin')
    const exact = await fetch(origin + '/api/admin/agents')
    assert.equal((await exact.json()).url, '/admin/agents')
    const legacy = await fetch(origin + '/api/v1/agents/me')
    assert.equal((await legacy.json()).url, '/api/v1/agents/me')
  } finally {
    await server.close()
    backend.closeAllConnections()
    await new Promise((resolve) => backend.close(resolve))
  }
})

test('위험 사용자 GET·POST·PATCH를 변환하고 본문·JWT·CSRF·기존 경로를 보존한다', { timeout: 15000 }, async () => {
  const backend = createHttpServer((request, response) => {
    response.writeHead(200, { 'Content-Type': 'application/json' })
    let body = ''
    request.setEncoding('utf8')
    request.on('data', (chunk) => { body += chunk })
    request.on('end', () => response.end(JSON.stringify({
      url: request.url, method: request.method, authorization: request.headers.authorization,
      csrf: request.headers['x-xsrf-token'], cookie: request.headers.cookie,
      contentType: request.headers['content-type'], body,
    })))
  })
  backend.listen(0, '127.0.0.1')
  await once(backend, 'listening')
  const keys = Object.keys(config.server.proxy)
  assert.ok(config.server.proxy['^/api/admin/risk-users(?:\\?|$)'], '위험 사용자 전용 프록시가 필요하다')
  assert.ok(keys.indexOf('^/api/admin/risk-users(?:\\?|$)') < keys.indexOf('/api'))
  const proxy = Object.fromEntries(Object.entries(config.server.proxy).map(([path, value]) => [
    path, { ...value, target: `http://127.0.0.1:${backend.address().port}` },
  ]))
  const server = await createServer({
    ...config, configFile: false,
    server: { ...config.server, host: '127.0.0.1', port: 0, hmr: false, proxy },
  })
  try {
    await server.listen()
    const origin = `http://127.0.0.1:${server.httpServer.address().port}`
    const query = new URLSearchParams({ type: 'LANDLORD', keyword: '보금 %_', page: '2' })
    const response = await fetch(`${origin}/api/admin/risk-users?${query}`, {
      headers: { Authorization: 'Bearer test-admin' },
    })
    assert.equal(response.status, 200)
    const received = await response.json()
    const url = new URL(received.url, origin)
    assert.equal(url.pathname, '/admin/risk-users')
    assert.deepEqual(Object.fromEntries(url.searchParams), { type: 'LANDLORD', keyword: '보금 %_', page: '2' })
    assert.equal(received.authorization, 'Bearer test-admin')
    const exact = await fetch(origin + '/api/admin/risk-users')
    assert.equal((await exact.json()).url, '/admin/risk-users')
    const body = { userId: 20, reasonType: 'DEPOSIT_UNPAID', detail: '  보증금\n미지급  ' }
    const posted = await fetch(origin + '/api/admin/risk-users', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer test-admin', 'X-XSRF-TOKEN': 'test-csrf', Cookie: 'BOGEUM_CSRF=test-cookie' },
      body: JSON.stringify(body),
    })
    const receivedPost = await posted.json()
    assert.equal(receivedPost.url, '/admin/risk-users')
    assert.equal(receivedPost.method, 'POST')
    assert.equal(receivedPost.authorization, 'Bearer test-admin')
    assert.equal(receivedPost.csrf, 'test-csrf')
    assert.equal(receivedPost.cookie, 'BOGEUM_CSRF=test-cookie')
    assert.equal(receivedPost.contentType, 'application/json')
    assert.deepEqual(JSON.parse(receivedPost.body), body)
    const patchBody = { reasonType: 'RENT_OVERDUE', detail: '  확인\n내용  ', updateReason: '증빙 확인' }
    const patched = await fetch(origin + '/api/admin/risk-users/123', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer test-admin', 'X-XSRF-TOKEN': 'test-csrf', Cookie: 'BOGEUM_CSRF=test-cookie' },
      body: JSON.stringify(patchBody),
    })
    const receivedPatch = await patched.json()
    assert.equal(receivedPatch.url, '/admin/risk-users/123')
    assert.equal(receivedPatch.method, 'PATCH')
    assert.equal(receivedPatch.authorization, 'Bearer test-admin')
    assert.equal(receivedPatch.csrf, 'test-csrf')
    assert.equal(receivedPatch.cookie, 'BOGEUM_CSRF=test-cookie')
    assert.deepEqual(JSON.parse(receivedPatch.body), patchBody)
    const withQuery = await fetch(origin + '/api/admin/risk-users/123?check=1')
    assert.equal((await withQuery.json()).url, '/admin/risk-users/123?check=1')
    for (const path of ['/api/admin/risk-users/123/extra', '/api/admin/risk-users/123x', '/api/admin/risk-users/-1', '/api/admin/risk-users/extra', '/api/admin/risk-users-other', '/api/v1/agents/me', '/openbanking/admin/institution-token']) {
      const unchanged = await fetch(origin + path)
      assert.equal((await unchanged.json()).url, path)
    }
  } finally {
    await server.close()
    backend.closeAllConnections()
    await new Promise((resolve) => backend.close(resolve))
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

test('월세 납부 API만 변환하고 검색·페이지·JWT 및 비대상 경로를 보존한다', { timeout: 15000 }, async () => {
  const backend = createHttpServer((request, response) => {
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify({ url: request.url, authorization: request.headers.authorization }))
  })
  backend.listen(0, '127.0.0.1')
  await once(backend, 'listening')
  const key = '^/api/admin/rents/payments(?:\\?|$)'
  const keys = Object.keys(config.server.proxy)
  assert.ok(config.server.proxy[key])
  assert.ok(keys.indexOf(key) < keys.indexOf('/api'))
  const proxy = Object.fromEntries(Object.entries(config.server.proxy).map(([path, value]) => [
    path, { ...value, target: 'http://127.0.0.1:' + backend.address().port },
  ]))
  const server = await createServer({
    ...config, configFile: false,
    server: { ...config.server, host: '127.0.0.1', port: 0, hmr: false, proxy },
  })
  try {
    await server.listen()
    const origin = 'http://127.0.0.1:' + server.httpServer.address().port
    const params = { tenantId: '22', keyword: '보금 %_', page: '2' }
    const response = await fetch(origin + '/api/admin/rents/payments?' + new URLSearchParams(params), {
      headers: { Authorization: 'Bearer test-admin' },
    })
    assert.equal(response.status, 200)
    const received = await response.json()
    const url = new URL(received.url, origin)
    assert.equal(url.pathname, '/admin/rents/payments')
    assert.deepEqual(Object.fromEntries(url.searchParams), params)
    assert.equal(received.authorization, 'Bearer test-admin')
    assert.equal((await (await fetch(origin + '/api/admin/rents/payments')).json()).url, '/admin/rents/payments')
    for (const path of ['/api/admin/rents/payments/extra', '/api/admin/rents/payments-other',
      '/api/admin/rents/failures', '/api/v1/agents/me', '/openbanking/admin/institution-token']) {
      assert.equal((await (await fetch(origin + path)).json()).url, path)
    }
  } finally {
    await server.close()
    backend.closeAllConnections()
    await new Promise((resolve) => backend.close(resolve))
  }
})
