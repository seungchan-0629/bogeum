import assert from 'node:assert/strict'
import test from 'node:test'
import { AxiosError } from 'axios'
import { createAuthClient } from './jwtUtil.js'

function cookieStore() {
  const values = new Map()
  return {
    setCookie(name, value, days) { values.set(name, { value, days }) },
    getCookie(name) { return values.get(name)?.value },
    removeCookie(name) { values.delete(name) },
    values,
  }
}

function setup(cookies = cookieStore()) {
  const state = {
    user: null,
    login(user) { this.user = user },
    logout() { this.user = null },
  }
  const client = createAuthClient({ getState: () => state }, cookies)
  return { ...client, state, cookies }
}

function response(config, data, status = 200) {
  if (data?.accessToken) {
    data = { refreshToken: `refresh-${data.accessToken}`, refreshExpiresAt: '2030-01-01T00:00:00Z', ...data }
  }
  return { config, data, status, statusText: '', headers: {} }
}

function reject(config, status, code) {
  throw new AxiosError(code, undefined, config, undefined, response(config, { code }, status))
}

function publicAdapter(client, handleRequest) {
  client.publicApi.defaults.adapter = async (config) => {
    if (config.url === '/v1/auth/csrf') {
      return response(config, { headerName: 'X-XSRF-TOKEN', token: 'csrf-value' })
    }
    assert.equal(config.headers.get('X-XSRF-TOKEN'), 'csrf-value')
    return handleRequest(config)
  }
}

const user = { userId: 1, name: '보금', role: 'USER' }

test('로그인 뒤 보호 요청에 토큰을 붙이고 로그아웃하면 제거한다', async () => {
  const client = setup()
  publicAdapter(client, (config) => response(config,
    config.url === '/v1/auth/login' ? { user, accessToken: 'access-1' } : null))
  assert.deepEqual(await client.login('user@example.com', ' password '), user)
  assert.deepEqual(client.state.user, user)
  assert.equal(JSON.parse(client.cookies.getCookie('member')).refreshToken, 'refresh-access-1')
  const headers = []
  client.api.defaults.adapter = async (config) => {
    headers.push(config.headers.get('Authorization'))
    return response(config, {})
  }
  await client.api.get('/v1/auth/me')
  await client.logout()
  await client.api.get('/v1/auth/me')
  assert.deepEqual(headers, ['Bearer access-1', undefined])
  assert.equal(client.state.user, null)
  assert.equal(client.cookies.getCookie('member'), undefined)
})

test('동시에 만료된 요청들은 한 번만 갱신하고 원래 요청을 다시 보낸다', async () => {
  const client = setup()
  let refreshCount = 0
  publicAdapter(client, (config) => {
    if (config.url === '/v1/auth/refresh') refreshCount += 1
    return response(config, { user, accessToken: config.url.endsWith('/login') ? 'old' : 'new' })
  })
  await client.login('user@example.com', 'password')
  const retried = []
  client.api.defaults.adapter = async (config) => {
    if (config.headers.get('Authorization') === 'Bearer old') {
      reject(config, 401, 'ACCESS_TOKEN_EXPIRED')
    }
    retried.push({ url: config.url, method: config.method, data: config.data, params: config.params })
    return response(config, {})
  }
  await Promise.all([
    client.api.get('/v1/items', { params: { page: 2 } }),
    client.api.post('/v1/items', { name: '집' }),
  ])
  assert.equal(refreshCount, 1)
  assert.deepEqual(retried, [
    { url: '/v1/items', method: 'get', data: undefined, params: { page: 2 } },
    { url: '/v1/items', method: 'post', data: JSON.stringify({ name: '집' }), params: undefined },
  ])
})

test('갱신 후에도 만료 응답이면 더 재시도하지 않고 로그인 상태를 지운다', async () => {
  const client = setup()
  let refreshCount = 0
  let requestCount = 0
  publicAdapter(client, (config) => {
    if (config.url.endsWith('/refresh')) refreshCount += 1
    return response(config, { user, accessToken: 'access' })
  })
  await client.login('user@example.com', 'password')
  client.api.defaults.adapter = async (config) => {
    requestCount += 1
    reject(config, 401, 'ACCESS_TOKEN_EXPIRED')
  }
  await assert.rejects(client.api.get('/v1/auth/me'))
  assert.equal(refreshCount, 1)
  assert.equal(requestCount, 2)
  assert.equal(client.state.user, null)
  assert.equal(client.cookies.getCookie('member'), undefined)
})

test('로그아웃 전에 시작된 갱신 응답은 로그인 상태를 되살리지 못한다', async () => {
  const client = setup()
  let finishRefresh
  let markStarted
  const started = new Promise((resolve) => { markStarted = resolve })
  const pending = new Promise((resolve) => { finishRefresh = resolve })
  let logoutCount = 0
  publicAdapter(client, async (config) => {
    if (config.url.endsWith('/refresh')) {
      markStarted()
      await pending
      return response(config, { user, accessToken: 'late' })
    }
    if (config.url.endsWith('/logout')) {
      logoutCount += 1
      assert.equal(JSON.parse(config.data).refreshToken, 'refresh-late')
    }
    return response(config, { user, accessToken: 'old' })
  })
  await client.login('user@example.com', 'password')
  client.api.defaults.adapter = async (config) => reject(config, 401, 'ACCESS_TOKEN_EXPIRED')
  const request = client.api.get('/v1/auth/me')
  const rejected = assert.rejects(request)
  await started
  const logout = client.logout()
  finishRefresh()
  await Promise.all([rejected, logout])
  assert.equal(client.state.user, null)
  assert.equal(logoutCount, 1)
})

test('초기 복원은 중복 실행되어도 한 번이며 갱신 쿠키가 없으면 비로그인으로 끝난다', async () => {
  const client = setup()
  let refreshCount = 0
  publicAdapter(client, (config) => {
    refreshCount += 1
    reject(config, 401, 'REFRESH_TOKEN_INVALID')
  })
  await Promise.all([client.restoreSession(), client.restoreSession()])
  assert.equal(refreshCount, 1)
  assert.equal(client.state.user, null)
  assert.equal(client.cookies.getCookie('member'), undefined)
})

test('갱신 서버 장애와 CSRF 오류는 변경 요청을 반복하거나 사용자를 로그아웃시키지 않는다', async () => {
  const client = setup()
  publicAdapter(client, (config) => {
    if (config.url.endsWith('/refresh')) reject(config, 503, 'UNAVAILABLE')
    return response(config, { user, accessToken: 'access' })
  })
  await client.login('user@example.com', 'password')
  client.api.defaults.adapter = async (config) => reject(config, 401, 'ACCESS_TOKEN_EXPIRED')
  await assert.rejects(client.api.get('/v1/auth/me'))
  assert.deepEqual(client.state.user, user)
  let requestCount = 0
  client.api.defaults.adapter = async (config) => {
    requestCount += 1
    reject(config, 403, 'CSRF_INVALID')
  }
  await assert.rejects(client.api.post('/v1/items', { name: '집' }))
  assert.equal(requestCount, 1)
  assert.deepEqual(client.state.user, user)
})


test('새 클라이언트는 쿠키의 Refresh를 전송해 복원하고 회전된 토큰으로 교체한다', async () => {
  const cookies = cookieStore()
  const first = setup(cookies)
  publicAdapter(first, (config) => response(config, { user, accessToken: 'old' }))
  await first.login('user@example.com', 'password')
  const restored = setup(cookies)
  publicAdapter(restored, (config) => {
    assert.equal(config.url, '/v1/auth/refresh')
    assert.equal(JSON.parse(config.data).refreshToken, 'refresh-old')
    return response(config, { user, accessToken: 'new' })
  })
  await restored.restoreSession()
  assert.deepEqual(restored.state.user, user)
  const saved = JSON.parse(cookies.getCookie('member'))
  assert.equal(saved.accessToken, 'new')
  assert.equal(saved.refreshToken, 'refresh-new')
  assert.equal(saved.refreshExpiresAt, '2030-01-01T00:00:00Z')
  assert.ok(cookies.values.get('member').days > 0)
})

test('손상된 쿠키는 지우고 갱신 거절 시 비로그인 상태로 복원한다', async () => {
  const client = setup()
  client.cookies.setCookie('member', '{broken')
  publicAdapter(client, (config) => {
    assert.deepEqual(JSON.parse(config.data), {})
    reject(config, 401, 'REFRESH_TOKEN_INVALID')
  })
  await client.restoreSession()
  assert.equal(client.state.user, null)
  assert.equal(client.cookies.getCookie('member'), undefined)
})


test('중개사도 공통 로그인·복원·로그아웃을 사용하고 갱신된 승인 상태를 저장한다', async () => {
  const client = setup()
  const agent = { userId: 30, agentId: 7, name: '중개사', role: 'AGENT', approvalStatus: 'PENDING' }
  publicAdapter(client, (config) => {
    if (config.url === '/v1/auth/login') return response(config, { user: agent, accessToken: 'agent-login' })
    if (config.url === '/v1/auth/refresh') return response(config, { user: { ...agent, approvalStatus: 'APPROVED' }, accessToken: 'agent-refresh' })
    return response(config, null, 204)
  })
  assert.deepEqual(await client.login('agent@example.com', 'password'), agent)
  await client.restoreSession()
  assert.equal(client.state.user.approvalStatus, 'APPROVED')
  assert.equal(client.state.user.agentId, 7)
  await client.logout()
  assert.equal(client.state.user, null)
  assert.equal(client.cookies.getCookie('member'), undefined)
})
