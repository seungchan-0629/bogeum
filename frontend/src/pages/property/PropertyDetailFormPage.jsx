// [페이지] 매물 정보 올리기 (중개사) — /agent/properties/:propertyId/edit
// 중개를 수락한 매물의 상세 정보와 사진을 올려 매물을 공개한다
// 요구사항: PROPERTY_010
// API:
// - 첫 값(신청 조건·상세 정보·사진): GET /property/{propertyId}/details
// - 임시 저장: PUT /property/{propertyId}/details
// - 사진: POST /property/{propertyId}/images, DELETE /property/{propertyId}/images/{propertyImageId}
// - 공개: POST /property/{propertyId}/publish
import { useNavigate, useParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import PageHeader from '../../components/common/PageHeader.jsx'
import FileDropzone from '../../components/common/FileDropzone.jsx'
import PhotoPlaceholder from '../../components/common/PhotoPlaceholder.jsx'
import { Field, SelectInput, TextInput } from '../../components/common/FormControls.jsx'
import { formatLeasePrice } from '../../utils/format.js'
import { getPropertyDetail } from '../../api/property/index.js'

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

// 응답은 원 단위, formatLeasePrice는 만원 단위라서 바꿔서 넘긴다
const toManwonPrice = (detail) => ({
  leaseType: detail.leaseType,
  deposit: detail.deposit / 10000,
  monthlyRent: detail.monthlyRent / 10000,
})

// 서버는 지하를 음수로 저장한다. -1 → 지하 1
const splitFloor = (floor) => ({
  level: floor < 0 ? 'BASEMENT' : 'ABOVE',
  number: floor == null ? '' : Math.abs(floor),
})

function PropertyDetailFormPage() {
  const navigate = useNavigate()
  const { propertyId } = useParams()
  const detailQuery = useQuery({
    queryKey: ['property-detail', propertyId],
    queryFn: ({ signal }) => getPropertyDetail(propertyId, signal),
  })

  // TODO: [임시 저장]은 PUT /details, [매물 공개하기]는 저장 후 POST /publish로 연결한다
  const handleSubmit = (event) => {
    event.preventDefault()
    navigate('/agent/contracts')
  }

  if (detailQuery.isPending) {
    return <div className="page-container"><p role="status">매물 정보를 불러오고 있어요.</p></div>
  }
  if (detailQuery.isError) {
    const message = detailQuery.error.response?.status === 404
      ? '맡고 있는 매물이 아니거나 없는 매물이에요.'
      : '매물 정보를 불러오지 못했어요.'
    return <div className="page-container"><p role="alert">{message}</p></div>
  }

  const detail = detailQuery.data
  const address = `${detail.roadAddress} ${detail.detailAddress ?? ''}`.trim()
  const floor = splitFloor(detail.floor)
  // 공개된 매물은 상세 정보와 사진을 바꿀 수 없다 (서버도 409로 막는다)
  const isEditable = detail.propertyStatus === 'REQUESTED'

  return (
    <div className="page-container">
      <PageHeader title="매물 정보 올리기" description={`${address} · 임대인 ${detail.landlordName}`} />
      {!isEditable && (
        <p role="status" className="rounded-xl bg-slate-100 px-4 py-3 text-gray-700">공개된 매물은 수정할 수 없어요.</p>
      )}
      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
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
                <SelectInput id="detail-type" options={PROPERTY_TYPE_OPTIONS} defaultValue={detail.propertyType ?? ''} />
              </Field>
              <Field label="층" htmlFor="detail-floor">
                <div className="flex gap-2">
                  <SelectInput aria-label="지상·지하" options={FLOOR_LEVEL_OPTIONS} defaultValue={floor.level} fullWidth={false} />
                  <TextInput id="detail-floor" inputMode="numeric" defaultValue={floor.number} />
                </div>
              </Field>
              {DETAIL_FIELDS.map((field) => (
                <Field key={field.id} label={field.label} htmlFor={`detail-${field.id}`}>
                  <TextInput id={`detail-${field.id}`} inputMode={field.inputMode ?? 'numeric'} defaultValue={detail[field.id] ?? ''} />
                </Field>
              ))}
            </div>
            <Field label="주변 지하철역" htmlFor="detail-subway" hint="여러 개면 쉼표로 구분해요.">
              <TextInput id="detail-subway" placeholder="예: 역삼역, 강남역" defaultValue={detail.nearbySubwayStation ?? ''} />
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
                  </li>
                ))}
              </ul>
            )}
            {isEditable && <FileDropzone title="건물·방 사진을 올려주세요" hint="첫 사진이 대표 사진이 돼요" multiple />}
          </section>
        </fieldset>
        {isEditable && (
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary">임시 저장</button>
            <button type="submit" className="btn-primary">매물 공개하기</button>
          </div>
        )}
      </form>
    </div>
  )
}

export default PropertyDetailFormPage
