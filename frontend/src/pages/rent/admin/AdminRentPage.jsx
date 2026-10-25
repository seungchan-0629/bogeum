// [관리자 페이지] 월세 관리 — /admin/rent
// 탭 두 개: 임차인 월세 납부 내역 / 결제 실패·연체 내역
// 요구사항: RENT_001 (납부 내역), RENT_002 (실패 횟수·재시도 결과·연체 기간), NOTIFY_003 (최종 재시도 실패 알림)
// API:
// - 납부 내역: GET /api/v1/admin/rents/payments?tenantId=&page=&size= (BE-051)
// - 실패·연체: GET /api/v1/admin/rents/failures?page=&size= (BE-052)
import { useState } from 'react'
import PageHeader from '../../../components/common/PageHeader.jsx'
import DataTable from '../../../components/common/DataTable.jsx'
import StatusBadge from '../../../components/common/StatusBadge.jsx'
import SearchInput from '../../../components/common/SearchInput.jsx'
import Pagination from '../../../components/common/Pagination.jsx'
import { Tabs } from '../../../components/common/Tabs.jsx'
import { formatWon } from '../../../utils/format.js'

// TODO: GET /api/v1/admin/rents/payments (BE-051) 응답으로 교체
const SAMPLE_PAYMENTS = [
  { paymentId: 1, tenantName: '김보금', propertyTitle: '역삼동 오피스텔', round: '10월', paidAt: '2026.10.25', amount: 650000, isSuccess: true },
  { paymentId: 2, tenantName: '오세입', propertyTitle: '흑석동 원룸', round: '10월', paidAt: '2026.10.10', amount: 500000, isSuccess: true },
  { paymentId: 3, tenantName: '이월세', propertyTitle: '봉천동 빌라', round: '10월', paidAt: '2026.10.20', amount: 450000, isSuccess: false },
]

// TODO: GET /api/v1/admin/rents/failures (BE-052) 응답으로 교체 (실패 횟수, 재시도 결과, 연체 기간, 위험 후보 여부)
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

const PAYMENT_COLUMNS = [
  { label: '임차인', render: (row) => <strong>{row.tenantName}</strong> },
  { label: '매물', render: (row) => row.propertyTitle },
  { label: '회차', render: (row) => row.round },
  { label: '결제일', render: (row) => row.paidAt },
  { label: '금액', align: 'right', render: (row) => formatWon(row.amount) },
  { label: '결과', render: (row) => (row.isSuccess ? <StatusBadge tone="green">성공</StatusBadge> : <StatusBadge tone="red">실패</StatusBadge>) },
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
  const [tab, setTab] = useState('FAILURES')
  const [page, setPage] = useState(1)

  return (
    <>
      <PageHeader title="월세 관리" actions={<SearchInput label="임차인 검색" placeholder="임차인 이름으로 검색" />} />
      <Tabs
        label="월세 관리 보기"
        items={[
          { key: 'PAYMENTS', label: '납부 내역' },
          { key: 'FAILURES', label: `결제 실패·연체 ${SAMPLE_FAILURES.length}` },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === 'PAYMENTS' ? (
        <DataTable columns={PAYMENT_COLUMNS} rows={SAMPLE_PAYMENTS} rowKey="paymentId" />
      ) : (
        <DataTable columns={FAILURE_COLUMNS} rows={SAMPLE_FAILURES} rowKey="failureId" />
      )}
      <Pagination page={page} totalPages={3} onChange={setPage} />
    </>
  )
}

export default AdminRentPage
