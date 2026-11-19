import { api } from '../../utils/jwtUtil.js'

// 채팅 API는 /api 아래가 아니라서 공통 baseURL(/api)을 비우고 보낸다. 토큰·CSRF는 api 인터셉터가 붙인다.
const CHAT_CONFIG = { baseURL: '' }

// CHAT_002 매물로 대화할 채팅방을 열고 번호({ chatRoomId })를 받는다. 이미 있으면 기존 방 번호다
// 방 종류는 누른 사람으로 서버가 고른다: 임차인은 담당 중개사와의 문의 방, 임대인(본인 매물)·중개사(맡은 매물)는 임대인–중개사 방
// 열 수 없는 매물이면 404(PROPERTY_NOT_FOUND), 중개사가 수락하기 전 매물이면 409(CHAT_AGENT_NOT_ASSIGNED)
export async function createChatRoom(propertyId) {
  const { data } = await api.post('/chat/create', { propertyId }, CHAT_CONFIG)
  return data
}

// CHAT_003 채팅방에 글을 보내고 저장된 메시지를 받는다. 방을 보고 있는 상대에게는 STOMP로 바로 간다
// 빈 글·2,000자 초과 400(INVALID_REQUEST), 참여하지 않은 방 404(CHAT_ROOM_NOT_FOUND), 읽기 전용 방 409(CHAT_ROOM_READ_ONLY)
export async function createChatMessage(chatRoomId, content) {
  const { data } = await api.post(`/chat/rooms/${chatRoomId}/messages`, { content }, CHAT_CONFIG)
  return data
}

// CHAT_007 방의 마지막 메시지까지 읽었다고 남긴다 (204). 상대 화면의 "1"과 내 목록의 안 읽은 수가 바로 바뀐다
// 참여하지 않은 방이면 404(CHAT_ROOM_NOT_FOUND)
export async function updateChatRead(chatRoomId) {
  await api.patch(`/chat/rooms/${chatRoomId}/read`, null, CHAT_CONFIG)
}

// CHAT_005 내가 참여한 채팅방 전부 ({ rooms }). 최근 대화 순이고, 메시지가 없는 새 방은 만든 시각으로 줄을 선다
// 각 방: 상대 이름·사무소(partnerOfficeName, 상대가 중개사일 때), 매물 요약(원 단위), 마지막 메시지·시각,
//        안 읽은 수(unreadCount, CHAT_007), 읽기 전용이면 readOnlyReason(DELETED·CONTRACTED, CHAT_009)
export async function getChatRooms(signal) {
  const { data } = await api.get('/chat/rooms', { ...CHAT_CONFIG, signal })
  return data
}

// CHAT_006 채팅방의 이전 대화 ({ messages, nextCursor, hasNext }). messages는 오래된 것부터 30개
// cursor가 없으면 최근 30개, 있으면 그 메시지보다 오래된 30개. 참여하지 않은 방이면 404(CHAT_ROOM_NOT_FOUND)
export async function getChatMessages(chatRoomId, cursor, signal) {
  const { data } = await api.get(`/chat/rooms/${chatRoomId}/messages`, {
    ...CHAT_CONFIG,
    params: cursor == null ? {} : { cursor },
    signal,
  })
  return data
}
