// [페이지] 계약서 올리기 (중개사) — /agent/properties/:propertyId/contract
// 계약이 체결되면 계약서 이미지와 계약 정보를 올린다. 등록하면 매물은 '계약 완료', 관련 채팅방은 읽기 전용이 된다 (CHAT_009)
// 요구사항: PROPERTY_011
// API: POST /api/v1/properties/{propertyId}/contracts (BE-027)
// 월 납부일은 월세 자동 결제(RENT_004)의 기준이 된다. 입력 칸 구성은 팀 확인 필요
// 임대인·임차인은 가입할 때가 아니라 여기서 정해진다. 이 매물의 중개 신청자와 이 매물로 채팅한 사람 중에서 고른다
import { useState } from 'react'
import { useNavigate } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import FileDropzone from '../../components/common/FileDropzone.jsx'
import { DateInput, Field, TextInput } from '../../components/common/FormControls.jsx'
import ContractPartyField from '../../components/property/ContractPartyField.jsx'

// TODO: 계약 당사자 후보 API가 명세에 없어 백엔드와 협의 필요 (이 매물의 중개 신청자 + 이 매물로 채팅한 사람)
// - 이메일은 서버가 앞 두 글자만 남기고 가려서 보낸다 (maskedEmail)
const SAMPLE_PARTY_CANDIDATES = [
  { userId: 11, name: '기영서', maskedEmail: 'gi****@example.com', source: '중개 신청자' },
  { userId: 12, name: '기민준', maskedEmail: 'mi****@example.com', source: '채팅' },
  { userId: 13, name: '김보금', maskedEmail: 'bo****@example.com', source: '채팅' },
  { userId: 14, name: '최세입', maskedEmail: 'se****@example.com', source: '채팅' },
]

function ContractUploadPage() {
  const navigate = useNavigate()
  const [landlord, setLandlord] = useState(null)
  const [tenant, setTenant] = useState(null)

  // TODO: POST /api/v1/properties/{propertyId}/contracts (BE-027) — 계약서 이미지 + 계약 정보를 multipart로 보낸다
  // - 요청에 landlordId(landlord.userId), tenantId(tenant.userId), 중개보수를 넣는다. 필드 이름은 백엔드와 협의 필요
  const handleSubmit = (event) => {
    event.preventDefault()
    navigate('/agent/contracts')
  }

  return (
    <div className="page-container">
      <PageHeader title="계약서 올리기" description="역삼동 오피스텔 · 월세 1,000 / 65" />
      <form onSubmit={handleSubmit} className="grid items-start gap-5 lg:grid-cols-2">
        <section aria-labelledby="contract-image-title" className="card flex flex-col gap-4">
          <h2 id="contract-image-title" className="text-lg font-bold">계약서 이미지</h2>
          <FileDropzone title="계약서 사진이나 스캔본을 올려주세요" accept="image/*,application/pdf" multiple />
        </section>
        <section aria-labelledby="contract-info-title" className="card flex flex-col gap-4">
          <h2 id="contract-info-title" className="text-lg font-bold">계약 정보</h2>
          {/* 6칸 격자: 한 줄 = 6칸, 반 칸 = 3칸, 3등분 = 2칸. 좁은 화면에서는 한 줄에 하나씩 쌓인다 */}
          <div className="grid gap-3 sm:grid-cols-6">
            <div className="sm:col-span-6">
              <ContractPartyField
                id="contract-landlord"
                label="임대인"
                candidates={SAMPLE_PARTY_CANDIDATES}
                selected={landlord}
                excludeUserId={tenant?.userId}
                onSelect={setLandlord}
              />
            </div>
            <div className="sm:col-span-6">
              <ContractPartyField
                id="contract-tenant"
                label="임차인"
                candidates={SAMPLE_PARTY_CANDIDATES}
                selected={tenant}
                excludeUserId={landlord?.userId}
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
            <div className="sm:col-span-2">
              <Field label="보증금 (만원)" htmlFor="contract-deposit">
                <TextInput id="contract-deposit" inputMode="numeric" defaultValue="1000" />
              </Field>
            </div>
            <div className="sm:col-span-2">
              <Field label="월세 (만원)" htmlFor="contract-rent">
                <TextInput id="contract-rent" inputMode="numeric" defaultValue="65" />
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
            <div className="sm:col-span-3">
              <Field label="계약일" htmlFor="contract-date">
                <DateInput id="contract-date" />
              </Field>
            </div>
            <div className="sm:col-span-3">
              <Field label="중개보수 (만원)" htmlFor="contract-fee">
                <TextInput id="contract-fee" inputMode="numeric" placeholder="50" />
              </Field>
            </div>
          </div>
          <button type="submit" className="btn-primary">계약 등록</button>
        </section>
      </form>
    </div>
  )
}

export default ContractUploadPage
