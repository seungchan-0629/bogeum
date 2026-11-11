import { useState } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
import DataTable from '../common/DataTable.jsx'
import StatusBadge from '../common/StatusBadge.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { api } from '../../utils/jwtUtil.js'
import { formatWon } from '../../utils/format.js'

const PAGE_SIZE = 20
const TRANSFER_STATUS = {
  REQUESTED: { label: '요청됨', tone: 'blue' },
  SUCCESS: { label: '완료', tone: 'green' },
  FAILED: { label: '실패', tone: 'red' },
  PENDING_CONFIRMATION: { label: '결과 확인 중', tone: 'amber' },
}

const formatDateTime = (value) => value ? value.replace('T', ' ').slice(0, 16) : '-'

function TransferHistory({ type, userId }) {
  const [page, setPage] = useState(0)
  const label = type === 'withdrawals' ? '출금' : '송금'
  const list = useInfiniteQuery({
    queryKey: ['rent-transfers', userId, type],
    queryFn: async ({ pageParam, signal }) => (await api.get(`/openbanking/transfers/${type}/me`, {
      params: { size: PAGE_SIZE, cursor: pageParam ?? undefined },
      signal,
    })).data,
    initialPageParam: null,
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.nextCursor : undefined,
    enabled: Boolean(userId),
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
  })
  const pages = list.data?.pages ?? []
  const currentPage = pages[page]
  const transfers = currentPage?.transfers ?? []
  const hasNextError = list.isFetchNextPageError && page === pages.length - 1
  const hasError = list.isError && (!list.isFetchNextPageError || hasNextError)

  const handleNext = async () => {
    if (list.isFetching || !currentPage?.hasNext) return
    if (page + 1 < pages.length) {
      setPage(page + 1)
      return
    }
    const result = await list.fetchNextPage()
    if (result.data?.pages.length > page + 1) setPage(page + 1)
  }

  const columns = [
    { label: '매물', render: (row) => <span>{row.roadAddress} {row.detailAddress}</span> },
    { label: `${label} 요청 일시`, render: (row) => formatDateTime(row.requestedAt) },
    { label: '금액', align: 'right', render: (row) => formatWon(row.amount) },
    {
      label: '상태',
      render: (row) => {
        const status = TRANSFER_STATUS[row.transferStatus]
        return <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
      },
    },
    { label: '비고', render: (row) => <span className="text-gray-600">{row.bankResponseMessage || '-'}</span> },
  ]

  return (
    <section aria-label={`${label} 내역`} className="flex flex-col gap-4">
      {list.isPending && <p role="status">{label} 내역을 불러오고 있어요.</p>}
      {hasError && (
        <div role="alert" className="flex flex-wrap items-center gap-3">
          <p className="text-red-600">{list.error.response?.data?.message || `${hasNextError ? '다음 페이지의 ' : ''}${label} 내역을 불러오지 못했어요.`}</p>
          <button type="button" className="btn-secondary btn-sm" disabled={list.isFetching} onClick={() => hasNextError ? handleNext() : list.refetch()}>다시 불러오기</button>
        </div>
      )}
      {list.data && (
        <>
          <DataTable columns={columns} rows={transfers} rowKey="openBankingTransferId" emptyMessage={`${label} 내역이 없어요.`} />
          <nav aria-label={`${label} 내역 페이지`} className="flex items-center justify-center gap-4">
            <button type="button" className="btn-secondary btn-sm disabled:opacity-40" disabled={page === 0 || list.isFetching} onClick={() => setPage(page - 1)}>이전 페이지</button>
            <span aria-current="page" aria-live="polite" className="text-sm text-gray-600">{page + 1}페이지 · {transfers.length}건</span>
            <button type="button" className="btn-secondary btn-sm disabled:opacity-40" disabled={!currentPage?.hasNext || list.isFetching} onClick={handleNext}>
              {list.isFetchingNextPage ? '불러오는 중…' : '다음 페이지'}
            </button>
          </nav>
        </>
      )}
    </section>
  )
}

function TransferHistoryTable({ type }) {
  const userId = useAuthStore((state) => state.user?.userId)
  return <TransferHistory key={`${userId}:${type}`} type={type} userId={userId} />
}

export default TransferHistoryTable
