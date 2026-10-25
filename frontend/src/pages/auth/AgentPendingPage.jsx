// [페이지] 중개사 승인 대기 — /agent/pending
// 누가: 중개사 중 아직 관리자 승인을 받지 못한 사용자. 승인 전에 중개 화면에 들어오면 RequireRole이 이 화면을 대신 보여준다
// 요구사항: USER_021 (가입 후 2~3일 내 서류 제출, 관리자 승인 후 이용), NOTIFY_024·025 (기한 임박, 승인·반려 알림)
import { Link } from 'react-router'
import StatusBadge from '../../components/common/StatusBadge.jsx'

// TODO: 진행 상태는 로그인 응답(BE-001)이나 중개사 정보 API에서 받아야 한다. 해당 필드가 명세에 없어 백엔드와 협의 필요
// - 가입일, 서류 제출 여부·마감일, 승인 상태(대기/승인/반려)와 반려 사유
const SAMPLE_STEPS = [
  { label: '가입', detail: '2026.10.21 완료', badge: <StatusBadge tone="green">완료</StatusBadge> },
  { label: '인증 서류 제출', detail: '마감 2026.10.24 · 2일 남음', badge: <StatusBadge tone="amber">제출 전</StatusBadge> },
  { label: '관리자 승인', detail: '서류 제출 후 진행돼요', badge: <span className="text-sm text-gray-500">대기</span> },
]

function AgentPendingPage() {
  return (
    <div className="page-container items-center text-center">
      <div className="flex flex-col gap-2 pt-8">
        <h1 className="text-3xl font-extrabold tracking-tight">가입 승인을 기다리고 있어요</h1>
        <p className="text-gray-600">관리자가 서류를 확인하면 알림으로 알려드려요. 승인 전에는 중개 기능을 쓸 수 없어요.</p>
      </div>
      <ol className="card w-full max-w-xl py-2 text-left">
        {SAMPLE_STEPS.map((step, index) => (
          <li key={step.label} className={`flex items-center justify-between gap-4 py-3.5 ${index > 0 ? 'border-t border-gray-100' : ''}`}>
            <div className="flex flex-col">
              <span className="font-bold">{step.label}</span>
              <span className="text-sm text-gray-600">{step.detail}</span>
            </div>
            {step.badge}
          </li>
        ))}
      </ol>
      <Link to="/agent/documents" className="btn-primary">서류 제출하러 가기</Link>
    </div>
  )
}

export default AgentPendingPage
