import assert from 'node:assert/strict'
import { test } from 'node:test'
import { once } from 'node:events'
import { createFakeKftcServer } from './server.js'

const authorization = {
  response_type: 'code', client_id: 'bogeum',
  redirect_uri: 'http://localhost:5173/mypage/openbanking/callback',
  scope: 'login inquiry transfer', state: 'a'.repeat(32), auth_type: '0',
}
const account = { bank_name: '국민은행', account_num: '123456789012', account_holder_name: '홍길동', agree: 'Y' }

async function withServer(run) {
  const server = createFakeKftcServer().listen(0, '127.0.0.1')
  await once(server, 'listening')
  const origin = `http://127.0.0.1:${server.address().port}`
  const post = (path, fields) => fetch(`${origin}${path}`, {
    method: 'POST', redirect: 'manual', body: new URLSearchParams(fields),
  })
  try {
    await run({ origin, post })
  } finally {
    server.closeAllConnections()
    await new Promise((resolve) => server.close(resolve))
  }
}

test('입력 화면 → code/state 콜백 → 일회성 토큰 교환 → 입력 계좌 조회', () => withServer(async ({ origin, post }) => {
  const page = await fetch(`${origin}/oauth/2.0/authorize?${new URLSearchParams(authorization)}`)
  assert.equal(page.status, 200)
  assert.match(await page.text(), /<option>국민은행<\/option>/)
  const response = await post('/oauth/2.0/authorize', { ...authorization, ...account })
  assert.equal(response.status, 303)
  const callback = new URL(response.headers.get('location'))
  assert.equal(`${callback.origin}${callback.pathname}`, authorization.redirect_uri)
  assert.equal(callback.searchParams.get('state'), authorization.state)
  const code = callback.searchParams.get('code')
  assert.ok(code)
  assert.equal(callback.searchParams.has('account_num'), false)

  const tokenRequest = { grant_type: 'authorization_code', code, client_id: 'bogeum', redirect_uri: authorization.redirect_uri }
  const wrongRedirect = await post('/oauth/2.0/token', { ...tokenRequest, redirect_uri: 'http://example.com' })
  assert.equal(wrongRedirect.status, 400)
  const tokenResponse = await post('/oauth/2.0/token', tokenRequest)
  assert.equal(tokenResponse.status, 200)
  const token = await tokenResponse.json()
  assert.equal(token.token_type, 'Bearer')
  assert.ok(token.refresh_token)
  assert.equal((await post('/oauth/2.0/token', tokenRequest)).status, 400)

  const userResponse = await fetch(`${origin}/v2.0/user/me?user_seq_no=${token.user_seq_no}`, {
    headers: { Authorization: `Bearer ${token.access_token}` },
  })
  const user = await userResponse.json()
  assert.equal(user.rsp_code, 'A0000')
  assert.equal(user.user_name, account.account_holder_name)
  assert.equal(user.res_list[0].bank_name, account.bank_name)
  assert.equal(user.res_list[0].account_num_masked, '********9012')
  assert.match(user.res_list[0].fintech_use_num, /^\d{24}$/)
  assert.equal((await fetch(`${origin}/v2.0/user/me?user_seq_no=${token.user_seq_no}`)).status, 401)
}))

test('빈 입력과 동의 누락은 거절하고 취소하면 state와 오류만 반환한다', () => withServer(async ({ post }) => {
  for (const invalid of [{ account_num: '123' }, { agree: '' }, { account_holder_name: ' ' }, { bank_name: '없는은행' }]) {
    assert.equal((await post('/oauth/2.0/authorize', { ...authorization, ...account, ...invalid })).status, 400)
  }
  const cancelled = await post('/oauth/2.0/authorize', { ...authorization, action: 'cancel' })
  const callback = new URL(cancelled.headers.get('location'))
  assert.equal(callback.searchParams.get('state'), authorization.state)
  assert.equal(callback.searchParams.get('error'), 'access_denied')
  assert.equal(callback.searchParams.has('code'), false)
}))

test('미리보기는 열리고 다른 콜백 주소나 잘못된 state는 거절한다', () => withServer(async ({ origin, post }) => {
  assert.equal((await fetch(origin)).status, 200)
  for (const invalid of [{ redirect_uri: 'http://example.com' }, { state: '' }, { client_id: 'other' }]) {
    const params = { ...authorization, ...invalid }
    assert.equal((await fetch(`${origin}/oauth/2.0/authorize?${new URLSearchParams(params)}`)).status, 400)
    assert.equal((await post('/oauth/2.0/authorize', { ...params, ...account })).status, 400)
  }
}))
