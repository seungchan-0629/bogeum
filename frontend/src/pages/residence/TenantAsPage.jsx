// [페이지] AS 요청 (임차인) — /my-home/as (내 집 관리의 두 번째 탭)
// 왼쪽: AS 요청 작성 / 오른쪽: 내가 보낸 요청 목록과 임대인 답변
// 요구사항: AS_001 (요청 + 사진 첨부), AS_003 (처리 상태 확인), NOTIFY_016 (답변 알림)
// API:
// - 요청: POST /api/v1/as-requests (BE-069) — 제목, 위치, 증상, 사진
// - 목록: GET /api/v1/as-requests?propertyId=&status=&cursor=&size= (BE-070)
import { useState } from 'react'
import clsx from 'clsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import FileDropzone from '../../components/common/FileDropzone.jsx'
import { Field, TextArea, TextInput } from '../../components/common/FormControls.jsx'
import { AS_STATUS } from '../../constants/residence.js'

// TODO: GET /api/v1/as-requests (BE-070) 응답으로 교체 (asId, title, status, createdAt, comments[])
const SAMPLE_AS_REQUESTS = [
  { asId: 1, title: '화장실 천장 누수', status: 'IN_PROGRESS', createdAt: '10.22', comments: [{ author: '임대인', date: '10.23', content: '수요일 오전에 수리 기사님이 방문할 예정이에요.' }] },
  { asId: 2, title: '보일러 온수 약함', status: 'CHECKING', createdAt: '10.20', comments: [] },
  { asId: 3, title: '창문 방충망 찢어짐', status: 'RECEIVED', createdAt: '10.21', comments: [] },
  { asId: 4, title: '현관 도어락 고장', status: 'DONE', createdAt: '09.30', comments: [] },
]

function TenantAsPage() {
  const [openAsId, setOpenAsId] = useState(1)

  // TODO: POST /api/v1/as-requests (BE-069) — multipart로 사진과 함께 보낸다. 성공하면 목록 새로고침
  const handleSubmit = (event) => {
    event.preventDefault()
  }

  return (
    <div className="grid items-start gap-5 lg:grid-cols-[420px_1fr]">
      <form onSubmit={handleSubmit} className="card flex flex-col gap-4">
        <h2 className="text-lg font-bold">AS 요청하기</h2>
        <Field label="제목" htmlFor="as-title">
          <TextInput id="as-title" placeholder="예: 화장실 천장에서 물이 새요" />
        </Field>
        <Field label="위치" htmlFor="as-location">
          <TextInput id="as-location" placeholder="예: 화장실" />
        </Field>
        <Field label="증상" htmlFor="as-content">
          <TextArea id="as-content" placeholder="언제부터 어떤 문제가 있는지 적어 주세요." />
        </Field>
        <FileDropzone title="사진 첨부" hint="최대 5장" multiple />
        <button type="submit" className="btn-primary">요청 보내기</button>
      </form>

      <section aria-labelledby="my-as-title" className="flex flex-col gap-3">
        <h2 id="my-as-title" className="text-lg font-bold">내 요청</h2>
        {SAMPLE_AS_REQUESTS.map((request) => {
          const isOpen = openAsId === request.asId
          return (
            <article key={request.asId} className={clsx('card flex flex-col gap-3 py-4', isOpen && 'border-blue-300')}>
              <button type="button" onClick={() => setOpenAsId(isOpen ? null : request.asId)} aria-expanded={isOpen} className="flex items-center justify-between gap-3 text-left">
                <span className="flex flex-col">
                  <span className="font-bold">{request.title}</span>
                  <span className="text-sm text-gray-500">{request.createdAt} 접수</span>
                </span>
                <StatusBadge tone={AS_STATUS[request.status].tone}>{AS_STATUS[request.status].label}</StatusBadge>
              </button>
              {isOpen && (
                request.comments.length > 0 ? (
                  request.comments.map((comment) => (
                    <p key={comment.date} className="rounded-xl bg-slate-50 px-4 py-3 text-sm">
                      <strong>{comment.author}</strong> · {comment.date}
                      <span className="mt-1 block">{comment.content}</span>
                    </p>
                  ))
                ) : (
                  <p className="text-sm text-gray-500">아직 임대인 답변이 없어요.</p>
                )
              )}
            </article>
          )
        })}
      </section>
    </div>
  )
}

export default TenantAsPage
