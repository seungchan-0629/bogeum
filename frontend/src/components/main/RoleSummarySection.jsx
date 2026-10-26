// 로그인한 사용자의 역할별 요약
// 요약 전용 API가 없어 각 화면의 조회 API를 묶어 쓰는 것으로 가정했다. 백엔드와 요약 API 여부 협의 필요
import { Link } from 'react-router'
import { ROLE_LABELS } from '../../constants/auth.js'

// TODO: 아래 임시 값을 각 항목 주석의 API 응답으로 교체
const SAMPLE_SUMMARY_BY_ROLE = {
  TENANT: [
    // RENT_005: GET /api/v1/tenants/me/rent-payments?size=1 (BE-054) 의 가장 최근 payments[0]
    { label: '최근 월세 납부', value: '10회차', detail: '납부 완료 · 65만원', to: '/my-home/rent' },
    // AS_002: GET /api/v1/as-requests?status= (BE-070) 중 처리 완료(DONE)가 아닌 건 수
    { label: '진행 중인 AS', value: '1건', detail: '처리중', to: '/my-home/as' },
  ],
  LANDLORD: [
    // RENT_003: GET /api/v1/landlords/me/rents?year=&month= (BE-053). 매물별 조회라 매물 수만큼 호출하거나 합계 API 협의 필요
    { label: '이번 달 월세 수납', value: '2 / 3건', detail: '미입금 1건', to: '/rent/collections' },
    // SETTLE_002: GET /api/v1/landlords/me/settlements (BE-059) 의 최근 지급 상태
    { label: '최근 월세 지급', value: '지급 완료', detail: '65만원', to: '/rent/collections' },
    // AS_002: GET /api/v1/as-requests?status=RECEIVED (BE-070) 건 수
    { label: '새 AS 요청', value: '1건', detail: '접수 완료', to: '/care/as' },
  ],
  AGENT: [
    // PROPERTY_008: GET /api/v1/agents/me/brokerage-requests?status= (BE-023) 중 수락 전 건 수
    { label: '새 중개 신청', value: '2건', detail: '수락 대기', to: '/agent/requests' },
    // AGENT_003: GET /api/v1/agents/me/brokerages (BE-028). totalCount가 없어 건 수 표시는 협의 필요
    { label: '중개 완료', value: '12건', detail: '누적', to: '/agent/history' },
  ],
  ADMIN: [
    // RENT_007: GET /api/v1/admin/risk-candidates (BE-063) 의 totalCount
    { label: '위험 정보 후보', value: '3건', detail: '검토 필요', to: '/admin/risk-users' },
    // RENT_002: GET /api/v1/admin/rents/failures (BE-052) 의 totalCount
    { label: '월세 결제 실패·연체', value: '5건', detail: '확인 필요', to: '/admin/rent' },
    // AGENT_002: GET /api/v1/admin/agents (BE-076). 승인 대기만 거르는 조건이 없어 협의 필요
    { label: '중개사 가입 승인 대기', value: '2건', detail: '서류 검토', to: '/admin/agents' },
  ],
}

function RoleSummarySection({ user }) {
  const items = SAMPLE_SUMMARY_BY_ROLE[user.role] ?? []

  return (
    <section className="border-b border-gray-200 bg-white">
      <div className="mx-auto flex max-w-[1200px] flex-col gap-5 px-6 py-10">
        <h2 className="text-2xl font-extrabold">
          {ROLE_LABELS[user.role]}님, 한눈에 확인하세요
        </h2>
        <div className="grid grid-cols-[repeat(auto-fit,minmax(220px,1fr))] gap-4">
          {/* 항목을 누르면 해당 상세 화면으로 이동한다 */}
          {items.map((item) => (
            <Link key={item.label} to={item.to} className="flex flex-col gap-1.5 rounded-2xl border border-gray-200 bg-[#f3f7fd] p-5 hover:border-blue-300">
              <span className="text-sm text-gray-600">{item.label}</span>
              <span className="text-2xl font-extrabold text-gray-900">{item.value}</span>
              <span className="text-sm text-gray-600">{item.detail}</span>
            </Link>
          ))}
        </div>
      </div>
    </section>
  )
}

export default RoleSummarySection
