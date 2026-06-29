// 로그인 후 모든 화면 왼쪽에 고정되는 사이드바.
// - 내 채팅방 목록을 보여주고, 클릭하면 해당 대화로 이동한다.
// - "+ 새 대화"는 캐릭터 목록(홈)으로 보낸다.
// - 하단에 로그아웃.
import { useEffect, useRef, useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { api } from './api.js'
import { useAuth } from './auth.jsx'

export default function Sidebar() {
  const [rooms, setRooms] = useState([])
  // 채팅목록 접기/펼치기 — 새로고침해도 유지되도록 localStorage에 저장
  const [collapsed, setCollapsed] = useState(
    () => localStorage.getItem('soksak_sidebar_collapsed') === '1',
  )
  // ⋮ 메뉴가 열려 있는 방 id, 이름 수정 중인 방 id, 그 입력값
  const [menuId, setMenuId] = useState(null)
  const [renamingId, setRenamingId] = useState(null)
  const [renameValue, setRenameValue] = useState('')
  // 이름변경/삭제 요청 진행 중인 방 id (중복 클릭 방지)
  const [busyId, setBusyId] = useState(null)
  // Esc 취소 직후 blur가 다시 저장을 부르는 것을 막는 플래그
  const skipBlurSave = useRef(false)
  const location = useLocation()
  const navigate = useNavigate()
  const { logout } = useAuth()

  useEffect(() => {
    localStorage.setItem('soksak_sidebar_collapsed', collapsed ? '1' : '0')
  }, [collapsed])

  // 메뉴가 열려 있으면 바깥 클릭 시 닫는다.
  useEffect(() => {
    if (menuId === null) return
    const close = () => setMenuId(null)
    document.addEventListener('click', close)
    return () => document.removeEventListener('click', close)
  }, [menuId])

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
    // 메인은 공개라 로그아웃 후에도 캐릭터 목록을 볼 수 있다.
    navigate('/')
  }

  // ⋮ → 이름 수정: 인라인 입력으로 전환
  const startRename = (room) => {
    setMenuId(null)
    setRenamingId(room.id)
    setRenameValue(room.title)
  }
  const cancelRename = () => {
    setRenamingId(null)
    setRenameValue('')
  }
  const submitRename = async (room) => {
    const title = renameValue.trim()
    // 비었거나 그대로면 저장하지 않고 닫는다.
    if (!title || title === room.title) {
      cancelRename()
      return
    }
    setBusyId(room.id)
    try {
      const updated = await api.renameChatRoom(room.id, title)
      setRooms((prev) => prev.map((r) => (r.id === room.id ? { ...r, title: updated?.title ?? title } : r)))
      cancelRename()
    } catch {
      // 실패하면 입력 상태를 유지해 다시 시도할 수 있게 둔다.
    } finally {
      setBusyId(null)
    }
  }

  // ⋮ → 삭제
  const removeRoom = async (room) => {
    setMenuId(null)
    if (!window.confirm(`'${room.title}' 대화를 삭제할까요? 대화 내용도 함께 사라집니다.`)) return
    setBusyId(room.id)
    try {
      await api.deleteChatRoom(room.id)
      setRooms((prev) => prev.filter((r) => r.id !== room.id))
      // 지금 보고 있던 방을 지웠으면 홈으로 보낸다.
      if (location.pathname === `/chat/${room.id}`) navigate('/')
    } catch {
      // 조용히 무시 (목록 동작을 막지 않는다)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <aside className={`sidebar${collapsed ? ' collapsed' : ''}`}>
      <button
        type="button"
        className="sidebar-toggle"
        onClick={() => setCollapsed((c) => !c)}
        aria-label={collapsed ? '채팅목록 펼치기' : '채팅목록 접기'}
        title={collapsed ? '채팅목록 펼치기' : '채팅목록 접기'}
      >
        {collapsed ? '»' : '«'}
      </button>

      {!collapsed && (
        <>
          {/* 1) 채팅방 목록 */}
          <NavLink to="/" end className="new-chat">+ 새 대화</NavLink>
          <nav className="room-list">
            {rooms.length === 0 ? (
              <p className="muted">아직 대화가 없어요.</p>
            ) : (
              rooms.map((r) => (
                <div className="room-row" key={r.id}>
                  {renamingId === r.id ? (
                    // 이름 수정 — 인라인 입력 (Enter 저장 / Esc·blur 취소)
                    <input
                      className="room-rename"
                      autoFocus
                      value={renameValue}
                      disabled={busyId === r.id}
                      onChange={(e) => setRenameValue(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') submitRename(r)
                        if (e.key === 'Escape') {
                          skipBlurSave.current = true
                          cancelRename()
                        }
                      }}
                      onBlur={() => {
                        // Esc로 막 취소한 경우엔 저장하지 않는다.
                        if (skipBlurSave.current) {
                          skipBlurSave.current = false
                          return
                        }
                        submitRename(r)
                      }}
                    />
                  ) : (
                    <>
                      <NavLink
                        to={`/chat/${r.id}`}
                        className={({ isActive }) => `room-item${isActive ? ' active' : ''}`}
                      >
                        {r.title}
                      </NavLink>
                      <button
                        type="button"
                        className={`room-menu-btn${menuId === r.id ? ' open' : ''}`}
                        aria-label="채팅방 메뉴"
                        onClick={(e) => {
                          e.stopPropagation()
                          setMenuId((id) => (id === r.id ? null : r.id))
                        }}
                      >
                        ⋮
                      </button>
                    </>
                  )}
                  {menuId === r.id && (
                    <div className="room-menu-pop" onClick={(e) => e.stopPropagation()}>
                      <button onClick={() => startRename(r)}>이름 수정</button>
                      <button className="danger" onClick={() => removeRoom(r)}>삭제</button>
                    </div>
                  )}
                </div>
              ))
            )}
          </nav>

          {/* 2) 내 캐릭터 · 내 페르소나 */}
          <hr className="sidebar-divider" />
          <nav className="sidebar-nav">
            <NavLink
              to="/my-characters"
              className={({ isActive }) => `room-item${isActive ? ' active' : ''}`}
            >
              내 캐릭터
            </NavLink>
            <NavLink
              to="/personas"
              className={({ isActive }) => `room-item${isActive ? ' active' : ''}`}
            >
              내 페르소나
            </NavLink>
          </nav>

          {/* 3) 로그아웃 */}
          <hr className="sidebar-divider" />
          <button className="link-btn logout" onClick={onLogout}>로그아웃</button>
        </>
      )}
    </aside>
  )
}
