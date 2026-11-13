// [페이지] 매물 중개 신청 / 매물 수정 (임대인)
// - /my-properties/new                 새 중개 신청 (PROPERTY_002)
// - /my-properties/:propertyId/edit    기존 매물 수정 (PROPERTY_006) — 같은 화면을 기존 값이 채워진 상태로 쓴다
// API:
// - 신청: POST /property/brokerage-requests — 거래 유형, 가격, 주소, 희망 중개사(없으면 랜덤)
// - 수정 화면 첫 값: GET /property/mine/{propertyId}
// - 수정: PUT /property/mine/{propertyId} — 거래 유형·가격만 바꾼다. 주소가 바뀌면 다른 집이라 삭제 후 새로 신청한다
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import clsx from 'clsx'
import PageHeader from '../../components/common/PageHeader.jsx'
import { Field, TextInput } from '../../components/common/FormControls.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { createBrokerageRequest, getMyProperty, updateMyProperty } from '../../api/property/index.js'
import { searchRoadAddress } from '../../utils/postcode.js'

const LEASE_TYPES = [
  { value: 'JEONSE', label: '전세' },
  { value: 'MONTHLY', label: '월세' },
  { value: 'SALE', label: '매매' },
]

// 화면은 만원 단위로 받고, 서버에는 원 단위로 보낸다
const MANWON = 10000

// 서버는 원 단위 15자리까지 받아서 만원 단위로는 11자리까지다
const amountText = z.string().trim().regex(/^\d*$/, '숫자만 입력해 주세요.').max(11, '금액이 너무 커요.')

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

const NEW_VALUES = { leaseType: 'MONTHLY', deposit: '', monthlyRent: '', roadAddress: '', detailAddress: '' }

// 수정 화면 첫 값. 서버는 원 단위라 만원으로 바꾸고, 전세는 월세 칸을 비운다
const toFormValues = (property) => ({
  leaseType: property.leaseType,
  deposit: String(property.deposit / MANWON),
  monthlyRent: property.leaseType === 'MONTHLY' ? String(property.monthlyRent / MANWON) : '',
  roadAddress: property.roadAddress,
  detailAddress: property.detailAddress ?? '',
})

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

// property가 있으면 수정, 없으면 새 신청
function BrokerageForm({ property }) {
  const isEdit = Boolean(property)
  // 계약이 끝난 매물은 보여 주기만 한다. 목록에서는 월세·AS로 보내지만 주소로 직접 들어올 수 있다
  const isLocked = isEdit && !property.isUpdatable
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
    defaultValues: isEdit ? toFormValues(property) : NEW_VALUES,
  })
  const leaseType = useWatch({ control, name: 'leaseType' })
  // 매매는 아직 신청할 수 없어 아래 입력 칸을 모두 막는다
  const isSale = leaseType === 'SALE'

  // 성공하면 내 매물 목록으로 돌아간다. 신청이 생기면 중개사에게 알림(NOTIFY_022)은 서버가 보낸다
  const onSubmit = async (values) => {
    const price = {
      leaseType: values.leaseType,
      deposit: Number(values.deposit) * MANWON,
      monthlyRent: values.leaseType === 'MONTHLY' ? Number(values.monthlyRent) * MANWON : 0,
    }
    try {
      if (isEdit) {
        await updateMyProperty(property.propertyId, price)
      } else {
        // TODO: 중개사 검색 API가 생기면 직접 지정한 중개사의 agentId를 함께 보낸다. 지금은 서버가 배정한다
        await createBrokerageRequest({ ...price, roadAddress: values.roadAddress, detailAddress: values.detailAddress || null })
      }
    } catch (error) {
      window.alert(error.response?.data?.message || '서버에 연결할 수 없습니다.')
      return
    }
    window.alert(isEdit ? '수정되었어요.' : '중개 신청이 완료되었어요.')
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
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="card flex max-w-2xl flex-col gap-5">
      {isLocked && <p role="status" className="text-sm text-gray-600">계약이 끝난 매물은 수정할 수 없어요.</p>}
      {/* 숨김은 관리자가 확인 중인 매물이다. 고쳐도 숨김은 그대로고 다시 공개는 관리자가 한다 (RISK_005) */}
      {isEdit && property.propertyStatus === 'HIDDEN' && (
        <p className="text-sm text-amber-800">수정해도 숨김은 유지되고, 관리자가 확인한 뒤 다시 공개돼요.</p>
      )}

      <fieldset disabled={isLocked} className={clsx('flex min-w-0 flex-col gap-5', isLocked && '[&_button]:cursor-not-allowed [&_input]:bg-gray-100 [&_input]:text-gray-500')}>
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-sm font-medium text-gray-700">거래 유형</legend>
          <div className="flex gap-2">
            {LEASE_TYPES.map((type) => (
              <ChoiceButton key={type.value} isSelected={leaseType === type.value} onClick={() => setValue('leaseType', type.value, { shouldValidate: true })}>
                {type.label}
              </ChoiceButton>
            ))}
          </div>
          {isSale ? <p className="text-sm text-red-600">매매 중개 신청은 아직 준비 중이에요.</p> : <ErrorText error={errors.leaseType} />}
        </fieldset>

        {/* disabled fieldset은 안의 입력 칸과 버튼을 한 번에 막는다 */}
        <fieldset
          disabled={isSale}
          className={clsx('flex min-w-0 flex-col gap-5', isSale && 'cursor-not-allowed text-gray-400 [&_button]:bg-gray-100 [&_button]:text-gray-400 [&_input]:bg-gray-100 [&_input]:text-gray-400 [&_label]:text-gray-400 [&_legend]:text-gray-400')}
        >
          {/* 매매는 매매가 하나, 전세는 보증금, 월세는 보증금 + 월세 */}
          {isSale ? (
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

          {/* 수정 화면의 주소는 보여 주기만 한다. 주소가 바뀌면 다른 집이라 서버도 받지 않는다 */}
          {isEdit ? (
            <div className="flex flex-col gap-5 [&_input]:bg-gray-100 [&_input]:text-gray-500">
              <Field label="주소" htmlFor="brokerage-address" hint="주소를 바꾸려면 삭제 후 새로 신청해 주세요.">
                <TextInput id="brokerage-address" readOnly {...register('roadAddress')} />
              </Field>
              <Field label="상세 주소" htmlFor="brokerage-address-detail">
                <TextInput id="brokerage-address-detail" readOnly {...register('detailAddress')} />
              </Field>
            </div>
          ) : (
            <>
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
            </>
          )}

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
        </fieldset>
      </fieldset>

      {isLocked ? (
        <Link to="/my-properties" className="btn-secondary h-12">내 매물로</Link>
      ) : (
        <button type="submit" disabled={isSubmitting || isSale} className="btn-primary h-12 disabled:opacity-60">{isEdit ? '수정 저장' : '중개 신청하기'}</button>
      )}
    </form>
  )
}

function BrokerageRequestFormPage() {
  const { propertyId } = useParams()
  const isEdit = Boolean(propertyId)
  const user = useAuthStore((state) => state.user)
  // 폼은 처음 그릴 때의 값으로 채워져서, 지난번에 남은 값 대신 늘 새로 불러온 값으로 그린다
  const property = useQuery({
    queryKey: ['my-property', user?.userId, propertyId],
    queryFn: ({ signal }) => getMyProperty(propertyId, signal),
    enabled: isEdit,
    gcTime: 0,
    // 없는 매물(404)은 다시 시도해도 같아서 바로 안내한다
    retry: (failureCount, error) => error.response?.status !== 404 && failureCount < 3,
  })

  return (
    <div className="page-container">
      <PageHeader
        title={isEdit ? '매물 수정' : '매물 중개 신청'}
        description={isEdit ? '거래 유형과 가격만 바꿀 수 있어요.' : '조건과 주소를 입력하면 중개사에게 신청돼요.'}
      />
      {!isEdit && <BrokerageForm />}
      {isEdit && property.isPending && <p role="status">매물 정보를 불러오고 있어요.</p>}
      {isEdit && property.isError && (
        <div role="alert" className="flex flex-col items-start gap-3">
          <p>{property.error.response?.status === 404 ? '매물을 찾을 수 없어요.' : '매물 정보를 불러오지 못했어요.'}</p>
          <Link to="/my-properties" className="btn-secondary btn-sm">내 매물로</Link>
        </div>
      )}
      {isEdit && property.isSuccess && <BrokerageForm property={property.data} />}
    </div>
  )
}

export default BrokerageRequestFormPage
