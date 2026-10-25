// [페이지] 매물 중개 신청 / 매물 수정 (임대인)
// - /my-properties/new                 새 중개 신청 (PROPERTY_002)
// - /my-properties/:propertyId/edit    기존 매물 수정 (PROPERTY_006) — 같은 화면을 기존 값이 채워진 상태로 쓴다
// API:
// - 신청: POST /api/v1/properties/brokerage-requests (BE-020) — 거래 유형, 가격, 주소, 희망 중개사(없으면 랜덤)
// - 수정: PUT/PATCH /api/v1/properties/{propertyId} (BE-021)
// - 수정 화면 첫 값: GET /api/v1/properties/{propertyId} (BE-019)
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import clsx from 'clsx'
import PageHeader from '../../components/common/PageHeader.jsx'
import { Field, TextInput } from '../../components/common/FormControls.jsx'

const LEASE_TYPES = [
  { value: 'JEONSE', label: '전세' },
  { value: 'MONTHLY', label: '월세' },
  { value: 'SALE', label: '매매' },
]

// TODO: 수정 화면이면 GET /api/v1/properties/{propertyId} (BE-019) 응답으로 채운다
const SAMPLE_EDIT_VALUES = { leaseType: 'JEONSE', deposit: '32000', monthlyRent: '', address: '서울 마포구 공덕동 45', addressDetail: '1203호' }

function ChoiceButton({ isSelected, onClick, children }) {
  return (
    <button
      type="button"
      aria-pressed={isSelected}
      onClick={onClick}
      className={clsx('rounded-full px-4 py-2 text-sm', isSelected ? 'bg-blue-600 font-bold text-white' : 'border border-gray-300 bg-white hover:bg-gray-50')}
    >
      {children}
    </button>
  )
}

function BrokerageRequestFormPage() {
  const { propertyId } = useParams()
  const isEdit = Boolean(propertyId)
  const initialValues = isEdit ? SAMPLE_EDIT_VALUES : { leaseType: 'MONTHLY' }
  const navigate = useNavigate()
  const [leaseType, setLeaseType] = useState(initialValues.leaseType)
  const [wantsAgent, setWantsAgent] = useState(false)

  // TODO: 신청은 POST /api/v1/properties/brokerage-requests (BE-020), 수정은 PUT/PATCH /api/v1/properties/{propertyId} (BE-021)
  // 성공하면 내 매물 목록으로 돌아간다. 신청이 생기면 중개사에게 알림(NOTIFY_022)은 서버가 보낸다
  const handleSubmit = (event) => {
    event.preventDefault()
    navigate('/my-properties')
  }

  // TODO: 주소 검색 (다음 우편번호 서비스 등) 연동 방식 팀 결정 필요
  const handleAddressSearch = () => {}

  return (
    <div className="page-container">
      <PageHeader
        title={isEdit ? '매물 수정' : '매물 중개 신청'}
        description={isEdit ? '바꿀 항목만 고쳐 주세요.' : '조건과 주소를 입력하면 중개사에게 신청돼요.'}
      />
      <form onSubmit={handleSubmit} className="card flex max-w-2xl flex-col gap-5">
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-sm font-medium text-gray-700">거래 유형</legend>
          <div className="flex gap-2">
            {LEASE_TYPES.map((type) => (
              <ChoiceButton key={type.value} isSelected={leaseType === type.value} onClick={() => setLeaseType(type.value)}>
                {type.label}
              </ChoiceButton>
            ))}
          </div>
        </fieldset>

        {/* 매매는 매매가 하나, 전세는 보증금, 월세는 보증금 + 월세 */}
        {leaseType === 'SALE' ? (
          <Field label="매매가 (만원)" htmlFor="brokerage-sale-price">
            <TextInput id="brokerage-sale-price" inputMode="numeric" />
          </Field>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="보증금 (만원)" htmlFor="brokerage-deposit">
              <TextInput id="brokerage-deposit" inputMode="numeric" defaultValue={initialValues.deposit} />
            </Field>
            {leaseType === 'MONTHLY' && (
              <Field label="월세 (만원)" htmlFor="brokerage-rent">
                <TextInput id="brokerage-rent" inputMode="numeric" defaultValue={initialValues.monthlyRent} />
              </Field>
            )}
          </div>
        )}

        <Field label="주소" htmlFor="brokerage-address">
          <div className="flex gap-2">
            <TextInput id="brokerage-address" defaultValue={initialValues.address} readOnly placeholder="주소 검색을 눌러 주세요" />
            <button type="button" onClick={handleAddressSearch} className="btn-secondary h-12 shrink-0">주소 검색</button>
          </div>
        </Field>
        <Field label="상세 주소" htmlFor="brokerage-address-detail">
          <TextInput id="brokerage-address-detail" defaultValue={initialValues.addressDetail} />
        </Field>

        {!isEdit && (
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-2 text-sm font-medium text-gray-700">희망 중개사</legend>
            <div className="flex flex-wrap gap-2">
              <ChoiceButton isSelected={!wantsAgent} onClick={() => setWantsAgent(false)}>지정 안 함 (랜덤 배정)</ChoiceButton>
              <ChoiceButton isSelected={wantsAgent} onClick={() => setWantsAgent(true)}>직접 지정</ChoiceButton>
            </div>
            {/* TODO: 중개사 검색 API가 명세에 없어 백엔드와 협의 필요 */}
            {wantsAgent && (
              <>
                <label htmlFor="brokerage-agent" className="sr-only">중개사 검색</label>
                <TextInput id="brokerage-agent" placeholder="중개사무소 이름으로 검색" />
              </>
            )}
          </fieldset>
        )}

        <button type="submit" className="btn-primary h-12">{isEdit ? '수정 저장' : '중개 신청하기'}</button>
      </form>
    </div>
  )
}

export default BrokerageRequestFormPage
