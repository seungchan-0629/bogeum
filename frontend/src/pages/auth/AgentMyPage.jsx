import { Link } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import { useAgentProfile } from '../../hooks/useAgentProfile.js'

const STATUS_LABELS = { PENDING: '승인 대기', APPROVED: '승인 완료', REJECTED: '반려' }

function AgentMyPage() {
  const { data: agent, isPending, isFetching, error, refetch } = useAgentProfile()
  return (
    <div className="page-container">
      <PageHeader title="마이페이지" />
      {isPending && <p role="status">중개사 정보를 확인하고 있어요.</p>}
      {error && <p role="alert" className="text-red-600">{error.response?.data?.message || '중개사 정보를 불러오지 못했습니다.'}</p>}
      {agent && (
        <section className="card flex flex-col gap-3">
          <h2 className="text-lg font-bold">내 중개사 정보</h2>
          <dl className="flex flex-col gap-3">
            {[
              ['중개사무소', agent.officeName], ['대표자', agent.representativeName],
              ['이메일', agent.email], ['주소', agent.officeAddress], ['전화번호', agent.phone],
              ['가입 상태', STATUS_LABELS[agent.approvalStatus]],
            ].map(([label, value]) => (
              <div key={label} className="flex justify-between gap-4"><dt className="text-gray-600">{label}</dt><dd>{value}</dd></div>
            ))}
          </dl>
        </section>
      )}
      <div className="flex gap-3">
        <button type="button" onClick={() => refetch()} disabled={isFetching} className="btn-secondary">내 정보 다시 확인</button>
        <Link to="/agent/pending" className="btn-primary">가입 상태 보기</Link>
      </div>
    </div>
  )
}
export default AgentMyPage
