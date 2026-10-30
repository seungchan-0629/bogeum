// USER_001, USER_005, USER_018 (아이디·비밀번호 로그인), USER_007 (최근 로그인 수단 안내), USER_008 (소셜 로그인)
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import clsx from 'clsx'
import { LAST_LOGIN_TYPE_KEY, LOGIN_TYPES, LOGIN_TYPE_LABELS } from '../../constants/auth.js'
import { login } from '../../utils/jwtUtil.js'

const loginSchema = z.object({
  email: z.string().trim().min(1, '이메일을 입력해 주세요.').pipe(z.email({ error: '이메일 형식으로 입력해 주세요.' })),
  password: z.string().refine((password) => password.trim().length > 0, '비밀번호를 입력해 주세요.'),
})

const SOCIAL_BUTTONS = [
  { type: LOGIN_TYPES.NAVER, className: 'bg-[#03c75a] text-white hover:bg-[#02b350]' },
  { type: LOGIN_TYPES.KAKAO, className: 'bg-[#fee500] text-[#191919] hover:bg-[#f5dc00]' },
  { type: LOGIN_TYPES.GOOGLE, className: 'border border-gray-300 bg-white text-gray-900 hover:bg-gray-50' },
]

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
  const [lastLoginType] = useState(readLastLoginType)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(loginSchema) })

  // 검증된 입력을 실제 API로 보낸다. 비밀번호는 콘솔이나 브라우저 저장소에 기록하지 않는다.
  const onSubmit = async ({ email, password }) => {
    let user
    try {
      user = await login(email, password)
    } catch (error) {
      setError('root', { message: error.response?.data?.message || '서버에 연결할 수 없습니다.' })
      return
    }

    try {
      localStorage.setItem(LAST_LOGIN_TYPE_KEY, user.lastLoginProvider)
    } catch {
      // 최근 사용 안내를 저장하지 못해도 인증 성공 흐름은 계속 진행한다.
      console.warn('최근 로그인 수단을 저장하지 못했습니다.')
    }
    navigate(user.role === 'AGENT'
      ? (user.approvalStatus === 'APPROVED' ? '/agent/requests' : '/agent/pending')
      : '/')
  }

  // TODO: 소셜 로그인 (BE-005) 연결
  // - 각 제공자의 로그인 창으로 이동 → 돌아온 authorizationCode로 POST /api/v1/auth/social/{provider} 호출
  // - 응답의 isNewUser가 true면 동의 항목 화면으로 이동 (USER_004, USER_017)
  const handleSocialLogin = () => {}

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
          disabled={isSubmitting}
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
            className={clsx('flex h-12 items-center justify-center gap-2 rounded-xl text-[15px] font-bold', className)}
          >
            {LOGIN_TYPE_LABELS[type]}로 로그인
            {lastLoginType === type && <RecentBadge />}
          </button>
        ))}
      </div>

      <div className="flex flex-wrap justify-center gap-x-6 gap-y-2 text-sm text-gray-600">
        <Link to="/signup" className="hover:text-gray-900">회원가입</Link>
        <Link to="/signup/agent" className="hover:text-gray-900">중개사 회원가입</Link>
      </div>

    </div>
  )
}

export default LoginForm
