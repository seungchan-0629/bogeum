// [페이지] 받은 AS 요청 (임대인) — /care/as (입주·AS의 첫 탭)
// 왼쪽: 요청 목록 / 오른쪽: 선택한 요청의 내용, 사진, 처리 상태 변경, 답변(댓글)
// 요구사항: AS_002 (조회), AS_003 (답변 + 처리 상태), NOTIFY_015 (접수 알림)
// API:
// - 목록: GET /as-requests/received?page= (AS_002)
// - 상세: GET /as-requests/{asRequestId} (AS_002) — 사진은 10분짜리 조회 주소(url)로 온다
// - 답변: POST /as-requests/{asRequestId}/comments (AS_003) — 등록하면 임차인에게 알림(NOTIFY_016)
// - 상태 변경: PATCH /as-requests/{asRequestId}/status (AS_003)
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import PhotoPlaceholder from '../../components/common/PhotoPlaceholder.jsx'
import { SelectInput, TextArea } from '../../components/common/FormControls.jsx'
import { AS_STATUS } from '../../constants/residence.js'
import { createAsComment, getAsRequest, getReceivedAsRequests } from '../../api/residence/index.js'

const STATUS_OPTIONS = Object.entries(AS_STATUS).map(([value, { label }]) => ({ value, label }))
// 서버 검증(@Size(max = 1000))과 같은 길이
const MAX_REPLY_LENGTH = 1000

// "2026-11-06T19:47:48" → "11.06"
const formatDate = (dateTime) => dateTime.slice(5, 10).replace('-', '.')

// AS_003 답변 등록. 등록하면 상세를 다시 불러와 답변 목록에 반영한다
function AsReplyForm({ asRequestId }) {
  const queryClient = useQueryClient()
  const [content, setContent] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (!content.trim()) {
      setErrorMessage('답변 내용을 입력해 주세요.')
      return
    }
    setErrorMessage('')
    setIsSubmitting(true)
    try {
      await createAsComment(asRequestId, content)
      setContent('')
      await queryClient.invalidateQueries({ queryKey: ['as-request', asRequestId] })
    } catch (error) {
      setErrorMessage(error.response?.data?.message || '답변을 등록하지 못했어요. 다시 시도해 주세요.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-2">
      <label htmlFor="as-reply" className="text-sm font-medium text-gray-700">조치 사항 답변</label>
      <TextArea
        id="as-reply"
        rows={3}
        maxLength={MAX_REPLY_LENGTH}
        value={content}
        onChange={(event) => setContent(event.target.value)}
        placeholder="예: 수요일 오전에 수리 기사님이 방문할 예정이에요."
      />
      {errorMessage && <p role="alert" className="text-sm text-red-600">{errorMessage}</p>}
      <button type="submit" disabled={isSubmitting} className="btn-primary self-end">
        {isSubmitting ? '등록 중...' : '답변 등록'}
      </button>
    </form>
  )
}

function LandlordAsPage() {
  const [selectedId, setSelectedId] = useState(null)
  // TODO: 한 페이지(5개)만 보여 준다. 다음 페이지 이동은 추후 추가
  const list = useQuery({
    queryKey: ['as-requests', 'received', 1],
    queryFn: ({ signal }) => getReceivedAsRequests(1, signal),
  })
  const requests = list.data?.requests ?? []
  const currentId = selectedId ?? requests[0]?.asRequestId
  const detail = useQuery({
    queryKey: ['as-request', currentId],
    queryFn: ({ signal }) => getAsRequest(currentId, signal),
    enabled: currentId != null,
  })
  const selected = detail.data

  // TODO: PATCH /as-requests/{asRequestId}/status (AS_003)
  const handleStatusChange = () => {}

  if (list.isPending) return <p role="status">받은 AS 요청을 불러오고 있어요.</p>
  if (list.isError) return <p role="alert">받은 AS 요청을 불러오지 못했어요.</p>
  if (requests.length === 0) return <p className="card text-gray-600">아직 받은 AS 요청이 없어요.</p>

  return (
    <div className="grid items-start gap-5 lg:grid-cols-[340px_1fr]">
      <ul aria-label="받은 AS 요청" className="flex flex-col gap-2.5">
        {requests.map((request) => (
          <li key={request.asRequestId}>
            <button
              type="button"
              onClick={() => setSelectedId(request.asRequestId)}
              aria-current={request.asRequestId === currentId}
              className={clsx('card flex w-full items-center justify-between gap-3 py-4 text-left', request.asRequestId === currentId && 'border-blue-400 bg-blue-50')}
            >
              <span className="flex min-w-0 flex-col">
                <span className="truncate font-bold">{request.content}</span>
                <span className="text-sm text-gray-500">{request.roadAddress} {request.detailAddress} · {formatDate(request.createdAt)}</span>
              </span>
              <StatusBadge tone={AS_STATUS[request.asStatus].tone}>{AS_STATUS[request.asStatus].label}</StatusBadge>
            </button>
          </li>
        ))}
      </ul>

      {detail.isPending && <p role="status" className="card">AS 요청 내용을 불러오고 있어요.</p>}
      {detail.isError && <p role="alert" className="card">AS 요청 내용을 불러오지 못했어요.</p>}
      {selected && (
        <article key={selected.asRequestId} className="card flex flex-col gap-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="flex flex-col">
              <h2 className="text-xl font-bold">{selected.roadAddress} {selected.detailAddress}</h2>
              <p className="text-sm text-gray-600">임차인 {selected.tenantName} · {formatDate(selected.createdAt)} 접수</p>
            </div>
            <label htmlFor="as-status" className="sr-only">처리 상태</label>
            <SelectInput id="as-status" options={STATUS_OPTIONS} defaultValue={selected.asStatus} onChange={handleStatusChange} size="sm" fullWidth={false} />
          </div>
          <p className="whitespace-pre-line">{selected.content}</p>
          {selected.images.length > 0 && (
            <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
              {selected.images.map((image, index) => (image.url ? (
                <a key={image.asRequestImageId} href={image.url} target="_blank" rel="noreferrer">
                  <img src={image.url} alt={`AS 사진 ${index + 1}`} className="h-24 w-full rounded-lg object-cover" />
                </a>
              ) : (
                // 저장소가 연결되지 않은 환경에서는 주소 없이 사진 정보만 온다
                <PhotoPlaceholder key={image.asRequestImageId} className="h-24" />
              )))}
            </div>
          )}
          {selected.comments.length > 0 && (
            <ul aria-label="등록한 답변" className="flex flex-col gap-2">
              {selected.comments.map((comment) => (
                <li key={comment.asCommentId} className="rounded-lg bg-gray-50 p-3">
                  <p className="text-sm text-gray-500">{comment.authorName} · {formatDate(comment.createdAt)}</p>
                  <p className="whitespace-pre-line">{comment.content}</p>
                </li>
              ))}
            </ul>
          )}
          <AsReplyForm asRequestId={selected.asRequestId} />
        </article>
      )}
    </div>
  )
}

export default LandlordAsPage
