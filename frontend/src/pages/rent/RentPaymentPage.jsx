// [페이지] 월세 납부 내역 (임차인) — /my-home/rent (내 집 관리의 첫 탭)
// 회원이 임차인으로 참여한 모든 계약의 월세 납부 내역을 보여준다.
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import SummaryStrip from '../../components/common/SummaryStrip.jsx'
import DataTable from '../../components/common/DataTable.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { api } from '../../utils/jwtUtil.js'
import { formatWon } from '../../utils/format.js'

const PAYMENT_STATUS = {
  SCHEDULED: { label: '납부 예정', tone: 'gray' },
  PROCESSING: { label: '출금 처리 중', tone: 'blue' },
  UNKNOWN: { label: '결과 확인 필요', tone: 'amber' },
  PAID: { label: '납부 완료', tone: 'green' },
  FAILED: { label: '납부 실패', tone: 'red' },
  OVERDUE: { label: '연체', tone: 'amber' },
}

const formatDate = (value) => value ? value.slice(0, 10).replaceAll('-', '.') : '-'

const COLUMNS = [
  { label: '회차', render: (row) => <strong>{row.seq}회차</strong> },
  { label: '납부 예정일', render: (row) => formatDate(row.dueAt) },
  { label: '납부일', render: (row) => formatDate(row.paidAt) },
  { label: '금액', align: 'right', render: (row) => formatWon(row.amount) },
  { label: '상태', render: (row) => {
    const status = PAYMENT_STATUS[row.rentPaymentStatus] ?? { label: '확인 필요', tone: 'gray' }
    return <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
  } },
]

function RentPaymentPage() {
  const userId = useAuthStore((state) => state.user?.userId)
  const { data: payments, isPending, isFetching, error, refetch } = useQuery({
    queryKey: ['rent-payments', userId],
    queryFn: async ({ signal }) => (await api.get('/v1/tenants/me/rent-payments', { signal })).data,
    enabled: Boolean(userId),
    retry: false,
  })
  const accountQuery = useQuery({
    queryKey: ['payment-account', userId],
    queryFn: async ({ signal }) => (await api.get('/v1/tenants/me/payment-account', { signal })).data || null,
    enabled: Boolean(userId),
    retry: false,
  })
  const account = accountQuery.data
  const summary = [
    { label: '전체 납부 회차', value: error ? '조회 실패' : isPending ? '조회 중' : `${payments.length}건` },
    { label: '출금 계좌', value: accountQuery.error ? '조회 실패' : accountQuery.isPending ? '조회 중' : account ? `${account.bankCode} · ${account.accountNumberMasked}` : '미등록', detail: <Link to="/mypage/payment-method" className="text-blue-700 hover:underline">확인·변경</Link> },
  ]

  return (
    <div className="flex flex-col gap-5">
      <PageHeader title="전체 월세 납부 내역" description="임차인으로 계약한 모든 계약의 월세 납부 내역이에요." />
      <SummaryStrip items={summary} />
      {accountQuery.error && (
        <div role="alert">
          <p className="text-red-600">출금 계좌를 불러오지 못했습니다.</p>
          <button type="button" className="btn-secondary btn-sm" disabled={accountQuery.isFetching} onClick={() => accountQuery.refetch()}>계좌 다시 불러오기</button>
        </div>
      )}
      {isPending ? <p role="status">월세 납부 내역을 불러오고 있어요.</p> : error ? (
        <div role="alert">
          <p className="text-red-600">월세 납부 내역을 불러오지 못했습니다.</p>
          <button type="button" className="btn-secondary btn-sm" disabled={isFetching} onClick={() => refetch()}>다시 불러오기</button>
        </div>
      ) : <DataTable columns={COLUMNS} rows={payments} rowKey="rentPaymentId" emptyMessage="월세 납부 내역이 없어요." />}
    </div>
  )
}

export default RentPaymentPage
