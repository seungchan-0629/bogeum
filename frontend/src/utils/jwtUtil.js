import axios from 'axios'
import * as cookieUtil from './cookieUtil.js'
import { useAuthStore } from '../stores/authStore.js'

const MEMBER_COOKIE = 'member'
const safeMethods = new Set(['get', 'head', 'options'])
const authFailureCodes = new Set(['AUTH_REQUIRED', 'ACCESS_TOKEN_INVALID', 'ACCESS_TOKEN_EXPIRED'])

/**
 * 공통 Axios와 JWT 갱신 처리. authStore는 화면에 필요한 사용자 정보만 관리한다.
 * api: 보호 API용. 토큰 부착 및 만료 후 1회 재시도.
 * publicApi: 로그인/갱신/공개 조회용. 자동 갱신 인터셉터를 넣지 않아 재귀를 막는다.
 * store와 cookies 주입은 서버 없이 동시 만료와 쿠키 저장을 검증하기 위한 것이다.
 */
export function createAuthClient(store, cookies = cookieUtil) {
  const publicApi = axios.create({ baseURL: '/api', timeout: 10000, withCredentials: true })
  const api = axios.create({ baseURL: '/api', timeout: 10000, withCredentials: true })
  // 요청에는 메모리의 Access를 사용하고 새로고침 시 member 쿠키의 Refresh로 복원한다.
  let accessToken = null
  // 로그아웃 이전에 출발한 늦은 응답이 로그인 상태를 되살리지 못하게 한다.
  let authVersion = 0
  let csrfPromise = null
  let refreshPromise = null
  let restorePromise = null

  function readSessionCookie() {
    try {
      return JSON.parse(cookies.getCookie(MEMBER_COOKIE) ?? 'null')
    } catch {
      cookies.removeCookie(MEMBER_COOKIE)
      return null
    }
  }

  function setSession(data) {
    // 갱신할 때에도 최초 로그인 시 정한 만료 시각을 유지한다.
    const days = Math.max(0, (Date.parse(data.refreshExpiresAt) - Date.now()) / 86400000)
    cookies.setCookie(MEMBER_COOKIE, JSON.stringify(data), days)
    accessToken = data.accessToken
    store.getState().login(data.user)
  }

  function clearSession() {
    accessToken = null
    cookies.removeCookie(MEMBER_COOKIE)
    authVersion += 1
    store.getState().logout()
  }

  // 동시에 준비 중인 변경 요청끼리만 CSRF 발급 Promise를 공유한다.
  function ensureCsrf() {
    if (!csrfPromise) {
      csrfPromise = publicApi.get('/v1/auth/csrf')
        .then(({ data }) => data)
        .finally(() => { csrfPromise = null })
    }
    return csrfPromise
  }

  async function attachCsrf(config) {
    if (!safeMethods.has((config.method ?? 'get').toLowerCase())) {
      const csrf = await ensureCsrf()
      config.headers[csrf.headerName] = csrf.token
    }
    return config
  }
  publicApi.interceptors.request.use(attachCsrf)
  api.interceptors.request.use(async (config) => {
    await attachCsrf(config)
    // 재시도 전에 로그아웃/다른 계정 로그인으로 바뀌었으면 요청하지 않는다.
    if (config._authVersion !== undefined && config._authVersion !== authVersion) {
      throw new axios.CanceledError('로그인 상태가 변경되었습니다.')
    }
    config._authVersion = authVersion
    config._sentAccessToken = accessToken
    if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
    else delete config.headers.Authorization
    return config
  })

  function refreshOnce() {
    if (!refreshPromise) {
      const version = authVersion
      refreshPromise = publicApi.post('/v1/auth/refresh', { refreshToken: readSessionCookie()?.refreshToken })
        .then(({ data }) => {
          // 로그아웃 중 도착한 갱신 결과는 쿠키/화면에 저장하지 않는다.
          if (authVersion === version) setSession(data)
          return data
        })
        .catch((error) => {
          // 네트워크/서버 장애는 재로그인 사유로 단정하지 않는다.
          if (error.response?.status === 401 && authVersion === version) {
            clearSession()
          }
          throw error
        })
        .finally(() => { refreshPromise = null })
    }
    return refreshPromise
  }

  api.interceptors.response.use(
    (response) => response,
    async (error) => {
      const request = error.config
      const code = error.response?.data?.code
      if (!request || request._authVersion !== authVersion) throw error

      if (error.response?.status !== 401 || code !== 'ACCESS_TOKEN_EXPIRED' || request._retry) {
        if (error.response?.status === 401 && authFailureCodes.has(code)) clearSession()
        throw error
      }
      request._retry = true
      // 다른 요청이 이미 갱신했다면 최신 토큰을 재사용한다.
      if (!accessToken || accessToken === request._sentAccessToken) await refreshOnce()
      return api(request) // 원래 URL, 메서드, params, body를 유지한다.
    },
  )

  // CSRF 오류로 업무 요청을 자동 재전송하지 않는다. 다음 시도에서 새 값을 받는다.
  for (const client of [publicApi, api]) {
    client.interceptors.response.use((response) => response, (error) => {
      if (error.response?.data?.code === 'CSRF_INVALID') csrfPromise = null
      return Promise.reject(error)
    })
  }

  async function login(email, password) {
    if (refreshPromise) await refreshPromise.catch(() => {})
    clearSession()
    const version = authVersion
    const { data } = await publicApi.post('/v1/auth/login', { email, password })
    if (version !== authVersion) throw new axios.CanceledError('로그인 요청이 취소되었습니다.')
    setSession(data)
    return data.user
  }

  function restoreSession() {
    // React StrictMode가 Effect를 다시 실행해도 초기 복원은 한 번만 한다.
    if (!restorePromise) {
      restorePromise = refreshOnce()
        .catch((error) => {
          if (error.response?.status !== 401) throw error
        })
    }
    return restorePromise
  }

  async function logout() {
    let refreshToken = readSessionCookie()?.refreshToken
    clearSession()
    // 진행 중인 갱신이 성공했다면 회전된 최신 토큰으로 서버 기록을 폐기한다.
    if (refreshPromise) {
      const refreshed = await refreshPromise.catch(() => null)
      refreshToken = refreshed?.refreshToken ?? refreshToken
    }
    await publicApi.post('/v1/auth/logout', { refreshToken })
  }

  return { api, publicApi, login, logout, restoreSession }
}

export const { api, publicApi, login, logout, restoreSession } = createAuthClient(useAuthStore)
