// [페이지] 계약서 올리기 (중개사) — /agent/properties/:propertyId/contract
// 계약이 체결되면 계약서 이미지와 계약 정보를 올린다. 등록하면 매물은 '계약 완료'가 된다
// 요구사항: PROPERTY_011
// API:
// - 첫 값(거래 조건·임대인·임차인 후보): GET /contract/candidates?propertyId=
// 월 납부일은 월세 자동 결제(RENT_004)의 기준이 된다
// 임대인은 매물의 임대인으로 정해져 있고, 임차인은 이 매물로 상담한 사람 중에서 고른다
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import PageHeader from '../../components/common/PageHeader.jsx'
import FileDropzone from '../../components/common/FileDropzone.jsx'
import { DateInput, Field, TextInput } from '../../components/common/FormControls.jsx'
import ContractPartyField from '../../components/property/ContractPartyField.jsx'
import { formatLeasePrice } from '../../utils/format.js'
import { getContractCandidates } from '../../api/contract/index.js'

// 응답은 원 단위, 화면은 만원 단위라서 바꿔서 쓴다
const toManwonPrice = (candidates) => ({
  leaseType: candidates.leaseType,
  deposit: candidates.deposit / 10000,
  monthlyRent: candidates.monthlyRent / 10000,
})

// 공개 중(ACTIVE)인 매물만 계약을 등록할 수 있다. 서버도 같은 규칙으로 막는다 (PROPERTY_NOT_CONTRACTABLE)
function NotContractableNotice({ propertyId, propertyStatus }) {
  if (propertyStatus === 'REQUESTED') {
    return (
      <div className="card flex flex-col items-start gap-3">
        <p>매물을 공개한 뒤에 계약을 등록할 수 있어요.</p>
        <Link to={`/agent/properties/${propertyId}/edit`} className="btn-secondary btn-sm">매물 정보 올리기</Link>
      </div>
    )
  }
  if (propertyStatus === 'CONTRACTED') {
    return (
      <div className="card flex flex-col items-start gap-3">
        <p>이미 계약을 등록한 매물이에요.</p>
        <Link to="/agent/history" className="btn-secondary btn-sm">중개 이력 보기</Link>
      </div>
    )
  }
  return <div className="card"><p>관리자가 숨겼거나 삭제된 매물이라 계약을 등록할 수 없어요.</p></div>
}

function ContractUploadPage() {
  const { propertyId } = useParams()
  const candidatesQuery = useQuery({
    queryKey: ['contract-candidates', propertyId],
    queryFn: ({ signal }) => getContractCandidates(propertyId, signal),
  })

  if (candidatesQuery.isPending) {
    return <div className="page-container"><p role="status">계약 정보를 불러오고 있어요.</p></div>
  }
  if (candidatesQuery.isError) {
    const message = candidatesQuery.error.response?.status === 404
      ? '맡고 있는 매물이 아니거나 없는 매물이에요.'
      : '계약 정보를 불러오지 못했어요.'
    return <div className="page-container"><p role="alert">{message}</p></div>
  }

  const candidates = candidatesQuery.data
  const title = `${candidates.roadAddress} ${candidates.detailAddress ?? ''}`.trim()
  return (
    <div className="page-container">
      <PageHeader title="계약서 올리기" description={`${title} · ${formatLeasePrice(toManwonPrice(candidates))}`} />
      {candidates.propertyStatus === 'ACTIVE'
        ? <ContractForm candidates={candidates} />
        : <NotContractableNotice propertyId={propertyId} propertyStatus={candidates.propertyStatus} />}
    </div>
  )
}

// 조회가 끝난 뒤에 만들어 신청 가격을 첫 값으로 바로 채운다
function ContractForm({ candidates }) {
  const navigate = useNavigate()
  const [tenant, setTenant] = useState(null)
  const isMonthly = candidates.leaseType === 'MONTHLY'
  const price = toManwonPrice(candidates)

  // TODO: POST /contract-details — 계약 정보(JSON)와 계약서 이미지를 multipart로 보낸다
  const handleSubmit = (event) => {
    event.preventDefault()
    navigate('/agent/contracts')
  }

  return (
    <form onSubmit={handleSubmit} className="grid items-start gap-5 lg:grid-cols-2">
      <section aria-labelledby="contract-image-title" className="card flex flex-col gap-4">
        <h2 id="contract-image-title" className="text-lg font-bold">계약서 이미지</h2>
        <FileDropzone title="계약서 사진이나 스캔 이미지를 올려주세요" hint="최대 10장" multiple />
      </section>
      <section aria-labelledby="contract-info-title" className="card flex flex-col gap-4">
        <h2 id="contract-info-title" className="text-lg font-bold">계약 정보</h2>
        {/* 6칸 격자: 한 줄 = 6칸, 반 칸 = 3칸, 3등분 = 2칸. 좁은 화면에서는 한 줄에 하나씩 쌓인다 */}
        <div className="grid gap-3 sm:grid-cols-6">
          <div className="flex flex-col gap-1.5 sm:col-span-6">
            <span className="text-sm font-medium text-gray-700">임대인</span>
            <div className="flex h-12 items-center gap-2 rounded-xl border border-gray-200 bg-gray-50 px-4">
              <span className="font-bold">{candidates.landlord.name}</span>
              <span className="text-xs text-slate-400">{candidates.landlord.maskedEmail}</span>
            </div>
          </div>
          <div className="sm:col-span-6">
            <ContractPartyField
              id="contract-tenant"
              label="임차인"
              candidates={candidates.tenants}
              selected={tenant}
              onSelect={setTenant}
            />
          </div>
          <div className="sm:col-span-6">
            <Field label="계약 기간" htmlFor="contract-start">
              <div className="flex items-center gap-2">
                <DateInput id="contract-start" aria-label="계약 시작일" />
                <span aria-hidden="true" className="text-gray-500">~</span>
                <DateInput id="contract-end" aria-label="계약 종료일" />
              </div>
            </Field>
          </div>
          <div className={isMonthly ? 'sm:col-span-2' : 'sm:col-span-6'}>
            <Field label={isMonthly ? '보증금 (만원)' : '전세금 (만원)'} htmlFor="contract-deposit">
              <TextInput id="contract-deposit" inputMode="numeric" defaultValue={price.deposit} />
            </Field>
          </div>
          {isMonthly && (
            <>
              <div className="sm:col-span-2">
                <Field label="월세 (만원)" htmlFor="contract-rent">
                  <TextInput id="contract-rent" inputMode="numeric" defaultValue={price.monthlyRent} />
                </Field>
              </div>
              <div className="sm:col-span-2">
                <Field
                  label={<>월 납부일 <span className="text-xs font-normal text-gray-500">매월 며칠</span></>}
                  htmlFor="contract-payment-day"
                >
                  <TextInput id="contract-payment-day" inputMode="numeric" placeholder="25" />
                </Field>
              </div>
            </>
          )}
          <div className="sm:col-span-3">
            <Field label="계약일" htmlFor="contract-date">
              <DateInput id="contract-date" />
            </Field>
          </div>
          <div className="sm:col-span-3">
            <Field label="중개보수 (만원)" htmlFor="contract-fee">
              <TextInput id="contract-fee" inputMode="decimal" placeholder="50" />
            </Field>
          </div>
        </div>
        <button type="submit" className="btn-primary">계약 등록</button>
      </section>
    </form>
  )
}

export default ContractUploadPage
