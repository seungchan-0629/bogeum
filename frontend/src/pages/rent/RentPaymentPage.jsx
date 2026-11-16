// [페이지] 월세 납부 내역 (임차인) — /my-home/rent (내 집 관리의 첫 탭)
// 매물 선택 전에는 전체 출금 내역을, 선택 후에는 월세 요약과 해당 매물의 출금 내역을 보여준다.
import { useState } from 'react'
import { Link, useOutletContext } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import SummaryStrip from '../../components/common/SummaryStrip.jsx'
import TransferHistoryTable from '../../components/rent/TransferHistoryTable.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { api } from '../../utils/jwtUtil.js'
import { formatWon } from '../../utils/format.js'
import { getNextPaymentDate } from './rentPayment.js'

const WITHDRAWAL_DETAIL = {
  REQUESTED: '출금 요청됨',
  SUCCESS: '출금 완료',
  FAILED: '출금 실패',
  PENDING_CONFIRMATION: '출금 결과 확인 중',
}

function RentSummary({ contract, userId }) {
  const [today] = useState(() => new Date())
  const year = today.getFullYear()
  const month = today.getMonth() + 1
  const currentMonth = `${year}-${String(month).padStart(2, '0')}`
  const isMonthly = contract.leaseType === 'MONTHLY'
  const hasRentThisMonth = isMonthly && contract.startAt.slice(0, 7) <= currentMonth && contract.endAt.slice(0, 7) >= currentMonth
  const account = useQuery({
    queryKey: ['user-account', userId],
    queryFn: async ({ signal }) => {
      const response = await api.get('/openbanking/accounts/me', { signal })
      return response.status === 204 ? null : response.data
    },
    enabled: Boolean(userId),
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
  })
  const monthly = useQuery({
    queryKey: ['rent-withdrawal-summary', userId, contract.contractId, year, month],
    queryFn: async ({ signal }) => (await api.get('/openbanking/transfers/withdrawals/me', {
      params: { contractId: contract.contractId, year, month, size: 1 },
      signal,
    })).data,
    enabled: Boolean(userId) && hasRentThisMonth,
    retry: false,
  })
  const withdrawal = monthly.data?.transfers[0]
  const nextDate = getNextPaymentDate(contract, today, withdrawal?.transferStatus === 'SUCCESS')
  let rentDetail = '월세 납부 대상이 아니에요.'
  if (hasRentThisMonth) {
    if (monthly.isError) {
      rentDetail = <span role="alert">출금 여부를 확인하지 못했어요. <button type="button" onClick={() => monthly.refetch()} disabled={monthly.isFetching} className="text-blue-700 hover:underline">다시 확인</button></span>
    } else if (monthly.isPending) {
      rentDetail = '이번 달 출금 여부를 확인하고 있어요.'
    } else if (withdrawal) {
      const date = withdrawal.bankTranDate || withdrawal.requestedAt
      rentDetail = `${date.slice(5, 10).replace('-', '.')} ${WITHDRAWAL_DETAIL[withdrawal.transferStatus]}`
    } else {
      rentDetail = '이번 달 출금 내역이 없어요.'
    }
  } else if (isMonthly) {
    rentDetail = '이번 달은 계약 기간이 아니에요.'
  }

  const summary = [
    { label: '이번 달 월세', value: hasRentThisMonth ? formatWon(contract.monthlyRent) : '납부 대상 아님', detail: rentDetail },
    {
      label: '다음 납부일',
      value: nextDate ? `${nextDate.getFullYear() !== year ? `${nextDate.getFullYear()}년 ` : ''}${nextDate.getMonth() + 1}월 ${nextDate.getDate()}일` : '예정 없음',
      detail: nextDate ? account.isSuccess && account.data ? '오픈뱅킹 등록 계좌로 납부' : '납부 계좌 등록 여부를 확인해 주세요.' : '예정된 월세 납부일이 없어요.',
    },
    {
      label: '납부 계좌',
      value: account.isError ? '조회 실패' : account.isPending ? '확인 중' : account.data ? '계좌 등록됨' : '등록된 계좌 없음',
      detail: account.isError
        ? <span role="alert">계좌 정보를 불러오지 못했어요. <button type="button" onClick={() => account.refetch()} disabled={account.isFetching} className="text-blue-700 hover:underline">다시 확인</button></span>
        : account.isSuccess && <Link to="/mypage#account-registration-title" className="text-blue-700 hover:underline">{account.data ? '계좌 변경' : '계좌 등록'}</Link>,
    },
  ]

  return <SummaryStrip items={summary} />
}

function RentPaymentPage() {
  const { selectedContract } = useOutletContext()
  const userId = useAuthStore((state) => state.user?.userId)

  return (
    <div className="flex flex-col gap-5">
      {selectedContract && <RentSummary key={`${userId}:${selectedContract.contractId}`} contract={selectedContract} userId={userId} />}
      <h2 className="text-lg font-bold">{selectedContract ? '선택한 매물의 출금 내역' : '전체 출금 내역'}</h2>
      <TransferHistoryTable type="withdrawals" propertyId={selectedContract?.propertyId} />
    </div>
  )
}

export default RentPaymentPage
