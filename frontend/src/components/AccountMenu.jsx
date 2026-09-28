import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'

// 상단바 오른쪽 계정 메뉴. 내 정보·내 캐릭터·내 페르소나·마디·로그아웃을 한데 모은다
// (예전엔 사이드바 아래에 있었다 — 사이드바는 대화 목록에 집중시킨다).
// - 닉네임은 마운트 때 한 번 읽고, 내 정보에서 바꾸면 'soksak:me-changed' 신호로 다시 읽는다
//   (CreditBadge의 credits-changed와 같은 방식).
// - 마디 잔액은 메뉴를 열 때마다 읽는다. 닫혀 있을 땐 보이지 않으니 신호를 들을 필요가 없다.
export default function AccountMenu() {
  const [me, setMe] = useState(null)
  const [open, setOpen] = useState(false)
  const [balance, setBalance] = useState(null)
  const rootRef = useRef(null)
  const triggerRef = useRef(null)
  const { logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => {
    let alive = true
    const load = () => {
      api
        .getMe()
        .then((data) => alive && setMe(data))
        .catch(() => {
          // 이름을 못 읽어도 메뉴는 쓸 수 있어야 한다 — 이니셜 자리에 '?'만 남는다.
        })
    }
    load()
    window.addEventListener('soksak:me-changed', load)
    return () => {
      alive = false
      window.removeEventListener('soksak:me-changed', load)
    }
  }, [])

  // 열릴 때 잔액을 새로 읽는다.
  useEffect(() => {
    if (!open) return
    let alive = true
    api
      .getCredits()
      .then((data) => alive && setBalance(data?.balance ?? null))
      .catch(() => alive && setBalance(null))
    return () => {
      alive = false
    }
  }, [open])

  // 바깥 클릭 → 닫기, Esc → 닫고 트리거로 포커스 복귀.
  // - 캡처 단계에서 듣는다: 사이드바·카드의 ⋮ 버튼은 stopPropagation으로 버블링을 끊으므로
  //   버블 단계로 들으면 ⋮를 눌러도 이 메뉴가 안 닫힌다.
  // - 반대로 이쪽은 전파를 끊지 않는다(끊으면 계정 버튼을 눌러도 열린 ⋮ 메뉴가 안 닫힌다).
  //   대신 안쪽 클릭인지는 contains로 가린다.
  useEffect(() => {
    if (!open) return
    const close = (e) => {
      if (rootRef.current?.contains(e.target)) return
      setOpen(false)
    }
    const onKey = (e) => {
      if (e.key !== 'Escape') return
      setOpen(false)
      triggerRef.current?.focus()
    }
    document.addEventListener('click', close, true)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('click', close, true)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  // 뒤로가기처럼 메뉴 밖에서 경로가 바뀌어도 닫는다.
  useEffect(() => {
    setOpen(false)
  }, [location.pathname])

  const onLogout = async () => {
    setOpen(false)
    await logout()
    // 메인은 공개라 로그아웃 후에도 캐릭터 목록을 볼 수 있다.
    navigate('/')
  }

  const initial = me?.nickname?.[0] ?? '?'

  return (
    <div className="account" ref={rootRef}>
      <button
        type="button"
        ref={triggerRef}
        className={`account-trigger${open ? ' open' : ''}`}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="계정 메뉴"
        onClick={() => setOpen((o) => !o)}
      >
        <span className="account-avatar" aria-hidden="true">{initial}</span>
        <span className="account-name">{me?.nickname}</span>
        <svg className="account-chevron" width="14" height="14" viewBox="0 0 16 16" fill="none"
          stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M4 6l4 4 4-4" />
        </svg>
      </button>

      {open && (
        // 링크를 누르면 닫는다 — 지금 있는 화면의 링크를 누르면 경로가 안 바뀌어 아래 effect가 못 잡는다.
        <div className="account-pop" role="menu" onClick={(e) => e.target.closest('a') && setOpen(false)}>
          <div className="account-head">
            <span className="account-avatar lg" aria-hidden="true">{initial}</span>
            <div className="account-who">
              <strong>{me?.nickname}</strong>
              <span>{me?.loginId}</span>
            </div>
          </div>

          <div className="account-credit">
            <div className="account-credit-now">
              <span>남은 마디</span>
              <strong>{balance ?? '–'}<small>마디</small></strong>
            </div>
            <Link to="/credits" className="account-credit-btn" role="menuitem">충전</Link>
          </div>

          <hr className="account-divider" />
          <Link to="/me" className="account-item" role="menuitem">
            <Icon d={<><circle cx="10" cy="7" r="3.2" /><path d="M3.8 16.5c1.2-3 3.5-4.5 6.2-4.5s5 1.5 6.2 4.5" /></>} />
            내 정보
          </Link>
          <Link to="/my-characters" className="account-item" role="menuitem">
            <Icon d={<><rect x="3" y="3" width="6" height="6" rx="1.5" /><rect x="11" y="3" width="6" height="6" rx="1.5" /><rect x="3" y="11" width="6" height="6" rx="1.5" /><rect x="11" y="11" width="6" height="6" rx="1.5" /></>} />
            내 캐릭터
          </Link>
          <Link to="/personas" className="account-item" role="menuitem">
            <Icon d={<><path d="M4 4.5h12v8.5H8.5L5 16v-3H4z" /><path d="M7.5 8.5h5" /></>} />
            내 페르소나
          </Link>
          <hr className="account-divider" />
          <button type="button" className="account-item logout" role="menuitem" onClick={onLogout}>
            <Icon d={<><path d="M8 4H4.5v12H8" /><path d="M11.5 6.5L15 10l-3.5 3.5M15 10H8" /></>} />
            로그아웃
          </button>
        </div>
      )}
    </div>
  )
}

function Icon({ d }) {
  return (
    <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {d}
    </svg>
  )
}
