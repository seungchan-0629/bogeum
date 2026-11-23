// [페이지] 채팅 — /chats (방 목록만), /chats/:roomId (방 목록 + 선택한 방)
// 누가: 임차인·임대인·중개사. 넓은 화면은 왼쪽 목록 + 오른쪽 대화, 모바일은 하나씩 보여준다
// 요구사항: CHAT_003~010, RISK_007
// API:
// - 방 목록: GET /chat/rooms — 내 방 전부, 최근 대화 순, 방별 안 읽은 수 (CHAT_005, CHAT_007)
// - 대화 내역: GET /chat/rooms/{chatRoomId}/messages?cursor= — 30개씩, 위로 스크롤하면 이전 메시지를 이어 불러온다 (CHAT_006)
// - 메시지 전송: POST /chat/rooms/{chatRoomId}/messages, 받기는 WebSocket /ws(STOMP) 구독 — 상대가 보고 있으면 바로 뜬다 (CHAT_003)
// - 이미지 전송: POST /chat/rooms/{chatRoomId}/images — 고른 사진을 미리보기로 확인하고 한 장씩 보낸다 (CHAT_004)
// - 읽음 처리: PATCH /chat/rooms/{chatRoomId}/read — 방을 열 때·보는 중 상대 메시지가 올 때·탭으로 돌아올 때 (CHAT_007)
// - 차단·해제: POST·DELETE /chat/rooms/{chatRoomId}/block — 머리글 오른쪽 버튼 (CHAT_010)
// - 신고: POST /chat/rooms/{chatRoomId}/report — 차단 옆 버튼, 사유를 골라 관리자에게 보낸다 (CHAT_011)
// - 임차인 위험 안내: GET /api/v1/chat/rooms/{roomId}/risk-notice (BE-046) (RISK_007, 임대인만)
import { Fragment, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import Icon from '../../components/common/Icon.jsx'
import PhotoPlaceholder from '../../components/common/PhotoPlaceholder.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import ConfirmModal from '../../components/common/ConfirmModal.jsx'
import { SelectInput, TextArea } from '../../components/common/FormControls.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { ROLES } from '../../constants/auth.js'
import { createChatBlock, createChatImage, createChatMessage, createChatReport, deleteChatBlock, getChatMessages, getChatRooms, updateChatRead } from '../../api/chat/index.js'
import { useChatSocket } from '../../hooks/useChatSocket.js'
import { formatLeasePrice, getPropertyTypeLabel } from '../../utils/format.js'

// 서버가 받는 메시지 최대 길이 (CHAT_003)
const MESSAGE_MAX_LENGTH = 2000
// 서버의 공통 사진 검사와 같은 기준 (CHAT_004)
const IMAGE_TYPES = ['image/jpeg', 'image/png']
const IMAGE_MAX_BYTES = 20 * 1024 * 1024
// 서버가 받는 신고 사유와 내용 최대 길이 (CHAT_011). 기타는 내용이 있어야 한다
const REPORT_REASON_OPTIONS = [
  { value: '', label: '사유를 골라 주세요' },
  { value: 'FALSE_LISTING', label: '허위 매물' },
  { value: 'RISKY_PROPERTY', label: '위험 매물' },
  { value: 'RISK_INFO', label: '위험 정보' },
  { value: 'ABUSIVE', label: '욕설·비매너' },
  { value: 'FRAUD', label: '사기 의심' },
  { value: 'ETC', label: '기타' },
]
const REPORT_CONTENT_MAX_LENGTH = 1000
// 맨 아래에서 이만큼 안이면 새 메시지가 올 때 따라 내려간다
const NEAR_BOTTOM_PX = 80

const messagesKeyOf = (chatRoomId, userId) => ['chat-messages', chatRoomId, userId]

// 같은 메시지가 보내기 응답과 실시간 전달로 두 번 올 수 있어서 번호로 한 번만 붙인다. 첫 쪽이 최근 대화다
// 대화를 아직 불러오지 않았으면 그대로 두고, 불러올 때 함께 받는다
function appendMessage(data, message) {
  if (!data || data.pages.some((page) => page.messages.some((item) => item.chatMessageId === message.chatMessageId))) return data
  const [newest, ...older] = data.pages
  return { ...data, pages: [{ ...newest, messages: [...newest.messages, message] }, ...older] }
}

// 상대가 더 읽었다는 알림이 오면 첫 쪽의 상대 읽음 위치를 바꿔 내 메시지의 "1"을 지운다 (CHAT_007)
function updatePartnerRead(data, lastReadMessageId) {
  if (!data) return data
  const [newest, ...older] = data.pages
  return { ...data, pages: [{ ...newest, partnerLastReadMessageId: lastReadMessageId }, ...older] }
}

// 탭이 보일 때만 읽음 처리한다. 안 본 메시지가 읽은 것으로 바뀌지 않게 한다 (CHAT_007)
// 실패해도 빨간 숫자가 조금 늦게 줄어들 뿐이라 따로 안내하지 않는다
function markReadIfVisible(chatRoomId) {
  if (document.visibilityState === 'visible') updateChatRead(chatRoomId).catch(() => {})
}

// "오후 3:12"
const formatMessageTime = (dateTime) => new Date(dateTime).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })

// "2026년 11월 19일 수요일". 날짜가 바뀌는 곳의 구분선에 쓴다
const formatMessageDate = (dateTime) =>
  new Date(dateTime).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

const isSameDay = (left, right) => new Date(left).toDateString() === new Date(right).toDateString()

// CHAT_009 채팅 목록에서 왜 닫힌 방인지 보여 주는 배지. 빨강은 실패·연체에만 쓰므로 삭제는 주황으로 둔다
const READ_ONLY_BADGES = {
  CONTRACTED: { label: '계약 완료', tone: 'green' },
  DELETED: { label: '매물 삭제', tone: 'amber' },
}

// 읽기 전용 방의 목록 글자를 연하게 한다 (배지는 빼고)
const dimIfReadOnly = (room) => room.chatStatus === 'READ_ONLY' && 'opacity-60'

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

// 말풍선 속 사진 (CHAT_004). 누르면 새 탭에서 원본을 연다
// 주소가 만료돼 안 뜨면 한 번만 대화를 새로 받아 새 주소로 바꾸고, 그래도 안 되면 회색 칸으로 둔다 (파일이 없는 사진에서 계속 다시 받지 않게)
// 잘 뜨고 나면 다시 기회를 줘서, 화면을 오래 열어 둬 또 만료돼도 한 번 더 새로 받는다
function ChatImage({ imageUrl, onExpired }) {
  const [hasRetried, setHasRetried] = useState(false)
  const [isBroken, setIsBroken] = useState(false)
  // 저장소가 없는 환경이면 주소가 비어 있다
  if (!imageUrl || isBroken) return <PhotoPlaceholder label={imageUrl ? '사진을 불러올 수 없어요' : '보낸 사진'} className="h-32 w-48" />
  const handleError = () => {
    if (hasRetried) {
      setIsBroken(true)
      return
    }
    setHasRetried(true)
    onExpired()
  }
  return (
    <a href={imageUrl} target="_blank" rel="noreferrer" aria-label="사진 크게 보기">
      <img src={imageUrl} alt="보낸 사진" loading="lazy" onLoad={() => setHasRetried(false)} onError={handleError} className="block max-h-72 max-w-60 bg-gray-100 object-cover" />
    </a>
  )
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
          {/* 읽기 전용 방은 들어가기 전에도 닫힌 방인 걸 알 수 있게 글자를 연하게 하고, 닫힌 이유 배지는 또렷하게 붙인다 (CHAT_009)
              내가 차단한 상대는 이름을 빨간 글자로 보여 준다. 상대가 나를 차단한 건 드러내지 않는다 (CHAT_010) */}
          <div className="flex min-w-0 grow flex-col">
            <div className="flex justify-between gap-2">
              <span className="flex min-w-0 items-center gap-1.5 font-bold">
                <span className={clsx('min-w-0', dimIfReadOnly(room), room.blockStatus === 'BLOCKED_BY_ME' && 'text-red-600')}><PartnerName room={room} isAgent={isAgent} /></span>
                {room.chatStatus === 'READ_ONLY' && (
                  <StatusBadge tone={READ_ONLY_BADGES[room.readOnlyReason]?.tone}>{READ_ONLY_BADGES[room.readOnlyReason]?.label}</StatusBadge>
                )}
              </span>
              <span className={clsx('shrink-0 text-xs text-gray-500', dimIfReadOnly(room))}>{formatChatTime(room.lastMessageAt)}</span>
            </div>
            <span className={clsx('truncate text-xs text-gray-500', dimIfReadOnly(room))}>{propertyTitleOf(room)}</span>
            <div className={clsx('flex items-center justify-between gap-2', dimIfReadOnly(room))}>
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
  const queryClient = useQueryClient()
  const isMineOf = (message) => message.senderId === user?.userId
  // 첫 쪽이 최근 30개이고, 다음 쪽으로 갈수록 오래된 대화다
  const history = useInfiniteQuery({
    queryKey: messagesKeyOf(room.chatRoomId, user?.userId),
    queryFn: ({ pageParam, signal }) => getChatMessages(room.chatRoomId, pageParam, signal),
    initialPageParam: null,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })
  const { hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage } = history
  const pages = history.data?.pages ?? []
  // 화면은 위에서 아래로 오래된 것부터 그린다
  const messages = [...pages].reverse().flatMap((page) => page.messages)
  // 쪽마다 받은 시각이 달라서 가장 앞선 값을 쓴다. 이 번호보다 뒤의 내 메시지는 상대가 아직 안 읽었다 (CHAT_007)
  const partnerLastRead = Math.max(0, ...pages.map((page) => page.partnerLastReadMessageId ?? 0))
  const isUnreadByPartner = (message) => isMineOf(message) && message.chatMessageId > partnerLastRead

  // 대화를 불러오면, 그리고 다른 탭에 갔다가 돌아오면 읽음 처리한다 (CHAT_007)
  useEffect(() => {
    if (!history.isSuccess) return
    const markRead = () => markReadIfVisible(room.chatRoomId)
    markRead()
    document.addEventListener('visibilitychange', markRead)
    return () => document.removeEventListener('visibilitychange', markRead)
  }, [history.isSuccess, room.chatRoomId])

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

  // 새 메시지가 붙으면 맨 아래 근처에 있을 때와 내가 보낸 메시지만 따라 내려간다
  // 위로 올려 예전 대화를 보고 있으면 자리를 지키고 [새 메시지] 버튼을 띄운다
  const isNearBottomRef = useRef(true)
  const [hasNewMessage, setHasNewMessage] = useState(false)
  const lastMessageId = messages.at(-1)?.chatMessageId
  const isLastMine = messages.length > 0 && isMineOf(messages.at(-1))
  useLayoutEffect(() => {
    const scroller = scrollRef.current
    if (!scroller || lastMessageId === undefined) return
    if (isNearBottomRef.current || isLastMine) scroller.scrollTop = scroller.scrollHeight
    else setHasNewMessage(true)
    previousHeightRef.current = scroller.scrollHeight
  }, [lastMessageId, isLastMine])

  const handleScroll = () => {
    const scroller = scrollRef.current
    isNearBottomRef.current = scroller.scrollHeight - scroller.scrollTop - scroller.clientHeight < NEAR_BOTTOM_PX
    if (isNearBottomRef.current) setHasNewMessage(false)
  }

  const scrollToBottom = () => {
    scrollRef.current.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' })
    setHasNewMessage(false)
  }

  // 보내기에 실패하면 쓴 글은 그대로 두고 입력창 아래에 서버 문구를 보여 준다
  const [draft, setDraft] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [sendError, setSendError] = useState(null)
  const appendToHistory = (message) =>
    queryClient.setQueryData(messagesKeyOf(room.chatRoomId, user?.userId), (data) => appendMessage(data, message))
  const showSendError = (error) => {
    setSendError(error.response?.data?.message || '서버에 연결할 수 없습니다.')
    // 보내는 사이 매물이 삭제·계약돼 읽기 전용이 됐거나 차단됐으면 목록을 새로 받아 안내 문구로 바꾼다
    if (['CHAT_ROOM_READ_ONLY', 'CHAT_BLOCKED'].includes(error.response?.data?.code)) queryClient.invalidateQueries({ queryKey: ['chat-rooms'] })
  }
  const handleSend = async (event) => {
    event.preventDefault()
    const content = draft.trim()
    if (!content || isSending) return
    setIsSending(true)
    setSendError(null)
    try {
      appendToHistory(await createChatMessage(room.chatRoomId, content))
      setDraft('')
    } catch (error) {
      showSendError(error)
    } finally {
      setIsSending(false)
    }
  }

  // 사진은 고른 뒤 미리보기에서 [보내기]를 눌러야 간다. 잘못 고른 사진이 바로 상대에게 가지 않게 한다 (CHAT_004)
  const [imagePreview, setImagePreview] = useState(null)
  const [isSendingImage, setIsSendingImage] = useState(false)
  // 미리보기를 바꾸거나 닫으면 브라우저가 잡아 둔 미리보기 주소를 놓아준다
  useEffect(() => () => {
    if (imagePreview) URL.revokeObjectURL(imagePreview.url)
  }, [imagePreview])

  const handleImageSelect = (event) => {
    const file = event.target.files[0]
    // 같은 사진을 다시 골라도 onChange가 오도록 비운다
    event.target.value = ''
    if (!file) return
    // 서버와 같은 기준으로 먼저 걸러 올리지 않고 바로 알려 준다
    if (!IMAGE_TYPES.includes(file.type) || file.size > IMAGE_MAX_BYTES) {
      setSendError('JPEG·PNG 사진만 20MB까지 보낼 수 있어요.')
      return
    }
    setSendError(null)
    setImagePreview({ file, url: URL.createObjectURL(file) })
  }

  const handleSendImage = async () => {
    if (!imagePreview || isSendingImage) return
    setIsSendingImage(true)
    setSendError(null)
    try {
      appendToHistory(await createChatImage(room.chatRoomId, imagePreview.file))
      setImagePreview(null)
    } catch (error) {
      showSendError(error)
    } finally {
      setIsSendingImage(false)
    }
  }

  // 사진 주소는 10분 뒤 만료된다. 안 뜨면 대화를 새로 받아 새 주소로 바꾼다
  const refreshHistory = () => queryClient.invalidateQueries({ queryKey: messagesKeyOf(room.chatRoomId, user?.userId) })

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

  const isReadOnly = room.chatStatus === 'READ_ONLY'

  // CHAT_010: 차단·해제한 뒤 목록을 새로 받아 입력창 자리와 목록의 이름 색을 바로 바꾼다. 상대 화면은 서버 알림으로 바뀐다
  const [isBlockConfirmOpen, setIsBlockConfirmOpen] = useState(false)
  const [isBlockPending, setIsBlockPending] = useState(false)
  const changeBlock = async (request) => {
    if (isBlockPending) return
    setIsBlockPending(true)
    try {
      await request(room.chatRoomId)
      await queryClient.invalidateQueries({ queryKey: ['chat-rooms'] })
    } catch (error) {
      window.alert(error.response?.data?.message || '서버에 연결할 수 없습니다.')
    } finally {
      setIsBlockPending(false)
    }
  }
  const handleBlock = () => {
    setIsBlockConfirmOpen(false)
    changeBlock(createChatBlock)
  }
  // 풀기는 되돌리는 동작이라 확인 없이 바로 푼다
  const handleUnblock = () => changeBlock(deleteChatBlock)

  // CHAT_011: 신고는 차단과 따로 한다. 읽기 전용·차단된 방도 신고할 수 있다
  const [report, setReport] = useState(null)
  const [isReportPending, setIsReportPending] = useState(false)
  const openReport = () => setReport({ reasonType: '', content: '', error: null })
  const handleReport = async () => {
    if (isReportPending) return
    const content = report.content.trim()
    if (!report.reasonType) return setReport({ ...report, error: '신고 사유를 골라 주세요.' })
    if (report.reasonType === 'ETC' && !content) return setReport({ ...report, error: '기타를 골랐으면 내용을 적어 주세요.' })
    setIsReportPending(true)
    try {
      await createChatReport(room.chatRoomId, report.reasonType, content || null)
      setReport(null)
      window.alert('신고가 접수됐어요. 관리자가 확인할게요.')
    } catch (error) {
      setReport(null)
      window.alert(error.response?.data?.message || '서버에 연결할 수 없습니다.')
    } finally {
      setIsReportPending(false)
    }
  }

  return (
    // CHAT_009: 읽기 전용이면 대화 칸을 연한 회색으로 바꿔 닫힌 방인 걸 한눈에 보이게 한다. 열어 둔 채 바뀌면 색이 천천히 바뀐다
    <section aria-label={`${partnerNameOf(room)} 채팅방`}
      className={clsx('flex h-[min(720px,calc(100dvh-10rem))] min-h-[480px] min-w-0 grow flex-col transition-colors duration-300',
        isReadOnly ? 'bg-gray-100' : 'bg-slate-50')}>
      <header className="flex items-center justify-between gap-3 border-b border-gray-200 bg-white px-5 py-3">
        <div className="flex min-w-0 items-center gap-3">
          <Link to="/chats" className="md:hidden" aria-label="채팅방 목록으로">
            <Icon name="back" />
          </Link>
          <div className="flex min-w-0 flex-col">
            <span className="min-w-0 font-bold"><PartnerName room={room} isAgent={isAgent} /></span>
            <span className="truncate text-sm text-gray-500">{propertyTitleOf(room)}</span>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {/* CHAT_011: 차단과 같은 빨간 계열이지만 바탕을 비워 다른 버튼으로 보이게 한다 */}
          <button type="button" onClick={openReport} disabled={isReportPending}
            className="inline-flex h-9 shrink-0 items-center rounded-lg border border-red-600 bg-white px-3 text-sm font-bold whitespace-nowrap text-red-600 hover:bg-red-50 disabled:cursor-wait disabled:opacity-60">
            신고
          </button>
          {/* CHAT_010: 요구사항의 "이름을 누르면 차단 버튼"을 한눈에 보이도록 머리글 오른쪽 버튼으로 둔다
              상대가 나를 차단했어도 나도 차단할 수 있다. 내가 차단했으면 [차단 해제] */}
          {room.blockStatus === 'BLOCKED_BY_ME' ? (
            <button type="button" onClick={handleUnblock} disabled={isBlockPending} className="btn-secondary btn-sm shrink-0 disabled:cursor-wait">
              차단 해제
            </button>
          ) : (
            <button type="button" onClick={() => setIsBlockConfirmOpen(true)} disabled={isBlockPending}
              className="inline-flex h-9 shrink-0 items-center rounded-lg bg-red-600 px-3 text-sm font-bold whitespace-nowrap text-white hover:bg-red-700 disabled:cursor-wait disabled:opacity-60">
              차단
            </button>
          )}
        </div>
      </header>
      {report && (
        <ConfirmModal
          title={`${partnerNameOf(room)}님을 신고할까요?`}
          description="신고 내용은 관리자에게만 전달되고 상대에게는 알리지 않아요."
          confirmLabel="신고"
          onConfirm={handleReport}
          onClose={() => setReport(null)}
        >
          <div className="flex flex-col gap-2">
            <SelectInput aria-label="신고 사유" options={REPORT_REASON_OPTIONS} value={report.reasonType}
              onChange={(event) => setReport({ ...report, reasonType: event.target.value, error: null })} />
            <TextArea aria-label="신고 내용" maxLength={REPORT_CONTENT_MAX_LENGTH} value={report.content}
              placeholder={report.reasonType === 'ETC' ? '자세한 내용 (필수)' : '자세한 내용 (선택)'}
              onChange={(event) => setReport({ ...report, content: event.target.value, error: null })} />
            <div className="flex justify-between gap-2 text-sm">
              <span className="text-red-600">{report.error}</span>
              <span className="shrink-0 text-gray-400">{report.content.length}/{REPORT_CONTENT_MAX_LENGTH}</span>
            </div>
          </div>
        </ConfirmModal>
      )}
      {isBlockConfirmOpen && (
        <ConfirmModal
          title={`${partnerNameOf(room)}님을 차단할까요?`}
          description="차단하면 서로 메시지와 사진을 보낼 수 없어요. 이전 대화는 그대로 남고, 언제든 풀 수 있어요."
          confirmLabel="차단"
          onConfirm={handleBlock}
          onClose={() => setIsBlockConfirmOpen(false)}
        />
      )}

      {/* RISK_007: 해당 매물 등록자(임대인)에게만 보이고 사유 상세·연체 금액은 보여주지 않는다
          참고: CHAT_002 기준으로 임대인은 중개사와만 채팅하므로 이 안내가 뜨는 경우가 있는지 팀 확인 필요 */}
      {room.isMyProperty && (
        <p className="border-b border-amber-200 bg-amber-50 px-5 py-2.5 text-sm text-amber-900">
          이 임차인은 월세 미납이나 연체 이력이 있는 임차인입니다
        </p>
      )}

      <div ref={scrollRef} onScroll={handleScroll} className="relative min-h-0 grow overflow-y-auto px-5 py-5">
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
            {/* 읽기 전용이면 이전 대화를 조금 흐리게 둔다. 글자는 그대로 읽을 수 있을 만큼만 */}
            <ol className={clsx('flex flex-col gap-2.5 transition-opacity duration-300', isReadOnly && 'opacity-70')}>
              {messages.map((message, index) => (
                <Fragment key={message.chatMessageId}>
                  {/* 바로 앞 메시지와 날짜가 다르면 그 위에 날짜 구분선을 둔다. 맨 위 메시지에도 둔다 */}
                  {(index === 0 || !isSameDay(messages[index - 1].createdAt, message.createdAt)) && (
                    <li className="flex items-center gap-3 py-1 text-xs text-gray-400">
                      <span className="h-px grow bg-gray-200" aria-hidden="true" />
                      <time dateTime={message.createdAt.slice(0, 10)} className="whitespace-nowrap">{formatMessageDate(message.createdAt)}</time>
                      <span className="h-px grow bg-gray-200" aria-hidden="true" />
                    </li>
                  )}
                  {/* 내 메시지는 말풍선 왼쪽에, 상대 메시지는 오른쪽에 "1"(상대가 아직 안 읽음)과 보낸 시각을 둔다 */}
                  <li className={clsx('flex max-w-[80%] items-end gap-1.5', isMineOf(message) ? 'flex-row-reverse self-end' : 'self-start')}>
                    <div className={clsx('min-w-0 overflow-hidden rounded-2xl', isMineOf(message) ? 'rounded-br-sm bg-blue-600 text-white' : 'rounded-bl-sm border border-gray-200 bg-white')}>
                      {message.messageType === 'IMAGE'
                        ? <ChatImage imageUrl={message.imageUrl} onExpired={refreshHistory} />
                        : <p className="px-4 py-2.5 text-[15px] break-words whitespace-pre-wrap">{message.content}</p>}
                    </div>
                    <span className={clsx('flex shrink-0 flex-col text-[11px] leading-4', isMineOf(message) ? 'items-end' : 'items-start')}>
                      {isUnreadByPartner(message) && (
                        <span className="font-bold text-amber-500">1<span className="sr-only"> 상대가 아직 안 읽음</span></span>
                      )}
                      <time dateTime={message.createdAt} className="whitespace-nowrap text-gray-400">{formatMessageTime(message.createdAt)}</time>
                    </span>
                  </li>
                </Fragment>
              ))}
            </ol>
          </>
        )}
      </div>
      {hasNewMessage && (
        <div className="relative">
          <button type="button" onClick={scrollToBottom} className="btn-primary btn-sm absolute bottom-3 left-1/2 -translate-x-1/2 shadow-lg">
            새 메시지 ↓
          </button>
        </div>
      )}

      {isReadOnly ? (
        // CHAT_009: 읽기 전용이면 입력창 대신 안내 문구. 이전 대화는 그대로 볼 수 있다
        <p className="border-t border-gray-300 bg-gray-200 px-5 py-4 text-center font-medium text-gray-600">{READ_ONLY_MESSAGES[room.readOnlyReason]}</p>
      ) : room.blockStatus === 'BLOCKED_BY_ME' ? (
        // CHAT_010: 내가 차단했으면 여기서도 바로 풀 수 있게 한다
        <div className="flex items-center justify-center gap-3 border-t border-gray-200 bg-white px-5 py-3.5 text-gray-600">
          차단한 사용자예요.
          <button type="button" onClick={handleUnblock} disabled={isBlockPending} className="btn-secondary btn-sm disabled:cursor-wait">차단 해제</button>
        </div>
      ) : room.blockStatus === 'BLOCKED_ME' ? (
        // 차단당한 사실을 직접 알리지 않는다
        <p className="border-t border-gray-200 bg-white px-5 py-4 text-center text-gray-600">지금은 메시지를 보낼 수 없어요.</p>
      ) : (
        <form onSubmit={handleSend} className="flex flex-col gap-1.5 border-t border-gray-200 bg-white px-4 py-3">
          {imagePreview && (
            <div className="flex items-center gap-3 rounded-xl bg-slate-50 p-2">
              <img src={imagePreview.url} alt="보낼 사진 미리보기" className="size-16 shrink-0 rounded-lg object-cover" />
              <span className="min-w-0 grow truncate text-sm text-gray-600">{isSendingImage ? '사진 보내는 중…' : imagePreview.file.name}</span>
              <button type="button" onClick={handleSendImage} disabled={isSendingImage} className="btn-primary btn-sm disabled:cursor-wait">보내기</button>
              <button type="button" onClick={() => setImagePreview(null)} disabled={isSendingImage} className="btn-secondary btn-sm">취소</button>
            </div>
          )}
          {/* 입력칸이 여러 줄로 커져도 사진 버튼과 [보내기]는 아래쪽에 붙는다 */}
          <div className="flex items-end gap-2">
            {/* 휴대폰에서는 사진 찍기·보관함을 고르는 창이 뜬다. 서버가 받는 JPEG·PNG만 고를 수 있게 한다 (CHAT_004) */}
            <label className={clsx('flex size-11 shrink-0 items-center justify-center rounded-lg text-gray-600 hover:bg-gray-100',
              isSendingImage ? 'cursor-wait opacity-50' : 'cursor-pointer')}>
              <Icon name="image" size={22} />
              <span className="sr-only">사진 보내기</span>
              <input type="file" accept={IMAGE_TYPES.join(',')} onChange={handleImageSelect} disabled={isSendingImage} className="sr-only" />
            </label>
            <label htmlFor="chat-input" className="sr-only">메시지</label>
            {/* Enter는 보내기, Shift+Enter는 줄바꿈. 한글을 조합하는 중에 누른 Enter는 보내지 않아 마지막 글자가 따로 가지 않는다
                글이 길어지면 다섯 줄쯤까지 칸이 자라고 그 뒤는 칸 안에서 스크롤한다 */}
            <textarea id="chat-input" rows={1} value={draft} onChange={(event) => setDraft(event.target.value)} maxLength={MESSAGE_MAX_LENGTH}
              onKeyDown={(event) => {
                if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return
                event.preventDefault()
                event.currentTarget.form.requestSubmit()
              }}
              placeholder="메시지를 입력하세요"
              className="max-h-32 min-h-11 min-w-0 grow resize-none rounded-xl border border-gray-300 px-4 py-2.5 [field-sizing:content]" />
            <button type="submit" disabled={isSending || !draft.trim()} className="btn-primary disabled:cursor-not-allowed disabled:opacity-50">
              {isSending ? '보내는 중…' : '보내기'}
            </button>
          </div>
          {sendError && <p role="alert" className="text-sm text-rose-700">{sendError}</p>}
        </form>
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
  // 보고 있는 방에 상대가 보낸 메시지를 대화 끝에 바로 붙인다 (CHAT_003)
  const queryClient = useQueryClient()
  // 보는 중에 상대 메시지가 오면 바로 읽음 처리해 빨간 숫자가 올라가지 않게 한다 (CHAT_007)
  // 상대가 읽었다는 알림이 오면 내 메시지의 "1"을 지운다. 내가 읽은 알림은 "1"과 상관없다
  const { isDisconnected } = useChatSocket(
    activeRoom?.chatRoomId,
    (chatRoomId, message) => {
      queryClient.setQueryData(messagesKeyOf(chatRoomId, user?.userId), (data) => appendMessage(data, message))
      if (message.senderId !== user?.userId) markReadIfVisible(chatRoomId)
    },
    (chatRoomId, read) => {
      if (read.userId === user?.userId) return
      queryClient.setQueryData(messagesKeyOf(chatRoomId, user?.userId), (data) => updatePartnerRead(data, read.lastReadMessageId))
    },
  )

  return (
    <div className="page-container">
      {/* 다시 이어지면 끊긴 사이 메시지를 새로 불러온다 */}
      {isDisconnected && (
        <p role="status" className="mb-3 rounded-xl bg-amber-50 px-4 py-2.5 text-sm text-amber-900">서버와의 연결이 끊겼어요. 잠시만 기다려주세요.</p>
      )}
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
