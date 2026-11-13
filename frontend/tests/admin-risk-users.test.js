import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'

let server
let api
let getAdminRiskUsers
let AdminRiskUserPage
let store
let AdminLayout
let AdminAgentPage
let AdminOpenBankingPage
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
  AdminLayout = (await server.ssrLoadModule('/src/components/common/AdminLayout.jsx')).default
  AdminAgentPage = (await server.ssrLoadModule('/src/pages/agent/admin/AdminAgentPage.jsx')).default
  AdminOpenBankingPage = (await server.ssrLoadModule('/src/pages/openbanking/admin/AdminOpenBankingPage.jsx')).default
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

test('긴 이름과 회원 ID를 같은 셀에 줄바꿈 없이 세로로 표시한다', () => {
  const name = '이름이 긴 위험 사용자'
  const userIds = [2001, 2002]
  const html = renderList({ riskUsers: userIds.map((userId, index) => risk(101 + index, { name, userId })) })
  const nameCells = [...html.matchAll(/<td\b[^>]*>([\s\S]*?)<\/td>/g)]
    .map((match) => match[1]).filter((cell) => cell.includes(name))
  assert.equal(nameCells.length, 2)
  for (const [index, cell] of nameCells.entries()) {
    assert.match(cell, /<div class="[^"]*\bflex-col\b[^"]*\bwhitespace-nowrap\b[^"]*">/)
    assert.ok(cell.includes(`<strong>${name}</strong>`))
    assert.match(cell, /<span class="[^"]*\btext-xs\b[^"]*\btext-gray-500\b[^"]*">ID \d+<\/span>/)
    assert.ok(cell.includes(`>ID ${userIds[index]}</span>`))
    assert.doesNotMatch(cell, /<a\b|<button\b|<dialog\b/)
  }
  assert.match(html, /overflow-x-auto/)
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

function renderAdmin(path, user = admin) {
  store.getInitialState().user = user
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
  client.setQueryData(['admin-risk-users', user?.userId, 'TENANT', '', 1], {
    riskUsers: [risk(101)], page: 1, totalPages: 1, totalElements: 1,
  })
  client.setQueryData(['admin-agents', user?.userId, '', 1], {
    agents: [{ agentId: 7, officeName: '기존 중개사무소', representativeName: '중개사 대표', officeAddress: '서울', approvalStatus: 'PENDING' }],
    page: 1, totalPages: 1, totalElements: 1,
  })
  client.setQueryData(['admin-institution-token'], {
    usable: true, issued: true, maskedToken: 'MASKED-TOKEN', clientUseCode: 'TEST-CODE',
  })
  const hiddenPaths = ['properties', 'rent', 'risk-properties', 'chats', 'notifications']
  const router = createMemoryRouter([{
    path: '/admin', element: createElement(AdminLayout),
    children: [
      { path: 'risk-users', element: createElement(AdminRiskUserPage) },
      { path: 'agents', element: createElement(AdminAgentPage) },
      { path: 'openbanking', element: createElement(AdminOpenBankingPage) },
      ...hiddenPaths.map((path) => ({ path, element: createElement('p', null, '미제공 관리자 화면') })),
    ],
  }], { initialEntries: [path] })
  try {
    return renderToStaticMarkup(createElement(QueryClientProvider, { client }, createElement(RouterProvider, { router })))
  } finally {
    router.dispose()
    client.clear()
  }
}

test('관리자는 위험 사용자 메뉴와 목록에 접근하고 기존 중개사·금융 화면도 유지한다', () => {
  const html = renderAdmin('/admin/risk-users')
  assert.match(html, /href="\/admin\/risk-users"[^>]*>위험 사용자/)
  assert.match(html, /조회 대상/)
  assert.match(html, /href="\/admin\/agents"/)
  assert.match(html, /href="\/admin\/openbanking"/)
  const agents = renderAdmin('/admin/agents')
  assert.match(agents, /기존 중개사무소/)
  assert.match(agents, /중개사 대표/)
  assert.doesNotMatch(agents, /조회 대상/)
  const finance = renderAdmin('/admin/openbanking')
  assert.match(finance, /기업용 OOB 토큰 관리/)
  assert.match(finance, /MASKED-TOKEN/)
  assert.match(finance, /상태 새로고침/)
  assert.doesNotMatch(finance, /조회 대상|기존 중개사무소/)
})

test('미로그인·일반 사용자·중개사는 캐시가 있어도 관리자 메뉴와 데이터를 볼 수 없다', () => {
  for (const user of [null, { userId: 1, role: 'USER' }, { userId: 2, role: 'AGENT' }]) {
    for (const path of ['/admin/risk-users', '/admin/agents', '/admin/openbanking']) {
      const html = renderAdmin(path, user)
      assert.doesNotMatch(html, /조회 대상|기존 중개사무소|중개사 대표|MASKED-TOKEN|href="\/admin\/(risk-users|agents|openbanking)"/)
      if (user) assert.match(html, /들어갈 수 없는 방이에요/)
    }
  }
})

test('위험 사용자 접근 허용 후에도 다른 미제공 관리자 메뉴와 화면은 계속 차단한다', () => {
  const html = renderAdmin('/admin/risk-users')
  for (const path of ['properties', 'rent', 'risk-properties', 'chats', 'notifications']) {
    assert.ok(!html.includes(`href="/admin/${path}"`), path)
    assert.doesNotMatch(renderAdmin(`/admin/${path}`), /미제공 관리자 화면/)
  }
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
