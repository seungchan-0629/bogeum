// [관리자 페이지] 매물 관리 — /admin/properties
// 등록된 매물을 열람하고, 등기상 소유자와 등록자가 다른 매물을 검수한다
// 요구사항: PROPERTY_001
// API:
// - 목록: GET /api/v1/admin/properties?status=&keyword=&page=&size= (BE-034)
// - 상세: GET /api/v1/admin/properties/{propertyId} (BE-035)
// '등기 소유자 일치 여부'와 검수는 CLAUDE.md 역할 설명에만 있고 요구사항 시트·API 명세에는 없어 팀 확인 필요
import { useState } from 'react'
import PageHeader from '../../../components/common/PageHeader.jsx'
import DataTable from '../../../components/common/DataTable.jsx'
import StatusBadge from '../../../components/common/StatusBadge.jsx'
import SearchInput from '../../../components/common/SearchInput.jsx'
import Pagination from '../../../components/common/Pagination.jsx'
import { Tabs } from '../../../components/common/Tabs.jsx'

const FILTER_TABS = [
  { key: 'ALL', label: '전체' },
  { key: 'REVIEW', label: '검수 필요' },
  { key: 'HIDDEN', label: '숨김' },
]

// TODO: GET /api/v1/admin/properties (BE-034) 응답으로 교체. 탭은 status 파라미터로, 검색은 keyword로 보낸다
const SAMPLE_PROPERTIES = [
  { propertyId: 1, title: '역삼동 오피스텔', leaseType: '월세', landlordName: '박임대', agentName: '보금공인중개사', ownerMatched: true, isHidden: false, createdAt: '10.12' },
  { propertyId: 7, title: '신림동 빌라', leaseType: '전세', landlordName: '홍길동', agentName: '새집중개', ownerMatched: false, isHidden: false, createdAt: '10.20' },
  { propertyId: 4, title: '분당 아파트', leaseType: '매매', landlordName: '최집주', agentName: '-', ownerMatched: true, isHidden: false, createdAt: '10.22' },
  { propertyId: 8, title: '화곡동 다세대', leaseType: '월세', landlordName: '강임대', agentName: '새집중개', ownerMatched: true, isHidden: true, createdAt: '10.18' },
]

// TODO: [상세]·[검수]를 누르면 GET /api/v1/admin/properties/{propertyId} (BE-035) 로 상세를 연다. 상세 화면 구성은 다음 단계
const COLUMNS = [
  { label: '매물', render: (row) => <strong>{row.title}</strong> },
  { label: '거래', render: (row) => row.leaseType },
  { label: '임대인', render: (row) => row.landlordName },
  { label: '중개사', render: (row) => row.agentName },
  { label: '등기 소유자', render: (row) => (row.ownerMatched ? <StatusBadge tone="green">일치</StatusBadge> : <StatusBadge tone="red">불일치 · 검수 필요</StatusBadge>) },
  { label: '등록일', render: (row) => row.createdAt },
  { label: '', render: (row) => <button type="button" className={row.ownerMatched ? 'btn-secondary btn-sm' : 'btn-primary btn-sm'}>{row.ownerMatched ? '상세' : '검수'}</button> },
]

function AdminPropertyPage() {
  const [tab, setTab] = useState('ALL')
  const [page, setPage] = useState(1)
  const rows = SAMPLE_PROPERTIES.filter((row) => (tab === 'REVIEW' ? !row.ownerMatched : tab === 'HIDDEN' ? row.isHidden : true))

  return (
    <>
      <PageHeader title="매물 관리" actions={<SearchInput label="매물 검색" placeholder="주소, 중개사무소로 검색" />} />
      <Tabs label="매물 보기" items={FILTER_TABS} value={tab} onChange={setTab} />
      <DataTable columns={COLUMNS} rows={rows} rowKey="propertyId" />
      <Pagination page={page} totalPages={5} onChange={setPage} />
    </>
  )
}

export default AdminPropertyPage
