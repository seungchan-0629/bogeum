// [페이지] 받은 AS 요청 (임대인) — /care/as (입주·AS의 첫 탭)
// 왼쪽: 요청 목록 / 오른쪽: 선택한 요청의 내용, 처리 상태 변경, 답변(댓글)
// 요구사항: AS_002 (조회), AS_003 (답변 + 처리 상태), NOTIFY_015 (접수 알림)
// API:
// - 목록: GET /api/v1/as-requests?propertyId=&status=&cursor=&size= (BE-070)
// - 답변: POST /api/v1/as-requests/{asId}/comments (BE-071) — 등록하면 임차인에게 알림(NOTIFY_016)
// - 상태 변경: PUT/PATCH /api/v1/as-requests/{asId}/status (BE-072)
import { useState } from 'react'
import clsx from 'clsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import PhotoPlaceholder from '../../components/common/PhotoPlaceholder.jsx'
import { SelectInput, TextArea } from '../../components/common/FormControls.jsx'
import { AS_STATUS } from '../../constants/residence.js'

const STATUS_OPTIONS = Object.entries(AS_STATUS).map(([value, { label }]) => ({ value, label }))

// TODO: GET /api/v1/as-requests (BE-070) 응답으로 교체
const SAMPLE_AS_REQUESTS = [
  { asId: 1, title: '화장실 천장 누수', propertyTitle: '역삼동 오피스텔', tenantName: '김보금', location: '화장실', content: '어제 밤부터 천장 등 주변에서 물이 떨어져요.', photoCount: 2, status: 'IN_PROGRESS', createdAt: '10.22' },
  { asId: 2, title: '보일러 온수 약함', propertyTitle: '역삼동 오피스텔', tenantName: '김보금', location: '보일러실', content: '온수가 미지근하게 나와요.', photoCount: 0, status: 'CHECKING', createdAt: '10.20' },
  { asId: 3, title: '창문 방충망 찢어짐', propertyTitle: '역삼동 오피스텔', tenantName: '김보금', location: '거실', content: '방충망 아래쪽이 찢어졌어요.', photoCount: 1, status: 'RECEIVED', createdAt: '10.21' },
]

function LandlordAsPage() {
  const [selectedId, setSelectedId] = useState(SAMPLE_AS_REQUESTS[0].asId)
  const selected = SAMPLE_AS_REQUESTS.find((request) => request.asId === selectedId)

  // TODO: PUT/PATCH /api/v1/as-requests/{asId}/status (BE-072)
  const handleStatusChange = () => {}

  // TODO: POST /api/v1/as-requests/{asId}/comments (BE-071)
  const handleReply = (event) => {
    event.preventDefault()
  }

  return (
    <div className="grid items-start gap-5 lg:grid-cols-[340px_1fr]">
      <ul aria-label="받은 AS 요청" className="flex flex-col gap-2.5">
        {SAMPLE_AS_REQUESTS.map((request) => (
          <li key={request.asId}>
            <button
              type="button"
              onClick={() => setSelectedId(request.asId)}
              aria-current={request.asId === selectedId}
              className={clsx('card flex w-full items-center justify-between gap-3 py-4 text-left', request.asId === selectedId && 'border-blue-400 bg-blue-50')}
            >
              <span className="flex flex-col">
                <span className="font-bold">{request.title}</span>
                <span className="text-sm text-gray-500">{request.propertyTitle} · {request.createdAt}</span>
              </span>
              <StatusBadge tone={AS_STATUS[request.status].tone}>{AS_STATUS[request.status].label}</StatusBadge>
            </button>
          </li>
        ))}
      </ul>

      <article key={selected.asId} className="card flex flex-col gap-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex flex-col">
            <h2 className="text-xl font-bold">{selected.title}</h2>
            <p className="text-sm text-gray-600">임차인 {selected.tenantName} · {selected.createdAt} 접수 · 위치: {selected.location}</p>
          </div>
          <label htmlFor="as-status" className="sr-only">처리 상태</label>
          <SelectInput id="as-status" options={STATUS_OPTIONS} defaultValue={selected.status} onChange={handleStatusChange} size="sm" fullWidth={false} />
        </div>
        <p>{selected.content}</p>
        {selected.photoCount > 0 && (
          <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
            {Array.from({ length: selected.photoCount }, (_, index) => (
              <PhotoPlaceholder key={index} className="h-24" />
            ))}
          </div>
        )}
        <form onSubmit={handleReply} className="flex flex-col gap-2">
          <label htmlFor="as-reply" className="text-sm font-medium text-gray-700">조치 사항 답변</label>
          <TextArea id="as-reply" rows={3} placeholder="예: 수요일 오전에 수리 기사님이 방문할 예정이에요." />
          <button type="submit" className="btn-primary self-end">답변 등록</button>
        </form>
      </article>
    </div>
  )
}

export default LandlordAsPage
