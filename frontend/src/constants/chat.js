// 채팅 신고 사유와 처리 상태 문구. 사용자 신고 창(CHAT_011)과 관리자 신고 대화 화면(CHAT_001)이 같이 쓴다

// 서버 ChatReportReason과 같은 값. 기타는 신고 내용이 있어야 한다
export const REPORT_REASON_LABELS = {
  FALSE_LISTING: '허위 매물',
  RISKY_PROPERTY: '위험 매물',
  RISK_INFO: '위험 정보',
  ABUSIVE: '욕설·비매너',
  FRAUD: '사기 의심',
  ETC: '기타',
}

// 서버 ChatReportStatus와 같은 값. 빨강은 공통 배지 규칙상 실패·연체 전용이라 쓰지 않는다
export const REPORT_STATUS_BADGES = {
  RECEIVED: { label: '접수', tone: 'amber' },
  CHECKED: { label: '확인', tone: 'blue' },
  RESOLVED: { label: '처리 완료', tone: 'green' },
}
