// [페이지] 내 매물 (임대인) — /my-properties
// 중개를 신청한 매물과 진행 상태를 보고, 수정·삭제한다
// 요구사항: PROPERTY_006 (수정 → /my-properties/:propertyId/edit), PROPERTY_007 (삭제, 확인 창)
// API:
// - 목록: 임대인 본인 매물 목록 API가 명세에 없어 백엔드와 협의 필요
// - 삭제: DELETE /api/v1/properties/{propertyId} (BE-022)
import { useState } from 'react'
import { Link } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import DataTable from '../../components/common/DataTable.jsx'
import ConfirmModal from '../../components/common/ConfirmModal.jsx'
import { formatLeasePrice } from '../../utils/format.js'

// 진행 상태: 수락 대기 → 공개 중 → 계약 완료. 상태 값 이름은 백엔드와 협의 필요
const PROPERTY_STATUS = {
  WAITING: { label: '수락 대기', tone: 'amber' },
  OPEN: { label: '공개 중', tone: 'blue' },
  CONTRACTED: { label: '계약 완료', tone: 'green' },
}

const SAMPLE_MY_PROPERTIES = [
  { propertyId: 1, title: '역삼동 오피스텔', address: '서울 강남구 역삼동 123-4', leaseType: 'MONTHLY', deposit: 1000, monthlyRent: 65, agentName: '보금공인중개사', status: 'CONTRACTED' },
  { propertyId: 2, title: '공덕동 아파트', address: '서울 마포구 공덕동 45', leaseType: 'JEONSE', deposit: 32000, monthlyRent: 0, agentName: '한강부동산', status: 'OPEN' },
  { propertyId: 4, title: '분당 아파트', address: '경기 성남시 분당구 정자동 7', leaseType: 'SALE', salePrice: 95000, agentName: '지정 안 함 (랜덤 배정)', status: 'WAITING' },
]

function MyPropertyListPage() {
  const [properties, setProperties] = useState(SAMPLE_MY_PROPERTIES)
  const [deleteTarget, setDeleteTarget] = useState(null)

  // TODO: DELETE /api/v1/properties/{propertyId} (BE-022) 성공 후 목록에서 뺀다
  // 삭제되면 관련 채팅방은 읽기 전용(CHAT_009), 관심 등록한 임차인에게 알림(NOTIFY_020)은 서버가 처리
  const handleDelete = () => {
    setProperties((prev) => prev.filter((item) => item.propertyId !== deleteTarget.propertyId))
    setDeleteTarget(null)
  }

  const columns = [
    {
      label: '매물',
      render: (row) => (
        <span className="flex flex-col">
          <strong>{row.title}</strong>
          <span className="text-gray-500">{row.address}</span>
        </span>
      ),
    },
    { label: '거래', render: (row) => formatLeasePrice(row) },
    { label: '담당 중개사', render: (row) => row.agentName },
    { label: '상태', render: (row) => <StatusBadge tone={PROPERTY_STATUS[row.status].tone}>{PROPERTY_STATUS[row.status].label}</StatusBadge> },
    {
      label: '관리',
      render: (row) =>
        row.status === 'CONTRACTED' ? (
          <Link to={`/properties/${row.propertyId}`} className="btn-secondary btn-sm">상세</Link>
        ) : (
          <span className="flex gap-1.5">
            <Link to={`/my-properties/${row.propertyId}/edit`} className="btn-secondary btn-sm">수정</Link>
            <button type="button" onClick={() => setDeleteTarget(row)} className="btn-secondary btn-sm">삭제</button>
          </span>
        ),
    },
  ]

  return (
    <div className="page-container">
      <PageHeader
        title="내 매물"
        description="중개를 신청한 매물과 진행 상태를 확인해요."
        actions={<Link to="/my-properties/new" className="btn-primary">+ 중개 신청</Link>}
      />
      <DataTable columns={columns} rows={properties} rowKey="propertyId" emptyMessage="아직 중개를 신청한 매물이 없어요." />

      {deleteTarget && (
        <ConfirmModal
          title="이 매물을 삭제할까요?"
          description={`${deleteTarget.title} — 삭제하면 관련 채팅방은 읽기 전용이 되고, 관심 등록한 임차인에게 알림이 가요.`}
          confirmLabel="삭제"
          onConfirm={handleDelete}
          onClose={() => setDeleteTarget(null)}
        />
      )}
    </div>
  )
}

export default MyPropertyListPage
