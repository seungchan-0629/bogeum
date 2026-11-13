// [페이지] 내 매물 (임대인) — /my-properties
// 중개를 신청한 매물과 진행 상태를 보고, 수정·삭제한다
// 요구사항: PROPERTY_013 (목록), PROPERTY_006 (수정 → /my-properties/:propertyId/edit, 계약 완료는 월세·AS로), PROPERTY_007 (삭제, 확인 창)
// API:
// - 목록: GET /property/mine?page= — 최근 신청 순서로 10개씩, 삭제한 매물은 빠지고 숨긴 매물은 남는다
// - 삭제: DELETE /api/v1/properties/{propertyId} (BE-022)
import { useState } from 'react'
import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import PageHeader from '../../components/common/PageHeader.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import DataTable from '../../components/common/DataTable.jsx'
import Pagination from '../../components/common/Pagination.jsx'
import ConfirmModal from '../../components/common/ConfirmModal.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { getMyProperties } from '../../api/property/index.js'
import { formatLeasePrice } from '../../utils/format.js'

// 진행 단계: 서버가 주는 중개 신청 상태(중개사가 수락했는지)와 매물 상태(공개·계약했는지)를 합쳐 하나로 보여 준다
// 숨김은 관리자가 위험 매물로 가린 것이라 중개사의 내 계약 관리처럼 주황으로 구분한다
const STEPS = {
  WAITING: { label: '수락 대기', tone: 'gray' },
  PREPARING: { label: '매물 준비 중', tone: 'blue' },
  ACTIVE: { label: '공개 중', tone: 'green' },
  CONTRACTED: { label: '계약 완료', tone: 'gray' },
  HIDDEN: { label: '숨김', tone: 'amber' },
}

function toStep(row) {
  if (row.propertyStatus === 'HIDDEN') return 'HIDDEN'
  if (row.propertyStatus === 'CONTRACTED') return 'CONTRACTED'
  if (row.propertyStatus === 'ACTIVE') return 'ACTIVE'
  // 공개 전(REQUESTED): 중개사가 수락했으면 사진·상세 정보를 올리는 중이다
  return row.brokerageStatus === 'ACCEPTED' ? 'PREPARING' : 'WAITING'
}

// 목록 API는 원 단위, formatLeasePrice는 만원 단위라서 바꿔서 넘긴다
const toManwonPrice = (row) => ({
  leaseType: row.leaseType,
  deposit: row.deposit / 10000,
  monthlyRent: row.monthlyRent / 10000,
})

// "2026-11-13T10:00:00" → "2026.11.13"
const formatDate = (dateTime) => dateTime.slice(0, 10).replaceAll('-', '.')

const addressOf = (row) => `${row.roadAddress} ${row.detailAddress ?? ''}`.trim()

function MyPropertyListPage() {
  const user = useAuthStore((state) => state.user)
  const [page, setPage] = useState(1)
  // 다른 화면(중개 신청 등)에 갔다 오면 새로 불러와서, 방금 신청한 매물이 맨 위에 나온다
  const list = useQuery({
    queryKey: ['my-properties', user?.userId, page],
    queryFn: ({ signal }) => getMyProperties(page, signal),
  })
  const rows = list.data?.properties ?? []
  const hasHidden = rows.some((row) => row.propertyStatus === 'HIDDEN')
  const [removedIds, setRemovedIds] = useState(() => new Set())
  const [deleteTarget, setDeleteTarget] = useState(null)

  // TODO: DELETE /api/v1/properties/{propertyId} (BE-022) 성공 후 목록을 다시 불러온다 (PROPERTY_007)
  // 지금은 삭제 API가 없어 화면에서만 뺀다. 새로고침하면 다시 보인다
  // 삭제되면 관련 채팅방은 읽기 전용(CHAT_009), 관심 등록한 임차인에게 알림(NOTIFY_020)은 서버가 처리
  const handleDelete = () => {
    setRemovedIds((prev) => new Set(prev).add(deleteTarget.propertyId))
    setDeleteTarget(null)
  }

  const columns = [
    { label: '매물', render: (row) => <strong className="break-keep">{addressOf(row)}</strong> },
    { label: '거래', render: (row) => formatLeasePrice(toManwonPrice(row)) },
    {
      label: '담당 중개사',
      render: (row) => (
        <span className="flex flex-col">
          {/* 사무소 정보가 없는 중개사는 이름만 보여 준다 */}
          {row.officeName && <span className="font-medium">{row.officeName}</span>}
          <span className="text-gray-600">
            {row.agentName} {row.isDesignated ? '(희망)' : '(배정)'}
          </span>
        </span>
      ),
    },
    {
      label: '진행 상태',
      render: (row) => {
        const step = STEPS[toStep(row)]
        return <StatusBadge tone={step.tone}>{step.label}</StatusBadge>
      },
    },
    { label: '신청일', render: (row) => <span className="whitespace-nowrap">{formatDate(row.requestedAt)}</span> },
    {
      label: '관리',
      // 계약이 끝난 매물은 고칠 수 없고 이제 월세와 입주·AS를 관리한다
      // TODO: 월세 수납 현황(RENT_003)·AS가 매물별로 보이게 되면 ?propertyId=를 붙여 이 매물만 보여 준다
      render: (row) =>
        row.propertyStatus === 'CONTRACTED' ? (
          <span className="flex gap-1.5">
            <Link to="/rent/collections" className="btn-secondary btn-sm whitespace-nowrap">월세 관리</Link>
            <Link to="/care" className="btn-secondary btn-sm whitespace-nowrap">입주·AS</Link>
          </span>
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
        description={list.isSuccess ? `중개를 신청한 매물 ${list.data.totalElements}건의 진행 상태예요.` : '중개를 신청한 매물과 진행 상태를 확인해요.'}
        actions={<Link to="/my-properties/new" className="btn-primary">+ 중개 신청</Link>}
      />
      {list.isPending && <p role="status">내 매물을 불러오고 있어요.</p>}
      {list.isError && <p role="alert">내 매물을 불러오지 못했어요.</p>}
      {list.isSuccess && (
        <DataTable
          columns={columns}
          rows={rows.filter((row) => !removedIds.has(row.propertyId))}
          rowKey="propertyId"
          emptyMessage={
            <span className="flex flex-col items-center gap-3">
              아직 중개를 신청한 매물이 없어요.
              <Link to="/my-properties/new" className="btn-primary btn-sm">+ 중개 신청</Link>
            </span>
          }
        />
      )}
      {/* 숨김은 관리자가 확인 중인 매물이다. 임대인이 고치거나 지울 수 있게 목록에 남기고 안내한다 (RISK_005) */}
      {hasHidden && (
        <p className="text-sm text-amber-800">숨김 매물은 관리자가 확인 중인 매물이에요. 안내에 따라 수정하거나 삭제해 주세요.</p>
      )}
      {list.data?.totalPages > 1 && <Pagination page={page} totalPages={list.data.totalPages} onChange={setPage} />}

      {deleteTarget && (
        <ConfirmModal
          title="이 매물을 삭제할까요?"
          description={`${addressOf(deleteTarget)} — 삭제하면 관련 채팅방은 읽기 전용이 되고, 관심 등록한 임차인에게 알림이 가요.`}
          confirmLabel="삭제"
          onConfirm={handleDelete}
          onClose={() => setDeleteTarget(null)}
        />
      )}
    </div>
  )
}

export default MyPropertyListPage
