// [페이지] 중개 신청 (중개사) — /agent/requests
// 임대인이 보낸 중개 신청을 보고 수락한다. 수락하면 임대인과의 채팅방이 생긴다 (CHAT_002, BE-037 서버 처리)
// 요구사항: PROPERTY_008 (중개 신청 수락), NOTIFY_022 (신청 알림)
// API:
// - 목록: GET /api/v1/agents/me/brokerage-requests?status=&cursor=&size= (BE-023)
// - 수락: POST /api/v1/brokerage-requests/{requestId}/accept (BE-024)
import { useState } from 'react'
import { Link } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import PhotoPlaceholder from '../../components/common/PhotoPlaceholder.jsx'
import { Tabs } from '../../components/common/Tabs.jsx'
import { formatLeasePrice } from '../../utils/format.js'

// TODO: GET /api/v1/agents/me/brokerage-requests (BE-023) 응답으로 교체
// - isDesignated: 임대인이 나를 지정했는지 (아니면 랜덤 배정). 필드 이름은 백엔드와 협의 필요
const SAMPLE_REQUESTS = [
  { requestId: 1, title: '역삼동 오피스텔', leaseType: 'MONTHLY', deposit: 1000, monthlyRent: 65, address: '서울 강남구 역삼동 123-4', landlordName: '박임대', requestedAt: '10.23', isDesignated: true, status: 'WAITING' },
  { requestId: 2, title: '분당 아파트', leaseType: 'SALE', salePrice: 95000, address: '경기 성남시 분당구 정자동 7', landlordName: '최집주', requestedAt: '10.22', isDesignated: false, status: 'WAITING' },
  { requestId: 3, title: '봉천동 빌라', leaseType: 'MONTHLY', deposit: 500, monthlyRent: 45, address: '서울 관악구 봉천동 88', landlordName: '정소유', requestedAt: '10.21', isDesignated: false, status: 'WAITING' },
  { requestId: 4, title: '공덕동 아파트', leaseType: 'JEONSE', deposit: 32000, monthlyRent: 0, address: '서울 마포구 공덕동 45', landlordName: '최집주', requestedAt: '10.10', isDesignated: true, status: 'ACCEPTED' },
]

function AgentRequestListPage() {
  const [requests, setRequests] = useState(SAMPLE_REQUESTS)
  const [tab, setTab] = useState('WAITING')
  const waitingCount = requests.filter((request) => request.status === 'WAITING').length
  const visibleRequests = requests.filter((request) => request.status === tab)

  // TODO: POST /api/v1/brokerage-requests/{requestId}/accept (BE-024) 성공 후 상태 반영
  const handleAccept = (requestId) => {
    setRequests((prev) => prev.map((request) => (request.requestId === requestId ? { ...request, status: 'ACCEPTED' } : request)))
  }

  return (
    <div className="page-container">
      <PageHeader title="중개 신청" description="임대인이 보낸 중개 신청이에요. 수락하면 임대인과의 채팅방이 생겨요." />
      <Tabs
        label="중개 신청 보기"
        items={[
          { key: 'WAITING', label: `새 신청 ${waitingCount}` },
          { key: 'ACCEPTED', label: '수락한 신청' },
        ]}
        value={tab}
        onChange={setTab}
      />
      <ul className="flex flex-col gap-3">
        {visibleRequests.map((request) => (
          <li key={request.requestId} className="card flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-4">
              <PhotoPlaceholder className="size-16 shrink-0" />
              <div className="flex flex-col">
                <span className="flex items-center gap-2">
                  <strong className="text-[17px]">{request.title}</strong>
                  {request.isDesignated && <StatusBadge tone="blue">나를 지정</StatusBadge>}
                </span>
                <span>{formatLeasePrice(request)}</span>
                <span className="text-sm text-gray-500">
                  {request.address} · 임대인 {request.landlordName} · {request.requestedAt} 신청{!request.isDesignated && ' · 랜덤 배정'}
                </span>
              </div>
            </div>
            {request.status === 'WAITING' ? (
              <button type="button" onClick={() => handleAccept(request.requestId)} className="btn-secondary">수락하기</button>
            ) : (
              <Link to="/agent/contracts" className="btn-secondary">내 계약 관리에서 보기</Link>
            )}
          </li>
        ))}
        {visibleRequests.length === 0 && (
          <li className="rounded-xl border border-dashed border-gray-300 px-6 py-10 text-center text-gray-600">새 중개 신청이 없어요.</li>
        )}
      </ul>
    </div>
  )
}

export default AgentRequestListPage
