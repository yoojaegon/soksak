import { useEffect, useRef, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { GENRES, genreLabel } from '../genres.js'
import { useOutsideClose } from '../useOutsideClose.js'
import { useAlert } from '../confirm.jsx'
import CharacterImage from '../components/CharacterImage.jsx'
import ReportDialog from '../components/ReportDialog.jsx'

const SORT_OPTIONS = [
  { value: 'createdAt,desc', label: '최신순' },
  { value: 'likeCount,desc', label: '인기순' },
  { value: 'chatCount,desc', label: '대화 많은순' },
]

export default function CharactersPage() {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const alert = useAlert()
  const [characters, setCharacters] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [startingId, setStartingId] = useState(null)
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [sort, setSort] = useState(SORT_OPTIONS[0].value)
  const [tag, setTag] = useState('') // 선택된 장르 필터(1개). 빈 문자열이면 전체.
  // ⋮ 메뉴가 열려 있는 카드 id (null = 모두 닫힘)
  const [menuId, setMenuId] = useState(null)
  // 신고 창을 띄운 캐릭터 id (null = 닫힘)
  const [reportId, setReportId] = useState(null)
  // 내 캐릭터 카드엔 ⋮(신고)을 안 띄운다 — 서버가 REPORT_OWN_CHARACTER로 거절한다.
  const [myId, setMyId] = useState(null)

  useEffect(() => {
    if (!isAuthenticated) {
      setMyId(null)
      return
    }
    let alive = true
    api
      .getMe()
      .then((me) => alive && setMyId(me.id))
      .catch(() => {})
    return () => {
      alive = false
    }
  }, [isAuthenticated])

  // 타이핑마다 요청하지 않도록 검색어는 300ms 디바운스 후에만 반영한다.
  useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(query), 300)
    return () => clearTimeout(t)
  }, [query])

  useEffect(() => {
    let alive = true
    setLoading(true)
    api
      .getCharacters({ q: debouncedQuery, sort, tag })
      .then((page) => {
        // 백엔드가 Page<> 형태(VIA_DTO)로 주므로 실제 목록은 page.content 에 있다.
        if (alive) setCharacters(page?.content ?? [])
      })
      .catch((err) => {
        if (alive) setError(err.message)
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [debouncedQuery, sort, tag])

  const startChat = async (characterId) => {
    // 로그인 안 했으면 대화 시작 대신 로그인으로 보낸다.
    if (!isAuthenticated) {
      navigate('/login')
      return
    }
    setStartingId(characterId)
    setError('')
    try {
      // 같은 캐릭터라도 매번 새 방을 만든다. 제목은 서버가 자동으로 넘버링한다
      // (예: 클쨩, 클쨩2, 클쨩3 …).
      const room = await api.createChatRoom(characterId)
      navigate(`/chat/${room.id}`)
    } catch (err) {
      setError(err.message)
      setStartingId(null)
    }
  }

  // 메뉴가 열려 있으면 바깥 클릭 시 닫는다. ref는 열린 메뉴(버튼+팝업)에만 붙는다.
  const menuRef = useRef(null)
  useOutsideClose(menuRef, menuId !== null, () => setMenuId(null))
  // 신고 창이 닫히면 포커스를 돌려줄 곳. 팝업 항목은 메뉴와 함께 사라지므로 ⋮ 버튼으로 돌린다.
  const menuBtnRef = useRef(null)

  const openReport = (characterId) => {
    setMenuId(null)
    if (!isAuthenticated) {
      navigate('/login')
      return
    }
    setReportId(characterId)
  }
  const onReported = async () => {
    setReportId(null)
    // 신고 창의 제출 버튼이 사라진 뒤라 알림이 돌려줄 곳이 없다 → ⋮ 버튼으로.
    await alert({ title: '신고가 접수되었어요', message: '검토 후 운영 정책에 따라 처리합니다.', focusFallback: menuBtnRef })
  }

  // 장르 필터 칩을 마우스로 끌어서 가로 스크롤. (native overflow는 클릭-드래그 팬을 지원 안 함)
  const chipsRef = useRef(null)
  const dragRef = useRef({ down: false, startX: 0, startScroll: 0, moved: false })

  const onChipsPointerDown = (e) => {
    const el = chipsRef.current
    if (!el) return
    dragRef.current = { down: true, startX: e.clientX, startScroll: el.scrollLeft, moved: false }
    el.setPointerCapture?.(e.pointerId)
  }
  const onChipsPointerMove = (e) => {
    const st = dragRef.current
    if (!st.down || !chipsRef.current) return
    const dx = e.clientX - st.startX
    if (Math.abs(dx) > 4) st.moved = true
    chipsRef.current.scrollLeft = st.startScroll - dx
  }
  const onChipsPointerUp = (e) => {
    chipsRef.current?.releasePointerCapture?.(e.pointerId)
    dragRef.current.down = false
  }
  // 드래그였으면 뒤이어 발생하는 칩 클릭(필터 토글)을 캡처 단계에서 삼킨다.
  const onChipsClickCapture = (e) => {
    if (dragRef.current.moved) {
      e.preventDefault()
      e.stopPropagation()
      dragRef.current.moved = false
    }
  }

  return (
    <div>
      {/* 히어로는 처음 온 사람에게만. 로그인한 사용자에겐 매번 큰 배너가 소음이라
          아래 컴팩트 헤더만 보여준다. */}
      {!isAuthenticated && (
        <section className="hero">
          <div className="hero-main">
            <span className="eyebrow">💬 캐릭터와 대화하기</span>
            <h1 className="hero-title">
              오늘 밤은<br />
              <span className="hl">누구와</span> 이야기할까?
            </h1>
            <p className="hero-sub">
              말수 적은 선배부터 수다스러운 음유시인까지. 마음에 드는 캐릭터를 골라
              바로 말을 걸어보세요. 직접 만들어 둘 수도 있어요.
            </p>
            <div className="hero-actions">
              <Link to="/signup" className="btn-link">시작하기</Link>
              <Link to="/characters/new" className="btn-ghost">캐릭터 만들기</Link>
            </div>
          </div>
          <p className="sticker" aria-hidden="true">
            오늘 밤도 네 얘기,<br />내가 다 들어줄게 ✦
          </p>
        </section>
      )}

      <div className="page-head">
        {/* 비로그인 화면은 히어로가 h1을 가져가므로 여기선 h2로 내린다. */}
        {isAuthenticated ? <h1>캐릭터</h1> : <h2>둘러보기</h2>}
        <Link to="/characters/new" className="btn-link">+ 캐릭터 만들기</Link>
      </div>

      <div className="catalog-toolbar">
        <input
          type="search"
          className="catalog-search"
          placeholder="이름·소개로 검색"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <select
          className="catalog-sort"
          value={sort}
          onChange={(e) => setSort(e.target.value)}
          aria-label="정렬"
        >
          {SORT_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>
      </div>

      <div
        className="chip-picker filter-chips"
        ref={chipsRef}
        onPointerDown={onChipsPointerDown}
        onPointerMove={onChipsPointerMove}
        onPointerUp={onChipsPointerUp}
        onPointerLeave={onChipsPointerUp}
        onClickCapture={onChipsClickCapture}
      >
        <button
          type="button"
          className={`chip${tag === '' ? ' active' : ''}`}
          onClick={() => setTag('')}
        >
          전체
        </button>
        {GENRES.map((g) => (
          <button
            key={g.value}
            type="button"
            className={`chip${tag === g.value ? ' active' : ''}`}
            aria-pressed={tag === g.value}
            // 같은 칩을 다시 누르면 필터 해제.
            onClick={() => setTag((cur) => (cur === g.value ? '' : g.value))}
          >
            {g.label}
          </button>
        ))}
      </div>

      {error && <p className="error">{error}</p>}
      {loading ? (
        <p className="muted">불러오는 중…</p>
      ) : characters.length === 0 ? (
        <p className="muted">
          {debouncedQuery.trim() || tag ? '검색 결과가 없습니다.' : '아직 등록된 캐릭터가 없습니다.'}
        </p>
      ) : (
        <div className="card-grid">
          {characters.map((c, i) => (
            <article className="wp-card" key={c.id}>
              {/* ⋮ 더보기 메뉴 — 지금은 신고만이라 내 캐릭터엔 없다 */}
              {c.userId !== myId && (
                <div className="card-menu" ref={menuId === c.id ? menuRef : null}>
                  <button
                    type="button"
                    className="card-menu-btn"
                    aria-label="더보기"
                    aria-expanded={menuId === c.id}
                    onClick={(e) => {
                      menuBtnRef.current = e.currentTarget
                      setMenuId((id) => (id === c.id ? null : c.id))
                    }}
                  >
                    ⋮
                  </button>
                  {menuId === c.id && (
                    <div className="card-menu-pop">
                      <button className="danger" onClick={() => openReport(c.id)}>캐릭터 신고</button>
                    </div>
                  )}
                </div>
              )}
              <div className={`wp-art wp-g${(i % 8) + 1}`}>
                {(c.likeCount ?? 0) >= 1000 && <span className="wp-badge hot">인기</span>}
                <CharacterImage src={c.imageUrl} className="wp-img" loading="lazy">
                  <span className="wp-mono" aria-hidden="true">{c.characterName?.[0] ?? '?'}</span>
                </CharacterImage>
              </div>
              <div className="wp-body">
                <h3 className="wp-name">{c.characterName}</h3>
                <p className="wp-line">{c.description || '소개가 없습니다.'}</p>
                {(c.tags ?? []).length > 0 && (
                  <div className="card-tags">
                    {c.tags.map((t) => (
                      <span className="card-tag" key={t}>{genreLabel(t)}</span>
                    ))}
                  </div>
                )}
                <div className="card-stats">
                  <span className="card-stat" title="좋아요">♥ {(c.likeCount ?? 0).toLocaleString()}</span>
                  <span className="card-stat" title="대화수">💬 {(c.chatCount ?? 0).toLocaleString()}</span>
                </div>
                <button className="wp-talk" onClick={() => startChat(c.id)} disabled={startingId === c.id}>
                  {startingId === c.id ? '입장 중…' : '💬 대화 시작'}
                </button>
              </div>
            </article>
          ))}
        </div>
      )}

      {reportId !== null && (
        <ReportDialog
          kind="character"
          onSubmit={(body) => api.reportCharacter(reportId, body)}
          onClose={() => setReportId(null)}
          onDone={onReported}
          returnFocusRef={menuBtnRef}
        />
      )}
    </div>
  )
}
