// 로그인 후 모든 화면 왼쪽에 고정되는 사이드바.
// - 내 채팅방 목록을 보여주고, 클릭하면 해당 대화로 이동한다.
// - "+ 새 대화"는 캐릭터 목록(홈)으로 보낸다.
// - 하단에 로그아웃.
import { useEffect, useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { api } from './api.js'
import { useAuth } from './auth.jsx'

export default function Sidebar() {
  const [rooms, setRooms] = useState([])
  const location = useLocation()
  const navigate = useNavigate()
  const { logout } = useAuth()

  // 경로가 바뀔 때마다 목록을 다시 불러온다.
  // (새 방을 만들고 /chat/:id 로 이동하면 목록에도 바로 반영된다.)
  useEffect(() => {
    let alive = true
    api
      .getChatRooms()
      .then((data) => {
        if (!alive) return
        // 최신 대화가 위로 오도록 createdAt 내림차순 정렬
        const sorted = [...(data ?? [])].sort((a, b) =>
          (b.createdAt ?? '').localeCompare(a.createdAt ?? '')
        )
        setRooms(sorted)
      })
      .catch(() => {
        // 사이드바 로딩 실패는 본문 동작을 막지 않도록 조용히 무시한다.
      })
    return () => {
      alive = false
    }
  }, [location.pathname])

  const onLogout = async () => {
    await logout()
    navigate('/login')
  }

  return (
    <aside className="sidebar">
      <NavLink to="/" end className="new-chat">+ 새 대화</NavLink>
      <nav className="room-list">
        {rooms.length === 0 ? (
          <p className="muted">아직 대화가 없어요.</p>
        ) : (
          rooms.map((r) => (
            <NavLink
              key={r.id}
              to={`/chat/${r.id}`}
              className={({ isActive }) => `room-item${isActive ? ' active' : ''}`}
            >
              {r.title}
            </NavLink>
          ))
        )}
      </nav>
      <button className="link-btn logout" onClick={onLogout}>로그아웃</button>
    </aside>
  )
}
