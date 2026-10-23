import { useEffect, useState } from 'react'
import Header from '../../components/common/Header.jsx'
import Footer from '../../components/common/Footer.jsx'
import IntroSection from '../../components/main/IntroSection.jsx'
import SearchSection from '../../components/main/SearchSection.jsx'
import ListingSection from '../../components/main/ListingSection.jsx'
import ValueSection from '../../components/main/ValueSection.jsx'
import RoleSummarySection from '../../components/main/RoleSummarySection.jsx'
import ChatbotLauncher from '../../components/chatbot/ChatbotLauncher.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { ROLES } from '../../constants/auth.js'

const INTRO_SEEN_KEY = 'bogeum.introSeen'
const MAIN_SECTION_ID = 'main-content'

function readIntroSeen() {
  try {
    return localStorage.getItem(INTRO_SEEN_KEY) === 'true'
  } catch {
    return false // 저장소를 못 쓰는 환경(사생활 보호 모드 등)에서는 첫 방문으로 본다
  }
}

function MainPage() {
  const user = useAuthStore((state) => state.user)
  // 첫 방문에만 애니메이션을 재생하고, 이후에는 완성된 화면을 바로 보여준다
  const [isFirstVisit] = useState(() => !readIntroSeen())

  useEffect(() => {
    try {
      localStorage.setItem(INTRO_SEEN_KEY, 'true')
    } catch {
      // 저장하지 못하면 다음 방문에도 애니메이션이 재생될 뿐이라 무시한다
    }
  }, [])

  return (
    <>
      {/* 로그인한 사용자는 소개 없이 바로 본문을 본다 */}
      {!user && <IntroSection animate={isFirstVisit} nextSectionId={MAIN_SECTION_ID} />}
      <Header id={MAIN_SECTION_ID} />
      <main>
        {user && <RoleSummarySection user={user} />}
        <SearchSection />
        <ListingSection />
        <ValueSection />
      </main>
      <Footer />
      {/* 챗봇은 임대인·임차인·관리자만 쓴다 (CHATBOT_001) */}
      {user && user.role !== ROLES.AGENT && <ChatbotLauncher role={user.role} />}
    </>
  )
}

export default MainPage
