// [알림 컴포넌트] 알림 한 줄 — 알림 목록 화면과 헤더 종 말풍선이 같이 쓴다 (NOTIFY_007, NOTIFY_021)
// 안 읽은 알림은 왼쪽 파란 점과 굵은 제목. 누르면 onOpen(notification)
import clsx from 'clsx'
import { formatNotificationTime } from '../../utils/notification.js'

function NotificationItem({ notification, onOpen }) {
  return (
    <li className="border-b border-gray-100 last:border-b-0">
      <button type="button" onClick={() => onOpen(notification)} className="flex w-full items-center gap-3.5 px-5 py-4 text-left hover:bg-slate-50">
        <span aria-hidden="true" className={clsx('size-2 shrink-0 rounded-full', notification.isRead ? 'bg-transparent' : 'bg-blue-600')} />
        <span className="flex min-w-0 grow flex-col">
          <span className={notification.isRead ? 'text-gray-800' : 'font-bold'}>
            {!notification.isRead && <span className="sr-only">안 읽음, </span>}
            {notification.title}
          </span>
          <span className="truncate text-sm text-gray-600">{notification.body}</span>
        </span>
        <span className="shrink-0 text-sm text-gray-500">{formatNotificationTime(notification.createdAt)}</span>
      </button>
    </li>
  )
}

export default NotificationItem
