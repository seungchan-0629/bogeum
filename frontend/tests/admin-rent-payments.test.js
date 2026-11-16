import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'

let server, auth, store, AdminRentPage, AdminLayout, getAdminRentPayments
const admin = { userId: 39, role: 'ADMIN' }
const payment = (id, overrides = {}) => ({
  rentPaymentId: id, contractId: 21, tenantId: 22, tenantName: '실제 임차인',
  propertyId: 11, roadAddress: '서울 보금로 39', detailAddress: '101호',
  seq: 7, amount: 650000, dueAt: '2026-11-10T09:00:00',
  paidAt: '2026-11-11T12:34:56', rentPaymentStatus: 'PAID', ...overrides,
})
const key = (user = admin) => ['admin-rent-payments', user?.userId, '', 1]

before(async () => {
  server = await createServer({
    configFile: false, plugins: [react()], cacheDir: 'node_modules/.vite-admin-rent-test',
    server: { middlewareMode: true, hmr: false },
  })
  auth = await server.ssrLoadModule('/src/utils/jwtUtil.js')
  store = (await server.ssrLoadModule('/src/stores/authStore.js')).useAuthStore
  getAdminRentPayments = (await server.ssrLoadModule('/src/api/admin/index.js')).getAdminRentPayments
  AdminRentPage = (await server.ssrLoadModule('/src/pages/rent/admin/AdminRentPage.jsx')).default
  AdminLayout = (await server.ssrLoadModule('/src/components/common/AdminLayout.jsx')).default
})
after(async () => { await server?.close() })

function render({ payments = [], page = 1, totalPages = 1, totalElements = payments.length,
  user = admin, loading = false, error, fetching = false, cached = [], layout = false } = {}) {
  store.getInitialState().user = user
  const client = new QueryClient({ defaultOptions: { queries: {
    retry: false, retryOnMount: false, staleTime: Infinity,
  } } })
  for (const [queryKey, data] of cached) client.setQueryData(queryKey, data)
  if (!loading) {
    client.setQueryData(key(user), { payments, page, totalPages, totalElements })
    if (error || fetching) client.getQueryCache().find({ queryKey: key(user) }).setState({
      ...(error ? { status: 'error', error } : {}), fetchStatus: fetching ? 'fetching' : 'idle',
    })
  }
  try {
    const element = layout
      ? createElement(RouterProvider, { router: createMemoryRouter([{
        path: '/admin', element: createElement(AdminLayout), children: [
          { path: 'rent', element: createElement(AdminRentPage) },
        ],
      }, { path: '/admin/login', element: createElement('p', null, '로그인 화면') }], { initialEntries: ['/admin/rent'] }) })
      : createElement(AdminRentPage)
    return renderToStaticMarkup(createElement(QueryClientProvider, { client }, element))
  } finally { client.clear() }
}

test('실제 응답의 이름·주소·회차·금액·예정일·납부일 및 서버 총 건수를 표시한다 (SSR)', () => {
  const html = render({ payments: [payment(301)], totalElements: 12 })
  for (const text of ['실제 임차인', '서울 보금로 39 101호', '7회차', '650,000원', '2026-11-10', '2026-11-11', '총 12건', '예정일', '납부일']) assert.ok(html.includes(text), text)
  assert.doesNotMatch(html, /역삼동 오피스텔|김보금|결제일|>결과</)
  assert.match(html, /aria-selected="true"[^>]*>납부 내역/)
})
for (const [status, label, color] of [
  ['SCHEDULED', '납부 예정', 'blue'], ['PAID', '납부 완료', 'emerald'],
  ['FAILED', '결제 실패', 'red'], ['OVERDUE', '연체', 'amber'],
]) {
  test(status + ' 상태를 ' + label + '로 구분한다 (SSR)', () => {
    const html = render({ payments: [payment(301, { rentPaymentStatus: status })] })
    assert.match(html, new RegExp('bg-' + color + '-100[^>]*>' + label + '</span>'))
  })
}
test('납부일·상세주소 NULL과 큰 원 단위 금액을 안전하게 표시한다 (SSR)', () => {
  const html = render({ payments: [payment(301, { paidAt: null, detailAddress: null, amount: 123456789012345 })] })
  assert.match(html, /서울 보금로 39<\/td>/)
  assert.match(html, /123,456,789,012,345원/)
  assert.match(html, /<td[^>]*>-<\/td>/)
  assert.doesNotMatch(html, /null|undefined/)
})
test('여러 계약의 동일 회차도 납부 ID별 행으로 표시한다 (SSR)', () => {
  const html = render({ payments: [payment(301), payment(302, { contractId: 22, tenantName: '다른 계약 임차인' })] })
  assert.equal((html.match(/7회차/g) || []).length, 2)
  assert.match(html, /다른 계약 임차인/)
})
test('응답 page와 totalPages를 사용하고 마지막 페이지의 다음 버튼을 비활성화한다 (SSR)', () => {
  const html = render({ payments: [payment(301)], page: 3, totalPages: 3, totalElements: 11 })
  assert.match(html, /aria-current="page"[^>]*>3<\/button>/)
  assert.match(html, /disabled="" aria-label="다음 페이지"/)
  assert.match(html, /총 11건/)
})
for (const totalPages of [0, 1]) {
  test('빈 목록 totalPages=' + totalPages + '에서 페이지 영역이 없다 (SSR)', () => {
    const html = render({ totalPages })
    assert.match(html, /등록된 납부 내역이 없어요/)
    assert.doesNotMatch(html, /aria-label="페이지"|aria-label="다음 페이지"/)
  })
}
test('최초 조회 중 이전 관리자·다른 검색·페이지의 캐시를 표시하지 않는다 (SSR)', () => {
  const data = { payments: [payment(301, { tenantName: '이전 비공개 조회' })], page: 1, totalPages: 1, totalElements: 1 }
  const html = render({ loading: true, cached: [
    [key({ userId: 38 }), data], [['admin-rent-payments', 39, '이전', 1], data],
    [['admin-rent-payments', 39, '', 2], data],
  ] })
  assert.match(html, /납부 내역을 불러오고 있어요/)
  assert.doesNotMatch(html, /이전 비공개 조회|실제 임차인/)
})
test('백그라운드 재조회 진행 상태를 표시한다 (SSR)', () => {
  assert.match(render({ payments: [payment(301)], fetching: true }), /납부 내역을 다시 불러오고 있어요/)
})
test('API 오류는 오래된 행 대신 안내·재시도를 표시하고 재조회 중 중복 클릭을 막는다 (SSR)', () => {
  for (const error of [new Error('offline'), { response: { data: { message: '서버 점검 중' } } }]) {
    const html = render({ payments: [payment(301)], error, fetching: true })
    assert.match(html, /role="alert"/)
    assert.match(html, /납부 내역을 불러오지 못했어요|서버 점검 중/)
    assert.match(html, /disabled="">다시 조회/)
    assert.doesNotMatch(html, /실제 임차인/)
  }
})
test('월세 관리 메뉴와 /admin/rent 화면을 ADMIN에게 제공한다 (SSR)', () => {
  const html = render({ payments: [payment(301)], layout: true })
  assert.match(html, /href="\/admin\/rent"/)
  assert.match(html, /실제 임차인/)
})
test('미인증·일반 사용자·중개사는 캐시가 있어도 관리자 데이터를 볼 수 없다 (SSR)', () => {
  for (const user of [null, { userId: 22, role: 'USER' }, { userId: 23, role: 'AGENT' }]) {
    const html = render({ payments: [payment(301)], user, layout: true })
    assert.doesNotMatch(html, /실제 임차인|href="\/admin\/rent"/)
  }
})
test('PAYMENTS 기본 진입에서 실패·연체 샘플과 집계 컬럼을 표시하지 않는다 (SSR)', () => {
  const html = render({ payments: [payment(301)] })
  assert.doesNotMatch(html, /위험 후보|재시도 중|재시도 결과|실패 횟수|연체 기간|역삼동 오피스텔/)
})

test('조회 API는 공통 JWT·GET·정확한 주소·검색·페이지·취소 신호를 유지한다', async () => {
  const originalApi = auth.api.defaults.adapter, originalPublic = auth.publicApi.defaults.adapter
  const originalWindow = globalThis.window, originalDocument = globalThis.document
  globalThis.window = { location: { protocol: 'http:' } }
  globalThis.document = { cookie: '' }
  const csrfCalls = []
  auth.publicApi.defaults.adapter = async (config) => {
    csrfCalls.push(config.url)
    return { status: 200, headers: {}, config, data: config.url === '/auth/csrf'
      ? { headerName: 'X-XSRF-TOKEN', token: 'test-csrf' }
      : { user: admin, accessToken: 'rent-access', refreshToken: 'test-refresh', refreshExpiresAt: '2030-01-01T00:00:00Z' } }
  }
  const controller = new AbortController()
  const data = { payments: [payment(301)], page: 2, totalPages: 3, totalElements: 12 }
  let calls = 0
  auth.api.defaults.adapter = async (config) => {
    calls += 1
    assert.equal(config.method, 'get')
    assert.equal(config.url, '/api/admin/rents/payments')
    assert.deepEqual(config.params, { keyword: '보금 %_', page: 2 })
    assert.equal(config.signal, controller.signal)
    assert.equal(config.headers.get('Authorization'), 'Bearer rent-access')
    assert.equal(config.withCredentials, true)
    return { status: 200, data, config, headers: {} }
  }
  try {
    await auth.login('admin@example.test', 'password')
    const before = csrfCalls.length
    assert.deepEqual(await getAdminRentPayments('보금 %_', 2, controller.signal), data)
    assert.equal(csrfCalls.length, before, 'GET은 별도 CSRF 발급 없이 공통 인증을 사용')
    controller.abort()
    await assert.rejects(getAdminRentPayments('보금 %_', 2, controller.signal), (error) => error.code === 'ERR_CANCELED')
    assert.equal(calls, 1)
  } finally {
    auth.api.defaults.adapter = originalApi
    auth.publicApi.defaults.adapter = originalPublic
    if (originalWindow === undefined) delete globalThis.window
    else globalThis.window = originalWindow
    if (originalDocument === undefined) delete globalThis.document
    else globalThis.document = originalDocument
  }
})
