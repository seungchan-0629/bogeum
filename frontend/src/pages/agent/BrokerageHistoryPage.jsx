// [페이지] 중개 이력 (중개사) — /agent/history
// 내가 중개한 계약과 매물을 본다
// 요구사항: AGENT_003
// API: GET /api/v1/agents/me/brokerages?cursor=&size= (BE-028)
import { Link } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import DataTable from '../../components/common/DataTable.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import { formatLeasePrice } from '../../utils/format.js'

// TODO: GET /api/v1/agents/me/brokerages (BE-028) 응답으로 교체
const SAMPLE_HISTORY = [
  { propertyId: 1, title: '역삼동 오피스텔', leaseType: 'MONTHLY', deposit: 1000, monthlyRent: 65, landlordName: '박임대', tenantName: '김보금', contractedAt: '2026.09.20', isContracted: true },
  { propertyId: 5, title: '흑석동 원룸', leaseType: 'MONTHLY', deposit: 300, monthlyRent: 50, landlordName: '이건물', tenantName: '오세입', contractedAt: '2026.07.02', isContracted: true },
  { propertyId: 2, title: '공덕동 아파트', leaseType: 'JEONSE', deposit: 32000, monthlyRent: 0, landlordName: '최집주', tenantName: null, contractedAt: null, isContracted: false },
]

const COLUMNS = [
  { label: '매물', render: (row) => <Link to={`/properties/${row.propertyId}`} className="font-bold text-blue-800 hover:underline">{row.title}</Link> },
  { label: '거래', render: (row) => formatLeasePrice(row) },
  { label: '임대인', render: (row) => row.landlordName },
  { label: '임차인', render: (row) => row.tenantName ?? '-' },
  { label: '계약일', render: (row) => row.contractedAt ?? '-' },
  { label: '상태', render: (row) => (row.isContracted ? <StatusBadge tone="green">계약 완료</StatusBadge> : <StatusBadge tone="blue">중개 중</StatusBadge>) },
]

function BrokerageHistoryPage() {
  return (
    <div className="page-container">
      <PageHeader title="중개 이력" description="내가 중개한 계약과 매물이에요." />
      <DataTable columns={COLUMNS} rows={SAMPLE_HISTORY} rowKey="propertyId" emptyMessage="아직 중개한 매물이 없어요." />
    </div>
  )
}

export default BrokerageHistoryPage
