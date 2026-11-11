// [페이지] 월세 납부 내역 (임차인) — /my-home/rent (내 집 관리의 첫 탭)
// API: GET /openbanking/transfers/withdrawals/me?cursor=&size=20
// 카드번호·계좌번호는 어디에도 표시하지 않는다 (USER_013, USER_014)
import { Link } from 'react-router'
import SummaryStrip from '../../components/common/SummaryStrip.jsx'
import TransferHistoryTable from '../../components/rent/TransferHistoryTable.jsx'

function RentPaymentPage() {
  // TODO: 요약 값도 BE-054 응답(가장 최근 회차)과 결제 수단 등록 여부에서 계산한다
  const summary = [
    { label: '이번 달 월세', value: '650,000원', detail: '10.25 결제 완료' },
    { label: '다음 납부일', value: '11월 25일', detail: '등록된 카드로 자동 결제' },
    { label: '결제 수단', value: '카드 등록됨', detail: <Link to="/mypage/payment-method" className="text-blue-700 hover:underline">변경</Link> },
  ]

  return (
    <div className="flex flex-col gap-5">
      <SummaryStrip items={summary} />
      <TransferHistoryTable type="withdrawals" />
    </div>
  )
}

export default RentPaymentPage
