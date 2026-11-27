// 알림 목록·헤더 말풍선에서 같이 쓰는 표시 규칙 (NOTIFY_007, NOTIFY_021)

const MINUTE_MS = 60 * 1000
const HOUR_MS = 60 * MINUTE_MS
const DAY_MS = 24 * HOUR_MS

// 알림 시각을 짧게 보여 준다. 방금 / 12분 전 / 3시간 전 / 어제 / 10.18 / 2025.10.18
// createdAt은 서버가 준 시각 문자열(예: '2026-11-26T14:05:00')
export function formatNotificationTime(createdAt, now = new Date()) {
  const date = new Date(createdAt)
  const elapsed = now - date
  if (elapsed < MINUTE_MS) return '방금'
  if (elapsed < HOUR_MS) return `${Math.floor(elapsed / MINUTE_MS)}분 전`
  if (elapsed < DAY_MS) return `${Math.floor(elapsed / HOUR_MS)}시간 전`
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) return '어제'
  const monthDay = `${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
  return date.getFullYear() === now.getFullYear() ? monthDay : `${date.getFullYear()}.${monthDay}`
}

// 알림을 눌렀을 때 갈 화면. 지금 실제로 생기는 알림은 채팅뿐이라 채팅방만 잇는다
// 다른 종류(AS, 중개 등)는 그 알림을 만들 때 이동할 화면을 정한다. 그 전까지는 읽음 처리만 하고 이동하지 않는다
export function getNotificationPath({ refType, refId }) {
  if (refType === 'CHAT_ROOM') return `/chats/${refId}`
  return null
}
