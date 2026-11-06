import { Link, useLocation, useNavigate } from 'react-router'
import AuthLayout from '../../components/auth/AuthLayout.jsx'
import UserSignupForm from '../../components/auth/UserSignupForm.jsx'
import SocialSignupForm from '../../components/auth/SocialSignupForm.jsx'
import { LAST_LOGIN_TYPE_KEY, LOGIN_TYPES, LOGIN_TYPE_LABELS } from '../../constants/auth.js'
import { signupNaver, signupKakao } from '../../utils/jwtUtil.js'

function SignupPage() {
  const navigate = useNavigate()
  const { state } = useLocation()
  const signupProfile = state?.socialSignup
  const signupSocial = signupProfile?.provider === LOGIN_TYPES.NAVER ? signupNaver
    : signupProfile?.provider === LOGIN_TYPES.KAKAO ? signupKakao : null

  const handleSocialSignup = async (values) => {
    const user = await signupSocial(values)
    try {
      localStorage.setItem(LAST_LOGIN_TYPE_KEY, user.lastLoginProvider)
    } catch {
      console.warn('최근 로그인 수단을 저장하지 못했습니다.')
    }
    navigate('/', { replace: true })
  }

  return (
    <AuthLayout title="회원가입">
      {signupSocial ? (
        <SocialSignupForm providerLabel={LOGIN_TYPE_LABELS[signupProfile.provider]} profile={signupProfile}
          onSubmit={handleSocialSignup} onCancel={() => navigate('/login', { replace: true })} />
      ) : <UserSignupForm />}
      {!signupSocial && <div className="flex flex-col items-center gap-2 text-sm text-gray-600">
        <p>
          중개사이신가요? <Link to="/signup/agent" className="font-bold text-blue-700 hover:text-blue-900">중개사 회원가입</Link>
        </p>
        <p>
          이미 계정이 있나요? <Link to="/login" className="font-bold text-blue-700 hover:text-blue-900">로그인</Link>
        </p>
      </div>}
    </AuthLayout>
  )
}

export default SignupPage
