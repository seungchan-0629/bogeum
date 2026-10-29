import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../../utils/jwtUtil.js'
import { useAuthStore } from '../../stores/authStore.js'
import { Link } from 'react-router'
import { useAgentProfile } from '../../hooks/useAgentProfile.js'

const TITLES = {
  PENDING: '가입 승인을 기다리고 있어요',
  APPROVED: '중개사 승인이 완료됐어요',
  REJECTED: '가입 신청이 반려되었어요',
}

function AgentPendingPage() {
  const { data: agent, isPending, isFetching, error, refetch } = useAgentProfile()
  const queryClient = useQueryClient()
  const [isApproving, setIsApproving] = useState(false)
  const [approvalError, setApprovalError] = useState('')

  const handleDemoApproval = async () => {
    if (isApproving) return
    setIsApproving(true)
    setApprovalError('')
    try {
      await queryClient.cancelQueries({ queryKey: ['agent-profile', agent.userId] })
      const { data } = await api.post('/v1/agents/me/demo-approval')
      const current = useAuthStore.getState().user
      if (current?.userId === data.userId) {
        queryClient.setQueryData(['agent-profile', data.userId], data)
        useAuthStore.getState().login({ ...current, agentId: data.agentId, approvalStatus: data.approvalStatus })
      }
    } catch (error) {
      setApprovalError(error.response?.data?.message || '승인 처리에 실패했습니다. 다시 시도해 주세요.')
    } finally {
      setIsApproving(false)
    }
  }

  return (
    <div className="page-container items-center text-center">
      <h1 className="text-3xl font-extrabold tracking-tight">{TITLES[agent?.approvalStatus] || '중개사 가입 상태'}</h1>
      {isPending && <p role="status">가입 상태를 확인하고 있어요.</p>}
      {error && <p role="alert" className="text-red-600">{error.response?.data?.message || '가입 상태를 불러오지 못했습니다.'}</p>}
      {agent && (
        <div className="card flex w-full max-w-xl flex-col gap-3 text-left">
          <p><strong>중개사무소:</strong> {agent.officeName}</p>
          <p><strong>가입일:</strong> {agent.createdAt?.slice(0, 10)}</p>
          <p><strong>서류 제출 기한:</strong> {agent.documentDueAt?.replace('T', ' ').slice(0, 16)}</p>
          {agent.approvalStatus === 'PENDING' && <p>관리자 승인 후 중개 기능을 이용할 수 있습니다.</p>}
          {agent.approvalStatus === 'REJECTED' && <p><strong>반려 사유:</strong> {agent.rejectReason || '관리자에게 문의해 주세요.'}</p>}
          {agent.approvalStatus === 'APPROVED' && <Link to="/agent/requests" className="btn-primary">중개 업무로 이동</Link>}
        </div>
      )}
      {agent?.demoApprovalAvailable && agent.approvalStatus !== 'APPROVED' && (
        <div className="flex flex-col items-center gap-2">
          <button type="button" onClick={handleDemoApproval} disabled={isApproving || isFetching} className="btn-primary">
            {isApproving ? '승인 처리 중…' : '승인 처리 (실습용)'}
          </button>
          <p className="text-sm text-gray-500">실습을 위해 내 계정을 승인 상태로 변경합니다.</p>
        </div>
      )}
      {approvalError && <p role="alert" className="text-red-600">{approvalError}</p>}
      <div className="flex gap-3">
        <button type="button" onClick={() => refetch()} disabled={isFetching} className="btn-secondary">상태 다시 확인</button>
        <Link to="/agent/mypage" className="btn-primary">내 중개사 정보</Link>
      </div>
      <p className="text-sm text-gray-500">서류 업로드와 관리자 심사 기능은 준비 중입니다.</p>
    </div>
  )
}
export default AgentPendingPage
