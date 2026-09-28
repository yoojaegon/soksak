import { Routes, Route, Navigate, Link, Outlet } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import Sidebar from './Sidebar.jsx'
import AccountMenu from './components/AccountMenu.jsx'
import LoginPage from './pages/LoginPage.jsx'
import SignupPage from './pages/SignupPage.jsx'
import CharactersPage from './pages/CharactersPage.jsx'
import NewCharacterPage from './pages/NewCharacterPage.jsx'
import CharacterEditPage from './pages/CharacterEditPage.jsx'
import MyCharactersPage from './pages/MyCharactersPage.jsx'
import PersonasPage from './pages/PersonasPage.jsx'
import CreditsPage from './pages/CreditsPage.jsx'
import ProfilePage from './pages/ProfilePage.jsx'
import ChatPage from './pages/ChatPage.jsx'

// 로그인하지 않았으면 로그인 페이지로 보내는 보호용 래퍼
function RequireAuth({ children }) {
  const { isAuthenticated, loading } = useAuth()
  // 시작 시 세션 확인 중에는 잠깐 아무것도 그리지 않아 로그인 화면 깜빡임을 막는다.
  if (loading) return null
  return isAuthenticated ? children : <Navigate to="/login" replace />
}

function Header() {
  const { isAuthenticated } = useAuth()
  return (
    <header className="topbar">
      <Link to="/" className="logo">속삭</Link>
      {/* 로그인했으면 계정 메뉴, 아니면 로그인 진입점.
          남은 마디 배지는 헤더에 두지 않는다 — 쓰는 자리(채팅 입력칸 옆)에서 보이는 게 맞다(D13).
          계정 메뉴 안의 잔액 줄은 펼쳤을 때만 보이는 정보라 그 결정과 부딪히지 않는다. */}
      {isAuthenticated ? (
        <AccountMenu />
      ) : (
        <Link to="/login" className="link-btn">로그인</Link>
      )}
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

// 공개 메인(/): 로그인하면 사이드바 포함 레이아웃, 아니면 본문만.
function HomeLayout() {
  const { isAuthenticated, loading } = useAuth()
  if (loading) return null
  if (isAuthenticated) return <AppLayout />
  return (
    <main className="content">
      <Outlet />
    </main>
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

        {/* 공개 메인: 로그인 없이도 캐릭터 둘러보기 */}
        <Route path="/" element={<HomeLayout />}>
          <Route index element={<CharactersPage />} />
        </Route>

        {/* 로그인 필요 + 사이드바 레이아웃 */}
        <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
          <Route path="/characters/new" element={<NewCharacterPage />} />
          <Route path="/characters/:id/edit" element={<CharacterEditPage />} />
          <Route path="/my-characters" element={<MyCharactersPage />} />
          <Route path="/personas" element={<PersonasPage />} />
          <Route path="/credits" element={<CreditsPage />} />
          <Route path="/me" element={<ProfilePage />} />
          <Route path="/chat/:roomId" element={<ChatPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  )
}
