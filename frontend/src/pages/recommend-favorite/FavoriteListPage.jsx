// [페이지] 관심 매물 — /favorites
// 누가: 임차인
// 요구사항: FAVORITE_002 (해제), FAVORITE_003 (목록, 누르면 매물 상세)
// API:
// - 목록: GET /api/v1/favorites?cursor=&size= (BE-033)
// - 해제: DELETE /favorites/{propertyId} — useFavoriteToggle 안에 연결됨. 실제 목록을 연결할 때 이 화면도 그 훅을 쓴다
import { useState } from 'react'
import { Link } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import PropertyCard from '../../components/property/PropertyCard.jsx'
import { SAMPLE_PROPERTIES } from '../../constants/sampleProperties.js'

function FavoriteListPage() {
  // TODO: GET /api/v1/favorites (BE-033) 응답의 properties[]로 교체
  const favorites = SAMPLE_PROPERTIES
  // 하트를 다시 누르면 목록에서 빠진다 (FAVORITE_002)
  // TODO(FAVORITE_003): 실제 목록을 연결할 때 useFavoriteToggle로 바꾼다. 지금은 샘플이라
  //       샘플 번호가 실제 관심 매물 상태에 섞이지 않게 이 화면 안에서만 뺀다
  const [removedIds, setRemovedIds] = useState(() => new Set())
  const toggleFavorite = (propertyId) => setRemovedIds((prev) => new Set(prev).add(propertyId))
  const visibleFavorites = favorites.filter((property) => !removedIds.has(property.propertyId))

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
