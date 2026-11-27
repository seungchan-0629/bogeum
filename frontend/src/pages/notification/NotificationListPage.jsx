// [페이지] 알림 목록 — /notifications (관리자는 /admin/notifications 에서 같은 화면을 쓴다)
// 누가: 임차인·임대인·중개사·관리자. 헤더 종 말풍선의 [전체 보기]를 누르면 이 화면으로 온다
// 요구사항: NOTIFY_001 (관리자), NOTIFY_007 (임대·임차인), NOTIFY_021 (중개사) — 최신순, 안 읽은 수 표시, 누르면 읽음 처리 후 관련 화면 이동
// API:
// - 목록: GET /notifications/me?cursor=&size=&unreadOnly= (20개씩, 스크롤이 바닥에 닿으면 다음 20개)
// - 안 읽은 수: GET /notifications/unread-count
// - 읽음 처리: PATCH /notifications/{notificationId}/read, 모두 읽음: PATCH /notifications/read-all
import { useEffect, useRef, useState } from 'react'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import PageHeader from '../../components/common/PageHeader.jsx'
import { Tabs } from '../../components/common/Tabs.jsx'
import NotificationItem from '../../components/notification/NotificationItem.jsx'
import { getNotifications, readAllNotifications } from '../../api/notification/index.js'
import { refreshNotifications, useOpenNotification, useUnreadNotificationCount } from '../../hooks/useNotifications.js'

const PAGE_SIZE = 20

const FILTER_TABS = [
  { key: 'ALL', label: '전체' },
  { key: 'UNREAD', label: '안 읽음' },
]

function NotificationListPage() {
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState('ALL')
  const unreadCount = useUnreadNotificationCount()
  const openNotification = useOpenNotification()

  // 안 읽음 탭은 서버가 거른 목록을 따로 받는다. 화면에서 거르면 20개 안의 안 읽은 것만 보인다
  const list = useInfiniteQuery({
    queryKey: ['notifications', 'list', filter],
    queryFn: ({ pageParam, signal }) => getNotifications({ cursor: pageParam, size: PAGE_SIZE, unreadOnly: filter === 'UNREAD' }, signal),
    initialPageParam: null,
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.nextCursor : undefined),
  })
  const notifications = list.data?.pages.flatMap((page) => page.notifications) ?? []

  const readAll = useMutation({
    mutationFn: readAllNotifications,
    onSuccess: () => refreshNotifications(queryClient),
  })

  // 목록 맨 아래 표시 칸이 화면에 들어오면 다음 목록을 불러온다. 불러오다 실패하면 자동으로 다시 부르지 않고 [다시 시도]를 보여 준다 (매물 목록과 같다)
  const sentinelRef = useRef(null)
  const { hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage } = list
  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || !hasNextPage || isFetchingNextPage || isFetchNextPageError) return
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) fetchNextPage()
    }, { rootMargin: '200px' })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage])

  return (
    <div className="page-container">
      <PageHeader
        title="알림"
        description={unreadCount.isSuccess ? `안 읽은 알림 ${unreadCount.data}개` : undefined}
        actions={(
          <button type="button" onClick={() => readAll.mutate()} disabled={!unreadCount.data || readAll.isPending}
            className="btn-secondary btn-sm disabled:opacity-40">모두 읽음</button>
        )}
      />
      {readAll.isError && <p role="alert" className="text-red-600">모두 읽음 처리를 하지 못했어요. 다시 시도해 주세요.</p>}
      <Tabs label="알림 보기" items={FILTER_TABS} value={filter} onChange={setFilter} />
      {list.isPending && <p role="status">알림을 불러오고 있어요.</p>}
      {list.isError && !list.isFetchNextPageError && <p role="alert">알림을 불러오지 못했어요.</p>}
      {list.isSuccess && notifications.length === 0 && (
        <p className="rounded-xl border border-dashed border-gray-300 px-6 py-10 text-center text-gray-600">
          {filter === 'UNREAD' ? '안 읽은 알림이 없어요.' : '받은 알림이 없어요.'}
        </p>
      )}
      {notifications.length > 0 && (
        <ul className="card p-0">
          {notifications.map((notification) => (
            <NotificationItem key={notification.notificationId} notification={notification} onOpen={openNotification} />
          ))}
        </ul>
      )}
      <div ref={sentinelRef} aria-hidden="true" />
      {isFetchingNextPage && <p role="status" className="text-center text-gray-600">알림을 더 불러오고 있어요.</p>}
      {isFetchNextPageError && (
        <div role="alert" className="flex flex-col items-center gap-2 text-gray-600">
          다음 알림을 불러오지 못했어요.
          <button type="button" onClick={() => fetchNextPage()} className="btn-secondary btn-sm">다시 시도</button>
        </div>
      )}
    </div>
  )
}

export default NotificationListPage
