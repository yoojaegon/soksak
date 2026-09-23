// 백엔드 API 호출을 한 곳에 모아둔 모듈.
// - 토큰은 localStorage에 저장한다.
// - 모든 요청에 Authorization 헤더(Bearer)를 자동으로 붙인다.
// - accessToken이 만료(401)되면 refreshToken으로 한 번 재발급 후 재시도한다.

// 모든 API 경로 앞에 붙는 접두사(백엔드 server.servlet.context-path와 같은 값).
// 화면 라우트와 API 경로가 겹쳐서(/characters/3/edit 은 화면, /characters/3 은 API),
// 배포 환경의 nginx가 정적 파일 요청과 API 요청을 가르는 기준이 이 접두사다.
// ⚠️ 업로드된 이미지 주소(imageUrl)는 서버가 이미 접두사를 붙여 내려주므로 여기서 또 붙이지 않는다.
const API_BASE = '/api'

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

// JWT payload(가운데 조각)를 디코드. 실패하면 null.
function decodeJwt(token) {
  try {
    return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
  } catch {
    return null
  }
}

// 만료되지 않은 accessToken만 돌려준다. 없거나 만료됐으면 null.
// (localStorage에 남은 옛 토큰으로 "가짜 로그인" 되는 것을 막는다.)
export function getValidAccessToken() {
  const token = getAccessToken()
  if (!token) return null
  const payload = decodeJwt(token)
  if (payload && typeof payload.exp === 'number' && payload.exp * 1000 <= Date.now()) {
    return null
  }
  return token
}

// 앱 시작 시 세션 확인: 유효한 access가 있으면 true,
// 없지만 refresh가 있으면 재발급을 시도, 둘 다 실패하면 토큰을 비우고 false.
export async function ensureSession() {
  if (getValidAccessToken()) return true
  if (getRefreshToken() && (await tryReissue())) return true
  clearTokens()
  return false
}

// 서버 에러를 다루기 쉽게 감싼 예외 타입.
// code: 백엔드 ErrorCode 이름(ROOM_BUSY 등) 또는 클라이언트측 표식('NETWORK'). 없으면 null.
export class ApiError extends Error {
  constructor(status, message, code = null) {
    super(message)
    this.status = status
    this.code = code
  }
}

async function request(path, { method = 'GET', body, auth = true, retry = true } = {}) {
  // 파일 업로드(FormData)는 그대로 보낸다. Content-Type을 직접 지정하면 multipart 경계값이
  // 빠져서 서버가 파트를 못 읽으므로, 이때만 브라우저가 헤더를 붙이게 둔다.
  const isFormData = body instanceof FormData
  const headers = isFormData ? {} : { 'Content-Type': 'application/json' }
  if (auth) {
    // 만료된 토큰은 붙이지 않는다. 없으면 아래 401 흐름에서 refresh로 재발급한다.
    const token = getValidAccessToken()
    if (token) headers.Authorization = `Bearer ${token}`
  }

  const res = await fetch(API_BASE + path, {
    method,
    headers,
    body: body == null ? undefined : isFormData ? body : JSON.stringify(body),
  })

  // accessToken 만료(401) 처리
  if (res.status === 401 && auth) {
    // 아직 재시도 전이면 refreshToken으로 한 번 재발급 후 재요청.
    if (retry) {
      const reissued = await tryReissue()
      if (reissued) {
        return request(path, { method, body, auth, retry: false })
      }
    }
    // 재발급 실패, 또는 재발급 후 재시도했는데도 여전히 401 → 세션 만료.
    // (재시도 요청은 retry=false로 들어와 여기서 곧장 정리된다.)
    // 토큰을 비우고 앱에 알려 로그인 화면으로 보낸다.
    clearTokens()
    window.dispatchEvent(new Event('soksak:unauthorized'))
    throw new ApiError(401, '로그인이 필요합니다.')
  }

  if (!res.ok) {
    let message = `요청에 실패했습니다 (${res.status})`
    let code = null
    try {
      const data = await res.json()
      // 백엔드 ErrorResponse 형식에 message가 있으면 사용
      if (data && data.message) message = data.message
      // code도 함께 실어 보낸다 — 호출부가 INSUFFICIENT_CREDIT 같은 걸 문구가 아니라
      // 코드로 가를 수 있어야 한다(문구는 바뀌어도 코드는 계약이다).
      if (data && data.code) code = data.code
    } catch {
      // 본문이 비어있거나 JSON이 아니면 기본 메시지 사용
    }
    throw new ApiError(res.status, message, code)
  }

  if (res.status === 204) return null
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

// SSE(text/event-stream) 응답을 fetch로 직접 소비한다.
// EventSource는 Authorization 헤더를 못 붙여서, 기존 Bearer/401-재발급 흐름을 유지하려고 fetch를 쓴다.
// 백엔드 이벤트 계약: token({content}), done(MessageResponse), error({code, message}).
async function streamRequest(path, { body } = {}, { onToken, onDone, onError } = {}, retry = true) {
  const headers = { 'Content-Type': 'application/json', Accept: 'text/event-stream' }
  const token = getValidAccessToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let res
  try {
    res = await fetch(API_BASE + path, {
      method: 'POST',
      headers,
      body: body != null ? JSON.stringify(body) : undefined,
    })
  } catch (err) {
    // 요청이 서버에 닿지 못함 → 저장된 것도 없다('NETWORK'로 표식).
    onError?.(new ApiError(0, '연결에 실패했습니다.', 'NETWORK'))
    return
  }

  // accessToken 만료(401) → 한 번 재발급 후 재시도.
  if (res.status === 401) {
    if (retry && (await tryReissue())) {
      return streamRequest(path, { body }, { onToken, onDone, onError }, false)
    }
    clearTokens()
    window.dispatchEvent(new Event('soksak:unauthorized'))
    onError?.(new ApiError(401, '로그인이 필요합니다.'))
    return
  }

  if (!res.ok || !res.body) {
    // 스트림이 열리기 전에 거절당한 경우다(마디 부족 402 등) — 본문이 SSE가 아니라 평범한
    // ErrorResponse JSON이라 여기서 code·message를 꺼내 준다. 못 꺼내도 상태코드는 남긴다.
    let message = `요청에 실패했습니다 (${res.status})`
    let code = null
    try {
      const data = await res.json()
      if (data?.message) message = data.message
      if (data?.code) code = data.code
    } catch {
      // 본문이 없거나 JSON이 아님 — 기본 문구로 둔다.
    }
    onError?.(new ApiError(res.status, message, code))
    return
  }

  // SSE 파싱: 스트림을 읽어 '\n\n'(이벤트 경계)으로 끊고, event/data 필드를 뽑는다.
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''
  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })

      let sep
      while ((sep = buf.indexOf('\n\n')) !== -1) {
        const raw = buf.slice(0, sep)
        buf = buf.slice(sep + 2)

        let event = 'message'
        const dataLines = []
        for (const line of raw.split('\n')) {
          if (line.startsWith('event:')) event = line.slice(6).trim()
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
        }
        if (dataLines.length === 0) continue

        // data는 백엔드가 JSON으로 실어보낸다(개행 안전).
        let payload
        try {
          payload = JSON.parse(dataLines.join('\n'))
        } catch {
          continue
        }

        if (event === 'token') onToken?.(payload.content ?? '')
        else if (event === 'done') { onDone?.(payload); return }
        else if (event === 'error') { onError?.(new ApiError(0, payload.message || 'AI 응답 실패', payload.code)); return }
      }
    }
    // done 이벤트 없이 스트림이 끝남(중단) → 에러로 처리.
    onError?.(new ApiError(0, 'AI 응답이 중단되었습니다.'))
  } catch (err) {
    onError?.(new ApiError(0, 'AI 응답이 중단되었습니다.'))
  }
}

// 진행 중인 재발급 프로미스(single-flight). 동시에 여러 401이 나도
// 재발급은 한 번만 수행하고 나머지는 그 결과를 함께 기다린다.
// (백엔드가 리프레시 토큰을 회전시킬 때, 겹친 재발급이 이미 소비된 토큰을
//  제시해 실패 → 멀쩡한 세션이 로그아웃되는 것을 막는다.)
let reissuePromise = null

function tryReissue() {
  if (!reissuePromise) {
    reissuePromise = doReissue().finally(() => {
      reissuePromise = null
    })
  }
  return reissuePromise
}

async function doReissue() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false
  try {
    // request()를 거치지 않고 직접 부르는 유일한 경로라 접두사를 따로 붙인다.
    const res = await fetch(`${API_BASE}/auth/reissue`, {
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
  // 모델 카탈로그(선택지·라벨·기본값·마디 계수)의 단일 출처는 백엔드.
  // 응답 계약: { models: [{ id, label, cost, thinking }...], default, thinkingLevels }
  getModels: () => request('/models'),

  // 마디(소모 재화) 잔액. 입력칸 옆 배지가 띄우고, 전송이 끝날 때마다 다시 부른다.
  getCredits: () => request('/credits/me'),
  // 살 수 있는 묶음. 모델 카탈로그와 같이 백엔드가 단일 출처다 — 여기에 폴백 목록을 두지 않는다.
  // 응답 계약: [{ id, amount, priceKrw, label }...]
  getCreditPacks: () => request('/credits/packs'),
  // 충전. 수량이 아니라 묶음 id를 보낸다(수량을 보내면 여기서 고쳐 얼마든지 받을 수 있다).
  topUpCredits: (packId) => request('/credits/topup', { method: 'POST', body: { packId } }),

  // 인증
  signup: (body) => request('/signup', { method: 'POST', body, auth: false }),
  login: (body) => request('/auth/login', { method: 'POST', body, auth: false }),
  logout: (refreshToken) =>
    request('/auth/logout', { method: 'POST', body: { refreshToken }, auth: false }),

  // 캐릭터
  // sort는 스프링 페이징 규약 그대로 "<field>,<dir>" (예: likeCount,desc).
  // q(키워드)는 비어 있으면 아예 안 붙여서 백엔드가 전체 목록으로 처리하게 둔다.
  getCharacters: ({ page = 0, size = 20, sort = 'createdAt,desc', q = '', tag = '' } = {}) => {
    const params = new URLSearchParams({ page, size, sort })
    if (q.trim()) params.set('q', q.trim())
    if (tag) params.set('tag', tag) // 선택된 장르 enum 이름. 없으면 안 붙여 전체 조회.
    return request(`/characters?${params.toString()}`)
  },
  getCharacter: (id) => request(`/characters/${id}`),
  // 내가 만든 캐릭터 목록 (소유 판별·로어북 진입에 사용)
  getMyCharacters: () => request('/characters/me'),
  // 이미지 업로드 — 파일을 보내면 저장된 주소({ url })를 돌려준다.
  // 캐릭터 저장과는 별개라, 업로드만 하고 저장을 취소하면 파일은 서버에 남는다(고아 파일).
  uploadImage: (file) => {
    const form = new FormData()
    form.append('file', file)
    return request('/uploads/images', { method: 'POST', body: form })
  },

  createCharacter: (body) => request('/characters', { method: 'POST', body }),
  updateCharacter: (id, body) => request(`/characters/${id}`, { method: 'PUT', body }),
  deleteCharacter: (id) => request(`/characters/${id}`, { method: 'DELETE' }),

  // 로어북 (캐릭터별 설정 지식 — 소유자만 접근)
  getLores: (characterId) => request(`/characters/${characterId}/lores`),
  getLore: (characterId, id) => request(`/characters/${characterId}/lores/${id}`),
  createLore: (characterId, body) =>
    request(`/characters/${characterId}/lores`, { method: 'POST', body }),
  updateLore: (characterId, id, body) =>
    request(`/characters/${characterId}/lores/${id}`, { method: 'PUT', body }),
  // on/off 토글. boolean은 body가 아니라 query param으로 보낸다.
  updateLoreEnabled: (characterId, id, enabled) =>
    request(`/characters/${characterId}/lores/${id}/enabled?enabled=${enabled}`, { method: 'PATCH' }),
  deleteLore: (characterId, id) =>
    request(`/characters/${characterId}/lores/${id}`, { method: 'DELETE' }),

  // 유저 페르소나 (대화 시 내가 어떤 사람으로 등장할지)
  getUserPersonas: () => request('/user-personas'),
  createUserPersona: (body) => request('/user-personas', { method: 'POST', body }),
  updateUserPersona: (id, body) => request(`/user-personas/${id}`, { method: 'PUT', body }),
  // 기본 페르소나로 지정 (대화에 기본으로 쓰임)
  setDefaultUserPersona: (id) => request(`/user-personas/${id}/default`, { method: 'PATCH' }),
  // 삭제 (마지막 한 개는 백엔드가 막는다)
  deleteUserPersona: (id) => request(`/user-personas/${id}`, { method: 'DELETE' }),

  // 채팅방
  // 생성 시 모델은 안 보낸다 — room.model이 null이면 "기본 모델 따라감"이고,
  // 기본값이 뭔지는 백엔드 카탈로그가 정한다(대화 중 updateModel로 변경).
  createChatRoom: async (characterId) => {
    const room = await request('/chatrooms', { method: 'POST', body: { characterId } })
    // 사이드바 목록이 새 방을 바로 반영하도록 알린다.
    window.dispatchEvent(new Event('soksak:rooms-changed'))
    return room
  },
  getChatRooms: () => request('/chatrooms'),
  getChatRoom: (id) => request(`/chatrooms/${id}`),
  // 채팅방 이름 변경
  renameChatRoom: (id, title) => request(`/chatrooms/${id}`, { method: 'PATCH', body: { title } }),
  // 대화 중 사용할 모델 변경. model은 문자열이라 토글과 달리 body로 보낸다(rename과 동일 패턴).
  updateModel: (id, model) =>
    request(`/chatrooms/${id}/model`, { method: 'PATCH', body: { model } }),
  // 추론 깊이 변경. 모델이 그 레벨을 받는지는 서버가 보낼 때 맞춰주므로 여기서 막지 않는다.
  updateThinking: (id, thinkingLevel) =>
    request(`/chatrooms/${id}/thinking`, { method: 'PATCH', body: { thinkingLevel } }),
  // 채팅방 삭제
  deleteChatRoom: (id) => request(`/chatrooms/${id}`, { method: 'DELETE' }),
  // 장기기억(자동 요약문) 조회. 대화 중에 갱신되므로 방 정보와 함께 받지 않고
  // 설정 패널을 열 때마다 따로 부른다(목록 응답이 긴 텍스트로 불어나는 것도 막는다).
  getSummary: (id) => request(`/chatrooms/${id}/summary`),
  // 장기기억 직접 수정. 빈 문자열은 "지우기"라 유효한 값이다(백엔드에서 null로 저장).
  updateSummary: (id, summary) =>
    request(`/chatrooms/${id}/summary`, { method: 'PATCH', body: { summary } }),
  // 대화 설정(프롬프트 모드/스포일러 접기) 변경. boolean 토글은 body가 아니라 query param으로 보낸다.
  updateConfig: (id, { writingToggle, foldSpoilerToggle }) =>
    request(
      `/chatrooms/${id}/config?writingToggle=${writingToggle}&foldSpoilerToggle=${foldSpoilerToggle}`,
      { method: 'PATCH' },
    ),

  // 메시지 (보내면 AI 응답이 돌아온다)
  getMessages: (roomId) => request(`/chatrooms/${roomId}/messages`),
  sendMessage: (roomId, content) =>
    request(`/chatrooms/${roomId}/messages`, { method: 'POST', body: { content } }),
  // 메시지 전송 → AI 응답을 토큰 단위로 스트리밍(SSE)으로 받는다.
  sendMessageStream: (roomId, content, handlers) =>
    streamRequest(`/chatrooms/${roomId}/messages/stream`, { body: { content } }, handlers),
  // 마지막 AI 응답을 스트리밍으로 다시 생성
  regenerateStream: (roomId, handlers) =>
    streamRequest(`/chatrooms/${roomId}/messages/regenerate/stream`, {}, handlers),
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
