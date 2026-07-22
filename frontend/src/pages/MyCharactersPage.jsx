import { useEffect, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { api } from '../api.js'
import { useConfirm } from '../confirm.jsx'

// 내가 만든 캐릭터만 모아 보여주는 페이지.
// 각 카드에서 바로 대화를 시작하거나 로어북으로 들어갈 수 있고,
// ⋮ 메뉴로 수정·삭제할 수 있다.
export default function MyCharactersPage() {
  const navigate = useNavigate()
  const confirm = useConfirm()
  const [characters, setCharacters] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [startingId, setStartingId] = useState(null)
  // ⋮ 메뉴가 열려 있는 카드 id (null = 모두 닫힘)
  const [menuId, setMenuId] = useState(null)
  const [deletingId, setDeletingId] = useState(null)

  useEffect(() => {
    let alive = true
    api
      .getMyCharacters()
      .then((data) => {
        if (alive) setCharacters(data ?? [])
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
  }, [])

  // 메뉴가 열려 있으면 바깥을 클릭했을 때 닫는다.
  useEffect(() => {
    if (menuId === null) return
    const close = () => setMenuId(null)
    document.addEventListener('click', close)
    return () => document.removeEventListener('click', close)
  }, [menuId])

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
    })
    if (!ok) return
    setDeletingId(c.id)
    setError('')
    try {
      await api.deleteCharacter(c.id)
      setCharacters((prev) => prev.filter((x) => x.id !== c.id))
    } catch (err) {
      setError(err.message || '삭제에 실패했습니다.')
    } finally {
      setDeletingId(null)
    }
  }

  if (loading) return <p className="muted">불러오는 중…</p>

  return (
    <div>
      <div className="page-head">
        <h1>내 캐릭터</h1>
        <Link to="/characters/new" className="btn-link">+ 캐릭터 만들기</Link>
      </div>
      {error && <p className="error">{error}</p>}
      {characters.length === 0 ? (
        <p className="muted">아직 만든 캐릭터가 없어요. 새로 만들어 보세요.</p>
      ) : (
        <div className="card-grid">
          {characters.map((c) => (
            <div className="char-card" key={c.id}>
              {/* ⋮ 수정·삭제 메뉴 */}
              <div className="card-menu">
                <button
                  type="button"
                  className="card-menu-btn"
                  aria-label="더보기"
                  onClick={(e) => {
                    e.stopPropagation()
                    setMenuId((id) => (id === c.id ? null : c.id))
                  }}
                >
                  ⋮
                </button>
                {menuId === c.id && (
                  <div className="card-menu-pop" onClick={(e) => e.stopPropagation()}>
                    <button onClick={() => navigate(`/characters/${c.id}/edit`)}>캐릭터 수정</button>
                    <button className="danger" onClick={() => remove(c)} disabled={deletingId === c.id}>
                      {deletingId === c.id ? '삭제 중…' : '캐릭터 삭제'}
                    </button>
                  </div>
                )}
              </div>

              <div className="char-avatar">{c.characterName?.[0] ?? '?'}</div>
              <h3>{c.characterName}</h3>
              <p className="muted">{c.description || '소개가 없습니다.'}</p>
              <button onClick={() => startChat(c.id)} disabled={startingId === c.id}>
                {startingId === c.id ? '입장 중…' : '대화하기'}
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
