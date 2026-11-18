// [추천 컴포넌트] 추천 매물 한 칸 — 제목 + 매물 카드 한 줄 (RECOMMEND_001, RECOMMEND_002)
// 메인(최근 본 매물·관심 매물·근처 인기 매물)과 관심 매물 화면에서 쓴다
// - properties: 추천 API 응답의 매물 목록(원 단위). 카드 모양으로 바꿔서 그린다
// - showAllLink: 칸 오른쪽 위에 "매물 전체 보기"를 둔다. 메인 첫 번째 칸에만 쓴다
// 하트로 담아도 카드는 그 자리에 남는다. 서버가 담은 매물을 다음 추천부터 빼서, 다시 들어오면 바뀐다
import { Link } from 'react-router'
import PropertyCard from '../property/PropertyCard.jsx'
import { useFavoriteToggle } from '../../hooks/useFavoriteToggle.js'
import { toCardProperty } from '../../utils/propertyCard.js'

function RecommendSection({ title, properties, showAllLink = false }) {
  const { favoriteIds, toggleFavorite, isPending, errorOf } = useFavoriteToggle()

  return (
    <section className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h2 className="text-[26px] font-extrabold">{title}</h2>
        {showAllLink && (
          <Link to="/properties" className="text-[15px] font-medium text-blue-700 hover:text-blue-900">
            매물 전체 보기
          </Link>
        )}
      </div>
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {properties.map(toCardProperty).map((property) => (
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
    </section>
  )
}

export default RecommendSection
