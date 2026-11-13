import { api } from '../../utils/jwtUtil.js'

// 매물 API는 /api 아래가 아니라서 공통 baseURL(/api)을 비우고 보낸다. 토큰·CSRF는 api 인터셉터가 붙인다.
const PROPERTY_CONFIG = { baseURL: '' }

// PROPERTY_002 매물 중개 신청. agentId가 없으면 서버가 같은 지역 중개사를 우선으로 배정한다
export async function createBrokerageRequest(request) {
  const { data } = await api.post('/property/brokerage-requests', request, PROPERTY_CONFIG)
  return data
}

// PROPERTY_010 매물 사진 추가. 기존 사진 뒤에 붙고, 추가한 사진 목록(조회 주소 포함)을 돌려준다
// 서버가 사진을 검사·변환해 올리느라 오래 걸릴 수 있어 기본 제한 시간(10초)보다 길게 기다린다
export async function addPropertyImages(propertyId, files) {
  const formData = new FormData()
  files.forEach((file) => formData.append('images', file))
  const { data } = await api.post(`/property/${propertyId}/images`, formData, { ...PROPERTY_CONFIG, timeout: 60000 })
  return data
}

// PROPERTY_004·005 공개 매물 목록·검색. 최근 공개 순서로 12개씩 준다
// - search: 검색 조건 쿼리 그대로 ({ leaseTypes: 'JEONSE,MONTHLY', region: '서울 강남구', minDeposit: '100000000' … })
//   여러 개 고른 값은 쉼표로 이어 보낸다. axios 기본은 propertyTypes[]=…라 서버가 읽지 못한다
// - cursor: 첫 목록은 비우고, 다음 목록은 앞 응답의 nextCursor({ cursorPublishedAt, cursorPropertyId })를 그대로 보낸다
export async function getProperties(search, cursor, signal) {
  const { data } = await api.get('/property', { ...PROPERTY_CONFIG, params: { ...search, ...cursor }, signal })
  return data
}

// PROPERTY_003 공개 매물 상세. 상세 항목, 사진(조회 주소, 첫 장이 대표), 담당 중개사를 준다
// 공개 중이 아니거나 없는 매물은 404다. 중개사용 상세(getPropertyDetail)와 달리 임대인 이름·상세주소는 없다
export async function getPublicProperty(propertyId, signal) {
  const { data } = await api.get(`/property/${propertyId}`, { ...PROPERTY_CONFIG, signal })
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

// PROPERTY_010 매물 공개(ACTIVE). 필수 상세 정보와 사진 1장 이상이 있어야 하고, 공개 후에는 수정할 수 없다
export async function publishProperty(propertyId) {
  const { data } = await api.post(`/property/${propertyId}/publish`, null, PROPERTY_CONFIG)
  return data
}

// PROPERTY_008 중개 신청 수락. 승인된 중개사가 본인에게 온 신청만 수락할 수 있다
export async function acceptBrokerageRequest(brokerageRequestId) {
  const { data } = await api.post(`/property/brokerage-requests/${brokerageRequestId}/accept`, null, PROPERTY_CONFIG)
  return data
}

// PROPERTY_010 매물 사진 하나 삭제. 성공하면 본문 없이 204를 준다
export async function deletePropertyImage(propertyId, propertyImageId) {
  await api.delete(`/property/${propertyId}/images/${propertyImageId}`, PROPERTY_CONFIG)
}
