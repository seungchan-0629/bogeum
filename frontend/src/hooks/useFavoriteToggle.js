// [커스텀 훅] 관심 매물 등록·해제 상태 — 매물 목록, 매물 상세, 관심 매물 화면에서 함께 쓴다
// 요구사항: FAVORITE_001 (등록), FAVORITE_002 (해제)
// - initialIds: 처음부터 관심 매물로 표시할 매물 ID 목록
import { useState } from 'react'

export function useFavoriteToggle(initialIds = []) {
  const [favoriteIds, setFavoriteIds] = useState(() => new Set(initialIds))

  // TODO: 등록은 POST /api/v1/favorites (BE-031, 요청: { propertyId }),
  //       해제는 DELETE /api/v1/favorites/{propertyId} (BE-032) 호출 후 성공하면 상태 반영
  const toggleFavorite = (propertyId) => {
    setFavoriteIds((prev) => {
      const next = new Set(prev)
      if (next.has(propertyId)) next.delete(propertyId)
      else next.add(propertyId)
      return next
    })
  }

  return { favoriteIds, toggleFavorite }
}
