// [커스텀 훅] 알림 실시간 연결 — 로그인해 있는 동안 헤더에서 WebSocket(STOMP)으로 서버와 이어 둔다
// 요구사항: NOTIFY_007, NOTIFY_021 (안 읽은 알림 수를 바로 바꾸기)
// - 채팅 연결(useChatSocket)은 채팅 화면에서만 열려서, 다른 화면에서도 알림 수를 받으려고 따로 연다
// - /user/queue/notifications: 알림이 생기거나 읽히면 서버가 새 안 읽은 수({ unreadCount })를 통째로 보낸다
//   숫자는 그대로 바꿔 넣고, 열려 있는 알림 목록·말풍선은 새로 받는다
// - 연결: /ws, CONNECT 헤더에 로그인 토큰. 끊기면 3초마다 다시 잇고, 다시 이어지면 끊긴 사이 놓친 숫자·목록을 새로 받는다
import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Client } from '@stomp/stompjs'
import { getFreshAccessToken } from '../utils/jwtUtil.js'
import { NOTIFICATION_UNREAD_COUNT_KEY, refreshNotifications } from './useNotifications.js'

const RECONNECT_DELAY_MS = 3000

export function useNotificationSocket(isEnabled) {
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!isEnabled) return
    let hasConnected = false
    const client = new Client({
      brokerURL: `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`,
      reconnectDelay: RECONNECT_DELAY_MS,
      // 다시 이을 때마다 최신 토큰을 넣는다
      beforeConnect: async (stomp) => {
        stomp.connectHeaders = { Authorization: `Bearer ${await getFreshAccessToken()}` }
      },
      onConnect: () => {
        client.subscribe('/user/queue/notifications', (frame) => {
          queryClient.setQueryData(NOTIFICATION_UNREAD_COUNT_KEY, JSON.parse(frame.body).unreadCount)
          queryClient.invalidateQueries({ queryKey: ['notifications'] })
        })
        if (hasConnected) refreshNotifications(queryClient)
        hasConnected = true
      },
    })
    client.activate()
    return () => {
      client.deactivate()
    }
  }, [isEnabled, queryClient])
}
