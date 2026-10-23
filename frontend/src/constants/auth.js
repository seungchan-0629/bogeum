// 역할 값은 API 명세(BE-010 role)의 LANDLORD, TENANT, AGENT를 따른다. ADMIN은 명세에 없어 협의 필요
export const ROLES = {
  ADMIN: 'ADMIN',
  LANDLORD: 'LANDLORD',
  TENANT: 'TENANT',
  AGENT: 'AGENT',
}

export const ROLE_LABELS = {
  ADMIN: '관리자',
  LANDLORD: '임대인',
  TENANT: '임차인',
  AGENT: '중개사',
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
