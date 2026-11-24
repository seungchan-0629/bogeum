// [관리자 페이지] 신고 대화 — /admin/chats
// 신고된 1:1 대화만 열람한다. 왼쪽: 신고 목록 / 오른쪽: 신고 내용 + 원문 대화
// 요구사항: CHAT_001 (신고된 건만 열람). AI 요약(CHATBOT_009)은 별도 작업
// API:
// - 신고 목록: GET /admin/chat-reports?page= — 최근 신고부터 5개씩
// - 원문: GET /admin/chat-reports/{chatReportId}/messages?cursor= — 30개씩, 처음 열면 신고가 확인 상태가 된다
import { useEffect, useState } from 'react'
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import PageHeader from '../../../components/common/PageHeader.jsx'
import Pagination from '../../../components/common/Pagination.jsx'
import StatusBadge from '../../../components/common/StatusBadge.jsx'
import { getAdminChatReportMessages, getAdminChatReports } from '../../../api/admin/index.js'
import { useAuthStore } from '../../../stores/authStore.js'
import { REPORT_REASON_LABELS, REPORT_STATUS_BADGES } from '../../../constants/chat.js'

// "11.24"
const formatShortDate = (dateTime) => {
  const date = new Date(dateTime)
  return `${date.getMonth() + 1}.${date.getDate()}`
}
// "2026. 11. 24. 오후 1:20"
const formatDateTime = (dateTime) =>
  new Date(dateTime).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })

const errorMessageOf = (error) => error?.response?.data?.message || '서버에 연결할 수 없습니다.'

function ReportStatusBadge({ status }) {
  const badge = REPORT_STATUS_BADGES[status]
  return badge ? <StatusBadge tone={badge.tone}>{badge.label}</StatusBadge> : null
}

function AdminReportedChatPage() {
  const user = useAuthStore((state) => state.user)
  const [page, setPage] = useState(1)
  const [selectedId, setSelectedId] = useState(null)

  const list = useQuery({
    queryKey: ['admin-chat-reports', user?.userId, page],
    queryFn: ({ signal }) => getAdminChatReports(page, signal),
    enabled: user?.role === 'ADMIN',
    retry: false,
  })
  const reports = list.data?.reports ?? []
  // 고른 신고가 지금 쪽에 없으면 첫 신고를 보여 준다
  const selected = reports.find((report) => report.chatReportId === selectedId) ?? reports[0]

  const changePage = (nextPage) => {
    setPage(nextPage)
    setSelectedId(null)
  }

  return (
    <>
      <PageHeader title="신고 대화" description="신고된 대화만 열람할 수 있어요." />
      {list.isPending ? (
        <p className="text-sm text-gray-500">신고 목록을 불러오는 중이에요.</p>
      ) : list.isError ? (
        <p role="alert" className="text-sm text-red-600">{errorMessageOf(list.error)}</p>
      ) : reports.length === 0 ? (
        <p className="card text-center text-sm text-gray-500">신고된 대화가 없어요.</p>
      ) : (
        <div className="grid items-start gap-5 lg:grid-cols-[320px_1fr]">
          <div className="flex flex-col gap-4">
            <ul aria-label="신고 목록" className="flex flex-col gap-2.5">
              {reports.map((report) => (
                <li key={report.chatReportId}>
                  <button
                    type="button"
                    onClick={() => setSelectedId(report.chatReportId)}
                    aria-current={report.chatReportId === selected.chatReportId}
                    className={clsx('card flex w-full flex-col gap-1 py-4 text-left',
                      report.chatReportId === selected.chatReportId && 'border-blue-400 bg-blue-50')}
                  >
                    <span className="flex items-center justify-between gap-2">
                      <span className="flex min-w-0 items-center gap-2">
                        <strong className="truncate">{REPORT_REASON_LABELS[report.reasonType]}</strong>
                        <ReportStatusBadge status={report.chatReportStatus} />
                      </span>
                      <span className="shrink-0 text-sm text-gray-500">{formatShortDate(report.createdAt)}</span>
                    </span>
                    <span className="text-sm text-gray-600">{report.reporterName} → {report.reportedName}</span>
                    <span className="truncate text-sm text-gray-500">{report.roadAddress}</span>
                  </button>
                </li>
              ))}
            </ul>
            {list.data.totalPages > 1 && <Pagination page={list.data.page} totalPages={list.data.totalPages} onChange={changePage} />}
          </div>
          <ReportDetail key={selected.chatReportId} report={selected} />
        </div>
      )}
    </>
  )
}

function ReportDetail({ report }) {
  const queryClient = useQueryClient()
  const messages = useInfiniteQuery({
    queryKey: ['admin-chat-report-messages', report.chatReportId],
    queryFn: ({ pageParam, signal }) => getAdminChatReportMessages(report.chatReportId, pageParam, signal),
    initialPageParam: null,
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.nextCursor : undefined),
    retry: false,
  })

  // 처음 열면 서버가 확인으로 바꾸므로 목록을 새로 받아 배지를 바로 바꾼다
  const isLoaded = messages.isSuccess
  useEffect(() => {
    if (isLoaded && report.chatReportStatus === 'RECEIVED') queryClient.invalidateQueries({ queryKey: ['admin-chat-reports'] })
  }, [isLoaded, report.chatReportStatus, queryClient])

  const firstPage = messages.data?.pages[0]
  // 첫 쪽이 최근 대화라 거꾸로 이어 붙여 오래된 것부터 보여 준다
  const history = messages.data ? [...messages.data.pages].reverse().flatMap((page) => page.messages) : []
  const senderOf = (senderId) => {
    if (!firstPage) return ''
    if (senderId === firstPage.agent.userId) return `${firstPage.agent.name} (중개사)`
    return `${firstPage.inquirer.name} (${firstPage.roomType === 'TENANT_AGENT' ? '임차인' : '임대인'})`
  }

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <section aria-labelledby="report-detail-title" className="card flex flex-col gap-2">
        <h2 id="report-detail-title" className="flex flex-wrap items-center gap-2 font-bold">
          {REPORT_REASON_LABELS[report.reasonType]}
          <ReportStatusBadge status={report.chatReportStatus} />
          <span className="text-sm font-normal text-gray-500">{formatDateTime(report.createdAt)}</span>
        </h2>
        <p className="text-sm text-gray-700">신고한 사람 {report.reporterName} → 신고된 사람 {report.reportedName}</p>
        <p className="text-sm text-gray-500">{report.roadAddress}</p>
        {report.content && <p className="rounded-xl bg-gray-50 px-4 py-3 text-sm whitespace-pre-wrap">{report.content}</p>}
      </section>
      <section aria-labelledby="raw-chat-title" className="card flex flex-col gap-3 bg-slate-50">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="raw-chat-title" className="font-bold">원문 대화</h2>
          <p className="flex flex-wrap gap-3 text-xs text-gray-600">
            <span className="flex items-center gap-1.5"><span className="size-2.5 rounded-full border border-gray-300 bg-white" />왼쪽: 신고한 사람 {report.reporterName}</span>
            <span className="flex items-center gap-1.5"><span className="size-2.5 rounded-full bg-slate-700" />오른쪽: 신고된 사람 {report.reportedName}</span>
          </p>
        </div>
        {messages.isPending ? (
          <p className="text-sm text-gray-500">대화를 불러오는 중이에요.</p>
        ) : messages.isError && !messages.data ? (
          <p role="alert" className="text-sm text-red-600">{errorMessageOf(messages.error)}</p>
        ) : (
          <>
            {/* 관리자는 증거를 차근차근 읽으므로 자동으로 불러오지 않고 버튼으로 이전 대화를 더 받는다 */}
            {messages.hasNextPage && (
              <button type="button" onClick={() => messages.fetchNextPage()} disabled={messages.isFetchingNextPage}
                className="btn-secondary btn-sm self-center disabled:cursor-wait">
                {messages.isFetchingNextPage ? '불러오는 중' : '이전 대화 더 보기'}
              </button>
            )}
            {messages.isFetchNextPageError && <p role="alert" className="text-center text-sm text-red-600">{errorMessageOf(messages.error)}</p>}
            {history.length === 0 ? (
              <p className="text-sm text-gray-500">주고받은 메시지가 없어요.</p>
            ) : (
              <ol className="flex flex-col gap-1.5">
                {history.map((message, index) => {
                  // 신고한 사람의 메시지는 왼쪽, 신고된 사람의 메시지는 오른쪽에 둬 누가 한 말인지 바로 보이게 한다
                  const isReporter = message.senderId === report.reporterId
                  // 같은 사람이 이어 보내면 이름을 한 번만 보여 주고, 사람이 바뀌면 간격을 더 둔다
                  const isNewSender = index === 0 || history[index - 1].senderId !== message.senderId
                  return (
                    <li key={message.chatMessageId}
                      className={clsx('flex max-w-[80%] flex-col gap-1', isReporter ? 'items-start self-start' : 'items-end self-end', isNewSender && index > 0 && 'mt-3')}>
                      {isNewSender && <span className="text-xs font-medium text-gray-600">{senderOf(message.senderId)}</span>}
                      {message.messageType === 'IMAGE' ? (
                        message.imageUrl ? (
                          <a href={message.imageUrl} target="_blank" rel="noreferrer">
                            <img src={message.imageUrl} alt="보낸 사진" className="max-h-60 max-w-60 rounded-2xl border border-gray-200 object-cover" />
                          </a>
                        ) : (
                          <span className="text-sm text-gray-500">사진을 불러올 수 없어요</span>
                        )
                      ) : (
                        <span className={clsx('rounded-2xl px-4 py-2.5 text-sm break-words whitespace-pre-wrap',
                          isReporter ? 'rounded-tl-sm border border-gray-200 bg-white' : 'rounded-tr-sm bg-slate-700 text-white')}>
                          {message.content}
                        </span>
                      )}
                      <span className="text-[11px] text-gray-400">{formatDateTime(message.createdAt)}</span>
                    </li>
                  )
                })}
              </ol>
            )}
          </>
        )}
      </section>
    </div>
  )
}

export default AdminReportedChatPage
