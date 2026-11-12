import { api } from '../../utils/jwtUtil.js'

// AGENT_001: 화면 주소 /admin/agents와 겹치지 않는 프론트 전용 프록시 주소다.
export async function getAdminAgents(keyword, page, signal) {
  const { data } = await api.get('/api/admin/agents', { params: { keyword, page }, signal })
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
