// [페이지] 매물 정보 올리기 (중개사) — /agent/properties/:propertyId/edit
// 중개를 수락한 매물의 상세 정보와 사진을 올려 매물을 공개한다
// 요구사항: PROPERTY_010
// API:
// - 상세 정보: PUT/PATCH /api/v1/properties/{propertyId}/details (BE-025)
// - 사진: POST /api/v1/properties/{propertyId}/images (BE-026)
// - 첫 값(임대인이 신청한 조건): GET /api/v1/properties/{propertyId} (BE-019)
import { useNavigate } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import FileDropzone from '../../components/common/FileDropzone.jsx'
import { Field, SelectInput, TextInput } from '../../components/common/FormControls.jsx'

const PROPERTY_TYPE_OPTIONS = ['아파트', '오피스텔', '빌라', '원룸', '투룸'].map((type) => ({ value: type, label: type }))
const LEASE_TYPE_OPTIONS = [
  { value: 'MONTHLY', label: '월세' },
  { value: 'JEONSE', label: '전세' },
  { value: 'SALE', label: '매매' },
]

// TODO: GET /api/v1/properties/{propertyId} (BE-019) 응답으로 채운다
const SAMPLE_VALUES = { title: '역삼동 오피스텔', landlordName: '박임대', address: '서울 강남구 역삼동 123-4, 701호', deposit: '1000', monthlyRent: '65' }

const DETAIL_FIELDS = [
  { id: 'area', label: '면적 (평)' },
  { id: 'roomCount', label: '방 개수' },
  { id: 'floor', label: '층' },
  { id: 'totalFloor', label: '전체 층' },
  { id: 'households', label: '세대 수' },
  { id: 'parkingCount', label: '주차 대수' },
]

function PropertyDetailFormPage() {
  const navigate = useNavigate()

  // TODO: PUT/PATCH /api/v1/properties/{propertyId}/details (BE-025) 후 사진은 POST .../images (BE-026)
  // [매물 공개하기]와 [임시 저장]의 차이(공개 여부 값)는 백엔드와 협의 필요
  const handleSubmit = (event) => {
    event.preventDefault()
    navigate('/agent/contracts')
  }

  return (
    <div className="page-container">
      <PageHeader title="매물 정보 올리기" description={`${SAMPLE_VALUES.title} · 임대인 ${SAMPLE_VALUES.landlordName}`} />
      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        <div className="grid items-start gap-5 lg:grid-cols-2">
          <section aria-labelledby="detail-info-title" className="card flex flex-col gap-4">
            <h2 id="detail-info-title" className="text-lg font-bold">상세 정보</h2>
            <Field label="주소" htmlFor="detail-address">
              <TextInput id="detail-address" defaultValue={SAMPLE_VALUES.address} />
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="매물 종류" htmlFor="detail-type">
                <SelectInput id="detail-type" options={PROPERTY_TYPE_OPTIONS} defaultValue="오피스텔" />
              </Field>
              <Field label="거래 유형" htmlFor="detail-lease-type">
                <SelectInput id="detail-lease-type" options={LEASE_TYPE_OPTIONS} />
              </Field>
              <Field label="보증금 (만원)" htmlFor="detail-deposit">
                <TextInput id="detail-deposit" inputMode="numeric" defaultValue={SAMPLE_VALUES.deposit} />
              </Field>
              <Field label="월세 (만원)" htmlFor="detail-rent">
                <TextInput id="detail-rent" inputMode="numeric" defaultValue={SAMPLE_VALUES.monthlyRent} />
              </Field>
              {DETAIL_FIELDS.map((field) => (
                <Field key={field.id} label={field.label} htmlFor={`detail-${field.id}`}>
                  <TextInput id={`detail-${field.id}`} inputMode="numeric" />
                </Field>
              ))}
            </div>
            <Field label="주변 지하철역" htmlFor="detail-subway" hint="여러 개면 쉼표로 구분해요.">
              <TextInput id="detail-subway" placeholder="예: 역삼역, 강남역" />
            </Field>
          </section>
          <section aria-labelledby="detail-photo-title" className="card flex flex-col gap-4">
            <h2 id="detail-photo-title" className="text-lg font-bold">사진</h2>
            <FileDropzone title="건물·방 사진을 올려주세요" hint="첫 사진이 대표 사진이 돼요" multiple />
          </section>
        </div>
        <div className="flex justify-end gap-2">
          <button type="button" className="btn-secondary">임시 저장</button>
          <button type="submit" className="btn-primary">매물 공개하기</button>
        </div>
      </form>
    </div>
  )
}

export default PropertyDetailFormPage
