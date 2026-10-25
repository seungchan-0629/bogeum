// [관리자 페이지] 중개사 관리 · 가입 검증 — /admin/agents
// 왼쪽: 중개사 목록(페이지 방식, 신규 신청은 맨 위) / 오른쪽: 선택한 중개사의 제출 서류와 승인·반려
// 요구사항: AGENT_001 (조회, 사무소명·주소 검색, 신규 신청 최상단), AGENT_002 (가입 검증·최종 승인), NOTIFY_025 (결과 알림)
// API:
// - 목록: GET /api/v1/admin/agents?keyword=&page=&size= (BE-076)
// - 제출 서류: GET /api/v1/admin/agents/{agentId}/documents (BE-077)
// - 승인·반려: PUT/PATCH /api/v1/admin/agents/{agentId}/approval (BE-078) — 반려 시 사유 포함
import { useState } from 'react'
import clsx from 'clsx'
import PageHeader from '../../../components/common/PageHeader.jsx'
import StatusBadge from '../../../components/common/StatusBadge.jsx'
import SearchInput from '../../../components/common/SearchInput.jsx'
import Pagination from '../../../components/common/Pagination.jsx'
import { Field, TextInput } from '../../../components/common/FormControls.jsx'

const APPROVAL_STATUS = {
  WAITING: { label: '검증 대기', tone: 'amber' },
  NO_DOCUMENT: { label: '서류 미제출', tone: 'gray' },
  APPROVED: { label: '승인', tone: 'green' },
  REJECTED: { label: '반려', tone: 'red' },
}

// TODO: GET /api/v1/admin/agents (BE-076) 응답으로 교체. 신규 신청이 먼저 오도록 정렬은 서버에서 해 주는지 협의 필요
const SAMPLE_AGENTS = [
  { agentId: 11, officeName: '새봄공인중개사', representative: '정새봄', phone: '02-555-0000', address: '서울 송파구', joinedAt: '10.23', status: 'WAITING', isNew: true },
  { agentId: 12, officeName: '한빛부동산', representative: '한빛나', phone: '02-777-1111', address: '서울 노원구', joinedAt: '10.22', status: 'NO_DOCUMENT', isNew: true },
  { agentId: 3, officeName: '보금공인중개사', representative: '이중개', phone: '02-123-4567', address: '서울 강남구', joinedAt: '09.30', status: 'APPROVED', isNew: false },
  { agentId: 4, officeName: '한강부동산', representative: '김한강', phone: '02-333-2222', address: '서울 마포구', joinedAt: '09.28', status: 'APPROVED', isNew: false },
  { agentId: 5, officeName: '새집중개', representative: '박새집', phone: '02-444-3333', address: '서울 성북구', joinedAt: '09.15', status: 'REJECTED', isNew: false },
]

// TODO: GET /api/v1/admin/agents/{agentId}/documents (BE-077) 응답의 documents[]로 교체 (서류명, 파일 주소)
const SAMPLE_DOCUMENTS = ['공인중개사 자격증', '중개사무소 등록증', '사업자등록증']

function AdminAgentPage() {
  const [selectedId, setSelectedId] = useState(SAMPLE_AGENTS[0].agentId)
  const [page, setPage] = useState(1)
  const selected = SAMPLE_AGENTS.find((agent) => agent.agentId === selectedId)

  // TODO: PUT/PATCH /api/v1/admin/agents/{agentId}/approval (BE-078) — 요청: { approved: true/false, rejectReason }
  const handleApprove = () => {}
  const handleReject = () => {}

  return (
    <>
      <PageHeader title="중개사 관리" actions={<SearchInput label="중개사 검색" placeholder="중개사무소 이름, 주소로 검색" />} />
      <div className="grid items-start gap-5 xl:grid-cols-[1fr_360px]">
        <div className="flex flex-col gap-4">
          <div className="card overflow-x-auto p-0">
            <table className="w-full min-w-[560px] text-left text-sm">
              <thead className="text-gray-500">
                <tr className="border-b border-gray-200">
                  <th scope="col" className="px-5 py-3 font-medium">중개사무소</th>
                  <th scope="col" className="px-5 py-3 font-medium">주소</th>
                  <th scope="col" className="px-5 py-3 font-medium">가입일</th>
                  <th scope="col" className="px-5 py-3 font-medium">상태</th>
                </tr>
              </thead>
              <tbody>
                {SAMPLE_AGENTS.map((agent) => (
                  <tr
                    key={agent.agentId}
                    onClick={() => setSelectedId(agent.agentId)}
                    className={clsx('cursor-pointer border-b border-gray-100 last:border-b-0 hover:bg-slate-50', agent.agentId === selectedId && 'bg-blue-50')}
                  >
                    <td className="px-5 py-3.5">
                      <button type="button" onClick={() => setSelectedId(agent.agentId)} className="flex items-center gap-2 font-bold">
                        {agent.officeName}
                        {agent.isNew && <StatusBadge tone="red">신규</StatusBadge>}
                      </button>
                    </td>
                    <td className="px-5 py-3.5">{agent.address}</td>
                    <td className="px-5 py-3.5">{agent.joinedAt}</td>
                    <td className="px-5 py-3.5">
                      <StatusBadge tone={APPROVAL_STATUS[agent.status].tone}>{APPROVAL_STATUS[agent.status].label}</StatusBadge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={page} totalPages={5} onChange={setPage} />
        </div>

        <section key={selected.agentId} aria-labelledby="agent-review-title" className="card flex flex-col gap-4">
          <div className="flex flex-col">
            <h2 id="agent-review-title" className="text-lg font-bold">{selected.officeName} 검증</h2>
            <p className="text-sm text-gray-600">대표 {selected.representative} · {selected.phone}</p>
          </div>
          <ul className="flex flex-col gap-2.5">
            {SAMPLE_DOCUMENTS.map((documentName) => (
              <li key={documentName} className="flex justify-between gap-3">
                <span>{documentName}</span>
                <button type="button" className="text-sm font-medium text-blue-700 hover:underline">보기</button>
              </li>
            ))}
          </ul>
          <Field label="반려 사유 (반려할 때만)" htmlFor="reject-reason">
            <TextInput id="reject-reason" placeholder="예: 등록증 사진이 흐려요" />
          </Field>
          <div className="flex gap-2">
            <button type="button" onClick={handleReject} className="btn-secondary flex-1">반려</button>
            <button type="button" onClick={handleApprove} className="btn-primary flex-1">최종 승인</button>
          </div>
        </section>
      </div>
    </>
  )
}

export default AdminAgentPage
