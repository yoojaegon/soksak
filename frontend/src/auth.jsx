// 로그인 상태를 앱 전체에서 공유하기 위한 Context.
// 컴포넌트 어디서든 useAuth()로 isAuthenticated / login / logout 을 쓸 수 있다.
import { createContext, useContext, useState, useCallback, useEffect } from 'react'
import {
  api,
  getAccessToken,
  getRefreshToken,
  getValidAccessToken,
  ensureSession,
  setTokens,
  clearTokens,
} from './api.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  // 만료되지 않은 토큰이 있을 때만 로그인 상태로 시작한다.
  // (옛 토큰이 localStorage에 남아 "가짜 로그인" 되는 것을 막는다.)
  const [token, setToken] = useState(() => getValidAccessToken())
  // 시작 시 세션 확인이 끝나기 전까지는 화면 전환을 보류한다.
  const [loading, setLoading] = useState(true)

  // 앱 로드 시 한 번: access가 만료됐으면 refresh로 재발급 시도, 안 되면 로그아웃 상태.
  useEffect(() => {
    let alive = true
    ensureSession().then((ok) => {
      if (!alive) return
      setToken(ok ? getAccessToken() : null)
      setLoading(false)
    })
    return () => {
      alive = false
    }
  }, [])

  // api.js가 토큰 재발급에 실패하면 보내는 신호. 받으면 로그인 상태를 해제한다.
  // (이렇게 해야 세션 만료 시 RequireAuth가 로그인 페이지로 보낸다.)
  useEffect(() => {
    const onUnauthorized = () => setToken(null)
    window.addEventListener('soksak:unauthorized', onUnauthorized)
    return () => window.removeEventListener('soksak:unauthorized', onUnauthorized)
  }, [])

  const login = useCallback(async (loginId, password) => {
    const data = await api.login({ loginId, password })
    setTokens(data)
    setToken(data.accessToken)
  }, [])

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken()
    try {
      if (refreshToken) await api.logout(refreshToken)
    } catch {
      // 서버 로그아웃이 실패해도 클라이언트 토큰은 지운다.
    }
    clearTokens()
    setToken(null)
  }, [])

  const value = { isAuthenticated: !!token, loading, login, logout }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}
