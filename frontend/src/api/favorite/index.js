import { api } from '../../utils/jwtUtil.js'

// 관심 매물 API는 /api 아래가 아니라서 공통 baseURL(/api)을 비우고 보낸다. 토큰·CSRF는 api 인터셉터가 붙인다.
const FAVORITE_CONFIG = { baseURL: '' }

// FAVORITE_001 관심 매물 등록. 이미 담은 매물이어도 성공(204)이라 여러 번 보내도 한 번만 담긴다
// 공개 중이 아니면 404(PROPERTY_NOT_FOUND), 본인이 중개를 신청한 매물이면 400(FAVORITE_OWN_PROPERTY)
export async function addFavorite(propertyId) {
  await api.put(`/favorites/${propertyId}`, null, FAVORITE_CONFIG)
}

// FAVORITE_003 내 관심 매물 목록. 최근에 담은 순서로 12개씩, page는 1부터
// 각 항목: { property: 매물 찾기 카드와 같은 칸, favoritedAt, isAvailable(지금도 공개 중인지) }
export async function getFavorites(page = 1, signal) {
  const { data } = await api.get('/favorites/me', { ...FAVORITE_CONFIG, params: { page }, signal })
  return data
}

// FAVORITE_003 내가 담은 매물 번호 전부 ({ propertyIds }). 하트를 처음부터 채워 보여 줄 때 쓴다
export async function getFavoriteIds(signal) {
  const { data } = await api.get('/favorites/me/ids', { ...FAVORITE_CONFIG, signal })
  return data.propertyIds
}

// FAVORITE_002 관심 매물 해제. 담지 않은 매물이어도 성공(204)이다. 매물 상태와 상관없이 뺄 수 있다
export async function removeFavorite(propertyId) {
  await api.delete(`/favorites/${propertyId}`, FAVORITE_CONFIG)
}
