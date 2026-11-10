// [페이지] 내 계약 관리 (중개사 전용 메뉴) — /agent/contracts
// 수락한 매물마다 진행 단계(중개 수락 → 매물 공개 → 계약 완료)를 보여주고 다음 할 일로 보낸다
// 요구사항: PROPERTY_010 (매물 상세 정보·사진 업로드), PROPERTY_011 (계약 정보 업로드)
// API: 수락한 신청 목록 GET /property/brokerage-requests/received?status=ACCEPTED&page= (PROPERTY_012) — 한 페이지 5개
//      계약 정보 등록(#183)이 생기면 계약 상태를 함께 주는 전용 목록 API로 바꾼다
import { useState } from 'react'
import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import PageHeader from '../../components/common/PageHeader.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import Pagination from '../../components/common/Pagination.jsx'
import { formatLeasePrice } from '../../utils/format.js'
import { getReceivedBrokerageRequests } from '../../api/property/index.js'

const STEPS = ['중개 수락', '매물 공개', '계약 완료']
// 매물 상태(PropertyStatus)별 진행 단계(1~3). 공개 전(REQUESTED)은 임시 저장 여부와 관계없이 1단계다
const STEP_BY_STATUS = { REQUESTED: 1, ACTIVE: 2, CONTRACTED: 3 }
// 관리자가 숨겼거나 삭제된 매물은 진행할 일이 없어 단계와 버튼 대신 표시만 한다
const CLOSED_STATUS_LABELS = { HIDDEN: '관리자 숨김', DELETED: '삭제됨' }

// "2026-11-06T19:47:48" → "11.06"
const formatDate = (dateTime) => dateTime.slice(5, 10).replace('-', '.')

// 목록 API는 원 단위, formatLeasePrice는 만원 단위라서 바꿔서 넘긴다
const toManwonPrice = (request) => ({
  leaseType: request.leaseType,
  deposit: request.deposit / 10000,
  monthlyRent: request.monthlyRent / 10000,
})

// 공개된 매물은 수정할 수 없어 정보 올리기 화면이 읽기 전용으로 열린다
function NextAction({ propertyId, step }) {
  if (step === 1) {
    return <Link to={`/agent/properties/${propertyId}/edit`} className="btn-secondary btn-sm">매물 정보 올리기</Link>
  }
  if (step === 2) {
    return (
      <span className="flex gap-2">
        <Link to={`/agent/properties/${propertyId}/edit`} className="btn-secondary btn-sm">매물 정보 보기</Link>
        <Link to={`/agent/properties/${propertyId}/contract`} className="btn-secondary btn-sm">계약서 올리기</Link>
      </span>
    )
  }
  return <Link to={`/agent/properties/${propertyId}/contract`} className="btn-secondary btn-sm">계약 정보 보기</Link>
}

function StepProgress({ title, step }) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex justify-between gap-3 text-sm">
        <span className="font-bold">{STEPS[step - 1]}</span>
        <span className="text-gray-600">
          {step} / {STEPS.length}단계{step < STEPS.length && ` · 다음: ${STEPS[step]}`}
        </span>
      </div>
      <div
        role="progressbar"
        aria-label={`${title} 진행 단계`}
        aria-valuemin={1}
        aria-valuemax={STEPS.length}
        aria-valuenow={step}
        className="h-1.5 overflow-hidden rounded-full bg-gray-100"
      >
        <div className="h-full bg-blue-600" style={{ width: `${(step / STEPS.length) * 100}%` }} />
      </div>
    </div>
  )
}

function AgentContractListPage() {
  const [page, setPage] = useState(1)
  // 중개 신청 화면의 '수락한 신청' 탭과 같은 key라서, 거기서 수락하면 이 목록도 다시 불러온다
  const list = useQuery({
    queryKey: ['brokerage-requests', 'ACCEPTED', page],
    queryFn: ({ signal }) => getReceivedBrokerageRequests('ACCEPTED', page, signal),
  })
  const requests = list.data?.requests ?? []

  return (
    <div className="page-container">
      <PageHeader title="내 계약 관리" description="수락한 매물의 진행 단계를 관리해요." />
      {list.isPending && <p role="status">수락한 매물을 불러오고 있어요.</p>}
      {list.isError && <p role="alert">수락한 매물을 불러오지 못했어요.</p>}
      {list.isSuccess && (
        <ul className="flex flex-col gap-3">
          {requests.map((request) => {
            const title = `${request.roadAddress} ${request.detailAddress ?? ''}`.trim()
            const closedLabel = CLOSED_STATUS_LABELS[request.propertyStatus]
            const step = STEP_BY_STATUS[request.propertyStatus]
            return (
              <li key={request.brokerageRequestId} className="card flex flex-col gap-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="flex flex-col">
                    <span className="flex items-center gap-2">
                      <strong className="text-[17px]">{title}</strong>
                      {closedLabel && <StatusBadge tone="gray">{closedLabel}</StatusBadge>}
                    </span>
                    <span className="text-sm text-gray-600">{formatLeasePrice(toManwonPrice(request))}</span>
                    <span className="text-sm text-gray-500">
                      임대인 {request.landlordName} · {formatDate(request.acceptedAt)} 수락
                    </span>
                  </div>
                  {step && <NextAction propertyId={request.propertyId} step={step} />}
                </div>
                {step && <StepProgress title={title} step={step} />}
              </li>
            )
          })}
          {requests.length === 0 && (
            <li className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-gray-300 px-6 py-10 text-center text-gray-600">
              수락한 매물이 없어요. 중개 신청에서 수락하면 여기에 나와요.
              <Link to="/agent/requests" className="btn-secondary btn-sm">중개 신청 보기</Link>
            </li>
          )}
        </ul>
      )}
      {list.data?.totalPages > 1 && <Pagination page={page} totalPages={list.data.totalPages} onChange={setPage} />}
    </div>
  )
}

export default AgentContractListPage
