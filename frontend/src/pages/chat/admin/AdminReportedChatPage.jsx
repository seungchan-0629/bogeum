// [관리자 페이지] 신고 대화 — /admin/chats
// 신고된 1:1 대화만 열람한다. 왼쪽: 신고 목록 / 오른쪽: AI 요약 + 원문 대화
// 요구사항: CHAT_001 (신고된 건만 열람), CHATBOT_009 (신고 건 AI 요약)
// API:
// - 원문: GET /api/v1/admin/chat/reports/{reportId}/messages?cursor=&size= (BE-047)
// - AI 요약: GET /api/v1/admin/chat/reports/{reportId}/summary (BE-115)
// - 신고 목록 API가 명세에 없어 백엔드와 협의 필요
import { useState } from 'react'
import clsx from 'clsx'
import PageHeader from '../../../components/common/PageHeader.jsx'

// TODO: 신고 목록 API 확정 후 교체
const SAMPLE_REPORTS = [
  { reportId: 1, reason: '허위 매물 신고', description: '화곡동 다세대 · 새집중개 ↔ 오세입', reportedAt: '10.23' },
  { reportId: 2, reason: '욕설 신고', description: '공덕동 아파트 · 한강부동산 ↔ 이월세', reportedAt: '10.19' },
]

// TODO: GET .../summary (BE-115) 응답으로 교체
const SAMPLE_SUMMARY = [
  '임차인이 방문 후 실제 매물이 사진과 다르다고 항의했어요.',
  '중개사는 "사진은 리모델링 전"이라고 답했어요.',
  '신고 사유(허위 매물)와 관련된 메시지 3건을 찾았어요.',
]

// TODO: GET .../messages (BE-047) 응답으로 교체 (보낸 사람, 내용, 보낸 시각)
const SAMPLE_MESSAGES = [
  { messageId: 1, sender: '오세입 (임차인)', content: '사진이랑 완전히 다른데요?' },
  { messageId: 2, sender: '새집중개 (중개사)', content: '사진은 리모델링 전에 찍은 거예요.' },
  { messageId: 3, sender: '오세입 (임차인)', content: '그럼 허위 매물 아닌가요?' },
]

function AdminReportedChatPage() {
  const [selectedId, setSelectedId] = useState(SAMPLE_REPORTS[0].reportId)

  return (
    <>
      <PageHeader title="신고 대화" description="신고된 대화만 열람할 수 있어요." />
      <div className="grid items-start gap-5 lg:grid-cols-[320px_1fr]">
        <ul aria-label="신고 목록" className="flex flex-col gap-2.5">
          {SAMPLE_REPORTS.map((report) => (
            <li key={report.reportId}>
              <button
                type="button"
                onClick={() => setSelectedId(report.reportId)}
                aria-current={report.reportId === selectedId}
                className={clsx('card flex w-full flex-col gap-0.5 py-4 text-left', report.reportId === selectedId && 'border-blue-400 bg-blue-50')}
              >
                <span className="flex justify-between gap-2">
                  <strong>{report.reason}</strong>
                  <span className="text-sm text-gray-500">{report.reportedAt}</span>
                </span>
                <span className="text-sm text-gray-600">{report.description}</span>
              </button>
            </li>
          ))}
        </ul>
        <div className="flex flex-col gap-4">
          <section aria-labelledby="ai-summary-title" className="rounded-2xl border border-violet-200 bg-violet-50 p-5">
            <h2 id="ai-summary-title" className="mb-2 font-bold text-violet-800">AI 요약</h2>
            <ul className="list-disc space-y-1 pl-5 text-sm leading-relaxed">
              {SAMPLE_SUMMARY.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          </section>
          <section aria-labelledby="raw-chat-title" className="card flex flex-col gap-3 bg-slate-50">
            <h2 id="raw-chat-title" className="font-bold">원문 대화</h2>
            <ol className="flex flex-col gap-2.5">
              {SAMPLE_MESSAGES.map((message) => (
                <li key={message.messageId} className="flex flex-col gap-0.5">
                  <span className="text-xs text-gray-500">{message.sender}</span>
                  <span className="self-start rounded-2xl rounded-tl-sm border border-gray-200 bg-white px-4 py-2.5 text-sm">{message.content}</span>
                </li>
              ))}
            </ol>
          </section>
        </div>
      </div>
    </>
  )
}

export default AdminReportedChatPage
