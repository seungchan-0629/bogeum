import { api } from '../../utils/jwtUtil.js'

// 추천 API는 /api 아래가 아니라서 공통 baseURL(/api)을 비우고 보낸다. 토큰·CSRF는 api 인터셉터가 붙인다.
const RECOMMEND_CONFIG = { baseURL: '' }

// RECOMMEND_001 나를 위한 추천 매물. 사용자(USER)만 받는다
// 최근 본 매물·검색·관심 매물로 정한 관심 지역(regions)·지하철역(stations) 근처의 인기 매물을 준다
// 기록이 없으면 전체 인기 매물을 주고 isPersonalized가 false다. 카드는 매물 목록과 같은 모양이다
export async function getRecommendations(size, signal) {
  const { data } = await api.get('/recommend/properties', { ...RECOMMEND_CONFIG, params: { size }, signal })
  return data
}

// RECOMMEND_002 매물 상세에서 보고 있는 매물과 비슷한 매물. 임대 형식이 같은 공개 매물을 비슷한 순서로 준다
// 본인 매물과 이미 담은 관심 매물은 빠진다. 공개 중이 아닌 매물이면 404다
export async function getSimilarProperties(propertyId, size, signal) {
  const { data } = await api.get(`/recommend/properties/${propertyId}/similar`, { ...RECOMMEND_CONFIG, params: { size }, signal })
  return data
}

// RECOMMEND_002 최근 본 매물(basis: 'VIEW')이나 담은 관심 매물('FAVORITE')과 비슷한 매물. 사용자(USER)만 받는다
// 기준 매물이 없으면 properties가 빈 배열이다
export async function getSimilarByBasis(basis, size, signal) {
  const { data } = await api.get('/recommend/similar', { ...RECOMMEND_CONFIG, params: { basis, size }, signal })
  return data
}
