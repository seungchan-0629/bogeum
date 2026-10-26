// [페이지] 계약서 올리기 (중개사) — /agent/properties/:propertyId/contract
// 계약이 체결되면 계약서 이미지와 계약 정보를 올린다. 등록하면 매물은 '계약 완료', 관련 채팅방은 읽기 전용이 된다 (CHAT_009)
// 요구사항: PROPERTY_011
// API: POST /api/v1/properties/{propertyId}/contracts (BE-027)
// 월 납부일은 월세 자동 결제(RENT_004)의 기준이 된다. 입력 칸 구성은 팀 확인 필요
import { useNavigate } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import FileDropzone from '../../components/common/FileDropzone.jsx'
import { Field, TextInput } from '../../components/common/FormControls.jsx'

function ContractUploadPage() {
  const navigate = useNavigate()

  // TODO: POST /api/v1/properties/{propertyId}/contracts (BE-027) — 계약서 이미지 + 계약 정보를 multipart로 보낸다
  // 임차인을 어떻게 지정할지(아이디 검색 등)는 백엔드와 협의 필요
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
          <Field label="임차인 아이디 (이메일)" htmlFor="contract-tenant">
            <TextInput id="contract-tenant" type="email" placeholder="tenant@example.com" />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="계약일" htmlFor="contract-date">
              <TextInput id="contract-date" type="date" />
            </Field>
            <Field label="월 납부일" htmlFor="contract-payment-day" hint="매월 며칠">
              <TextInput id="contract-payment-day" inputMode="numeric" placeholder="25" />
            </Field>
            <Field label="계약 시작일" htmlFor="contract-start">
              <TextInput id="contract-start" type="date" />
            </Field>
            <Field label="계약 종료일" htmlFor="contract-end">
              <TextInput id="contract-end" type="date" />
            </Field>
            <Field label="보증금 (만원)" htmlFor="contract-deposit">
              <TextInput id="contract-deposit" inputMode="numeric" defaultValue="1000" />
            </Field>
            <Field label="월세 (만원)" htmlFor="contract-rent">
              <TextInput id="contract-rent" inputMode="numeric" defaultValue="65" />
            </Field>
          </div>
          <button type="submit" className="btn-primary">계약 등록</button>
        </section>
      </form>
    </div>
  )
}

export default ContractUploadPage
