// [페이지] 매물 상세 — /properties/:propertyId
// 누가: 임차인·임대인·중개사. 관심 매물 버튼과 위험 안내는 임차인에게만 보인다
// 요구사항: PROPERTY_003 (상세 정보·사진), RECOMMEND_002 (유사 매물), FAVORITE_001·002, RISK_006 (위험 안내), CHAT_002 (채팅 문의)
// API:
// - 상세: GET /property/{propertyId} — 공개 중인 매물만, 아니면 404
// - 유사 매물: GET /api/v1/properties/{propertyId}/similar (BE-030)
// - 채팅 문의: POST /api/v1/chat/rooms (BE-036)
import { Link, useNavigate, useParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import Icon from '../../components/common/Icon.jsx'
import PhotoPlaceholder from '../../components/common/PhotoPlaceholder.jsx'
import PropertyCard from '../../components/property/PropertyCard.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { useFavoriteToggle } from '../../hooks/useFavoriteToggle.js'
import { ROLES } from '../../constants/auth.js'
import { getPublicProperty } from '../../api/property/index.js'
import { formatLeasePrice, getLeaseTypeLabel, getPropertyTypeLabel } from '../../utils/format.js'

// 사진 칸: 큰 대표 사진 1장 + 작은 사진 4장. 더 있으면 마지막 칸에 남은 장수를 보여 준다
const SMALL_PHOTO_COUNT = 4

// 공개할 때 필수가 아닌 칸(세대 수·주차 대수·지하철역)은 비어 있을 수 있다. 주차 0대는 "주차 불가"라 그대로 보여 준다
const withUnit = (value, unit) => (value == null ? '정보 없음' : `${value.toLocaleString()}${unit}`)

// 지하층은 음수로 온다. -1 → "지하 1층"
const formatFloor = (floor, totalFloor) => {
  if (floor == null) return '정보 없음'
  const floorText = floor < 0 ? `지하 ${-floor}층` : `${floor}층`
  return totalFloor == null ? floorText : `${floorText} / ${totalFloor}층`
}

// 상세 API는 원 단위, formatLeasePrice는 만원 단위라서 바꿔서 넘긴다
const toManwonPrice = (property) => ({
  leaseType: property.leaseType,
  deposit: property.deposit / 10000,
  monthlyRent: property.monthlyRent / 10000,
})

function Photo({ image, label, className, children }) {
  // 저장소가 없으면 서버가 사진 주소를 비워서 보낸다
  if (!image?.url) {
    return <PhotoPlaceholder label={label} className={className} />
  }
  return (
    <div className={clsx('relative overflow-hidden rounded-lg bg-[#e6ecf4]', className)}>
      <img src={image.url} alt={label ?? '매물 사진'} className="size-full object-cover" />
      {children}
    </div>
  )
}

function PhotoGallery({ images }) {
  const [mainImage, ...restImages] = images
  const smallImages = restImages.slice(0, SMALL_PHOTO_COUNT)
  const hiddenCount = restImages.length - smallImages.length
  return (
    <div className="grid h-72 grid-cols-4 grid-rows-2 gap-2 sm:h-96">
      <Photo image={mainImage} label="대표 사진" className="col-span-4 row-span-2 sm:col-span-2" />
      {/* 사진이 적어도 칸 모양이 무너지지 않게 빈 칸으로 채운다 */}
      {Array.from({ length: SMALL_PHOTO_COUNT }, (_, index) => {
        const isLast = index === SMALL_PHOTO_COUNT - 1
        return (
          <Photo key={index} image={smallImages[index]} label={`매물 사진 ${index + 2}`} className="hidden sm:flex">
            {isLast && hiddenCount > 0 && (
              <span className="absolute inset-0 flex items-center justify-center bg-black/45 text-lg font-bold text-white">
                + {hiddenCount}장
              </span>
            )}
          </Photo>
        )
      })}
    </div>
  )
}

function PropertyDetail({ property }) {
  const user = useAuthStore((state) => state.user)
  const navigate = useNavigate()
  const { favoriteIds, toggleFavorite } = useFavoriteToggle()
  const isUser = user.role === ROLES.USER
  const isFavorite = favoriteIds.has(property.propertyId)
  const price = formatLeasePrice(toManwonPrice(property))
  const propertyTypeLabel = getPropertyTypeLabel(property.propertyType)

  // TODO: RISK_006 위험 안내 API가 생기면 응답의 안내 문구로 채운다. 지금은 위험 신호가 없는 매물에도 잘못 보이지 않게 비워 둔다
  const riskNotices = []
  // TODO: RECOMMEND_002 유사 매물 API(GET /api/v1/properties/{propertyId}/similar, BE-030)가 생기면 응답으로 채운다
  //       본인 매물·삭제·계약 완료 매물은 서버에서 뺀다. 지금은 관계없는 샘플이 보이지 않게 비워 둔다
  const similarProperties = []

  // TODO: POST /api/v1/chat/rooms (BE-036, 요청: { propertyId }) 호출 → 응답의 roomId로 이동
  // 이미 대화 중인 방이 있으면 서버가 기존 roomId를 돌려준다 (CHAT_002)
  const handleStartChat = () => {
    navigate('/chats/1')
  }

  const infoItems = [
    ['매물 종류', propertyTypeLabel],
    ['거래 유형', getLeaseTypeLabel(property.leaseType)],
    ['가격', price],
    ['면적', withUnit(property.areaPyeong, '평')],
    ['세대 수', withUnit(property.householdCount, '세대')],
    ['주차', withUnit(property.parkingCount, '대')],
    ['층수', formatFloor(property.floor, property.totalFloor)],
    ['방 개수', withUnit(property.roomCount, '개')],
    ['주변 지하철역', property.nearbySubwayStation || '정보 없음'],
  ]

  return (
    <>
      <PhotoGallery images={property.images} />

      <div className="grid items-start gap-6 lg:grid-cols-[1fr_340px]">
        <div className="flex flex-col gap-6">
          <div className="flex flex-col gap-1">
            <h1 className="text-[28px] font-extrabold tracking-tight">{property.roadAddress} {propertyTypeLabel}</h1>
            <p className="text-gray-600">{property.nearbySubwayStation || '주변 지하철역 정보 없음'}</p>
          </div>

          {/* RISK_006: 사용자에게는 안내 문구만 보여주고 사유 상세·근거 자료는 보여주지 않는다 */}
          {isUser && riskNotices.map((notice) => (
            <p key={notice} className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-amber-900">
              <strong>{notice}</strong>
              <span className="block text-sm">계약 전에 등기부등본을 꼭 다시 확인하세요.</span>
            </p>
          ))}

          <section aria-labelledby="property-info-title" className="card">
            <h2 id="property-info-title" className="mb-4 text-lg font-bold">매물 정보</h2>
            <dl className="grid grid-cols-2 gap-x-5 gap-y-4 sm:grid-cols-3">
              {infoItems.map(([label, value]) => (
                <div key={label} className="flex flex-col">
                  <dt className="text-sm text-gray-500">{label}</dt>
                  <dd className="font-bold">{value}</dd>
                </div>
              ))}
            </dl>
          </section>

          {similarProperties.length > 0 && (
            <section aria-labelledby="similar-title" className="flex flex-col gap-3">
              <h2 id="similar-title" className="text-lg font-bold">비슷한 매물</h2>
              <div className="grid gap-4 sm:grid-cols-3">
                {similarProperties.map((item) => (
                  <PropertyCard key={item.propertyId} property={item} />
                ))}
              </div>
            </section>
          )}
        </div>

        <aside className="card flex flex-col gap-4 lg:sticky lg:top-6">
          <p className="text-2xl font-extrabold">{price}</p>
          {/* 이 매물을 중개 수락한 중개사. 채팅 문의는 이 중개사에게 간다 (CHAT_002) */}
          <div className="flex flex-col rounded-xl bg-slate-50 px-4 py-3">
            {property.agent.officeName && <span className="font-bold">{property.agent.officeName}</span>}
            <span className="text-sm text-gray-600">담당 중개사 · {property.agent.agentName}</span>
          </div>
          {/* 중개사는 자신의 매물에 채팅방을 만들 수 없다 (CHAT_002). 사용자만 문의 버튼을 보여준다
              TODO: 본인이 중개 신청한 매물이면 문의 버튼을 숨길지 팀 확인 필요 (상세 응답에 본인 매물 여부가 있는지 백엔드와 협의) */}
          {isUser && (
            <>
              <button type="button" onClick={handleStartChat} className="btn-primary h-12">
                <Icon name="chat" size={18} /> 채팅으로 문의하기
              </button>
              <button type="button" onClick={() => toggleFavorite(property.propertyId)} aria-pressed={isFavorite} className="btn-secondary h-12">
                <Icon name="heart" size={18} filled={isFavorite} className={isFavorite ? 'text-rose-600' : undefined} />
                {isFavorite ? '관심 매물 해제' : '관심 매물 등록'}
              </button>
              <p className="text-sm text-gray-500">이미 대화 중이면 기존 채팅방으로 이동해요.</p>
            </>
          )}
        </aside>
      </div>
    </>
  )
}

function PropertyDetailPage() {
  const { propertyId } = useParams()
  const detail = useQuery({
    queryKey: ['property', propertyId],
    queryFn: ({ signal }) => getPublicProperty(propertyId, signal),
    // 없거나 공개 중이 아닌 매물(404)은 다시 불러도 같아서 다시 시도하지 않는다
    retry: (failureCount, error) => error.response?.status !== 404 && failureCount < 3,
  })
  const isNotFound = detail.error?.response?.status === 404

  return (
    <div className="page-container">
      <Link to="/properties" className="flex items-center gap-1 text-sm text-gray-600 hover:text-gray-900">
        <Icon name="back" size={16} /> 목록으로
      </Link>
      {detail.isPending && <p role="status">매물 정보를 불러오고 있어요.</p>}
      {/* 숨김·계약 완료·삭제 매물도 없는 매물과 같이 안내한다 */}
      {isNotFound && (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-gray-300 px-6 py-10 text-center text-gray-600">
          매물을 찾을 수 없어요. 계약이 끝났거나 더 이상 공개되지 않는 매물일 수 있어요.
          <Link to="/properties" className="btn-secondary btn-sm">매물 목록 보기</Link>
        </div>
      )}
      {detail.isError && !isNotFound && <p role="alert">매물 정보를 불러오지 못했어요.</p>}
      {detail.isSuccess && <PropertyDetail property={detail.data} />}
    </div>
  )
}

export default PropertyDetailPage
