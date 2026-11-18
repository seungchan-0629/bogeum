// [커스텀 훅] 채팅방 열기 — 매물 상세 [채팅으로 문의하기], 내 매물 [담당 중개사와 연락하기], 중개 신청 [임대인과 연락하기]에서 쓴다
// 요구사항: CHAT_002 (채팅방 생성, 이미 있으면 기존 방으로 이동)
// - openChatRoom(propertyId): 방을 열고 /chats/{chatRoomId}로 이동한다. 어떤 방인지는 서버가 누른 사람으로 고른다
// - isOpening(propertyId): 여는 중이면 같은 버튼을 다시 누르지 못하게 막는다
// - errorOf(propertyId): 실패 안내 문구. 버튼 바로 아래에 보여 준다
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { createChatRoom } from '../api/chat/index.js'

export function useOpenChatRoom() {
  const navigate = useNavigate()
  const [openingId, setOpeningId] = useState(null)
  const [error, setError] = useState(null)

  const openChatRoom = async (propertyId) => {
    if (openingId !== null) return
    setOpeningId(propertyId)
    setError(null)
    try {
      const { chatRoomId } = await createChatRoom(propertyId)
      navigate(`/chats/${chatRoomId}`)
    } catch (caught) {
      setError({ propertyId, message: caught.response?.data?.message || '서버에 연결할 수 없습니다.' })
    } finally {
      setOpeningId(null)
    }
  }

  return {
    openChatRoom,
    isOpening: (propertyId) => openingId === propertyId,
    errorOf: (propertyId) => (error?.propertyId === propertyId ? error.message : null),
  }
}
