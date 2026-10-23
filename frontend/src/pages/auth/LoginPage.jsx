import AuthLayout from '../../components/auth/AuthLayout.jsx'
import LoginForm from '../../components/auth/LoginForm.jsx'

function LoginPage() {
  return (
    <AuthLayout title="로그인">
      <LoginForm />
    </AuthLayout>
  )
}

export default LoginPage
