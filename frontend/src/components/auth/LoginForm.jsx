// USER_001, USER_005, USER_018 (아이디·비밀번호 로그인), USER_007 (최근 로그인 수단 안내), USER_008 (소셜 로그인)
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import clsx from 'clsx'
import { LAST_LOGIN_TYPE_KEY, LOGIN_TYPES, LOGIN_TYPE_LABELS } from '../../constants/auth.js'

const loginSchema = z.object({
  loginId: z.string().trim().min(1, '아이디를 입력해 주세요.'),
  password: z.string().min(1, '비밀번호를 입력해 주세요.'),
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
  const [lastLoginType] = useState(readLastLoginType)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(loginSchema) })

  // TODO: POST /api/v1/auth/login (BE-001) 연결
  // - 요청: { loginId, password } / 응답: { userId, role, loginType }
  // - 성공 시: authStore의 login({ userId, role }) 호출 → localStorage에 LAST_LOGIN_TYPE_KEY로 loginType 저장(USER_007) → '/'로 이동
  // - 인증 방식(세션/토큰)이 미정이라 토큰 저장 여부는 확정 후 반영
  const onSubmit = async () => {}

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
          <label htmlFor="login-id" className="text-sm font-medium text-gray-700">
            아이디
          </label>
          <input
            id="login-id"
            type="text"
            autoComplete="username"
            aria-invalid={Boolean(errors.loginId)}
            aria-describedby={errors.loginId ? 'login-id-error' : undefined}
            className={clsx(
              'h-12 rounded-xl border px-4 text-base',
              errors.loginId ? 'border-red-500' : 'border-gray-300',
            )}
            {...register('loginId')}
          />
          {errors.loginId && (
            <p id="login-id-error" className="text-sm text-red-600">{errors.loginId.message}</p>
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
        <button
          type="submit"
          disabled={isSubmitting}
          className="mt-2 flex h-12 items-center justify-center gap-2 rounded-xl bg-blue-600 text-base font-bold text-white hover:bg-blue-700 disabled:opacity-60"
        >
          로그인
          {lastLoginType === LOGIN_TYPES.NORMAL && <RecentBadge />}
        </button>
      </form>

      <div className="flex flex-col gap-3">
        <div className="flex items-center gap-3 text-sm text-gray-500">
          <span className="h-px grow bg-gray-200" />
          간편 로그인 (임대인·임차인)
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

      {/* 회원가입 화면이 아직 없어서 링크는 비워 둔다 (USER_003, USER_016) */}
      <div className="flex flex-wrap justify-center gap-x-6 gap-y-2 text-sm text-gray-600">
        <a href="#" className="hover:text-gray-900">회원가입</a>
        <a href="#" className="hover:text-gray-900">중개사 회원가입</a>
      </div>
    </div>
  )
}

export default LoginForm
