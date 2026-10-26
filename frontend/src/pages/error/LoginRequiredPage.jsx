// [페이지] 로그인 필요 · 세션 만료 — 주소가 따로 없고, RequireRole이 로그인 안 된 사용자에게 대신 보여준다
// 요구사항: USER_006 (세션 만료 시 자동 로그아웃), USER_007 (최근 로그인 수단 안내)
// - 최근 로그인 수단은 로그인 화면과 같은 localStorage 값(LAST_LOGIN_TYPE_KEY)을 읽는다
// - TODO: 세션 만료 감지(BE-003)는 인증 방식이 정해지면 API 응답 401 처리에서 logout() 후 이 화면으로 보낸다
import { useState } from 'react'
import { Link } from 'react-router'
import GuideScreen from '../../components/common/GuideScreen.jsx'
import { LAST_LOGIN_TYPE_KEY, LOGIN_TYPE_LABELS } from '../../constants/auth.js'

function readLastLoginType() {
  try {
    return localStorage.getItem(LAST_LOGIN_TYPE_KEY)
  } catch {
    return null
  }
}

function LoginRequiredPage() {
  const [lastLoginType] = useState(readLastLoginType)
  const lastLoginLabel = LOGIN_TYPE_LABELS[lastLoginType]

  return (
    <GuideScreen
      title="로그인하고 이용해 주세요"
      description="보금은 회원가입 후 이용할 수 있어요. 오래 쉬셨다면 다시 로그인해 주세요."
      litRatio={0.15}
      extra={
        lastLoginLabel && (
          <p className="rounded-full bg-blue-950 px-4 py-1.5 text-sm text-blue-200">최근에 {lastLoginLabel}(으)로 로그인했어요</p>
        )
      }
    >
      <Link to="/login" className="btn-primary">로그인</Link>
      <Link to="/signup" className="btn-secondary">회원가입</Link>
    </GuideScreen>
  )
}

export default LoginRequiredPage
