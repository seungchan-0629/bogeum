// USER_001, USER_005, USER_018 (아이디·비밀번호 로그인), USER_007 (최근 로그인 수단 안내), USER_008 (소셜 로그인)
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import clsx from 'clsx'
import { LAST_LOGIN_TYPE_KEY, LOGIN_TYPES, LOGIN_TYPE_LABELS } from '../../constants/auth.js'
import { login, loginNaver, loginKakao, publicApi } from '../../utils/jwtUtil.js'
import DevLoginPanel from './DevLoginPanel.jsx'

const loginSchema = z.object({
  email: z.string().trim().min(1, '이메일을 입력해 주세요.').pipe(z.email({ error: '이메일 형식으로 입력해 주세요.' })),
  password: z.string().refine((password) => password.trim().length > 0, '비밀번호를 입력해 주세요.'),
})

const SOCIAL_BUTTONS = [
  { type: LOGIN_TYPES.NAVER, className: 'bg-[#03c75a] text-white hover:bg-[#02b350]' },
  { type: LOGIN_TYPES.KAKAO, className: 'bg-[#fee500] text-[#191919] hover:bg-[#f5dc00]' },
  { type: LOGIN_TYPES.GOOGLE, className: 'border border-gray-300 bg-white text-gray-900 hover:bg-gray-50' },
]

const SOCIAL_AUTH = {
  [LOGIN_TYPES.NAVER]: { slug: 'naver', login: loginNaver },
  [LOGIN_TYPES.KAKAO]: { slug: 'kakao', login: loginKakao },
}

function readLastLoginType() {
  try {
    return localStorage.getItem(LAST_LOGIN_TYPE_KEY)
  } catch {
    return null
  }
}

function RecentBadge() {
  return (
    <span className="rounded-full bg-blue-600 px-2 py-0.5 text-xs font-bold text-white">최근 사용</span>
  )
}

function LoginForm() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const callbackProvider = Object.keys(SOCIAL_AUTH).find((type) => searchParams.has(SOCIAL_AUTH[type].slug))
  const [lastLoginType] = useState(readLastLoginType)
  const [pendingProvider, setPendingProvider] = useState(
    callbackProvider && searchParams.get(SOCIAL_AUTH[callbackProvider].slug) === 'complete' ? callbackProvider : null,
  )
  const socialHandled = useRef(false)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(loginSchema) })

  const finishLogin = useCallback((user) => {
    try {
      localStorage.setItem(LAST_LOGIN_TYPE_KEY, user.lastLoginProvider)
    } catch {
      // 최근 사용 안내를 저장하지 못해도 인증 성공 흐름은 계속 진행한다.
      console.warn('최근 로그인 수단을 저장하지 못했습니다.')
    }
    navigate(user.role === 'AGENT'
      ? (user.approvalStatus === 'APPROVED' ? '/agent/requests' : '/agent/pending')
      : '/')
  }, [navigate])

  useEffect(() => {
    if (!callbackProvider || socialHandled.current) return
    const provider = SOCIAL_AUTH[callbackProvider]
    const label = LOGIN_TYPE_LABELS[callbackProvider]
    const result = searchParams.get(provider.slug)
    // StrictMode에서도 일회용 인증 결과를 중복 소비하지 않는다.
    socialHandled.current = true
    if (result !== 'complete') {
      setError('root', { message: `${label} 인증이 취소되었거나 만료되었습니다. 다시 시도해 주세요.` })
      return
    }
    provider.login()
      .then((data) => {
        if (data.isNewUser) {
          navigate('/signup', {
            replace: true,
            state: { socialSignup: { provider: callbackProvider, name: data.name, email: data.email } },
          })
        } else {
          finishLogin(data.authentication.user)
        }
      })
      .catch((error) => setError('root', { message: error.response?.data?.message || `${label} 로그인을 완료하지 못했습니다.` }))
      .finally(() => setPendingProvider(null))
  }, [callbackProvider, searchParams, setError, finishLogin, navigate])

  // 검증된 입력을 실제 API로 보낸다. 비밀번호는 콘솔이나 브라우저 저장소에 기록하지 않는다.
  const onSubmit = async ({ email, password }) => {
    try {
      finishLogin(await login(email, password))
    } catch (error) {
      setError('root', { message: error.response?.data?.message || '서버에 연결할 수 없습니다.' })
    }
  }

  const handleSocialLogin = async (type) => {
    const provider = SOCIAL_AUTH[type]
    if (!provider || pendingProvider) return
    setPendingProvider(type)
    try {
      const { data } = await publicApi.post(`/auth/social/${provider.slug}/authorize`)
      window.location.assign(data.authorizationUrl)
    } catch (error) {
      setError('root', { message: error.response?.data?.message || `${LOGIN_TYPE_LABELS[type]} 로그인 화면을 열지 못했습니다.` })
      setPendingProvider(null)
    }
  }

  return (
    <div className="flex flex-col gap-8">
      {lastLoginType && LOGIN_TYPE_LABELS[lastLoginType] && (
        <p className="rounded-xl bg-blue-50 px-4 py-3 text-sm text-blue-900">
          지난번에 <strong>{LOGIN_TYPE_LABELS[lastLoginType]}</strong>(으)로 로그인했어요.
        </p>
      )}

      <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="login-email" className="text-sm font-medium text-gray-700">
            아이디(이메일)
          </label>
          <input
            id="login-email"
            type="email"
            autoComplete="username"
            aria-invalid={Boolean(errors.email)}
            aria-describedby={errors.email ? 'login-email-error' : undefined}
            className={clsx(
              'h-12 rounded-xl border px-4 text-base',
              errors.email ? 'border-red-500' : 'border-gray-300',
            )}
            {...register('email')}
          />
          {errors.email && (
            <p id="login-email-error" className="text-sm text-red-600">{errors.email.message}</p>
          )}
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="login-password" className="text-sm font-medium text-gray-700">
            비밀번호
          </label>
          <input
            id="login-password"
            type="password"
            autoComplete="current-password"
            aria-invalid={Boolean(errors.password)}
            aria-describedby={errors.password ? 'login-password-error' : undefined}
            className={clsx(
              'h-12 rounded-xl border px-4 text-base',
              errors.password ? 'border-red-500' : 'border-gray-300',
            )}
            {...register('password')}
          />
          {errors.password && (
            <p id="login-password-error" className="text-sm text-red-600">{errors.password.message}</p>
          )}
        </div>
        {errors.root && <p role="alert" className="text-sm text-red-600">{errors.root.message}</p>}
        <button
          type="submit"
          disabled={isSubmitting || Boolean(pendingProvider)}
          className="mt-2 flex h-12 items-center justify-center gap-2 rounded-xl bg-blue-600 text-base font-bold text-white hover:bg-blue-700 disabled:opacity-60"
        >
          로그인
          {lastLoginType === LOGIN_TYPES.LOCAL && <RecentBadge />}
        </button>
      </form>

      <div className="flex flex-col gap-3">
        <div className="flex items-center gap-3 text-sm text-gray-500">
          <span className="h-px grow bg-gray-200" />
          간편 로그인 (사용자)
          <span className="h-px grow bg-gray-200" />
        </div>
        {SOCIAL_BUTTONS.map(({ type, className }) => (
          <button
            key={type}
            type="button"
            onClick={() => handleSocialLogin(type)}
            disabled={!SOCIAL_AUTH[type] || Boolean(pendingProvider) || isSubmitting}
            className={clsx('flex h-12 items-center justify-center gap-2 rounded-xl text-[15px] font-bold disabled:opacity-60', className)}
          >
            {LOGIN_TYPE_LABELS[type]}로 로그인{!SOCIAL_AUTH[type] && ' (준비 중)'}
            {lastLoginType === type && <RecentBadge />}
          </button>
        ))}
        {pendingProvider && <p role="status" className="text-sm text-gray-600">{LOGIN_TYPE_LABELS[pendingProvider]} 로그인을 진행하고 있어요.</p>}
      </div>

      <div className="flex flex-wrap justify-center gap-x-6 gap-y-2 text-sm text-gray-600">
        <Link to="/signup" className="hover:text-gray-900">회원가입</Link>
        <Link to="/signup/agent" className="hover:text-gray-900">중개사 회원가입</Link>
      </div>

      {/* 개발 모드(npm run dev)에서만 보이는 역할별 테스트 로그인 */}
      {import.meta.env.DEV && <DevLoginPanel />}
    </div>
  )
}

export default LoginForm
