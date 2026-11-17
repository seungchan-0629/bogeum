// RECOMMEND_001 (조회 지역 기반 인기 매물 추천), FAVORITE_001, FAVORITE_002 (관심 매물 등록·해제)
// 메인의 추천 매물. 사용자(USER)에게만 보인다 (MainPage)
// API: GET /recommend/properties?size=3 — 최근 본 매물·검색·관심 매물로 정한 관심 지역 근처의 인기 매물
import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import PropertyCard from '../property/PropertyCard.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { useFavoriteToggle } from '../../hooks/useFavoriteToggle.js'
import { getRecommendations } from '../../api/recommend/index.js'
import { toCardProperty } from '../../utils/propertyCard.js'

// 메인은 카드 한 줄(3개)만 보여 준다
const RECOMMEND_SIZE = 3

// 추천에 쓴 관심 지역·역으로 제목을 붙인다. 예: "서울특별시 동작구" + "노량진" → "동작구·노량진역 근처 인기 매물"
// 지역은 마지막 낱말(구·시)만, 역은 끝에 "역"을 붙여 짧게 보여 준다. 기록이 없으면 전체 인기 매물이다
function toTitle(recommendation) {
  if (!recommendation?.isPersonalized) return '지금 인기 있는 매물'
  const area = recommendation.regions[0]?.trim().split(/\s+/).at(-1)
  const station = recommendation.stations[0]?.replace(/역$/, '').concat('역')
  return `${[area, station].filter(Boolean).join('·')} 근처 인기 매물`
}

function ListingSection() {
  const user = useAuthStore((state) => state.user)
  const { favoriteIds, toggleFavorite, isPending, errorOf } = useFavoriteToggle()
  // 메인에 돌아올 때마다 새로 불러와서 방금 본 매물·검색이 추천에 반영된다
  const recommendation = useQuery({
    queryKey: ['recommendations', user?.userId, RECOMMEND_SIZE],
    queryFn: ({ signal }) => getRecommendations(RECOMMEND_SIZE, signal),
  })
  const properties = recommendation.data?.properties.map(toCardProperty) ?? []
  // 불러오지 못했거나 추천할 매물이 없으면 카드 자리에 추천 기준을 안내한다
  const hasNothing = recommendation.isError || (recommendation.isSuccess && properties.length === 0)

  return (
    <section className="mx-auto flex max-w-[1200px] flex-col gap-6 px-6 py-16">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h2 className="text-[26px] font-extrabold">{recommendation.isSuccess ? toTitle(recommendation.data) : '추천 매물'}</h2>
        <Link to="/properties" className="text-[15px] font-medium text-blue-700 hover:text-blue-900">
          전체 보기
        </Link>
      </div>
      {recommendation.isPending && <p role="status">추천 매물을 불러오고 있어요.</p>}
      {hasNothing && <p className="text-gray-600">추천 매물은 사용자의 매물 상세 조회와 관심 매물 등록을 기반으로 이루어져요.</p>}
      {properties.length > 0 && (
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {properties.map((property) => (
            <PropertyCard
              key={property.propertyId}
              property={property}
              isFavorite={favoriteIds.has(property.propertyId)}
              onToggleFavorite={toggleFavorite}
              isFavoritePending={isPending(property.propertyId)}
              favoriteError={errorOf(property.propertyId)}
            />
          ))}
        </div>
      )}
    </section>
  )
}

export default ListingSection
