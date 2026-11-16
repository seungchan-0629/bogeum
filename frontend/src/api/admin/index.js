import { api } from '../../utils/jwtUtil.js'

// AGENT_001: 화면 주소 /admin/agents와 겹치지 않는 프론트 전용 프록시 주소다.
export async function getAdminAgents(keyword, page, signal) {
  const { data } = await api.get('/api/admin/agents', { params: { keyword, page }, signal })
  return data
}

// RISK_001: 인증·CSRF는 공통 api가 처리하고 조회와 같은 전용 프록시를 사용한다.
export async function createAdminRiskUser({ userId, reasonType, detail }) {
  const { data } = await api.post('/api/admin/risk-users', { userId, reasonType, detail })
  return data
}

// RISK_002: 화면과 API의 /admin/risk-users 경로 충돌을 전용 프록시로 피한다.
export async function getAdminRiskUsers(type, keyword, page, signal) {
  const { data } = await api.get('/api/admin/risk-users', { params: { type, keyword, page }, signal })
  return data
}

// RISK_003: 목록의 riskInfoId를 사용하며 detail을 생략하지 않는다.
export async function updateAdminRiskUser({ riskInfoId, reasonType, detail, updateReason }) {
  const { data } = await api.patch('/api/admin/risk-users/' + riskInfoId, { reasonType, detail, updateReason })
  return data
}

// RENT_001: 실제 BE GET /admin/rents/payments. 인증은 공통 api를 재사용한다.
export async function getAdminRentPayments(keyword, page, signal) {
  const { data } = await api.get('/api/admin/rents/payments', { params: { keyword, page }, signal })
  return data
}

export async function getInstitutionToken() {
  const { data } = await api.get('/openbanking/admin/institution-token')
  return data
}

export async function issueInstitutionToken() {
  const { data } = await api.post('/openbanking/admin/institution-token')
  return data
}
