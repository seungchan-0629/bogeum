// [페이지] 정산 계좌 등록 (임대인) — /mypage/settlement-account
// 임차인의 월세가 결제되면 이 계좌로 바로 지급된다 (SETTLE_001)
// 요구사항: USER_015
// API: POST /api/v1/landlords/me/settlement-account (BE-050)
// 등록 방식이 요구사항에 정해져 있지 않다. '계좌번호를 화면에서 직접 입력받지 않는다'는 원칙에 맞춰
// 외부 계좌 인증 창을 쓰는 것으로 만들었다. 방식은 팀 확인 필요
import PageHeader from '../../components/common/PageHeader.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'

// TODO: 현재 정산 계좌 조회 API가 명세에 없어 백엔드와 협의 필요 (은행명, 마스킹된 번호, 인증 여부)
const SAMPLE_ACCOUNT = { bankName: '국민은행', maskedNumber: '****1234' }

function SettlementAccountPage() {
  // TODO: 계좌 인증 업체 창 열기 → 완료 결과로 POST /api/v1/landlords/me/settlement-account (BE-050)
  const handleChangeAccount = () => {}

  return (
    <div className="page-container">
      <PageHeader title="정산 계좌" description="임차인의 월세가 결제되면 이 계좌로 바로 지급돼요." />
      <section className="card flex max-w-xl flex-col gap-4">
        <div className="flex items-center justify-between gap-3">
          <span className="text-gray-600">현재 계좌</span>
          <span className="flex items-center gap-2">
            <strong>{SAMPLE_ACCOUNT.bankName} {SAMPLE_ACCOUNT.maskedNumber}</strong>
            <StatusBadge tone="green">인증 완료</StatusBadge>
          </span>
        </div>
        <p className="rounded-xl bg-slate-100 px-4 py-3 text-sm text-slate-800">계좌 인증 업체 화면에서 본인 명의 계좌를 확인한 뒤 등록돼요.</p>
        <button type="button" onClick={handleChangeAccount} className="btn-primary">계좌 변경하기</button>
      </section>
    </div>
  )
}

export default SettlementAccountPage
