// [페이지] 관리자 로그인 — /admin/login
// 누가: 관리자. 회원가입·소셜 로그인 없이 운영팀이 발급한 계정으로만 로그인한다
// 요구사항: USER_001 (관리자 로그인)
// API: POST /api/v1/auth/login (BE-001) — 일반 로그인과 같은 API. 응답의 role이 ADMIN이 아니면 막는다
import { useNavigate } from 'react-router'
import Logo from '../../components/common/Logo.jsx'
import { Field, TextInput } from '../../components/common/FormControls.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { DEV_USERS } from '../../constants/devUsers.js'

function AdminLoginPage() {
  const login = useAuthStore((state) => state.login)
  const navigate = useNavigate()

  // TODO: POST /api/v1/auth/login (BE-001) 연결
  // - 요청: { loginId, password } / 응답: { userId, role, loginType }
  // - role이 ADMIN이면 login() 후 /admin 으로 이동, 아니면 "관리자 계정이 아니에요" 안내
  const handleSubmit = (event) => {
    event.preventDefault()
  }

  // [개발용] API 연결 전 관리자 화면 확인용. 로그인 API가 연결되면 지운다
  const handleDevLogin = () => {
    login(DEV_USERS.find((devUser) => devUser.user.role === 'ADMIN').user)
    navigate('/admin')
  }

  return (
    <section className="flex min-h-svh flex-col items-center justify-center gap-6 bg-night px-6">
      <Logo dark />
      <form onSubmit={handleSubmit} className="card flex w-full max-w-sm flex-col gap-4 p-8">
        <h1 className="text-[22px] font-extrabold">관리자 로그인</h1>
        <Field label="관리자 아이디" htmlFor="admin-login-id">
          <TextInput id="admin-login-id" autoComplete="username" />
        </Field>
        <Field label="비밀번호" htmlFor="admin-login-password">
          <TextInput id="admin-login-password" type="password" autoComplete="current-password" />
        </Field>
        <button type="submit" className="btn-primary h-12">로그인</button>
        <p className="text-sm text-gray-500">관리자 계정은 회원가입이 없어요. 운영팀에서 발급해요.</p>
        {import.meta.env.DEV && (
          <button type="button" onClick={handleDevLogin} className="rounded-xl border-2 border-dashed border-amber-400 bg-amber-50 py-2.5 text-sm font-bold text-amber-900">
            개발용: 관리자로 바로 로그인
          </button>
        )}
      </form>
    </section>
  )
}

export default AdminLoginPage
