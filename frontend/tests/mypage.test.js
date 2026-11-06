import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'

let server
let MyPage
let SocialSignupForm
let LoginForm
let SignupPage
let authStore
const profile = { userId: 1, name: '회원정보 확인', email: 'mypage@example.com', phone: '01000000000', role: 'USER', passwordLoginAvailable: true }

before(async () => {
  server = await createServer({ configFile: false, plugins: [react()], server: { middlewareMode: true, hmr: false } })
  MyPage = (await server.ssrLoadModule('/src/pages/auth/MyPage.jsx')).default
  SocialSignupForm = (await server.ssrLoadModule('/src/components/auth/SocialSignupForm.jsx')).default
  LoginForm = (await server.ssrLoadModule('/src/components/auth/LoginForm.jsx')).default
  SignupPage = (await server.ssrLoadModule('/src/pages/auth/SignupPage.jsx')).default
  authStore = (await server.ssrLoadModule('/src/stores/authStore.js')).useAuthStore
  // 서버 렌더링은 현재 상태 대신 초기 상태를 읽는다.
  authStore.getInitialState().user = profile
})

after(async () => { await server?.close() })

function renderAccount(account, error, passwordLoginAvailable = true, connections = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false, staleTime: Infinity } } })
  client.setQueryData(['user-profile', profile.userId], { ...profile, passwordLoginAvailable })
  for (const provider of ['naver', 'kakao']) {
    const queryKey = [`${provider}-connection`, profile.userId]
    const connection = connections[provider]
    if (connection instanceof Error) {
      client.getQueryCache().build(client, { queryKey }).setState({ status: 'error', error: connection, fetchStatus: 'idle' })
    } else {
      client.setQueryData(queryKey, { connected: connection ?? false })
    }
  }
  const queryKey = ['user-account', profile.userId]
  if (error) {
    client.getQueryCache().build(client, { queryKey }).setState({ status: 'error', error, fetchStatus: 'idle' })
  } else {
    client.setQueryData(queryKey, account)
  }
  try {
    return renderToStaticMarkup(createElement(QueryClientProvider, { client },
      createElement(MemoryRouter, { initialEntries: ['/mypage'] }, createElement(MyPage))))
  } finally {
    client.clear()
  }
}

function assertProfileVisible(html) {
  assert.match(html, /회원정보 확인/)
  assert.match(html, /mypage@example\.com/)
  assert.doesNotMatch(html, /회원 정보를 불러오지 못했습니다/)
  assert.doesNotMatch(html, /<button[^>]*disabled[^>]*>저장<\/button>/)
}

test('계좌가 없어도 회원 정보와 저장 버튼은 표시하고 계좌 영역에만 없음 안내를 표시한다', () => {
  const html = renderAccount(null)
  assertProfileVisible(html)
  assert.match(html, /등록된 계좌가 없어요/)
  assert.doesNotMatch(html, /등록 계좌를 불러오지 못했어요/)
})

test('등록 계좌가 있으면 기존 회원 정보와 은행명·마스킹된 번호를 함께 표시한다', () => {
  const html = renderAccount({ bankName: '국민은행', accountNumberMasked: '********9012' })
  assertProfileVisible(html)
  assert.match(html, /국민은행/)
  assert.match(html, /\*{8}9012/)
})

test('계좌 조회만 실패해도 기존 회원 정보와 저장 버튼은 정상 표시한다', () => {
  const html = renderAccount(undefined, new Error('계좌 조회 실패'))
  assertProfileVisible(html)
  assert.match(html, /등록 계좌를 불러오지 못했어요/)
  assert.doesNotMatch(html, /등록된 계좌가 없어요/)
})

test('네이버·카카오 연결 버튼은 비밀번호 입력 없이 사용할 수 있고 재확인을 요구하지 않는다', () => {
  for (const passwordLoginAvailable of [true, false]) {
    const html = renderAccount(null, null, passwordLoginAvailable)
    assert.doesNotMatch(html, /연결됨/)
    assert.match(html, /<button(?![^>]*\bdisabled\b)[^>]*>연결하기<\/button>/)
    assert.equal((html.match(/<button(?![^>]*\bdisabled\b)[^>]*>연결하기<\/button>/g) ?? []).length, 2)
    assert.doesNotMatch(html, /네이버 연결을 위한 현재 비밀번호/)
    assert.match(html, /준비 중/)
  }
})

test('네이버 전용 계정은 연결 상태를 표시하고 비밀번호 설정·변경 입력란을 숨긴다', () => {
  const html = renderAccount(null, null, false, { naver: true })
  assert.match(html, /연결됨/)
  assert.doesNotMatch(html, /type="password"/)
  assert.doesNotMatch(html, /새 비밀번호/)
})

test('네이버와 카카오의 연결 상태는 각각 표시한다', () => {
  for (const connections of [{ naver: true }, { kakao: true }, { naver: true, kakao: true }]) {
    const html = renderAccount(null, null, true, connections)
    const count = Object.values(connections).filter(Boolean).length
    assert.equal((html.match(/연결됨/g) ?? []).length, count)
    assert.equal((html.match(/<button(?![^>]*\bdisabled\b)[^>]*>연결하기<\/button>/g) ?? []).length, 2 - count)
  }
})

test('카카오 연결 조회가 실패해도 네이버 연결과 회원 정보는 사용할 수 있다', () => {
  const html = renderAccount(null, null, true, { kakao: new Error('조회 실패') })
  assertProfileVisible(html)
  assert.match(html, /카카오 연결 상태를 불러오지 못했습니다/)
  assert.doesNotMatch(html, /네이버 연결 상태를 불러오지 못했습니다/)
  assert.equal((html.match(/<button(?![^>]*\bdisabled\b)[^>]*>연결하기<\/button>/g) ?? []).length, 1)
})

test('로그인 화면은 네이버·카카오 버튼을 활성화하고 구글은 준비 중으로 표시한다', () => {
  const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/login'] }, createElement(LoginForm)))
  for (const label of ['네이버', '카카오']) {
    const button = html.match(new RegExp(`<button([^>]*)>${label}로 로그인`))
    assert.ok(button)
    assert.doesNotMatch(button[1], /\sdisabled(?:=|\s|$)/)
  }
  assert.match(html, /<button[^>]*disabled[^>]*>구글로 로그인 \(준비 중\)/)
})

function renderSignup(state) {
  return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [{ pathname: '/signup', state }] },
    createElement(Routes, null, createElement(Route, { path: '/signup', element: createElement(SignupPage) }))))
}

test('/signup은 네이버·카카오 신규 회원에게 소셜 추가 가입 폼을 표시한다', () => {
  for (const [provider, label] of [['NAVER', '네이버'], ['KAKAO', '카카오']]) {
    const html = renderSignup({ socialSignup: { provider, name: '소셜 가입 회원', email: 'social@example.com' } })
    assert.match(html, /<h1[^>]*>회원가입<\/h1>/)
    assert.ok(html.includes(`${label} 회원가입`))
    assert.ok(html.includes(`${label}에서 제공한 이름이에요.`))
    assert.ok(html.includes(`${label}에서 제공한 이메일이에요.`))
    assert.equal((html.match(/readOnly=""/g) ?? []).length, 2)
    assert.match(html, /가입하고 로그인/)
    assert.match(html, /로그인으로 돌아가기/)
    assert.doesNotMatch(html, /type="password"/)
  }
})

test('/signup에 소셜 프로필이 없으면 일반 회원가입을 표시한다', () => {
  for (const state of [null, { socialSignup: { provider: 'GOOGLE' } }]) {
    const html = renderSignup(state)
    assert.match(html, /type="password"/)
    assert.match(html, /가입하기/)
    assert.doesNotMatch(html, /가입하고 로그인/)
  }
})

test('/signup의 소셜 가입은 제공받지 못한 이름·이메일을 직접 입력받는다', () => {
  const html = renderSignup({ socialSignup: { provider: 'KAKAO', name: null, email: null } })
  assert.match(html, /카카오 회원가입/)
  assert.match(html, /name="name"/)
  assert.match(html, /name="email"/)
  assert.doesNotMatch(html, /readOnly=""/)
  assert.doesNotMatch(html, /type="password"/)
})

test('공통 소셜 가입 폼은 제공자 이름을 표시하고 필요한 추가 정보만 받는다', () => {
  for (const providerLabel of ['네이버', '카카오', '구글']) {
    const html = renderToStaticMarkup(createElement(SocialSignupForm, {
      providerLabel, profile: { name: '가입 회원', email: 'social@example.com' }, onSubmit() {}, onCancel() {},
    }))
    assert.ok(html.includes(`${providerLabel} 회원가입`))
    assert.ok(html.includes(`${providerLabel}에서 제공한 이메일이에요.`))
    if (providerLabel !== '네이버') assert.doesNotMatch(html, /네이버/)
    assert.match(html, /name="name"/)
    assert.match(html, /name="email"/)
    assert.match(html, /인증 요청/)
    assert.match(html, /개인정보 수집·이용 동의/)
    assert.match(html, /개인정보 제3자 제공 동의/)
    assert.equal((html.match(/readOnly=""/g) ?? []).length, 2)
    assert.doesNotMatch(html, /type="password"/)
    assert.doesNotMatch(html, /name="loginId"/)
  }
})
