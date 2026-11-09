// [페이지] 중개 신청 (중개사) — /agent/requests
// 임대인이 보낸 중개 신청을 보고 수락한다. 수락하면 임대인과의 채팅방이 생긴다 (CHAT_002, BE-037 서버 처리)
// 요구사항: PROPERTY_008 (중개 신청 수락), PROPERTY_012 (중개 신청 목록), NOTIFY_022 (신청 알림)
// API:
// - 목록: GET /property/brokerage-requests/received?status=&page= (PROPERTY_012) — 상태별, 한 페이지 5개
// - 수락: POST /property/brokerage-requests/{brokerageRequestId}/accept (BE-024)
import { useState } from 'react'
import { Link } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import PageHeader from '../../components/common/PageHeader.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import PhotoPlaceholder from '../../components/common/PhotoPlaceholder.jsx'
import Pagination from '../../components/common/Pagination.jsx'
import { Tabs } from '../../components/common/Tabs.jsx'
import { formatLeasePrice } from '../../utils/format.js'
import { acceptBrokerageRequest, getReceivedBrokerageRequests } from '../../api/property/index.js'

const EMPTY_MESSAGES = { REQUESTED: '새 중개 신청이 없어요.', ACCEPTED: '수락한 중개 신청이 없어요.' }

// "2026-11-06T19:47:48" → "11.06"
const formatDate = (dateTime) => dateTime.slice(5, 10).replace('-', '.')

// 목록 API는 원 단위, formatLeasePrice는 만원 단위라서 바꿔서 넘긴다
const toManwonPrice = (request) => ({
  leaseType: request.leaseType,
  deposit: request.deposit / 10000,
  monthlyRent: request.monthlyRent / 10000,
})

function useBrokerageRequests(status, page) {
  return useQuery({
    queryKey: ['brokerage-requests', status, page],
    queryFn: ({ signal }) => getReceivedBrokerageRequests(status, page, signal),
  })
}

function AgentRequestListPage() {
  const queryClient = useQueryClient()
  const [tab, setTab] = useState('REQUESTED')
  const [page, setPage] = useState(1)
  const list = useBrokerageRequests(tab, page)
  // 탭 이름의 '새 신청' 개수는 수락한 신청 탭에 있을 때도 보여야 해서 따로 조회한다
  const requestedFirstPage = useBrokerageRequests('REQUESTED', 1)
  const requests = list.data?.requests ?? []

  const handleTabChange = (nextTab) => {
    setTab(nextTab)
    setPage(1)
  }

  const handleAccept = async (brokerageRequestId) => {
    try {
      await acceptBrokerageRequest(brokerageRequestId)
    } catch (error) {
      window.alert(error.response?.data?.message || '서버에 연결할 수 없습니다.')
      return
    }
    // 수락한 신청은 '새 신청'에서 빠지고 '수락한 신청'으로 옮겨 가므로 두 탭을 모두 다시 불러온다
    await queryClient.invalidateQueries({ queryKey: ['brokerage-requests'] })
  }

  return (
    <div className="page-container">
      <PageHeader title="중개 신청" description="임대인이 보낸 중개 신청이에요. 수락하면 임대인과의 채팅방이 생겨요." />
      <Tabs
        label="중개 신청 보기"
        items={[
          { key: 'REQUESTED', label: `새 신청 ${requestedFirstPage.data?.totalElements ?? ''}` },
          { key: 'ACCEPTED', label: '수락한 신청' },
        ]}
        value={tab}
        onChange={handleTabChange}
      />
      {list.isPending && <p role="status">중개 신청을 불러오고 있어요.</p>}
      {list.isError && <p role="alert">중개 신청을 불러오지 못했어요.</p>}
      {list.isSuccess && (
        <ul className="flex flex-col gap-3">
          {requests.map((request) => (
            <li key={request.brokerageRequestId} className="card flex flex-wrap items-center justify-between gap-4">
              <div className="flex items-center gap-4">
                <PhotoPlaceholder className="size-16 shrink-0" />
                <div className="flex flex-col">
                  <span className="flex items-center gap-2">
                    <strong className="text-[17px]">{request.roadAddress} {request.detailAddress}</strong>
                    {request.isDesignated && <StatusBadge tone="blue">나를 지정</StatusBadge>}
                  </span>
                  <span>{formatLeasePrice(toManwonPrice(request))}</span>
                  <span className="text-sm text-gray-500">
                    임대인 {request.landlordName} · {formatDate(request.createdAt)} 신청
                    {request.acceptedAt && ` · ${formatDate(request.acceptedAt)} 수락`}
                    {!request.isDesignated && ' · 랜덤 배정'}
                  </span>
                </div>
              </div>
              {request.brokerageStatus === 'REQUESTED' ? (
                <button type="button" onClick={() => handleAccept(request.brokerageRequestId)} className="btn-secondary">수락하기</button>
              ) : (
                <Link to="/agent/contracts" className="btn-secondary">내 계약 관리에서 보기</Link>
              )}
            </li>
          ))}
          {requests.length === 0 && (
            <li className="rounded-xl border border-dashed border-gray-300 px-6 py-10 text-center text-gray-600">{EMPTY_MESSAGES[tab]}</li>
          )}
        </ul>
      )}
      {list.data?.totalPages > 1 && <Pagination page={page} totalPages={list.data.totalPages} onChange={setPage} />}
    </div>
  )
}

export default AgentRequestListPage
