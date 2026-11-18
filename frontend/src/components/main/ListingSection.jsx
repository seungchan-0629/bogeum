// RECOMMEND_001 (조회 지역 기반 인기 매물 추천), RECOMMEND_002 (비슷한 매물), FAVORITE_001, FAVORITE_002 (관심 매물 등록·해제)
// 메인의 추천 매물. 사용자(USER)에게만 보인다 (MainPage)
// API:
// - 최근 본 매물·관심 매물과 비슷한 매물: GET /recommend/similar?basis=VIEW|FAVORITE — 기준 매물이 없으면 빈 목록
// - 근처 인기 매물: GET /recommend/properties — 비슷한 매물 칸이 빈 자리를 채운다. 기록이 없으면 전체 인기 매물
// 칸 구성 (사용자 상황별):
// - 본 매물 O, 관심 매물 O → [최근 본 매물과 비슷한] + [관심 매물과 비슷한]
// - 한쪽만 있음 → [있는 쪽과 비슷한] + [근처 인기 매물]
// - 둘 다 없음 → [근처 인기 매물] 한 칸 (기록이 없으면 "지금 인기 있는 매물")
// 칸마다 3개를 보여 주고, 아래 칸에서는 위 칸에 나온 매물을 뺀다. 그래서 넉넉히 받아 온다
import { useQuery } from '@tanstack/react-query'
import RecommendSection from '../recommend-favorite/RecommendSection.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { getRecommendations, getSimilarByBasis } from '../../api/recommend/index.js'

const CARD_COUNT = 3
// 두 번째 칸에서 첫 번째 칸과 겹치는 매물을 빼도 3개가 남도록 두 배를 받는다
const FETCH_SIZE = CARD_COUNT * 2

// 추천에 쓴 관심 지역·역으로 제목을 붙인다. 예: "서울특별시 동작구" + "노량진" → "동작구·노량진역 근처 인기 매물"
// 지역은 마지막 낱말(구·시)만, 역은 끝에 "역"을 붙여 짧게 보여 준다. 기록이 없으면 전체 인기 매물이다
function toPopularTitle(recommendation) {
  if (!recommendation.isPersonalized) return '지금 인기 있는 매물'
  const area = recommendation.regions[0]?.trim().split(/\s+/).at(-1)
  const station = recommendation.stations[0]?.replace(/역$/, '').concat('역')
  return `${[area, station].filter(Boolean).join('·')} 근처 인기 매물`
}

// 앞 칸에 나온 매물을 빼고 3개만
const excludeShown = (properties, shownIds) => properties.filter((property) => !shownIds.has(property.propertyId)).slice(0, CARD_COUNT)

function ListingSection() {
  const user = useAuthStore((state) => state.user)
  // 메인에 돌아올 때마다 새로 불러와서 방금 본 매물·담은 매물이 추천에 반영된다
  const viewed = useQuery({
    queryKey: ['recommendations', 'similar', 'VIEW', user?.userId, FETCH_SIZE],
    queryFn: ({ signal }) => getSimilarByBasis('VIEW', FETCH_SIZE, signal),
  })
  const favorited = useQuery({
    queryKey: ['recommendations', 'similar', 'FAVORITE', user?.userId, FETCH_SIZE],
    queryFn: ({ signal }) => getSimilarByBasis('FAVORITE', FETCH_SIZE, signal),
  })
  const isSettled = !viewed.isPending && !favorited.isPending
  // 불러오지 못한 칸은 빈 칸처럼 다루고 근처 인기 매물로 채운다
  const viewedItems = viewed.data?.properties ?? []
  const favoritedItems = favorited.data?.properties ?? []
  const needsPopular = isSettled && (viewedItems.length === 0 || favoritedItems.length === 0)
  const popular = useQuery({
    queryKey: ['recommendations', user?.userId, FETCH_SIZE],
    queryFn: ({ signal }) => getRecommendations(FETCH_SIZE, signal),
    enabled: needsPopular,
  })

  const sections = []
  if (viewedItems.length > 0) sections.push({ key: 'viewed', title: '최근 본 매물과 비슷한 매물', items: viewedItems })
  if (favoritedItems.length > 0) sections.push({ key: 'favorited', title: '관심 매물과 비슷한 매물', items: favoritedItems })
  if (sections.length < 2 && popular.isSuccess) {
    sections.push({ key: 'popular', title: toPopularTitle(popular.data), items: popular.data.properties })
  }
  const shownIds = new Set()
  const visibleSections = sections
    .map((section) => {
      const items = excludeShown(section.items, shownIds)
      items.forEach((item) => shownIds.add(item.propertyId))
      return { ...section, items }
    })
    .filter((section) => section.items.length > 0)

  const isLoading = !isSettled || (needsPopular && popular.isPending)
  // 보여 줄 칸이 하나도 없으면(모두 불러오지 못함 등) 카드 자리에 추천 기준을 안내한다
  const hasNothing = !isLoading && visibleSections.length === 0

  return (
    <section className="mx-auto flex max-w-[1200px] flex-col gap-12 px-6 py-16">
      {(isLoading || hasNothing) && (
        <div className="flex flex-col gap-6">
          <h2 className="text-[26px] font-extrabold">추천 매물</h2>
          {isLoading && <p role="status">추천 매물을 불러오고 있어요.</p>}
          {hasNothing && <p className="text-gray-600">추천 매물은 사용자의 매물 상세 조회와 관심 매물 등록을 기반으로 이루어져요.</p>}
        </div>
      )}
      {!isLoading && visibleSections.map((section, index) => (
        <RecommendSection key={section.key} title={section.title} properties={section.items} showAllLink={index === 0} />
      ))}
    </section>
  )
}

export default ListingSection
