// [페이지] 관심 매물 — /favorites
// 누가: 임차인
// 요구사항: FAVORITE_002 (해제), FAVORITE_003 (목록, 누르면 매물 상세)
// API:
// - 목록: GET /api/v1/favorites?cursor=&size= (BE-033)
// - 해제: DELETE /api/v1/favorites/{propertyId} (BE-032) — useFavoriteToggle 안에 연결
import { Link } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import PropertyCard from '../../components/property/PropertyCard.jsx'
import { useFavoriteToggle } from '../../hooks/useFavoriteToggle.js'
import { SAMPLE_PROPERTIES } from '../../constants/sampleProperties.js'

function FavoriteListPage() {
  // TODO: GET /api/v1/favorites (BE-033) 응답의 properties[]로 교체
  const favorites = SAMPLE_PROPERTIES
  const { favoriteIds, toggleFavorite } = useFavoriteToggle(favorites.map((property) => property.propertyId))
  // 하트를 다시 누르면 목록에서 빠진다 (FAVORITE_002)
  const visibleFavorites = favorites.filter((property) => favoriteIds.has(property.propertyId))

  return (
    <div className="page-container">
      <PageHeader title="관심 매물" description={`저장한 매물 ${visibleFavorites.length}개`} />
      {visibleFavorites.length > 0 ? (
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {visibleFavorites.map((property) => (
            <PropertyCard key={property.propertyId} property={property} isFavorite onToggleFavorite={toggleFavorite} />
          ))}
        </div>
      ) : (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-gray-300 px-6 py-10 text-center text-gray-600">
          아직 관심 매물이 없어요.
          <Link to="/properties" className="btn-primary">매물 찾기</Link>
        </div>
      )}
    </div>
  )
}

export default FavoriteListPage
