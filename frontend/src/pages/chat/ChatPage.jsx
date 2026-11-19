// [페이지] 채팅 — /chats (방 목록만), /chats/:roomId (방 목록 + 선택한 방)
// 누가: 임차인·임대인·중개사. 넓은 화면은 왼쪽 목록 + 오른쪽 대화, 모바일은 하나씩 보여준다
// 요구사항: CHAT_003~010, RISK_007
// API:
// - 방 목록: GET /chat/rooms — 내 방 전부, 최근 대화 순, 방별 안 읽은 수 (CHAT_005, CHAT_007)
// - 대화 내역: GET /chat/rooms/{chatRoomId}/messages?cursor= — 30개씩, 위로 스크롤하면 이전 메시지를 이어 불러온다 (CHAT_006)
// - 메시지 전송: WebSocket /ws/chat/rooms/{roomId} (BE-040) — 상대가 접속해 있으면 실시간 전달 (CHAT_003)
// - 이미지 전송: POST /api/v1/chat/rooms/{roomId}/images (BE-041) (CHAT_004)
// - 읽음 처리: PUT/PATCH /api/v1/chat/rooms/{roomId}/read (BE-042) — 방에 들어오면 호출 (CHAT_007)
// - 차단: POST /api/v1/chat/blocks (BE-045) (CHAT_010)
// - 임차인 위험 안내: GET /api/v1/chat/rooms/{roomId}/risk-notice (BE-046) (RISK_007, 임대인만)
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import Icon from '../../components/common/Icon.jsx'
import PhotoPlaceholder from '../../components/common/PhotoPlaceholder.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { ROLES } from '../../constants/auth.js'
import { getChatMessages, getChatRooms } from '../../api/chat/index.js'
import { formatLeasePrice, getPropertyTypeLabel } from '../../utils/format.js'

// CHAT_009 안내 문구 (요구사항 그대로)
const READ_ONLY_MESSAGES = {
  DELETED: '해당 매물은 삭제 되었습니다',
  CONTRACTED: '해당 매물은 계약이 체결 되었습니다',
}

// 목록 API는 원 단위, formatLeasePrice는 만원 단위라서 바꿔서 넘긴다
const toManwonPrice = (room) => ({
  leaseType: room.leaseType,
  deposit: room.deposit / 10000,
  monthlyRent: room.monthlyRent / 10000,
})

// "서울특별시 마포구 월드컵로 61 · 아파트 · 전세 3억". 시·도는 빼고, 매물 종류가 없으면 건너뛴다
const propertyTitleOf = (room) =>
  [room.roadAddress.split(' ').slice(1).join(' '), getPropertyTypeLabel(room.propertyType), formatLeasePrice(toManwonPrice(room))]
    .filter(Boolean)
    .join(' · ')

// 상대가 중개사면 "사무소 이름 · 중개사 이름"으로 매물 상세의 담당 중개사 칸과 맞춘다
const partnerNameOf = (room) => (room.partnerOfficeName ? `${room.partnerOfficeName} · ${room.partnerName}` : room.partnerName)

// 중개사 화면에서는 방 종류로 상대가 임대인인지 매물을 보고 문의한 손님인지 색으로 나눈다
const PARTNER_BADGES = {
  LANDLORD_AGENT: { label: '임대인', tone: 'blue' },
  TENANT_AGENT: { label: '문의', tone: 'amber' },
}

function PartnerName({ room, isAgent }) {
  const badge = isAgent ? PARTNER_BADGES[room.roomType] : null
  return (
    <span className="flex min-w-0 items-center gap-1.5">
      {badge && <StatusBadge tone={badge.tone}>{badge.label}</StatusBadge>}
      <span className="truncate">{partnerNameOf(room)}</span>
    </span>
  )
}

const twoDigits = (number) => String(number).padStart(2, '0')

// 오늘은 "방금·12분 전·3시간 전", 어제는 "어제", 올해는 "10.12", 그 전은 "2025.10.12"
function formatChatTime(dateTime) {
  if (!dateTime) return ''
  const time = new Date(dateTime)
  const now = new Date()
  if (time.toDateString() === now.toDateString()) {
    const minutes = Math.floor((now - time) / 60000)
    if (minutes < 1) return '방금'
    if (minutes < 60) return `${minutes}분 전`
    return `${Math.floor(minutes / 60)}시간 전`
  }
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (time.toDateString() === yesterday.toDateString()) return '어제'
  const monthDay = `${twoDigits(time.getMonth() + 1)}.${twoDigits(time.getDate())}`
  return time.getFullYear() === now.getFullYear() ? monthDay : `${time.getFullYear()}.${monthDay}`
}

function ChatRoomList({ rooms, activeRoomId, isAgent }) {
  return (
    <nav aria-label="채팅방 목록" className="flex flex-col">
      <h1 className="border-b border-gray-200 px-5 py-4 text-lg font-bold">채팅</h1>
      {rooms.map((room) => (
        <Link
          key={room.chatRoomId}
          to={`/chats/${room.chatRoomId}`}
          aria-current={room.chatRoomId === activeRoomId ? 'page' : undefined}
          className={clsx('flex gap-3 px-5 py-3.5 hover:bg-slate-50', room.chatRoomId === activeRoomId && 'bg-blue-50')}
        >
          <PhotoPlaceholder label="" className="size-12 shrink-0" />
          <div className="flex min-w-0 grow flex-col">
            <div className="flex justify-between gap-2">
              <span className="min-w-0 font-bold"><PartnerName room={room} isAgent={isAgent} /></span>
              <span className="shrink-0 text-xs text-gray-500">{formatChatTime(room.lastMessageAt)}</span>
            </div>
            <span className="truncate text-xs text-gray-500">{propertyTitleOf(room)}</span>
            <div className="flex items-center justify-between gap-2">
              <span className="truncate text-sm text-gray-700">{room.lastMessage ?? '아직 대화가 없어요.'}</span>
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

function ChatRoom({ room, isAgent }) {
  const user = useAuthStore((state) => state.user)
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  // 첫 쪽이 최근 30개이고, 다음 쪽으로 갈수록 오래된 대화다
  const history = useInfiniteQuery({
    queryKey: ['chat-messages', room.chatRoomId, user?.userId],
    queryFn: ({ pageParam, signal }) => getChatMessages(room.chatRoomId, pageParam, signal),
    initialPageParam: null,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })
  const { hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage } = history
  const pages = history.data?.pages ?? []
  // 화면은 위에서 아래로 오래된 것부터 그린다
  const messages = [...pages].reverse().flatMap((page) => page.messages)

  // 처음에는 맨 아래(최근 메시지)를 보여 주고, 이전 대화가 위에 붙으면 늘어난 높이만큼 내려서 보던 자리를 지킨다
  const scrollRef = useRef(null)
  const previousHeightRef = useRef(null)
  useLayoutEffect(() => {
    const scroller = scrollRef.current
    if (!scroller || pages.length === 0) return
    scroller.scrollTop = previousHeightRef.current === null
      ? scroller.scrollHeight
      : scroller.scrollTop + scroller.scrollHeight - previousHeightRef.current
    previousHeightRef.current = scroller.scrollHeight
  }, [pages.length])

  // 맨 위에 닿기 조금 전(200px)부터 이전 대화를 불러와 스크롤이 끊기지 않게 한다
  const sentinelRef = useRef(null)
  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || !hasNextPage || isFetchingNextPage || isFetchNextPageError) return
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) fetchNextPage()
    }, { root: scrollRef.current, rootMargin: '200px 0px 0px 0px' })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage])

  // TODO: POST /api/v1/chat/blocks (BE-045, 요청: { roomId 또는 상대 userId }) — 요청 필드는 백엔드와 협의 필요
  const handleBlock = () => {
    setIsMenuOpen(false)
  }

  return (
    <section aria-label={`${partnerNameOf(room)} 채팅방`} className="flex h-[min(720px,calc(100dvh-10rem))] min-h-[480px] min-w-0 grow flex-col bg-slate-50">
      <header className="flex items-center justify-between gap-3 border-b border-gray-200 bg-white px-5 py-3">
        <div className="flex min-w-0 items-center gap-3">
          <Link to="/chats" className="md:hidden" aria-label="채팅방 목록으로">
            <Icon name="back" />
          </Link>
          <div className="flex min-w-0 flex-col">
            {/* CHAT_010: 상대 이름을 누르면 차단 버튼이 나온다 */}
            <div className="relative">
              <button type="button" onClick={() => setIsMenuOpen((prev) => !prev)} aria-expanded={isMenuOpen} className="max-w-full font-bold hover:underline">
                <PartnerName room={room} isAgent={isAgent} />
              </button>
              {isMenuOpen && (
                <div className="absolute top-8 left-0 z-10 rounded-xl border border-gray-200 bg-white p-1.5 shadow-lg">
                  <button type="button" onClick={handleBlock} className="rounded-lg px-4 py-2 text-sm whitespace-nowrap text-red-700 hover:bg-red-50">
                    이 사용자 차단하기
                  </button>
                </div>
              )}
            </div>
            <span className="truncate text-sm text-gray-500">{propertyTitleOf(room)}</span>
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

      <div ref={scrollRef} className="min-h-0 grow overflow-y-auto px-5 py-5">
        {history.isPending && <p role="status" className="text-center text-gray-500">대화를 불러오고 있어요.</p>}
        {history.isError && <p role="alert" className="text-center text-gray-500">대화를 불러오지 못했어요.</p>}
        {history.isSuccess && messages.length === 0 && (
          <p className="flex h-full items-center justify-center text-gray-500">첫 메시지를 보내 보세요.</p>
        )}
        {messages.length > 0 && (
          <>
            <div ref={sentinelRef} aria-hidden="true" />
            {isFetchingNextPage && <p role="status" className="pb-3 text-center text-sm text-gray-500">이전 대화를 불러오고 있어요.</p>}
            {isFetchNextPageError && (
              <div role="alert" className="flex flex-col items-center gap-2 pb-3 text-sm text-gray-500">
                이전 대화를 불러오지 못했어요.
                <button type="button" onClick={() => fetchNextPage()} className="btn-secondary btn-sm">다시 시도</button>
              </div>
            )}
            {!hasNextPage && <p className="pb-3 text-center text-xs text-gray-400">대화의 처음이에요.</p>}
            <ol className="flex flex-col gap-2.5">
              {messages.map((message) => (
                <li key={message.chatMessageId} className={clsx('max-w-[70%] rounded-2xl', message.isMine ? 'self-end rounded-br-sm bg-blue-600 text-white' : 'self-start rounded-bl-sm border border-gray-200 bg-white')}>
                  {/* 사진 주소는 이미지 전송(CHAT_004)을 만들 때 받는다 */}
                  {message.messageType === 'IMAGE' ? <PhotoPlaceholder label="보낸 사진" className="h-32 w-48" /> : <p className="px-4 py-2.5 text-[15px] break-words whitespace-pre-wrap">{message.content}</p>}
                </li>
              ))}
            </ol>
          </>
        )}
      </div>

      {room.chatStatus === 'READ_ONLY' ? (
        // CHAT_009: 읽기 전용이면 입력창 대신 안내 문구. 이전 대화는 그대로 볼 수 있다
        <p className="border-t border-gray-200 bg-white px-5 py-4 text-center text-gray-600">{READ_ONLY_MESSAGES[room.readOnlyReason]}</p>
      ) : (
        // TODO: 메시지 전송(CHAT_003, WebSocket)·사진 전송(CHAT_004)이 생기면 연결한다. 저장되지 않는 메시지가 섞이지 않게 지금은 막아 둔다
        <div className="flex items-center gap-2 border-t border-gray-200 bg-white px-4 py-3">
          <span className="flex size-11 shrink-0 items-center justify-center rounded-lg text-gray-300" aria-hidden="true">
            <Icon name="image" size={22} />
          </span>
          <label htmlFor="chat-input" className="sr-only">메시지</label>
          <input id="chat-input" disabled placeholder="메시지 보내기는 준비 중이에요" className="h-11 min-w-0 grow rounded-xl border border-gray-300 bg-gray-50 px-4" />
          <button type="button" disabled className="btn-primary disabled:cursor-not-allowed disabled:opacity-50">보내기</button>
        </div>
      )}
    </section>
  )
}

function ChatPage() {
  const { roomId } = useParams()
  const user = useAuthStore((state) => state.user)
  const isAgent = user?.role === ROLES.AGENT
  // 다른 화면에 갔다 오면 새로 불러와 최근 대화 순서와 안 읽은 수를 맞춘다
  const list = useQuery({
    queryKey: ['chat-rooms', user?.userId],
    queryFn: ({ signal }) => getChatRooms(signal),
  })
  const rooms = list.data?.rooms ?? []
  const activeRoom = rooms.find((room) => String(room.chatRoomId) === roomId)

  return (
    <div className="page-container">
      <div className="card grid grid-cols-1 overflow-hidden p-0 md:grid-cols-[320px_1fr]">
        {/* 모바일은 방을 고르면 목록을 숨기고 대화만 보여 준다. 넓은 화면에서는 대화 칸과 같은 높이 안에서 목록만 스크롤한다 */}
        <div className={clsx('border-gray-200 md:h-[min(720px,calc(100dvh-10rem))] md:min-h-[480px] md:overflow-y-auto md:border-r', roomId && 'hidden md:block')}>
          {list.isPending && <p role="status" className="px-5 py-4 text-gray-500">채팅방을 불러오고 있어요.</p>}
          {list.isError && <p role="alert" className="px-5 py-4 text-gray-500">채팅방을 불러오지 못했어요.</p>}
          {list.isSuccess && rooms.length === 0 && (
            <p className="px-5 py-10 text-center text-gray-500">
              {isAgent
                ? '아직 채팅방이 없어요. 중개 신청을 수락하면 임대인과의 채팅방이 생겨요.'
                : '아직 채팅방이 없어요. 매물 상세에서 채팅으로 문의해 보세요.'}
            </p>
          )}
          {rooms.length > 0 && <ChatRoomList rooms={rooms} activeRoomId={activeRoom?.chatRoomId} isAgent={isAgent} />}
        </div>
        {activeRoom ? (
          // key: 방을 바꾸면 메시지 목록과 스크롤 위치를 새로 시작한다
          <ChatRoom key={activeRoom.chatRoomId} room={activeRoom} isAgent={isAgent} />
        ) : roomId && list.isSuccess ? (
          // 내 목록에 없는 방 번호다
          <div className="flex flex-col items-center justify-center gap-3 bg-slate-50 p-10 text-gray-500">
            채팅방을 찾을 수 없어요.
            <Link to="/chats" className="btn-secondary btn-sm md:hidden">채팅방 목록으로</Link>
          </div>
        ) : (
          <p className="hidden items-center justify-center bg-slate-50 p-10 text-gray-500 md:flex">왼쪽에서 채팅방을 골라 주세요.</p>
        )}
      </div>
    </div>
  )
}

export default ChatPage
