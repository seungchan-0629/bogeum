import { api } from '../../utils/jwtUtil.js'

// 계약 API는 /api 아래가 아니라서 공통 baseURL(/api)을 비우고 보낸다. 토큰·CSRF는 api 인터셉터가 붙인다.
const CONTRACT_CONFIG = { baseURL: '' }

// PROPERTY_011 계약 정보 등록. 계약 정보는 JSON 파트(request), 계약서는 이미지 파트(images)로 함께 보낸다
// 서버가 이미지를 검사·변환해 올리느라 오래 걸릴 수 있어 기본 제한 시간(10초)보다 길게 기다린다
export async function createContract(request, images) {
  const formData = new FormData()
  formData.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))
  images.forEach((image) => formData.append('images', image))
  const { data } = await api.post('/contract-details', formData, { ...CONTRACT_CONFIG, timeout: 60000 })
  return data
}

// AGENT_003 중개 이력. 본인이 중개해 계약 정보를 등록한 계약을 최근 계약일 순서로 10개씩 준다 (page는 1부터)
export async function getContracts(page, signal) {
  const { data } = await api.get('/contract-details', { ...CONTRACT_CONFIG, params: { page }, signal })
  return data
}

// PROPERTY_011 계약 등록 화면의 첫 값. 매물 거래 조건, 임대인(매물의 임대인), 임차인 후보(이 매물로 상담한 사람)를 준다
export async function getContractCandidates(propertyId, signal) {
  const { data } = await api.get('/contract/candidates', { ...CONTRACT_CONFIG, params: { propertyId }, signal })
  return data
}
