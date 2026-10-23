import { Link } from 'react-router'
import AuthLayout from '../../components/auth/AuthLayout.jsx'
import UserSignupForm from '../../components/auth/UserSignupForm.jsx'

function SignupPage() {
  return (
    <AuthLayout title="회원가입">
      <UserSignupForm />
      <div className="flex flex-col items-center gap-2 text-sm text-gray-600">
        <p>
          중개사이신가요? <Link to="/signup/agent" className="font-bold text-blue-700 hover:text-blue-900">중개사 회원가입</Link>
        </p>
        <p>
          이미 계정이 있나요? <Link to="/login" className="font-bold text-blue-700 hover:text-blue-900">로그인</Link>
        </p>
      </div>
    </AuthLayout>
  )
}

export default SignupPage
