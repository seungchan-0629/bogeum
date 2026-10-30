// [페이지] 월세 관리 (임대인) — /rent/collections
// 매물·연도를 고르면 월별 수납 현황과 지급 내역을 보여준다
// 요구사항: RENT_003 (매물별 수납 현황, 월별·연별), SETTLE_002 (지급 내역), NOTIFY_014 (정산 계좌 미등록 안내)
// API:
// - 수납 현황: GET /api/v1/landlords/me/rents?propertyId=&year=&month= (BE-053)
// - 지급 내역: GET /api/v1/landlords/me/settlements?propertyId=&cursor=&size= (BE-059)
// 임차인의 계좌 정보는 표시하지 않는다 (NOTIFY_013)
import { useState } from 'react'
import { Link } from 'react-router'
import clsx from 'clsx'
import PageHeader from '../../components/common/PageHeader.jsx'
import SummaryStrip from '../../components/common/SummaryStrip.jsx'
import DataTable from '../../components/common/DataTable.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import { SelectInput } from '../../components/common/FormControls.jsx'
import { Tabs } from '../../components/common/Tabs.jsx'
import { formatWon } from '../../utils/format.js'

const PROPERTY_OPTIONS = [
  { value: '1', label: '역삼동 오피스텔' },
  { value: '2', label: '공덕동 아파트' },
]
const YEAR_OPTIONS = [
  { value: '2026', label: '2026년' },
  { value: '2025', label: '2025년' },
]
const VIEW_TABS = [
  { key: 'COLLECTION', label: '수납 현황' },
  { key: 'SETTLEMENT', label: '지급 내역' },
]

// TODO: GET /api/v1/landlords/me/rents (BE-053) 응답으로 교체. 월별 상태: PAID(입금), UNPAID(미입금), UPCOMING(예정)
const SAMPLE_MONTHLY_STATUS = ['PAID', 'PAID', 'PAID', 'PAID', 'PAID', 'PAID', 'PAID', 'UNPAID', 'PAID', 'PAID', 'UPCOMING', 'UPCOMING']
const MONTH_STATUS = {
  PAID: { label: '입금 완료', className: 'bg-emerald-100' },
  UNPAID: { label: '미입금', className: 'bg-red-100' },
  UPCOMING: { label: '예정', className: 'bg-gray-100' },
}

// TODO: GET /api/v1/landlords/me/settlements (BE-059) 응답의 settlements[]로 교체 (settledAt, amount, status)
const SAMPLE_SETTLEMENTS = [
  { settlementId: 1, settledAt: '2026.10.25 09:02', amount: 650000, status: 'DONE' },
  { settlementId: 2, settledAt: '2026.09.28 09:01', amount: 650000, status: 'DONE' },
  { settlementId: 3, settledAt: '2026.08.25 09:03', amount: 650000, status: 'FAILED' },
]

const SETTLEMENT_COLUMNS = [
  { label: '지급 일시', render: (row) => row.settledAt },
  { label: '금액', align: 'right', render: (row) => formatWon(row.amount) },
  { label: '상태', render: (row) => (row.status === 'DONE' ? <StatusBadge tone="green">지급 완료</StatusBadge> : <StatusBadge tone="red">지급 실패</StatusBadge>) },
]

// TODO: 정산 계좌 등록 여부를 알려주는 필드가 명세에 없어 백엔드와 협의 필요
const SAMPLE_HAS_SETTLEMENT_ACCOUNT = false

function RentCollectionPage() {
  const [view, setView] = useState('COLLECTION')

  return (
    <div className="page-container">
      <PageHeader
        title="월세 관리"
        actions={
          <>
            <label htmlFor="rent-property" className="sr-only">매물</label>
            <SelectInput id="rent-property" options={PROPERTY_OPTIONS} size="sm" fullWidth={false} />
            <label htmlFor="rent-year" className="sr-only">연도</label>
            <SelectInput id="rent-year" options={YEAR_OPTIONS} size="sm" fullWidth={false} />
          </>
        }
      />

      {!SAMPLE_HAS_SETTLEMENT_ACCOUNT && (
        <p className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-amber-900">
          <strong>정산 계좌를 등록해 주세요.</strong> 계좌가 없으면 결제된 월세를 지급할 수 없어 보류돼요.{' '}
          <Link to="/mypage/settlement-account" className="font-bold text-blue-700 hover:underline">정산 계좌 등록</Link>
        </p>
      )}

      <SummaryStrip
        items={[
          { label: '올해 수납', value: '9 / 10회' },
          { label: '이번 달', value: '650,000원', detail: '10.25 입금 완료' },
          { label: '미입금', value: '1건', detail: '8월분 · 재시도 중' },
        ]}
      />

      <Tabs label="월세 관리 보기" items={VIEW_TABS} value={view} onChange={setView} />

      {view === 'COLLECTION' ? (
        <section aria-label="월별 수납 현황" className="card flex flex-col gap-3">
          <ol className="grid grid-cols-6 gap-2 md:grid-cols-12">
            {SAMPLE_MONTHLY_STATUS.map((status, index) => (
              <li key={index} className="flex flex-col items-center gap-1.5">
                <span className="text-sm text-gray-600">{index + 1}월</span>
                <span className={clsx('h-10 w-full rounded-lg', MONTH_STATUS[status].className)} title={MONTH_STATUS[status].label}>
                  <span className="sr-only">{MONTH_STATUS[status].label}</span>
                </span>
              </li>
            ))}
          </ol>
          <div className="flex flex-wrap gap-4 text-sm text-gray-600">
            {Object.values(MONTH_STATUS).map((item) => (
              <span key={item.label} className="flex items-center gap-1.5">
                <span aria-hidden="true" className={clsx('size-3 rounded', item.className)} />
                {item.label}
              </span>
            ))}
          </div>
        </section>
      ) : (
        <DataTable columns={SETTLEMENT_COLUMNS} rows={SAMPLE_SETTLEMENTS} rowKey="settlementId" />
      )}
    </div>
  )
}

export default RentCollectionPage
