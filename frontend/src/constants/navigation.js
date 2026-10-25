// 역할별 헤더 메뉴. 로그인한 사용자의 role(constants/auth.js의 ROLES)에 맞는 목록을 보여준다
// GUEST는 로그인하지 않은 사용자. 매물 찾기를 누르면 로그인 안내 페이지가 뜬다
export const NAV_ITEMS_BY_ROLE = {
  GUEST: [{ label: '매물 찾기', to: '/properties' }],
  TENANT: [
    { label: '매물 찾기', to: '/properties' },
    { label: '관심 매물', to: '/favorites' },
    { label: '내 집 관리', to: '/my-home' },
    { label: '채팅', to: '/chats' },
  ],
  LANDLORD: [
    { label: '매물 찾기', to: '/properties' },
    { label: '내 매물', to: '/my-properties' },
    { label: '월세 관리', to: '/rent/collections' },
    { label: '입주·AS', to: '/care' },
    { label: '채팅', to: '/chats' },
  ],
  // '내 계약 관리'는 중개사에게만 있다
  AGENT: [
    { label: '중개 신청', to: '/agent/requests' },
    { label: '내 계약 관리', to: '/agent/contracts' },
    { label: '중개 이력', to: '/agent/history' },
    { label: '매물 찾기', to: '/properties' },
    { label: '채팅', to: '/chats' },
  ],
  // 관리자는 /admin 화면을 따로 쓴다. 메인 화면에 왔을 때만 이 메뉴가 보인다
  ADMIN: [{ label: '관리자 화면', to: '/admin' }],
}

// 역할별 마이페이지 주소
export const MY_PAGE_PATH_BY_ROLE = {
  TENANT: '/mypage',
  LANDLORD: '/mypage',
  AGENT: '/agent/mypage',
}
