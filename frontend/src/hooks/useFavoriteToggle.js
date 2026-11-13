// [커스텀 훅] 관심 매물 담기·빼기 — 매물 목록 카드의 하트, 매물 상세의 [관심 매물 등록] 버튼에서 쓴다
// 요구사항: FAVORITE_001 (등록), FAVORITE_002 (해제)
// - favoriteIds: 담은 매물 번호 Set. 목록과 상세가 같은 값을 보도록 TanStack Query 저장 공간에 둔다
//   TODO: FAVORITE_003 목록 API가 생기면 queryFn에서 내가 담은 매물 번호를 받아 처음부터 채운다.
//         그 전까지는 빈 상태로 시작하고, 새로고침하면 다시 빈 하트다 (이미 담은 매물을 또 눌러도 서버가 성공으로 답한다)
// - toggleFavorite(propertyId): 빈 하트면 담고, 채워진 하트면 뺀다. 누르자마자 바꾸고 실패하면 되돌린다
// - isPending(propertyId): 요청 중이면 같은 매물을 다시 누르지 못하게 막는다
// - errorOf(propertyId): 실패 안내 문구. 하트·버튼 옆에 잠깐 보여 주고 사라진다
import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { addFavorite, removeFavorite } from '../api/favorite/index.js'

const FAVORITE_IDS_KEY = ['favorites', 'ids']
// 실패 안내를 보여 주는 시간
const ERROR_VISIBLE_MS = 3000

// 카드가 작아서 서버 문구 대신 짧은 문구를 쓴다
function toErrorMessage(error, isAdding) {
  const code = error.response?.data?.code
  if (code === 'FAVORITE_OWN_PROPERTY') return '본인이 중개를 신청한 매물은 담을 수 없어요.'
  if (code === 'PROPERTY_NOT_FOUND') return '더 이상 공개되지 않는 매물이에요.'
  return isAdding ? '관심 매물에 담지 못했어요. 잠시 후 다시 시도해 주세요.' : '관심 매물에서 빼지 못했어요. 잠시 후 다시 시도해 주세요.'
}

export function useFavoriteToggle() {
  const queryClient = useQueryClient()
  // 서버에서 받는 값이 아직 없어 빈 목록으로 두고, 다시 불러오지 않는다 (위 TODO)
  const { data: ids } = useQuery({
    queryKey: FAVORITE_IDS_KEY,
    queryFn: () => [],
    initialData: [],
    staleTime: Infinity,
  })
  const favoriteIds = new Set(ids)
  const [pendingIds, setPendingIds] = useState(() => new Set())
  const [errors, setErrors] = useState({})
  const timersRef = useRef({})

  // 화면을 떠나면 안내를 지우려던 타이머도 함께 멈춘다
  useEffect(() => {
    const timers = timersRef.current
    return () => Object.values(timers).forEach(clearTimeout)
  }, [])

  const setIds = (update) => queryClient.setQueryData(FAVORITE_IDS_KEY, (prev = []) => update(new Set(prev)))
  const togglePending = (propertyId, isOn) => setPendingIds((prev) => {
    const next = new Set(prev)
    if (isOn) next.add(propertyId)
    else next.delete(propertyId)
    return next
  })
  const showError = (propertyId, message) => {
    clearTimeout(timersRef.current[propertyId])
    setErrors((prev) => ({ ...prev, [propertyId]: message }))
    timersRef.current[propertyId] = setTimeout(() => {
      setErrors((prev) => {
        const next = { ...prev }
        delete next[propertyId]
        return next
      })
    }, ERROR_VISIBLE_MS)
  }

  const toggleFavorite = async (propertyId) => {
    if (pendingIds.has(propertyId)) return
    const isAdding = !favoriteIds.has(propertyId)
    // 누르자마자 하트를 바꿔 빠르게 느껴지게 한다
    const flip = (set) => {
      if (isAdding) set.add(propertyId)
      else set.delete(propertyId)
      return [...set]
    }
    const unflip = (set) => {
      if (isAdding) set.delete(propertyId)
      else set.add(propertyId)
      return [...set]
    }
    setIds(flip)
    togglePending(propertyId, true)
    try {
      await (isAdding ? addFavorite(propertyId) : removeFavorite(propertyId))
    } catch (error) {
      setIds(unflip)
      showError(propertyId, toErrorMessage(error, isAdding))
    } finally {
      togglePending(propertyId, false)
    }
  }

  return {
    favoriteIds,
    toggleFavorite,
    isPending: (propertyId) => pendingIds.has(propertyId),
    errorOf: (propertyId) => errors[propertyId],
  }
}
