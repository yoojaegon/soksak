import { Routes, Route, Navigate, Link, Outlet } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import Sidebar from './Sidebar.jsx'
import LoginPage from './pages/LoginPage.jsx'
import SignupPage from './pages/SignupPage.jsx'
import CharactersPage from './pages/CharactersPage.jsx'
import NewCharacterPage from './pages/NewCharacterPage.jsx'
import PersonasPage from './pages/PersonasPage.jsx'
import ChatPage from './pages/ChatPage.jsx'

// 로그인하지 않았으면 로그인 페이지로 보내는 보호용 래퍼
function RequireAuth({ children }) {
  const { isAuthenticated } = useAuth()
  return isAuthenticated ? children : <Navigate to="/login" replace />
}

function Header() {
  return (
    <header className="topbar">
      <Link to="/" className="logo">속삭</Link>
    </header>
  )
}

// 로그인 후 화면 공통 레이아웃: 왼쪽 사이드바 + 오른쪽 본문
function AppLayout() {
  return (
    <div className="layout">
      <Sidebar />
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}

export default function App() {
  return (
    <div className="app">
      <Header />
      <Routes>
        {/* 로그인/회원가입은 사이드바 없이 단독 화면 */}
        <Route path="/login" element={<main className="content"><LoginPage /></main>} />
        <Route path="/signup" element={<main className="content"><SignupPage /></main>} />

        {/* 로그인 필요 + 사이드바 레이아웃 */}
        <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
          <Route path="/" element={<CharactersPage />} />
          <Route path="/characters/new" element={<NewCharacterPage />} />
          <Route path="/personas" element={<PersonasPage />} />
          <Route path="/chat/:roomId" element={<ChatPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  )
}
