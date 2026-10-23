// NOTIFY_001, NOTIFY_007, NOTIFY_021 (안 읽은 알림 수), CHAT_007 (안 읽은 메시지 수)
import { Link } from 'react-router'
import { useAuthStore } from '../../stores/authStore.js'
import { ROLE_LABELS } from '../../constants/auth.js'
import logoIcon from '../../assets/logo-icon.svg'

// 각 메뉴의 페이지가 아직 없어서 링크는 비워 둔다
const NAV_ITEMS = ['매물 찾기', '관심 매물', '내 계약 관리', '채팅']

// TODO: 안 읽은 수 API 연동 전까지 쓰는 임시 값
// - 알림: GET /api/v1/notifications/unread-count (BE-080) 응답의 unreadCount
// - 채팅: GET /api/v1/chat/rooms (BE-038) 응답의 rooms[].unreadCount 합계. 전체 합계 API가 없어 백엔드와 협의 필요
const SAMPLE_UNREAD_NOTIFICATIONS = 3
const SAMPLE_UNREAD_CHATS = 2

function CountBadge({ count }) {
  if (count <= 0) return null
  return (
    <span aria-hidden="true" className="min-w-5 rounded-full bg-red-600 px-1.5 text-center text-xs leading-5 font-bold text-white">
      {count > 99 ? '99+' : count}
    </span>
  )
}

function Header({ id }) {
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)

  // TODO: POST /api/v1/auth/logout (BE-002) 호출 후 logout() 실행
  const handleLogout = () => {
    logout()
  }

  return (
    <header id={id} className="border-b border-gray-200 bg-white">
      <div className="mx-auto flex max-w-[1200px] flex-wrap items-center gap-6 px-6 py-4">
        <Link to="/" className="flex items-center gap-2.5 font-logo text-[28px] leading-none text-blue-900">
          <img src={logoIcon} alt="" className="size-10" />
          보금
        </Link>
        <nav aria-label="주요 메뉴" className="flex grow flex-wrap gap-6 text-[15px] font-medium">
          {NAV_ITEMS.map((label) => {
            const unreadCount = user && label === '채팅' ? SAMPLE_UNREAD_CHATS : 0
            return (
              <a
                key={label}
                href="#"
                aria-label={unreadCount > 0 ? `${label}, 안 읽은 메시지 ${unreadCount}개` : undefined}
                className="flex items-center gap-1.5 text-gray-900 hover:text-blue-700"
              >
                {label}
                <CountBadge count={unreadCount} />
              </a>
            )
          })}
        </nav>
        {user ? (
          <div className="flex items-center gap-3">
            {/* 알림 목록 화면이 아직 없어서 버튼만 둔다 (BE-079) */}
            <button
              type="button"
              aria-label={`알림, 안 읽은 알림 ${SAMPLE_UNREAD_NOTIFICATIONS}개`}
              className="relative flex size-11 items-center justify-center rounded-lg text-gray-700 hover:bg-gray-100"
            >
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
                <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
              </svg>
              <span className="absolute top-1 right-0.5">
                <CountBadge count={SAMPLE_UNREAD_NOTIFICATIONS} />
              </span>
            </button>
            <span className="text-sm text-gray-600">{ROLE_LABELS[user.role]}</span>
            <button
              type="button"
              onClick={handleLogout}
              className="rounded-lg border border-gray-300 px-[18px] py-2.5 text-sm font-medium text-gray-900 hover:bg-gray-50"
            >
              로그아웃
            </button>
          </div>
        ) : (
          <Link to="/login" className="rounded-lg border border-gray-300 px-[18px] py-2.5 text-sm font-medium text-gray-900 hover:bg-gray-50">
            로그인
          </Link>
        )}
      </div>
    </header>
  )
}

export default Header
