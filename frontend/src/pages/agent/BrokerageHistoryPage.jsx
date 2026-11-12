// [페이지] 중개 이력 (중개사) — /agent/history
// 내가 중개해서 계약 정보까지 등록한(계약 완료) 계약을 본다. 진행 중인 매물은 내 계약 관리(/agent/contracts)에서 본다
// 요구사항: AGENT_003
// API: GET /contract-details?page= — 계약일 최신순, 한 페이지 10개
import { useState } from 'react'
import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import PageHeader from '../../components/common/PageHeader.jsx'
import DataTable from '../../components/common/DataTable.jsx'
import Pagination from '../../components/common/Pagination.jsx'
import { formatLeasePrice, formatManwon } from '../../utils/format.js'
import { getContracts } from '../../api/contract/index.js'

// "2026-11-08" → "2026.11.08"
const formatDate = (date) => date.replaceAll('-', '.')

// 목록 API는 원 단위, formatLeasePrice는 만원 단위라서 바꿔서 넘긴다
const toManwonPrice = (contract) => ({
  leaseType: contract.leaseType,
  deposit: contract.deposit / 10000,
  monthlyRent: contract.monthlyRent / 10000,
})

const COLUMNS = [
  { label: '매물', render: (row) => <span className="font-bold">{`${row.roadAddress} ${row.detailAddress ?? ''}`.trim()}</span> },
  { label: '거래', render: (row) => formatLeasePrice(toManwonPrice(row)) },
  // 계약 등록 화면에서 만원 단위(소수 첫째 자리까지)로 입력받아 같은 단위로 보여 준다
  { label: '중개보수', align: 'right', render: (row) => `${formatManwon(row.brokerageFee / 10000)}만원` },
  { label: '임대인', render: (row) => row.landlordName },
  { label: '임차인', render: (row) => row.tenantName },
  { label: '계약 기간', render: (row) => <span className="whitespace-nowrap">{formatDate(row.startAt)} ~ {formatDate(row.endAt)}</span> },
  { label: '계약일', render: (row) => <span className="whitespace-nowrap">{formatDate(row.contractedAt)}</span> },
]

function BrokerageHistoryPage() {
  const [page, setPage] = useState(1)
  // 계약을 등록하면 ['contracts']를 다시 불러와 방금 등록한 계약이 바로 보인다 (ContractUploadPage)
  const list = useQuery({
    queryKey: ['contracts', page],
    queryFn: ({ signal }) => getContracts(page, signal),
  })

  return (
    <div className="page-container">
      <PageHeader
        title="중개 이력"
        description={`계약까지 마친 중개 이력이에요.${list.isSuccess ? ` 총 ${list.data.totalElements}건` : ''}`}
      />
      {list.isPending && <p role="status">중개 이력을 불러오고 있어요.</p>}
      {list.isError && <p role="alert">중개 이력을 불러오지 못했어요.</p>}
      {list.isSuccess && (
        <DataTable
          columns={COLUMNS}
          rows={list.data.contracts}
          rowKey="contractId"
          emptyMessage={
            <span className="flex flex-col items-center gap-3">
              아직 계약을 마친 중개가 없어요.
              <Link to="/agent/contracts" className="btn-secondary btn-sm">내 계약 관리 보기</Link>
            </span>
          }
        />
      )}
      {list.data?.totalPages > 1 && <Pagination page={page} totalPages={list.data.totalPages} onChange={setPage} />}
    </div>
  )
}

export default BrokerageHistoryPage
