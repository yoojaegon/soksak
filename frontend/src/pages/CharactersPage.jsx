import { useEffect, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { GENRES, genreLabel } from '../genres.js'

const SORT_OPTIONS = [
  { value: 'createdAt,desc', label: '최신순' },
  { value: 'likeCount,desc', label: '인기순' },
  { value: 'chatCount,desc', label: '대화 많은순' },
]

export default function CharactersPage() {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const [characters, setCharacters] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [startingId, setStartingId] = useState(null)
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [sort, setSort] = useState(SORT_OPTIONS[0].value)
  const [tag, setTag] = useState('') // 선택된 장르 필터(1개). 빈 문자열이면 전체.

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

  return (
    <div>
      <div className="page-head">
        <h1>캐릭터</h1>
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

      <div className="chip-picker filter-chips">
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
          {characters.map((c) => (
            <div className="char-card" key={c.id}>
              <div className="char-avatar">{c.characterName?.[0] ?? '?'}</div>
              <h3>{c.characterName}</h3>
              {(c.tags ?? []).length > 0 && (
                <div className="card-tags">
                  {c.tags.map((t) => (
                    <span className="card-tag" key={t}>{genreLabel(t)}</span>
                  ))}
                </div>
              )}
              <p className="muted">{c.description || '소개가 없습니다.'}</p>
              <div className="card-stats">
                <span className="card-stat" title="좋아요">♥ {(c.likeCount ?? 0).toLocaleString()}</span>
                <span className="card-stat" title="대화수">💬 {(c.chatCount ?? 0).toLocaleString()}</span>
              </div>
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
