import { api } from '../../utils/jwtUtil.js'

// AS API는 /api 아래가 아니라서 공통 baseURL을 비우고 보낸다. 토큰은 api 인터셉터가 붙인다.
const AS_CONFIG = { baseURL: '' }

// AS_002 받은 AS 목록. 본인 소유 매물의 접수만 최신순으로 한 페이지 5개씩 온다
export async function getReceivedAsRequests(page = 1, signal) {
  const { data } = await api.get('/as-requests/received', { ...AS_CONFIG, params: { page }, signal })
  return data
}

// AS_002 AS 상세. 사진은 일정 시간만 열리는 조회 주소(url)로 온다
export async function getAsRequest(asRequestId, signal) {
  const { data } = await api.get(`/as-requests/${asRequestId}`, { ...AS_CONFIG, signal })
  return data
}

// AS_003 조치 사항 답변. 작성자는 서버가 매물 소유자로 정하고, 처리 완료된 AS에는 등록할 수 없다
export async function createAsComment(asRequestId, content) {
  const { data } = await api.post(`/as-requests/${asRequestId}/comments`, { content }, AS_CONFIG)
  return data
}
