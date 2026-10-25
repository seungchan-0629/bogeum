// [페이지] 내 계약 관리 (중개사 전용 메뉴) — /agent/contracts
// 수락한 매물마다 진행 단계(중개 수락 → 매물 정보 등록 → 매물 공개 → 계약 완료)를 보여주고 다음 할 일로 보낸다
// 요구사항: PROPERTY_010 (매물 상세 정보·사진 업로드), PROPERTY_011 (계약 정보 업로드)
// API: 수락한 신청 목록 GET /api/v1/agents/me/brokerage-requests?status=ACCEPTED (BE-023)
//      진행 단계 값이 명세에 없어 백엔드와 협의 필요
import { Link } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import { formatLeasePrice } from '../../utils/format.js'

const STEPS = ['중개 수락', '매물 정보 등록', '매물 공개', '계약 완료']

// TODO: BE-023 응답으로 교체. step: 1~4 (위 STEPS 순서)
const SAMPLE_CONTRACTS = [
  { propertyId: 1, title: '역삼동 오피스텔', leaseType: 'MONTHLY', deposit: 1000, monthlyRent: 65, step: 4 },
  { propertyId: 2, title: '공덕동 아파트', leaseType: 'JEONSE', deposit: 32000, monthlyRent: 0, step: 3 },
  { propertyId: 3, title: '봉천동 빌라', leaseType: 'MONTHLY', deposit: 500, monthlyRent: 45, step: 1 },
]

function NextAction({ contract }) {
  if (contract.step === 1) {
    return <Link to={`/agent/properties/${contract.propertyId}/edit`} className="btn-secondary btn-sm">매물 정보 올리기</Link>
  }
  if (contract.step < 4) {
    return (
      <span className="flex gap-2">
        <Link to={`/agent/properties/${contract.propertyId}/edit`} className="btn-secondary btn-sm">매물 정보 수정</Link>
        <Link to={`/agent/properties/${contract.propertyId}/contract`} className="btn-secondary btn-sm">계약서 올리기</Link>
      </span>
    )
  }
  return <Link to={`/agent/properties/${contract.propertyId}/contract`} className="btn-secondary btn-sm">계약 정보 보기</Link>
}

function AgentContractListPage() {
  return (
    <div className="page-container">
      <PageHeader title="내 계약 관리" description="수락한 매물의 진행 단계를 관리해요." />
      <ul className="flex flex-col gap-3">
        {SAMPLE_CONTRACTS.map((contract) => (
          <li key={contract.propertyId} className="card flex flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-col">
                <strong className="text-[17px]">{contract.title}</strong>
                <span className="text-sm text-gray-600">{formatLeasePrice(contract)}</span>
              </div>
              <NextAction contract={contract} />
            </div>
            <div className="flex flex-col gap-1.5">
              <div className="flex justify-between gap-3 text-sm">
                <span className="font-bold">{STEPS[contract.step - 1]}</span>
                <span className="text-gray-600">
                  {contract.step} / {STEPS.length}단계{contract.step < STEPS.length && ` · 다음: ${STEPS[contract.step]}`}
                </span>
              </div>
              <div
                role="progressbar"
                aria-label={`${contract.title} 진행 단계`}
                aria-valuemin={1}
                aria-valuemax={STEPS.length}
                aria-valuenow={contract.step}
                className="h-1.5 overflow-hidden rounded-full bg-gray-100"
              >
                <div className="h-full bg-blue-600" style={{ width: `${(contract.step / STEPS.length) * 100}%` }} />
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

export default AgentContractListPage
