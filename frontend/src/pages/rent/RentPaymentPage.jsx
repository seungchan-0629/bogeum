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
  PAID: { label: '수납 완료', tone: 'green' },
  FAILED: { label: '납부 실패', tone: 'red' },
  OVERDUE: { label: '연체', tone: 'amber' },
}

const SETTLEMENT_STATUS = {
  PENDING: { label: '지급 대기', tone: 'gray' },
  PROCESSING: { label: '지급 처리 중', tone: 'blue' },
  UNKNOWN: { label: '지급 결과 확인 중', tone: 'amber' },
  COMPLETED: { label: '지급 완료', tone: 'green' },
  FAILED: { label: '지급 실패', tone: 'red' },
}

const formatDate = (value) => value ? value.slice(0, 10).replaceAll('-', '.') : '-'

const COLUMNS = [
  { label: '회차', render: (row) => <strong>{row.seq}회차</strong> },
  { label: '납부 예정일', render: (row) => formatDate(row.dueAt) },
  { label: '수납일', render: (row) => formatDate(row.paidAt) },
  { label: '금액', align: 'right', render: (row) => formatWon(row.amount) },
  { label: '회사 수납', render: (row) => {
    const status = PAYMENT_STATUS[row.rentPaymentStatus] ?? { label: '확인 필요', tone: 'gray' }
    return <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
  } },
  { label: '임대인 지급', render: (row) => {
    const status = SETTLEMENT_STATUS[row.settlementStatus] ?? {
      label: row.rentPaymentStatus === 'PAID' ? '지급 내역 확인 필요' : '수납 전', tone: 'gray',
    }
    return <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
  } },
  { label: '지급일', render: (row) => formatDate(row.settlementPaidAt) },
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
      <PageHeader title="전체 월세 납부 내역" description="출금된 월세의 회사 수납과 임대인 지급 상태를 확인할 수 있어요." actions={<button type="button" className="btn-secondary btn-sm" disabled={isFetching} onClick={() => refetch()}>내역 새로고침</button>} />
      <SummaryStrip items={summary} />
      <p className="text-sm text-gray-600">수납 완료는 회사 계좌로 월세가 입금된 상태예요. 임대인 지급이 대기 중이거나 확인 중이어도 같은 회차의 월세를 다시 납부하지 마세요.</p>
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
