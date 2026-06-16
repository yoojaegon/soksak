// 로그인 상태를 앱 전체에서 공유하기 위한 Context.
// 컴포넌트 어디서든 useAuth()로 isAuthenticated / login / logout 을 쓸 수 있다.
import { createContext, useContext, useState, useCallback } from 'react'
import { api, getAccessToken, getRefreshToken, setTokens, clearTokens } from './api.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  // 새로고침해도 토큰이 남아있으면 로그인 상태를 유지한다.
  const [token, setToken] = useState(getAccessToken())

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

  const value = { isAuthenticated: !!token, login, logout }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}
