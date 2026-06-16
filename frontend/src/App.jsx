import { Routes, Route, Navigate, Link, useNavigate } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import LoginPage from './pages/LoginPage.jsx'
import SignupPage from './pages/SignupPage.jsx'
import CharactersPage from './pages/CharactersPage.jsx'
import NewCharacterPage from './pages/NewCharacterPage.jsx'
import ChatPage from './pages/ChatPage.jsx'

// 로그인하지 않았으면 로그인 페이지로 보내는 보호용 래퍼
function RequireAuth({ children }) {
  const { isAuthenticated } = useAuth()
  return isAuthenticated ? children : <Navigate to="/login" replace />
}

function Header() {
  const { isAuthenticated, logout } = useAuth()
  const navigate = useNavigate()

  const onLogout = async () => {
    await logout()
    navigate('/login')
  }

  return (
    <header className="topbar">
      <Link to="/" className="logo">속삭</Link>
      {isAuthenticated && (
        <button className="link-btn" onClick={onLogout}>로그아웃</button>
      )}
    </header>
  )
}

export default function App() {
  return (
    <div className="app">
      <Header />
      <main className="content">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/" element={<RequireAuth><CharactersPage /></RequireAuth>} />
          <Route path="/characters/new" element={<RequireAuth><NewCharacterPage /></RequireAuth>} />
          <Route path="/chat/:roomId" element={<RequireAuth><ChatPage /></RequireAuth>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}
