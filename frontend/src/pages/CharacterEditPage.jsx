import { useEffect, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { api } from '../api.js'
import CharacterForm from '../components/CharacterForm.jsx'
import LorebookPanel from '../components/LorebookPanel.jsx'

// 캐릭터 수정 페이지. 상단 탭으로 '기본정보'와 '로어북'을 오간다.
// (메인/공개 화면에서는 진입할 수 없고, 내 캐릭터 → ⋮ → 수정으로만 들어온다.)
export default function CharacterEditPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [tab, setTab] = useState('basic')
  const [character, setCharacter] = useState(null)
  // 내 캐릭터가 아닌 경우 편집 폼 대신 안내를 보여준다(표시/UX용, 저장은 서버가 소유자만 허용).
  const [notOwned, setNotOwned] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let alive = true
    // 캐릭터 정보와 함께 '내 캐릭터' 목록을 받아, 이 캐릭터가 내 것인지 확인한다.
    // 소유 목록 조회가 실패하면(mine === null) 편집을 막지 않는다. 저장은 서버가 소유자만 허용.
    Promise.all([api.getCharacter(id), api.getMyCharacters().catch(() => null)])
      .then(([c, mine]) => {
        if (!alive) return
        setCharacter(c)
        if (Array.isArray(mine)) {
          setNotOwned(!mine.some((m) => String(m.id) === String(id)))
        }
      })
      .catch((err) => {
        if (alive) setError(err.message || '캐릭터를 불러오지 못했습니다.')
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [id])

  if (loading) return <p className="muted">불러오는 중…</p>
  if (error) return <p className="error">{error}</p>
  if (!character) return null
  if (notOwned) {
    return (
      <div>
        <Link to="/my-characters" className="back-link">← 내 캐릭터</Link>
        <p className="error">내 캐릭터가 아니에요. 수정할 수 없습니다.</p>
      </div>
    )
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <Link to="/my-characters" className="back-link">← 내 캐릭터</Link>
          <h1>{character.characterName}</h1>
        </div>
      </div>

      <div className="tabs">
        <button
          className={`tab${tab === 'basic' ? ' active' : ''}`}
          onClick={() => setTab('basic')}
        >
          캐릭터 기본정보
        </button>
        <button
          className={`tab${tab === 'lore' ? ' active' : ''}`}
          onClick={() => setTab('lore')}
        >
          로어북
        </button>
      </div>

      {tab === 'basic' ? (
        <CharacterForm
          key={id}
          initial={{
            name: character.characterName,
            description: character.description ?? '',
            persona: character.persona ?? '',
            greeting: character.greeting ?? '',
          }}
          submitLabel="저장"
          savingLabel="저장 중…"
          cancelTo="/my-characters"
          onSubmit={async (form) => {
            await api.updateCharacter(id, form)
            navigate('/my-characters')
          }}
        />
      ) : (
        <LorebookPanel characterId={id} />
      )}
    </div>
  )
}
