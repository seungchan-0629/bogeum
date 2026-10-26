// AS 처리 상태 (AS_003: 접수 완료 → 확인중 → 처리중 → 처리 완료)
// 키 이름(RECEIVED 등)은 API 명세에 없어 백엔드와 협의 필요
export const AS_STATUS = {
  RECEIVED: { label: '접수 완료', tone: 'gray' },
  CHECKING: { label: '확인중', tone: 'amber' },
  IN_PROGRESS: { label: '처리중', tone: 'blue' },
  DONE: { label: '처리 완료', tone: 'green' },
}

// 클레임 사유 (CLAIM_001). 계약상 역할(CONTRACT_ROLES)마다 고를 수 있는 사유가 다르다
export const CLAIM_REASONS_BY_ROLE = {
  TENANT: ['AS 요청 미응답', '실제 매물과 다름', '기타'],
  LANDLORD: ['월세 미납', '악성 세입자', '기타'],
}
