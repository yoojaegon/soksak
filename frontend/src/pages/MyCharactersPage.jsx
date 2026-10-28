import { useEffect, useRef, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { api } from '../api.js'
import { useConfirm } from '../confirm.jsx'
import { useOutsideClose } from '../useOutsideClose.js'
import CharacterImage from '../components/CharacterImage.jsx'
import { SORT_OPTIONS } from '../characterSort.js'

const PAGE_SIZE = 20

// 받아 둔 목록 뒤에 새 페이지를 붙인다. 삭제로 서버 쪽 순번이 당겨지면 같은 캐릭터가 다시 오므로 id로 거른다.
function appendUnique(prev, next) {
  const seen = new Set(prev.map((c) => c.id))
  return [...prev, ...next.filter((c) => !seen.has(c.id))]
}

// 내가 만든 캐릭터만 모아 보여주는 페이지.
// 각 카드에서 바로 대화를 시작하거나 로어북으로 들어갈 수 있고,
// ⋮ 메뉴로 수정·삭제할 수 있다.
export default function MyCharactersPage() {
  const navigate = useNavigate()
  const confirm = useConfirm()
  const [characters, setCharacters] = useState([])
  const [total, setTotal] = useState(0)
  const [sort, setSort] = useState(SORT_OPTIONS[0].value)
  // 삭제하면 포커스가 있던 버튼이 카드째 사라져 포커스가 body로 떨어진다. 여기로 옮겨준다.
  const landingRef = useRef(null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const [startingId, setStartingId] = useState(null)
  // ⋮ 메뉴가 열려 있는 카드 id (null = 모두 닫힘)
  const [menuId, setMenuId] = useState(null)
  const [deletingId, setDeletingId] = useState(null)

  // 정렬이 바뀌면 처음부터 다시 받는다.
  useEffect(() => {
    let alive = true
    setLoading(true)
    api
      .getMyCharacters({ page: 0, size: PAGE_SIZE, sort })
      .then((page) => {
        if (!alive) return
        setCharacters(page?.content ?? [])
        setTotal(page?.page?.totalElements ?? 0)
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
  }, [sort])

  // 다음 페이지 번호는 받아 둔 개수로 정한다. 삭제한 만큼 앞 페이지를 한 번 더 받게 되는데,
  // 겹치는 건 appendUnique가 거르고 당겨져 온 캐릭터는 빠지지 않는다.
  const loadMore = async () => {
    setLoadingMore(true)
    setError('')
    try {
      const page = await api.getMyCharacters({ page: Math.floor(characters.length / PAGE_SIZE), size: PAGE_SIZE, sort })
      setCharacters((prev) => appendUnique(prev, page?.content ?? []))
      setTotal(page?.page?.totalElements ?? 0)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoadingMore(false)
    }
  }

  // 메뉴가 열려 있으면 바깥 클릭 시 닫는다. ref는 열린 메뉴(버튼+팝업)에만 붙는다.
  const menuRef = useRef(null)
  useOutsideClose(menuRef, menuId !== null, () => setMenuId(null))

  const startChat = async (characterId) => {
    setStartingId(characterId)
    setError('')
    try {
      const room = await api.createChatRoom(characterId)
      navigate(`/chat/${room.id}`)
    } catch (err) {
      setError(err.message)
      setStartingId(null)
    }
  }

  const remove = async (c) => {
    setMenuId(null)
    const ok = await confirm({
      title: '이 캐릭터를 삭제할까요?',
      message: `'${c.characterName}'와(과) 나눈 대화도 함께 사라질 수 있어요. 되돌릴 수 없습니다.`,
      confirmLabel: '삭제',
      danger: true,
      // ⋮ 메뉴를 닫으면서 눌렀던 버튼이 사라진다 → 취소해도 돌아갈 곳이 없다.
      focusFallback: landingRef,
    })
    if (!ok) return
    setDeletingId(c.id)
    setError('')
    try {
      await api.deleteCharacter(c.id)
      setCharacters((prev) => prev.filter((x) => x.id !== c.id))
      setTotal((t) => t - 1)
      // 다이얼로그가 닫히며 이미 여기로 보냈지만, 삭제로 카드가 사라진 뒤에도 확실히 맞춰 둔다.
      landingRef.current?.focus()
    } catch (err) {
      setError(err.message || '삭제에 실패했습니다.')
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <div>
      <div className="page-head">
        {/* tabIndex=-1: Tab으로는 닿지 않고, 삭제 후 포커스가 착지할 자리로만 쓴다 */}
        <h1 tabIndex={-1} ref={landingRef}>내 캐릭터</h1>
        <Link to="/characters/new" className="btn-link">+ 캐릭터 만들기</Link>
      </div>
      <div className="catalog-toolbar">
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
      {error && <p className="error">{error}</p>}
      {loading ? (
        <p className="muted">불러오는 중…</p>
      ) : characters.length === 0 ? (
        <p className="muted">아직 만든 캐릭터가 없어요. 새로 만들어 보세요.</p>
      ) : (
        <div className="card-grid">
          {characters.map((c) => (
            <div className="char-card" key={c.id}>
              {/* ⋮ 수정·삭제 메뉴 */}
              <div className="card-menu" ref={menuId === c.id ? menuRef : null}>
                <button
                  type="button"
                  className="card-menu-btn"
                  aria-label="더보기"
                  onClick={() => setMenuId((id) => (id === c.id ? null : c.id))}
                >
                  ⋮
                </button>
                {menuId === c.id && (
                  <div className="card-menu-pop">
                    <button onClick={() => navigate(`/characters/${c.id}/edit`)}>캐릭터 수정</button>
                    <button className="danger" onClick={() => remove(c)} disabled={deletingId === c.id}>
                      {deletingId === c.id ? '삭제 중…' : '캐릭터 삭제'}
                    </button>
                  </div>
                )}
              </div>

              <div className="char-avatar" aria-hidden="true">
                <CharacterImage src={c.imageUrl}>{c.characterName?.[0] ?? '?'}</CharacterImage>
              </div>
              <h3>{c.characterName}</h3>
              {/* 신고가 모여 자동으로 숨겨진 상태 — 카탈로그·새 대화에서 빠져 있다 */}
              {c.hidden && (
                <span className="badge hidden-badge" title="신고가 접수되어 검토 중이에요. 그동안 목록에 보이지 않고 새 대화를 시작할 수 없어요.">
                  검토 중
                </span>
              )}
              <p className="muted">{c.description || '소개가 없습니다.'}</p>
              <div className="card-stats">
                <span className="card-stat" title="좋아요">♥ {(c.likeCount ?? 0).toLocaleString()}</span>
                <span className="card-stat" title="대화수">💬 {(c.chatCount ?? 0).toLocaleString()}</span>
              </div>
              {/* 숨겨진 캐릭터는 제작자도 새 방을 못 연다(서버 CHARACTER_HIDDEN) — 눌러봐야 에러뿐이라 막아 둔다 */}
              <button onClick={() => startChat(c.id)} disabled={c.hidden || startingId === c.id}>
                {startingId === c.id ? '입장 중…' : '대화하기'}
              </button>
            </div>
          ))}
        </div>
      )}
      {!loading && characters.length < total && (
        <div className="load-more">
          <button type="button" onClick={loadMore} disabled={loadingMore}>
            {loadingMore ? '불러오는 중…' : '더 보기'}
          </button>
        </div>
      )}
    </div>
  )
}
