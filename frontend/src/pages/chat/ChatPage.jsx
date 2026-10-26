// [페이지] 채팅 — /chats (방 목록만), /chats/:roomId (방 목록 + 선택한 방)
// 누가: 임차인·임대인·중개사. 넓은 화면은 왼쪽 목록 + 오른쪽 대화, 모바일은 하나씩 보여준다
// 요구사항: CHAT_003~010, RISK_007
// API:
// - 방 목록: GET /api/v1/chat/rooms?cursor=&size= (BE-038) — 최근 대화 순, 방별 안 읽은 수 (CHAT_005, CHAT_007)
// - 대화 내역: GET /api/v1/chat/rooms/{roomId}/messages?cursor=&size= (BE-039) — 위로 스크롤하면 이전 메시지를 이어 불러온다 (CHAT_006)
// - 메시지 전송: WebSocket /ws/chat/rooms/{roomId} (BE-040) — 상대가 접속해 있으면 실시간 전달 (CHAT_003)
// - 이미지 전송: POST /api/v1/chat/rooms/{roomId}/images (BE-041) (CHAT_004)
// - 읽음 처리: PUT/PATCH /api/v1/chat/rooms/{roomId}/read (BE-042) — 방에 들어오면 호출 (CHAT_007)
// - 차단: POST /api/v1/chat/blocks (BE-045) (CHAT_010)
// - 임차인 위험 안내: GET /api/v1/chat/rooms/{roomId}/risk-notice (BE-046) (RISK_007, 임대인만)
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import clsx from 'clsx'
import Icon from '../../components/common/Icon.jsx'
import PhotoPlaceholder from '../../components/common/PhotoPlaceholder.jsx'

// TODO: GET /api/v1/chat/rooms (BE-038) 응답의 rooms[]로 교체
// - readOnlyReason: 매물 삭제(DELETED)·계약 체결(CONTRACTED) 시 읽기 전용 (CHAT_009). 필드 이름은 백엔드와 협의 필요
// - isMyProperty: 내가 중개 신청한(임대인인) 매물의 방인지. 임차인 위험 안내(RISK_007)에 쓴다. 필드 이름은 백엔드와 협의 필요
const SAMPLE_ROOMS = [
  { roomId: 1, partnerName: '보금공인중개사', propertyTitle: '역삼동 오피스텔 · 월세 1,000/65', lastMessage: '내일 오후 3시에 방문 가능하세요?', time: '12분 전', unreadCount: 2, readOnlyReason: null, isMyProperty: true },
  { roomId: 2, partnerName: '한강부동산', propertyTitle: '공덕동 아파트 · 전세 3억 2,000', lastMessage: '사진 더 보내드릴게요.', time: '어제', unreadCount: 0, readOnlyReason: null, isMyProperty: false },
  { roomId: 3, partnerName: '새집중개', propertyTitle: '안암동 투룸 · 전세 1억 8,000', lastMessage: '해당 매물은 계약이 체결 되었습니다', time: '10.12', unreadCount: 0, readOnlyReason: 'CONTRACTED', isMyProperty: false },
]

// CHAT_009 안내 문구 (요구사항 그대로)
const READ_ONLY_MESSAGES = {
  DELETED: '해당 매물은 삭제 되었습니다',
  CONTRACTED: '해당 매물은 계약이 체결 되었습니다',
}

// TODO: GET /api/v1/chat/rooms/{roomId}/messages (BE-039) 응답의 messages[]로 교체 (isMine, type: TEXT/IMAGE, content, imageUrl, isRead)
const SAMPLE_MESSAGES = [
  { messageId: 1, isMine: false, type: 'TEXT', content: '안녕하세요, 문의 주신 매물 담당 중개사예요.' },
  { messageId: 2, isMine: true, type: 'TEXT', content: '주차 가능한가요?' },
  { messageId: 3, isMine: false, type: 'TEXT', content: '네, 세대당 0.5대라 신청하시면 배정돼요.' },
  { messageId: 4, isMine: true, type: 'IMAGE', content: '' },
  { messageId: 5, isMine: false, type: 'TEXT', content: '내일 오후 3시에 방문 가능하세요?' },
]

function ChatRoomList({ activeRoomId }) {
  return (
    <nav aria-label="채팅방 목록" className="flex flex-col">
      <h1 className="border-b border-gray-200 px-5 py-4 text-lg font-bold">채팅</h1>
      {SAMPLE_ROOMS.map((room) => (
        <Link
          key={room.roomId}
          to={`/chats/${room.roomId}`}
          aria-current={room.roomId === activeRoomId ? 'page' : undefined}
          className={clsx('flex gap-3 px-5 py-3.5 hover:bg-slate-50', room.roomId === activeRoomId && 'bg-blue-50')}
        >
          <PhotoPlaceholder label="" className="size-12 shrink-0" />
          <div className="flex min-w-0 grow flex-col">
            <div className="flex justify-between gap-2">
              <span className="font-bold">{room.partnerName}</span>
              <span className="shrink-0 text-xs text-gray-500">{room.time}</span>
            </div>
            <span className="truncate text-xs text-gray-500">{room.propertyTitle}</span>
            <div className="flex items-center justify-between gap-2">
              <span className="truncate text-sm text-gray-700">{room.lastMessage}</span>
              {room.unreadCount > 0 && (
                <span className="min-w-5 rounded-full bg-red-600 px-1.5 text-center text-xs leading-5 font-bold text-white">
                  {room.unreadCount}
                  <span className="sr-only">개 안 읽음</span>
                </span>
              )}
            </div>
          </div>
        </Link>
      ))}
    </nav>
  )
}

function ChatRoom({ room }) {
  const [messages, setMessages] = useState(SAMPLE_MESSAGES)
  const [draft, setDraft] = useState('')
  const [isMenuOpen, setIsMenuOpen] = useState(false)

  // TODO: WebSocket /ws/chat/rooms/{roomId} (BE-040) 으로 전송. 지금은 화면에만 추가한다
  const handleSend = (event) => {
    event.preventDefault()
    if (!draft.trim()) return
    setMessages((prev) => [...prev, { messageId: Date.now(), isMine: true, type: 'TEXT', content: draft.trim() }])
    setDraft('')
  }

  // TODO: POST /api/v1/chat/rooms/{roomId}/images (BE-041) 로 업로드. 형식·용량 제한은 백엔드와 맞춘다 (CHAT_004)
  const handleImageSelect = (event) => {
    if (event.target.files.length === 0) return
    setMessages((prev) => [...prev, { messageId: Date.now(), isMine: true, type: 'IMAGE', content: '' }])
    event.target.value = ''
  }

  // TODO: POST /api/v1/chat/blocks (BE-045, 요청: { roomId 또는 상대 userId }) — 요청 필드는 백엔드와 협의 필요
  const handleBlock = () => {
    setIsMenuOpen(false)
  }

  return (
    <section aria-label={`${room.partnerName} 채팅방`} className="flex min-h-[560px] min-w-0 grow flex-col bg-slate-50">
      <header className="flex items-center justify-between gap-3 border-b border-gray-200 bg-white px-5 py-3">
        <div className="flex items-center gap-3">
          <Link to="/chats" className="md:hidden" aria-label="채팅방 목록으로">
            <Icon name="back" />
          </Link>
          <div className="flex flex-col">
            {/* CHAT_010: 상대 이름을 누르면 차단 버튼이 나온다 */}
            <div className="relative">
              <button type="button" onClick={() => setIsMenuOpen((prev) => !prev)} aria-expanded={isMenuOpen} className="font-bold hover:underline">
                {room.partnerName}
              </button>
              {isMenuOpen && (
                <div className="absolute top-8 left-0 z-10 rounded-xl border border-gray-200 bg-white p-1.5 shadow-lg">
                  <button type="button" onClick={handleBlock} className="rounded-lg px-4 py-2 text-sm whitespace-nowrap text-red-700 hover:bg-red-50">
                    이 사용자 차단하기
                  </button>
                </div>
              )}
            </div>
            <span className="text-sm text-gray-500">{room.propertyTitle}</span>
          </div>
        </div>
      </header>

      {/* RISK_007: 해당 매물 등록자(임대인)에게만 보이고 사유 상세·연체 금액은 보여주지 않는다
          참고: CHAT_002 기준으로 임대인은 중개사와만 채팅하므로 이 안내가 뜨는 경우가 있는지 팀 확인 필요 */}
      {room.isMyProperty && (
        <p className="border-b border-amber-200 bg-amber-50 px-5 py-2.5 text-sm text-amber-900">
          이 임차인은 월세 미납이나 연체 이력이 있는 임차인입니다
        </p>
      )}

      <ol className="flex grow flex-col gap-2.5 px-5 py-5">
        {messages.map((message) => (
          <li key={message.messageId} className={clsx('max-w-[70%] rounded-2xl', message.isMine ? 'self-end rounded-br-sm bg-blue-600 text-white' : 'self-start rounded-bl-sm border border-gray-200 bg-white')}>
            {message.type === 'IMAGE' ? <PhotoPlaceholder label="보낸 사진" className="h-32 w-48" /> : <p className="px-4 py-2.5 text-[15px]">{message.content}</p>}
          </li>
        ))}
      </ol>

      {room.readOnlyReason ? (
        // CHAT_009: 읽기 전용이면 입력창 대신 안내 문구. 이전 대화는 그대로 볼 수 있다
        <p className="border-t border-gray-200 bg-white px-5 py-4 text-center text-gray-600">{READ_ONLY_MESSAGES[room.readOnlyReason]}</p>
      ) : (
        <form onSubmit={handleSend} className="flex items-center gap-2 border-t border-gray-200 bg-white px-4 py-3">
          <label className="flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-lg text-gray-600 hover:bg-gray-100">
            <Icon name="image" size={22} />
            <span className="sr-only">사진 보내기</span>
            <input type="file" accept="image/*" onChange={handleImageSelect} className="sr-only" />
          </label>
          <label htmlFor="chat-input" className="sr-only">메시지</label>
          <input id="chat-input" value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="메시지를 입력하세요" className="h-11 min-w-0 grow rounded-xl border border-gray-300 px-4" />
          <button type="submit" className="btn-primary">보내기</button>
        </form>
      )}
    </section>
  )
}

function ChatPage() {
  const { roomId } = useParams()
  const activeRoom = SAMPLE_ROOMS.find((room) => String(room.roomId) === roomId)

  return (
    <div className="page-container">
      <div className="card grid grid-cols-1 overflow-hidden p-0 md:grid-cols-[320px_1fr]">
        <div className={clsx('border-gray-200 md:border-r', activeRoom && 'hidden md:block')}>
          <ChatRoomList activeRoomId={activeRoom?.roomId} />
        </div>
        {activeRoom ? (
          // key: 방을 바꾸면 입력 중이던 글과 메시지 목록을 새로 시작한다
          <ChatRoom key={activeRoom.roomId} room={activeRoom} />
        ) : (
          <p className="hidden items-center justify-center bg-slate-50 p-10 text-gray-500 md:flex">왼쪽에서 채팅방을 골라 주세요.</p>
        )}
      </div>
    </div>
  )
}

export default ChatPage
