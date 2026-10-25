// [개발용 컴포넌트] 역할별 테스트 로그인 — 로그인 화면 아래에 붙는다
// 백엔드 로그인(BE-001) 없이 샘플 사용자로 로그인해 역할별 화면을 클릭해 볼 수 있다
// LoginForm에서 import.meta.env.DEV 일 때만 그리므로 npm run build 결과물에는 나오지 않는다
// 로그인 API가 연결되면 이 파일과 constants/devUsers.js 를 지운다
import { useNavigate } from 'react-router'
import { useAuthStore } from '../../stores/authStore.js'
import { DEV_USERS } from '../../constants/devUsers.js'

// 받침이 있으면 '으로', 없거나 ㄹ 받침이면 '로' (예: 임차인으로, 중개사로)
function withRo(label) {
  const lastHangul = label.match(/[가-힣]/g).at(-1)
  const finalConsonant = (lastHangul.charCodeAt(0) - 0xac00) % 28
  return label + (finalConsonant === 0 || finalConsonant === 8 ? '로' : '으로')
}

function DevLoginPanel() {
  const login = useAuthStore((state) => state.login)
  const navigate = useNavigate()

  const handleDevLogin = ({ user, homePath }) => {
    login(user)
    navigate(homePath)
  }

  return (
    <section aria-label="개발용 테스트 로그인" className="flex flex-col gap-3 rounded-2xl border-2 border-dashed border-amber-400 bg-amber-50 p-4">
      <div className="flex items-center justify-between gap-2">
        <p className="font-bold text-amber-900">개발용 테스트 로그인</p>
        <span className="rounded-full bg-amber-200 px-2 py-0.5 text-xs font-bold text-amber-900">개발 모드에서만 보임</span>
      </div>
      <div className="grid grid-cols-2 gap-2">
        {DEV_USERS.map((devUser) => (
          <button key={devUser.label} type="button" onClick={() => handleDevLogin(devUser)} className="btn-secondary btn-sm">
            {withRo(devUser.label)} 로그인
          </button>
        ))}
      </div>
      <p className="text-xs text-amber-900">새로고침하면 로그아웃돼요. 화면 이동은 메뉴를 눌러서 해 주세요.</p>
    </section>
  )
}

export default DevLoginPanel
