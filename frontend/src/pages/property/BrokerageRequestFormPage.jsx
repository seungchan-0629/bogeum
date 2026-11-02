// [페이지] 매물 중개 신청 / 매물 수정 (임대인)
// - /my-properties/new                 새 중개 신청 (PROPERTY_002)
// - /my-properties/:propertyId/edit    기존 매물 수정 (PROPERTY_006) — 같은 화면을 기존 값이 채워진 상태로 쓴다
// API:
// - 신청: POST /property/brokerage-requests (BE-020) — 거래 유형, 가격, 주소, 희망 중개사(없으면 랜덤)
// - 수정: PUT/PATCH /api/v1/properties/{propertyId} (BE-021)
// - 수정 화면 첫 값: GET /api/v1/properties/{propertyId} (BE-019)
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import clsx from 'clsx'
import PageHeader from '../../components/common/PageHeader.jsx'
import { Field, TextInput } from '../../components/common/FormControls.jsx'
import { createBrokerageRequest } from '../../api/property/index.js'
import { searchRoadAddress } from '../../utils/postcode.js'

const LEASE_TYPES = [
  { value: 'JEONSE', label: '전세' },
  { value: 'MONTHLY', label: '월세' },
  { value: 'SALE', label: '매매' },
]

// 화면은 만원 단위로 받고, 서버에는 원 단위로 보낸다
const MANWON = 10000

const amountText = z.string().trim().regex(/^\d*$/, '숫자만 입력해 주세요.')

// 서버도 같은 규칙으로 다시 검사한다 (INVALID_LEASE_PRICE)
const brokerageSchema = z
  .object({
    leaseType: z.enum(['JEONSE', 'MONTHLY', 'SALE']),
    deposit: amountText,
    monthlyRent: amountText,
    roadAddress: z.string().min(1, '주소 검색으로 주소를 입력해 주세요.'),
    detailAddress: z.string().trim().max(100, '상세 주소는 100자 이하로 입력해 주세요.'),
  })
  .superRefine((values, context) => {
    const addIssue = (path, message) => context.addIssue({ code: 'custom', path: [path], message })
    // 서버의 임대 유형(LeaseType)에 매매가 아직 없다
    if (values.leaseType === 'SALE') {
      addIssue('leaseType', '매매 중개 신청은 아직 준비 중이에요.')
      return
    }
    if (values.deposit === '') addIssue('deposit', '보증금을 입력해 주세요.')
    else if (values.leaseType === 'JEONSE' && Number(values.deposit) === 0) addIssue('deposit', '전세는 보증금을 0보다 크게 입력해 주세요.')
    if (values.leaseType === 'MONTHLY' && !(Number(values.monthlyRent) > 0)) addIssue('monthlyRent', '월세를 0보다 크게 입력해 주세요.')
  })

// TODO: 수정 화면이면 GET /api/v1/properties/{propertyId} (BE-019) 응답으로 채운다
const SAMPLE_EDIT_VALUES = { leaseType: 'JEONSE', deposit: '32000', monthlyRent: '', roadAddress: '서울 마포구 공덕동 45', detailAddress: '1203호' }
const NEW_VALUES = { leaseType: 'MONTHLY', deposit: '', monthlyRent: '', roadAddress: '', detailAddress: '' }

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

function ErrorText({ error }) {
  return error ? <p className="text-sm text-red-600">{error.message}</p> : null
}

function BrokerageRequestFormPage() {
  const { propertyId } = useParams()
  const isEdit = Boolean(propertyId)
  const navigate = useNavigate()
  const [wantsAgent, setWantsAgent] = useState(false)
  const {
    register,
    handleSubmit,
    setValue,
    control,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(brokerageSchema),
    defaultValues: isEdit ? SAMPLE_EDIT_VALUES : NEW_VALUES,
  })
  const leaseType = useWatch({ control, name: 'leaseType' })

  // 성공하면 내 매물 목록으로 돌아간다. 신청이 생기면 중개사에게 알림(NOTIFY_022)은 서버가 보낸다
  const onSubmit = async (values) => {
    // TODO: 수정은 PUT/PATCH /api/v1/properties/{propertyId} (BE-021)
    if (isEdit) {
      navigate('/my-properties')
      return
    }
    try {
      // TODO: 중개사 검색 API가 생기면 직접 지정한 중개사의 agentId를 함께 보낸다. 지금은 서버가 배정한다
      await createBrokerageRequest({
        leaseType: values.leaseType,
        deposit: Number(values.deposit) * MANWON,
        monthlyRent: values.leaseType === 'MONTHLY' ? Number(values.monthlyRent) * MANWON : 0,
        roadAddress: values.roadAddress,
        detailAddress: values.detailAddress || null,
      })
    } catch (error) {
      window.alert(error.response?.data?.message || '서버에 연결할 수 없습니다.')
      return
    }
    window.alert('중개 신청이 완료되었어요.')
    navigate('/my-properties')
  }

  const handleAddressSearch = async () => {
    try {
      const address = await searchRoadAddress()
      if (address) setValue('roadAddress', address, { shouldValidate: true })
    } catch (error) {
      window.alert(error.message)
    }
  }

  return (
    <div className="page-container">
      <PageHeader
        title={isEdit ? '매물 수정' : '매물 중개 신청'}
        description={isEdit ? '바꿀 항목만 고쳐 주세요.' : '조건과 주소를 입력하면 중개사에게 신청돼요.'}
      />
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="card flex max-w-2xl flex-col gap-5">
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-sm font-medium text-gray-700">거래 유형</legend>
          <div className="flex gap-2">
            {LEASE_TYPES.map((type) => (
              <ChoiceButton key={type.value} isSelected={leaseType === type.value} onClick={() => setValue('leaseType', type.value, { shouldValidate: true })}>
                {type.label}
              </ChoiceButton>
            ))}
          </div>
          <ErrorText error={errors.leaseType} />
        </fieldset>

        {/* 매매는 매매가 하나, 전세는 보증금, 월세는 보증금 + 월세 */}
        {leaseType === 'SALE' ? (
          <Field label="매매가 (만원)" htmlFor="brokerage-sale-price">
            <TextInput id="brokerage-sale-price" inputMode="numeric" />
          </Field>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="보증금 (만원)" htmlFor="brokerage-deposit">
              <TextInput id="brokerage-deposit" inputMode="numeric" {...register('deposit')} />
              <ErrorText error={errors.deposit} />
            </Field>
            {leaseType === 'MONTHLY' && (
              <Field label="월세 (만원)" htmlFor="brokerage-rent">
                <TextInput id="brokerage-rent" inputMode="numeric" {...register('monthlyRent')} />
                <ErrorText error={errors.monthlyRent} />
              </Field>
            )}
          </div>
        )}

        <Field label="주소" htmlFor="brokerage-address">
          <div className="flex gap-2">
            <TextInput id="brokerage-address" readOnly placeholder="주소 검색을 눌러 주세요" onClick={handleAddressSearch} {...register('roadAddress')} />
            <button type="button" onClick={handleAddressSearch} className="btn-secondary h-12 shrink-0">주소 검색</button>
          </div>
          <ErrorText error={errors.roadAddress} />
        </Field>
        <Field label="상세 주소" htmlFor="brokerage-address-detail">
          <TextInput id="brokerage-address-detail" placeholder="동·호수 (선택)" {...register('detailAddress')} />
          <ErrorText error={errors.detailAddress} />
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
                <p className="text-sm text-gray-500">중개사 검색은 준비 중이에요. 지금은 같은 지역 중개사가 배정돼요.</p>
              </>
            )}
          </fieldset>
        )}

        <button type="submit" disabled={isSubmitting} className="btn-primary h-12 disabled:opacity-60">{isEdit ? '수정 저장' : '중개 신청하기'}</button>
      </form>
    </div>
  )
}

export default BrokerageRequestFormPage
