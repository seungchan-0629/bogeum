// [페이지] 계약서 올리기 (중개사) — /agent/properties/:propertyId/contract
// 계약이 체결되면 계약서 이미지와 계약 정보를 올린다. 등록하면 매물은 '계약 완료'가 된다
// 요구사항: PROPERTY_011
// API:
// - 첫 값(거래 조건·임대인·임차인 후보): GET /contract/candidates?propertyId=
// - 등록: POST /contract-details (계약 정보 JSON + 계약서 이미지 multipart)
// 월 납부일은 월세 자동 결제(RENT_004)의 기준이 된다
// 임대인은 매물의 임대인으로 정해져 있고, 임차인은 이 매물로 상담한 사람 중에서 고른다
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import PageHeader from '../../components/common/PageHeader.jsx'
import FileDropzone from '../../components/common/FileDropzone.jsx'
import ConfirmModal from '../../components/common/ConfirmModal.jsx'
import { DateInput, Field, TextInput } from '../../components/common/FormControls.jsx'
import ContractPartyField from '../../components/property/ContractPartyField.jsx'
import { formatLeasePrice } from '../../utils/format.js'
import { createContract, getContractCandidates } from '../../api/contract/index.js'

// 서버의 계약당 계약서 이미지 상한과 같다
const MAX_DOCUMENT_COUNT = 10

// 응답은 원 단위, 화면은 만원 단위라서 바꿔서 쓴다
const toManwonPrice = (candidates) => ({
  leaseType: candidates.leaseType,
  deposit: candidates.deposit / 10000,
  monthlyRent: candidates.monthlyRent / 10000,
})

// 날짜 칸 값과 같은 "2026-11-11" 모양의 오늘 날짜 (내 컴퓨터 시간 기준)
const todayText = () => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

const manwonText = (message) => z.string().trim().regex(/^\d+$/, message)

// 서버(ContractRequest, ContractService)와 같은 규칙. 서버도 다시 검사한다
const createContractSchema = (isMonthly) =>
  z
    .object({
      tenant: z.custom((value) => value != null, '임차인을 선택해 주세요.'),
      startAt: z.string().min(1, '계약 시작일을 입력해 주세요.'),
      endAt: z.string().min(1, '계약 종료일을 입력해 주세요.'),
      deposit: manwonText('보증금은 만원 단위 숫자로 입력해 주세요.'),
      monthlyRent: z.string().trim(),
      paymentDay: z.string().trim(),
      contractedAt: z.string().min(1, '계약일을 입력해 주세요.'),
      // 법정 상한으로 계산하면 87.5만 원처럼 천 원 단위가 나올 수 있어 소수 첫째 자리까지 받는다
      brokerageFee: z.string().trim().regex(/^\d+(\.\d)?$/, '중개보수는 만원 단위로, 소수 첫째 자리까지 입력해 주세요.'),
    })
    .superRefine((values, context) => {
      const addIssue = (path, message) => context.addIssue({ code: 'custom', path: [path], message })
      // "YYYY-MM-DD" 글자는 앞에서부터 비교하면 날짜 순서와 같다
      if (values.startAt && values.endAt && values.startAt >= values.endAt) {
        addIssue('endAt', '계약 종료일은 시작일보다 뒤여야 해요.')
      }
      if (values.contractedAt && values.contractedAt > todayText()) {
        addIssue('contractedAt', '계약일은 오늘이나 지난 날짜로 입력해 주세요.')
      } else if (values.contractedAt && values.endAt && values.contractedAt >= values.endAt) {
        addIssue('contractedAt', '계약일은 계약 종료일보다 앞이어야 해요.')
      }
      if (isMonthly) {
        // 월세는 자동 결제(RENT_004)에 쓰여 월세와 납부일이 함께 있어야 한다
        if (!/^\d+$/.test(values.monthlyRent) || Number(values.monthlyRent) === 0) {
          addIssue('monthlyRent', '월세는 0보다 큰 만원 단위 숫자로 입력해 주세요.')
        }
        if (!/^\d+$/.test(values.paymentDay) || Number(values.paymentDay) < 1 || Number(values.paymentDay) > 31) {
          addIssue('paymentDay', '월 납부일은 1일부터 31일까지 입력해 주세요.')
        }
      } else if (Number(values.deposit) === 0) {
        addIssue('deposit', '전세금은 0보다 크게 입력해 주세요.')
      }
    })

// 만원 → 원. 중개보수의 소수(천 원 단위)도 정수 원이 되도록 반올림한다
const toWon = (manwon) => Math.round(Number(manwon) * 10000)

// 전세는 월세·납부일을 보내지 않는다 (서버가 월세를 0원으로 저장한다)
const toRequest = (propertyId, isMonthly, values) => ({
  propertyId,
  tenantId: values.tenant.userId,
  deposit: toWon(values.deposit),
  monthlyRent: isMonthly ? toWon(values.monthlyRent) : null,
  paymentDay: isMonthly ? Number(values.paymentDay) : null,
  startAt: values.startAt,
  endAt: values.endAt,
  contractedAt: values.contractedAt,
  brokerageFee: toWon(values.brokerageFee),
})

function ErrorText({ error }) {
  return error ? <p className="text-sm text-red-600">{error.message}</p> : null
}

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
        ? <ContractForm propertyId={propertyId} candidates={candidates} />
        : <NotContractableNotice propertyId={propertyId} propertyStatus={candidates.propertyStatus} />}
    </div>
  )
}

// 조회가 끝난 뒤에 만들어 신청 가격을 첫 값으로 바로 채운다
function ContractForm({ propertyId, candidates }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const isMonthly = candidates.leaseType === 'MONTHLY'
  const price = toManwonPrice(candidates)
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(createContractSchema(isMonthly)),
    defaultValues: {
      tenant: null,
      startAt: '',
      endAt: '',
      deposit: String(price.deposit),
      monthlyRent: isMonthly ? String(price.monthlyRent) : '',
      paymentDay: '',
      contractedAt: '',
      brokerageFee: '',
    },
  })
  // 계약서는 고른 뒤 [계약 등록]을 누를 때 계약 정보와 함께 보낸다 (API가 한 번에 받는다)
  const [images, setImages] = useState([])
  const [dropzoneKey, setDropzoneKey] = useState(0)
  const [isImageMissing, setIsImageMissing] = useState(false)
  // 확인 창이 열려 있는 동안 등록할 입력값. null이면 창이 닫힌 상태다
  const [confirmValues, setConfirmValues] = useState(null)
  const [isRegistering, setIsRegistering] = useState(false)

  const handleImagesChange = (files) => {
    if (files.length > MAX_DOCUMENT_COUNT) {
      window.alert(`계약서는 ${MAX_DOCUMENT_COUNT}장까지 올릴 수 있어요.`)
      setImages([])
      // 고른 파일 이름 목록을 비우도록 칸을 새로 만든다
      setDropzoneKey((key) => key + 1)
      return
    }
    setImages(files)
    setIsImageMissing(false)
  }

  // 입력 검사를 통과한 뒤 계약서를 확인하고, 되돌릴 수 없으므로 확인 창을 띄운다
  const handleRegisterClick = (values) => {
    if (images.length === 0) {
      setIsImageMissing(true)
      return
    }
    setConfirmValues(values)
  }

  const handleRegisterConfirm = async () => {
    if (isRegistering) return
    setIsRegistering(true)
    try {
      await createContract(toRequest(candidates.propertyId, isMonthly, confirmValues), images)
    } catch (error) {
      window.alert(error.response?.data?.message || '서버에 연결할 수 없습니다.')
      setIsRegistering(false)
      setConfirmValues(null)
      return
    }
    // 내 계약 관리 목록에서 이 매물이 '계약 완료' 단계로 보이도록 다시 불러온다
    await queryClient.invalidateQueries({ queryKey: ['brokerage-requests'] })
    await queryClient.invalidateQueries({ queryKey: ['contract-candidates', propertyId] })
    // 중개 이력에 들어가면 방금 등록한 계약이 바로 보이도록 한다
    await queryClient.invalidateQueries({ queryKey: ['contracts'] })
    window.alert('계약을 등록했어요.')
    navigate('/agent/contracts')
  }

  return (
    <>
      <form onSubmit={handleSubmit(handleRegisterClick)} noValidate className="grid items-start gap-5 lg:grid-cols-2">
        <section aria-labelledby="contract-image-title" className="card flex flex-col gap-4">
          <h2 id="contract-image-title" className="text-lg font-bold">계약서 이미지</h2>
          <FileDropzone
            key={dropzoneKey}
            title="계약서 사진이나 스캔 이미지를 올려주세요"
            hint={`최대 ${MAX_DOCUMENT_COUNT}장 (${images.length}/${MAX_DOCUMENT_COUNT})`}
            multiple
            onChange={handleImagesChange}
          />
          {isImageMissing && <p className="text-sm text-red-600">계약서 이미지를 1장 이상 올려 주세요.</p>}
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
            <div className="flex flex-col gap-1.5 sm:col-span-6">
              <Controller
                name="tenant"
                control={control}
                render={({ field }) => (
                  <ContractPartyField
                    id="contract-tenant"
                    label="임차인"
                    candidates={candidates.tenants}
                    selected={field.value}
                    onSelect={field.onChange}
                  />
                )}
              />
              <ErrorText error={errors.tenant} />
            </div>
            <div className="sm:col-span-6">
              <Field label="계약 기간" htmlFor="contract-start">
                <div className="flex items-center gap-2">
                  {/* DateInput은 달력을 열려고 안쪽 ref를 쓰므로 폼의 ref는 넘기지 않는다 */}
                  <Controller
                    name="startAt"
                    control={control}
                    render={({ field: { ref: _ref, ...field } }) => (
                      <DateInput id="contract-start" aria-label="계약 시작일" {...field} />
                    )}
                  />
                  <span aria-hidden="true" className="text-gray-500">~</span>
                  <Controller
                    name="endAt"
                    control={control}
                    render={({ field: { ref: _ref, ...field } }) => (
                      <DateInput id="contract-end" aria-label="계약 종료일" {...field} />
                    )}
                  />
                </div>
                <ErrorText error={errors.startAt ?? errors.endAt} />
              </Field>
            </div>
            <div className={isMonthly ? 'sm:col-span-2' : 'sm:col-span-6'}>
              <Field label={isMonthly ? '보증금 (만원)' : '전세금 (만원)'} htmlFor="contract-deposit">
                <TextInput id="contract-deposit" inputMode="numeric" {...register('deposit')} />
                <ErrorText error={errors.deposit} />
              </Field>
            </div>
            {isMonthly && (
              <>
                <div className="sm:col-span-2">
                  <Field label="월세 (만원)" htmlFor="contract-rent">
                    <TextInput id="contract-rent" inputMode="numeric" {...register('monthlyRent')} />
                    <ErrorText error={errors.monthlyRent} />
                  </Field>
                </div>
                <div className="sm:col-span-2">
                  <Field
                    label={<>월 납부일 <span className="text-xs font-normal text-gray-500">매월 며칠</span></>}
                    htmlFor="contract-payment-day"
                  >
                    <TextInput id="contract-payment-day" inputMode="numeric" placeholder="25" {...register('paymentDay')} />
                    <ErrorText error={errors.paymentDay} />
                  </Field>
                </div>
              </>
            )}
            <div className="sm:col-span-3">
              <Field label="계약일" htmlFor="contract-date">
                <Controller
                  name="contractedAt"
                  control={control}
                  render={({ field: { ref: _ref, ...field } }) => <DateInput id="contract-date" {...field} />}
                />
                <ErrorText error={errors.contractedAt} />
              </Field>
            </div>
            <div className="sm:col-span-3">
              <Field label="중개보수 (만원)" htmlFor="contract-fee">
                <TextInput id="contract-fee" inputMode="decimal" placeholder="50" {...register('brokerageFee')} />
                <ErrorText error={errors.brokerageFee} />
              </Field>
            </div>
          </div>
          <button type="submit" disabled={isRegistering} className="btn-primary">
            {isRegistering ? '등록하는 중' : '계약 정보 등록'}
          </button>
        </section>
      </form>
      {confirmValues && (
        <ConfirmModal
          title="계약을 등록할까요?"
          description="등록한 계약 정보는 수정할 수 없습니다. 등록하면 매물은 계약 완료로 바뀌어요."
          confirmLabel={isRegistering ? '등록하는 중' : '등록'}
          onConfirm={handleRegisterConfirm}
          onClose={() => !isRegistering && setConfirmValues(null)}
        />
      )}
    </>
  )
}

export default ContractUploadPage
