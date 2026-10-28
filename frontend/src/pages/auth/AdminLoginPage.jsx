// [페이지] 관리자 로그인 — /admin/login
// 누가: 관리자. 회원가입·소셜 로그인 없이 운영팀이 발급한 계정으로만 로그인한다
// 요구사항: USER_001 (관리자 로그인)
// API: POST /api/v1/auth/login (BE-001) — 일반 로그인과 같은 API. 응답의 role이 ADMIN이 아니면 막는다
import { useNavigate } from 'react-router'
import Logo from '../../components/common/Logo.jsx'
import { Field, TextInput } from '../../components/common/FormControls.jsx'
import { login, logout } from '../../utils/jwtUtil.js'
import { useState } from 'react'

function AdminLoginPage() {
  const navigate = useNavigate()

  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const handleSubmit = async (event) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setError('')
    setPending(true)
    try {
      const user = await login(form.get('email'), form.get('password'))
      if (user.role !== 'ADMIN') {
        await logout()
        setError('관리자 계정이 아닙니다.')
        return
      }
      navigate('/admin')
    } catch (failure) {
      setError(failure.response?.data?.message || '인증 요청을 완료하지 못했습니다.')
    } finally {
      setPending(false)
    }
  }

  return (
    <section className="flex min-h-svh flex-col items-center justify-center gap-6 bg-night px-6">
      <Logo dark />
      <form onSubmit={handleSubmit} className="card flex w-full max-w-sm flex-col gap-4 p-8">
        <h1 className="text-[22px] font-extrabold">관리자 로그인</h1>
        <Field label="관리자 아이디" htmlFor="admin-login-id">
          <TextInput id="admin-login-id" name="email" type="email" required autoComplete="username" />
        </Field>
        <Field label="비밀번호" htmlFor="admin-login-password">
          <TextInput id="admin-login-password" name="password" required type="password" autoComplete="current-password" />
        </Field>
        {error && <p role="alert">{error}</p>}
        <button disabled={pending} type="submit" className="btn-primary h-12">로그인</button>
        <p className="text-sm text-gray-500">관리자 계정은 회원가입이 없어요. 운영팀에서 발급해요.</p>

      </form>
    </section>
  )
}

export default AdminLoginPage
