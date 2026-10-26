// 백엔드 Role과 로그인 응답의 role 값을 따른다
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

// 백엔드 LoginProvider와 로그인 응답의 lastLoginProvider 값을 따른다
export const LOGIN_TYPES = {
  LOCAL: 'LOCAL',
  NAVER: 'NAVER',
  KAKAO: 'KAKAO',
  GOOGLE: 'GOOGLE',
}

export const LOGIN_TYPE_LABELS = {
  LOCAL: '아이디',
  NAVER: '네이버',
  KAKAO: '카카오',
  GOOGLE: '구글',
}

// 응답의 lastLoginProvider를 최근 로그인 수단 안내용으로 저장한다. 인증 상태 저장용이 아니다
export const LAST_LOGIN_TYPE_KEY = 'bogeum.lastLoginType'
