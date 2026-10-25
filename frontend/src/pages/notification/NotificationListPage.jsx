// [페이지] 알림 목록 — /notifications (관리자는 /admin/notifications 에서 같은 화면을 쓴다)
// 누가: 임차인·임대인·중개사·관리자. 헤더의 종 아이콘을 누르면 이 화면으로 온다
// 요구사항: NOTIFY_001 (관리자), NOTIFY_007 (임대·임차인), NOTIFY_021 (중개사) — 최신순, 안 읽은 수 표시, 누르면 읽음 처리 후 관련 화면 이동
// API:
// - 목록: GET /api/v1/notifications?cursor=&size= (BE-079)
// - 안 읽은 수: GET /api/v1/notifications/unread-count (BE-080)
// - 읽음 처리: PUT/PATCH /api/v1/notifications/{notificationId}/read (BE-081)
import { useState } from 'react'
import { useNavigate } from 'react-router'
import clsx from 'clsx'
import PageHeader from '../../components/common/PageHeader.jsx'
import { Tabs } from '../../components/common/Tabs.jsx'
import { useAuthStore } from '../../stores/authStore.js'

// TODO: GET /api/v1/notifications (BE-079) 응답의 notifications[]로 교체
// - 필드: notificationId, title, content, isRead, createdAt, 이동할 화면 정보(type + targetId 등). 이동 정보 형식은 백엔드와 협의 필요
// - 지금은 역할마다 다른 예시를 보여준다
const SAMPLE_NOTIFICATIONS_BY_ROLE = {
  TENANT: [
    { notificationId: 1, title: '이번 달 월세가 결제됐어요', content: '역삼동 오피스텔 · 650,000원 · 10.25 결제', time: '방금', isRead: false, to: '/my-home/rent' },
    { notificationId: 2, title: '보금공인중개사님이 메시지를 보냈어요', content: '"내일 오후 3시에 방문 가능하세요?"', time: '12분 전', isRead: false, to: '/chats/1' },
    { notificationId: 3, title: '관심 매물 가격이 바뀌었어요', content: '공덕동 아파트 · 전세 3억 4,000 → 3억 2,000', time: '1시간 전', isRead: false, to: '/properties/2' },
    { notificationId: 4, title: 'AS 요청에 답변이 달렸어요', content: '화장실 천장 누수 · "수요일 오전에 기사님이 방문할 예정이에요."', time: '어제', isRead: true, to: '/my-home/as' },
    { notificationId: 5, title: '입주 하자 사진 업로드 기한이 다가와요', content: '계약 체결 후 7일 이내 · 2일 남음', time: '10.18', isRead: true, to: '/my-home/move-in' },
  ],
  LANDLORD: [
    { notificationId: 1, title: '월세가 지급됐어요', content: '역삼동 오피스텔 · 650,000원', time: '방금', isRead: false, to: '/rent/collections' },
    { notificationId: 2, title: '새 AS 요청이 들어왔어요', content: '역삼동 오피스텔 · 화장실 천장 누수', time: '1시간 전', isRead: false, to: '/care/as' },
    { notificationId: 3, title: '입주 하자 사진이 올라왔어요', content: '역삼동 오피스텔 · 4장', time: '10.05', isRead: true, to: '/care/move-in' },
  ],
  AGENT: [
    { notificationId: 1, title: '새 중개 신청이 왔어요', content: '역삼동 오피스텔 · 월세 1,000 / 65', time: '방금', isRead: false, to: '/agent/requests' },
    { notificationId: 2, title: '임차인이 메시지를 보냈어요', content: '"주차 가능한가요?"', time: '20분 전', isRead: false, to: '/chats/1' },
  ],
  ADMIN: [
    { notificationId: 1, title: '위험 정보 후보가 등록됐어요', content: '임차인 김보금 · 8월분 월세 미납', time: '방금', isRead: false, to: '/admin/risk-users' },
    { notificationId: 2, title: '월세 결제 최종 재시도가 실패했어요', content: '역삼동 오피스텔 · 8월분', time: '2시간 전', isRead: false, to: '/admin/rent' },
    { notificationId: 3, title: '새 중개사 가입 신청이 있어요', content: '새봄공인중개사', time: '어제', isRead: true, to: '/admin/agents' },
  ],
}

const FILTER_TABS = [
  { key: 'ALL', label: '전체' },
  { key: 'UNREAD', label: '안 읽음' },
]

function NotificationListPage() {
  const user = useAuthStore((state) => state.user)
  const navigate = useNavigate()
  const [filter, setFilter] = useState('ALL')
  const [notifications, setNotifications] = useState(() => SAMPLE_NOTIFICATIONS_BY_ROLE[user.role] ?? [])
  const unreadCount = notifications.filter((item) => !item.isRead).length
  const visibleNotifications = filter === 'UNREAD' ? notifications.filter((item) => !item.isRead) : notifications

  // TODO: PUT/PATCH /api/v1/notifications/{notificationId}/read (BE-081) 호출 후 관련 화면으로 이동
  const handleOpen = (notification) => {
    setNotifications((prev) => prev.map((item) => (item.notificationId === notification.notificationId ? { ...item, isRead: true } : item)))
    navigate(notification.to)
  }

  // TODO: 모두 읽음 API가 명세에 없어 백엔드와 협의 필요 (없으면 안 읽은 알림마다 BE-081 호출)
  const handleReadAll = () => {
    setNotifications((prev) => prev.map((item) => ({ ...item, isRead: true })))
  }

  return (
    <div className="page-container">
      <PageHeader
        title="알림"
        description={`안 읽은 알림 ${unreadCount}개`}
        actions={<button type="button" onClick={handleReadAll} className="btn-secondary btn-sm">모두 읽음</button>}
      />
      <Tabs label="알림 보기" items={FILTER_TABS} value={filter} onChange={setFilter} />
      {visibleNotifications.length > 0 ? (
        <ul className="card p-0">
          {visibleNotifications.map((notification) => (
            <li key={notification.notificationId} className="border-b border-gray-100 last:border-b-0">
              <button type="button" onClick={() => handleOpen(notification)} className="flex w-full items-center gap-3.5 px-5 py-4 text-left hover:bg-slate-50">
                <span aria-hidden="true" className={clsx('size-2 shrink-0 rounded-full', notification.isRead ? 'bg-transparent' : 'bg-blue-600')} />
                <span className="flex min-w-0 grow flex-col">
                  <span className={notification.isRead ? 'text-gray-800' : 'font-bold'}>
                    {!notification.isRead && <span className="sr-only">안 읽음, </span>}
                    {notification.title}
                  </span>
                  <span className="truncate text-sm text-gray-600">{notification.content}</span>
                </span>
                <span className="shrink-0 text-sm text-gray-500">{notification.time}</span>
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="rounded-xl border border-dashed border-gray-300 px-6 py-10 text-center text-gray-600">안 읽은 알림이 없어요.</p>
      )}
    </div>
  )
}

export default NotificationListPage
