import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'

let server
let api
let store
let notificationApi
let formatNotificationTime
let getNotificationPath
let NotificationListPage
let Header
const user = { userId: 7, role: 'USER', name: '최세입' }
const notification = (id, overrides = {}) => ({
  notificationId: id, type: 'CHAT_MESSAGE', title: '새 메시지', body: '김중개: 매물 아직 있나요?',
  refType: 'CHAT_ROOM', refId: 3, isRead: false, createdAt: '2025-10-18T09:00:00', ...overrides,
})

before(async () => {
  server = await createServer({
    configFile: false, plugins: [react()], cacheDir: 'node_modules/.vite-notification-test',
    server: { middlewareMode: true, hmr: false },
  })
  api = (await server.ssrLoadModule('/src/utils/jwtUtil.js')).api
  store = (await server.ssrLoadModule('/src/stores/authStore.js')).useAuthStore
  notificationApi = await server.ssrLoadModule('/src/api/notification/index.js')
  ;({ formatNotificationTime, getNotificationPath } = await server.ssrLoadModule('/src/utils/notification.js'))
  NotificationListPage = (await server.ssrLoadModule('/src/pages/notification/NotificationListPage.jsx')).default
  Header = (await server.ssrLoadModule('/src/components/common/Header.jsx')).default
})

after(async () => { await server?.close() })

function render(component, cached = []) {
  store.getInitialState().user = user
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false, staleTime: Infinity } } })
  for (const [queryKey, data] of cached) client.setQueryData(queryKey, data)
  try {
    return renderToStaticMarkup(createElement(QueryClientProvider, { client },
      createElement(MemoryRouter, { initialEntries: ['/notifications'] }, createElement(component))))
  } finally {
    client.clear()
  }
}

const listKey = ['notifications', 'list', 'ALL']
const listData = (notifications, hasNext = false) => ({ pages: [{ notifications, nextCursor: null, hasNext }], pageParams: [null] })

test('알림 시각을 방금·N분 전·N시간 전·어제·날짜로 보여 준다', () => {
  const now = new Date('2026-11-27T10:00:00')
  assert.equal(formatNotificationTime('2026-11-27T09:59:30', now), '방금')
  assert.equal(formatNotificationTime('2026-11-27T09:48:00', now), '12분 전')
  assert.equal(formatNotificationTime('2026-11-27T07:00:00', now), '3시간 전')
  assert.equal(formatNotificationTime('2026-11-26T08:00:00', now), '어제')
  assert.equal(formatNotificationTime('2026-11-25T08:00:00', now), '11.25')
  assert.equal(formatNotificationTime('2025-10-18T08:00:00', now), '2025.10.18')
})

test('채팅 알림은 채팅방, 중개 신청 알림은 신청 목록으로 잇고, 다른 종류는 이동하지 않는다', () => {
  assert.equal(getNotificationPath({ refType: 'CHAT_ROOM', refId: 3 }), '/chats/3')
  assert.equal(getNotificationPath({ refType: 'BROKERAGE_REQUEST', refId: 50 }), '/agent/requests')
  assert.equal(getNotificationPath({ refType: 'AS_REQUEST', refId: 3 }), null)
  assert.equal(getNotificationPath({ refType: null, refId: null }), null)
})

test('목록·안 읽은 수 API는 /api 없이 보내고 cursor·size·안 읽음 조건을 넘긴다', async () => {
  const original = api.defaults.adapter
  const calls = []
  api.defaults.adapter = async (config) => {
    calls.push(config)
    const data = config.url === '/notifications/unread-count' ? { unreadCount: 4 } : { notifications: [], nextCursor: null, hasNext: false }
    return { status: 200, data, config, headers: {} }
  }
  try {
    assert.deepEqual(await notificationApi.getNotifications({ cursor: 30, size: 20, unreadOnly: true }),
      { notifications: [], nextCursor: null, hasNext: false })
    assert.equal(await notificationApi.getUnreadNotificationCount(), 4)
    assert.deepEqual(calls.map((config) => [config.method, config.baseURL, config.url]), [
      ['get', '', '/notifications/me'], ['get', '', '/notifications/unread-count'],
    ])
    assert.deepEqual(calls[0].params, { cursor: 30, size: 20, unreadOnly: true })
  } finally {
    api.defaults.adapter = original
  }
})

test('알림 화면은 서버 알림의 제목·내용·시각과 안 읽은 수를 보여 준다', () => {
  const html = render(NotificationListPage, [
    [['notification-unread-count'], 1],
    [listKey, listData([notification(2), notification(1, { isRead: true, body: '김중개: 사진을 보냈어요.' })])],
  ])
  for (const text of ['안 읽은 알림 1개', '새 메시지', '김중개: 매물 아직 있나요?', '김중개: 사진을 보냈어요.', '2025.10.18']) {
    assert.ok(html.includes(text), text)
  }
  assert.equal((html.match(/안 읽음, /g) ?? []).length, 1)
  assert.doesNotMatch(html.match(/<button[^>]*>모두 읽음<\/button>/)[0], /disabled=""/)
  assert.doesNotMatch(html, /월세가 결제됐어요/)
})

test('알림이 없으면 안내를 보여 주고, 안 읽은 알림이 없으면 [모두 읽음]을 누를 수 없다', () => {
  const html = render(NotificationListPage, [[['notification-unread-count'], 0], [listKey, listData([])]])
  assert.ok(html.includes('받은 알림이 없어요.'))
  assert.match(html, /<button[^>]*disabled=""[^>]*>모두 읽음<\/button>/)
})

test('헤더 종은 서버의 안 읽은 수를 보여 주고 100개부터 99+로 줄인다', () => {
  let html = render(Header, [[['notification-unread-count'], 3]])
  assert.match(html, /aria-label="알림, 안 읽은 알림 3개"/)
  assert.match(html, /aria-expanded="false"/)
  html = render(Header, [[['notification-unread-count'], 120]])
  assert.ok(html.includes('99+'))
})
