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

export async function getInstitutionToken() {
  const { data } = await api.get('/openbanking/admin/institution-token')
  return data
}

export async function issueInstitutionToken() {
  const { data } = await api.post('/openbanking/admin/institution-token')
  return data
}
