import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'

let server
let api
let getAdminRiskUsers
let AdminRiskUserPage
let store
const admin = { userId: 35, role: 'ADMIN' }
const risk = (id, overrides = {}) => ({
  riskInfoId: id, userId: 20, name: '조회 대상', userType: 'TENANT',
  reasonType: 'RENT_UNPAID', detail: '첫 줄\n둘째 줄', createdAt: '2026-11-01T12:00:00',
  hasEvidence: true, ...overrides,
})

before(async () => {
  server = await createServer({
    configFile: false, plugins: [react()], cacheDir: 'node_modules/.vite-admin-risk-users-test',
    server: { middlewareMode: true, hmr: false },
  })
  api = (await server.ssrLoadModule('/src/utils/jwtUtil.js')).api
  getAdminRiskUsers = (await server.ssrLoadModule('/src/api/admin/index.js')).getAdminRiskUsers
  AdminRiskUserPage = (await server.ssrLoadModule('/src/pages/risk/admin/AdminRiskUserPage.jsx')).default
  store = (await server.ssrLoadModule('/src/stores/authStore.js')).useAuthStore
})

after(async () => { await server?.close() })

function renderList({ riskUsers = [], page = 1, totalPages = 1, totalElements = riskUsers.length, error, loading = false, fetching = false, cached = [] } = {}) {
  store.getInitialState().user = admin
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false, staleTime: Infinity } } })
  const key = ['admin-risk-users', admin.userId, 'TENANT', '', 1]
  for (const [queryKey, data] of cached) client.setQueryData(queryKey, data)
  if (error) {
    client.setQueryData(key, { riskUsers, page, totalPages, totalElements })
    client.getQueryCache().find({ queryKey: key }).setState({ status: 'error', error, fetchStatus: fetching ? 'fetching' : 'idle' })
  } else if (!loading) {
    client.setQueryData(key, { riskUsers, page, totalPages, totalElements })
  }
  try {
    return renderToStaticMarkup(createElement(QueryClientProvider, { client }, createElement(AdminRiskUserPage)))
  } finally {
    client.clear()
  }
}

test('실제 위험정보 필드·총 건수·서버 페이지를 표시하고 미동작 UI를 제거한다', () => {
  const html = renderList({ riskUsers: [risk(101), risk(102, { hasEvidence: false })], page: 2, totalPages: 3, totalElements: 12 })
  for (const text of ['임차인', '임대인', '중개사', '이름으로 검색', '월세 미지급', '첫 줄\n둘째 줄', '2026-11-01', '생성일', '총 12건', '있음', '없음']) {
    assert.ok(html.includes(text), text)
  }
  assert.equal((html.match(/<strong>조회 대상<\/strong>/g) ?? []).length, 2)
  assert.match(html, /whitespace-pre-wrap break-words/)
  assert.match(html, /aria-current="page"[^>]*>2<\/button>/)
  assert.match(html, />3<\/button>/)
  assert.doesNotMatch(html, /위험 사용자 등록|>수정<|위험 정보 후보|결제 내역|등록일|김보금/)
})

test('위험 사유 6개를 한국어로 표시한다', () => {
  const reasons = {
    RENT_UNPAID: '월세 미지급', RENT_OVERDUE: '연체', DEPOSIT_UNPAID: '보증금 미지급',
    FALSE_LISTING: '허위 매물 등록', NO_PROPERTY_IMAGE: '매물 이미지 미등록', AGENT_CHANGE_HISTORY: '중개사 교체 이력',
  }
  for (const [reasonType, label] of Object.entries(reasons)) {
    const html = renderList({ riskUsers: [risk(101, { reasonType })] })
    assert.ok(html.includes(label), reasonType)
    assert.ok(!html.includes(reasonType), reasonType)
  }
})

test('상세 누락은 대시로 표시하고 HTML·추가 개인정보·증빙 URL을 노출하지 않는다', () => {
  for (const detail of [null, '', ' \n ']) {
    const html = renderList({ riskUsers: [risk(101, {
      detail, phone: '010-SECRET', email: 'secret@example.com', evidenceFileKey: 'private/secret.pdf',
      evidenceUrl: 'https://secret.example/evidence', passwordHash: 'SECRET-HASH',
    })] })
    assert.match(html, /whitespace-pre-wrap break-words">-<\/span>/)
    assert.doesNotMatch(html, /010-SECRET|secret@example|private\/secret|secret.example|SECRET-HASH/)
    assert.doesNotMatch(html, /<a\b|>있음<\/button>/)
  }
  const html = renderList({ riskUsers: [risk(101, { detail: '<script>alert(1)</script>' })] })
  assert.match(html, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/)
  assert.doesNotMatch(html, /<script>/)
})

test('로딩·오류·재조회·빈 목록을 구분하고 실패 시 이전 데이터를 숨긴다', () => {
  const loading = renderList({ loading: true })
  assert.match(loading, /role="status"[^>]*>위험 사용자 목록을 불러오고/)
  assert.doesNotMatch(loading, /등록된 위험 사용자가 없어요/)
  const error = { response: { data: { message: '조회할 수 없습니다' } } }
  const failed = renderList({ error, riskUsers: [risk(101)] })
  assert.match(failed, /role="alert"/)
  assert.match(failed, /조회할 수 없습니다/)
  assert.match(failed, /다시 조회/)
  assert.doesNotMatch(failed, /조회 대상|<table|aria-label="페이지"/)
  const retrying = renderList({ error, fetching: true })
  assert.match(retrying, /disabled=""[^>]*>다시 조회/)
  assert.match(retrying, /role="status"[^>]*>위험 사용자 목록을 다시 불러오고/)
  assert.match(renderList({ error: new Error('Network Error') }), /위험 사용자 목록을 불러오지 못했어요/)
  const empty = renderList({ totalPages: 0 })
  assert.match(empty, /총 0건/)
  assert.match(empty, /등록된 위험 사용자가 없어요/)
  assert.doesNotMatch(empty, /aria-label="페이지"/)
  assert.doesNotMatch(renderList({ riskUsers: [risk(101)], totalPages: 1 }), /aria-label="페이지"/)
})

test('다른 관리자·유형·검색어·페이지의 캐시를 현재 목록에 섞지 않는다', () => {
  const data = { riskUsers: [risk(101, { name: '이전 조회 대상' })], page: 1, totalPages: 1, totalElements: 1 }
  const cached = [
    [['admin-risk-users', 99, 'TENANT', '', 1], data],
    [['admin-risk-users', admin.userId, 'LANDLORD', '', 1], data],
    [['admin-risk-users', admin.userId, 'TENANT', '보금', 1], data],
    [['admin-risk-users', admin.userId, 'TENANT', '', 2], data],
  ]
  assert.doesNotMatch(renderList({ loading: true, cached }), /이전 조회 대상/)
})

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
