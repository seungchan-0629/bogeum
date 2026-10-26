// [페이지] 매물 상세 — /properties/:propertyId
// 누가: 임차인·임대인·중개사. 관심 매물 버튼과 위험 안내는 임차인에게만 보인다
// 요구사항: PROPERTY_003 (상세 정보·사진), RECOMMEND_002 (유사 매물), FAVORITE_001·002, RISK_006 (위험 안내), CHAT_002 (채팅 문의)
// API:
// - 상세: GET /api/v1/properties/{propertyId} (BE-019)
// - 유사 매물: GET /api/v1/properties/{propertyId}/similar (BE-030)
// - 채팅 문의: POST /api/v1/chat/rooms (BE-036)
import { Link, useNavigate, useParams } from 'react-router'
import Icon from '../../components/common/Icon.jsx'
import PhotoPlaceholder from '../../components/common/PhotoPlaceholder.jsx'
import PropertyCard from '../../components/property/PropertyCard.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { useFavoriteToggle } from '../../hooks/useFavoriteToggle.js'
import { ROLES } from '../../constants/auth.js'
import { SAMPLE_PROPERTIES } from '../../constants/sampleProperties.js'
import { formatLeasePrice, getLeaseTypeLabel } from '../../utils/format.js'

// TODO: GET /api/v1/properties/{propertyId} (BE-019) 응답으로 교체
// - 응답 필드: propertyType, leaseType, deposit, monthlyRent, area, households, parkingCount, floor, totalFloor, roomCount, address, nearbySubway, images[], agent
// - 위험 안내 문구(RISK_006)는 riskNotices[] 같은 필드로 받는 것으로 가정. 명세에 없어 백엔드와 협의 필요
const SAMPLE_DETAIL = {
  households: 120,
  parkingCount: 60,
  floor: 7,
  totalFloor: 15,
  roomCount: 1,
  agent: { officeName: '보금공인중개사', agentName: '이중개' },
  riskNotices: ['해당 매물은 근저당 등 위험 매물의 가능성이 있습니다'],
}

function PropertyDetailPage() {
  const { propertyId } = useParams()
  const user = useAuthStore((state) => state.user)
  const navigate = useNavigate()
  const { favoriteIds, toggleFavorite } = useFavoriteToggle()
  const isUser = user.role === ROLES.USER

  const property = { ...(SAMPLE_PROPERTIES.find((item) => String(item.propertyId) === propertyId) ?? SAMPLE_PROPERTIES[0]), ...SAMPLE_DETAIL }
  // TODO: GET /api/v1/properties/{propertyId}/similar (BE-030) 응답으로 교체. 본인 매물·삭제·계약 완료 매물은 서버에서 뺀다
  const similarProperties = SAMPLE_PROPERTIES.filter((item) => item.propertyId !== property.propertyId).slice(0, 3)
  const isFavorite = favoriteIds.has(property.propertyId)

  // TODO: POST /api/v1/chat/rooms (BE-036, 요청: { propertyId }) 호출 → 응답의 roomId로 이동
  // 이미 대화 중인 방이 있으면 서버가 기존 roomId를 돌려준다 (CHAT_002)
  const handleStartChat = () => {
    navigate('/chats/1')
  }

  const infoItems = [
    ['매물 종류', property.propertyType],
    ['거래 유형', getLeaseTypeLabel(property.leaseType)],
    ['가격', formatLeasePrice(property)],
    ['면적', `${property.area}평`],
    ['세대 수', `${property.households}세대`],
    ['주차', `${property.parkingCount}대`],
    ['층수', `${property.floor}층 / ${property.totalFloor}층`],
    ['방 개수', `${property.roomCount}개`],
    ['주변 지하철역', property.nearbySubway],
  ]

  return (
    <div className="page-container">
      <Link to="/properties" className="flex items-center gap-1 text-sm text-gray-600 hover:text-gray-900">
        <Icon name="back" size={16} /> 목록으로
      </Link>

      {/* TODO: 응답의 images[]로 교체. 첫 장이 대표 사진 */}
      <div className="grid h-72 grid-cols-4 grid-rows-2 gap-2 sm:h-96">
        <PhotoPlaceholder label="대표 사진" className="col-span-4 row-span-2 sm:col-span-2" />
        <PhotoPlaceholder className="hidden sm:flex" />
        <PhotoPlaceholder className="hidden sm:flex" />
        <PhotoPlaceholder className="hidden sm:flex" />
        <PhotoPlaceholder label="+ 8장" className="hidden sm:flex" />
      </div>

      <div className="grid items-start gap-6 lg:grid-cols-[1fr_340px]">
        <div className="flex flex-col gap-6">
          <div className="flex flex-col gap-1">
            <h1 className="text-[28px] font-extrabold tracking-tight">{property.address} {property.propertyType}</h1>
            <p className="text-gray-600">{property.nearbySubway} · 등기 확인 완료</p>
          </div>

          {/* RISK_006: 사용자에게는 안내 문구만 보여주고 사유 상세·근거 자료는 보여주지 않는다 */}
          {isUser && property.riskNotices.map((notice) => (
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

          <section aria-labelledby="similar-title" className="flex flex-col gap-3">
            <h2 id="similar-title" className="text-lg font-bold">비슷한 매물</h2>
            <div className="grid gap-4 sm:grid-cols-3">
              {similarProperties.map((item) => (
                <PropertyCard key={item.propertyId} property={item} />
              ))}
            </div>
          </section>
        </div>

        <aside className="card flex flex-col gap-4 lg:sticky lg:top-6">
          <p className="text-2xl font-extrabold">{formatLeasePrice(property)}</p>
          <div className="flex flex-col rounded-xl bg-slate-50 px-4 py-3">
            <span className="font-bold">{property.agent.officeName}</span>
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
    </div>
  )
}

export default PropertyDetailPage
