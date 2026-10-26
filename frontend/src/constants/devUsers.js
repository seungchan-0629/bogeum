// [개발용] 백엔드 로그인 없이 역할별 화면을 확인하기 위한 샘플 사용자
// 로그인 화면의 '개발용 테스트 로그인'에서만 쓰며, npm run dev 에서만 보인다 (빌드 결과물에는 버튼이 없다)
// 로그인 API(BE-001)가 연결되면 이 파일과 DevLoginPanel.jsx를 지운다
export const DEV_USERS = [
  { label: '사용자 (임대인·임차인 계약 있음)', homePath: '/', user: { userId: 1, name: '김보금', role: 'USER', contractRoles: ['LANDLORD', 'TENANT'] } },
  { label: '사용자 (계약 없음)', homePath: '/', user: { userId: 2, name: '박새집', role: 'USER', contractRoles: [] } },
  { label: '중개사', homePath: '/agent/requests', user: { userId: 3, name: '이중개', role: 'AGENT', approvalStatus: 'APPROVED' } },
  { label: '중개사 (승인 대기)', homePath: '/agent/pending', user: { userId: 4, name: '정새봄', role: 'AGENT', approvalStatus: 'PENDING' } },
  { label: '관리자', homePath: '/admin', user: { userId: 5, name: '관리자', role: 'ADMIN' } },
]
