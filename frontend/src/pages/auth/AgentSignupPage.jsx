import { Link } from 'react-router'
import AuthLayout from '../../components/auth/AuthLayout.jsx'
import AgentSignupForm from '../../components/auth/AgentSignupForm.jsx'

function AgentSignupPage() {
  return (
    <AuthLayout title="중개사 회원가입">
      <AgentSignupForm />
      <div className="flex flex-col items-center gap-2 text-sm text-gray-600">
        <p>
          임대인·임차인이신가요? <Link to="/signup" className="font-bold text-blue-700 hover:text-blue-900">회원가입</Link>
        </p>
        <p>
          이미 계정이 있나요? <Link to="/login" className="font-bold text-blue-700 hover:text-blue-900">로그인</Link>
        </p>
      </div>
    </AuthLayout>
  )
}

export default AgentSignupPage
