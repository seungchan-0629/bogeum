import { useCallback, useEffect, useState } from 'react'
import Header from '../../components/common/Header.jsx'
import Footer from '../../components/common/Footer.jsx'
import IntroSection from '../../components/main/IntroSection.jsx'
import OnboardingSection from '../../components/main/OnboardingSection.jsx'
import SearchSection from '../../components/main/SearchSection.jsx'
import ListingSection from '../../components/main/ListingSection.jsx'
import ValueSection from '../../components/main/ValueSection.jsx'
import RoleSummarySection from '../../components/main/RoleSummarySection.jsx'
import ChatbotLauncher from '../../components/chatbot/ChatbotLauncher.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { ROLES } from '../../constants/auth.js'

// 소개 화면을 이번 접속에서 이미 봤는지. 새로고침하거나 새로 들어오면 다시 false가 된다
// 사이트 안에서 다른 화면(로그인·회원가입 등)에 갔다가 뒤로 가기로 돌아왔을 때는 소개를 다시 틀지 않는다
let hasPlayedIntro = false

function MainPage() {
  const user = useAuthStore((state) => state.user)
  // 로그인하지 않은 사용자는 접속할 때마다 소개 화면부터 본다. 로그인한 사용자는 바로 본문을 본다
  const [shouldPlayIntro] = useState(() => !user && !hasPlayedIntro)
  const [isIntroOpen, setIsIntroOpen] = useState(shouldPlayIntro)
  const closeIntro = useCallback(() => {
    hasPlayedIntro = true
    setIsIntroOpen(false)
  }, [])

  // 소개 화면이 덮고 있는 동안에는 뒤의 본문이 스크롤되지 않게 막고, 끝나면 맨 위(온보딩)부터 보여준다
  // 새로고침하면 브라우저가 예전 스크롤 위치를 되살리는데(scrollRestoration: auto), 소개 중에는 이를 꺼 둔다
  useEffect(() => {
    if (!isIntroOpen) return
    const previousRestoration = window.history.scrollRestoration
    window.history.scrollRestoration = 'manual'
    window.scrollTo(0, 0)
    document.documentElement.style.overflow = 'hidden'
    return () => {
      document.documentElement.style.overflow = ''
      window.scrollTo(0, 0)
      window.history.scrollRestoration = previousRestoration
    }
  }, [isIntroOpen])

  return (
    <>
      {isIntroOpen && <IntroSection onFinish={closeIntro} />}
      <Header />
      <main>
        {/* 로그인하지 않은 사용자: 역할별 이용 방법 → 회원가입 안내. 소개 화면이 걷히면 차례로 나타난다 */}
        {!user && <OnboardingSection animate={shouldPlayIntro} isPlaying={!isIntroOpen} />}
        {user && <RoleSummarySection user={user} />}
        <SearchSection />
        <ListingSection />
        <ValueSection />
      </main>
      <Footer />
      {/* 챗봇은 사용자(임대인·임차인)·관리자만 쓴다 (CHATBOT_001) */}
      {user && user.role !== ROLES.AGENT && <ChatbotLauncher role={user.role} />}
    </>
  )
}

export default MainPage
