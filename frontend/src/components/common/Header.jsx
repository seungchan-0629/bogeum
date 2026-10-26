// [공통 컴포넌트] 상단 헤더 — 관리자 화면(/admin)을 뺀 모든 화면 위에 붙는다
// 역할별 메뉴는 constants/navigation.js 의 NAV_ITEMS_BY_ROLE 에서 바꾼다
// 요구사항: NOTIFY_001, NOTIFY_007, NOTIFY_021 (안 읽은 알림 수), CHAT_007 (안 읽은 메시지 수)
import { useEffect, useRef, useState } from 'react'
import { NavLink, Link, useLocation, useNavigate } from 'react-router'
import clsx from 'clsx'
import Logo from './Logo.jsx'
import Icon from './Icon.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { ROLE_LABELS } from '../../constants/auth.js'
import { MY_PAGE_PATH_BY_ROLE, NAV_ITEMS_BY_ROLE } from '../../constants/navigation.js'

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

const navLinkClassName = ({ isActive }) =>
  clsx('flex items-center gap-1.5', isActive ? 'font-bold text-blue-700' : 'text-gray-900 hover:text-blue-700')

// 하위 메뉴를 드롭다운으로 묶은 메뉴. 바깥을 누르거나 Esc를 누르면 닫힌다
function NavGroup({ item }) {
  const { pathname } = useLocation()
  const [isOpen, setIsOpen] = useState(false)
  const groupRef = useRef(null)
  const isActive = item.children.some((child) => pathname.startsWith(child.to))

  useEffect(() => {
    if (!isOpen) return
    const handlePointerDown = (event) => {
      if (!groupRef.current.contains(event.target)) setIsOpen(false)
    }
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') setIsOpen(false)
    }
    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen])

  return (
    <div ref={groupRef} className="relative">
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        aria-expanded={isOpen}
        className={clsx('flex items-center gap-1', isActive ? 'font-bold text-blue-700' : 'text-gray-900 hover:text-blue-700')}
      >
        {item.label}
        <Icon name="chevronDown" size={14} className={clsx('transition-transform', isOpen && 'rotate-180')} />
      </button>
      {isOpen && (
        <div className="absolute top-full left-[-12px] z-40 mt-2 flex w-40 flex-col rounded-xl border border-gray-200 bg-white p-1.5 shadow-[0_12px_30px_rgba(15,23,42,0.18)]">
          {item.children.map((child) => (
            <NavLink
              key={child.to}
              to={child.to}
              onClick={() => setIsOpen(false)}
              className={({ isActive: isChildActive }) =>
                clsx('rounded-lg px-3 py-2.5', isChildActive ? 'bg-slate-100 font-bold text-blue-700' : 'text-gray-900 hover:bg-slate-50')
              }
            >
              {child.label}
            </NavLink>
          ))}
        </div>
      )}
    </div>
  )
}

function Header() {
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)
  const navigate = useNavigate()
  const navItems = NAV_ITEMS_BY_ROLE[user?.role ?? 'GUEST']
  const myPagePath = user && MY_PAGE_PATH_BY_ROLE[user.role]

  // TODO: POST /api/v1/auth/logout (BE-002) 호출 후 logout() 실행
  const handleLogout = () => {
    logout()
    navigate('/')
  }

  return (
    <header className="border-b border-gray-200 bg-white">
      <div className="mx-auto flex max-w-[1200px] flex-wrap items-center gap-x-7 gap-y-3 px-6 py-3.5">
        <Logo />
        <nav aria-label="주요 메뉴" className="flex grow flex-wrap gap-x-6 gap-y-2 text-[15px] font-medium">
          {navItems.map((item) => {
            if (item.children) return <NavGroup key={item.label} item={item} />
            const unreadCount = user && item.to === '/chats' ? SAMPLE_UNREAD_CHATS : 0
            return (
              <NavLink
                key={item.to}
                to={item.to}
                aria-label={unreadCount > 0 ? `${item.label}, 안 읽은 메시지 ${unreadCount}개` : undefined}
                className={navLinkClassName}
              >
                {item.label}
                <CountBadge count={unreadCount} />
              </NavLink>
            )
          })}
        </nav>
        {user ? (
          <div className="flex flex-wrap items-center gap-2.5">
            <Link
              to="/notifications"
              aria-label={`알림, 안 읽은 알림 ${SAMPLE_UNREAD_NOTIFICATIONS}개`}
              className="relative flex size-11 items-center justify-center rounded-lg text-gray-700 hover:bg-gray-100"
            >
              <Icon name="bell" size={22} />
              <span className="absolute top-1 right-0.5">
                <CountBadge count={SAMPLE_UNREAD_NOTIFICATIONS} />
              </span>
            </Link>
            <span className="text-sm text-gray-600">
              {ROLE_LABELS[user.role]} · {user.name}
            </span>
            {myPagePath && <Link to={myPagePath} className="btn-secondary btn-sm">마이페이지</Link>}
            <button type="button" onClick={handleLogout} className="btn-secondary btn-sm">로그아웃</button>
          </div>
        ) : (
          <div className="flex items-center gap-2">
            <Link to="/login" className="btn-secondary btn-sm">로그인</Link>
            <Link to="/signup" className="btn-primary btn-sm">회원가입</Link>
          </div>
        )}
      </div>
    </header>
  )
}

export default Header
