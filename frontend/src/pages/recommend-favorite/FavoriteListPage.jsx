// [페이지] 관심 매물 — /favorites
// 누가: 사용자(USER)
// 요구사항: FAVORITE_002 (해제), FAVORITE_003 (목록, 누르면 매물 상세)
// API:
// - 목록: GET /favorites/me?page= — 최근에 담은 순서로 12개씩
// - 해제: DELETE /favorites/{propertyId} — useFavoriteToggle 안에 연결
// 하트로 빼도 카드는 그 자리에 남고 하트만 빈다. 실수로 눌렀으면 바로 다시 담을 수 있고, 화면을 다시 열면 빠져 있다
import { useState } from 'react'
import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import PageHeader from '../../components/common/PageHeader.jsx'
import Pagination from '../../components/common/Pagination.jsx'
import PropertyCard from '../../components/property/PropertyCard.jsx'
import { FAVORITE_LIST_KEY, useFavoriteToggle } from '../../hooks/useFavoriteToggle.js'
import { useAuthStore } from '../../stores/authStore.js'
import { getFavorites } from '../../api/favorite/index.js'
import { toCardProperty } from '../../utils/propertyCard.js'

function FavoriteListPage() {
  const user = useAuthStore((state) => state.user)
  const [page, setPage] = useState(1)
  const { favoriteIds, toggleFavorite, isPending, errorOf } = useFavoriteToggle()
  const list = useQuery({
    queryKey: [...FAVORITE_LIST_KEY, user?.userId, page],
    queryFn: ({ signal }) => getFavorites(page, signal),
  })
  const favorites = list.data?.favorites ?? []

  return (
    <div className="page-container">
      <PageHeader title="관심 매물"
        description={list.isSuccess ? `저장한 매물 ${list.data.totalElements}개` : '마음에 드는 매물을 모아 봐요.'} />
      {list.isPending && <p role="status">관심 매물을 불러오고 있어요.</p>}
      {list.isError && <p role="alert">관심 매물을 불러오지 못했어요.</p>}
      {list.isSuccess && favorites.length === 0 && (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-gray-300 px-6 py-10 text-center text-gray-600">
          아직 관심 매물이 없어요.
          <Link to="/properties" className="btn-primary">매물 찾기</Link>
        </div>
      )}
      {favorites.length > 0 && (
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {favorites.map((favorite) => (
            <PropertyCard
              key={favorite.property.propertyId}
              property={toCardProperty(favorite.property)}
              isUnavailable={!favorite.isAvailable}
              isFavorite={favoriteIds.has(favorite.property.propertyId)}
              onToggleFavorite={toggleFavorite}
              isFavoritePending={isPending(favorite.property.propertyId)}
              favoriteError={errorOf(favorite.property.propertyId)}
            />
          ))}
        </div>
      )}
      {list.data?.totalPages > 1 && <Pagination page={page} totalPages={list.data.totalPages} onChange={setPage} />}
    </div>
  )
}

export default FavoriteListPage
