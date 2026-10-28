// [페이지] 중개사 마이페이지 — /agent/mypage
// 요구사항: USER_020 (중개사무소 주소, 전화번호, 비밀번호 수정)
// API: PUT/PATCH /api/v1/agents/me (BE-016)
// 회원탈퇴: 사용자 요청으로 추가했지만 요구사항 시트에 중개사 탈퇴가 없다 (USER_012는 임대·임차인만).
//          탈퇴 API와 진행 중인 중개가 있을 때의 처리 정책을 팀과 정해야 한다
import { useState } from 'react'
import { useNavigate } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import ConfirmModal from '../../components/common/ConfirmModal.jsx'
import { Field, TextInput } from '../../components/common/FormControls.jsx'
import { logout } from '../../utils/jwtUtil.js'

// TODO: 중개사 정보 조회 API가 명세에 없어 백엔드와 협의 필요 (사무소명, 이메일, 주소, 전화번호, 승인 상태)
const SAMPLE_AGENT = {
  officeName: '보금공인중개사',
  email: 'agent@bogeum.com',
  officeAddress: '서울 강남구 테헤란로 1',
  phone: '02-123-4567',
}

function AgentMyPage() {
  const navigate = useNavigate()
  const [isWithdrawOpen, setIsWithdrawOpen] = useState(false)

  // TODO: PUT/PATCH /api/v1/agents/me (BE-016)
  const handleSave = (event) => {
    event.preventDefault()
  }

  // TODO: 중개사 탈퇴 API가 명세에 없다. 생기면 호출 후 로그아웃
  const handleWithdraw = async () => {
    try {
      await logout()
    } catch {
      window.alert('서버 로그아웃을 완료하지 못했습니다. 다시 로그인한 후 로그아웃해 주세요.')
      return
    }
    navigate('/')
  }

  return (
    <div className="page-container">
      <PageHeader title="마이페이지" />
      <div className="grid items-start gap-5 lg:grid-cols-2">
        <section aria-labelledby="agent-info-title" className="card flex flex-col gap-3">
          <h2 id="agent-info-title" className="text-lg font-bold">중개사 정보</h2>
          <dl className="flex flex-col gap-2.5">
            <div className="flex justify-between gap-4">
              <dt className="text-gray-600">중개사무소</dt>
              <dd className="font-bold">{SAMPLE_AGENT.officeName}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-gray-600">이메일</dt>
              <dd className="font-bold">{SAMPLE_AGENT.email}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-gray-600">가입 상태</dt>
              <dd><StatusBadge tone="green">승인 완료</StatusBadge></dd>
            </div>
          </dl>
        </section>
        <div className="flex flex-col gap-3">
          <form onSubmit={handleSave} className="card flex flex-col gap-4">
            <h2 className="text-lg font-bold">정보 수정</h2>
            <Field label="중개사무소 주소" htmlFor="agent-address">
              <TextInput id="agent-address" defaultValue={SAMPLE_AGENT.officeAddress} />
            </Field>
            <Field label="전화번호" htmlFor="agent-phone">
              <TextInput id="agent-phone" type="tel" defaultValue={SAMPLE_AGENT.phone} />
            </Field>
            <Field label="새 비밀번호" htmlFor="agent-password" hint="바꾸지 않으려면 비워 두세요.">
              <TextInput id="agent-password" type="password" autoComplete="new-password" />
            </Field>
            <button type="submit" className="btn-primary">저장</button>
          </form>
          <button type="button" onClick={() => setIsWithdrawOpen(true)} className="self-end text-sm text-gray-600 underline hover:text-gray-900">
            회원 탈퇴
          </button>
        </div>
      </div>

      {isWithdrawOpen && (
        <ConfirmModal
          title="정말 탈퇴하시겠습니까?"
          description="진행 중인 중개와 중개 이력을 다시 볼 수 없어요."
          confirmLabel="탈퇴하기"
          onConfirm={handleWithdraw}
          onClose={() => setIsWithdrawOpen(false)}
        >
          <Field label="확인을 위해 비밀번호 입력" htmlFor="agent-withdraw-password">
            <TextInput id="agent-withdraw-password" type="password" autoComplete="current-password" />
          </Field>
        </ConfirmModal>
      )}
    </div>
  )
}

export default AgentMyPage
