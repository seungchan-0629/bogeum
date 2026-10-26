// CHATBOT_001 (챗봇 진입 시 자주 찾는 질문을 추천 버튼으로 제공)
import { useState } from 'react'

// TODO: GET /api/v1/chatbot/quick-replies (BE-104) 응답의 quickReplies[id, label, message]로 교체
// - 역할별로 다른 질문을 주는 것으로 가정(BE-104 비고)
const SAMPLE_QUICK_REPLIES_BY_ROLE = {
  USER: [
    { id: 1, label: '매물 검색' },
    { id: 2, label: '월세 납부 내역' },
    { id: 3, label: '월세 수납·지급 내역' },
    { id: 4, label: 'AS 요청' },
    { id: 5, label: '서비스 이용 안내' },
  ],
  ADMIN: [
    { id: 1, label: '월세 연체 현황' },
    { id: 2, label: '위험 정보 현황' },
  ],
}

function ChatbotLauncher({ role }) {
  const [isOpen, setIsOpen] = useState(false)
  const quickReplies = SAMPLE_QUICK_REPLIES_BY_ROLE[role] ?? []

  // TODO: POST /api/v1/chatbot/messages (BE-105) 로 quickReply.message 전송 후 대화 화면 표시 (대화 화면은 다음 단계)
  const handleQuickReply = () => {}

  return (
    <div className="fixed right-6 bottom-6 z-50 flex flex-col items-end gap-3">
      {isOpen && (
        <div id="chatbot-panel" className="flex w-[min(320px,calc(100vw-48px))] flex-col gap-4 rounded-2xl border border-gray-200 bg-white p-5 shadow-[0_12px_32px_rgba(17,24,39,0.16)]">
          <div className="flex flex-col gap-1">
            <p className="font-bold text-gray-900">보금 챗봇</p>
            <p className="text-sm text-gray-600">무엇을 도와드릴까요?</p>
          </div>
          <div className="flex flex-wrap gap-2">
            {quickReplies.map((reply) => (
              <button
                key={reply.id}
                type="button"
                onClick={() => handleQuickReply(reply)}
                className="rounded-full border border-blue-200 bg-blue-50 px-3.5 py-2 text-sm font-medium text-blue-800 hover:bg-blue-100"
              >
                {reply.label}
              </button>
            ))}
          </div>
        </div>
      )}
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        aria-expanded={isOpen}
        aria-controls="chatbot-panel"
        aria-label={isOpen ? '챗봇 닫기' : '챗봇 열기'}
        className="flex size-14 items-center justify-center rounded-full bg-blue-600 text-white shadow-[0_8px_20px_rgba(37,99,235,0.4)] hover:bg-blue-700"
      >
        {isOpen ? (
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M18 6 6 18" />
            <path d="m6 6 12 12" />
          </svg>
        ) : (
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
        )}
      </button>
    </div>
  )
}

export default ChatbotLauncher
