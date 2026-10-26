// [공통 컴포넌트] 계약상 역할 검사 — router/index.jsx 에서 사용자(USER) 화면 묶음을 감싼다
// 임대인·임차인은 가입할 때가 아니라 계약을 등록할 때 정해진다 (PROPERTY_011)
// - 로그인한 사용자의 contractRoles에 contractRole이 있으면 하위 화면(Outlet)
// - 없으면 '관련 정보가 없어요' 안내 (헤더·푸터는 RequireRole의 AppLayout이 그대로 붙인다)
// 실제 데이터 접근 권한은 서버가 '이 계약의 임대인/임차인이 나인가'로 다시 확인한다
import { Outlet } from 'react-router'
import EmptyGuide from './EmptyGuide.jsx'
import { useAuthStore } from '../../stores/authStore.js'

function RequireContract({ contractRole, description }) {
  const user = useAuthStore((state) => state.user)

  if (!user.contractRoles?.includes(contractRole)) return <EmptyGuide description={description} />
  return <Outlet />
}

export default RequireContract
