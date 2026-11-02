// [페이지] 월세 결제 수단 등록 (임차인) — /mypage/payment-method
// 카드나 계좌 중 하나를 등록하면 매월 납부일에 자동 결제된다
// 요구사항: USER_013 (카드 — PG사 등록창), USER_014 (계좌 — CMS 업체 절차)
// 카드번호·계좌번호를 이 화면에서 입력받거나 표시하지 않는다. 외부 창의 결과만 받아 '등록됨'으로 보여준다
// API:
// - 카드: POST /api/v1/tenants/me/payment-card (BE-048) — PG사 창에서 받은 빌링키 정보 전달
// - 계좌: POST /api/v1/tenants/me/payment-account (BE-049) — CMS 등록·출금 동의 결과 전달
import PageHeader from '../../components/common/PageHeader.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'

// TODO: 현재 등록된 결제 수단 조회 API가 명세에 없어 백엔드와 협의 필요
const SAMPLE_REGISTERED = { type: 'CARD', registeredAt: '2026.10.02' }

function PaymentMethodPage() {
  // TODO: PG사 카드 등록 창 열기 → 완료 콜백에서 POST /api/v1/tenants/me/payment-card (BE-048). PG사 선정 후 연동
  const handleRegisterCard = () => {}

  // TODO: CMS 업체 계좌 등록·출금 동의 절차 열기 → 완료 후 POST /api/v1/tenants/me/payment-account (BE-049)
  const handleRegisterAccount = () => {}

  return (
    <div className="page-container">
      <PageHeader title="월세 결제 수단" description="카드나 계좌 중 하나를 등록하면 매월 납부일에 자동으로 결제돼요." />
      <p className="rounded-xl bg-slate-100 px-4 py-3 text-slate-800">
        보금은 카드번호와 계좌번호를 저장하지 않아요. 결제 대행사(PG)·CMS 업체 화면에서 안전하게 등록돼요.
      </p>
      <div className="grid gap-5 md:grid-cols-2">
        <section aria-labelledby="card-title" className="card flex flex-col gap-3 p-7">
          <div className="flex items-center justify-between">
            <h2 id="card-title" className="text-xl font-bold">카드</h2>
            {SAMPLE_REGISTERED.type === 'CARD' && <StatusBadge tone="blue">현재 사용 중</StatusBadge>}
          </div>
          <p className="text-gray-600">PG사 카드 등록 창에서 카드 정보 입력과 본인 인증을 진행해요.</p>
          {SAMPLE_REGISTERED.type === 'CARD' && <p className="text-sm text-gray-500">{SAMPLE_REGISTERED.registeredAt} 등록</p>}
          <button type="button" onClick={handleRegisterCard} className="btn-primary mt-auto">
            {SAMPLE_REGISTERED.type === 'CARD' ? '카드 다시 등록 (교체)' : '카드 등록하기'}
          </button>
        </section>
        <section aria-labelledby="account-title" className="card flex flex-col gap-3 p-7">
          <h2 id="account-title" className="text-xl font-bold">은행 계좌</h2>
          <p className="text-gray-600">CMS 업체 절차로 계좌 인증과 출금 동의를 진행해요.</p>
          <p className="text-sm text-gray-500">등록된 계좌 없음</p>
          <button type="button" onClick={handleRegisterAccount} className="btn-secondary mt-auto">계좌 등록하기</button>
        </section>
      </div>
      <p className="text-sm text-gray-600">새로 등록하면 기존 결제 수단이 새 수단으로 교체돼요.</p>
    </div>
  )
}

export default PaymentMethodPage
