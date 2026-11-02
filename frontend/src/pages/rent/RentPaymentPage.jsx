// [페이지] 월세 납부 내역 (임차인) — /my-home/rent (내 집 관리의 첫 탭)
// 요구사항: RENT_005 (회차별 결제일·금액·상태), RENT_006 (실패 시 재시도 일정)
// API: GET /api/v1/tenants/me/rent-payments?cursor=&size= (BE-054)
// 카드번호·계좌번호는 어디에도 표시하지 않는다 (USER_013, USER_014)
import { Link } from 'react-router'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import SummaryStrip from '../../components/common/SummaryStrip.jsx'
import DataTable from '../../components/common/DataTable.jsx'
import { formatWon } from '../../utils/format.js'

const PAYMENT_STATUS = {
  SUCCESS: { label: '성공', tone: 'green' },
  FAILED: { label: '실패', tone: 'red' },
  OVERDUE: { label: '연체', tone: 'amber' },
}

// TODO: GET /api/v1/tenants/me/rent-payments (BE-054) 응답의 payments[]로 교체 (round, paidAt, amount, status, 재시도 정보)
const SAMPLE_PAYMENTS = [
  { round: '10월', paidAt: '2026.10.25', amount: 650000, status: 'SUCCESS', note: '' },
  { round: '9월', paidAt: '2026.09.28', amount: 650000, status: 'SUCCESS', note: '3일 후 재시도에서 결제' },
  { round: '8월', paidAt: '2026.08.25', amount: 650000, status: 'FAILED', note: '재시도 예정: 8.28, 9.1' },
  { round: '7월', paidAt: '2026.07.25', amount: 650000, status: 'OVERDUE', note: '' },
]

const COLUMNS = [
  { label: '회차', render: (row) => <strong>{row.round}</strong> },
  { label: '결제일', render: (row) => row.paidAt },
  { label: '금액', align: 'right', render: (row) => formatWon(row.amount) },
  { label: '상태', render: (row) => <StatusBadge tone={PAYMENT_STATUS[row.status].tone}>{PAYMENT_STATUS[row.status].label}</StatusBadge> },
  { label: '비고', render: (row) => <span className="text-gray-600">{row.note || '-'}</span> },
]

function RentPaymentPage() {
  // TODO: 요약 값도 BE-054 응답(가장 최근 회차)과 결제 수단 등록 여부에서 계산한다
  const summary = [
    { label: '이번 달 월세', value: '650,000원', detail: '10.25 결제 완료' },
    { label: '다음 납부일', value: '11월 25일', detail: '등록된 카드로 자동 결제' },
    { label: '결제 수단', value: '카드 등록됨', detail: <Link to="/mypage/payment-method" className="text-blue-700 hover:underline">변경</Link> },
  ]

  return (
    <div className="flex flex-col gap-5">
      <SummaryStrip items={summary} />
      <DataTable columns={COLUMNS} rows={SAMPLE_PAYMENTS} rowKey="round" />
    </div>
  )
}

export default RentPaymentPage
