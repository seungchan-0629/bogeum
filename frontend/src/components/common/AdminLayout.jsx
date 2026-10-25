// [공통 레이아웃] 관리자 화면 틀 — /admin 아래 모든 화면
// 일반 헤더 대신 왼쪽 메뉴(사이드바)를 쓴다. 관리자가 아니면 들어올 수 없다
// - 로그인 안 됨 → /admin/login 으로 이동
// - 관리자가 아닌 역할 → 권한 없음(403) 페이지
// - 챗봇: 관리자용 위험·연체 현황 조회, 보고서 생성 (CHATBOT_007, CHATBOT_008)
import { Navigate, NavLink, Outlet, ScrollRestoration, useNavigate } from 'react-router'
import clsx from 'clsx'
import Logo from './Logo.jsx'
import ChatbotLauncher from '../chatbot/ChatbotLauncher.jsx'
import ForbiddenPage from '../../pages/error/ForbiddenPage.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { ROLES } from '../../constants/auth.js'

const ADMIN_MENU = [
  { label: '매물 관리', to: '/admin/properties' },
  { label: '중개사 관리', to: '/admin/agents' },
  { label: '월세 관리', to: '/admin/rent' },
  { label: '위험 사용자', to: '/admin/risk-users' },
  { label: '위험 매물', to: '/admin/risk-properties' },
  { label: '신고 대화', to: '/admin/chats' },
  { label: '알림', to: '/admin/notifications' },
]

function AdminLayout() {
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)
  const navigate = useNavigate()

  if (!user) return <Navigate to="/admin/login" replace />
  if (user.role !== ROLES.ADMIN) return <ForbiddenPage allowedRoles={[ROLES.ADMIN]} />

  // TODO: POST /api/v1/auth/logout (BE-002) 호출 후 logout() 실행
  const handleLogout = () => {
    logout()
    navigate('/admin/login')
  }

  return (
    <div className="flex min-h-svh flex-col bg-slate-50 md:flex-row">
      <ScrollRestoration />
      <aside className="flex shrink-0 flex-col gap-1 bg-night px-3.5 py-5 text-slate-300 md:sticky md:top-0 md:h-svh md:w-60">
        <div className="px-2 pb-5">
          <Logo to="/admin" dark />
          <p className="mt-1.5 text-xs text-blue-300">관리자</p>
        </div>
        <nav aria-label="관리자 메뉴" className="flex flex-wrap gap-1 md:flex-col">
          {ADMIN_MENU.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                clsx('rounded-lg px-3 py-2.5 text-sm', isActive ? 'bg-blue-900 font-bold text-white' : 'hover:bg-night-building hover:text-white')
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <button type="button" onClick={handleLogout} className="mt-auto rounded-lg px-3 py-2.5 text-left text-sm hover:bg-night-building hover:text-white">
          로그아웃
        </button>
      </aside>
      <main className="flex min-w-0 grow flex-col gap-5 px-6 py-7 md:px-8">
        <Outlet />
      </main>
      <ChatbotLauncher role={ROLES.ADMIN} />
    </div>
  )
}

export default AdminLayout
