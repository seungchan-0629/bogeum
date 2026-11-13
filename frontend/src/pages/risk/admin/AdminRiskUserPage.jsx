// RISK_002: 관리자 위험 사용자 유형별 목록·이름 검색·서버 페이징.
// GET /admin/risk-users?type=&keyword=&page= (#44). 페이지 크기와 정렬은 서버에서 결정한다.
// 증빙은 키 기록 여부만 표시하며 등록·수정·후보 조회·파일 열람은 별도 기능이다.
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getAdminRiskUsers } from '../../../api/admin/index.js'
import { useAuthStore } from '../../../stores/authStore.js'
import PageHeader from '../../../components/common/PageHeader.jsx'
import DataTable from '../../../components/common/DataTable.jsx'
import StatusBadge from '../../../components/common/StatusBadge.jsx'
import SearchInput from '../../../components/common/SearchInput.jsx'
import Pagination from '../../../components/common/Pagination.jsx'
import { Tabs } from '../../../components/common/Tabs.jsx'

const USER_TYPE_TABS = [
  { key: 'TENANT', label: '임차인' },
  { key: 'LANDLORD', label: '임대인' },
  { key: 'AGENT', label: '중개사' },
]

const RISK_REASONS = {
  RENT_UNPAID: { label: '월세 미지급', tone: 'red' },
  RENT_OVERDUE: { label: '연체', tone: 'amber' },
  DEPOSIT_UNPAID: { label: '보증금 미지급', tone: 'red' },
  FALSE_LISTING: { label: '허위 매물 등록', tone: 'amber' },
  NO_PROPERTY_IMAGE: { label: '매물 이미지 미등록', tone: 'amber' },
  AGENT_CHANGE_HISTORY: { label: '중개사 교체 이력', tone: 'amber' },
}

function AdminRiskUserPage() {
  const user = useAuthStore((state) => state.user)
  const [userType, setUserType] = useState('TENANT')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const list = useQuery({
    queryKey: ['admin-risk-users', user?.userId, userType, keyword, page],
    queryFn: ({ signal }) => getAdminRiskUsers(userType, keyword, page, signal),
    enabled: user?.role === 'ADMIN',
    gcTime: 0,
    retry: false,
  })
  const changeType = (nextType) => {
    setUserType(nextType)
    setPage(1)
  }
  const changeSearch = (event) => {
    setKeyword(event.target.value)
    setPage(1)
  }

  const columns = [
    { label: '이름', render: (row) => (
      <div className="flex flex-col gap-1 whitespace-nowrap">
        <strong>{row.name}</strong>
        <span className="text-xs text-gray-500">ID {row.userId}</span>
      </div>
    ) },
    { label: '사유 유형', render: (row) => {
      const reason = RISK_REASONS[row.reasonType] ?? { label: '사유 확인 필요', tone: 'gray' }
      return <StatusBadge tone={reason.tone}>{reason.label}</StatusBadge>
    } },
    { label: '상세 내용', render: (row) => <span className="whitespace-pre-wrap break-words">{row.detail?.trim() ? row.detail : '-'}</span> },
    { label: '근거 자료', render: (row) => row.hasEvidence ? '있음' : '없음' },
    { label: '생성일', render: (row) => row.createdAt?.slice(0, 10) || '-' },
  ]

  return (
    <>
      <PageHeader title="위험 사용자" />
      <div className="flex flex-wrap items-end justify-between gap-3">
        <Tabs label="대상" items={USER_TYPE_TABS} value={userType} onChange={changeType} />
        <SearchInput label="이름 검색" placeholder="이름으로 검색" onChange={changeSearch} />
      </div>
      {list.isPending && <p role="status">위험 사용자 목록을 불러오고 있어요.</p>}
      {list.isError && (
        <div role="alert" className="card flex items-center justify-between gap-3">
          <p>{list.error.response?.data?.message || '위험 사용자 목록을 불러오지 못했어요.'}</p>
          <button type="button" className="btn-secondary" disabled={list.isFetching} onClick={() => list.refetch()}>다시 조회</button>
        </div>
      )}
      {list.isFetching && !list.isPending && <p role="status">위험 사용자 목록을 다시 불러오고 있어요.</p>}
      {list.isSuccess && (
        <>
          <p className="text-sm text-gray-600">총 {list.data.totalElements}건</p>
          <DataTable columns={columns} rows={list.data.riskUsers} rowKey="riskInfoId"
            emptyMessage={keyword.trim() ? '검색 결과가 없어요.' : '등록된 위험 사용자가 없어요.'} />
          {list.data.totalPages > 1 && (
            <Pagination page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />
          )}
        </>
      )}
    </>
  )
}

export default AdminRiskUserPage
