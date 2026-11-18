import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, Outlet, RouterProvider, matchRoutes } from 'react-router'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'

// 실제 DOM 회귀 검증: PowerShell에서 $env:MOVEIN_BROWSER_TEST='1'; node --test tests/movein-upload.test.js
// 출력된 로컬 URL을 브라우저로 열면 자동 실행한다. 종료 후 Remove-Item Env:MOVEIN_BROWSER_TEST.
// 기본 npm test는 SSR·API를 실행하며 브라우저 검증은 사유를 표시하고 건너뛴다.

let server, auth, store, routes, MoveInUploadPage, uploadMoveInPhotos
const user = { userId: 22, name: '입주 사용자', role: 'USER', contractRoles: [] }
const contract = {
  contractId: 21, propertyId: 11, roadAddress: '서울 보금로 21', detailAddress: '101호',
  leaseType: 'MONTHLY', deposit: 10000000, monthlyRent: 500000,
  paymentDay: 10, startAt: '2026-11-01', endAt: '2027-10-31',
}

before(async () => {
  server = await createServer({
    configFile: false, plugins: [{
      name: 'memory-router-for-ssr-test', enforce: 'pre',
      transform(code, id) {
        if (id.replaceAll('\\', '/').endsWith('/src/router/index.jsx')) {
          return code.replace('import { createBrowserRouter,', 'import { createMemoryRouter as createBrowserRouter,')
        }
      },
    }, react()], cacheDir: 'node_modules/.vite-movein-test',
    server: { middlewareMode: true, hmr: false },
  })
  auth = await server.ssrLoadModule('/src/utils/jwtUtil.js')
  store = (await server.ssrLoadModule('/src/stores/authStore.js')).useAuthStore
  const contractApi = await server.ssrLoadModule('/src/api/contract/index.js')
  uploadMoveInPhotos = contractApi.uploadMoveInPhotos
  MoveInUploadPage = (await server.ssrLoadModule('/src/pages/residence/MoveInUploadPage.jsx')).default
  routes = (await server.ssrLoadModule('/src/router/index.jsx')).router.routes
})
after(async () => { await server?.close() })

function render({ account = user, contracts = [contract], selectedContract, actualRoute = true } = {}) {
  store.getInitialState().user = account
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
  client.setQueryData(['tenant-contracts', account?.userId], contracts)
  const router = createMemoryRouter(actualRoute ? routes : [{
    path: '/my-home', element: createElement(Outlet, { context: { selectedContract } }),
    children: [{ path: 'move-in', element: createElement(MoveInUploadPage) }],
  }], { initialEntries: ['/my-home/move-in'] })
  try {
    return renderToStaticMarkup(createElement(QueryClientProvider, { client }, createElement(RouterProvider, { router })))
  } finally { router.dispose(); client.clear() }
}

test('실제 라우트는 USER 보호와 MyHomeLayout 직계 context를 유지한다', () => {
  const matches = matchRoutes(routes, '/my-home/move-in')
  assert.equal(matches.at(-2).route.path, '/my-home')
  assert.equal(matches.at(-1).route.element.type, MoveInUploadPage)
  const guard = matches.find((match) => match.route.element?.props.roles)
  assert.deepEqual(guard.route.element.props.roles, ['USER'])
  for (const path of ['as', 'claim']) {
    const other = matchRoutes(routes, `/my-home/${path}`)
    assert.equal(other.at(-2).route.element.props.contractRole, 'TENANT')
  }
  const html = render()
  assert.match(html, /서울 보금로 21/)
  assert.match(html, /사진을 눌러서 선택하세요/)
  assert.match(html, /계약한 매물에서 매물을 선택/)
  assert.match(html, /<fieldset disabled=""/)
})

test('실제 라우트에서 비로그인·ADMIN·AGENT는 업로드 화면에 접근할 수 없다 (SSR)', () => {
  for (const account of [null, { userId: 1, role: 'ADMIN' }, { userId: 2, role: 'AGENT', approvalStatus: 'APPROVED' }]) {
    assert.doesNotMatch(render({ account }), /사진을 눌러서 선택하세요|서울 보금로 21/)
  }
})

test('계약이 없는 사용자에게 안내하고 업로드를 비활성화한다 (SSR)', () => {
  const html = render({ contracts: [] })
  assert.match(html, /아직 계약한 매물이 없어요/)
  assert.match(html, /<fieldset disabled=""/)
  assert.match(html, /<button type="submit" disabled=""/)
})

test('Outlet의 선택 계약을 받아 입력을 활성화하며 샘플·잔여일·서버 사진 목록을 표시하지 않는다 (SSR)', () => {
  const html = render({ actualRoute: false, selectedContract: contract })
  assert.doesNotMatch(html, /<fieldset disabled|2일 남음|거실 벽지 얼룩|\/api\/v1/)
  assert.match(html, /계약일부터 7일 후까지 마지막 날을 포함/)
  assert.match(html, /선택한 로컬 사진 미리보기/)
  assert.match(html, /<button type="submit" disabled=""/)
})

test('선택 계약이 있어도 로그인 사용자가 없으면 파일 입력은 비활성화된다 (SSR)', () => {
  assert.match(render({ actualRoute: false, selectedContract: contract, account: null }), /<fieldset disabled=""/)
})

async function withClient(run) {
  const originalApi = auth.api.defaults.adapter, originalPublic = auth.publicApi.defaults.adapter
  const originalWindow = globalThis.window, originalDocument = globalThis.document
  globalThis.window = { location: { protocol: 'http:' } }
  globalThis.document = { cookie: '' }
  auth.publicApi.defaults.adapter = async (config) => ({ status: 200, headers: {}, config,
    data: config.url === '/auth/csrf' ? { headerName: 'X-XSRF-TOKEN', token: 'movein-csrf' }
      : { user, accessToken: 'movein-access', refreshToken: 'test-refresh', refreshExpiresAt: '2030-01-01T00:00:00Z' },
  })
  try { await auth.login('movein@example.test', 'password'); await run() }
  finally {
    auth.api.defaults.adapter = originalApi
    auth.publicApi.defaults.adapter = originalPublic
    if (originalWindow === undefined) delete globalThis.window
    else globalThis.window = originalWindow
    if (originalDocument === undefined) delete globalThis.document
    else globalThis.document = originalDocument
  }
}

const photos = () => [
  { file: new File(['first'], 'first.png', { type: 'image/png' }), caption: '' },
  { file: new File(['second'], 'second.jpg', { type: 'image/jpeg' }), caption: '벽지 흠집' },
]
test('multipart API 경로·JSON Blob·사진과 설명 순서·JWT·CSRF·60초 설정을 유지한다', async () => {
  await withClient(async () => {
    const result = { contractId: 21, photos: [{ moveInDefectPhotoId: 1 }, { moveInDefectPhotoId: 2 }] }
    auth.api.defaults.adapter = async (config) => {
      assert.equal(config.url, '/contract/21/movein-defects')
      assert.equal(config.method, 'post')
      assert.equal(config.baseURL, '')
      assert.equal(config.timeout, 60000)
      assert.equal(config.withCredentials, true)
      assert.equal(config.headers.get('Authorization'), 'Bearer movein-access')
      assert.equal(config.headers.get('X-XSRF-TOKEN'), 'movein-csrf')
      assert.doesNotMatch(config.headers.get('Content-Type') || '', /multipart\/form-data/)
      const body = config.data
      assert.ok(body instanceof FormData)
      assert.deepEqual([...body.keys()], ['request', 'images', 'images'])
      assert.equal(body.get('request').type, 'application/json')
      assert.deepEqual(JSON.parse(await body.get('request').text()), { captions: ['', '벽지 흠집'] })
      assert.deepEqual(body.getAll('images').map((file) => file.name), ['first.png', 'second.jpg'])
      assert.deepEqual(await Promise.all(body.getAll('images').map((file) => file.text())), ['first', 'second'])
      return { status: 201, data: result, config, headers: {} }
    }
    assert.deepEqual(await uploadMoveInPhotos(21, photos()), result)
  })
})

test('201 이외 응답을 성공으로 취급하지 않는다', async () => {
  await withClient(async () => {
    auth.api.defaults.adapter = async (config) => ({ status: 200, data: { photos: [] }, config, headers: {} })
    await assert.rejects(uploadMoveInPhotos(21, photos()), /등록 결과를 확인하지 못했습니다/)
  })
})

for (const [status, code] of [[400, 'MOVEIN_OUTSIDE_UPLOAD_PERIOD'], [403, 'FORBIDDEN'], [404, 'CONTRACT_NOT_FOUND'], [503, 'MOVEIN_STORAGE_UNAVAILABLE']]) {
  test(`${status} 업무 오류를 자동 재전송하지 않고 호출자에게 전달한다`, async () => {
    await withClient(async () => {
      let calls = 0
      auth.api.defaults.adapter = async (config) => {
        calls++
        throw Object.assign(new Error(code), { config, response: { status, data: { code, message: code } } })
      }
      await assert.rejects(uploadMoveInPhotos(21, photos()), (failure) => failure.response.data.code === code)
      assert.equal(calls, 1)
    })
  })
}

test('타임아웃은 자동 재전송하지 않는다', async () => {
  await withClient(async () => {
    let calls = 0
    auth.api.defaults.adapter = async (config) => {
      calls++
      throw Object.assign(new Error('timeout'), { code: 'ECONNABORTED', config })
    }
    await assert.rejects(uploadMoveInPhotos(21, photos()), (failure) => failure.code === 'ECONNABORTED')
    assert.equal(calls, 1)
  })
})

test('401 인증 만료 시 기존 공통 처리로 세션을 정리하고 업무 요청을 반복하지 않는다', async () => {
  await withClient(async () => {
    let calls = 0
    auth.api.defaults.adapter = async (config) => {
      calls++
      throw Object.assign(new Error('AUTH_REQUIRED'), { config, response: { status: 401, data: { code: 'AUTH_REQUIRED' } } })
    }
    await assert.rejects(uploadMoveInPhotos(21, photos()), (failure) => failure.response.status === 401)
    assert.equal(store.getState().user, null)
    assert.equal(calls, 1)
  })
})

test('Access 만료 시 기존 JWT 갱신이 원래 multipart 사진·설명 순서를 보존한다', async () => {
  await withClient(async () => {
    let calls = 0, originalBody
    auth.api.defaults.adapter = async (config) => {
      calls++
      if (calls === 1) {
        originalBody = config.data
        throw Object.assign(new Error('ACCESS_TOKEN_EXPIRED'), { config, response: { status: 401, data: { code: 'ACCESS_TOKEN_EXPIRED' } } })
      }
      assert.equal(config.data, originalBody)
      assert.deepEqual(config.data.getAll('images').map((file) => file.name), ['first.png', 'second.jpg'])
      assert.deepEqual(JSON.parse(await config.data.get('request').text()).captions, ['', '벽지 흠집'])
      return { status: 201, data: { contractId: 21, photos: [{ moveInDefectPhotoId: 1 }] }, config, headers: {} }
    }
    const result = await uploadMoveInPhotos(21, photos())
    assert.equal(calls, 2)
    assert.equal(result.contractId, 21)
  })
})

// jsdom 등을 추가하지 않고 Vite가 실제 라우트를 제공한다. API adapter만 Mock으로 교체한다.
test('실제 브라우저에서 계약 context와 입주 사진 상태 전환을 검증한다', {
  skip: process.env.MOVEIN_BROWSER_TEST !== '1' && 'MOVEIN_BROWSER_TEST=1 및 출력 URL을 열 브라우저 필요',
}, async (t) => {
  let receiveReport
  const report = new Promise((resolve) => { receiveReport = resolve })
  const browserServer = await createServer({
    configFile: false,
    plugins: [{
      name: 'movein-browser-regression',
      resolveId(id) { if (id === '/movein-browser-entry.js') return '\0movein-browser-entry' },
      load(id) {
        if (id !== '\0movein-browser-entry') return
        return `
          import React from 'react'
          import { createRoot } from 'react-dom/client'
          import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
          import { RouterProvider } from 'react-router/dom'
          import { router } from '/src/router/index.jsx'
          import { useAuthStore } from '/src/stores/authStore.js'
          import { api, publicApi } from '/src/utils/jwtUtil.js'
          const results = await (${runBrowserScenarios.toString()})({ React, createRoot, QueryClient,
            QueryClientProvider, RouterProvider, router, useAuthStore, api, publicApi })
          await fetch('/movein-browser-result', { method: 'POST', body: JSON.stringify(results) })
          document.title = results.every(result => !result.error) ? 'MOVEIN PASS' : 'MOVEIN FAIL'
        `
      },
      configureServer(vite) {
        vite.middlewares.use(async (req, res, next) => {
          if (req.url === '/movein-browser-result' && req.method === 'POST') {
            let body = ''
            for await (const chunk of req) body += chunk
            try { receiveReport(JSON.parse(body)); res.end('ok') }
            catch { res.statusCode = 400; res.end('invalid report') }
            return
          }
          if (req.url !== '/my-home/move-in') return next()
          res.setHeader('Content-Type', 'text/html; charset=utf-8')
          res.end(await vite.transformIndexHtml(req.url,
            '<html><head></head><body><div id="root"></div><script type="module" src="/movein-browser-entry.js"></script></body></html>'))
        })
      },
    }, react()],
    cacheDir: 'node_modules/.vite-movein-browser-test',
    server: { host: '127.0.0.1', port: 0, hmr: false },
  })
  let timer
  try {
    await browserServer.listen()
    const port = browserServer.httpServer.address().port
    t.diagnostic(`브라우저 자동 테스트 URL: http://127.0.0.1:${port}/my-home/move-in`)
    // node:test의 diagnostic 출력은 테스트 완료까지 지연될 수 있어 URL은 즉시 출력한다.
    console.log(`MOVEIN_BROWSER_URL=http://127.0.0.1:${port}/my-home/move-in`)
    const results = await Promise.race([report, new Promise((_, reject) => {
      timer = setTimeout(() => reject(new Error('90초 이내에 브라우저 URL을 열고 테스트 결과를 받아야 합니다.')), 90000)
    })])
    for (const result of results) {
      await t.test(result.name, () => assert.equal(result.error, undefined, result.error))
    }
    assert.equal(results.length, 10, '중간 실패 없이 모든 브라우저 시나리오를 실행해야 한다')
  } finally {
    clearTimeout(timer)
    await browserServer.close()
  }
})

// 이 함수는 문자열로 Vite에 전달되어 실제 브라우저 안에서 실행된다. Node용 DOM 모방은 없다.
async function runBrowserScenarios({ React, createRoot, QueryClient, QueryClientProvider, RouterProvider,
  router, useAuthStore, api, publicApi }) {
  const results = []
  const assert = (condition, message) => { if (!condition) throw new Error(message) }
  const equal = (actual, expected, message) => assert(JSON.stringify(actual) === JSON.stringify(expected),
    `${message}: actual=${JSON.stringify(actual)}, expected=${JSON.stringify(expected)}`)
  const waitFor = async (predicate, message) => {
    const deadline = Date.now() + 5000
    while (!predicate()) {
      if (Date.now() > deadline) throw new Error(message)
      await new Promise((resolve) => setTimeout(resolve, 10))
    }
  }
  const flush = () => new Promise((resolve) => setTimeout(resolve, 0))
  const check = async (name, run) => {
    try { await run(); results.push({ name }) }
    catch (error) { results.push({ name, error: error.stack }); throw error }
  }
  const account = (userId) => ({ userId, name: `테스트 사용자 ${userId}`, role: 'USER', contractRoles: [] })
  const contracts = [21, 22].map((contractId) => ({
    contractId, propertyId: contractId, roadAddress: `테스트 계약 ${contractId}`, detailAddress: '101호',
    leaseType: 'MONTHLY', deposit: 10000000, monthlyRent: 500000, paymentDay: 10,
    startAt: '2026-11-01', endAt: '2027-10-31',
  }))
  const calls = [], queries = [], deferred = []
  let mode = 'success', settled = 0
  publicApi.defaults.adapter = async (config) => ({ config, headers: {}, status: 200,
    data: { headerName: 'X-XSRF-TOKEN', token: 'browser-test-csrf' } })
  api.defaults.adapter = async (config) => {
    if (config.method === 'get' && config.url === '/api/tenants/me/contracts') {
      queries.push(useAuthStore.getState().user?.userId)
      return { config, headers: {}, status: 200, data: useAuthStore.getState().user?.userId === 99 ? [] : contracts }
    }
    assert(config.method === 'post' && /^\/contract\/\d+\/movein-defects$/.test(config.url), `예상하지 않은 요청 ${config.url}`)
    const behavior = mode
    const images = config.data.getAll('images')
    const captions = JSON.parse(await config.data.get('request').text()).captions
    calls.push({ url: config.url, names: images.map((file) => file.name), captions })
    try {
      if (behavior.startsWith('defer')) await new Promise((resolve) => deferred.push(resolve))
      if (behavior === 'timeout') throw Object.assign(new Error('timeout'), { config, code: 'ECONNABORTED' })
      if (['400', '503', 'defer-error'].includes(behavior)) {
        throw Object.assign(new Error('업무 오류'), { config, response: { status: behavior === '400' ? 400 : 503,
          data: { message: behavior === '400' ? '계약일부터 7일 후까지 입주 하자 사진을 올릴 수 있습니다.' : '사진을 저장할 수 없습니다. 잠시 후 다시 시도해 주세요.' } } })
      }
      return { config, headers: {}, status: 201, data: { contractId: Number(config.url.split('/')[2]),
        photos: images.map((_, index) => ({ moveInDefectPhotoId: index + 1, caption: captions[index], createdAt: '2026-11-18T12:00:00' })) } }
    } finally { settled++ }
  }
  const liveUrls = new Set()
  const originalCreate = URL.createObjectURL.bind(URL), originalRevoke = URL.revokeObjectURL.bind(URL)
  URL.createObjectURL = (file) => { const url = originalCreate(file); liveUrls.add(url); return url }
  URL.revokeObjectURL = (url) => { liveUrls.delete(url); originalRevoke(url) }
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
  useAuthStore.getState().login(account(1))
  const root = createRoot(document.getElementById('root'))
  root.render(React.createElement(React.StrictMode, null,
    React.createElement(QueryClientProvider, { client }, React.createElement(RouterProvider, { router }))))
  const form = () => document.querySelector('form')
  const captions = () => [...document.querySelectorAll('input[id^="defect-caption-"]')]
  const names = () => [...document.querySelectorAll('form li img')].map((image) => image.alt)
  const status = () => form()?.querySelector('[role="status"]')?.textContent || ''
  const error = () => form()?.querySelector('[role="alert"]')?.textContent || ''
  const fileInput = () => document.querySelector('input[type="file"]')
  const choose = async (contractId) => {
    const button = [...document.querySelectorAll('section button')].find((item) => item.textContent.includes(`테스트 계약 ${contractId}`))
    assert(button, '실제 계약 목록에 선택할 계약이 있어야 한다')
    button.click()
    await waitFor(() => button.getAttribute('aria-pressed') === 'true' && !fileInput().matches(':disabled'), '선택 계약의 context가 업로드 화면에 전달되지 않음')
  }
  const select = async (count, prefix = 'photo') => {
    const transfer = new DataTransfer()
    const bytes = Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jPioAAAAASUVORK5CYII='), (char) => char.charCodeAt(0))
    for (let index = 0; index < count; index++) transfer.items.add(new File([bytes], `${prefix}${index}.png`, { type: 'image/png' }))
    fileInput().files = transfer.files
    fileInput().dispatchEvent(new Event('change', { bubbles: true }))
    await flush()
  }
  const fill = async (index, value) => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(captions()[index], value)
    captions()[index].dispatchEvent(new Event('input', { bubbles: true }))
    await flush()
  }
  const submit = () => form().dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
  const changeUser = async (id) => {
    useAuthStore.getState().login(account(id))
    await waitFor(() => fileInput()?.matches(':disabled') && names().length === 0, '사용자 변경 시 선택 계약·파일 초기화 실패')
    await flush()
  }
  const assertEmpty = () => {
    equal(names(), [], '파일명 초기화')
    equal(captions().length, 0, '설명 초기화')
    equal(fileInput().files.length, 0, 'FileDropzone 내부 선택 초기화')
    equal(form().querySelectorAll('fieldset ul li').length, 0, 'FileDropzone 내부 파일명과 사진 목록 초기화')
    equal(error(), '', '오류 초기화')
    equal(status(), '', '성공 메시지 초기화')
    equal(liveUrls.size, 0, '이전 미리보기 URL 해제')
  }
  try {
    await check('실제 USER 라우트·계약 목록 API·선택 context를 연결한다', async () => {
      await waitFor(() => document.querySelector('section button[aria-pressed]') && form(), '라우트 로딩 실패')
      await waitFor(() => document.querySelector('section').textContent.includes('테스트 계약 22'), '계약 API 목록 표시 실패')
      equal(queries, [1], '사용자별 실제 계약 API 헬퍼 호출')
      assert(fileInput().matches(':disabled'), '미선택 상태에서 입력 금지')
      submit(); await flush(); equal(calls.length, 0, '미선택 제출 금지')
      await choose(21)
      assert(!fileInput().matches(':disabled'), '실제 레이아웃의 선택 context로 입력 활성화')
    })
    await check('파일 제거 뒤 파일·설명 순서와 실제 선택 계약 ID를 보존한다', async () => {
      await select(3, 'order'); await fill(0, '첫 설명'); await fill(1, '둘 설명'); await fill(2, '셋 설명')
      document.querySelectorAll('form li button')[1].click(); await flush()
      equal(names(), ['order0.png', 'order2.png'], '제거 후 파일 순서')
      equal(captions().map((input) => input.value), ['첫 설명', '셋 설명'], '제거 후 설명 대응')
      mode = 'defer-success'; submit(); submit()
      await waitFor(() => deferred.length === 1, '업로드 요청 대기 실패')
      equal(calls.length, 1, '같은 이벤트 루프의 중복 제출 차단')
      equal(calls[0], { url: '/contract/21/movein-defects', names: ['order0.png', 'order2.png'], captions: ['첫 설명', '셋 설명'] }, 'context의 계약으로 순서대로 전송')
      assert(fileInput().matches(':disabled') && captions().every((input) => input.matches(':disabled')), '진행 중 편집 차단')
    })
    await check('계약 변경 시 파일·설명 초기화와 이전 성공 응답 격리', async () => {
      await choose(22); assertEmpty()
      await select(1, 'current-contract'); await fill(0, '현재 계약 설명')
      const before = settled; deferred.shift()()
      await waitFor(() => settled === before + 1, '이전 요청 완료 실패'); await flush()
      equal(names(), ['current-contract0.png'], '이전 성공 응답이 현재 파일을 지우지 않음')
      equal(captions()[0].value, '현재 계약 설명', '현재 설명 유지')
      equal(status(), '', '이전 계약 성공 메시지를 표시하지 않음')
      await choose(21); assertEmpty()
    })
    await check('0·10·11장 및 빈 설명·255·256자 경계를 검증한다', async () => {
      const before = calls.length
      submit(); await flush(); equal(calls.length, before, '0장 제출 차단')
      await select(11); equal(names().length, 0, '11장 선택 거절'); assert(error(), '개수 오류 안내')
      await select(10); equal(names().length, 10, '10장 선택 허용')
      await select(1, 'extra'); equal(names().length, 10, '추가 1장 거절')
      await fill(0, '가'.repeat(256)); submit(); await flush(); equal(calls.length, before, '256자 제출 차단')
      await fill(0, '가'.repeat(255)); mode = 'success'; submit()
      await waitFor(() => status().includes('사진 10장'), '255자와 빈 설명 업로드 실패')
      equal(calls.at(-1).captions, ['가'.repeat(255), ...Array(9).fill('')], '빈 설명은 null 대신 빈 문자열')
      assertEmptyAfterSuccess()
    })
    await check('기간·저장소 업무 오류에 입력 유지, 수동 재시도 성공 후 초기화', async () => {
      await select(1, 'retry'); await fill(0, '재시도 설명')
      for (const behavior of ['400', '503']) {
        mode = behavior; const before = calls.length; submit()
        await waitFor(() => error().includes(behavior === '400' ? '계약일부터' : '사진을 저장할 수'), '업무 오류 표시 실패')
        equal(calls.length, before + 1, '업무 오류 자동 재전송 없음')
        equal(names(), ['retry0.png'], '오류 후 파일 유지'); equal(captions()[0].value, '재시도 설명', '오류 후 설명 유지')
        assert(!fileInput().matches(':disabled'), '오류 뒤 재시도 가능')
      }
      mode = 'success'; submit(); await waitFor(() => status().includes('사진 1장'), '재시도 성공 안내 실패')
      assertEmptyAfterSuccess()
      await choose(22); assertEmpty()
    })
    await check('타임아웃에서 결과 불확실 안내와 입력을 유지하고 자동 재전송하지 않는다', async () => {
      await select(1, 'timeout'); await fill(0, '응답 유실'); mode = 'timeout'
      const before = calls.length; submit()
      await waitFor(() => error().includes('중복 등록될 수'), '타임아웃 안내 누락')
      equal(calls.length, before + 1, '타임아웃 자동 재전송 없음')
      equal(names(), ['timeout0.png'], '타임아웃 파일 유지'); equal(captions()[0].value, '응답 유실', '설명 유지')
      await choose(21); assertEmpty()
    })
    await check('사용자 변경 시 선택 계약 초기화와 이전 오류 응답 격리', async () => {
      await select(1, 'old-user'); mode = 'defer-error'; submit()
      await waitFor(() => deferred.length === 1, '이전 사용자 요청 대기 실패')
      await changeUser(2)
      equal(error(), '', '이전 사용자 오류 초기화'); equal(names(), [], '이전 사용자 파일명 초기화')
      equal(fileInput().files.length, 0, '파일 선택기 초기화')
      assert([...document.querySelectorAll('section button[aria-pressed="true"]')].some((button) => button.textContent === '전체 보기'), '사용자 변경 후 계약 선택 초기화')
      await choose(22); await select(1, 'new-user'); await fill(0, '새 사용자 설명')
      const before = settled; deferred.shift()()
      await waitFor(() => settled === before + 1, '이전 사용자 요청 완료 실패'); await flush()
      equal(error(), '', '이전 사용자 지연 오류 격리'); equal(names(), ['new-user0.png'], '새 사용자 파일 유지')
      equal(captions()[0].value, '새 사용자 설명', '새 사용자 설명 유지')
    })
    await check('사용자 변경 시 이미 표시된 오류·성공 메시지도 초기화한다', async () => {
      mode = '503'; submit(); await waitFor(() => error(), '오류 대기 실패')
      await changeUser(3); await choose(21); assertEmpty()
      await select(1, 'success-user'); mode = 'success'; submit()
      await waitFor(() => status().includes('사진 1장'), '사용자 성공 안내 실패')
      await changeUser(4); await choose(21); assertEmpty()
    })
    await check('실제 RequireRole은 비로그인·ADMIN·AGENT 접근을 차단한다', async () => {
      for (const next of [null, { userId: 5, role: 'ADMIN' }, { userId: 6, role: 'AGENT', approvalStatus: 'APPROVED' }]) {
        useAuthStore.setState({ user: next })
        await waitFor(() => !form(), '허용하지 않은 계정의 업로드 화면 노출')
        assert(!document.body.textContent.includes('테스트 계약 21'), '계약 목록 미노출')
      }
    })
    await check('계약 없는 USER에게 실제 API 빈 목록과 업로드 불가 안내를 표시한다', async () => {
      await changeUser(99)
      await waitFor(() => document.body.textContent.includes('아직 계약한 매물이 없어요'), '빈 계약 목록 안내 누락')
      assert(form().textContent.includes('계약한 매물이 없으면'), '업로드 불가 안내 누락')
      const before = calls.length; submit(); await flush(); equal(calls.length, before, '계약 없는 사용자 제출 차단')
      equal(liveUrls.size, 0, '미리보기 URL 누수 없음')
    })
  } catch {
    // 실패한 시나리오를 Node의 실제 실패로 전달하고 후속 연쇄 오류는 만들지 않는다.
  } finally {
    root.unmount(); client.clear(); router.dispose()
    URL.createObjectURL = originalCreate; URL.revokeObjectURL = originalRevoke
  }
  return results

  function assertEmptyAfterSuccess() {
    equal(names(), [], '성공 후 사진 초기화'); equal(captions().length, 0, '성공 후 설명 초기화')
    equal(fileInput().files.length, 0, '성공 후 파일 선택기 초기화')
    assert(document.querySelector('button[type="submit"]').matches(':disabled'), '성공 후 중복 제출 차단')
    equal(liveUrls.size, 0, '성공 후 URL 해제')
  }
}
