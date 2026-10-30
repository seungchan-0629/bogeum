// 인증 정보를 포함해 JavaScript에서 접근 가능한 쿠키를 관리한다.
// HttpOnly인 BOGEUM_CSRF 쿠키는 서버에서 관리한다.

/** 쿠키를 days일 동안 저장한다. days를 생략하면 세션 쿠키로 저장한다. */
export function setCookie(name, value, days, path = '/') {
  let cookie = `${encodeURIComponent(name)}=${encodeURIComponent(value)}; Path=${path}; SameSite=Lax`
  if (days !== undefined) {
    cookie += `; Max-Age=${Math.floor(days * 24 * 60 * 60)}`
  }
  if (window.location.protocol === 'https:') cookie += '; Secure'
  document.cookie = cookie
}

/** 저장된 문자열을 반환한다. 쿠키가 없으면 undefined를 반환한다. */
export function getCookie(name) {
  const prefix = `${encodeURIComponent(name)}=`
  const cookie = document.cookie.split(';').map((value) => value.trim())
    .find((value) => value.startsWith(prefix))
  return cookie === undefined ? undefined : decodeURIComponent(cookie.slice(prefix.length))
}

/** 저장할 때와 같은 경로를 지정해야 해당 쿠키가 삭제된다. */
export function removeCookie(name, path = '/') {
  setCookie(name, '', 0, path)
}
