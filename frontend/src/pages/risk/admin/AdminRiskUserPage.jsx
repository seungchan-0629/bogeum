// [관리자 페이지] 위험 사용자 — /admin/risk-users
// 대상(임차인·임대인·중개사)별 위험 사용자 목록, 등록, 수정. 월세 연체로 생긴 '위험 정보 후보'도 안내한다
// 요구사항: RISK_001 (등록), RISK_002 (조회, 이름 검색, 페이지 방식), RISK_003 (수정 + 수정 사유), RENT_007·NOTIFY_002 (후보)
// API:
// - 목록: GET /api/v1/admin/risk-users?userType=&name=&page=&size= (BE-061)
// - 등록: POST /api/v1/admin/risk-users (BE-060)
// - 수정: PUT/PATCH /api/v1/admin/risk-users/{riskUserId} (BE-062) — 수정 사유 필수
// - 후보: GET /api/v1/admin/risk-candidates?page=&size= (BE-063)
import { useState } from 'react'
import PageHeader from '../../../components/common/PageHeader.jsx'
import DataTable from '../../../components/common/DataTable.jsx'
import StatusBadge from '../../../components/common/StatusBadge.jsx'
import SearchInput from '../../../components/common/SearchInput.jsx'
import Pagination from '../../../components/common/Pagination.jsx'
import { Tabs } from '../../../components/common/Tabs.jsx'

const USER_TYPE_TABS = [
  { key: 'TENANT', label: '임차인' },
  { key: 'LANDLORD', label: '임대인' },
  { key: 'AGENT', label: '중개사' },
]

// TODO: GET /api/v1/admin/risk-users (BE-061) 응답으로 교체. 탭은 userType 파라미터
const SAMPLE_RISK_USERS = [
  { riskUserId: 1, userType: 'TENANT', name: '김보금', reasonType: '월세 미납', reasonTone: 'red', detail: '8월분 미납, 58일 연체', evidence: '결제 내역', registeredAt: '10.20' },
  { riskUserId: 2, userType: 'TENANT', name: '이월세', reasonType: '연체 이력', reasonTone: 'amber', detail: '3회 연체 후 납부', evidence: '결제 내역', registeredAt: '09.11' },
  { riskUserId: 3, userType: 'LANDLORD', name: '홍길동', reasonType: '보증금 미지급', reasonTone: 'red', detail: '퇴실 후 2개월째 미반환', evidence: '임차인 신고서', registeredAt: '08.30' },
  { riskUserId: 4, userType: 'AGENT', name: '박새집', reasonType: '매물 이미지 미등록', reasonTone: 'amber', detail: '공개 매물 3건 사진 없음', evidence: '매물 목록', registeredAt: '10.02' },
]

function AdminRiskUserPage() {
  const [userType, setUserType] = useState('TENANT')
  const [page, setPage] = useState(1)
  const rows = SAMPLE_RISK_USERS.filter((row) => row.userType === userType)

  // TODO: 등록·수정 창(대상 선택, 사유 유형, 상세, 근거 자료, 수정 사유)은 다음 단계. 저장은 BE-060 / BE-062
  const handleCreate = () => {}
  const handleEdit = () => {}

  const columns = [
    { label: '이름', render: (row) => <strong>{row.name}</strong> },
    { label: '사유 유형', render: (row) => <StatusBadge tone={row.reasonTone}>{row.reasonType}</StatusBadge> },
    { label: '상세 내용', render: (row) => row.detail },
    { label: '근거 자료', render: (row) => <button type="button" className="font-medium text-blue-700 hover:underline">{row.evidence}</button> },
    { label: '등록일', render: (row) => row.registeredAt },
    { label: '', render: (row) => <button type="button" onClick={() => handleEdit(row)} className="btn-secondary btn-sm">수정</button> },
  ]

  return (
    <>
      <PageHeader title="위험 사용자" actions={<button type="button" onClick={handleCreate} className="btn-primary">+ 위험 사용자 등록</button>} />
      {/* TODO: GET /api/v1/admin/risk-candidates (BE-063) 의 건수. 후보 목록 화면은 다음 단계 */}
      <p className="rounded-xl border border-orange-200 bg-orange-50 px-4 py-3 text-orange-900">
        <strong>새 위험 정보 후보 1명</strong> — 월세 연체가 다음 달까지 이어진 임차인이에요.
      </p>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <Tabs label="대상" items={USER_TYPE_TABS} value={userType} onChange={setUserType} />
        <SearchInput label="이름 검색" placeholder="이름으로 검색" />
      </div>
      <DataTable columns={columns} rows={rows} rowKey="riskUserId" emptyMessage="등록된 위험 사용자가 없어요." />
      <Pagination page={page} totalPages={5} onChange={setPage} />
    </>
  )
}

export default AdminRiskUserPage
