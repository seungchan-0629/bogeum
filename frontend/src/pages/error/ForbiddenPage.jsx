// [페이지] 403 · 권한 없음 — 주소가 따로 없고, RequireRole이 역할이 맞지 않을 때 대신 보여준다
// 예: 임차인이 임대인 전용 화면(/my-properties)에 들어온 경우
// - allowedRoles: 이 화면을 볼 수 있는 역할 목록 (안내 문구에 쓴다)
import { Link, useNavigate } from 'react-router'
import GuideScreen from '../../components/common/GuideScreen.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { ROLE_LABELS } from '../../constants/auth.js'

function ForbiddenPage({ allowedRoles = [] }) {
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)
  const navigate = useNavigate()
  const allowedLabel = allowedRoles.map((role) => ROLE_LABELS[role]).join('·')

  // TODO: POST /api/v1/auth/logout (BE-002) 호출 후 logout() 실행
  const handleSwitchAccount = () => {
    logout()
    navigate('/login')
  }

  return (
    <GuideScreen
      code="403"
      title="들어갈 수 없는 방이에요"
      description={`이 화면은 ${allowedLabel}만 볼 수 있어요. 지금은 ${ROLE_LABELS[user.role]}(으)로 로그인되어 있어요.`}
      litRatio={0.3}
    >
      <Link to="/" className="btn-primary">메인으로</Link>
      <button type="button" onClick={handleSwitchAccount} className="btn-secondary">다른 계정으로 로그인</button>
    </GuideScreen>
  )
}

export default ForbiddenPage
