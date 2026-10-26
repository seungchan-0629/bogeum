// [공통 레이아웃] 일반 화면 틀 — 헤더 + 본문(children) + 푸터 + 챗봇 버튼
// RequireRole이 권한 검사를 통과한 화면(과 중개사 승인 대기 화면)을 이 틀에 넣어 보여준다
// 로그인 안내·권한 없음 화면은 이 틀 없이 전체 화면으로 보여준다
import Header from './Header.jsx'
import Footer from './Footer.jsx'
import ChatbotLauncher from '../chatbot/ChatbotLauncher.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { ROLES } from '../../constants/auth.js'

function AppLayout({ children }) {
  const user = useAuthStore((state) => state.user)
  // 챗봇은 사용자(임대인·임차인)·관리자만 쓴다 (CHATBOT_001). 관리자는 AdminLayout에서 띄운다
  const canUseChatbot = user?.role === ROLES.USER

  return (
    <div className="flex min-h-svh flex-col bg-slate-50">
      <Header />
      <main className="flex grow flex-col">{children}</main>
      <Footer />
      {canUseChatbot && <ChatbotLauncher role={user.role} />}
    </div>
  )
}

export default AppLayout
