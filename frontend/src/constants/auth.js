// 회원 종류. 가입할 때 사용자(USER) / 중개사(AGENT)로 나뉘고 바뀌지 않는다
// TODO: API 명세(BE-010 role)는 아직 LANDLORD, TENANT, AGENT라 USER로 바꾸는 것을 백엔드와 협의 필요. ADMIN도 명세에 없어 협의 필요
export const ROLES = {
  ADMIN: 'ADMIN',
  USER: 'USER',
  AGENT: 'AGENT',
}

export const ROLE_LABELS = {
  ADMIN: '관리자',
  USER: '사용자',
  AGENT: '중개사',
}

// 계약 안에서의 역할. 중개사가 계약을 등록할 때 정해진다 (PROPERTY_011)
// 한 사용자가 어떤 계약에선 임대인, 다른 계약에선 임차인일 수 있다
export const CONTRACT_ROLES = {
  LANDLORD: 'LANDLORD',
  TENANT: 'TENANT',
}

// 로그인 응답의 loginType 값 (BE-001, BE-004). 실제 값은 백엔드와 협의 필요
export const LOGIN_TYPES = {
  NORMAL: 'NORMAL',
  NAVER: 'NAVER',
  KAKAO: 'KAKAO',
  GOOGLE: 'GOOGLE',
}

export const LOGIN_TYPE_LABELS = {
  NORMAL: '아이디',
  NAVER: '네이버',
  KAKAO: '카카오',
  GOOGLE: '구글',
}

// 최근 로그인 수단은 프론트가 저장한다 (BE-004 비고)
export const LAST_LOGIN_TYPE_KEY = 'bogeum.lastLoginType'
