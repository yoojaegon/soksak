// 백엔드 API 호출을 한 곳에 모아둔 모듈.
// - 토큰은 localStorage에 저장한다.
// - 모든 요청에 Authorization 헤더(Bearer)를 자동으로 붙인다.
// - accessToken이 만료(401)되면 refreshToken으로 한 번 재발급 후 재시도한다.

const ACCESS_KEY = 'soksak_access'
const REFRESH_KEY = 'soksak_refresh'

export function getAccessToken() {
  return localStorage.getItem(ACCESS_KEY)
}
export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY)
}
export function setTokens({ accessToken, refreshToken }) {
  if (accessToken) localStorage.setItem(ACCESS_KEY, accessToken)
  if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken)
}
export function clearTokens() {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

// 서버 에러를 다루기 쉽게 감싼 예외 타입
export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

async function request(path, { method = 'GET', body, auth = true, retry = true } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (auth) {
    const token = getAccessToken()
    if (token) headers.Authorization = `Bearer ${token}`
  }

  const res = await fetch(path, {
    method,
    headers,
    body: body != null ? JSON.stringify(body) : undefined,
  })

  // accessToken 만료 → refreshToken으로 한 번만 재발급 시도 후 재요청
  if (res.status === 401 && auth && retry) {
    const reissued = await tryReissue()
    if (reissued) {
      return request(path, { method, body, auth, retry: false })
    }
    // 재발급까지 실패 → 세션 만료. 토큰을 비우고 앱에 알려 로그인 화면으로 보낸다.
    clearTokens()
    window.dispatchEvent(new Event('soksak:unauthorized'))
    throw new ApiError(401, '로그인이 필요합니다.')
  }

  if (!res.ok) {
    let message = `요청에 실패했습니다 (${res.status})`
    try {
      const data = await res.json()
      // 백엔드 ErrorResponse 형식에 message가 있으면 사용
      if (data && data.message) message = data.message
    } catch {
      // 본문이 비어있거나 JSON이 아니면 기본 메시지 사용
    }
    throw new ApiError(res.status, message)
  }

  if (res.status === 204) return null
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

async function tryReissue() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false
  try {
    const res = await fetch('/auth/reissue', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!res.ok) return false
    const data = await res.json()
    setTokens(data)
    return true
  } catch {
    return false
  }
}

export const api = {
  // 인증
  signup: (body) => request('/signup', { method: 'POST', body, auth: false }),
  login: (body) => request('/auth/login', { method: 'POST', body, auth: false }),
  logout: (refreshToken) =>
    request('/auth/logout', { method: 'POST', body: { refreshToken }, auth: false }),

  // 캐릭터
  getCharacters: (page = 0, size = 20) => request(`/characters?page=${page}&size=${size}`),
  getCharacter: (id) => request(`/characters/${id}`),
  createCharacter: (body) => request('/characters', { method: 'POST', body }),

  // 채팅방
  createChatRoom: (characterId) => request('/chatrooms', { method: 'POST', body: { characterId } }),
  getChatRooms: () => request('/chatrooms'),

  // 메시지 (보내면 AI 응답이 돌아온다)
  getMessages: (roomId) => request(`/chatrooms/${roomId}/messages`),
  sendMessage: (roomId, content) =>
    request(`/chatrooms/${roomId}/messages`, { method: 'POST', body: { content } }),
  // 메시지 내용 수정
  updateMessage: (roomId, messageId, content) =>
    request(`/chatrooms/${roomId}/messages/${messageId}`, { method: 'PUT', body: { content } }),
  // 마지막 AI 응답을 다시 생성
  regenerate: (roomId) =>
    request(`/chatrooms/${roomId}/messages/regenerate`, { method: 'POST' }),
  // 해당 메시지부터 이후 메시지를 모두 삭제
  deleteFrom: (roomId, messageId) =>
    request(`/chatrooms/${roomId}/messages/${messageId}/after`, { method: 'DELETE' }),
}
