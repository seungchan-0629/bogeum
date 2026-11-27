import { api } from '../../utils/jwtUtil.js'

// 알림 API는 /api 아래가 아니라서 공통 baseURL(/api)을 비우고 보낸다. 토큰·CSRF는 api 인터셉터가 붙인다.
const NOTIFICATION_CONFIG = { baseURL: '' }

// NOTIFY_007·021 내 알림 최신순. cursor가 없으면 최근 것부터, 있으면 그보다 오래된 것. size는 1~20
// 응답: { notifications: [{ notificationId, type, title, body, refType, refId, isRead, createdAt }], nextCursor, hasNext }
export async function getNotifications({ cursor, size, unreadOnly }, signal) {
  const { data } = await api.get('/notifications/me', { ...NOTIFICATION_CONFIG, params: { cursor, size, unreadOnly }, signal })
  return data
}

// NOTIFY_007·021 헤더 종 아이콘에 표시할 안 읽은 알림 수
export async function getUnreadNotificationCount(signal) {
  const { data } = await api.get('/notifications/unread-count', { ...NOTIFICATION_CONFIG, signal })
  return data.unreadCount
}

// NOTIFY_007·021 알림을 누르면 읽음으로 바꾼다 (204). 이미 읽었으면 그대로, 내 알림이 아니면 404(NOTIFICATION_NOT_FOUND)
export async function readNotification(notificationId) {
  await api.patch(`/notifications/${notificationId}/read`, null, NOTIFICATION_CONFIG)
}

// 안 읽은 내 알림을 모두 읽음으로 바꾼다 (204)
export async function readAllNotifications() {
  await api.patch('/notifications/read-all', null, NOTIFICATION_CONFIG)
}
