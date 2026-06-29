import { useEffect, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'

export default function CharactersPage() {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const [characters, setCharacters] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [startingId, setStartingId] = useState(null)

  useEffect(() => {
    let alive = true
    api
      .getCharacters()
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
  }, [])

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

  if (loading) return <p className="muted">불러오는 중…</p>

  return (
    <div>
      <div className="page-head">
        <h1>캐릭터</h1>
        <Link to="/characters/new" className="btn-link">+ 캐릭터 만들기</Link>
      </div>
      {error && <p className="error">{error}</p>}
      {characters.length === 0 ? (
        <p className="muted">아직 등록된 캐릭터가 없습니다.</p>
      ) : (
        <div className="card-grid">
          {characters.map((c) => (
            <div className="char-card" key={c.id}>
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
