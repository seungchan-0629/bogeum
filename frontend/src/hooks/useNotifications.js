// [커스텀 훅] 알림 목록 화면과 헤더 종 말풍선이 같이 쓰는 안 읽은 수·알림 열기 (NOTIFY_007, NOTIFY_021)
// - 목록·말풍선 query key는 'notifications'로 시작한다. 알림이 바뀌면 이 묶음을 한 번에 새로 받는다
// - 안 읽은 수는 실시간으로 숫자가 오면 그대로 바꿔 넣으려고 key를 따로 둔다
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { getUnreadNotificationCount, readNotification } from '../api/notification/index.js'
import { getNotificationPath } from '../utils/notification.js'

export const NOTIFICATION_UNREAD_COUNT_KEY = ['notification-unread-count']

export function useUnreadNotificationCount(isEnabled = true) {
  return useQuery({
    queryKey: NOTIFICATION_UNREAD_COUNT_KEY,
    queryFn: ({ signal }) => getUnreadNotificationCount(signal),
    enabled: isEnabled,
  })
}

// 읽음 처리·모두 읽음이 끝나면 숫자와 목록을 새로 받는다 (실시간 연결이 끊겨 있어도 맞도록)
export function refreshNotifications(queryClient) {
  queryClient.invalidateQueries({ queryKey: NOTIFICATION_UNREAD_COUNT_KEY })
  queryClient.invalidateQueries({ queryKey: ['notifications'] })
}

// 알림을 누르면 안 읽은 것은 읽음 처리하고, 이동할 화면이 있으면 바로 옮긴다
// 읽음 처리는 화면을 옮긴 뒤에도 이어서 끝난다
export function useOpenNotification() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const read = useMutation({
    mutationFn: readNotification,
    onSuccess: () => refreshNotifications(queryClient),
  })

  return (notification) => {
    if (!notification.isRead) read.mutate(notification.notificationId)
    const path = getNotificationPath(notification)
    if (path) navigate(path)
  }
}
