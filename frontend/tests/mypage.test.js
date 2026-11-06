import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'

let server
let MyPage
let authStore
const profile = { userId: 1, name: '회원정보 확인', email: 'mypage@example.com', phone: '01000000000', role: 'USER' }

before(async () => {
  server = await createServer({ configFile: false, plugins: [react()], server: { middlewareMode: true, hmr: false } })
  MyPage = (await server.ssrLoadModule('/src/pages/auth/MyPage.jsx')).default
  authStore = (await server.ssrLoadModule('/src/stores/authStore.js')).useAuthStore
  // 서버 렌더링은 현재 상태 대신 초기 상태를 읽는다.
  authStore.getInitialState().user = profile
})

after(async () => { await server?.close() })

function renderAccount(account, error) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false, staleTime: Infinity } } })
  client.setQueryData(['user-profile', profile.userId], profile)
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
