import { api } from '../../utils/jwtUtil.js'

// 계약 API는 /api 아래가 아니라서 공통 baseURL(/api)을 비우고 보낸다. 토큰·CSRF는 api 인터셉터가 붙인다.
const CONTRACT_CONFIG = { baseURL: '' }

// PROPERTY_011 계약 등록 화면의 첫 값. 매물 거래 조건, 임대인(매물의 임대인), 임차인 후보(이 매물로 상담한 사람)를 준다
export async function getContractCandidates(propertyId, signal) {
  const { data } = await api.get('/contract/candidates', { ...CONTRACT_CONFIG, params: { propertyId }, signal })
  return data
}
