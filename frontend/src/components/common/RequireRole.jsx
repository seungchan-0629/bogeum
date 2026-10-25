// [공통 컴포넌트] 화면 접근 권한 검사 + 화면 틀 — router/index.jsx 에서 화면 묶음을 감싼다
// - 로그인 안 됨 → 로그인 안내 페이지 (헤더·푸터 없이 전체 화면)
// - 역할이 roles에 없음 → 권한 없음(403) 페이지 (헤더·푸터 없이 전체 화면)
// - requireApproved: 중개사가 관리자 승인 전이면 승인 대기 페이지 (USER_021, 메뉴를 쓸 수 있게 일반 화면 틀 안에)
// 통과하면 하위 화면(Outlet)을 일반 화면 틀(AppLayout: 헤더·푸터)에 넣어 보여준다
// - ScrollRestoration: 다른 화면으로 넘어가면 맨 위부터 보여준다
import { Outlet, ScrollRestoration } from 'react-router'
import AppLayout from './AppLayout.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { ROLES } from '../../constants/auth.js'
import LoginRequiredPage from '../../pages/error/LoginRequiredPage.jsx'
import ForbiddenPage from '../../pages/error/ForbiddenPage.jsx'
import AgentPendingPage from '../../pages/auth/AgentPendingPage.jsx'

function RequireRole({ roles, requireApproved = false }) {
  const user = useAuthStore((state) => state.user)

  let page
  if (!user) page = <LoginRequiredPage />
  else if (!roles.includes(user.role)) page = <ForbiddenPage allowedRoles={roles} />
  else if (requireApproved && user.role === ROLES.AGENT && user.approvalStatus !== 'APPROVED') page = <AppLayout><AgentPendingPage /></AppLayout>
  else page = <AppLayout><Outlet /></AppLayout>

  return (
    <>
      <ScrollRestoration />
      {page}
    </>
  )
}

export default RequireRole
