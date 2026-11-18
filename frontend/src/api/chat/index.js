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
