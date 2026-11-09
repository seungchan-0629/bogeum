import { api } from '../../utils/jwtUtil.js'

// 매물 API는 /api 아래가 아니라서 공통 baseURL(/api)을 비우고 보낸다. 토큰·CSRF는 api 인터셉터가 붙인다.
const PROPERTY_CONFIG = { baseURL: '' }

// PROPERTY_002 매물 중개 신청. agentId가 없으면 서버가 같은 지역 중개사를 우선으로 배정한다
export async function createBrokerageRequest(request) {
  const { data } = await api.post('/property/brokerage-requests', request, PROPERTY_CONFIG)
  return data
}

// PROPERTY_012 나에게 온 중개 신청 목록. status(REQUESTED·ACCEPTED)별로 한 페이지 5개씩, page는 1부터
export async function getReceivedBrokerageRequests(status, page = 1, signal) {
  const { data } = await api.get('/property/brokerage-requests/received', { ...PROPERTY_CONFIG, params: { status, page }, signal })
  return data
}

// PROPERTY_010 매물 정보 올리기 화면의 첫 값. 임대인이 신청한 조건, 상세 정보, 사진(조회 주소)을 함께 준다
export async function getPropertyDetail(propertyId, signal) {
  const { data } = await api.get(`/property/${propertyId}/details`, { ...PROPERTY_CONFIG, signal })
  return data
}

// PROPERTY_010 매물 상세 정보 임시 저장. 모든 칸을 함께 보내며 null로 보낸 칸은 비운다. 공개 상태는 그대로다
export async function updatePropertyDetail(propertyId, request) {
  const { data } = await api.put(`/property/${propertyId}/details`, request, PROPERTY_CONFIG)
  return data
}

// PROPERTY_008 중개 신청 수락. 승인된 중개사가 본인에게 온 신청만 수락할 수 있다
export async function acceptBrokerageRequest(brokerageRequestId) {
  const { data } = await api.post(`/property/brokerage-requests/${brokerageRequestId}/accept`, null, PROPERTY_CONFIG)
  return data
}
