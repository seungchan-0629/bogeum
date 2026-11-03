import { api } from '../../utils/jwtUtil.js'

// 매물 API는 /api 아래가 아니라서 공통 baseURL(/api)을 비우고 보낸다. 토큰·CSRF는 api 인터셉터가 붙인다.
const PROPERTY_CONFIG = { baseURL: '' }

// PROPERTY_002 매물 중개 신청. agentId가 없으면 서버가 같은 지역 중개사를 우선으로 배정한다
export async function createBrokerageRequest(request) {
  const { data } = await api.post('/property/brokerage-requests', request, PROPERTY_CONFIG)
  return data
}

// PROPERTY_008 중개 신청 수락. 승인된 중개사가 본인에게 온 신청만 수락할 수 있다
export async function acceptBrokerageRequest(brokerageRequestId) {
  const { data } = await api.post(`/property/brokerage-requests/${brokerageRequestId}/accept`, null, PROPERTY_CONFIG)
  return data
}
