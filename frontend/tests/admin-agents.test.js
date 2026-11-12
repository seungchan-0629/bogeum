import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'

let server
let AdminAgentPage
let AdminLayout
let store
let api
let getAdminAgents
const admin = { userId: 35, name: '관리자', role: 'ADMIN' }
const agent = (id, status = 'PENDING') => ({
  agentId: id, officeName: `보금${id}`, officeAddress: '서울 송파구',
  representativeName: `대표${id}`, approvalStatus: status, createdAt: '2026-11-11T10:00:00',
})

before(async () => {
  server = await createServer({
    configFile: false, plugins: [react()], cacheDir: 'node_modules/.vite-admin-agents-test',
    server: { middlewareMode: true, hmr: false },
  })
  AdminAgentPage = (await server.ssrLoadModule('/src/pages/agent/admin/AdminAgentPage.jsx')).default
  AdminLayout = (await server.ssrLoadModule('/src/components/common/AdminLayout.jsx')).default
  store = (await server.ssrLoadModule('/src/stores/authStore.js')).useAuthStore
  api = (await server.ssrLoadModule('/src/utils/jwtUtil.js')).api
  getAdminAgents = (await server.ssrLoadModule('/src/api/admin/index.js')).getAdminAgents
})
after(async () => { await server?.close() })

function renderList({ agents = [], keyword = '', page = 1, totalPages = 1, totalElements = agents.length, error, loading = false, user = admin, path = '/admin/agents' } = {}) {
  store.getInitialState().user = user
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false, staleTime: Infinity } } })
  const key = ['admin-agents', user?.userId, keyword, page]
  if (error) {
    client.getQueryCache().build(client, { queryKey: key }).setState({ status: 'error', error })
  } else if (!loading) {
    client.setQueryData(key, { agents, page, totalPages, totalElements })
  }
  const router = createMemoryRouter([{
    path: '/admin', element: createElement(AdminLayout),
    children: [
      { path: 'agents', element: createElement(AdminAgentPage) },
      { path: 'properties', element: createElement('p', null, '숨겨진 관리자 화면') },
      { path: 'openbanking', element: createElement('p', null, '기존 금융 화면') },
    ],
  }], { initialEntries: [path] })
  const html = renderToStaticMarkup(createElement(QueryClientProvider, { client }, createElement(RouterProvider, { router })))
  router.dispose()
  client.clear()
  return html
}

test('관리자 목록은 실제 필드·상태·날짜·서버 페이지 수를 표시하고 가짜 심사 UI를 제거한다', () => {
  const html = renderList({ agents: [agent(1), agent(2, 'APPROVED'), agent(3, 'REJECTED')], totalPages: 4, totalElements: 18 })
  for (const text of ['보금1', '대표1', '서울 송파구', '2026-11-11', '검증 대기', '승인', '반려', '총 18개', '서류 열람과 승인·반려 기능은 준비 중']) {
    assert.ok(html.includes(text), text)
  }
  assert.match(html, />4<\/button>/)
  assert.doesNotMatch(html, /공인중개사 자격증|중개사무소 등록증|사업자등록증|최종 승인|reject-reason|새봄공인중개사/)
  assert.doesNotMatch(html, /href="\/admin\/properties"/)
  assert.match(html, /href="\/admin\/agents"/)
})

test('로딩·실패·빈 목록을 구분하고 실패 시 이전 선택 정보를 노출하지 않는다', () => {
  assert.match(renderList({ loading: true }), /role="status"[^>]*>중개사 목록을 불러오고/)
  const failed = renderList({ error: { response: { data: { message: '조회할 수 없습니다' } } } })
  assert.match(failed, /role="alert"/)
  assert.match(failed, /조회할 수 없습니다/)
  assert.match(failed, /다시 조회/)
  assert.doesNotMatch(failed, /agent-detail-title/)
  assert.match(renderList({ totalPages: 0 }), /등록된 중개사가 없어요/)
  assert.doesNotMatch(renderList({ totalPages: 0 }), /aria-label="페이지"/)
})

test('일반 사용자·중개사에게 목록과 관리자 메뉴를 노출하지 않는다', () => {
  for (const role of ['USER', 'AGENT']) {
    const html = renderList({ agents: [agent(1)], user: { userId: 2, role } })
    assert.doesNotMatch(html, /보금1|agent-detail-title|href="\/admin\/agents"/)
    assert.match(html, /들어갈 수 없는 방이에요/)
  }
  assert.doesNotMatch(renderList({ user: null }), /보금|agent-detail-title/)
})

test('다른 관리자 화면은 계속 차단하고 기존 금융 화면은 유지한다', () => {
  assert.doesNotMatch(renderList({ path: '/admin/properties' }), /숨겨진 관리자 화면/)
  assert.match(renderList({ path: '/admin/openbanking' }), /기존 금융 화면/)
})

test('관리자 API는 전용 프록시 주소와 검색어·페이지·취소 신호를 전달한다', async () => {
  const original = api.defaults.adapter
  const controller = new AbortController()
  let request
  const data = { agents: [agent(1)], page: 2, totalPages: 3, totalElements: 12 }
  api.defaults.adapter = async (config) => {
    request = config
    return { status: 200, data, config, headers: {} }
  }
  try {
    assert.deepEqual(await getAdminAgents('서울 %_', 2, controller.signal), data)
    assert.equal(request.url, '/api/admin/agents')
    assert.deepEqual(request.params, { keyword: '서울 %_', page: 2 })
    assert.equal(request.signal, controller.signal)
  } finally {
    api.defaults.adapter = original
  }
})
