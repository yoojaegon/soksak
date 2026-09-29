// 로그인 후 모든 화면 왼쪽에 고정되는 사이드바.
// - 내 채팅방 목록을 보여주고, 클릭하면 해당 대화로 이동한다.
// - "+ 새 대화"는 캐릭터 목록(홈)으로 보낸다.
// - 내 캐릭터·내 페르소나·마디 충전·로그아웃은 상단바 계정 메뉴(AccountMenu)로 옮겼다.
import { useEffect, useRef, useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { api } from './api.js'
import { useConfirm } from './confirm.jsx'
import { useOutsideClose } from './useOutsideClose.js'

// 이 폭 이하에선 사이드바가 본문 위에 겹쳐 뜨는 드로어가 된다. styles.css의 @media 값과 같아야 한다.
const DRAWER_QUERY = '(max-width: 768px)'

function useMediaQuery(query) {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches)
  useEffect(() => {
    const mql = window.matchMedia(query)
    const onChange = () => setMatches(mql.matches)
    mql.addEventListener('change', onChange)
    return () => mql.removeEventListener('change', onChange)
  }, [query])
  return matches
}

export default function Sidebar() {
  const [rooms, setRooms] = useState([])
  // 채팅목록 접기/펼치기 — 새로고침해도 유지되도록 localStorage에 저장 (넓은 화면 전용)
  const [collapsed, setCollapsed] = useState(
    () => localStorage.getItem('soksak_sidebar_collapsed') === '1',
  )
  // 좁은 화면의 드로어 열림 — collapsed와 따로 둔다. 저장된 '펼침'을 그대로 쓰면
  // 넓은 화면에서 펼쳐 둔 사람이 폰으로 들어올 때 드로어가 본문을 덮은 채 시작한다.
  const narrow = useMediaQuery(DRAWER_QUERY)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const folded = narrow ? !drawerOpen : collapsed
  const asideRef = useRef(null)
  const toggleRef = useRef(null)
  // ⋮ 메뉴가 열려 있는 방 id, 이름 수정 중인 방 id, 그 입력값
  const [menuId, setMenuId] = useState(null)
  const [renamingId, setRenamingId] = useState(null)
  const [renameValue, setRenameValue] = useState('')
  // 이름변경/삭제 요청 진행 중인 방 id (중복 클릭 방지)
  const [busyId, setBusyId] = useState(null)
  // Esc 취소 직후 blur가 다시 저장을 부르는 것을 막는 플래그
  const skipBlurSave = useRef(false)
  // 삭제하면 포커스가 있던 버튼이 행째 사라져 포커스가 body로 떨어진다. 여기로 옮겨준다.
  // (접혀 있으면 ⋮ 메뉴 자체가 없어 삭제에 닿을 수 없으므로, 삭제 흐름에선 항상 렌더돼 있다)
  const newChatRef = useRef(null)
  const location = useLocation()
  const navigate = useNavigate()
  const confirm = useConfirm()

  useEffect(() => {
    localStorage.setItem('soksak_sidebar_collapsed', collapsed ? '1' : '0')
  }, [collapsed])

  // 드로어를 닫는다. 포커스가 안에 있었으면 늘 보이는 토글로 돌려준다
  // (펼친 목록이 사라지면서 포커스가 body로 떨어지지 않게).
  const closeDrawer = () => {
    if (asideRef.current?.contains(document.activeElement)) toggleRef.current?.focus()
    setDrawerOpen(false)
  }
  // 경계 폭을 넘나들면 닫는다 — 열어 둔 채 창을 넓히면 반투명 배경만 남는다.
  useEffect(() => {
    setDrawerOpen(false)
  }, [narrow])
  // 방·새 대화를 누르면 닫는다 — 경로가 그대로인 경우(보던 방을 다시 누름)도 있어 클릭에서 직접 닫는다.
  const onNavClick = () => {
    if (narrow) closeDrawer()
  }
  // 클릭 없이 경로가 바뀌어도(보던 방 삭제 → 홈) 닫는다. (closeDrawer는 매 렌더 새 함수라 의존성에서 뺀다)
  useEffect(() => {
    if (drawerOpen) closeDrawer()
  }, [location.pathname])
  // 열려 있는 동안 뒤 본문이 같이 스크롤되지 않게 잠근다.
  useEffect(() => {
    if (!drawerOpen) return
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = prev
    }
  }, [drawerOpen])
  // Esc: ⋮ 메뉴가 열려 있으면 그것부터, 아니면 드로어를 닫는다.
  // 드로어 안에서 난 키만 받는다 — 확인 다이얼로그의 Esc가 드로어까지 닫지 않게.
  const onAsideKeyDown = (e) => {
    if (e.key !== 'Escape' || !drawerOpen) return
    if (menuId !== null) {
      // 팝업 버튼에 포커스가 있었으면 팝업과 함께 사라져 body로 떨어진다 → ⋮로 돌려준다.
      menuBtnRef.current?.focus()
      setMenuId(null)
    } else closeDrawer()
  }

  // 메뉴가 열려 있으면 바깥 클릭 시 닫는다. ref는 열린 방의 ⋮ 버튼과 팝업에만 붙는다
  // (행 전체를 넣으면 같은 행의 방 링크를 눌러도 메뉴가 안 닫힌다).
  const menuBtnRef = useRef(null)
  const menuPopRef = useRef(null)
  useOutsideClose([menuBtnRef, menuPopRef], menuId !== null, () => setMenuId(null))
  // 메뉴는 목록 위에 겹쳐 뜨므로, 아래쪽 방에서 열면 스크롤 영역 밖으로 가려질 수 있다.
  // 열릴 때 필요한 만큼만 목록을 스크롤해 보이게 한다.
  useEffect(() => {
    if (menuId !== null) menuPopRef.current?.scrollIntoView({ block: 'nearest' })
  }, [menuId])

  // 목록은 마운트 시 한 번, 그리고 방 생성 신호(soksak:rooms-changed)를 받을 때만
  // 다시 불러온다. 이름변경·삭제는 아래에서 로컬 상태를 직접 갱신하므로 재요청이 필요 없다.
  // (매 경로 변경마다 다시 부르지 않아 불필요한 요청을 줄인다.)
  useEffect(() => {
    let alive = true
    const loadRooms = (retry = true) => {
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
          // 재요청 트리거가 마운트·방생성뿐이라, 일시적 실패로 목록이 빈 채 고착되지 않도록
          // 한 번은 잠시 뒤 재시도한다. 그래도 실패하면 조용히 둔다(본문 동작은 막지 않음).
          if (alive && retry) setTimeout(() => loadRooms(false), 1500)
        })
    }
    // 이벤트 리스너는 Event 객체를 인자로 넘기므로 retry로 새지 않게 래핑한다.
    const reload = () => loadRooms()
    loadRooms()
    window.addEventListener('soksak:rooms-changed', reload)
    return () => {
      alive = false
      window.removeEventListener('soksak:rooms-changed', reload)
    }
  }, [])

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
    // 이미 저장 요청이 진행 중이면(입력이 disabled되며 blur가 다시 호출) 중복 PATCH를 막는다.
    if (busyId === room.id) return
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
    const ok = await confirm({
      title: '이 대화를 삭제할까요?',
      message: `'${room.title}'의 대화 내용도 함께 사라집니다. 되돌릴 수 없어요.`,
      confirmLabel: '삭제',
      danger: true,
      // ⋮ 메뉴를 닫으면서 눌렀던 버튼이 사라진다 → 취소해도 돌아갈 곳이 없다.
      focusFallback: newChatRef,
    })
    if (!ok) return
    setBusyId(room.id)
    try {
      await api.deleteChatRoom(room.id)
      setRooms((prev) => prev.filter((r) => r.id !== room.id))
      newChatRef.current?.focus()
      // 지금 보고 있던 방을 지웠으면 홈으로 보낸다.
      if (location.pathname === `/chat/${room.id}`) navigate('/')
    } catch {
      // 조용히 무시 (목록 동작을 막지 않는다)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <>
      {/* 드로어 뒤 반투명 배경 — 누르면 닫힌다. 확인 다이얼로그(z 100)는 이보다 위라 그 클릭은 여기 안 닿는다. */}
      {drawerOpen && <div className="sidebar-backdrop" aria-hidden="true" onClick={closeDrawer} />}
      <aside
        ref={asideRef}
        className={`sidebar${folded ? ' collapsed' : ''}${drawerOpen ? ' drawer-open' : ''}`}
        onKeyDown={onAsideKeyDown}
      >
        <button
          type="button"
          ref={toggleRef}
          className="sidebar-toggle"
          onClick={() => (narrow ? setDrawerOpen((o) => !o) : setCollapsed((c) => !c))}
          aria-label={folded ? '채팅목록 펼치기' : '채팅목록 접기'}
          aria-expanded={!folded}
          title={folded ? '채팅목록 펼치기' : '채팅목록 접기'}
        >
          {folded ? '»' : '«'}
        </button>

        {!folded && (
          <>
            {/* 1) 채팅방 목록 */}
            <NavLink to="/" end className="new-chat" ref={newChatRef} onClick={onNavClick}>+ 새 대화</NavLink>
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
                            // 이름 수정 취소만 하고 드로어까지 닫히지 않게 한다.
                            e.stopPropagation()
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
                          onClick={onNavClick}
                          className={({ isActive }) => `room-item${isActive ? ' active' : ''}`}
                        >
                          {r.title}
                        </NavLink>
                        <button
                          type="button"
                          ref={menuId === r.id ? menuBtnRef : null}
                          className={`room-menu-btn${menuId === r.id ? ' open' : ''}`}
                          aria-label="채팅방 메뉴"
                          onClick={() => setMenuId((id) => (id === r.id ? null : r.id))}
                        >
                          ⋮
                        </button>
                      </>
                    )}
                    {menuId === r.id && (
                      <div className="room-menu-pop" ref={menuPopRef}>
                        <button onClick={() => startRename(r)}>이름 수정</button>
                        <button className="danger" onClick={() => removeRoom(r)}>삭제</button>
                      </div>
                    )}
                  </div>
                ))
              )}
            </nav>
          </>
        )}
      </aside>
    </>
  )
}
