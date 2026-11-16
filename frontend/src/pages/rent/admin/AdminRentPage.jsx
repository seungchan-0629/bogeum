// RENT_001: 관리자 납부 내역 조회. 실패·연체 집계는 #40·#41에서 연결한다.
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getAdminRentPayments } from '../../../api/admin/index.js'
import { useAuthStore } from '../../../stores/authStore.js'
import PageHeader from '../../../components/common/PageHeader.jsx'
import DataTable from '../../../components/common/DataTable.jsx'
import StatusBadge from '../../../components/common/StatusBadge.jsx'
import SearchInput from '../../../components/common/SearchInput.jsx'
import Pagination from '../../../components/common/Pagination.jsx'
import { Tabs } from '../../../components/common/Tabs.jsx'
import { formatWon } from '../../../utils/format.js'

// TODO: #40·#41 실제 실패·연체 API 연결. 아래는 화면 예시 데이터다.
const SAMPLE_FAILURES = [
  { failureId: 1, tenantName: '김보금', propertyTitle: '역삼동 오피스텔', round: '8월', failCount: 3, retryResult: '즉시 ✕ · 3일 ✕ · 7일 ✕', overdueDays: 58, state: 'CANDIDATE' },
  { failureId: 2, tenantName: '오세입', propertyTitle: '흑석동 원룸', round: '10월', failCount: 1, retryResult: '즉시 ✕ · 3일 예정', overdueDays: 0, state: 'RETRYING' },
  { failureId: 3, tenantName: '이월세', propertyTitle: '봉천동 빌라', round: '10월', failCount: 2, retryResult: '즉시 ✕ · 3일 ✕', overdueDays: 3, state: 'OVERDUE' },
]

const FAILURE_STATE = {
  CANDIDATE: { label: '위험 후보', tone: 'red' },
  RETRYING: { label: '재시도 중', tone: 'amber' },
  OVERDUE: { label: '연체', tone: 'amber' },
}

const PAYMENT_STATUS = {
  SCHEDULED: { label: '납부 예정', tone: 'blue' },
  PAID: { label: '납부 완료', tone: 'green' },
  FAILED: { label: '결제 실패', tone: 'red' },
  OVERDUE: { label: '연체', tone: 'amber' },
}

const formatDate = (date) => date?.slice(0, 10) || '-'

const PAYMENT_COLUMNS = [
  { label: '임차인', render: (row) => <strong>{row.tenantName}</strong> },
  { label: '매물', render: (row) => [row.roadAddress, row.detailAddress].filter(Boolean).join(' ') },
  { label: '회차', render: (row) => `${row.seq}회차` },
  { label: '예정일', render: (row) => formatDate(row.dueAt) },
  { label: '납부일', render: (row) => formatDate(row.paidAt) },
  { label: '금액', align: 'right', render: (row) => formatWon(row.amount) },
  { label: '상태', render: (row) => {
    const status = PAYMENT_STATUS[row.rentPaymentStatus] ?? { label: '상태 확인 필요', tone: 'gray' }
    return <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
  } },
]

const FAILURE_COLUMNS = [
  { label: '임차인', render: (row) => <strong>{row.tenantName}</strong> },
  { label: '매물', render: (row) => row.propertyTitle },
  { label: '회차', render: (row) => row.round },
  { label: '실패 횟수', align: 'right', render: (row) => `${row.failCount}회` },
  { label: '재시도 결과', render: (row) => row.retryResult },
  { label: '연체 기간', align: 'right', render: (row) => (row.overdueDays > 0 ? `${row.overdueDays}일` : '-') },
  { label: '상태', render: (row) => <StatusBadge tone={FAILURE_STATE[row.state].tone}>{FAILURE_STATE[row.state].label}</StatusBadge> },
]

function AdminRentPage() {
  const user = useAuthStore((state) => state.user)
  const [tab, setTab] = useState('PAYMENTS')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const list = useQuery({
    queryKey: ['admin-rent-payments', user?.userId, keyword, page],
    queryFn: ({ signal }) => getAdminRentPayments(keyword, page, signal),
    enabled: user?.role === 'ADMIN' && tab === 'PAYMENTS',
    gcTime: 0,
    retry: false,
  })
  const changeSearch = (event) => {
    setKeyword(event.target.value)
    setPage(1)
  }

  return (
    <>
      <PageHeader title="월세 관리" actions={(
        <div hidden={tab !== 'PAYMENTS'}><SearchInput label="임차인 검색" placeholder="임차인 이름으로 검색" onChange={changeSearch} /></div>
      )} />
      <Tabs
        label="월세 관리 보기"
        items={[
          { key: 'PAYMENTS', label: '납부 내역' },
          { key: 'FAILURES', label: '결제 실패·연체' },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === 'PAYMENTS' ? (
        user?.role === 'ADMIN' && <>
          {list.isPending && <p role="status">납부 내역을 불러오고 있어요.</p>}
          {list.isError && (
            <div role="alert" className="card flex items-center justify-between gap-3">
              <p>{list.error.response?.data?.message || '납부 내역을 불러오지 못했어요.'}</p>
              <button type="button" className="btn-secondary" disabled={list.isFetching} onClick={() => list.refetch()}>다시 조회</button>
            </div>
          )}
          {list.isFetching && !list.isPending && <p role="status">납부 내역을 다시 불러오고 있어요.</p>}
          {list.isSuccess && (
            <>
              <p className="text-sm text-gray-600">총 {list.data.totalElements}건</p>
              <DataTable columns={PAYMENT_COLUMNS} rows={list.data.payments} rowKey="rentPaymentId"
                emptyMessage={keyword.trim() ? '검색 결과가 없어요.' : '등록된 납부 내역이 없어요.'} />
              {list.data.payments.length > 0 && list.data.totalPages > 1 && (
                <Pagination page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />
              )}
            </>
          )}
        </>
      ) : (
        <>
          <p role="status" className="text-sm text-gray-600">화면 예시 데이터입니다. 실제 결제 실패·연체 조회는 준비 중입니다.</p>
          <DataTable columns={FAILURE_COLUMNS} rows={SAMPLE_FAILURES} rowKey="failureId" />
        </>
      )}
    </>
  )
}

export default AdminRentPage
