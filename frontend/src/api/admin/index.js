import { api } from '../../utils/jwtUtil.js'

export async function getInstitutionToken() {
  const { data } = await api.get('/openbanking/admin/institution-token')
  return data
}

export async function issueInstitutionToken() {
  const { data } = await api.post('/openbanking/admin/institution-token')
  return data
}
