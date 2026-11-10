// [페이지] 매물 정보 올리기 (중개사) — /agent/properties/:propertyId/edit
// 중개를 수락한 매물의 상세 정보와 사진을 올려 매물을 공개한다
// 요구사항: PROPERTY_010
// API:
// - 첫 값(신청 조건·상세 정보·사진): GET /property/{propertyId}/details
// - 임시 저장: PUT /property/{propertyId}/details
// - 사진: POST /property/{propertyId}/images, DELETE /property/{propertyId}/images/{propertyImageId}
// - 공개: POST /property/{propertyId}/publish
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import PageHeader from '../../components/common/PageHeader.jsx'
import FileDropzone from '../../components/common/FileDropzone.jsx'
import PhotoPlaceholder from '../../components/common/PhotoPlaceholder.jsx'
import { Field, SelectInput, TextInput } from '../../components/common/FormControls.jsx'
import { formatLeasePrice } from '../../utils/format.js'
import Icon from '../../components/common/Icon.jsx'
import { addPropertyImages, deletePropertyImage, getPropertyDetail, updatePropertyDetail } from '../../api/property/index.js'

// 서버의 매물당 사진 상한과 같다
const MAX_IMAGE_COUNT = 10

// 서버 enum(PropertyType)과 같은 값. 원룸·투룸은 방 개수로 나타낸다
const PROPERTY_TYPE_OPTIONS = [
  { value: '', label: '선택' },
  { value: 'APARTMENT', label: '아파트' },
  { value: 'VILLA', label: '빌라' },
  { value: 'OFFICETEL', label: '오피스텔' },
  { value: 'MULTI_FAMILY', label: '다가구주택' },
]
const FLOOR_LEVEL_OPTIONS = [
  { value: 'ABOVE', label: '지상' },
  { value: 'BASEMENT', label: '지하' },
]

const DETAIL_FIELDS = [
  { id: 'areaPyeong', label: '면적 (평)', inputMode: 'decimal' },
  { id: 'roomCount', label: '방 개수' },
  { id: 'totalFloor', label: '전체 층' },
  { id: 'householdCount', label: '세대 수' },
  { id: 'parkingCount', label: '주차 대수' },
]

// 임시 저장이라 모든 칸은 비워도 된다. 값이 있으면 서버(DetailRequest)와 같은 범위로 검사한다
const integerText = (min, message) =>
  z.string().trim().refine((value) => value === '' || (/^\d+$/.test(value) && Number(value) >= min), message)

const detailSchema = z
  .object({
    propertyType: z.string(),
    areaPyeong: z.string().trim().refine(
      (value) => value === '' || (/^\d{1,5}(\.\d{1,2})?$/.test(value) && Number(value) > 0),
      '면적은 0보다 크게, 소수 둘째 자리까지 입력해 주세요.',
    ),
    roomCount: integerText(1, '방 개수는 1 이상 숫자로 입력해 주세요.'),
    floorLevel: z.enum(['ABOVE', 'BASEMENT']),
    floorNumber: integerText(1, '층은 1 이상 숫자로 입력해 주세요.'),
    totalFloor: integerText(1, '전체 층은 1 이상 숫자로 입력해 주세요.'),
    householdCount: integerText(1, '세대 수는 1 이상 숫자로 입력해 주세요.'),
    parkingCount: integerText(0, '주차 대수는 0 이상 숫자로 입력해 주세요.'),
    nearbySubwayStation: z.string().trim().max(100, '주변 지하철역은 100자 이하로 입력해 주세요.'),
  })
  .superRefine((values, context) => {
    // 서버도 같은 규칙으로 다시 검사한다 (INVALID_PROPERTY_FLOOR). 지하는 항상 전체 층보다 낮다
    const isAboveTotal = values.floorLevel === 'ABOVE' && values.floorNumber !== '' && values.totalFloor !== ''
      && Number(values.floorNumber) > Number(values.totalFloor)
    if (isAboveTotal) context.addIssue({ code: 'custom', path: ['floorNumber'], message: '층은 전체 층보다 클 수 없어요.' })
  })

// 응답은 원 단위, formatLeasePrice는 만원 단위라서 바꿔서 넘긴다
const toManwonPrice = (detail) => ({
  leaseType: detail.leaseType,
  deposit: detail.deposit / 10000,
  monthlyRent: detail.monthlyRent / 10000,
})

const toText = (value) => (value == null ? '' : String(value))

// 서버는 지하를 음수로 저장한다. -1 → 지하 + '1'
const toFormValues = (detail) => ({
  propertyType: detail.propertyType ?? '',
  areaPyeong: toText(detail.areaPyeong),
  roomCount: toText(detail.roomCount),
  floorLevel: detail.floor < 0 ? 'BASEMENT' : 'ABOVE',
  floorNumber: detail.floor == null ? '' : String(Math.abs(detail.floor)),
  totalFloor: toText(detail.totalFloor),
  householdCount: toText(detail.householdCount),
  parkingCount: toText(detail.parkingCount),
  nearbySubwayStation: detail.nearbySubwayStation ?? '',
})

// PUT이라 빈 칸은 null로 보내 서버에서도 비운다
const toNumber = (value) => (value === '' ? null : Number(value))

const toRequest = (values) => ({
  propertyType: values.propertyType || null,
  areaPyeong: toNumber(values.areaPyeong),
  roomCount: toNumber(values.roomCount),
  floor: values.floorNumber === '' ? null : Number(values.floorNumber) * (values.floorLevel === 'BASEMENT' ? -1 : 1),
  totalFloor: toNumber(values.totalFloor),
  householdCount: toNumber(values.householdCount),
  parkingCount: toNumber(values.parkingCount),
  nearbySubwayStation: values.nearbySubwayStation || null,
})

function ErrorText({ error }) {
  return error ? <p className="text-sm text-red-600">{error.message}</p> : null
}

function PropertyDetailFormPage() {
  const { propertyId } = useParams()
  const detailQuery = useQuery({
    queryKey: ['property-detail', propertyId],
    queryFn: ({ signal }) => getPropertyDetail(propertyId, signal),
  })

  if (detailQuery.isPending) {
    return <div className="page-container"><p role="status">매물 정보를 불러오고 있어요.</p></div>
  }
  if (detailQuery.isError) {
    const message = detailQuery.error.response?.status === 404
      ? '맡고 있는 매물이 아니거나 없는 매물이에요.'
      : '매물 정보를 불러오지 못했어요.'
    return <div className="page-container"><p role="alert">{message}</p></div>
  }
  return <PropertyDetailForm propertyId={propertyId} detail={detailQuery.data} />
}

// 조회가 끝난 뒤에 만들어서, 폼 첫 값을 조회 결과로 바로 채운다
function PropertyDetailForm({ propertyId, detail }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(detailSchema),
    defaultValues: toFormValues(detail),
  })
  const [isUploading, setIsUploading] = useState(false)
  const [dropzoneKey, setDropzoneKey] = useState(0)
  const [deletingImageId, setDeletingImageId] = useState(null)

  const address = `${detail.roadAddress} ${detail.detailAddress ?? ''}`.trim()
  // 공개된 매물은 상세 정보와 사진을 바꿀 수 없다 (서버도 409로 막는다)
  const isEditable = detail.propertyStatus === 'REQUESTED'

  const handleSave = async (values) => {
    try {
      await updatePropertyDetail(propertyId, toRequest(values))
    } catch (error) {
      window.alert(error.response?.data?.message || '서버에 연결할 수 없습니다.')
      return
    }
    await queryClient.invalidateQueries({ queryKey: ['property-detail', propertyId] })
    window.alert('임시 저장했어요.')
  }

  // 고르면 바로 올린다. 다른 칸을 입력하는 동안 올라가서 저장·공개할 때 사진을 기다리지 않는다
  const handleImagesChange = async (files) => {
    if (files.length === 0) return
    const remainingCount = MAX_IMAGE_COUNT - detail.images.length
    if (files.length > remainingCount) {
      window.alert(`사진은 ${MAX_IMAGE_COUNT}장까지 올릴 수 있어요. 지금은 ${remainingCount}장 더 올릴 수 있어요.`)
      setDropzoneKey((key) => key + 1)
      return
    }
    setIsUploading(true)
    try {
      await addPropertyImages(propertyId, files)
      await queryClient.invalidateQueries({ queryKey: ['property-detail', propertyId] })
    } catch (error) {
      window.alert(error.response?.data?.message || '서버에 연결할 수 없습니다.')
    } finally {
      setIsUploading(false)
      // 올린 파일 이름 목록을 비우고, 같은 파일을 다시 골라도 올라가도록 칸을 새로 만든다
      setDropzoneKey((key) => key + 1)
    }
  }

  // 공개 전에는 지우고 다시 올릴 수 있다. 대표 사진을 지우면 다음 사진이 대표가 된다
  const handleImageDelete = async (propertyImageId) => {
    setDeletingImageId(propertyImageId)
    try {
      await deletePropertyImage(propertyId, propertyImageId)
      await queryClient.invalidateQueries({ queryKey: ['property-detail', propertyId] })
    } catch (error) {
      window.alert(error.response?.data?.message || '서버에 연결할 수 없습니다.')
    } finally {
      setDeletingImageId(null)
    }
  }

  // TODO: [매물 공개하기]는 저장 후 POST /publish로 연결한다
  const handlePublish = (event) => {
    event.preventDefault()
    navigate('/agent/contracts')
  }

  return (
    <div className="page-container">
      <PageHeader title="매물 정보 올리기" description={`${address} · 임대인 ${detail.landlordName}`} />
      {!isEditable && (
        <p role="status" className="rounded-xl bg-slate-100 px-4 py-3 text-gray-700">공개된 매물은 수정할 수 없어요.</p>
      )}
      <form onSubmit={handlePublish} noValidate className="flex flex-col gap-5">
        <fieldset disabled={!isEditable} className="grid min-w-0 items-start gap-5 lg:grid-cols-2">
          <section aria-labelledby="detail-info-title" className="card flex flex-col gap-4">
            <h2 id="detail-info-title" className="text-lg font-bold">상세 정보</h2>
            {/* 임대인이 신청할 때 입력한 값이라 중개사는 바꿀 수 없다 */}
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 rounded-xl bg-slate-50 px-4 py-3 text-[15px]">
              <dt className="text-gray-500">주소</dt>
              <dd>{address}</dd>
              <dt className="text-gray-500">거래 조건</dt>
              <dd>{formatLeasePrice(toManwonPrice(detail))} (만원)</dd>
            </dl>
            <div className="grid grid-cols-2 gap-3">
              <Field label="매물 종류" htmlFor="detail-type">
                <SelectInput id="detail-type" options={PROPERTY_TYPE_OPTIONS} {...register('propertyType')} />
              </Field>
              <Field label="층" htmlFor="detail-floor">
                <div className="flex gap-2">
                  <SelectInput aria-label="지상·지하" options={FLOOR_LEVEL_OPTIONS} fullWidth={false} {...register('floorLevel')} />
                  <TextInput id="detail-floor" inputMode="numeric" {...register('floorNumber')} />
                </div>
                <ErrorText error={errors.floorNumber} />
              </Field>
              {DETAIL_FIELDS.map((field) => (
                <Field key={field.id} label={field.label} htmlFor={`detail-${field.id}`}>
                  <TextInput id={`detail-${field.id}`} inputMode={field.inputMode ?? 'numeric'} {...register(field.id)} />
                  <ErrorText error={errors[field.id]} />
                </Field>
              ))}
            </div>
            <Field label="주변 지하철역" htmlFor="detail-subway" hint="여러 개면 쉼표로 구분해요.">
              <TextInput id="detail-subway" placeholder="예: 역삼역, 강남역" {...register('nearbySubwayStation')} />
              <ErrorText error={errors.nearbySubwayStation} />
            </Field>
          </section>
          <section aria-labelledby="detail-photo-title" className="card flex flex-col gap-4">
            <h2 id="detail-photo-title" className="text-lg font-bold">사진</h2>
            {detail.images.length > 0 && (
              <ul className="grid grid-cols-3 gap-2">
                {detail.images.map((image, index) => (
                  <li key={image.propertyImageId} className="relative">
                    {image.url
                      ? <img src={image.url} alt={`매물 사진 ${index + 1}`} className="aspect-square w-full rounded-lg object-cover" />
                      : <PhotoPlaceholder className="aspect-square w-full" />}
                    {index === 0 && (
                      <span className="absolute left-1.5 top-1.5 rounded-md bg-slate-900/70 px-1.5 py-0.5 text-xs text-white">대표</span>
                    )}
                    {isEditable && (
                      <button
                        type="button"
                        aria-label={`매물 사진 ${index + 1} 삭제`}
                        onClick={() => handleImageDelete(image.propertyImageId)}
                        disabled={deletingImageId !== null}
                        className="absolute right-1.5 top-1.5 flex size-7 items-center justify-center rounded-full bg-slate-900/70 text-white hover:bg-slate-900 disabled:opacity-50"
                      >
                        <Icon name="close" size={16} />
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
            {isEditable && isUploading && <p role="status" className="text-sm text-gray-600">사진을 올리고 있어요.</p>}
            {isEditable && !isUploading && detail.images.length < MAX_IMAGE_COUNT && (
              <FileDropzone
                key={dropzoneKey}
                title="건물·방 사진을 올려주세요"
                hint={`첫 사진이 대표 사진이 돼요 · 최대 ${MAX_IMAGE_COUNT}장 (${detail.images.length}/${MAX_IMAGE_COUNT})`}
                multiple
                onChange={handleImagesChange}
              />
            )}
            {isEditable && detail.images.length >= MAX_IMAGE_COUNT && (
              <p className="text-sm text-gray-600">사진을 {MAX_IMAGE_COUNT}장 모두 올렸어요. 바꾸려면 사진을 지운 뒤 올려 주세요.</p>
            )}
          </section>
        </fieldset>
        {isEditable && (
          <div className="flex justify-end gap-2">
            <button type="button" onClick={handleSubmit(handleSave)} disabled={isSubmitting} className="btn-secondary">임시 저장</button>
            <button type="submit" className="btn-primary">매물 공개하기</button>
          </div>
        )}
      </form>
    </div>
  )
}

export default PropertyDetailFormPage
