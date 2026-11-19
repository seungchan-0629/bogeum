// [커스텀 훅] 채팅 실시간 연결 — 채팅 화면(/chats)에 있는 동안만 WebSocket(STOMP)으로 서버와 이어 둔다
// 요구사항: CHAT_003 (상대가 보낸 메시지를 바로 받기), CHAT_005 (채팅 목록 순서·마지막 메시지 갱신), CHAT_007 (상대가 읽으면 "1" 지우기)
// - 연결: /ws, CONNECT 헤더에 로그인 토큰. 끊기면 3초마다 다시 잇는다
// - /user/queue/chat-rooms: 내 방에 새 메시지가 생기면 채팅 목록을 다시 불러온다
// - /topic/chat-rooms/{chatRoomId}: 보고 있는 방의 새 메시지를 onRoomMessage(chatRoomId, message)로 넘긴다. 방을 바꾸면 구독도 바꾼다
// - /topic/chat-rooms/{chatRoomId}/read: 누가 어디까지 읽었는지 onRoomRead(chatRoomId, { userId, lastReadMessageId })로 넘긴다 (CHAT_007)
// - 다시 이어지면 끊긴 사이 메시지를 채우려고 목록과 대화를 새로 불러온다
// - isDisconnected: 서버와 연결이 끊겨 다시 잇는 중인지. 화면 안내에 쓴다
import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Client } from '@stomp/stompjs'
import { getFreshAccessToken } from '../utils/jwtUtil.js'

const RECONNECT_DELAY_MS = 3000

export function useChatSocket(chatRoomId, onRoomMessage, onRoomRead) {
  const queryClient = useQueryClient()
  const clientRef = useRef(null)
  const onRoomMessageRef = useRef(onRoomMessage)
  const onRoomReadRef = useRef(onRoomRead)
  // 이어질 때마다 늘려서, 다시 이어진 뒤 방 구독을 새로 한다
  const [connectionCount, setConnectionCount] = useState(0)
  const [isDisconnected, setIsDisconnected] = useState(false)

  useEffect(() => {
    onRoomMessageRef.current = onRoomMessage
    onRoomReadRef.current = onRoomRead
  })

  useEffect(() => {
    const client = new Client({
      brokerURL: `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`,
      reconnectDelay: RECONNECT_DELAY_MS,
      // 다시 이을 때마다 최신 토큰을 넣는다
      beforeConnect: async (stomp) => {
        stomp.connectHeaders = { Authorization: `Bearer ${await getFreshAccessToken()}` }
      },
      onConnect: () => {
        client.subscribe('/user/queue/chat-rooms', () => queryClient.invalidateQueries({ queryKey: ['chat-rooms'] }))
        setIsDisconnected(false)
        setConnectionCount((count) => {
          if (count > 0) {
            queryClient.invalidateQueries({ queryKey: ['chat-rooms'] })
            queryClient.invalidateQueries({ queryKey: ['chat-messages'] })
          }
          return count + 1
        })
      },
      onWebSocketClose: () => setIsDisconnected(true),
    })
    clientRef.current = client
    client.activate()
    return () => {
      client.deactivate()
    }
  }, [queryClient])

  useEffect(() => {
    const client = clientRef.current
    if (!chatRoomId || !client?.connected) return
    const subscriptions = [
      client.subscribe(`/topic/chat-rooms/${chatRoomId}`, (frame) => onRoomMessageRef.current(chatRoomId, JSON.parse(frame.body))),
      client.subscribe(`/topic/chat-rooms/${chatRoomId}/read`, (frame) => onRoomReadRef.current(chatRoomId, JSON.parse(frame.body))),
    ]
    // 끊긴 연결의 구독은 이미 사라져서 풀 필요가 없다
    return () => {
      if (client.connected) subscriptions.forEach((subscription) => subscription.unsubscribe())
    }
  }, [chatRoomId, connectionCount])

  return { isDisconnected }
}
