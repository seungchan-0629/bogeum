// [관리자 페이지] 위험 매물 — /admin/risk-properties
// 위험 매물을 조회하고, 임차인에게 숨기거나 임대인·중개사에게 수정·삭제를 요청한다
// 요구사항: RISK_004 (조회, 주소 검색), RISK_005 (숨김, 수정·삭제 요청)
// API:
// - 목록: GET /api/v1/admin/risk-properties?target=&address=&page=&size= (BE-064)
// - 숨김: PUT/PATCH /api/v1/admin/properties/{propertyId}/hide (BE-065)
// - 수정·삭제 요청: POST /api/v1/admin/properties/{propertyId}/change-requests (BE-066)
import { useState } from 'react'
import PageHeader from '../../../components/common/PageHeader.jsx'
import DataTable from '../../../components/common/DataTable.jsx'
import StatusBadge from '../../../components/common/StatusBadge.jsx'
import SearchInput from '../../../components/common/SearchInput.jsx'
import Pagination from '../../../components/common/Pagination.jsx'

// TODO: GET /api/v1/admin/risk-properties (BE-064) 응답으로 교체
const SAMPLE_RISK_PROPERTIES = [
  { propertyId: 7, title: '신림동 빌라', reasonType: '근저당 과다', reasonTone: 'red', detail: '채권최고액이 시세의 90%', evidence: '등기부등본', registeredAt: '10.20', isHidden: true },
  { propertyId: 8, title: '화곡동 다세대', reasonType: '허위 매물 의심', reasonTone: 'amber', detail: '실제 사진과 다름 신고 2건', evidence: '신고 내역', registeredAt: '10.18', isHidden: false },
]

function AdminRiskPropertyPage() {
  const [properties, setProperties] = useState(SAMPLE_RISK_PROPERTIES)
  const [page, setPage] = useState(1)

  // TODO: PUT/PATCH /api/v1/admin/properties/{propertyId}/hide (BE-065, 요청: { hidden }) 성공 후 반영
  const handleToggleHidden = (propertyId) => {
    setProperties((prev) => prev.map((item) => (item.propertyId === propertyId ? { ...item, isHidden: !item.isHidden } : item)))
  }

  // TODO: POST /api/v1/admin/properties/{propertyId}/change-requests (BE-066) — 요청 내용 입력 창은 다음 단계
  const handleChangeRequest = () => {}

  const columns = [
    { label: '매물', render: (row) => <strong>{row.title}</strong> },
    { label: '사유 유형', render: (row) => <StatusBadge tone={row.reasonTone}>{row.reasonType}</StatusBadge> },
    { label: '상세 내용', render: (row) => row.detail },
    { label: '근거 자료', render: (row) => <button type="button" className="font-medium text-blue-700 hover:underline">{row.evidence}</button> },
    { label: '등록일', render: (row) => row.registeredAt },
    { label: '노출', render: (row) => (row.isHidden ? <StatusBadge tone="gray">숨김</StatusBadge> : <StatusBadge tone="green">노출 중</StatusBadge>) },
    {
      label: '',
      render: (row) => (
        <span className="flex gap-1.5">
          <button type="button" onClick={() => handleToggleHidden(row.propertyId)} className={row.isHidden ? 'btn-secondary btn-sm' : 'btn-primary btn-sm'}>
            {row.isHidden ? '숨김 해제' : '숨김'}
          </button>
          <button type="button" onClick={() => handleChangeRequest(row)} className="btn-secondary btn-sm">수정·삭제 요청</button>
        </span>
      ),
    },
  ]

  return (
    <>
      <PageHeader title="위험 매물" actions={<SearchInput label="주소 검색" placeholder="주소로 검색" />} />
      <DataTable columns={columns} rows={properties} rowKey="propertyId" emptyMessage="등록된 위험 매물이 없어요." />
      <p className="text-sm text-gray-600">숨김 처리하면 임차인에게만 안 보이고, 임대인·중개사에게는 수정이나 삭제를 요청할 수 있어요.</p>
      <Pagination page={page} totalPages={2} onChange={setPage} />
    </>
  )
}

export default AdminRiskPropertyPage
