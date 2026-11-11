// AGENT_001: 관리자 중개사 목록·사무소명/주소 검색·페이징. 정렬은 서버에서 적용한다.
// 서류 열람·가입 심사는 후속 AGENT_002에서 연결한다.
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { getAdminAgents } from '../../../api/admin/index.js'
import { useAuthStore } from '../../../stores/authStore.js'
import PageHeader from '../../../components/common/PageHeader.jsx'
import StatusBadge from '../../../components/common/StatusBadge.jsx'
import SearchInput from '../../../components/common/SearchInput.jsx'
import Pagination from '../../../components/common/Pagination.jsx'

const APPROVAL_STATUS = {
  PENDING: { label: '검증 대기', tone: 'amber' },
  APPROVED: { label: '승인', tone: 'green' },
  REJECTED: { label: '반려', tone: 'red' },
}

const formatDate = (createdAt) => createdAt?.slice(0, 10) || '-'

function AdminAgentPage() {
  const user = useAuthStore((state) => state.user)
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [selectedId, setSelectedId] = useState(null)
  const list = useQuery({
    queryKey: ['admin-agents', user?.userId, keyword, page],
    queryFn: ({ signal }) => getAdminAgents(keyword, page, signal),
    enabled: user?.role === 'ADMIN',
    gcTime: 0,
    retry: false,
  })
  // 다른 검색·페이지의 데이터를 유지하지 않고 현재 결과 안에서만 선택한다.
  const agents = list.isSuccess ? list.data.agents : []
  const selected = agents.find((agent) => agent.agentId === selectedId) ?? agents[0]
  const changeSearch = (event) => {
    setKeyword(event.target.value)
    setPage(1)
    setSelectedId(null)
  }
  const changePage = (nextPage) => {
    setPage(nextPage)
    setSelectedId(null)
  }

  return (
    <>
      <PageHeader title="중개사 관리" actions={<SearchInput label="중개사 검색" placeholder="중개사무소 이름, 주소로 검색" onChange={changeSearch} />} />
      {list.isPending && <p role="status">중개사 목록을 불러오고 있어요.</p>}
      {list.isError && (
        <div role="alert" className="card flex items-center justify-between gap-3">
          <p>{list.error.response?.data?.message || '중개사 목록을 불러오지 못했어요.'}</p>
          <button type="button" className="btn-secondary" onClick={() => list.refetch()}>다시 조회</button>
        </div>
      )}
      {list.isSuccess && (
        <div className="grid items-start gap-5 xl:grid-cols-[1fr_360px]">
          <div className="flex flex-col gap-4">
            <p className="text-sm text-gray-600">총 {list.data.totalElements}개</p>
            {agents.length === 0 ? (
              <p role="status" className="card py-10 text-center text-gray-600">
                {keyword.trim() ? '검색 결과가 없어요.' : '등록된 중개사가 없어요.'}
              </p>
            ) : (
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
                    {agents.map((agent) => {
                      const approval = APPROVAL_STATUS[agent.approvalStatus] ?? { label: '상태 확인 필요', tone: 'gray' }
                      return (
                        <tr key={agent.agentId} onClick={() => setSelectedId(agent.agentId)}
                          className={clsx('cursor-pointer border-b border-gray-100 last:border-b-0 hover:bg-slate-50', agent.agentId === selected?.agentId && 'bg-blue-50')}>
                          <td className="px-5 py-3.5">
                            <button type="button" onClick={() => setSelectedId(agent.agentId)}
                              aria-pressed={agent.agentId === selected?.agentId} className="font-bold">{agent.officeName}</button>
                          </td>
                          <td className="px-5 py-3.5">{agent.officeAddress}</td>
                          <td className="px-5 py-3.5">{formatDate(agent.createdAt)}</td>
                          <td className="px-5 py-3.5"><StatusBadge tone={approval.tone}>{approval.label}</StatusBadge></td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
            {list.data.totalPages > 1 && (
              <Pagination page={list.data.page} totalPages={list.data.totalPages} onChange={changePage} />
            )}
          </div>
          {selected && (
            <section key={selected.agentId} aria-labelledby="agent-detail-title" className="card flex flex-col gap-4">
              <div>
                <h2 id="agent-detail-title" className="text-lg font-bold">{selected.officeName}</h2>
                <p className="text-sm text-gray-600">대표 {selected.representativeName}</p>
              </div>
              <p>{selected.officeAddress}</p>
              <p className="text-sm text-gray-600">가입일 {formatDate(selected.createdAt)}</p>
              <p className="text-sm text-gray-500">서류 열람과 승인·반려 기능은 준비 중입니다.</p>
            </section>
          )}
        </div>
      )}
    </>
  )
}

export default AdminAgentPage
